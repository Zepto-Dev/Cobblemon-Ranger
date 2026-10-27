package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerProgress;
import dev.zepto.cobblemonranger.item.StylerStats;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CobblemonRanger.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StylerStats>> STYLER_STATS =
            COMPONENTS.register("styler_stats", () -> DataComponentType.<StylerStats>builder()
                    .persistent(StylerStats.CODEC)
                    .networkSynchronized(StylerStats.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StylerProgress>> STYLER_PROGRESS =
            COMPONENTS.register("styler_progress", () -> DataComponentType.<StylerProgress>builder()
                    .persistent(StylerProgress.CODEC)
                    .networkSynchronized(StylerProgress.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }
}
