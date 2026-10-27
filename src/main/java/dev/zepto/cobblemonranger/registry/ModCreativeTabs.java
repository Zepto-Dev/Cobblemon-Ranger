package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CobblemonRanger.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cobblemonranger"))
                    .icon(() -> ModItems.STYLERS.get(DyeColor.RED).get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        for (DyeColor color : DyeColor.values()) {
                            output.accept(ModItems.STYLERS.get(color).get());
                        }
                        output.accept(ModItems.ENERGY_UPGRADE.get());
                        output.accept(ModItems.LINE_UPGRADE.get());
                        output.accept(ModItems.POWER_UPGRADE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
