package com.arteen.rackfinder.client;

import com.arteen.rackfinder.data.ItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The item the player is currently looking for (picked in the search window). It is kept after the
 * window is closed so container GUIs can highlight matching slots.
 */
public final class TargetItem {
    private static ItemKey target;
    private static Item targetItem;

    private TargetItem() {
    }

    public static void set(final ItemKey key) {
        target = key;
        targetItem = key == null ? null : key.prototype().getItem();
    }

    @Nullable
    public static ItemKey get() {
        return target;
    }

    public static boolean isSet() {
        return target != null;
    }

    public static void clear() {
        target = null;
        targetItem = null;
    }

    public static boolean matches(final ItemStack stack) {
        if (target == null || targetItem == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() != targetItem) {
            return false;
        }
        return target.equals(ItemKey.of(stack));
    }
}
