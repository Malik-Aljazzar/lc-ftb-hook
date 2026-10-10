package dev.malik.lcftbhook.port.protection;

import java.util.ArrayList;
import java.util.List;

/**
 * All protection fields one claim backend offers, in display order.
 *
 * @param id                 config section / price key namespace, e.g. "ftb", "opac"
 * @param displayName        for config comments and logs
 * @param mainDismantleOrder the main (primary) fields' ids in the order they are dropped
 *                           when upkeep can't be paid, first = dropped first
 */
public record ProtectionFieldSet(String id, String displayName, List<ProtectionFieldSpec> fields, List<String> mainDismantleOrder) {
    public ProtectionFieldSet {
        fields = List.copyOf(fields);
        mainDismantleOrder = List.copyOf(mainDismantleOrder);
    }

    /**
     * Default drop order when upkeep can't be paid: the advanced fields first
     * (in display order - the least noticed protections go first), then the
     * main ones in {@link #mainDismantleOrder}, then any main field that list
     * forgot.
     */
    public List<String> defaultDismantleOrder() {
        List<String> order = new ArrayList<>();
        fields.stream().filter(field -> !field.primary()).forEach(field -> order.add(field.id()));
        mainDismantleOrder.stream().filter(id -> !order.contains(id)).forEach(order::add);
        fields.stream().map(ProtectionFieldSpec::id).filter(id -> !order.contains(id)).forEach(order::add);
        return List.copyOf(order);
    }
}
