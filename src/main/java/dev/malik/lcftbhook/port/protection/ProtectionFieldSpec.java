package dev.malik.lcftbhook.port.protection;

import java.util.EnumSet;
import java.util.Set;

/**
 * One protection setting a claim backend offers (e.g. FTB Chunks' "block
 * edit mode", OPAC's "blocks from players"). Pure data - the backend's own
 * adapter translates it into whatever its mod enforces.
 *
 * @param id                 stable id, used as storage key in regions, pending
 *                           state, chunk overrides and lang keys
 * @param kind               {@link Kind#TOGGLE}: allowed (free) / protected;
 *                           {@link Kind#ACCESS}: Public (free) / Allies / Team / Private
 * @param accessListRelevant whether a private chunk owner may keep a
 *                           whitelist/blacklist of players for this field
 * @param roles              what the field means to backend-neutral features
 *                           (war vulnerability, Sable explosions, ...)
 * @param defaultPrice       default config price, tenths of a copper per
 *                           {@code ProtectionProperty.PRICE_UNIT_CHUNKS} chunks
 * @param description        config comment for the price entry
 * @param primary            one of the few protections players care about all
 *                           the time - always shown; the others ("advanced")
 *                           only when set or when the player expands them
 */
public record ProtectionFieldSpec(
        String id,
        Kind kind,
        boolean accessListRelevant,
        Set<Role> roles,
        long defaultPrice,
        String description,
        boolean primary
) {
    public enum Kind { TOGGLE, ACCESS }

    public enum Role { BLOCK_EDIT, BLOCK_INTERACT, ENTITY_INTERACT, EXPLOSIONS, MOB_GRIEFING, PVP }

    public ProtectionFieldSpec {
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public static ProtectionFieldSpec toggle(String id, long defaultPrice, String description, Role... roles) {
        return new ProtectionFieldSpec(id, Kind.TOGGLE, false, Set.of(roles), defaultPrice, description, false);
    }

    public static ProtectionFieldSpec access(String id, boolean accessListRelevant, long defaultPrice, String description, Role... roles) {
        return new ProtectionFieldSpec(id, Kind.ACCESS, accessListRelevant, Set.of(roles), defaultPrice, description, false);
    }

    /** The same field, marked as one of the always-shown main protections. */
    public ProtectionFieldSpec asPrimary() {
        return new ProtectionFieldSpec(id, kind, accessListRelevant, roles, defaultPrice, description, true);
    }
}
