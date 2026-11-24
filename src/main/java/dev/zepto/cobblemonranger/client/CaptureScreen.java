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
    private static final float CHARGE_FX_TIME = 1.8f, DRAGON_FX_TIME = 1f;

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
    private final AttackEffects attacks;
    private final List<float[]> decorations = new ArrayList<>();
    private final List<float[]> canopyShade = new ArrayList<>();

    private float arenaX, arenaY, scale;
    private long lastFrameNanos = -1;
    private float facing = 180f;
    private float shake;
    private boolean resultSent;
    /** init() runs again on every window resize; the music starts only the first time. */
    private boolean musicStarted;
    private final boolean[] assistUsed;
    private Component assistBanner = Component.empty();
    private float assistBannerTime;
    private int assistColor;
    /** Loops towards the Partner's own assist; it can be used once this reaches the payload's full charge. */
    private int partnerCharge;
    /** Loops drawn around the Pokémon since the Partner's assist was used, or since the start. */
    private int chargeLoops;
    /** Seconds since an Electric or Dragon assist went off, for their one-off animations; negative when idle. */
    private float chargeFx = -1, dragonFx = -1;
    /** Hearts rising from the Pokémon when a Charm-boosted loop lands: x, y, age. */
    private final List<float[]> heartPops = new ArrayList<>();
    private int lastCharmLoops;

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
        this.partnerCharge = payload.partnerCharge();
        this.game = new CaptureGame(payload.pokemonType(), payload.secondaryType(), payload.catchRate(), payload.level(),
                payload.energy(),
                payload.maxEnergy(), payload.lineLength(), payload.loopPower(), sizeHint,
                System.nanoTime(), this);
        this.attacks = new AttackEffects(game, AttackArt.resolve(payload.pokemonType(), arena.theme(),
                arena.plantTint(), typeColor(payload.pokemonType())));
    }

    @Override
    protected void init() {
        float availableWidth = width - 2 * (MARGIN + BEZEL_SIDE);
        float availableHeight = height - 2 * MARGIN - BEZEL_TOP - bezelBottom();
        scale = Math.max(0.5f, Math.min(availableWidth / CaptureGame.WIDTH, availableHeight / CaptureGame.HEIGHT));
        arenaX = (width - CaptureGame.WIDTH * scale) / 2;
        arenaY = MARGIN + BEZEL_TOP + (availableHeight - CaptureGame.HEIGHT * scale) / 2;
        playSound(SoundEvents.BEACON_ACTIVATE, 1.8f);
        if (!musicStarted) {
            musicStarted = true;
            CaptureMusic.start();
        }
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
        attacks.tick(dt);
        shake = Math.max(0, shake - dt);
        assistBannerTime = Math.max(0, assistBannerTime - dt);
        if (chargeFx >= 0) {
            chargeFx = chargeFx + dt > CHARGE_FX_TIME ? -1 : chargeFx + dt;
        }
        if (dragonFx >= 0) {
            dragonFx = dragonFx + dt > DRAGON_FX_TIME ? -1 : dragonFx + dt;
        }
        if (game.charmLoops() < lastCharmLoops) {
            heartPops.add(new float[]{game.x, game.y - game.radius, 0});
        }
        lastCharmLoops = game.charmLoops();
        heartPops.removeIf(heart -> (heart[2] += dt) > 1f);

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
            PacketDistributor.sendToServer(new CaptureResultPayload(payload.entityId(), outcome, game.damageTaken(),
                    chargeLoops));
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
        CaptureMusic.stop(1f);
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

    private boolean assistReady(int index) {
        return !assistUsed[index] && game.canUseAssist()
                && (!payload.assists().get(index).partner() || partnerCharge >= payload.partnerChargeFull());
    }

    private void useAssist(int index) {
        if (!assistReady(index)) {
            if (!assistUsed[index] && payload.assists().get(index).partner()) {
                playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
            }
            return;
        }
        OpenCapturePayload.Assist assist = payload.assists().get(index);
        assistUsed[index] = true;
        CaptureGame.AssistEffect effect = CaptureGame.assistFor(assist.type());
        assistColor = typeColor(assist.type());
        // Sent first: the server checks the Partner's charge against the loops drawn before this assist.
        PacketDistributor.sendToServer(new UseAssistPayload(payload.entityId(), assist.pokemonId(), game.loops()));
        if (assist.partner()) {
            partnerCharge = 0;
            chargeLoops = 0;
        }
        float matchup = game.useAssist(assist.type(), assist.partner() ? payload.assistStrength() : 1f);
        Component what = Component.translatable("screen.cobblemonranger.assist." + effect.name().toLowerCase(Locale.ROOT));
        if (matchup <= 0) {
            what = Component.translatable("screen.cobblemonranger.assist.immune");
        } else if (matchup >= 2) {
            what = Component.translatable("screen.cobblemonranger.assist.super", what);
        } else if (matchup < 1) {
            what = Component.translatable("screen.cobblemonranger.assist.weak", what);
        }
        assistBanner = Component.translatable("screen.cobblemonranger.assist", assist.name(), what);
        assistBannerTime = 1.8f;
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
        graphics.flush();
        graphics.enableScissor((int) arenaX, (int) arenaY, (int) Math.ceil(arenaX + w), (int) Math.ceil(arenaY + h));
        attacks.drawGround(graphics, arenaX, arenaY, scale);
        drawAssistGround(graphics);
        graphics.disableScissor();

        // Shadow under the Pokémon
        shapes = new Shapes(graphics);
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
        if (game.isHeld() || game.isBogged()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0);
            Shapes shapes = new Shapes(graphics);
            shapes.ring(px, py, r * (1.3f + 0.1f * pulse), 2f, 0xA0000000 | assistColor);
            graphics.flush();
        }
    }

    private void drawEffects(GuiGraphics graphics) {
        graphics.enableScissor((int) arenaX, (int) arenaY, (int) Math.ceil(arenaX + CaptureGame.WIDTH * scale),
                (int) Math.ceil(arenaY + CaptureGame.HEIGHT * scale));
        attacks.drawAir(graphics, arenaX, arenaY, scale);
        drawAssistAir(graphics);
        graphics.disableScissor();
        Shapes shapes = new Shapes(graphics);

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

        drawLine(shapes);
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
        if (game.isFrozen()) statuses.add(Component.translatable("screen.cobblemonranger.status.frozen"));
        else if (game.isTrapped()) statuses.add(Component.translatable("screen.cobblemonranger.status.trapped"));
        else if (game.isStunned()) statuses.add(Component.translatable("screen.cobblemonranger.status.stunned"));
        if (game.isBogged()) statuses.add(Component.translatable("screen.cobblemonranger.status.slowed"));
        if (game.isTimeSlowed()) statuses.add(Component.translatable("screen.cobblemonranger.status.slow_time"));
        if (game.isSpooked()) statuses.add(Component.translatable("screen.cobblemonranger.status.spooked"));
        if (game.isBlind()) statuses.add(Component.translatable("screen.cobblemonranger.status.blind"));
        if (game.isPoisoned()) statuses.add(Component.translatable("screen.cobblemonranger.status.poisoned"));
        if (game.isLineLong()) statuses.add(Component.translatable("screen.cobblemonranger.status.long_line"));
        if (game.isLineIron()) statuses.add(Component.translatable("screen.cobblemonranger.status.iron_line"));
        if (game.isCounterReady()) statuses.add(Component.translatable("screen.cobblemonranger.status.counter"));
        if (game.charmLoops() > 0) {
            statuses.add(Component.translatable("screen.cobblemonranger.status.charm", game.charmLoops()));
        }
        for (int i = 0; i < statuses.size(); i++) {
            Component status = statuses.get(i);
            int statusRight = right - 6, statusTop = (int) arenaY + 48 + i * 14;
            pill(graphics, statusRight - font.width(status) - 8, statusTop, statusRight, statusTop + 11);
            graphics.drawString(font, status, statusRight - font.width(status) - 4, statusTop + 2, GOLD, true);
        }

        // Message box near the bottom of the screen: the controls at first, then attack warnings.
        Component message = null;
        boolean playing = game.phase() == CaptureGame.Phase.PLAYING;
        if (playing && game.drawMode() != CaptureGame.DrawMode.NONE) {
            // An assist waiting to be drawn says how, even over the opening hint.
            message = Component.translatable("screen.cobblemonranger.draw."
                    + game.drawMode().name().toLowerCase(Locale.ROOT));
        } else if (game.phase() == CaptureGame.Phase.INTRO || (playing && game.phaseTime() < HINT_TIME)) {
            message = Component.translatable(payload.assists().isEmpty()
                    ? "screen.cobblemonranger.hint" : "screen.cobblemonranger.hint_assist");
        } else if (playing && game.isWindingUp()) {
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
            boolean available = assistReady(i);
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
            boolean charging = assist.partner() && !assistUsed[i] && partnerCharge < payload.partnerChargeFull();
            if (charging) {
                label += " " + partnerCharge + "/" + payload.partnerChargeFull();
            }
            label = font.plainSubstrByWidth(label, box[2] - box[0] - 17);
            graphics.drawString(font, label, box[0] + 14, box[1] + 3, available ? 0xFFFFFFFF : 0xFF8890A0, available);
            if (charging) {
                // The Partner's charge meter along the bottom of its button, filled by loops.
                int meterLeft = box[0] + 13, meterRight = box[2] - 3;
                int fill = meterLeft + Math.round((meterRight - meterLeft) * partnerCharge
                        / (float) Math.max(1, payload.partnerChargeFull()));
                graphics.fill(meterLeft, box[3] - 3, meterRight, box[3] - 1, OUTLINE);
                graphics.fill(meterLeft, box[3] - 3, fill, box[3] - 1, GOLD);
            }
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
            chargeLoops++;
            if (partnerCharge < payload.partnerChargeFull()) {
                partnerCharge++;
                if (partnerCharge == payload.partnerChargeFull() && hasPartnerAssist()) {
                    playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f);
                }
            }
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
        if (!CaptureMusic.victory()) {
            playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f);
        }
    }

    @Override
    public void onFailed() {
        playSound(SoundEvents.ITEM_BREAK, 0.8f);
        CaptureMusic.stop(1.2f);
    }

    @Override
    public void onAssist(CaptureGame.AssistEffect effect, float effectiveness) {
        if (effectiveness <= 0) {
            playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
            return;
        }
        shake = 0.2f;
        if (effect == CaptureGame.AssistEffect.CHARGE) {
            chargeFx = 0;
        } else if (effect == CaptureGame.AssistEffect.DRAGON_RUSH) {
            dragonFx = 0;
        }
        playSound(switch (effect) {
            case FIRE_RING -> SoundEvents.FIRECHARGE_USE;
            case BUBBLE -> SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE;
            case THICKET -> SoundEvents.AZALEA_LEAVES_PLACE;
            case CHARGE -> SoundEvents.BEACON_POWER_SELECT;
            case ICE_WALL -> SoundEvents.GLASS_PLACE;
            case QUICKSAND -> SoundEvents.SAND_PLACE;
            case BOULDER_WALL -> SoundEvents.STONE_PLACE;
            case WHIRLWIND -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
            case WEB -> SoundEvents.SPIDER_AMBIENT;
            case SLOW_TIME -> SoundEvents.ILLUSIONER_CAST_SPELL;
            case SPOOK -> SoundEvents.VEX_CHARGE;
            case BLIND -> SoundEvents.SQUID_SQUIRT;
            case POISON -> SoundEvents.BREWING_STAND_BREW;
            case CHARM -> SoundEvents.ALLAY_ITEM_GIVEN;
            case DRAGON_RUSH -> SoundEvents.ENDER_DRAGON_GROWL;
            case IRON_LINE -> SoundEvents.ANVIL_USE;
            case COUNTER -> SoundEvents.SHIELD_BLOCK;
            case LONG_LINE -> SoundEvents.FISHING_BOBBER_THROW;
        }, effect == CaptureGame.AssistEffect.DRAGON_RUSH ? 1.8f : 1.2f);
    }

    @Override
    public void onCounter() {
        shake = 0.25f;
        playSound(SoundEvents.SHIELD_BLOCK, 1.1f);
    }

    @Override
    public void onFrozen() {
        shake = 0.2f;
        playSound(SoundEvents.GLASS_BREAK, 0.7f);
    }

    @Override
    public void onTrapped() {
        playSound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.4f);
    }

    private boolean hasPartnerAssist() {
        for (OpenCapturePayload.Assist assist : payload.assists()) {
            if (assist.partner()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ assist effects

    /** The styler line, restyled by Steel (riveted cable), Fairy (pink and sparkling) and Fighting (orange aura). */
    private void drawLine(Shapes shapes) {
        List<float[]> line = game.line();
        if (line.isEmpty()) {
            return;
        }
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        boolean iron = game.isLineIron();
        boolean charm = game.charmLoops() > 0;
        int lineColor = charm ? 0xFF96C8 : this.lineColor;
        if (game.isCounterReady()) {
            for (int i = 1; i < line.size(); i++) {
                float[] a = line.get(i - 1), b = line.get(i);
                float flicker = 0.5f + 0.5f * (float) Math.sin(i * 0.8f - time * 18);
                float fade = 1 - game.pointAge(a);
                shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), (7 + 3 * flicker) * scale,
                        alpha((int) (0x50 + 0x64 * flicker), fade) | 0xFF8C28);
            }
        }
        for (int i = 1; i < line.size(); i++) {
            float[] a = line.get(i - 1), b = line.get(i);
            float fade = 1 - game.pointAge(a);
            if (iron) {
                shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), 4f * scale, alpha(0xFF, 0.4f + 0.6f * fade) | 0x3C4250);
            } else {
                int glowAlpha = (int) (0x90 * fade);
                shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), 5f * scale, (glowAlpha << 24) | (lineColor & 0xFFFFFF));
            }
        }
        for (int i = 1; i < line.size(); i++) {
            float[] a = line.get(i - 1), b = line.get(i);
            float fade = 1 - game.pointAge(a);
            if (iron) {
                shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), 2f * scale, alpha(0xFF, 0.4f + 0.6f * fade) | 0xA0AABE);
            } else {
                int coreAlpha = (int) (0x40 + 0xBF * fade);
                shapes.line(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), 1.5f * scale, (coreAlpha << 24) | 0xFFFFFF);
            }
        }
        if (iron) {
            List<float[]> rivets = resample(line, 9);
            for (float[] p : rivets) {
                shapes.rect(sx(p[0]) - 1.5f * scale, sy(p[1]) - 1.5f * scale, sx(p[0]) + 1.5f * scale, sy(p[1]) + 1.5f * scale,
                        0xFF282832);
                shapes.rect(sx(p[0]) - 0.8f * scale, sy(p[1]) - 0.8f * scale, sx(p[0]) + 0.8f * scale, sy(p[1]) + 0.8f * scale,
                        0xFFE6EBF0);
            }
            // Sparks where the Pokémon grinds against the cable.
            float[] nearest = null;
            float best = Float.MAX_VALUE;
            for (float[] p : line) {
                float d = (p[0] - game.x) * (p[0] - game.x) + (p[1] - game.y) * (p[1] - game.y);
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest != null && Math.sqrt(best) < game.radius + 3) {
                Random sparks = new Random(System.currentTimeMillis() / 60);
                for (int k = 0; k < 4; k++) {
                    shapes.line(sx(nearest[0]), sy(nearest[1]), sx(nearest[0] + sparks.nextFloat() * 12 - 6),
                            sy(nearest[1] - sparks.nextFloat() * 6), 1f * scale, 0xFFFFDC78);
                }
            }
        }
        if (charm) {
            List<float[]> points = resample(line, 8);
            long tick = System.currentTimeMillis() / 110;
            for (int i = 0; i < points.size(); i++) {
                if ((i + tick) % 3 == 0) {
                    float[] p = points.get(i);
                    shapes.line(sx(p[0]) - 2 * scale, sy(p[1]), sx(p[0]) + 2 * scale, sy(p[1]), 1f * scale, 0xFFFFE6F5);
                    shapes.line(sx(p[0]), sy(p[1]) - 2 * scale, sx(p[0]), sy(p[1]) + 2 * scale, 1f * scale, 0xFFFFE6F5);
                }
            }
        }
        float[] tip = line.get(line.size() - 1);
        shapes.disc(sx(tip[0]), sy(tip[1]), 3f * scale, 0xFF000000 | (iron ? 0xA0AABE : lineColor));
    }

    /** Points every {@code step} arena units along a polyline. */
    private static List<float[]> resample(List<float[]> points, float step) {
        List<float[]> out = new ArrayList<>();
        if (points.isEmpty()) {
            return out;
        }
        out.add(points.get(0));
        float carried = 0;
        for (int i = 1; i < points.size(); i++) {
            float ax = points.get(i - 1)[0], ay = points.get(i - 1)[1];
            float bx = points.get(i)[0], by = points.get(i)[1];
            float length = (float) Math.hypot(bx - ax, by - ay);
            float along = step - carried;
            while (along <= length) {
                float t = along / length;
                out.add(new float[]{ax + (bx - ax) * t, ay + (by - ay) * t});
                along += step;
            }
            carried = length - (along - step);
        }
        return out;
    }

    /** A sprite to draw after the shapes, sorted by height so nearer ones cover farther ones. */
    private record Placed(AssistArt.Sprite sprite, int variant, float x, float y, float size, float alpha,
                          boolean standing) {
    }

    private void drawPlaced(GuiGraphics graphics, List<Placed> sprites) {
        drawPlaced(graphics, sprites, true);
    }

    private void drawPlaced(GuiGraphics graphics, List<Placed> sprites, boolean sort) {
        if (sprites.isEmpty()) {
            return;
        }
        if (sort) {
            sprites.sort((a, b) -> Float.compare(a.y, b.y));
        }
        AttackArt.begin();
        for (Placed p : sprites) {
            float size = AssistArt.CELL * p.size * scale;
            if (p.standing) {
                AssistArt.standing(graphics, p.sprite, p.variant, sx(p.x), sy(p.y), size, p.alpha);
            } else {
                AssistArt.sprite(graphics, p.sprite, p.variant, sx(p.x), sy(p.y), size, p.alpha);
            }
        }
    }

    /** A flattened disc or ring lying on the ground, centred on (x, y) in arena units. */
    private void groundEllipse(GuiGraphics graphics, float x, float y, float radius, float squash, int fill, int edge,
                               float edgeWidth) {
        graphics.pose().pushPose();
        graphics.pose().translate(sx(x), sy(y), 0);
        graphics.pose().scale(1, squash, 1);
        Shapes flat = new Shapes(graphics);
        if (fill != 0) {
            flat.disc(0, 0, radius * scale, fill);
        }
        if (edge != 0) {
            flat.ring(0, 0, radius * scale, edgeWidth * scale / squash, edge);
        }
        graphics.pose().popPose();
    }

    /** What Poké Assists leave on the ground: grass, quicksand, webs, scorch, frost, the clock and drawn walls. */
    private void drawAssistGround(GuiGraphics graphics) {
        Shapes shapes = new Shapes(graphics);
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        List<Placed> sprites = new ArrayList<>();
        for (CaptureGame.Zone zone : game.zones) {
            float fade = Math.min(1, zone.life / 0.6f);
            switch (zone.kind) {
                case THICKET -> {
                    shapes.disc(sx(zone.x), sy(zone.y), zone.radius * scale, alpha(0x50, fade) | 0x2E6B2A);
                    for (int i = 0; i < 30; i++) {
                        // Tufts spread evenly over the patch, each swaying a little.
                        float angle = i * 2.39996f;
                        float dist = (float) Math.sqrt((i + 0.5f) / 30f) * zone.radius * 0.9f;
                        float tx = zone.x + (float) Math.cos(angle) * dist;
                        float ty = zone.y + (float) Math.sin(angle) * dist;
                        float sway = (float) Math.sin(time * 3 + i) * 1.2f;
                        int blade = alpha(0xE0, fade) | (i % 3 == 0 ? 0x4FA83A : 0x3C8F2E);
                        for (int b = -1; b <= 1; b++) {
                            shapes.line(sx(tx + b * 1.5f), sy(ty + 2), sx(tx + b * 3f + sway), sy(ty - 6 + Math.abs(b) * 2),
                                    1.3f * scale, blade);
                        }
                    }
                }
                case QUICKSAND -> {
                    int sand = alpha(0xC0, fade) | 0xC9A55A;
                    for (int i = 0; i < zone.xs.length; i++) {
                        int j = (i + 1) % zone.xs.length;
                        shapes.quad(sx(zone.x), sy(zone.y), sx(zone.xs[i]), sy(zone.ys[i]), sx(zone.xs[j]),
                                sy(zone.ys[j]), sx(zone.xs[j]), sy(zone.ys[j]), sand);
                    }
                    for (int i = 0; i < zone.xs.length; i++) {
                        int j = (i + 1) % zone.xs.length;
                        shapes.line(sx(zone.xs[i]), sy(zone.ys[i]), sx(zone.xs[j]), sy(zone.ys[j]), 2f * scale,
                                alpha(0xE0, fade) | 0x8A6A2E);
                    }
                    // Sand swirling down into the middle.
                    for (int k = 0; k < 3; k++) {
                        float r = (8 + k * 9) * scale;
                        shapes.arc(sx(zone.x), sy(zone.y), r, 1.5f * scale, time * (2.5f - k * 0.5f) + k * 2,
                                (float) Math.PI * 0.9f, alpha(0x90, fade) | 0x7A5A26);
                    }
                }
                case WEB -> {
                    int silk = alpha(0xD0, fade) | 0xF4F4F4;
                    float cx = sx(zone.x), cy = sy(zone.y), r = zone.radius * scale;
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8;
                        shapes.line(cx, cy, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1f * scale, silk);
                    }
                    for (int k = 1; k <= 3; k++) {
                        shapes.ring(cx, cy, r * k / 3.4f, 0.9f * scale, silk);
                    }
                }
                case FIRE_RING -> shapes.ring(sx(zone.x), sy(zone.y), zone.radius * scale, 10f * scale,
                        alpha(0x38, fade) | 0x502808);
                default -> {
                }
            }
        }
        for (CaptureGame.Wall wall : game.walls) {
            float fade = Math.min(1, wall.life / 0.6f);
            List<float[]> points = resample(wall.points, 5);
            for (int i = 0; i < points.size(); i++) {
                float[] p = points.get(i);
                if (wall.ice) {
                    shapes.disc(sx(p[0]), sy(p[1]) + 1 * scale, 4 * scale, alpha(0x6E, fade) | 0xC8F0FF);
                } else {
                    shapes.disc(sx(p[0]), sy(p[1]) + 1 * scale, 5 * scale, alpha(0x70, fade) | 0x5A4632);
                }
                // A fixed variant per point, so the wall doesn't shimmer as it grows.
                int variant = (int) (Math.abs(p[0] * 7 + p[1] * 13)) % 4;
                sprites.add(new Placed(wall.ice ? AssistArt.Sprite.SHARD : AssistArt.Sprite.CRAG, variant, p[0], p[1],
                        1, fade, true));
            }
            if (wall.ice && !points.isEmpty()) {
                float[] glint = points.get((int) (time * 25) % points.size());
                sprites.add(new Placed(AssistArt.Sprite.SPARKLE, 0, glint[0], glint[1] - 9, 1, fade, false));
            }
        }
        graphics.flush();

        float footY = game.y + game.radius * 0.7f;
        if (game.isFrozen()) {
            groundEllipse(graphics, game.x, footY, game.radius + 8, 0.35f, 0xAAE1F5FF, 0xFF96C8EB, 1);
        }
        if (game.isTimeSlowed()) {
            // A clock face under the Pokémon, its hand creeping round.
            float r = game.radius + 20;
            groundEllipse(graphics, game.x, footY, r, 0.6f, 0, 0xDCF078C8, 2);
            Shapes clock = new Shapes(graphics);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                float tx = sx(game.x + (float) Math.cos(a) * r * 0.86f), ty = sy(footY + (float) Math.sin(a) * r * 0.86f * 0.6f);
                clock.rect(tx - 1.2f * scale, ty - 1.2f * scale, tx + 1.2f * scale, ty + 1.2f * scale, 0xFFFFBEF0);
            }
            double hand = time * Math.PI * 2 / 6 - Math.PI / 2;
            clock.line(sx(game.x), sy(footY), sx(game.x + (float) Math.cos(hand) * r * 0.8f),
                    sy(footY + (float) Math.sin(hand) * r * 0.8f * 0.6f), 2 * scale, 0xFFFF96DC);
        }
        graphics.flush();
        drawPlaced(graphics, sprites);
    }

    /** Assist effects that sit above the Pokémon: flames, wind, bubbles, frost, wisps, smoke and pop-ups. */
    private void drawAssistAir(GuiGraphics graphics) {
        Shapes shapes = new Shapes(graphics);
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        float w = CaptureGame.WIDTH * scale, h = CaptureGame.HEIGHT * scale;
        List<Placed> sprites = new ArrayList<>();
        // Drawn after the sorted sprites, in this order.
        List<Placed> overlay = new ArrayList<>();
        if (game.isTimeSlowed()) {
            shapes.rect(arenaX, arenaY, arenaX + w, arenaY + h, 0x1EB060F0);
        }
        for (CaptureGame.Zone zone : game.zones) {
            float fade = Math.min(1, zone.life / 0.6f);
            if (zone.kind == CaptureGame.ZoneKind.FIRE_RING) {
                drawFireWall(shapes, zone, fade, time);
            } else if (zone.kind == CaptureGame.ZoneKind.WHIRLWIND) {
                for (int k = 0; k < 4; k++) {
                    float r = (zone.radius * (0.55f + k * 0.18f)) * scale;
                    shapes.arc(sx(zone.x), sy(zone.y), r, 2f * scale, -time * (5 + k) + k * 1.6f, (float) Math.PI * 0.7f,
                            alpha(0xA0, fade) | 0xE8F4FF);
                }
            }
        }
        for (CaptureGame.Bubble bubble : game.bubbles) {
            // Outlined like the attack sprites: dark rim, light inner rim and a curved shine.
            float bx = sx(bubble.x), by = sy(bubble.y);
            float r = bubble.radius * (1 + 0.04f * (float) Math.sin(time * 9)) * scale;
            shapes.disc(bx, by, r, 0x4696D2FF);
            shapes.ring(bx, by, r, 1.2f * scale, 0xFF2850A0);
            shapes.ring(bx, by, r - 1.2f * scale, 1f * scale, 0xE6C8F0FF);
            shapes.arc(bx, by, r - 3.5f * scale, 2f * scale, (float) Math.toRadians(190), (float) Math.toRadians(60),
                    0xFFFFFFFF);
            shapes.disc(bx + r * 0.4f, by + r * 0.4f, 1.2f * scale, 0xE6FFFFFF);
        }
        float px = game.x, py = game.y, pr = game.radius;
        if (game.isFrozen()) {
            shapes.disc(sx(px), sy(py), pr * 0.95f * scale, 0x4678BEFF);
            for (int i = 0; i < 5; i++) {
                double a = Math.PI * 2 * i / 5 + time * 2;
                sprites.add(new Placed(AssistArt.Sprite.SNOWFLAKE, 0, px + (float) Math.cos(a) * (pr + 8),
                        py - 2 + (float) Math.sin(a) * pr * 0.7f, 1, 1, false));
            }
        }
        if (game.isSpooked()) {
            for (int i = 0; i < 3; i++) {
                double a = Math.PI * 2 * i / 3 + time * 2;
                sprites.add(new Placed(AssistArt.Sprite.WISP, (int) (time * 4) + i, px + (float) Math.cos(a) * (pr + 10),
                        py - 6 + (float) Math.sin(a) * 10 + (float) Math.sin(time * 5 + i) * 2, 1, 0.95f, false));
            }
        }
        if (game.isBlind()) {
            for (int i = 0; i < 7; i++) {
                double a = Math.PI * 2 * i / 7 + time * 1.5;
                sprites.add(new Placed(AssistArt.Sprite.SMOKE, i, px + (float) Math.cos(a) * pr * 0.75f,
                        py - pr * 0.4f + (float) Math.sin(a) * pr * 0.35f, 1, 1, false));
            }
        }
        if (game.isPoisoned()) {
            for (int i = 0; i < 6; i++) {
                float u = (time * 0.6f + i / 6f) % 1;
                sprites.add(new Placed(AssistArt.Sprite.POISON_BUBBLE, i,
                        px - pr * 0.7f + i * pr * 0.28f + (float) Math.sin(u * 6 + i) * 2, py + 4 - u * pr * 2.4f,
                        0.8f, 1 - u * 0.6f, false));
            }
        }
        for (float[] heart : heartPops) {
            sprites.add(new Placed(AssistArt.Sprite.HEART, 0, heart[0], heart[1] - heart[2] * 20, 1, 1 - heart[2], false));
        }
        if (chargeFx >= 0) {
            // A battery pops up beside the Pokémon and fills while the styler recharges.
            float progress = chargeFx / CHARGE_FX_TIME;
            float bx = Math.min(CaptureGame.WIDTH - 14, px + pr + 18), by = Math.max(14, py - pr - 12);
            float pop = Math.min(1, chargeFx * 6);
            float out = progress > 0.85f ? (1 - progress) / 0.15f : 1;
            overlay.add(new Placed(AssistArt.Sprite.BATTERY, Math.min(5, (int) (progress * 1.4f * 6)), bx, by,
                    1.3f * pop, out, false));
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 2 * i / 4 + time * 6;
                overlay.add(new Placed(AssistArt.Sprite.SPARKLE, 0, bx + (float) Math.cos(a) * 15,
                        by + (float) Math.sin(a) * 9, 1, out, false));
            }
        }
        if (dragonFx >= 0) {
            drawDragonComet(shapes, px, py);
        }
        CaptureGame.DrawMode mode = game.drawMode();
        if (mode == CaptureGame.DrawMode.BOULDER || mode == CaptureGame.DrawMode.ICE) {
            // Ink left for walls, as a bar along the top of the arena.
            float barWidth = w * 0.3f;
            float left = arenaX + (w - barWidth) / 2, top = arenaY + 46;
            shapes.rect(left - 1, top - 1, left + barWidth + 1, top + 4, 0xC0141826);
            shapes.rect(left, top, left + barWidth * game.inkFraction(), top + 3,
                    mode == CaptureGame.DrawMode.ICE ? 0xFF9FDCF2 : 0xFF9C9284);
        }
        graphics.flush();
        drawPlaced(graphics, sprites);
        drawPlaced(graphics, overlay, false);
    }

    /** A wall of flame tongues round the ring, drawn back to front, with embers drifting up. */
    private void drawFireWall(Shapes shapes, CaptureGame.Zone zone, float fade, float time) {
        int count = 72;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(Math.sin(Math.PI * 2 * a / count),
                Math.sin(Math.PI * 2 * b / count)));
        int[] colors = {0xC42818, 0xF0641E, 0xFFAA32, 0xFFF096};
        for (int i : order) {
            double angle = Math.PI * 2 * i / count;
            float x = zone.x + (float) Math.cos(angle) * zone.radius, y = zone.y + (float) Math.sin(angle) * zone.radius;
            float height = 9 + 6 * (0.5f + 0.5f * (float) Math.sin(angle * 5 + time * 4))
                    + 3 * (float) Math.sin(angle * 11 - time * 6);
            for (int k = 0; k < colors.length; k++) {
                float hh = height * (1 - k * 0.24f), half = 3 - k * 0.6f;
                shapes.quad(sx(x - half), sy(y), sx(x + half), sy(y), sx(x), sy(y - hh), sx(x), sy(y - hh),
                        alpha(0xE6, fade) | colors[k]);
            }
        }
        for (int i = 0; i < 18; i++) {
            double angle = i * 2.39996;
            float rise = (time * 0.5f + i * 0.618f) % 1;
            float x = zone.x + (float) Math.cos(angle) * zone.radius + (float) Math.sin(rise * 9 + i) * 2;
            float y = zone.y + (float) Math.sin(angle) * zone.radius - 6 - rise * 26;
            shapes.rect(sx(x), sy(y), sx(x + 1), sy(y + 2), alpha(0xFF, fade * (1 - rise)) | 0xFFDC78);
        }
    }

    /** Dragon assist: a comet swoops in from the corner and bursts on the Pokémon. */
    private void drawDragonComet(Shapes shapes, float targetX, float targetY) {
        float u = dragonFx / DRAGON_FX_TIME;
        if (u < 0.6f) {
            float k = u / 0.6f;
            for (int j = 11; j >= 0; j--) {
                float kk = Math.max(0, k - j * 0.025f);
                float x = -10 + (targetX + 10) * kk, y = 10 + (targetY - 10) * kk * kk;
                float r = 6 - j * 0.45f;
                int color = mix(0xFFF0DCFF, 0xFF6E3CF0, j / 12f) & 0xFFFFFF;
                shapes.disc(sx(x), sy(y), r * scale, alpha(0xFF, 1 - j / 12f) | color);
            }
        } else {
            float k = (u - 0.6f) / 0.4f;
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                float r0 = 8 + k * 20;
                shapes.line(sx(targetX + (float) Math.cos(a) * r0), sy(targetY + (float) Math.sin(a) * r0),
                        sx(targetX + (float) Math.cos(a) * (r0 + 6)), sy(targetY + (float) Math.sin(a) * (r0 + 6)),
                        2 * scale, alpha(0xFF, 1 - k) | 0xBE96FF);
            }
        }
    }

    private static int alpha(int alpha, float fade) {
        return ((int) (alpha * Math.max(0, Math.min(1, fade)))) << 24;
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
