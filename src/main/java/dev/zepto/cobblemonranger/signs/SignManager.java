package dev.zepto.cobblemonranger.signs;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.ranger.RangerData;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Server side of Ranger Signs: checks the player may use the sign, then calls the legendary Pokémon, which appears
 * next to them for a few seconds while its power takes effect.
 */
public final class SignManager {
    /** Marks a summoned legendary, so it can't be captured and is removed even if the server stops while it's out. */
    public static final String HELPER_TAG = CobblemonRanger.MOD_ID + ":sign_helper";
    private static final int HELPER_TICKS = 20 * 5;
    private static final int EFFECT_DELAY = 25;

    private static final List<Summon> SUMMONS = new ArrayList<>();

    private static final class Summon {
        final ServerPlayer player;
        final RangerSign sign;
        final Entity helper;
        int age;

        Summon(ServerPlayer player, RangerSign sign, Entity helper) {
            this.player = player;
            this.sign = sign;
            this.helper = helper;
        }
    }

    private SignManager() {
    }

    public static boolean isHelper(Entity entity) {
        return entity.getPersistentData().getBoolean(HELPER_TAG);
    }

    public static void use(ServerPlayer player, String signId) {
        RangerSign sign = RangerSign.byId(signId);
        if (sign == null || !player.isAlive()) {
            return;
        }
        RangerData data = RangerManager.get(player);
        if (data.rank() < sign.rank()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_locked",
                    sign.displayName(), sign.rank()), true);
            return;
        }
        long now = RangerManager.now(player);
        long readyAt = data.signCooldowns().getOrDefault(sign.id(), 0L);
        if (readyAt > now) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_cooldown",
                    sign.displayName(), (readyAt - now + 19) / 20), true);
            return;
        }
        ItemStack styler = findStyler(player);
        if (styler.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_no_energy"), true);
            return;
        }
        StylerItem.drainEnergy(styler, RangerSign.ENERGY_COST);
        RangerManager.update(player, d -> d.withSignCooldown(sign.id(), now + sign.cooldownTicks()));

        ServerLevel level = player.serverLevel();
        Entity helper = spawnHelper(player, sign);
        SUMMONS.add(new Summon(player, sign, helper));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_used",
                sign.displayName()), false);
    }

    /** The styler with the most energy that can pay for a sign: the held one first, then anything in the inventory. */
    private static ItemStack findStyler(ServerPlayer player) {
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof StylerItem && StylerItem.getEnergy(stack) >= RangerSign.ENERGY_COST
                    && (best.isEmpty() || StylerItem.getEnergy(stack) > StylerItem.getEnergy(best))) {
                best = stack;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof StylerItem && StylerItem.getEnergy(offhand) >= RangerSign.ENERGY_COST
                && best.isEmpty()) {
            best = offhand;
        }
        return best;
    }

    private static Entity spawnHelper(ServerPlayer player, RangerSign sign) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 pos = player.position().add(look.scale(3));
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse(sign.species() + " level=70").create();
            PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
            entity.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180, 0);
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.getPersistentData().putBoolean(HELPER_TAG, true);
            level.addFreshEntity(entity);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 40, 0.8, 1, 0.8, 0.05);
            return entity;
        } catch (RuntimeException e) {
            CobblemonRanger.LOGGER.warn("Could not summon {} for a Ranger Sign", sign.species(), e);
            return null;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Summon> it = SUMMONS.iterator();
        while (it.hasNext()) {
            Summon summon = it.next();
            summon.age++;
            if (summon.age == EFFECT_DELAY && summon.player.isAlive() && !summon.player.hasDisconnected()) {
                apply(summon.player, summon.sign, summon.helper);
            }
            if (summon.age >= HELPER_TICKS) {
                if (summon.helper != null) {
                    Vec3 pos = summon.helper.position();
                    if (summon.helper.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 30, 0.6, 1, 0.6, 0.08);
                    }
                    summon.helper.discard();
                }
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // A helper saved with the chunk (the server stopped while it was out) never comes back.
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && isHelper(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ sign powers

    private static void apply(ServerPlayer player, RangerSign sign, Entity helper) {
        ServerLevel level = player.serverLevel();
        switch (sign) {
            case CELEBI -> bloom(player, level);
            case SUICUNE -> purify(player, level);
            case ENTEI -> sacredFire(player, level, helper);
            case RAIKOU -> thunder(player, level);
            case LATIOS -> soar(player);
            case GROUDON -> drought(player, level);
        }
    }

    /** Celebi: crops, saplings and grass around the player grow as if bone-mealed a few times. */
    private static void bloom(ServerPlayer player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock) || state.is(BlockTags.DIRT)
                    || state.is(Blocks.SHORT_GRASS) || !level.mayInteract(player, pos)) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            for (int i = 0; i < 3; i++) {
                state = level.getBlockState(immutable);
                if (state.getBlock() instanceof BonemealableBlock current
                        && current.isValidBonemealTarget(level, immutable, state)) {
                    current.performBonemeal(level, level.random, immutable, state);
                }
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, immutable.getX() + 0.5, immutable.getY() + 0.5,
                    immutable.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            grown++;
        }
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.4f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_celebi", grown), false);
    }

    /** Suicune: heals the player and their party, cures harmful effects and puts out fires nearby. */
    private static void purify(ServerPlayer player, ServerLevel level) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        List<MobEffectInstance> harmful = player.getActiveEffects().stream()
                .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).toList();
        harmful.forEach(e -> player.removeEffect(e.getEffect()));
        Cobblemon.INSTANCE.getStorage().getParty(player).heal();
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE)) {
                level.removeBlock(pos, false);
            } else if (state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
            }
        }
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 80, 3, 1, 3, 0.2);
        level.playSound(null, center, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_suicune"), false);
    }

    private static List<LivingEntity> hostilesNear(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive());
    }

    /** Entei: sets every hostile mob nearby on fire. */
    private static void sacredFire(ServerPlayer player, ServerLevel level, Entity helper) {
        List<LivingEntity> targets = hostilesNear(player, 12);
        for (LivingEntity target : targets) {
            target.igniteForSeconds(8);
            target.hurt(level.damageSources().indirectMagic(helper != null ? helper : player, player), 6);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.6,
                    0.4, 0.05);
        }
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 120, 4, 0.2, 4,
                0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_entei", targets.size()),
                false);
    }

    /** Raikou: lightning strikes the closest hostile mobs. The bolts are visual, so they never start fires. */
    private static void thunder(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> targets = new ArrayList<>(hostilesNear(player, 16));
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        int struck = 0;
        for (LivingEntity target : targets) {
            if (struck >= 6) {
                break;
            }
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.position());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            target.hurt(level.damageSources().indirectMagic(player, player), 14);
            struck++;
        }
        if (struck == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    0.6f, 1.2f);
        }
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_raikou", struck), false);
    }

    /** Latios: flies the player back to their bed (or the world spawn). */
    private static void soar(ServerPlayer player) {
        ServerLevel target = player.server.getLevel(player.getRespawnDimension());
        BlockPos respawn = player.getRespawnPosition();
        Vec3 pos;
        if (target != null && respawn != null) {
            pos = Vec3.atBottomCenterOf(respawn.above());
        } else {
            target = player.server.overworld();
            pos = Vec3.atBottomCenterOf(target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    target.getSharedSpawnPos()));
        }
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 40,
                0.6, 1, 0.6, 0.1);
        player.teleportTo(target, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100));
        target.playSound(null, BlockPos.containing(pos), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1f, 1.4f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_latios"), false);
    }

    /** Groudon: the sun comes out, clearing rain and storms for the next ten minutes. */
    private static void drought(ServerPlayer player, ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            level.setWeatherParameters(20 * 60 * 10, 0, false, false);
        }
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1, player.getZ(), 30, 3, 1, 3, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                0.5f, 0.6f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_groudon"), false);
    }
}
