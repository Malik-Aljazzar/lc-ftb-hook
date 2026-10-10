package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.FTBChunks;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.malik.lcftbhook.service.PartyJoinClaimSettlementService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FTBChunks.class, remap = false)
public class FTBChunksPlayerJoinedPartyMixin {
    @Inject(method = "playerJoinedParty", at = @At("HEAD"), remap = false)
    private void lcFtbHook$dissolvePersonalClaims(PlayerJoinedPartyTeamEvent event, CallbackInfo ci) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        Team previousTeam = event.getPreviousTeam();
        if (previousTeam == null || previousTeam.isPartyTeam()) {
            return;
        }

        dev.malik.lcftbhook.util.Guard.run("FTB party join settlement", () -> PartyJoinClaimSettlementService.settle(player.server, player, previousTeam.getId()));
    }
}
