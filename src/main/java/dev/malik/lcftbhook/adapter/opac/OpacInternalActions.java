package dev.malik.lcftbhook.adapter.opac;

import java.util.function.BooleanSupplier;

/**
 * Marks claim actions this mod performs itself through OPAC (settlement
 * toggling force-loads, unclaiming a disbanded team's land). OPAC runs those
 * through the same claim action listeners as player actions, and
 * {@link OpacClaimListener} must not treat them as player requests (no
 * rights check, no queueing) - only the "after" bookkeeping still applies.
 */
final class OpacInternalActions {
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

    private OpacInternalActions() {
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    static boolean run(BooleanSupplier action) {
        boolean previous = RUNNING.get();
        RUNNING.set(true);
        try {
            return action.getAsBoolean();
        } finally {
            RUNNING.set(previous);
        }
    }
}
