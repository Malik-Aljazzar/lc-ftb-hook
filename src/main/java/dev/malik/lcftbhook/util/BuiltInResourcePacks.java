package dev.malik.lcftbhook.util;

import dev.malik.lcftbhook.LCFtbHook;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Always-on client resource packs shipped inside this mod
 * ({@code resourcepacks/<name>}), placed above every mod's own resources so
 * they can add to or override another mod's assets. Callers register them
 * only when the mod whose assets they touch is installed.
 */
public final class BuiltInResourcePacks {
    private BuiltInResourcePacks() {
    }

    public static void register(IEventBus modEventBus, String name, String title) {
        modEventBus.addListener((AddPackFindersEvent event) -> event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "resourcepacks/" + name), PackType.CLIENT_RESOURCES,
                Component.literal("LC FTB Hook: " + title), PackSource.BUILT_IN, true, Pack.Position.TOP));
    }
}
