package dev.malik.lcftbhook.data;

import java.util.List;

/**
 * Per-region choice of when that region's force-loaded chunks are actually
 * loaded - and, since billing follows loading, when they're charged (see
 * {@code service.ForceLoadBilling}). Declared cheapest first: every moment
 * {@link #TEAM_ONLINE} loads, {@link #ANY_PLAYER} loads too, and
 * {@link #ALWAYS} loads at every moment either of them does.
 */
public enum ForceLoadMode {
    /** Loaded (and charged) only while at least one of the team's own members is online. */
    TEAM_ONLINE("team_online"),
    /** Loaded (and charged) while at least one player is online anywhere on the server. */
    ANY_PLAYER("any_player"),
    /** Always loaded and charged every period, even while nobody is on the server. */
    ALWAYS("always");

    public static final ForceLoadMode DEFAULT = TEAM_ONLINE;

    private final String id;

    ForceLoadMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** {@code fallback} if {@code id} names no mode (unknown/corrupt save or packet value). */
    public static ForceLoadMode byId(String id, ForceLoadMode fallback) {
        for (ForceLoadMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return fallback;
    }

    /** This mode and every cheaper one, most expensive first - the fallback order when this mode is disabled. */
    public List<ForceLoadMode> thisAndCheaper() {
        List<ForceLoadMode> modes = new java.util.ArrayList<>();
        for (int i = ordinal(); i >= 0; i--) {
            modes.add(values()[i]);
        }
        return List.copyOf(modes);
    }

    /**
     * The mode that applies when this one was chosen: itself if enabled,
     * else the next cheaper enabled one - never a more expensive one. Empty
     * if there is none (the region's force-loads rest).
     */
    public java.util.Optional<ForceLoadMode> effective(java.util.function.Predicate<ForceLoadMode> enabled) {
        return thisAndCheaper().stream().filter(enabled).findFirst();
    }
}
