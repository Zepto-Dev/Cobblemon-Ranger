package dev.zepto.cobblemonranger.rescue;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.browser.BrowserManager;
import dev.zepto.cobblemonranger.browser.GuidePage;
import dev.zepto.cobblemonranger.capture.CaptureManager;
import dev.zepto.cobblemonranger.capture.FriendManager;
import dev.zepto.cobblemonranger.capture.PartnerManager;
import dev.zepto.cobblemonranger.config.RangerConfig;
import dev.zepto.cobblemonranger.field.FieldMoveParticles;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerProgress;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.registry.ModAttachments;
import dev.zepto.cobblemonranger.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Rescue events: now and then a wild Pokémon near a Ranger gets trapped, and freeing it with the right field move
 * makes it a friend. Events only use chunks that are already loaded, belong to one player, and always clean up their
 * trap blocks: on success, after {@code rescueEscapeMinutes}, or when the trapped Pokémon disappears.
 */
public final class RescueManager {
    public static final String TAG = CobblemonRanger.MOD_ID + ":rescue";
    private static final int MIN_DISTANCE = 20;
    private static final int MAX_DISTANCE = 40;
    private static final int SPOT_TRIES = 16;
    private static final int RETRY_TICKS = 20 * 30;
    private static final int SPACING = 24;
    private static final int CLEAR_RADIUS = 6;
    private static final int MISSING_SECONDS = 5;
    private static final int SPOOK_RADIUS = 12;
    public static final int RANGER_POINTS_IF_FULL = 5;

    private RescueManager() {
    }

    // ------------------------------------------------------------------ queries

    /** True while a running rescue event still needs the trap block at {@code pos}. */
    public static boolean isActiveTrap(ServerLevel level, BlockPos pos) {
        return RescueData.get(level).activeAt(pos) != null;
    }

    /** A Pokémon (or mob) that belongs to a rescue event. */
    public static boolean isTrapped(Entity entity) {
        return entity.getPersistentData().hasUUID(TAG);
    }

    /** Tells the player how to free a trapped Pokémon they tried to capture. */
    public static void explainTrapped(ServerPlayer player, PokemonEntity entity) {
        RescueData.Event event = entity.level() instanceof ServerLevel level
                ? RescueData.get(level).byId(entity.getPersistentData().getUUID(TAG)) : null;
        if (event == null) {
            return;
        }
        player.displayClientMessage(Component.translatable("message.cobblemonranger.rescue_free_first",
                entity.getName(), event.scenario.move().displayName(), event.scenario.move().typeHint()), true);
    }

    private static RescueData.Event activeEventOf(MinecraftServer server, UUID owner) {
        for (ServerLevel level : server.getAllLevels()) {
            for (RescueData.Event event : RescueData.get(level).events()) {
                if (!event.ended && event.owner.equals(owner)) {
                    return event;
                }
            }
        }
        return null;
    }

    private static ItemStack findStyler(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof StylerItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof StylerItem) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof StylerItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ starting an event

    private static long nextDelay(RandomSource random) {
        int min = RangerConfig.current(RangerConfig.RESCUE_MIN_MINUTES);
        int max = Math.max(min, RangerConfig.current(RangerConfig.RESCUE_MAX_MINUTES));
        return (min * 60L + random.nextInt((max - min) * 60 + 1)) * 20L;
    }

    /**
     * Tries to trap a Pokémon near the player. {@code forced} (the command) ignores the timer; {@code only} picks the
     * scenario. Returns a message key explaining a failure, or null on success.
     */
    public static String start(ServerPlayer player, RescueScenario only, boolean forced) {
        if (activeEventOf(player.server, player.getUUID()) != null) {
            return "message.cobblemonranger.rescue_already";
        }
        ItemStack styler = findStyler(player);
        if (styler.isEmpty()) {
            return "message.cobblemonranger.rescue_no_styler";
        }
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < SPOT_TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos center = findGround(level, x, player.getBlockY(), z);
            if (center == null) {
                continue;
            }
            RescueScenario scenario = only != null ? only : chooseScenario(level, center, random);
            if (scenario == null || !fits(level, player, center, scenario)) {
                continue;
            }
            String species = RescueSpecies.pick(scenario, level.getBiome(center), random);
            if (species == null) {
                continue;
            }
            if (spawn(player, level, center, scenario, species, styler)) {
                return null;
            }
        }
        return forced ? "message.cobblemonranger.rescue_no_spot" : null;
    }

    /** The first standing spot (sturdy natural ground, three free blocks above) within 12 blocks of {@code y}. */
    private static BlockPos findGround(ServerLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 12; dy >= -12; dy--) {
            pos.set(x, y + dy, z);
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState ground = level.getBlockState(pos.below());
            if (isNaturalGround(ground) && ground.isFaceSturdy(level, pos.below(), Direction.UP)
                    && isFree(level, pos) && isFree(level, pos.above()) && isFree(level, pos.above(2))) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean isNaturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.SOUL_SAND) || state.is(BlockTags.NYLIUM) || state.is(Blocks.END_STONE);
    }

    /** Air, or a plant or snow layer that a trap block may replace. Never water or lava. */
    private static boolean isFree(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced());
    }

    private static RescueScenario chooseScenario(ServerLevel level, BlockPos center, RandomSource random) {
        Holder<Biome> biome = level.getBiome(center);
        BlockState ground = level.getBlockState(center.below());
        boolean nether = biome.is(BiomeTags.IS_NETHER);
        boolean hot = biome.value().getBaseTemperature() > 1.0f || nether;
        boolean cold = biome.value().coldEnoughToSnow(center);
        boolean underground = !level.canSeeSky(center);
        int[] weights = new int[RescueScenario.values().length];
        for (RescueScenario scenario : RescueScenario.values()) {
            int w = RescueSpecies.hasAny(scenario) ? 2 : 0;
            switch (scenario) {
                case RUBBLE -> w *= underground || ground.is(BlockTags.BASE_STONE_OVERWORLD)
                        || biome.is(BiomeTags.IS_MOUNTAIN) ? 3 : 1;
                case TANGLE -> w *= nether || underground ? 0
                        : biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) ? 3 : 1;
                case FIRE -> w *= nether ? 3 : cold ? 0 : 1;
                case ICE -> w *= hot ? 0 : cold ? 4 : 1;
                case SAND -> w *= ground.is(BlockTags.SAND) || biome.is(BiomeTags.IS_BEACH) ? 4 : nether ? 0 : 1;
                case MOBS -> w *= underground || level.isNight() ? 2 : 1;
            }
            weights[scenario.ordinal()] = w;
        }
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        if (total == 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (RescueScenario scenario : RescueScenario.values()) {
            roll -= weights[scenario.ordinal()];
            if (roll < 0) {
                return scenario;
            }
        }
        return null;
    }

    /** Checks the whole trap area: free space, solid ground, no player builds nearby, claims, other events. */
    private static boolean fits(ServerLevel level, ServerPlayer player, BlockPos center, RescueScenario scenario) {
        for (ServerLevel other : player.server.getAllLevels()) {
            if (other != level) {
                continue;
            }
            for (RescueData.Event event : RescueData.get(level).events()) {
                if (event.center.closerThan(center, SPACING)) {
                    return false;
                }
            }
        }
        int r = scenario.radius();
        if (!level.isLoaded(center.offset(-r - 1, 0, -r - 1)) || !level.isLoaded(center.offset(r + 1, 0, r + 1))) {
            return false;
        }
        // No chests, beds, furnaces or other block entities near: that's somebody's base.
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-CLEAR_RADIUS, -3, -CLEAR_RADIUS),
                center.offset(CLEAR_RADIUS, 4, CLEAR_RADIUS))) {
            if (level.getBlockEntity(pos) != null) {
                return false;
            }
        }
        if (scenario == RescueScenario.MOBS) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos spot = center.relative(dir, 2);
                if (!isFree(level, spot) || !isFree(level, spot.above())
                        || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                    return false;
                }
            }
            return mayPlace(level, player, center);
        }
        for (BlockPos pos : scenario.trapPositions(center)) {
            if (!isFree(level, pos) || !mayPlace(level, player, pos)) {
                return false;
            }
            if (pos.getY() == center.getY()) {
                BlockPos below = pos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Claim mods hook block placing, so an event never drops blocks into a protected area. */
    private static boolean mayPlace(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return level.mayInteract(player, pos) && !EventHooks.onBlockPlace(player,
                BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }

    private static boolean spawn(ServerPlayer player, ServerLevel level, BlockPos center, RescueScenario scenario,
                                 String species, ItemStack styler) {
        int partyLevel = 0;
        int count = 0;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            partyLevel += pokemon.getLevel();
            count++;
        }
        RandomSource random = player.getRandom();
        int cap = StylerProgress.get(styler).captureLevelCap();
        int lvl = Mth.clamp((count == 0 ? 5 : partyLevel / count) - 3 + random.nextInt(5), 2, Math.max(2, cap));

        UUID id = UUID.randomUUID();
        PokemonEntity entity;
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse(species + " level=" + lvl + " uncatchable").create();
            entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        } catch (RuntimeException e) {
            CobblemonRanger.LOGGER.warn("Could not trap a {} for a rescue event", species, e);
            return false;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        entity.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, facing.toYRot(), 0);
        entity.setNoAi(true);
        entity.setInvulnerable(true);
        entity.setPersistenceRequired();
        entity.getEntityData().set(PokemonEntity.Companion.getUNBATTLEABLE(), true);
        entity.getPersistentData().putUUID(TAG, id);

        long now = RangerManager.now(player);
        List<BlockPos> blocks = scenario.trapPositions(center);
        RescueData data = RescueData.get(level);
        RescueData.Event event = new RescueData.Event(id, player.getUUID(), scenario, center, blocks,
                entity.getUUID(), List.of(), now + RangerConfig.current(RangerConfig.RESCUE_ESCAPE_MINUTES) * 1200L,
                false);
        // Registered before anything appears, so the trap blocks and entities recognise their event.
        data.add(event);
        if (!level.addFreshEntity(entity)) {
            data.remove(event);
            return false;
        }
        BlockState trap = scenario.block() == null ? null : scenario.block().defaultBlockState();
        for (BlockPos pos : blocks) {
            level.setBlock(pos, trap, 3);
        }
        if (scenario == RescueScenario.MOBS) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (dir == facing.getOpposite()) {
                    continue; // three mobs, leaving the side it looks out of
                }
                Mob mob = spawnMob(level, center.relative(dir, 2), center, id, random);
                if (mob != null) {
                    event.mobs.add(mob.getUUID());
                }
            }
            data.setDirty();
        }
        level.playSound(null, center, SoundEvents.FOX_SCREECH, SoundSource.NEUTRAL, 0.8f, 1.4f);
        alert(player, entity, event);
        return true;
    }

    private static Mob spawnMob(ServerLevel level, BlockPos pos, BlockPos center, UUID id, RandomSource random) {
        boolean nether = level.getBiome(pos).is(BiomeTags.IS_NETHER);
        EntityType<? extends Mob> type = nether
                ? (random.nextBoolean() ? EntityType.WITHER_SKELETON : EntityType.PIGLIN_BRUTE)
                : switch (random.nextInt(3)) {
                    case 0 -> EntityType.ZOMBIE;
                    case 1 -> EntityType.SKELETON;
                    default -> EntityType.SPIDER;
                };
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        Vec3 look = Vec3.atCenterOf(center).subtract(Vec3.atCenterOf(pos));
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        // They only menace the Pokémon: held in place, kept from burning in daylight or wandering off.
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        mob.getPersistentData().putUUID(TAG, id);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void alert(ServerPlayer player, PokemonEntity entity, RescueData.Event event) {
        BlockPos center = event.center;
        int dx = center.getX() - player.getBlockX();
        int dz = center.getZ() - player.getBlockZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        double yaw = Math.atan2(-dx, dz) * Mth.RAD_TO_DEG; // 0 = south, like Minecraft yaw
        int index = Math.floorMod(Math.round((float) (yaw / 45.0)), 8);
        Component direction = Component.translatable("direction.cobblemonranger." + names[index]);
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.rescue_alert", entity.getName(),
                event.scenario.trappedText(), distance, direction).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.rescue_hint",
                event.scenario.move().displayName(), event.scenario.move().typeHint(),
                RangerConfig.current(RangerConfig.RESCUE_ESCAPE_MINUTES)).withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
        BrowserManager.unlock(player, GuidePage.RESCUES);
    }

    // ------------------------------------------------------------------ freeing

    /**
     * A field move used on a trap block. Returns 1 when it freed the Pokémon, 0 for the wrong move, or -1 when the
     * player was told why not (the event belongs to someone else).
     */
    public static int freeAt(ServerLevel level, ServerPlayer player, BlockPos pos,
                             dev.zepto.cobblemonranger.field.FieldMove move) {
        RescueData data = RescueData.get(level);
        RescueData.Event event = data.activeAt(pos);
        if (event == null || event.scenario.move() != move) {
            return 0;
        }
        if (!event.owner.equals(player.getUUID())) {
            ServerPlayer owner = player.server.getPlayerList().getPlayer(event.owner);
            player.displayClientMessage(Component.translatable("message.cobblemonranger.rescue_not_yours",
                    owner == null ? Component.literal("?") : owner.getDisplayName()), true);
            return -1;
        }
        complete(level, data, event, player);
        return 1;
    }

    /** Spook: scares off the mobs cornering the player's trapped Pokémon. Returns how many events it settled. */
    public static int onSpook(ServerLevel level, ServerPlayer player) {
        RescueData data = RescueData.get(level);
        int freed = 0;
        for (RescueData.Event event : List.copyOf(data.events())) {
            if (!event.ended && event.scenario == RescueScenario.MOBS && event.owner.equals(player.getUUID())
                    && event.center.closerToCenterThan(player.position(), SPOOK_RADIUS)) {
                complete(level, data, event, player);
                freed++;
            }
        }
        return freed;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !isTrapped(dead) || dead instanceof PokemonEntity) {
            return;
        }
        RescueData data = RescueData.get(level);
        RescueData.Event rescue = data.byId(dead.getPersistentData().getUUID(TAG));
        if (rescue == null || rescue.ended) {
            return;
        }
        rescue.mobs.remove(dead.getUUID());
        data.setDirty();
        if (rescue.mobs.isEmpty()) {
            // Fighting the mobs off works too.
            complete(level, data, rescue, level.getServer().getPlayerList().getPlayer(rescue.owner));
        }
    }

    private static void complete(ServerLevel level, RescueData data, RescueData.Event event, ServerPlayer player) {
        event.ended = true;
        data.setDirty();
        Vec3 at = Vec3.atBottomCenterOf(event.center);
        level.sendParticles(FieldMoveParticles.of(event.scenario.move()), at.x, at.y + 1, at.z, 30, 1.2, 0.8, 1.2,
                0.03);
        removeBlocks(level, data, event);
        removeMobs(level, event, true);

        Entity found = level.getEntity(event.pokemon);
        if (!(found instanceof PokemonEntity entity)) {
            cleanUp(level, data, event);
            return;
        }
        if (player == null) {
            vanish(level, entity);
            cleanUp(level, data, event);
            return;
        }
        Pokemon pokemon = entity.getPokemon();
        Component name = entity.getName();
        ItemStack styler = findStyler(player);
        level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ(),
                8, entity.getBbWidth() / 2, 0.3, entity.getBbWidth() / 2, 0.1);
        level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.6f);

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        boolean joined = party.getFirstAvailablePosition() != null && !CaptureManager.isCapturing(player)
                && FriendManager.befriend(player, entity);
        if (joined) {
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.rescue_joined", name)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            vanish(level, entity);
            int oldRank = RangerManager.get(player).rank();
            RangerManager.update(player, d -> d.withPoints(d.points() + RANGER_POINTS_IF_FULL));
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.rescue_thanks", name,
                    RANGER_POINTS_IF_FULL).withStyle(ChatFormatting.GREEN));
            MissionManager.announceRankUp(player, oldRank, RangerManager.get(player).rank());
        }
        if (!styler.isEmpty()) {
            StylerItem.addXp(player, styler, 20 + pokemon.getLevel() / 2);
            BrowserManager.onCapture(player, pokemon, styler);
        }
        PartnerManager.rewardPartner(player, 2);
        MissionManager.onRescue(player);
        cleanUp(level, data, event);
    }

    /** The Pokémon heads off on its own (no Ranger to join, or its time ran out). */
    private static void vanish(ServerLevel level, Entity entity) {
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.5, entity.getZ(), 12, 0.3, 0.4, 0.3,
                0.02);
        entity.discard();
    }

    private static void removeBlocks(ServerLevel level, RescueData data, RescueData.Event event) {
        Iterator<BlockPos> it = event.blocks.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockState(pos).getBlock() instanceof RescueBlock) {
                level.removeBlock(pos, false);
            }
            it.remove();
            data.setDirty();
        }
    }

    private static void removeMobs(ServerLevel level, RescueData.Event event, boolean scared) {
        for (UUID id : List.copyOf(event.mobs)) {
            Entity mob = level.getEntity(id);
            if (mob != null) {
                if (scared) {
                    level.sendParticles(FieldMoveParticles.spook(), mob.getX(), mob.getEyeY(), mob.getZ(), 6, 0.3, 0.3,
                            0.3, 0.02);
                    level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.4, 0.3,
                            0.02);
                }
                mob.discard();
                event.mobs.remove(id);
            }
        }
        if (scared && event.scenario == RescueScenario.MOBS) {
            level.playSound(null, event.center, SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 1f, 1.4f);
        }
    }

    /** Forgets the event once nothing of it is left in the world. */
    private static void cleanUp(ServerLevel level, RescueData data, RescueData.Event event) {
        if (event.blocks.isEmpty() && event.mobs.isEmpty() && level.getEntity(event.pokemon) == null) {
            data.remove(event);
        }
    }

    /** Ends an event without a rescue: time ran out, or the Pokémon disappeared. */
    private static void escape(ServerLevel level, RescueData data, RescueData.Event event) {
        event.ended = true;
        data.setDirty();
        Entity entity = level.getEntity(event.pokemon);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(event.owner);
        if (owner != null) {
            Component name = entity != null ? entity.getName() : Component.translatable("message.cobblemonranger.rescue_it");
            owner.sendSystemMessage(Component.translatable("message.cobblemonranger.rescue_escaped", name)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (entity != null) {
            vanish(level, entity);
        }
        removeBlocks(level, data, event);
        removeMobs(level, event, false);
        cleanUp(level, data, event);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 == 0) {
            beams(server);
        }
        if (tick % 20 != 0) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            RescueData data = RescueData.get(level);
            for (RescueData.Event rescue : List.copyOf(data.events())) {
                if (rescue.ended) {
                    // Leftovers from an event whose chunk was unloaded when it ended.
                    removeBlocks(level, data, rescue);
                    removeMobs(level, rescue, false);
                    Entity entity = level.getEntity(rescue.pokemon);
                    if (entity != null) {
                        entity.discard();
                    }
                    cleanUp(level, data, rescue);
                    continue;
                }
                if (now >= rescue.expiresAt) {
                    escape(level, data, rescue);
                    continue;
                }
                if (level.isLoaded(rescue.center) && level.getEntity(rescue.pokemon) == null) {
                    if (++rescue.missing >= MISSING_SECONDS) {
                        escape(level, data, rescue); // despawned or removed by another mod
                    }
                } else {
                    rescue.missing = 0;
                }
            }
        }
        if (!RangerConfig.RESCUE_EVENTS.get()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive() || findStyler(player).isEmpty()) {
                continue;
            }
            long next = player.getData(ModAttachments.NEXT_RESCUE);
            if (next == 0) {
                player.setData(ModAttachments.NEXT_RESCUE, now + nextDelay(player.getRandom()));
                continue;
            }
            if (now < next || CaptureManager.isCapturing(player)
                    || activeEventOf(server, player.getUUID()) != null) {
                continue;
            }
            start(player, null, false);
            boolean started = activeEventOf(server, player.getUUID()) != null;
            player.setData(ModAttachments.NEXT_RESCUE, now + (started ? nextDelay(player.getRandom()) : RETRY_TICKS));
        }
    }

    /** The column of sparkles over each trapped Pokémon, shown only to its own Ranger. */
    private static void beams(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (RescueData.Event rescue : RescueData.get(level).events()) {
                if (rescue.ended) {
                    continue;
                }
                ServerPlayer owner = server.getPlayerList().getPlayer(rescue.owner);
                if (owner == null || owner.level() != level) {
                    continue;
                }
                Vec3 at = Vec3.atBottomCenterOf(rescue.center);
                for (int i = 0; i < 4; i++) {
                    double y = at.y + 2.5 + level.random.nextDouble() * 18;
                    level.sendParticles(owner, ModParticles.RESCUE_BEAM.get(), true, at.x, y, at.z, 1, 0.08, 0.3,
                            0.08, 0.02);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // A trapped Pokémon or mob whose event is over (saved with an unloaded chunk) doesn't come back.
        if (event.getLevel() instanceof ServerLevel level && isTrapped(event.getEntity())) {
            RescueData.Event rescue = RescueData.get(level).byId(event.getEntity().getPersistentData().getUUID(TAG));
            if (rescue == null || rescue.ended) {
                event.setCanceled(true);
            }
        }
    }
}
