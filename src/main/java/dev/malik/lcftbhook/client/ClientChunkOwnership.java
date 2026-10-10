package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.ChunkOwnership;

import java.util.Map;

/** Client-side cache of every visible chunk's marketplace ownership/listing state, synced from the server. */
public final class ClientChunkOwnership {
    private static Map<String, ChunkOwnership> ownership = Map.of();

    private ClientChunkOwnership() {
    }

    public static void update(Map<String, ChunkOwnership> updated) {
        ownership = Map.copyOf(updated);
        ClientUiRefresh.redrawMap();
        // The payload is the full current snapshot of every tracked (owned
        // or listed) chunk, not a delta - still far more scoped than every
        // loaded map-region, so this avoids queuing unrelated regions ahead
        // of the ones that actually matter right now.
        dev.malik.lcftbhook.client.xaero.XaeroHighlightRefresh.refresh(ownership.keySet());
    }

    public static ChunkOwnership get(String chunkKey) {
        return ownership.getOrDefault(chunkKey, ChunkOwnership.EMPTY);
    }
}
