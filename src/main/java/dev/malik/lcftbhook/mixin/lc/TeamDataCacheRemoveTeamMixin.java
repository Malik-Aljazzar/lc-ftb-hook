package dev.malik.lcftbhook.mixin.lc;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.adapter.lightmanscurrency.LcTeamDeletionGuard;
import dev.malik.lcftbhook.adapter.lightmanscurrency.LcTeamLinks;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TeamDataCache.class, remap = false)
public class TeamDataCacheRemoveTeamMixin {
    @Inject(method = "removeTeam", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$guardManagedTeamRemoval(long id, CallbackInfo ci) {
        if (LcTeamDeletionGuard.isAllowed()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        // Guarded: if we can't tell whether the team belongs to a party, it is NOT removed.
        if (!dev.malik.lcftbhook.util.Guard.call("LC team removal check", () -> LcTeamLinks.isManaged(server, id), true)) {
            return;
        }

        ITeam team = TeamAPI.getApi().GetTeam(false, id);
        if (team != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (team.isOwner(player)) {
                    player.displayClientMessage(Component.translatable("message.lc_ftb_hook.team_disband_denied"), true);
                    break;
                }
            }
        }

        LCFtbHook.LOGGER.debug("Blocked removal of LC team {} while FTB party link is active", id);
        ci.cancel();
    }

    @Inject(method = "removeTeam", at = @At("TAIL"), remap = false)
    private void lcFtbHook$cleanupLinkAfterRemoval(long id, CallbackInfo ci) {
        if (LcTeamDeletionGuard.isAllowed()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        dev.malik.lcftbhook.util.Guard.run("LC team link cleanup", () -> {
            if (LcTeamLinks.findByLcTeamId(server, id) != null) {
                LcTeamLinks.unlink(server, id);
            }
        });
    }
}
