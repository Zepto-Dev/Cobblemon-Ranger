package dev.zepto.cobblemonranger.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.zepto.cobblemonranger.recipe.StylerUpgradeRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;

import java.util.List;

/**
 * Shows styler upgrade recipes in EMI's smithing category, which only picks up vanilla's own smithing recipe classes.
 * Only loaded when EMI is installed.
 */
@EmiEntrypoint
public final class EmiCompat implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        for (RecipeHolder<SmithingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            if (holder.value() instanceof StylerUpgradeRecipe recipe) {
                registry.addRecipe(new UpgradeRecipe(holder, recipe));
            }
        }
    }

    /** Laid out like EMI's own smithing recipes: template, base, addition, arrow, result. */
    private static final class UpgradeRecipe extends BasicEmiRecipe {
        UpgradeRecipe(RecipeHolder<?> holder, StylerUpgradeRecipe recipe) {
            super(VanillaEmiRecipeCategories.SMITHING, holder.id(), 112, 18);
            List<ItemStack> bases = StylerUpgradeRecipe.exampleBases();
            inputs.add(EmiIngredient.of(recipe.template()));
            inputs.add(EmiIngredient.of(bases.stream().map(EmiStack::of).toList()));
            inputs.add(EmiIngredient.of(recipe.addition()));
            outputs.add(EmiStack.of(recipe.upgrade(bases.getFirst())));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 62, 1);
            widgets.addSlot(inputs.get(0), 0, 0);
            widgets.addSlot(inputs.get(1), 18, 0);
            widgets.addSlot(inputs.get(2), 36, 0);
            widgets.addSlot(outputs.getFirst(), 94, 0).recipeContext(this);
        }
    }
}
