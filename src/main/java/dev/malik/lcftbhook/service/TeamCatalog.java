package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Backend-neutral view of which teams exist and which participate in wars,
 * upkeep and billing ("active": a party someone still actively belongs to, or
 * a solo team its player still uses). Thin convenience layer over
 * {@code Backends.claim()} so core services read naturally.
 */
public final class TeamCatalog {
    private TeamCatalog() {
    }

    public static boolean exists(MinecraftServer server, UUID teamId) {
        return teamId != null && Backends.claim().teamExists(server, teamId);
    }

    public static boolean isActive(MinecraftServer server, UUID teamId) {
        return teamId != null && Backends.claim().isActiveTeam(server, teamId);
    }

    public static List<UUID> activeTeams(MinecraftServer server) {
        return Backends.claim().allTeams(server).stream().filter(id -> isActive(server, id)).toList();
    }

    public static boolean isParty(MinecraftServer server, UUID teamId) {
        return Backends.claim().isPartyTeam(server, teamId);
    }

    public static String displayName(MinecraftServer server, UUID teamId) {
        String name = Backends.claim().teamDisplayName(server, teamId);
        return name.isBlank() ? teamId.toString() : name;
    }

    @Nullable
    public static UUID teamOf(MinecraftServer server, UUID playerId) {
        return Backends.claim().teamIdForPlayer(server, playerId);
    }

    public static boolean isOfficer(MinecraftServer server, UUID teamId, UUID playerId) {
        return Backends.claim().rankOf(server, teamId, playerId).isOfficerOrBetter();
    }

    /** The player's own team if they're officer-or-better there, otherwise null. */
    @Nullable
    public static UUID managedTeamOf(MinecraftServer server, UUID playerId) {
        UUID teamId = teamOf(server, playerId);
        return teamId != null && isOfficer(server, teamId, playerId) ? teamId : null;
    }
}
