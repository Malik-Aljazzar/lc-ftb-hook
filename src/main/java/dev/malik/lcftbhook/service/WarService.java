package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * War existence and lifecycle: is the war system on, is a team eligible to
 * fight, declaring/ending/clearing wars. Cost math lives in
 * {@link WarCostService}, UI-facing view lists in {@link WarViewBuilder},
 * and dismantle/restore ordering in {@link WarDismantleOrder} - split out of
 * this class to keep each a single, focused responsibility.
 */
public final class WarService {
    private WarService() {
    }

    public static boolean isEnabled() {
        return LCFtbHookConfig.SERVER.warEnabled.get();
    }

    /** An active team that holds at least one claimed chunk. */
    public static boolean isClaimTeam(MinecraftServer server, UUID teamId) {
        return TeamCatalog.isActive(server, teamId) && Backends.claim().claimedChunkCount(server, teamId) > 0;
    }

    public static boolean isWarEligibleTeam(MinecraftServer server, UUID teamId) {
        return TeamCatalog.isActive(server, teamId);
    }

    public static boolean canManageWar(MinecraftServer server, UUID teamId, UUID playerId) {
        return Backends.currency().canPlayerSpend(server, teamId, playerId);
    }

    public static double warMultiplier() {
        return LCFtbHookConfig.SERVER.warCostMultiplier.get();
    }

    public static String displayName(MinecraftServer server, UUID teamId) {
        return TeamCatalog.displayName(server, teamId);
    }

    @Nullable
    public static Component toggleWar(MinecraftServer server, ServerPlayer player, UUID targetTeamId) {
        Component validationError = validateToggleWar(server, player, targetTeamId);
        if (validationError != null) {
            return validationError;
        }
        return applyWarToggle(server, TeamCatalog.teamOf(server, player.getUUID()), targetTeamId);
    }

    @Nullable
    private static Component validateToggleWar(MinecraftServer server, ServerPlayer player, UUID targetTeamId) {
        if (!isEnabled()) {
            return Component.translatable("message.lc_ftb_hook.war_disabled");
        }
        UUID self = TeamCatalog.teamOf(server, player.getUUID());
        if (self == null || !TeamCatalog.exists(server, targetTeamId)) {
            return Component.translatable("message.lc_ftb_hook.war_unavailable");
        }
        if (!canManageWar(server, self, player.getUUID())) {
            return Component.translatable("message.lc_ftb_hook.war_denied");
        }
        if (self.equals(targetTeamId)) {
            return Component.translatable("message.lc_ftb_hook.war_self");
        }
        if (!isWarEligibleTeam(server, self) || !isWarEligibleTeam(server, targetTeamId)) {
            return Component.translatable("message.lc_ftb_hook.war_unavailable");
        }
        return null;
    }

    private static Component applyWarToggle(MinecraftServer server, UUID selfId, UUID targetId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(selfId);
        String targetName = displayName(server, targetId);

        if (pendingState.isPendingWarDeclare(targetId)) {
            savedData.setPendingState(selfId, pendingState.withoutPendingWarDeclare(targetId));
            return Component.translatable("message.lc_ftb_hook.war_pending_cancelled", targetName);
        }
        if (savedData.isAtWarWith(selfId, targetId)) {
            return toggleActiveWar(savedData, selfId, targetId, targetName, pendingState);
        }
        savedData.setPendingState(selfId, pendingState.withPendingWarDeclare(targetId));
        return Component.translatable("message.lc_ftb_hook.war_declare_pending", targetName);
    }

    private static Component toggleActiveWar(FtbHookSavedData savedData, UUID selfId, UUID targetId, String targetName, TeamPendingState pendingState) {
        if (pendingState.isPendingWarEnd(targetId)) {
            savedData.setPendingState(selfId, pendingState.withoutPendingWarEnd(targetId));
            return Component.translatable("message.lc_ftb_hook.war_pending_cancelled", targetName);
        }
        savedData.setPendingState(selfId, pendingState.withPendingWarEnd(targetId));
        return Component.translatable("message.lc_ftb_hook.war_end_pending", targetName);
    }

    /**
     * Removes all wars declared by or against the given team and refreshes
     * war/upkeep state for every team that was involved.
     */
    public static void cleanupTeamWars(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return;
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Set<UUID> partners = savedData.collectWarPartnerIds(teamId);
        boolean hadOutgoing = !savedData.getWarTargets(teamId).isEmpty();
        if (partners.isEmpty() && !hadOutgoing) {
            return;
        }

        savedData.clearWarReferences(teamId);
        for (UUID partnerId : partners) {
            WarStateSync.syncToTeam(server, partnerId);
            if (TeamCatalog.exists(server, partnerId)) {
                WarStateSync.onUpkeepFactorsChanged(server, partnerId);
            }
        }

        LCFtbHook.LOGGER.debug("Cleared war state for team {} and refreshed {} partner team(s)", teamId, partners.size());
    }

    /**
     * Discards every active and pending war for every team that has ANY
     * SavedData link at all - deliberately NOT scoped to active teams (an
     * inactive party/solo team can still hold a stale outgoing war target -
     * "active" is about upkeep/billing eligibility, not "does this team's data
     * still exist"). {@code ClearWarsCommand} is the thin command wrapper
     * around this.
     */
    public static List<UUID> clearAllWars(MinecraftServer server) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        List<UUID> affected = new ArrayList<>();
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (clearTeamWarState(savedData, entry)) {
                affected.add(entry.ftbTeamId());
            }
        }
        syncAffectedTeams(server, affected);
        return affected;
    }

    private static boolean clearTeamWarState(FtbHookSavedData savedData, FtbHookSavedData.TeamLinkEntry entry) {
        UUID teamId = entry.ftbTeamId();
        boolean changed = false;

        if (!entry.warTargets().isEmpty()) {
            savedData.clearWarReferences(teamId);
            changed = true;
        }

        TeamPendingState pending = savedData.getPendingState(teamId);
        if (!pending.pendingWarDeclares().isEmpty() || !pending.pendingWarEnds().isEmpty()) {
            savedData.setPendingState(teamId, clearPendingWarReferences(pending, teamId));
            changed = true;
        }

        return changed;
    }

    private static TeamPendingState clearPendingWarReferences(TeamPendingState pending, UUID teamId) {
        TeamPendingState cleared = pending.copy().withoutWarReferences(teamId);
        for (UUID targetId : new HashSet<>(pending.pendingWarDeclares())) {
            cleared = cleared.withoutPendingWarDeclare(targetId);
        }
        for (UUID targetId : new HashSet<>(pending.pendingWarEnds())) {
            cleared = cleared.withoutPendingWarEnd(targetId);
        }
        return cleared;
    }

    private static void syncAffectedTeams(MinecraftServer server, List<UUID> affected) {
        for (UUID teamId : affected) {
            WarStateSync.syncToTeam(server, teamId);
            if (TeamCatalog.exists(server, teamId)) {
                WarStateSync.onUpkeepFactorsChanged(server, teamId);
            }
        }
    }

    /**
     * Runtime toggle for the whole war system (see {@code RuntimeConfigCommand}'s
     * {@code warEnabled} entry - the config value itself is already set/saved
     * by the time this runs). Turning it OFF discards every active and
     * pending war for every team, not just stops billing them - an inert
     * "at war" relationship left sitting in storage would resurrect itself
     * (still billed) the moment the config is flipped back on, which isn't
     * what "disabled" should mean. Both directions push a fresh
     * {@code SyncClaimPricesPayload} to every online player so the war
     * button/menu ({@code ClientWarState.warModuleEnabled}) appears or
     * disappears immediately, without needing a relog.
     */
    public static void setWarSystemEnabled(@Nullable MinecraftServer server, boolean enabled) {
        if (server == null) {
            return;
        }

        if (!enabled) {
            List<UUID> affected = clearAllWars(server);
            LCFtbHook.LOGGER.info("War system disabled at runtime - discarded active/pending wars for {} team(s).", affected.size());
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ClaimPriceSync.syncToPlayer(player);
        }
    }
}
