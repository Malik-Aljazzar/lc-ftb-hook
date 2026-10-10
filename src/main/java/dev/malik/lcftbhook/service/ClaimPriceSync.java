package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.network.SyncClaimPricesPayload;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class ClaimPriceSync {
    private ClaimPriceSync() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPayload(player));
    }

    public static SyncClaimPricesPayload createPayload(ServerPlayer player) {
        boolean balanceSynced = false;
        boolean balanceEmpty = true;
        String balanceText = "";
        boolean privateBalanceEmpty = true;
        String privateBalanceText = "";
        boolean hasCountryBalance = false;
        boolean countryBalanceEmpty = true;
        String countryBalanceText = "";
        int claimedChunks = 0;
        boolean canManageClaims = true;

        MinecraftServer server = player.server;
        ClaimPort claims = Backends.claim();
        UUID teamId = claims.teamIdForPlayer(server, player.getUUID());
        if (teamId != null) {
            CurrencyPort currency = Backends.currency();
            canManageClaims = currency.canPlayerSpend(server, teamId, player.getUUID());
            currency.ensureTeamAccountExists(server, teamId);
            long balance = currency.teamAccount(server, teamId).balanceCopper();
            balanceSynced = true;
            balanceEmpty = balance <= 0;
            balanceText = balanceEmpty ? "" : currency.format(balance).getString();

            long privateBalance = currency.playerAccount(server, player.getUUID()).balanceCopper();
            privateBalanceEmpty = privateBalance <= 0;
            privateBalanceText = privateBalanceEmpty ? "" : currency.format(privateBalance).getString();

            // Only shown to officers/the owner of an actual party - a solo
            // player's own "team" IS their personal account (same UUID), so
            // there's nothing distinct to show as a "country" balance then.
            if (claims.isPartyTeam(server, teamId) && claims.rankOf(server, teamId, player.getUUID()).isOfficerOrBetter()) {
                long countryBalance = currency.teamAccount(server, teamId).balanceCopper();
                hasCountryBalance = true;
                countryBalanceEmpty = countryBalance <= 0;
                countryBalanceText = countryBalanceEmpty ? "" : currency.format(countryBalance).getString();
            }

            claimedChunks = claims.claimedChunkCount(server, teamId);
        }

        return new SyncClaimPricesPayload(
                ClaimPricingService.priceForClaim(claimedChunks),
                // forceLoadUpkeepPrice is raw tenths-of-a-copper (see
                // LCFtbHookConfig's [general] note) - converted to whole
                // copper here so the client always receives ready-to-display
                // amounts, same as ClaimPricingService.priceForClaim above.
                Math.floorDiv(LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get(), 10L),
                LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get(),
                UpkeepService.minutesUntilNextUpkeep(player.server),
                UpkeepService.secondsUntilNextUpkeep(player.server),
                LCFtbHookConfig.SERVER.freeChunks.get(),
                claimedChunks,
                balanceSynced,
                balanceEmpty,
                balanceText,
                privateBalanceEmpty,
                privateBalanceText,
                hasCountryBalance,
                countryBalanceEmpty,
                countryBalanceText,
                dev.malik.lcftbhook.data.ProtectionProperty.values(),
                WarService.isEnabled(),
                LCFtbHookConfig.SERVER.lockClaimVisibilityPublic.get(),
                canManageClaims
        );
    }
}
