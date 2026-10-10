package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Kind;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * One protection setting a region can hold - defined by the active claim
 * backend ({@link ProtectionFieldSpec}), priced by the protection price config
 * on the server or by the synced price on the client. The set of existing
 * properties is {@link #values()}, i.e. whatever {@link ProtectionCatalog}
 * currently holds for this side. Equality is by id.
 */
public final class ProtectionProperty {
    /**
     * All config prices are denominated per this many billable chunks, not
     * per single chunk - a config value of {@code V} means {@code V} tenths
     * of a copper... see {@code ProtectionPricing}'s `* chunks / PRICE_UNIT_CHUNKS`
     * formula: nothing is charged below 10 chunks for a price of 1, then 1
     * copper at 10-19 chunks etc., without fractional money anywhere.
     */
    public static final long PRICE_UNIT_CHUNKS = 10L;

    private final ProtectionFieldSpec spec;
    private final LongSupplier price;

    public ProtectionProperty(ProtectionFieldSpec spec, LongSupplier price) {
        this.spec = spec;
        this.price = price;
    }

    public String id() {
        return spec.id();
    }

    public ProtectionFieldSpec spec() {
        return spec;
    }

    public boolean isPrivacyMode() {
        return spec.kind() == Kind.ACCESS;
    }

    public boolean hasRole(Role role) {
        return spec.roles().contains(role);
    }

    /** One of the main protections - always shown; the rest only when set or expanded. */
    public boolean isPrimary() {
        return spec.primary();
    }

    public boolean isAccessListRelevant() {
        return spec.accessListRelevant();
    }

    /** "true" for toggles (allowed/unprotected), {@code PrivacyLevel.PUBLIC} name for access fields. */
    public String minimumSerialized() {
        return isPrivacyMode() ? PrivacyLevel.PUBLIC.name() : "true";
    }

    public boolean isAtMinimum(String serialized) {
        return minimumSerialized().equals(serialized);
    }

    public long configPrice() {
        return price.getAsLong();
    }

    /** The properties of the active claim backend on this side, in display order. */
    public static List<ProtectionProperty> values() {
        return ProtectionCatalog.current();
    }

    public static List<ProtectionProperty> withRole(Role role) {
        return values().stream().filter(property -> property.hasRole(role)).toList();
    }

    public static ProtectionProperty byId(String id) {
        for (ProtectionProperty property : values()) {
            if (property.id().equals(id)) {
                return property;
            }
        }
        throw new IllegalArgumentException("Unknown protection property id: " + id);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProtectionProperty property && property.id().equals(id());
    }

    @Override
    public int hashCode() {
        return id().hashCode();
    }

    @Override
    public String toString() {
        return id();
    }
}
