package dev.zepto.cobblemonranger.field;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * String Shot silk: a bridge block and a ladder block that drop nothing and remove themselves 30 seconds after they
 * appear. The removal is a scheduled block tick, which the world saves, so silk never outlives a restart.
 */
public final class SilkBlocks {
    public static final int LIFETIME_TICKS = 20 * 30;

    private SilkBlocks() {
    }

    private static void fade(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8,
                0.3, 0.3, 0.3, 0.01);
    }

    /** A thin silk mat to walk across a gap. */
    public static class Bridge extends Block {
        public static final MapCodec<Bridge> CODEC = simpleCodec(Bridge::new);
        private static final VoxelShape SHAPE = Block.box(0, 12, 0, 16, 16, 16);

        public Bridge(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
            if (!old.is(this)) {
                level.scheduleTick(pos, this, LIFETIME_TICKS);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            fade(level, pos);
        }
    }

    /** A silk ladder up a wall. */
    public static class Ladder extends LadderBlock {
        public Ladder(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
            if (!old.is(this)) {
                level.scheduleTick(pos, this, LIFETIME_TICKS);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            fade(level, pos);
        }
    }
}
