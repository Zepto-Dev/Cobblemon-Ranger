package dev.zepto.cobblemonranger.field;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.capture.FriendManager;
import dev.zepto.cobblemonranger.capture.PartnerManager;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Clears obstacles with the help of friend Pokémon or the Partner Pokémon. A friend goes back to the wild once it has
 * helped, as in the DS games; the Partner Pokémon stays but the styler spends a little energy.
 */
public final class FieldMoveManager {
    private static final int PARTNER_ENERGY_COST = 3;
    private static final int MAX_TREE_LOGS = 128;
    private static final int SOAK_RADIUS = 5;
    private static final int SURF_TICKS = 20 * 120;

    private FieldMoveManager() {
    }

    /** Works out which field move, if any, the block the player used the styler on calls for. */
    public static FieldMove moveFor(Level level, BlockPos pos) {
        if (hasFireOrLavaNear(level, pos, 2)) {
            return FieldMove.SOAK;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LOGS) || isCuttable(state)) {
            return FieldMove.CUT;
        }
        if (isSmashable(state)) {
            return FieldMove.ROCK_SMASH;
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
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GRAVEL);
    }

    private static boolean hasFireOrLavaNear(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || isLitCampfire(state) || state.getFluidState().is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLitCampfire(BlockState state) {
        return state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT);
    }

    /**
     * Finds a helper for the move and performs it, or tells the player why nobody could.
     */
    public static void use(ServerPlayer player, ItemStack styler, FieldMove move, BlockPos pos) {
        if (!player.mayBuild()) {
            return;
        }
        Pokemon helper = null;
        boolean partner = false;
        Pokemon partnerPokemon = PartnerManager.getPartner(player);
        if (partnerPokemon != null && move.canUse(partnerPokemon) && !partnerPokemon.isFainted()) {
            if (StylerItem.getEnergy(styler) >= PARTNER_ENERGY_COST) {
                helper = partnerPokemon;
                partner = true;
            }
        }
        if (helper == null) {
            for (Pokemon friend : FriendManager.getFriends(player)) {
                if (move.canUse(friend)) {
                    helper = friend;
                    break;
                }
            }
        }
        if (helper == null) {
            if (partnerPokemon != null && move.canUse(partnerPokemon)) {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_no_energy",
                        partnerPokemon.getSpecies().getTranslatedName(), move.displayName()), true);
            } else {
                player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_no_helper",
                        move.displayName(), move.typeHint()), false);
            }
            return;
        }

        ServerLevel level = player.serverLevel();
        int cleared = switch (move) {
            case CUT -> cut(level, player, pos);
            case ROCK_SMASH -> smash(level, player, pos);
            case SOAK -> soak(level, player, pos);
            case SURF -> surf(player);
        };
        if (cleared <= 0) {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_nothing",
                    move.displayName()), true);
            return;
        }

        Component name = helper.getSpecies().getTranslatedName();
        Vec3 center = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 12, 0.6, 0.6, 0.6, 0);
        if (partner) {
            StylerItem.drainEnergy(styler, PARTNER_ENERGY_COST);
            player.displayClientMessage(Component.translatable("message.cobblemonranger.field_move_partner", name,
                    move.displayName()), true);
        } else {
            Vec3 releaseAt = move == FieldMove.SURF ? player.position() : center.add(0, 0.5, 0);
            FriendManager.release(player, helper, releaseAt, Component.translatable(
                    "message.cobblemonranger.field_move_friend", name, move.displayName()));
        }
        MissionManager.onFieldMove(player, move);
    }

    private static boolean breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.mayInteract(player, pos)
                || CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state)
                .isCanceled()) {
            return false;
        }
        return level.destroyBlock(pos, true, player);
    }

    private static int cut(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState start = level.getBlockState(pos);
        int cleared = 0;
        if (start.is(BlockTags.LOGS)) {
            // Fell the whole tree: every log connected to the one that was used on, diagonals included.
            Set<BlockPos> seen = new HashSet<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> logs = new ArrayList<>();
            queue.add(pos.immutable());
            seen.add(pos.immutable());
            while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS) {
                BlockPos current = queue.poll();
                logs.add(current);
                for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, 0, -1), current.offset(1, 1, 1))) {
                    if (!seen.contains(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
                        BlockPos immutable = next.immutable();
                        seen.add(immutable);
                        queue.add(immutable);
                    }
                }
            }
            for (BlockPos log : logs) {
                if (breakBlock(level, player, log)) {
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
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1f, 0.8f);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.4, 0.4, 0.4, 0);
        }
        return cleared;
    }

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

    private static int soak(ServerLevel level, ServerPlayer player, BlockPos center) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SOAK_RADIUS, -SOAK_RADIUS, -SOAK_RADIUS),
                center.offset(SOAK_RADIUS, SOAK_RADIUS, SOAK_RADIUS))) {
            if (!level.mayInteract(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            BlockState replacement = null;
            if (state.is(BlockTags.FIRE)) {
                replacement = Blocks.AIR.defaultBlockState();
            } else if (isLitCampfire(state)) {
                replacement = state.setValue(CampfireBlock.LIT, false);
            } else if (fluid.is(FluidTags.LAVA) && state.is(Blocks.LAVA)) {
                replacement = fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState();
            }
            if (replacement != null) {
                level.setBlockAndUpdate(pos, replacement);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                        2, 0.2, 0.2, 0.2, 0.01);
                cleared++;
            }
        }
        for (Entity entity : level.getEntities((Entity) null, new AABB(center).inflate(SOAK_RADIUS),
                Entity::isOnFire)) {
            entity.clearFire();
            cleared++;
        }
        if (cleared > 0) {
            level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1f, 1f);
            level.sendParticles(ParticleTypes.SPLASH, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                    40, SOAK_RADIUS / 2.0, 1, SOAK_RADIUS / 2.0, 0.1);
        }
        return cleared;
    }

    private static int surf(ServerPlayer player) {
        if (player.hasEffect(ModEffects.SURFING)) {
            return 0;
        }
        player.addEffect(new MobEffectInstance(ModEffects.SURFING, SURF_TICKS, 0, false, false, true));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                SoundSource.PLAYERS, 1f, 1f);
        return 1;
    }
}
