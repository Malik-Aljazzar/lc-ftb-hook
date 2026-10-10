package dev.malik.lcftbhook.mixin.lc;

import dev.malik.lcftbhook.adapter.lightmanscurrency.LcServerAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grants any vanilla-OP player (permission level 2+) direct access to
 * {@link LcServerAccount}'s account specifically - and ONLY that one -
 * without needing Lightman's Currency's own separate Admin Mode toggle.
 * <p>
 * Confirmed via decompile that LC's Admin Mode is the wrong tool for this:
 * {@code PlayerBankReference.allowedAccess(Player)} (the exact method
 * {@code BankDataCache.setSelectedAccount}/{@code getSelectedAccount}
 * enforce before letting a player select/keep an account through the ATM)
 * treats admin mode as blanket access to EVERY player's personal account,
 * not just one - there is no way to grant "access to this one account" via
 * LC's own admin mode without also exposing every other player's private
 * balance. Injecting here instead widens the check only for the one
 * PlayerReference id matching {@link LcServerAccount#SERVER_ACCOUNT_ID} -
 * every other PlayerBankReference (i.e. every real player's own account)
 * falls through to the original, unmodified check.
 */
@Mixin(value = PlayerBankReference.class, remap = false)
public class PlayerBankReferenceOpAccessMixin {
    @Inject(method = "allowedAccess(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$allowOpsForServerAccount(Player player, CallbackInfoReturnable<Boolean> cir) {
        PlayerBankReference self = (PlayerBankReference) (Object) this;
        // Guarded: if the check fails, LC's own access rule stands.
        if (dev.malik.lcftbhook.util.Guard.call("LC server account op access", () -> player.hasPermissions(2)
                && LcServerAccount.SERVER_ACCOUNT_ID.equals(self.getPlayer().id), false)) {
            cir.setReturnValue(true);
        }
    }
}
