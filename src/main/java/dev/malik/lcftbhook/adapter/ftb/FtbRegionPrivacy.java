package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.util.Guard;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * FTB Chunks' privacy properties (block edit, block interact, entity
 * interact, non-living entity attack) are per-Region fields of this mod. This
 * is the one place that turns a Region's value into the {@link PrivacyMode}
 * FTB's own checks compare against - for {@code canPlayerUse} (via the region
 * context) and for the checks that read the team property straight from a
 * claimed chunk's team (fire spread, fluid flow, pistons across claims).
 */
public final class FtbRegionPrivacy {
    private static final Set<String> REPORTED_CALLERS = ConcurrentHashMap.newKeySet();

    private FtbRegionPrivacy() {
    }

    /** The Region field behind an FTB privacy property, or null if it isn't one (claim visibility, location, ...). */
    @Nullable
    public static ProtectionProperty regionPropertyFor(TeamProperty<?> property) {
        String path = property.getId().getPath();
        for (ProtectionProperty candidate : ProtectionProperty.values()) {
            if (candidate.isPrivacyMode() && candidate.id().equals(path)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * FTB's native rank check only knows its 3-value {@link PrivacyMode};
     * {@code TEAM} and {@code PRIVATE} both map to FTB's "whole team"
     * ({@code PRIVATE}) - narrowing {@code PRIVATE} further to the owner or
     * officers is done by the region privacy mixin on top.
     */
    public static PrivacyMode toFtbMode(PrivacyLevel level) {
        return switch (level) {
            case PUBLIC -> PrivacyMode.PUBLIC;
            case ALLIES -> PrivacyMode.ALLIES;
            case TEAM, PRIVATE -> PrivacyMode.PRIVATE;
        };
    }

    /**
     * What {@code chunk.getTeamData().getTeam().getProperty(property)} means
     * for this one chunk: its Region's value, loosened by a private owner's
     * override. Server team chunks, wilderness and non-region properties keep
     * {@code teamSetting} (FTB's own team property). Guarded: on failure FTB's
     * own setting applies, as it did before.
     */
    public static Object propertyFor(@Nullable ClaimedChunk chunk, TeamProperty<?> property, Supplier<Object> teamSetting) {
        PrivacyMode region = Guard.call("FTB region privacy", () -> regionMode(chunk, property), null);
        return region != null ? region : teamSetting.get();
    }

    @Nullable
    private static PrivacyMode regionMode(@Nullable ClaimedChunk chunk, TeamProperty<?> property) {
        if (chunk == null || !(property instanceof PrivacyProperty)) {
            return null;
        }
        Team team = chunk.getTeamData().getTeam();
        ProtectionProperty regionProperty = regionPropertyFor(property);
        if (team == null || team.isServerTeam() || regionProperty == null) {
            return null;
        }
        String value = ProtectionResolution.effectiveValue(
                FtbRegionLookup.resolveRegion(chunk), FtbRegionLookup.resolveOwnership(chunk), regionProperty);
        return toFtbMode(PrivacyLevel.valueOf(value));
    }

    /**
     * A region privacy field was asked through {@code canPlayerUse} with no
     * chunk context - the caller asked FTB directly instead of through
     * {@code shouldPreventInteraction}, so the Region can't be known and the
     * field reads as Public. Logged once per calling method, so a new FTB
     * Chunks check (or another mod asking FTB's API) that bypasses region
     * settings shows up in the log instead of silently letting everyone in.
     */
    public static void reportMissingContext(TeamProperty<?> property) {
        String caller = StackWalker.getInstance().walk(frames -> frames
                .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                .filter(name -> !name.startsWith("dev.malik.lcftbhook.")
                        && !name.startsWith("dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl.")
                        && !name.startsWith("java.")
                        && !name.startsWith("jdk."))
                .findFirst()
                .orElse("unknown"));
        if (REPORTED_CALLERS.add(caller)) {
            LCFtbHook.LOGGER.warn("{} checked FTB Chunks' '{}' without saying which chunk it is about, so region "
                    + "settings can't apply to it there and it is treated as Public. Please report this to LC FTB Hook "
                    + "(logged once per caller).", caller, property.getId());
        }
    }
}
