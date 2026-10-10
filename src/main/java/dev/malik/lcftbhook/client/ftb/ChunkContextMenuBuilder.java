package dev.malik.lcftbhook.client.ftb;

import dev.malik.lcftbhook.client.BuyableChunkChecker;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.client.ClientPublicRegions;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.client.ClientUnsettledChunks;
import dev.malik.lcftbhook.client.ProtectionInfoLines;
import dev.malik.lcftbhook.client.ScreenOpener;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.CancelListingPayload;
import dev.malik.lcftbhook.network.CreateRegionAndAssignPayload;
import dev.malik.lcftbhook.network.RequestRegionAssignmentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the right-click context menu for one or more chunks selected in
 * FTB Chunks' claim manager (a plain click, or a left/right drag). Shared
 * between the single-button right-click path and the multi-chunk
 * drag-then-right-click path so both behave identically.
 */
public final class ChunkContextMenuBuilder {
    private ChunkContextMenuBuilder() {
    }

    public static List<ContextMenuItem> build(ChunkScreen chunkScreen, List<XZ> positions) {
        ResourceKey<Level> dimension = chunkScreen.getDimension().dimension;

        List<XZ> unclaimed = new ArrayList<>();
        List<XZ> claimed = new ArrayList<>();
        for (XZ pos : positions) {
            MapChunk chunk = chunkScreen.getDimension()
                    .getRegion(XZ.regionFromChunk(pos.x(), pos.z()))
                    .getDataBlocking()
                    .getChunk(pos);
            if (chunk != null && chunk.getClaimedDate().isPresent()) {
                claimed.add(pos);
            } else {
                unclaimed.add(pos);
            }
        }

        List<ContextMenuItem> items = new ArrayList<>();

        // Hidden entirely (not just disabled) rather than left visible and
        // silently failing, for a player who currently lacks spending access
        // on their team's account - server independently re-checks this
        // regardless (BankAccountHelper.canPurchaseForTeam), this is purely
        // about not showing an option that would just deny them.
        if (!unclaimed.isEmpty() && ClientClaimPrices.canManageClaims()) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.regions.claim"),
                    ItemIcon.getItemIcon(Items.EMERALD_BLOCK),
                    button -> sendChangeRequest(chunkScreen, RequestChunkChangePacket.ChunkChangeOp.CLAIM, unclaimed)
            ));
        }

        if (claimed.isEmpty()) {
            return items;
        }

        List<String> claimedKeys = claimed.stream()
                .map(pos -> ChunkPosKey.encode(dimension.location(), pos.x(), pos.z()))
                .toList();

        // Both "Change region" and "New Region from Selection" need at least
        // one chunk in the selection that actually belongs to the viewer's
        // own team - the server already silently ignores every other team's
        // chunks when assigning (RegionService#handleAssignmentRequest), so
        // without this check a selection over only foreign territory would
        // do nothing (or, for the "new region" case, still create a
        // genuinely empty region).
        UUID localPlayerForRegion = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        boolean anyOwnTeamChunk = false;
        for (XZ pos : claimed) {
            MapChunk c = chunkScreen.getDimension().getRegion(XZ.regionFromChunk(pos.x(), pos.z())).getDataBlocking().getChunk(pos);
            Team t = c != null ? c.getTeam().orElse(null) : null;
            if (t != null && localPlayerForRegion != null && t.getRankForPlayer(localPlayerForRegion).isOfficerOrBetter()) {
                anyOwnTeamChunk = true;
                break;
            }
        }
        if (anyOwnTeamChunk) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.regions.assign_to"), ItemIcon.getItemIcon(Items.GRASS_BLOCK), button ->
                    ScreenOpener.openRegionPicker(currentScreen(), regionId ->
                            PacketDistributor.sendToServer(new RequestRegionAssignmentPayload(claimedKeys, regionId))
                    )
            ));
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.regions.new_from_selection"), Icons.ADD, button ->
                    ScreenOpener.openTextPrompt(
                            currentScreen(),
                            Component.translatable("gui.lc_ftb_hook.regions.new_from_selection"),
                            Component.translatable("gui.lc_ftb_hook.regions.create_ghost").getString(),
                            name -> PacketDistributor.sendToServer(new CreateRegionAndAssignPayload(name, claimedKeys))
                    )
            ));
        }

        // Single-chunk only, own team only (needed for ClientRegions to
        // resolve the region at all - it's team-private, see its own
        // javadoc). Shown regardless of whether the chunk has a private
        // owner - "manage the region" is about the region's own settings,
        // not this one chunk's override.
        if (claimed.size() == 1) {
            XZ pos = claimed.get(0);
            MapChunk singleChunk = chunkScreen.getDimension().getRegion(XZ.regionFromChunk(pos.x(), pos.z())).getDataBlocking().getChunk(pos);
            Team singleTeam = singleChunk != null ? singleChunk.getTeam().orElse(null) : null;
            if (singleTeam != null && localPlayerForRegion != null && singleTeam.getRankForPlayer(localPlayerForRegion).isOfficerOrBetter()) {
                String singleKey = claimedKeys.get(0);
                UUID singleRegionId = ClientRegionMembership.rawRegionOf(singleKey);
                Region singleRegion = singleRegionId != null ? ClientRegions.get(singleRegionId) : ClientRegions.getDefault();
                if (singleRegion != null) {
                    UUID resolvedRegionId = singleRegion.id();
                    items.add(new ContextMenuItem(
                            Component.translatable("gui.lc_ftb_hook.regions.manage_region", singleRegion.name()),
                            Icons.SETTINGS,
                            button -> ScreenOpener.openRegionSettings(currentScreen(), resolvedRegionId)
                    ));
                }
            }
        }

        if (ClientClaimPrices.canManageClaims()) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.regions.unclaim"),
                    ItemIcon.getItemIcon(Items.BARRIER),
                    button -> sendChangeRequest(chunkScreen, RequestChunkChangePacket.ChunkChangeOp.UNCLAIM, claimed)
            ));
        }

        List<XZ> notForceLoaded = new ArrayList<>();
        List<XZ> forceLoaded = new ArrayList<>();
        for (XZ pos : claimed) {
            MapChunk c = chunkScreen.getDimension()
                    .getRegion(XZ.regionFromChunk(pos.x(), pos.z()))
                    .getDataBlocking()
                    .getChunk(pos);
            if (c != null && c.getForceLoadedDate().isPresent()) {
                forceLoaded.add(pos);
            } else {
                notForceLoaded.add(pos);
            }
        }
        if (!notForceLoaded.isEmpty() && ClientClaimPrices.canManageClaims()) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.regions.force_load"),
                    ItemIcon.getItemIcon(Items.GLOWSTONE),
                    button -> sendChangeRequest(chunkScreen, RequestChunkChangePacket.ChunkChangeOp.LOAD, notForceLoaded)
            ));
        }
        if (!forceLoaded.isEmpty() && ClientClaimPrices.canManageClaims()) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.regions.force_unload"),
                    ItemIcon.getItemIcon(Items.REDSTONE_TORCH),
                    button -> sendChangeRequest(chunkScreen, RequestChunkChangePacket.ChunkChangeOp.UNLOAD, forceLoaded)
            ));
        }

        UUID localPlayer = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        boolean anyPrivatelyOwnedInOwnTeam = false;
        for (int i = 0; i < claimed.size(); i++) {
            String key = claimedKeys.get(i);
            ChunkOwnership o = ClientChunkOwnership.get(key);
            if (o.isStateOwned()) {
                continue;
            }
            MapChunk c = chunkScreen.getDimension().getRegion(XZ.regionFromChunk(claimed.get(i).x(), claimed.get(i).z()))
                    .getDataBlocking().getChunk(claimed.get(i));
            Team t = c != null ? c.getTeam().orElse(null) : null;
            if (t != null && localPlayer != null && t.getRankForPlayer(localPlayer).isOfficerOrBetter()) {
                anyPrivatelyOwnedInOwnTeam = true;
                break;
            }
        }
        if (anyPrivatelyOwnedInOwnTeam) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.marketplace.expropriate"),
                    ItemIcon.getItemIcon(Items.IRON_BARS),
                    button -> ScreenOpener.openExpropriatePrice(chunkScreen::openGui, claimedKeys)
            ));
        }

        if (claimed.size() > 1) {
            boolean anyCancelableListing = false;
            for (int i = 0; i < claimed.size(); i++) {
                ChunkOwnership o = ClientChunkOwnership.get(claimedKeys.get(i));
                if (!o.isListed()) {
                    continue;
                }
                if (o.isStateOwned()) {
                    MapChunk c = chunkScreen.getDimension().getRegion(XZ.regionFromChunk(claimed.get(i).x(), claimed.get(i).z()))
                            .getDataBlocking().getChunk(claimed.get(i));
                    Team t = c != null ? c.getTeam().orElse(null) : null;
                    if (t != null && localPlayer != null && t.getRankForPlayer(localPlayer).isOfficerOrBetter()) {
                        anyCancelableListing = true;
                        break;
                    }
                } else if (localPlayer != null && localPlayer.equals(o.privateOwner())) {
                    anyCancelableListing = true;
                    break;
                }
            }
            if (anyCancelableListing) {
                items.add(new ContextMenuItem(
                        Component.translatable("gui.lc_ftb_hook.marketplace.cancel_listings_in_selection"),
                        Icons.CANCEL,
                        button -> PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.CancelListingsPayload(claimedKeys))
                ));
            }
        }

        if (claimed.size() != 1) {
            return items;
        }

        items.add(ContextMenuItem.separator());

        String chunkKey = claimedKeys.get(0);
        ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
        MapChunk singleChunk = chunkScreen.getDimension()
                .getRegion(XZ.regionFromChunk(claimed.get(0).x(), claimed.get(0).z()))
                .getDataBlocking()
                .getChunk(claimed.get(0));
        Team owningTeam = singleChunk != null ? singleChunk.getTeam().orElse(null) : null;

        boolean chunkUnsettled = ClientUnsettledChunks.isUnsettled(chunkKey);
        // Was missing any team-membership/rank check at all - any player,
        // including one on a completely different team, could see (and try)
        // an option to list this team's unlisted state-owned chunk for sale.
        // isOfficerOrBetter matches the server's own real gate
        // (MarketplaceService#listForSale's asCountry eligibility check) -
        // showing it to a mere member would just get silently rejected.
        boolean canSellAsCountry = owningTeam != null && localPlayer != null
                && owningTeam.getRankForPlayer(localPlayer).isOfficerOrBetter();
        if (ownership.isStateOwned() && !ownership.isListed() && !chunkUnsettled && canSellAsCountry) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.marketplace.sell_as_country"),
                    ItemIcon.getItemIcon(Items.GOLD_INGOT),
                    button -> ScreenOpener.openSalePrice(chunkScreen::openGui, List.of(chunkKey), true)
            ));
        }
        if (!ownership.isStateOwned() && ownership.privateOwner().equals(localPlayer)) {
            // owningTeam, not the viewer's own team - a privately-owned chunk
            // can sit in another team's territory entirely (you bought it off
            // their marketplace), and resolveRegion needs the LAND's team to
            // resolve a Default-region fallback correctly.
            if (!ownership.isListed() && !chunkUnsettled
                    && resolveRegion(chunkKey, owningTeam != null ? owningTeam.getTeamId() : null).allowPrivateSelling()) {
                items.add(new ContextMenuItem(
                        Component.translatable("gui.lc_ftb_hook.marketplace.sell"),
                        ItemIcon.getItemIcon(Items.GOLD_INGOT),
                        button -> ScreenOpener.openSalePrice(chunkScreen::openGui, List.of(chunkKey), false)
                ));
            }
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.marketplace.settings"),
                    Icons.SETTINGS,
                    button -> ScreenOpener.openOverride(currentScreen(), chunkKey)
            ));
        }
        if (ownership.isListed()) {
            if (BuyableChunkChecker.isBuyable(ownership, owningTeam != null ? owningTeam.getTeamId() : null, chunkKey, localPlayer)) {
                items.add(new ContextMenuItem(
                        Component.translatable("gui.lc_ftb_hook.marketplace.buy"),
                        ItemIcon.getItemIcon(Items.EMERALD),
                        button -> ScreenOpener.openBuyConfirm(currentScreen(), List.of(chunkKey), false)
                ));
            }
            items.add(new ContextMenuItem(
                    Component.translatable("gui.lc_ftb_hook.marketplace.buy_as_country"),
                    ItemIcon.getItemIcon(Items.EMERALD),
                    button -> ScreenOpener.openBuyConfirm(currentScreen(), List.of(chunkKey), true)
            ));
            if (ownership.isStateOwned() || localPlayer.equals(ownership.privateOwner())) {
                items.add(new ContextMenuItem(
                        Component.translatable("gui.lc_ftb_hook.marketplace.cancel_listing"),
                        Icons.CANCEL,
                        button -> PacketDistributor.sendToServer(new CancelListingPayload(chunkKey))
                ));
            }
        }

        return items;
    }

    /**
     * {@code teamId} is the LAND's owning team, not necessarily the viewer's
     * own - uses the globally-broadcast public region cache (not
     * {@code ClientRegions}, which only ever holds the viewer's own team's
     * regions) so this resolves correctly for a privately-owned chunk sitting
     * in another team's territory too, same as {@link ProtectionInfoLines}.
     */
    private static Region resolveRegion(String chunkKey, @Nullable UUID teamId) {
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        Region region = regionId != null ? ClientPublicRegions.get(regionId)
                : teamId != null ? ClientPublicRegions.getDefaultFor(teamId) : null;
        return region != null ? region : Region.createDefault();
    }

    /** FTB's current (wrapper) screen - where our vanilla screens return to. */
    private static net.minecraft.client.gui.screens.Screen currentScreen() {
        return net.minecraft.client.Minecraft.getInstance().screen;
    }

    private static void sendChangeRequest(ChunkScreen chunkScreen, RequestChunkChangePacket.ChunkChangeOp op, List<XZ> positions) {
        var team = chunkScreen.getOpenedAs();
        Optional<UUID> teamId = Optional.ofNullable(team).map(dev.ftb.mods.ftbteams.api.Team::getTeamId);
        dev.architectury.networking.NetworkManager.sendToServer(new RequestChunkChangePacket(op, Set.copyOf(positions), false, teamId));
    }
}
