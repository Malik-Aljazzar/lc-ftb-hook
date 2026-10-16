package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * What the core does on team lifecycle events, independent of the claim
 * backend. Claim adapters call these from their own events (FTB Teams'
 * TeamEvent, OPAC's party changes).
 */
public final class TeamLifecycleService {
    private TeamLifecycleService() {
    }

    /**
     * Purges data of teams that disappeared while the server was down, and
     * touches the server account once so the currency backend can finish its
     * own setup - deferred one tick because Lightman's Currency's bank data
     * isn't ready at the exact moment ServerStartedEvent fires. (server.execute
     * would run it immediately - we are already on the server thread.)
     */
    public static void onServerStarted(MinecraftServer server) {
        TeamDeletionService.reconcile(server);
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            try {
                Backends.currency().serverAccount(server);
            } catch (IllegalStateException notReadyYet) {
                // the next real access retries
            }
            dev.malik.lcftbhook.util.Guard.run("chunk data check at startup", () -> ChunkDataReconciler.reconcile(server));
        }));
    }

    /** A player is online and their team is resolvable - bring their client up to date. */
    public static void onPlayerReady(MinecraftServer server, ServerPlayer player) {
        TeamDeletionService.reconcile(server);
        UUID teamId = TeamCatalog.teamOf(server, player.getUUID());
        if (teamId != null) {
            TeamChangeHooks.onTeamChanged(server, teamId);
        }
        ClaimPriceSync.syncToPlayer(player);
        PendingStateSync.syncToPlayer(player);
        syncRegions(player);
        WarStateSync.syncToPlayer(player);
        // Once more a tick later: the login sync can race the client's play
        // handler registration right after a full server restart + reconnect.
        server.execute(() -> syncRegions(player));
    }

    private static void syncRegions(ServerPlayer player) {
        RegionService.syncRegionsToPlayer(player);
        RegionService.syncMembershipToPlayer(player);
        RegionService.syncPublicRegionsToPlayer(player);
    }

    /**
     * A party was disbanded: refund and release its chunks, hand its money to
     * the owner, then clean up. {@code claimedChunks} is captured by the
     * adapter from the backend's own (possibly already unregistered) party.
     */
    public static void onPartyDisbanded(MinecraftServer server, UUID teamId, @Nullable UUID ownerId,
                                        List<ClaimPort.ChunkKey> claimedChunks, @Nullable ServerPlayer leavingPlayer) {
        PartyDisbandSettlementService.settle(server, teamId, ownerId, claimedChunks);
        if (leavingPlayer != null) {
            WarStateSync.syncToPlayer(leavingPlayer);
        }
    }

    public static void onTeamDeleted(MinecraftServer server, UUID teamId) {
        TeamDeletionService.purge(server, teamId);
    }
}
