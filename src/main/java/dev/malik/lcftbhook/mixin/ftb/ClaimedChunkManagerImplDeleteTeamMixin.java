package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.adapter.ftb.FtbChunkKeys;
import dev.malik.lcftbhook.adapter.ftb.FtbIntegration;
import dev.malik.lcftbhook.service.TeamLifecycleService;
import dev.malik.lcftbhook.service.WarService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Settles a party (refunds, money to the owner) right before FTB Chunks
 * deletes its claim data - after that its chunks would be gone.
 */
@Mixin(value = ClaimedChunkManagerImpl.class, remap = false)
public class ClaimedChunkManagerImplDeleteTeamMixin {
    @Inject(method = "deleteTeam", at = @At("HEAD"), remap = false)
    private void lcFtbHook$settleBeforeDelete(Team team, CallbackInfo ci) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (team == null || server == null || !FtbIntegration.isActive()) {
            return;
        }
        try {
            if (team.isValid() && team.isPartyTeam()) {
                TeamLifecycleService.onPartyDisbanded(server, team.getTeamId(), team.getOwner(), FtbChunkKeys.claimedChunksOf(team), null);
            } else {
                WarService.cleanupTeamWars(server, team.getId());
            }
        } catch (Throwable error) {
            LCFtbHook.LOGGER.error("Party disband settlement failed for {} - FTB party deletion will continue", team.getId(), error);
        }
    }
}
