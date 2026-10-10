package dev.malik.lcftbhook.data;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Private-property layer on top of a chunk's Region (see {@link Region}).
 * The chunk legally stays part of the team's territory - upkeep, region
 * membership, and dismantle order are all unaffected by this - a private
 * owner can only ever loosen (never tighten) that one chunk's protection
 * below the owning region's baseline via {@code protectionOverride}.
 * Sparse: absence of an entry for a chunk means state-owned, unlisted, no
 * override (matches every other sparse-map convention in this mod).
 */
public record ChunkOwnership(
        @Nullable UUID privateOwner,
        @Nullable Long listingPricePerChunk,
        Map<String, String> protectionOverride,
        Map<String, PlayerAccessList> accessLists,
        @Nullable String label
) {
    public static final ChunkOwnership EMPTY = new ChunkOwnership(null, null, Map.of(), Map.of(), null);

    public boolean isStateOwned() {
        return privateOwner == null;
    }

    public boolean isListed() {
        return listingPricePerChunk != null;
    }

    public boolean isEmpty() {
        return privateOwner == null && listingPricePerChunk == null && protectionOverride.isEmpty()
                && accessLists.isEmpty() && (label == null || label.isEmpty());
    }

    public ChunkOwnership withListingPrice(@Nullable Long pricePerChunk) {
        return new ChunkOwnership(privateOwner, pricePerChunk, protectionOverride, accessLists, label);
    }

    public ChunkOwnership withOverrideProperty(String propertyId, String value) {
        Map<String, String> updated = new HashMap<>(protectionOverride);
        updated.put(propertyId, value);
        return new ChunkOwnership(privateOwner, listingPricePerChunk, Map.copyOf(updated), accessLists, label);
    }

    public ChunkOwnership withAccessList(String propertyId, PlayerAccessList list) {
        Map<String, PlayerAccessList> updated = new HashMap<>(accessLists);
        if (list.isEmpty()) {
            updated.remove(propertyId);
        } else {
            updated.put(propertyId, list);
        }
        return new ChunkOwnership(privateOwner, listingPricePerChunk, protectionOverride, Map.copyOf(updated), label);
    }

    /** Empty/blank clears the label (stored as {@code null}, matching every other sparse/optional field here). */
    public ChunkOwnership withLabel(@Nullable String newLabel) {
        return new ChunkOwnership(privateOwner, listingPricePerChunk, protectionOverride, accessLists,
                newLabel == null || newLabel.isEmpty() ? null : newLabel);
    }

    /** Resets to state ownership at region baseline - used by "Buy as Country" and by a resale to a different player. */
    public static ChunkOwnership resetToStateOwned() {
        return EMPTY;
    }

}
