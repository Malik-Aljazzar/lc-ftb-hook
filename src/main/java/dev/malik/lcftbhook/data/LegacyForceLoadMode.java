package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.ForceLoadUpkeepMode;
import dev.malik.lcftbhook.config.LCFtbHookConfig;

/**
 * One-time migration for saves from before 4.5.0: force-load mode used to be
 * the single server-wide {@code forceLoadUpkeepMode} setting, now every
 * region has its own. A region saved without one takes over what that old
 * setting said - ALWAYS stays {@link ForceLoadMode#ALWAYS}, DEFAULT ("only
 * while a team member is online") becomes {@link ForceLoadMode#TEAM_ONLINE} -
 * so nothing changes for any server on the update itself. Once saved, a
 * region carries its own mode and the old setting is never read for it again.
 */
public final class LegacyForceLoadMode {
    private static int migratedCount;

    private LegacyForceLoadMode() {
    }

    /** The mode for a region read from a save that has none yet; counts it as migrated. */
    static ForceLoadMode modeForUnmigratedRegion() {
        migratedCount++;
        return modeForUnmigratedRegionPreview();
    }

    static ForceLoadMode modeForUnmigratedRegionPreview() {
        try {
            return LCFtbHookConfig.SERVER.forceLoadUpkeepMode.get() == ForceLoadUpkeepMode.ALWAYS
                    ? ForceLoadMode.ALWAYS
                    : ForceLoadMode.TEAM_ONLINE;
        } catch (IllegalStateException configNotLoaded) {
            // Server configs load before any world data, so this should never
            // happen - but if it does, the old default is the safe answer: it
            // never charges more than before.
            LCFtbHook.LOGGER.warn("Server config not loaded while migrating region force-load modes - using {}", ForceLoadMode.TEAM_ONLINE.id());
            return ForceLoadMode.TEAM_ONLINE;
        }
    }

    /** How many regions were migrated since the last call - lets the loader mark the data dirty so the result is saved. */
    static int takeMigratedCount() {
        int count = migratedCount;
        migratedCount = 0;
        return count;
    }
}
