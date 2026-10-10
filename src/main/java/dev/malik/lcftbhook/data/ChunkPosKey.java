package dev.malik.lcftbhook.data;

import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The string key a chunk is stored under in saved data and sent over the
 * network ("dimension#x#z"). Backend-neutral - FTB's own {@code ChunkDimPos}
 * converts via {@code adapter.ftb.FtbChunkKeys}.
 */
public final class ChunkPosKey {
    private static final String SEPARATOR = "#";

    private ChunkPosKey() {
    }

    public static String encode(ResourceLocation dimension, int x, int z) {
        return dimension + SEPARATOR + x + SEPARATOR + z;
    }

    public static String encode(ResourceKey<Level> dimension, ChunkPos pos) {
        return encode(dimension.location(), pos.x, pos.z);
    }

    public static String encode(ClaimPort.ChunkKey chunk) {
        return encode(chunk.dimension(), chunk.pos());
    }

    public static ClaimPort.ChunkKey toChunkKey(String key) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimension(key));
        return new ClaimPort.ChunkKey(dimension, new ChunkPos(x(key), z(key)));
    }

    public static ResourceLocation dimension(String key) {
        return ResourceLocation.parse(key.substring(0, key.indexOf(SEPARATOR)));
    }

    public static int x(String key) {
        int first = key.indexOf(SEPARATOR);
        int second = key.indexOf(SEPARATOR, first + 1);
        return Integer.parseInt(key.substring(first + 1, second));
    }

    public static int z(String key) {
        int second = key.indexOf(SEPARATOR, key.indexOf(SEPARATOR) + 1);
        return Integer.parseInt(key.substring(second + 1));
    }
}
