package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * The server changed a setting that decides how many of a team's chunks are
 * billed (freeChunks, freeChunksExemptFromUpkeep). Billing picks it up at the
 * next upkeep on its own; this pushes the new billable counts and costs to
 * everyone online right away so the region screens and war costs match.
 */
public final class UpkeepPricingChange {
    private UpkeepPricingChange() {
    }

    public static void onBillableChunksChanged(MinecraftServer server) {
        if (server == null || !FtbHookSavedData.isAvailable(server)) {
            return;
        }
        for (UUID teamId : TeamCatalog.activeTeams(server)) {
            if (!Backends.claim().onlineMembers(server, teamId).isEmpty()) {
                RegionService.syncRegionsToTeam(server, teamId);
                WarStateSync.onUpkeepFactorsChanged(server, teamId);
            }
        }
    }
}
