package dev.zepto.cobblemonranger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Colours and widgets shared by the Ranger screens (Browser, Guide, HQ Terminal), so they look like one device. */
public final class RangerStyle {
    public static final int ACCENT = 0xFF5BC0EB;
    public static final int ACCENT_DARK = 0xFF2F7FA3;
    public static final int PANEL = 0xF0101820;
    public static final int CELL = 0xFF1C2A36;
    public static final int CELL_HOVER = 0xFF29404F;
    public static final int INSET = 0xFF0B1118;
    public static final int TEXT = 0xFFE0E6EB;
    public static final int TEXT_DIM = 0xFF6A7C8C;
    public static final int GOLD = 0xFFFFC94A;
    public static final int ON_ACCENT = 0xFF101820;

    private RangerStyle() {
    }

    /** The device frame: a light blue edge around the dark navy body. */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, ACCENT);
        graphics.fill(x, y, x + w, y + h, PANEL);
    }

    /** A recessed box, for item slots and screens inside the panel. */
    public static void inset(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, INSET);
        graphics.fill(x + 1, y + 1, x + w, y + h, CELL);
    }

    /** A flat button in the Browser's tab style. */
    public static class FlatButton extends Button {
        public FlatButton(int x, int y, int w, int h, Component message, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fill = !active ? CELL : isHoveredOrFocused() ? ACCENT : ACCENT_DARK;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, fill);
            Font font = Minecraft.getInstance().font;
            int color = !active ? TEXT_DIM : isHoveredOrFocused() ? ON_ACCENT : 0xFFFFFFFF;
            String text = font.plainSubstrByWidth(getMessage().getString(), width - 4);
            graphics.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - 8) / 2, color,
                    false);
        }
    }
}
