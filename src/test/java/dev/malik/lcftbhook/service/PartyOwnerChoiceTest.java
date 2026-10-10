package dev.malik.lcftbhook.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyOwnerChoiceTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void realOwner_isKept() {
        assertEquals(A, PartyOwnerChoice.choose(A, List.of(B)));
    }

    @Test
    void incident_4_0_0_nilOwner_fallsBackToTheOneOwnerRank() {
        // Production 2026-10-08: Luminous Empire saved with owner 00000000-..., ranks still list PieceOfChara as "owner".
        assertEquals(A, PartyOwnerChoice.choose(PartyOwnerChoice.NIL, List.of(A)));
        assertEquals(A, PartyOwnerChoice.choose(null, List.of(A, A)));
    }

    @Test
    void ambiguousOrNone_isNotGuessed() {
        assertNull(PartyOwnerChoice.choose(PartyOwnerChoice.NIL, List.of(A, B)));
        assertNull(PartyOwnerChoice.choose(PartyOwnerChoice.NIL, List.of()));
        assertNull(PartyOwnerChoice.choose(PartyOwnerChoice.NIL, List.of(PartyOwnerChoice.NIL)));
    }
}
