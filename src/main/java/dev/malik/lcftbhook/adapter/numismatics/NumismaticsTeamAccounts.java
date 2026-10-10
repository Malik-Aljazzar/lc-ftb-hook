package dev.malik.lcftbhook.adapter.numismatics;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/**
 * A party's Numismatics account. Only {@code BLAZE_BANKER}-type accounts
 * carry a trust list and a label ({@code PLAYER} accounts authorize their
 * own id only), so parties get that type: its trust list mirrors the
 * party's officers and owner, which lets them open the account at a Bank
 * Terminal with a card bound via {@code /lc_ftb_hook team_bank card}.
 * A solo team's account stays the player's own {@code PLAYER} account.
 */
final class NumismaticsTeamAccounts {
    private NumismaticsTeamAccounts() {
    }

    static BankAccount account(MinecraftServer server, UUID teamId) {
        BankAccount existing = Numismatics.BANK.getAccount(teamId);
        if (!Backends.claim().isPartyTeam(server, teamId)) {
            return existing != null ? existing : Numismatics.BANK.getOrCreateAccount(teamId, BankAccount.Type.PLAYER);
        }
        if (existing == null) {
            return Numismatics.BANK.getOrCreateAccount(teamId, BankAccount.Type.BLAZE_BANKER);
        }
        return existing.type == BankAccount.Type.BLAZE_BANKER ? existing : migrate(existing);
    }

    /** Mirrors the party's officers/owner into the trust list and its name into the label (idempotent). */
    static void mirror(MinecraftServer server, UUID teamId) {
        ClaimPort claims = Backends.claim();
        if (!claims.isPartyTeam(server, teamId)) {
            return;
        }
        BankAccount account = account(server, teamId);
        List<UUID> trusted = claims.members(server, teamId).stream()
                .filter(member -> claims.rankOf(server, teamId, member).isOfficerOrBetter())
                .toList();
        account.updateTrustList(list -> {
            if (!list.equals(trusted)) {
                list.clear();
                list.addAll(trusted);
            }
        });
        String name = claims.teamDisplayName(server, teamId);
        if (name != null && !name.isBlank()) {
            account.setLabel(name);
        }
    }

    /** Party accounts created before trust-list support were {@code PLAYER} accounts; the type is final, so swap in a copy. */
    private static BankAccount migrate(BankAccount old) {
        BankAccount shared = new BankAccount(old.id, old.getBalance(), BankAccount.Type.BLAZE_BANKER);
        Numismatics.BANK.accounts.put(old.id, shared);
        Numismatics.BANK.markBankDirty();
        LCFtbHook.LOGGER.info("Converted Numismatics team account {} to a shared account ({} spurs)", old.id, old.getBalance());
        return shared;
    }
}
