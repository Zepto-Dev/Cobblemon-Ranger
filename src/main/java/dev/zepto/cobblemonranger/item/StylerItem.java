package dev.zepto.cobblemonranger.item;

import dev.zepto.cobblemonranger.capture.CaptureManager;
import dev.zepto.cobblemonranger.capture.FriendManager;
import dev.zepto.cobblemonranger.field.FieldMove;
import dev.zepto.cobblemonranger.field.FieldMoveManager;
import dev.zepto.cobblemonranger.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
        stack.set(DataComponents.MAX_DAMAGE, maxEnergy(stack));
    }

    /** Maximum energy from the Energy module plus the styler's level. */
    public static int maxEnergy(ItemStack stack) {
        return StylerStats.get(stack).maxEnergy() + StylerProgress.get(stack).energyBonus();
    }

    public static float lineLength(ItemStack stack) {
        return StylerStats.get(stack).lineLength() + StylerProgress.get(stack).lineBonus();
    }

    public static float loopPower(ItemStack stack) {
        return StylerStats.get(stack).loopPower() + StylerProgress.get(stack).powerBonus();
    }

    /** Gives the styler XP and announces any level gained. A level-up also refills the styler. */
    public static void addXp(ServerPlayer player, ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof StylerItem) || amount <= 0) {
            return;
        }
        StylerProgress before = StylerProgress.get(stack);
        if (before.isMaxed()) {
            return;
        }
        StylerProgress after = before.withXp(amount);
        stack.set(ModDataComponents.STYLER_PROGRESS.get(), after);
        if (after.level() > before.level()) {
            stack.set(DataComponents.MAX_DAMAGE, maxEnergy(stack));
            stack.setDamageValue(0);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 1f, 1.1f);
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.styler_level_up",
                    after.level()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        } else {
            player.displayClientMessage(Component.translatable("message.cobblemonranger.styler_xp", amount,
                    after.xp(), StylerProgress.xpToNext(after.level())), true);
        }
    }

    /** Using the styler on an obstacle calls a friend Pokémon (or the Partner Pokémon) to clear it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        // Water between the player and the block (a pond floor, say) means Surf, which use() handles.
        BlockHitResult fluidHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (fluidHit.getType() == HitResult.Type.BLOCK && !fluidHit.getBlockPos().equals(context.getClickedPos())
                && !level.getFluidState(fluidHit.getBlockPos()).isEmpty()) {
            return InteractionResult.PASS;
        }
        FieldMove move = FieldMoveManager.moveFor(level, context.getClickedPos());
        if (move == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FieldMoveManager.use(serverPlayer, context.getItemInHand(), move, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return InteractionResultHolder.pass(stack);
            }
            FluidState fluid = level.getFluidState(hit.getBlockPos());
            FieldMove move = fluid.is(FluidTags.WATER) ? FieldMove.SURF
                    : fluid.is(FluidTags.LAVA) ? FieldMove.SOAK : null;
            if (move == null) {
                return InteractionResultHolder.pass(stack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                FieldMoveManager.use(serverPlayer, stack, move, hit.getBlockPos());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int released = FriendManager.releaseAll(serverPlayer);
            if (released == 0) {
                serverPlayer.displayClientMessage(Component.translatable("message.cobblemonranger.no_friends"), true);
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
        StylerProgress progress = StylerProgress.get(stack);
        tooltip.add(Component.translatable("tooltip.cobblemonranger.energy", getEnergy(stack), stack.getMaxDamage())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add((progress.isMaxed()
                ? Component.translatable("tooltip.cobblemonranger.level_max", progress.level())
                : Component.translatable("tooltip.cobblemonranger.level", progress.level(), progress.xp(),
                StylerProgress.xpToNext(progress.level()))).withStyle(ChatFormatting.GOLD));
        for (StylerStats.Stat stat : StylerStats.Stat.values()) {
            tooltip.add(Component.translatable("tooltip.cobblemonranger.stat." + stat.getSerializedName(),
                    stats.level(stat) + 1, StylerStats.MAX_LEVEL + 1).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.cobblemonranger.styler_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
