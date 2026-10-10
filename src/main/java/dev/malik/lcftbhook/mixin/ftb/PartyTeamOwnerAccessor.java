package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbteams.data.PartyTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** Only for {@code FtbPartyOwners}' load-time repair of a missing party owner - FTB has no API to set it. */
@Mixin(value = PartyTeam.class, remap = false)
public interface PartyTeamOwnerAccessor {
    @Accessor(value = "owner", remap = false)
    void lcFtbHook$setOwner(UUID owner);
}
