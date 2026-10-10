package dev.malik.lcftbhook.service;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Who owns a party when the claim mod's own owner field is unusable.
 * <p>
 * lc_ftb_hook 4.0.0 could abort FTB Teams while it was loading a party - after
 * the member ranks were read but before the owner field was. Such a party was
 * then saved with the "nil" owner {@code 00000000-...}, while its rank list
 * still names the real owner. This mod must never pass that nil id on (e.g.
 * as the owner of a Lightman's Currency team), and may only repair it when the
 * answer is unambiguous.
 */
public final class PartyOwnerChoice {
    public static final UUID NIL = new UUID(0L, 0L);

    private PartyOwnerChoice() {
    }

    public static boolean isMissing(@Nullable UUID owner) {
        return owner == null || NIL.equals(owner);
    }

    /**
     * The stored owner if it is a real player; otherwise the one member whose
     * rank is "owner"; otherwise null - never a guess between several.
     */
    @Nullable
    public static UUID choose(@Nullable UUID storedOwner, Collection<UUID> membersRankedOwner) {
        if (!isMissing(storedOwner)) {
            return storedOwner;
        }
        UUID only = null;
        for (UUID candidate : membersRankedOwner) {
            if (isMissing(candidate) || candidate.equals(only)) {
                continue;
            }
            if (only != null) {
                return null;
            }
            only = candidate;
        }
        return only;
    }
}
