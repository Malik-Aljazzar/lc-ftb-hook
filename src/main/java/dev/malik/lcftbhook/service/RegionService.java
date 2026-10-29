package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.network.SyncPublicRegionsPayload;
import dev.malik.lcftbhook.network.SyncRegionMembershipPayload;
import dev.malik.lcftbhook.network.SyncRegionsPayload;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side handling of Regions: named, user-orderable subdivisions of a
 * team's claimed chunks that replace the old flat land/build chunk split.
 * Region creation/deletion/rename/reorder are immediate; chunk-to-region
 * assignment and protection property edits are queued (like the old chunk
 * type toggles) since both affect upkeep billing.
 */
public final class RegionService {
    private RegionService() {
    }

    public static Region createRegion(MinecraftServer server, UUID teamId, String name) {
        Region region = Region.create(name);
        // New regions insert at regionOrder index 0 (dismantled first) until
        // dragged elsewhere - RegionListScreen's reversed display puts that
        // at the visual BOTTOM (least-prioritized), which is the right
        // default for an unconfigured region.
        FtbHookSavedData.get(server).addRegion(teamId, region, true);
        syncRegionsToTeam(server, teamId);
        return region;
    }

    /** Creates a new region and queues the given selection into it in one action, e.g. from the marketplace/map context menu's "New Region from Selection...". */
    public static void createRegionAndAssign(ServerPlayer player, String name, List<String> chunkKeys) {
        if (name == null || name.isBlank()) {
            return;
        }
        UUID teamId = TeamCatalog.managedTeamOf(player.server, player.getUUID());
        if (teamId == null) {
            return;
        }
        Region region = createRegion(player.server, teamId, name.trim());
        handleAssignmentRequest(player, chunkKeys, region.id());
    }

    public static boolean deleteRegion(MinecraftServer server, UUID teamId, UUID regionId) {
        boolean removed = FtbHookSavedData.get(server).removeRegion(teamId, regionId);
        if (removed) {
            syncRegionsToTeam(server, teamId);
            broadcastRegionMembership(server);
            PendingStateSync.syncTeam(server, teamId);
        }
        return removed;
    }

    public static boolean renameRegion(MinecraftServer server, UUID teamId, UUID regionId, String newName) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null || region.isDefault() || newName == null || newName.isBlank()) {
            return false;
        }
        savedData.updateRegion(teamId, region.withName(newName.trim()));
        syncRegionsToTeam(server, teamId);
        return true;
    }

    public static boolean setAllowPrivateSelling(MinecraftServer server, UUID teamId, UUID regionId, boolean allow) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            return false;
        }
        savedData.updateRegion(teamId, region.withAllowPrivateSelling(allow));
        if (!allow && cancelPrivateListingsInRegion(savedData, teamId, regionId)) {
            MarketplaceService.broadcastChunkOwnership(server);
        }
        syncRegionsToTeam(server, teamId);
        return true;
    }

    /**
     * Cancels every currently-listed PRIVATE (non-country) listing in this
     * region, immediately - state/"country" listings are entirely
     * unaffected, this setting is only ever about privately-owned chunks
     * reselling themselves. Previously this only happened indirectly, and
     * only for a chunk whose region assignment happened to get re-applied
     * later (see {@link #reclampOverrideToRegion}) - disabling the toggle
     * left already-listed private chunks fully live and buyable until
     * something else coincidentally triggered that path.
     */
    private static boolean cancelPrivateListingsInRegion(FtbHookSavedData savedData, UUID teamId, UUID regionId) {
        boolean changed = false;
        for (var entry : savedData.getChunkOwnershipForTeam(teamId).entrySet()) {
            String chunkKey = entry.getKey();
            ChunkOwnership ownership = entry.getValue();
            if (ownership.isStateOwned() || !ownership.isListed()) {
                continue;
            }
            if (!savedData.getChunkRegion(teamId, chunkKey).equals(regionId)) {
                continue;
            }
            savedData.setChunkOwnership(teamId, chunkKey, ownership.withListingPrice(null));
            changed = true;
        }
        return changed;
    }

    /**
     * Who may buy a marketplace listing within this region. Re-syncing (both
     * the team-private and the globally-broadcast public region payloads,
     * both already sent by {@link #syncRegionsToTeam}) is what makes this
     * apply to already-listed chunks immediately - buyability/highlighting
     * are computed live from this same data on every render, not cached.
     */
    public static boolean setBuyerAccess(MinecraftServer server, UUID teamId, UUID regionId, String access) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            return false;
        }
        savedData.updateRegion(teamId, region.withBuyerAccess(access));
        syncRegionsToTeam(server, teamId);
        return true;
    }

    public static boolean reorderRegions(MinecraftServer server, UUID teamId, List<UUID> newOrder) {
        boolean applied = FtbHookSavedData.get(server).setRegionOrder(teamId, newOrder);
        if (applied) {
            syncRegionsToTeam(server, teamId);
        }
        return applied;
    }

    public static Region resolveRegion(MinecraftServer server, UUID teamId, String chunkKey) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (savedData.isChunkUnsettled(teamId, chunkKey)) {
            // Newly claimed/bought chunks have no protection at all until the
            // team's next successful upkeep settlement - an empty-properties
            // region is exactly "every property at its minimum" per Region's
            // own sparse-map convention (absent = minimum).
            return Region.createDefault();
        }
        UUID regionId = savedData.getChunkRegion(teamId, chunkKey);
        Region region = savedData.getRegion(teamId, regionId);
        return region != null ? region : Region.createDefault();
    }

    /** The region protecting this chunk right now (Default-at-minimum if unclaimed or unsettled). */
    public static Region resolveRegion(MinecraftServer server, ClaimPort.ChunkKey chunk) {
        UUID teamId = Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z);
        return teamId == null ? Region.createDefault() : resolveRegion(server, teamId, ChunkPosKey.encode(chunk));
    }

    /**
     * A chunk's private-owner protection override (loosens - never tightens
     * - the region's own value, see {@link dev.malik.lcftbhook.service.ProtectionResolution}).
     */
    public static ChunkOwnership resolveOwnership(MinecraftServer server, ClaimPort.ChunkKey chunk) {
        UUID teamId = Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z);
        return teamId == null ? ChunkOwnership.EMPTY : FtbHookSavedData.get(server).getChunkOwnership(teamId, ChunkPosKey.encode(chunk));
    }

    public static void handleAssignmentRequest(ServerPlayer player, List<String> chunkKeys, UUID targetRegionId) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        MinecraftServer server = player.server;
        UUID teamId = TeamCatalog.managedTeamOf(server, player.getUUID());
        if (teamId == null) {
            player.displayClientMessage(Component.translatable("message.lc_ftb_hook.region_assign_denied"), false);
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        if (savedData.getRegion(teamId, targetRegionId) == null) {
            return;
        }
        // No isProtectionLocked gate here (unlike before) - a region
        // assignment is ALREADY always queued, never applied immediately
        // (see applyPendingRegionAssignments, only committed at the next
        // upkeep settlement), so there's no "already broke, don't let it get
        // worse" case to guard against - the same "always settable, pending
        // until it can actually apply" principle as setRegionProperty.

        TeamPendingState pendingState = savedData.getPendingState(teamId);
        int queued = 0;
        for (String rawKey : chunkKeys) {
            ClaimPort.ChunkKey chunk = ChunkPosKey.toChunkKey(rawKey);
            if (!teamId.equals(Backends.claim().ownerTeamOf(server, chunk.dimension(), chunk.pos().x, chunk.pos().z))) {
                continue;
            }
            String key = ChunkPosKey.encode(chunk);
            UUID currentRegion = savedData.getChunkRegion(teamId, key);
            if (currentRegion.equals(targetRegionId) && !pendingState.isPendingRegionAssignment(key)) {
                continue;
            }
            pendingState = pendingState.withPendingRegionAssignment(key, targetRegionId);
            queued++;
        }

        if (queued > 0) {
            savedData.setPendingState(teamId, pendingState);
            PendingStateSync.syncTeam(server, teamId);
            player.displayClientMessage(
                    Component.translatable("message.lc_ftb_hook.region_assign_queued", queued), false
            );
        }
    }

    public static TeamPendingState applyPendingRegionAssignments(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState updated = pendingState;
        boolean changed = false;

        boolean ownershipChanged = false;
        for (var entry : new java.util.HashMap<>(pendingState.pendingRegionAssignments()).entrySet()) {
            String chunkKey = entry.getKey();
            UUID targetRegionId = entry.getValue();
            savedData.setChunkRegion(teamId, chunkKey, targetRegionId);
            ownershipChanged |= reclampOverrideToRegion(savedData, teamId, chunkKey, targetRegionId);
            updated = updated.withoutPendingRegionAssignment(chunkKey);
            changed = true;
        }

        if (changed) {
            broadcastRegionMembership(server);
        }
        if (ownershipChanged) {
            dev.malik.lcftbhook.service.MarketplaceService.broadcastChunkOwnership(server);
        }
        return updated;
    }

    /**
     * Keeps a privately-owned chunk's marketplace state consistent with
     * whichever Region it now belongs to, right as a reassignment takes
     * effect:
     * <ul>
     *   <li>Its protection override may only ever be as loose or looser than
     *   the owning Region's own value (never stricter - enforced at write
     *   time in {@code MarketplaceService#setChunkOverride}); any entry that
     *   the new Region's baseline made too strict gets clamped down to that
     *   baseline.</li>
     *   <li>If the chunk is currently listed for private resale and the new
     *   Region has {@code allowPrivateSelling} disabled, the listing is
     *   cancelled outright - the chunk stays privately owned, it just can no
     *   longer be actively for sale there.</li>
     * </ul>
     */
    private static boolean reclampOverrideToRegion(FtbHookSavedData savedData, UUID teamId, String chunkKey, UUID targetRegionId) {
        ChunkOwnership ownership = savedData.getChunkOwnership(teamId, chunkKey);
        if (ownership.isStateOwned()) {
            return false;
        }
        Region newRegion = savedData.getRegion(teamId, targetRegionId);
        if (newRegion == null) {
            return false;
        }
        boolean changed = false;
        Map<String, String> clamped = ownership.protectionOverride();
        if (!clamped.isEmpty()) {
            var working = new java.util.HashMap<>(clamped);
            for (var overrideEntry : ownership.protectionOverride().entrySet()) {
                ProtectionProperty property;
                try {
                    property = ProtectionProperty.byId(overrideEntry.getKey());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String regionValue = newRegion.propertyValue(property);
                if (!ProtectionResolution.isLooserOrEqual(property, overrideEntry.getValue(), regionValue)) {
                    working.put(overrideEntry.getKey(), regionValue);
                    changed = true;
                }
            }
            if (changed) {
                clamped = Map.copyOf(working);
            }
        }

        Long listingPrice = ownership.listingPricePerChunk();
        if (ownership.isListed() && !newRegion.allowPrivateSelling()) {
            listingPrice = null;
            changed = true;
        }

        if (changed) {
            savedData.setChunkOwnership(teamId, chunkKey, new ChunkOwnership(
                    ownership.privateOwner(), listingPrice, clamped, ownership.accessLists(), ownership.label()
            ));
        }
        return changed;
    }

    /**
     * Applies a protection-property edit made from the Region settings
     * screen. Cost-neutral changes (vs. the currently billed price) apply
     * immediately; anything else is queued for the next upkeep settlement,
     * mirroring the old {@code TeamPropertyHandler} dance but without any
     * FTB property system to fight — the region's stored value already is
     * the live/enforced one.
     */
    public static void setRegionProperty(
            ServerPlayer player,
            UUID regionId,
            ProtectionProperty property,
            String newValue
    ) {
        MinecraftServer server = player.server;
        UUID teamId = TeamCatalog.managedTeamOf(server, player.getUUID());
        if (teamId == null) {
            return;
        }
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            return;
        }
        // No isProtectionLocked gate here (unlike before) - a change is
        // always settable, even while the team is already frozen for
        // non-payment: a cost-neutral edit still applies immediately below
        // (harmless - it doesn't add to what's already owed), and any
        // cost-increasing edit already queues as pending rather than
        // applying (see the oldPrice != newPrice branch further down) -
        // exactly the "always settable, pending until affordable" principle
        // this was supposed to follow, which the flat rejection here broke
        // for a team that's already frozen.

        String key = RegionPropertyKey.encode(regionId, property.id());
        TeamPendingState pendingState = savedData.getPendingState(teamId);

        if (ProtectionRollbackService.isDismantled(savedData, teamId, regionId, property, pendingState)) {
            TeamPendingState updated;
            if (property.isAtMinimum(newValue)) {
                updated = pendingState.withoutPendingProperty(key);
                ProtectionService.notifyTeamManagers(server, teamId, Component.translatable("message.lc_ftb_hook.protection_pending_cancelled"));
            } else {
                updated = pendingState.withPendingProperty(key, newValue);
                ProtectionService.notifyTeamManagers(server, teamId, Component.translatable("message.lc_ftb_hook.protection_change_pending"));
            }
            savedData.setPendingState(teamId, updated);
            syncState(server, teamId);
            return;
        }

        String liveValue = region.propertyValue(property);
        if (newValue.equals(liveValue)) {
            if (pendingState.hasPendingProperty(key)) {
                savedData.setPendingState(teamId, pendingState.withoutPendingProperty(key));
                ProtectionService.notifyTeamManagers(server, teamId, Component.translatable("message.lc_ftb_hook.protection_pending_cancelled"));
                syncState(server, teamId);
            }
            return;
        }

        // ProtectionPricing.calculateTotalUpkeepCopper(server, teamId, pendingState)
        // (the 3-arg overload) prices via ProtectionRollbackService.pricingProperties,
        // which deliberately EXCLUDES any pendingProperties entry whose region is
        // currently at minimum - that's correct for a genuine dismantle-restore
        // entry (never bill a suspended protection while it's off), but it's
        // structurally indistinguishable from the single most common edit there
        // is: turning a protection on for the first time (live value is, by
        // definition, still at minimum at this point). Using that overload for
        // "newPrice" silently dropped every such edit from the comparison, so
        // oldPrice == newPrice always held and the change applied immediately
        // instead of ever going pending - skipping the affordability check a
        // genuine cost increase is supposed to get. Building the pricing map
        // explicitly and force-including this one key (already proven NOT to be
        // a real dismantle by the isDismantled() check above) fixes that.
        Map<String, String> basePricing = ProtectionRollbackService.pricingProperties(server, teamId, pendingState);
        // basePricing carries THIS key's own value from whatever was most
        // recently queued for it (if anything) - e.g. cycling Private ->
        // Public -> Allies in one sitting leaves Public queued when the
        // Allies request arrives. Comparing against that stale queued
        // target instead of the true live value made every subsequent
        // switch look like a price change relative to it, even switches
        // back into the SAME tier the live value already sits in (Allies
        // after Private, both cost-neutral). Force this one key back to the
        // live value for the "old" side of the comparison - every OTHER
        // already-queued property still correctly uses its own pending
        // target via basePricing.
        Map<String, String> oldPricing = new java.util.HashMap<>(basePricing);
        oldPricing.put(key, liveValue);
        long oldPrice = ProtectionPricing.calculateTotalUpkeepCopper(server, teamId, pendingState, oldPricing);
        Map<String, String> newPricing = new java.util.HashMap<>(basePricing);
        newPricing.put(key, newValue);
        long newPrice = ProtectionPricing.calculateTotalUpkeepCopper(server, teamId, pendingState, newPricing);
        TeamPendingState simulated = pendingState.withPendingProperty(key, newValue);

        if (oldPrice == newPrice) {
            Region updatedRegion = property.isAtMinimum(newValue)
                    ? region.withoutProperty(property.id())
                    : region.withProperty(property.id(), newValue);
            savedData.updateRegion(teamId, updatedRegion);
            if (pendingState.hasPendingProperty(key)) {
                savedData.setPendingState(teamId, pendingState.withoutPendingProperty(key));
            }
        } else {
            savedData.setPendingState(teamId, simulated);
            ProtectionService.notifyTeamManagers(server, teamId, Component.translatable("message.lc_ftb_hook.protection_change_pending"));
        }
        syncState(server, teamId);
    }

    /** Region/ownership cleanup after a chunk left its team ({@code teamId} = the team that just lost it, if known). */
    public static void onChunkUnclaimed(MinecraftServer server, @Nullable UUID teamId, String chunkKey) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        boolean changed = savedData.clearChunkRegion(chunkKey);
        if (savedData.clearChunkOwnership(chunkKey)) {
            MarketplaceService.broadcastChunkOwnership(server);
        }

        if (teamId != null) {
            TeamPendingState pending = savedData.getPendingState(teamId);
            TeamPendingState cleared = pending.withoutChunkReferences(chunkKey);
            if (cleared != pending) {
                savedData.setPendingState(teamId, cleared);
                PendingStateSync.syncTeam(server, teamId);
            }
        }

        if (changed) {
            broadcastRegionMembership(server);
        }
    }

    public static int countChunks(MinecraftServer server, UUID teamId, UUID regionId) {
        return countInRegion(server, teamId, regionId, Backends.claim().claimedChunks(server, teamId));
    }

    /** How many of this region's chunks are currently force-loaded (actual claim-backend state, no pending deltas). */
    public static int countForceLoadedChunks(MinecraftServer server, UUID teamId, UUID regionId) {
        return countInRegion(server, teamId, regionId, Backends.claim().forceLoadedChunks(server, teamId));
    }

    private static int countInRegion(MinecraftServer server, UUID teamId, UUID regionId, List<ClaimPort.ChunkKey> chunks) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int count = 0;
        for (ClaimPort.ChunkKey chunk : chunks) {
            if (savedData.getChunkRegion(teamId, ChunkPosKey.encode(chunk)).equals(regionId)) {
                count++;
            }
        }
        return count;
    }

    /** Same as {@link #countForceLoadedChunks}, plus this region's share of queued pending force-load/unload requests - mirrors {@code ProtectionPricing.countEffectiveForceLoads}, broken down per region. */
    public static int countEffectiveForceLoadedChunks(MinecraftServer server, UUID teamId, UUID regionId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        int count = countForceLoadedChunks(server, teamId, regionId);
        for (String chunkKey : pendingState.pendingForceLoads()) {
            if (savedData.getChunkRegion(teamId, chunkKey).equals(regionId)) {
                count++;
            }
        }
        for (String chunkKey : pendingState.pendingForceUnloads()) {
            if (savedData.getChunkRegion(teamId, chunkKey).equals(regionId)) {
                count--;
            }
        }
        return Math.max(count, 0);
    }

    public static SyncRegionMembershipPayload createMembershipPayload(MinecraftServer server) {
        return new SyncRegionMembershipPayload(FtbHookSavedData.get(server).getAllChunkRegions());
    }

    public static void broadcastRegionMembership(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createMembershipPayload(server));
    }

    public static void syncMembershipToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createMembershipPayload(player.server));
    }

    public static SyncRegionsPayload createRegionsPayload(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        java.util.Map<UUID, Integer> counts = new java.util.HashMap<>();
        java.util.Map<UUID, Integer> billableCounts = new java.util.HashMap<>();
        java.util.Map<UUID, Long> currentUpkeep = new java.util.HashMap<>();
        java.util.Map<UUID, Long> pendingUpkeep = new java.util.HashMap<>();
        java.util.Map<UUID, Integer> forceLoadCounts = new java.util.HashMap<>();
        java.util.Map<UUID, Long> forceLoadCurrentUpkeep = new java.util.HashMap<>();
        java.util.Map<UUID, Long> forceLoadPendingUpkeep = new java.util.HashMap<>();
        if (TeamCatalog.exists(server, teamId)) {
            TeamPendingState pendingState = savedData.getPendingState(teamId);
            ProtectionPricing.ChunkCounts billable = ProtectionPricing.countBillableChunks(server, teamId);
            for (UUID regionId : savedData.getRegionOrder(teamId)) {
                counts.put(regionId, countChunks(server, teamId, regionId));
                Region region = savedData.getRegion(teamId, regionId);
                ProtectionPricing.RegionChunkCount rc = billable.perRegion().get(regionId);
                int billableChunks = rc != null ? rc.billableChunks() : 0;
                billableCounts.put(regionId, billableChunks);
                if (region != null) {
                    // regionBasePrice is priced per ProtectionProperty.PRICE_UNIT_CHUNKS
                    // chunks, not per single chunk - see ProtectionPricing.calculateProtectionCopper.
                    currentUpkeep.put(regionId,
                            (ProtectionPricing.regionBasePrice(region, Map.of()) * billableChunks) / ProtectionProperty.PRICE_UNIT_CHUNKS);
                    pendingUpkeep.put(regionId,
                            (ProtectionPricing.regionBasePrice(region, pendingState.pendingProperties()) * billableChunks) / ProtectionProperty.PRICE_UNIT_CHUNKS);
                }
                int flCurrentCount = countForceLoadedChunks(server, teamId, regionId);
                int flPendingCount = countEffectiveForceLoadedChunks(server, teamId, regionId, pendingState);
                forceLoadCounts.put(regionId, flCurrentCount);
                forceLoadCurrentUpkeep.put(regionId, ForceLoadBilling.regionCopper(server, teamId, regionId, flCurrentCount));
                forceLoadPendingUpkeep.put(regionId, ForceLoadBilling.regionCopper(server, teamId, regionId, flPendingCount));
            }
        }
        return new SyncRegionsPayload(
                savedData.getRegionOrder(teamId), savedData.getRegions(teamId),
                counts, billableCounts, currentUpkeep, pendingUpkeep,
                forceLoadCounts, forceLoadCurrentUpkeep, forceLoadPendingUpkeep,
                ForceLoadModes.enabledModes()
        );
    }

    public static void syncRegionsToTeam(MinecraftServer server, UUID teamId) {
        SyncRegionsPayload payload = createRegionsPayload(server, teamId);
        for (ServerPlayer member : Backends.claim().onlineMembers(server, teamId)) {
            PacketDistributor.sendToPlayer(member, payload);
        }
        broadcastPublicRegions(server);
    }

    /**
     * Every team's region names + protection values, broadcast to everyone -
     * unlike {@link #syncRegionsToTeam}'s payload (chunk counts and copper
     * prices stay team-private), this carries neither, so hovering another
     * team's chunk can show that team's actual protection status even for a
     * non-Default region (previously only resolvable for the viewer's own
     * team, since it relied on the team-scoped cache).
     */
    public static SyncPublicRegionsPayload createPublicRegionsPayload(MinecraftServer server) {
        Map<UUID, Region> regionsById = new java.util.HashMap<>();
        Map<UUID, UUID> defaultRegionByTeam = new java.util.HashMap<>();
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        for (UUID teamId : Backends.claim().allTeams(server)) {
            for (Region region : savedData.getRegions(teamId).values()) {
                regionsById.put(region.id(), region);
                if (region.isDefault()) {
                    defaultRegionByTeam.put(teamId, region.id());
                }
            }
        }
        return new SyncPublicRegionsPayload(regionsById, defaultRegionByTeam);
    }

    public static void broadcastPublicRegions(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createPublicRegionsPayload(server));
    }

    public static void syncPublicRegionsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPublicRegionsPayload(player.server));
    }

    public static void syncRegionsToPlayer(ServerPlayer player) {
        UUID teamId = TeamCatalog.teamOf(player.server, player.getUUID());
        if (teamId == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createRegionsPayload(player.server, teamId));
    }

    private static void syncState(MinecraftServer server, UUID teamId) {
        PendingStateSync.syncTeam(server, teamId);
        syncRegionsToTeam(server, teamId);
        WarStateSync.onUpkeepFactorsChanged(server, teamId);
    }
}
