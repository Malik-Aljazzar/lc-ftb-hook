package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.Region;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Carries the resolved {@link Region} and {@link ChunkOwnership} of the
 * chunk currently being evaluated down to {@code canPlayerUse} (which only
 * receives the privacy property, not the chunk). Set around the protection
 * check - by {@code shouldPreventInteraction} itself, or with
 * {@link #callWith} around a claim-mod check that asks {@code canPlayerUse}
 * directly - and cleared when it returns.
 */
public final class RegionEnforcementContext {
    public record Resolved(Region region, ChunkOwnership ownership) {
    }

    private static final ThreadLocal<Resolved> CURRENT = new ThreadLocal<>();

    private RegionEnforcementContext() {
    }

    public static void set(Region region, ChunkOwnership ownership) {
        CURRENT.set(new Resolved(region, ownership));
    }

    @Nullable
    public static Resolved get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs {@code action} with {@code context} as the current context and
     * restores whatever was current before afterwards - also when
     * {@code action} throws, and also when this runs inside another
     * protection check, so a check never sees a stale or foreign context.
     */
    public static <T> T callWith(Resolved context, Supplier<T> action) {
        Resolved previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
