package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * The ground the capture arena is drawn on, picked from where the wild Pokémon stands: its dimension, whether it is
 * underground or in water, the block under it and its biome.
 */
public enum ArenaTheme {
    MEADOW("grass", Tint.GRASS, 45, false, 1f, new int[][]{
            {Decor.TUFT, 6}, {Decor.TUFT_TALL, 2}, {Decor.CLOVER, 1}, {Decor.FLOWER_RED, 2}, {Decor.FLOWER_YELLOW, 2},
            {Decor.FLOWER_BLUE, 1}, {Decor.FLOWER_WHITE, 1}, {Decor.PEBBLES, 1}}),
    FOREST("grass", Tint.GRASS, 51, true, 1f, new int[][]{
            {Decor.TUFT, 3}, {Decor.FERN, 3}, {Decor.BUSH, 2}, {Decor.LEAVES, 3}, {Decor.MUSHROOM_RED, 1},
            {Decor.MUSHROOM_BROWN, 2}, {Decor.ROCK, 1}, {Decor.FLOWER_WHITE, 1}}),
    JUNGLE("grass", Tint.GRASS, 63, true, 1f, new int[][]{
            {Decor.FERN, 4}, {Decor.BUSH, 4}, {Decor.LEAVES, 3}, {Decor.TUFT_TALL, 3}, {Decor.FLOWER_RED, 1}}),
    SWAMP("grass", Tint.GRASS, 42, true, 1f, new int[][]{
            {Decor.REEDS, 3}, {Decor.LILY, 2}, {Decor.PUDDLE, 3}, {Decor.TUFT, 2}, {Decor.MUSHROOM_BROWN, 1}}),
    DESERT("sand", Tint.NONE, 21, false, 1f, new int[][]{
            {Decor.CACTUS, 1}, {Decor.DEAD_BUSH, 3}, {Decor.PEBBLES, 2}, {Decor.BONE, 1}, {Decor.ROCK, 1}}),
    BADLANDS("red_sand", Tint.NONE, 21, false, 1f, new int[][]{
            {Decor.DEAD_BUSH, 3}, {Decor.PEBBLES, 2}, {Decor.ROCK, 2}, {Decor.CACTUS, 1}}),
    BEACH("sand", Tint.NONE, 21, false, 1f, new int[][]{
            {Decor.SHELL, 2}, {Decor.STARFISH, 1}, {Decor.PEBBLES, 2}, {Decor.PUDDLE, 1}}),
    SNOW("snow", Tint.NONE, 27, false, 1f, new int[][]{
            {Decor.SNOW_TUFT, 4}, {Decor.ICE, 2}, {Decor.PEBBLES, 1}, {Decor.ROCK, 1}}),
    MOUNTAIN("stone", Tint.NONE, 27, false, 1f, new int[][]{
            {Decor.ROCK, 3}, {Decor.PEBBLES, 3}, {Decor.TUFT, 1}, {Decor.FLOWER_WHITE, 1}, {Decor.CRACK, 2}}),
    GRAVEL("gravel", Tint.NONE, 21, false, 1f, new int[][]{
            {Decor.PEBBLES, 4}, {Decor.ROCK, 2}, {Decor.BONE, 1}}),
    CAVE("stone", Tint.NONE, 27, false, 0.35f, new int[][]{
            {Decor.ROCK, 3}, {Decor.PEBBLES, 3}, {Decor.CRACK, 3}, {Decor.CRYSTAL, 1}, {Decor.BONE, 1},
            {Decor.MUSHROOM_BROWN, 1}, {Decor.PUDDLE, 1}}),
    DEEP_CAVE("deepslate", Tint.NONE, 24, false, 0.25f, new int[][]{
            {Decor.SCULK, 3}, {Decor.CRACK, 3}, {Decor.PEBBLES, 2}, {Decor.BONE, 1}}),
    LUSH_CAVE("moss", Tint.NONE, 45, false, 0.55f, new int[][]{
            {Decor.GLOW_BERRIES, 3}, {Decor.FERN, 2}, {Decor.BUSH, 2}, {Decor.FLOWER_YELLOW, 1}, {Decor.PUDDLE, 1}}),
    WATER("water", Tint.WATER, 21, false, 1f, new int[][]{
            {Decor.RIPPLE, 5}, {Decor.LILY, 1}}),
    NETHER("netherrack", Tint.NONE, 24, false, 0.6f, new int[][]{
            {Decor.EMBER, 3}, {Decor.FUNGUS, 2}, {Decor.CRACK, 2}, {Decor.BONE, 1}}),
    END("end_stone", Tint.NONE, 18, false, 0.7f, new int[][]{
            {Decor.CHORUS, 2}, {Decor.PEBBLES, 2}, {Decor.CRACK, 1}, {Decor.CRYSTAL, 1}}),
    MUSHROOM("mycelium", Tint.NONE, 27, false, 1f, new int[][]{
            {Decor.MUSHROOM_RED, 3}, {Decor.MUSHROOM_BROWN, 3}, {Decor.PEBBLES, 1}});

    public static final ResourceLocation DECORATIONS = CobblemonRanger.id("textures/gui/capture/decorations.png");
    public static final int TILE_SIZE = 64;
    /** Colour for the tinted decorations on ground that has no biome tint of its own. */
    private static final int DEFAULT_PLANT_TINT = 0x6DBE45;

    public enum Tint { NONE, GRASS, WATER }

    public final ResourceLocation tile;
    public final Tint tint;
    public final int decorationCount;
    /** Dappled shade from a canopy overhead. */
    public final boolean canopy;
    /** How much daylight reaches the arena: lower values draw a darker vignette and a dimmer centre. */
    public final float light;
    private final int[][] decorations;
    private final int totalWeight;

    ArenaTheme(String tile, Tint tint, int decorationCount, boolean canopy, float light, int[][] decorations) {
        this.tile = CobblemonRanger.id("textures/gui/capture/terrain/" + tile + ".png");
        this.tint = tint;
        this.decorationCount = decorationCount;
        this.canopy = canopy;
        this.light = light;
        this.decorations = decorations;
        int total = 0;
        for (int[] entry : decorations) {
            total += entry[1];
        }
        this.totalWeight = total;
    }

    /** Picks a decoration sprite index from this theme's weighted list; {@code roll} is in [0, 1). */
    public int pickDecoration(float roll) {
        int target = (int) (roll * totalWeight);
        for (int[] entry : decorations) {
            target -= entry[1];
            if (target < 0) {
                return entry[0];
            }
        }
        return decorations[0][0];
    }

    // ------------------------------------------------------------------ detection

    /** The theme and its tint colours for the spot where {@code entity} stands. */
    public record Arena(ArenaTheme theme, int groundTint, int plantTint, long seed) {
    }

    public static Arena detect(Level level, Entity entity) {
        BlockPos pos = entity.blockPosition();
        ArenaTheme theme = pick(level, entity, pos);
        int grass = brighten(BiomeColors.getAverageGrassColor(level, pos));
        int groundTint = switch (theme.tint) {
            case GRASS -> grass;
            case WATER -> brighten(BiomeColors.getAverageWaterColor(level, pos));
            case NONE -> 0xFFFFFF;
        };
        int plantTint = theme.tint == Tint.GRASS || theme == MOUNTAIN ? grass : DEFAULT_PLANT_TINT;
        return new Arena(theme, groundTint, plantTint, pos.asLong());
    }

    private static ArenaTheme pick(Level level, Entity entity, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (level.dimension() == Level.NETHER || biome.is(Tags.Biomes.IS_NETHER)) {
            return NETHER;
        }
        if (level.dimension() == Level.END || biome.is(Tags.Biomes.IS_END)) {
            return END;
        }
        if (entity.isInWater() || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return WATER;
        }

        BlockState ground = groundUnder(level, pos);
        boolean underground = !level.canSeeSky(pos) && level.getBrightness(LightLayer.SKY, pos) < 6;
        if (underground) {
            if (biome.is(Tags.Biomes.IS_LUSH) || ground.is(Blocks.MOSS_BLOCK)) {
                return LUSH_CAVE;
            }
            if (biome.is(Biomes.DEEP_DARK) || ground.is(Blocks.SCULK) || ground.is(Blocks.DEEPSLATE) || pos.getY() < 0) {
                return DEEP_CAVE;
            }
            return CAVE;
        }

        if (ground.is(Blocks.RED_SAND) || ground.is(BlockTags.TERRACOTTA)) {
            return BADLANDS;
        }
        if (ground.is(BlockTags.SAND)) {
            return biome.is(Tags.Biomes.IS_BEACH) || biome.is(Tags.Biomes.IS_OCEAN) || biome.is(Tags.Biomes.IS_RIVER)
                    ? BEACH : DESERT;
        }
        if (ground.is(Blocks.SNOW) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW)
                || ground.is(BlockTags.ICE)) {
            return SNOW;
        }
        if (ground.is(Blocks.MYCELIUM)) {
            return MUSHROOM;
        }
        if (ground.is(Blocks.MUD)) {
            return SWAMP;
        }
        if (ground.is(Blocks.GRAVEL)) {
            return GRAVEL;
        }
        if (ground.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return MOUNTAIN;
        }

        if (biome.is(Tags.Biomes.IS_JUNGLE)) {
            return JUNGLE;
        }
        if (biome.is(Tags.Biomes.IS_SWAMP)) {
            return SWAMP;
        }
        if (biome.is(Tags.Biomes.IS_BADLANDS)) {
            return BADLANDS;
        }
        if (biome.is(Tags.Biomes.IS_DESERT)) {
            return DESERT;
        }
        if (biome.is(Tags.Biomes.IS_SNOWY)) {
            return SNOW;
        }
        if (biome.is(Tags.Biomes.IS_MUSHROOM)) {
            return MUSHROOM;
        }
        if (biome.is(Tags.Biomes.IS_FOREST) || biome.is(Tags.Biomes.IS_TAIGA)) {
            return FOREST;
        }
        if (biome.is(Tags.Biomes.IS_BEACH)) {
            return BEACH;
        }
        return MEADOW;
    }

    /** The first block the entity stands in or on, skipping air and plants (but not snow layers). */
    private static BlockState groundUnder(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < 5; i++) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.SNOW) || (!state.isAir() && state.isSolid())) {
                return state;
            }
            cursor.move(0, -1, 0);
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * Biome colours are made for blocks shaded by the world's lighting; the arena is flat-lit, so lift dull colours
     * towards a common brightness and saturate them a little.
     */
    private static int brighten(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float luma = 0.3f * r + 0.59f * g + 0.11f * b;
        r = luma + (r - luma) * 1.15f;
        g = luma + (g - luma) * 1.15f;
        b = luma + (b - luma) * 1.15f;
        float lift = Math.max(1f, Math.min(1.3f, 0.74f / Math.max(0.01f, luma)));
        return (channel(r * lift) << 16) | (channel(g * lift) << 8) | channel(b * lift);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }

    /** Sprite indices in {@link #DECORATIONS}, an 8x4 atlas of 16x16 cells (see tools/arena_textures.py). */
    public static final class Decor {
        /** Sprites before this index are drawn in greys and take the plant tint. */
        public static final int TINTED_COUNT = 7;

        public static final int TUFT = 0, TUFT_TALL = 1, FERN = 2, CLOVER = 3, BUSH = 4, LEAVES = 5, REEDS = 6,
                LILY = 7, FLOWER_RED = 8, FLOWER_YELLOW = 9, FLOWER_BLUE = 10, FLOWER_WHITE = 11, MUSHROOM_RED = 12,
                MUSHROOM_BROWN = 13, PEBBLES = 14, ROCK = 15, CACTUS = 16, DEAD_BUSH = 17, SHELL = 18, STARFISH = 19,
                SNOW_TUFT = 20, ICE = 21, CRYSTAL = 22, BONE = 23, RIPPLE = 24, EMBER = 25, FUNGUS = 26, CHORUS = 27,
                GLOW_BERRIES = 28, SCULK = 29, CRACK = 30, PUDDLE = 31;

        private Decor() {
        }
    }
}
