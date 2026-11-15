package dev.malik.lcftbhook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ModCompatibility}'s "built against" versions are what the startup
 * version warning compares against, so they have to follow gradle.properties
 * on every dependency bump. They drifted once already (FTB Chunks bumped to
 * 2101.1.24, warning still said "built for 2101.1.22", 2026-11-15) - this
 * fails the build instead.
 */
class ModCompatibilityVersionsTest {
    @Test
    void builtAgainstVersionsMatchGradleProperties() throws IOException {
        Properties gradle = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            gradle.load(reader);
        }
        assertEquals(gradle.getProperty("ftb_chunks_version"), ModCompatibility.REQUIRED_FTB_CHUNKS_VERSION,
                "ModCompatibility.REQUIRED_FTB_CHUNKS_VERSION must match ftb_chunks_version in gradle.properties");
        assertEquals(gradle.getProperty("lightmanscurrency_version"), ModCompatibility.REQUIRED_LIGHTMANS_CURRENCY_VERSION,
                "ModCompatibility.REQUIRED_LIGHTMANS_CURRENCY_VERSION must match lightmanscurrency_version in gradle.properties");
    }
}
