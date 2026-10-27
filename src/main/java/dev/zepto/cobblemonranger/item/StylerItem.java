package dev.zepto.cobblemonranger.item;

import dev.zepto.cobblemonranger.capture.CaptureManager;
import dev.zepto.cobblemonranger.capture.PartnerManager;
import dev.zepto.cobblemonranger.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Capture Styler. Its durability is the styler's energy: hits taken during a capture drain it, it recharges
 * slowly while carried, and it never breaks (an empty styler just can't start a capture).
 */
public class StylerItem extends Item {
    /** Ticks between passive recharge steps while the styler is in an inventory. */
    private static final int RECHARGE_INTERVAL = 100;

    private final DyeColor color;

    public StylerItem(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public DyeColor getColor() {
        return color;
    }

    public static int getEnergy(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    public static void drainEnergy(ItemStack stack, int amount) {
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + Math.max(0, amount)));
    }

    /** Applies the stats component and keeps max energy in sync with the energy upgrade level. */
    public static void setStats(ItemStack stack, StylerStats stats) {
        stack.set(ModDataComponents.STYLER_STATS.get(), stats);
        stack.set(DataComponents.MAX_DAMAGE, stats.maxEnergy());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int released = PartnerManager.releaseAll(serverPlayer);
            if (released == 0) {
                serverPlayer.displayClientMessage(Component.translatable("message.cobblemonranger.no_partners"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !stack.isDamaged() || level.getGameTime() % RECHARGE_INTERVAL != 0) {
            return;
        }
        if (entity instanceof ServerPlayer player && CaptureManager.isCapturing(player)) {
            return;
        }
        stack.setDamageValue(stack.getDamageValue() - 1);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.REDSTONE) || repairCandidate.is(Items.REDSTONE_BLOCK);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        StylerStats stats = StylerStats.get(stack);
        tooltip.add(Component.translatable("tooltip.cobblemonranger.energy", getEnergy(stack), stack.getMaxDamage())
                .withStyle(ChatFormatting.AQUA));
        for (StylerStats.Stat stat : StylerStats.Stat.values()) {
            tooltip.add(Component.translatable("tooltip.cobblemonranger.stat." + stat.getSerializedName(),
                    stats.level(stat) + 1, StylerStats.MAX_LEVEL + 1).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.cobblemonranger.styler_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
