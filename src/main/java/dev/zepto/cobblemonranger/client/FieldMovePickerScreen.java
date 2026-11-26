package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.field.FieldMove;
import dev.zepto.cobblemonranger.field.FieldMoveManager;
import dev.zepto.cobblemonranger.network.OpenFieldMovePickerPayload;
import dev.zepto.cobblemonranger.network.UseFieldMovePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The field move picker, opened by sneak + right-click with the styler: a wheel of the moves somebody in the party
 * can do right now around the crosshair. Point at one and click (or press its number) to use it. Moves aimed at a
 * block are readied for the next right-click instead.
 */
public class FieldMovePickerScreen extends Screen {
    /** Icon atlas from tools/field_move_art.py: 6 x 3 cells of 20x20 in FieldMove order. */
    private static final ResourceLocation ICONS = CobblemonRanger.id("textures/gui/field_moves.png");
    private static final int ICON = 20;
    private static final int MIN_RADIUS = 46;

    private final List<FieldMove> moves = new ArrayList<>();
    private final List<Boolean> byPartner = new ArrayList<>();
    private int selected;

    public FieldMovePickerScreen(OpenFieldMovePickerPayload payload) {
        super(Component.translatable("screen.cobblemonranger.field_moves"));
        for (OpenFieldMovePickerPayload.Entry entry : payload.moves()) {
            FieldMove move = FieldMove.byId(entry.id());
            if (move != null && !moves.contains(move)) {
                moves.add(move);
                byPartner.add(entry.partner());
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No blur or dimming: the world stays visible around the wheel.
    }

    /** Grows with the number of moves so the icons never touch. */
    private int radius() {
        return Math.max(MIN_RADIUS, (int) Math.ceil(moves.size() * (ICON + 6) / (2 * Math.PI)));
    }

    private int iconX(int i) {
        double angle = -Math.PI / 2 + i * 2 * Math.PI / moves.size();
        return width / 2 + (int) Math.round(radius() * Math.cos(angle)) - ICON / 2;
    }

    private int iconY(int i) {
        double angle = -Math.PI / 2 + i * 2 * Math.PI / moves.size();
        return height / 2 + (int) Math.round(radius() * Math.sin(angle)) - ICON / 2;
    }

    private void hover(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (moves.isEmpty() || dx * dx + dy * dy < 12 * 12) {
            return;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        double step = 2 * Math.PI / moves.size();
        selected = Math.floorMod((int) Math.round(angle / step), moves.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (moves.isEmpty()) {
            return;
        }
        hover(mouseX, mouseY);
        int cx = width / 2;
        int cy = height / 2;
        for (int i = 0; i < moves.size(); i++) {
            FieldMove move = moves.get(i);
            int x = iconX(i);
            int y = iconY(i);
            if (i == selected) {
                graphics.fill(x - 2, y - 2, x + ICON + 2, y + ICON + 2, RangerStyle.GOLD);
                graphics.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, RangerStyle.INSET);
            }
            int index = move.ordinal();
            graphics.blit(ICONS, x, y, (index % 6) * ICON, (index / 6) * ICON, ICON, ICON, 120, 60);
            if (moves.size() <= 9) {
                graphics.drawString(font, String.valueOf(i + 1), x + ICON - 4, y + ICON - 6, 0xFFFFFFFF, true);
            }
        }
        FieldMove move = moves.get(selected);
        Component name = move.displayName();
        int textTop = cy + radius() + 14;
        int boxWidth = Math.max(font.width(name), font.width(move.description())) + 12;
        graphics.fill(cx - boxWidth / 2, textTop - 3, cx + boxWidth / 2, textTop + 34, 0xC0101820);
        graphics.drawCenteredString(font, name, cx, textTop, 0xFFFFFFFF);
        Component who = byPartner.get(selected)
                ? Component.translatable("screen.cobblemonranger.field_moves.partner",
                FieldMoveManager.PARTNER_ENERGY_COST)
                : Component.translatable("screen.cobblemonranger.field_moves.friend");
        graphics.drawCenteredString(font, who, cx, textTop + 11, 0xFFA8F0FF);
        graphics.drawCenteredString(font, move.description(), cx, textTop + 22, 0xFFB0BCC8);
    }

    private void choose(int index) {
        if (index < 0 || index >= moves.size()) {
            return;
        }
        PacketDistributor.sendToServer(new UseFieldMovePayload(moves.get(index).id()));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            double dx = mouseX - width / 2.0;
            double dy = mouseY - height / 2.0;
            if (dx * dx + dy * dy < 12 * 12) {
                onClose();
                return true;
            }
            hover(mouseX, mouseY);
            choose(selected);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!moves.isEmpty()) {
            selected = Math.floorMod(selected - (int) Math.signum(scrollY), moves.size());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            choose(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            choose(selected);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
