package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

public class UpkeepService {
    // Static, not instance state - there's only ever one UpkeepService
    // instance (registered once in LCFtbHook), and every team shares the
    // same next-settlement tick since processTeamUpkeep below runs for all
    // of them in a single pass. Static makes it queryable from anywhere
    // (e.g. ClaimPriceSync, for "next payment due" display) without needing
    // a reference to that one instance.
    private static volatile long nextUpkeepTick = -1L;

    private static volatile boolean forceNextTick = false;

    /** Debug/testing hook (see {@code /lc_ftb_hook force_upkeep}): makes every tracked team's upkeep due on the very next server tick. */
    public static void forceNextUpkeep() {
        forceNextTick = true;
    }

    /** Minutes until the next settlement pass, or -1 if not yet known (server just started). Pauses while no players are online, same as the settlement loop itself, unless settleWhileEmpty is set. */
    public static int minutesUntilNextUpkeep(MinecraftServer server) {
        if (nextUpkeepTick < 0L) {
            return -1;
        }
        long remainingTicks = Math.max(0L, nextUpkeepTick - server.getTickCount());
        return (int) Math.ceil(remainingTicks / 20.0 / 60.0);
    }

    /**
     * Same as {@link #minutesUntilNextUpkeep}, but exact seconds instead of
     * ceil-rounded minutes - lets the client tick a live mm:ss countdown
     * between syncs instead of a static, whole-minutes value that only ever
     * changes when a new sync happens to land.
     */
    public static int secondsUntilNextUpkeep(MinecraftServer server) {
        if (nextUpkeepTick < 0L) {
            return -1;
        }
        long remainingTicks = Math.max(0L, nextUpkeepTick - server.getTickCount());
        return (int) (remainingTicks / 20L);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        dev.malik.lcftbhook.util.Guard.run("upkeep tick", () -> tick(event.getServer()));
    }

    private void tick(MinecraftServer server) {
        long periodTicks = LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get() * 60L * 20L;

        if (nextUpkeepTick < 0L) {
            nextUpkeepTick = server.getTickCount() + periodTicks;
            return;
        }

        if (forceNextTick) {
            forceNextTick = false;
            nextUpkeepTick = server.getTickCount();
        }

        if (!LCFtbHookConfig.SERVER.settleWhileEmpty.get()
                && server.getPlayerList().getPlayerCount() <= 0) {
            nextUpkeepTick++;
            return;
        }

        if (server.getTickCount() < nextUpkeepTick) {
            return;
        }

        nextUpkeepTick = server.getTickCount() + periodTicks;

        // Drop data of chunks that left a team behind this mod's back before anything is billed or retried.
        dev.malik.lcftbhook.util.Guard.run("chunk data check", () -> ChunkDataReconciler.reconcile(server));
        for (UUID teamId : TeamCatalog.activeTeams(server)) {
            try {
                processTeamUpkeep(server, teamId);
            } catch (Throwable e) {
                // Throwable (not just Exception): a transient classloading hiccup
                // (NoClassDefFoundError - see PartyTeamRankSyncMixin's javadoc for
                // the general pattern) must never kill the whole server tick loop.
                LCFtbHook.LOGGER.error("Failed to process upkeep for team {}", teamId, e);
            }
        }
        // One broadcast for the whole settlement pass (this only runs once per
        // upkeep period, so doing it unconditionally rather than tracking a
        // per-team dirty flag is negligible overhead) - covers every team whose
        // unsettled chunks just got cleared by processTeamUpkeep above.
        dev.malik.lcftbhook.service.MarketplaceService.broadcastUnsettledChunks(server);
    }

    private void processTeamUpkeep(MinecraftServer server, UUID teamId) {
        if (!TeamCatalog.exists(server, teamId)) {
            return;
        }

        Backends.currency().ensureTeamAccountExists(server, teamId);
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);

        int chunkCount = Backends.claim().claimedChunkCount(server, teamId);
        int forceLoadCount = ProtectionPricing.countEffectiveForceLoads(server, teamId, pendingState);

        if (chunkCount <= 0 && forceLoadCount <= 0 && pendingState.isEmpty()) {
            ProtectionService.tryUnlock(server, teamId);
            return;
        }

        UpkeepSettlementService.SettlementResult result = UpkeepSettlementService.settle(server, teamId);

        // Upkeep detail messages (what got restored/suspended/queued, top-up
        // reminders) are officers/owner only - a plain member has no way to
        // act on any of this (can't pay upkeep, can't change protections,
        // per RegionService's own rank checks), so broadcasting it to
        // everyone online was just noise for them.
        if (result.anythingRestored()) {
            ProtectionService.notifyTeamManagers(server, teamId,
                    UpkeepMessageBuilder.buildRestorationSummary(server, teamId, result.restoredProtections(), result.restoredWarNames()));
        }

        if (result.anythingSuspended()) {
            ProtectionService.notifyTeamManagers(server, teamId,
                    UpkeepMessageBuilder.buildSuspensionSummary(server, teamId, result.suspendedProtections(), result.suspendedWarNames()));
        }

        if (!result.unaffordableRestorations().isEmpty() || !result.unaffordableWarNames().isEmpty() || result.unaffordableForceLoadCount() > 0) {
            ProtectionService.notifyTeamManagers(server, teamId,
                    UpkeepMessageBuilder.buildUnaffordableRestorationMessage(
                            server, teamId, result.unaffordableRestorations(), result.unaffordableWarNames(), result.unaffordableForceLoadCount()));
        }

        UpkeepBreakdown breakdown = UpkeepBreakdown.capture(
                server,
                teamId,
                result.forceLoadCount(),
                result.paid() ? result.chargedCopper() : 0L,
                result.pendingState()
        );
        UpkeepBreakdownStore.store(breakdown);

        if (!result.paid()) {
            return;
        }

        // Upkeep successfully settled - every chunk claimed/bought since the
        // last settlement now uses its Region's real protection instead of
        // the "no protection at all yet" minimum.
        FtbHookSavedData.get(server).clearUnsettledChunks(teamId);
        // Also lifts the marketplace "not sellable yet" cooldown on chunks
        // freshly claimed since the last settlement.
        FtbHookSavedData.get(server).clearFreshlyClaimedChunks(teamId);
        dev.malik.lcftbhook.service.MarketplaceService.broadcastUnsettledChunks(server);

        if (result.chargedCopper() <= 0L) {
            ProtectionService.tryUnlock(server, teamId);
            return;
        }

        ProtectionService.tryUnlock(server, teamId);
        ProtectionService.notifyTeamManagers(server, teamId, UpkeepMessageBuilder.buildSummary(breakdown));
    }
}
