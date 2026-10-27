package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerStats;
import dev.zepto.cobblemonranger.network.CaptureResultPayload;
import dev.zepto.cobblemonranger.network.OpenCapturePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of a styler capture: starts the minigame when a wild Pokémon is right-clicked with a styler, holds the
 * Pokémon still while the player loops it, and turns it into a partner when the client reports a full gauge.
 */
public final class CaptureManager {
    /** Marks a Pokémon we froze, so it gets its AI back even if the server stopped mid-capture. */
    private static final String FROZEN_TAG = CobblemonRanger.MOD_ID + ":frozen";
    /** The minigame can't be won faster than this, which rejects forged results. */
    private static final int MIN_CAPTURE_TICKS = 40;
    private static final int SESSION_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final double MAX_DISTANCE_SQR = 24 * 24;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Session(UUID entityUuid, int entityId, InteractionHand hand, long startTick, ServerLevel level,
                           boolean wasNoAi) {
    }

    private CaptureManager() {
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
        if (PartnerManager.isPartner(pokemon) && player.getUUID().equals(pokemon.getOwnerUUID())) {
            if (player.isShiftKeyDown()) {
                PartnerManager.release(player, pokemon);
            } else {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.partner_hint",
                        entity.getName()), true);
            }
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
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party.getFirstAvailablePosition() == null) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.party_full"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        SESSIONS.put(player.getUUID(), new Session(entity.getUUID(), entity.getId(), hand, level.getGameTime(), level,
                entity.isNoAi()));
        entity.getNavigation().stop();
        entity.setNoAi(true);
        entity.getPersistentData().putBoolean(FROZEN_TAG, true);

        StylerItem stylerItem = (StylerItem) styler.getItem();
        PacketDistributor.sendToPlayer(player, new OpenCapturePayload(
                entity.getId(),
                entity.getName(),
                pokemon.getPrimaryType().getName(),
                pokemon.getSpecies().getCatchRate(),
                pokemon.getLevel(),
                pokemon.getShiny(),
                StylerItem.getEnergy(styler),
                styler.getMaxDamage(),
                StylerStats.get(styler),
                stylerItem.getColor().getTextureDiffuseColor()));
    }

    public static void handleResult(ServerPlayer player, CaptureResultPayload result) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.entityId() != result.entityId()) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        Entity target = session.level().getEntity(session.entityUuid());
        unfreeze(target, session.wasNoAi());

        ItemStack styler = player.getItemInHand(session.hand());
        if (!(styler.getItem() instanceof StylerItem)) {
            return;
        }
        StylerItem.drainEnergy(styler, result.damage());

        switch (result.outcome()) {
            case STYLER_EMPTY -> player.displayClientMessage(
                    Component.translatable("message.cobblemonranger.capture_failed_energy"), false);
            case GAVE_UP -> player.displayClientMessage(
                    Component.translatable("message.cobblemonranger.capture_cancelled"), true);
            case CAPTURED -> {
                long elapsed = session.level().getGameTime() - session.startTick();
                if (target instanceof PokemonEntity entity && elapsed >= MIN_CAPTURE_TICKS
                        && StylerItem.getEnergy(styler) > 0) {
                    capture(player, entity);
                }
            }
        }
    }

    private static void capture(ServerPlayer player, PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        if (!entity.isAlive() || !pokemon.isWild() || entity.isBattling()
                || entity.level() != player.level() || entity.distanceToSqr(player) > MAX_DISTANCE_SQR) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.capture_escaped",
                    entity.getName()), false);
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

        PartnerManager.markPartner(pokemon);
        entity.discard();
        if (!party.add(pokemon)) {
            PartnerManager.clearPartner(pokemon);
            CobblemonRanger.LOGGER.warn("Could not add captured {} to {}'s party", name.getString(),
                    player.getGameProfile().getName());
            return;
        }
        player.displayClientMessage(Component.translatable("message.cobblemonranger.captured", name), false);
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
