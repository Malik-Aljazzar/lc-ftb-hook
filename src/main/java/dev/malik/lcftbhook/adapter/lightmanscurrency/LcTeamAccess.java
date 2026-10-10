package dev.malik.lcftbhook.adapter.lightmanscurrency;

import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import io.github.lightman314.lightmanscurrency.common.teams.TeamBankAccount;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

final class LcTeamAccess {
    private static final Field TEAM_OWNER;
    private static final Field TEAM_NAME;
    private static final Field TEAM_ADMINS;
    private static final Field TEAM_MEMBERS;
    private static final Field TEAM_BANK_ACCOUNT;
    private static final Field CACHE_TEAMS;
    private static final Method CACHE_GET_NEXT_ID;

    static {
        try {
            TEAM_OWNER = Team.class.getDeclaredField("owner");
            TEAM_OWNER.setAccessible(true);
            TEAM_NAME = Team.class.getDeclaredField("teamName");
            TEAM_NAME.setAccessible(true);
            TEAM_ADMINS = Team.class.getDeclaredField("admins");
            TEAM_ADMINS.setAccessible(true);
            TEAM_MEMBERS = Team.class.getDeclaredField("members");
            TEAM_MEMBERS.setAccessible(true);
            TEAM_BANK_ACCOUNT = Team.class.getDeclaredField("bankAccount");
            TEAM_BANK_ACCOUNT.setAccessible(true);
            CACHE_TEAMS = TeamDataCache.class.getDeclaredField("teams");
            CACHE_TEAMS.setAccessible(true);
            CACHE_GET_NEXT_ID = TeamDataCache.class.getDeclaredMethod("getNextID");
            CACHE_GET_NEXT_ID.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private LcTeamAccess() {
    }

    @Nullable
    static TeamDataCache cache() {
        if (!TeamDataCache.TYPE.isLoaded(false)) {
            return null;
        }
        return TeamDataCache.TYPE.get(false);
    }

    static Team registerTeam(PlayerReference owner, String name) {
        TeamDataCache cache = cache();
        if (cache == null) {
            throw new IllegalStateException("LC team data is not loaded yet");
        }
        try {
            long id = (long) CACHE_GET_NEXT_ID.invoke(cache);
            Team team = Team.of(id, owner, name).initialize();
            @SuppressWarnings("unchecked")
            Map<Long, Team> teams = (Map<Long, Team>) CACHE_TEAMS.get(cache);
            teams.put(id, team);
            cache.markTeamDirty(id);
            return team;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register LC team", exception);
        }
    }

    static void setOwner(Team team, PlayerReference owner) {
        try {
            TEAM_OWNER.set(team, owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to set LC team owner", exception);
        }
    }

    static void setName(Team team, String name) {
        try {
            TEAM_NAME.set(team, name);
            TeamBankAccount bankAccount = (TeamBankAccount) TEAM_BANK_ACCOUNT.get(team);
            if (bankAccount != null) {
                bankAccount.updateOwnersName(name);
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to set LC team name", exception);
        }
    }

    @SuppressWarnings("unchecked")
    static List<PlayerReference> admins(Team team) {
        try {
            return (List<PlayerReference>) TEAM_ADMINS.get(team);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to read LC team admins", exception);
        }
    }

    @SuppressWarnings("unchecked")
    static List<PlayerReference> members(Team team) {
        try {
            return (List<PlayerReference>) TEAM_MEMBERS.get(team);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to read LC team members", exception);
        }
    }

    static void createBankAccount(Team team) {
        if (team.hasBankAccount()) {
            return;
        }
        try {
            TeamBankAccount bankAccount = new TeamBankAccount(team, team::markDirty);
            bankAccount.updateOwnersName(team.getName());
            TEAM_BANK_ACCOUNT.set(team, bankAccount);
            team.markDirty();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to create LC team bank account", exception);
        }
    }

    static void replacePlayerReference(List<PlayerReference> list, int index, PlayerReference replacement) {
        list.set(index, replacement);
    }

    static PlayerReference ensureNamed(PlayerReference reference, String name) {
        String current = reference.getName(false);
        if (current != null && !current.isBlank()) {
            return reference;
        }
        return reference.copyWithName(name);
    }
}
