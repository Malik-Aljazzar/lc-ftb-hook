package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/**
 * Team-wide upkeep totals for the Region list screen's header: protection
 * (summed across every region), force-load, and war (incoming + outgoing)
 * upkeep, each as a current/pending pair. "Current" always uses an empty
 * {@link TeamPendingState} (the true live baseline, unaffected by anything
 * queued), "pending" uses the team's real queued state - same current-vs-
 * projected convention as the per-region upkeep display.
 */
public final class UpkeepSummaryService {
    private UpkeepSummaryService() {
    }

    public record UpkeepSummary(
            long protectionCurrentCopper,
            long protectionPendingCopper,
            long forceLoadCurrentCopper,
            long forceLoadPendingCopper,
            int forceLoadCurrentCount,
            int forceLoadPendingCount,
            long warIncomingCurrentCopper,
            long warIncomingPendingCopper,
            long warOutgoingCurrentCopper,
            long warOutgoingPendingCopper,
            int warIncomingCount,
            int warOutgoingCount
    ) {
        public long totalCurrentCopper() {
            return protectionCurrentCopper + forceLoadCurrentCopper + warIncomingCurrentCopper + warOutgoingCurrentCopper;
        }

        public long totalPendingCopper() {
            return protectionPendingCopper + forceLoadPendingCopper + warIncomingPendingCopper + warOutgoingPendingCopper;
        }
    }

    public static UpkeepSummary compute(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);
        TeamPendingState empty = new TeamPendingState();

        int forceLoadCurrentCount = ProtectionPricing.countEffectiveForceLoads(server, teamId, empty);
        int forceLoadPendingCount = ProtectionPricing.countEffectiveForceLoads(server, teamId, pendingState);
        long forceLoadCurrent = ForceLoadBilling.chargeableCopper(server, teamId, empty);
        long forceLoadPending = ForceLoadBilling.chargeableCopper(server, teamId, pendingState);

        // baseUpkeepCopper is protection + force-load combined - subtracting
        // the independently-computed force-load piece (above) isolates
        // protection alone, using calculateWarCosts as the single source of
        // truth for the combined figure rather than re-deriving it a second,
        // potentially inconsistent way.
        //
        // "Pending" deliberately does NOT use calculateWarCosts(server, teamId,
        // pendingState)'s default overload - that one prices via
        // ProtectionRollbackService.pricingProperties, which excludes any
        // pendingProperties entry whose region is still at minimum (correct
        // for "what's actually billed right now", since a dismantled/not-
        // yet-restored property isn't live yet - but wrong here, since that
        // silently excluded exactly the entries this "pending" total exists
        // to show). Passing pendingState's raw pendingProperties() directly
        // as the pricing overlay instead prices every queued target as if it
        // had already settled, matching what RegionService.createRegionsPayload
        // already does for the per-region figures shown in RegionSettingsScreen -
        // this brings the Region List's combined total/tooltip in line with
        // that per-region view instead of silently disagreeing with it.
        WarCostService.WarCostBreakdown warCurrent = WarCostService.calculateWarCosts(server, teamId, empty);
        WarCostService.WarCostBreakdown warPending = WarCostService.calculateWarCosts(server, teamId, pendingState, pendingState.pendingProperties());
        long protectionCurrent = warCurrent.baseUpkeepCopper() - forceLoadCurrent;
        long protectionPending = warPending.baseUpkeepCopper() - forceLoadPending;

        return new UpkeepSummary(
                protectionCurrent, protectionPending,
                forceLoadCurrent, forceLoadPending,
                forceLoadCurrentCount, forceLoadPendingCount,
                warCurrent.incomingWarCopper(), warPending.incomingWarCopper(),
                warCurrent.outgoingWarCopper(), warPending.outgoingWarCopper(),
                warCurrent.incomingWarCount(), warCurrent.outgoingWarCount()
        );
    }
}
