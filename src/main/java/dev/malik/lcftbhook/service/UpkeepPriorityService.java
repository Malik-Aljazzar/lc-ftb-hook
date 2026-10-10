package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.service.ProtectionDismantleOrder.DismantleStep;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the dismantle/restore priority list for a team. Priority 1 is the
 * protection dismantled last (restored first); the highest number is the most
 * expensive active outgoing war (dismantled first).
 */
public final class UpkeepPriorityService {
    public record PriorityEntry(
            int priority,
            EntryKind kind,
            String id,
            Component label,
            long costCopper
    ) {
    }

    public enum EntryKind {
        PROTECTION,
        FORCE_LOAD,
        OUTGOING_WAR
    }

    private UpkeepPriorityService() {
    }

    public static List<PriorityEntry> buildOrder(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);
        List<PriorityEntry> entries = new ArrayList<>();
        int priority = 1;

        for (DismantleStep step : ProtectionDismantleOrder.restoreOrder(server, teamId)) {
            if (!ProtectionRollbackService.isLiveProtectionBillable(savedData, teamId, step.regionId(), step.property())) {
                continue;
            }
            String key = RegionPropertyKey.encode(step.regionId(), step.property().id());
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.PROTECTION,
                    key,
                    protectionLabel(savedData, teamId, step),
                    protectionUpkeepCopper(server, teamId, pendingState, step)
            ));
        }

        // Between protections (restored first, dismantled last) and wars
        // (dismantled first) - matches the actual settlement order in
        // UpkeepSettlementService.settle.
        // Raw config value is tenths of a copper (see LCFtbHookConfig's
        // [general] note) - converted to whole copper for display here.
        long forceLoadPrice = Math.floorDiv(dev.malik.lcftbhook.config.LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get(), 10L);
        // Region-grouped, all-or-nothing (see PendingChangeService.RegionForceLoadGroup) -
        // still listed one chunk per row here since /upkeep_priority is a flat
        // display, but chunks from the same region are always adjacent and in
        // the exact order they'd actually be dropped/restored together.
        for (PendingChangeService.RegionForceLoadGroup group : PendingChangeService.forceLoadDismantleOrder(server, teamId)) {
            for (String chunkKey : group.chunkKeys()) {
                entries.add(new PriorityEntry(
                        priority++,
                        EntryKind.FORCE_LOAD,
                        chunkKey,
                        forceLoadLabel(chunkKey),
                        forceLoadPrice
                ));
            }
        }

        List<UUID> outgoing = new ArrayList<>(savedData.getWarTargets(teamId));
        outgoing.sort(Comparator.comparingLong(targetId -> {
            return TeamCatalog.exists(server, targetId) ? WarCostService.costToDeclareWar(server, teamId, targetId) : 0L;
        }));

        for (UUID targetId : outgoing) {
            if (!TeamCatalog.exists(server, targetId)) {
                continue;
            }
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.OUTGOING_WAR,
                    targetId.toString(),
                    Component.literal(WarService.displayName(server, targetId)).withStyle(net.minecraft.ChatFormatting.YELLOW),
                    WarCostService.costToDeclareWar(server, teamId, targetId)
            ));
        }

        return entries;
    }

    private static Component forceLoadLabel(String chunkKey) {
        return Component.translatable(
                "message.lc_ftb_hook.upkeep_priority.force_load",
                dev.malik.lcftbhook.data.ChunkPosKey.x(chunkKey),
                dev.malik.lcftbhook.data.ChunkPosKey.z(chunkKey)
        ).withStyle(net.minecraft.ChatFormatting.RED);
    }

    private static Component protectionLabel(FtbHookSavedData savedData, UUID teamId, DismantleStep step) {
        Region region = savedData.getRegion(teamId, step.regionId());
        String regionName = region != null ? region.name() : Region.DEFAULT_NAME;
        return Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + step.property().id())
                .withStyle(net.minecraft.ChatFormatting.YELLOW)
                .append(Component.literal(" (" + regionName + ")").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private static long protectionUpkeepCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            DismantleStep step
    ) {
        ProtectionPricing.ChunkCounts counts = ProtectionPricing.countBillableChunks(server, teamId, pendingState);
        Map<String, String> pricing = ProtectionRollbackService.pricingProperties(server, teamId, pendingState);
        long withLive = ProtectionPricing.calculateProtectionCopper(server, teamId,
                new TeamPendingState(pricing, pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                        pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds()),
                counts);

        String key = RegionPropertyKey.encode(step.regionId(), step.property().id());
        java.util.Map<String, String> atMinimum = new java.util.HashMap<>(pricing);
        atMinimum.put(key, step.property().minimumSerialized());

        long atMin = ProtectionPricing.calculateProtectionCopper(server, teamId,
                new TeamPendingState(atMinimum, pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                        pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds()),
                counts);
        return Math.max(0L, withLive - atMin);
    }
}
