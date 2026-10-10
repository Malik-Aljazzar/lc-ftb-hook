package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public final class ClaimSettlementHelper {
    private ClaimSettlementHelper() {
    }

    /** Total refund for unclaiming ALL {@code claimedChunksBeforeUnclaim} chunks a team/player currently holds, summed per-chunk over whichever price tiers those chunks actually fall into (see {@link ClaimPricingService}), then the usual refund ratio applied once to the total. */
    public static long refundForFullUnclaim(int claimedChunksBeforeUnclaim) {
        double refundRatio = LCFtbHookConfig.SERVER.unclaimRefundRatio.get();
        if (refundRatio <= 0) {
            return 0;
        }
        long total = ClaimPricingService.sumPriceForRange(0, claimedChunksBeforeUnclaim);
        return (long) Math.floor(total * refundRatio);
    }

    /** Unclaims every given chunk through the claim backend's normal unclaim path (so refunds and region cleanup run), without per-chunk chat spam. */
    public static int unclaimAll(MinecraftServer server, List<ClaimPort.ChunkKey> chunks) {
        return unclaimAll(server, chunks, true);
    }

    public static int unclaimAll(MinecraftServer server, List<ClaimPort.ChunkKey> chunks, boolean suppressNotifications) {
        if (chunks.isEmpty()) {
            return 0;
        }
        int[] unclaimed = {0};
        Runnable action = () -> {
            for (ClaimPort.ChunkKey chunk : List.copyOf(chunks)) {
                if (Backends.claim().unclaim(server, chunk)) {
                    unclaimed[0]++;
                }
            }
        };
        if (suppressNotifications) {
            ClaimBatchContext.runSuppressingNotifications(action);
        } else {
            action.run();
        }
        return unclaimed[0];
    }
}
