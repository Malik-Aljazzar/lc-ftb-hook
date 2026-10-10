package dev.malik.lcftbhook.adapter.lightmanscurrency;

import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import io.github.lightman314.lightmanscurrency.common.teams.Team;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/**
 * LC teams that belong to a party but lost their link (e.g. the hook's saved
 * link data was lost). They hold the party's bank balance, so a party without
 * a link takes one of them back instead of getting a fresh, empty team - and
 * an unlinked team with money in it is never deleted as a "duplicate".
 */
final class LcOrphanTeams {
    private LcOrphanTeams() {
    }

    /**
     * The unlinked LC team owned by {@code ownerId} that most likely is this
     * party's: same name first, then the one holding money, then the oldest.
     */
    @Nullable
    static Team find(TeamDataCache cache, Set<Long> linkedIds, UUID ownerId, String expectedName) {
        return cache.getAllTeams().stream()
                .filter(team -> team instanceof Team && !linkedIds.contains(team.getID()))
                .filter(team -> ownerId.equals(team.getOwner().id))
                .min(Comparator.<ITeam>comparingInt(team -> expectedName.equals(team.getName()) ? 0 : 1)
                        .thenComparingInt(team -> hasMoney(team) ? 0 : 1)
                        .thenComparingLong(ITeam::getID))
                .map(Team.class::cast)
                .orElse(null);
    }

    static boolean hasMoney(ITeam team) {
        return team.hasBankAccount()
                && team.getBankAccount().getMoneyStorage().allValues().stream().anyMatch(value -> !value.isEmpty());
    }

}
