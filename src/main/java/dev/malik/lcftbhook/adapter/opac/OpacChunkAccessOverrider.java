package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.util.Guard;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverride;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverrideType;
import xaero.pac.common.server.claims.protection.override.api.IChunkAccessOverriderAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;

import java.util.UUID;

/**
 * Full access in a team's land for the people our rules always let in,
 * whatever the protection level: the owning team's officers/owner, and a
 * privately sold chunk's own buyer. This is what makes the "Private" level
 * (OPAC group "nobody") mean "officers / private owner only", as under FTB.
 */
final class OpacChunkAccessOverrider implements IChunkAccessOverriderAPI {
    private static final ChunkAccessOverride ALLOW = new ChunkAccessOverride(ChunkAccessOverrideType.ALLOW);

    @Override
    public String getName() {
        return "lc_ftb_hook";
    }

    /** Guarded: if our check fails, OPAC's own decision stands. */
    @Override
    public ChunkAccessOverride overrideChunkAccess(ResourceLocation dimension, int chunkX, int chunkZ, IPlayerConfigAPI claimConfig,
                                                   Entity accessor, UUID accessorId, MinecraftServer server,
                                                   ChunkAccessOverride currentOverride) {
        return Guard.call("OPAC chunk access override",
                () -> decide(dimension, chunkX, chunkZ, accessorId, server, currentOverride), currentOverride);
    }

    private ChunkAccessOverride decide(ResourceLocation dimension, int chunkX, int chunkZ, UUID accessorId,
                                       MinecraftServer server, ChunkAccessOverride currentOverride) {
        // Only players acting in person - checked on every protection query, so bail out cheaply.
        if (dimension == null || accessorId == null || !OpacIntegration.isActive()
                || server.getPlayerList().getPlayer(accessorId) == null) {
            return currentOverride;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimension);
        UUID teamId = Backends.claim().ownerTeamOf(server, dim, chunkX, chunkZ);
        if (teamId == null) {
            return currentOverride;
        }
        if (Backends.claim().rankOf(server, teamId, accessorId).isOfficerOrBetter()) {
            return ALLOW;
        }
        ChunkOwnership ownership = FtbHookSavedData.get(server).getChunkOwnership(teamId, ChunkPosKey.encode(dimension, chunkX, chunkZ));
        return !ownership.isStateOwned() && accessorId.equals(ownership.privateOwner()) ? ALLOW : currentOverride;
    }
}
