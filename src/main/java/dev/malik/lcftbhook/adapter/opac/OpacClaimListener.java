package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.bank.ClaimSettlementContext;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.util.Guard;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.service.ClaimDecision;
import dev.malik.lcftbhook.service.ClaimLifecycleService;
import dev.malik.lcftbhook.service.ForceLoadRequestService;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates OPAC's claim action listener callbacks into this mod's
 * backend-neutral hooks - the OPAC counterpart of {@code FtbClaimEvents}.
 * <p>
 * OPAC may still refuse an action after the permission check (its own claim
 * limits etc.) and may call the check more than once, so money only moves in
 * {@link #handleSuccessfulClaimingAction}: the check only verifies rights and
 * affordability. Chunk counts from before the change are remembered between
 * the two callbacks, since OPAC's counts already reflect the change in the
 * second one.
 */
final class OpacClaimListener implements IClaimActionListenerAPI {
    private final Map<String, Integer> claimCountBefore = new ConcurrentHashMap<>();
    private final Map<String, PendingUnclaim> unclaimsInFlight = new ConcurrentHashMap<>();

    private record PendingUnclaim(@Nullable UUID teamId, int countBefore) {
    }

    @Override
    public String getName() {
        return "LC FTB Hook";
    }

    /** Guarded: if our check fails, the action is refused - nothing changes unchecked or unpaid. */
    @Override
    public ClaimActionPermissionOverride overrideClaimingActionPermission(
            UUID playerId, ResourceLocation dim, int x, int z, ClaimingAction action,
            IServerClaimsManagerAPI claimsManager, ClaimActionPermissionOverride currentOverride, MinecraftServer server) {
        return Guard.call("OPAC claim action check",
                () -> decide(playerId, dim, x, z, action, claimsManager, currentOverride, server),
                toOverride(ClaimDecision.failedCheck(), currentOverride));
    }

    private ClaimActionPermissionOverride decide(
            UUID playerId, ResourceLocation dim, int x, int z, ClaimingAction action,
            IServerClaimsManagerAPI claimsManager, ClaimActionPermissionOverride currentOverride, MinecraftServer server) {
        ClaimPort.ChunkKey chunk = chunk(dim, x, z);
        if (!OpacIntegration.isActive()) {
            return currentOverride;
        }
        OpacActions kind = OpacActions.of(action);
        if (OpacInternalActions.isRunning()) {
            rememberUnclaim(server, kind, chunk);
            return currentOverride;
        }
        ServerPlayer actor = server.getPlayerList().getPlayer(playerId);
        IPlayerChunkClaimAPI existing = kind == OpacActions.CLAIM ? claimsManager.get(dim, x, z) : null;
        if (existing != null && playerId.equals(existing.getPlayerId())) {
            // Re-claiming an existing claim only switches its OPAC sub-config -
            // protection per chunk comes from regions here, so that's refused.
            return toOverride(ClaimDecision.deny("message.lc_ftb_hook.opac_subclaims_managed"), currentOverride);
        }
        ClaimDecision decision = switch (kind) {
            case CLAIM -> checkClaim(server, actor, playerId, chunk);
            case UNCLAIM -> checkUnclaim(server, actor, chunk);
            case FORCELOAD, UNFORCELOAD -> forceLoadRequest(server, actor, chunk, kind == OpacActions.FORCELOAD);
            case OTHER -> ClaimDecision.ALLOW;
        };
        return toOverride(decision, currentOverride);
    }

    private ClaimDecision checkClaim(MinecraftServer server, @Nullable ServerPlayer actor, UUID playerId, ClaimPort.ChunkKey chunk) {
        UUID teamId = Backends.claim().teamIdForPlayer(server, playerId);
        int count = teamId == null ? 0 : Backends.claim().claimedChunkCount(server, teamId);
        claimCountBefore.put(ChunkPosKey.encode(chunk), count);
        return ClaimLifecycleService.checkClaim(server, actor, count);
    }

    private ClaimDecision checkUnclaim(MinecraftServer server, @Nullable ServerPlayer actor, ClaimPort.ChunkKey chunk) {
        PendingUnclaim pending = rememberUnclaim(server, OpacActions.UNCLAIM, chunk);
        return ClaimLifecycleService.beforeUnclaim(server, actor, pending.teamId());
    }

    /** The owning team disappears with the claim, so it's captured before the unclaim happens. */
    private PendingUnclaim rememberUnclaim(MinecraftServer server, OpacActions kind, ClaimPort.ChunkKey chunk) {
        if (kind != OpacActions.UNCLAIM) {
            return new PendingUnclaim(null, 0);
        }
        ClaimSettlementContext.SettlementTeam settlement = ClaimSettlementContext.current();
        PendingUnclaim pending;
        if (settlement != null) {
            // A player's claims follow them in and out of parties, so the team
            // being settled can't be read off the chunk (or no longer exists).
            pending = new PendingUnclaim(settlement.teamId(), settlement.nextCountBefore());
        } else {
            UUID owner = Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z);
            pending = new PendingUnclaim(owner, owner == null ? 0 : Backends.claim().claimedChunkCount(server, owner));
        }
        unclaimsInFlight.put(ChunkPosKey.encode(chunk), pending);
        return pending;
    }

    private static ClaimDecision forceLoadRequest(MinecraftServer server, @Nullable ServerPlayer actor, ClaimPort.ChunkKey chunk, boolean load) {
        UUID owner = Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z);
        if (actor == null || owner == null) {
            return ClaimDecision.ALLOW;
        }
        return ForceLoadRequestService.onRequest(server, actor, owner, ChunkPosKey.encode(chunk),
                Backends.claim().isForceLoaded(server, chunk), load);
    }

    @Override
    public void handleSuccessfulClaimingAction(UUID playerId, ResourceLocation dim, int x, int z, ClaimingAction action,
                                               IServerClaimsManagerAPI claimsManager, MinecraftServer server) {
        Guard.run("OPAC claim action follow-up", () -> afterAction(playerId, dim, x, z, action, server));
    }

    private void afterAction(UUID playerId, ResourceLocation dim, int x, int z, ClaimingAction action, MinecraftServer server) {
        if (!OpacIntegration.isActive()) {
            return;
        }
        ClaimPort.ChunkKey chunk = chunk(dim, x, z);
        String key = ChunkPosKey.encode(chunk);
        ServerPlayer actor = OpacInternalActions.isRunning() ? null : server.getPlayerList().getPlayer(playerId);
        switch (OpacActions.of(action)) {
            case CLAIM -> {
                if (!OpacInternalActions.isRunning()) {
                    afterClaim(server, actor, chunk, key);
                }
            }
            case UNCLAIM -> {
                PendingUnclaim pending = unclaimsInFlight.remove(key);
                ClaimLifecycleService.afterUnclaim(server, actor, pending == null ? null : pending.teamId(), key,
                        pending == null ? 0 : pending.countBefore());
            }
            case FORCELOAD, UNFORCELOAD, OTHER -> {
            }
        }
    }

    /** Pays now; if the team can no longer afford it after all, the claim is taken back without a refund. */
    private void afterClaim(MinecraftServer server, @Nullable ServerPlayer actor, ClaimPort.ChunkKey chunk, String key) {
        Integer remembered = claimCountBefore.remove(key);
        UUID teamId = Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z);
        if (teamId == null) {
            return;
        }
        int countBefore = remembered != null ? remembered : Math.max(0, Backends.claim().claimedChunkCount(server, teamId) - 1);
        if (!ClaimLifecycleService.chargeClaim(server, actor, countBefore)) {
            OpacClaimPort.unclaimSilently(server, chunk);
            if (actor != null) {
                actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.claim_reverted_unaffordable"), false);
            }
            return;
        }
        ClaimLifecycleService.afterClaim(server, actor, teamId, key, countBefore);
        // The new claim still points at the owner's default config - move it to its region's sub-config now.
        OpacProtectionSync.request();
    }

    private static ClaimActionPermissionOverride toOverride(ClaimDecision decision, ClaimActionPermissionOverride currentOverride) {
        return switch (decision.kind()) {
            case ALLOW -> currentOverride;
            case DENY -> {
                OpacAreaClaims.noteDenied();
                yield new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID, decision.message());
            }
            // Queued by this mod instead of applied now - OPAC must not apply it itself.
            case HANDLED -> {
                OpacAreaClaims.noteQueued();
                yield new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID,
                        Component.translatable("message.lc_ftb_hook.forceload_change_pending"));
            }
        };
    }

    private static ClaimPort.ChunkKey chunk(ResourceLocation dim, int x, int z) {
        return new ClaimPort.ChunkKey(ResourceKey.create(Registries.DIMENSION, dim), new ChunkPos(x, z));
    }
}
