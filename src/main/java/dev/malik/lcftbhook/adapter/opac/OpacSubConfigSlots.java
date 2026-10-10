package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.LCFtbHook;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps protection profiles onto one claim owner's OPAC sub-configs ("lcfh1",
 * "lcfh2", ...), all managed by this mod. A sub-config already holding the
 * profile is reused; otherwise an lcfh sub-config no chunk needs any more is
 * rewritten, and only then a new one created (OPAC caps sub-configs per player).
 */
final class OpacSubConfigSlots {
    static final String PREFIX = "lcfh";

    private static final Set<String> warned = new HashSet<>();

    private OpacSubConfigSlots() {
    }

    /** profile -> sub-config holding it; a profile left out had no slot left (limit reached). */
    static Map<OpacProtectionProfile, IPlayerConfigAPI> assign(IPlayerConfigManagerAPI manager, IPlayerConfigAPI owner,
                                                               Collection<OpacProtectionProfile> profiles) {
        Map<OpacProtectionProfile, IPlayerConfigAPI> assigned = new HashMap<>();
        List<IPlayerConfigAPI> free = new ArrayList<>(managedSubConfigs(owner));
        List<OpacProtectionProfile> missing = new ArrayList<>();
        for (OpacProtectionProfile profile : profiles) {
            IPlayerConfigAPI match = free.stream().filter(sub -> holds(manager, sub, profile)).findFirst().orElse(null);
            if (match != null) {
                free.remove(match);
                assigned.put(profile, match);
            } else {
                missing.add(profile);
            }
        }
        for (OpacProtectionProfile profile : missing) {
            IPlayerConfigAPI slot = !free.isEmpty() ? free.remove(0) : create(owner);
            if (slot != null) {
                write(manager, slot, profile);
                assigned.put(profile, slot);
            }
        }
        return assigned;
    }

    private static List<IPlayerConfigAPI> managedSubConfigs(IPlayerConfigAPI owner) {
        return owner.getSubConfigIds().stream()
                .filter(id -> id.startsWith(PREFIX))
                .map(owner::getSubConfig)
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean holds(IPlayerConfigManagerAPI manager, IPlayerConfigAPI sub, OpacProtectionProfile profile) {
        for (Map.Entry<String, Object> entry : profile.values().entrySet()) {
            IPlayerConfigOptionSpecAPI<?> option = option(manager, entry.getKey());
            if (option != null && !Objects.equals(sub.getEffective(option), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void write(IPlayerConfigManagerAPI manager, IPlayerConfigAPI sub, OpacProtectionProfile profile) {
        for (Map.Entry<String, Object> entry : profile.values().entrySet()) {
            IPlayerConfigOptionSpecAPI option = option(manager, entry.getKey());
            if (option == null) {
                continue;
            }
            IPlayerConfigAPI.SetResult result = sub.tryToSet(option, entry.getValue());
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) {
                warnOnce(entry.getKey(), "OPAC refused setting " + entry.getKey() + " in a sub-config (" + result
                        + ") - is it listed in opConfigurablePlayerConfigOptions of OPAC's server config?");
            }
        }
    }

    @Nullable
    private static IPlayerConfigAPI create(IPlayerConfigAPI owner) {
        for (int n = 1; n <= owner.getSubConfigLimit(); n++) {
            String id = PREFIX + n;
            if (!owner.subConfigExists(id)) {
                IPlayerConfigAPI created = owner.createSubConfig(id);
                if (created != null) {
                    return created;
                }
                break;
            }
        }
        warnOnce("limit:" + owner.getPlayerId(), "No OPAC sub-config left for " + owner.getPlayerId()
                + " - some of their chunks keep their previous protection. Raise playerSubConfigLimit in OPAC's server config.");
        return null;
    }

    @Nullable
    private static IPlayerConfigOptionSpecAPI<?> option(IPlayerConfigManagerAPI manager, String optionId) {
        IPlayerConfigOptionSpecAPI<?> option = manager.getOptionForId("playerConfig." + optionId);
        if (option == null) {
            warnOnce("missing:" + optionId, "OPAC has no player config option " + optionId + " - skipped.");
        }
        return option;
    }

    private static void warnOnce(String key, String message) {
        if (warned.add(key)) {
            LCFtbHook.LOGGER.warn(message);
        }
    }
}
