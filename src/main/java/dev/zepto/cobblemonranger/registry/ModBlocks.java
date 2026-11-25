package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.field.SilkBlocks;
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

    private ModBlocks() {
    }
}
