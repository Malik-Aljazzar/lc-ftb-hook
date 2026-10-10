package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbchunks.data.PvPMode;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PvP is resolved from the chunk's own Region now (uniformly, not just for
 * a "land" special case) unless the server's global PvP mode forbids it
 * entirely regardless of protection settings.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.FTBChunks", remap = false)
public abstract class FTBChunksPvPMixin {
    @Inject(method = "isPvPProtectedChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$regionPvp(PvPMode mode, Player player, CallbackInfoReturnable<Boolean> cir) {
        // Guarded: if our lookup fails, FTB's own PvP rule stands.
        dev.malik.lcftbhook.util.Guard.run("FTB PvP check", () -> lcFtbHook$applyRegionPvp(mode, player, cir));
    }

    private static void lcFtbHook$applyRegionPvp(PvPMode mode, Player player, CallbackInfoReturnable<Boolean> cir) {
        ClaimedChunk cc = ClaimedChunkManagerImpl.getInstance()
                .getChunk(new ChunkDimPos(player.level(), player.blockPosition()));
        if (cc == null || cc.getTeamData().getTeam() == null || cc.getTeamData().getTeam().isServerTeam()) {
            return; // unclaimed, or a server team's chunk: FTB's own PvP rule applies
        }
        if (mode == PvPMode.NEVER) {
            cir.setReturnValue(true);
            return;
        }
        Region region = dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveRegion(cc);
        ChunkOwnership ownership = dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveOwnership(cc);
        if (!ProtectionResolution.isAtMinimum(region, ownership, ProtectionProperty.byId(dev.malik.lcftbhook.adapter.ftb.FtbProtectionFields.ALLOW_PVP))) {
            cir.setReturnValue(true);
        }
    }
}
