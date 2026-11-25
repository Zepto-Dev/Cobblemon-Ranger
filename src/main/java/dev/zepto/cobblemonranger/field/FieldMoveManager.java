package dev.zepto.cobblemonranger.field;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.capture.FriendManager;
import dev.zepto.cobblemonranger.capture.PartnerManager;
import dev.zepto.cobblemonranger.config.RangerConfig;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.network.OpenFieldMovePickerPayload;
import dev.zepto.cobblemonranger.registry.ModBlocks;
import dev.zepto.cobblemonranger.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Field moves: friend Pokémon or the Partner Pokémon act on the world for the player. A friend goes back to the wild
 * once it has helped, as in the DS games; the Partner Pokémon stays but the styler spends a little energy.
 *
 * <p>Every move follows the rule that keeps field moves weaker than Ranger Signs: it needs a target in reach or gives
 * a short personal effect, never moves the player far, never touches weather or time, lasts at most 2 minutes and
 * reaches about 8 blocks.
 */
public final class FieldMoveManager {
    public static final int PARTNER_ENERGY_COST = 3;
    /** How far Telekinesis reaches. */
    public static final int TELEKINESIS_RANGE = 16;
    private static final int MAX_TREE_LOGS = 128;
    private static final int DOUSE_RADIUS = 5;
    private static final int AREA_RADIUS = 8;
    private static final int SURF_TICKS = 20 * 120;
    private static final int FLASH_TICKS = 20 * 120;
    private static final int TAILWIND_TICKS = 20 * 30;
    private static final int SPOOK_TICKS = 20 * 15;
    private static final int TEMP_BLOCK_TICKS = 20 * 30;
    private static final int PICK_TICKS = 20 * 30;
    public static final int MAX_CHARMED = 6;
    private static final int PATH_LENGTH = 8;
    private static final float HEADBUTT_POKEMON_CHANCE = 0.08f;
    /** Small Pokémon that live in trees and can drop out of one that is headbutted. */
    private static final List<String> TREE_POKEMON = List.of("pineco", "burmy", "combee", "aipom", "heracross",
            "hoothoot", "spearow", "pikachu", "starly", "pidgey", "caterpie", "weedle", "kakuna", "metapod",
            "seedot", "taillow", "pidove", "sewaddle", "skwovet");

    /** Server tick when each player's Partner Pokémon can use a field move again. */
    private static final Map<UUID, Integer> PARTNER_READY_AT = new HashMap<>();
    /** A targeted move picked from the move picker, preferred for the next right-click until it runs out. */
    private static final Map<UUID, Picked> PICKED = new HashMap<>();
    /** Trees already headbutted, per dimension and lowest log, with the in-game day they were. */
    private static final Map<String, Long> HEADBUTTED = new HashMap<>();
    private static final List<Spooked> SPOOKED = new ArrayList<>();
    private static final List<TempIce> TEMP_ICE = new ArrayList<>();

    private record Picked(FieldMove move, int until) {
    }

    private record Spooked(Mob mob, Vec3 from, int until) {
    }

    private record TempIce(ServerLevel level, BlockPos pos, int until) {
    }

    private record Helper(Pokemon pokemon, boolean partner) {
    }

    private FieldMoveManager() {
    }

    // ------------------------------------------------------------------ choosing a move

    /** Every targeted move that could act on this block, best first. Works on both sides. */
    public static List<FieldMove> candidatesFor(Level level, BlockPos pos) {
        List<FieldMove> moves = new ArrayList<>();
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (hasFireNear(level, pos, 2)) {
            moves.add(FieldMove.SURF);
        }
        if (fluid.is(FluidTags.WATER)) {
            moves.add(FieldMove.SURF);
            moves.add(FieldMove.FREEZE);
        }
        if (fluid.is(FluidTags.LAVA) || hasLavaNear(level, pos, 2)) {
            moves.add(FieldMove.FREEZE);
        }
        if (isMeltable(state) || isUnlit(state)) {
            moves.add(FieldMove.BURN);
        }
        if (state.is(BlockTags.LOGS) || isCuttable(state)) {
            moves.add(FieldMove.CUT);
        }
        if (state.is(BlockTags.LOGS) || state.getBlock() instanceof LeavesBlock) {
            moves.add(FieldMove.HEADBUTT);
        }
        if (isDiggable(state)) {
            moves.add(FieldMove.DIG);
        }
        if (isSmashable(state)) {
            moves.add(FieldMove.ROCK_SMASH);
        }
        if (StoneShapes.shape(state) != null) {
            moves.add(FieldMove.STONE_EDGE);
        }
        if (isTooHard(state)) {
            moves.add(FieldMove.OUTRAGE);
        }
        if (isTelekinetic(state)) {
            moves.add(FieldMove.TELEKINESIS);
        }
        return moves.stream().distinct().toList();
    }

    /**
     * Picks the move for a right-click on a block: a move chosen in the picker first, then the first one somebody in
     * the party can do, then the first one at all (so the player is told who could help).
     */
    public static FieldMove choose(ServerPlayer player, ItemStack styler, List<FieldMove> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        Picked picked = PICKED.get(player.getUUID());
        if (picked != null && picked.until >= player.server.getTickCount() && candidates.contains(picked.move)) {
            return picked.move;
        }
        for (FieldMove move : candidates) {
            if (findHelper(player, styler, move, false) != null) {
                return move;
            }
        }
        return candidates.get(0);
    }

    /** The openable block (lever, button, wooden door…) the player is looking at within Telekinesis range. */
    public static BlockHitResult telekinesisTarget(Level level, Entity player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(TELEKINESIS_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (isTelekinetic(level.getBlockState(hit.getBlockPos()))) {
            return hit;
        }
        // Levers and buttons are small: aiming at the block they sit on counts too.
        BlockPos front = hit.getBlockPos().relative(hit.getDirection());
        if (isTelekinetic(level.getBlockState(front))) {
            return new BlockHitResult(hit.getLocation(), hit.getDirection(), front, false);
        }
        return null;
    }

    public static boolean isCuttable(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(Blocks.VINE) || state.is(Blocks.COBWEB)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT);
    }

    public static boolean isSmashable(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
    }

    public static boolean isDiggable(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SUSPICIOUS_GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH);
    }

    /** Blocks too hard for Rock Smash that Outrage can break, one at a time. */
    public static boolean isTooHard(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.END_STONE)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.BLACKSTONE);
    }

    private static boolean isMeltable(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    private static boolean isUnlit(BlockState state) {
        return (state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES))
                && state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)
                && !(state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED));
    }

    /** Blocks a player could open by hand: Telekinesis never opens iron doors or other locked things. */
    public static boolean isTelekinetic(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.BUTTONS) || block instanceof LeverBlock || block instanceof FenceGateBlock
                || block instanceof BellBlock
                || block instanceof DoorBlock door && door.type().canOpenByHand()
                || block instanceof TrapDoorBlock && !state.is(Blocks.IRON_TRAPDOOR);
    }

    private static boolean hasFireNear(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || isLitCampfire(state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLavaNear(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLitCampfire(BlockState state) {
        return state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT);
    }

    // ------------------------------------------------------------------ helpers

    private static Helper findHelper(ServerPlayer player, ItemStack styler, FieldMove move, boolean report) {
        Pokemon partnerPokemon = PartnerManager.getPartner(player);
        if (partnerPokemon != null && move.canUse(partnerPokemon) && !partnerPokemon.isFainted()) {
            // A resting partner doesn't hand the move to a friend, which would send the friend away unasked.
            int now = player.server.getTickCount();
            int readyAt = PARTNER_READY_AT.getOrDefault(player.getUUID(), 0);
            if (now < readyAt) {
                if (report) {
                    int seconds = (readyAt - now + 19) / 20;
                    player.displayClientMessage(Component.translatable(
                            "message.cobblemonranger.field_move_cooldown",
                            partnerPokemon.getSpecies().getTranslatedName(), seconds), true);
                }
                return null;
            }
            if (StylerItem.getEnergy(styler) >= PARTNER_ENERGY_COST) {
                return new Helper(partnerPokemon, true);
            }
        }
        for (Pokemon friend : FriendManager.getFriends(player)) {
            if (move.canUse(friend)) {
                return new Helper(friend, false);
            }
        }
        if (report) {
            if (partnerPokemon != null && move.canUse(partnerPokemon)) {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_no_energy",
                        partnerPokemon.getSpecies().getTranslatedName(), move.displayName()), true);
            } else {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_no_helper",
                        move.displayName(), move.typeHint()), false);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the move picker

    /** Sneak + right-click: shows every move somebody in the party can do right now. */
    public static void openPicker(ServerPlayer player, ItemStack styler) {
        Pokemon partner = PartnerManager.getPartner(player);
        List<OpenFieldMovePickerPayload.Entry> entries = new ArrayList<>();
        for (FieldMove move : FieldMove.values()) {
            Helper helper = findHelper(player, styler, move, false);
            boolean partnerCan = partner != null && move.canUse(partner) && !partner.isFainted();
            if (helper != null || partnerCan) {
                // Shown as a Partner move when the partner does it (or would, once rested or recharged).
                entries.add(new OpenFieldMovePickerPayload.Entry(move.id(), helper == null || helper.partner));
            }
        }
        if (entries.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_none"), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenFieldMovePickerPayload(entries));
    }

    /** A move chosen in the picker: moves around the player happen now; aimed moves wait for the next right-click. */
    public static void pick(ServerPlayer player, String id) {
        FieldMove move = FieldMove.byId(id);
        ItemStack styler = player.getMainHandItem();
        if (move == null || !(styler.getItem() instanceof StylerItem)) {
            return;
        }
        if (move.targeted()) {
            if (findHelper(player, styler, move, true) == null) {
                return;
            }
            PICKED.put(player.getUUID(), new Picked(move, player.server.getTickCount() + PICK_TICKS));
            player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_ready",
                    move.displayName(), move.description()).withStyle(ChatFormatting.AQUA), true);
            return;
        }
        use(player, styler, move, player.blockPosition(), Direction.UP);
    }

    // ------------------------------------------------------------------ using a move

    /** Finds a helper for the move and performs it, or tells the player why nobody could. */
    public static void use(ServerPlayer player, ItemStack styler, FieldMove move, BlockPos pos, Direction face) {
        if (!player.mayBuild()) {
            return;
        }
        Helper helper = findHelper(player, styler, move, true);
        if (helper == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int done = switch (move) {
            case HEADBUTT -> headbutt(level, player, pos);
            case BURN -> burn(level, player, pos);
            case SURF -> hasFireNear(level, pos, 2) ? douse(level, player, pos) : surf(player);
            case CUT -> cut(level, player, pos);
            case FLASH -> flash(level, player);
            case FREEZE -> level.getFluidState(pos).is(FluidTags.LAVA) || hasLavaNear(level, pos, 2)
                    ? coolLava(level, player, pos) : freezePath(level, player, pos);
            case ROCK_SMASH -> smash(level, player, pos);
            case DECOMPOSE -> decompose(level, player);
            case DIG -> dig(level, player, pos, face);
            case TAILWIND -> tailwind(level, player);
            case TELEKINESIS -> telekinesis(level, player, pos);
            case STRING_SHOT -> stringShot(level, player);
            case STONE_EDGE -> stoneEdge(level, player, pos);
            case SPOOK -> spook(level, player);
            case OUTRAGE -> outrage(level, player, pos);
            case SNATCH -> snatch(level, player);
            case MAGNET -> magnet(level, player);
            case CHARM -> charm(level, player);
        };
        if (done <= 0) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_nothing_" + move.id()),
                    true);
            return;
        }
        PICKED.remove(player.getUUID());

        Component name = helper.pokemon.getSpecies().getTranslatedName();
        Vec3 center = move.targeted() ? Vec3.atCenterOf(pos) : player.position().add(0, 1, 0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 12, 0.6, 0.6, 0.6, 0);
        if (helper.partner) {
            StylerItem.drainEnergy(styler, PARTNER_ENERGY_COST);
            PARTNER_READY_AT.put(player.getUUID(),
                    player.server.getTickCount() + RangerConfig.partnerFieldMoveCooldownTicks());
            player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_partner", name,
                    move.displayName()), true);
        } else {
            Vec3 releaseAt = move.targeted() && move != FieldMove.SURF && move != FieldMove.TELEKINESIS
                    ? center.add(0, 0.5, 0) : player.position();
            FriendManager.release(player, helper.pokemon, releaseAt, Component.translatable(
                    "message.cobblemonranger.field_move_friend", name, move.displayName()));
        }
        MissionManager.onFieldMove(player, move);
    }

    private static boolean breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !mayChange(level, player, pos, state)) {
            return false;
        }
        return level.destroyBlock(pos, true, player);
    }

    /** Claim mods hook the break event, so every block a move changes or removes asks it first. */
    private static boolean mayChange(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        return level.mayInteract(player, pos) && !CommonHooks.fireBlockBreak(level,
                player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled();
    }

    private static boolean mayPlace(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return level.mayInteract(player, pos) && !EventHooks.onBlockPlace(player,
                BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }

    private static void sound(ServerLevel level, BlockPos pos, net.minecraft.sounds.SoundEvent sound, float pitch) {
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 1f, pitch);
    }

    private static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread,
                              double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    // Normal ---------------------------------------------------------------

    private static int headbutt(ServerLevel level, ServerPlayer player, BlockPos pos) {
        List<BlockPos> logs = connectedLogs(level, pos.immutable(), level.getBlockState(pos).is(BlockTags.LOGS));
        List<BlockPos> leaves = new ArrayList<>();
        BlockPos lowest = null;
        for (BlockPos log : logs) {
            if (lowest == null || log.getY() < lowest.getY()) {
                lowest = log;
            }
        }
        if (lowest == null) {
            return 0;
        }
        for (BlockPos log : logs) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-2, -1, -2), log.offset(2, 2, 2))) {
                BlockState state = level.getBlockState(near);
                if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
                    leaves.add(near.immutable());
                }
            }
        }
        if (leaves.isEmpty()) {
            return 0; // a log wall or a player's build, not a tree
        }
        long day = level.getDayTime() / 24000L;
        String key = level.dimension().location() + "@" + lowest.asLong();
        HEADBUTTED.values().removeIf(d -> d < day - 1);
        if (HEADBUTTED.getOrDefault(key, Long.MIN_VALUE) >= day) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.headbutt_bare"), true);
            return -1;
        }
        HEADBUTTED.put(key, day);
        var random = level.getRandom();
        BlockPos leaf = leaves.get(random.nextInt(leaves.size()));
        Block leafBlock = level.getBlockState(leaf).getBlock();
        String leafName = BuiltInRegistries.BLOCK.getKey(leafBlock).getPath();
        List<ItemStack> drops = new ArrayList<>();
        if (random.nextFloat() < 0.6f) {
            drops.add(new ItemStack(Items.STICK, 1 + random.nextInt(2)));
        }
        if (random.nextFloat() < 0.35f) {
            ResourceLocation sapling = BuiltInRegistries.BLOCK.getKey(leafBlock).withPath(
                    leafName.replace("_leaves", "_sapling"));
            Item item = BuiltInRegistries.ITEM.get(sapling);
            if (item != Items.AIR) {
                drops.add(new ItemStack(item));
            }
        }
        if ((leafBlock == Blocks.OAK_LEAVES || leafBlock == Blocks.DARK_OAK_LEAVES) && random.nextFloat() < 0.3f) {
            drops.add(new ItemStack(Items.APPLE));
        }
        if (leafBlock == Blocks.JUNGLE_LEAVES && random.nextFloat() < 0.3f) {
            drops.add(new ItemStack(Items.COCOA_BEANS, 1 + random.nextInt(2)));
        }
        if (drops.isEmpty()) {
            drops.add(new ItemStack(Items.STICK));
        }
        for (ItemStack drop : drops) {
            BlockPos from = leaves.get(random.nextInt(leaves.size()));
            ItemEntity item = new ItemEntity(level, from.getX() + 0.5, from.getY(), from.getZ() + 0.5, drop);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        for (int i = 0; i < 6; i++) {
            BlockPos from = leaves.get(random.nextInt(leaves.size()));
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    level.getBlockState(from)), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 4, 0.4, 0.2, 0.4, 0);
        }
        sound(level, pos, SoundEvents.WOOD_HIT, 0.7f);
        sound(level, leaf, SoundEvents.AZALEA_LEAVES_FALL, 1f);
        if (random.nextFloat() < HEADBUTT_POKEMON_CHANCE) {
            spawnTreePokemon(level, player, lowest, leaves);
        }
        return drops.size();
    }

    private static void spawnTreePokemon(ServerLevel level, ServerPlayer player, BlockPos trunk,
                                         List<BlockPos> leaves) {
        List<String> names = new ArrayList<>();
        for (String name : TREE_POKEMON) {
            Species species = PokemonSpecies.getByName(name);
            if (species != null && species.getImplemented()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return;
        }
        // Drops out next to the trunk, at the bottom of the canopy.
        int canopy = leaves.stream().mapToInt(BlockPos::getY).min().orElse(trunk.getY() + 2);
        BlockPos spot = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = new BlockPos(trunk.getX(), canopy - 1, trunk.getZ()).relative(dir);
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                spot = candidate;
                break;
            }
        }
        if (spot == null) {
            return;
        }
        int partyLevel = 0;
        int count = 0;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            partyLevel += pokemon.getLevel();
            count++;
        }
        int lvl = Mth.clamp((count == 0 ? 5 : partyLevel / count) - 3 + level.getRandom().nextInt(5), 2, 80);
        String name = names.get(level.getRandom().nextInt(names.size()));
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse(name + " level=" + lvl).create();
            PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
            entity.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
            level.addFreshEntity(entity);
            player.displayClientMessage(Component.translatable("message.cobblemonranger.headbutt_pokemon",
                    pokemon.getSpecies().getTranslatedName()).withStyle(ChatFormatting.GREEN), false);
        } catch (RuntimeException e) {
            CobblemonRanger.LOGGER.warn("Could not drop {} out of a tree", name, e);
        }
    }

    private static List<BlockPos> connectedLogs(Level level, BlockPos start, boolean startIsLog) {
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        if (startIsLog) {
            queue.add(start);
            seen.add(start);
        } else {
            // Clicked the leaves: start from the nearest log under them.
            for (BlockPos near : BlockPos.betweenClosed(start.offset(-3, -4, -3), start.offset(3, 1, 3))) {
                if (level.getBlockState(near).is(BlockTags.LOGS)) {
                    BlockPos log = near.immutable();
                    queue.add(log);
                    seen.add(log);
                    break;
                }
            }
        }
        while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS) {
            BlockPos current = queue.poll();
            logs.add(current);
            for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                if (!seen.contains(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
                    BlockPos immutable = next.immutable();
                    seen.add(immutable);
                    queue.add(immutable);
                }
            }
        }
        return logs;
    }

    // Fire -----------------------------------------------------------------

    private static int burn(ServerLevel level, ServerPlayer player, BlockPos center) {
        int done = 0;
        boolean hot = level.dimensionType().ultraWarm();
        for (BlockPos target : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockPos pos = target.immutable();
            BlockState state = level.getBlockState(pos);
            if (isMeltable(state) && mayChange(level, player, pos, state)) {
                BlockState melted = (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) && !hot
                        ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlockAndUpdate(pos, melted);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        2, 0.2, 0.2, 0.2, 0.01);
                done++;
            } else if (isUnlit(state) && level.mayInteract(player, pos)) {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
                done++;
            }
        }
        if (done > 0) {
            sound(level, center, SoundEvents.FIRECHARGE_USE, 1.1f);
            burst(level, ParticleTypes.FLAME, Vec3.atCenterOf(center), 20, 0.7, 0.02);
        }
        return done;
    }

    // Water ----------------------------------------------------------------

    private static int douse(ServerLevel level, ServerPlayer player, BlockPos center) {
        int done = 0;
        for (BlockPos target : BlockPos.betweenClosed(center.offset(-DOUSE_RADIUS, -DOUSE_RADIUS, -DOUSE_RADIUS),
                center.offset(DOUSE_RADIUS, DOUSE_RADIUS, DOUSE_RADIUS))) {
            BlockPos pos = target.immutable();
            if (!level.mayInteract(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } else if (isLitCampfire(state)) {
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
            } else {
                continue;
            }
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    2, 0.2, 0.2, 0.2, 0.01);
            done++;
        }
        for (Entity entity : level.getEntities((Entity) null, new AABB(center).inflate(DOUSE_RADIUS),
                Entity::isOnFire)) {
            entity.clearFire();
            done++;
        }
        if (done > 0) {
            sound(level, center, SoundEvents.FIRE_EXTINGUISH, 1f);
            level.sendParticles(ParticleTypes.SPLASH, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                    40, DOUSE_RADIUS / 2.0, 1, DOUSE_RADIUS / 2.0, 0.1);
        }
        return done;
    }

    private static int surf(ServerPlayer player) {
        if (player.hasEffect(ModEffects.SURFING)) {
            return 0;
        }
        player.addEffect(new MobEffectInstance(ModEffects.SURFING, SURF_TICKS, 0, false, false, true));
        sound(player.serverLevel(), player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f);
        return 1;
    }

    // Grass ----------------------------------------------------------------

    private static int cut(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState start = level.getBlockState(pos);
        int cleared = 0;
        if (start.is(BlockTags.LOGS)) {
            // Fell the whole tree: every log connected to the one that was used on.
            for (BlockPos log : connectedLogs(level, pos.immutable(), true)) {
                if (log.getY() >= pos.getY() && breakBlock(level, player, log)) {
                    cleared++;
                }
            }
        } else {
            for (BlockPos target : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
                if (isCuttable(level.getBlockState(target)) && breakBlock(level, player, target.immutable())) {
                    cleared++;
                }
            }
        }
        if (cleared > 0) {
            sound(level, pos, SoundEvents.SHEEP_SHEAR, 0.8f);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.4, 0.4, 0.4, 0);
        }
        return cleared;
    }

    // Electric -------------------------------------------------------------

    private static int flash(ServerLevel level, ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && current.getDuration() > 20 * 60) {
            return 0;
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, FLASH_TICKS, 0, false, false, true));
        Vec3 at = player.position().add(0, 1.2, 0);
        burst(level, FieldMoveParticles.flash(), at, 30, 1.5, 0.05);
        burst(level, ParticleTypes.FLASH, at, 1, 0, 0);
        sound(level, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, 2f);
        return 1;
    }

    // Ice ------------------------------------------------------------------

    private static int coolLava(ServerLevel level, ServerPlayer player, BlockPos center) {
        int done = 0;
        for (BlockPos target : BlockPos.betweenClosed(center.offset(-DOUSE_RADIUS, -DOUSE_RADIUS, -DOUSE_RADIUS),
                center.offset(DOUSE_RADIUS, DOUSE_RADIUS, DOUSE_RADIUS))) {
            BlockPos pos = target.immutable();
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (!state.is(Blocks.LAVA) || !level.mayInteract(player, pos)) {
                continue;
            }
            level.setBlockAndUpdate(pos, fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                    : Blocks.COBBLESTONE.defaultBlockState());
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                    2, 0.3, 0.1, 0.3, 0.01);
            done++;
        }
        if (done > 0) {
            sound(level, center, SoundEvents.LAVA_EXTINGUISH, 1f);
        }
        return done;
    }

    /** A 3-wide path of frosted ice across water, along the way the player faces, which melts after 30 s. */
    private static int freezePath(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        BlockPos start = clicked;
        while (level.getFluidState(start.above()).is(FluidTags.WATER)) {
            start = start.above();
        }
        Direction dir = player.getDirection();
        Direction side = dir.getClockWise();
        int done = 0;
        int until = player.server.getTickCount() + TEMP_BLOCK_TICKS;
        for (int i = 0; i < PATH_LENGTH; i++) {
            for (int s = -1; s <= 1; s++) {
                BlockPos pos = start.relative(dir, i).relative(side, s);
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.WATER) && state.getFluidState().isSource()
                        && level.getBlockState(pos.above()).isAir() && mayPlace(level, player, pos)) {
                    level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                    TEMP_ICE.add(new TempIce(level, pos, until + level.getRandom().nextInt(40)));
                    level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.1,
                            pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.01);
                    done++;
                }
            }
        }
        if (done > 0) {
            sound(level, start, SoundEvents.GLASS_PLACE, 1.4f);
        }
        return done;
    }

    // Fighting -------------------------------------------------------------

    private static int smash(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int cleared = 0;
        for (BlockPos target : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (isSmashable(level.getBlockState(target)) && breakBlock(level, player, target.immutable())) {
                cleared++;
            }
        }
        if (cleared > 0) {
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
            level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.3, 0.3, 0.3, 0);
        }
        return cleared;
    }

    // Poison ---------------------------------------------------------------

    /** Dropped rotten flesh, bones and compostable plant scraps nearby turn into bone meal. */
    private static int decompose(ServerLevel level, ServerPlayer player) {
        double meal = 0;
        int used = 0;
        Vec3 sum = Vec3.ZERO;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6))) {
            ItemStack stack = item.getItem();
            double each;
            if (stack.is(Items.BONE)) {
                each = 3; // the same as crafting
            } else if (stack.is(Items.ROTTEN_FLESH)) {
                each = 0.5;
            } else {
                float chance = ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
                if (chance <= 0) {
                    continue;
                }
                each = chance / 7.0; // a composter needs 7 layers on average chances
            }
            meal += each * stack.getCount();
            used += stack.getCount();
            sum = sum.add(item.position());
            level.sendParticles(FieldMoveParticles.decompose(), item.getX(), item.getY() + 0.3, item.getZ(),
                    6, 0.2, 0.2, 0.2, 0.01);
            item.discard();
        }
        if (used == 0) {
            return 0;
        }
        int whole = (int) meal;
        if (level.getRandom().nextDouble() < meal - whole) {
            whole++;
        }
        Vec3 at = sum.scale(1.0 / Math.max(1, used));
        while (whole > 0) {
            int n = Math.min(64, whole);
            whole -= n;
            ItemEntity out = new ItemEntity(level, at.x, at.y + 0.2, at.z, new ItemStack(Items.BONE_MEAL, n));
            out.setDefaultPickUpDelay();
            level.addFreshEntity(out);
        }
        sound(level, BlockPos.containing(at), SoundEvents.COMPOSTER_READY, 1f);
        return used;
    }

    // Ground ---------------------------------------------------------------

    /** Digs a 3x3 tunnel two blocks deep into soft ground, from the face that was clicked. */
    private static int dig(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        Direction into = face.getOpposite();
        Direction a = into.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        Direction b = into.getAxis() == Direction.Axis.Y ? Direction.EAST : into.getClockWise();
        int done = 0;
        for (int depth = 0; depth < 2; depth++) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    BlockPos target = pos.relative(into, depth).relative(a, i).relative(b, j);
                    BlockState state = level.getBlockState(target);
                    if (!isDiggable(state)) {
                        continue;
                    }
                    if (state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
                        if (unearth(level, player, target)) {
                            done++;
                        }
                        continue;
                    }
                    if (breakBlock(level, player, target)) {
                        done++;
                    }
                }
            }
        }
        if (done > 0) {
            sound(level, pos, SoundEvents.ROOTED_DIRT_BREAK, 0.8f);
        }
        return done;
    }

    /** Suspicious sand or gravel gives up its find, as if brushed, then is dug away like the rest. */
    private static boolean unearth(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BrushableBlock block)
                || !(level.getBlockEntity(pos) instanceof BrushableBlockEntity entity)
                || !mayChange(level, player, pos, state)) {
            return false;
        }
        entity.unpackLootTable(player);
        ItemStack find = entity.getItem().copy();
        level.setBlockAndUpdate(pos, block.getTurnsInto().defaultBlockState());
        if (!find.isEmpty()) {
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, find);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
            sound(level, pos, SoundEvents.BRUSH_SAND_COMPLETED, 1f);
        }
        return breakBlock(level, player, pos);
    }

    // Flying ---------------------------------------------------------------

    private static int tailwind(ServerLevel level, ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (current != null && current.getAmplifier() >= 1 && current.getDuration() > 20 * 10) {
            return 0;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, TAILWIND_TICKS, 1, false, false, true));
        Vec3 back = player.getLookAngle().multiply(-1, 0, -1).normalize();
        for (int i = 0; i < 16; i++) {
            Vec3 at = player.position().add(back.scale(1 + i * 0.2)).add(0, 0.3 + (i % 4) * 0.4, 0);
            level.sendParticles(FieldMoveParticles.tailwind(), at.x, at.y, at.z, 1, 0.3, 0.1, 0.3, 0.02);
        }
        sound(level, player.blockPosition(), SoundEvents.ELYTRA_FLYING, 1.6f);
        return 1;
    }

    // Psychic --------------------------------------------------------------

    private static int telekinesis(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = telekinesisTarget(level, player);
        if (hit == null || !hit.getBlockPos().equals(pos) || !level.mayInteract(player, pos)) {
            return 0;
        }
        if (CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit).isCanceled()) {
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.useWithoutItem(level, player, hit).consumesAction()) {
            return 0;
        }
        Vec3 from = player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(pos);
        int steps = (int) (from.distanceTo(to) * 2);
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, i / (double) steps);
            level.sendParticles(FieldMoveParticles.telekinesis(), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        burst(level, FieldMoveParticles.telekinesis(), to, 10, 0.4, 0.02);
        sound(level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f);
        return 1;
    }

    // Bug ------------------------------------------------------------------

    /**
     * Facing a wall: a silk ladder up it. Otherwise: a silk bridge out from the player's feet across a gap. The silk
     * is gone again after 30 seconds.
     */
    private static int stringShot(ServerLevel level, ServerPlayer player) {
        Direction dir = player.getDirection();
        BlockPos feet = player.blockPosition();
        BlockPos wall = feet.relative(dir);
        int done = 0;
        if (level.getBlockState(wall).isFaceSturdy(level, wall, dir.getOpposite()) && player.getXRot() < 30) {
            BlockState ladder = ModBlocks.SILK_LADDER.get().defaultBlockState().setValue(LadderBlock.FACING,
                    dir.getOpposite());
            for (int i = 0; i < PATH_LENGTH; i++) {
                BlockPos pos = feet.above(i);
                BlockPos behind = wall.above(i);
                if (!level.getBlockState(behind).isFaceSturdy(level, behind, dir.getOpposite())) {
                    break;
                }
                BlockState here = level.getBlockState(pos);
                if (here.is(ModBlocks.SILK_LADDER.get())) {
                    continue;
                }
                if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || !mayPlace(level, player, pos)) {
                    if (i == 0) {
                        continue;
                    }
                    break;
                }
                level.setBlockAndUpdate(pos, ladder);
                done++;
            }
        } else {
            BlockState silk = ModBlocks.SILK_BRIDGE.get().defaultBlockState();
            BlockPos row = feet.below();
            for (int i = 1; i <= PATH_LENGTH; i++) {
                BlockPos pos = row.relative(dir, i);
                BlockState here = level.getBlockState(pos);
                if (here.is(ModBlocks.SILK_BRIDGE.get())) {
                    continue;
                }
                if (!here.canBeReplaced()) {
                    break; // reached the other side
                }
                if (!mayPlace(level, player, pos)) {
                    break;
                }
                level.setBlockAndUpdate(pos, silk);
                done++;
            }
        }
        if (done > 0) {
            sound(level, feet, SoundEvents.WOOL_PLACE, 1.3f);
            Vec3 at = player.position().add(Vec3.atLowerCornerOf(dir.getNormal()).scale(1.5)).add(0, 0.5, 0);
            burst(level, FieldMoveParticles.stringShot(), at, 16, 0.8, 0.02);
        }
        return done;
    }

    // Rock -----------------------------------------------------------------

    private static int stoneEdge(ServerLevel level, ServerPlayer player, BlockPos center) {
        int done = 0;
        for (BlockPos target : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockPos pos = target.immutable();
            BlockState state = level.getBlockState(pos);
            Block shaped = StoneShapes.shape(state);
            if (shaped == null || !mayChange(level, player, pos, state)) {
                continue;
            }
            level.setBlockAndUpdate(pos, shaped.withPropertiesOf(state));
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
            done++;
        }
        if (done > 0) {
            sound(level, center, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1f);
        }
        return done;
    }

    // Ghost ----------------------------------------------------------------

    private static int spook(ServerLevel level, ServerPlayer player) {
        int done = 0;
        int until = player.server.getTickCount() + SPOOK_TICKS;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AREA_RADIUS),
                m -> m instanceof Enemy && m.isAlive())) {
            SPOOKED.removeIf(s -> s.mob == mob);
            SPOOKED.add(new Spooked(mob, player.position(), until));
            mob.setTarget(null);
            level.sendParticles(FieldMoveParticles.spook(), mob.getX(), mob.getEyeY() + 0.4, mob.getZ(),
                    4, 0.3, 0.2, 0.3, 0.01);
            done++;
        }
        if (done > 0) {
            sound(level, player.blockPosition(), SoundEvents.PHANTOM_AMBIENT, 1.4f);
        }
        return done;
    }

    // Dragon ---------------------------------------------------------------

    private static int outrage(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!isTooHard(level.getBlockState(pos)) || !breakBlock(level, player, pos)) {
            return 0;
        }
        sound(level, pos, SoundEvents.ENDER_DRAGON_GROWL, 1.6f);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                20, 0.4, 0.4, 0.4, 0.02);
        return 1;
    }

    // Dark -----------------------------------------------------------------

    /** Takes what the nearest hostile mob holds in its main hand. */
    private static int snatch(ServerLevel level, ServerPlayer player) {
        Mob target = null;
        double best = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AREA_RADIUS),
                m -> m instanceof Enemy && m.isAlive() && !m.getMainHandItem().isEmpty())) {
            double dist = mob.distanceToSqr(player);
            if (dist < best) {
                best = dist;
                target = mob;
            }
        }
        if (target == null) {
            return 0;
        }
        ItemStack taken = target.getMainHandItem().copy();
        target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.4, 0.3,
                0.02);
        sound(level, target.blockPosition(), SoundEvents.ITEM_PICKUP, 0.6f);
        return 1;
    }

    // Steel ----------------------------------------------------------------

    private static int magnet(ServerLevel level, ServerPlayer player) {
        int done = 0;
        AABB area = player.getBoundingBox().inflate(AREA_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, area,
                e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.isAlive())) {
            if (entity.distanceToSqr(player) < 2) {
                continue;
            }
            level.sendParticles(FieldMoveParticles.magnet(), entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    3, 0.1, 0.1, 0.1, 0);
            entity.teleportTo(player.getX(), player.getY() + 0.3, player.getZ());
            entity.setDeltaMovement(Vec3.ZERO);
            done++;
        }
        if (done > 0) {
            sound(level, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, 1.8f);
        }
        return done;
    }

    // Fairy ----------------------------------------------------------------

    private static int charm(ServerLevel level, ServerPlayer player) {
        int done = 0;
        AABB area = player.getBoundingBox().inflate(AREA_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m instanceof NeutralMob && m.isAlive())) {
            NeutralMob neutral = (NeutralMob) mob;
            if (neutral.isAngry() || mob.getTarget() != null) {
                neutral.stopBeingAngry();
                mob.setTarget(null);
                level.sendParticles(FieldMoveParticles.charm(), mob.getX(), mob.getEyeY() + 0.3, mob.getZ(),
                        3, 0.3, 0.2, 0.3, 0);
                done++;
            }
        }
        int charmed = 0;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, area,
                a -> a.isAlive() && a.getAge() == 0 && a.canFallInLove() && !a.isInLove())) {
            if (charmed >= MAX_CHARMED) {
                break;
            }
            animal.setInLove(player);
            level.sendParticles(FieldMoveParticles.charm(), animal.getX(), animal.getEyeY() + 0.3, animal.getZ(),
                    4, 0.3, 0.2, 0.3, 0);
            charmed++;
        }
        done += charmed;
        if (done > 0) {
            sound(level, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f);
        }
        return done;
    }

    // ------------------------------------------------------------------ ticking effects

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        Iterator<Spooked> spooked = SPOOKED.iterator();
        while (spooked.hasNext()) {
            Spooked s = spooked.next();
            if (s.until < now || !s.mob.isAlive()) {
                spooked.remove();
                continue;
            }
            s.mob.setTarget(null);
            if (now % 10 == 0) {
                Vec3 away = s.mob.position().subtract(s.from).multiply(1, 0, 1);
                if (away.lengthSqr() < 1e-3) {
                    away = new Vec3(1, 0, 0);
                }
                Vec3 to = s.mob.position().add(away.normalize().scale(AREA_RADIUS));
                s.mob.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
            }
        }
        Iterator<TempIce> ice = TEMP_ICE.iterator();
        while (ice.hasNext()) {
            TempIce t = ice.next();
            if (t.until > now) {
                continue;
            }
            ice.remove();
            if (t.level.isLoaded(t.pos) && t.level.getBlockState(t.pos).is(Blocks.FROSTED_ICE)) {
                t.level.setBlockAndUpdate(t.pos, Blocks.WATER.defaultBlockState());
            }
        }
        if (now % 200 == 0) {
            PICKED.values().removeIf(p -> p.until < now);
        }
    }
}
