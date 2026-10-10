package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.bank.ClaimSettlementContext;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.service.WarStateSync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyJoinClaimSettlementService {
    private static final Set<UUID> SETTLING = ConcurrentHashMap.newKeySet();

    private PartyJoinClaimSettlementService() {
    }

    /** {@code previousTeamId}: the solo team the player just left by joining a party (solo team id == player UUID). */
    public static void settle(MinecraftServer server, ServerPlayer player, UUID previousTeamId) {
        if (player == null || previousTeamId == null || Backends.claim().isPartyTeam(server, previousTeamId)) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!SETTLING.add(playerId)) {
            return;
        }

        try {
            List<ClaimPort.ChunkKey> claimed = Backends.claim().claimedChunks(server, previousTeamId);
            if (claimed.isEmpty() && Backends.claim().forceLoadedChunks(server, previousTeamId).isEmpty()) {
                return;
            }

            FtbHookSavedData savedData = FtbHookSavedData.get(server);
            WarService.cleanupTeamWars(server, previousTeamId);
            savedData.setPendingState(previousTeamId, savedData.getPendingState(previousTeamId).cleared());

            PendingChangeService.removeAllForceLoads(server, previousTeamId);

            int claimedBefore = claimed.size();
            int[] unclaimed = {0};
            ClaimBatchContext.runPersonalRefundSettlement(playerId, () ->
                    ClaimSettlementContext.run(previousTeamId, claimedBefore, () ->
                            unclaimed[0] = ClaimSettlementHelper.unclaimAll(server, claimed, false))
            );

            if (unclaimed[0] <= 0) {
                return;
            }

            long refundCopper = ClaimSettlementHelper.refundForFullUnclaim(claimedBefore);
            Component refund = dev.malik.lcftbhook.util.Money.formatValue(refundCopper);
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.party_join_claim_refund", refund, unclaimed[0]),
                    false
            );
            ClaimPriceSync.syncToPlayer(player);
            WarStateSync.syncToPlayer(player);

            LCFtbHook.LOGGER.info(
                    "Dissolved {} personal claims for {} when joining a party (refund {})",
                    unclaimed[0],
                    playerId,
                    refundCopper
            );
        } catch (Exception exception) {
            LCFtbHook.LOGGER.error("Failed to dissolve personal claims for {} on party join", playerId, exception);
        } finally {
            SETTLING.remove(playerId);
        }
    }
}
