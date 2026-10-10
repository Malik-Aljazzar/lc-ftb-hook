package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.service.BulkClaimAffordability;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.config.ServerConfig;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * OPAC's area claims/unclaims (spread over several ticks by its own task),
 * given the same treatment as FTB Chunks' multi-chunk actions: an
 * all-or-nothing price check before anything is claimed, and one summary
 * message at the end instead of one per chunk. Driven by
 * {@code OpacAreaClaimTaskMixin}.
 */
public final class OpacAreaClaims {
    /** Outcome of the area task currently being processed, null outside one. */
    @Nullable
    private static Outcome current;

    private OpacAreaClaims() {
    }

    /**
     * What this mod decided for an area task's chunks. A force-load change
     * this mod queues is refused towards OPAC (it must not apply it itself),
     * which OPAC reports as a red "prevented by an addon" failure - so a task
     * whose refusals were all such queued changes drops that failure type,
     * leaving only the "queued until the next upkeep period" note.
     */
    public static final class Outcome {
        private boolean queued;
        private boolean denied;

        public boolean onlyQueued() {
            return queued && !denied;
        }
    }

    public static void track(@Nullable Outcome outcome) {
        current = outcome;
    }

    static void noteQueued() {
        if (current != null) {
            current.queued = true;
        }
    }

    static void noteDenied() {
        if (current != null) {
            current.denied = true;
        }
    }

    /** The area a task will touch, as OPAC clamps it (force skips the distance limit). */
    public record Area(ResourceLocation dimension, int left, int top, int right, int bottom, int fromX, int fromZ, boolean force) {
    }

    /** Why the whole area claim is refused up front, or null to let it run. */
    @Nullable
    public static Component refusal(MinecraftServer server, UUID playerId, ClaimingAction action, Area area) {
        if (OpacActions.of(action) != OpacActions.CLAIM || !OpacIntegration.isActive() || server.getPlayerList().getPlayer(playerId) == null) {
            return null;
        }
        UUID teamId = Backends.claim().teamIdForPlayer(server, playerId);
        if (teamId == null) {
            return null;
        }
        BulkClaimAffordability.Refusal refusal = BulkClaimAffordability.check(server, playerId, teamId,
                Backends.claim().claimedChunkCount(server, teamId), countUnclaimed(server, area));
        return refusal == null ? null : refusal.message();
    }

    /** Opens a summary batch for this task; the returned handle travels with the task between ticks. */
    @Nullable
    public static Object begin(UUID playerId, ClaimingAction action, Area area) {
        int chunks = (1 + area.right() - area.left()) * (1 + area.bottom() - area.top());
        ClaimBatchContext.beginExecution(ClaimBatchContext.Operation.of(OpacActions.of(action).name()), chunks, playerId);
        return ClaimBatchContext.suspend();
    }

    public static void resume(@Nullable Object batch) {
        ClaimBatchContext.resume(batch);
    }

    @Nullable
    public static Object pause() {
        return ClaimBatchContext.suspend();
    }

    /** Sends the summary to the player (if still online) and closes the batch. */
    public static void finish(MinecraftServer server, UUID playerId, @Nullable Object batch) {
        ClaimBatchContext.resume(batch);
        ClaimBatchContext.flush(server.getPlayerList().getPlayer(playerId));
    }

    private static int countUnclaimed(MinecraftServer server, Area area) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        int maxDistance = area.force() ? Integer.MAX_VALUE : ServerConfig.CONFIG.maxClaimDistance.get();
        int count = 0;
        for (int x = Math.max(area.left(), area.fromX() - maxDistance); x <= Math.min(area.right(), area.fromX() + maxDistance); x++) {
            for (int z = Math.max(area.top(), area.fromZ() - maxDistance); z <= Math.min(area.bottom(), area.fromZ() + maxDistance); z++) {
                if (claims.get(area.dimension(), x, z) == null) {
                    count++;
                }
            }
        }
        return count;
    }
}
