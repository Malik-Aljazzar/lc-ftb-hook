package dev.malik.lcftbhook.bank;

import java.util.Locale;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.util.Money;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public final class ClaimBatchContext {
    /** A bulk chunk action, independent of which claim mod issued it. */
    public enum Operation {
        CLAIM, UNCLAIM, LOAD, UNLOAD, OTHER;

        /** Maps a backend operation name (e.g. FTB Chunks' ChunkChangeOp) by name. */
        public static Operation of(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return OTHER;
            }
        }
    }

    private static final ThreadLocal<Boolean> VALIDATING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SUPPRESS_NOTIFICATIONS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<UUID> PERSONAL_REFUND_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<BatchState> EXECUTING = new ThreadLocal<>();

    private ClaimBatchContext() {
    }

    public static boolean isValidating() {
        return VALIDATING.get();
    }

    public static void beginValidation() {
        VALIDATING.set(true);
    }

    public static void endValidation() {
        VALIDATING.remove();
    }

    public static boolean isExecuting() {
        return EXECUTING.get() != null;
    }

    public static boolean suppressNotifications() {
        return SUPPRESS_NOTIFICATIONS.get();
    }

    public static void runSuppressingNotifications(Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
        }
    }

    public static void runPersonalRefundSettlement(UUID playerId, Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        PERSONAL_REFUND_PLAYER.set(playerId);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
            PERSONAL_REFUND_PLAYER.remove();
        }
    }

    @Nullable
    public static UUID personalRefundPlayerId() {
        return PERSONAL_REFUND_PLAYER.get();
    }

    public static void beginExecution(Operation operation, int chunkCount, UUID playerId) {
        if (chunkCount <= 1) {
            return;
        }
        if (operation != Operation.CLAIM
                && operation != Operation.UNCLAIM) {
            return;
        }
        EXECUTING.set(new BatchState(operation, playerId));
    }

    /** Parks the running batch (between the ticks of a spread-out area action); hand the result back to {@link #resume}. */
    @Nullable
    public static Object suspend() {
        BatchState state = EXECUTING.get();
        EXECUTING.remove();
        return state;
    }

    public static void resume(@Nullable Object batch) {
        if (batch instanceof BatchState state) {
            EXECUTING.set(state);
        }
    }

    public static void recordClaimSpend(long priceCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimPaidCopper += priceCopper;
        state.claimPaidCount++;
        state.uiSyncNeeded = true;
    }

    public static void recordClaimFree() {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimFreeCount++;
        state.uiSyncNeeded = true;
    }

    /** A chunk in this batch became unsettled (see {@code FtbHookSavedData#markChunkUnsettled}) - broadcast once in {@link #flush}, not once per chunk. */
    public static void recordUnsettledChunk() {
        BatchState state = EXECUTING.get();
        if (state != null) {
            state.unsettledBroadcastNeeded = true;
        }
    }

    public static void recordUnclaim(long refundCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.unclaimCount++;
        state.refundCopper += refundCopper;
        state.uiSyncNeeded = true;
    }

    public static void recordClaimInsufficientFunds(CurrencyAccount account, long unitPriceCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimInsufficientCount++;
        state.claimUnitPriceCopper = unitPriceCopper;
        state.insufficientBalance = Money.formatBalance(account);
        state.uiSyncNeeded = true;
    }

    public static void flush(@Nullable ServerPlayer player) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }

        try {
            if (player == null || !player.getUUID().equals(state.playerId)) {
                return;
            }

            if (state.operation == Operation.UNCLAIM && state.unclaimCount > 0) {
                sendUnclaimSummary(player, state);
            }

            if (state.operation == Operation.CLAIM) {
                sendClaimSummary(player, state);
            }

            if (state.uiSyncNeeded) {
                ClaimPriceSync.syncToPlayer(player);
            }
            if (state.unsettledBroadcastNeeded) {
                dev.malik.lcftbhook.service.MarketplaceService.broadcastUnsettledChunks(player.server);
            }
        } finally {
            EXECUTING.remove();
        }
    }

    private static void sendUnclaimSummary(ServerPlayer player, BatchState state) {
        int refundPercent = refundPercent();
        if (state.refundCopper > 0) {
            Component refund = dev.malik.lcftbhook.util.Money.formatValue(state.refundCopper);
            if (state.unclaimCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.unclaim_refund", refund, refundPercent),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_ftb_hook.unclaim_refund_bulk",
                                refund,
                                state.unclaimCount,
                                refundPercent
                        ),
                        false
                );
            }
            return;
        }

        if (state.unclaimCount == 1) {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.unclaim_bulk_single"),
                    false
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.unclaim_bulk", state.unclaimCount),
                    false
            );
        }
    }

    private static void sendClaimSummary(ServerPlayer player, BatchState state) {
        if (state.claimInsufficientCount > 0) {
            Component unitPrice = dev.malik.lcftbhook.util.Money.formatValue(state.claimUnitPriceCopper);
            Component balance = state.insufficientBalance == null
                    ? Component.translatable("message.lc_ftb_hook.balance_empty")
                    : state.insufficientBalance;
            if (state.claimInsufficientCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.insufficient_funds", unitPrice, balance),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_ftb_hook.insufficient_funds_bulk_claim",
                                unitPrice,
                                state.claimInsufficientCount,
                                balance
                        ),
                        false
                );
            }
        }

        int claimedCount = state.claimPaidCount + state.claimFreeCount;
        if (claimedCount <= 0) {
            return;
        }

        if (state.claimPaidCopper > 0) {
            Component spent = dev.malik.lcftbhook.util.Money.formatValue(state.claimPaidCopper);
            if (claimedCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.claim_paid", spent),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable("message.lc_ftb_hook.claim_paid_bulk", spent, claimedCount),
                        false
                );
            }
            return;
        }

        if (claimedCount == 1) {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.claim_free"),
                    false
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.claim_free_bulk", claimedCount),
                    false
            );
        }
    }

    private static int refundPercent() {
        return (int) Math.round(LCFtbHookConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
    }

    private static final class BatchState {
        private final Operation operation;
        private final UUID playerId;
        private long refundCopper;
        private int unclaimCount;
        private long claimPaidCopper;
        private int claimPaidCount;
        private int claimFreeCount;
        private int claimInsufficientCount;
        private long claimUnitPriceCopper;
        @Nullable
        private Component insufficientBalance;
        private boolean uiSyncNeeded;
        private boolean unsettledBroadcastNeeded;

        private BatchState(Operation operation, UUID playerId) {
            this.operation = operation;
            this.playerId = playerId;
        }
    }
}
