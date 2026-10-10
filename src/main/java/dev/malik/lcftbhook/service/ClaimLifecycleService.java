package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.bank.ServerAccountDeposit;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * This mod's rules around claiming and unclaiming - price, free allowance,
 * spending rights, refunds, region/ownership cleanup - independent of which
 * claim mod is installed. Claim adapters call these hooks from their own
 * events (FTB Chunks' ClaimedChunkEvent, OPAC's claim action listener) and
 * translate the {@link ClaimDecision}; they also pass the team's claimed-chunk
 * count from BEFORE the change, since backends differ in when their own
 * counts update.
 */
public final class ClaimLifecycleService {
    private ClaimLifecycleService() {
    }

    // ---- claim -------------------------------------------------------------

    /**
     * Check and charge in one go - for backends whose pre-claim event is the
     * last word (FTB Chunks). {@code claimingTeamId} is the team the chunk is
     * claimed for (an admin may claim for another team, e.g. a server team);
     * null means the actor's own team.
     */
    public static ClaimDecision beforeClaim(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID claimingTeamId, int countBeforeClaim) {
        ClaimDecision decision = checkClaim(server, actor, claimingTeamId, countBeforeClaim);
        if (decision.kind() == ClaimDecision.Kind.ALLOW) {
            chargeClaim(server, actor, claimingTeamId, countBeforeClaim);
        }
        return decision;
    }

    public static ClaimDecision checkClaim(MinecraftServer server, @Nullable ServerPlayer actor, int countBeforeClaim) {
        return checkClaim(server, actor, null, countBeforeClaim);
    }

    public static boolean chargeClaim(MinecraftServer server, @Nullable ServerPlayer actor, int countBeforeClaim) {
        return chargeClaim(server, actor, null, countBeforeClaim);
    }

    /**
     * Rights and affordability only, nothing charged - for backends that may
     * still refuse the claim after this check (OPAC's own limits), which then
     * call {@link #chargeClaim} once the claim actually succeeded.
     */
    public static ClaimDecision checkClaim(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID claimingTeamId, int countBeforeClaim) {
        UUID teamId = claimingTeam(server, actor, claimingTeamId);
        if (isServerTeam(server, teamId)) {
            return ClaimDecision.ALLOW; // admin-run and free - the claim mod already checked the admin's permission
        }
        boolean ownTeam = actsForOwnTeam(server, actor, teamId);
        if (ownTeam && !mayClaimForTeam(server, actor, teamId)) {
            return ClaimDecision.deny("message.lc_ftb_hook.claim_rank_denied");
        }
        ClaimCharge charge = pendingCharge(actor, teamId, countBeforeClaim);
        if (charge == null) {
            return ClaimDecision.ALLOW;
        }
        if (ownTeam && !Backends.currency().canPlayerSpend(server, charge.teamId(), actor.getUUID())) {
            return ClaimDecision.deny("message.lc_ftb_hook.claim_rank_denied");
        }
        if (charge.price() <= 0) {
            return ClaimDecision.ALLOW;
        }
        Backends.currency().ensureTeamAccountExists(server, charge.teamId());
        CurrencyAccount account = Backends.currency().teamAccount(server, charge.teamId());
        return account.canAfford(charge.price()) ? ClaimDecision.ALLOW : reportInsufficientFunds(actor, account, charge.price());
    }

    /** Pays for a claim that was checked with {@link #checkClaim}. Returns false if the team can no longer afford it. */
    public static boolean chargeClaim(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID claimingTeamId, int countBeforeClaim) {
        UUID teamId = claimingTeam(server, actor, claimingTeamId);
        if (isServerTeam(server, teamId)) {
            return true;
        }
        ClaimCharge charge = pendingCharge(actor, teamId, countBeforeClaim);
        if (charge == null || charge.price() <= 0) {
            return true;
        }
        CurrencyAccount account = Backends.currency().teamAccount(server, charge.teamId());
        if (!account.canAfford(charge.price())) {
            return false;
        }
        pay(actor, charge.teamId(), account, charge.price());
        return true;
    }

    private record ClaimCharge(UUID teamId, long price) {
    }

    /** Only a party's officers and owner may claim for it - free chunks included. */
    private static boolean mayClaimForTeam(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID teamId) {
        return actor == null || teamId == null || !Backends.claim().isPartyTeam(server, teamId)
                || Backends.claim().rankOf(server, teamId, actor.getUUID()).isOfficerOrBetter();
    }

    /** The team a claim/unclaim is for: the one the claim mod names, else the actor's own. */
    @Nullable
    private static UUID claimingTeam(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID claimingTeamId) {
        if (claimingTeamId != null) {
            return claimingTeamId;
        }
        return actor == null ? null : TeamCatalog.teamOf(server, actor.getUUID());
    }

    /**
     * False when an admin acts for a team that isn't their own (the claim mod
     * only allows that to admins): the team's rank and spending rules then
     * don't apply to the admin - but the team still pays or is refunded.
     */
    private static boolean actsForOwnTeam(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID teamId) {
        return actor == null || teamId == null || teamId.equals(TeamCatalog.teamOf(server, actor.getUUID()));
    }

    private static boolean isServerTeam(MinecraftServer server, @Nullable UUID teamId) {
        return teamId != null && Backends.claim().isServerTeam(server, teamId);
    }

    /** Null when nothing is owed: a free claim, a dry validation run, no player, or no team. */
    @Nullable
    private static ClaimCharge pendingCharge(@Nullable ServerPlayer actor, @Nullable UUID teamId, int countBeforeClaim) {
        if (FreeChunkAllowance.isClaimFree(countBeforeClaim) || ClaimBatchContext.isValidating() || actor == null || teamId == null) {
            return null;
        }
        return new ClaimCharge(teamId, ClaimPricingService.priceForClaim(countBeforeClaim));
    }

    private static void pay(ServerPlayer actor, UUID teamId, CurrencyAccount account, long price) {
        account.withdraw(price, "Claim Payment");
        ServerAccountDeposit.deposit(actor.server, price, WarService.displayName(actor.server, teamId) + " (claim)");
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordClaimSpend(price);
        } else {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.claim_paid", Money.formatValue(price)), false);
            ClaimPriceSync.syncToPlayer(actor);
        }
    }

    private static ClaimDecision reportInsufficientFunds(ServerPlayer actor, CurrencyAccount account, long price) {
        Component message = Component.translatable("message.lc_ftb_hook.insufficient_funds",
                Money.formatValue(price), Money.formatBalance(account));
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordClaimInsufficientFunds(account, price);
        } else {
            actor.displayClientMessage(message, false);
            ClaimPriceSync.syncToPlayer(actor);
        }
        return ClaimDecision.deny("message.lc_ftb_hook.insufficient_funds", message);
    }

    public static void afterClaim(MinecraftServer server, @Nullable ServerPlayer actor, UUID claimingTeamId, String chunkKey, int countBeforeClaim) {
        if (isServerTeam(server, claimingTeamId)) {
            // Protected by the claim mod's own team settings right away - nothing to settle.
            if (!ClaimBatchContext.isExecuting()) {
                syncClaimUi(actor);
            }
            return;
        }
        recordFreshClaim(server, claimingTeamId, chunkKey);
        if (ClaimBatchContext.isExecuting()) {
            if (FreeChunkAllowance.isClaimFree(countBeforeClaim)) {
                ClaimBatchContext.recordClaimFree();
            }
            return;
        }
        syncClaimUi(actor);
    }

    /**
     * A fresh claim has no protection until the next successful upkeep
     * settlement, and can't be listed on the marketplace until then either
     * (a freshly BOUGHT chunk can be resold right away - different path).
     */
    private static void recordFreshClaim(MinecraftServer server, UUID teamId, String chunkKey) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        savedData.markChunkUnsettled(teamId, chunkKey);
        savedData.markChunkFreshlyClaimed(teamId, chunkKey);
        // One broadcast per bulk claim (ClaimBatchContext.flush) instead of one per chunk.
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordUnsettledChunk();
        } else {
            MarketplaceService.broadcastUnsettledChunks(server);
        }
    }

    // ---- unclaim -----------------------------------------------------------

    /** Unclaiming needs the same spending rights as claiming (it pays out a refund). */
    public static ClaimDecision beforeUnclaim(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID teamId) {
        if (actor == null || teamId == null || isServerTeam(server, teamId) || !actsForOwnTeam(server, actor, teamId)
                || Backends.currency().canPlayerSpend(server, teamId, actor.getUUID())) {
            return ClaimDecision.ALLOW;
        }
        return ClaimDecision.deny("message.lc_ftb_hook.claim_rank_denied");
    }

    public static void afterUnclaim(MinecraftServer server, @Nullable ServerPlayer actor, @Nullable UUID teamId, String chunkKey, int countBeforeUnclaim) {
        RegionService.onChunkUnclaimed(server, teamId, chunkKey);
        long refund = isServerTeam(server, teamId) ? 0L : unclaimRefund(countBeforeUnclaim); // a server team paid nothing
        if (refund <= 0 || teamId == null) {
            recordOrSyncNoRefund(actor);
            return;
        }
        CurrencyAccount account = refundAccount(server, teamId);
        if (account == null) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(0);
            }
            return;
        }
        account.deposit(refund, "Claim Refund");
        reportRefund(actor, refund);
    }

    private static long unclaimRefund(int countBeforeUnclaim) {
        if (!FreeChunkAllowance.shouldRefundOnUnclaim(countBeforeUnclaim)) {
            return 0L;
        }
        long claimPrice = ClaimPricingService.priceForClaim(countBeforeUnclaim - 1);
        double ratio = LCFtbHookConfig.SERVER.unclaimRefundRatio.get();
        return claimPrice <= 0 || ratio <= 0 ? 0L : (long) Math.floor(claimPrice * ratio);
    }

    /**
     * The team's own account, unless a specific player is owed a personal
     * refund instead (e.g. their solo claims dissolving as they join a party -
     * see {@link ClaimBatchContext#personalRefundPlayerId()}).
     */
    @Nullable
    private static CurrencyAccount refundAccount(MinecraftServer server, UUID teamId) {
        Backends.currency().ensureTeamAccountExists(server, teamId);
        UUID personalRefundPlayer = ClaimBatchContext.personalRefundPlayerId();
        try {
            return personalRefundPlayer != null
                    ? Backends.currency().playerAccount(server, personalRefundPlayer)
                    : Backends.currency().teamAccount(server, teamId);
        } catch (IllegalStateException e) {
            LCFtbHook.LOGGER.warn("Missing bank account for unclaim refund: {}", e.getMessage());
            return null;
        }
    }

    private static void recordOrSyncNoRefund(@Nullable ServerPlayer actor) {
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordUnclaim(0);
        } else if (!ClaimBatchContext.suppressNotifications()) {
            syncClaimUi(actor);
        }
    }

    private static void reportRefund(@Nullable ServerPlayer actor, long refund) {
        if (actor == null) {
            return;
        }
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordUnclaim(refund);
            return;
        }
        if (!ClaimBatchContext.suppressNotifications()) {
            int percent = (int) Math.round(LCFtbHookConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.unclaim_refund", Money.formatValue(refund), percent), false);
            syncClaimUi(actor);
        }
    }

    private static void syncClaimUi(@Nullable ServerPlayer actor) {
        if (actor != null) {
            ClaimPriceSync.syncToPlayer(actor);
        }
    }
}
