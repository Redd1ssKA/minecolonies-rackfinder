package com.arteen.rackfinder.client;

import com.arteen.rackfinder.RackFinder;
import com.arteen.rackfinder.capture.ContainerCapture;
import com.arteen.rackfinder.capture.RackGuiCapture;
import com.arteen.rackfinder.client.gui.RackSearchScreen;
import com.arteen.rackfinder.config.RackFinderConfig;
import com.arteen.rackfinder.data.ItemAggregate;
import com.arteen.rackfinder.data.RackHit;
import com.arteen.rackfinder.data.RackRecord;
import com.arteen.rackfinder.data.RackStore;
import com.arteen.rackfinder.scan.RackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RackFinder.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static String lastWorldKey = "unknown";
    private static long highlightRevision = -1;
    private static int ticksAfterJoin;
    private static boolean summarySent;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ticksAfterJoin = 0;
            summarySent = false;
            return;
        }

        final String worldKey = RackStore.currentWorldKey(minecraft);
        if (!"unknown".equals(worldKey) && !worldKey.equals(lastWorldKey)) {
            lastWorldKey = worldKey;
            RackStore.INSTANCE.setWorldKey(worldKey);
            RackScanner.requestRescan();
        }

        while (RackFinderKeybinds.OPEN_SEARCH.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new RackSearchScreen());
            }
        }

        RackScanner.tick(minecraft);
        RackGuiCapture.tick(minecraft);
        ContainerCapture.tick(minecraft);
        RackStore.INSTANCE.tick();
        refreshTargetHighlight(minecraft);

        ticksAfterJoin++;
        if (!summarySent && ticksAfterJoin == 80 && RackFinderConfig.CHAT_FEEDBACK.get()) {
            summarySent = true;
            final int racks = RackStore.INSTANCE.rackCount();
            final int withData = RackStore.INSTANCE.documentedRackCount();
            if (racks > 0) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "rackfinder.chat.indexed", racks, withData, RackStore.INSTANCE.itemCount()), false);
            }
        }
    }

    /**
     * Keeps the outline in sync with the index: when a new container is captured (or contents change),
     * the boxes are rebuilt for the target item. Skipped while the player isolated a single container.
     */
    private static void refreshTargetHighlight(final Minecraft minecraft) {
        if (!TargetItem.isSet() || HighlightState.isIsolated()) {
            return;
        }
        final RackStore store = RackStore.INSTANCE;
        if (store.revision() == highlightRevision) {
            return;
        }
        highlightRevision = store.revision();
        final ItemAggregate aggregate = store.find(TargetItem.get());
        if (aggregate == null || aggregate.rackCount() == 0) {
            HighlightState.clear();
            return;
        }
        final List<RackRecord> records = new ArrayList<>(aggregate.rackCount());
        for (final RackHit hit : store.hits(aggregate, minecraft.player)) {
            records.add(hit.record());
        }
        HighlightState.highlight(records, aggregate.key().displayName());
    }

    @SubscribeEvent
    public static void onLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event) {
        ticksAfterJoin = 0;
        summarySent = false;
        HighlightState.clear();
    }

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        ContainerCapture.onRightClickBlock(event);
    }

    @SubscribeEvent
    public static void onContainerForeground(final ContainerScreenEvent.Render.Foreground event) {
        SlotHighlighter.onForeground(event);
    }

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        RackStore.INSTANCE.save();
        TargetItem.clear();
        HighlightState.clear();
        RackScanner.requestRescan();
        lastWorldKey = "unknown";
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(final RegisterClientCommandsEvent event) {
        ClientCommands.register(event);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ProjectionCache.capture(event);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        HighlightRenderer.render(event.getGuiGraphics());
    }
}
