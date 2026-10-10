package dev.malik.lcftbhook.adapter.opac;

import xaero.pac.common.claims.action.api.ClaimingAction;

/**
 * What an OPAC claiming action means for this mod. Matched by name, not by
 * enum constant: OPAC adds actions between versions (0.32 added anchor-based
 * claiming: ANCHOR, UNANCHOR, UNCLAIM_UNANCHORED), and referencing a constant
 * that doesn't exist in the installed OPAC would fail at runtime - while an
 * exhaustive switch over the enum fails on any constant it doesn't know.
 */
enum OpacActions {
    CLAIM, UNCLAIM, FORCELOAD, UNFORCELOAD,
    /** Doesn't add or remove claims (e.g. setting or removing a claiming anchor) - nothing to check or pay. */
    OTHER;

    static OpacActions of(ClaimingAction action) {
        return switch (action.name()) {
            case "CLAIM" -> CLAIM;
            // Claims OPAC removes because they are no longer near an anchor - an unclaim like any other (refund, region cleanup).
            case "UNCLAIM", "UNCLAIM_UNANCHORED" -> UNCLAIM;
            case "FORCELOAD" -> FORCELOAD;
            case "UNFORCELOAD" -> UNFORCELOAD;
            default -> OTHER;
        };
    }
}
