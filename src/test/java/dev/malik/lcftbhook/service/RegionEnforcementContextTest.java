package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A region context that outlives its check would let a later check on the
 * same thread (the server thread) read some other chunk's Region - so
 * {@link RegionEnforcementContext#callWith} must restore the previous context
 * on every exit path.
 */
class RegionEnforcementContextTest {
    private static final RegionEnforcementContext.Resolved OUTER =
            new RegionEnforcementContext.Resolved(Region.create("outer"), ChunkOwnership.EMPTY);
    private static final RegionEnforcementContext.Resolved INNER =
            new RegionEnforcementContext.Resolved(Region.create("inner"), ChunkOwnership.EMPTY);

    @AfterEach
    void clear() {
        RegionEnforcementContext.clear();
    }

    @Test
    void contextIsOnlyVisibleInsideTheCall() {
        assertNull(RegionEnforcementContext.get());
        RegionEnforcementContext.Resolved seen = RegionEnforcementContext.callWith(INNER, RegionEnforcementContext::get);
        assertSame(INNER, seen);
        assertNull(RegionEnforcementContext.get());
    }

    @Test
    void nestedCallRestoresTheOuterContext() {
        RegionEnforcementContext.set(OUTER.region(), OUTER.ownership());
        RegionEnforcementContext.Resolved outerBefore = RegionEnforcementContext.get();
        RegionEnforcementContext.Resolved seen = RegionEnforcementContext.callWith(INNER, RegionEnforcementContext::get);
        assertSame(INNER, seen);
        assertSame(outerBefore, RegionEnforcementContext.get());
    }

    @Test
    void throwingCheckLeavesNoContextBehind() {
        assertThrows(IllegalStateException.class, () -> RegionEnforcementContext.callWith(INNER, () -> {
            throw new IllegalStateException("check failed");
        }));
        assertNull(RegionEnforcementContext.get());

        RegionEnforcementContext.set(OUTER.region(), OUTER.ownership());
        RegionEnforcementContext.Resolved outerBefore = RegionEnforcementContext.get();
        assertThrows(IllegalStateException.class, () -> RegionEnforcementContext.callWith(INNER, () -> {
            throw new IllegalStateException("check failed");
        }));
        assertSame(outerBefore, RegionEnforcementContext.get());
    }
}
