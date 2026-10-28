package dev.zepto.cobblemonranger.compat;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.recipe.StylerUpgradeRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * Shows styler upgrade recipes in JEI's smithing category. JEI only lists smithing recipes it knows how to lay out, so
 * the upgrade recipe needs this extension. Only loaded when JEI is installed.
 */
@JeiPlugin
public final class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return CobblemonRanger.id("jei_plugin");
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getSmithingCategory().addExtension(StylerUpgradeRecipe.class, new StylerUpgradeExtension());
    }

    private static final class StylerUpgradeExtension implements ISmithingCategoryExtension<StylerUpgradeRecipe> {
        @Override
        public <T extends IIngredientAcceptor<T>> void setTemplate(StylerUpgradeRecipe recipe, T ingredients) {
            ingredients.addIngredients(recipe.template());
        }

        @Override
        public <T extends IIngredientAcceptor<T>> void setBase(StylerUpgradeRecipe recipe, T ingredients) {
            ingredients.addItemStacks(StylerUpgradeRecipe.exampleBases());
        }

        @Override
        public <T extends IIngredientAcceptor<T>> void setAddition(StylerUpgradeRecipe recipe, T ingredients) {
            ingredients.addIngredients(recipe.addition());
        }

        @Override
        public <T extends IIngredientAcceptor<T>> void setOutput(StylerUpgradeRecipe recipe, T ingredients) {
            // Same order as the bases, so JEI cycles each colour's result alongside it.
            ingredients.addItemStacks(StylerUpgradeRecipe.exampleBases().stream().map(recipe::upgrade).toList());
        }
    }
}
