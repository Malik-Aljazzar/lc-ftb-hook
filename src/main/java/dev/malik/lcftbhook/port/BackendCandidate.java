package dev.malik.lcftbhook.port;

import net.neoforged.fml.ModList;

import java.util.function.Supplier;

/**
 * One selectable backend implementation for a port: a short config-matchable
 * id, the mod it needs installed, and how to construct it. A port's own
 * candidate list (e.g. {@code ClaimBackends.CANDIDATES},
 * {@code CurrencyBackends.CANDIDATES}) declares these in descending priority
 * order - {@link BackendSelection} picks the first one whose mod is actually
 * installed under {@code AUTO}. Adding a future backend (another claim mod,
 * another currency mod, a map mod) means adding one more candidate to the
 * relevant list - no change needed to the selection logic itself.
 */
public record BackendCandidate<T>(String id, String modId, Supplier<T> factory) {
    public boolean isAvailable() {
        return ModList.get().isLoaded(modId);
    }
}
