package dev.zepto.cobblemonranger.signs;

import dev.zepto.cobblemonranger.browser.BrowserManager;
import dev.zepto.cobblemonranger.browser.GuidePage;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.config.RangerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import java.util.Optional;
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
import java.util.Iterator;
import java.util.List;

/**
 * Server side of Ranger Signs: checks the player may use the sign, then calls the legendary Pokémon, which appears
 * next to them for a few seconds while its power takes effect.
 */
public final class SignManager {
    /**
     * Marks a summoned legendary, so the styler ignores it and it is removed even if the server stops while it's out.
     * It is also uncatchable and unbattleable through Cobblemon's own flags.
     */
    public static final String HELPER_TAG = CobblemonRanger.MOD_ID + ":sign_helper";
    private static final int HELPER_TICKS = 20 * 5;
    private static final int EFFECT_DELAY = 25;
    private static final double FURNACE_RADIUS = 8;
    private static final int FURNACE_MAX_ITEMS = 64;
    private static final double RADAR_RADIUS = 64;
    private static final int RADAR_TICKS = 20 * 60;

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
                    sign.displayName(), RangerSign.formatTime((readyAt - now + 19) / 20)), true);
            return;
        }
        SignWorldData world = SignWorldData.get(player.server);
        if (sign.worldSign() && world.worldSignReadyAt() > now) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_world_cooldown",
                    RangerSign.formatTime((world.worldSignReadyAt() - now + 19) / 20)), true);
            return;
        }
        Component refusal = refusal(player, sign);
        if (refusal != null) {
            player.displayClientMessage(refusal, true);
            return;
        }
        ItemStack styler = findStyler(player);
        if (styler.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_no_energy"), true);
            return;
        }
        StylerItem.drainEnergy(styler, RangerSign.ENERGY_COST);
        RangerManager.update(player, d -> d.withSignCooldown(sign.id(), now + sign.cooldownTicks()));
        if (sign.worldSign()) {
            world.setWorldSignReadyAt(now + RangerConfig.worldSignSharedCooldownTicks());
            // Everyone's sign list shows the shared wait.
            player.server.getPlayerList().getPlayers().forEach(RangerManager::sync);
        }

        ServerLevel level = player.serverLevel();
        BrowserManager.unlock(player, GuidePage.SIGNS);
        Entity helper = spawnHelper(player, sign);
        SUMMONS.add(new Summon(player, sign, helper));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_used",
                sign.displayName()), false);
    }

    /** Why the sign can't work here and now, checked before it costs energy or starts a cooldown; null if it can. */
    private static Component refusal(ServerPlayer player, RangerSign sign) {
        ServerLevel level = player.serverLevel();
        return switch (sign) {
            case GROUDON, KYOGRE -> level.dimension() == Level.OVERWORLD ? null
                    : Component.translatable("message.cobblemonranger.sign_no_weather", sign.displayName());
            case CRESSELIA, DARKRAI -> {
                if (level.dimensionType().hasFixedTime()) {
                    yield Component.translatable("message.cobblemonranger.sign_no_time", sign.displayName());
                }
                boolean night = isNight(player.server.overworld());
                if (sign == RangerSign.CRESSELIA && !night) {
                    yield Component.translatable("message.cobblemonranger.sign_already_day", sign.displayName());
                }
                if (sign == RangerSign.DARKRAI && night) {
                    yield Component.translatable("message.cobblemonranger.sign_already_night", sign.displayName());
                }
                yield null;
            }
            default -> null;
        };
    }

    /** Night as far as sleeping and night spawns go: from dusk (12542) to dawn (23460). */
    private static boolean isNight(ServerLevel overworld) {
        long time = overworld.getDayTime() % 24000L;
        return time >= 12542 && time < 23460;
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
            // A one-off copy that only appears for the sign: no Poké Ball or battle can take it.
            Pokemon pokemon = PokemonProperties.Companion.parse(sign.species() + " level=70 uncatchable").create();
            PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
            entity.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180, 0);
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.getEntityData().set(PokemonEntity.Companion.getUNBATTLEABLE(), true);
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
            case ENTEI -> furnace(player, level);
            case RAIKOU -> radar(player, level);
            case LATIOS -> soar(player);
            case GROUDON -> drought(player, level);
            case KYOGRE -> downpour(player, level);
            case CRESSELIA -> dawn(player, level);
            case DARKRAI -> dusk(player, level);
            case RAYQUAZA -> skyLaunch(player, level);
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

    /**
     * Entei: smelts or cooks the dropped items lying near the player, as a furnace would, and drops the experience a
     * furnace gives. Items in inventories are left alone, so nothing a player carries changes without them asking.
     */
    private static void furnace(ServerPlayer player, ServerLevel level) {
        int smelted = 0;
        float experience = 0;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(FURNACE_RADIUS),
                ItemEntity::isAlive);
        for (ItemEntity item : items) {
            if (smelted >= FURNACE_MAX_ITEMS) {
                break;
            }
            ItemStack stack = item.getItem();
            Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level);
            if (recipe.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            int count = Math.min(stack.getCount(), FURNACE_MAX_ITEMS - smelted);
            ItemStack output = result.copyWithCount(Math.min(result.getMaxStackSize(), result.getCount() * count));
            if (count >= stack.getCount()) {
                item.setItem(output);
            } else {
                // Only part of the stack fits in what's left: the rest stays raw next to it.
                item.setItem(stack.copyWithCount(stack.getCount() - count));
                ItemEntity cooked = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), output);
                cooked.setDefaultPickUpDelay();
                level.addFreshEntity(cooked);
            }
            smelted += count;
            experience += recipe.get().value().getExperience() * count;
            level.sendParticles(ParticleTypes.FLAME, item.getX(), item.getY() + 0.3, item.getZ(), 10, 0.2, 0.2, 0.2,
                    0.02);
        }
        int orbs = (int) experience + (level.random.nextFloat() < experience % 1 ? 1 : 0);
        if (orbs > 0) {
            ExperienceOrb.award(level, player.position(), orbs);
        }
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 60, 3, 0.2, 3,
                0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable(smelted > 0 ? "message.cobblemonranger.sign_entei"
                : "message.cobblemonranger.sign_entei_none", smelted), false);
    }

    /** Raikou: every wild Pokémon nearby glows for a minute, so they can be seen through walls. Shinies are named. */
    private static void radar(ServerPlayer player, ServerLevel level) {
        List<PokemonEntity> found = level.getEntitiesOfClass(PokemonEntity.class,
                player.getBoundingBox().inflate(RADAR_RADIUS),
                e -> e.isAlive() && e.getPokemon().isWild() && !isHelper(e));
        List<Component> shinies = new ArrayList<>();
        for (PokemonEntity pokemon : found) {
            pokemon.addEffect(new MobEffectInstance(MobEffects.GLOWING, RADAR_TICKS, 0, false, false));
            if (pokemon.getPokemon().getShiny()) {
                shinies.add(pokemon.getName());
            }
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(player.position().add(player.getLookAngle().multiply(3, 0, 3)));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_raikou", found.size()),
                false);
        if (!shinies.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_raikou_shiny",
                    ComponentUtils.formatList(shinies, Component.literal(", "))).withStyle(ChatFormatting.GOLD), false);
        }
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

    /** Kyogre: rain for the next ten minutes, the opposite of Groudon. */
    private static void downpour(ServerPlayer player, ServerLevel level) {
        level.setWeatherParameters(0, 20 * 60 * 10, true, false);
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 120, 4, 1, 4, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_kyogre"), false);
    }

    /** Cresselia: the night ends, as if everyone had slept. */
    private static void dawn(ServerPlayer player, ServerLevel level) {
        ServerLevel overworld = player.server.overworld();
        long time = overworld.getDayTime();
        overworld.setDayTime(time + (24000L - time % 24000L) % 24000L);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 60, 3, 1.5, 3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.6f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_cresselia"), false);
    }

    /** Darkrai: day turns to night, for the Pokémon that only come out after dark. */
    private static void dusk(ServerPlayer player, ServerLevel level) {
        ServerLevel overworld = player.server.overworld();
        long time = overworld.getDayTime();
        long day = time - time % 24000L;
        overworld.setDayTime(day + 13000L);
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 60, 3, 1, 3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.6f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_darkrai"), false);
    }

    /** Rayquaza: launches the player high into the sky, then they glide down slowly. */
    private static void skyLaunch(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        player.setDeltaMovement(look.x * 0.6, 3.2, look.z * 0.6);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 30));
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 60, 0.8, 0.3, 0.8, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.cobblemonranger.sign_rayquaza"), false);
    }
}
