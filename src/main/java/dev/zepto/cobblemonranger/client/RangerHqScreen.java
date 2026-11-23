package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.hq.RangerHqMenu;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.item.StylerStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Screen of the Ranger HQ Terminal. Drawn with plain fills (no texture): an upgrade bench and charger on the left,
 * the mission panel on the right, the player's inventory underneath.
 */
public class RangerHqScreen extends AbstractContainerScreen<RangerHqMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADE = 0xFF555555;
    private static final int SCREEN = 0xFFDCEFF5;
    private static final int TEXT = 0xFF404040;
    private static final int MISSION_X = 132;
    private static final int MISSION_WIDTH = 116;

    private Button upgradeButton;
    private Button chargeButton;

    public RangerHqScreen(RangerHqMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RangerHqMenu.WIDTH;
        imageHeight = RangerHqMenu.HEIGHT;
        inventoryLabelX = RangerHqMenu.INVENTORY_X;
        inventoryLabelY = RangerHqMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        upgradeButton = addRenderableWidget(Button.builder(Component.translatable("gui.cobblemonranger.hq.install"),
                b -> press(RangerHqMenu.BUTTON_UPGRADE)).bounds(leftPos + 84, topPos + 33, 38, 20).build());
        chargeButton = addRenderableWidget(Button.builder(Component.translatable("gui.cobblemonranger.hq.charge"),
                b -> press(RangerHqMenu.BUTTON_CHARGE)).bounds(leftPos + 8, topPos + 96, 114, 20).build());
        updateButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        Component upgrade = menu.upgradeProblem();
        upgradeButton.active = upgrade == null;
        upgradeButton.setTooltip(upgrade == null ? null : Tooltip.create(upgrade));
        Component charge = menu.chargeProblem();
        chargeButton.active = charge == null;
        chargeButton.setTooltip(charge == null ? null : Tooltip.create(charge));
        int wait = menu.chargeCooldownSeconds();
        chargeButton.setMessage(wait > 0
                ? Component.translatable("gui.cobblemonranger.hq.charge_cooldown", wait / 60,
                String.format("%02d", wait % 60))
                : Component.translatable("gui.cobblemonranger.hq.charge"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        bevel(graphics, x, y, imageWidth, imageHeight, PANEL, LIGHT, SHADE);
        // Mission screen inset.
        bevel(graphics, x + MISSION_X - 4, y + 16, MISSION_WIDTH + 8, 102, SCREEN, SHADE, LIGHT);
        slot(graphics, x + RangerHqMenu.STYLER_X, y + RangerHqMenu.SLOT_Y);
        slot(graphics, x + RangerHqMenu.MODULE_X, y + RangerHqMenu.SLOT_Y);
        slot(graphics, x + RangerHqMenu.MATERIAL_X, y + RangerHqMenu.SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + RangerHqMenu.INVENTORY_X - 1 + col * 18, y + RangerHqMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + RangerHqMenu.INVENTORY_X - 1 + col * 18, y + RangerHqMenu.INVENTORY_Y + 57);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.cobblemonranger.hq.upgrades"), 8, 20, TEXT, false);
        graphics.drawString(font, "+", RangerHqMenu.MODULE_X - 10, RangerHqMenu.SLOT_Y + 5, TEXT, false);

        ItemStack styler = menu.styler();
        if (styler.getItem() instanceof StylerItem) {
            StylerStats stats = StylerStats.get(styler);
            int lineY = 56;
            for (StylerStats.Stat stat : StylerStats.Stat.values()) {
                graphics.drawString(font, Component.translatable("tooltip.cobblemonranger.stat." + stat.getSerializedName(),
                        stats.level(stat) + 1, StylerStats.MAX_LEVEL + 1), 8, lineY, TEXT, false);
                lineY += 9;
            }
            graphics.drawString(font, Component.translatable("tooltip.cobblemonranger.energy",
                    StylerItem.getEnergy(styler), styler.getMaxDamage()), 8, lineY, 0xFF1E7FA8, false);
        } else {
            graphics.drawWordWrap(font, Component.translatable("gui.cobblemonranger.hq.styler_hint"), 8, 58, 116, TEXT);
        }
        graphics.drawString(font, Component.translatable("gui.cobblemonranger.hq.missions"), MISSION_X, 20, 0xFF1E5F7A,
                false);
        int lineY = 32;
        for (Component line : menu.missionLines()) {
            List<FormattedCharSequence> wrapped = font.split(line, MISSION_WIDTH);
            if (wrapped.isEmpty()) {
                lineY += 5;
                continue;
            }
            for (FormattedCharSequence part : wrapped) {
                if (lineY > 106) {
                    return;
                }
                graphics.drawString(font, part, MISSION_X, lineY, TEXT, false);
                lineY += 9;
            }
        }
    }

    private static void bevel(GuiGraphics graphics, int x, int y, int w, int h, int fill, int topLeft, int bottomRight) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w - 1, y + 1, topLeft);
        graphics.fill(x, y, x + 1, y + h - 1, topLeft);
        graphics.fill(x + 1, y + h - 1, x + w, y + h, bottomRight);
        graphics.fill(x + w - 1, y + 1, x + w, y + h, bottomRight);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        bevel(graphics, x, y, 18, 18, 0xFF8B8B8B, 0xFF373737, LIGHT);
    }
}
