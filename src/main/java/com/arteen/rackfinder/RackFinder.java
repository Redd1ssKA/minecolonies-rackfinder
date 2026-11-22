package com.arteen.rackfinder;

import com.arteen.rackfinder.config.RackFinderConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * MineColonies Rack Finder - a fully client-side mod that indexes the contents of
 * MineColonies rack blocks from chunks the client already knows about, and lets the
 * player search them with a GUI + through-wall highlight.
 */
@Mod(RackFinder.MOD_ID)
public final class RackFinder {
    public static final String MOD_ID = "rackfinder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RackFinder() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RackFinderConfig.SPEC);
    }
}
