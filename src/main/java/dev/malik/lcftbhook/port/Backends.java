package dev.malik.lcftbhook.port;

import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.port.chunkload.ChunkLoadPort;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Single place business logic asks for the currently-active backend
 * implementation of each port - mirrors the same static-locator style this
 * codebase already uses for third-party APIs (e.g. {@code FTBTeamsAPI.api()},
 * {@code FTBChunksAPI.api()}), just for OUR OWN ports instead of a
 * mod-provided one.
 * <p>
 * Which concrete adapter each accessor returns is resolved lazily (on first
 * call, not at class-load time) via {@link BackendSelection} against each
 * port's own priority-ordered candidate list ({@link ClaimBackends},
 * {@link CurrencyBackends}) and the {@code claimBackend}/{@code
 * currencyBackend} config values ({@code AUTO} by default - highest-priority
 * installed mod wins; an explicit value pins one backend even when a
 * higher-priority one is also installed). Lazy resolution matters because
 * config values and the mod list both need to be fully loaded first - a
 * static field initializer here would run too early to see either reliably.
 * <p>
 * <b>Current real-world effect:</b> FTB Teams/Chunks/Library remain hard
 * {@code required} dependencies in {@code neoforge.mods.toml} (see
 * {@code ModCompatibility}), and almost no business logic has been migrated
 * to call {@link #claim()} yet (see {@code ARCHITECTURE.md}'s migration
 * status table) - so today, {@code claim()} under {@code AUTO} will always
 * resolve to the FTB adapter in practice (FTB is always installed), and
 * choosing OPAC explicitly only adds it alongside FTB, not instead of it.
 * This class and its selection logic are already fully correct for the day
 * FTB becomes a genuinely optional dependency - no further change needed
 * here when that happens.
 */
public final class Backends {
    private static CurrencyPort currency;
    private static ClaimPort claim;
    private static ChunkLoadPort chunkLoad;

    private Backends() {
    }

    public static CurrencyPort currency() {
        CurrencyPort selected = currency;
        if (selected == null) {
            String configured = configured(LCFtbHookConfig.SERVER.currencyBackend);
            selected = BackendSelection.select(configured == null ? "AUTO" : configured, CurrencyBackends.CANDIDATES, "currency");
            if (configured != null) {
                currency = selected;
            }
        }
        return selected;
    }

    public static ClaimPort claim() {
        ClaimPort selected = claim;
        if (selected == null) {
            String configured = configured(LCFtbHookConfig.SERVER.claimBackend);
            selected = BackendSelection.select(configured == null ? "AUTO" : configured, ClaimBackends.CANDIDATES, "claim");
            if (configured != null) {
                claim = selected;
            }
        }
        return selected;
    }

    /**
     * Forgets the chosen backends so the next access re-selects - called when
     * a server stops and when a client disconnects, since the next world or
     * server may be configured differently (the server config is synced to
     * clients on join).
     */
    /** The one chunk-loading implementation (the platform's own, not a pluggable mod) - set once at mod construction. */
    public static ChunkLoadPort chunkLoad() {
        if (chunkLoad == null) {
            throw new IllegalStateException("No chunk loading backend registered");
        }
        return chunkLoad;
    }

    public static void setChunkLoad(ChunkLoadPort port) {
        chunkLoad = port;
    }

    public static void reset() {
        currency = null;
        claim = null;
    }

    /**
     * Null while the server config isn't loaded yet (early in server startup,
     * or on a client that hasn't joined a world) - callers then use AUTO for
     * that one call without caching it, so the real setting wins once loaded.
     */
    @javax.annotation.Nullable
    private static String configured(ModConfigSpec.ConfigValue<String> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return null;
        }
    }
}
