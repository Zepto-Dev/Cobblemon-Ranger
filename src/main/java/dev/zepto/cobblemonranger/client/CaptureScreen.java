package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.client.minigame.CaptureGame;
import dev.zepto.cobblemonranger.network.CaptureResultPayload;
import dev.zepto.cobblemonranger.network.OpenCapturePayload;
import dev.zepto.cobblemonranger.item.StylerItem;
import net.minecraft.ChatFormatting;
import dev.zepto.cobblemonranger.network.UseAssistPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** The capture minigame: a top-down arena where the player drags loops around the wild Pokémon. */
public class CaptureScreen extends Screen implements CaptureGame.Listener {
    private static final float END_DELAY_CAPTURED = 1.8f;
    private static final float END_DELAY_FAILED = 1.6f;
    private static final float HINT_TIME = 6f;
    private static final int GAUGE_SEGMENTS = 10;

    // Layout of the styler frame around the arena, in GUI pixels.
    private static final int MARGIN = 6, BEZEL_SIDE = 7, BEZEL_TOP = 12, BEZEL_BOTTOM = 28;
    /** Extra frame height for the row of assist buttons under the gauges. */
    private static final int ASSIST_ROW = 18;

    private static final int OUTLINE = 0xFF141826;
    private static final int PANEL = 0xE0141C2A;
    private static final int PANEL_EDGE = 0xFF3C5470;
    private static final int GOLD = 0xFFF2C14E, GOLD_DARK = 0xFFA8781E;
    private static final int SCREEN_GLOW = 0xFF8CE8FF;

    private final OpenCapturePayload payload;
    private final CaptureGame game;
    private final LivingEntity pokemonEntity;
    private final int lineColor;
    private final int bezelColor;
    private final ArenaTheme.Arena arena;
    private final List<float[]> decorations = new ArrayList<>();
    private final List<float[]> canopyShade = new ArrayList<>();

    private float arenaX, arenaY, scale;
    private long lastFrameNanos = -1;
    private float facing = 180f;
    private float shake;
    private boolean resultSent;
    private final boolean[] assistUsed;
    private Component assistBanner = Component.empty();
    private float assistBannerTime;
    private int assistColor;

    public CaptureScreen(OpenCapturePayload payload) {
        super(Component.translatable("screen.cobblemonranger.capture"));
        this.payload = payload;
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(payload.entityId());
        this.pokemonEntity = entity instanceof LivingEntity living ? living : null;
        float sizeHint = pokemonEntity == null ? 1f : Math.max(pokemonEntity.getBbWidth(), pokemonEntity.getBbHeight());
        this.lineColor = brighten(payload.lineColor());
        this.bezelColor = payload.lineColor() & 0xFFFFFF;
        Entity anchor = pokemonEntity != null ? pokemonEntity : minecraft.player;
        this.arena = anchor == null || minecraft.level == null
                ? new ArenaTheme.Arena(ArenaTheme.MEADOW, 0x8FD95A, 0x8FD95A, 0)
                : ArenaTheme.detect(minecraft.level, anchor);
        scatterDecorations();
        this.assistUsed = new boolean[payload.assists().size()];
        this.game = new CaptureGame(payload.pokemonType(), payload.catchRate(), payload.level(), payload.energy(),
                payload.maxEnergy(), payload.lineLength(), payload.loopPower(), sizeHint,
                System.nanoTime(), this);
    }

    @Override
    protected void init() {
        float availableWidth = width - 2 * (MARGIN + BEZEL_SIDE);
        float availableHeight = height - 2 * MARGIN - BEZEL_TOP - bezelBottom();
        scale = Math.max(0.5f, Math.min(availableWidth / CaptureGame.WIDTH, availableHeight / CaptureGame.HEIGHT));
        arenaX = (width - CaptureGame.WIDTH * scale) / 2;
        arenaY = MARGIN + BEZEL_TOP + (availableHeight - CaptureGame.HEIGHT * scale) / 2;
        playSound(SoundEvents.BEACON_ACTIVATE, 1.8f);
    }

    /** Places the ground decorations; seeded by the spot so the same place always looks the same. */
    private void scatterDecorations() {
        Random random = new Random(arena.seed());
        ArenaTheme theme = arena.theme();
        for (int i = 0; i < theme.decorationCount; i++) {
            decorations.add(new float[]{random.nextFloat() * CaptureGame.WIDTH,
                    8 + random.nextFloat() * (CaptureGame.HEIGHT - 4), theme.pickDecoration(random.nextFloat())});
        }
        decorations.sort((a, b) -> Float.compare(a[1], b[1]));
        if (theme.canopy) {
            for (int i = 0; i < 7; i++) {
                canopyShade.add(new float[]{random.nextFloat() * CaptureGame.WIDTH,
                        random.nextFloat() * CaptureGame.HEIGHT, 22 + random.nextFloat() * 26});
            }
        }
    }

    // ------------------------------------------------------------------ game loop

    private void advance() {
        long now = System.nanoTime();
        float dt = lastFrameNanos < 0 ? 0 : Math.min(0.05f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        game.tick(dt);
        shake = Math.max(0, shake - dt);
        assistBannerTime = Math.max(0, assistBannerTime - dt);

        if (Math.abs(game.vx) > 4) {
            float target = game.vx > 0 ? 135f : 225f;
            facing += Mth.wrapDegrees(target - facing) * Math.min(1, dt * 8);
        }

        if (game.phase() == CaptureGame.Phase.CAPTURED && game.phaseTime() > END_DELAY_CAPTURED) {
            finish(CaptureResultPayload.Outcome.CAPTURED);
        } else if (game.phase() == CaptureGame.Phase.FAILED && game.phaseTime() > END_DELAY_FAILED) {
            finish(CaptureResultPayload.Outcome.STYLER_EMPTY);
        }
    }

    private void finish(CaptureResultPayload.Outcome outcome) {
        sendResult(outcome);
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private void sendResult(CaptureResultPayload.Outcome outcome) {
        if (!resultSent) {
            resultSent = true;
            PacketDistributor.sendToServer(new CaptureResultPayload(payload.entityId(), outcome, game.damageTaken()));
        }
    }

    @Override
    public void onClose() {
        sendResult(CaptureResultPayload.Outcome.GAVE_UP);
        super.onClose();
    }

    @Override
    public void removed() {
        sendResult(CaptureResultPayload.Outcome.GAVE_UP);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ input

    private float toArenaX(double mouseX) {
        return (float) ((mouseX - arenaX) / scale);
    }

    private float toArenaY(double mouseY) {
        return (float) ((mouseY - arenaY) / scale);
    }

    private boolean insideArena(double mouseX, double mouseY) {
        float x = toArenaX(mouseX);
        float y = toArenaY(mouseY);
        return x >= 0 && y >= 0 && x <= CaptureGame.WIDTH && y <= CaptureGame.HEIGHT;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int index = keyCode - GLFW.GLFW_KEY_1;
        if (index >= 0 && index < payload.assists().size()) {
            useAssist(index);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void useAssist(int index) {
        if (assistUsed[index] || !game.canUseAssist()) {
            return;
        }
        OpenCapturePayload.Assist assist = payload.assists().get(index);
        assistUsed[index] = true;
        CaptureGame.AssistEffect effect = CaptureGame.assistFor(assist.type());
        assistBanner = Component.translatable("screen.cobblemonranger.assist", assist.name(),
                Component.translatable("screen.cobblemonranger.assist." + effect.name().toLowerCase(Locale.ROOT)));
        assistBannerTime = 1.6f;
        assistColor = typeColor(assist.type());
        game.useAssist(effect, payload.assistStrength());
        PacketDistributor.sendToServer(new UseAssistPayload(payload.entityId(), assist.pokemonId()));
    }

    private int bezelBottom() {
        return BEZEL_BOTTOM + (payload.assists().isEmpty() ? 0 : ASSIST_ROW);
    }

    /** Assist buttons sit in a row in the frame under the gauges; returns the index under the mouse or -1. */
    private int assistAt(double mouseX, double mouseY) {
        for (int i = 0; i < payload.assists().size(); i++) {
            int[] box = assistBox(i);
            if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                return i;
            }
        }
        return -1;
    }

    private int[] assistBox(int index) {
        int count = payload.assists().size();
        float w = CaptureGame.WIDTH * scale;
        int boxWidth = (int) Math.min(104, (w - (count - 1) * 5) / Math.max(1, count));
        int total = count * boxWidth + (count - 1) * 5;
        int x = (int) (arenaX + (w - total) / 2) + index * (boxWidth + 5);
        int y = (int) (arenaY + CaptureGame.HEIGHT * scale) + BEZEL_BOTTOM - 3;
        return new int[]{x, y, x + boxWidth, y + 14};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int assist = assistAt(mouseX, mouseY);
        if (button == 0 && assist >= 0) {
            useAssist(assist);
            return true;
        }
        if (button == 0 && insideArena(mouseX, mouseY)) {
            game.beginStroke(toArenaX(mouseX), toArenaY(mouseY));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            game.dragStroke(toArenaX(mouseX), toArenaY(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            game.endStroke();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0080C14);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advance();
        super.render(graphics, mouseX, mouseY, partialTick);

        float w = CaptureGame.WIDTH * scale;
        float h = CaptureGame.HEIGHT * scale;
        drawBezel(graphics, w, h);

        float shakeX = shake > 0 ? (float) Math.sin(shake * 90) * 3 * shake / 0.3f : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(shakeX, 0, 0);
        drawArena(graphics, w, h);
        drawPokemon(graphics);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        drawEffects(graphics);
        graphics.flush();
        drawFriendshipGauge(graphics);
        graphics.pose().popPose();
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawHud(graphics, w, h);
        drawBanner(graphics, w, h);
        graphics.pose().popPose();
    }

    /** The styler's shell framing the arena, in the styler's own colour. */
    private void drawBezel(GuiGraphics graphics, float w, float h) {
        int x1 = (int) arenaX - BEZEL_SIDE, y1 = (int) arenaY - BEZEL_TOP;
        int x2 = (int) (arenaX + w) + BEZEL_SIDE, y2 = (int) (arenaY + h) + bezelBottom();
        int body = 0xFF000000 | bezelColor;
        roundedFill(graphics, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 6, OUTLINE);
        roundedFill(graphics, x1, y1, x2, y2, 5, shade(body, 0.62f));
        roundedFill(graphics, x1, y1, x2, y2 - 2, 5, body);
        graphics.fill(x1 + 5, y1 + 1, x2 - 5, y1 + 2, mix(body, 0xFFFFFFFF, 0.45f));
        graphics.fill(x1 + 1, y1 + 5, x1 + 2, y2 - 7, mix(body, 0xFFFFFFFF, 0.25f));

        // Dark rim and a thin lit edge around the screen.
        int sx1 = (int) arenaX, sy1 = (int) arenaY, sx2 = (int) (arenaX + w), sy2 = (int) (arenaY + h);
        roundedFill(graphics, sx1 - 3, sy1 - 3, sx2 + 3, sy2 + 3, 2, OUTLINE);
        graphics.fill(sx1 - 1, sy1 - 1, sx2 + 1, sy2 + 1, SCREEN_GLOW);

        // Three status lights along the top: green, yellow and red, lit by how much energy is left.
        float energyFraction = game.energy() / (float) Math.max(1, game.maxEnergy);
        int[] lit = {0xFF5BE07A, 0xFFFFD23F, 0xFFFF5A3C};
        int litCount = energyFraction > 0.5f ? 3 : energyFraction > 0.25f ? 2 : 1;
        int cx = (x1 + x2) / 2;
        for (int i = 0; i < 3; i++) {
            int lx = cx - 13 + i * 10;
            int ly = y1 + 3;
            boolean on = 2 - i < litCount;
            roundedFill(graphics, lx - 1, ly - 1, lx + 7, ly + 5, 1, OUTLINE);
            graphics.fill(lx, ly, lx + 6, ly + 4, on ? lit[i] : shade(lit[i], 0.35f));
            if (on) {
                graphics.fill(lx + 1, ly + 1, lx + 3, ly + 2, 0xC0FFFFFF);
            }
        }
        // Corner screws.
        for (int[] corner : new int[][]{{x1 + 3, y1 + 3}, {x2 - 6, y1 + 3}, {x1 + 3, y2 - 8}, {x2 - 6, y2 - 8}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 3, corner[1] + 3, shade(body, 0.5f));
            graphics.fill(corner[0], corner[1], corner[0] + 2, corner[1] + 2, mix(body, 0xFFFFFFFF, 0.35f));
        }
    }

    private void drawArena(GuiGraphics graphics, float w, float h) {
        ArenaTheme theme = arena.theme();
        graphics.enableScissor((int) arenaX, (int) arenaY, (int) Math.ceil(arenaX + w), (int) Math.ceil(arenaY + h));
        graphics.pose().pushPose();
        graphics.pose().translate(arenaX, arenaY, 0);
        graphics.pose().scale(scale, scale, 1);

        setColor(graphics, arena.groundTint());
        for (int tx = 0; tx < CaptureGame.WIDTH; tx += ArenaTheme.TILE_SIZE) {
            for (int ty = 0; ty < CaptureGame.HEIGHT; ty += ArenaTheme.TILE_SIZE) {
                graphics.blit(theme.tile, tx, ty, 0, 0, ArenaTheme.TILE_SIZE, ArenaTheme.TILE_SIZE,
                        ArenaTheme.TILE_SIZE, ArenaTheme.TILE_SIZE);
            }
        }
        graphics.setColor(1, 1, 1, 1);

        Shapes shapes = new Shapes(graphics);
        for (float[] blob : canopyShade) {
            shapes.disc(blob[0], blob[1], blob[2], 0x14000000);
            shapes.disc(blob[0] + blob[2] * 0.25f, blob[1] - blob[2] * 0.15f, blob[2] * 0.7f, 0x10000000);
            shapes.disc(blob[0] - blob[2] * 0.2f, blob[1] + blob[2] * 0.2f, blob[2] * 0.45f, 0x10000000);
        }
        graphics.flush();

        for (float[] decoration : decorations) {
            int sprite = (int) decoration[2];
            if (sprite < ArenaTheme.Decor.TINTED_COUNT) {
                setColor(graphics, arena.plantTint());
            }
            // Drawn a bit larger than the ground so small sprites still read as flowers and stones.
            graphics.blit(ArenaTheme.DECORATIONS, (int) decoration[0] - 12, (int) decoration[1] - 24, 24, 24,
                    (sprite % 8) * 16, (sprite / 8) * 16, 16, 16, 128, 64);
            graphics.setColor(1, 1, 1, 1);
        }

        // A lit clearing in the middle and a vignette towards the edges, darker underground.
        shapes = new Shapes(graphics);
        float light = theme.light;
        if (light < 1) {
            shapes.rect(0, 0, CaptureGame.WIDTH, CaptureGame.HEIGHT, (int) ((1 - light) * 0x70) << 24);
        }
        int glow = light < 0.6f ? 0xFFE6B0 : 0xFFFFFF;
        graphics.pose().pushPose();
        graphics.pose().translate(CaptureGame.WIDTH / 2, CaptureGame.HEIGHT / 2, 0);
        graphics.pose().scale(1, 0.62f, 1);
        Shapes ellipse = new Shapes(graphics);
        for (int i = 0; i < 4; i++) {
            ellipse.disc(0, 0, 170 - i * 35, ((int) (0x12 * (0.6f + light * 0.4f)) << 24) | glow);
        }
        graphics.pose().popPose();
        int edge = (int) (0x48 + (1 - light) * 0x80);
        for (int i = 0; i < 6; i++) {
            int color = (edge * (6 - i) / 6) << 24;
            float in = i * 5, out = in + 5;
            shapes.rect(in, in, CaptureGame.WIDTH - in, out, color);
            shapes.rect(in, CaptureGame.HEIGHT - out, CaptureGame.WIDTH - in, CaptureGame.HEIGHT - in, color);
            shapes.rect(in, out, out, CaptureGame.HEIGHT - out, color);
            shapes.rect(CaptureGame.WIDTH - out, out, CaptureGame.WIDTH - in, CaptureGame.HEIGHT - out, color);
        }
        graphics.flush();
        graphics.pose().popPose();
        graphics.disableScissor();

        shapes = new Shapes(graphics);
        if (game.hitFlash > 0) {
            int alpha = (int) (game.hitFlash / 0.35f * 0x70);
            shapes.rect(arenaX, arenaY, arenaX + w, arenaY + h, (alpha << 24) | 0xFF2020);
        }
        for (CaptureGame.Hazard hazard : game.hazards) {
            float hx = sx(hazard.x), hy = sy(hazard.y), r = hazard.radius * scale;
            if (hazard.exploding()) {
                shapes.disc(hx, hy, r, 0xC0FF7A1A);
                shapes.disc(hx, hy, r * 0.6f, 0xE0FFD23F);
            } else {
                float pulse = 0.5f + 0.5f * (float) Math.sin(hazard.timer * 20);
                shapes.disc(hx, hy, r, ((int) (0x30 + 0x30 * pulse) << 24) | 0xFF3030);
                shapes.ring(hx, hy, r, 1.5f, 0xC0FF5050);
            }
        }
        // Shadow under the Pokémon
        shapes.disc(sx(game.x), sy(game.y) + game.radius * scale * 0.7f, game.radius * scale * 0.7f, 0x50000000);
        graphics.flush();
    }

    private void drawPokemon(GuiGraphics graphics) {
        float px = sx(game.x);
        float py = sy(game.y);
        float r = game.radius * scale;
        boolean blink = game.isWindingUp() && (System.currentTimeMillis() / 80) % 2 == 0;

        if (pokemonEntity == null) {
            Shapes shapes = new Shapes(graphics);
            shapes.disc(px, py, r, blink ? 0xFFFF6060 : 0xFFE0E0E0);
            graphics.flush();
            return;
        }

        float size = Math.max(pokemonEntity.getBbWidth(), pokemonEntity.getBbHeight());
        float renderScale = r * 2.4f / Math.max(0.1f, size) / Math.max(0.01f, pokemonEntity.getScale());
        Vector3f offset = new Vector3f(0, pokemonEntity.getBbHeight() / 2f, 0);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);

        float bodyRot = pokemonEntity.yBodyRot, bodyRotO = pokemonEntity.yBodyRotO;
        float yRot = pokemonEntity.getYRot(), yRotO = pokemonEntity.yRotO;
        float xRot = pokemonEntity.getXRot(), xRotO = pokemonEntity.xRotO;
        float headRot = pokemonEntity.yHeadRot, headRotO = pokemonEntity.yHeadRotO;
        pokemonEntity.yBodyRot = pokemonEntity.yBodyRotO = facing;
        pokemonEntity.setYRot(facing);
        pokemonEntity.yRotO = facing;
        pokemonEntity.setXRot(0);
        pokemonEntity.xRotO = 0;
        pokemonEntity.yHeadRot = pokemonEntity.yHeadRotO = facing;

        graphics.enableScissor((int) arenaX, (int) arenaY, (int) (arenaX + CaptureGame.WIDTH * scale),
                (int) (arenaY + CaptureGame.HEIGHT * scale));
        // Cobblemon draws its name and "Press R to battle" label while the player looks at a Pokémon; hiding the GUI
        // for this one draw keeps the label out of the arena.
        boolean hideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        InventoryScreen.renderEntityInInventory(graphics, px, py, renderScale, offset, pose, null, pokemonEntity);
        minecraft.options.hideGui = hideGui;
        graphics.disableScissor();

        pokemonEntity.yBodyRot = bodyRot;
        pokemonEntity.yBodyRotO = bodyRotO;
        pokemonEntity.setYRot(yRot);
        pokemonEntity.yRotO = yRotO;
        pokemonEntity.setXRot(xRot);
        pokemonEntity.xRotO = xRotO;
        pokemonEntity.yHeadRot = headRot;
        pokemonEntity.yHeadRotO = headRotO;

        if (blink) {
            Shapes shapes = new Shapes(graphics);
            shapes.ring(px, py, r * 1.2f, 2f, 0xC0FF4040);
            graphics.flush();
        }
        if (game.isStunned() || game.isSlowed() || game.isCalmed()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0);
            Shapes shapes = new Shapes(graphics);
            shapes.ring(px, py, r * (1.3f + 0.1f * pulse), 2f, 0xA0000000 | assistColor);
            graphics.flush();
        }
    }

    private void drawEffects(GuiGraphics graphics) {
        Shapes shapes = new Shapes(graphics);
        int attackColor = attackColor(game.signatureAttack);

        for (CaptureGame.Shockwave wave : game.shockwaves) {
            shapes.ring(sx(wave.x), sy(wave.y), wave.radius * scale, 3f * scale, attackColor | 0xD0000000);
        }
        for (CaptureGame.Projectile p : game.projectiles) {
            shapes.disc(sx(p.x), sy(p.y), p.radius * scale, attackColor | 0xFF000000);
            shapes.disc(sx(p.x), sy(p.y), p.radius * scale * 0.5f, 0xFFFFFFFF);
        }

        // Dashed target ring around the Pokémon, turning slowly.
        float px = sx(game.x), py = sy(game.y), ringRadius = (game.radius + 6) * scale;
        float spin = (System.currentTimeMillis() % 6000) / 6000f * (float) (Math.PI * 2);
        for (int i = 0; i < 16; i++) {
            float start = spin + (float) (Math.PI * 2 * i / 16);
            shapes.arc(px, py, ringRadius, 1.2f * scale, start, (float) (Math.PI / 16), 0xA0FFFFFF);
        }

        for (CaptureGame.LoopFlash flash : game.flashes) {
            int alpha = (int) ((1 - flash.age / 0.5f) * 255);
            int color = (alpha << 24) | (flash.enclosed ? 0xFFFFFF : 0x8090A0);
            for (int i = 0; i < flash.xs.length; i++) {
                int j = (i + 1) % flash.xs.length;
                shapes.line(sx(flash.xs[i]), sy(flash.ys[i]), sx(flash.xs[j]), sy(flash.ys[j]), 3f * scale, color);
            }
        }

        List<float[]> line = game.line();
        for (int i = 1; i < line.size(); i++) {
            float[] a = line.get(i - 1);
            float[] b = line.get(i);
            float fade = 1 - game.pointAge(a);
            int glowAlpha = (int) (0x90 * fade);
            int coreAlpha = (int) (0x40 + 0xBF * fade);
            shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), 5f * scale, (glowAlpha << 24) | (lineColor & 0xFFFFFF));
            shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), 1.5f * scale, (coreAlpha << 24) | 0xFFFFFF);
        }
        if (!line.isEmpty()) {
            float[] tip = line.get(line.size() - 1);
            shapes.disc(sx(tip[0]), sy(tip[1]), 3f * scale, 0xFF000000 | lineColor);
        }
    }

    /** Segmented capture gauge under the Pokémon, as in the DS games. */
    private void drawFriendshipGauge(GuiGraphics graphics) {
        int segment = 5, gap = 1;
        int total = GAUGE_SEGMENTS * (segment + gap) - gap;
        int left = Math.round(sx(game.x)) - total / 2;
        int top = Math.round(sy(game.y) + (game.radius + 9) * scale);
        top = Math.min(top, (int) (arenaY + CaptureGame.HEIGHT * scale) - 8);
        graphics.fill(left - 2, top - 2, left + total + 2, top + 6, OUTLINE);
        float filled = game.gauge() * GAUGE_SEGMENTS;
        for (int i = 0; i < GAUGE_SEGMENTS; i++) {
            int x = left + i * (segment + gap);
            graphics.fill(x, top, x + segment, top + 4, 0xFFD8DEE8);
            float part = Math.max(0, Math.min(1, filled - i));
            if (part > 0) {
                graphics.fill(x, top, x + Math.max(1, Math.round(segment * part)), top + 4, GOLD);
                graphics.fill(x, top, x + Math.max(1, Math.round(segment * part)), top + 1, 0xFFFFF0B0);
            }
        }
    }

    private void drawHud(GuiGraphics graphics, float w, float h) {
        int left = (int) arenaX, right = (int) (arenaX + w);
        int stripTop = (int) (arenaY + h) + 4;
        int rowTop = stripTop + (BEZEL_BOTTOM - 4 - 14) / 2 - 1;

        // Styler energy: gold frame, red counter badge and a green bar, bottom left.
        int energyRight = left + (int) (w * 0.42f);
        roundedFill(graphics, left - 1, rowTop - 1, energyRight + 1, rowTop + 15, 3, OUTLINE);
        roundedFill(graphics, left, rowTop, energyRight, rowTop + 14, 3, GOLD_DARK);
        roundedFill(graphics, left, rowTop, energyRight, rowTop + 13, 3, GOLD);
        String count = String.valueOf(game.energy());
        int badgeRight = left + 4 + Math.max(18, font.width(count) + 8);
        roundedFill(graphics, left + 2, rowTop + 2, badgeRight, rowTop + 12, 2, 0xFFD8402F);
        graphics.fill(left + 4, rowTop + 2, badgeRight - 2, rowTop + 3, 0xFFFF7A5C);
        graphics.drawString(font, count, (left + 2 + badgeRight) / 2 - font.width(count) / 2, rowTop + 3, 0xFFFFFFFF, true);
        float energyFraction = game.energy() / (float) Math.max(1, game.maxEnergy);
        int energyColor = energyFraction > 0.5f ? 0xFF6FD94A : energyFraction > 0.25f ? 0xFFFFCC00 : 0xFFFF3B30;
        int barLeft = badgeRight + 3, barRight = energyRight - 4;
        graphics.fill(barLeft, rowTop + 4, barRight, rowTop + 10, OUTLINE);
        graphics.fill(barLeft + 1, rowTop + 5, barRight - 1, rowTop + 9, 0xFF2A3344);
        int fillRight = barLeft + 1 + Math.round((barRight - barLeft - 2) * energyFraction);
        graphics.fill(barLeft + 1, rowTop + 5, fillRight, rowTop + 9, energyColor);
        graphics.fill(barLeft + 1, rowTop + 5, fillRight, rowTop + 6, mix(energyColor, 0xFFFFFFFF, 0.5f));

        // The styler itself in the middle of the frame.
        int cx = (left + right) / 2;
        int cy = rowTop + 7;
        Shapes shapes = new Shapes(graphics);
        shapes.disc(cx, cy, 11, OUTLINE);
        shapes.disc(cx, cy, 10, 0xFF2A3344);
        shapes.disc(cx - 1, cy - 1, 8, 0xFF3A465C);
        graphics.flush();
        ItemStack styler = heldStyler();
        if (!styler.isEmpty()) {
            graphics.renderItem(styler, cx - 8, cy - 8);
        }

        // Line length left, bottom right.
        int lineLeft = right - (int) (w * 0.3f);
        roundedFill(graphics, lineLeft - 1, rowTop - 1, right + 1, rowTop + 15, 3, OUTLINE);
        roundedFill(graphics, lineLeft, rowTop, right, rowTop + 14, 3, PANEL_EDGE);
        roundedFill(graphics, lineLeft + 1, rowTop + 1, right - 1, rowTop + 13, 2, 0xFF1A2232);
        Component lineLabel = Component.translatable("screen.cobblemonranger.line");
        graphics.drawString(font, lineLabel, lineLeft + 5, rowTop + 3, SCREEN_GLOW, true);
        int lineBarLeft = lineLeft + 9 + font.width(lineLabel), lineBarRight = right - 5;
        graphics.fill(lineBarLeft, rowTop + 4, lineBarRight, rowTop + 10, OUTLINE);
        graphics.fill(lineBarLeft + 1, rowTop + 5, lineBarRight - 1, rowTop + 9, 0xFF2A3344);
        int lineFill = lineBarLeft + 1 + Math.round((lineBarRight - lineBarLeft - 2) * (1 - game.lineUsage()));
        graphics.fill(lineBarLeft + 1, rowTop + 5, lineFill, rowTop + 9, 0xFF000000 | lineColor);
        graphics.fill(lineBarLeft + 1, rowTop + 5, lineFill, rowTop + 6, 0xFFFFFFFF);

        // Pokémon name, level and type in a label at the top of the screen.
        Component name = payload.name().copy();
        if (payload.shiny()) {
            name = Component.literal("★ ").withStyle(ChatFormatting.GOLD).append(name);
        }
        Component levelText = Component.translatable("screen.cobblemonranger.level", payload.level());
        String type = payload.pokemonType() == null ? "" : payload.pokemonType().toLowerCase(Locale.ROOT);
        Component typeText = Component.literal(type.isEmpty() ? "" : Character.toUpperCase(type.charAt(0)) + type.substring(1));
        int nameWidth = font.width(name), levelWidth = font.width(levelText), typeWidth = font.width(typeText);
        int labelWidth = nameWidth + 6 + levelWidth + (typeWidth > 0 ? 8 + typeWidth : 0) + 12;
        int labelLeft = cx - labelWidth / 2, labelTop = (int) arenaY + 5;
        pill(graphics, labelLeft, labelTop, labelLeft + labelWidth, labelTop + 13);
        int textX = labelLeft + 6;
        graphics.drawString(font, name, textX, labelTop + 3, 0xFFFFFFFF, true);
        textX += nameWidth + 6;
        graphics.drawString(font, levelText, textX, labelTop + 3, 0xFFB8C8DA, true);
        textX += levelWidth + 5;
        if (typeWidth > 0) {
            int chip = 0xFF000000 | typeColor(type);
            roundedFill(graphics, textX - 1, labelTop + 2, textX + typeWidth + 5, labelTop + 11, 1, chip);
            graphics.drawString(font, typeText, textX + 2, labelTop + 3, 0xFFFFFFFF, true);
        }

        if (!payload.assists().isEmpty()) {
            drawAssists(graphics);
        }

        // Effects from assists, as small labels down the right side of the screen.
        List<Component> statuses = new ArrayList<>();
        if (game.isStunned()) statuses.add(Component.translatable("screen.cobblemonranger.status.stunned"));
        if (game.isSlowed()) statuses.add(Component.translatable("screen.cobblemonranger.status.slowed"));
        if (game.isCalmed()) statuses.add(Component.translatable("screen.cobblemonranger.status.calm"));
        if (game.isBoosted()) statuses.add(Component.translatable("screen.cobblemonranger.status.boosted"));
        for (int i = 0; i < statuses.size(); i++) {
            Component status = statuses.get(i);
            int statusRight = right - 6, statusTop = (int) arenaY + 48 + i * 14;
            pill(graphics, statusRight - font.width(status) - 8, statusTop, statusRight, statusTop + 11);
            graphics.drawString(font, status, statusRight - font.width(status) - 4, statusTop + 2, GOLD, true);
        }

        // Message box near the bottom of the screen: the controls at first, then attack warnings.
        Component message = null;
        if (game.phase() == CaptureGame.Phase.INTRO
                || (game.phase() == CaptureGame.Phase.PLAYING && game.phaseTime() < HINT_TIME)) {
            message = Component.translatable(payload.assists().isEmpty()
                    ? "screen.cobblemonranger.hint" : "screen.cobblemonranger.hint_assist");
        } else if (game.phase() == CaptureGame.Phase.PLAYING && game.isWindingUp()) {
            message = Component.translatable("screen.cobblemonranger.charging", payload.name());
        }
        if (message != null) {
            List<FormattedCharSequence> lines = font.split(message, (int) (w * 0.62f));
            int boxWidth = 0;
            for (FormattedCharSequence line : lines) {
                boxWidth = Math.max(boxWidth, font.width(line));
            }
            int boxHeight = lines.size() * 10 + 4;
            int boxLeft = cx - boxWidth / 2 - 6, boxBottom = (int) (arenaY + h) - 6;
            pill(graphics, boxLeft, boxBottom - boxHeight, boxLeft + boxWidth + 12, boxBottom);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawCenteredString(font, lines.get(i), cx, boxBottom - boxHeight + 3 + i * 10, 0xFFFFFFFF);
            }
        }
    }

    /** A swoosh band across the arena with the phase's title, like the games' "Capture On!" cut-in. */
    private void drawBanner(GuiGraphics graphics, float w, float h) {
        if (assistBannerTime > 0 && game.phase() == CaptureGame.Phase.PLAYING) {
            float alpha = Math.min(1, assistBannerTime / 0.4f);
            float bannerScale = Math.min(1.5f, (w - 24) / Math.max(1, font.width(assistBanner)));
            int bannerWidth = (int) (font.width(assistBanner) * bannerScale);
            int bannerTop = (int) arenaY + 24;
            int centre = (int) (arenaX + w / 2);
            if (alpha > 0.5f) {
                pill(graphics, centre - bannerWidth / 2 - 6, bannerTop, centre + bannerWidth / 2 + 6,
                        bannerTop + 6 + (int) (8 * bannerScale));
            }
            graphics.pose().pushPose();
            graphics.pose().translate(centre, bannerTop + 3, 0);
            graphics.pose().scale(bannerScale, bannerScale, 1);
            graphics.drawCenteredString(font, assistBanner, 0, 0,
                    ((int) (alpha * 255) << 24) | (0xFFFFFF & brighten(assistColor)));
            graphics.pose().popPose();
        }
        Component text;
        int top, middle, bottom;
        switch (game.phase()) {
            case INTRO -> {
                text = Component.translatable("screen.cobblemonranger.capture_on");
                top = 0xFFFFD23F;
                middle = 0xFFFF7A1A;
                bottom = 0xFFD8302F;
            }
            case CAPTURED -> {
                text = Component.translatable("screen.cobblemonranger.capture_complete");
                top = 0xFFFFF0A0;
                middle = 0xFF3FB8FF;
                bottom = 0xFF2459C8;
            }
            case FAILED -> {
                text = Component.translatable("screen.cobblemonranger.styler_broken");
                top = 0xFFB0B8C4;
                middle = 0xFF5A6070;
                bottom = 0xFF8A2030;
            }
            default -> {
                return;
            }
        }
        float slide = Math.min(1, game.phaseTime() * 5);
        int bandHeight = 28;
        int centerY = (int) (arenaY + h / 2);
        int bandLeft = (int) arenaX;
        int bandRight = bandLeft + Math.round(w * slide);
        int bandTop = centerY - bandHeight / 2;
        graphics.fill(bandLeft, bandTop - 1, bandRight, bandTop + bandHeight + 1, OUTLINE);
        graphics.fill(bandLeft, bandTop, bandRight, bandTop + 3, top);
        graphics.fillGradient(bandLeft, bandTop + 3, bandRight, bandTop + bandHeight - 3, middle, bottom);
        graphics.fill(bandLeft, bandTop + bandHeight - 3, bandRight, bandTop + bandHeight, shade(bottom, 0.6f));
        // Speed streaks.
        long now = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            int streakY = bandTop + 5 + (i * 7) % (bandHeight - 10);
            int streakX = bandLeft + (int) ((now / 2 + i * 97) % Math.max(1, (int) w));
            if (streakX + 24 < bandRight) {
                graphics.fill(streakX, streakY, streakX + 24, streakY + 1, 0x60FFFFFF);
            }
        }

        if (slide >= 1) {
            float pop = Math.min(1, (game.phaseTime() - 0.2f) * 6);
            float textScale = 2f * (0.7f + 0.3f * Math.max(0, pop));
            Component bold = text.copy().withStyle(ChatFormatting.BOLD);
            graphics.pose().pushPose();
            graphics.pose().translate(arenaX + w / 2, centerY, 0);
            graphics.pose().scale(textScale, textScale, 1);
            int textWidth = font.width(bold);
            graphics.drawString(font, bold, -textWidth / 2 + 1, -3, OUTLINE, false);
            graphics.drawString(font, bold, -textWidth / 2, -4, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        }
    }

    private ItemStack heldStyler() {
        if (minecraft == null || minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof StylerItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Assist buttons: a chip in the helper's type colour with its key number, greyed out once used. */
    private void drawAssists(GuiGraphics graphics) {
        for (int i = 0; i < payload.assists().size(); i++) {
            OpenCapturePayload.Assist assist = payload.assists().get(i);
            int[] box = assistBox(i);
            boolean available = !assistUsed[i] && game.canUseAssist();
            int color = 0xFF000000 | typeColor(assist.type());
            int body = available ? color : 0xFF3A4252;
            int edge = assist.partner() ? GOLD : OUTLINE;
            roundedFill(graphics, box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1, 3, edge);
            roundedFill(graphics, box[0], box[1], box[2], box[3], 2, shade(body, 0.6f));
            roundedFill(graphics, box[0], box[1], box[2], box[3] - 1, 2, body);
            if (available) {
                graphics.fill(box[0] + 2, box[1] + 1, box[2] - 2, box[1] + 2, mix(body, 0xFFFFFFFF, 0.4f));
            }
            // Key number badge.
            roundedFill(graphics, box[0] + 2, box[1] + 2, box[0] + 11, box[3] - 2, 1, OUTLINE);
            graphics.drawString(font, String.valueOf(i + 1), box[0] + 4, box[1] + 3, available ? 0xFFFFFFFF : 0xFF707888,
                    false);
            String label = (assist.partner() ? "★" : "") + assist.name().getString();
            label = font.plainSubstrByWidth(label, box[2] - box[0] - 17);
            graphics.drawString(font, label, box[0] + 14, box[1] + 3, available ? 0xFFFFFFFF : 0xFF8890A0, available);
        }
    }

    private static void pill(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        roundedFill(graphics, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 3, PANEL_EDGE);
        roundedFill(graphics, x1, y1, x2, y2, 3, PANEL);
    }

    /** A filled rectangle with stepped, pixel-art corners. */
    private static void roundedFill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        graphics.fill(x1, y1 + radius, x2, y2 - radius, color);
        for (int i = 0; i < radius; i++) {
            int inset = radius - i;
            graphics.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            graphics.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    private static void setColor(GuiGraphics graphics, int rgb) {
        graphics.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1);
    }

    // ------------------------------------------------------------------ listener

    @Override
    public void onLoop(boolean enclosed, int loops) {
        if (enclosed) {
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, Math.min(2f, 0.9f + loops * 0.07f));
        }
    }

    @Override
    public void onLineBroken(int damage) {
        shake = 0.3f;
        playSound(SoundEvents.GLASS_BREAK, 1.5f);
    }

    @Override
    public void onWindup(CaptureGame.AttackStyle style) {
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.6f);
    }

    @Override
    public void onCaptured() {
        playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f);
    }

    @Override
    public void onFailed() {
        playSound(SoundEvents.ITEM_BREAK, 0.8f);
    }

    @Override
    public void onAssist(CaptureGame.AssistEffect effect) {
        shake = 0.2f;
        playSound(switch (effect) {
            case STUN -> SoundEvents.FIRECHARGE_USE;
            case SOAK -> SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
            case GUST -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
            case CRUSH -> SoundEvents.ANVIL_LAND;
            default -> SoundEvents.ILLUSIONER_CAST_SPELL;
        }, 1.2f);
    }

    // ------------------------------------------------------------------ helpers

    private float sx(float x) {
        return arenaX + x * scale;
    }

    private float sy(float y) {
        return arenaY + y * scale;
    }

    private static void playSound(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    /** Colours of the Pokémon types, as on the DS games' type labels. */
    private static int typeColor(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "fire" -> 0xF08030;
            case "water" -> 0x6890F0;
            case "grass" -> 0x78C850;
            case "electric" -> 0xF8D030;
            case "ice" -> 0x98D8D8;
            case "fighting" -> 0xC03028;
            case "poison" -> 0xA040A0;
            case "ground" -> 0xE0C068;
            case "flying" -> 0xA890F0;
            case "psychic" -> 0xF85888;
            case "bug" -> 0xA8B820;
            case "rock" -> 0xB8A038;
            case "ghost" -> 0x705898;
            case "dragon" -> 0x7038F8;
            case "dark" -> 0x705848;
            case "steel" -> 0xB8B8D0;
            case "fairy" -> 0xEE99AC;
            default -> 0xA8A878;
        };
    }

    private static int attackColor(CaptureGame.AttackStyle style) {
        return switch (style) {
            case DASH -> 0xFFFFFF;
            case BURST -> 0x4FC3F7;
            case SHOCKWAVE -> 0xE040FB;
            case ERUPTION -> 0xFF7A1A;
        };
    }

    private static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor), g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int argb, int other, float amount) {
        int r = (int) Mth.lerp(amount, (argb >> 16) & 0xFF, (other >> 16) & 0xFF);
        int g = (int) Mth.lerp(amount, (argb >> 8) & 0xFF, (other >> 8) & 0xFF);
        int b = (int) Mth.lerp(amount, argb & 0xFF, other & 0xFF);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Dark dyes would make an invisible line, so mix the styler colour towards white. */
    private static int brighten(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = r + (255 - r) * 2 / 5;
        g = g + (255 - g) * 2 / 5;
        b = b + (255 - b) * 2 / 5;
        return (r << 16) | (g << 8) | b;
    }
}
