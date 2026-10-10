package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * War declare/dismantle ordering for upkeep settlement - split out of
 * {@link WarService} to mirror {@link ProtectionDismantleOrder}'s own
 * separation of "ordering policy" from state mutation.
 */
public final class WarDismantleOrder {
    private WarDismantleOrder() {
    }

    /** Cheapest-to-declare pending war restored first. */
    public static List<UUID> pendingWarRestoreOrder(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            FtbHookSavedData savedData
    ) {
        List<UUID> targets = new ArrayList<>();
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (!savedData.isAtWarWith(teamId, targetId)) {
                targets.add(targetId);
            }
        }
        targets.sort(Comparator.comparingLong(id -> costOrMax(server, teamId, id)));
        return targets;
    }

    private static long costOrMax(MinecraftServer server, UUID teamId, UUID targetId) {
        return TeamCatalog.exists(server, targetId) ? WarCostService.costToDeclareWar(server, teamId, targetId) : Long.MAX_VALUE;
    }

    /** Most expensive active outgoing war dismantled first. */
    public static List<UUID> outgoingWarDismantleOrder(MinecraftServer server, UUID teamId, FtbHookSavedData savedData) {
        List<UUID> targets = new ArrayList<>(savedData.getWarTargets(teamId));
        // Deterministic tie-break for equal-cost targets - sort is stable, so
        // this ordering survives the cost-based sort below for any ties.
        targets.sort(Comparator.comparing(UUID::toString));

        Map<UUID, Long> costByTarget = outgoingCostByTarget(server, targets);
        targets.sort(Comparator.comparingLong(id -> costByTarget.getOrDefault(id, 0L)).reversed());
        return targets;
    }

    private static Map<UUID, Long> outgoingCostByTarget(MinecraftServer server, List<UUID> targets) {
        Map<UUID, Long> costByTarget = new HashMap<>();
        for (UUID targetId : targets) {
            long cost = TeamCatalog.exists(server, targetId) ? WarUpkeepMath.outgoingWarCostCopper(WarCostService.baseUpkeepCopper(server, targetId)) : 0L;
            costByTarget.put(targetId, cost);
        }
        return costByTarget;
    }
}
