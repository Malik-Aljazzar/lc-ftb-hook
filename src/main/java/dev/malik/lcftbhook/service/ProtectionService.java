package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class ProtectionService {
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private ProtectionService() {
    }

    public static void setApplying(boolean applying) {
        if (applying) {
            APPLYING.set(true);
        } else {
            APPLYING.remove();
        }
    }

    public static boolean canAffordNextPeriod(MinecraftServer server, UUID teamId) {
        TeamPendingState pendingState = FtbHookSavedData.get(server).getPendingState(teamId);
        return canAffordNextPeriod(server, teamId, pendingState);
    }

    public static boolean canAffordNextPeriod(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        int chunkCount = Backends.claim().claimedChunkCount(server, teamId);
        int forceLoadCount = ProtectionPricing.countEffectiveForceLoads(server, teamId, pendingState);
        if (chunkCount <= 0 && forceLoadCount <= 0) {
            return true;
        }
        long costCopper = WarCostService.calculateTotalUpkeepCostCopper(server, teamId, pendingState);
        return Backends.currency().teamAccount(server, teamId).canAfford(costCopper);
    }

    public static void tryUnlock(MinecraftServer server, UUID teamId) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        if (!data.isProtectionLocked(teamId)) {
            return;
        }
        if (canAffordNextPeriod(server, teamId)) {
            data.setProtectionLocked(teamId, false);
            notifyTeamManagers(server, teamId, Component.translatable("message.lc_ftb_hook.protection_unlocked"));
        }
    }

    /**
     * Officers/owner only - a plain member has no way to act on any upkeep
     * detail (can't pay it, can't change protections - see RegionService's
     * own rank checks), so this mod no longer broadcasts these to everyone.
     */
    public static void notifyTeamManagers(MinecraftServer server, UUID teamId, Component message) {
        boolean party = Backends.claim().isPartyTeam(server, teamId);
        for (ServerPlayer member : Backends.claim().onlineMembers(server, teamId)) {
            if (!party || Backends.claim().rankOf(server, teamId, member.getUUID()).isOfficerOrBetter()) {
                member.displayClientMessage(message, false);
            }
        }
    }
}
