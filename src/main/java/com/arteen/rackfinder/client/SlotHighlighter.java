package com.arteen.rackfinder.client;

import com.arteen.rackfinder.config.RackFinderConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ContainerScreenEvent;

import javax.annotation.Nullable;

/**
 * Frames slots that hold the item picked in the search window. Works in the MineColonies rack
 * window as well as in any other container GUI.
 *
 * <p>The foreground event fires after the slots but before the tooltip, so tooltips stay readable.
 * The pose stack is already translated to the GUI origin at that point, exactly like vanilla
 * {@code renderSlotHighlight} does it.</p>
 */
public final class SlotHighlighter {
    private static final int BORDER_COLOR = 0xFF00E676;
    private static final int FILL_COLOR = 0x4400E676;

    private SlotHighlighter() {
    }

    public static void onForeground(final ContainerScreenEvent.Render.Foreground event) {
        if (!RackFinderConfig.SLOT_HIGHLIGHT.get() || !TargetItem.isSet()) {
            return;
        }

        final AbstractContainerScreen<?> screen = event.getContainerScreen();
        final GuiGraphics graphics = event.getGuiGraphics();
        for (final Slot slot : screen.getMenu().slots) {
            if (slot.x < 0 || slot.y < 0) {
                continue;
            }
            final ItemStack stack = slot.getItem();
            if (TargetItem.matches(stack)) {
                drawSlotFrame(graphics, slot.x, slot.y);
            }
        }
    }

    private static void drawSlotFrame(final GuiGraphics graphics, final int x, final int y) {
        graphics.fill(x, y, x + 16, y + 16, FILL_COLOR);
        graphics.fill(x - 1, y - 1, x + 17, y, BORDER_COLOR);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, BORDER_COLOR);
        graphics.fill(x - 1, y, x, y + 16, BORDER_COLOR);
        graphics.fill(x + 16, y, x + 17, y + 16, BORDER_COLOR);
    }
}
