package dev.malik.lcftbhook.adapter.ftb;

import dev.malik.lcftbhook.util.Guard;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLeftPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLoggedInAfterTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerTransferredTeamOwnershipEvent;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.malik.lcftbhook.service.TeamChangeHooks;
import dev.malik.lcftbhook.service.TeamLifecycleService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;


/**
 * Translates FTB Teams' team lifecycle events into this mod's backend-neutral
 * {@link TeamLifecycleService} / {@link TeamChangeHooks}, and keeps FTB Chunks'
 * claim visibility locked (see {@link FtbClaimVisibility}).
 */
public class FtbTeamEvents {
    public FtbTeamEvents() {
        TeamEvent.CREATED.register(event -> Guard.run("FTB team event CREATED", () -> onTeamCreated(event)));
        TeamEvent.LOADED.register(event -> Guard.run("FTB team event LOADED", () -> onTeamLoaded(event)));
        TeamEvent.DELETED.register(event -> Guard.run("FTB team event DELETED", () -> onTeamDeleted(event)));
        TeamEvent.PLAYER_JOINED_PARTY.register(event -> Guard.run("FTB team event PLAYER_JOINED_PARTY", () -> onPlayerJoinedParty(event)));
        TeamEvent.PLAYER_LEFT_PARTY.register(event -> Guard.run("FTB team event PLAYER_LEFT_PARTY", () -> onPlayerLeftParty(event)));
        TeamEvent.OWNERSHIP_TRANSFERRED.register(event -> Guard.run("FTB team event OWNERSHIP_TRANSFERRED", () -> onOwnershipTransferred(event)));
        TeamEvent.PLAYER_CHANGED.register(event -> Guard.run("FTB team event PLAYER_CHANGED", () -> onPlayerChanged(event)));
        TeamEvent.PLAYER_LOGGED_IN.register(event -> Guard.run("FTB team event PLAYER_LOGGED_IN", () -> onPlayerLoggedInAfterTeam(event)));
    }

    private void onTeamCreated(TeamCreatedEvent event) {
        FtbClaimVisibility.ensurePublic(event.getTeam());
        teamChanged(event.getTeam());
    }

    /** Fired by FTB Teams while it reads its team files at startup - see {@link #teamChanged}. */
    private void onTeamLoaded(TeamEvent event) {
        try {
            FtbClaimVisibility.ensurePublic(event.getTeam());
        } catch (RuntimeException e) {
            dev.malik.lcftbhook.LCFtbHook.LOGGER.error("Claim visibility check failed for loaded team {}", event.getTeam().getTeamId(), e);
        }
        teamChanged(event.getTeam());
    }

    private void onTeamDeleted(TeamEvent event) {
        MinecraftServer server = activeServer();
        if (server != null) {
            TeamLifecycleService.onTeamDeleted(server, event.getTeam().getTeamId());
        }
    }

    private void onPlayerJoinedParty(PlayerJoinedPartyTeamEvent event) {
        teamChanged(event.getTeam());
    }

    private void onPlayerLeftParty(PlayerLeftPartyTeamEvent event) {
        MinecraftServer server = activeServer();
        if (server != null && event.getTeamDeleted()) {
            Team party = event.getTeam();
            TeamLifecycleService.onPartyDisbanded(server, party.getTeamId(), FtbPartyOwners.effectiveOwner(party), FtbChunkKeys.claimedChunksOf(party), event.getPlayer());
        } else {
            teamChanged(event.getTeam());
        }
        teamChanged(event.getPlayerTeam());
    }

    private void onOwnershipTransferred(PlayerTransferredTeamOwnershipEvent event) {
        teamChanged(event.getTeam());
    }

    private void onPlayerChanged(PlayerChangedTeamEvent event) {
        teamChanged(event.getTeam());
        event.getPreviousTeam().ifPresent(this::teamChanged);
    }

    private void onPlayerLoggedInAfterTeam(PlayerLoggedInAfterTeamEvent event) {
        MinecraftServer server = activeServer();
        if (server != null && event.getPlayer() != null) {
            TeamLifecycleService.onPlayerReady(server, event.getPlayer());
        }
    }

    private void teamChanged(Team team) {
        MinecraftServer server = activeServer();
        if (server == null || team == null || !team.isValid()) {
            return;
        }
        // Our hook must never break FTB itself: an exception escaping here
        // during FTB's team loading aborted the load of every remaining team.
        try {
            TeamChangeHooks.onTeamChanged(server, team.getTeamId());
        } catch (RuntimeException e) {
            dev.malik.lcftbhook.LCFtbHook.LOGGER.error("Team change hook failed for {}", team.getTeamId(), e);
        }
    }

    private static MinecraftServer activeServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && FtbIntegration.isActive() ? server : null;
    }
}
