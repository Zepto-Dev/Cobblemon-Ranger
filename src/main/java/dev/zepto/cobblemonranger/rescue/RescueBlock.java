package dev.zepto.cobblemonranger.rescue;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.serialization.MapCodec;
import dev.zepto.cobblemonranger.field.FieldMove;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rescue trap block: rubble, tangle, fire, ice or sand. It can't be mined, pushed or blown up (only the right field
 * move clears it), drops nothing, and never spreads. Each block checks every 10 seconds that its rescue event is still
 * running and removes itself if not, so a trap can't outlive its event even if the event data were lost.
 */
public class RescueBlock extends Block {
    public static final MapCodec<RescueBlock> CODEC = simpleCodec(p -> new RescueBlock(p, FieldMove.ROCK_SMASH, false));
    private static final int CHECK_TICKS = 200;
    private static final VoxelShape FIRE_OUTLINE = Block.box(0, 0, 0, 16, 12, 16);

    private final FieldMove move;
    private final boolean fire;

    public RescueBlock(BlockBehaviour.Properties properties, FieldMove move, boolean fire) {
        super(properties);
        this.move = move;
        this.fire = fire;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** The field move that clears this trap. */
    public FieldMove move() {
        return move;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return fire ? FIRE_OUTLINE : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return fire ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction side) {
        // Ice and tangle are see-through: hide the faces between two of the same block, like glass.
        return neighbour.is(this) && !fire || super.skipRendering(state, neighbour, side);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (fire && !(entity instanceof PokemonEntity) && entity instanceof LivingEntity living
                && !living.fireImmune()) {
            living.igniteForSeconds(3);
            living.hurt(level.damageSources().inFire(), 1f);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!fire) {
            return;
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT,
                    SoundSource.BLOCKS, 0.6f + random.nextFloat() * 0.4f, 0.8f + random.nextFloat() * 0.4f, false);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.8,
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (RescueManager.isActiveTrap(level, pos)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        } else {
            level.removeBlock(pos, false);
        }
    }
}
