package dev.malik.lcftbhook.client.xaero;

import dev.malik.lcftbhook.port.map.ChunkHighlightResolver;
import dev.malik.lcftbhook.port.map.ChunkRegionInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.AbstractHighlighter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Two always-on overlays on Xaero's World Map (registered into Xaero's own
 * {@code HighlighterRegistry}, the same mechanism ftbxaerocompat uses for
 * its claim-color tinting) - never the Minimap, since this class is only
 * ever registered into the World Map session's registry (see
 * {@code HighlighterRegistryEndMixin}, which hooks
 * {@code xaero.map.highlight.HighlighterRegistry}, not the separate Minimap
 * mod):
 * <ul>
 *   <li>A Region BORDER line - same-team chunks in different Regions get a
 *   line drawn on the shared edge only, interior untinted, matching FTB's
 *   own claim-manager map.</li>
 *   <li>A diagonal-stripe HATCH over the marketplace/force-load-highlighted
 *   area - purple for chunks the local player already privately owns,
 *   orange for chunks listed for sale and actually buyable by them, blue for
 *   chunks privately owned by someone else that aren't listed, red for
 *   force-loaded (or pending force-load). Always visible (no Ctrl needed).</li>
 * </ul>
 * <p>
 * This used to be Ctrl-hold-only and blinking. That was dropped for two
 * reasons: Xaero's highlighter caching only re-checks a highlighter on an
 * explicit external invalidation, not by polling every frame, which made a
 * live per-frame blink require pumping that invalidation on a timer -
 * workable, but it kept the whole cache thrashing while Ctrl was held
 * (laggy), and Minecraft's Ctrl key state can get stuck "held" if the game
 * window loses focus while the key is physically down (e.g. Alt-Tab or
 * screenshotting), leaving the highlight seemingly stuck on. A static,
 * always-on color needs neither: {@link #calculateRegionHash} only has to
 * change when the underlying data does, and that's already driven by
 * explicit {@code XaeroHighlightRefresh.refresh()} calls on sync.
 * <p>
 * Extends {@code AbstractHighlighter} directly (not the more convenient
 * {@code ChunkHighlighter}, which only lets you set 5 flat colors - center +
 * 4 edges, each blended against the underlying tile by Xaero itself). A
 * solid, alpha-blended fill ran into a genuine wall: any alpha under 255
 * blends with the team-color tint underneath (different-looking colors on a
 * red team vs a blue team, defeating the point of a universally-recognizable
 * status color), but alpha 255 fully hides the terrain/map under the whole
 * chunk - there's no single alpha value that's both "always reads as the
 * pure status color" and "doesn't blank out the map".
 * {@code AbstractHighlighter.getChunkHighlitColor} instead returns a full
 * 16x16-per-block color grid, which means we can hatch it: alternating
 * blocks are either fully opaque status color (255 alpha - genuinely zero
 * blend) or fully transparent (unmodified terrain+team tint) in a diagonal
 * stripe pattern.
 * <p>
 * WHAT to highlight (marketplace status, force-load status, region
 * membership) is resolved map-mod-agnostically by
 * {@link ChunkHighlightResolver} - this class only owns HOW Xaero
 * specifically renders that (color choice, the diagonal-stripe hatch, packed
 * pixel arrays) - see {@code ARCHITECTURE.md}.
 * <p>
 * A plain (non-mixin) class - safe to reference lc_ftb_hook classes
 * directly, unlike the mixins that register an instance of this class.
 */
public class BuyableChunkHighlighter extends AbstractHighlighter {
    private static final int ORANGE_RGB = 0xFF9800;
    private static final int PURPLE_RGB = 0x9C27B0;
    private static final int BLUE_RGB = 0x2196F3;
    private static final int FORCE_LOAD_RED_RGB = 0xF44336;
    // Fully opaque - safe now that it's only ever applied to half the tile
    // (the hatch), not the whole chunk, so it never blanks out the map. See
    // the class javadoc for why alpha-blending a flat fill couldn't work.
    private static final int STATUS_ALPHA = 255;
    private static final int BORDER_RGB = 0xFFFFFF;
    private static final int BORDER_ALPHA = 200;
    // Width in blocks of each diagonal band (opaque, then transparent, then
    // opaque...) - 3 reads as a clear coarse hatch at normal map zoom
    // without looking like static/noise the way a 1-block checkerboard would.
    private static final int STRIPE_WIDTH = 3;

    public BuyableChunkHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> key, int regionX, int regionZ) {
        // Must NOT depend on a live, per-frame value: Xaero's
        // RegionHighlightExistenceTracker caches a "no highlights here"
        // result the first time a region is checked and only re-checks it on
        // an explicit data-invalidation event, not every frame - so this
        // always reports "might have highlights" whenever a dimension is
        // loaded, matching ftbxaerocompat's own unconditional pattern.
        return true;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> key, int x, int z) {
        if (marketplaceRgb(key, x, z) != 0 || ChunkHighlightResolver.isForceLoadWanted(key, x, z)) {
            return true;
        }
        return hasAnyBorderEdge(key, x, z);
    }

    @Override
    public int[] getChunkHighlitColor(ResourceKey<Level> key, int x, int z) {
        int marketplaceRgb = marketplaceRgb(key, x, z);
        int marketplacePacked = marketplaceRgb != 0 ? pack(marketplaceRgb, STATUS_ALPHA) : 0;
        int forceLoadPacked = ChunkHighlightResolver.isForceLoadWanted(key, x, z) ? pack(FORCE_LOAD_RED_RGB, STATUS_ALPHA) : 0;
        int[] borders = borderColorsAround(key, x, z);

        if (marketplacePacked == 0 && forceLoadPacked == 0 && allZero(borders)) {
            return null;
        }

        paintHatch(marketplacePacked, forceLoadPacked, borders);
        return resultStore;
    }

    /** top/right/bottom/left, in that order (0 = no border on that edge). */
    private static int[] borderColorsAround(ResourceKey<Level> key, int x, int z) {
        ChunkRegionInfo self = ChunkHighlightResolver.regionInfo(key, x, z);
        if (self == null) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{
                borderColor(self, ChunkHighlightResolver.regionInfo(key, x, z - 1)),
                borderColor(self, ChunkHighlightResolver.regionInfo(key, x + 1, z)),
                borderColor(self, ChunkHighlightResolver.regionInfo(key, x, z + 1)),
                borderColor(self, ChunkHighlightResolver.regionInfo(key, x - 1, z))
        };
    }

    private static boolean allZero(int[] values) {
        for (int value : values) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * A chunk can be both marketplace-highlighted and force-loaded at once -
     * rather than picking one to hide, the diagonal cycle grows a 3rd band
     * (marketplace, red, transparent) instead of the usual 2 (color,
     * transparent) whenever both are present, so neither status silently
     * disappears on a chunk that has both. Region border ring takes priority
     * over the hatch on that specific edge, so the boundary line stays
     * visible even on a highlighted chunk.
     */
    private void paintHatch(int marketplacePacked, int forceLoadPacked, int[] borders) {
        int top = borders[0];
        int right = borders[1];
        int bottom = borders[2];
        int left = borders[3];
        boolean bothPresent = marketplacePacked != 0 && forceLoadPacked != 0;
        int bandCount = bothPresent ? 3 : 2;

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int borderHere = edgeBorderAt(bx, bz, top, right, bottom, left);
                if (borderHere != 0) {
                    setResult(bx, bz, borderHere);
                    continue;
                }
                int band = ((bx + bz) % (STRIPE_WIDTH * bandCount)) / STRIPE_WIDTH;
                int color;
                if (bothPresent) {
                    color = band == 0 ? marketplacePacked : band == 1 ? forceLoadPacked : 0;
                } else if (marketplacePacked != 0) {
                    color = band == 0 ? marketplacePacked : 0;
                } else {
                    color = band == 0 ? forceLoadPacked : 0;
                }
                setResult(bx, bz, color);
            }
        }
    }

    private static int marketplaceRgb(ResourceKey<Level> key, int chunkX, int chunkZ) {
        return switch (ChunkHighlightResolver.marketplaceHighlight(key, chunkX, chunkZ)) {
            case OWNED_BY_VIEWER -> PURPLE_RGB;
            case BUYABLE -> ORANGE_RGB;
            case OWNED_BY_OTHER -> BLUE_RGB;
            case NONE -> 0;
        };
    }

    /** 0 = this block isn't on a bordered edge (or there's no border there). */
    private static int edgeBorderAt(int bx, int bz, int top, int right, int bottom, int left) {
        if (bz == 0 && top != 0) {
            return top;
        }
        if (bz == 15 && bottom != 0) {
            return bottom;
        }
        if (bx == 0 && left != 0) {
            return left;
        }
        if (bx == 15 && right != 0) {
            return right;
        }
        return 0;
    }

    private static boolean hasAnyBorderEdge(ResourceKey<Level> key, int x, int z) {
        ChunkRegionInfo self = ChunkHighlightResolver.regionInfo(key, x, z);
        if (self == null) {
            return false;
        }
        return borderColor(self, ChunkHighlightResolver.regionInfo(key, x, z - 1)) != 0
                || borderColor(self, ChunkHighlightResolver.regionInfo(key, x + 1, z)) != 0
                || borderColor(self, ChunkHighlightResolver.regionInfo(key, x, z + 1)) != 0
                || borderColor(self, ChunkHighlightResolver.regionInfo(key, x - 1, z)) != 0;
    }

    /** 0 = no border on this edge (same Region, different team, or no neighbor data). */
    private static int borderColor(ChunkRegionInfo self, @Nullable ChunkRegionInfo neighbor) {
        if (neighbor == null || !neighbor.team().equals(self.team()) || Objects.equals(self.region(), neighbor.region())) {
            return 0;
        }
        return pack(BORDER_RGB, BORDER_ALPHA);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> key, int regionX, int regionZ) {
        // Stable constant - this only needs to change when the underlying
        // Region/ownership data does, and that's already handled by an
        // explicit XaeroHighlightRefresh.refresh() call on sync.
        return 0;
    }

    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> key, int x, int z) {
        return Component.empty();
    }

    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> key, int x, int z) {
        return null;
    }

    // AbstractHighlighter's tooltip contract is block-level, not chunk-level
    // (ChunkHighlighter, which we no longer extend, used to supply this
    // exact delegation for free - see its decompiled source).
    @Override
    public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> key, int blockX, int blockZ) {
        return !chunkIsHighlit(key, blockX >> 4, blockZ >> 4) ? null : getChunkHighlightSubtleTooltip(key, blockX >> 4, blockZ >> 4);
    }

    @Override
    public Component getBlockHighlightBluntTooltip(ResourceKey<Level> key, int blockX, int blockZ) {
        return !chunkIsHighlit(key, blockX >> 4, blockZ >> 4) ? null : getChunkHighlightBluntTooltip(key, blockX >> 4, blockZ >> 4);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> key, int x, int z, int width) {
    }

    private static int pack(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (b << 24) | (g << 16) | (r << 8) | alpha;
    }
}
