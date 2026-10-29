package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.LCFtbHookConfig;

public final class FreeChunkAllowance {
    private FreeChunkAllowance() {
    }

    public static int allowance() {
        try {
            return LCFtbHookConfig.SERVER.freeChunks.get();
        } catch (Throwable error) {
            LCFtbHook.LOGGER.debug("freeChunks config unavailable, defaulting to 0", error);
            return 0;
        }
    }

    /** How many of a team's chunks are left out of protection upkeep - the allowance, or 0 unless freeChunksExemptFromUpkeep is on. */
    public static int upkeepAllowance() {
        try {
            return LCFtbHookConfig.SERVER.freeChunksExemptFromUpkeep.get() ? allowance() : 0;
        } catch (Throwable error) {
            LCFtbHook.LOGGER.debug("freeChunksExemptFromUpkeep config unavailable, charging every chunk", error);
            return 0;
        }
    }

    public static boolean isClaimFree(int currentClaimedChunks) {
        return currentClaimedChunks < allowance();
    }

    public static boolean shouldRefundOnUnclaim(int claimedChunksBeforeUnclaim) {
        return claimedChunksBeforeUnclaim > allowance();
    }

    public static int countPaidClaimsInBatch(int currentClaimedChunks, int newClaims) {
        if (newClaims <= 0) {
            return 0;
        }
        int freeRemaining = Math.max(0, allowance() - currentClaimedChunks);
        return Math.max(0, newClaims - freeRemaining);
    }
}
