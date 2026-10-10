package dev.malik.lcftbhook.client;


import java.util.Map;
import java.util.UUID;

/** Client-side cache of every visible team's chunk-&gt;region assignments, synced from the server. */
public final class ClientRegionMembership {
    private static Map<String, UUID> chunkRegions = Map.of();

    private ClientRegionMembership() {
    }

    public static void update(Map<String, UUID> keys) {
        chunkRegions = Map.copyOf(keys);
        // Region borders are baked into the map texture, re-render so
        // membership changes show up immediately.
        ClientUiRefresh.redrawMap();
        dev.malik.lcftbhook.client.xaero.XaeroHighlightRefresh.refreshAll();
    }

    public static UUID regionOf(String chunkKey, UUID defaultRegionId) {
        return chunkRegions.getOrDefault(chunkKey, defaultRegionId);
    }

    /** Null means "no explicit assignment" (implicitly the owning team's Default region) - the sparse-map convention used everywhere else in this mod. */
    @javax.annotation.Nullable
    public static UUID rawRegionOf(String chunkKey) {
        return chunkRegions.get(chunkKey);
    }
}
