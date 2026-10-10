package dev.malik.lcftbhook.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MassChangeGuardTest {
    @Test
    void singleDeletions_areNormal() {
        assertFalse(MassChangeGuard.looksLikeIncompleteLoad(1, 30));
        assertFalse(MassChangeGuard.looksLikeIncompleteLoad(2, 3));
    }

    @Test
    void incident_4_0_0_isRecognized() {
        // Production 2026-10-01: FTB loaded 2 of 30 teams, 6 hook entries looked "deleted".
        assertTrue(MassChangeGuard.looksLikeIncompleteLoad(6, 8));
        assertTrue(MassChangeGuard.looksLikeIncompleteLoad(28, 30));
    }

    @Test
    void fewOfMany_isNormal() {
        assertFalse(MassChangeGuard.looksLikeIncompleteLoad(3, 30));
    }
}
