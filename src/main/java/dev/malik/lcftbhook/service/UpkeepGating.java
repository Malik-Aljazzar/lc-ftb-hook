package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.config.ProtectionUpkeepMode;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/**
 * Whether protection upkeep should actually be charged (and thus can be
 * dismantled for non-payment) for the current period, per
 * {@link LCFtbHookConfig.Server#protectionUpkeepMode}. When it isn't
 * chargeable this period its cost is treated as zero everywhere (billing, UI,
 * dismantle/restore affordability checks) - it is simply frozen until
 * someone relevant comes online again. Force-loads are decided per region
 * instead, see {@link ForceLoadBilling}.
 */
public final class UpkeepGating {
    private UpkeepGating() {
    }

    public static boolean shouldChargeProtection(MinecraftServer server, UUID teamId) {
        ProtectionUpkeepMode mode = LCFtbHookConfig.SERVER.protectionUpkeepMode.get();
        return switch (mode) {
            case HEAVY -> true;
            case DEFAULT -> isAnyPlayerOnlineOnServer(server);
            case LIGHT -> isAnyTeamMemberOnline(server, teamId);
        };
    }

    /**
     * Whether settlement passes must keep running while nobody is on the
     * server: protection on HEAVY, or some region whose force-loads are on
     * "Always". Every other mode charges nothing while empty, so pausing the
     * clock then loses nothing.
     */
    public static boolean chargesWhileEmpty(MinecraftServer server) {
        return isProtectionChargedWhileEmpty() || ForceLoadBilling.anyChargedWhileEmpty(server);
    }

    /**
     * Whether this team has anything to settle on an empty-server pass. Teams
     * that don't are skipped on such a pass - they'd be "settled" for zero,
     * which would also activate their freshly claimed chunks' protection
     * without anything ever being paid.
     */
    public static boolean teamChargedWhileEmpty(MinecraftServer server, UUID teamId) {
        return isProtectionChargedWhileEmpty()
                || ForceLoadBilling.teamChargedWhileEmpty(server, FtbHookSavedData.get(server), teamId);
    }

    public static boolean isProtectionChargedWhileEmpty() {
        return LCFtbHookConfig.SERVER.protectionUpkeepMode.get() == ProtectionUpkeepMode.HEAVY;
    }

    private static boolean isAnyTeamMemberOnline(MinecraftServer server, UUID teamId) {
        return !dev.malik.lcftbhook.port.Backends.claim().onlineMembers(server, teamId).isEmpty();
    }

    private static boolean isAnyPlayerOnlineOnServer(MinecraftServer server) {
        return server.getPlayerList().getPlayerCount() > 0;
    }
}
