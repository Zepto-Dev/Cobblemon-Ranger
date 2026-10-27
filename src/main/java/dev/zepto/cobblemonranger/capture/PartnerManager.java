package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Partner Pokémon are wild Pokémon befriended with a styler. They join the party only so they can fight alongside
 * the player, and go back to the wild when released or once the next battle they were around for is over.
 */
public final class PartnerManager {
    private static final String PARTNER_TAG = CobblemonRanger.MOD_ID + ":partner";
    private static final String BATTLED_TAG = CobblemonRanger.MOD_ID + ":battled";

    private PartnerManager() {
    }

    public static boolean isPartner(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(PARTNER_TAG);
    }

    static void markPartner(Pokemon pokemon) {
        pokemon.getPersistentData().putBoolean(PARTNER_TAG, true);
        pokemon.getPersistentData().remove(BATTLED_TAG);
    }

    static void clearPartner(Pokemon pokemon) {
        pokemon.getPersistentData().remove(PARTNER_TAG);
        pokemon.getPersistentData().remove(BATTLED_TAG);
    }

    public static List<Pokemon> getPartners(ServerPlayer player) {
        List<Pokemon> partners = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null && isPartner(pokemon)) {
                partners.add(pokemon);
            }
        }
        return partners;
    }

    private static boolean isInBattle(ServerPlayer player) {
        return BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) != null;
    }

    public static int releaseAll(ServerPlayer player) {
        int released = 0;
        for (Pokemon pokemon : getPartners(player)) {
            if (release(player, pokemon)) {
                released++;
            }
        }
        return released;
    }

    /** Removes a partner from the party and lets it loose next to where it was (or next to the player). */
    public static boolean release(ServerPlayer player, Pokemon pokemon) {
        if (isInBattle(player)) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.release_in_battle"), true);
            return false;
        }
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        ServerLevel level = player.serverLevel();
        PokemonEntity sentOut = pokemon.getEntity();
        Vec3 pos = sentOut != null && sentOut.level() == level
                ? sentOut.position()
                : player.position().add(player.getLookAngle().multiply(2, 0, 2));
        if (sentOut != null) {
            pokemon.recall();
        }
        if (!party.remove(pokemon)) {
            return false;
        }
        clearPartner(pokemon);
        pokemon.heal();

        PokemonEntity wild = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        wild.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180, 0);
        level.addFreshEntity(wild);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + wild.getBbHeight() / 2, pos.z,
                10, wild.getBbWidth() / 2, wild.getBbHeight() / 2, wild.getBbWidth() / 2, 0);
        level.playSound(null, wild.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.released", wild.getName()), false);
        return true;
    }

    /** Partners are marked when a battle involving their trainer starts, and released once it is over. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        List<Pokemon> partners = getPartners(player);
        if (partners.isEmpty()) {
            return;
        }
        boolean inBattle = isInBattle(player);
        for (Pokemon pokemon : partners) {
            if (inBattle) {
                pokemon.getPersistentData().putBoolean(BATTLED_TAG, true);
            } else if (pokemon.getPersistentData().getBoolean(BATTLED_TAG)) {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.battle_over",
                        pokemon.getSpecies().getTranslatedName()), false);
                release(player, pokemon);
            }
        }
    }
}
