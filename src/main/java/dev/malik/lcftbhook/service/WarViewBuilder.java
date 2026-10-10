package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.WarEntryStatus;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds outgoing-war and available-target views - incoming-war views live
 * in {@link WarIncomingViewBuilder} instead (incoming cost scales with
 * declare order across the whole list, a wrinkle these views don't share).
 * Both are split out of {@link WarService} to keep "what does the UI show"
 * separate from war existence/lifecycle and cost math (see
 * {@link WarCostService}).
 */
public final class WarViewBuilder {
    private static final Comparator<WarTeamView> BY_DISPLAY_NAME =
            Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER);

    private WarViewBuilder() {
    }

    /** Active outgoing wars only — used for upkeep billing breakdown lines. */
    public static List<WarTeamView> buildBilledOutgoingViews(MinecraftServer server, UUID selfId) {
        if (!WarService.isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        List<WarTeamView> views = activeOutgoingViews(server, savedData, selfId);
        views.sort(BY_DISPLAY_NAME);
        return views;
    }

    public static List<WarTeamView> buildOutgoingViews(MinecraftServer server, UUID selfId) {
        if (!WarService.isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        List<WarTeamView> views = activeOutgoingViews(server, savedData, selfId);
        addPendingOutgoingViews(server, selfId, savedData, views);
        views.sort(BY_DISPLAY_NAME);
        return views;
    }

    private static List<WarTeamView> activeOutgoingViews(MinecraftServer server, FtbHookSavedData savedData, UUID selfId) {
        TeamPendingState pendingState = savedData.getPendingState(selfId);
        List<WarTeamView> views = new ArrayList<>();
        for (UUID targetId : sortedActiveTargetIds(savedData, selfId)) {
            if (!WarService.isWarEligibleTeam(server, targetId)) {
                continue;
            }
            views.add(activeOutgoingView(server, targetId, pendingState));
        }
        return views;
    }

    private static List<UUID> sortedActiveTargetIds(FtbHookSavedData savedData, UUID selfId) {
        List<UUID> ids = new ArrayList<>(savedData.getWarTargets(selfId));
        ids.sort(Comparator.comparing(UUID::toString));
        return ids;
    }

    private static WarTeamView activeOutgoingView(MinecraftServer server, UUID targetId, TeamPendingState pendingState) {
        WarEntryStatus status = pendingState.isPendingWarEnd(targetId)
                ? WarEntryStatus.PENDING_END
                : WarEntryStatus.ACTIVE;
        long targetBase = WarCostService.baseUpkeepCopper(server, targetId);
        return opponentView(server, targetId, targetBase, WarUpkeepMath.outgoingWarCostCopper(targetBase), status, false);
    }

    private static void addPendingOutgoingViews(
            MinecraftServer server, UUID selfId, FtbHookSavedData savedData, List<WarTeamView> views
    ) {
        TeamPendingState pendingState = savedData.getPendingState(selfId);
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (savedData.isAtWarWith(selfId, targetId) || !WarService.isWarEligibleTeam(server, targetId)) {
                continue;
            }
            long targetBase = WarCostService.baseUpkeepCopper(server, targetId);
            long cost = WarCostService.costToDeclareWar(server, selfId, targetId);
            views.add(opponentView(server, targetId, targetBase, cost, WarEntryStatus.PENDING_DECLARE, false));
        }
    }

    public static List<WarTeamView> buildAvailableTargets(MinecraftServer server, UUID selfId) {
        if (!WarService.isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Set<UUID> activeTargets = savedData.getWarTargets(selfId);
        TeamPendingState selfPending = savedData.getPendingState(selfId);

        List<WarTeamView> views = new ArrayList<>();
        for (UUID teamId : TeamCatalog.activeTeams(server)) {
            if (teamId.equals(selfId) || activeTargets.contains(teamId) || selfPending.isPendingWarDeclare(teamId)) {
                continue;
            }
            views.add(availableTargetView(server, selfId, teamId, selfPending, savedData));
        }
        views.sort(BY_DISPLAY_NAME);
        return views;
    }

    private static WarTeamView availableTargetView(
            MinecraftServer server, UUID selfId, UUID teamId, TeamPendingState selfPending, FtbHookSavedData savedData
    ) {
        boolean opponentPending = savedData.getPendingState(teamId).isPendingWarDeclare(selfId);
        WarEntryStatus status = selfPending.isPendingWarDeclare(teamId) ? WarEntryStatus.PENDING_DECLARE : WarEntryStatus.ACTIVE;
        long targetBase = WarCostService.baseUpkeepCopper(server, teamId);
        long cost = WarCostService.costToDeclareWar(server, selfId, teamId);
        return opponentView(server, teamId, targetBase, cost, status, opponentPending);
    }

    static WarTeamView opponentView(
            MinecraftServer server,
            UUID opponentId,
            long targetBaseUpkeepCopper,
            long warCostCopper,
            WarEntryStatus status,
            boolean opponentPendingDeclareOnViewer
    ) {
        WarTargetProtections protections = WarTargetProtections.live(server, opponentId);
        return new WarTeamView(
                opponentId,
                WarService.displayName(server, opponentId),
                targetBaseUpkeepCopper,
                warCostCopper,
                status,
                opponentPendingDeclareOnViewer,
                protections.blockEditProtected(),
                protections.explosionProtected(),
                protections.pvpProtected()
        );
    }
}
