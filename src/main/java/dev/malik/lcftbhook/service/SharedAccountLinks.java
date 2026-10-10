package dev.malik.lcftbhook.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which parties lose their link when one bank-account team (e.g. an
 * LC team) is linked to several parties. That must never be decided blindly:
 * the load-time check of 4.1.0 cleared whichever link came second in map
 * order - possibly the one of the real, active party, which then got a new,
 * empty account. This is only decided at runtime, when the claim mod can say
 * which parties exist and the account team can say whose it is.
 */
public final class SharedAccountLinks {
    /**
     * One party linked to the shared account team.
     *
     * @param active       the party exists and is in use
     * @param ownerMatches the account team's owner is this party's owner
     * @param nameMatches  the account team's name is this party's name
     */
    public record Claimant(UUID partyId, boolean active, boolean ownerMatches, boolean nameMatches) {
    }

    private SharedAccountLinks() {
    }

    /**
     * The parties whose link to the shared account team is cleared. The account
     * stays with the one active party it demonstrably belongs to (owner first,
     * then name); links of parties that don't exist or aren't in use are only
     * cleared when an active party keeps the account. If that can't be decided,
     * nothing is cleared - the caller reports it for an admin.
     */
    public static Set<UUID> toUnlink(List<Claimant> claimants) {
        if (claimants.size() < 2) {
            return Set.of();
        }
        List<Claimant> active = claimants.stream().filter(Claimant::active).toList();
        Claimant keeper = active.size() == 1 ? active.get(0) : null;
        if (keeper == null) {
            keeper = single(active.stream().filter(Claimant::ownerMatches).toList());
        }
        if (keeper == null) {
            keeper = single(active.stream().filter(Claimant::nameMatches).toList());
        }
        if (keeper == null) {
            return Set.of();
        }
        Set<UUID> unlink = new HashSet<>();
        for (Claimant claimant : claimants) {
            if (!claimant.partyId().equals(keeper.partyId())) {
                unlink.add(claimant.partyId());
            }
        }
        return unlink;
    }

    private static Claimant single(List<Claimant> candidates) {
        return candidates.size() == 1 ? candidates.get(0) : null;
    }
}
