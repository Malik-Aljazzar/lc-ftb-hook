package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.LCFtbHook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies this mod's world data file aside at every server start, before it is
 * read - so a bad update or an unreadable file (which the game would replace
 * with empty data on the next save) can always be rolled back. Keeps the
 * newest {@value #KEEP} copies in {@code world/data/lc_ftb_hook_backups/}.
 */
public final class SavedDataBackups {
    static final int KEEP = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SavedDataBackups() {
    }

    public static void snapshot(MinecraftServer server) {
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path file = dataDir.resolve(FtbHookSavedData.DATA_NAME + ".dat");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Path backupDir = dataDir.resolve(LCFtbHook.MOD_ID + "_backups");
        try {
            Files.createDirectories(backupDir);
            Path target = backupDir.resolve(FtbHookSavedData.DATA_NAME + "-" + LocalDateTime.now().format(STAMP) + ".dat");
            Files.copy(file, target);
            prune(backupDir);
            LCFtbHook.LOGGER.info("Backed up {} to {}", file.getFileName(), target);
        } catch (IOException e) {
            LCFtbHook.LOGGER.error("Could not back up {} - continuing without a backup", file, e);
        }
    }

    private static void prune(Path backupDir) throws IOException {
        List<Path> backups;
        try (Stream<Path> files = Files.list(backupDir)) {
            backups = files.filter(path -> path.getFileName().toString().startsWith(FtbHookSavedData.DATA_NAME + "-"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
        }
        for (Path old : backups.subList(Math.min(KEEP, backups.size()), backups.size())) {
            Files.deleteIfExists(old);
        }
    }
}
