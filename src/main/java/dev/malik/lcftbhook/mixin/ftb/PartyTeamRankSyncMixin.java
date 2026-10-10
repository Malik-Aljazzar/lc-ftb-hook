package dev.malik.lcftbhook.mixin.ftb;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * {@code PartyTeam} gets loaded (and therefore Mixin-transformed) on a
 * background resource-reload worker thread very early during world join
 * (triggered indirectly via FTB Teams' command registration touching
 * related classes). A direct static reference from an injected method to
 * another lc_ftb_hook class made Sponge Mixin's late-transform reference
 * resolution intermittently throw {@code ClassNotFoundException} for a
 * class that demonstrably exists and loads fine everywhere else - crashing
 * world join entirely. Reflection avoids Mixin needing to resolve
 * {@code TeamChangeHooks} as a bytecode-level type reference at all.
 */
@Mixin(value = PartyTeam.class, remap = false)
public class PartyTeamRankSyncMixin {
    @Inject(method = "promote", at = @At("RETURN"), remap = false)
    private void lcFtbHook$syncLcRolesAfterPromote(
            ServerPlayer player,
            Collection<GameProfile> profiles,
            CallbackInfoReturnable<Integer> cir
    ) {
        syncLinkedLcTeam((Team) (Object) this);
    }

    @Inject(method = "demote", at = @At("RETURN"), remap = false)
    private void lcFtbHook$syncLcRolesAfterDemote(
            ServerPlayer player,
            Collection<GameProfile> profiles,
            CallbackInfoReturnable<Integer> cir
    ) {
        syncLinkedLcTeam((Team) (Object) this);
    }

    private static void syncLinkedLcTeam(Team team) {
        if (!team.isPartyTeam() || !team.isValid()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        try {
            Class<?> cls = Class.forName("dev.malik.lcftbhook.service.TeamChangeHooks");
            Method method = cls.getMethod("onTeamChanged", MinecraftServer.class, java.util.UUID.class);
            method.invoke(null, server, team.getTeamId());
        } catch (ReflectiveOperationException e) {
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error("Failed to run team-changed hook after rank change", e);
        }
    }
}
