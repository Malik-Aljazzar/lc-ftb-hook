package dev.malik.lcftbhook.util;

import dev.malik.lcftbhook.LCFtbHook;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Wraps every entry point another mod (or the game) calls into: an exception
 * of ours must never escape into the caller - in 4.0.0 one escaping from an
 * FTB Teams event aborted FTB's whole team loading. A failure is logged (full
 * stack trace the first time per entry point) and the caller gets a fallback
 * chosen per entry point: "leave the other mod's behavior untouched", or -
 * where land, money or deletions are at stake - "do nothing".
 */
public final class Guard {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private Guard() {
    }

    public static void run(String where, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            report(where, e);
        }
    }

    public static <T> T call(String where, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException | LinkageError e) {
            report(where, e);
            return fallback;
        }
    }

    private static void report(String where, Throwable e) {
        if (REPORTED.add(where + "/" + e.getClass().getName())) {
            LCFtbHook.LOGGER.error("{} failed - fell back to the safe default", where, e);
        } else {
            LCFtbHook.LOGGER.error("{} failed again: {}", where, e.toString());
        }
    }
}
