package dev.malik.lcftbhook.client.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

import javax.annotation.Nullable;

/** The dimension Xaero's World Map is currently showing (can differ from the player's own). */
final class XaeroMapDimension {
    private XaeroMapDimension() {
    }

    @Nullable
    static ResourceKey<Level> current() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session != null && session.isUsable() ? session.getMapProcessor() : null;
        ResourceKey<Level> shown = processor != null && processor.getMapWorld() != null
                ? processor.getMapWorld().getCurrentDimensionId() : null;
        if (shown != null) {
            return shown;
        }
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.dimension() : null;
    }
}
