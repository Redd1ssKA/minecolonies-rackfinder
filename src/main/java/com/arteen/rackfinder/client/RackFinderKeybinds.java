package com.arteen.rackfinder.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class RackFinderKeybinds {
    public static final KeyMapping OPEN_SEARCH = new KeyMapping(
            "key.rackfinder.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.rackfinder");


    private RackFinderKeybinds() {
    }

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_SEARCH);
    }
}
