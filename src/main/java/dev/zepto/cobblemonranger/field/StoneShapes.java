package dev.zepto.cobblemonranger.field;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/** Stone Edge shapes stone in place, like a stonecutter in the world: each block becomes its next cut form. */
public final class StoneShapes {
    private static final Map<Block, Block> SHAPES = Map.ofEntries(
            Map.entry(Blocks.COBBLESTONE, Blocks.STONE_BRICKS),
            Map.entry(Blocks.STONE, Blocks.SMOOTH_STONE),
            Map.entry(Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS),
            Map.entry(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS),
            Map.entry(Blocks.GRANITE, Blocks.POLISHED_GRANITE),
            Map.entry(Blocks.DIORITE, Blocks.POLISHED_DIORITE),
            Map.entry(Blocks.ANDESITE, Blocks.POLISHED_ANDESITE),
            Map.entry(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE),
            Map.entry(Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE),
            Map.entry(Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE),
            Map.entry(Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE),
            Map.entry(Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE),
            Map.entry(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS),
            Map.entry(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES),
            Map.entry(Blocks.TUFF, Blocks.POLISHED_TUFF),
            Map.entry(Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS),
            Map.entry(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE),
            Map.entry(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS),
            Map.entry(Blocks.BASALT, Blocks.POLISHED_BASALT),
            Map.entry(Blocks.END_STONE, Blocks.END_STONE_BRICKS),
            Map.entry(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS),
            Map.entry(Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS));

    private StoneShapes() {
    }

    /** The block this one becomes, or null if Stone Edge can't shape it. */
    public static Block shape(BlockState state) {
        return SHAPES.get(state.getBlock());
    }
}
