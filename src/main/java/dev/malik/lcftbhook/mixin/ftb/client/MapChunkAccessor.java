package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MapChunk.class, remap = false)
public interface MapChunkAccessor {
    @Accessor("region")
    MapRegion lcFtbHook$getMapRegion();
}
