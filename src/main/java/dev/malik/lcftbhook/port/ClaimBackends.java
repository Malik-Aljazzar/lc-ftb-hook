package dev.malik.lcftbhook.port;

import dev.malik.lcftbhook.adapter.ftb.FtbClaimPort;
import dev.malik.lcftbhook.adapter.opac.OpacClaimPort;
import dev.malik.lcftbhook.port.claim.ClaimPort;

import java.util.List;

/**
 * {@link ClaimPort} candidates in priority order - FTB Chunks first (this
 * mod's original, most-tested backend), then OPAC. See
 * {@link BackendCandidate}'s javadoc for how a future third claim backend
 * would be added here.
 * <p>
 * Factories are lambdas, not constructor references ({@code Foo::new}), on
 * purpose: a constructor reference is resolved as soon as this list is built,
 * which would load every adapter class - and with it the classes of mods that
 * may not be installed. A lambda only touches the adapter when invoked.
 */
final class ClaimBackends {
    static final List<BackendCandidate<ClaimPort>> CANDIDATES = List.of(
            new BackendCandidate<>("FTB", "ftbchunks", () -> new FtbClaimPort()),
            new BackendCandidate<>("OPAC", "openpartiesandclaims", () -> new OpacClaimPort())
    );

    private ClaimBackends() {
    }
}
