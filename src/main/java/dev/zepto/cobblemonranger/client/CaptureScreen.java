package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.client.minigame.CaptureGame;
import dev.zepto.cobblemonranger.network.CaptureResultPayload;
import dev.zepto.cobblemonranger.network.OpenCapturePayload;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The capture minigame: a top-down arena where the player drags loops around the wild Pokémon. */
public class CaptureScreen extends Screen implements CaptureGame.Listener {
    private static final float END_DELAY_CAPTURED = 1.8f;
    private static final float END_DELAY_FAILED = 1.6f;

    private final OpenCapturePayload payload;
    private final CaptureGame game;
    private final LivingEntity pokemonEntity;
    private final int lineColor;

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
        this.assistUsed = new boolean[payload.assists().size()];
        this.game = new CaptureGame(payload.pokemonType(), payload.catchRate(), payload.level(), payload.energy(),
                payload.maxEnergy(), payload.lineLength(), payload.loopPower(), sizeHint,
                System.nanoTime(), this);
    }

    @Override
    protected void init() {
        float availableWidth = width - 32;
        float availableHeight = height - (payload.assists().isEmpty() ? 76 : 94);
        scale = Math.max(0.5f, Math.min(availableWidth / CaptureGame.WIDTH, availableHeight / CaptureGame.HEIGHT));
        arenaX = (width - CaptureGame.WIDTH * scale) / 2;
        arenaY = 34 + (availableHeight - CaptureGame.HEIGHT * scale) / 2;
        playSound(SoundEvents.BEACON_ACTIVATE, 1.8f);
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

    /** Assist buttons sit in a row under the arena; returns the index under the mouse or -1. */
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
        int boxWidth = (int) Math.min(96, (w - (count - 1) * 4) / Math.max(1, count));
        int total = count * boxWidth + (count - 1) * 4;
        int x = (int) (arenaX + (w - total) / 2) + index * (boxWidth + 4);
        int y = (int) (arenaY + CaptureGame.HEIGHT * scale + 18);
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
        graphics.fill(0, 0, width, height, 0xC0080C14);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advance();
        super.render(graphics, mouseX, mouseY, partialTick);

        float shakeX = shake > 0 ? (float) Math.sin(shake * 90) * 3 * shake / 0.3f : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(shakeX, 0, 0);

        float w = CaptureGame.WIDTH * scale;
        float h = CaptureGame.HEIGHT * scale;
        drawArena(graphics, w, h);
        drawPokemon(graphics);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        drawEffects(graphics);
        graphics.flush();
        graphics.pose().popPose();
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawHud(graphics, w, h);
        drawBanner(graphics, w, h);
        graphics.pose().popPose();
    }

    private void drawArena(GuiGraphics graphics, float w, float h) {
        Shapes shapes = new Shapes(graphics);
        shapes.rect(arenaX - 3, arenaY - 3, arenaX + w + 3, arenaY + h + 3, 0xFF5BC0EB);
        shapes.rect(arenaX - 1, arenaY - 1, arenaX + w + 1, arenaY + h + 1, 0xFF0B1F2A);
        shapes.rect(arenaX, arenaY, arenaX + w, arenaY + h, 0xFF1F4D3A);
        float cell = 20 * scale;
        for (float gx = cell; gx < w; gx += cell) {
            shapes.rect(arenaX + gx, arenaY, arenaX + gx + 1, arenaY + h, 0x18FFFFFF);
        }
        for (float gy = cell; gy < h; gy += cell) {
            shapes.rect(arenaX, arenaY + gy, arenaX + w, arenaY + gy + 1, 0x18FFFFFF);
        }
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

        // Friendship gauge as a ring around the Pokémon, like the DS capture gauge.
        float px = sx(game.x), py = sy(game.y), gaugeRadius = (game.radius + 7) * scale;
        shapes.ring(px, py, gaugeRadius, 3f * scale, 0x60000000);
        if (game.gauge() > 0) {
            shapes.arc(px, py, gaugeRadius, 3f * scale, (float) (-Math.PI / 2), (float) (Math.PI * 2 * game.gauge()),
                    0xFFFFD23F);
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

    private void drawHud(GuiGraphics graphics, float w, float h) {
        // Styler energy, top left
        float barWidth = Math.min(140, w * 0.4f);
        float top = arenaY - 22;
        graphics.drawString(font, Component.translatable("screen.cobblemonranger.styler"), (int) arenaX, (int) top, 0xFF5BC0EB, true);
        float energyFraction = game.energy() / (float) Math.max(1, game.maxEnergy);
        int energyColor = energyFraction > 0.5f ? 0xFF4CD964 : energyFraction > 0.25f ? 0xFFFFCC00 : 0xFFFF3B30;
        int barX = (int) arenaX;
        int barY = (int) top + 10;
        graphics.fill(barX, barY, (int) (barX + barWidth), barY + 6, 0xFF202830);
        graphics.fill(barX, barY, (int) (barX + barWidth * energyFraction), barY + 6, energyColor);
        graphics.drawString(font, game.energy() + "/" + game.maxEnergy, (int) (barX + barWidth + 4), barY - 1, 0xFFFFFFFF, true);

        // Pokémon name and level, top right
        Component name = payload.name().copy().append(" ")
                .append(Component.translatable("screen.cobblemonranger.level", payload.level()));
        if (payload.shiny()) {
            name = Component.literal("★ ").append(name);
        }
        graphics.drawString(font, name, (int) (arenaX + w - font.width(name)), (int) top, 0xFFFFFFFF, true);

        // Friendship gauge and line length, bottom
        int bottom = (int) (arenaY + h + 6);
        Component gaugeLabel = Component.translatable("screen.cobblemonranger.friendship");
        graphics.drawString(font, gaugeLabel, (int) arenaX, bottom, 0xFFFFD23F, true);
        int gaugeX = (int) arenaX + font.width(gaugeLabel) + 6;
        int gaugeWidth = (int) (w * 0.45f);
        graphics.fill(gaugeX, bottom + 1, gaugeX + gaugeWidth, bottom + 7, 0xFF202830);
        graphics.fill(gaugeX, bottom + 1, gaugeX + (int) (gaugeWidth * game.gauge()), bottom + 7, 0xFFFFD23F);

        Component lineLabel = Component.translatable("screen.cobblemonranger.line");
        int lineBarWidth = (int) (w * 0.18f);
        int lineX = (int) (arenaX + w) - lineBarWidth;
        graphics.drawString(font, lineLabel, lineX - font.width(lineLabel) - 4, bottom, 0xFFB0C4D0, true);
        graphics.fill(lineX, bottom + 1, lineX + lineBarWidth, bottom + 7, 0xFF202830);
        graphics.fill(lineX, bottom + 1, lineX + (int) (lineBarWidth * (1 - game.lineUsage())), bottom + 7,
                0xFF000000 | lineColor);

        int hintY = bottom + 14;
        if (!payload.assists().isEmpty()) {
            drawAssists(graphics);
            hintY += 18;
        }
        List<FormattedCharSequence> hint = font.split(Component.translatable(payload.assists().isEmpty()
                ? "screen.cobblemonranger.hint" : "screen.cobblemonranger.hint_assist"), width - 16);
        for (int i = 0; i < Math.min(2, hint.size()); i++) {
            graphics.drawCenteredString(font, hint.get(i), width / 2, hintY + i * 10, 0xFF8090A0);
        }

        // Status effects from assists, under the Pokémon's name
        List<Component> statuses = new ArrayList<>();
        if (game.isStunned()) statuses.add(Component.translatable("screen.cobblemonranger.status.stunned"));
        if (game.isSlowed()) statuses.add(Component.translatable("screen.cobblemonranger.status.slowed"));
        if (game.isCalmed()) statuses.add(Component.translatable("screen.cobblemonranger.status.calm"));
        if (game.isBoosted()) statuses.add(Component.translatable("screen.cobblemonranger.status.boosted"));
        for (int i = 0; i < statuses.size(); i++) {
            Component status = statuses.get(i);
            graphics.drawString(font, status, (int) (arenaX + w - font.width(status) - 4), (int) arenaY + 4 + i * 10,
                    0xFFFFD23F, true);
        }
    }

    private void drawAssists(GuiGraphics graphics) {
        for (int i = 0; i < payload.assists().size(); i++) {
            OpenCapturePayload.Assist assist = payload.assists().get(i);
            int[] box = assistBox(i);
            boolean available = !assistUsed[i] && game.canUseAssist();
            int color = typeColor(assist.type());
            graphics.fill(box[0], box[1], box[2], box[3], available ? 0xFF000000 | darken(color) : 0xFF202830);
            graphics.renderOutline(box[0], box[1], box[2] - box[0], box[3] - box[1],
                    assist.partner() ? 0xFFFFD23F : available ? 0xFF000000 | color : 0xFF303840);
            String label = (i + 1) + " " + (assist.partner() ? "★" : "") + assist.name().getString();
            label = font.plainSubstrByWidth(label, box[2] - box[0] - 6);
            graphics.drawString(font, label, box[0] + 3, box[1] + 3, available ? 0xFFFFFFFF : 0xFF606870, false);
        }
    }

    private void drawBanner(GuiGraphics graphics, float w, float h) {
        if (assistBannerTime > 0 && game.phase() == CaptureGame.Phase.PLAYING) {
            float alpha = Math.min(1, assistBannerTime / 0.4f);
            graphics.pose().pushPose();
            graphics.pose().translate(arenaX + w / 2, arenaY + 18, 0);
            graphics.pose().scale(1.5f, 1.5f, 1);
            graphics.drawCenteredString(font, assistBanner, 0, 0,
                    ((int) (alpha * 255) << 24) | (0xFFFFFF & brighten(assistColor)));
            graphics.pose().popPose();
        }
        Component text;
        int color;
        switch (game.phase()) {
            case INTRO -> {
                text = Component.translatable("screen.cobblemonranger.capture_on");
                color = 0xFF5BC0EB;
            }
            case CAPTURED -> {
                text = Component.translatable("screen.cobblemonranger.capture_complete");
                color = 0xFFFFD23F;
            }
            case FAILED -> {
                text = Component.translatable("screen.cobblemonranger.styler_broken");
                color = 0xFFFF3B30;
            }
            default -> {
                return;
            }
        }
        float pop = Math.min(1, game.phaseTime() * 6);
        float textScale = 2.5f * (0.6f + 0.4f * pop);
        graphics.pose().pushPose();
        graphics.pose().translate(arenaX + w / 2, arenaY + h / 2, 0);
        graphics.pose().scale(textScale, textScale, 1);
        graphics.drawCenteredString(font, text, 0, -4, color);
        graphics.pose().popPose();
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

    private static int darken(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r / 2) << 16) | ((g / 2) << 8) | (b / 2);
    }

    private static int attackColor(CaptureGame.AttackStyle style) {
        return switch (style) {
            case DASH -> 0xFFFFFF;
            case BURST -> 0x4FC3F7;
            case SHOCKWAVE -> 0xE040FB;
            case ERUPTION -> 0xFF7A1A;
        };
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
