package dev.zepto.cobblemonranger.recipe;

import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.registry.ModItems;
import dev.zepto.cobblemonranger.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Styler + dye in a crafting grid recolours the styler without losing its upgrades or energy. */
public class StylerDyeRecipe extends CustomRecipe {
    public StylerDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findResultColor(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        DyeColor color = findResultColor(input);
        if (color == null) {
            return ItemStack.EMPTY;
        }
        ItemStack styler = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).getItem() instanceof StylerItem) {
                styler = input.getItem(i);
            }
        }
        return styler.transmuteCopy(ModItems.STYLERS.get(color).get(), 1);
    }

    private static DyeColor findResultColor(CraftingInput input) {
        StylerItem styler = null;
        DyeColor dye = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof StylerItem item && styler == null) {
                styler = item;
            } else if (stack.getItem() instanceof DyeItem item && dye == null) {
                dye = item.getDyeColor();
            } else {
                return null;
            }
        }
        if (styler == null || dye == null || styler.getColor() == dye) {
            return null;
        }
        return dye;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.STYLER_DYE.get();
    }
}
