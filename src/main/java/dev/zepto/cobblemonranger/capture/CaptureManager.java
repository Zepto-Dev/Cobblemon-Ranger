package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.browser.BrowserManager;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerProgress;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.network.CaptureResultPayload;
import dev.zepto.cobblemonranger.network.LoopPayload;
import dev.zepto.cobblemonranger.network.OpenCapturePayload;
import dev.zepto.cobblemonranger.network.UseAssistPayload;
import dev.zepto.cobblemonranger.signs.SignManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of a styler capture: starts the minigame when a wild Pokémon is right-clicked with a styler, holds the
 * Pokémon still while the player loops it, and turns it into a friend when the client reports a full gauge.
 */
public final class CaptureManager {
    /** Marks a Pokémon we froze, so it gets its AI back even if the server stopped mid-capture. */
    private static final String FROZEN_TAG = CobblemonRanger.MOD_ID + ":frozen";
    /** The minigame can't be won faster than this, which rejects forged results. */
    private static final int MIN_CAPTURE_TICKS = 40;
    /**
     * Loops closer together than this don't count. Even a quick spin around a small Pokémon takes longer, so it only
     * stops a forged client from claiming loops faster than anyone could draw them.
     */
    private static final int MIN_LOOP_TICKS = 3;
    private static final int SESSION_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final double MAX_DISTANCE_SQR = 24 * 24;
    /** The Partner Pokémon's own Poké Assist is this much stronger than a friend's. */
    private static final float PARTNER_ASSIST_STRENGTH = 1.5f;
    /**
     * Most styler energy one Electric assist can put back, as a share of the maximum: the 20% it gives, times the
     * Partner's strength, with some slack for rounding.
     */
    private static final float MAX_CHARGE_PER_ASSIST = 0.2f * PARTNER_ASSIST_STRENGTH + 0.02f;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Session(UUID entityUuid, int entityId, InteractionHand hand, long startTick, ServerLevel level,
                           boolean wasNoAi, Set<UUID> assistsUsed, AssistLog log, float gainPerLoop) {
    }

    /** What the assists in a capture did, to check the client's result against. */
    private static final class AssistLog {
        final UUID partnerId;
        final int partnerCharge;
        boolean partnerUsed;
        int electricUses;
        /** Dragon and Poison assists fill the friendship gauge without loops. */
        boolean gaugeAssistUsed;
        int charmUses;
        /** Loops around the Pokémon the client reported, at most one every {@link #MIN_LOOP_TICKS}. */
        int loops;
        // Far enough back that the first loop always counts (Long.MIN_VALUE would overflow the subtraction).
        long lastLoopTick = -1000;

        AssistLog(UUID partnerId, int partnerCharge) {
            this.partnerId = partnerId;
            this.partnerCharge = partnerCharge;
        }
    }

    private CaptureManager() {
    }

    /**
     * While a Pokémon is held still for a capture, nobody else can throw a Poké Ball at it or battle it, so it can't
     * be taken from the Ranger mid-capture.
     */
    public static void registerCobblemonEvents() {
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            if (isBeingCaptured(event.getPokemon().getUUID())) {
                event.cancel();
            }
        });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
            if (SESSIONS.isEmpty()) {
                return;
            }
            for (BattleActor actor : event.getBattle().getActors()) {
                for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                    PokemonEntity entity = battlePokemon.getEntity();
                    if (entity != null && isBeingCaptured(entity.getUUID())) {
                        event.cancel();
                        return;
                    }
                }
            }
        });
    }

    /** Nothing hurts a Pokémon while it is being captured, so another player can't knock it out either. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!SESSIONS.isEmpty() && event.getEntity() instanceof PokemonEntity
                && isBeingCaptured(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    public static boolean isCapturing(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    private static boolean isBeingCaptured(UUID entityUuid) {
        return SESSIONS.values().stream().anyMatch(s -> s.entityUuid().equals(entityUuid));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof StylerItem) || !(event.getTarget() instanceof PokemonEntity pokemonEntity)) {
            return;
        }
        // Stop Cobblemon's own interaction (and the other hand) on both sides.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            interact(player, pokemonEntity, stack, event.getHand());
        }
    }

    private static void interact(ServerPlayer player, PokemonEntity entity, ItemStack styler, InteractionHand hand) {
        Pokemon pokemon = entity.getPokemon();
        if (SignManager.isHelper(entity)) {
            return;
        }
        boolean own = player.getUUID().equals(pokemon.getOwnerUUID());
        if (own && FriendManager.isFriend(pokemon)) {
            if (player.isShiftKeyDown()) {
                FriendManager.release(player, pokemon);
            } else {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.friend_hint",
                        entity.getName()), true);
            }
            return;
        }
        if (own) {
            // Cobblemon keeps sneak-use on your own Pokémon for itself, so a plain use picks the partner.
            PartnerManager.setPartner(player, entity);
            return;
        }
        if (!pokemon.isWild()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.not_wild"), true);
            return;
        }
        if (entity.isBattling()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.in_battle"), true);
            return;
        }
        if (isCapturing(player) || isBeingCaptured(entity.getUUID())) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.busy"), true);
            return;
        }
        if (StylerItem.getEnergy(styler) <= 0) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.no_energy"), true);
            return;
        }
        if (tooHighLevel(player, pokemon, styler, entity.getName())) {
            return;
        }
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party.getFirstAvailablePosition() == null) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.party_full"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        Pokemon partner = PartnerManager.getPartner(player);
        int partnerCharge = partner == null ? 0 : PartnerManager.charge(player, partner);
        SESSIONS.put(player.getUUID(), new Session(entity.getUUID(), entity.getId(), hand, level.getGameTime(), level,
                entity.isNoAi(), new HashSet<>(), new AssistLog(partner == null ? null : partner.getUuid(), partnerCharge),
                CaptureMath.gainPerLoop(pokemon.getSpecies().getCatchRate(), pokemon.getLevel(),
                        StylerItem.loopPower(styler))));
        // Through Mob: Cobblemon 1.7 dropped the PokemonNavigation override, so a direct call breaks the 1.6.1-built jar.
        ((Mob) entity).getNavigation().stop();
        entity.setNoAi(true);
        entity.getPersistentData().putBoolean(FROZEN_TAG, true);

        StylerItem stylerItem = (StylerItem) styler.getItem();
        List<OpenCapturePayload.Assist> assists = new ArrayList<>();
        if (partner != null && !partner.isFainted()) {
            assists.add(assistOf(partner, true));
        }
        for (Pokemon friend : FriendManager.getFriends(player)) {
            if (assists.size() < 6) {
                assists.add(assistOf(friend, false));
            }
        }
        PacketDistributor.sendToPlayer(player, new OpenCapturePayload(
                entity.getId(),
                entity.getName(),
                pokemon.getPrimaryType().getName(),
                pokemon.getSecondaryType() == null ? "" : pokemon.getSecondaryType().getName(),
                pokemon.getSpecies().getCatchRate(),
                pokemon.getLevel(),
                pokemon.getShiny(),
                StylerItem.getEnergy(styler),
                styler.getMaxDamage(),
                StylerItem.lineLength(styler),
                StylerItem.loopPower(styler),
                stylerItem.getColor().getTextureDiffuseColor(),
                assists,
                PARTNER_ASSIST_STRENGTH,
                partnerCharge,
                PartnerCharge.FULL));
    }

    /** Stylers can only capture Pok\u00e9mon up to a level that rises with the styler's own level. */
    private static boolean tooHighLevel(ServerPlayer player, Pokemon pokemon, ItemStack styler, Component name) {
        StylerProgress progress = StylerProgress.get(styler);
        int cap = progress.captureLevelCap();
        if (pokemon.getLevel() <= cap) {
            return false;
        }
        player.displayClientMessage(Component.translatable("message.cobblemonranger.too_high_level", name,
                pokemon.getLevel(), progress.level(), cap), false);
        return true;
    }

    private static OpenCapturePayload.Assist assistOf(Pokemon pokemon, boolean partner) {
        return new OpenCapturePayload.Assist(pokemon.getUuid(), pokemon.getSpecies().getTranslatedName(),
                pokemon.getPrimaryType().getName(), partner);
    }

    /**
     * A friend lending a Poké Assist goes back to the wild straight away. The Partner Pokémon stays, and can help once
     * its charge is full: loops from earlier captures plus the ones drawn so far in this one.
     */
    public static void handleAssist(ServerPlayer player, UseAssistPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != payload.entityId()
                || session.assistsUsed().contains(payload.pokemonId())) {
            return;
        }
        AssistLog log = session.log();
        Pokemon partner = PartnerManager.getPartner(player);
        if (partner != null && partner.getUuid().equals(payload.pokemonId())) {
            if (!partner.getUuid().equals(log.partnerId)
                    || log.partnerCharge + log.loops < PartnerCharge.FULL) {
                return;
            }
            session.assistsUsed().add(payload.pokemonId());
            log.partnerUsed = true;
            countAssist(log, partner);
            MissionManager.onAssist(player);
            return;
        }
        for (Pokemon friend : FriendManager.getFriends(player)) {
            if (friend.getUuid().equals(payload.pokemonId())) {
                session.assistsUsed().add(payload.pokemonId());
                countAssist(log, friend);
                FriendManager.release(player, friend, null, Component.translatable(
                        "message.cobblemonranger.assist_used", friend.getSpecies().getTranslatedName()));
                MissionManager.onAssist(player);
                return;
            }
        }
    }

    private static void countAssist(AssistLog log, Pokemon helper) {
        switch (helper.getPrimaryType().getName()) {
            case "electric" -> log.electricUses++;
            case "dragon", "poison" -> log.gaugeAssistUsed = true;
            case "fairy" -> log.charmUses++;
            default -> {
            }
        }
    }

    /** Counts a loop the client drew around the Pokémon, ignoring any that come impossibly fast. */
    public static void handleLoop(ServerPlayer player, LoopPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != payload.entityId()) {
            return;
        }
        AssistLog log = session.log();
        long now = session.level().getGameTime();
        if (now - log.lastLoopTick >= MIN_LOOP_TICKS) {
            log.loops++;
            log.lastLoopTick = now;
        }
    }

    public static void handleResult(ServerPlayer player, CaptureResultPayload result) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != result.entityId()) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        Entity target = session.level().getEntity(session.entityUuid());
        unfreeze(target, session.wasNoAi());

        AssistLog log = session.log();
        Pokemon partner = PartnerManager.getPartner(player);
        if (partner != null && partner.getUuid().equals(log.partnerId)) {
            // Nobody draws more than about two loops a second, which caps what a forged result could claim.
            int loops = Math.min(Math.max(0, result.chargeLoops()), log.loops);
            PartnerManager.setCharge(player, partner, (log.partnerUsed ? 0 : log.partnerCharge) + loops);
        }

        ItemStack styler = player.getItemInHand(session.hand());
        if (!(styler.getItem() instanceof StylerItem)) {
            return;
        }
        if (result.damage() >= 0) {
            StylerItem.drainEnergy(styler, result.damage());
        } else {
            // Electric assists recharged the styler by more than the hits took.
            int cap = (int) Math.ceil(log.electricUses * MAX_CHARGE_PER_ASSIST * styler.getMaxDamage());
            StylerItem.restoreEnergy(styler, Math.min(cap, -result.damage()));
        }

        switch (result.outcome()) {
            case STYLER_EMPTY -> player.displayClientMessage(
                    Component.translatable("message.cobblemonranger.capture_failed_energy"), false);
            case GAVE_UP -> player.displayClientMessage(
                    Component.translatable("message.cobblemonranger.capture_cancelled"), true);
            case CAPTURED -> {
                long elapsed = session.level().getGameTime() - session.startTick();
                int minLoops = CaptureMath.minLoops(session.gainPerLoop(), log.gaugeAssistUsed, log.charmUses);
                if (log.loops < minLoops) {
                    CobblemonRanger.LOGGER.warn("Rejected a capture by {}: {} loops reported, at least {} needed",
                            player.getGameProfile().getName(), log.loops, minLoops);
                    if (target != null) {
                        player.displayClientMessage(Component.translatable(
                                "message.cobblemonranger.capture_escaped", target.getName()), false);
                    }
                } else if (target instanceof PokemonEntity entity && elapsed >= MIN_CAPTURE_TICKS
                        && StylerItem.getEnergy(styler) > 0) {
                    capture(player, entity, styler);
                }
            }
        }
    }

    private static void capture(ServerPlayer player, PokemonEntity entity, ItemStack styler) {
        Pokemon pokemon = entity.getPokemon();
        if (!entity.isAlive() || !pokemon.isWild() || entity.isBattling()
                || entity.level() != player.level() || entity.distanceToSqr(player) > MAX_DISTANCE_SQR) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.capture_escaped",
                    entity.getName()), false);
            return;
        }
        if (tooHighLevel(player, pokemon, styler, entity.getName())) {
            return;
        }
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party.getFirstAvailablePosition() == null) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.party_full"), false);
            return;
        }

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ(),
                8, entity.getBbWidth() / 2, 0.3, entity.getBbWidth() / 2, 0.1);
        level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        Component name = entity.getName();

        FriendManager.markFriend(pokemon);
        entity.discard();
        if (!party.add(pokemon)) {
            FriendManager.clearFriend(pokemon);
            CobblemonRanger.LOGGER.warn("Could not add captured {} to {}'s party", name.getString(),
                    player.getGameProfile().getName());
            return;
        }
        player.displayClientMessage(Component.translatable("message.cobblemonranger.captured", name), false);

        int catchRate = pokemon.getSpecies().getCatchRate();
        int xp = 10 + Math.max(0, 255 - catchRate) / 8 + pokemon.getLevel() / 3;
        StylerItem.addXp(player, styler, xp);
        PartnerManager.rewardPartner(player, 2);
        MissionManager.onCapture(player, pokemon);
        BrowserManager.onCapture(player, pokemon, styler);
    }

    private static void unfreeze(Entity entity, boolean wasNoAi) {
        if (entity != null) {
            entity.getPersistentData().remove(FROZEN_TAG);
            if (entity instanceof PokemonEntity pokemonEntity) {
                pokemonEntity.setNoAi(wasNoAi);
            }
        }
    }

    private static void endSession(UUID playerId) {
        Session session = SESSIONS.remove(playerId);
        if (session != null) {
            unfreeze(session.level().getEntity(session.entityUuid()), session.wasNoAi());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        endSession(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            Entity target = session.level().getEntity(session.entityUuid());
            boolean expired = session.level().getGameTime() - session.startTick() > SESSION_TIMEOUT_TICKS;
            if (target == null || !target.isAlive() || expired) {
                unfreeze(target, session.wasNoAi());
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PokemonEntity entity
                && entity.getPersistentData().getBoolean(FROZEN_TAG)) {
            unfreeze(entity, false);
        }
    }
}
