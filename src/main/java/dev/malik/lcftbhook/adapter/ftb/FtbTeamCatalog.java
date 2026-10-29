package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.malik.lcftbhook.service.WarService;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central view of which FTB teams exist and how the mod should treat them.
 * Single-player teams and active parties participate in war, upkeep, and billing;
 * inactive/empty parties are ignored until they become active again.
 */
public final class FtbTeamCatalog {
    public enum TeamKind {
        INVALID,
        SINGLE_PLAYER,
        /** Admin-run team without players - never billed, no upkeep or wars. */
        SERVER,
        ACTIVE_PARTY,
        INACTIVE_PARTY
    }

    private FtbTeamCatalog() {
    }

    public static TeamKind kindOf(MinecraftServer server, @Nullable Team team) {
        if (team == null || !team.isValid()) {
            return TeamKind.INVALID;
        }
        if (team.isServerTeam()) {
            return TeamKind.SERVER;
        }
        if (!team.isPartyTeam()) {
            return TeamKind.SINGLE_PLAYER;
        }
        return isPartyInUse(team)
                ? TeamKind.ACTIVE_PARTY
                : TeamKind.INACTIVE_PARTY;
    }

    public static boolean isSinglePlayerTeam(@Nullable Team team) {
        return team != null && team.isValid() && team.isPlayerTeam();
    }

    public static boolean isPartyTeam(@Nullable Team team) {
        return team != null && team.isValid() && team.isPartyTeam();
    }

    public static boolean isActiveParty(MinecraftServer server, @Nullable Team team) {
        return kindOf(server, team) == TeamKind.ACTIVE_PARTY;
    }

    /** Teams that participate in war, upkeep, and other mod features. */
    public static boolean isTracked(MinecraftServer server, @Nullable Team team) {
        TeamKind kind = kindOf(server, team);
        return switch (kind) {
            case ACTIVE_PARTY -> true;
            case SINGLE_PLAYER -> isActiveSinglePlayerTeam(server, team);
            default -> false;
        };
    }

    /**
     * A solo FTB team counts only while at least one member still uses it as their active team.
     * Players who joined a party keep their personal team object, but it must not appear in war/upkeep.
     */
    public static boolean isActiveSinglePlayerTeam(MinecraftServer server, @Nullable Team team) {
        if (!isSinglePlayerTeam(team) || !FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        for (UUID memberId : team.getMembers()) {
            // getId(), not getTeamId(): getTeamId() of a party member's
            // personal team already returns the party, so the old comparison
            // called that leftover personal team "active" as well.
            if (manager.getTeamForPlayerID(memberId)
                    .map(activeTeam -> activeTeam.getId().equals(team.getId()))
                    .orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean exists(MinecraftServer server, UUID teamId) {
        return resolve(server, teamId) != null;
    }

    @Nullable
    public static Team resolve(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId).filter(Team::isValid).orElse(null);
    }

    /** A party at least one of its members still actively uses (a member who joined another party no longer counts). */
    private static boolean isPartyInUse(Team party) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        for (UUID memberId : party.getMembers()) {
            if (manager.getTeamForPlayerID(memberId).map(active -> active.getId().equals(party.getId())).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public static List<Team> allStoredTeams(MinecraftServer server) {
        if (server == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        List<Team> teams = new ArrayList<>();
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            if (team.isValid()) {
                teams.add(team);
            }
        }
        return teams;
    }

    public static List<Team> trackedTeams(MinecraftServer server) {
        List<Team> tracked = new ArrayList<>();
        for (Team team : allStoredTeams(server)) {
            if (isTracked(server, team)) {
                tracked.add(team);
            }
        }
        return tracked;
    }

    public static List<Team> activeParties(MinecraftServer server) {
        List<Team> parties = new ArrayList<>();
        for (Team team : allStoredTeams(server)) {
            if (isActiveParty(server, team)) {
                parties.add(team);
            }
        }
        return parties;
    }

    /**
     * Removes all active and pending war links for the given team and refreshes partners.
     */
    public static void dissolveWarLinks(MinecraftServer server, UUID teamId) {
        WarService.cleanupTeamWars(server, teamId);
    }

}
