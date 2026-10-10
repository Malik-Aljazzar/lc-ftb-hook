package dev.malik.lcftbhook.adapter.lightmanscurrency;

public final class LcTeamDeletionGuard {
    private static final ThreadLocal<Boolean> ALLOW = ThreadLocal.withInitial(() -> false);

    private LcTeamDeletionGuard() {
    }

    public static boolean isAllowed() {
        return Boolean.TRUE.equals(ALLOW.get());
    }

    public static void runAllowed(Runnable action) {
        ALLOW.set(true);
        try {
            action.run();
        } finally {
            ALLOW.remove();
        }
    }
}
