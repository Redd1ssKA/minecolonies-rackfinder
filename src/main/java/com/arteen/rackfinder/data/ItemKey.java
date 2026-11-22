package com.arteen.rackfinder.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Identity of a stored stack: item + NBT. Used to group the same item coming from many racks.
 */
public final class ItemKey {
    private final ItemStack prototype;
    private final String itemId;
    private final int hash;

    private String displayName;
    private String searchText;

    private ItemKey(final ItemStack prototype) {
        this.prototype = prototype.copyWithCount(1);
        this.itemId = BuiltInRegistries.ITEM.getKey(this.prototype.getItem()).toString();
        final CompoundTag tag = this.prototype.getTag();
        // Cheap, stable hash. equals() still compares the real stacks, so collisions are harmless.
        this.hash = this.itemId.hashCode() * 31 + (tag == null ? 0 : tag.size());
    }

    public static ItemKey of(final ItemStack stack) {
        return new ItemKey(stack);
    }

    public static ItemKey of(final String itemId, @Nullable final CompoundTag tag) {
        final Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        final ItemStack stack = new ItemStack(item);
        if (tag != null && !tag.isEmpty()) {
            stack.setTag(tag.copy());
        }
        return new ItemKey(stack);
    }

    public ItemStack prototype() {
        return prototype.copy();
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        if (displayName == null) {
            displayName = prototype.getHoverName().getString();
        }
        return displayName;
    }

    public String searchText() {
        if (searchText == null) {
            searchText = (displayName() + " " + itemId).toLowerCase(Locale.ROOT);
        }
        return searchText;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemKey key)) {
            return false;
        }
        return ItemStack.isSameItemSameTags(prototype, key.prototype);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
