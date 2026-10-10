package dev.malik.lcftbhook.port.map;

import javax.annotation.Nullable;
import java.util.UUID;

/** {@code region} null means claimed-but-unassigned (the team's Default region). */
public record ChunkRegionInfo(UUID team, @Nullable UUID region) {
}
