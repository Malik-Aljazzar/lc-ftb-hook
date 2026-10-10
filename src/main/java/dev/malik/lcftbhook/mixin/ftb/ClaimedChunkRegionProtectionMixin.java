package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.service.RegionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every claimed chunk now belongs to a Region (the auto-created Default
 * catches unassigned ones), and explosions/mob griefing are read from that
 * Region's own stored value (further loosened by a private owner's
 * marketplace override, if any) instead of a flat team-wide build/land split.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl", remap = false)
public abstract class ClaimedChunkRegionProtectionMixin {
    @Inject(method = "allowExplosions", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionExplosions(CallbackInfoReturnable<Boolean> cir) {
        // Guarded: if our lookup fails, FTB's own setting stands.
        ClaimedChunk chunk = (ClaimedChunk) (Object) this;
        Boolean allowed = dev.malik.lcftbhook.util.Guard.call("FTB allow_explosions check", () -> {
            if (lcFtbHook$isServerTeamChunk(chunk)) {
                return null; // FTB's own server team setting applies
            }
            Region region = dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveRegion(chunk);
            ChunkOwnership ownership = dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveOwnership(chunk);
            return ProtectionResolution.isAtMinimum(region, ownership, ProtectionProperty.byId(dev.malik.lcftbhook.adapter.ftb.FtbProtectionFields.ALLOW_EXPLOSIONS));
        }, null);
        if (allowed != null) {
            cir.setReturnValue(allowed);
        }
    }

    @Inject(method = "allowMobGriefing", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionMobGriefing(CallbackInfoReturnable<Boolean> cir) {
        // Guarded: if our lookup fails, FTB's own setting stands.
        ClaimedChunk chunk = (ClaimedChunk) (Object) this;
        Boolean allowed = dev.malik.lcftbhook.util.Guard.call("FTB allow_mob_griefing check", () -> {
            if (lcFtbHook$isServerTeamChunk(chunk)) {
                return null;
            }
            Region region = dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveRegion(chunk);
            ChunkOwnership ownership = dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveOwnership(chunk);
            return ProtectionResolution.isAtMinimum(region, ownership, ProtectionProperty.byId(dev.malik.lcftbhook.adapter.ftb.FtbProtectionFields.ALLOW_MOB_GRIEFING));
        }, null);
        if (allowed != null) {
            cir.setReturnValue(allowed);
        }
    }

    private static boolean lcFtbHook$isServerTeamChunk(ClaimedChunk chunk) {
        dev.ftb.mods.ftbteams.api.Team team = chunk.getTeamData().getTeam();
        return team != null && team.isServerTeam();
    }
}
