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

    /**
     * Every active team exactly once - upkeep bills each entry of this list,
     * so a team listed twice would be charged twice per pass (FTB listed a
     * party once per member until 4.4.2). Deduplicated here as well, not only
     * in the FTB adapter, so no claim backend can ever cause double billing.
     */
    public static List<UUID> activeTeams(MinecraftServer server) {
        return distinctActive(Backends.claim().allTeams(server), id -> isActive(server, id));
    }

    /** Backend order kept, nulls and repeats dropped, then filtered by {@code isActive}. */
    static List<UUID> distinctActive(List<UUID> teams, java.util.function.Predicate<UUID> isActive) {
        return teams.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .filter(isActive)
                .toList();
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
