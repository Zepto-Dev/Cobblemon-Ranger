package dev.zepto.cobblemonranger.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.registry.ModItems;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Explains in EMI that upgrade modules are installed at a Ranger HQ Terminal. Only loaded when EMI is installed.
 */
@EmiEntrypoint
public final class EmiCompat implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        List<EmiIngredient> stacks = List.of(EmiStack.of(ModItems.ENERGY_UPGRADE.get()),
                EmiStack.of(ModItems.LINE_UPGRADE.get()), EmiStack.of(ModItems.POWER_UPGRADE.get()),
                EmiStack.of(ModItems.RANGER_HQ.get()));
        registry.addRecipe(new EmiInfoRecipe(stacks, List.of(Component.translatable("info.cobblemonranger.hq_upgrades")),
                CobblemonRanger.id("/info/hq_upgrades")));
    }
}
