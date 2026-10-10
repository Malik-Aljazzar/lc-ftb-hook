package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.CancelListingPayload;
import dev.malik.lcftbhook.network.CancelListingsPayload;
import dev.malik.lcftbhook.network.CreateRegionAndAssignPayload;
import dev.malik.lcftbhook.network.RequestRegionAssignmentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Region and marketplace actions for a selection of claimed chunks - map-mod
 * and claim-mod independent, shown by Xaero's right-click menu and by the main
 * menu for the chunk the player stands in. Only actions the local player can
 * actually perform are offered; the server re-checks everything anyway.
 */
public final class ChunkActions {
    /** @param run gets the screen to return to afterwards */
    public record ChunkAction(Component label, Consumer<Screen> run) {
    }

    private final List<String> keys;
    private final List<UUID> teams;
    @Nullable
    private final UUID player;
    private final List<ChunkAction> actions = new ArrayList<>();

    private ChunkActions(List<String> keys, List<UUID> teams) {
        this.keys = keys;
        this.teams = teams;
        this.player = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
    }

    /** @param teams the owning team of each entry of {@code chunkKeys} (claimed chunks only) */
    public static List<ChunkAction> forSelection(List<String> chunkKeys, List<UUID> teams) {
        if (chunkKeys.isEmpty()) {
            return List.of();
        }
        ChunkActions builder = new ChunkActions(chunkKeys, teams);
        builder.regionActions();
        builder.sellActions();
        builder.buyActions();
        return builder.actions;
    }

    private void regionActions() {
        if (!anyChunk(this::officerChunk)) {
            return;
        }
        add(Component.translatable("gui.lc_ftb_hook.regions.assign_to"), parent -> ScreenOpener.openRegionPicker(parent,
                regionId -> PacketDistributor.sendToServer(new RequestRegionAssignmentPayload(keys, regionId))));
        add(Component.translatable("gui.lc_ftb_hook.regions.new_from_selection"), parent -> ScreenOpener.openTextPrompt(parent,
                Component.translatable("gui.lc_ftb_hook.regions.new_from_selection"),
                Component.translatable("gui.lc_ftb_hook.regions.create_ghost").getString(),
                name -> PacketDistributor.sendToServer(new CreateRegionAndAssignPayload(name, keys))));
        if (keys.size() == 1) {
            UUID regionId = ClientRegionMembership.rawRegionOf(keys.get(0));
            Region region = regionId != null ? ClientRegions.get(regionId) : ClientRegions.getDefault();
            if (region != null) {
                add(Component.translatable("gui.lc_ftb_hook.regions.manage_region", region.name()),
                        parent -> ScreenOpener.openRegionSettings(parent, region.id()));
            }
        }
    }

    private void sellActions() {
        if (anyChunk(i -> officerChunk(i) && ownership(i).isStateOwned() && !ownership(i).isListed()
                && !ClientUnsettledChunks.isUnsettled(keys.get(i)))) {
            add(Component.translatable("gui.lc_ftb_hook.marketplace.sell_as_country"),
                    parent -> ScreenOpener.openSalePrice(ScreenOpener.backTo(parent), keys, true));
        }
        ChunkOwnership first = ownership(0);
        if (keys.size() == 1 && !first.isStateOwned() && first.privateOwner().equals(player)) {
            if (!first.isListed() && !ClientUnsettledChunks.isUnsettled(keys.get(0)) && landRegion(0).allowPrivateSelling()) {
                add(Component.translatable("gui.lc_ftb_hook.marketplace.sell"),
                        parent -> ScreenOpener.openSalePrice(ScreenOpener.backTo(parent), keys, false));
            }
            add(Component.translatable("gui.lc_ftb_hook.marketplace.settings"), parent -> ScreenOpener.openOverride(parent, keys.get(0)));
        }
        if (anyChunk(i -> officerChunk(i) && !ownership(i).isStateOwned())) {
            add(Component.translatable("gui.lc_ftb_hook.marketplace.expropriate"),
                    parent -> ScreenOpener.openExpropriatePrice(ScreenOpener.backTo(parent), keys));
        }
    }

    private void buyActions() {
        ChunkOwnership first = ownership(0);
        if (keys.size() > 1 && anyChunk(this::cancelableListing)) {
            add(Component.translatable("gui.lc_ftb_hook.marketplace.cancel_listings_in_selection"),
                    parent -> PacketDistributor.sendToServer(new CancelListingsPayload(keys)));
        }
        if (!first.isListed()) {
            return;
        }
        if (allChunks(i -> BuyableChunkChecker.isBuyable(ownership(i), teams.get(i), keys.get(i), player))) {
            add(Component.translatable("gui.lc_ftb_hook.marketplace.buy"), parent -> ScreenOpener.openBuyConfirm(parent, keys, false));
        }
        // Buying "as country" only works if every chunk is listed and the buyer
        // is an officer of each owning team - otherwise the server rejects it.
        if (allChunks(i -> ownership(i).isListed() && officerChunk(i))) {
            add(Component.translatable("gui.lc_ftb_hook.marketplace.buy_as_country"), parent -> ScreenOpener.openBuyConfirm(parent, keys, true));
        }
        if (keys.size() == 1 && cancelableListing(0)) {
            add(Component.translatable("gui.lc_ftb_hook.marketplace.cancel_listing"),
                    parent -> PacketDistributor.sendToServer(new CancelListingPayload(keys.get(0))));
        }
    }

    private boolean cancelableListing(int i) {
        ChunkOwnership ownership = ownership(i);
        return ownership.isListed() && (ownership.isStateOwned() ? officerChunk(i) : ownership.privateOwner().equals(player));
    }

    private boolean officerChunk(int i) {
        return ClientClaims.rankIn(teams.get(i)).isOfficerOrBetter();
    }

    private ChunkOwnership ownership(int i) {
        return ClientChunkOwnership.get(keys.get(i));
    }

    /** The chunk's region from the public cache - the land may belong to another team. */
    private Region landRegion(int i) {
        UUID regionId = ClientRegionMembership.rawRegionOf(keys.get(i));
        Region region = regionId != null ? ClientPublicRegions.get(regionId) : ClientPublicRegions.getDefaultFor(teams.get(i));
        return region != null ? region : Region.createDefault();
    }

    private boolean anyChunk(java.util.function.IntPredicate test) {
        for (int i = 0; i < keys.size(); i++) {
            if (test.test(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean allChunks(java.util.function.IntPredicate test) {
        return !anyChunk(i -> !test.test(i));
    }

    private void add(Component label, Consumer<Screen> run) {
        actions.add(new ChunkAction(label, run));
    }
}
