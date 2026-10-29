package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ForceLoadMode;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * A team picking a region's force-load mode, and the server changing which
 * modes are allowed. Both take effect immediately: loading follows the new
 * mode within the same tick, and billing can't be dodged by switching - a
 * region already loaded this period stays charged for it (see
 * {@link ForceLoadBilling}).
 */
public final class ForceLoadModeChange {
    private ForceLoadModeChange() {
    }

    /** False (nothing changed) for an unknown region or a mode the server doesn't allow. */
    public static boolean setMode(MinecraftServer server, UUID teamId, UUID regionId, ForceLoadMode mode) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null || !ForceLoadModes.isEnabled(mode)) {
            return false;
        }
        savedData.updateRegion(teamId, region.withForceLoadMode(mode));
        ForceLoadActivation.reconcile(server);
        RegionService.syncRegionsToTeam(server, teamId);
        WarStateSync.onUpkeepFactorsChanged(server, teamId);
        return true;
    }

    /** After the server enabled/disabled a mode: regions may now fall back (or back up), so reload and resync everyone. */
    public static void onEnabledModesChanged(MinecraftServer server) {
        if (server == null || !FtbHookSavedData.isAvailable(server)) {
            return;
        }
        ForceLoadActivation.reconcile(server);
        for (UUID teamId : TeamCatalog.activeTeams(server)) {
            if (!Backends.claim().onlineMembers(server, teamId).isEmpty()) {
                RegionService.syncRegionsToTeam(server, teamId);
                WarStateSync.onUpkeepFactorsChanged(server, teamId);
            }
        }
    }
}
