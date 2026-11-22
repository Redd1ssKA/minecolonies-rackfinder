package com.arteen.rackfinder.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Map;

/**
 * Everything the client knows about a single rack block.
 */
public final class RackRecord {
    private final ResourceLocation dimension;
    private final BlockPos pos;

    private Map<ItemKey, Integer> contents = Collections.emptyMap();
    private int filledSlots;
    private boolean warehouse;
    private boolean doubleRack;
    private boolean hasData;
    private long updatedAt;
    private String source = "";
    private String blockId = "minecolonies:blockminecoloniesrack";

    public RackRecord(final ResourceLocation dimension, final BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos.immutable();
    }

    /**
     * @return true when the stored information actually changed (so the item index must be rebuilt).
     */
    public boolean apply(final RackSnapshot snapshot, final String source, final boolean doubleRack) {
        return apply(snapshot, source, doubleRack, blockId);
    }

    public boolean apply(final RackSnapshot snapshot, final String source, final boolean doubleRack,
                         final String blockId) {
        final boolean changed = !contents.equals(snapshot.contents())
                || warehouse != snapshot.warehouse()
                || hasData != snapshot.hasData()
                || this.doubleRack != doubleRack
                || !this.blockId.equals(blockId);

        this.contents = snapshot.contents();
        this.filledSlots = snapshot.filledSlots();
        this.warehouse = snapshot.warehouse();
        this.hasData = snapshot.hasData();
        this.doubleRack = doubleRack;
        this.updatedAt = System.currentTimeMillis();
        this.source = source;
        if (blockId != null && !blockId.isBlank()) {
            this.blockId = blockId;
        }
        return changed;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public Map<ItemKey, Integer> contents() {
        return contents;
    }

    public int filledSlots() {
        return filledSlots;
    }

    public boolean warehouse() {
        return warehouse;
    }

    public boolean doubleRack() {
        return doubleRack;
    }

    public boolean hasData() {
        return hasData;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public String source() {
        return source;
    }

    /**
     * Registry id of the block (rack, chest, barrel, ...).
     */
    public String blockId() {
        return blockId;
    }

    public int countOf(final ItemKey key) {
        return contents.getOrDefault(key, 0);
    }

    /**
     * Distance in blocks, or {@link Double#MAX_VALUE} when the rack is in another dimension.
     */
    public double distanceTo(final Player player) {
        if (player == null || player.level() == null) {
            return Double.MAX_VALUE;
        }
        if (!player.level().dimension().location().equals(dimension)) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(player.blockPosition().distSqr(pos));
    }

    public boolean sameDimensionAs(final Player player) {
        return player != null && player.level() != null
                && player.level().dimension().location().equals(dimension);
    }

    public String key() {
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
