package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.mixin.ftb.PartyTeamOwnerAccessor;
import dev.malik.lcftbhook.service.PartyOwnerChoice;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Party owners as this mod uses them, robust against parties that lost their
 * owner field (see {@link PartyOwnerChoice}), plus the one-time repair of such
 * parties in FTB Teams' own data.
 */
final class FtbPartyOwners {
    private FtbPartyOwners() {
    }

    @Nullable
    static UUID effectiveOwner(Team team) {
        return PartyOwnerChoice.choose(team.getOwner(), membersRankedOwner(team));
    }

    /**
     * Gives every party whose owner field is the nil id its owner back - only
     * when its rank list names exactly one owner, who is still a member. Anything
     * else is logged and left untouched for an admin.
     */
    static void repairMissingOwners() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            if (!team.isPartyTeam() || !PartyOwnerChoice.isMissing(team.getOwner())) {
                continue;
            }
            UUID owner = PartyOwnerChoice.choose(team.getOwner(), membersRankedOwner(team));
            if (owner == null || !(team instanceof PartyTeamOwnerAccessor accessor)) {
                LCFtbHook.LOGGER.error("FTB party {} ({}) has no owner and no unique member with the owner rank - left unchanged,"
                        + " an admin has to transfer its ownership", team.getShortName(), team.getId());
                continue;
            }
            accessor.lcFtbHook$setOwner(owner);
            team.markDirty();
            LCFtbHook.LOGGER.warn("Repaired FTB party {} ({}): its owner field was empty (damage from lc_ftb_hook 4.0.0),"
                    + " restored {} - the member its rank list names as owner", team.getShortName(), team.getId(), owner);
        }
    }

    private static List<UUID> membersRankedOwner(Team team) {
        return team.getMembers().stream().filter(id -> team.getRankForPlayer(id) == TeamRank.OWNER).toList();
    }
}
