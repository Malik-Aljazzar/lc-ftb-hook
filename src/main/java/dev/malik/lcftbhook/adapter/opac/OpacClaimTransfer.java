package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.UUID;

/**
 * FTB Chunks keeps a leaving member's claims with the party; OPAC claims
 * belong to the player and would leave with them. So they are handed to the
 * party owner instead - no money moves, the land simply stays party land
 * (region, private ownership and listings are keyed by the party and stay).
 */
final class OpacClaimTransfer {
    /** OPAC's main (non-sub) player config; the protection sync moves each claim to its region's sub-config. */
    private static final int MAIN_CONFIG = -1;

    private OpacClaimTransfer() {
    }

    static void keepWithParty(MinecraftServer server, UUID partyId, UUID leaverId, UUID ownerId) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        int moved = 0;
        for (ClaimPort.ChunkKey chunk : Backends.claim().claimedChunks(server, leaverId)) {
            ResourceLocation dimension = chunk.dimension().location();
            IPlayerChunkClaimAPI claim = claims.get(dimension, chunk.pos().x, chunk.pos().z);
            if (claim != null && leaverId.equals(claim.getPlayerId())) {
                claims.claim(dimension, ownerId, MAIN_CONFIG, chunk.pos().x, chunk.pos().z, claim.isForceloadable());
                moved++;
            }
        }
        if (moved == 0) {
            return;
        }
        OpacProtectionSync.request();
        ServerPlayer leaver = server.getPlayerList().getPlayer(leaverId);
        if (leaver != null) {
            leaver.displayClientMessage(Component.translatable("message.lc_ftb_hook.party_leave_claims_kept",
                    moved, Backends.claim().teamDisplayName(server, partyId)), false);
        }
        LCFtbHook.LOGGER.info("Kept {} claims of {} with party {} (now owned by {})", moved, leaverId, partyId, ownerId);
    }
}
