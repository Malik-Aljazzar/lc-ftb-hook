package dev.malik.lcftbhook.data;

import java.util.UUID;

/** What every client knows about one claimed chunk, whatever claim mod the server runs. */
public record ClaimedChunkInfo(UUID teamId, boolean forceLoaded) {
}
