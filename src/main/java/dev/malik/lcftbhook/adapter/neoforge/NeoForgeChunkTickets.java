package dev.malik.lcftbhook.adapter.neoforge;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.chunkload.ChunkLoadPort;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.UUID;

/**
 * {@link ChunkLoadPort} on NeoForge's own chunk ticket system - the same
 * mechanism FTB Chunks uses, but under this mod's id, so the claim mod's
 * tickets and ours never touch each other. Tickets NeoForge restores from the
 * save on world load are all dropped: which chunks should be loaded is
 * recomputed from the regions' force-load modes on the first reconcile pass
 * right after (a second at most), so a stale ticket can never outlive the
 * rule that put it there.
 */
public final class NeoForgeChunkTickets implements ChunkLoadPort {
    public static final NeoForgeChunkTickets INSTANCE = new NeoForgeChunkTickets();

    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "force_load"),
            (level, tickets) -> tickets.getEntityTickets().keySet().forEach(tickets::removeAllTickets));

    private NeoForgeChunkTickets() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
    }

    @Override
    public void setLoaded(MinecraftServer server, ClaimPort.ChunkKey chunk, UUID ownerTeamId, boolean loaded) {
        ServerLevel level = server.getLevel(chunk.dimension());
        if (level == null) {
            return;
        }
        CONTROLLER.forceChunk(level, ownerTeamId, chunk.pos().x, chunk.pos().z, loaded, true);
    }
}
