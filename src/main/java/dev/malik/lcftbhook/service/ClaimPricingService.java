package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.config.LCFtbHookConfig;

/**
 * Central place computing the copper cost of a team's next chunk claim (or
 * the refund for unclaiming one), as a function of how many chunks they
 * already have - see {@code claimPricingMode}'s config comment for the
 * exact CONSTANT/LINEAR/EXPONENTIAL formulas. Every caller that used to
 * read {@code LCFtbHookConfig.SERVER.claimPrice.get()} directly for a
 * per-chunk price now goes through here instead, so all of them (single
 * claim, bulk claim, unclaim refund, party disband/join settlement, and the
 * client-facing price display) stay consistent with whichever pricing mode
 * is configured.
 */
public final class ClaimPricingService {
    private ClaimPricingService() {
    }

    /** Price to claim the chunk that would become a team's {@code (chunksAlreadyClaimed + 1)}-th chunk - ignores the free-chunk allowance (see {@link FreeChunkAllowance}), callers gate that separately. */
    public static long priceForClaim(int chunksAlreadyClaimed) {
        int increment = incrementFor(Math.max(0, chunksAlreadyClaimed));
        return priceForIncrement(increment);
    }

    /**
     * Sum of {@link #priceForClaim} over the {@code count} chunk indices
     * starting at {@code startIndex} (i.e. {@code [startIndex, startIndex +
     * count)}), skipping any index covered by the free-chunk allowance -
     * for bulk claims/refunds spanning multiple price tiers at once.
     */
    public static long sumPriceForRange(int startIndex, int count) {
        long total = 0L;
        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            if (FreeChunkAllowance.isClaimFree(index)) {
                continue;
            }
            total += priceForClaim(index);
        }
        return total;
    }

    /**
     * The free-chunk allowance doesn't count toward increments - increment 0
     * always covers exactly {@code claimIncrementSize} PAID claims,
     * regardless of how many free ones came before them. E.g. with 10 free
     * chunks and an increment size of 20, the price only steps up to
     * increment 1 once 30 chunks have been claimed in total (10 free + the
     * first 20 paid), not 20.
     */
    private static int incrementFor(int chunksAlreadyClaimed) {
        int size = LCFtbHookConfig.SERVER.claimIncrementSize.get();
        if (size <= 0) {
            return 0;
        }
        int paidChunksAlreadyClaimed = Math.max(0, chunksAlreadyClaimed - FreeChunkAllowance.allowance());
        return paidChunksAlreadyClaimed / size;
    }

    /**
     * All three modes compute in TENTHS of a copper (the config's own base
     * unit - see {@code LCFtbHookConfig}'s comment at the top of
     * {@code [general]}); the single {@code /10} floor-divide at the end
     * converts to actual whole copper exactly once, avoiding compounding
     * rounding error from converting each intermediate term separately.
     */
    private static long priceForIncrement(int increment) {
        long tenths = switch (LCFtbHookConfig.SERVER.claimPricingMode.get()) {
            case CONSTANT -> LCFtbHookConfig.SERVER.claimPrice.get();
            case LINEAR -> {
                long base = LCFtbHookConfig.SERVER.claimBasePrice.get();
                long incrementPrice = LCFtbHookConfig.SERVER.claimIncrementPrice.get();
                yield base + (long) increment * incrementPrice;
            }
            case EXPONENTIAL -> {
                double base = LCFtbHookConfig.SERVER.claimBasePrice.get();
                double percent = LCFtbHookConfig.SERVER.claimPriceGrowthPercent.get();
                yield (long) Math.floor(base * Math.pow(1.0D + percent, increment));
            }
        };
        return Math.floorDiv(tenths, 10L);
    }
}
