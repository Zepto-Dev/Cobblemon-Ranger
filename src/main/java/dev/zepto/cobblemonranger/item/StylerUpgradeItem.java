package dev.zepto.cobblemonranger.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** An upgrade module found in structure loot. Used as the template in a smithing table with a styler as the base. */
public class StylerUpgradeItem extends Item {
    private final StylerStats.Stat stat;

    public StylerUpgradeItem(StylerStats.Stat stat, Properties properties) {
        super(properties);
        this.stat = stat;
    }

    public StylerStats.Stat getStat() {
        return stat;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cobblemonranger.module." + stat.getSerializedName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.cobblemonranger.module_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
