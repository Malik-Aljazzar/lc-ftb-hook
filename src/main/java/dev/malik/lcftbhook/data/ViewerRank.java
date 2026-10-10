package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.port.claim.ClaimRank;

/** The local player's standing in some team, as synced to the client. */
public enum ViewerRank {
    NONE, ALLY, MEMBER, OFFICER;

    public static ViewerRank of(ClaimRank rank) {
        if (rank.isOfficerOrBetter()) {
            return OFFICER;
        }
        if (rank.isMemberOrBetter()) {
            return MEMBER;
        }
        return rank.isAllyOrBetter() ? ALLY : NONE;
    }

    public boolean isAllyOrBetter() {
        return this != NONE;
    }

    public boolean isMemberOrBetter() {
        return this == MEMBER || this == OFFICER;
    }

    public boolean isOfficerOrBetter() {
        return this == OFFICER;
    }
}
