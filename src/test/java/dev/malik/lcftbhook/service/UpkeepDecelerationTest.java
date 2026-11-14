package dev.malik.lcftbhook.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The properties the "upkeep discount for large chunk amounts" relies on to be
 * unexploitable, checked over a spread of curve settings.
 */
class UpkeepDecelerationTest {
    private static final List<UpkeepDeceleration> CURVES = List.of(
            new UpkeepDeceleration(true, 64, 128.0D, 0.0D),
            new UpkeepDeceleration(true, 64, 128.0D, 0.15D),
            new UpkeepDeceleration(true, 0, 1.0D, 0.0D),
            new UpkeepDeceleration(true, 100, 200.0D, 0.5D),
            new UpkeepDeceleration(true, 1, 7.5D, 0.99D)
    );
    private static final int MAX_CHUNKS = 20_000;

    @Test
    void offAndFloorOneAreLinear() {
        UpkeepDeceleration floorOne = new UpkeepDeceleration(true, 10, 50.0D, 1.0D);
        for (long n = 0; n <= 5_000; n++) {
            assertEquals(n, UpkeepDeceleration.OFF.effectiveChunks(n), 1e-9);
            assertEquals(n, new UpkeepDeceleration(false, 64, 128.0D, 0.0D).effectiveChunks(n), 1e-9);
            assertEquals(n, floorOne.effectiveChunks(n), 1e-6);
        }
    }

    @Test
    void fullPriceUpToTheThreshold() {
        for (UpkeepDeceleration curve : CURVES) {
            for (long n = 0; n <= curve.threshold(); n++) {
                assertEquals(n, curve.effectiveChunks(n), 1e-9, curve + " n=" + n);
            }
        }
    }

    @Test
    void neverMoreThanLinearAndStrictlyIncreasing() {
        for (UpkeepDeceleration curve : CURVES) {
            double previous = curve.effectiveChunks(0);
            for (long n = 1; n <= MAX_CHUNKS; n++) {
                double current = curve.effectiveChunks(n);
                assertTrue(current <= n + 1e-9, curve + " exceeds linear at n=" + n);
                assertTrue(current > previous, curve + " not increasing at n=" + n);
                previous = current;
            }
        }
    }

    @Test
    void marginalCostNeverRises() {
        // Concave: each extra chunk costs at most as much as the one before.
        for (UpkeepDeceleration curve : CURVES) {
            double lastStep = Double.MAX_VALUE;
            for (long n = 1; n <= MAX_CHUNKS; n++) {
                double step = curve.effectiveChunks(n) - curve.effectiveChunks(n - 1);
                assertTrue(step <= lastStep + 1e-9, curve + " marginal cost rises at n=" + n);
                lastStep = step;
            }
        }
    }

    @Test
    void splittingNeverPaysLess() {
        // Subadditive: E(a + b) <= E(a) + E(b) - one big holding never costs more than two parts.
        long[] sizes = {0, 1, 5, 63, 64, 65, 100, 128, 250, 999, 1000, 4096, 10_000};
        for (UpkeepDeceleration curve : CURVES) {
            for (long a : sizes) {
                for (long b : sizes) {
                    assertTrue(curve.effectiveChunks(a + b) <= curve.effectiveChunks(a) + curve.effectiveChunks(b) + 1e-9,
                            curve + " a=" + a + " b=" + b);
                }
            }
        }
    }

    @Test
    void noCliffAtTheThreshold() {
        for (UpkeepDeceleration curve : CURVES) {
            long t = curve.threshold();
            double step = curve.effectiveChunks(t + 1) - curve.effectiveChunks(t);
            assertTrue(step > 0.5D && step <= 1.0D + 1e-9, curve + " step at threshold " + step);
        }
    }

    @Test
    void floorIsTheLeastShareAnExtraChunkCosts() {
        UpkeepDeceleration curve = new UpkeepDeceleration(true, 64, 128.0D, 0.15D);
        double step = curve.effectiveChunks(1_000_001) - curve.effectiveChunks(1_000_000);
        assertTrue(step >= 0.15D && step < 0.16D, "step " + step);
    }

    @Test
    void documentedExampleValues() {
        UpkeepDeceleration curve = new UpkeepDeceleration(true, 64, 128.0D, 0.0D);
        assertEquals(156.7D, curve.effectiveChunks(200), 0.05D);
        assertEquals(253.8D, curve.effectiveChunks(500), 0.05D);
        assertEquals(335.1D, curve.effectiveChunks(1000), 0.05D);
    }

    @Test
    void invalidSettingsAreClampedSafe() {
        UpkeepDeceleration curve = new UpkeepDeceleration(true, -5, Double.NaN, 7.0D);
        assertEquals(0, curve.threshold());
        assertEquals(1.0D, curve.scale());
        assertEquals(1.0D, curve.floor());
        assertEquals(42.0D, curve.effectiveChunks(42), 1e-9);
        assertEquals(0.0D, curve.effectiveChunks(-3), 1e-9);
    }
}
