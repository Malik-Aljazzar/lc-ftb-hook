package dev.malik.lcftbhook.client.xaero;

import dev.malik.lcftbhook.data.ChunkPosKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.Collection;

/**
 * Invalidation of Xaero World Map's highlighter cache, for events that
 * change what {@link BuyableChunkHighlighter} should be drawing (Region
 * membership sync, chunk ownership/listing sync).
 * <p>
 * FTB Chunks' OWN {@code MapManager} (used to refresh FTB's own
 * claim-manager map and, via {@link dev.malik.lcftbhook.mixin.ftb.client.MapChunkRegionBorderMixin},
 * its border rendering) is a completely separate, unrelated pipeline from
 * Xaero's - both need refreshing on the same events, but through their own
 * respective APIs.
 * <p>
 * Unlike the mixin-based parts of this integration (optional via
 * {@code lc_ftb_hook_xaero.mixins.json}'s {@code required: false}, so they
 * simply never apply when Xaero isn't installed), {@link #refresh}/{@link #refreshAll}
 * are called directly - by plain Java method calls, not Mixin injection -
 * from {@link dev.malik.lcftbhook.client.ClientChunkOwnership} and
 * {@link dev.malik.lcftbhook.client.ClientRegionMembership} on every sync,
 * completely independent of whether those mixins applied. Without the
 * {@link #ENABLED} guard below, any player without Xaero World Map installed
 * at all would hit {@code NoClassDefFoundError} the moment either class'
 * body first touches {@code xaero.map.*} types (i.e. on the very first
 * chunk-ownership/region sync after joining). The inner try/catch handles
 * the OTHER failure mode - Xaero installed, but an incompatible version
 * whose internals changed shape.
 */
public final class XaeroHighlightRefresh {
    private static final boolean ENABLED = ModList.get().isLoaded("xaeroworldmap");

    private XaeroHighlightRefresh() {
    }

    /**
     * Clears the highlighter cache for only the 32x32-chunk region-tiles
     * actually containing {@code chunkKeys} - a change from an earlier
     * version of this class that always cleared every loaded region-tile
     * across the whole explored map. That still worked, but it queues every
     * one of those tiles for Xaero's own background re-render pass, and a
     * single chunk's sale/cancel/purchase was visibly landing behind
     * whatever else happened to already be queued - scoping down to just
     * the affected tile(s) means there's nothing else competing ahead of it.
     */
    public static void refresh(Collection<String> chunkKeys) {
        if (!ENABLED || chunkKeys.isEmpty()) {
            return;
        }
        try {
            MapWorld world = currentWorld();
            if (world == null) {
                return;
            }
            for (String chunkKey : chunkKeys) {
                ResourceLocation dimensionId;
                int chunkX;
                int chunkZ;
                try {
                    dimensionId = ChunkPosKey.dimension(chunkKey);
                    chunkX = ChunkPosKey.x(chunkKey);
                    chunkZ = ChunkPosKey.z(chunkKey);
                } catch (RuntimeException malformedKey) {
                    continue;
                }
                ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
                MapDimension dimension = world.getDimension(dimensionKey);
                if (dimension != null) {
                    dimension.onClearCachedHighlightHash(chunkX >> 5, chunkZ >> 5);
                }
            }
        } catch (Throwable e) {
            logIncompatible(e);
        }
    }

    /** Full-map clear, for events that can touch an unpredictable/large scatter of chunks at once (e.g. Region membership sync). */
    public static void refreshAll() {
        if (!ENABLED) {
            return;
        }
        try {
            MapWorld world = currentWorld();
            if (world != null) {
                world.clearAllCachedHighlightHashes();
            }
        } catch (Throwable e) {
            logIncompatible(e);
        }
    }

    private static MapWorld currentWorld() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) {
            return null;
        }
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) {
            return null;
        }
        return processor.getMapWorld();
    }

    private static void logIncompatible(Throwable e) {
        org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error(
                "Failed to refresh Xaero World Map highlights (likely an incompatible Xaero World Map version)", e);
    }
}
