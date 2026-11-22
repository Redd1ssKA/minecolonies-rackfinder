package com.arteen.rackfinder.dev;

import com.arteen.rackfinder.compat.MineColoniesRackCompat;
import com.arteen.rackfinder.data.ItemAggregate;
import com.arteen.rackfinder.data.ItemKey;
import com.arteen.rackfinder.data.RackRecord;
import com.arteen.rackfinder.data.RackSnapshot;
import com.arteen.rackfinder.data.RackStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless self-test for the MineColonies rack NBT reader.
 * Run with: ./gradlew selfTest
 */
public final class SelfTest {
    private static int failures;

    private SelfTest() {
    }

    /**
     * @return number of failed assertions.
     */
    public static int run() {
        System.out.println("[selftest] registries ready, stone id = " + BuiltInRegistries.ITEM.getKey(Items.STONE));

        testRealRackLayout();
        testEmptyRack();
        testNoInventoryTag();
        testTaggedItems();
        testStorePersistence();

        System.out.println(failures == 0 ? "[selftest] PASS" : "[selftest] FAILED: " + failures);
        return failures;
    }

    /**
     * Reproduces MineColonies' TileEntityRack#saveAdditional layout.
     */
    private static void testRealRackLayout() {
        final CompoundTag root = new CompoundTag();
        root.putBoolean("inWarehouse", true);
        root.putInt("tagSIze", 1);
        final ListTag inventory = new ListTag();
        inventory.add(emptyEntry());
        inventory.add(stackEntry(new ItemStack(Items.OAK_PLANKS, 64)));
        inventory.add(stackEntry(new ItemStack(Items.OAK_PLANKS, 32)));
        inventory.add(stackEntry(new ItemStack(Items.BREAD, 5)));
        root.put("inventory", inventory);

        final RackSnapshot snapshot = MineColoniesRackCompat.read(root);
        check(snapshot.hasData(), "hasData");
        check(snapshot.warehouse(), "warehouse flag");
        check(snapshot.filledSlots() == 3, "filled slots = " + snapshot.filledSlots());
        check(snapshot.contents().size() == 2, "distinct items = " + snapshot.contents().size());

        int planks = 0;
        int bread = 0;
        for (final Map.Entry<ItemKey, Integer> entry : snapshot.contents().entrySet()) {
            if (entry.getKey().prototype().getItem() == Items.OAK_PLANKS) {
                planks = entry.getValue();
            }
            if (entry.getKey().prototype().getItem() == Items.BREAD) {
                bread = entry.getValue();
            }
        }
        check(planks == 96, "planks merged to 96, got " + planks);
        check(bread == 5, "bread 5, got " + bread);
        check(snapshot.totalItems() == 101, "total 101, got " + snapshot.totalItems());
    }

    private static void testEmptyRack() {
        final CompoundTag root = new CompoundTag();
        final ListTag inventory = new ListTag();
        inventory.add(emptyEntry());
        inventory.add(emptyEntry());
        root.put("inventory", inventory);
        final RackSnapshot snapshot = MineColoniesRackCompat.read(root);
        check(snapshot.hasData(), "empty rack still has data");
        check(snapshot.contents().isEmpty(), "empty rack has no items");
        check(snapshot.filledSlots() == 0, "empty rack has 0 filled slots");
    }

    private static void testNoInventoryTag() {
        final RackSnapshot snapshot = MineColoniesRackCompat.read(new CompoundTag());
        check(!snapshot.hasData(), "unknown rack has no data");
    }

    private static void testTaggedItems() {
        final ItemStack withTag = new ItemStack(Items.DIAMOND_SWORD);
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Damage", 7);
        withTag.setTag(tag);

        final ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        final ItemKey taggedKey = ItemKey.of(withTag);
        final ItemKey plainKey = ItemKey.of(plain);
        check(!taggedKey.equals(plainKey), "tagged and plain swords differ");
        check(taggedKey.equals(ItemKey.of(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD).toString(),
                taggedKey.prototype().getTag())), "rebuilt key from id+nbt is equal");

        final CompoundTag root = new CompoundTag();
        final ListTag inventory = new ListTag();
        inventory.add(stackEntry(withTag));
        root.put("inventory", inventory);
        final RackSnapshot snapshot = MineColoniesRackCompat.read(root);
        check(snapshot.contents().size() == 1, "one tagged item");
        final ItemKey parsed = snapshot.contents().keySet().iterator().next();
        check(parsed.equals(taggedKey), "parsed key keeps NBT");
        check(parsed.prototype().getTag() != null && parsed.prototype().getTag().getInt("Damage") == 7,
                "parsed stack keeps Damage");
    }

    /**
     * Exercises the in-memory index, the search and the JSON round-trip through the real config dir.
     */
    private static void testStorePersistence() {
        final RackStore store = RackStore.INSTANCE;
        store.setWorldKey("selftest");
        store.clear();

        final ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        final Map<ItemKey, Integer> first = new LinkedHashMap<>();
        first.put(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 64);
        first.put(ItemKey.of(new ItemStack(Items.EMERALD)), 10);
        store.put(overworld, new BlockPos(10, 64, 10), new RackSnapshot(first, 2, true, true), "selftest", true);

        final Map<ItemKey, Integer> second = new LinkedHashMap<>();
        second.put(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 32);
        store.put(overworld, new BlockPos(-5, 70, 3), new RackSnapshot(second, 1, false, true), "selftest", false);
        final Map<ItemKey, Integer> chestContents = new LinkedHashMap<>();
        chestContents.put(ItemKey.of(new ItemStack(Items.EMERALD)), 7);
        store.put(overworld, new BlockPos(3, 64, 3), new RackSnapshot(chestContents, 1, false, true),
                "selftest", false, "minecraft:chest");

        check(store.rackCount() == 3, "store has 3 containers, got " + store.rackCount());
        check(store.itemCount() == 2, "store has 2 item types, got " + store.itemCount());
        for (final ItemAggregate aggregate : store.aggregates()) {
            System.out.println("[selftest] agg " + aggregate.key().itemId()
                    + " total=" + aggregate.total() + " racks=" + aggregate.rackCount());
        }
        final ItemAggregate planks = find(store, Items.OAK_PLANKS);
        check(planks != null && planks.total() == 96, "aggregate planks 96, got "
                + (planks == null ? "null" : planks.total()));
        check(planks != null && planks.rackCount() == 2, "planks in 2 racks, got "
                + (planks == null ? "null" : planks.rackCount()));
        check(store.search("oak").size() == 1, "search by display name");
        check(store.search("minecraft:emerald").size() == 1, "search by registry id");
        check(store.search("oak emerald").isEmpty(), "space separated tokens are AND");
        check(store.search("").size() == 2, "empty query lists everything");

        store.save();
        store.setWorldKey("selftest-other");
        check(store.rackCount() == 0, "other world starts empty, got " + store.rackCount());
        store.setWorldKey("selftest");
        check(store.rackCount() == 3, "reloaded 3 containers, got " + store.rackCount());
        check(store.itemCount() == 2, "reloaded 2 item types, got " + store.itemCount());
        final ItemAggregate reloaded = find(store, Items.OAK_PLANKS);
        check(reloaded != null && reloaded.total() == 96 && reloaded.rackCount() == 2, "reloaded aggregate");
        boolean warehouseFlag = false;
        boolean doubleFlag = false;
        for (final RackRecord record : store.all()) {
            warehouseFlag |= record.warehouse();
            doubleFlag |= record.doubleRack();
        }
        check(warehouseFlag, "warehouse flag persisted");
        check(doubleFlag, "double rack flag persisted");
        check(store.search("emerald").size() == 1, "search after reload");
        boolean chestSeen = false;
        for (final RackRecord record : store.all()) {
            if ("minecraft:chest".equals(record.blockId())) {
                chestSeen = true;
            }
        }
        check(chestSeen, "block id persisted");
        check(!RackStore.blockName("minecraft:chest").isBlank(), "block name resolves");

        store.clear();
        store.save();
    }

    private static ItemAggregate find(final RackStore store, final net.minecraft.world.item.Item item) {
        for (final ItemAggregate aggregate : store.aggregates()) {
            if (aggregate.key().prototype().getItem() == item) {
                return aggregate;
            }
        }
        return null;
    }

    private static CompoundTag emptyEntry() {
        final CompoundTag entry = new CompoundTag();
        entry.putBoolean("empty", true);
        return entry;
    }

    private static CompoundTag stackEntry(final ItemStack stack) {
        final CompoundTag entry = new CompoundTag();
        stack.save(entry);
        return entry;
    }

    private static void check(final boolean condition, final String what) {
        if (condition) {
            System.out.println("[selftest] ok   " + what);
        } else {
            failures++;
            System.out.println("[selftest] FAIL " + what);
        }
    }
}
