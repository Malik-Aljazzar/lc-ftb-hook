package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Makes OPAC enforce our regions: every claim is pointed at the owner's
 * sub-config holding that chunk's protection profile (see
 * {@link OpacSubConfigSlots}). Runs every few seconds - region/marketplace
 * changes have many sources - and right after a claim.
 */
final class OpacProtectionSync {
    private static final int INTERVAL_TICKS = 100;

    /** A chunk with its own sub-config because of a whitelist/blacklist. */
    record ExclusiveChunk(UUID teamId, String chunkKey) {
    }

    private record ClaimRef(ResourceLocation dimension, ChunkPos pos, IPlayerChunkClaimAPI claim, OpacProtectionProfile profile) {
    }

    /** "ownerId/subConfigId" -> chunk, rebuilt on every pass. */
    private static volatile Map<String, ExclusiveChunk> exclusiveChunks = Map.of();
    private static volatile boolean requested;
    private int ticks;

    void onServerTick(MinecraftServer server) {
        if ((++ticks < INTERVAL_TICKS && !requested) || !OpacIntegration.isActive()) {
            return;
        }
        ticks = 0;
        requested = false;
        try {
            reconcile(server);
        } catch (RuntimeException e) {
            LCFtbHook.LOGGER.error("Syncing region protection into OPAC failed", e);
        }
    }

    void reset() {
        ticks = 0;
        exclusiveChunks = Map.of();
    }

    /** Sync on the next tick instead of waiting for the interval, e.g. after a new claim. */
    static void request() {
        requested = true;
    }

    @Nullable
    static ExclusiveChunk exclusiveChunk(UUID ownerId, String subConfigId) {
        return exclusiveChunks.get(ownerId + "/" + subConfigId);
    }

    private static void reconcile(MinecraftServer server) {
        OpenPACServerAPI api = OpenPACServerAPI.get(server);
        IServerClaimsManagerAPI claims = api.getServerClaimsManager();
        Map<String, ExclusiveChunk> exclusive = new HashMap<>();
        for (IServerPlayerClaimInfoAPI info : claims.getPlayerInfoStream().toList()) {
            syncOwner(server, claims, api.getPlayerConfigManager(), info, exclusive);
        }
        exclusiveChunks = Map.copyOf(exclusive);
    }

    private static void syncOwner(MinecraftServer server, IServerClaimsManagerAPI claims, IPlayerConfigManagerAPI configs,
                                  IServerPlayerClaimInfoAPI info, Map<String, ExclusiveChunk> exclusive) {
        UUID ownerId = info.getPlayerId();
        IPlayerConfigAPI ownerConfig = configs.getLoadedConfig(ownerId);
        UUID teamId = Backends.claim().teamIdForPlayer(server, ownerId);
        if (ownerConfig == null || teamId == null) {
            return;
        }
        Map<String, ClaimRef> refs = claimsOf(server, claims, info, teamId, Backends.claim().teamDisplayName(server, teamId));
        Map<OpacProtectionProfile, IPlayerConfigAPI> slots = OpacSubConfigSlots.assign(configs, ownerConfig,
                new LinkedHashSet<>(refs.values().stream().map(ClaimRef::profile).toList()));
        refs.forEach((chunkKey, ref) -> {
            IPlayerConfigAPI slot = slots.get(ref.profile());
            if (slot == null) {
                return;
            }
            if (ref.claim().getSubConfigIndex() != slot.getSubIndex()) {
                claims.claim(ref.dimension(), ownerId, slot.getSubIndex(), ref.pos().x, ref.pos().z, ref.claim().isForceloadable());
            }
            if (ref.profile().exclusiveChunk() != null) {
                exclusive.put(ownerId + "/" + slot.getSubId(), new ExclusiveChunk(teamId, chunkKey));
            }
        });
    }

    private static Map<String, ClaimRef> claimsOf(MinecraftServer server, IServerClaimsManagerAPI claims,
                                                 IServerPlayerClaimInfoAPI info, UUID teamId, String teamName) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Map<String, ClaimRef> refs = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, IPlayerDimensionClaimsAPI> dim : info.getStream().toList()) {
            for (IPlayerClaimPosListAPI posList : dim.getValue().getStream().toList()) {
                posList.getStream().forEach(pos -> {
                    IPlayerChunkClaimAPI claim = claims.get(dim.getKey(), pos.x, pos.z);
                    if (claim == null) {
                        return;
                    }
                    String chunkKey = ChunkPosKey.encode(dim.getKey(), pos.x, pos.z);
                    Region region = RegionService.resolveRegion(server, teamId, chunkKey);
                    ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
                    refs.put(chunkKey, new ClaimRef(dim.getKey(), pos, claim, OpacProtectionProfile.of(region, ownership, chunkKey, teamName)));
                });
            }
        }
        return refs;
    }
}
