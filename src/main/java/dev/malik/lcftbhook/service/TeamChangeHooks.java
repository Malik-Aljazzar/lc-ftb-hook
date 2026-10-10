package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * What the core does when a claim backend reports that a team was created or
 * its members/ranks/owner/name changed. Claim adapters (FTB events and
 * mixins, OPAC listeners) call this instead of touching any currency
 * backend directly.
 */
public final class TeamChangeHooks {
    private TeamChangeHooks() {
    }

    public static void onTeamChanged(MinecraftServer server, UUID teamId) {
        // FTB Teams loads its teams (firing a change event per team) before
        // the worlds exist; touching saved data then throws - and aborts FTB's
        // whole team load. Teams loaded at startup are handled once players
        // join / the startup reconcile runs.
        if (teamId == null || !FtbHookSavedData.isAvailable(server)) {
            return;
        }
        Backends.currency().ensureTeamAccountExists(server, teamId);
        ensureRegions(server, teamId);
    }

    /**
     * A team that never went through a region operation (e.g. an OPAC party
     * formed after its members already claimed) has no Default region yet -
     * without one, no client can resolve that team's protection status.
     */
    public static void ensureRegions(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (!savedData.getRegions(teamId).isEmpty()) {
            return;
        }
        savedData.getOrCreateLink(teamId);
        RegionService.syncRegionsToTeam(server, teamId);
    }
}
