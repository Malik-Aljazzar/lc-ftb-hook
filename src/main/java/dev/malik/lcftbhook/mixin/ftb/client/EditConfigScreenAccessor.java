package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen", remap = false)
public interface EditConfigScreenAccessor {
    @Accessor(value = "group", remap = false)
    ConfigGroup lcFtbHook$getGroup();

    @Accessor(value = "changed", remap = false)
    boolean lcFtbHook$getChanged();
}
