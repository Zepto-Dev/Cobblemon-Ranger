package dev.zepto.cobblemonranger.hq;

import dev.zepto.cobblemonranger.config.RangerConfig;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerStats;
import dev.zepto.cobblemonranger.item.StylerUpgradeItem;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.registry.ModAttachments;
import dev.zepto.cobblemonranger.registry.ModBlocks;
import dev.zepto.cobblemonranger.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Ranger HQ Terminal screen: installs upgrade modules into a styler, recharges the styler on a cooldown, and shows
 * the player's Ranger rank and current mission.
 */
public class RangerHqMenu extends AbstractContainerMenu {
    public static final int STYLER_SLOT = 0;
    public static final int MODULE_SLOT = 1;
    public static final int MATERIAL_SLOT = 2;
    private static final int INVENTORY_START = 3;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    public static final int BUTTON_UPGRADE = 0;
    public static final int BUTTON_CHARGE = 1;

    /** Layout shared with the screen. */
    public static final int WIDTH = 256;
    public static final int HEIGHT = 214;
    public static final int STYLER_X = 12;
    public static final int MODULE_X = 42;
    public static final int MATERIAL_X = 62;
    public static final int SLOT_Y = 34;
    public static final int INVENTORY_X = 48;
    public static final int INVENTORY_Y = 132;

    public static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> LINES_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private final Container inputs = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    /** Seconds until the terminal will charge this player's styler again (0 = ready). */
    private final ContainerData chargeData;
    /** The mission panel text, written by the server when the screen opens. */
    private final List<Component> missionLines;

    /** Client side, from the open-screen packet. */
    public RangerHqMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(1), LINES_CODEC.decode(buf));
    }

    private RangerHqMenu(int id, Inventory inventory, ContainerLevelAccess access, ContainerData chargeData,
                         List<Component> missionLines) {
        super(ModMenus.RANGER_HQ.get(), id);
        this.access = access;
        this.chargeData = chargeData;
        this.missionLines = missionLines;

        addSlot(new Slot(inputs, STYLER_SLOT, STYLER_X + 1, SLOT_Y + 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof StylerItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(inputs, MODULE_SLOT, MODULE_X + 1, SLOT_Y + 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof StylerUpgradeItem;
            }
        });
        addSlot(new Slot(inputs, MATERIAL_SLOT, MATERIAL_X + 1, SLOT_Y + 1));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(chargeData);
    }

    /** Server side. */
    public static RangerHqMenu server(int id, Inventory inventory, ContainerLevelAccess access, ServerPlayer player,
                                      List<Component> missionLines) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                long left = player.getData(ModAttachments.NEXT_HQ_CHARGE) - RangerManager.now(player);
                // Container data travels as a short; the config caps the cooldown well below that.
                return left <= 0 ? 0 : (int) Math.min(Short.MAX_VALUE, (left + 19) / 20);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        return new RangerHqMenu(id, inventory, access, data, missionLines);
    }

    public List<Component> missionLines() {
        return missionLines;
    }

    public int chargeCooldownSeconds() {
        return chargeData.get(0);
    }

    public ItemStack styler() {
        return inputs.getItem(STYLER_SLOT);
    }

    /** Why the module can't be installed right now, or null if it can. */
    public Component upgradeProblem() {
        ItemStack styler = styler();
        ItemStack module = inputs.getItem(MODULE_SLOT);
        if (styler.isEmpty()) {
            return Component.translatable("gui.cobblemonranger.hq.need_styler");
        }
        if (!(module.getItem() instanceof StylerUpgradeItem upgrade)) {
            return Component.translatable("gui.cobblemonranger.hq.need_module");
        }
        if (StylerStats.get(styler).level(upgrade.getStat()) >= StylerStats.MAX_LEVEL) {
            return Component.translatable("gui.cobblemonranger.hq.maxed");
        }
        if (!inputs.getItem(MATERIAL_SLOT).is(StylerUpgradeItem.material(upgrade.getStat()))) {
            return Component.translatable("gui.cobblemonranger.hq.need_material",
                    StylerUpgradeItem.material(upgrade.getStat()).getDescription());
        }
        return null;
    }

    /** Why the styler can't be charged right now, or null if it can. */
    public Component chargeProblem() {
        ItemStack styler = styler();
        if (styler.isEmpty()) {
            return Component.translatable("gui.cobblemonranger.hq.need_styler");
        }
        if (chargeCooldownSeconds() > 0) {
            int seconds = chargeCooldownSeconds();
            return Component.translatable("gui.cobblemonranger.hq.charge_wait", seconds / 60,
                    String.format("%02d", seconds % 60));
        }
        if (!styler.isDamaged()) {
            return Component.translatable("gui.cobblemonranger.hq.charged");
        }
        return null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_UPGRADE && upgradeProblem() == null) {
            ItemStack styler = styler();
            StylerUpgradeItem upgrade = (StylerUpgradeItem) inputs.getItem(MODULE_SLOT).getItem();
            // Damage is kept as-is, so an energy upgrade's extra capacity arrives already charged.
            StylerItem.setStats(styler, StylerStats.get(styler).upgraded(upgrade.getStat()));
            inputs.removeItem(MODULE_SLOT, 1);
            inputs.removeItem(MATERIAL_SLOT, 1);
            inputs.setChanged();
            access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE,
                    SoundSource.BLOCKS, 1f, 1.2f));
            return true;
        }
        if (id == BUTTON_CHARGE && chargeProblem() == null) {
            styler().setDamageValue(0);
            serverPlayer.setData(ModAttachments.NEXT_HQ_CHARGE,
                    RangerManager.now(serverPlayer) + RangerConfig.hqChargeCooldownTicks());
            inputs.setChanged();
            access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.BLOCKS, 0.8f, 1.6f));
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= INVENTORY_END) {
            return ItemStack.EMPTY;
        }
        Slot slot = getSlot(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved;
            if (stack.getItem() instanceof StylerItem) {
                moved = moveItemStackTo(stack, STYLER_SLOT, STYLER_SLOT + 1, false);
            } else if (stack.getItem() instanceof StylerUpgradeItem) {
                moved = moveItemStackTo(stack, MODULE_SLOT, MODULE_SLOT + 1, false);
            } else {
                moved = moveItemStackTo(stack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false);
            }
            if (!moved) {
                int hotbar = INVENTORY_END - 9;
                moved = index < hotbar ? moveItemStackTo(stack, hotbar, INVENTORY_END, false)
                        : moveItemStackTo(stack, INVENTORY_START, hotbar, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, inputs));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RANGER_HQ.get());
    }
}
