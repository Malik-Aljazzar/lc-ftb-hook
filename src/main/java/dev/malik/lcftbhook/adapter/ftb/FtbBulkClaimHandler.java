package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbchunks.net.ChunkChangeResponsePacket;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.bank.ClaimResultIds;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.service.BulkClaimAffordability;
import dev.architectury.networking.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FtbBulkClaimHandler {
    private FtbBulkClaimHandler() {
    }

    public static boolean rejectIfInsufficientFunds(
            RequestChunkChangePacket message,
            ServerPlayer player,
            CommandSourceStack source,
            ChunkTeamData chunkTeamData
    ) {
        if (message.action() != RequestChunkChangePacket.ChunkChangeOp.CLAIM || message.chunks().size() <= 1) {
            return false;
        }

        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }

        // The team the chunks are claimed for (an admin may claim for another one) - it pays.
        Team team = chunkTeamData.getTeam();
        if (team == null || team.isServerTeam()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        int claimableCount = countClaimableChunks(source, chunkTeamData, message.chunks(), level);
        BulkClaimAffordability.Refusal refusal = BulkClaimAffordability.check(player.server, player.getUUID(),
                team.getTeamId(), chunkTeamData.getClaimedChunks().size(), claimableCount);
        if (refusal == null) {
            return false;
        }
        player.displayClientMessage(refusal.message(), false);
        ClaimPriceSync.syncToPlayer(player);

        Map<String, Integer> problems = new HashMap<>();
        problems.put(ClaimResultIds.INSUFFICIENT_FUNDS_BULK, refusal.paidClaims());
        // FTB Chunks registers ChunkChangeResponsePacket through Architectury's
        // NetworkManager (see RequestChunkChangePacket.handle()), which wraps the
        // payload for encoding. Sending it via NeoForge's native PacketDistributor
        // instead bypasses that wrapping and throws a ClassCastException
        // (ftbchunks...ChunkChangeResponsePacket cannot be cast to
        // architectury...NetworkAggregator$BufCustomPacketPayload) when the client
        // tries to decode it - crashing the connection. Send it the same way FTB
        // Chunks itself does.
        NetworkManager.sendToPlayer(player, new ChunkChangeResponsePacket(message.chunks().size(), 0, problems));
        return true;
    }

    public static ChunkTeamData resolveTeamData(RequestChunkChangePacket message, ServerPlayer player) {
        ChunkTeamData chunkTeamData = null;
        if (message.teamId().isPresent()) {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamByID(message.teamId().get());
            if (team.isEmpty()) {
                return null;
            }
            chunkTeamData = ClaimedChunkManagerImpl.getInstance().getOrCreateData(team.get());
        }
        if (chunkTeamData == null) {
            chunkTeamData = ClaimedChunkManagerImpl.getInstance().getOrCreateData(player);
        }
        return chunkTeamData;
    }

    private static int countClaimableChunks(
            CommandSourceStack source,
            ChunkTeamData chunkTeamData,
            Set<XZ> chunks,
            ServerLevel level
    ) {
        ClaimBatchContext.beginValidation();
        try {
            int claimableCount = 0;
            for (XZ pos : chunks) {
                ClaimResult result = chunkTeamData.claim(source, pos.dim(level), true);
                if (result.isSuccess()) {
                    claimableCount++;
                }
            }
            return claimableCount;
        } finally {
            ClaimBatchContext.endValidation();
        }
    }
}
