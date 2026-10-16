package dev.malik.lcftbhook.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamPendingState {
    private final Map<String, String> pendingProperties;
    private final Set<String> pendingForceLoads;
    private final Set<String> pendingForceUnloads;
    private final Map<String, UUID> pendingRegionAssignments;
    private final Set<UUID> pendingWarDeclares;
    private final Set<UUID> pendingWarEnds;

    public TeamPendingState() {
        this(new HashMap<>(), new HashSet<>(), new HashSet<>(), new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamPendingState(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads
    ) {
        this(pendingProperties, pendingForceLoads, pendingForceUnloads, new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    public TeamPendingState(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads,
            Set<UUID> pendingWarDeclares,
            Set<UUID> pendingWarEnds
    ) {
        this(pendingProperties, pendingForceLoads, pendingForceUnloads, new HashMap<>(), pendingWarDeclares, pendingWarEnds);
    }

    public TeamPendingState(
            Map<String, String> pendingProperties,
            Set<String> pendingForceLoads,
            Set<String> pendingForceUnloads,
            Map<String, UUID> pendingRegionAssignments,
            Set<UUID> pendingWarDeclares,
            Set<UUID> pendingWarEnds
    ) {
        this.pendingProperties = new HashMap<>(pendingProperties);
        this.pendingForceLoads = new HashSet<>(pendingForceLoads);
        this.pendingForceUnloads = new HashSet<>(pendingForceUnloads);
        this.pendingRegionAssignments = new HashMap<>(pendingRegionAssignments);
        this.pendingWarDeclares = new HashSet<>(pendingWarDeclares);
        this.pendingWarEnds = new HashSet<>(pendingWarEnds);
    }

    public Map<String, String> pendingProperties() {
        return Collections.unmodifiableMap(pendingProperties);
    }

    public Set<String> pendingForceLoads() {
        return Collections.unmodifiableSet(pendingForceLoads);
    }

    public Set<String> pendingForceUnloads() {
        return Collections.unmodifiableSet(pendingForceUnloads);
    }

    public Map<String, UUID> pendingRegionAssignments() {
        return Collections.unmodifiableMap(pendingRegionAssignments);
    }

    public Set<UUID> pendingWarDeclares() {
        return Collections.unmodifiableSet(pendingWarDeclares);
    }

    public Set<UUID> pendingWarEnds() {
        return Collections.unmodifiableSet(pendingWarEnds);
    }

    public boolean isEmpty() {
        return pendingProperties.isEmpty()
                && pendingForceLoads.isEmpty()
                && pendingForceUnloads.isEmpty()
                && pendingRegionAssignments.isEmpty()
                && pendingWarDeclares.isEmpty()
                && pendingWarEnds.isEmpty();
    }

    public boolean hasPendingProperty(String propertyId) {
        return pendingProperties.containsKey(propertyId);
    }

    public boolean isPendingForceLoad(String chunkKey) {
        return pendingForceLoads.contains(chunkKey);
    }

    public boolean isPendingForceUnload(String chunkKey) {
        return pendingForceUnloads.contains(chunkKey);
    }

    public boolean isPendingRegionAssignment(String chunkKey) {
        return pendingRegionAssignments.containsKey(chunkKey);
    }

    public UUID pendingRegionAssignmentTarget(String chunkKey) {
        return pendingRegionAssignments.get(chunkKey);
    }

    public boolean isPendingWarDeclare(UUID targetTeamId) {
        return pendingWarDeclares.contains(targetTeamId);
    }

    public boolean isPendingWarEnd(UUID targetTeamId) {
        return pendingWarEnds.contains(targetTeamId);
    }

    public TeamPendingState withPendingProperty(String propertyId, String value) {
        TeamPendingState copy = copy();
        copy.pendingProperties.put(propertyId, value);
        return copy;
    }

    public TeamPendingState withoutPendingProperty(String propertyId) {
        if (!pendingProperties.containsKey(propertyId)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingProperties.remove(propertyId);
        return copy;
    }

    public TeamPendingState withPendingForceLoad(String chunkKey) {
        TeamPendingState copy = copy();
        copy.pendingForceLoads.add(chunkKey);
        copy.pendingForceUnloads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withoutPendingForceLoad(String chunkKey) {
        if (!pendingForceLoads.contains(chunkKey)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingForceLoads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withPendingForceUnload(String chunkKey) {
        TeamPendingState copy = copy();
        copy.pendingForceUnloads.add(chunkKey);
        copy.pendingForceLoads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withoutPendingForceUnload(String chunkKey) {
        if (!pendingForceUnloads.contains(chunkKey)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingForceUnloads.remove(chunkKey);
        return copy;
    }

    public TeamPendingState withPendingRegionAssignment(String chunkKey, UUID regionId) {
        TeamPendingState copy = copy();
        copy.pendingRegionAssignments.put(chunkKey, regionId);
        return copy;
    }

    public TeamPendingState withoutPendingRegionAssignment(String chunkKey) {
        if (!pendingRegionAssignments.containsKey(chunkKey)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingRegionAssignments.remove(chunkKey);
        return copy;
    }

    /**
     * Forgets everything queued for one chunk (region assignment, force-load,
     * force-unload) - for when the chunk stops belonging to the team. A queued
     * force-load left behind used to stay forever: shown as owed upkeep and as
     * force-loaded on the maps, and retried (and reported unaffordable) every
     * period, until the player re-claimed the chunk and cancelled it.
     */
    public TeamPendingState withoutChunkReferences(String chunkKey) {
        return withoutPendingRegionAssignment(chunkKey).withoutPendingForceLoad(chunkKey).withoutPendingForceUnload(chunkKey);
    }

    public TeamPendingState withPendingWarDeclare(UUID targetTeamId) {
        TeamPendingState copy = copy();
        copy.pendingWarDeclares.add(targetTeamId);
        copy.pendingWarEnds.remove(targetTeamId);
        return copy;
    }

    public TeamPendingState withoutPendingWarDeclare(UUID targetTeamId) {
        if (!pendingWarDeclares.contains(targetTeamId)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingWarDeclares.remove(targetTeamId);
        return copy;
    }

    public TeamPendingState withPendingWarEnd(UUID targetTeamId) {
        TeamPendingState copy = copy();
        copy.pendingWarEnds.add(targetTeamId);
        copy.pendingWarDeclares.remove(targetTeamId);
        return copy;
    }

    public TeamPendingState withoutPendingWarEnd(UUID targetTeamId) {
        if (!pendingWarEnds.contains(targetTeamId)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingWarEnds.remove(targetTeamId);
        return copy;
    }

    public TeamPendingState withoutWarReferences(UUID teamId) {
        if (!pendingWarDeclares.contains(teamId) && !pendingWarEnds.contains(teamId)) {
            return this;
        }
        TeamPendingState copy = copy();
        copy.pendingWarDeclares.remove(teamId);
        copy.pendingWarEnds.remove(teamId);
        return copy;
    }

    /**
     * Strips every pending property edit ({@code "<regionId>|<propertyId>"}
     * keys, see {@link RegionPropertyKey}) and every pending region
     * assignment that references the given region - used when a region is
     * deleted so no stale pending state can resurrect it at the next
     * upkeep settlement.
     */
    public TeamPendingState withoutRegionReferences(UUID regionId) {
        boolean changed = false;
        TeamPendingState copy = copy();
        for (String key : pendingProperties.keySet()) {
            if (RegionPropertyKey.referencesRegion(key, regionId)) {
                copy.pendingProperties.remove(key);
                changed = true;
            }
        }
        for (Map.Entry<String, UUID> entry : pendingRegionAssignments.entrySet()) {
            if (entry.getValue().equals(regionId)) {
                copy.pendingRegionAssignments.remove(entry.getKey());
                changed = true;
            }
        }
        return changed ? copy : this;
    }

    public TeamPendingState cleared() {
        return new TeamPendingState();
    }

    public TeamPendingState copy() {
        return new TeamPendingState(
                pendingProperties,
                pendingForceLoads,
                pendingForceUnloads,
                pendingRegionAssignments,
                pendingWarDeclares,
                pendingWarEnds
        );
    }
}
