package dev.zepto.cobblemonranger.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerStats;
import dev.zepto.cobblemonranger.registry.ModItems;
import dev.zepto.cobblemonranger.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;

/**
 * Smithing table recipe: upgrade module (template) + any styler (base) + material (addition) raises one styler stat
 * by a level, keeping the styler's colour, energy and other upgrades.
 */
public record StylerUpgradeRecipe(Ingredient template, Ingredient addition, StylerStats.Stat stat) implements SmithingRecipe {
    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return template.test(input.template())
                && isBaseIngredient(input.base())
                && addition.test(input.addition())
                && StylerStats.get(input.base()).level(stat) < StylerStats.MAX_LEVEL;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return upgrade(input.base());
    }

    /** The styler this recipe turns {@code base} into. Also used to show the result in JEI and EMI. */
    public ItemStack upgrade(ItemStack base) {
        // Damage is kept as-is, so an energy upgrade's extra capacity arrives already charged.
        ItemStack result = base.copyWithCount(1);
        StylerItem.setStats(result, StylerStats.get(result).upgraded(stat));
        return result;
    }

    /** One plain styler of every colour, for recipe viewers to show as the base. */
    public static List<ItemStack> exampleBases() {
        return Arrays.stream(DyeColor.values()).map(color -> new ItemStack(ModItems.STYLERS.get(color).get())).toList();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ModItems.STYLERS.get(DyeColor.RED).get());
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return stack.getItem() instanceof StylerItem;
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.STYLER_UPGRADE.get();
    }

    public static final class Serializer implements RecipeSerializer<StylerUpgradeRecipe> {
        private static final MapCodec<StylerUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("template").forGetter(StylerUpgradeRecipe::template),
                Ingredient.CODEC_NONEMPTY.fieldOf("addition").forGetter(StylerUpgradeRecipe::addition),
                StylerStats.Stat.CODEC.fieldOf("stat").forGetter(StylerUpgradeRecipe::stat)
        ).apply(i, StylerUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, StylerUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, StylerUpgradeRecipe::template,
                Ingredient.CONTENTS_STREAM_CODEC, StylerUpgradeRecipe::addition,
                StylerStats.Stat.STREAM_CODEC.cast(), StylerUpgradeRecipe::stat,
                StylerUpgradeRecipe::new);

        @Override
        public MapCodec<StylerUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StylerUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
