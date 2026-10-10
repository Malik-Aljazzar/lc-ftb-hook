package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.network.WarEntryStatus;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds incoming-war views specifically - split out of
 * {@link WarViewBuilder} (which keeps outgoing/available-target views) since
 * incoming cost scales with declare ORDER across the whole list
 * ({@link #applyOrdinalIncomingCosts}), a wrinkle outgoing/available views
 * don't share.
 */
public final class WarIncomingViewBuilder {
    private static final Comparator<WarTeamView> BY_DISPLAY_NAME =
            Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER);

    private WarIncomingViewBuilder() {
    }

    public static List<WarTeamView> buildIncomingViews(MinecraftServer server, UUID selfId) {
        if (!WarService.isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        long selfBase = WarCostService.baseUpkeepCopper(server, selfId);

        List<WarTeamView> views = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        collectActiveIncomingDeclarers(server, savedData, selfId, selfBase, views, seen);
        collectPendingIncomingDeclarers(server, savedData, selfId, selfBase, views, seen);

        views.sort(BY_DISPLAY_NAME);
        return applyOrdinalIncomingCosts(views, selfBase);
    }

    private static void collectActiveIncomingDeclarers(
            MinecraftServer server, FtbHookSavedData savedData, UUID selfId, long selfBase,
            List<WarTeamView> views, Set<UUID> seen
    ) {
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            UUID declarerId = entry.ftbTeamId();
            if (!entry.warTargets().contains(selfId) || !WarService.isWarEligibleTeam(server, declarerId)) {
                continue;
            }
            seen.add(declarerId);
            long declarerBase = WarCostService.baseUpkeepCopper(server, declarerId);
            views.add(WarViewBuilder.opponentView(server, declarerId, declarerBase, selfBase, WarEntryStatus.ACTIVE, false));
        }
    }

    private static void collectPendingIncomingDeclarers(
            MinecraftServer server, FtbHookSavedData savedData, UUID selfId, long selfBase,
            List<WarTeamView> views, Set<UUID> seen
    ) {
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            UUID declarerId = entry.ftbTeamId();
            if (declarerId.equals(selfId) || seen.contains(declarerId) || !entry.pendingState().isPendingWarDeclare(selfId)) {
                continue;
            }
            if (!WarService.isWarEligibleTeam(server, declarerId)) {
                continue;
            }
            seen.add(declarerId);
            long declarerBase = WarCostService.baseUpkeepCopper(server, declarerId);
            views.add(WarViewBuilder.opponentView(server, declarerId, declarerBase, selfBase, WarEntryStatus.PENDING_DECLARE, false));
        }
    }

    /** Incoming cost scales with declare order (1st/2nd/3rd... incoming war), so it can only be assigned once the full sorted list is known. */
    private static List<WarTeamView> applyOrdinalIncomingCosts(List<WarTeamView> views, long selfBase) {
        double exponent = WarService.warMultiplier();
        List<WarTeamView> result = new ArrayList<>(views.size());
        for (int i = 0; i < views.size(); i++) {
            WarTeamView view = views.get(i);
            long term = WarUpkeepMath.ordinalWarTermCopper(selfBase, i, exponent);
            result.add(new WarTeamView(
                    view.teamId(), view.displayName(), view.targetBaseUpkeepCopper(), term,
                    view.status(), view.opponentPendingDeclareOnViewer(),
                    view.blockEditProtected(), view.explosionProtected(), view.pvpProtected()
            ));
        }
        return result;
    }

    /** Active incoming wars only — used for upkeep billing breakdown lines. */
    public static List<WarTeamView> buildBilledIncomingViews(MinecraftServer server, UUID selfId) {
        if (!WarService.isEnabled()) {
            return List.of();
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        long selfBase = WarCostService.baseUpkeepCopper(server, selfId);
        double exponent = WarService.warMultiplier();

        List<UUID> declarerIds = eligibleIncomingDeclarerIds(server, savedData, selfId);
        List<WarTeamView> views = new ArrayList<>();
        for (int i = 0; i < declarerIds.size(); i++) {
            UUID declarerId = declarerIds.get(i);
            long term = WarUpkeepMath.ordinalWarTermCopper(selfBase, i, exponent);
            long declarerBase = WarCostService.baseUpkeepCopper(server, declarerId);
            views.add(WarViewBuilder.opponentView(server, declarerId, declarerBase, term, WarEntryStatus.ACTIVE, false));
        }
        views.sort(BY_DISPLAY_NAME);
        return views;
    }

    private static List<UUID> eligibleIncomingDeclarerIds(MinecraftServer server, FtbHookSavedData savedData, UUID selfId) {
        List<UUID> declarerIds = new ArrayList<>();
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (entry.warTargets().contains(selfId) && WarService.isWarEligibleTeam(server, entry.ftbTeamId())) {
                declarerIds.add(entry.ftbTeamId());
            }
        }
        declarerIds.sort(Comparator.comparing(UUID::toString));
        return declarerIds;
    }
}
