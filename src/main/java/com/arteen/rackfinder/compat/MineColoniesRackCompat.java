package com.arteen.rackfinder.compat;

import com.arteen.rackfinder.data.ItemKey;
import com.arteen.rackfinder.data.RackSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything version specific lives here. MineColonies is not a compile dependency:
 * racks are recognised by their block entity class name / block id and their content is read
 * from the block entity NBT that MineColonies writes (tags "inventory", "empty", "inWarehouse").
 */
public final class MineColoniesRackCompat {
    public static final String MOD_ID = "minecolonies";
    public static final String MOD_PACKAGE = "com.minecolonies.";

    private static final String RACK_CLASS_SUFFIX = "TileEntityRack";

    private static final String TAG_INVENTORY = "inventory";
    private static final String TAG_EMPTY = "empty";
    private static final String TAG_IN_WAREHOUSE = "inWarehouse";

    private MineColoniesRackCompat() {
    }

    public static boolean isRack(@Nullable final BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null) {
            return false;
        }
        final String className = blockEntity.getClass().getName();
        if (className.startsWith(MOD_PACKAGE)) {
            return className.endsWith(RACK_CLASS_SUFFIX);
        }
        // Cheap fallback for relocated / shaded block entity classes.
        final String blockClass = blockEntity.getBlockState().getBlock().getClass().getName();
        return blockClass.startsWith(MOD_PACKAGE) && className.endsWith(RACK_CLASS_SUFFIX);
    }

    public static RackSnapshot read(final BlockEntity blockEntity) {
        try {
            return read(blockEntity.saveWithoutMetadata());
        } catch (final RuntimeException e) {
            return RackSnapshot.unknown();
        }
    }

    public static RackSnapshot read(final CompoundTag tag) {
        final boolean warehouse = tag.getBoolean(TAG_IN_WAREHOUSE);
        if (!tag.contains(TAG_INVENTORY)) {
            return new RackSnapshot(Collections.emptyMap(), 0, warehouse, false);
        }
        final ListTag inventory = tag.getList(TAG_INVENTORY, Tag.TAG_COMPOUND);
        final Map<ItemKey, Integer> contents = new LinkedHashMap<>();
        int filledSlots = 0;
        for (int index = 0; index < inventory.size(); index++) {
            final CompoundTag entry = inventory.getCompound(index);
            if (entry.getBoolean(TAG_EMPTY)) {
                continue;
            }
            final ItemStack stack = ItemStack.of(entry);
            if (stack.isEmpty()) {
                continue;
            }
            filledSlots++;
            contents.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
        }
        return new RackSnapshot(contents, filledSlots, warehouse, true);
    }

    /**
     * Racks are placed in pairs; a rack touching another rack forms a "double rack".
     */
    public static boolean isDoubleRack(final BlockGetter level, final BlockPos pos) {
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            if (isRack(level.getBlockEntity(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWarehouseRack(final BlockEntity blockEntity) {
        try {
            return blockEntity.saveWithoutMetadata().getBoolean(TAG_IN_WAREHOUSE);
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
