package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.LCFtbHookConfig;

/**
 * The curve behind the "upkeep discount for large chunk amounts": how many
 * chunks a protection setting covering {@code n} chunks (team-wide) is billed
 * as. Up to {@code threshold} chunks exactly {@code n}; above it
 * {@code T + f*(n-T) + (1-f) * s * ln(1 + (n-T)/s)}.
 * <p>
 * Properties every caller relies on (covered by {@code UpkeepDecelerationTest}):
 * never more than {@code n}; strictly increasing (an extra chunk always costs
 * something, so more land never pays less); concave with {@code E(0) = 0},
 * hence subadditive (splitting never pays less than keeping together);
 * continuous with slope 1 at the threshold (no cliff to game).
 *
 * @param threshold chunks billed at the full price
 * @param scale     how fast the price flattens above the threshold (chunks, > 0)
 * @param floor     least share of the full price each extra chunk costs (0..1)
 */
public record UpkeepDeceleration(boolean enabled, int threshold, double scale, double floor) {
    public static final UpkeepDeceleration OFF = new UpkeepDeceleration(false, 0, 1.0D, 1.0D);

    public UpkeepDeceleration {
        threshold = Math.max(0, threshold);
        scale = Double.isFinite(scale) && scale >= 1.0D ? scale : 1.0D;
        floor = Double.isFinite(floor) ? Math.min(1.0D, Math.max(0.0D, floor)) : 1.0D;
    }

    /** The billed chunk count for {@code chunks} chunks of one protection setting. */
    public double effectiveChunks(long chunks) {
        if (!enabled || chunks <= threshold) {
            return Math.max(0L, chunks);
        }
        double extra = chunks - (double) threshold;
        return threshold + floor * extra + (1.0D - floor) * scale * Math.log1p(extra / scale);
    }

    /**
     * Unrounded copper for one protection setting: {@code price} is the
     * setting's config price (per {@code unitChunks} chunks), billed for the
     * effective chunk count.
     */
    public double settingCopper(long price, long chunks, long unitChunks) {
        return price * effectiveChunks(chunks) / unitChunks;
    }

    /** The server's current settings; OFF if the config isn't available (e.g. on a client). */
    public static UpkeepDeceleration configured() {
        try {
            LCFtbHookConfig.Server c = LCFtbHookConfig.SERVER;
            return new UpkeepDeceleration(c.upkeepDeceleration.get(), c.upkeepDecelerationThreshold.get(),
                    c.upkeepDecelerationScale.get(), c.upkeepDecelerationFloor.get());
        } catch (RuntimeException e) {
            LCFtbHook.LOGGER.debug("upkeepDeceleration config unavailable, billing linearly", e);
            return OFF;
        }
    }
}
