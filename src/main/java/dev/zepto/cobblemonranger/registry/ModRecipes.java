package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.recipe.StylerDyeRecipe;
import dev.zepto.cobblemonranger.recipe.StylerUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CobblemonRanger.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, StylerUpgradeRecipe.Serializer> STYLER_UPGRADE =
            SERIALIZERS.register("styler_upgrade", StylerUpgradeRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<StylerDyeRecipe>> STYLER_DYE =
            SERIALIZERS.register("styler_dye", () -> new SimpleCraftingRecipeSerializer<>(StylerDyeRecipe::new));

    private ModRecipes() {
    }
}
