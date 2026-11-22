package com.arteen.rackfinder.data;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of one rack's content, read either from the client block entity
 * (chunk scan) or from an open rack GUI (fresh sync).
 */
public final class RackSnapshot {
    private static final RackSnapshot UNKNOWN = new RackSnapshot(Collections.emptyMap(), 0, false, false);

    private final Map<ItemKey, Integer> contents;
    private final int filledSlots;
    private final boolean warehouse;
    private final boolean hasData;

    public RackSnapshot(final Map<ItemKey, Integer> contents, final int filledSlots,
                        final boolean warehouse, final boolean hasData) {
        this.contents = contents;
        this.filledSlots = filledSlots;
        this.warehouse = warehouse;
        this.hasData = hasData;
    }

    public static RackSnapshot unknown() {
        return UNKNOWN;
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

    /**
     * False when the client has no inventory information for this rack yet.
     */
    public boolean hasData() {
        return hasData;
    }

    public int totalItems() {
        int total = 0;
        for (final int value : contents.values()) {
            total += value;
        }
        return total;
    }
}
