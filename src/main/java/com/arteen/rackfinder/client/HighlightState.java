package com.arteen.rackfinder.client;

import com.arteen.rackfinder.config.RackFinderConfig;
import com.arteen.rackfinder.data.RackRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Racks highlighted for the item picked in the search window (cyan outlines, green slot frames).
 */
public final class HighlightState {
    public static final int TARGET_COLOR = 0xFF33D6FF;
    public static final int NEAREST_TARGET_COLOR = 0xFFFFC400;

    public record Box(ResourceLocation dimension, BlockPos pos, int color, String label, long createdAt) {
    }

    private static final long LIFETIME_MS = 10L * 60L * 1000L;
    private static final Map<String, Box> TARGET = new LinkedHashMap<>();
    private static boolean isolated;

    private HighlightState() {
    }

    public static void highlight(final Collection<RackRecord> records, final String label) {
        isolated = false;
        TARGET.clear();
        final int limit = Math.max(1, RackFinderConfig.MAX_HIGHLIGHTS.get());
        final long now = System.currentTimeMillis();
        int index = 0;
        for (final RackRecord record : records) {
            if (index++ >= limit) {
                break;
            }
            TARGET.put(record.key(), new Box(record.dimension(), record.pos(), TARGET_COLOR, label, now));
        }
    }

    public static void clear() {
        isolated = false;
        TARGET.clear();
    }

    /**
     * True when the player isolated a single container from the result list; live updates must not
     * expand that selection back to every match.
     */
    public static boolean isIsolated() {
        return isolated;
    }

    public static void setIsolated(final boolean value) {
        isolated = value;
    }

    public static List<Box> target() {
        final long now = System.currentTimeMillis();
        TARGET.values().removeIf(box -> now - box.createdAt() > LIFETIME_MS);
        return new ArrayList<>(TARGET.values());
    }

    public static int size() {
        return TARGET.size();
    }
}
