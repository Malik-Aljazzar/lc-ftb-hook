package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.LCFtbHook;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FtbHookSavedData extends SavedData {
    static final String DATA_NAME = LCFtbHook.MOD_ID + "_team_accounts";
    /** Name given to the region auto-created from a pre-Regions world's "land chunks" on first load after upgrade. */
    private static final String MIGRATED_LAND_REGION_NAME = "Land";

    private final Map<UUID, TeamLinkEntry> teamLinks = new HashMap<>();
    /** Entries that failed to load - written back unchanged, never dropped (see {@link #load}). */
    private final List<CompoundTag> quarantined = new ArrayList<>();

    /** False while the server is still starting up (worlds not created yet) - {@link #get} would fail then. */
    public static boolean isAvailable(MinecraftServer server) {
        return server != null && server.overworld() != null;
    }

    /** Stored instead of an LC team id while a party has none. */
    public static final long NO_LC_TEAM = -1L;

    /**
     * Lightman's Currency numbers its teams from 0 - so 0 is a real team, and
     * only a negative id means "no team". (Up to 4.2.0 this mod treated 0 as
     * "none": a new world's first party could never keep its LC team, got
     * "Missing LC team bank account" and couldn't claim past the free chunks.)
     */
    public static boolean isLcTeamId(long lcTeamId) {
        return lcTeamId >= 0;
    }

    public static FtbHookSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) {
            // e.g. another mod's load-time event reaching our code before the worlds exist (4.0.0 incident)
            throw new IllegalStateException("LC FTB Hook world data accessed before the worlds are loaded");
        }
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(FtbHookSavedData::new, FtbHookSavedData::load), DATA_NAME);
    }

    private static FtbHookSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        FtbHookSavedData data = new FtbHookSavedData();
        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            try {
                loadEntry(data, entryTag);
            } catch (RuntimeException e) {
                // One broken entry must not cost every team its data: keep it as-is.
                data.quarantined.add(entryTag.copy());
                LCFtbHook.LOGGER.error("Could not read hook data entry #{} - kept unchanged in the save file", i, e);
            }
        }
        data.reportSharedLcLinks();
        return data;
    }

    private static void loadEntry(FtbHookSavedData data, CompoundTag entryTag) {
        {
            UUID teamId = entryTag.getUUID("TeamId");
            long lcTeamId = entryTag.contains("LcTeamId", Tag.TAG_LONG) ? entryTag.getLong("LcTeamId") : NO_LC_TEAM;
            // Raw NBT on purpose - only the Lightman's Currency adapter knows how to
            // interpret it (LcTeamSync.migrateLegacyBalance), so this class never
            // depends on a currency mod.
            CompoundTag legacyAccount = entryTag.contains("Account", Tag.TAG_COMPOUND) ? entryTag.getCompound("Account").copy() : null;
            boolean locked = entryTag.getBoolean("ProtectionLocked");
            TeamPendingState pending = loadPendingState(entryTag);

            Map<String, ChunkOwnership> chunkOwnership = loadChunkOwnership(entryTag);

            List<UUID> regionOrder;
            Map<UUID, Region> regions;
            Map<String, UUID> chunkRegions;
            if (entryTag.contains("Regions", Tag.TAG_LIST)) {
                regionOrder = new ArrayList<>();
                regions = new LinkedHashMap<>();
                loadRegions(entryTag, regionOrder, regions);
                chunkRegions = loadChunkRegions(entryTag);
            } else {
                // Pre-Regions save: migrate the old flat land-chunk set into a
                // dedicated "Land" region so the existing split isn't lost,
                // alongside the usual auto-created Default for everything else.
                regionOrder = new ArrayList<>();
                regions = new LinkedHashMap<>();
                chunkRegions = new HashMap<>();
                Region defaultRegion = Region.createDefault();
                regionOrder.add(defaultRegion.id());
                regions.put(defaultRegion.id(), defaultRegion);

                Set<String> legacyLandChunks = loadLegacyLandChunks(entryTag);
                if (!legacyLandChunks.isEmpty()) {
                    Region landRegion = Region.create(MIGRATED_LAND_REGION_NAME);
                    regionOrder.add(0, landRegion.id());
                    regions.put(landRegion.id(), landRegion);
                    for (String chunkKey : legacyLandChunks) {
                        chunkRegions.put(chunkKey, landRegion.id());
                    }
                }
                data.setDirty();
            }

            Set<UUID> warTargets = new HashSet<>();
            if (entryTag.contains("WarTargets", Tag.TAG_LIST)) {
                ListTag warList = entryTag.getList("WarTargets", Tag.TAG_INT_ARRAY);
                for (int j = 0; j < warList.size(); j++) {
                    warTargets.add(NbtUtils.loadUUID(warList.get(j)));
                }
            }
            Set<String> unsettledChunks = new HashSet<>();
            if (entryTag.contains("UnsettledChunks", Tag.TAG_LIST)) {
                ListTag unsettledList = entryTag.getList("UnsettledChunks", Tag.TAG_STRING);
                for (int j = 0; j < unsettledList.size(); j++) {
                    unsettledChunks.add(unsettledList.getString(j));
                }
            }
            Set<String> freshlyClaimedChunks = new HashSet<>();
            if (entryTag.contains("FreshlyClaimedChunks", Tag.TAG_LIST)) {
                ListTag freshlyClaimedList = entryTag.getList("FreshlyClaimedChunks", Tag.TAG_STRING);
                for (int j = 0; j < freshlyClaimedList.size(); j++) {
                    freshlyClaimedChunks.add(freshlyClaimedList.getString(j));
                }
            }
            TeamLinkInvariants.Checked checked = TeamLinkInvariants.check(regionOrder, regions, chunkRegions, pending);
            if (!checked.repairs().isEmpty()) {
                LCFtbHook.LOGGER.warn("Repaired hook data of team {}: {}", teamId, checked.repairs());
                data.setDirty();
            }
            data.teamLinks.put(teamId, new TeamLinkEntry(
                    teamId, lcTeamId, legacyAccount, locked, checked.pending(), checked.regionOrder(), checked.regions(),
                    checked.chunkRegions(), chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks
            ));
        }
    }

    /**
     * One LC team belongs to at most one party. At load time it can't be told
     * which party is the real one (4.1.0 cleared whichever came second - possibly
     * the active party's link), so a shared link is only reported here and
     * resolved at runtime by {@code LcTeamSync} with the claim mod's knowledge.
     */
    private void reportSharedLcLinks() {
        Map<Long, List<UUID>> parties = new HashMap<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (isLcTeamId(entry.lcTeamId())) {
                parties.computeIfAbsent(entry.lcTeamId(), id -> new ArrayList<>()).add(entry.ftbTeamId());
            }
        }
        parties.forEach((lcTeamId, linked) -> {
            if (linked.size() > 1) {
                LCFtbHook.LOGGER.warn("LC team {} is linked to {} parties {} - resolved once the parties are known", lcTeamId, linked.size(), linked);
            }
        });
    }

    /** The parties whose link points at this LC team. */
    public List<UUID> partiesLinkedTo(long lcTeamId) {
        return teamLinks.values().stream().filter(entry -> entry.lcTeamId() == lcTeamId).map(TeamLinkEntry::ftbTeamId).toList();
    }

    private static Set<String> loadLegacyLandChunks(CompoundTag entryTag) {
        Set<String> landChunks = new HashSet<>();
        if (entryTag.contains("LandChunks", Tag.TAG_LIST)) {
            ListTag landList = entryTag.getList("LandChunks", Tag.TAG_STRING);
            for (int j = 0; j < landList.size(); j++) {
                landChunks.add(landList.getString(j));
            }
        }
        return landChunks;
    }

    private static void loadRegions(CompoundTag entryTag, List<UUID> regionOrder, Map<UUID, Region> regions) {
        ListTag regionsTag = entryTag.getList("Regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < regionsTag.size(); i++) {
            CompoundTag regionTag = regionsTag.getCompound(i);
            UUID id = regionTag.getUUID("Id");
            String name = regionTag.getString("Name");
            boolean isDefault = regionTag.getBoolean("IsDefault");
            Map<String, String> properties = new HashMap<>();
            if (regionTag.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag propertiesTag = regionTag.getCompound("Properties");
                for (String key : propertiesTag.getAllKeys()) {
                    properties.put(key, propertiesTag.getString(key));
                }
            }
            // Absent (pre-existing saves from before this setting existed) defaults to true.
            boolean allowPrivateSelling = !regionTag.contains("AllowPrivateSelling") || regionTag.getBoolean("AllowPrivateSelling");
            // Absent (pre-existing saves) defaults to ALL, matching the old team-wide property's own default.
            String buyerAccess = regionTag.contains("BuyerAccess")
                    ? regionTag.getString("BuyerAccess")
                    : dev.malik.lcftbhook.teams.MarketplaceTeamProperties.ACCESS_ALL;
            regions.put(id, new Region(id, name, isDefault, properties, allowPrivateSelling, buyerAccess));
        }
        if (entryTag.contains("RegionOrder", Tag.TAG_LIST)) {
            ListTag orderTag = entryTag.getList("RegionOrder", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < orderTag.size(); i++) {
                UUID id = NbtUtils.loadUUID(orderTag.get(i));
                if (regions.containsKey(id)) {
                    regionOrder.add(id);
                }
            }
        }
        // Safety net: any region present in the map but missing from the
        // (possibly hand-edited or corrupted) order list is appended so it
        // isn't silently orphaned from dismantle-order/UI purposes.
        for (UUID id : regions.keySet()) {
            if (!regionOrder.contains(id)) {
                regionOrder.add(id);
            }
        }
    }

    private static Map<String, ChunkOwnership> loadChunkOwnership(CompoundTag entryTag) {
        Map<String, ChunkOwnership> ownership = new HashMap<>();
        if (!entryTag.contains("ChunkOwnership", Tag.TAG_COMPOUND)) {
            return ownership;
        }
        CompoundTag ownershipTag = entryTag.getCompound("ChunkOwnership");
        for (String chunkKey : ownershipTag.getAllKeys()) {
            CompoundTag entry = ownershipTag.getCompound(chunkKey);
            UUID privateOwner = entry.contains("Owner", Tag.TAG_INT_ARRAY) ? entry.getUUID("Owner") : null;
            Long price = entry.contains("Price", Tag.TAG_LONG) ? entry.getLong("Price") : null;
            Map<String, String> overrides = new HashMap<>();
            if (entry.contains("Overrides", Tag.TAG_COMPOUND)) {
                CompoundTag overridesTag = entry.getCompound("Overrides");
                for (String key : overridesTag.getAllKeys()) {
                    overrides.put(key, overridesTag.getString(key));
                }
            }
            Map<String, dev.malik.lcftbhook.data.PlayerAccessList> accessLists = new HashMap<>();
            if (entry.contains("AccessLists", Tag.TAG_COMPOUND)) {
                CompoundTag accessListsTag = entry.getCompound("AccessLists");
                for (String propertyId : accessListsTag.getAllKeys()) {
                    CompoundTag listTag = accessListsTag.getCompound(propertyId);
                    boolean whitelist = !listTag.contains("Whitelist") || listTag.getBoolean("Whitelist");
                    Set<UUID> players = new HashSet<>();
                    ListTag playersTag = listTag.getList("Players", Tag.TAG_INT_ARRAY);
                    for (int i = 0; i < playersTag.size(); i++) {
                        players.add(NbtUtils.loadUUID(playersTag.get(i)));
                    }
                    accessLists.put(propertyId, new dev.malik.lcftbhook.data.PlayerAccessList(whitelist, players));
                }
            }
            String label = entry.contains("Label", Tag.TAG_STRING) ? entry.getString("Label") : null;
            ownership.put(chunkKey, new ChunkOwnership(privateOwner, price, overrides, accessLists, label));
        }
        return ownership;
    }

    private static Map<String, UUID> loadChunkRegions(CompoundTag entryTag) {
        Map<String, UUID> chunkRegions = new HashMap<>();
        if (entryTag.contains("ChunkRegions", Tag.TAG_COMPOUND)) {
            CompoundTag chunkRegionsTag = entryTag.getCompound("ChunkRegions");
            for (String chunkKey : chunkRegionsTag.getAllKeys()) {
                chunkRegions.put(chunkKey, NbtUtils.loadUUID(chunkRegionsTag.get(chunkKey)));
            }
        }
        return chunkRegions;
    }

    private static TeamPendingState loadPendingState(CompoundTag entryTag) {
        Map<String, String> pendingProperties = new HashMap<>();
        if (entryTag.contains("PendingProperties", Tag.TAG_COMPOUND)) {
            CompoundTag propertiesTag = entryTag.getCompound("PendingProperties");
            for (String key : propertiesTag.getAllKeys()) {
                pendingProperties.put(key, propertiesTag.getString(key));
            }
        }

        Set<String> pendingLoads = new HashSet<>();
        if (entryTag.contains("PendingForceLoads", Tag.TAG_LIST)) {
            ListTag loads = entryTag.getList("PendingForceLoads", Tag.TAG_STRING);
            for (int i = 0; i < loads.size(); i++) {
                pendingLoads.add(loads.getString(i));
            }
        }

        Set<String> pendingUnloads = new HashSet<>();
        if (entryTag.contains("PendingForceUnloads", Tag.TAG_LIST)) {
            ListTag unloads = entryTag.getList("PendingForceUnloads", Tag.TAG_STRING);
            for (int i = 0; i < unloads.size(); i++) {
                pendingUnloads.add(unloads.getString(i));
            }
        }

        Set<UUID> pendingWarDeclares = new HashSet<>();
        if (entryTag.contains("PendingWarDeclares", Tag.TAG_LIST)) {
            ListTag declares = entryTag.getList("PendingWarDeclares", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < declares.size(); i++) {
                pendingWarDeclares.add(NbtUtils.loadUUID(declares.get(i)));
            }
        }

        Set<UUID> pendingWarEnds = new HashSet<>();
        if (entryTag.contains("PendingWarEnds", Tag.TAG_LIST)) {
            ListTag ends = entryTag.getList("PendingWarEnds", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < ends.size(); i++) {
                pendingWarEnds.add(NbtUtils.loadUUID(ends.get(i)));
            }
        }

        Map<String, UUID> pendingRegionAssignments = new HashMap<>();
        if (entryTag.contains("PendingRegionAssignments", Tag.TAG_COMPOUND)) {
            CompoundTag assignmentsTag = entryTag.getCompound("PendingRegionAssignments");
            for (String chunkKey : assignmentsTag.getAllKeys()) {
                pendingRegionAssignments.put(chunkKey, NbtUtils.loadUUID(assignmentsTag.get(chunkKey)));
            }
        }

        if (entryTag.contains("AutoSuspendedWars", Tag.TAG_LIST)) {
            ListTag suspendedWars = entryTag.getList("AutoSuspendedWars", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < suspendedWars.size(); i++) {
                pendingWarDeclares.add(NbtUtils.loadUUID(suspendedWars.get(i)));
            }
        }

        return new TeamPendingState(
                pendingProperties,
                pendingLoads,
                pendingUnloads,
                pendingRegionAssignments,
                pendingWarDeclares,
                pendingWarEnds
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (TeamLinkEntry entry : teamLinks.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("TeamId", entry.ftbTeamId());
            if (isLcTeamId(entry.lcTeamId())) {
                entryTag.putLong("LcTeamId", entry.lcTeamId());
            }
            if (entry.legacyAccount() != null) {
                entryTag.put("Account", entry.legacyAccount().copy());
            }
            entryTag.putBoolean("ProtectionLocked", entry.protectionLocked());
            savePendingState(entryTag, entry.pendingState());

            ListTag regionsTag = new ListTag();
            for (Region region : entry.regions().values()) {
                CompoundTag regionTag = new CompoundTag();
                regionTag.putUUID("Id", region.id());
                regionTag.putString("Name", region.name());
                regionTag.putBoolean("IsDefault", region.isDefault());
                regionTag.putBoolean("AllowPrivateSelling", region.allowPrivateSelling());
                regionTag.putString("BuyerAccess", region.buyerAccess());
                if (!region.properties().isEmpty()) {
                    CompoundTag propertiesTag = new CompoundTag();
                    region.properties().forEach(propertiesTag::putString);
                    regionTag.put("Properties", propertiesTag);
                }
                regionsTag.add(regionTag);
            }
            entryTag.put("Regions", regionsTag);

            ListTag regionOrderTag = new ListTag();
            entry.regionOrder().forEach(id -> regionOrderTag.add(NbtUtils.createUUID(id)));
            entryTag.put("RegionOrder", regionOrderTag);

            if (!entry.chunkRegions().isEmpty()) {
                CompoundTag chunkRegionsTag = new CompoundTag();
                entry.chunkRegions().forEach((chunkKey, regionId) -> chunkRegionsTag.put(chunkKey, NbtUtils.createUUID(regionId)));
                entryTag.put("ChunkRegions", chunkRegionsTag);
            }

            if (!entry.chunkOwnership().isEmpty()) {
                CompoundTag ownershipTag = new CompoundTag();
                entry.chunkOwnership().forEach((chunkKey, ownership) -> {
                    CompoundTag ownershipEntryTag = new CompoundTag();
                    if (ownership.privateOwner() != null) {
                        ownershipEntryTag.putUUID("Owner", ownership.privateOwner());
                    }
                    if (ownership.listingPricePerChunk() != null) {
                        ownershipEntryTag.putLong("Price", ownership.listingPricePerChunk());
                    }
                    if (!ownership.protectionOverride().isEmpty()) {
                        CompoundTag overridesTag = new CompoundTag();
                        ownership.protectionOverride().forEach(overridesTag::putString);
                        ownershipEntryTag.put("Overrides", overridesTag);
                    }
                    if (!ownership.accessLists().isEmpty()) {
                        CompoundTag accessListsTag = new CompoundTag();
                        ownership.accessLists().forEach((propertyId, accessList) -> {
                            CompoundTag listTag = new CompoundTag();
                            listTag.putBoolean("Whitelist", accessList.whitelist());
                            ListTag playersTag = new ListTag();
                            accessList.players().forEach(playerId -> playersTag.add(NbtUtils.createUUID(playerId)));
                            listTag.put("Players", playersTag);
                            accessListsTag.put(propertyId, listTag);
                        });
                        ownershipEntryTag.put("AccessLists", accessListsTag);
                    }
                    if (ownership.label() != null && !ownership.label().isEmpty()) {
                        ownershipEntryTag.putString("Label", ownership.label());
                    }
                    ownershipTag.put(chunkKey, ownershipEntryTag);
                });
                entryTag.put("ChunkOwnership", ownershipTag);
            }

            if (!entry.warTargets().isEmpty()) {
                ListTag warList = new ListTag();
                entry.warTargets().forEach(id -> warList.add(NbtUtils.createUUID(id)));
                entryTag.put("WarTargets", warList);
            }
            if (!entry.unsettledChunks().isEmpty()) {
                ListTag unsettledList = new ListTag();
                entry.unsettledChunks().forEach(key -> unsettledList.add(StringTag.valueOf(key)));
                entryTag.put("UnsettledChunks", unsettledList);
            }
            if (!entry.freshlyClaimedChunks().isEmpty()) {
                ListTag freshlyClaimedList = new ListTag();
                entry.freshlyClaimedChunks().forEach(key -> freshlyClaimedList.add(StringTag.valueOf(key)));
                entryTag.put("FreshlyClaimedChunks", freshlyClaimedList);
            }
            list.add(entryTag);
        }
        quarantined.forEach(raw -> list.add(raw.copy()));
        tag.put("Teams", list);
        return tag;
    }

    private static void savePendingState(CompoundTag entryTag, TeamPendingState pendingState) {
        if (!pendingState.pendingProperties().isEmpty()) {
            CompoundTag propertiesTag = new CompoundTag();
            pendingState.pendingProperties().forEach(propertiesTag::putString);
            entryTag.put("PendingProperties", propertiesTag);
        }
        if (!pendingState.pendingForceLoads().isEmpty()) {
            ListTag loads = new ListTag();
            pendingState.pendingForceLoads().forEach(key -> loads.add(StringTag.valueOf(key)));
            entryTag.put("PendingForceLoads", loads);
        }
        if (!pendingState.pendingForceUnloads().isEmpty()) {
            ListTag unloads = new ListTag();
            pendingState.pendingForceUnloads().forEach(key -> unloads.add(StringTag.valueOf(key)));
            entryTag.put("PendingForceUnloads", unloads);
        }
        if (!pendingState.pendingRegionAssignments().isEmpty()) {
            CompoundTag assignmentsTag = new CompoundTag();
            pendingState.pendingRegionAssignments().forEach((chunkKey, regionId) -> assignmentsTag.put(chunkKey, NbtUtils.createUUID(regionId)));
            entryTag.put("PendingRegionAssignments", assignmentsTag);
        }
        if (!pendingState.pendingWarDeclares().isEmpty()) {
            ListTag declares = new ListTag();
            pendingState.pendingWarDeclares().forEach(id -> declares.add(NbtUtils.createUUID(id)));
            entryTag.put("PendingWarDeclares", declares);
        }
        if (!pendingState.pendingWarEnds().isEmpty()) {
            ListTag ends = new ListTag();
            pendingState.pendingWarEnds().forEach(id -> ends.add(NbtUtils.createUUID(id)));
            entryTag.put("PendingWarEnds", ends);
        }
    }

    public TeamLinkEntry getOrCreateLink(UUID ftbTeamId) {
        return teamLinks.computeIfAbsent(ftbTeamId, id -> {
            setDirty();
            return freshEntry(id);
        });
    }

    private static TeamLinkEntry freshEntry(UUID ftbTeamId) {
        Region defaultRegion = Region.createDefault();
        return new TeamLinkEntry(
                ftbTeamId, NO_LC_TEAM, null, false, new TeamPendingState(),
                new ArrayList<>(List.of(defaultRegion.id())),
                new LinkedHashMap<>(Map.of(defaultRegion.id(), defaultRegion)),
                new HashMap<>(),
                new HashMap<>(),
                Set.of(),
                new HashSet<>(),
                new HashSet<>()
        );
    }

    @Nullable
    public TeamLinkEntry get(UUID ftbTeamId) {
        return teamLinks.get(ftbTeamId);
    }

    @Nullable
    public TeamLinkEntry findByLcTeamId(long lcTeamId) {
        if (!isLcTeamId(lcTeamId)) {
            return null;
        }
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return entry;
            }
        }
        return null;
    }

    public java.util.Collection<TeamLinkEntry> getAllLinks() {
        return List.copyOf(teamLinks.values());
    }

    @Nullable
    public TeamLinkEntry removeLink(UUID ftbTeamId) {
        TeamLinkEntry removed = teamLinks.remove(ftbTeamId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Clears the LC bank link only; keeps regions, pending state, and protection lock. */
    public boolean clearLcTeamLink(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry == null || (!isLcTeamId(entry.lcTeamId()) && entry.legacyAccount() == null)) {
            return false;
        }
        teamLinks.put(ftbTeamId, entry.withLcTeamId(NO_LC_TEAM).withLegacyAccount(null));
        setDirty();
        return true;
    }

    public TeamPendingState getPendingState(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        return entry == null ? new TeamPendingState() : entry.pendingState();
    }

    public void setPendingState(UUID ftbTeamId, TeamPendingState pendingState) {
        TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        teamLinks.put(ftbTeamId, entry.withPendingState(pendingState));
        setDirty();
    }

    public void setLcTeamId(UUID ftbTeamId, long lcTeamId) {
        TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        if (entry.lcTeamId() != lcTeamId) {
            teamLinks.put(ftbTeamId, entry.withLcTeamId(lcTeamId));
            setDirty();
        }
    }

    public void clearLegacyAccount(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry != null && entry.legacyAccount() != null) {
            teamLinks.put(ftbTeamId, entry.withLegacyAccount(null));
            setDirty();
        }
    }

    /** Writes back a (currency-backend-serialized) legacy account after it was modified. */
    public void updateLegacyAccount(UUID ftbTeamId, CompoundTag account) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry != null && entry.legacyAccount() != null) {
            teamLinks.put(ftbTeamId, entry.withLegacyAccount(account.copy()));
            setDirty();
        }
    }

    public void setProtectionLocked(UUID teamId, boolean locked) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry != null && entry.protectionLocked() != locked) {
            teamLinks.put(teamId, entry.withProtectionLocked(locked));
            setDirty();
        } else if (entry == null && locked) {
            teamLinks.put(teamId, freshEntry(teamId).withProtectionLocked(true));
            setDirty();
        }
    }

    // ---- Regions ----

    public Map<UUID, Region> getRegions(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Map.of() : entry.regions();
    }

    @Nullable
    public Region getRegion(UUID teamId, UUID regionId) {
        return getRegions(teamId).get(regionId);
    }

    public List<UUID> getRegionOrder(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? List.of() : entry.regionOrder();
    }

    public UUID getDefaultRegionId(UUID teamId) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        for (Region region : entry.regions().values()) {
            if (region.isDefault()) {
                return region.id();
            }
        }
        // Should be unreachable (freshEntry always seeds one), but stay defensive.
        Region defaultRegion = Region.createDefault();
        addRegion(teamId, defaultRegion, true);
        return defaultRegion.id();
    }

    public void updateRegion(UUID teamId, Region region) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        Map<UUID, Region> updated = new LinkedHashMap<>(entry.regions());
        updated.put(region.id(), region);
        teamLinks.put(teamId, entry.withRegions(Map.copyOf(updated)));
        setDirty();
    }

    /** @param atTop true = insert at index 0 (dismantled first); false = append at the end. */
    public void addRegion(UUID teamId, Region region, boolean atTop) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        Map<UUID, Region> updatedRegions = new LinkedHashMap<>(entry.regions());
        updatedRegions.put(region.id(), region);
        List<UUID> updatedOrder = new ArrayList<>(entry.regionOrder());
        if (atTop) {
            updatedOrder.add(0, region.id());
        } else {
            updatedOrder.add(region.id());
        }
        teamLinks.put(teamId, entry.withRegions(Map.copyOf(updatedRegions)).withRegionOrder(List.copyOf(updatedOrder)));
        setDirty();
    }

    /** Removes a region, reassigning every chunk currently in it to the team's Default region. Refuses (no-op) on the Default region itself. */
    public boolean removeRegion(UUID teamId, UUID regionId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return false;
        }
        Region region = entry.regions().get(regionId);
        if (region == null || region.isDefault()) {
            return false;
        }
        UUID defaultId = getDefaultRegionId(teamId);
        entry = teamLinks.get(teamId); // may have changed if getDefaultRegionId had to seed one

        Map<UUID, Region> updatedRegions = new LinkedHashMap<>(entry.regions());
        updatedRegions.remove(regionId);
        List<UUID> updatedOrder = new ArrayList<>(entry.regionOrder());
        updatedOrder.remove(regionId);

        // Members fall back to Default, which is the absence of an entry - an
        // explicit Default id would read as a different region than the
        // team's implicit-Default chunks and draw a border between them.
        Map<String, UUID> updatedChunkRegions = new HashMap<>(entry.chunkRegions());
        updatedChunkRegions.values().removeIf(id -> id.equals(regionId) || id.equals(defaultId));

        TeamPendingState cleanedPending = entry.pendingState().withoutRegionReferences(regionId);

        teamLinks.put(teamId, entry
                .withRegions(Map.copyOf(updatedRegions))
                .withRegionOrder(List.copyOf(updatedOrder))
                .withChunkRegions(Map.copyOf(updatedChunkRegions))
                .withPendingState(cleanedPending));
        setDirty();
        return true;
    }

    public boolean setRegionOrder(UUID teamId, List<UUID> newOrder) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return false;
        }
        if (!Set.copyOf(newOrder).equals(entry.regions().keySet()) || newOrder.size() != entry.regionOrder().size()) {
            return false; // not a permutation of the existing regions
        }
        teamLinks.put(teamId, entry.withRegionOrder(List.copyOf(newOrder)));
        setDirty();
        return true;
    }

    public UUID getChunkRegion(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return getDefaultRegionId(teamId);
        }
        return entry.chunkRegions().getOrDefault(chunkKey, getDefaultRegionId(teamId));
    }

    public void setChunkRegion(UUID teamId, String chunkKey, UUID regionId) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        UUID defaultId = getDefaultRegionId(teamId);
        entry = teamLinks.get(teamId);
        Map<String, UUID> updated = new HashMap<>(entry.chunkRegions());
        if (regionId.equals(defaultId)) {
            // Default is the implicit absence-of-entry state; keep the map sparse.
            updated.remove(chunkKey);
        } else {
            updated.put(chunkKey, regionId);
        }
        teamLinks.put(teamId, entry.withChunkRegions(Map.copyOf(updated)));
        setDirty();
    }

    /** Removes a chunk's region assignment from every team (e.g. after unclaiming). */
    public boolean clearChunkRegion(String chunkKey) {
        boolean changed = false;
        for (TeamLinkEntry entry : List.copyOf(teamLinks.values())) {
            if (entry.chunkRegions().containsKey(chunkKey)) {
                Map<String, UUID> updated = new HashMap<>(entry.chunkRegions());
                updated.remove(chunkKey);
                teamLinks.put(entry.ftbTeamId(), entry.withChunkRegions(Map.copyOf(updated)));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public Map<String, UUID> getAllChunkRegions() {
        Map<String, UUID> all = new HashMap<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            // Skips explicit Default entries older saves may still hold (see removeRegion).
            UUID defaultId = entry.regions().values().stream()
                    .filter(Region::isDefault).map(Region::id).findFirst().orElse(null);
            entry.chunkRegions().forEach((chunkKey, regionId) -> {
                if (!regionId.equals(defaultId)) {
                    all.put(chunkKey, regionId);
                }
            });
        }
        return all;
    }

    // ---- Per-chunk data as a whole (see ChunkDataReconciliation) ----

    /** Every chunk this team has per-chunk data for: regions, marketplace, settlement markers, queued changes. */
    public Set<String> trackedChunkKeys(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>(entry.chunkRegions().keySet());
        keys.addAll(entry.chunkOwnership().keySet());
        keys.addAll(entry.unsettledChunks());
        keys.addAll(entry.freshlyClaimedChunks());
        keys.addAll(entry.pendingState().pendingRegionAssignments().keySet());
        keys.addAll(entry.pendingState().pendingForceLoads());
        keys.addAll(entry.pendingState().pendingForceUnloads());
        return keys;
    }

    /** Removes everything this team keeps for these chunks (the team no longer owns them). */
    public void dropChunkData(UUID teamId, Set<String> chunkKeys) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null || chunkKeys.isEmpty()) {
            return;
        }
        Map<String, UUID> regions = new HashMap<>(entry.chunkRegions());
        Map<String, ChunkOwnership> ownership = new HashMap<>(entry.chunkOwnership());
        Set<String> unsettled = new HashSet<>(entry.unsettledChunks());
        Set<String> fresh = new HashSet<>(entry.freshlyClaimedChunks());
        TeamPendingState pending = entry.pendingState();
        for (String chunkKey : chunkKeys) {
            regions.remove(chunkKey);
            ownership.remove(chunkKey);
            unsettled.remove(chunkKey);
            fresh.remove(chunkKey);
            pending = pending.withoutChunkReferences(chunkKey);
        }
        teamLinks.put(teamId, entry.withChunkRegions(Map.copyOf(regions))
                .withChunkOwnership(Map.copyOf(ownership))
                .withUnsettledChunks(Set.copyOf(unsettled))
                .withFreshlyClaimedChunks(Set.copyOf(fresh))
                .withPendingState(pending));
        setDirty();
    }

    // ---- Unsettled chunks (no protection until the next successful upkeep settlement) ----

    public boolean isChunkUnsettled(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry != null && entry.unsettledChunks().contains(chunkKey);
    }

    public void markChunkUnsettled(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        entry = teamLinks.get(teamId);
        if (entry.unsettledChunks().contains(chunkKey)) {
            return;
        }
        Set<String> updated = new HashSet<>(entry.unsettledChunks());
        updated.add(chunkKey);
        teamLinks.put(teamId, entry.withUnsettledChunks(Set.copyOf(updated)));
        setDirty();
    }

    /** Called once a team's upkeep successfully settles - from then on every currently-claimed chunk uses its Region's real protection. */
    public void clearUnsettledChunks(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null || entry.unsettledChunks().isEmpty()) {
            return;
        }
        teamLinks.put(teamId, entry.withUnsettledChunks(Set.of()));
        setDirty();
    }

    /** Bare chunk keys across every team - broadcast globally so any viewer's hover panel can show "not sellable yet", same as chunk ownership. */
    public Set<String> getAllUnsettledChunks() {
        Set<String> all = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            all.addAll(entry.unsettledChunks());
        }
        return all;
    }

    // ---- Freshly claimed chunks (marketplace sell cooldown - claims only, never private buys) ----

    public boolean isChunkFreshlyClaimed(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry != null && entry.freshlyClaimedChunks().contains(chunkKey);
    }

    public void markChunkFreshlyClaimed(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        entry = teamLinks.get(teamId);
        if (entry.freshlyClaimedChunks().contains(chunkKey)) {
            return;
        }
        Set<String> updated = new HashSet<>(entry.freshlyClaimedChunks());
        updated.add(chunkKey);
        teamLinks.put(teamId, entry.withFreshlyClaimedChunks(Set.copyOf(updated)));
        setDirty();
    }

    /** Called once a team's upkeep successfully settles - from then on every currently-claimed chunk is sellable again. */
    public void clearFreshlyClaimedChunks(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null || entry.freshlyClaimedChunks().isEmpty()) {
            return;
        }
        teamLinks.put(teamId, entry.withFreshlyClaimedChunks(Set.of()));
        setDirty();
    }

    /** Bare chunk keys across every team - broadcast globally so any viewer's hover panel can show "not sellable yet". */
    public Set<String> getAllFreshlyClaimedChunks() {
        Set<String> all = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            all.addAll(entry.freshlyClaimedChunks());
        }
        return all;
    }

    // ---- Marketplace ----

    public ChunkOwnership getChunkOwnership(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return ChunkOwnership.EMPTY;
        }
        return entry.chunkOwnership().getOrDefault(chunkKey, ChunkOwnership.EMPTY);
    }

    public void setChunkOwnership(UUID teamId, String chunkKey, ChunkOwnership ownership) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
        Map<String, ChunkOwnership> updated = new HashMap<>(entry.chunkOwnership());
        if (ownership.isEmpty()) {
            updated.remove(chunkKey);
        } else {
            updated.put(chunkKey, ownership);
        }
        teamLinks.put(teamId, entry.withChunkOwnership(Map.copyOf(updated)));
        setDirty();
    }

    /** Clears a chunk's ownership/listing/override from every team (e.g. after unclaiming). */
    public boolean clearChunkOwnership(String chunkKey) {
        boolean changed = false;
        for (TeamLinkEntry entry : List.copyOf(teamLinks.values())) {
            if (entry.chunkOwnership().containsKey(chunkKey)) {
                Map<String, ChunkOwnership> updated = new HashMap<>(entry.chunkOwnership());
                updated.remove(chunkKey);
                teamLinks.put(entry.ftbTeamId(), entry.withChunkOwnership(Map.copyOf(updated)));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Sparse chunkKey -> ChunkOwnership map for one team only (unlike {@link #getAllChunkOwnership}, which merges every team). */
    public Map<String, ChunkOwnership> getChunkOwnershipForTeam(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Map.of() : entry.chunkOwnership();
    }

    public Map<String, ChunkOwnership> getAllChunkOwnership() {
        Map<String, ChunkOwnership> all = new HashMap<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            all.putAll(entry.chunkOwnership());
        }
        return all;
    }

    public boolean isProtectionLocked(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry != null && entry.protectionLocked();
    }

    public Set<Long> getLinkedLcTeamIds() {
        Set<Long> linkedIds = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (isLcTeamId(entry.lcTeamId())) {
                linkedIds.add(entry.lcTeamId());
            }
        }
        return linkedIds;
    }

    public Set<UUID> getWarTargets(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.warTargets();
    }

    public boolean isAtWarWith(UUID declarerTeamId, UUID targetTeamId) {
        return getWarTargets(declarerTeamId).contains(targetTeamId);
    }

    public boolean setWarTarget(UUID declarerTeamId, UUID targetTeamId, boolean atWar) {
        TeamLinkEntry entry = getOrCreateLink(declarerTeamId);
        Set<UUID> updated = new HashSet<>(entry.warTargets());
        if (atWar) {
            if (!updated.add(targetTeamId)) {
                return false;
            }
        } else if (!updated.remove(targetTeamId)) {
            return false;
        }
        teamLinks.put(declarerTeamId, entry.withWarTargets(Set.copyOf(updated)));
        setDirty();
        return true;
    }

    public Set<UUID> collectWarPartnerIds(UUID teamId) {
        Set<UUID> partners = new HashSet<>();
        partners.addAll(getWarTargets(teamId));
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(teamId)) {
                partners.add(entry.ftbTeamId());
            }
        }
        partners.remove(teamId);
        return partners;
    }

    public void clearWarReferences(UUID teamId) {
        boolean changed = false;
        TeamLinkEntry ownEntry = teamLinks.get(teamId);
        if (ownEntry != null && !ownEntry.warTargets().isEmpty()) {
            teamLinks.put(teamId, ownEntry.withWarTargets(Set.of()));
            changed = true;
        }
        for (TeamLinkEntry entry : List.copyOf(teamLinks.values())) {
            UUID entryTeamId = entry.ftbTeamId();
            TeamPendingState pending = entry.pendingState();
            TeamPendingState cleaned = pending.withoutWarReferences(teamId);
            if (cleaned != pending) {
                teamLinks.put(entryTeamId, entry.withPendingState(cleaned));
                changed = true;
            }
            if (entry.warTargets().contains(teamId)) {
                Set<UUID> updated = new HashSet<>(entry.warTargets());
                updated.remove(teamId);
                teamLinks.put(entryTeamId, teamLinks.get(entryTeamId).withWarTargets(Set.copyOf(updated)));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public record TeamLinkEntry(
            UUID ftbTeamId,
            long lcTeamId,
            @Nullable CompoundTag legacyAccount,
            boolean protectionLocked,
            TeamPendingState pendingState,
            List<UUID> regionOrder,
            Map<UUID, Region> regions,
            Map<String, UUID> chunkRegions,
            Map<String, ChunkOwnership> chunkOwnership,
            Set<UUID> warTargets,
            /** Chunks claimed or newly privately-bought that haven't gone through an upkeep settlement yet - see {@link RegionService#resolveRegion}. */
            Set<String> unsettledChunks,
            /**
             * Chunks freshly CLAIMED (not bought - see {@code ChunkClaimHandler#afterClaim})
             * that haven't gone through an upkeep settlement yet, blocking them from
             * being listed on the marketplace until then. Deliberately separate from
             * {@link #unsettledChunks}, which ALSO covers freshly-bought chunks for an
             * unrelated purpose (protection reset) - a private buyer must be able to
             * resell a chunk they just bought immediately, so the marketplace
             * "not sellable yet" restriction can't reuse that broader set.
             */
            Set<String> freshlyClaimedChunks
    ) {
        TeamLinkEntry withLcTeamId(long id) {
            return new TeamLinkEntry(ftbTeamId, id, legacyAccount, protectionLocked, pendingState, regionOrder, regions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withLegacyAccount(@Nullable CompoundTag account) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, account, protectionLocked, pendingState, regionOrder, regions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withProtectionLocked(boolean locked) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, locked, pendingState, regionOrder, regions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withPendingState(TeamPendingState pending) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pending, regionOrder, regions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withRegionOrder(List<UUID> order) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, order, regions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withRegions(Map<UUID, Region> updatedRegions) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, regionOrder, updatedRegions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withChunkRegions(Map<String, UUID> updatedChunkRegions) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, regionOrder, regions, updatedChunkRegions, chunkOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withChunkOwnership(Map<String, ChunkOwnership> updatedOwnership) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, regionOrder, regions, chunkRegions, updatedOwnership, warTargets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withWarTargets(Set<UUID> targets) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, regionOrder, regions, chunkRegions, chunkOwnership, targets, unsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withUnsettledChunks(Set<String> updatedUnsettledChunks) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, regionOrder, regions, chunkRegions, chunkOwnership, warTargets, updatedUnsettledChunks, freshlyClaimedChunks);
        }

        TeamLinkEntry withFreshlyClaimedChunks(Set<String> updatedFreshlyClaimedChunks) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, regionOrder, regions, chunkRegions, chunkOwnership, warTargets, unsettledChunks, updatedFreshlyClaimedChunks);
        }
    }
}
