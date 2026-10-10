package dev.malik.lcftbhook.mixin.lc;

import dev.malik.lcftbhook.adapter.lightmanscurrency.LcTeamLinks;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Team.class, remap = false)
public class TeamRoleChangeMixin {
    @Inject(method = "changePromoteMember", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$blockManagedPromote(Player player, PlayerReference target, CallbackInfo ci) {
        if (blockRoleChange(player, ((Team) (Object) this).getID())) {
            ci.cancel();
        }
    }

    @Inject(method = "changeDemoteMember", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$blockManagedDemote(Player player, PlayerReference target, CallbackInfo ci) {
        if (blockRoleChange(player, ((Team) (Object) this).getID())) {
            ci.cancel();
        }
    }

    @Inject(method = "changeOwner", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$blockManagedOwnerChange(Player player, PlayerReference target, CallbackInfo ci) {
        if (blockRoleChange(player, ((Team) (Object) this).getID())) {
            ci.cancel();
        }
    }

    /** Guarded: if the check fails, the role change is blocked - a managed team's roles must stay in sync. */
    private static boolean blockRoleChange(Player player, long lcTeamId) {
        return dev.malik.lcftbhook.util.Guard.call("LC team role change check", () -> checkRoleChange(player, lcTeamId), true);
    }

    private static boolean checkRoleChange(Player player, long lcTeamId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !LcTeamLinks.isManaged(server, lcTeamId)) {
            return false;
        }

        player.displayClientMessage(Component.translatable("message.lc_ftb_hook.team_role_change_denied"), true);
        return true;
    }
}
