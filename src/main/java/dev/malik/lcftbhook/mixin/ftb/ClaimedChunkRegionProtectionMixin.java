package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.malik.lcftbhook.adapter.ftb.FtbChunkProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every claimed chunk now belongs to a Region (the auto-created Default
 * catches unassigned ones), and explosions/mob griefing are read from that
 * Region's own stored value (further loosened by a private owner's
 * marketplace override, if any) instead of a flat team-wide build/land split.
 * The lookup itself lives in {@link FtbChunkProtection}, shared with the
 * block-change guard for other mods' direct block changes.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl", remap = false)
public abstract class ClaimedChunkRegionProtectionMixin {
    @Inject(method = "allowExplosions", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionExplosions(CallbackInfoReturnable<Boolean> cir) {
        // Guarded: if our lookup fails, FTB's own setting stands.
        ClaimedChunk chunk = (ClaimedChunk) (Object) this;
        Boolean allowed = dev.malik.lcftbhook.util.Guard.call("FTB allow_explosions check",
                () -> FtbChunkProtection.regionAllowsExplosions(chunk), null);
        if (allowed != null) {
            cir.setReturnValue(allowed);
        }
    }

    @Inject(method = "allowMobGriefing", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionMobGriefing(CallbackInfoReturnable<Boolean> cir) {
        // Guarded: if our lookup fails, FTB's own setting stands.
        ClaimedChunk chunk = (ClaimedChunk) (Object) this;
        Boolean allowed = dev.malik.lcftbhook.util.Guard.call("FTB allow_mob_griefing check",
                () -> FtbChunkProtection.regionAllowsMobGriefing(chunk), null);
        if (allowed != null) {
            cir.setReturnValue(allowed);
        }
    }
}
