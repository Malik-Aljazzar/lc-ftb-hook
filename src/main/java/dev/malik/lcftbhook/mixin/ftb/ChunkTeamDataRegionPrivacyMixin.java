package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.service.RegionEnforcementContext;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FTB's privacy-mode protections (block edit, block interact, entity
 * interact, non-living entity attack - see {@code FtbProtectionFields}) are
 * mod-native per-Region data now, not FTB {@code TeamProperty} values -
 * stored as {@link PrivacyLevel}, a 4-tier enum of our own (FTB's own
 * {@link PrivacyMode} only has 3 values and can't be extended). Read the
 * resolved Region's own stored value for every privacy property whose id is a
 * region field; any other FTB property (e.g. claim visibility) passes through
 * unchanged.
 * <p>
 * FTB's native {@code canPlayerUse} rank check only understands its own
 * 3-value {@link PrivacyMode}, so {@code PUBLIC}/{@code ALLIES}/{@code TEAM}
 * delegate to it directly ({@code TEAM} feeding it {@code PrivacyMode.PRIVATE}
 * - FTB's own "whole team, any rank" concept, which is exactly what
 * {@code TEAM} means here). {@code PRIVATE} has no FTB equivalent at all
 * (FTB has neither "one specific player" nor "officers only"), so its base
 * result is computed here instead of trusted from FTB: the chunk's individual
 * marketplace owner if it has one, otherwise this team's officers/owner only.
 * <p>
 * On top of that, a private chunk owner's custom whitelist/blacklist (see
 * {@link PlayerAccessList}) is applied on top of whichever base result was
 * reached above - a whitelist grants the listed players access even if they
 * wouldn't otherwise qualify, a blacklist denies the listed players even if
 * they would. This is gated on the REGION's own baseline supporting
 * protection at all, not on the chunk's own effective value - so a chunk
 * explicitly loosened to Public can still carry a blacklist (whitelist is
 * simply a no-op there, since Public already grants everyone access).
 * <p>
 * Finally, the owning team's officers/owner always retain access to a
 * privately-sold chunk within their own land regardless of anything above -
 * even an explicit blacklist entry against them - since the land legally
 * stays the team's territory.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl", remap = false)
public abstract class ChunkTeamDataRegionPrivacyMixin {
    @Redirect(
            method = "canPlayerUse",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"
            ),
            remap = false
    )
    private Object lcFtbHook$useRegionProperty(Team team, TeamProperty<?> property) {
        // Guarded: if our lookup fails, FTB's own team property applies.
        return dev.malik.lcftbhook.util.Guard.call("FTB privacy check", () -> lcFtbHook$regionPropertyOr(team, property), team.getProperty(property));
    }

    private static Object lcFtbHook$regionPropertyOr(Team team, TeamProperty<?> property) {
        if (team.isServerTeam()) {
            return team.getProperty(property); // server teams are protected by FTB's own settings, not regions
        }
        if (property instanceof PrivacyProperty) {
            ProtectionProperty regionProperty = regionPropertyFor(property);
            if (regionProperty != null) {
                PrivacyLevel level = PrivacyLevel.valueOf(effectiveValue(regionProperty));
                return switch (level) {
                    case PUBLIC -> PrivacyMode.PUBLIC;
                    case ALLIES -> PrivacyMode.ALLIES;
                    // TEAM and PRIVATE both feed FTB's own "whole team"
                    // check here - PRIVATE's further narrowing (to just the
                    // owner, or to officers/owner) happens below instead,
                    // since FTB has no native concept for either.
                    case TEAM, PRIVATE -> PrivacyMode.PRIVATE;
                };
            }
        }
        return team.getProperty(property);
    }

    /**
     * FTB's two fake-player switches (all fake players count as allies /
     * fake players acting under an allowed player's UUID) are region fields
     * too - read the chunk's effective value instead of the team property.
     */
    @Redirect(
            method = "canFakePlayerUse",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"
            ),
            remap = false
    )
    private Object lcFtbHook$useRegionFakePlayerSetting(Team team, TeamProperty<?> property) {
        return dev.malik.lcftbhook.util.Guard.call("FTB fake player check", () -> lcFtbHook$fakePlayerSettingOr(team, property), team.getProperty(property));
    }

    private static Object lcFtbHook$fakePlayerSettingOr(Team team, TeamProperty<?> property) {
        if (team.isServerTeam()) {
            return team.getProperty(property);
        }
        String id = property.getId().getPath();
        for (ProtectionProperty candidate : ProtectionProperty.values()) {
            if (!candidate.isPrivacyMode() && candidate.id().equals(id)) {
                return Boolean.valueOf(effectiveValue(candidate));
            }
        }
        return team.getProperty(property);
    }

    @Inject(method = "canPlayerUse", at = @At("RETURN"), cancellable = true, remap = false)
    private void lcFtbHook$applyPrivacyOverrides(ServerPlayer player, PrivacyProperty property, CallbackInfoReturnable<Boolean> cir) {
        // Guarded: if our override fails, FTB's own answer stands.
        dev.malik.lcftbhook.util.Guard.run("FTB privacy override", () -> lcFtbHook$overridePrivacy(player, property, cir));
    }

    private void lcFtbHook$overridePrivacy(ServerPlayer player, PrivacyProperty property, CallbackInfoReturnable<Boolean> cir) {
        Team owner = ((dev.ftb.mods.ftbchunks.api.ChunkTeamData) (Object) this).getTeam();
        if (owner == null || owner.isServerTeam()) {
            return;
        }
        ProtectionProperty regionProperty = regionPropertyFor(property);
        if (regionProperty == null) {
            return;
        }
        RegionEnforcementContext.Resolved resolved = RegionEnforcementContext.get();
        Region region = resolved != null ? resolved.region() : Region.createDefault();
        ChunkOwnership ownership = resolved != null ? resolved.ownership() : ChunkOwnership.EMPTY;

        // Gated on the REGION's own baseline, not the chunk's own effective
        // value - if the region doesn't support protection here at all,
        // there's no override (and no whitelist/blacklist) possible for this
        // chunk regardless (matches ChunkOverrideScreen's canOverride()). But
        // if the region DOES support it, a whitelist/blacklist still applies
        // even while the chunk's own effective value is Public - a private
        // owner can loosen their chunk to Public while still blacklisting a
        // few specific players ("open to everyone except these players");
        // a whitelist is simply a no-op there since nobody needs adding.
        if (regionProperty.isAtMinimum(region.propertyValue(regionProperty))) {
            return;
        }

        PrivacyLevel level = PrivacyLevel.valueOf(ProtectionResolution.effectiveValue(region, ownership, regionProperty));
        boolean base = cir.getReturnValue();
        if (level == PrivacyLevel.PRIVATE) {
            if (!ownership.isStateOwned()) {
                base = player.getUUID().equals(ownership.privateOwner());
            } else {
                Team team = ((ChunkTeamData) (Object) this).getTeam();
                base = team != null && team.getRankForPlayer(player.getUUID()).isOfficerOrBetter();
            }
        } else if (!ownership.isStateOwned() && player.getUUID().equals(ownership.privateOwner())) {
            // Allies/Team tier on a PRIVATELY-owned chunk: the vanilla FTB
            // check that produced `base` above only asks "is this player an
            // ally of / a member of the LAND-owning team" - which says
            // nothing about whether they're this one chunk's own private
            // owner. A private owner bought the chunk off some OTHER team's
            // marketplace, so they're very often neither an ally nor a
            // member of that team, and were getting locked out of their own
            // chunk by their own Allies/Team setting. The owner always has
            // access to their own chunk regardless of which tier is in
            // effect (matches the PRIVATE tier's owner-bypass above) - these
            // tiers describe who ELSE gets let in beyond the owner, not a
            // condition the owner themselves has to separately satisfy.
            base = true;
        }

        PlayerAccessList list = ownership.accessLists().get(regionProperty.id());
        boolean result = list != null ? list.apply(base, player.getUUID()) : base;

        // The team's own officers/owner always retain access to a privately
        // sold chunk within their own land, no matter what the private
        // owner has configured for it - even an explicit blacklist entry
        // against them. The land legally stays the team's territory; this
        // is their ultimate administrative authority over it, not something
        // a private buyer can opt them out of.
        if (!result && !ownership.isStateOwned()) {
            Team team = ((ChunkTeamData) (Object) this).getTeam();
            if (team != null && team.getRankForPlayer(player.getUUID()).isOfficerOrBetter()) {
                result = true;
            }
        }

        cir.setReturnValue(result);
    }

    private static String effectiveValue(ProtectionProperty regionProperty) {
        RegionEnforcementContext.Resolved resolved = RegionEnforcementContext.get();
        Region region = resolved != null ? resolved.region() : Region.createDefault();
        ChunkOwnership ownership = resolved != null ? resolved.ownership() : ChunkOwnership.EMPTY;
        return ProtectionResolution.effectiveValue(region, ownership, regionProperty);
    }

    private static ProtectionProperty regionPropertyFor(TeamProperty<?> property) {
        String path = property.getId().getPath();
        for (ProtectionProperty candidate : ProtectionProperty.values()) {
            if (candidate.isPrivacyMode() && candidate.id().equals(path)) {
                return candidate;
            }
        }
        return null;
    }
}
