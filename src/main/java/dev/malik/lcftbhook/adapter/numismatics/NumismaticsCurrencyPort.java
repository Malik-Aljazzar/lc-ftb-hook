package dev.malik.lcftbhook.adapter.numismatics;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.bank.CardItem;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * {@link CurrencyPort} backed by Create: Numismatics. 1 "copper" in this
 * mod's ledger = 1 spur ({@code Coin.SPUR}, the smallest denomination).
 * <p>
 * Numismatics has no team/party account concept, so a team account is keyed
 * by the team's UUID (solo team id == player UUID, so a solo player's team
 * account IS their own Numismatics account); see
 * {@link NumismaticsTeamAccounts} for how parties reach theirs at a Bank
 * Terminal. Spending rights come from the claim backend's rank (officer or
 * better), since Numismatics has no per-team access setting.
 */
public final class NumismaticsCurrencyPort implements CurrencyPort {
    static final UUID SERVER_ACCOUNT_ID = UUID.nameUUIDFromBytes("lc_ftb_hook:server_account".getBytes(StandardCharsets.UTF_8));

    @Override
    public CurrencyAccount teamAccount(MinecraftServer server, UUID teamId) {
        String label = Backends.claim().teamDisplayName(server, teamId);
        return new NumismaticsAccount(NumismaticsTeamAccounts.account(server, teamId), label.isBlank() ? teamId.toString() : label);
    }

    @Override
    public CurrencyAccount playerAccount(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return account(playerId, player != null ? player.getGameProfile().getName() : playerId.toString());
    }

    @Override
    public boolean canPlayerSpend(MinecraftServer server, UUID teamId, UUID playerId) {
        if (!Backends.claim().isPartyTeam(server, teamId)) {
            return true;
        }
        return Backends.claim().rankOf(server, teamId, playerId).isOfficerOrBetter();
    }

    @Override
    public void ensureTeamAccountExists(MinecraftServer server, UUID teamId) {
        NumismaticsTeamAccounts.mirror(server, teamId);
    }

    @Override
    public CardBinding bindTeamCard(ServerPlayer player, UUID teamId) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CardItem)) {
            return CardBinding.NO_CARD;
        }
        NumismaticsTeamAccounts.mirror(player.server, teamId);
        CardItem.set(held, teamId);
        return CardBinding.BOUND;
    }

    /** Nothing to mirror - the team's leftover money was already settled by the caller. */
    @Override
    public void onTeamDeleted(MinecraftServer server, UUID teamId) {
    }

    @Override
    public void reconcile(MinecraftServer server) {
        Backends.claim().allTeams(server).forEach(teamId -> NumismaticsTeamAccounts.mirror(server, teamId));
    }

    @Override
    public CurrencyAccount serverAccount(MinecraftServer server) {
        return account(SERVER_ACCOUNT_ID, "Server Account");
    }

    @Override
    @Nullable
    public Component focusServerAccount(ServerPlayer op) {
        return null;
    }

    @Override
    public Component format(long amount) {
        return formatSpurs(amount);
    }

    static Component formatSpurs(long amount) {
        return NumismaticsCoins.format(amount);
    }

    @Override
    public List<Denomination> denominations() {
        return NumismaticsCoins.highToLow().stream()
                .map(coin -> new Denomination(coin.label(), coin.value()))
                .toList();
    }

    private static CurrencyAccount account(UUID id, String label) {
        return new NumismaticsAccount(Numismatics.BANK.getOrCreateAccount(id, BankAccount.Type.PLAYER), label);
    }

    /** Numismatics balances are {@code int} spurs. */
    private static int clamp(long amount) {
        return (int) Math.max(0L, Math.min(amount, Integer.MAX_VALUE));
    }

    private record NumismaticsAccount(BankAccount account, String label) implements CurrencyAccount {
        @Override
        public long balanceCopper() {
            return account.getBalance();
        }

        @Override
        public boolean canAfford(long copper) {
            return copper <= 0 || account.getBalance() >= copper;
        }

        @Override
        public void withdraw(long copper, String reason) {
            if (copper > 0 && !account.deduct(clamp(copper), true)) {
                LCFtbHook.LOGGER.warn("Numismatics refused to deduct {} spurs ({}) from {}", copper, reason, label);
            }
        }

        @Override
        public void deposit(long copper, String reason) {
            if (copper > Integer.MAX_VALUE - (long) account.getBalance()) {
                LCFtbHook.LOGGER.warn("Numismatics deposit of {} spurs ({}) to {} would overflow - skipped", copper, reason, label);
                return;
            }
            if (copper > 0) {
                account.deposit(clamp(copper));
            }
        }

        @Override
        public String ownerDisplayName() {
            return label;
        }

        @Override
        public Component balanceText() {
            return formatSpurs(account.getBalance());
        }

        @Override
        public boolean isEmpty() {
            return account.getBalance() <= 0;
        }

        @Override
        public void transferAllTo(CurrencyAccount target, String reason) {
            long amount = account.getBalance();
            if (amount > 0) {
                withdraw(amount, reason);
                target.deposit(amount, reason);
            }
        }
    }
}
