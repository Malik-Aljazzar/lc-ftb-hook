package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * Suppresses the 6 native FTB Chunks protection properties from the team
 * properties screen entirely - they're mod-native per-Region data now (see
 * plan A0/A1), edited only through the Region screens, so FTB's generic
 * screen would otherwise show dead controls that no longer do anything.
 * <p>
 * This mixin used to also override the Accept button's behavior (staying on
 * the settings screen instead of returning to the team screen); that's been
 * removed since Accept should behave exactly like vanilla FTB now that
 * protection settings live elsewhere.
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.MyTeamScreen$SettingsButton", remap = false)
public class MyTeamScreenSettingsButtonMixin {
    private static final Set<TeamProperty<?>> HIDDEN_PROPERTIES = Set.of(
            FTBChunksProperties.ALLOW_MOB_GRIEFING,
            FTBChunksProperties.ALLOW_EXPLOSIONS,
            FTBChunksProperties.ALLOW_PVP,
            FTBChunksProperties.BLOCK_INTERACT_MODE,
            FTBChunksProperties.BLOCK_EDIT_MODE,
            FTBChunksProperties.ENTITY_INTERACT_MODE,
            FTBChunksProperties.NONLIVING_ENTITY_ATTACK_MODE,
            // Not a region field (FTB only uses it on Fabric) - hidden as useless here.
            FTBChunksProperties.BLOCK_EDIT_AND_INTERACT_MODE,
            FTBChunksProperties.ALLOW_ALL_FAKE_PLAYERS,
            FTBChunksProperties.ALLOW_FAKE_PLAYERS_BY_ID
    );

    @Redirect(
            method = "lambda$new$2",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/api/property/TeamProperty;config(Ldev/ftb/mods/ftblibrary/config/ConfigGroup;Ldev/ftb/mods/ftbteams/api/property/TeamPropertyValue;)Ldev/ftb/mods/ftblibrary/config/ConfigValue;"
            ),
            remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static dev.ftb.mods.ftblibrary.config.ConfigValue<?> lcFtbHook$suppressProtectionProperties(
            TeamProperty key, ConfigGroup cfg, TeamPropertyValue value
    ) {
        if (HIDDEN_PROPERTIES.contains(key)) {
            return null;
        }
        return key.config(cfg, value);
    }
}
