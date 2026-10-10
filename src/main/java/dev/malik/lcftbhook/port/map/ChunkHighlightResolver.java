package dev.malik.lcftbhook.port.map;

import dev.malik.lcftbhook.client.BuyableChunkChecker;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientClaims;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Resolves WHAT a chunk's map highlight should show (marketplace status,
 * force-load status, region membership for border drawing) - map-mod
 * agnostic: the Xaero adapter ({@code BuyableChunkHighlighter}) only owns the
 * rendering, the answers come from here. Claim
 * data comes from {@link ClientClaims}, i.e. whatever claim mod the server runs.
 */
public final class ChunkHighlightResolver {
    private ChunkHighlightResolver() {
    }

    public static MarketplaceHighlight marketplaceHighlight(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClaimedChunkInfo claim = ClientClaims.chunk(dimension, chunkX, chunkZ);
        if (claim == null) {
            return MarketplaceHighlight.NONE;
        }
        String chunkKey = ChunkPosKey.encode(dimension.location(), chunkX, chunkZ);
        ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
        UUID localPlayer = localPlayerId();
        if (localPlayer != null && localPlayer.equals(ownership.privateOwner())) {
            return MarketplaceHighlight.OWNED_BY_VIEWER;
        }
        if (BuyableChunkChecker.isBuyable(ownership, claim.teamId(), chunkKey, localPlayer)) {
            return MarketplaceHighlight.BUYABLE;
        }
        // Privately owned by someone else - shown whether or not it's also
        // listed; only BUYABLE (above) depends on the viewer's eligibility.
        return ownership.isStateOwned() ? MarketplaceHighlight.NONE : MarketplaceHighlight.OWNED_BY_OTHER;
    }

    /** True while force-loaded OR while a force-load is pending (requested, or suspended for non-payment). */
    public static boolean isForceLoadWanted(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClaimedChunkInfo claim = ClientClaims.chunk(dimension, chunkX, chunkZ);
        return (claim != null && claim.forceLoaded()) || ClientPendingState.isPendingForceLoad(dimension, chunkX, chunkZ);
    }

    /** {@code null} means unclaimed. */
    @Nullable
    public static ChunkRegionInfo regionInfo(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClaimedChunkInfo claim = ClientClaims.chunk(dimension, chunkX, chunkZ);
        if (claim == null) {
            return null;
        }
        return new ChunkRegionInfo(claim.teamId(),
                ClientRegionMembership.rawRegionOf(ChunkPosKey.encode(dimension.location(), chunkX, chunkZ)));
    }

    @Nullable
    private static UUID localPlayerId() {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
    }
}
