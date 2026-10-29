package dev.malik.lcftbhook.mixin.numismatics;

import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.malik.lcftbhook.adapter.numismatics.NumismaticsCurrencyPort;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets any op (permission level 2+) open this mod's server account at a
 * Bank Terminal with a card bound via {@code /lc_ftb_hook server_account card}
 * - the Numismatics counterpart of {@code PlayerBankReferenceOpAccessMixin}.
 * <p>
 * The server account is a {@code PLAYER} account keyed by a non-player id,
 * which Numismatics authorizes for nobody (confirmed via javap:
 * {@code isAuthorized(UUID)} = own id, or the trust list on trust-list
 * types only). A trust list of ops would go stale on {@code /deop}; checking
 * the permission live here can't. Only that one account id is affected -
 * every other account falls through to Numismatics' own check.
 */
@Mixin(value = BankAccount.class, remap = false)
public class BankAccountServerOpAccessMixin {
    @Inject(method = "isAuthorized(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$allowOpsForServerAccount(Player player, CallbackInfoReturnable<Boolean> cir) {
        BankAccount self = (BankAccount) (Object) this;
        // Guarded: if the check fails, Numismatics' own access rule stands.
        if (dev.malik.lcftbhook.util.Guard.call("Numismatics server account op access", () -> player != null
                && !player.level().isClientSide()
                && NumismaticsCurrencyPort.isServerAccount(self.id)
                && player.hasPermissions(2), false)) {
            cir.setReturnValue(true);
        }
    }
}
