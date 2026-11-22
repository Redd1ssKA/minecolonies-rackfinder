package com.arteen.rackfinder.dev;

import com.arteen.rackfinder.RackFinder;
import com.arteen.rackfinder.client.HighlightState;
import com.arteen.rackfinder.client.ProjectionCache;
import com.arteen.rackfinder.client.gui.RackSearchScreen;
import com.arteen.rackfinder.data.ItemKey;
import com.arteen.rackfinder.data.RackRecord;
import com.arteen.rackfinder.data.RackSnapshot;
import com.arteen.rackfinder.data.RackStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev-only client smoke test: {@code -Drackfinder.uitest=true} injects fake racks, opens the search
 * screen, renders it for a few seconds and exits. Excluded from the built jar.
 */
@Mod.EventBusSubscriber(modid = RackFinder.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {
    private static int ticks;
    private static boolean injected;
    private static boolean guiOpened;
    private static boolean selected;

    private ClientSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("rackfinder.uitest")) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ticks++;

        if (ticks == 40 && !injected) {
            injected = true;
            inject(minecraft);
        }
        if (ticks == 60 && !guiOpened) {
            guiOpened = true;
            minecraft.setScreen(new RackSearchScreen());
            RackFinder.LOGGER.info("[uitest] search screen opened");
        }
        if (ticks == 90 && !selected && minecraft.screen instanceof RackSearchScreen screen) {
            selected = screen.selectFirstResult();
            RackFinder.LOGGER.info("[uitest] selectFirstResult = {}", selected);
        }
        if (ticks >= 140) {
            RackFinder.LOGGER.info("[uitest] racks={} items={} highlights={} projectionValid={} selected={}",
                    RackStore.INSTANCE.rackCount(), RackStore.INSTANCE.itemCount(),
                    HighlightState.size(), ProjectionCache.valid(), selected);
            RackFinder.LOGGER.info("[uitest] PASS");
            System.exit(0);
        }
    }

    private static void inject(final Minecraft minecraft) {
        RackStore.INSTANCE.setWorldKey("uitest");
        final ResourceLocation dimension = minecraft.level.dimension().location();
        final BlockPos base = minecraft.player.blockPosition();

        final List<RackRecord> highlighted = new ArrayList<>();
        final ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        final ItemKey bread = ItemKey.of(new ItemStack(Items.BREAD));
        final ItemKey emerald = ItemKey.of(new ItemStack(Items.EMERALD));

        final Map<ItemKey, Integer> first = new LinkedHashMap<>();
        first.put(planks, 64);
        first.put(emerald, 12);
        RackStore.INSTANCE.put(dimension, base.east(3), new RackSnapshot(first, 2, true, true), "uitest", true);

        final Map<ItemKey, Integer> second = new LinkedHashMap<>();
        second.put(planks, 96);
        second.put(bread, 5);
        RackStore.INSTANCE.put(dimension, base.west(5).north(2), new RackSnapshot(second, 2, false, true), "uitest", false);

        final Map<ItemKey, Integer> third = new LinkedHashMap<>();
        third.put(emerald, 3);
        RackStore.INSTANCE.put(dimension, base.south(8).above(2), new RackSnapshot(third, 1, true, true), "uitest", false);

        for (final RackRecord record : RackStore.INSTANCE.all()) {
            if (record.hasData()) {
                highlighted.add(record);
            }
        }
        HighlightState.highlight(highlighted, "Oak Planks");
        RackFinder.LOGGER.info("[uitest] injected {} racks, {} items, {} highlights",
                RackStore.INSTANCE.rackCount(), RackStore.INSTANCE.itemCount(), HighlightState.size());
    }
}
