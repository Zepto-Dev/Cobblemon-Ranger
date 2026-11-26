package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.field.FieldMove;
import dev.zepto.cobblemonranger.field.SilkBlocks;
import dev.zepto.cobblemonranger.rescue.RescueBlock;
import dev.zepto.cobblemonranger.hq.RangerHqBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CobblemonRanger.MOD_ID);

    public static final DeferredBlock<RangerHqBlock> RANGER_HQ = BLOCKS.register("ranger_hq",
            () -> new RangerHqBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(state -> 6)));

    /** String Shot silk: never an item, drops nothing, gone after 30 seconds. */
    public static final DeferredBlock<SilkBlocks.Bridge> SILK_BRIDGE = BLOCKS.register("silk_bridge",
            () -> new SilkBlocks.Bridge(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.2f)
                    .sound(SoundType.WOOL).noOcclusion().noLootTable().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<SilkBlocks.Ladder> SILK_LADDER = BLOCKS.register("silk_ladder",
            () -> new SilkBlocks.Ladder(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.2f)
                    .sound(SoundType.WOOL).noOcclusion().noLootTable().pushReaction(PushReaction.DESTROY)));

    /** Rescue event traps: unbreakable by hand, cleared only by the matching field move, never an item. */
    public static final DeferredBlock<RescueBlock> RESCUE_RUBBLE = BLOCKS.register("rescue_rubble",
            () -> new RescueBlock(trap(MapColor.STONE, SoundType.STONE), FieldMove.ROCK_SMASH, false));
    public static final DeferredBlock<RescueBlock> RESCUE_TANGLE = BLOCKS.register("rescue_tangle",
            () -> new RescueBlock(trap(MapColor.PLANT, SoundType.AZALEA_LEAVES).noOcclusion()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false), FieldMove.CUT, false));
    public static final DeferredBlock<RescueBlock> RESCUE_FIRE = BLOCKS.register("rescue_fire",
            () -> new RescueBlock(trap(MapColor.FIRE, SoundType.WOOL).noCollission().noOcclusion()
                    .lightLevel(s -> 15).replaceable(), FieldMove.SURF, true));
    public static final DeferredBlock<RescueBlock> RESCUE_ICE = BLOCKS.register("rescue_ice",
            () -> new RescueBlock(trap(MapColor.ICE, SoundType.GLASS).noOcclusion().friction(0.98f)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .isRedstoneConductor((s, l, p) -> false), FieldMove.BURN, false));
    public static final DeferredBlock<RescueBlock> RESCUE_SAND = BLOCKS.register("rescue_sand",
            () -> new RescueBlock(trap(MapColor.SAND, SoundType.SAND), FieldMove.DIG, false));

    private static BlockBehaviour.Properties trap(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(-1f, 3600000f).noLootTable()
                .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false);
    }

    private ModBlocks() {
    }
}
