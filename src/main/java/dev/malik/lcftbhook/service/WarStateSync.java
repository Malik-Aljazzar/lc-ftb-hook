package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.network.SyncWarStatePayload;
import dev.malik.lcftbhook.network.WarTeamEntry;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WarStateSync {
    private WarStateSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        UUID teamId = TeamCatalog.teamOf(player.server, player.getUUID());
        if (teamId == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createPayload(player.server, teamId, player.getUUID()));
    }

    public static void syncToTeam(MinecraftServer server, UUID teamId) {
        if (!TeamCatalog.exists(server, teamId)) {
            return;
        }
        for (ServerPlayer player : Backends.claim().onlineMembers(server, teamId)) {
            // A member of a party can still have a separate solo team - only
            // send it to players whose CURRENT team this actually is.
            if (teamId.equals(TeamCatalog.teamOf(server, player.getUUID()))) {
                PacketDistributor.sendToPlayer(player, createPayload(server, teamId, player.getUUID()));
            }
        }
    }

    /**
     * War costs are derived from each team's live upkeep (including queued
     * protection changes). Push fresh values to every team whose war UI or
     * next upkeep charge depends on the changed team.
     */
    public static void onUpkeepFactorsChanged(MinecraftServer server, UUID changedTeamId) {
        if (!WarService.isEnabled() || !TeamCatalog.exists(server, changedTeamId)) {
            return;
        }

        Set<UUID> synced = new HashSet<>();
        syncToTeam(server, changedTeamId);
        synced.add(changedTeamId);

        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        for (FtbHookSavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (entry.warTargets().contains(changedTeamId) && synced.add(entry.ftbTeamId())) {
                syncToTeam(server, entry.ftbTeamId());
            }
        }

        if (!WarService.isClaimTeam(server, changedTeamId)) {
            return;
        }
        for (UUID otherId : TeamCatalog.activeTeams(server)) {
            if (otherId.equals(changedTeamId) || !WarService.isClaimTeam(server, otherId)
                    || Backends.claim().onlineMembers(server, otherId).isEmpty()) {
                continue;
            }
            if (synced.add(otherId)) {
                syncToTeam(server, otherId);
            }
        }
    }

    private static SyncWarStatePayload createPayload(MinecraftServer server, UUID teamId, UUID viewerId) {
        WarCostService.WarCostBreakdown costs = WarCostService.calculateWarCosts(server, teamId);
        return new SyncWarStatePayload(
                costs.baseUpkeepCopper(),
                costs.incomingWarCopper(),
                costs.outgoingWarCopper(),
                WarService.warMultiplier(),
                toEntries(WarIncomingViewBuilder.buildIncomingViews(server, teamId)),
                toEntries(WarViewBuilder.buildOutgoingViews(server, teamId)),
                toEntries(WarViewBuilder.buildAvailableTargets(server, teamId)),
                viewerId != null && WarService.canManageWar(server, teamId, viewerId)
        );
    }

    private static List<WarTeamEntry> toEntries(List<WarTeamView> views) {
        return views.stream()
                .map(view -> new WarTeamEntry(
                        view.teamId(),
                        view.displayName(),
                        view.targetBaseUpkeepCopper(),
                        view.warCostCopper(),
                        view.status(),
                        view.opponentPendingDeclareOnViewer(),
                        view.blockEditProtected(),
                        view.explosionProtected(),
                        view.pvpProtected()
                ))
                .toList();
    }
}
