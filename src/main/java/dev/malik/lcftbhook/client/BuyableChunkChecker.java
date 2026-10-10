package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.ViewerRank;
import dev.malik.lcftbhook.teams.MarketplaceTeamProperties;

import javax.annotation.Nullable;
import java.util.UUID;

/** Whether the local player could actually buy a listed chunk (not already theirs, not blocked by the owning Region's buyer-access setting). */
public final class BuyableChunkChecker {
    private BuyableChunkChecker() {
    }

    /** @param teamId the land's owning team, null if unknown */
    public static boolean isBuyable(ChunkOwnership ownership, @Nullable UUID teamId, String chunkKey, @Nullable UUID localPlayer) {
        if (!ownership.isListed() || localPlayer == null) {
            return false;
        }
        if (localPlayer.equals(ownership.privateOwner())) {
            return false;
        }
        return teamId == null || hasAccess(teamId, chunkKey);
    }

    private static boolean hasAccess(UUID teamId, String chunkKey) {
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        Region region = regionId != null ? ClientPublicRegions.get(regionId) : ClientPublicRegions.getDefaultFor(teamId);
        String access = region != null ? region.buyerAccess() : MarketplaceTeamProperties.ACCESS_ALL;
        ViewerRank rank = ClientClaims.rankIn(teamId);
        return switch (access) {
            case MarketplaceTeamProperties.ACCESS_TEAM -> rank.isMemberOrBetter();
            case MarketplaceTeamProperties.ACCESS_ALLIES -> rank.isAllyOrBetter();
            default -> true;
        };
    }
}
