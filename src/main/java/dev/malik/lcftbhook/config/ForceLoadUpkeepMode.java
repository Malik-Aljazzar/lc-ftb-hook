package dev.malik.lcftbhook.config;

/**
 * The server-wide force-load setting from before 4.5.0. No longer has any
 * effect - force-load mode is chosen per region now
 * ({@link dev.malik.lcftbhook.data.ForceLoadMode}). Only read once, to give
 * regions from older saves the mode this setting stood for (see
 * {@code data.LegacyForceLoadMode}).
 */
public enum ForceLoadUpkeepMode {
    /** Charge every period regardless of who is online. */
    ALWAYS,
    /** Charge only when at least one of the team's own members is online. */
    DEFAULT
}
