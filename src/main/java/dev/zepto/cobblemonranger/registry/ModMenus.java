package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.hq.RangerHqMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
            CobblemonRanger.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RangerHqMenu>> RANGER_HQ = MENUS.register("ranger_hq",
            () -> IMenuTypeExtension.create(RangerHqMenu::new));

    private ModMenus() {
    }
}
