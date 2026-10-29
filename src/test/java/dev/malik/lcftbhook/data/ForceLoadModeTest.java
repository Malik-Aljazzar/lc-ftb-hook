package dev.malik.lcftbhook.data;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ForceLoadModeTest {
    @Test
    void enabledMode_isUsedAsIs() {
        for (ForceLoadMode mode : ForceLoadMode.values()) {
            assertEquals(Optional.of(mode), mode.effective(m -> true));
        }
    }

    @Test
    void disabledMode_fallsBackToNextCheaper_neverMoreExpensive() {
        assertEquals(Optional.of(ForceLoadMode.ANY_PLAYER),
                ForceLoadMode.ALWAYS.effective(Set.of(ForceLoadMode.ANY_PLAYER, ForceLoadMode.TEAM_ONLINE)::contains));
        assertEquals(Optional.of(ForceLoadMode.TEAM_ONLINE),
                ForceLoadMode.ALWAYS.effective(Set.of(ForceLoadMode.TEAM_ONLINE)::contains));
        assertEquals(Optional.of(ForceLoadMode.TEAM_ONLINE),
                ForceLoadMode.ANY_PLAYER.effective(Set.of(ForceLoadMode.TEAM_ONLINE, ForceLoadMode.ALWAYS)::contains));
    }

    @Test
    void noCheaperModeEnabled_rests() {
        // Only a more expensive mode is allowed - the team never gets moved up to it.
        assertEquals(Optional.empty(), ForceLoadMode.TEAM_ONLINE.effective(Set.of(ForceLoadMode.ALWAYS)::contains));
        assertEquals(Optional.empty(), ForceLoadMode.ANY_PLAYER.effective(Set.of(ForceLoadMode.ALWAYS)::contains));
        assertEquals(Optional.empty(), ForceLoadMode.ALWAYS.effective(m -> false));
    }

    @Test
    void ids_roundTrip_andUnknownFallsBack() {
        for (ForceLoadMode mode : ForceLoadMode.values()) {
            assertEquals(mode, ForceLoadMode.byId(mode.id(), null));
        }
        assertEquals(ForceLoadMode.DEFAULT, ForceLoadMode.byId("ALWAYS", ForceLoadMode.DEFAULT));
        assertNull(ForceLoadMode.byId("", null));
    }

    @Test
    void savedIds_neverChange() {
        // Written into world saves and packets - renaming one would reset every region using it.
        assertEquals("team_online", ForceLoadMode.TEAM_ONLINE.id());
        assertEquals("any_player", ForceLoadMode.ANY_PLAYER.id());
        assertEquals("always", ForceLoadMode.ALWAYS.id());
    }
}
