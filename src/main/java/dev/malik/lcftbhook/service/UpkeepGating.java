package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.config.ForceLoadUpkeepMode;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.config.ProtectionUpkeepMode;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/**
 * Whether force-load / protection upkeep should actually be charged (and
 * thus can be dismantled for non-payment) for the current period, per
 * {@link LCFtbHookConfig.Server#forceLoadUpkeepMode}/{@link LCFtbHookConfig.Server#protectionUpkeepMode}.
 * When a category isn't chargeable this period its cost is treated as zero
 * everywhere (billing, UI, dismantle/restore affordability checks) - it is
 * simply frozen until someone relevant comes online again.
 */
public final class UpkeepGating {
    private UpkeepGating() {
    }

    public static boolean shouldChargeForceLoad(MinecraftServer server, UUID teamId) {
        ForceLoadUpkeepMode mode = LCFtbHookConfig.SERVER.forceLoadUpkeepMode.get();
        return switch (mode) {
            case ALWAYS -> true;
            case DEFAULT -> isAnyTeamMemberOnline(server, teamId);
        };
    }

    public static boolean shouldChargeProtection(MinecraftServer server, UUID teamId) {
        ProtectionUpkeepMode mode = LCFtbHookConfig.SERVER.protectionUpkeepMode.get();
        return switch (mode) {
            case HEAVY -> true;
            case DEFAULT -> isAnyPlayerOnlineOnServer(server);
            case LIGHT -> isAnyTeamMemberOnline(server, teamId);
        };
    }

    private static boolean isAnyTeamMemberOnline(MinecraftServer server, UUID teamId) {
        return !dev.malik.lcftbhook.port.Backends.claim().onlineMembers(server, teamId).isEmpty();
    }

    private static boolean isAnyPlayerOnlineOnServer(MinecraftServer server) {
        return server.getPlayerList().getPlayerCount() > 0;
    }
}
