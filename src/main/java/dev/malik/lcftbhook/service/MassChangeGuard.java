package dev.malik.lcftbhook.service;

/**
 * Teams, parties and members disappear one at a time in normal play. Many of
 * them vanishing at once almost always means a mod didn't load (or report)
 * its data - acting on it (purging, releasing claims) would destroy that
 * data for good. Callers refuse such a change and log an error instead.
 */
public final class MassChangeGuard {
    private MassChangeGuard() {
    }

    /** More than 2 gone and more than a fifth of all known ones gone at once. */
    public static boolean looksLikeIncompleteLoad(int missing, int total) {
        return missing > 2 && missing * 5 > total;
    }
}
