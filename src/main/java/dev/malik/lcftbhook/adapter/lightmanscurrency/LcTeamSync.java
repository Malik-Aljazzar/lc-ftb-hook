package dev.malik.lcftbhook.adapter.lightmanscurrency;

import dev.malik.lcftbhook.service.PartyOwnerChoice;
import dev.malik.lcftbhook.service.SharedAccountLinks;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.claim.ClaimRank;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors a claim-backend party (FTB Teams party or OPAC party - read only
 * through {@link ClaimPort}, never a claim mod's own types) into a Lightman's
 * Currency team, so the party gets a real LC team bank account and LC's own
 * bank-access level applies. Roles: party owner -> LC owner, officer-or-better
 * -> LC admin, other members -> LC member. Keyed by the claim backend's team
 * UUID in {@link FtbHookSavedData}'s link entry.
 */
public final class LcTeamSync {
    private static final ConcurrentHashMap<UUID, Object> LINK_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> UNLINKED_REASONS = new ConcurrentHashMap<>();
    private static final Set<Long> SHARED_REPORTED = ConcurrentHashMap.newKeySet();

    private LcTeamSync() {
    }

    public static void ensureLinked(MinecraftServer server, UUID teamId) {
        ClaimPort claims = Backends.claim();
        if (!claims.isPartyTeam(server, teamId)) {
            return; // solo teams pay from the player's own account
        }
        if (!claims.isActiveTeam(server, teamId)) {
            // Not logged: right after a party is founded, its owner is switched over a moment later.
            // The reason still ends up in the "Missing LC team bank account" error if an account is needed.
            UNLINKED_REASONS.put(teamId, "the party is not in use by any of its members");
            return;
        }
        if (LcTeamAccess.cache() == null) {
            UNLINKED_REASONS.put(teamId, "Lightman's Currency's team data isn't loaded yet"); // transient - not logged
            return;
        }
        UUID ownerId = claims.ownerOf(server, teamId);
        if (PartyOwnerChoice.isMissing(ownerId)) {
            // Never hand the nil id on as an LC team owner - that locked the real owner out (4.0.0 damage).
            skipped(teamId, "the party has no owner (and no single member with the owner rank) - transfer its ownership");
            return;
        }

        synchronized (LINK_LOCKS.computeIfAbsent(teamId, id -> new Object())) {
            FtbHookSavedData data = FtbHookSavedData.get(server);
            FtbHookSavedData.TeamLinkEntry entry = data.getOrCreateLink(teamId);
            resolveSharedLink(server, data, entry.lcTeamId());
            entry = data.get(teamId);
            Team lcTeam = resolveLcTeam(entry);
            if (lcTeam == null) {
                // A lost link must not cost the party its bank balance: take its old LC team back first.
                lcTeam = LcOrphanTeams.find(LcTeamAccess.cache(), data.getLinkedLcTeamIds(), ownerId, lcName(server, teamId));
                if (lcTeam != null) {
                    data.setLcTeamId(teamId, lcTeam.getID());
                    LCFtbHook.LOGGER.warn("Re-linked existing LC team {} to party {} (its link was missing)", lcTeam.getID(), teamId);
                }
            }
            if (lcTeam == null) {
                lcTeam = LcTeamAccess.registerTeam(playerRef(server, ownerId), lcName(server, teamId));
                data.setLcTeamId(teamId, lcTeam.getID());
                LCFtbHook.LOGGER.info("Created LC team {} for party {}", lcTeam.getID(), teamId);
            }
            removeUnlinkedDuplicateTeams(server, data, teamId, ownerId, lcTeam.getID());
            syncTeamState(server, teamId, ownerId, lcTeam, data.get(teamId));
            UNLINKED_REASONS.remove(teamId);
        }
    }

    /** Why a party currently has no LC team, if this mod knows - for error messages. */
    @Nullable
    public static String whyUnlinked(UUID teamId) {
        return UNLINKED_REASONS.get(teamId);
    }

    /** Logged once per party and reason - a missing team account must never go unexplained. */
    private static void skipped(UUID teamId, String reason) {
        if (!reason.equals(UNLINKED_REASONS.put(teamId, reason))) {
            LCFtbHook.LOGGER.warn("No Lightman's Currency team account for party {}: {}", teamId, reason);
        }
    }

    /**
     * If this LC team is linked to several parties, keeps it with the one it
     * demonstrably belongs to and clears the others' links (see {@link SharedAccountLinks}).
     */
    private static void resolveSharedLink(MinecraftServer server, FtbHookSavedData data, long lcTeamId) {
        if (!FtbHookSavedData.isLcTeamId(lcTeamId)) {
            return;
        }
        List<UUID> parties = data.partiesLinkedTo(lcTeamId);
        if (parties.size() < 2) {
            return;
        }
        Team lcTeam = TeamAPI.getApi().GetTeam(false, lcTeamId) instanceof Team team ? team : null;
        ClaimPort claims = Backends.claim();
        List<SharedAccountLinks.Claimant> claimants = parties.stream().map(partyId -> new SharedAccountLinks.Claimant(
                partyId,
                claims.isPartyTeam(server, partyId) && claims.isActiveTeam(server, partyId),
                lcTeam != null && lcTeam.getOwner() != null && java.util.Objects.equals(claims.ownerOf(server, partyId), lcTeam.getOwner().id),
                lcTeam != null && lcName(server, partyId).equals(lcTeam.getName()))).toList();
        Set<UUID> unlink = SharedAccountLinks.toUnlink(claimants);
        if (unlink.isEmpty()) {
            if (SHARED_REPORTED.add(lcTeamId)) {
                LCFtbHook.LOGGER.error("LC team {} is linked to parties {} and it can't be told whose it is - left unchanged,"
                        + " an admin has to decide", lcTeamId, parties);
            }
            return;
        }
        for (UUID partyId : unlink) {
            if (data.clearLcTeamLink(partyId)) {
                LCFtbHook.LOGGER.warn("LC team {} was linked to several parties {} - kept it with its own party, cleared the link of {}",
                        lcTeamId, parties, partyId);
            }
        }
    }

    /** Removes the mirrored LC team (money must already have been settled by the caller). */
    public static void onTeamDeleted(MinecraftServer server, UUID teamId) {
        TeamDataCache cache = LcTeamAccess.cache();
        FtbHookSavedData.TeamLinkEntry entry = FtbHookSavedData.get(server).get(teamId);
        if (cache == null || entry == null || !FtbHookSavedData.isLcTeamId(entry.lcTeamId())) {
            return;
        }
        ITeam lcTeam = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
        if (lcTeam != null && LcOrphanTeams.hasMoney(lcTeam)) {
            // Settlement normally pays the balance out first; if it didn't, the money stays put.
            LCFtbHook.LOGGER.warn("Kept LC team {} of deleted party {}: it still holds money", entry.lcTeamId(), teamId);
            return;
        }
        LcTeamDeletionGuard.runAllowed(() -> cache.removeTeam(entry.lcTeamId()));
        LCFtbHook.LOGGER.info("Removed LC team {} for deleted party {}", entry.lcTeamId(), teamId);
    }

    /**
     * The team's LC account WITHOUT (re)linking - still resolvable after the
     * party itself is gone, which is exactly when disband settlement needs it.
     * Falls back to a pre-LC-teams legacy account if one is still stored.
     */
    @Nullable
    public static IBankAccount getLinkedBankAccount(MinecraftServer server, UUID teamId) {
        FtbHookSavedData.TeamLinkEntry entry = FtbHookSavedData.get(server).get(teamId);
        if (entry == null) {
            return null;
        }
        ITeam lcTeam = getLcTeam(server, teamId);
        if (lcTeam != null && lcTeam.hasBankAccount()) {
            return lcTeam.getBankAccount();
        }
        return legacyAccount(server, teamId, entry.legacyAccount());
    }

    @Nullable
    public static IBankAccount getBankAccount(MinecraftServer server, UUID teamId) {
        ensureLinked(server, teamId);
        ITeam lcTeam = getLcTeam(server, teamId);
        return lcTeam == null ? null : lcTeam.getBankAccount();
    }

    @Nullable
    public static ITeam getLcTeam(MinecraftServer server, UUID teamId) {
        FtbHookSavedData.TeamLinkEntry entry = FtbHookSavedData.get(server).get(teamId);
        return entry == null || !FtbHookSavedData.isLcTeamId(entry.lcTeamId()) ? null : TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
    }

    @Nullable
    private static Team resolveLcTeam(FtbHookSavedData.TeamLinkEntry entry) {
        if (FtbHookSavedData.isLcTeamId(entry.lcTeamId()) && TeamAPI.getApi().GetTeam(false, entry.lcTeamId()) instanceof Team team) {
            return team;
        }
        return null;
    }

    private static void removeUnlinkedDuplicateTeams(MinecraftServer server, FtbHookSavedData data, UUID teamId, UUID ownerId, long linkedId) {
        TeamDataCache cache = LcTeamAccess.cache();
        if (cache == null) {
            return;
        }
        String expectedName = lcName(server, teamId);
        Set<Long> linkedIds = data.getLinkedLcTeamIds();
        for (ITeam candidate : cache.getAllTeams()) {
            long candidateId = candidate.getID();
            if (candidateId == linkedId || linkedIds.contains(candidateId)
                    || !ownerId.equals(candidate.getOwner().id) || !expectedName.equals(candidate.getName())) {
                continue;
            }
            if (LcOrphanTeams.hasMoney(candidate)) {
                LCFtbHook.LOGGER.warn("Kept unlinked LC team {} for party {}: it still holds money", candidateId, teamId);
                continue;
            }
            LcTeamDeletionGuard.runAllowed(() -> cache.removeTeam(candidateId));
            LCFtbHook.LOGGER.info("Removed duplicate LC team {} for party {} (linked team is {})", candidateId, teamId, linkedId);
        }
    }

    private static void syncTeamState(MinecraftServer server, UUID teamId, UUID ownerId, Team lcTeam, FtbHookSavedData.TeamLinkEntry entry) {
        PlayerReference ownerRef = playerRef(server, ownerId);
        if (!ownerRef.is(lcTeam.getOwner())) {
            LcTeamAccess.setOwner(lcTeam, ownerRef);
        }
        String name = lcName(server, teamId);
        if (!name.equals(lcTeam.getName())) {
            LcTeamAccess.setName(lcTeam, name);
        }
        syncMembers(server, teamId, ownerId, lcTeam);
        ensureBankAccount(server, teamId, ownerId, lcTeam);
        migrateLegacyBalance(server, entry, lcTeam);
        lcTeam.markDirty();
    }

    private static void syncMembers(MinecraftServer server, UUID teamId, UUID ownerId, Team lcTeam) {
        Set<UUID> targetAdmins = new HashSet<>();
        Set<UUID> targetMembers = new HashSet<>();
        for (UUID playerId : Backends.claim().members(server, teamId)) {
            if (playerId.equals(ownerId)) {
                continue;
            }
            ClaimRank rank = Backends.claim().rankOf(server, teamId, playerId);
            if (rank.isOfficerOrBetter()) {
                targetAdmins.add(playerId);
            } else if (rank.isMemberOrBetter()) {
                targetMembers.add(playerId);
            }
        }
        applyRoles(server, lcTeam, targetAdmins, targetMembers);
        refreshStoredNames(server, lcTeam);
    }

    private static void applyRoles(MinecraftServer server, Team lcTeam, Set<UUID> targetAdmins, Set<UUID> targetMembers) {
        List<PlayerReference> admins = LcTeamAccess.admins(lcTeam);
        List<PlayerReference> members = LcTeamAccess.members(lcTeam);
        List.copyOf(admins).stream().filter(ref -> !targetAdmins.contains(ref.id)).forEach(ref -> PlayerReference.removeFromList(admins, ref));
        List.copyOf(members).stream().filter(ref -> !targetMembers.contains(ref.id)).forEach(ref -> PlayerReference.removeFromList(members, ref));
        for (UUID adminId : targetAdmins) {
            PlayerReference ref = playerRef(server, adminId);
            PlayerReference.removeFromList(members, ref);
            PlayerReference.addToList(admins, ref);
        }
        for (UUID memberId : targetMembers) {
            PlayerReference ref = playerRef(server, memberId);
            PlayerReference.removeFromList(admins, ref);
            PlayerReference.addToList(members, ref);
        }
    }

    private static void refreshStoredNames(MinecraftServer server, Team lcTeam) {
        LcTeamAccess.setOwner(lcTeam, LcTeamAccess.ensureNamed(lcTeam.getOwner(), resolvePlayerName(server, lcTeam.getOwner().id)));
        for (List<PlayerReference> list : List.of(LcTeamAccess.admins(lcTeam), LcTeamAccess.members(lcTeam))) {
            for (int i = 0; i < list.size(); i++) {
                PlayerReference ref = list.get(i);
                LcTeamAccess.replacePlayerReference(list, i, LcTeamAccess.ensureNamed(ref, resolvePlayerName(server, ref.id)));
            }
        }
    }

    private static void ensureBankAccount(MinecraftServer server, UUID teamId, UUID ownerId, Team lcTeam) {
        if (lcTeam.hasBankAccount()) {
            return;
        }
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(ownerId);
        if (onlinePlayer == null) {
            onlinePlayer = Backends.claim().onlineMembers(server, teamId).stream().findFirst().orElse(null);
        }
        if (onlinePlayer != null) {
            lcTeam.createBankAccount(onlinePlayer);
        } else {
            LcTeamAccess.createBankAccount(lcTeam);
        }
    }

    private static void migrateLegacyBalance(MinecraftServer server, FtbHookSavedData.TeamLinkEntry entry, Team lcTeam) {
        IBankAccount legacy = legacyAccount(server, entry.ftbTeamId(), entry.legacyAccount());
        IBankAccount target = lcTeam.hasBankAccount() ? lcTeam.getBankAccount() : null;
        if (legacy == null || target == null) {
            return;
        }
        boolean migrated = false;
        for (MoneyValue value : legacy.getMoneyStorage().allValues()) {
            if (!value.isEmpty()) {
                target.depositMoney(value);
                LcTransactionLog.logDeposit(target, "Legacy Balance Migration", value);
                migrated = true;
            }
        }
        if (migrated) {
            FtbHookSavedData.get(server).clearLegacyAccount(entry.ftbTeamId());
            LCFtbHook.LOGGER.info("Migrated legacy hook balance to LC team {} for party {}", lcTeam.getID(), entry.ftbTeamId());
        }
    }

    /**
     * A pre-LC-teams account stored as raw NBT in the saved data (the core
     * data class doesn't know Lightman's Currency). Changes to the returned
     * account are written straight back.
     */
    @Nullable
    private static IBankAccount legacyAccount(MinecraftServer server, UUID teamId, @Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        BankAccount[] holder = new BankAccount[1];
        holder[0] = new BankAccount(
                () -> FtbHookSavedData.get(server).updateLegacyAccount(teamId, holder[0].save(server.registryAccess())),
                tag, server.registryAccess());
        return holder[0];
    }

    private static PlayerReference playerRef(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null ? PlayerReference.of(player) : PlayerReference.of(playerId, resolvePlayerName(server, playerId));
    }

    private static String resolvePlayerName(@Nullable MinecraftServer server, UUID playerId) {
        String name = PlayerReference.getPlayerName(playerId);
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (server != null && server.getProfileCache() != null) {
            return server.getProfileCache().get(playerId)
                    .map(profile -> profile.getName())
                    .filter(profileName -> profileName != null && !profileName.isBlank())
                    .orElse(playerId.toString());
        }
        return playerId.toString();
    }

    /** Plain display name (no FTB "#id" disambiguator), truncated to LC's team-name limit. */
    private static String lcName(MinecraftServer server, UUID teamId) {
        String name = Backends.claim().teamDisplayName(server, teamId);
        int maxLength = Team.MAX_NAME_LENGTH;
        return name.length() <= maxLength ? name : name.substring(0, maxLength);
    }
}
