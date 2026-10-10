package dev.malik.lcftbhook.service;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * What a claim backend should do with a claim/unclaim/force-load request
 * after this mod's rules ran - backend-neutral, each claim adapter maps it
 * onto its own result type (FTB Chunks' {@code ClaimResult}, OPAC's claim
 * action override).
 *
 * @param resultId stable id of a denial (a translation key), for backends
 *                 that group results by id; null unless denied
 * @param message  what the player should see for a denial; null otherwise
 */
public record ClaimDecision(Kind kind, @Nullable String resultId, @Nullable Component message) {
    public enum Kind {
        /** Let the backend perform the action normally. */
        ALLOW,
        /** Refuse the action. */
        DENY,
        /** This mod took care of the request itself (e.g. queued it) - cancel the backend's own action, but report success. */
        HANDLED
    }

    public static final ClaimDecision ALLOW = new ClaimDecision(Kind.ALLOW, null, null);
    public static final ClaimDecision HANDLED = new ClaimDecision(Kind.HANDLED, null, null);

    /** Fallback when our own check failed: refuse, so nothing changes without being checked/paid. */
    public static ClaimDecision failedCheck() {
        return deny("message.lc_ftb_hook.internal_error_denied");
    }

    public static ClaimDecision deny(String translationKey) {
        return new ClaimDecision(Kind.DENY, translationKey, Component.translatable(translationKey));
    }

    public static ClaimDecision deny(String resultId, Component message) {
        return new ClaimDecision(Kind.DENY, resultId, message);
    }
}
