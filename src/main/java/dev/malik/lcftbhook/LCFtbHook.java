package dev.malik.lcftbhook;

import com.mojang.logging.LogUtils;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.config.ProtectionPriceConfig;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSet;
import dev.malik.lcftbhook.command.ClearWarsCommand;
import dev.malik.lcftbhook.command.ForceUpkeepCommand;
import dev.malik.lcftbhook.command.RuntimeConfigCommand;
import dev.malik.lcftbhook.command.ServerAccountCommand;
import dev.malik.lcftbhook.command.UpkeepDetailsCommand;
import dev.malik.lcftbhook.command.UpkeepPriorityCommand;
import dev.malik.lcftbhook.network.BuyChunksPayload;
import dev.malik.lcftbhook.network.CancelListingPayload;
import dev.malik.lcftbhook.network.CreateRegionAndAssignPayload;
import dev.malik.lcftbhook.network.CreateRegionPayload;
import dev.malik.lcftbhook.network.DeleteRegionPayload;
import dev.malik.lcftbhook.network.ListForSalePayload;
import dev.malik.lcftbhook.network.RenameRegionPayload;
import dev.malik.lcftbhook.network.ReorderRegionsPayload;
import dev.malik.lcftbhook.network.RequestChunkOwnershipPayload;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestPendingStatePayload;
import dev.malik.lcftbhook.network.RequestRegionAssignmentPayload;
import dev.malik.lcftbhook.network.RequestRegionsPayload;
import dev.malik.lcftbhook.network.SetChunkOverridePayload;
import dev.malik.lcftbhook.network.SetRegionPropertyPayload;
import dev.malik.lcftbhook.network.SyncChunkOwnershipPayload;
import dev.malik.lcftbhook.network.SyncClaimPricesPayload;
import dev.malik.lcftbhook.network.SyncPendingStatePayload;
import dev.malik.lcftbhook.network.SyncRegionMembershipPayload;
import dev.malik.lcftbhook.network.SyncRegionsPayload;
import dev.malik.lcftbhook.network.RequestWarStatePayload;
import dev.malik.lcftbhook.network.SyncWarStatePayload;
import dev.malik.lcftbhook.network.ToggleWarPayload;
import dev.malik.lcftbhook.client.ClientJoinSyncHandler;
import dev.malik.lcftbhook.client.ftb.ClientPendingRefreshHandler;
import dev.malik.lcftbhook.service.ClaimMapSync;
import dev.malik.lcftbhook.service.UpkeepService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(LCFtbHook.MOD_ID)
public class LCFtbHook {
    public static final String MOD_ID = "lc_ftb_hook";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LCFtbHook(IEventBus modEventBus, ModContainer modContainer) {
        ModCompatibility.validateOrThrow();

        modContainer.registerConfig(ModConfig.Type.SERVER, LCFtbHookConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ProtectionPriceConfig.build(installedProtectionFieldSets()),
                ProtectionPriceConfig.FILE_NAME);

        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(UpkeepDetailsCommand::register);
        NeoForge.EVENT_BUS.addListener(UpkeepPriorityCommand::register);
        NeoForge.EVENT_BUS.addListener(ClearWarsCommand::register);
        NeoForge.EVENT_BUS.addListener(ForceUpkeepCommand::register);
        NeoForge.EVENT_BUS.addListener(ServerAccountCommand::register);
        NeoForge.EVENT_BUS.addListener(dev.malik.lcftbhook.command.TeamBankCommand::register);
        NeoForge.EVENT_BUS.addListener(RuntimeConfigCommand::register);

        // Game events below are guarded: an exception of ours must not crash the server tick or startup.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) ->
                dev.malik.lcftbhook.util.Guard.run("hook data backup", () -> dev.malik.lcftbhook.data.SavedDataBackups.snapshot(event.getServer())));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent event) ->
                dev.malik.lcftbhook.util.Guard.run("server started", () -> dev.malik.lcftbhook.service.TeamLifecycleService.onServerStarted(event.getServer())));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            dev.malik.lcftbhook.port.Backends.reset();
            ClaimMapSync.reset();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                dev.malik.lcftbhook.util.Guard.run("claim map sync tick", () -> ClaimMapSync.onServerTick(event.getServer())));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                dev.malik.lcftbhook.util.Guard.run("claim map sync on join", () -> ClaimMapSync.onPlayerJoin(player));
            }
        });

        NeoForge.EVENT_BUS.register(new UpkeepService());

        // Backend integrations: each only touches its own mod's classes, and
        // only when that mod is installed.
        if (ModList.get().isLoaded("ftbchunks")) {
            dev.malik.lcftbhook.adapter.ftb.FtbIntegration.register();
        }
        if (ModList.get().isLoaded("openpartiesandclaims")) {
            dev.malik.lcftbhook.adapter.opac.OpacIntegration.register();
            // Rewords OPAC texts: force-loads are queued until the next upkeep, and a claim's
            // title is just its land/region name (set by OpacProtectionSync), without the owner.
            dev.malik.lcftbhook.util.BuiltInResourcePacks.register(modEventBus, "opac_texts", "Open Parties and Claims texts");
        }
        if (ModList.get().isLoaded("lightmanscurrency")) {
            dev.malik.lcftbhook.adapter.lightmanscurrency.LcIntegration.register();
        }
        if (ModList.get().isLoaded("numismatics")) {
            dev.malik.lcftbhook.adapter.numismatics.NumismaticsCoinIcons.register(modEventBus);
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            if (ModList.get().isLoaded("ftbteams")) {
                new ClientPendingRefreshHandler();
            }
            new ClientJoinSyncHandler();
            dev.malik.lcftbhook.client.ClientKeyBindings.register(modEventBus);
        }
    }

    /** Protection fields of every installed claim mod - each gets its own price section. */
    private static List<ProtectionFieldSet> installedProtectionFieldSets() {
        List<ProtectionFieldSet> sets = new ArrayList<>();
        if (ModList.get().isLoaded("ftbchunks")) {
            sets.add(dev.malik.lcftbhook.adapter.ftb.FtbProtectionFields.SET);
        }
        if (ModList.get().isLoaded("openpartiesandclaims")) {
            sets.add(dev.malik.lcftbhook.adapter.opac.OpacProtectionFields.SET);
        }
        return sets;
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                SyncClaimPricesPayload.TYPE,
                SyncClaimPricesPayload.STREAM_CODEC,
                SyncClaimPricesPayload::handleClient
        );
        registrar.playToClient(
                SyncPendingStatePayload.TYPE,
                SyncPendingStatePayload.STREAM_CODEC,
                SyncPendingStatePayload::handleClient
        );
        registrar.playToClient(
                SyncWarStatePayload.TYPE,
                SyncWarStatePayload.STREAM_CODEC,
                SyncWarStatePayload::handleClient
        );
        registrar.playToClient(
                SyncRegionsPayload.TYPE,
                SyncRegionsPayload.STREAM_CODEC,
                SyncRegionsPayload::handleClient
        );
        registrar.playToClient(
                dev.malik.lcftbhook.network.SyncClaimMapPayload.TYPE,
                dev.malik.lcftbhook.network.SyncClaimMapPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SyncClaimMapPayload::handleClient
        );
        registrar.playToClient(
                dev.malik.lcftbhook.network.SyncViewerRanksPayload.TYPE,
                dev.malik.lcftbhook.network.SyncViewerRanksPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SyncViewerRanksPayload::handleClient
        );
        registrar.playToClient(
                SyncRegionMembershipPayload.TYPE,
                SyncRegionMembershipPayload.STREAM_CODEC,
                SyncRegionMembershipPayload::handleClient
        );
        registrar.playToClient(
                dev.malik.lcftbhook.network.SyncPublicRegionsPayload.TYPE,
                dev.malik.lcftbhook.network.SyncPublicRegionsPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SyncPublicRegionsPayload::handleClient
        );
        registrar.playToClient(
                dev.malik.lcftbhook.network.SyncUnsettledChunksPayload.TYPE,
                dev.malik.lcftbhook.network.SyncUnsettledChunksPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SyncUnsettledChunksPayload::handleClient
        );
        registrar.playToClient(
                dev.malik.lcftbhook.network.SyncPlayerProfilePayload.TYPE,
                dev.malik.lcftbhook.network.SyncPlayerProfilePayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SyncPlayerProfilePayload::handleClient
        );
        registrar.playToClient(
                dev.malik.lcftbhook.network.SyncUpkeepSummaryPayload.TYPE,
                dev.malik.lcftbhook.network.SyncUpkeepSummaryPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SyncUpkeepSummaryPayload::handleClient
        );
        registrar.playToClient(
                SyncChunkOwnershipPayload.TYPE,
                SyncChunkOwnershipPayload.STREAM_CODEC,
                SyncChunkOwnershipPayload::handleClient
        );
        registrar.playToServer(
                RequestChunkOwnershipPayload.TYPE,
                RequestChunkOwnershipPayload.STREAM_CODEC,
                RequestChunkOwnershipPayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.RequestPlayerProfilePayload.TYPE,
                dev.malik.lcftbhook.network.RequestPlayerProfilePayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.RequestPlayerProfilePayload::handleServer
        );
        registrar.playToServer(
                ListForSalePayload.TYPE,
                ListForSalePayload.STREAM_CODEC,
                ListForSalePayload::handleServer
        );
        registrar.playToServer(
                CancelListingPayload.TYPE,
                CancelListingPayload.STREAM_CODEC,
                CancelListingPayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.CancelListingsPayload.TYPE,
                dev.malik.lcftbhook.network.CancelListingsPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.CancelListingsPayload::handleServer
        );
        registrar.playToServer(
                BuyChunksPayload.TYPE,
                BuyChunksPayload.STREAM_CODEC,
                BuyChunksPayload::handleServer
        );
        registrar.playToServer(
                SetChunkOverridePayload.TYPE,
                SetChunkOverridePayload.STREAM_CODEC,
                SetChunkOverridePayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.SetChunkAccessListPayload.TYPE,
                dev.malik.lcftbhook.network.SetChunkAccessListPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SetChunkAccessListPayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.SetChunkLabelPayload.TYPE,
                dev.malik.lcftbhook.network.SetChunkLabelPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SetChunkLabelPayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.ExpropriateChunksPayload.TYPE,
                dev.malik.lcftbhook.network.ExpropriateChunksPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.ExpropriateChunksPayload::handleServer
        );
        registrar.playToServer(
                RequestClaimPricesPayload.TYPE,
                RequestClaimPricesPayload.STREAM_CODEC,
                RequestClaimPricesPayload::handleServer
        );
        registrar.playToServer(
                RequestPendingStatePayload.TYPE,
                RequestPendingStatePayload.STREAM_CODEC,
                RequestPendingStatePayload::handleServer
        );
        registrar.playToServer(
                RequestRegionsPayload.TYPE,
                RequestRegionsPayload.STREAM_CODEC,
                RequestRegionsPayload::handleServer
        );
        registrar.playToServer(
                CreateRegionPayload.TYPE,
                CreateRegionPayload.STREAM_CODEC,
                CreateRegionPayload::handleServer
        );
        registrar.playToServer(
                CreateRegionAndAssignPayload.TYPE,
                CreateRegionAndAssignPayload.STREAM_CODEC,
                CreateRegionAndAssignPayload::handleServer
        );
        registrar.playToServer(
                DeleteRegionPayload.TYPE,
                DeleteRegionPayload.STREAM_CODEC,
                DeleteRegionPayload::handleServer
        );
        registrar.playToServer(
                RenameRegionPayload.TYPE,
                RenameRegionPayload.STREAM_CODEC,
                RenameRegionPayload::handleServer
        );
        registrar.playToServer(
                ReorderRegionsPayload.TYPE,
                ReorderRegionsPayload.STREAM_CODEC,
                ReorderRegionsPayload::handleServer
        );
        registrar.playToServer(
                SetRegionPropertyPayload.TYPE,
                SetRegionPropertyPayload.STREAM_CODEC,
                SetRegionPropertyPayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.SetRegionAllowPrivateSellingPayload.TYPE,
                dev.malik.lcftbhook.network.SetRegionAllowPrivateSellingPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SetRegionAllowPrivateSellingPayload::handleServer
        );
        registrar.playToServer(
                dev.malik.lcftbhook.network.SetRegionBuyerAccessPayload.TYPE,
                dev.malik.lcftbhook.network.SetRegionBuyerAccessPayload.STREAM_CODEC,
                dev.malik.lcftbhook.network.SetRegionBuyerAccessPayload::handleServer
        );
        registrar.playToServer(
                RequestRegionAssignmentPayload.TYPE,
                RequestRegionAssignmentPayload.STREAM_CODEC,
                RequestRegionAssignmentPayload::handleServer
        );
        registrar.playToServer(
                RequestWarStatePayload.TYPE,
                RequestWarStatePayload.STREAM_CODEC,
                RequestWarStatePayload::handleServer
        );
        registrar.playToServer(
                ToggleWarPayload.TYPE,
                ToggleWarPayload.STREAM_CODEC,
                ToggleWarPayload::handleServer
        );
    }
}
