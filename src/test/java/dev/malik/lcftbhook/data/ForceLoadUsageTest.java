package dev.malik.lcftbhook.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ForceLoadUsageTest {
    private static final UUID TEAM = UUID.randomUUID();
    private static final UUID OTHER_TEAM = UUID.randomUUID();
    private static final UUID REGION = UUID.randomUUID();
    private static final UUID OTHER_REGION = UUID.randomUUID();

    @Test
    void markUsed_reportsChangeOnlyOnce() {
        ForceLoadUsage usage = new ForceLoadUsage();
        assertTrue(usage.markUsed(TEAM, REGION));
        assertFalse(usage.markUsed(TEAM, REGION));
        assertTrue(usage.wasUsed(TEAM, REGION));
        assertFalse(usage.wasUsed(TEAM, OTHER_REGION));
        assertFalse(usage.wasUsed(OTHER_TEAM, REGION));
    }

    @Test
    void clear_startsNewPeriodForThatTeamOnly() {
        ForceLoadUsage usage = new ForceLoadUsage();
        usage.markUsed(TEAM, REGION);
        usage.markUsed(OTHER_TEAM, REGION);
        assertTrue(usage.clear(TEAM));
        assertFalse(usage.clear(TEAM));
        assertFalse(usage.wasUsed(TEAM, REGION));
        assertTrue(usage.wasUsed(OTHER_TEAM, REGION));
    }

    @Test
    void snapshot_isACopy() {
        ForceLoadUsage usage = new ForceLoadUsage();
        usage.markUsed(TEAM, REGION);
        Map<UUID, java.util.Set<UUID>> saved = usage.snapshot(id -> true);
        usage.clear(TEAM);
        assertTrue(ForceLoadUsage.of(saved).wasUsed(TEAM, REGION));
    }

    @Test
    void survivesSaveAndLoad() {
        // A restart mid-period must not forget usage - otherwise it would go unbilled.
        ForceLoadUsage usage = new ForceLoadUsage();
        usage.markUsed(TEAM, REGION);
        usage.markUsed(TEAM, OTHER_REGION);
        ForceLoadUsage loaded = ForceLoadUsage.of(usage.snapshot(id -> true));
        assertTrue(loaded.wasUsed(TEAM, REGION));
        assertTrue(loaded.wasUsed(TEAM, OTHER_REGION));
    }

    @Test
    void deletedTeams_areDroppedOnSave() {
        ForceLoadUsage usage = new ForceLoadUsage();
        usage.markUsed(TEAM, REGION);
        usage.markUsed(OTHER_TEAM, REGION);
        ForceLoadUsage loaded = ForceLoadUsage.of(usage.snapshot(TEAM::equals));
        assertTrue(loaded.wasUsed(TEAM, REGION));
        assertFalse(loaded.wasUsed(OTHER_TEAM, REGION));
    }

    @Test
    void saveFromBeforeThisExisted_loadsEmpty() {
        ForceLoadUsage loaded = ForceLoadUsage.of(Map.of());
        assertFalse(loaded.wasUsed(TEAM, REGION));
    }
}
