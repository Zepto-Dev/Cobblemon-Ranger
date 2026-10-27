package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Partner Pokémon: one of the player's own Pokémon, picked with the styler, that stays with them for good. When it
 * is sent out it follows the player around, and it powers up Poké Assists and helps with field moves without leaving.
 */
public final class PartnerManager {
    private static final double FOLLOW_DISTANCE = 4;
    private static final double TELEPORT_DISTANCE = 16;

    /**
     * Players whose partner was sent out and is now back in its ball outside of a battle: they recalled it on purpose,
     * so it is not sent out again until they send it out themselves.
     */
    private static final Set<UUID> RECALLED = new HashSet<>();
    private static final Set<UUID> WAS_OUT = new HashSet<>();

    private PartnerManager() {
    }

    /** The partner, if it is in the player's party right now. */
    public static Pokemon getPartner(ServerPlayer player) {
        Optional<UUID> partnerId = RangerManager.get(player).partner();
        if (partnerId.isEmpty()) {
            return null;
        }
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null && pokemon.getUuid().equals(partnerId.get())) {
                return pokemon;
            }
        }
        return null;
    }

    public static boolean isPartner(ServerPlayer player, Pokemon pokemon) {
        return RangerManager.get(player).partner().map(pokemon.getUuid()::equals).orElse(false);
    }

    public static void setPartner(ServerPlayer player, PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        if (isPartner(player, pokemon)) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.partner_already",
                    entity.getName()), true);
            return;
        }
        RangerManager.update(player, data -> data.withPartner(Optional.of(pokemon.getUuid())));
        WAS_OUT.add(player.getUUID());
        RECALLED.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ(),
                10, entity.getBbWidth() / 2, 0.3, entity.getBbWidth() / 2, 0.1);
        level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.8f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.partner_set", entity.getName()),
                false);
    }

    public static void clearPartner(ServerPlayer player) {
        RangerManager.update(player, data -> data.withPartner(Optional.empty()));
    }

    /** Raises the partner's friendship a little; called after captures it was around for. */
    public static void rewardPartner(ServerPlayer player, int friendship) {
        Pokemon partner = getPartner(player);
        if (partner != null) {
            partner.incrementFriendship(friendship, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECALLED.remove(event.getEntity().getUUID());
        WAS_OUT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 5 || !player.isAlive()
                || player.isSpectator()) {
            return;
        }
        Pokemon partner = getPartner(player);
        if (partner == null) {
            return;
        }
        UUID id = player.getUUID();
        boolean inBattle = BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) != null;
        PokemonEntity entity = partner.getEntity();
        if (entity != null) {
            WAS_OUT.add(id);
            RECALLED.remove(id);
            if (!inBattle && !entity.isBattling()) {
                follow(player, entity);
            }
            return;
        }
        if (WAS_OUT.remove(id) && !inBattle) {
            RECALLED.add(id);
        }
        if (!inBattle && !RECALLED.contains(id) && !partner.isFainted() && !CaptureManager.isCapturing(player)
                && player.tickCount % 40 == 5 && player.onGround()) {
            Vec3 pos = player.position().add(player.getLookAngle().multiply(-1.5, 0, -1.5));
            partner.sendOut(player.serverLevel(), pos, null, e -> kotlin.Unit.INSTANCE);
        }
    }

    // Navigation goes through Mob; see CaptureManager.interact.
    private static void follow(ServerPlayer player, PokemonEntity entity) {
        if (entity.level() != player.level()) {
            return;
        }
        double distance = entity.distanceTo(player);
        if (distance > TELEPORT_DISTANCE && player.onGround()) {
            Vec3 behind = player.position().add(player.getLookAngle().multiply(-1.5, 0, -1.5));
            entity.teleportTo(behind.x, player.getY(), behind.z);
            ((Mob) entity).getNavigation().stop();
        } else if (distance > FOLLOW_DISTANCE) {
            ((Mob) entity).getNavigation().moveTo(player, distance > FOLLOW_DISTANCE * 2 ? 1.6 : 1.1);
        }
    }
}
