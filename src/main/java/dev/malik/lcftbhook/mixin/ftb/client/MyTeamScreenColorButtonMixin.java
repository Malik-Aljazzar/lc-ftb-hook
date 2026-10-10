package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Teams' own team-color picker button (the small square swatch top-left
 * of {@code MyTeamScreen}, third anonymous class declared in that file - see
 * its decompiled source, {@code this.colorButton = new 3(...)}) had no rank
 * check at all - any member could open the color picker and change the
 * whole team's color, despite having no other management rights anywhere
 * else in this mod. Left fully visible for everyone (so a member can still
 * SEE the current color) - only the click callback is neutered for a
 * non-officer, via {@link dev.ftb.mods.ftblibrary.ui.SimpleButton#setConsumer},
 * rather than hiding the button (which risks the same
 * {@code MyTeamScreenMixin} toolbar-anchoring issue documented there).
 * <p>
 * The rank check is inlined here rather than calling a shared lc_ftb_hook
 * helper for the same reason documented on
 * {@link EditConfigScreenConfigEntryButtonMixin} - this mixin, like that one,
 * only applies lazily the first time this screen opens, and referencing our
 * own mod's classes from here has previously crashed with
 * {@code ClassNotFoundException} at that point even though they load fine
 * everywhere else. FTB Teams'/FTB Library's own API classes are safe.
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.MyTeamScreen$3", remap = false)
public class MyTeamScreenColorButtonMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void lcFtbHook$lockColorButton(CallbackInfo ci) {
        if (!lcFtbHook$isOfficerOrBetter()) {
            ((dev.ftb.mods.ftblibrary.ui.SimpleButton) (Object) this).setConsumer((button, mouseButton) -> {
            });
        }
    }

    private static boolean lcFtbHook$isOfficerOrBetter() {
        if (!FTBTeamsAPI.api().isClientManagerLoaded() || Minecraft.getInstance().player == null) {
            return false;
        }
        Team selfTeam = FTBTeamsAPI.api().getClientManager().selfTeam();
        return selfTeam != null && selfTeam.getRankForPlayer(Minecraft.getInstance().player.getUUID()).isOfficerOrBetter();
    }
}
