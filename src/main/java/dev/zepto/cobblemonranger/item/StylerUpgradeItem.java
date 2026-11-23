package dev.zepto.cobblemonranger.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An upgrade module found in structure loot. Installed into a styler at a Ranger HQ Terminal, together with one piece
 * of its {@link #material(StylerStats.Stat) material}.
 */
public class StylerUpgradeItem extends Item {
    private final StylerStats.Stat stat;

    public StylerUpgradeItem(StylerStats.Stat stat, Properties properties) {
        super(properties);
        this.stat = stat;
    }

    public StylerStats.Stat getStat() {
        return stat;
    }

    /** What installing a module of this stat costs on top of the module itself. */
    public static Item material(StylerStats.Stat stat) {
        return switch (stat) {
            case ENERGY -> Items.REDSTONE_BLOCK;
            case LINE -> Items.STRING;
            case POWER -> Items.AMETHYST_SHARD;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cobblemonranger.module." + stat.getSerializedName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.cobblemonranger.module_hint", material(stat).getDescription())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
