package dev.malik.lcftbhook.adapter.lightmanscurrency;

import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A single, mod-owned bank account that "sink" payments (upkeep, chunk-claim
 * price, and so on) deposit into instead of destroying the money outright -
 * see SPEC.md's "server money account" feature request.
 * <p>
 * Backed by a real LC {@link PlayerBankReference} bound to a fixed, non-
 * player UUID - {@code PlayerBankReference.of(UUID)} never requires that UUID
 * to actually belong to a player, it's just the storage key
 * {@code BankDataCache.getAccount(UUID)} auto-creates an account under on
 * first access (confirmed via decompile - the same lazy-vivify path every
 * personal account already goes through, e.g. {@code PlayerBankReference.of(player.getUUID())}
 * elsewhere in this codebase). So this is a genuine, persistent
 * {@link IBankAccount} using LC's own machinery, not a bespoke one.
 * <p>
 * Access: {@code PlayerBankReferenceOpAccessMixin} widens
 * {@code PlayerBankReference.allowedAccess(Player)} to also accept any
 * vanilla-OP player (permission level 2+) specifically for THIS account's
 * UUID - deliberately not LC's own Admin Mode, which (confirmed via
 * decompile) grants access to every player's personal account at once, not
 * just this one. Every op can reach this account through LC's real ATM UI
 * once they select it, no custom banking screen needed. See
 * {@code ServerAccountCommand} for the one custom piece: pointing an op's
 * currently-selected account at this one, since there's no real player name
 * to type into LC's own admin account-search field.
 */
public final class LcServerAccount {
    public static final UUID SERVER_ACCOUNT_ID =
            UUID.nameUUIDFromBytes("lc_ftb_hook:server_account".getBytes(StandardCharsets.UTF_8));

    private LcServerAccount() {
    }

    public static final String DISPLAY_NAME = "Server Account";

    /**
     * {@code PlayerBankReference.of(UUID)} hardcodes an empty fallback name
     * ({@code PlayerReference.of(player, "")}) - fine for a real player
     * (their actual username always resolves instead), but our fixed UUID
     * never resolves to a real player. Building the {@link PlayerReference}
     * ourselves with an explicit name is still worth doing (it's what a
     * freshly-created account's name gets seeded from - see
     * {@code BankDataCache.generateBankAccount}), even though it turned out
     * NOT to be the actual fix for an already-existing account - see
     * {@link #get()}.
     */
    public static BankReference reference() {
        return PlayerBankReference.of(PlayerReference.of(SERVER_ACCOUNT_ID, DISPLAY_NAME));
    }

    /**
     * The REAL fix for "Unknown's Bank Account": confirmed via decompile that
     * {@code BankAccount.getName()} does NOT derive from the
     * {@link PlayerReference} at all - it returns a separate {@code
     * ownerName} field (literally defaulting to {@code "Unknown"}) that's
     * only ever seeded ONCE, at account creation
     * ({@code BankDataCache.generateBankAccount}), and frozen from then on.
     * Since this account was already created earlier (before this class
     * existed), fixing how the reference is built (see {@link #reference()})
     * had no effect on its already-frozen name - only calling
     * {@code updateOwnersName} on the account itself does. Cheap and
     * idempotent, so it's just done unconditionally on every fetch rather
     * than only once, self-healing if it's ever wrong again for any reason.
     */
    @Nullable
    public static IBankAccount get() {
        IBankAccount account = reference().get();
        if (account instanceof BankAccount concrete && !DISPLAY_NAME.equals(concrete.getOwnersName())) {
            concrete.updateOwnersName(DISPLAY_NAME);
        }
        return account;
    }
}
