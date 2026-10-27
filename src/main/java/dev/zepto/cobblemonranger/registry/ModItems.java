package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerStats;
import dev.zepto.cobblemonranger.item.StylerUpgradeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CobblemonRanger.MOD_ID);

    public static final Map<DyeColor, DeferredItem<StylerItem>> STYLERS = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            STYLERS.put(color, ITEMS.register(color.getSerializedName() + "_styler",
                    () -> new StylerItem(color, new Item.Properties().stacksTo(1).durability(StylerStats.BASE_ENERGY))));
        }
    }

    public static final DeferredItem<StylerUpgradeItem> ENERGY_UPGRADE = ITEMS.register("styler_energy_module",
            () -> new StylerUpgradeItem(StylerStats.Stat.ENERGY, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<StylerUpgradeItem> LINE_UPGRADE = ITEMS.register("styler_line_module",
            () -> new StylerUpgradeItem(StylerStats.Stat.LINE, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<StylerUpgradeItem> POWER_UPGRADE = ITEMS.register("styler_power_module",
            () -> new StylerUpgradeItem(StylerStats.Stat.POWER, new Item.Properties().rarity(Rarity.RARE)));

    private ModItems() {
    }
}
