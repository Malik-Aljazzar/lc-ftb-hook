package dev.malik.lcftbhook.mixinplugin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skips every mixin whose target class belongs to a mod that isn't
 * installed (decided by the target's package), so e.g. a server without
 * Lightman's Currency never even tries to apply mixins into LC classes.
 * Uses {@link LoadingModList} - the regular {@code ModList} isn't available
 * yet while mixin configs are being prepared.
 */
public final class BackendMixinPlugin implements IMixinConfigPlugin {
    /** Target class package prefix -> the mod that provides it. */
    private static final Map<String, String> MOD_BY_TARGET_PACKAGE = Map.of(
            "dev.ftb.mods.ftbchunks.", "ftbchunks",
            "dev.ftb.mods.ftbteams.", "ftbteams",
            "dev.ftb.mods.ftblibrary.", "ftblibrary",
            "io.github.lightman314.lightmanscurrency.", "lightmanscurrency",
            "xaero.pac.", "openpartiesandclaims",
            "xaero.map.", "xaeroworldmap",
            "dev.satherov.ftbxaerocompat.", "ftbxaerocompat",
            "dev.ithundxr.createnumismatics.", "numismatics"
    );

    /** Applies a mixin only if the mod owning its target is installed; unknown targets always apply. */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> entry : MOD_BY_TARGET_PACKAGE.entrySet()) {
            if (targetClassName.startsWith(entry.getKey())) {
                return isModLoaded(entry.getValue());
            }
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        LoadingModList mods = LoadingModList.get();
        return mods != null && mods.getModFileById(modId) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
