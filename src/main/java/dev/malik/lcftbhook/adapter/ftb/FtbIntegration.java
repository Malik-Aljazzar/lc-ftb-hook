package dev.malik.lcftbhook.adapter.ftb;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.util.Guard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Registers every FTB Teams/Chunks listener. Called from the mod constructor
 * only when FTB Chunks is installed, so none of these classes load without
 * it. Registration happens before the server config (which picks the claim
 * backend) is loaded, so each listener also checks {@link #isActive()} at
 * event time - with FTB and OPAC both installed and OPAC selected, FTB's
 * events must not trigger this mod's claim logic.
 */
public final class FtbIntegration {
    private FtbIntegration() {
    }

    public static void register() {
        new FtbClaimEvents();
        new FtbTeamEvents();
        FtbClaimVisibility.register();
        // Before this mod's own server-start work, which reads party owners (e.g. for bank account links).
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (ServerStartedEvent event) ->
                Guard.run("FTB party owner repair", () -> {
                    if (isActive()) {
                        FtbPartyOwners.repairMissingOwners();
                    }
                }));
    }

    public static boolean isActive() {
        return Backends.claim() instanceof FtbClaimPort;
    }
}
