package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.bank.ClaimResultIds;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * All-or-nothing check before a multi-chunk claim: if the team can't pay for
 * every chunk in the selection, none of them is claimed (instead of claiming
 * until the money runs out). Claim adapters count the claimable chunks with
 * their own mod's rules and ask here.
 */
public final class BulkClaimAffordability {
    private BulkClaimAffordability() {
    }

    public record Refusal(Component message, int paidClaims) {
    }

    /** Null when the claim may go ahead (affordable, free, a single chunk, or the player can't spend anyway - the per-chunk check reports that). */
    @Nullable
    public static Refusal check(MinecraftServer server, UUID playerId, UUID teamId, int claimedBefore, int claimable) {
        if (claimable <= 1 || !Backends.currency().canPlayerSpend(server, teamId, playerId)) {
            return null;
        }
        int paidClaims = FreeChunkAllowance.countPaidClaimsInBatch(claimedBefore, claimable);
        long totalPrice = paidClaims <= 0 ? 0L : ClaimPricingService.sumPriceForRange(claimedBefore, claimable);
        if (totalPrice <= 0L) {
            return null;
        }
        Backends.currency().ensureTeamAccountExists(server, teamId);
        CurrencyAccount account = Backends.currency().teamAccount(server, teamId);
        if (account.canAfford(totalPrice)) {
            return null;
        }
        Component message = Component.translatable(ClaimResultIds.INSUFFICIENT_FUNDS_BULK,
                Money.formatValue(totalPrice), paidClaims, Money.formatBalance(account));
        return new Refusal(message, paidClaims);
    }
}
