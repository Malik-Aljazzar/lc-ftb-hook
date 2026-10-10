package dev.malik.lcftbhook.adapter.ftb;

import dev.malik.lcftbhook.util.Guard;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.service.ProtectionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * FTB Chunks' own "claim visibility" team setting is force-locked to Public
 * (config {@code lockClaimVisibilityPublic}) so wars/marketplace targeting
 * can't be dodged by hiding claims on the map. FTB-specific - OPAC has no
 * equivalent per-team visibility switch.
 */
public final class FtbClaimVisibility {
    public static final String PROPERTY_KEY = "claim_visibility";

    private FtbClaimVisibility() {
    }

    public static void register() {
        TeamEvent.PROPERTIES_CHANGED.register(event -> Guard.run("FTB team properties changed", () -> onTeamPropertiesChanged(event)));
    }

    private static void onTeamPropertiesChanged(TeamPropertiesChangedEvent event) {
        Team team = event.getTeam();
        if (!FtbIntegration.isActive() || !LCFtbHookConfig.SERVER.lockClaimVisibilityPublic.get() || !team.isValid()) {
            return;
        }
        if (team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            ensurePublic(team);
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                // Officers/owner only - region/land management is off-limits to a plain member.
                ProtectionService.notifyTeamManagers(server, team.getTeamId(),
                        Component.translatable("message.lc_ftb_hook.claim_visibility_locked"));
            }
        }
    }

    public static void ensurePublic(Team team) {
        if (team == null || !team.isValid() || !LCFtbHookConfig.SERVER.lockClaimVisibilityPublic.get()) {
            return;
        }
        if (team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            team.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
            team.syncOnePropertyToTeam(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
        }
    }
}
