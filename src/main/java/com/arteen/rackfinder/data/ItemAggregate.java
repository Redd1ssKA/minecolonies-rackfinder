package com.arteen.rackfinder.data;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * All racks that contain one particular item, aggregated across the whole indexed colony.
 */
public final class ItemAggregate {
    private final ItemKey key;
    private final List<RackHit> hits = new ArrayList<>();
    private int total;

    public ItemAggregate(final ItemKey key) {
        this.key = key;
    }

    void add(final RackRecord record, final int count) {
        if (count <= 0) {
            return;
        }
        hits.add(new RackHit(record, count));
        total += count;
    }

    public ItemKey key() {
        return key;
    }

    public int total() {
        return total;
    }

    public int rackCount() {
        return hits.size();
    }

    public List<RackHit> hits() {
        return hits;
    }

    /**
     * Hits ordered by distance to the player (nearest first, other dimensions last).
     */
    public List<RackHit> hitsNearestFirst(final Player player) {
        final List<RackHit> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingDouble(hit -> hit.record().distanceTo(player)));
        return sorted;
    }

    public RackHit nearest(final Player player) {
        RackHit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final RackHit hit : hits) {
            final double distance = hit.record().distanceTo(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }
        return best;
    }
}
