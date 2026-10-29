package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ForceLoadMode;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Force-load upkeep, region by region: a region's force-loads are charged for
 * a period if they're loaded right now, or were loaded at any moment since
 * the team's last paid settlement ({@link FtbHookSavedData#wasForceLoadUsed}).
 * The second half closes the gap a pure "loaded at settlement time" rule
 * would leave - switching to a cheaper mode or logging out just before every
 * settlement would make the chunks free. Admin-run server teams never pay.
 */
public final class ForceLoadBilling {
    private ForceLoadBilling() {
    }

    public static boolean isChargeable(MinecraftServer server, FtbHookSavedData savedData, UUID teamId, UUID regionId) {
        if (Backends.claim().isServerTeam(server, teamId)) {
            return false;
        }
        return savedData.wasForceLoadUsed(teamId, regionId) || ForceLoadModes.isLoadedNow(server, savedData, teamId, regionId);
    }

    /**
     * How many of the team's force-loads are charged this period: live ones
     * not queued for unloading, plus queued ones (priced as if already live,
     * same as before per-region modes), counted only in chargeable regions.
     */
    public static int chargeableCount(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int count = 0;
        for (String chunkKey : effectiveForceLoads(server, teamId, pendingState)) {
            UUID regionId = ProtectionPricing.effectiveRegion(savedData, teamId, chunkKey, pendingState);
            if (isChargeable(server, savedData, teamId, regionId)) {
                count++;
            }
        }
        return count;
    }

    public static long chargeableCopper(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        return ProtectionPricing.calculateForceLoadCopper(chargeableCount(server, teamId, pendingState));
    }

    /** Price of {@code count} force-loads in this region for this period - 0 where they wouldn't be charged. */
    public static long regionCopper(MinecraftServer server, UUID teamId, UUID regionId, int count) {
        return isChargeable(server, FtbHookSavedData.get(server), teamId, regionId)
                ? ProtectionPricing.calculateForceLoadCopper(count) : 0L;
    }

    /**
     * Whether some team has force-loads that are charged even with nobody on
     * the server - in a region on {@link ForceLoadMode#ALWAYS}. Settlement
     * keeps running on an empty server only then (or with protection on HEAVY).
     */
    public static boolean anyChargedWhileEmpty(MinecraftServer server) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        for (UUID teamId : TeamCatalog.activeTeams(server)) {
            if (teamChargedWhileEmpty(server, savedData, teamId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean teamChargedWhileEmpty(MinecraftServer server, FtbHookSavedData savedData, UUID teamId) {
        if (Backends.claim().isServerTeam(server, teamId)) {
            return false;
        }
        for (ClaimPort.ChunkKey chunk : Backends.claim().forceLoadedChunks(server, teamId)) {
            Region region = savedData.getRegion(teamId, savedData.getChunkRegion(teamId, ChunkPosKey.encode(chunk)));
            if (region != null && ForceLoadModes.effectiveMode(region).orElse(null) == ForceLoadMode.ALWAYS) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> effectiveForceLoads(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        Set<String> keys = new LinkedHashSet<>();
        for (ClaimPort.ChunkKey chunk : Backends.claim().forceLoadedChunks(server, teamId)) {
            String key = ChunkPosKey.encode(chunk);
            if (!pendingState.isPendingForceUnload(key)) {
                keys.add(key);
            }
        }
        keys.addAll(pendingState.pendingForceLoads());
        return keys;
    }
}
