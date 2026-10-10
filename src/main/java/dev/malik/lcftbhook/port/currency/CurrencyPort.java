package dev.malik.lcftbhook.port.currency;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Backend-agnostic banking: resolve a team's or a player's account, format
 * amounts for display, and reach the server-wide "sink" account - without
 * any currency mod's own types leaking into business logic. See
 * {@code ARCHITECTURE.md}. Implementations: {@code LightmansCurrencyPort},
 * {@code NumismaticsCurrencyPort}; picked via {@code Backends.currency()}.
 * <p>
 * All amounts are in the backend's smallest unit ("copper" in this mod's
 * vocabulary - an LC copper coin, a Numismatics spur).
 * <p>
 * Team membership/rank questions are never answered by a currency backend
 * on its own - they come from whichever claim backend is active
 * ({@code Backends.claim()}), so every currency backend works with every
 * claim backend.
 */
public interface CurrencyPort {
    CurrencyAccount teamAccount(MinecraftServer server, UUID teamId);

    CurrencyAccount playerAccount(MinecraftServer server, UUID playerId);

    /**
     * Whether {@code playerId} is currently allowed to spend from
     * {@code teamId}'s account. Backends with their own per-team access
     * setting (Lightman's Currency's team bank limit) honor it; others fall
     * back to the claim backend's rank (officer or better).
     */
    boolean canPlayerSpend(MinecraftServer server, UUID teamId, UUID playerId);

    /**
     * Ensures the backing account for a team exists and mirrors the team's
     * current membership (idempotent) - called whenever a team is created or
     * its members/ranks/name change.
     */
    void ensureTeamAccountExists(MinecraftServer server, UUID teamId);

    /** A team was deleted/disbanded - drop any backend-side mirror of it (after its money was settled). */
    void onTeamDeleted(MinecraftServer server, UUID teamId);

    /**
     * Startup/login consistency pass: repair or drop backend-side team mirrors
     * that drifted from the claim backend (e.g. an LC team deleted by hand).
     */
    void reconcile(MinecraftServer server);

    /**
     * The server-wide account that sink payments (upkeep, claim prices) are
     * deposited into instead of destroying the money.
     */
    CurrencyAccount serverAccount(MinecraftServer server);

    /**
     * Backend-specific follow-up for {@code /lc_ftb_hook server_account} run by
     * an op (e.g. Lightman's Currency selects the account in its ATM UI).
     * Returns a message describing what happened, or null when there's
     * nothing beyond showing the balance.
     */
    @Nullable
    Component focusServerAccount(ServerPlayer op);

    /**
     * Formats an amount for display, in this backend's own coin notation.
     * Must be safe to call on the logical client too (menus format prices).
     * Callers handle the "free"/zero case themselves.
     */
    Component format(long amount);

    /**
     * The coin denominations, highest value first, used by price-input
     * widgets. Must be safe to call on the logical client too.
     */
    List<Denomination> denominations();

    /**
     * Binds the bank card in {@code player}'s main hand to {@code teamId}'s
     * account, for backends whose team accounts are opened with a card.
     * Callers check the player's spending rights first.
     */
    default CardBinding bindTeamCard(ServerPlayer player, UUID teamId) {
        return CardBinding.UNSUPPORTED;
    }

    enum CardBinding { BOUND, NO_CARD, UNSUPPORTED }

    /** One coin type: its display label (name, or item icon where the backend has one) and its value in the smallest unit. */
    record Denomination(Component name, long value) {
    }
}
