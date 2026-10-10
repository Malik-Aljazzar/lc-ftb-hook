package dev.malik.lcftbhook.bank;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Which team the unclaims of a running settlement (party join/disband)
 * belong to - for claim backends that can't resolve that team from the chunk
 * itself any more (OPAC claims belong to players, and a disbanded party is
 * already gone when its land is released).
 */
public final class ClaimSettlementContext {
    private static final ThreadLocal<SettlementTeam> SETTLEMENT_TEAM = new ThreadLocal<>();

    private ClaimSettlementContext() {
    }

    /** Unclaims inside {@code action} belong to {@code teamId}, which held {@code chunkCount} chunks beforehand. */
    public static void run(UUID teamId, int chunkCount, Runnable action) {
        SETTLEMENT_TEAM.set(new SettlementTeam(teamId, chunkCount));
        try {
            action.run();
        } finally {
            SETTLEMENT_TEAM.remove();
        }
    }

    @Nullable
    public static SettlementTeam current() {
        return SETTLEMENT_TEAM.get();
    }

    public static final class SettlementTeam {
        private final UUID teamId;
        private int remaining;

        private SettlementTeam(UUID teamId, int chunkCount) {
            this.teamId = teamId;
            this.remaining = chunkCount;
        }

        public UUID teamId() {
            return teamId;
        }

        /** The team's chunk count before the next unclaim (counts down per unclaim). */
        public int nextCountBefore() {
            return Math.max(remaining--, 0);
        }
    }
}
