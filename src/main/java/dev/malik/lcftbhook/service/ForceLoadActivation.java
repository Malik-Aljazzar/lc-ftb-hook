package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps exactly those force-loaded chunks loaded whose region's force-load
 * mode says so right now (see {@link ForceLoadModes#isLoadedNow}) - the claim
 * mod's own force-load tickets are switched off while this mod runs upkeep
 * (see {@code ClaimPort#takeOverForceLoading}), so this is the only thing
 * that loads them. Re-evaluated once a second, and right away whenever
 * something that decides it changes (a settlement pass, a region's mode, the
 * enabled modes). Every region found loaded is recorded as used for the
 * current billing period ({@link FtbHookSavedData#markForceLoadUsed}).
 */
public final class ForceLoadActivation {
    private static final int INTERVAL_TICKS = 20;

    /** Chunks this mod currently holds a loading ticket for -> the team that ticket belongs to. */
    private static final Map<ClaimPort.ChunkKey, UUID> ticketed = new HashMap<>();
    private static int ticks;

    private ForceLoadActivation() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        reconcile(server);
    }

    public static void reconcile(MinecraftServer server) {
        if (!FtbHookSavedData.isAvailable(server)) {
            return;
        }
        applyTickets(server, chunksToLoad(server));
    }

    /** Marks every region of this team that is loaded right now as used - a fresh billing period starts with what's running at its start. */
    public static void markLoadedRegionsUsed(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        markUsed(savedData, teamId, loadedRegions(server, savedData, teamId));
    }

    public static void reset() {
        ticketed.clear();
        ticks = 0;
    }

    private static Map<ClaimPort.ChunkKey, UUID> chunksToLoad(MinecraftServer server) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Map<ClaimPort.ChunkKey, UUID> wanted = new HashMap<>();
        for (UUID teamId : TeamCatalog.activeTeams(server)) {
            Map<UUID, Boolean> regionLoaded = loadedRegions(server, savedData, teamId);
            markUsed(savedData, teamId, regionLoaded);
            for (ClaimPort.ChunkKey chunk : Backends.claim().forceLoadedChunks(server, teamId)) {
                if (regionLoaded.getOrDefault(savedData.getChunkRegion(teamId, ChunkPosKey.encode(chunk)), false)) {
                    wanted.putIfAbsent(chunk, teamId);
                }
            }
        }
        return wanted;
    }

    private static void markUsed(FtbHookSavedData savedData, UUID teamId, Map<UUID, Boolean> regionLoaded) {
        regionLoaded.forEach((regionId, loaded) -> {
            if (loaded) {
                savedData.markForceLoadUsed(teamId, regionId);
            }
        });
    }

    /** Region -> loaded right now, for every region of the team that has at least one force-loaded chunk. */
    private static Map<UUID, Boolean> loadedRegions(MinecraftServer server, FtbHookSavedData savedData, UUID teamId) {
        Map<UUID, Boolean> regionLoaded = new HashMap<>();
        for (ClaimPort.ChunkKey chunk : Backends.claim().forceLoadedChunks(server, teamId)) {
            UUID regionId = savedData.getChunkRegion(teamId, ChunkPosKey.encode(chunk));
            regionLoaded.computeIfAbsent(regionId, id -> ForceLoadModes.isLoadedNow(server, savedData, teamId, id));
        }
        return regionLoaded;
    }

    private static void applyTickets(MinecraftServer server, Map<ClaimPort.ChunkKey, UUID> wanted) {
        ticketed.entrySet().removeIf(entry -> {
            boolean keep = entry.getValue().equals(wanted.get(entry.getKey()));
            if (!keep) {
                Backends.chunkLoad().setLoaded(server, entry.getKey(), entry.getValue(), false);
            }
            return !keep;
        });
        wanted.forEach((chunk, teamId) -> {
            if (ticketed.putIfAbsent(chunk, teamId) == null) {
                Backends.chunkLoad().setLoaded(server, chunk, teamId, true);
            }
        });
    }
}
