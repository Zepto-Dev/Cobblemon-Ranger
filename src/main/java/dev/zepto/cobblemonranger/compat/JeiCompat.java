package dev.zepto.cobblemonranger.compat;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Explains in JEI's info tab that upgrade modules are installed at a Ranger HQ Terminal. Only loaded when JEI is
 * installed.
 */
@JeiPlugin
public final class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return CobblemonRanger.id("jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> modules = List.of(new ItemStack(ModItems.ENERGY_UPGRADE.get()),
                new ItemStack(ModItems.LINE_UPGRADE.get()), new ItemStack(ModItems.POWER_UPGRADE.get()),
                new ItemStack(ModItems.RANGER_HQ.get()));
        registration.addIngredientInfo(modules, VanillaTypes.ITEM_STACK,
                Component.translatable("info.cobblemonranger.hq_upgrades"));
    }
}
