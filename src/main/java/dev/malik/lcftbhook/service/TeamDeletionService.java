package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/**
 * Central point for all cleanup that must happen when a team ceases to exist
 * (in whichever claim backend). Every code path that handles team deletion -
 * backend event listeners, startup reconcile, disband settlement - goes
 * through here so nothing is left behind. Works purely on the team id: the
 * backend's own team object may already be gone.
 * <p>
 * Financial settlement (chunk refunds, balance transfer to the owner) is
 * {@link PartyDisbandSettlementService}'s job and must happen BEFORE this.
 */
public final class TeamDeletionService {
    private TeamDeletionService() {
    }

    /**
     * Dissolves all war links (refreshing partner upkeep), clears pending
     * state, drops the currency backend's mirror of the team (e.g. its LC
     * team), and removes the saved-data entry.
     */
    public static void purge(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        WarService.cleanupTeamWars(server, teamId);
        if (!savedData.getPendingState(teamId).isEmpty()) {
            savedData.setPendingState(teamId, new TeamPendingState());
        }
        Backends.currency().onTeamDeleted(server, teamId);
        if (savedData.removeLink(teamId) != null) {
            LCFtbHook.LOGGER.info("Removed hook data for deleted team {}", teamId);
        }
    }

    /**
     * Purges saved data of teams the claim backend no longer knows, then lets
     * the currency backend repair its own mirrors. Skipped while the claim
     * backend reports no teams at all (still loading at startup).
     */
    public static void reconcile(MinecraftServer server) {
        if (Backends.claim().allTeams(server).isEmpty()) {
            return;
        }
        List<FtbHookSavedData.TeamLinkEntry> entries = List.copyOf(FtbHookSavedData.get(server).getAllLinks());
        List<FtbHookSavedData.TeamLinkEntry> missing = entries.stream()
                .filter(entry -> !Backends.claim().teamExists(server, entry.ftbTeamId()))
                .toList();
        if (MassChangeGuard.looksLikeIncompleteLoad(missing.size(), entries.size())) {
            // Teams are deleted one at a time; many vanishing at once means the
            // claim mod didn't load its data - purging would destroy it for good.
            LCFtbHook.LOGGER.error("Reconcile: {} of {} teams are missing in the claim mod - refusing to purge their data. "
                    + "Check the claim mod's startup log; nothing was deleted.", missing.size(), entries.size());
            return;
        }
        for (FtbHookSavedData.TeamLinkEntry entry : missing) {
            purge(server, entry.ftbTeamId());
            LCFtbHook.LOGGER.info("Reconcile: purged stale hook data for deleted team {}", entry.ftbTeamId());
        }
        Backends.currency().reconcile(server);
    }

}
