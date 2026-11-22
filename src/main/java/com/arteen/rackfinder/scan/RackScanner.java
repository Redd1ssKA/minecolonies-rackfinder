package com.arteen.rackfinder.scan;

import com.arteen.rackfinder.RackFinder;
import com.arteen.rackfinder.compat.MineColoniesRackCompat;
import com.arteen.rackfinder.config.RackFinderConfig;
import com.arteen.rackfinder.data.RackSnapshot;
import com.arteen.rackfinder.data.RackStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks the chunks the client already has and indexes every MineColonies rack inside them.
 *
 * <p>The block entity NBT (including the rack inventory) is part of the chunk packet, so no
 * server side component is needed. Newly loaded chunks are picked up automatically, and the whole
 * radius is re-read occasionally to refresh values the server changed in the meantime.</p>
 */
public final class RackScanner {
    private static final Map<String, WeakReference<LevelChunk>> SEEN_CHUNKS = new HashMap<>();
    private static final Set<String> QUEUED = new HashSet<>();
    private static final Deque<long[]> QUEUE = new ArrayDeque<>();

    private static ResourceLocation dimension;
    private static int tickCounter;
    private static long lastFullRefresh;
    private static int totalRacksFound;

    private RackScanner() {
    }

    public static void tick(final Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        final ResourceLocation currentDimension = minecraft.level.dimension().location();
        if (!currentDimension.equals(dimension)) {
            dimension = currentDimension;
            reset();
        }

        final int interval = Math.max(5, RackFinderConfig.SCAN_INTERVAL_TICKS.get());
        if (++tickCounter >= interval) {
            tickCounter = 0;
            collect(minecraft.level);
        }

        int budget = 8;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            final long[] chunkPos = QUEUE.poll();
            QUEUED.remove(key((int) chunkPos[0], (int) chunkPos[1]));
            scanChunk(minecraft.level, (int) chunkPos[0], (int) chunkPos[1]);
        }
    }

    /**
     * Forget everything and rescan the whole radius (used by the GUI button / command).
     */
    public static void requestRescan() {
        SEEN_CHUNKS.clear();
        QUEUED.clear();
        QUEUE.clear();
        lastFullRefresh = 0L;
        tickCounter = 0;
    }

    public static int totalRacksFound() {
        return totalRacksFound;
    }

    private static void reset() {
        SEEN_CHUNKS.clear();
        QUEUED.clear();
        QUEUE.clear();
        tickCounter = 0;
        lastFullRefresh = 0L;
        totalRacksFound = 0;
    }

    private static void collect(final ClientLevel level) {
        final int radius = Math.max(1, RackFinderConfig.SCAN_RADIUS_CHUNKS.get());
        final int fullRefreshSeconds = RackFinderConfig.FULL_REFRESH_SECONDS.get();
        if (fullRefreshSeconds > 0 && System.currentTimeMillis() - lastFullRefresh > fullRefreshSeconds * 1000L) {
            lastFullRefresh = System.currentTimeMillis();
            SEEN_CHUNKS.clear();
        }

        final var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        final int playerChunkX = player.chunkPosition().x;
        final int playerChunkZ = player.chunkPosition().z;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final int chunkX = playerChunkX + dx;
                final int chunkZ = playerChunkZ + dz;
                final LevelChunk chunk = chunkAt(level, chunkX, chunkZ);
                final String chunkKey = key(chunkX, chunkZ);
                if (chunk == null) {
                    SEEN_CHUNKS.remove(chunkKey);
                    continue;
                }
                final WeakReference<LevelChunk> seen = SEEN_CHUNKS.get(chunkKey);
                if (seen != null && seen.get() == chunk) {
                    continue;
                }
                if (QUEUED.add(chunkKey)) {
                    QUEUE.add(new long[]{chunkX, chunkZ});
                }
            }
        }
    }

    private static void scanChunk(final ClientLevel level, final int chunkX, final int chunkZ) {
        final LevelChunk chunk = chunkAt(level, chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        SEEN_CHUNKS.put(key(chunkX, chunkZ), new WeakReference<>(chunk));

        final ResourceLocation dimensionId = level.dimension().location();
        for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!MineColoniesRackCompat.isRack(blockEntity)) {
                continue;
            }
            final BlockPos pos = blockEntity.getBlockPos();
            final RackSnapshot snapshot = MineColoniesRackCompat.read(blockEntity);
            final boolean doubleRack = MineColoniesRackCompat.isDoubleRack(level, pos);
            final String blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
            RackStore.INSTANCE.put(dimensionId, pos, snapshot, "chunk", doubleRack, blockId);
            totalRacksFound++;
        }
    }

    private static LevelChunk chunkAt(final ClientLevel level, final int chunkX, final int chunkZ) {
        final ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    private static String key(final int chunkX, final int chunkZ) {
        return chunkX + ":" + chunkZ;
    }

    public static void logSummary() {
        RackFinder.LOGGER.info("[rackfinder] scan stats: racks seen {}, indexed {}", totalRacksFound, RackStore.INSTANCE.rackCount());
    }
}
