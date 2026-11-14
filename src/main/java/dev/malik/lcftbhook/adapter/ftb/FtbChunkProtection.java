package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionResolution;

import javax.annotation.Nullable;

/**
 * Whether explosions / mob griefing may change terrain in one FTB claimed
 * chunk. The single source of truth for both FTB's own checks (redirected to
 * it by {@code ClaimedChunkRegionProtectionMixin}) and {@link FtbBlockProtection}.
 * A server team's chunk keeps FTB's own team setting; every other chunk reads
 * its Region's value, loosened by a private owner's override.
 */
public final class FtbChunkProtection {
    private FtbChunkProtection() {
    }

    /** The Region's answer, or null for a server team's chunk (FTB's own setting applies). */
    @Nullable
    public static Boolean regionAllowsExplosions(ClaimedChunk chunk) {
        return regionAllows(chunk, FtbProtectionFields.ALLOW_EXPLOSIONS);
    }

    /** The Region's answer, or null for a server team's chunk (FTB's own setting applies). */
    @Nullable
    public static Boolean regionAllowsMobGriefing(ClaimedChunk chunk) {
        return regionAllows(chunk, FtbProtectionFields.ALLOW_MOB_GRIEFING);
    }

    /** What FTB enforces for explosions in this chunk, Region or server team setting. */
    public static boolean allowsExplosions(ClaimedChunk chunk) {
        Boolean region = regionAllowsExplosions(chunk);
        return region != null ? region : chunk.getTeamData().canExplosionsDamageTerrain();
    }

    /** What FTB enforces for mob griefing in this chunk, Region or server team setting. */
    public static boolean allowsMobGriefing(ClaimedChunk chunk) {
        Boolean region = regionAllowsMobGriefing(chunk);
        return region != null ? region : chunk.getTeamData().allowMobGriefing();
    }

    @Nullable
    private static Boolean regionAllows(ClaimedChunk chunk, String propertyId) {
        if (isServerTeamChunk(chunk)) {
            return null;
        }
        Region region = FtbRegionLookup.resolveRegion(chunk);
        ChunkOwnership ownership = FtbRegionLookup.resolveOwnership(chunk);
        return ProtectionResolution.isAtMinimum(region, ownership, ProtectionProperty.byId(propertyId));
    }

    private static boolean isServerTeamChunk(ClaimedChunk chunk) {
        Team team = chunk.getTeamData().getTeam();
        return team != null && team.isServerTeam();
    }
}
