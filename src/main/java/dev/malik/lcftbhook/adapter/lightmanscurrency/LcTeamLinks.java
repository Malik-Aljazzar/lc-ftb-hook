package dev.malik.lcftbhook.adapter.lightmanscurrency;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The Lightman's Currency side of the party <-> LC team link: lookups by LC
 * team id (for the LC mixins that stop players from deleting/editing a managed
 * LC team by hand) and startup/login reconciliation of stale links. Party
 * state is only ever read through the active claim backend.
 */
public final class LcTeamLinks {
    private LcTeamLinks() {
    }

    @Nullable
    public static FtbHookSavedData.TeamLinkEntry findByLcTeamId(MinecraftServer server, long lcTeamId) {
        return !FtbHookSavedData.isLcTeamId(lcTeamId) ? null : FtbHookSavedData.get(server).findByLcTeamId(lcTeamId);
    }

    /** An LC team mirrored from a party that still exists and is in use must not be removed or edited by hand. */
    public static boolean isManaged(MinecraftServer server, long lcTeamId) {
        refreshLink(server, lcTeamId);
        FtbHookSavedData.TeamLinkEntry entry = findByLcTeamId(server, lcTeamId);
        return entry != null && isActiveParty(server, entry.ftbTeamId());
    }

    public static void unlink(MinecraftServer server, long lcTeamId) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        FtbHookSavedData.TeamLinkEntry entry = data.findByLcTeamId(lcTeamId);
        if (entry != null && data.clearLcTeamLink(entry.ftbTeamId())) {
            LCFtbHook.LOGGER.info("Unlinked LC team {} from party {}", lcTeamId, entry.ftbTeamId());
        }
    }

    /**
     * Drops links whose LC team vanished or whose party is empty, then makes
     * sure every active party has its LC team. Deleted parties themselves are
     * purged by the core ({@code TeamDeletionService}), not here.
     */
    public static void reconcile(MinecraftServer server) {
        if (LcTeamAccess.cache() == null) {
            return;
        }
        FtbHookSavedData data = FtbHookSavedData.get(server);
        for (FtbHookSavedData.TeamLinkEntry entry : List.copyOf(data.getAllLinks())) {
            if (!FtbHookSavedData.isLcTeamId(entry.lcTeamId())) {
                continue;
            }
            boolean lcTeamGone = TeamAPI.getApi().GetTeam(false, entry.lcTeamId()) == null;
            boolean partyEmpty = Backends.claim().isPartyTeam(server, entry.ftbTeamId())
                    && Backends.claim().members(server, entry.ftbTeamId()).isEmpty();
            if ((lcTeamGone || partyEmpty) && data.clearLcTeamLink(entry.ftbTeamId())) {
                LCFtbHook.LOGGER.info("Cleared stale LC link {} for party {}", entry.lcTeamId(), entry.ftbTeamId());
            }
        }
        for (UUID teamId : Backends.claim().allTeams(server)) {
            LcTeamSync.ensureLinked(server, teamId);
        }
    }

    private static void refreshLink(MinecraftServer server, long lcTeamId) {
        if (Backends.claim().allTeams(server).isEmpty()) {
            return; // claim mod hasn't loaded its teams - "not in use" would be a false conclusion
        }
        FtbHookSavedData.TeamLinkEntry entry = findByLcTeamId(server, lcTeamId);
        if (entry != null && !isActiveParty(server, entry.ftbTeamId())
                && FtbHookSavedData.get(server).clearLcTeamLink(entry.ftbTeamId())) {
            LCFtbHook.LOGGER.info("Cleared stale link for LC team {} (party {} is not in use)", lcTeamId, entry.ftbTeamId());
        }
    }

    private static boolean isActiveParty(MinecraftServer server, UUID teamId) {
        return Backends.claim().isPartyTeam(server, teamId) && Backends.claim().isActiveTeam(server, teamId);
    }
}
