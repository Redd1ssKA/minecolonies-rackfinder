package com.arteen.rackfinder.capture;

import com.arteen.rackfinder.data.ItemKey;
import com.arteen.rackfinder.data.RackSnapshot;
import com.arteen.rackfinder.data.RackStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Indexes vanilla storage containers. Unlike MineColonies racks, vanilla containers do not send
 * their contents to the client, so they are recorded when the player opens them: the last
 * right-clicked block gives the position, the open menu gives the (synced) stacks.
 */
public final class ContainerCapture {
    private static final long INTERACT_TTL_MS = 3000L;

    private static BlockPos lastPos;
    private static long lastPosAt;
    private static AbstractContainerMenu lastMenu;
    private static int lastHash;

    private ContainerCapture() {
    }

    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        lastPos = event.getPos().immutable();
        lastPosAt = System.currentTimeMillis();
        lastMenu = null;
        lastHash = 0;
    }

    public static void tick(final Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            lastMenu = null;
            return;
        }
        final AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == null || !isStorageMenu(menu) || RackGuiCapture.isRackMenu(menu)) {
            lastMenu = null;
            return;
        }
        if (lastPos == null || System.currentTimeMillis() - lastPosAt > INTERACT_TTL_MS) {
            return;
        }
        final int hash = slotsHash(menu);
        if (menu == lastMenu && hash == lastHash) {
            return;
        }
        lastMenu = menu;
        lastHash = hash;
        capture(minecraft, menu);
    }

    /**
     * Vanilla storage menus only; crafting tables, beacons and similar are ignored on purpose.
     */
    private static boolean isStorageMenu(final AbstractContainerMenu menu) {
        return menu instanceof ChestMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu
                || menu instanceof DispenserMenu
                || menu instanceof AbstractFurnaceMenu
                || menu instanceof BrewingStandMenu;
    }

    private static void capture(final Minecraft minecraft, final AbstractContainerMenu menu) {
        final Inventory playerInventory = minecraft.player.getInventory();
        final Map<ItemKey, Integer> contents = new LinkedHashMap<>();
        int filled = 0;
        for (final Slot slot : menu.slots) {
            if (slot.container == playerInventory || slot.container instanceof Inventory) {
                continue;
            }
            final ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            contents.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
            filled++;
        }

        final BlockState state = minecraft.level.getBlockState(lastPos);
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        final boolean doubleChest = state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;

        RackStore.INSTANCE.put(minecraft.level.dimension().location(), lastPos,
                new RackSnapshot(contents, filled, false, true), "gui", doubleChest, blockId.toString());
        RackStore.INSTANCE.markDirty();
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
