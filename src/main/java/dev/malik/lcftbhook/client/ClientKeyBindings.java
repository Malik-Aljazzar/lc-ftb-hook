package dev.malik.lcftbhook.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** The key (default N) that opens this mod's main menu - works with any claim mod. */
public final class ClientKeyBindings {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.lc_ftb_hook.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.lc_ftb_hook");

    private ClientKeyBindings() {
    }

    /** Client only, from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_MENU));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            while (OPEN_MENU.consumeClick()) {
                if (Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) {
                    ScreenOpener.openMainMenu();
                }
            }
        });
    }
}
