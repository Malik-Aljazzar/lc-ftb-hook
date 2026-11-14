package dev.malik.lcftbhook.port.protection;

/**
 * What a block change does, for the claim mod's player rules: {@link #BREAK}
 * destroys or replaces the block (glass shot out, terrain blasted away, fire
 * placed), {@link #INTERACT} only uses it (a door shot open).
 */
public enum BlockChangeKind {
    BREAK,
    INTERACT
}
