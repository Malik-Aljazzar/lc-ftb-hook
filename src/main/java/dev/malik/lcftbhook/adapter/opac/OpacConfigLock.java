package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.LCFtbHook;
import net.neoforged.neoforge.common.ModConfigSpec;
import xaero.pac.common.server.config.ServerConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Protection is paid per region, so players must not change the OPAC options
 * we manage themselves. OPAC only honours per-player values of options listed
 * as op- or player-configurable; ours are moved to the op-configurable list
 * (settable per player, but only by ops - and by us through the API).
 * <p>
 * This touches OPAC's config class directly (not its API): if a future OPAC
 * changes it, the lock is skipped with a warning telling admins what to set.
 */
final class OpacConfigLock {
    private OpacConfigLock() {
    }

    static void apply() {
        Set<String> managed = managedOptionIds();
        try {
            boolean changed = update(ServerConfig.CONFIG.opConfigurablePlayerConfigOptions, managed, true);
            changed |= update(ServerConfig.CONFIG.playerConfigurablePlayerConfigOptions, managed, false);
            if (changed) {
                ServerConfig.CONFIG.opConfigurablePlayerConfigOptions.save();
                LCFtbHook.LOGGER.info("Moved {} OPAC protection options to opConfigurablePlayerConfigOptions (managed by region settings)", managed.size());
            }
        } catch (RuntimeException | LinkageError e) {
            LCFtbHook.LOGGER.warn("Could not update OPAC's server config - please move these options from "
                    + "playerConfigurablePlayerConfigOptions to opConfigurablePlayerConfigOptions yourself: {}", managed, e);
        }
    }

    private static Set<String> managedOptionIds() {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(OpacProtectionProfile.PROTECT_CLAIMED_CHUNKS);
        ids.add(OpacProtectionProfile.FULL_ACCESS);
        OpacProtectionFields.all().forEach(field -> ids.add(field.optionId()));
        return ids;
    }

    /** Adds ({@code present}) or removes the managed ids; true if the list changed. */
    private static boolean update(ModConfigSpec.ConfigValue<List<? extends String>> value, Set<String> managed, boolean present) {
        List<String> current = new ArrayList<>(value.get());
        boolean changed = present ? addMissing(current, managed) : current.removeAll(managed);
        if (changed) {
            value.set(current);
        }
        return changed;
    }

    private static boolean addMissing(List<String> list, Set<String> ids) {
        boolean changed = false;
        for (String id : ids) {
            if (!list.contains(id)) {
                list.add(id);
                changed = true;
            }
        }
        return changed;
    }
}
