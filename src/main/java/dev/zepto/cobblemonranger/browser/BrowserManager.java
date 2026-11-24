package dev.zepto.cobblemonranger.browser;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.network.BrowserSyncPayload;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server side of the Ranger Browser: records every species befriended with a styler, pays the first-capture and
 * milestone rewards, and unlocks Guide pages as the player meets each feature.
 */
public final class BrowserManager {
    /** Styler XP for befriending a species for the first time. */
    public static final int NEW_SPECIES_XP = 15;
    /** Distinct species befriended, and the Ranger points each milestone gives. */
    public static final int[] MILESTONES = {10, 25, 50, 100};
    public static final int[] MILESTONE_POINTS = {10, 20, 40, 80};

    private BrowserManager() {
    }

    public static BrowserData get(ServerPlayer player) {
        return player.getData(ModAttachments.BROWSER);
    }

    /** {@code cobblemon:vulpix}, or {@code cobblemon:vulpix/alolan} for a form other than the normal one. */
    public static String keyOf(Pokemon pokemon) {
        String id = pokemon.getSpecies().getResourceIdentifier().toString();
        String form = pokemon.getForm().getName().toLowerCase(Locale.ROOT);
        return form.isEmpty() || form.equals("normal") ? id : id + "/" + form;
    }

    /** Called once a captured Pokémon has joined the party as a friend. */
    public static void onCapture(ServerPlayer player, Pokemon pokemon, ItemStack styler) {
        BrowserData before = get(player);
        String key = keyOf(pokemon);
        BrowserData.Entry old = before.species().get(key);
        boolean alpha = pokemon.isAlpha();
        BrowserData.Entry entry = new BrowserData.Entry(old == null ? 1 : old.count() + 1,
                alpha || old != null && old.alpha());
        BrowserData after = before.withSpecies(key, entry);
        for (GuidePage page : new GuidePage[]{GuidePage.FRIENDS, GuidePage.ASSISTS, GuidePage.FIELD_MOVES}) {
            after = after.withUnlocked(page);
        }
        player.setData(ModAttachments.BROWSER, after);
        PacketDistributor.sendToPlayer(player, new BrowserSyncPayload(false, Map.of(key, entry),
                List.copyOf(after.unlocked()), List.copyOf(after.read())));

        if (old == null) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.browser_new",
                    pokemon.getSpecies().getTranslatedName()).withStyle(ChatFormatting.AQUA), false);
            StylerItem.addXp(player, styler, NEW_SPECIES_XP);
        } else if (alpha && !old.alpha()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.browser_alpha",
                    pokemon.getSpecies().getTranslatedName()).withStyle(ChatFormatting.GOLD), false);
        }
        int countBefore = before.speciesCount();
        int countAfter = after.speciesCount();
        for (int i = 0; i < MILESTONES.length; i++) {
            if (countBefore < MILESTONES[i] && countAfter >= MILESTONES[i]) {
                int points = MILESTONE_POINTS[i];
                int oldRank = RangerManager.get(player).rank();
                RangerManager.update(player, data -> data.withPoints(data.points() + points));
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.PLAYERS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable("message.cobblemonranger.browser_milestone",
                        MILESTONES[i], points).withStyle(ChatFormatting.GOLD));
                MissionManager.announceRankUp(player, oldRank, RangerManager.get(player).rank());
            }
        }
    }

    /** Unlocks Guide pages the player has just met; does nothing for pages already unlocked. */
    public static void unlock(ServerPlayer player, GuidePage... pages) {
        BrowserData data = get(player);
        BrowserData after = data;
        List<GuidePage> added = new ArrayList<>();
        for (GuidePage page : pages) {
            if (!after.isUnlocked(page)) {
                after = after.withUnlocked(page);
                added.add(page);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        player.setData(ModAttachments.BROWSER, after);
        syncGuide(player, after);
        for (GuidePage page : added) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.guide_unlocked",
                    page.title()).withStyle(ChatFormatting.DARK_AQUA), false);
        }
    }

    public static void markRead(ServerPlayer player, String pageId) {
        GuidePage page = GuidePage.byId(pageId);
        BrowserData data = get(player);
        if (page == null || !data.isUnlocked(page) || data.isRead(page)) {
            return;
        }
        BrowserData after = data.withRead(page);
        player.setData(ModAttachments.BROWSER, after);
        syncGuide(player, after);
    }

    private static void syncGuide(ServerPlayer player, BrowserData data) {
        PacketDistributor.sendToPlayer(player, new BrowserSyncPayload(false, Map.of(),
                List.copyOf(data.unlocked()), List.copyOf(data.read())));
    }

    public static void syncAll(ServerPlayer player) {
        BrowserData data = get(player);
        PacketDistributor.sendToPlayer(player, new BrowserSyncPayload(true, data.species(),
                List.copyOf(data.unlocked()), List.copyOf(data.read())));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAll(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAll(player);
        }
    }

    /** The first time a player holds a styler, they are told how to open the Browser. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !(player.getMainHandItem().getItem() instanceof StylerItem)) {
            return;
        }
        BrowserData data = get(player);
        if (!data.hinted()) {
            player.setData(ModAttachments.BROWSER, data.withHinted());
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.browser_hint")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
