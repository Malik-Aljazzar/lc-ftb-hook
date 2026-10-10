package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.adapter.opac.OpacProtectionFields.OpacField;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A private chunk owner's per-field whitelist/blacklist on top of OPAC's own
 * group check - called from the one OPAC-internal mixin. Same rule as under
 * FTB: lists only apply where the REGION protects that field at all.
 */
public final class OpacAccessLists {
    private OpacAccessLists() {
    }

    /**
     * @param baseAllowed what OPAC decided from the exception group
     * @return the decision with the list applied, or null to keep OPAC's
     */
    @Nullable
    public static Boolean apply(String optionId, UUID claimOwnerId, @Nullable String subConfigId,
                                @Nullable Entity accessor, @Nullable UUID accessorId, boolean baseAllowed) {
        UUID player = accessorId != null ? accessorId : accessor instanceof Player p ? p.getUUID() : null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (player == null || subConfigId == null || server == null || !OpacIntegration.isActive()) {
            return null;
        }
        OpacProtectionSync.ExclusiveChunk chunk = OpacProtectionSync.exclusiveChunk(claimOwnerId, subConfigId);
        OpacField field = OpacProtectionFields.byOptionId(optionId);
        if (chunk == null || field == null) {
            return null;
        }
        Region region = RegionService.resolveRegion(server, chunk.teamId(), chunk.chunkKey());
        if (region.isAtMinimum(new ProtectionProperty(field.spec(), () -> 0L))) {
            return null;
        }
        ChunkOwnership ownership = FtbHookSavedData.get(server).getChunkOwnership(chunk.teamId(), chunk.chunkKey());
        PlayerAccessList list = ownership.accessLists().get(field.spec().id());
        return list == null ? null : list.apply(baseAllowed, player);
    }
}
