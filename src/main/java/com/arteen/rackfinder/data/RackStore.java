package com.arteen.rackfinder.data;

import com.arteen.rackfinder.RackFinder;
import com.arteen.rackfinder.config.RackFinderConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory index of every rack the client has seen, plus disk persistence.
 *
 * <p>All data comes from the client side only: block entities of already loaded chunks (their NBT
 * arrives with the chunk packet) and racks whose GUI the player has opened (fresh slot sync).
 * Nothing is ever requested from the server, so the mod works on any server.</p>
 */
public final class RackStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DATA_VERSION = 1;

    public static final RackStore INSTANCE = new RackStore();

    private final Map<String, RackRecord> racks = new LinkedHashMap<>();
    private String worldKey = "unknown";
    private String lastQuery = "";
    private boolean loaded;
    private boolean dirty;
    private long lastSave;
    private long revision;
    private List<ItemAggregate> aggregates;
    private Map<ItemKey, ItemAggregate> aggregateByKey;

    private RackStore() {
    }

    // ------------------------------------------------------------------ data in

    public void put(final ResourceLocation dimension, final BlockPos pos,
                    final RackSnapshot snapshot, final String source, final boolean doubleRack) {
        put(dimension, pos, snapshot, source, doubleRack, null);
    }

    public void put(final ResourceLocation dimension, final BlockPos pos, final RackSnapshot snapshot,
                    final String source, final boolean doubleRack, final String blockId) {
        final String key = key(dimension, pos);
        RackRecord record = racks.get(key);
        if (record == null) {
            record = new RackRecord(dimension, pos);
            racks.put(key, record);
        }
        if (record.apply(snapshot, source, doubleRack, blockId)) {
            dirty = true;
            aggregates = null;
            revision++;
        }
    }

    // ------------------------------------------------------------------ queries

    public Collection<RackRecord> all() {
        return Collections.unmodifiableCollection(racks.values());
    }

    public int rackCount() {
        return racks.size();
    }

    public int documentedRackCount() {
        int count = 0;
        for (final RackRecord record : racks.values()) {
            if (record.hasData()) {
                count++;
            }
        }
        return count;
    }

    public int emptyRackCount() {
        int count = 0;
        for (final RackRecord record : racks.values()) {
            if (record.hasData() && record.contents().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public long lastUpdateAt() {
        long latest = 0L;
        for (final RackRecord record : racks.values()) {
            latest = Math.max(latest, record.updatedAt());
        }
        return latest;
    }

    public List<ItemAggregate> aggregates() {
        if (aggregates == null) {
            rebuildAggregates();
        }
        return aggregates;
    }

    /**
     * Exact lookup used by the inventory hover highlight.
     */
    public ItemAggregate find(final ItemKey key) {
        if (aggregates == null) {
            rebuildAggregates();
        }
        return aggregateByKey == null ? null : aggregateByKey.get(key);
    }

    public int itemCount() {
        return aggregates().size();
    }

    /**
     * @param query space separated tokens; every token must match the item name or registry id.
     */
    public List<ItemAggregate> search(final String query) {
        final List<ItemAggregate> allItems = aggregates();
        final String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return allItems;
        }
        final String[] tokens = normalized.split("\\s+");
        final List<ItemAggregate> result = new ArrayList<>();
        for (final ItemAggregate aggregate : allItems) {
            final String haystack = aggregate.key().searchText();
            boolean matches = true;
            for (final String token : tokens) {
                if (!haystack.contains(token)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result.add(aggregate);
            }
        }
        return result;
    }

    /**
     * Racks that contain the given item, with per-rack amounts, nearest first.
     */
    public List<RackHit> hits(final ItemAggregate aggregate, final Player player) {
        return aggregate.hitsNearestFirst(player);
    }

    public void clear() {
        revision++;
        racks.clear();
        aggregates = null;
        aggregateByKey = null;
        dirty = true;
    }

    private void rebuildAggregates() {
        final Map<ItemKey, ItemAggregate> map = new LinkedHashMap<>();
        // fill below
        for (final RackRecord record : racks.values()) {
            if (!record.hasData()) {
                continue;
            }
            for (final Map.Entry<ItemKey, Integer> entry : record.contents().entrySet()) {
                map.computeIfAbsent(entry.getKey(), ItemAggregate::new).add(record, entry.getValue());
            }
        }
        final List<ItemAggregate> list = new ArrayList<>(map.values());
        list.sort((a, b) -> {
            final int byName = a.key().displayName().compareToIgnoreCase(b.key().displayName());
            return byName != 0 ? byName : a.key().itemId().compareTo(b.key().itemId());
        });
        aggregates = list;
        aggregateByKey = map;
    }

    // ------------------------------------------------------------------ persistence

    public String worldKey() {
        return worldKey;
    }

    /**
     * Text last typed into the search window (persisted per world).
     */
    public String lastQuery() {
        return lastQuery;
    }

    public void setLastQuery(final String query) {
        lastQuery = query == null ? "" : query;
        dirty = true;
    }

    public void setWorldKey(final String key) {
        if (loaded && key.equals(worldKey)) {
            return;
        }
        if (loaded) {
            save();
        }
        worldKey = key;
        racks.clear();
        aggregates = null;
        aggregateByKey = null;
        lastQuery = "";
        dirty = false;
        loaded = true;
        load();
        revision++;
    }

    /**
     * Localized name of the stored block id (e.g. "Chest", "Rack").
     */
    public static String blockName(final String blockId) {
        if (blockId == null) {
            return "";
        }
        final ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return blockId;
        }
        return BuiltInRegistries.BLOCK.getOptional(id).map(Block::getName).map(Object::toString).orElse(blockId);
    }

    /**
     * Monotonic counter bumped whenever indexed data changes; used to refresh the live highlight.
     */
    public long revision() {
        return revision;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Periodic autosave, called from the client tick.
     */
    public void tick() {
        if (!loaded || !dirty) {
            return;
        }
        final int seconds = RackFinderConfig.AUTO_SAVE_SECONDS.get();
        if (System.currentTimeMillis() - lastSave >= seconds * 1000L) {
            save();
        }
    }

    public void save() {
        lastSave = System.currentTimeMillis();
        if (!loaded) {
            return;
        }
        final Path file = storeFile(worldKey);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(), writer);
            }
            dirty = false;
        } catch (final IOException | RuntimeException e) {
            RackFinder.LOGGER.warn("[rackfinder] failed to save rack index to {}", file, e);
        }
    }

    private void load() {
        lastSave = System.currentTimeMillis();
        final Path file = storeFile(worldKey);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("racks")) {
                return;
            }
            if (root.has("lastQuery")) {
                lastQuery = root.get("lastQuery").getAsString();
            }
            final JsonArray array = root.getAsJsonArray("racks");
            for (final JsonElement element : array) {
                try {
                    readRecord(element.getAsJsonObject());
                } catch (final RuntimeException e) {
                    RackFinder.LOGGER.debug("[rackfinder] skipped broken rack entry", e);
                }
            }
            RackFinder.LOGGER.info("[rackfinder] loaded {} racks for world '{}'", racks.size(), worldKey);
        } catch (final IOException | RuntimeException e) {
            RackFinder.LOGGER.warn("[rackfinder] failed to load rack index from {}", file, e);
            racks.clear();
        }
        aggregates = null;
    }

    private JsonObject toJson() {
        final JsonObject root = new JsonObject();
        root.addProperty("version", DATA_VERSION);
        root.addProperty("world", worldKey);
        root.addProperty("savedAt", System.currentTimeMillis());
        root.addProperty("lastQuery", lastQuery);
        final JsonArray array = new JsonArray();
        for (final RackRecord record : racks.values()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("dim", record.dimension().toString());
            entry.addProperty("x", record.pos().getX());
            entry.addProperty("y", record.pos().getY());
            entry.addProperty("z", record.pos().getZ());
            entry.addProperty("warehouse", record.warehouse());
            entry.addProperty("double", record.doubleRack());
            entry.addProperty("hasData", record.hasData());
            entry.addProperty("filled", record.filledSlots());
            entry.addProperty("updated", record.updatedAt());
            entry.addProperty("source", record.source());
            entry.addProperty("block", record.blockId());
            final JsonArray items = new JsonArray();
            for (final Map.Entry<ItemKey, Integer> item : record.contents().entrySet()) {
                final JsonObject itemJson = new JsonObject();
                itemJson.addProperty("id", item.getKey().itemId());
                itemJson.addProperty("count", item.getValue());
                final CompoundTag tag = item.getKey().prototype().getTag();
                if (tag != null && !tag.isEmpty()) {
                    itemJson.addProperty("tag", tag.toString());
                }
                items.add(itemJson);
            }
            entry.add("items", items);
            array.add(entry);
        }
        root.add("racks", array);
        return root;
    }

    private void readRecord(final JsonObject entry) {
        if (!entry.has("dim") || !entry.has("x") || !entry.has("y") || !entry.has("z")) {
            return;
        }
        final ResourceLocation dimension = new ResourceLocation(entry.get("dim").getAsString());
        final BlockPos pos = new BlockPos(entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());

        final Map<ItemKey, Integer> contents = new LinkedHashMap<>();
        if (entry.has("items")) {
            for (final JsonElement itemElement : entry.getAsJsonArray("items")) {
                final JsonObject itemJson = itemElement.getAsJsonObject();
                if (!itemJson.has("id")) {
                    continue;
                }
                final String id = itemJson.get("id").getAsString();
                final int count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 0;
                CompoundTag tag = null;
                if (itemJson.has("tag")) {
                    try {
                        tag = TagParser.parseTag(itemJson.get("tag").getAsString());
                    } catch (final Exception e) {
                        RackFinder.LOGGER.debug("[rackfinder] unparsable NBT for item {}", id, e);
                    }
                }
                final ItemKey key = ItemKey.of(id, tag);
                if (key.prototype().isEmpty()) {
                    // Item no longer exists (removed mod) - drop it instead of showing "Air".
                    continue;
                }
                contents.merge(key, count, Integer::sum);
            }
        }

        final RackSnapshot snapshot = new RackSnapshot(contents,
                entry.has("filled") ? entry.get("filled").getAsInt() : 0,
                entry.has("warehouse") && entry.get("warehouse").getAsBoolean(),
                !entry.has("hasData") || entry.get("hasData").getAsBoolean());

        final RackRecord record = new RackRecord(dimension, pos);
        record.apply(snapshot, entry.has("source") ? entry.get("source").getAsString() : "disk",
                entry.has("double") && entry.get("double").getAsBoolean(),
                entry.has("block") ? entry.get("block").getAsString() : null);
        racks.put(record.key(), record);
    }

    private static Path storeFile(final String worldKey) {
        return FMLPaths.CONFIGDIR.get().resolve("rackfinder").resolve("racks-" + sanitize(worldKey) + ".json");
    }

    /**
     * Per-world key: server address, or singleplayer world name.
     */
    public static String currentWorldKey(final Minecraft minecraft) {
        if (minecraft == null) {
            return "unknown";
        }
        final ServerData server = minecraft.getCurrentServer();
        if (server != null) {
            return "srv-" + sanitize(server.ip);
        }
        if (minecraft.getSingleplayerServer() != null) {
            return "sp-" + sanitize(minecraft.getSingleplayerServer().getWorldData().getLevelName());
        }
        return "unknown";
    }

    private static String sanitize(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return "unknown";
        }
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String key(final ResourceLocation dimension, final BlockPos pos) {
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
