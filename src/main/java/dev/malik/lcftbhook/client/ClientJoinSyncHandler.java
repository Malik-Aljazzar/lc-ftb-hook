package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.network.RequestChunkOwnershipPayload;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestRegionsPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Requests claim prices/regions/chunk ownership right when the player joins
 * a world - previously these only synced when opening the FTB Chunks claim
 * manager screen, so a player who only ever used Xaero's World Map would see
 * stale/empty info (owner names, region names, prices) until some unrelated
 * action happened to trigger a broadcast.
 */
public final class ClientJoinSyncHandler {
    public ClientJoinSyncHandler() {
        NeoForge.EVENT_BUS.addListener(this::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        PacketDistributor.sendToServer(new RequestClaimPricesPayload());
        PacketDistributor.sendToServer(new RequestRegionsPayload());
        PacketDistributor.sendToServer(new RequestChunkOwnershipPayload());
    }

    /** The next server may use different claim/currency backends. */
    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        dev.malik.lcftbhook.port.Backends.reset();
        ClientClaims.reset();
    }
}
