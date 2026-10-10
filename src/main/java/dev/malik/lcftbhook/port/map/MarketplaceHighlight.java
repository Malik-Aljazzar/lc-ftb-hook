package dev.malik.lcftbhook.port.map;

/**
 * What a chunk's marketplace status should look like on any map integration -
 * the actual color/icon/rendering choice is each map adapter's own (see
 * {@code ChunkHighlightResolver} and {@code ARCHITECTURE.md}).
 */
public enum MarketplaceHighlight {
    NONE,
    OWNED_BY_VIEWER,
    BUYABLE,
    OWNED_BY_OTHER
}
