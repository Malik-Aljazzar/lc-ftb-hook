package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * War-inclusive upkeep cost math: what a team owes per period (protection +
 * incoming/outgoing war surcharges) and whether a given change is
 * affordable. Split out of {@link WarService} to keep cost math separate
 * from war existence/lifecycle - this class never mutates war state, only
 * reads it.
 */
public final class WarCostService {
    public record WarCostBreakdown(
            long baseUpkeepCopper,
            long incomingWarCopper,
            long outgoingWarCopper,
            int incomingWarCount,
            int outgoingWarCount
    ) {
        public long totalWarCopper() {
            return incomingWarCopper + outgoingWarCopper;
        }

        public long totalUpkeepCopper() {
            return baseUpkeepCopper + totalWarCopper();
        }
    }

    private WarCostService() {
    }

    public static long baseUpkeepCopper(MinecraftServer server, UUID teamId) {
        return baseUpkeepCopper(server, teamId, FtbHookSavedData.get(server).getPendingState(teamId));
    }

    public static long baseUpkeepCopper(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        return ProtectionPricing.calculateTotalUpkeepCopper(server, teamId, pendingState);
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer server, UUID teamId) {
        return calculateWarCosts(server, teamId, FtbHookSavedData.get(server).getPendingState(teamId));
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        Map<String, String> pricingOverrides = ProtectionRollbackService.pricingProperties(server, teamId, pendingState);
        // Force-load billing must follow the same "unbilled until actually
        // live" rule pricingProperties already applies to protections above -
        // without this, a QUEUED-but-never-applied force-load request (e.g.
        // one that was never affordable to activate in the first place) got
        // counted here as an already-owed cost in every affordability check
        // that goes through this "current" view, which could permanently
        // freeze a team - blocking restoration of otherwise fully-affordable
        // protections - over a single force-load nobody could ever actually
        // settle, since nothing ever "gives up" on a force-load that was
        // never live to begin with (unlike a LIVE force-loaded chunk, which
        // dismantleForceLoadsUntilAffordable can drop). Stripping the pending
        // force-load/unload sets here (pendingProperties/regions/wars are
        // untouched) makes this view price only chunks that are ACTUALLY
        // force-loaded right now.
        TeamPendingState liveForceLoadView = new TeamPendingState(
                pendingState.pendingProperties(), Set.of(), Set.of(),
                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds());
        return calculateWarCosts(server, teamId, liveForceLoadView, pricingOverrides);
    }

    public static WarCostBreakdown calculateWarCosts(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            Map<String, String> pricingOverrides
    ) {
        long base = ProtectionPricing.calculateTotalUpkeepCopper(server, teamId, pendingState, pricingOverrides);
        if (!WarService.isEnabled()) {
            return new WarCostBreakdown(base, 0L, 0L, 0, 0);
        }

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        // Only count declarers that still exist and have claimed chunks — the same
        // filter used by WarViewBuilder.buildIncomingViews. Stale references from
        // deleted or claim-less teams would otherwise inflate the billing without
        // showing up in the war screen.
        int incomingCount = countEligibleIncomingWars(server, savedData, teamId);
        long incoming = WarUpkeepMath.sumOrdinalIncomingTerms(base, incomingCount);

        Set<UUID> targets = savedData.getWarTargets(teamId);
        long outgoing = sumOutgoingWarCost(server, savedData, targets);

        return new WarCostBreakdown(base, incoming, outgoing, incomingCount, targets.size());
    }

    private static long sumOutgoingWarCost(MinecraftServer server, FtbHookSavedData savedData, Set<UUID> targets) {
        long total = 0L;
        for (UUID targetId : targets) {
            if (WarService.isClaimTeam(server, targetId)) {
                long targetBase = baseUpkeepCopper(server, targetId, savedData.getPendingState(targetId));
                total += WarUpkeepMath.outgoingWarCostCopper(targetBase);
            }
        }
        return total;
    }

    private static int countEligibleIncomingWars(MinecraftServer server, FtbHookSavedData savedData, UUID targetId) {
        int count = 0;
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(targetId)) {
                continue;
            }
            if (!WarService.isWarEligibleTeam(server, entry.ftbTeamId())) {
                continue;
            }
            count++;
        }
        return count;
    }

    public static long calculateTotalUpkeepCostCopper(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        return calculateWarCosts(server, teamId, pendingState).totalUpkeepCopper();
    }

    public static long calculateTotalUpkeepCostCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            Map<String, String> pricingOverrides
    ) {
        return calculateWarCosts(server, teamId, pendingState, pricingOverrides).totalUpkeepCopper();
    }

    public static long calculateProtectionAndIncomingUpkeepCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState
    ) {
        WarCostBreakdown war = calculateWarCosts(server, teamId, pendingState);
        return war.baseUpkeepCopper() + war.incomingWarCopper();
    }

    public static boolean canAffordUpkeep(MinecraftServer server, UUID teamId, TeamPendingState pendingState, CurrencyAccount account) {
        return canAffordCost(account, calculateTotalUpkeepCostCopper(server, teamId, pendingState));
    }

    public static boolean canAffordUpkeepWithPendingProperty(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account,
            UUID regionId,
            ProtectionProperty property
    ) {
        Map<String, String> pricing = ProtectionRollbackService.pricingWithAppliedPending(server, teamId, pendingState, regionId, property);
        // Same live-only force-load rule as the 3-arg calculateWarCosts
        // overload (see its javadoc) - this passes pricingOverrides
        // explicitly, so it bypasses that overload entirely and would
        // otherwise still count a queued-but-never-live force-load as
        // already owed in every one of these per-property restoration
        // checks, permanently blocking them.
        TeamPendingState liveForceLoadView = new TeamPendingState(
                pendingState.pendingProperties(), Set.of(), Set.of(),
                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds());
        return canAffordCost(account, calculateTotalUpkeepCostCopper(server, teamId, liveForceLoadView, pricing));
    }

    /**
     * Whether the team could still afford upkeep if ONE more currently-
     * pending force-load became live, on top of whatever's already live -
     * used only for {@code ForceLoadHandler}'s immediate "queued" vs "queued
     * - unaffordable" pre-check message at request time, a genuinely single-
     * chunk question. Settlement's own restoration ({@code
     * PendingChangeService.applyAffordablePendingForceLoads}) checks a whole
     * region's worth at once instead - see {@link #canAffordUpkeepWithMoreForceLoads}.
     */
    public static boolean canAffordUpkeepWithOneMoreForceLoad(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account
    ) {
        return canAffordUpkeepWithMoreForceLoads(server, teamId, pendingState, account, 1);
    }

    /**
     * Whether the team could still afford upkeep if {@code additionalCount}
     * more currently-pending force-loads became live all at once, on top of
     * whatever's already live - the force-load equivalent of
     * {@link #canAffordUpkeepWithPendingProperty}, but checking a whole group
     * together rather than one property at a time (force-loads are grouped
     * and restored/dismantled by region as an all-or-nothing unit - see
     * {@code PendingChangeService.RegionForceLoadGroup}).
     */
    public static boolean canAffordUpkeepWithMoreForceLoads(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account,
            int additionalCount
    ) {
        long liveCost = calculateTotalUpkeepCostCopper(server, teamId, pendingState);
        long extra = UpkeepGating.shouldChargeForceLoad(server, teamId)
                ? ProtectionPricing.calculateForceLoadCopper(additionalCount)
                : 0L;
        return canAffordCost(account, liveCost + extra);
    }

    public static boolean canAffordUpkeepWithOutgoingWar(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            CurrencyAccount account,
            FtbHookSavedData savedData,
            UUID targetId
    ) {
        if (savedData.isAtWarWith(teamId, targetId)) {
            return canAffordUpkeep(server, teamId, pendingState, account);
        }
        savedData.setWarTarget(teamId, targetId, true);
        boolean affordable = canAffordUpkeep(server, teamId, pendingState, account);
        savedData.setWarTarget(teamId, targetId, false);
        return affordable;
    }

    private static boolean canAffordCost(CurrencyAccount account, long costCopper) {
        return account.canAfford(costCopper);
    }

    public static long costToDeclareWar(MinecraftServer server, UUID declarer, UUID target) {
        if (!WarService.isEnabled()) {
            return 0L;
        }
        return WarUpkeepMath.outgoingWarCostCopper(baseUpkeepCopper(server, target));
    }
}
