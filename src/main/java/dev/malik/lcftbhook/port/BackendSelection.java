package dev.malik.lcftbhook.port;

import dev.malik.lcftbhook.LCFtbHook;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared AUTO/explicit-id resolution logic for every port's backend
 * candidate list - see {@link BackendCandidate}'s javadoc for the overall
 * design. Never fails to start over a backend-selection problem: an unknown
 * or currently-not-installed explicit config value logs a warning and falls
 * back to AUTO instead of throwing, since a broken config value here would
 * otherwise take down server startup entirely over what's usually a typo.
 */
final class BackendSelection {
    private BackendSelection() {
    }

    static <T> T select(String configuredId, List<BackendCandidate<T>> candidates, String portName) {
        if (!"AUTO".equalsIgnoreCase(configuredId)) {
            T explicit = selectExplicit(configuredId, candidates, portName);
            if (explicit != null) {
                return explicit;
            }
        }
        return selectAuto(candidates, portName);
    }

    private static <T> T selectExplicit(String configuredId, List<BackendCandidate<T>> candidates, String portName) {
        BackendCandidate<T> match = candidates.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(configuredId))
                .findFirst()
                .orElse(null);
        if (match == null) {
            LCFtbHook.LOGGER.warn("Unknown {} backend '{}' in config - falling back to AUTO selection. Valid values: {}",
                    portName, configuredId, validIds(candidates));
            return null;
        }
        if (!match.isAvailable()) {
            LCFtbHook.LOGGER.warn("{} backend explicitly set to '{}' but that mod isn't installed - falling back to AUTO selection.",
                    portName, configuredId);
            return null;
        }
        LCFtbHook.LOGGER.info("Using {} backend {} (explicitly configured)", portName, match.id());
        return match.factory().get();
    }

    private static <T> T selectAuto(List<BackendCandidate<T>> candidates, String portName) {
        for (BackendCandidate<T> candidate : candidates) {
            if (candidate.isAvailable()) {
                LCFtbHook.LOGGER.info("Using {} backend {} (AUTO: highest-priority installed)", portName, candidate.id());
                return candidate.factory().get();
            }
        }
        throw new IllegalStateException("No " + portName + " backend mod installed. Need one of: " + validIds(candidates));
    }

    private static <T> String validIds(List<BackendCandidate<T>> candidates) {
        return candidates.stream().map(BackendCandidate::id).collect(Collectors.joining(", "));
    }
}
