package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.service.TeamLifecycleService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

/**
 * Wires Open Parties and Claims into this mod's backend-neutral hooks -
 * called from the mod constructor only when OPAC is installed. Each listener
 * also checks {@link #isActive()} at event time: with OPAC and FTB both
 * installed, only the configured claim backend may drive this mod.
 */
public final class OpacIntegration {
    private static final OpacPartyWatcher PARTY_WATCHER = new OpacPartyWatcher();
    private static final OpacProtectionSync PROTECTION_SYNC = new OpacProtectionSync();

    private OpacIntegration() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> dev.malik.lcftbhook.util.Guard.run("OPAC server started", () -> onServerStarted(event)));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            PARTY_WATCHER.reset();
            PROTECTION_SYNC.reset();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> dev.malik.lcftbhook.util.Guard.run("OPAC server tick", () -> onServerTick(event)));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> dev.malik.lcftbhook.util.Guard.run("OPAC player login", () -> onPlayerLoggedIn(event)));
    }

    public static boolean isActive() {
        return Backends.claim() instanceof OpacClaimPort;
    }

    /** OPAC's managers are per server instance - listeners are registered on every start. */
    private static void onServerStarted(ServerStartedEvent event) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(event.getServer()).getServerClaimsManager();
        claims.getActionListenerManager().register(new OpacClaimListener());
        claims.getChunkAccessOverriderManager().register(new OpacChunkAccessOverrider());
        if (isActive()) {
            OpacConfigLock.apply();
            OpacProtectionSync.request();
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PARTY_WATCHER.onServerTick(server);
        PROTECTION_SYNC.onServerTick(server);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isActive()) {
            TeamLifecycleService.onPlayerReady(player.server, player);
        }
    }
}
