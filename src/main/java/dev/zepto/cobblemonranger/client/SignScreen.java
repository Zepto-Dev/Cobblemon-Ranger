package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.network.UseSignPayload;
import dev.zepto.cobblemonranger.signs.RangerSign;
import dev.zepto.cobblemonranger.signs.SignRecognizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Draw a Ranger Sign on the styler's pad to call a legendary Pokémon. The signs the player knows are listed alongside. */
public class SignScreen extends Screen {
    private static final float CLOSE_DELAY = 0.7f;

    private final List<float[]> stroke = new ArrayList<>();
    private boolean drawing;
    private float padX, padY, padSize;
    private Component status = Component.empty();
    private int statusColor = 0xFFFFFFFF;
    private RangerSign matched;
    private long matchedAt;

    public SignScreen() {
        super(Component.translatable("screen.cobblemonranger.sign"));
    }

    @Override
    protected void init() {
        padSize = Math.min(height - 70, Math.min(220, width * 0.5f));
        float listWidth = 150;
        float total = padSize + 16 + listWidth;
        padX = (width - total) / 2;
        padY = (height - padSize) / 2 + 6;
        status = Component.translatable("screen.cobblemonranger.sign_hint");
        statusColor = 0xFF8090A0;
        playSound(SoundEvents.BEACON_AMBIENT, 1.6f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && matched == null && insidePad(mouseX, mouseY)) {
            drawing = true;
            stroke.clear();
            stroke.add(new float[]{(float) mouseX, (float) mouseY});
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && drawing) {
            float x = Math.max(padX, Math.min(padX + padSize, (float) mouseX));
            float y = Math.max(padY, Math.min(padY + padSize, (float) mouseY));
            float[] last = stroke.get(stroke.size() - 1);
            if (Math.hypot(x - last[0], y - last[1]) >= 2) {
                stroke.add(new float[]{x, y});
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && drawing) {
            drawing = false;
            recognize();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void recognize() {
        if (SignRecognizer.pathLength(stroke) < padSize * 0.3f) {
            stroke.clear();
            return;
        }
        List<float[][]> templates = new ArrayList<>();
        List<Boolean> closed = new ArrayList<>();
        for (RangerSign sign : RangerSign.values()) {
            templates.add(sign.template());
            closed.add(sign.closed());
        }
        int index = SignRecognizer.recognize(stroke, templates, closed);
        if (index < 0) {
            fail(Component.translatable("screen.cobblemonranger.sign_unknown"));
            return;
        }
        RangerSign sign = RangerSign.values()[index];
        if (!ClientRangerData.isUnlocked(sign)) {
            fail(Component.translatable("screen.cobblemonranger.sign_locked", sign.rank()));
            return;
        }
        int cooldown = ClientRangerData.cooldownSeconds(sign);
        if (cooldown > 0) {
            fail(Component.translatable("screen.cobblemonranger.sign_cooldown", sign.displayName(), cooldown));
            return;
        }
        matched = sign;
        matchedAt = System.currentTimeMillis();
        status = Component.translatable("screen.cobblemonranger.sign_success", sign.displayName());
        statusColor = 0xFFFFD23F;
        playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4f);
        PacketDistributor.sendToServer(new UseSignPayload(sign.id()));
    }

    private void fail(Component message) {
        status = message;
        statusColor = 0xFFFF6B5E;
        stroke.clear();
        playSound(SoundEvents.VILLAGER_NO, 1.4f);
    }

    private boolean insidePad(double x, double y) {
        return x >= padX && y >= padY && x <= padX + padSize && y <= padY + padSize;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0080C14);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (matched != null && (System.currentTimeMillis() - matchedAt) / 1000f > CLOSE_DELAY) {
            onClose();
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        Shapes shapes = new Shapes(graphics);
        shapes.rect(padX - 3, padY - 3, padX + padSize + 3, padY + padSize + 3, 0xFF5BC0EB);
        shapes.rect(padX - 1, padY - 1, padX + padSize + 1, padY + padSize + 1, 0xFF0B1F2A);
        shapes.rect(padX, padY, padX + padSize, padY + padSize, 0xFF10263A);
        float step = padSize / 8;
        for (int i = 1; i < 8; i++) {
            for (int j = 1; j < 8; j++) {
                shapes.disc(padX + i * step, padY + j * step, 1f, 0x405BC0EB);
            }
        }

        int strokeColor = matched != null ? 0xFFFFD23F : 0xFFFFFFFF;
        for (int i = 1; i < stroke.size(); i++) {
            float[] a = stroke.get(i - 1);
            float[] b = stroke.get(i);
            shapes.line(a[0], a[1], b[0], b[1], 6f, 0x705BC0EB);
            shapes.line(a[0], a[1], b[0], b[1], 2.5f, strokeColor);
        }

        // The sign list: a small picture of each glyph, its name and whether it can be used.
        float listX = padX + padSize + 16;
        float rowHeight = Math.min(36, padSize / RangerSign.values().length);
        float glyph = rowHeight - 8;
        for (int i = 0; i < RangerSign.values().length; i++) {
            RangerSign sign = RangerSign.values()[i];
            float rowY = padY + i * rowHeight;
            boolean unlocked = ClientRangerData.isUnlocked(sign);
            int cooldown = ClientRangerData.cooldownSeconds(sign);
            int color = !unlocked ? 0xFF505A64 : cooldown > 0 ? 0xFF8090A0 : sign == matched ? 0xFFFFD23F : 0xFF5BC0EB;
            shapes.rect(listX, rowY + 2, listX + glyph + 4, rowY + glyph + 6, 0xFF10263A);
            if (unlocked) {
                float[][] t = sign.template();
                for (int k = 1; k < t.length; k++) {
                    shapes.line(listX + 2 + t[k - 1][0] * glyph, rowY + 4 + t[k - 1][1] * glyph,
                            listX + 2 + t[k][0] * glyph, rowY + 4 + t[k][1] * glyph, 1.5f, color);
                }
                if (!sign.closed()) {
                    shapes.disc(listX + 2 + t[0][0] * glyph, rowY + 4 + t[0][1] * glyph, 2f, 0xFFFFFFFF);
                }
            }
        }
        graphics.flush();

        for (int i = 0; i < RangerSign.values().length; i++) {
            RangerSign sign = RangerSign.values()[i];
            float rowY = padY + i * rowHeight;
            int textX = (int) (listX + glyph + 10);
            boolean unlocked = ClientRangerData.isUnlocked(sign);
            int cooldown = ClientRangerData.cooldownSeconds(sign);
            graphics.drawString(font, unlocked ? sign.displayName() : Component.literal("???"), textX,
                    (int) rowY + 4, unlocked ? 0xFFFFFFFF : 0xFF707A84, true);
            Component detail = !unlocked ? Component.translatable("screen.cobblemonranger.sign_rank", sign.rank())
                    : cooldown > 0 ? Component.translatable("screen.cobblemonranger.sign_wait", cooldown)
                    : sign.description();
            graphics.drawString(font, detail, textX, (int) rowY + 15, 0xFF8090A0, false);
        }

        graphics.drawCenteredString(font, title, (int) (padX + padSize / 2), (int) padY - 18, 0xFF5BC0EB);
        graphics.drawCenteredString(font, status, width / 2, (int) (padY + padSize + 8), statusColor);
    }

    private static void playSound(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}
