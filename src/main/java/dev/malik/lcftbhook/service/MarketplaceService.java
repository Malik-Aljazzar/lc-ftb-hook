package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.util.Money;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.SyncChunkOwnershipPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chunk marketplace: officers/the owner can list individual chunks for sale
 * (state-owned "Sell as Country") or a private owner can resell their own
 * chunk ("Sell"); any eligible player can buy, or officers/the owner can
 * force-buy back ("Buy as Country"). A sold chunk stays part of the team's
 * territory - upkeep/region membership/dismantle order are unaffected - the
 * private owner can only ever loosen that one chunk's protection below the
 * owning region's baseline.
 */
public final class MarketplaceService {
    private static final int MAX_CHUNK_LABEL_LENGTH = 24;

    private MarketplaceService() {
    }

    public static void listForSale(ServerPlayer actor, List<String> chunkKeys, long pricePerChunk, boolean asCountry) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        if (pricePerChunk <= 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_invalid_price"), false);
            return;
        }
        MinecraftServer server = actor.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int listed = 0;
        int notEligible = 0;
        int sellingDisabled = 0;
        int freshlyClaimed = 0;
        int notFound = 0;

        for (String chunkKey : chunkKeys) {
            UUID teamId = ownerTeam(server, chunkKey);
            if (teamId == null) {
                notFound++;
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);

            boolean eligible = asCountry
                    ? ownership.isStateOwned() && Backends.claim().rankOf(server, teamId, actor.getUUID()).isOfficerOrBetter()
                    : !ownership.isStateOwned() && actor.getUUID().equals(ownership.privateOwner());
            if (!eligible) {
                LCFtbHook.LOGGER.info(
                        "marketplace listForSale denied (not eligible): chunk={} asCountry={} isStateOwned={} privateOwner={} actorRank={}",
                        chunkKey, asCountry, ownership.isStateOwned(), ownership.privateOwner(), Backends.claim().rankOf(server, teamId, actor.getUUID())
                );
                notEligible++;
                continue;
            }
            // A private owner reselling their own chunk (not "as country" - that's the
            // team choosing to sell off state land, unaffected by this setting) needs
            // the owning Region to still allow it - an officer/owner may have disabled
            // private reselling for that Region after the chunk was already sold.
            // Tracked separately from "not eligible" - the ownership check above already
            // passed, so telling the player it's "not theirs to sell" here would be
            // straight-up wrong, not just unhelpful.
            if (!asCountry) {
                Region region = RegionService.resolveRegion(server, teamId, chunkKey);
                if (!region.allowPrivateSelling()) {
                    sellingDisabled++;
                    continue;
                }
            }
            // A freshly claimed chunk (newly added to the team's territory,
            // not previously claimed) can't be listed until the team's next
            // successful upkeep settlement - prevents immediately flipping
            // land the moment it's claimed. A freshly BOUGHT chunk has no
            // such restriction - a private buyer must be able to resell
            // right away. Matches the client-side menu gating in
            // ChunkContextMenuBuilder/XaeroMarketplaceMenu.
            if (savedData.isChunkFreshlyClaimed(teamId, chunkKey)) {
                freshlyClaimed++;
                continue;
            }

            savedData.setChunkOwnership(teamId, chunkKey, ownership.withListingPrice(pricePerChunk));
            listed++;
        }

        if (listed > 0) {
            broadcastChunkOwnership(server);
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_listed", listed), false);
        }
        if (notEligible > 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_list_denied", notEligible), false);
        }
        if (sellingDisabled > 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_list_selling_disabled", sellingDisabled), false);
        }
        if (freshlyClaimed > 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_list_freshly_claimed", freshlyClaimed), false);
        }
        if (notFound > 0) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_not_found", notFound), false);
        }
    }

    public static void cancelListing(ServerPlayer actor, String chunkKey) {
        MinecraftServer server = actor.server;
        UUID teamId = ownerTeam(server, chunkKey);
        if (teamId == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
        if (!ownership.isListed()) {
            return;
        }
        boolean eligible = ownership.isStateOwned()
                ? Backends.claim().rankOf(server, teamId, actor.getUUID()).isOfficerOrBetter()
                : actor.getUUID().equals(ownership.privateOwner());
        if (!eligible) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_denied"), false);
            return;
        }
        savedData.setChunkOwnership(teamId, chunkKey, ownership.withListingPrice(null));
        broadcastChunkOwnership(server);
    }

    /**
     * Cancels every listing in {@code chunkKeys} the actor is actually
     * eligible to cancel (their own private listing, or - as officer/owner -
     * a state-owned "as country" listing), silently skipping the rest (not
     * listed, or not theirs to cancel) rather than rejecting the whole
     * selection over one ineligible chunk.
     */
    public static void cancelListings(ServerPlayer actor, List<String> chunkKeys) {
        MinecraftServer server = actor.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int cancelled = 0;
        for (String chunkKey : chunkKeys) {
            UUID teamId = ownerTeam(server, chunkKey);
            if (teamId == null) {
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
            if (!ownership.isListed()) {
                continue;
            }
            boolean eligible = ownership.isStateOwned()
                    ? Backends.claim().rankOf(server, teamId, actor.getUUID()).isOfficerOrBetter()
                    : actor.getUUID().equals(ownership.privateOwner());
            if (!eligible) {
                continue;
            }
            savedData.setChunkOwnership(teamId, chunkKey, ownership.withListingPrice(null));
            cancelled++;
        }
        if (cancelled > 0) {
            broadcastChunkOwnership(server);
        }
    }

    public static void buy(ServerPlayer buyer, List<String> chunkKeys, boolean asCountry) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        MinecraftServer server = buyer.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        // Hard error if any selected chunk isn't currently listed - buying an
        // unlisted chunk in a selection is a user-facing mistake, not a
        // silent partial purchase.
        record Entry(String key, UUID teamId, ChunkOwnership ownership) {
        }
        List<Entry> entries = new java.util.ArrayList<>();
        for (String chunkKey : chunkKeys) {
            UUID teamId = ownerTeam(server, chunkKey);
            if (teamId == null) {
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
            if (!ownership.isListed()) {
                buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_not_listed", chunkKey), false);
                return;
            }
            entries.add(new Entry(chunkKey, teamId, ownership));
        }
        if (entries.isEmpty()) {
            return;
        }

        if (asCountry) {
            for (Entry entry : entries) {
                if (!Backends.claim().rankOf(server, entry.teamId(), buyer.getUUID()).isOfficerOrBetter()) {
                    buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_denied"), false);
                    return;
                }
            }
        } else {
            for (Entry entry : entries) {
                if (buyer.getUUID().equals(entry.ownership().privateOwner())) {
                    buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_already_yours"), false);
                    return;
                }
                Region entryRegion = RegionService.resolveRegion(server, entry.teamId(), entry.key());
                String access = entryRegion.buyerAccess();
                var rank = Backends.claim().rankOf(server, entry.teamId(), buyer.getUUID());
                boolean allowed = switch (access) {
                    case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_TEAM -> rank.isMemberOrBetter();
                    case dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALLIES -> rank.isAllyOrBetter();
                    default -> true;
                };
                if (!allowed) {
                    buyer.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_members_only"), false);
                    return;
                }
            }
        }

        long totalCopper = 0L;
        for (Entry entry : entries) {
            totalCopper += entry.ownership().listingPricePerChunk();
        }

        if (asCountry) {
            // A multi-chunk "as country" selection can span several teams (the
            // actor must be an officer of each - checked above); every team
            // pays only for its own chunks so one team can never end up
            // subsidizing another team's buyback.
            Map<UUID, Long> costPerTeam = new HashMap<>();
            for (Entry entry : entries) {
                UUID teamId = entry.teamId();
                costPerTeam.merge(teamId, entry.ownership().listingPricePerChunk(), Long::sum);
            }
            Map<UUID, CurrencyAccount> accountsById = new HashMap<>();
            for (Map.Entry<UUID, Long> costEntry : costPerTeam.entrySet()) {
                CurrencyAccount account = teamAccountOrNull(server, costEntry.getKey());
                if (!canPay(buyer, account, costEntry.getValue())) {
                    return;
                }
                accountsById.put(costEntry.getKey(), account);
            }
            for (Map.Entry<UUID, CurrencyAccount> accountEntry : accountsById.entrySet()) {
                accountEntry.getValue().withdraw(costPerTeam.get(accountEntry.getKey()), "Chunk Purchase");
            }
        } else {
            CurrencyAccount payerAccount = personalAccountOrNull(server, buyer.getUUID());
            if (!canPay(buyer, payerAccount, totalCopper)) {
                return;
            }
            payerAccount.withdraw(totalCopper, "Chunk Purchase");
        }

        for (Entry entry : entries) {
            long price = entry.ownership().listingPricePerChunk();
            CurrencyAccount sellerAccount = entry.ownership().isStateOwned()
                    ? teamAccountOrNull(server, entry.teamId())
                    : personalAccountOrNull(server, entry.ownership().privateOwner());
            if (sellerAccount != null) {
                sellerAccount.deposit(price, "Chunk Sale");
            }

            ChunkOwnership updated = asCountry
                    ? ChunkOwnership.resetToStateOwned()
                    : new ChunkOwnership(buyer.getUUID(), null, Map.of(), Map.of(), null);
            savedData.setChunkOwnership(entry.teamId(), entry.key(), updated);
            // A change of ownership resets protection to minimum until the
            // team's next successful upkeep settlement - same as a fresh claim.
            savedData.markChunkUnsettled(entry.teamId(), entry.key());
        }

        broadcastChunkOwnership(server);
        broadcastUnsettledChunks(server);
        buyer.displayClientMessage(Component.translatable(
                "message.lc_ftb_hook.marketplace_bought", entries.size(), Money.formatValue(totalCopper)
        ), false);
    }

    /**
     * Single-chunk only (no batch). Refuses only if {@code actor} isn't the
     * chunk's private owner - the stored preference itself is never gated on
     * whether it's currently achievable against the owning region's value
     * (that's an enforcement/display concern, see
     * {@link ProtectionResolution#effectiveValue}, not a write-time one): an
     * owner must always be able to set their preference, even while the
     * region can't currently deliver it (e.g. dismantled for non-payment),
     * so it's already in place the moment the region's protection is
     * restored.
     */
    public static void setChunkOverride(ServerPlayer actor, String chunkKey, ProtectionProperty property, String value) {
        MinecraftServer server = actor.server;
        UUID teamId = ownerTeam(server, chunkKey);
        if (teamId == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
        if (ownership.isStateOwned() || !actor.getUUID().equals(ownership.privateOwner())) {
            return;
        }

        savedData.setChunkOwnership(teamId, chunkKey, ownership.withOverrideProperty(property.id(), value));
        broadcastChunkOwnership(server);
    }

    /**
     * A private owner's custom whitelist/blacklist for one of the 3 privacy-mode
     * properties on their own chunk (see {@link PlayerAccessList}) - always
     * settable regardless of whether the owning Region's baseline currently
     * supports protection for that property. It's simply not CONSULTED while
     * it doesn't (see {@code ChunkTeamDataRegionPrivacyMixin}'s early return
     * when the region is at minimum) - this lets an owner pre-configure a
     * list now so it's already in place the moment an officer raises the
     * region's protection later, instead of forcing them to remember to set
     * it up again afterward.
     */
    public static void setAccessList(ServerPlayer actor, String chunkKey, ProtectionProperty property, boolean whitelist, Set<UUID> players) {
        if (!property.isPrivacyMode()) {
            return;
        }
        MinecraftServer server = actor.server;
        UUID teamId = ownerTeam(server, chunkKey);
        if (teamId == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
        if (ownership.isStateOwned() || !actor.getUUID().equals(ownership.privateOwner())) {
            return;
        }

        savedData.setChunkOwnership(teamId, chunkKey, ownership.withAccessList(property.id(), new PlayerAccessList(whitelist, players)));
        broadcastChunkOwnership(server);
    }

    /**
     * A private owner's custom label for their own chunk, shown next to the
     * region name in the hover info panel (see {@code ProtectionInfoLines}).
     * Purely cosmetic - blank clears it.
     */
    public static void setLabel(ServerPlayer actor, String chunkKey, String label) {
        MinecraftServer server = actor.server;
        UUID teamId = ownerTeam(server, chunkKey);
        if (teamId == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
        if (ownership.isStateOwned() || !actor.getUUID().equals(ownership.privateOwner())) {
            return;
        }

        String trimmed = label.trim();
        if (trimmed.length() > MAX_CHUNK_LABEL_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CHUNK_LABEL_LENGTH);
        }
        savedData.setChunkOwnership(teamId, chunkKey, ownership.withLabel(trimmed));
        broadcastChunkOwnership(server);
    }

    /**
     * A team officer/owner force-buys back one or more privately-owned
     * chunks for a set compensation, paid per chunk to each chunk's own
     * private owner. State-owned chunks in the selection are simply ignored
     * (not an error) - only the actor's own team's chunks are eligible.
     */
    public static void expropriate(ServerPlayer actor, List<String> chunkKeys, long compensationPerChunk) {
        if (chunkKeys.isEmpty() || compensationPerChunk < 0) {
            return;
        }
        MinecraftServer server = actor.server;
        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        record Entry(String key, ChunkOwnership ownership) {
        }
        UUID actorTeam = null;
        List<Entry> toExpropriate = new java.util.ArrayList<>();
        for (String chunkKey : chunkKeys) {
            UUID teamId = ownerTeam(server, chunkKey);
            if (teamId == null || !Backends.claim().rankOf(server, teamId, actor.getUUID()).isOfficerOrBetter()) {
                continue;
            }
            if (actorTeam == null) {
                actorTeam = teamId;
            } else if (!actorTeam.equals(teamId)) {
                continue;
            }
            ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
            if (ownership.isStateOwned()) {
                continue;
            }
            toExpropriate.add(new Entry(chunkKey, ownership));
        }
        if (toExpropriate.isEmpty() || actorTeam == null) {
            actor.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_nothing_to_expropriate"), false);
            return;
        }

        long totalCost = compensationPerChunk * toExpropriate.size();
        CurrencyAccount teamAccount = teamAccountOrNull(server, actorTeam);
        if (totalCost > 0 && !canPay(actor, teamAccount, totalCost)) {
            return;
        }
        if (totalCost > 0) {
            teamAccount.withdraw(totalCost, "Chunk Expropriation");
        }

        Map<UUID, Integer> expropriatedCountByOwner = new HashMap<>();
        for (Entry entry : toExpropriate) {
            UUID ownerId = entry.ownership().privateOwner();
            if (compensationPerChunk > 0) {
                CurrencyAccount ownerAccount = personalAccountOrNull(server, ownerId);
                if (ownerAccount != null) {
                    ownerAccount.deposit(compensationPerChunk, "Chunk Expropriation");
                }
            }
            expropriatedCountByOwner.merge(ownerId, 1, Integer::sum);
            savedData.setChunkOwnership(actorTeam, entry.key(), ChunkOwnership.resetToStateOwned());
        }

        // Only the actor (whoever ran the expropriation) got a summary
        // before - each affected private owner had no idea their chunk(s)
        // were just taken back, if they happened to be online to see it.
        UUID notifiedActorTeam = actorTeam;
        for (Map.Entry<UUID, Integer> ownerEntry : expropriatedCountByOwner.entrySet()) {
            ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerEntry.getKey());
            if (ownerPlayer == null) {
                continue;
            }
            ownerPlayer.displayClientMessage(Component.translatable(
                    "message.lc_ftb_hook.marketplace_expropriated_notice",
                    ownerEntry.getValue(),
                    WarService.displayName(server, notifiedActorTeam),
                    Money.formatValue(compensationPerChunk * ownerEntry.getValue())
            ), false);
        }

        broadcastChunkOwnership(server);
        actor.displayClientMessage(Component.translatable(
                "message.lc_ftb_hook.marketplace_expropriated", toExpropriate.size(), Money.formatValue(totalCost)
        ), false);
    }

    @Nullable
    private static CurrencyAccount personalAccountOrNull(MinecraftServer server, UUID playerId) {
        try {
            return Backends.currency().playerAccount(server, playerId);
        } catch (IllegalStateException missing) {
            return null;
        }
    }

    @Nullable
    private static CurrencyAccount teamAccountOrNull(MinecraftServer server, UUID teamId) {
        try {
            return Backends.currency().teamAccount(server, teamId);
        } catch (IllegalStateException missing) {
            return null;
        }
    }

    /** Tells {@code payer} what's missing when {@code account} can't cover {@code cost}. */
    private static boolean canPay(ServerPlayer payer, @Nullable CurrencyAccount account, long cost) {
        if (account != null && account.canAfford(cost)) {
            return true;
        }
        payer.displayClientMessage(Component.translatable(
                "message.lc_ftb_hook.insufficient_funds",
                Money.formatValue(cost),
                account != null ? Money.formatBalance(account) : Money.formatValue(0L)
        ), false);
        return false;
    }

    @Nullable
    private static UUID ownerTeam(MinecraftServer server, String chunkKey) {
        ClaimPort.ChunkKey chunk = ChunkPosKey.toChunkKey(chunkKey);
        return Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z);
    }

    public static SyncChunkOwnershipPayload createOwnershipPayload(MinecraftServer server) {
        return new SyncChunkOwnershipPayload(FtbHookSavedData.get(server).getAllChunkOwnership());
    }

    public static void broadcastChunkOwnership(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createOwnershipPayload(server));
    }

    /**
     * Called wherever a chunk's freshly-claimed state changes (fresh claim, or
     * upkeep settlement clearing it) - drives the client's "not sellable yet"
     * marketplace gating exclusively. Deliberately sources from
     * {@code getAllFreshlyClaimedChunks}, not {@code getAllUnsettledChunks} -
     * a freshly-bought chunk is also "unsettled" (protection reset), but must
     * stay immediately resellable, so it's excluded here.
     */
    public static void broadcastUnsettledChunks(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new dev.malik.lcftbhook.network.SyncUnsettledChunksPayload(FtbHookSavedData.get(server).getAllFreshlyClaimedChunks()));
    }

    public static void syncOwnershipToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createOwnershipPayload(player.server));
    }

    public static void syncUnsettledChunksToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new dev.malik.lcftbhook.network.SyncUnsettledChunksPayload(
                FtbHookSavedData.get(player.server).getAllFreshlyClaimedChunks()));
    }
}
