package dev.malik.lcftbhook.port.claim;

/**
 * A player's standing on a team, backend-agnostic - mirrors the predicate
 * shape FTB Teams' own {@code TeamRank} already exposes ({@code
 * isMemberOrBetter}/{@code isAllyOrBetter}/{@code isOfficerOrBetter}), so a
 * future migration of call sites is mechanical (rename the type, keep the
 * same method calls) rather than a rewrite of the conditions themselves.
 */
public interface ClaimRank {
    boolean isMemberOrBetter();

    boolean isAllyOrBetter();

    boolean isOfficerOrBetter();
}
