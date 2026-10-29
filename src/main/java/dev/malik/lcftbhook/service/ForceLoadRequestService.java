package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * A player asking to force-load or un-force-load a chunk never changes the
 * chunk directly: the request is queued in the team's pending state and
 * applied (or withheld) at the next upkeep settlement, depending on whether
 * the team can afford it - "intent is always saveable, enforcement is a
 * separate question", same as region property edits. Clicking again while a
 * request is queued cancels it. Backend-neutral: claim adapters call
 * {@link #onRequest} from their own force-load events and cancel the
 * backend's own action on {@link ClaimDecision.Kind#HANDLED}.
 */
public final class ForceLoadRequestService {
    private ForceLoadRequestService() {
    }

    public static ClaimDecision onRequest(MinecraftServer server, ServerPlayer player, UUID teamId, String chunkKey,
                                          boolean currentlyForceLoaded, boolean load) {
        if (Backends.claim().isServerTeam(server, teamId)) {
            return ClaimDecision.ALLOW; // admin-run team: free and applied right away
        }
        if (!Backends.currency().canPlayerSpend(server, teamId, player.getUUID())) {
            return ClaimDecision.deny("message.lc_ftb_hook.claim_rank_denied");
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);

        if (pendingState.isPendingForceLoad(chunkKey) || pendingState.isPendingForceUnload(chunkKey)) {
            savedData.setPendingState(teamId, pendingState.withoutPendingForceLoad(chunkKey).withoutPendingForceUnload(chunkKey));
            PendingStateSync.syncTeam(server, teamId);
            notifyManagers(server, teamId, "message.lc_ftb_hook.forceload_pending_cancelled");
            return ClaimDecision.HANDLED;
        }
        if (currentlyForceLoaded == load) {
            return ClaimDecision.ALLOW;
        }
        if (load && isResting(savedData, teamId, chunkKey, pendingState)) {
            // Its force-loads could never load or be charged - refuse instead of queueing something dead.
            return ClaimDecision.deny("message.lc_ftb_hook.forceload_mode_resting");
        }
        return load ? queueLoad(server, teamId, chunkKey, pendingState) : queueUnload(server, teamId, chunkKey, pendingState);
    }

    private static ClaimDecision queueLoad(MinecraftServer server, UUID teamId, String chunkKey, TeamPendingState pendingState) {
        TeamPendingState updated = pendingState.withPendingForceLoad(chunkKey);
        FtbHookSavedData.get(server).setPendingState(teamId, updated);
        PendingStateSync.syncTeam(server, teamId);
        // "Would adding just this one more force-loaded chunk (on top of what's
        // already live) push us over budget" - not the general affordability
        // check, which only reflects what's already live.
        boolean affordable = WarCostService.canAffordUpkeepWithOneMoreForceLoad(
                server, teamId, updated, Backends.currency().teamAccount(server, teamId),
                ProtectionPricing.effectiveRegion(FtbHookSavedData.get(server), teamId, chunkKey, updated));
        notifyManagers(server, teamId, affordable
                ? "message.lc_ftb_hook.forceload_change_pending"
                : "message.lc_ftb_hook.forceload_queued_unaffordable");
        return ClaimDecision.HANDLED;
    }

    private static ClaimDecision queueUnload(MinecraftServer server, UUID teamId, String chunkKey, TeamPendingState pendingState) {
        FtbHookSavedData.get(server).setPendingState(teamId, pendingState.withPendingForceUnload(chunkKey));
        PendingStateSync.syncTeam(server, teamId);
        notifyManagers(server, teamId, "message.lc_ftb_hook.forceload_change_pending");
        return ClaimDecision.HANDLED;
    }

    /** The chunk's region has no usable force-load mode: the server disabled its mode and every cheaper one. */
    private static boolean isResting(FtbHookSavedData savedData, UUID teamId, String chunkKey, TeamPendingState pendingState) {
        UUID regionId = ProtectionPricing.effectiveRegion(savedData, teamId, chunkKey, pendingState);
        return ForceLoadModes.effectiveMode(savedData, teamId, regionId).isEmpty();
    }

    /** Officers/owner only - force-load queue state is region management, off-limits to a plain member. */
    private static void notifyManagers(MinecraftServer server, UUID teamId, String key) {
        ProtectionService.notifyTeamManagers(server, teamId, Component.translatable(key));
    }
}
