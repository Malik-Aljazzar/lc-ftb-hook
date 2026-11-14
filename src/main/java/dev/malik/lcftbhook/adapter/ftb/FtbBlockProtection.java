package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.port.protection.BlockProtectionPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * {@link BlockProtectionPort} with FTB Chunks' own rules: a player goes through
 * FTB's normal block check ({@code shouldPreventInteraction} - privacy level,
 * allies, bypass, fake-player policy, and the "action prevented" notice), a
 * mob or an unattributed change through the chunk's mob-griefing / explosion
 * setting (the same lookup FTB's own explosion handler uses). FTB's world
 * switches are honored: {@code disable_protection} allows everything,
 * {@code protect_unknown_explosions = false} allows unattributed changes.
 */
public final class FtbBlockProtection implements BlockProtectionPort {
    @Override
    public boolean playerMayChange(ServerPlayer player, ServerLevel level, BlockPos pos, BlockChangeKind kind) {
        if (player.level() != level) {
            // FTB looks the chunk up in the player's own level - not the right one here.
            return unattributedMayChange(level, pos);
        }
        Protection protection = kind == BlockChangeKind.INTERACT ? Protection.INTERACT_BLOCK : Protection.EDIT_BLOCK;
        return !FTBChunksAPI.api().getManager().shouldPreventInteraction(player, InteractionHand.MAIN_HAND, pos, protection, null);
    }

    @Override
    public boolean mobMayChange(Entity mob, ServerLevel level, BlockPos pos) {
        if (protectionDisabled()) {
            return true;
        }
        ClaimedChunk chunk = chunkAt(level, pos);
        return chunk == null || FtbChunkProtection.allowsMobGriefing(chunk);
    }

    @Override
    public boolean unattributedMayChange(ServerLevel level, BlockPos pos) {
        if (protectionDisabled() || !FTBChunksWorldConfig.PROTECT_UNKNOWN_EXPLOSIONS.get()) {
            return true;
        }
        ClaimedChunk chunk = chunkAt(level, pos);
        return chunk == null || FtbChunkProtection.allowsExplosions(chunk);
    }

    @Nullable
    private static ClaimedChunk chunkAt(ServerLevel level, BlockPos pos) {
        return FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
    }

    private static boolean protectionDisabled() {
        return FTBChunksWorldConfig.DISABLE_PROTECTION.get();
    }
}
