package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.service.TeamCatalog;
import dev.malik.lcftbhook.service.UpkeepSummaryService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

public final class PendingStateSync {
    private PendingStateSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        UUID teamId = TeamCatalog.teamOf(player.server, player.getUUID());
        if (teamId == null) {
            PacketDistributor.sendToPlayer(player, SyncPendingStatePayload.EMPTY);
            return;
        }
        PacketDistributor.sendToPlayer(player, createPayload(player.server, teamId));
        PacketDistributor.sendToPlayer(player, new SyncUpkeepSummaryPayload(UpkeepSummaryService.compute(player.server, teamId)));
    }

    public static void syncTeam(MinecraftServer server, UUID teamId) {
        SyncPendingStatePayload payload = createPayload(server, teamId);
        SyncUpkeepSummaryPayload upkeepPayload = new SyncUpkeepSummaryPayload(UpkeepSummaryService.compute(server, teamId));
        for (ServerPlayer member : Backends.claim().onlineMembers(server, teamId)) {
            PacketDistributor.sendToPlayer(member, payload);
            PacketDistributor.sendToPlayer(member, upkeepPayload);
        }
    }

    public static SyncPendingStatePayload createPayload(MinecraftServer server, UUID teamId) {
        TeamPendingState pendingState = FtbHookSavedData.get(server).getPendingState(teamId);
        return new SyncPendingStatePayload(
                pendingState.pendingProperties(),
                pendingState.pendingForceLoads(),
                pendingState.pendingForceUnloads(),
                pendingState.pendingRegionAssignments()
        );
    }
}
