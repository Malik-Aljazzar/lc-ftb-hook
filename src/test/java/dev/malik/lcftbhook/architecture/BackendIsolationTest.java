package dev.malik.lcftbhook.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces that backend-specific mod APIs (FTB Teams/Chunks/Library,
 * Lightman's Currency, OPAC, Numismatics, Xaero) are only referenced from
 * their own zone packages - see ARCHITECTURE.md. Everything else must go
 * through the ports, otherwise the mod crashes with NoClassDefFoundError the
 * moment that code runs on a server without that particular mod installed.
 * <p>
 * Ratchet: files that still violate this are listed in
 * {@code architecture/unmigrated-backend-references.txt}. The test fails if a
 * NEW file violates, and also if a listed file no longer violates (so the
 * list can only ever shrink - remove it from the list when you migrate it).
 */
class BackendIsolationTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/dev/malik/lcftbhook");
    private static final Path RATCHET_FILE = Path.of("src/test/resources/architecture/unmigrated-backend-references.txt");

    /** Backend API package prefix -> zone directories (relative to SOURCE_ROOT) allowed to reference it. */
    private static final Map<String, List<String>> ZONES = Map.of(
            "dev.ftb.mods.", List.of("adapter/ftb/", "mixin/ftb/", "client/ftb/", "mixin/client/ftb/"),
            "io.github.lightman314.", List.of("adapter/lightmanscurrency/", "mixin/lc/", "client/lightmanscurrency/"),
            "xaero.pac.", List.of("adapter/opac/", "mixin/opac/", "client/opac/"),
            "dev.ithundxr.createnumismatics.", List.of("adapter/numismatics/", "client/numismatics/"),
            "xaero.map.", List.of("client/xaero/", "mixin/client/xaero/"),
            "xaero.common.", List.of("client/xaero/", "mixin/client/xaero/"),
            "xaero.lib.", List.of("client/xaero/", "mixin/client/xaero/")
    );

    @Test
    void backendApisOnlyReferencedFromTheirOwnZone() throws IOException {
        Set<String> violating = findViolatingFiles();
        Set<String> ratchet = readRatchet();

        List<String> problems = new ArrayList<>();
        for (String file : violating) {
            if (!ratchet.contains(file)) {
                problems.add("NEW violation (reference a port instead, or move into a zone package): " + file
                        + "\n    " + describeViolations(file));
            }
        }
        for (String file : ratchet) {
            if (!violating.contains(file)) {
                problems.add("Migrated - remove from " + RATCHET_FILE + ": " + file);
            }
        }
        if (!problems.isEmpty()) {
            fail(String.join("\n", problems) + "\n(" + violating.size() + " files still unmigrated)");
        }
    }

    private static Set<String> findViolatingFiles() throws IOException {
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (!violations(relative, Files.readString(file, StandardCharsets.UTF_8)).isEmpty()) {
                    result.add(relative);
                }
            }
        }
        return result;
    }

    private static String describeViolations(String relative) {
        try {
            return String.join(", ", violations(relative, Files.readString(SOURCE_ROOT.resolve(relative), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return e.toString();
        }
    }

    /** Only NAMES backend packages as data (which mod owns which mixin target) - never loads them. */
    private static final List<String> NAMES_BACKENDS_AS_DATA = List.of("mixinplugin/");

    private static Set<String> violations(String relativePath, String content) {
        Set<String> found = new TreeSet<>();
        if (NAMES_BACKENDS_AS_DATA.stream().anyMatch(relativePath::startsWith)) {
            return found;
        }
        for (String line : codeLines(content)) {
            for (Map.Entry<String, List<String>> zone : ZONES.entrySet()) {
                if (line.contains(zone.getKey()) && zone.getValue().stream().noneMatch(relativePath::startsWith)) {
                    found.add(zone.getKey() + "*");
                }
            }
        }
        return found;
    }

    /** Source lines with comments stripped - javadoc may freely mention backend classes by name. */
    private static List<String> codeLines(String content) {
        List<String> lines = new ArrayList<>();
        boolean inBlockComment = false;
        for (String raw : content.split("\n")) {
            String line = raw.strip();
            if (inBlockComment) {
                if (line.contains("*/")) {
                    inBlockComment = false;
                    line = line.substring(line.indexOf("*/") + 2);
                } else {
                    continue;
                }
            }
            if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            int lineComment = line.indexOf("//");
            if (lineComment >= 0) {
                line = line.substring(0, lineComment);
            }
            lines.add(line);
        }
        return lines;
    }

    private static Set<String> readRatchet() throws IOException {
        Set<String> entries = new TreeSet<>();
        for (String line : Files.readAllLines(RATCHET_FILE, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                entries.add(trimmed);
            }
        }
        return entries;
    }
}
