package dev.malik.lcftbhook.service;

import net.minecraft.network.chat.Component;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.service.ProtectionDismantleOrder.DismantleStep;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Upkeep settlement: restore protections before wars; dismantle wars before
 * protections. Queued and paused protections share pendingProperties and
 * follow the configured per-region dismantle order (regionOrder index 0
 * dismantled first - RegionListScreen displays this reversed, see its class
 * doc - then the configured property order within it).
 */
public final class UpkeepSettlementService {
    public record SettlementResult(
            boolean paid,
            long chargedCopper,
            TeamPendingState pendingState,
            int forceLoadCount,
            List<DismantleStep> suspendedProtections,
            List<String> suspendedWarNames,
            List<DismantleStep> restoredProtections,
            List<String> restoredWarNames,
            List<DismantleStep> unaffordableRestorations,
            List<String> unaffordableWarNames,
            int unaffordableForceLoadCount
    ) {
        public static SettlementResult skipped() {
            return new SettlementResult(false, 0L, new TeamPendingState(), 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        }

        public boolean anythingSuspended() {
            return !suspendedProtections.isEmpty() || !suspendedWarNames.isEmpty();
        }

        public boolean anythingRestored() {
            return !restoredProtections.isEmpty() || !restoredWarNames.isEmpty();
        }
    }

    private UpkeepSettlementService() {
    }

    public static SettlementResult settle(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);
        CurrencyAccount account = Backends.currency().teamAccount(server, teamId);

        List<DismantleStep> suspended = new ArrayList<>();
        List<String> suspendedWarNames = new ArrayList<>();
        List<DismantleStep> restored = new ArrayList<>();
        List<String> restoredWarNames = new ArrayList<>();
        List<DismantleStep> unaffordable = new ArrayList<>();
        List<String> unaffordableWarNames = new ArrayList<>();

        boolean chargeProtection = UpkeepGating.shouldChargeProtection(server, teamId);
        // Skips ALL war processing outright when the war system is off - not
        // just billing (WarCostService already zeroes war cost when
        // disabled), the settlement loops themselves. Without this gate, any
        // war data that predates the war system being disabled (e.g. it was
        // turned off by directly editing the config file/server restart
        // rather than the runtime toggle, which is the only path that
        // actually purges existing war state - see
        // WarService#setWarSystemEnabled) would still get reported as
        // "queued/unaffordable" in upkeep messages even though wars are
        // supposedly completely off - confusing and wrong from the player's
        // perspective.
        boolean warsActive = WarService.isEnabled();

        ProtectionService.setApplying(true);
        try {
            pendingState = RegionService.applyPendingRegionAssignments(server, teamId, pendingState);
            if (warsActive) {
                pendingState = applyUserWarEnds(server, teamId, savedData, pendingState);
            }
            int unaffordableForceLoadCount = 0;
            if (chargeProtection) {
                // Frozen while paused: no restores are attempted (protection
                // costs are already gated to 0 inside ProtectionPricing, but
                // that alone doesn't stop these loops from running against
                // whatever forceload/war cost remains, so gate explicitly).
                pendingState = restorePendingProtections(server, teamId, pendingState, account, restored, unaffordable);
                // Whatever's still pending-load right after this pass's
                // restore attempt is exactly what couldn't be activated -
                // mirrors how unaffordableOut/unaffordableWarNamesOut above
                // are captured, so a force-load stuck waiting on funds shows
                // up in the same "unaffordable" message instead of nowhere.
                unaffordableForceLoadCount = pendingState.pendingForceLoads().size();
            }
            if (warsActive) {
                pendingState = restorePendingWars(server, teamId, savedData, pendingState, account, restoredWarNames, unaffordableWarNames);
                pendingState = dismantleOutgoingWarsUntilAffordable(server, teamId, savedData, pendingState, account, suspendedWarNames);
            }
            // Force-loads sit between wars and protections in dismantle
            // priority - wars are cut first, then force-loads one chunk at a
            // time (stopping as soon as affordable, same as protections and
            // wars), and protections are cut last (only once neither of
            // those alone was enough). Same single settlement, same period
            // as everything else - not a separate cycle. Restore already
            // matched this symmetrically without needing a change: it's the
            // tail of restorePendingProtections, i.e. protections restored
            // first, force-loads second, wars last (see that method).
            pendingState = dismantleForceLoadsUntilAffordable(server, teamId, pendingState, account);
            if (chargeProtection) {
                pendingState = dismantleProtectionsUntilAffordable(server, teamId, pendingState, account, suspended);
            }

            long costCopper = WarCostService.calculateTotalUpkeepCostCopper(server, teamId, pendingState);
            if (costCopper <= 0L) {
                savedData.setPendingState(teamId, pendingState);
                savedData.setProtectionLocked(teamId, false);
                syncState(server, teamId);
                return new SettlementResult(true, 0L, pendingState, forceLoadCount(server, teamId), List.copyOf(suspended), List.copyOf(suspendedWarNames), List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable), List.copyOf(unaffordableWarNames), unaffordableForceLoadCount);
            }

            if (!account.canAfford(costCopper)) {
                savedData.setPendingState(teamId, pendingState);
                savedData.setProtectionLocked(teamId, true);
                // Officers/owner only - a plain member can't do anything
                // about the team's balance or protections anyway.
                ProtectionService.notifyTeamManagers(server, teamId, Component.translatable("message.lc_ftb_hook.upkeep_unpaid_frozen"));
                syncState(server, teamId);
                return new SettlementResult(false, 0L, pendingState, forceLoadCount(server, teamId), List.copyOf(suspended), List.copyOf(suspendedWarNames), List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable), List.copyOf(unaffordableWarNames), unaffordableForceLoadCount);
            }

            account.withdraw(costCopper, "Upkeep Payment");
            dev.malik.lcftbhook.bank.ServerAccountDeposit.deposit(server, costCopper, WarService.displayName(server, teamId) + " (upkeep)");
            savedData.setPendingState(teamId, pendingState);
            savedData.setProtectionLocked(teamId, false);
            syncState(server, teamId);
            return new SettlementResult(true, costCopper, pendingState, forceLoadCount(server, teamId), List.copyOf(suspended), List.copyOf(suspendedWarNames), List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable), List.copyOf(unaffordableWarNames), unaffordableForceLoadCount);
        } finally {
            ProtectionService.setApplying(false);
        }
    }

    private static TeamPendingState applyUserWarEnds(
            MinecraftServer server,
            UUID teamId,
            FtbHookSavedData savedData,
            TeamPendingState pendingState
    ) {
        Set<UUID> partners = new HashSet<>();
        TeamPendingState updated = pendingState;
        for (UUID targetId : new HashSet<>(pendingState.pendingWarEnds())) {
            if (savedData.setWarTarget(teamId, targetId, false)) {
                partners.add(targetId);
            }
            updated = updated.withoutPendingWarEnd(targetId);
        }
        refreshWarPartners(server, teamId, partners);
        return updated;
    }

    private static TeamPendingState restorePendingProtections(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account,
            List<DismantleStep> restoredOut,
            List<DismantleStep> unaffordableOut
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState updated = pendingState;

        for (DismantleStep step : ProtectionDismantleOrder.restoreOrder(server, teamId)) {
            if (!ProtectionRollbackService.hasPendingApply(savedData, teamId, step.regionId(), step.property(), updated)) {
                continue;
            }
            if (!WarCostService.canAffordUpkeepWithPendingProperty(server, teamId, updated, account, step.regionId(), step.property())) {
                unaffordableOut.add(step);
                continue;
            }
            updated = ProtectionRollbackService.restoreProtection(server, teamId, step.regionId(), step.property(), updated);
            restoredOut.add(step);
        }

        updated = PendingChangeService.applyAffordablePendingForceLoads(server, teamId, updated, account);

        return updated;
    }

    private static TeamPendingState restorePendingWars(
            MinecraftServer server,
            UUID teamId,
            FtbHookSavedData savedData,
            TeamPendingState pendingState,
            CurrencyAccount account,
            List<String> restoredWarNamesOut,
            List<String> unaffordableWarNamesOut
    ) {
        List<UUID> restoreOrder = WarDismantleOrder.pendingWarRestoreOrder(server, teamId, pendingState, savedData);
        TeamPendingState updated = pendingState;
        Set<UUID> partners = new HashSet<>();

        for (UUID targetId : restoreOrder) {
            if (!WarCostService.canAffordUpkeepWithOutgoingWar(server, teamId, updated, account, savedData, targetId)) {
                break;
            }
            savedData.setWarTarget(teamId, targetId, true);
            updated = updated.withoutPendingWarDeclare(targetId);
            partners.add(targetId);
            restoredWarNamesOut.add(WarService.displayName(server, targetId));
        }

        if (!partners.isEmpty()) {
            refreshWarPartners(server, teamId, partners);
        }
        // Whatever's still pending-declare after the loop above is exactly
        // what couldn't be afforded this pass - restorePendingProtections has
        // an equivalent unaffordableOut bucket that already gets reported to
        // the team; pending war declares had no equivalent until now, so a
        // war stuck waiting on funds silently never showed up anywhere.
        for (UUID targetId : updated.pendingWarDeclares()) {
            unaffordableWarNamesOut.add(WarService.displayName(server, targetId));
        }
        return updated;
    }

    private static TeamPendingState dismantleOutgoingWarsUntilAffordable(
            MinecraftServer server,
            UUID teamId,
            FtbHookSavedData savedData,
            TeamPendingState pendingState,
            CurrencyAccount account,
            List<String> suspendedWarNamesOut
    ) {
        TeamPendingState updated = pendingState;
        List<UUID> dismantleOrder = WarDismantleOrder.outgoingWarDismantleOrder(server, teamId, savedData);
        Set<UUID> partners = new HashSet<>();

        for (UUID targetId : dismantleOrder) {
            if (WarCostService.canAffordUpkeep(server, teamId, updated, account)) {
                break;
            }
            if (updated.isPendingWarEnd(targetId) || !savedData.isAtWarWith(teamId, targetId)) {
                continue;
            }
            savedData.setWarTarget(teamId, targetId, false);
            updated = updated.withPendingWarDeclare(targetId);
            partners.add(targetId);
            suspendedWarNamesOut.add(WarService.displayName(server, targetId));
        }

        if (!partners.isEmpty()) {
            refreshWarPartners(server, teamId, partners);
        }
        return updated;
    }

    /**
     * Drops force-loaded chunks one REGION at a time (stopping as soon as
     * affordable) - a region's force-loaded chunks are dropped all together
     * as one unit, not one chunk at a time, same all-or-nothing granularity
     * as one war being dropped in {@link #dismantleOutgoingWarsUntilAffordable}
     * (contrast {@link #dismantleProtectionsUntilAffordable}, which is
     * per-property). Not a hard drop, see
     * {@link PendingChangeService#convertOneForceLoadToPending}. A region
     * whose force-loads aren't charged this period is left alone - dropping
     * it wouldn't make the bill any smaller.
     */
    private static TeamPendingState dismantleForceLoadsUntilAffordable(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState updated = pendingState;
        for (PendingChangeService.RegionForceLoadGroup group : PendingChangeService.forceLoadDismantleOrder(server, teamId)) {
            if (WarCostService.canAffordUpkeep(server, teamId, updated, account)) {
                break;
            }
            if (!ForceLoadBilling.isChargeable(server, savedData, teamId, group.regionId())) {
                continue;
            }
            for (String chunkKey : group.chunkKeys()) {
                updated = PendingChangeService.convertOneForceLoadToPending(server, teamId, chunkKey, updated);
            }
        }
        return updated;
    }

    private static TeamPendingState dismantleProtectionsUntilAffordable(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account,
            List<DismantleStep> suspendedOut
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState updated = pendingState;

        for (DismantleStep step : ProtectionDismantleOrder.fullDismantleOrder(server, teamId)) {
            if (!ProtectionRollbackService.isLiveProtectionBillable(savedData, teamId, step.regionId(), step.property())) {
                continue;
            }
            long reducedCost = WarCostService.calculateProtectionAndIncomingUpkeepCopper(server, teamId, updated);
            if (account.canAfford(reducedCost)) {
                break;
            }
            updated = ProtectionRollbackService.suspendProtection(server, teamId, step.regionId(), step.property(), updated);
            suspendedOut.add(step);
        }

        return updated;
    }

    private static int forceLoadCount(MinecraftServer server, UUID teamId) {
        return Backends.claim().forceLoadedChunks(server, teamId).size();
    }

    private static void refreshWarPartners(MinecraftServer server, UUID teamId, Set<UUID> partners) {
        if (partners.isEmpty()) {
            return;
        }
        WarStateSync.syncToTeam(server, teamId);
        WarStateSync.onUpkeepFactorsChanged(server, teamId);
        for (UUID partnerId : partners) {
            WarStateSync.syncToTeam(server, partnerId);
            if (TeamCatalog.exists(server, partnerId)) {
                WarStateSync.onUpkeepFactorsChanged(server, partnerId);
            }
        }
    }

    private static void syncState(MinecraftServer server, UUID teamId) {
        PendingStateSync.syncTeam(server, teamId);
        RegionService.syncRegionsToTeam(server, teamId);
        WarStateSync.onUpkeepFactorsChanged(server, teamId);
        // Upkeep just withdrew (or tried to withdraw) money from the team's
        // account - without this, the balance shown in the claim/map info
        // panel stayed stale until something unrelated happened to trigger a
        // resync (opening the claim screen, claiming a chunk, etc.).
        for (net.minecraft.server.level.ServerPlayer member : Backends.claim().onlineMembers(server, teamId)) {
            ClaimPriceSync.syncToPlayer(member);
        }
    }
}
