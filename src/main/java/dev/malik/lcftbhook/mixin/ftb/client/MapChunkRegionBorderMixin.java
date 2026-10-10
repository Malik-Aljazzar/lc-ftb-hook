package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

/**
 * FTB Chunks' own map draws a border wherever {@code connects()} is false
 * and fills with the team's color otherwise. Regions are visually separated
 * by forcing a border between two same-team chunks that sit in different
 * Regions (both null - i.e. both implicitly the team's Default region -
 * still counts as "same", matching the not-yet-explicitly-assigned case).
 */
@Mixin(value = MapChunk.class, remap = false)
public abstract class MapChunkRegionBorderMixin {
    @Inject(method = "connects", at = @At("RETURN"), cancellable = true, remap = false)
    private void lcFtbHook$separateRegions(MapChunk other, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        MapChunk self = (MapChunk) (Object) this;
        String selfKey = lcFtbHook$key(self);
        String otherKey = lcFtbHook$key(other);
        if (selfKey == null || otherKey == null) {
            return;
        }
        UUID selfRegion = ClientRegionMembership.rawRegionOf(selfKey);
        UUID otherRegion = ClientRegionMembership.rawRegionOf(otherKey);
        if (!Objects.equals(selfRegion, otherRegion)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * {@code MapChunk.getPos()} is LOCAL to its 32x32 MapRegion tile
     * (confirmed from {@code getActualPos()}'s bytecode: {@code region.pos *
     * 32 + pos}) - using it directly here only coincidentally worked for
     * claims sitting inside the MapRegion tile whose own grid origin is
     * (0,0), and produced a bogus key (and therefore a wrongly-forced
     * border, since the sparse chunk-&gt;region map is keyed by real
     * absolute coordinates) for anything crossing a MapRegion tile boundary.
     * {@code getActualPos()} is the real absolute chunk position.
     */
    private static String lcFtbHook$key(MapChunk chunk) {
        var mapRegion = ((MapChunkAccessor) (Object) chunk).lcFtbHook$getMapRegion();
        if (mapRegion == null || mapRegion.dimension == null) {
            return null;
        }
        var pos = chunk.getActualPos();
        return ChunkPosKey.encode(mapRegion.dimension.dimension.location(), pos.x(), pos.z());
    }
}
