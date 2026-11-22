package com.arteen.rackfinder.dev;

import com.arteen.rackfinder.RackFinder;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only harness: with {@code -Drackfinder.selftest=true} the dedicated server runs the parser
 * self-test right after startup and stops. The whole dev package is excluded from the built jar.
 */
@Mod.EventBusSubscriber(modid = RackFinder.MOD_ID)
public final class SelfTestHook {
    private SelfTestHook() {
    }

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        if (!Boolean.getBoolean("rackfinder.selftest")) {
            return;
        }
        final int failures = SelfTest.run();
        RackFinder.LOGGER.info("[rackfinder] self-test failures: {}", failures);
        event.getServer().halt(false);
    }
}
