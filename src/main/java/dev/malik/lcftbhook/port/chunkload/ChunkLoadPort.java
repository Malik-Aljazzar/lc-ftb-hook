package dev.malik.lcftbhook.port.chunkload;

import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Keeps chunks loaded (and ticking) on this mod's own behalf - the force-load
 * mode of a region decides when, not the claim mod (see
 * {@code service.ForceLoadActivation}). One ticket per chunk, owned by the
 * team the force-load belongs to.
 */
public interface ChunkLoadPort {
    /** Adds or removes this mod's loading ticket for one chunk; a no-op if it's already in that state. */
    void setLoaded(MinecraftServer server, ClaimPort.ChunkKey chunk, UUID ownerTeamId, boolean loaded);
}
