package com.arteen.rackfinder.capture;

import com.arteen.rackfinder.RackFinder;
import com.arteen.rackfinder.compat.MineColoniesRackCompat;
import com.arteen.rackfinder.data.ItemKey;
import com.arteen.rackfinder.data.RackSnapshot;
import com.arteen.rackfinder.data.RackStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * When the player opens a MineColonies rack, the server syncs the (fresh) slot contents to the
 * client menu. This class records that snapshot, so racks that were opened once are always up to
 * date - even if the chunk was loaded long ago and its block entity NBT is stale.
 *
 * <p>No MineColonies classes are referenced; the menu is recognised by its class name and the rack
 * block entities are read from its public fields.</p>
 */
public final class RackGuiCapture {
    private static final String RACK_MENU_SUFFIX = "ContainerRack";
    private static final int MIN_OPEN_TICKS = 3;

    private static AbstractContainerMenu lastMenu;
    private static int lastHash;
    private static int openTicks;

    private RackGuiCapture() {
    }

    public static void tick(final Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        final AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == null || !isRackMenu(menu)) {
            lastMenu = null;
            openTicks = 0;
            return;
        }
        if (menu != lastMenu) {
            lastMenu = menu;
            lastHash = 0;
            openTicks = 0;
        }
        openTicks++;
        if (openTicks < MIN_OPEN_TICKS) {
            return;
        }
        final int hash = slotsHash(menu);
        if (hash == lastHash) {
            return;
        }
        lastHash = hash;
        capture(minecraft, menu);
    }

    public static boolean isRackMenu(final AbstractContainerMenu menu) {
        if (menu == null) {
            return false;
        }
        final String name = menu.getClass().getName();
        return name.endsWith(RACK_MENU_SUFFIX) && name.startsWith(MineColoniesRackCompat.MOD_PACKAGE);
    }

    private static void capture(final Minecraft minecraft, final AbstractContainerMenu menu) {
        try {
            final Object rackObject = readField(menu, "rack");
            if (!(rackObject instanceof BlockEntity rack)) {
                return;
            }
            final Object neighborObject = readField(menu, "neighborRack");
            final BlockEntity neighbor = neighborObject instanceof BlockEntity blockEntity ? blockEntity : null;

            final int rackSlots = slotsOf(rack);
            final int neighborSlots = neighbor == null ? 0 : slotsOf(neighbor);
            final int totalSlots = rackSlots + neighborSlots;
            if (totalSlots <= 0) {
                return;
            }

            final boolean neighborFirst = neighbor != null && isEmptyAir(neighbor);
            final int firstSlots = neighborFirst ? neighborSlots : rackSlots;

            final Map<ItemKey, Integer> rackContents = new LinkedHashMap<>();
            final Map<ItemKey, Integer> neighborContents = new LinkedHashMap<>();
            int rackFilled = 0;
            int neighborFilled = 0;

            final int limit = Math.min(totalSlots, menu.slots.size());
            for (int slot = 0; slot < limit; slot++) {
                final ItemStack stack = menu.getSlot(slot).getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                final boolean belongsToRack = neighborFirst ? slot >= firstSlots : slot < firstSlots;
                if (belongsToRack) {
                    rackContents.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
                    rackFilled++;
                } else {
                    neighborContents.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
                    neighborFilled++;
                }
            }

            final var dimension = minecraft.level == null ? null : minecraft.level.dimension().location();
            if (dimension == null) {
                return;
            }
            final BlockPos rackPos = rack.getBlockPos();
            RackStore.INSTANCE.put(dimension, rackPos,
                    new RackSnapshot(rackContents, rackFilled, MineColoniesRackCompat.isWarehouseRack(rack), true),
                    "gui", neighbor != null, blockId(rack));

            if (neighbor != null) {
                RackStore.INSTANCE.put(dimension, neighbor.getBlockPos(),
                        new RackSnapshot(neighborContents, neighborFilled, MineColoniesRackCompat.isWarehouseRack(neighbor), true),
                        "gui", true, blockId(neighbor));
            }
            RackStore.INSTANCE.markDirty();
        } catch (final ReflectiveOperationException | RuntimeException e) {
            RackFinder.LOGGER.debug("[rackfinder] could not capture rack GUI contents", e);
        }
    }

    private static String blockId(final BlockEntity blockEntity) {
        return BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
    }

    private static int slotsOf(final BlockEntity blockEntity) throws ReflectiveOperationException {
        final Method method = blockEntity.getClass().getMethod("getInventory");
        final Object inventory = method.invoke(blockEntity);
        if (inventory instanceof IItemHandler handler) {
            return handler.getSlots();
        }
        return 0;
    }

    private static Object readField(final AbstractContainerMenu menu, final String name) throws ReflectiveOperationException {
        final Field field = menu.getClass().getField(name);
        return field.get(menu);
    }

    /**
     * A rack merged with a neighbour is displayed with the {@code EMPTYAIR} variant first.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isEmptyAir(final BlockEntity blockEntity) {
        final BlockState state = blockEntity.getBlockState();
        for (final Property<?> property : state.getProperties()) {
            if (!"variant".equals(property.getName())) {
                continue;
            }
            final Object value = state.getValue((Property) property);
            return value != null && "EMPTYAIR".equals(value.toString().toUpperCase(Locale.ROOT));
        }
        return false;
    }

    private static int slotsHash(final AbstractContainerMenu menu) {
        int hash = 1;
        final int limit = Math.min(menu.slots.size(), 128);
        for (int slot = 0; slot < limit; slot++) {
            final ItemStack stack = menu.getSlot(slot).getItem();
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getCount() * 31 + stack.getItem().hashCode());
        }
        return hash;
    }
}
