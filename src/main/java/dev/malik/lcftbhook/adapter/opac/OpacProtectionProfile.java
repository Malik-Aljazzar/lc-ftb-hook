package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.adapter.opac.OpacProtectionFields.OpacField;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionResolution;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The OPAC option values one chunk's claim config must hold: the chunk's
 * effective protection per field (region value, loosened by a private owner's
 * override), in OPAC's value format. Two chunks with equal profiles can share
 * one OPAC sub-config - unless one of them has a whitelist/blacklist: then it
 * gets a sub-config of its own ({@code exclusiveChunk}), so the per-player
 * list check can tell from the sub-config which chunk it is looking at.
 *
 * @param values         OPAC option id (without "playerConfig.") -> OPAC value
 * @param exclusiveChunk chunk key of the only chunk using this profile, or null if shareable
 */
record OpacProtectionProfile(Map<String, Object> values, @Nullable String exclusiveChunk) {
    static final String PROTECT_CLAIMED_CHUNKS = "claims.protectClaimedChunks";
    static final String FULL_ACCESS = "claims.protection.exceptions.fullAccess";
    /** Shown by OPAC when entering the claim; defaults to "<player>'s Claim" since OPAC claims belong to a player. */
    static final String CLAIM_NAME = "claims.name";

    static final String EVERYONE = "E";
    static final String ALLIES = "A";
    static final String PARTY = "P";
    static final String NOBODY = "N";

    static OpacProtectionProfile of(Region region, ChunkOwnership ownership, String chunkKey, String teamName) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(CLAIM_NAME, region.isDefault() ? teamName : teamName + " - " + region.name());
        // Our fields decide everything: OPAC's master switch always on, nobody
        // gets blanket full access by group (officers / private owners get it
        // through OpacChunkAccessOverrider instead).
        values.put(PROTECT_CLAIMED_CHUNKS, Boolean.TRUE);
        values.put(FULL_ACCESS, NOBODY);
        for (OpacField field : OpacProtectionFields.all()) {
            ProtectionProperty property = new ProtectionProperty(field.spec(), () -> 0L);
            values.put(field.optionId(), toOpac(field, ProtectionResolution.effectiveValue(region, ownership, property)));
        }
        boolean hasAccessLists = ownership.accessLists().values().stream().anyMatch(list -> !list.isEmpty());
        return new OpacProtectionProfile(Collections.unmodifiableMap(values), hasAccessLists ? chunkKey : null);
    }

    static Object toOpac(OpacField field, String serialized) {
        return switch (field.optionKind()) {
            case GROUP -> switch (PrivacyLevel.valueOf(serialized)) {
                case PUBLIC -> EVERYONE;
                case ALLIES -> ALLIES;
                case TEAM -> PARTY;
                case PRIVATE -> NOBODY;
            };
            case EXCEPTION -> Boolean.valueOf(serialized);
            case PROTECTION -> !Boolean.parseBoolean(serialized);
        };
    }
}
