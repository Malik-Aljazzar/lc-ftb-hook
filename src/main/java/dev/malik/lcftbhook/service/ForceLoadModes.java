package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ForceLoadMode;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which force-load modes the server allows, which one is actually in effect
 * for a region, and whether that mode keeps its chunks loaded right now.
 * Billing follows loading (see {@link ForceLoadBilling}), so "loaded now" is
 * also "chargeable now".
 */
public final class ForceLoadModes {
    private ForceLoadModes() {
    }

    public static boolean isEnabled(ForceLoadMode mode) {
        return switch (mode) {
            case ALWAYS -> LCFtbHookConfig.SERVER.forceLoadModeAlwaysEnabled.get();
            case ANY_PLAYER -> LCFtbHookConfig.SERVER.forceLoadModeAnyPlayerEnabled.get();
            case TEAM_ONLINE -> LCFtbHookConfig.SERVER.forceLoadModeTeamOnlineEnabled.get();
        };
    }

    /** The modes a region may pick, cheapest first. */
    public static List<ForceLoadMode> enabledModes() {
        return Arrays.stream(ForceLoadMode.values()).filter(ForceLoadModes::isEnabled).toList();
    }

    /**
     * The mode in effect for a region: the one it picked, or - if the server
     * disabled that - the next cheaper enabled one, so a team never pays more
     * than it chose. Empty when there is no such mode: the region's
     * force-loads rest (not loaded, not charged) until the team picks again.
     */
    public static Optional<ForceLoadMode> effectiveMode(Region region) {
        return region.forceLoadMode().effective(ForceLoadModes::isEnabled);
    }

    /** Effective mode of the region with this id; empty also for an unknown region. */
    public static Optional<ForceLoadMode> effectiveMode(FtbHookSavedData savedData, UUID teamId, UUID regionId) {
        Region region = regionId != null ? savedData.getRegion(teamId, regionId) : null;
        return region != null ? effectiveMode(region) : Optional.empty();
    }

    /**
     * Whether force-loads in this region are loaded right now. An admin-run
     * server team's always are (they're free, and nobody ever logs in as one).
     */
    public static boolean isLoadedNow(MinecraftServer server, FtbHookSavedData savedData, UUID teamId, UUID regionId) {
        if (Backends.claim().isServerTeam(server, teamId)) {
            return true;
        }
        return effectiveMode(savedData, teamId, regionId).map(mode -> conditionMet(server, teamId, mode)).orElse(false);
    }

    private static boolean conditionMet(MinecraftServer server, UUID teamId, ForceLoadMode mode) {
        return switch (mode) {
            case ALWAYS -> true;
            case ANY_PLAYER -> server.getPlayerList().getPlayerCount() > 0;
            case TEAM_ONLINE -> !Backends.claim().onlineMembers(server, teamId).isEmpty();
        };
    }
}
