package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.hq.RangerHqBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CobblemonRanger.MOD_ID);

    public static final DeferredBlock<RangerHqBlock> RANGER_HQ = BLOCKS.register("ranger_hq",
            () -> new RangerHqBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(state -> 6)));

    private ModBlocks() {
    }
}
