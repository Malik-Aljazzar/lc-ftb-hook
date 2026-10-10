package dev.malik.lcftbhook.data;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamLinkInvariantsTest {
    private static TeamLinkInvariants.Checked check(List<UUID> order, Map<UUID, Region> regions,
                                                    Map<String, UUID> chunkRegions, TeamPendingState pending) {
        return TeamLinkInvariants.check(order, regions, chunkRegions, pending);
    }

    private static Map<UUID, Region> regionsOf(Region... regions) {
        Map<UUID, Region> map = new LinkedHashMap<>();
        for (Region region : regions) {
            map.put(region.id(), region);
        }
        return map;
    }

    @Test
    void consistentEntry_isLeftUntouched() {
        Region def = Region.createDefault();
        Region land = Region.create("Land");
        var checked = check(List.of(land.id(), def.id()), regionsOf(def, land), Map.of("overworld:0:0", land.id()), new TeamPendingState());
        assertTrue(checked.repairs().isEmpty());
        assertEquals(List.of(land.id(), def.id()), checked.regionOrder());
        assertEquals(Map.of("overworld:0:0", land.id()), checked.chunkRegions());
    }

    @Test
    void missingDefault_isAdded() {
        Region land = Region.create("Land");
        var checked = check(List.of(land.id()), regionsOf(land), Map.of(), new TeamPendingState());
        assertEquals(1, checked.regions().values().stream().filter(Region::isDefault).count());
        assertEquals(2, checked.regionOrder().size());
    }

    @Test
    void secondDefault_becomesNormalRegion() {
        Region a = Region.createDefault();
        Region b = Region.createDefault();
        var checked = check(List.of(a.id(), b.id()), regionsOf(a, b), Map.of(), new TeamPendingState());
        assertEquals(1, checked.regions().values().stream().filter(Region::isDefault).count());
        assertEquals(2, checked.regions().size(), "no region is deleted");
    }

    @Test
    void chunkPointingAtMissingRegion_fallsBackToDefault() {
        Region def = Region.createDefault();
        Map<String, UUID> chunks = new HashMap<>();
        chunks.put("overworld:1:1", UUID.randomUUID());
        chunks.put("overworld:2:2", def.id());
        var checked = check(List.of(def.id()), regionsOf(def), chunks, new TeamPendingState());
        assertTrue(checked.chunkRegions().isEmpty());
        assertFalse(checked.repairs().isEmpty());
    }

    @Test
    void regionOrder_listsEveryRegionExactlyOnce() {
        Region def = Region.createDefault();
        Region land = Region.create("Land");
        UUID ghost = UUID.randomUUID();
        var checked = check(List.of(land.id(), ghost, land.id()), regionsOf(def, land), Map.of(), new TeamPendingState());
        assertEquals(List.of(land.id(), def.id()), checked.regionOrder());
    }

    @Test
    void pendingChangeForMissingRegion_isDropped() {
        Region def = Region.createDefault();
        UUID ghost = UUID.randomUUID();
        TeamPendingState pending = new TeamPendingState().withPendingProperty(RegionPropertyKey.encode(ghost, "allow_pvp"), "false");
        var checked = check(List.of(def.id()), regionsOf(def), Map.of(), pending);
        assertTrue(checked.pending().pendingProperties().isEmpty());
    }
}
