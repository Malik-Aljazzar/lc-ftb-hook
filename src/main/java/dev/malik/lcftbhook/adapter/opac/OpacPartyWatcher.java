package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.service.MassChangeGuard;
import dev.malik.lcftbhook.service.PartyJoinClaimSettlementService;
import dev.malik.lcftbhook.service.TeamChangeHooks;
import dev.malik.lcftbhook.service.TeamLifecycleService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OPAC's API has no party change events, so party membership is compared
 * against the previous snapshot every few seconds: a changed party (members,
 * ranks, owner, name) triggers {@link TeamChangeHooks#onTeamChanged}, a party
 * that disappeared is treated as disbanded. OPAC claims always belong to a
 * player, never to the party itself, so joins and disbands are settled here
 * the way FTB Chunks settles them: personal claims are refunded on joining,
 * a leaving member's claims stay with the party, and the members' claims are
 * released as party land on disbanding.
 */
final class OpacPartyWatcher {
    private static final int INTERVAL_TICKS = 100;

    private record PartySnapshot(UUID ownerId, String name, Map<UUID, String> ranks) {
    }

    @Nullable
    private Map<UUID, PartySnapshot> previous;
    private int ticks;

    void onServerTick(MinecraftServer server) {
        if (++ticks < INTERVAL_TICKS || !OpacIntegration.isActive()) {
            return;
        }
        ticks = 0;
        try {
            compare(server, snapshot(server));
        } catch (RuntimeException e) {
            LCFtbHook.LOGGER.error("OPAC party change check failed", e);
        }
    }

    void reset() {
        previous = null;
        ticks = 0;
    }

    private void compare(MinecraftServer server, Map<UUID, PartySnapshot> current) {
        Map<UUID, PartySnapshot> before = previous;
        previous = current;
        if (before != null && looksLikeDataLoss(before, current)) {
            // Accept the new state as baseline, but act on none of it: nothing is released or moved.
            return;
        }
        if (before == null) {
            current.keySet().forEach(partyId -> TeamChangeHooks.onTeamChanged(server, partyId));
            return;
        }
        for (Map.Entry<UUID, PartySnapshot> entry : before.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                disbanded(server, entry.getKey(), entry.getValue());
            }
        }
        // Leaves before joins: someone who switched parties within one check
        // hands their claims to the old party first, so joining the new one
        // doesn't refund land that belongs to the old party.
        for (Map.Entry<UUID, PartySnapshot> entry : current.entrySet()) {
            PartySnapshot previousState = before.get(entry.getKey());
            if (previousState != null) {
                keepLeaversClaims(server, entry.getKey(), previousState, entry.getValue());
            }
        }
        for (Map.Entry<UUID, PartySnapshot> entry : current.entrySet()) {
            PartySnapshot previousState = before.get(entry.getKey());
            if (!Objects.equals(previousState, entry.getValue())) {
                settleJoins(server, previousState, entry.getValue());
                TeamChangeHooks.onTeamChanged(server, entry.getKey());
            }
        }
    }

    /**
     * Same rule as FTB Chunks: joining (or founding) a party dissolves the
     * player's personal claims with a refund, since OPAC would otherwise
     * silently turn them into party land.
     */
    private static void settleJoins(MinecraftServer server, @Nullable PartySnapshot before, PartySnapshot now) {
        for (UUID memberId : now.ranks().keySet()) {
            if (before != null && before.ranks().containsKey(memberId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                PartyJoinClaimSettlementService.settle(server, player, memberId);
            }
        }
    }

    /**
     * Many parties (or members) vanishing in one check means OPAC didn't report
     * its data, not that everyone left - releasing or moving their claims would
     * destroy them. See {@link MassChangeGuard}.
     */
    private static boolean looksLikeDataLoss(Map<UUID, PartySnapshot> before, Map<UUID, PartySnapshot> current) {
        long vanishedParties = before.keySet().stream().filter(id -> !current.containsKey(id)).count();
        // Members leaving parties that still exist (a disbanded party's members are covered by the party count).
        int membersBefore = before.values().stream().mapToInt(party -> party.ranks().size()).sum();
        long vanishedMembers = before.entrySet().stream()
                .filter(entry -> current.containsKey(entry.getKey()))
                .flatMap(entry -> entry.getValue().ranks().keySet().stream()
                        .filter(member -> !current.get(entry.getKey()).ranks().containsKey(member)))
                .count();
        if (MassChangeGuard.looksLikeIncompleteLoad((int) vanishedParties, before.size())
                || MassChangeGuard.looksLikeIncompleteLoad((int) vanishedMembers, membersBefore)) {
            LCFtbHook.LOGGER.error("OPAC reports {} of {} parties and {} of {} members gone at once - not acting on it: no claims were released or moved.",
                    vanishedParties, before.size(), vanishedMembers, membersBefore);
            return true;
        }
        return false;
    }

    private static void keepLeaversClaims(MinecraftServer server, UUID partyId, PartySnapshot before, PartySnapshot now) {
        for (UUID memberId : before.ranks().keySet()) {
            if (!now.ranks().containsKey(memberId)) {
                OpacClaimTransfer.keepWithParty(server, partyId, memberId, now.ownerId());
            }
        }
    }

    /**
     * The party's land is its former members' claims (OPAC claims belong to
     * players), which would otherwise stay behind as personal claims.
     */
    private static void disbanded(MinecraftServer server, UUID partyId, PartySnapshot last) {
        List<ClaimPort.ChunkKey> partyChunks = new ArrayList<>();
        last.ranks().keySet().forEach(memberId -> partyChunks.addAll(Backends.claim().claimedChunks(server, memberId)));
        TeamLifecycleService.onPartyDisbanded(server, partyId, last.ownerId(), partyChunks, null);
        TeamLifecycleService.onTeamDeleted(server, partyId);
    }

    private static Map<UUID, PartySnapshot> snapshot(MinecraftServer server) {
        Map<UUID, PartySnapshot> parties = new HashMap<>();
        OpenPACServerAPI.get(server).getPartyManager().getAllStream().forEach(party -> parties.put(party.getId(), snapshotOf(server, party)));
        return parties;
    }

    private static PartySnapshot snapshotOf(MinecraftServer server, IServerPartyAPI party) {
        Map<UUID, String> ranks = party.getMemberInfoStream()
                .collect(Collectors.toMap(IPartyMemberAPI::getUUID, member -> member.getRank().name()));
        return new PartySnapshot(party.getOwner().getUUID(), OpacClaimPort.partyName(server, party), ranks);
    }
}
