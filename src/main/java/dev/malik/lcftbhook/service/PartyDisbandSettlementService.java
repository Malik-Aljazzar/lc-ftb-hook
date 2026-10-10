package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.ClaimSettlementContext;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyDisbandSettlementService {
    private static final Set<UUID> SETTLING = ConcurrentHashMap.newKeySet();

    private PartyDisbandSettlementService() {
    }

    /**
     * @param claimedChunks the party's chunks, captured by the claim adapter
     *                      from its own party object - the backend may have
     *                      already unregistered the party itself by now
     */
    public static void settle(MinecraftServer server, UUID teamId, @Nullable UUID ownerId, List<ClaimPort.ChunkKey> claimedChunks) {
        if (!SETTLING.add(teamId)) {
            return;
        }

        try {
            // War cleanup and pending state must be cleared regardless of whether
            // account transfer succeeds — otherwise disbanded teams leave orphaned
            // war references and pending protection entries in SavedData.
            FtbHookSavedData savedData = FtbHookSavedData.get(server);
            WarService.cleanupTeamWars(server, teamId);
            savedData.setPendingState(teamId, savedData.getPendingState(teamId).cleared());

            if (ownerId == null) {
                return;
            }

            CurrencyPort currency = Backends.currency();
            CurrencyAccount teamAccount;
            CurrencyAccount ownerAccount;
            try {
                teamAccount = currency.teamAccount(server, teamId);
                ownerAccount = currency.playerAccount(server, ownerId);
            } catch (IllegalStateException missing) {
                LCFtbHook.LOGGER.warn("Could not transfer party funds for {}: {}", teamId, missing.getMessage());
                return;
            }

            Component accountBalance = Money.formatBalance(teamAccount);
            PendingChangeService.removeAllForceLoads(server, teamId);
            int[] unclaimed = {0};
            ClaimSettlementContext.run(teamId, claimedChunks.size(), () ->
                    unclaimed[0] = ClaimSettlementHelper.unclaimAll(server, claimedChunks));
            int unclaimedChunks = unclaimed[0];
            long refundCopper = ClaimSettlementHelper.refundForFullUnclaim(claimedChunks.size());

            if (teamAccount.isEmpty() && unclaimedChunks == 0) {
                return;
            }

            Component totalReceived = Money.formatBalance(teamAccount);
            teamAccount.transferAllTo(ownerAccount, "Party Disbanded");
            notifyOwner(server, ownerId, accountBalance, totalReceived, unclaimedChunks, refundCopper);
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                WarStateSync.syncToPlayer(owner);
            }
            LCFtbHook.LOGGER.info(
                    "Settled disbanded party {} for owner {} ({} chunks, {} refund copper)",
                    teamId,
                    ownerId,
                    unclaimedChunks,
                    refundCopper
            );
        } catch (Exception exception) {
            LCFtbHook.LOGGER.error("Failed to settle disbanded party {}", teamId, exception);
        } finally {
            SETTLING.remove(teamId);
        }
    }

    private static void notifyOwner(
            MinecraftServer server,
            UUID ownerId,
            Component accountBalance,
            Component totalReceived,
            int soldChunks,
            long refundCopper
    ) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        Component chunkRefunds = Money.formatValue(refundCopper);
        owner.displayClientMessage(
                Component.translatable(
                        "message.lc_ftb_hook.party_disband_settlement",
                        accountBalance,
                        totalReceived,
                        soldChunks,
                        chunkRefunds
                ),
                false
        );
    }
}
