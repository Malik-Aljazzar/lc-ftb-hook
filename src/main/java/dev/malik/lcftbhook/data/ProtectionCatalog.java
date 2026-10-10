package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.config.ProtectionPriceConfig;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSet;
import net.neoforged.fml.util.thread.EffectiveSide;

import java.util.List;

/**
 * Which protection properties exist right now. On the server they come from
 * the active claim backend, priced by {@link ProtectionPriceConfig}; on the
 * client they're whatever the server last synced (with its prices). In
 * singleplayer both live in one JVM, so the side is picked per thread.
 */
public final class ProtectionCatalog {
    private static volatile List<ProtectionProperty> clientFields = List.of();
    private static volatile ProtectionFieldSet serverSet;
    private static volatile List<ProtectionProperty> serverFields = List.of();

    private ProtectionCatalog() {
    }

    public static List<ProtectionProperty> current() {
        return EffectiveSide.get().isClient() ? clientFields : serverFields();
    }

    public static void setClientFields(List<ProtectionProperty> fields) {
        clientFields = List.copyOf(fields);
    }

    private static List<ProtectionProperty> serverFields() {
        ProtectionFieldSet set = Backends.claim().protectionFields();
        if (set != serverSet) {
            serverFields = set.fields().stream()
                    .map(spec -> new ProtectionProperty(spec, () -> ProtectionPriceConfig.price(set.id(), spec)))
                    .toList();
            serverSet = set;
        }
        return serverFields;
    }
}
