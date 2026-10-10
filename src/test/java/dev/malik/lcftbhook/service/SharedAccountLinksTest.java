package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.service.SharedAccountLinks.Claimant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SharedAccountLinksTest {
    private static final UUID LIVE = UUID.randomUUID();
    private static final UUID GONE = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void singleLink_isLeftAlone() {
        assertEquals(Set.of(), SharedAccountLinks.toUnlink(List.of(new Claimant(LIVE, true, true, true))));
    }

    @Test
    void deletedParty_losesTheLink_activeOneKeepsIt() {
        // The 4.1.0 load check could clear the LIVE party's link here, depending on map order.
        var result = SharedAccountLinks.toUnlink(List.of(
                new Claimant(GONE, false, true, true),
                new Claimant(LIVE, true, false, false)));
        assertEquals(Set.of(GONE), result);
    }

    @Test
    void twoActiveParties_accountStaysWithItsOwner() {
        var result = SharedAccountLinks.toUnlink(List.of(
                new Claimant(OTHER, true, false, true),
                new Claimant(LIVE, true, true, false)));
        assertEquals(Set.of(OTHER), result);
    }

    @Test
    void twoActiveParties_sameOwner_nameDecides() {
        var result = SharedAccountLinks.toUnlink(List.of(
                new Claimant(OTHER, true, true, false),
                new Claimant(LIVE, true, true, true)));
        assertEquals(Set.of(OTHER), result);
    }

    @Test
    void undecidable_clearsNothing() {
        assertEquals(Set.of(), SharedAccountLinks.toUnlink(List.of(
                new Claimant(OTHER, true, false, false),
                new Claimant(LIVE, true, false, false))));
        assertEquals(Set.of(), SharedAccountLinks.toUnlink(List.of(
                new Claimant(GONE, false, true, true),
                new Claimant(OTHER, false, false, false))));
    }
}
