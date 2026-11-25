package dev.zepto.cobblemonranger.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Species;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.browser.BrowserData;
import dev.zepto.cobblemonranger.browser.BrowserManager;
import dev.zepto.cobblemonranger.browser.GuidePage;
import dev.zepto.cobblemonranger.capture.BossCapture;
import dev.zepto.cobblemonranger.capture.PartnerCharge;
import dev.zepto.cobblemonranger.config.RangerConfig;
import dev.zepto.cobblemonranger.field.FieldMoveManager;
import dev.zepto.cobblemonranger.network.GuideReadPayload;
import dev.zepto.cobblemonranger.network.ReleaseFriendsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Ranger Browser, opened from the styler: a log of every species befriended, and the Ranger Guide, a tutorial
 * page per feature of the mod.
 */
public class RangerBrowserScreen extends Screen {
    private static final int ACCENT = RangerStyle.ACCENT;
    private static final int CELL = RangerStyle.CELL;
    private static final int CELL_HOVER = RangerStyle.CELL_HOVER;
    private static final int TEXT_DIM = RangerStyle.TEXT_DIM;
    private static final int GOLD = RangerStyle.GOLD;
    private static final int DETAIL_WIDTH = 124;
    /** Guide page illustrations, drawn by tools/guide_art.py. */
    private static final int ART_WIDTH = 96;
    private static final int ART_HEIGHT = 56;
    private static final int CELL_WIDTH = 82;
    private static final int CELL_HEIGHT = 20;
    private static final int TAB_HEIGHT = 16;
    private static final int PAGE_LIST_WIDTH = 124;

    private enum Tab { BROWSER, GUIDE }

    /** Remembered while the game runs, so the screen reopens where it was left. */
    private static Tab lastTab = Tab.BROWSER;
    private static GuidePage lastPage = GuidePage.STYLER;
    private static boolean lastOnlyBefriended;
    private static Species lastSelected;

    private Tab tab = lastTab;
    private GuidePage page = lastPage;
    private boolean onlyBefriended = lastOnlyBefriended;
    private Species selected = lastSelected;
    private final SpeciesModel model = new SpeciesModel();
    private final List<Species> allSpecies = new ArrayList<>();
    private List<Species> shown = new ArrayList<>();
    private int left, top, panelWidth, panelHeight;
    private double browserScroll, guideScroll;

    public RangerBrowserScreen() {
        super(Component.translatable("screen.cobblemonranger.browser"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 440);
        panelHeight = Math.min(height - 16, 240);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        allSpecies.clear();
        allSpecies.addAll(PokemonSpecies.getImplemented());
        allSpecies.sort(Comparator.comparingInt(Species::getNationalPokedexNumber));
        refilter();
        if (selected == null) {
            selected = allSpecies.stream()
                    .filter(sp -> ClientBrowserData.entryFor(sp.getResourceIdentifier().toString()) != null)
                    .findFirst().orElse(null);
        }
        if (tab == Tab.GUIDE) {
            openPage(page);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        lastTab = tab;
        lastPage = page;
        lastOnlyBefriended = onlyBefriended;
        lastSelected = selected;
        super.onClose();
    }

    private void refilter() {
        shown = onlyBefriended ? allSpecies.stream()
                .filter(s -> ClientBrowserData.entryFor(s.getResourceIdentifier().toString()) != null).toList()
                : List.copyOf(allSpecies);
        browserScroll = 0;
    }

    private void openPage(GuidePage newPage) {
        if (!ClientBrowserData.isUnlocked(newPage)) {
            return;
        }
        if (newPage != page) {
            guideScroll = 0;
        }
        page = newPage;
        if (ClientBrowserData.isNew(newPage)) {
            ClientBrowserData.markReadLocally(newPage);
            PacketDistributor.sendToServer(new GuideReadPayload(newPage.id()));
        }
    }

    // ------------------------------------------------------------------ layout

    private int contentTop() {
        return top + TAB_HEIGHT + 6;
    }

    private int tabX(Tab t) {
        return left + 6 + t.ordinal() * 78;
    }

    private int gridTop() {
        return contentTop() + 16;
    }

    private int gridBottom() {
        return top + panelHeight - 6;
    }

    private int columns() {
        return Math.max(1, (panelWidth - 18 - DETAIL_WIDTH) / CELL_WIDTH);
    }

    private int gridLeft() {
        return left + 6;
    }

    private int detailLeft() {
        return left + panelWidth - 6 - detailWidth();
    }

    /** At least DETAIL_WIDTH; also takes whatever the grid's whole columns leave over. */
    private int detailWidth() {
        return panelWidth - 18 - columns() * CELL_WIDTH;
    }

    private int maxBrowserScroll() {
        int rows = (shown.size() + columns() - 1) / columns();
        return Math.max(0, rows * CELL_HEIGHT - (gridBottom() - gridTop()));
    }

    private int textLeft() {
        return left + 6 + PAGE_LIST_WIDTH + 8;
    }

    private int textWidth() {
        return left + panelWidth - 8 - textLeft();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        RangerStyle.panel(graphics, left, top, panelWidth, panelHeight);
        drawTab(graphics, Tab.BROWSER, Component.translatable("screen.cobblemonranger.browser.tab"), mouseX, mouseY,
                false);
        drawTab(graphics, Tab.GUIDE, Component.translatable("screen.cobblemonranger.guide.tab"), mouseX, mouseY,
                ClientBrowserData.hasNewPages());
        drawReleaseButton(graphics, mouseX, mouseY);
        if (tab == Tab.BROWSER) {
            renderBrowser(graphics, mouseX, mouseY, partialTick);
        } else {
            renderGuide(graphics, mouseX, mouseY);
        }
    }

    private void drawTab(GuiGraphics graphics, Tab t, Component label, int mouseX, int mouseY, boolean badge) {
        int x = tabX(t);
        int y = top + 4;
        boolean selected = tab == t;
        boolean hover = mouseX >= x && mouseX < x + 74 && mouseY >= y && mouseY < y + TAB_HEIGHT;
        graphics.fill(x, y, x + 74, y + TAB_HEIGHT, selected ? ACCENT : hover ? CELL_HOVER : CELL);
        graphics.drawCenteredString(font, label, x + 37, y + 4, selected ? 0xFF101820 : 0xFFFFFFFF);
        if (badge) {
            graphics.fill(x + 68, y + 2, x + 72, y + 6, GOLD);
        }
    }

    private int releaseWidth() {
        return font.width(Component.translatable("screen.cobblemonranger.browser.release")) + 12;
    }

    private int releaseX() {
        return left + panelWidth - 6 - releaseWidth();
    }

    private boolean overRelease(double mouseX, double mouseY) {
        return mouseX >= releaseX() && mouseX < releaseX() + releaseWidth() && mouseY >= top + 4
                && mouseY < top + 4 + TAB_HEIGHT;
    }

    /** Sends every friend Pokémon home (this used to be sneak + right-click, which now opens the move picker). */
    private void drawReleaseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = releaseX();
        int y = top + 4;
        boolean hover = overRelease(mouseX, mouseY);
        graphics.fill(x, y, x + releaseWidth(), y + TAB_HEIGHT, hover ? RangerStyle.ACCENT : RangerStyle.ACCENT_DARK);
        graphics.drawCenteredString(font, Component.translatable("screen.cobblemonranger.browser.release"),
                x + releaseWidth() / 2, y + 4, hover ? 0xFF101820 : 0xFFFFFFFF);
    }

    private void renderBrowser(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int count = ClientBrowserData.speciesCount();
        Component header = Component.translatable("screen.cobblemonranger.browser.count", count, allSpecies.size());
        int next = nextMilestone(count);
        if (next > 0) {
            header = header.copy().append(Component.translatable("screen.cobblemonranger.browser.next", next));
        }
        graphics.drawString(font, header, left + 8, contentTop() + 2, 0xFFFFFFFF, false);
        Component filter = Component.translatable(onlyBefriended ? "screen.cobblemonranger.browser.filter_befriended"
                : "screen.cobblemonranger.browser.filter_all");
        int fw = font.width(filter) + 8;
        int fx = left + panelWidth - 8 - fw;
        boolean fHover = mouseX >= fx && mouseX < fx + fw && mouseY >= contentTop() && mouseY < contentTop() + 12;
        graphics.fill(fx, contentTop(), fx + fw, contentTop() + 12, fHover ? CELL_HOVER : CELL);
        graphics.drawString(font, filter, fx + 4, contentTop() + 2, ACCENT, false);

        int cols = columns();
        int gridLeft = gridLeft();
        graphics.enableScissor(gridLeft, gridTop(), gridLeft + cols * CELL_WIDTH, gridBottom());
        Species hovered = null;
        BrowserData.Entry hoveredEntry = null;
        for (int i = 0; i < shown.size(); i++) {
            int x = gridLeft + (i % cols) * CELL_WIDTH;
            int y = gridTop() + (i / cols) * CELL_HEIGHT - (int) browserScroll;
            if (y + CELL_HEIGHT < gridTop() || y > gridBottom()) {
                continue;
            }
            Species species = shown.get(i);
            BrowserData.Entry entry = ClientBrowserData.entryFor(species.getResourceIdentifier().toString());
            boolean hover = mouseX >= x && mouseX < x + CELL_WIDTH - 2 && mouseY >= y && mouseY < y + CELL_HEIGHT - 2
                    && mouseY >= gridTop() && mouseY < gridBottom();
            graphics.fill(x, y, x + CELL_WIDTH - 2, y + CELL_HEIGHT - 2, hover ? CELL_HOVER : CELL);
            if (species == selected) {
                outline(graphics, x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2, ACCENT);
            }
            if (entry != null) {
                graphics.fill(x, y, x + 2, y + CELL_HEIGHT - 2, 0xFF000000 | species.getPrimaryType().getHue());
            }
            graphics.drawString(font, String.format("%04d", species.getNationalPokedexNumber()), x + 4, y + 1,
                    TEXT_DIM, false);
            Component name = entry != null ? species.getTranslatedName() : Component.literal("???");
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), CELL_WIDTH - 14), x + 4, y + 9,
                    entry != null ? 0xFFFFFFFF : TEXT_DIM, false);
            if (entry != null && entry.alpha()) {
                graphics.drawString(font, "α", x + CELL_WIDTH - 10, y + 1, GOLD, false);
            }
            if (hover) {
                hovered = species;
                hoveredEntry = entry;
            }
        }
        graphics.disableScissor();
        scrollBar(graphics, gridLeft + cols * CELL_WIDTH, gridTop(), gridBottom(), browserScroll, maxBrowserScroll());
        if (shown.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.cobblemonranger.browser.empty"),
                    gridLeft + cols * CELL_WIDTH / 2, gridTop() + 30, TEXT_DIM);
        }
        renderDetail(graphics, partialTick);
        if (hovered != null && hovered != selected) {
            graphics.renderComponentTooltip(font, tooltip(hovered, hoveredEntry), mouseX, mouseY);
        }
    }

    /** The selected species: its 3D model turning slowly, then its name, types and how often it was befriended. */
    private void renderDetail(GuiGraphics graphics, float partialTick) {
        int x = detailLeft();
        int dw = detailWidth();
        int y = gridTop();
        int boxHeight = Math.min(110, gridBottom() - y - 40);
        RangerStyle.inset(graphics, x, y, dw, boxHeight);
        // Faint floor rings under the model.
        int cx = x + dw / 2;
        int floor = y + boxHeight - 14;
        graphics.fill(cx - 34, floor, cx + 34, floor + 1, CELL_HOVER);
        graphics.fill(cx - 22, floor + 3, cx + 22, floor + 4, CELL_HOVER);
        if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.cobblemonranger.browser.pick"), cx,
                    y + boxHeight / 2 - 4, TEXT_DIM);
            return;
        }
        BrowserData.Entry entry = ClientBrowserData.entryFor(selected.getResourceIdentifier().toString());
        if (entry != null) {
            model.render(graphics, selected, x + 1, y + 1, dw - 1, boxHeight - 1, partialTick);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(cx, y + boxHeight / 2f, 0);
            graphics.pose().scale(4, 4, 1);
            graphics.drawCenteredString(font, "?", 0, -4, CELL_HOVER);
            graphics.pose().popPose();
        }
        int ty = y + boxHeight + 5;
        graphics.drawString(font, "#" + String.format("%04d", selected.getNationalPokedexNumber()), x, ty, TEXT_DIM,
                false);
        if (entry != null && entry.alpha()) {
            Component alpha = Component.translatable("screen.cobblemonranger.browser.alpha_short");
            graphics.drawString(font, alpha, x + dw - font.width(alpha), ty, GOLD, false);
        }
        ty += 10;
        Component name = entry != null ? selected.getTranslatedName() : Component.literal("???");
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), dw), x, ty, 0xFFFFFFFF, false);
        ty += 11;
        if (entry == null) {
            graphics.drawString(font, Component.translatable("screen.cobblemonranger.browser.not_yet"), x, ty,
                    TEXT_DIM, false);
            return;
        }
        int tx = x;
        for (ElementalType type : selected.getTypes()) {
            Component label = type.getDisplayName();
            int w = font.width(label) + 6;
            graphics.fill(tx, ty - 1, tx + w, ty + 9, 0xFF000000 | type.getHue());
            graphics.drawString(font, label, tx + 3, ty, 0xFFFFFFFF, true);
            tx += w + 3;
        }
        ty += 13;
        graphics.drawString(font, Component.translatable("screen.cobblemonranger.browser.times", entry.count()), x,
                ty, ACCENT, false);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private List<Component> tooltip(Species species, BrowserData.Entry entry) {
        List<Component> lines = new ArrayList<>();
        if (entry == null) {
            lines.add(Component.literal("#" + species.getNationalPokedexNumber() + " ???"));
            lines.add(Component.translatable("screen.cobblemonranger.browser.not_yet").withColor(TEXT_DIM));
            return lines;
        }
        lines.add(Component.literal("#" + species.getNationalPokedexNumber() + " ").append(species.getTranslatedName()));
        MutableComponent types = Component.empty();
        for (ElementalType type : species.getTypes()) {
            if (!types.getSiblings().isEmpty()) {
                types.append(" / ");
            }
            types.append(type.getDisplayName().copy().withColor(0xFF000000 | type.getHue()));
        }
        lines.add(types);
        lines.add(Component.translatable("screen.cobblemonranger.browser.times", entry.count()).withColor(ACCENT));
        if (entry.alpha()) {
            lines.add(Component.translatable("screen.cobblemonranger.browser.alpha").withColor(GOLD));
        }
        return lines;
    }

    private static int nextMilestone(int count) {
        for (int milestone : BrowserManager.MILESTONES) {
            if (count < milestone) {
                return milestone;
            }
        }
        return -1;
    }

    private void renderGuide(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = contentTop();
        for (GuidePage p : GuidePage.values()) {
            boolean unlocked = ClientBrowserData.isUnlocked(p);
            boolean hover = unlocked && mouseX >= left + 6 && mouseX < left + 6 + PAGE_LIST_WIDTH && mouseY >= y
                    && mouseY < y + 16;
            graphics.fill(left + 6, y, left + 6 + PAGE_LIST_WIDTH, y + 15,
                    p == page ? ACCENT : hover ? CELL_HOVER : CELL);
            Component title = unlocked ? p.title() : Component.literal("???");
            int color = p == page ? 0xFF101820 : unlocked ? 0xFFFFFFFF : TEXT_DIM;
            boolean isNew = ClientBrowserData.isNew(p);
            Component badge = Component.translatable("screen.cobblemonranger.guide.new");
            int titleWidth = PAGE_LIST_WIDTH - 8 - (isNew ? font.width(badge) + 4 : 0);
            graphics.drawString(font, font.plainSubstrByWidth(title.getString(), titleWidth), left + 10,
                    y + 4, color, false);
            if (isNew) {
                graphics.drawString(font, badge, left + 6 + PAGE_LIST_WIDTH - 4 - font.width(badge), y + 4, GOLD,
                        false);
            }
            y += 17;
        }

        int textTop = contentTop();
        int textBottom = top + panelHeight - 6;
        List<FormattedCharSequence> lines = pageLines();
        int maxScroll = Math.max(0, lines.size() * 10 + 14 + ART_HEIGHT + 6 - (textBottom - textTop));
        guideScroll = Math.max(0, Math.min(guideScroll, maxScroll));
        graphics.enableScissor(textLeft(), textTop, textLeft() + textWidth(), textBottom);
        int ty = textTop - (int) guideScroll;
        graphics.drawString(font, page.title(), textLeft(), ty, ACCENT, false);
        ty += 14;
        ResourceLocation art = ResourceLocation.fromNamespaceAndPath(CobblemonRanger.MOD_ID,
                "textures/gui/guide/" + page.id() + ".png");
        graphics.blit(art, textLeft(), ty, 0, 0, ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);
        ty += ART_HEIGHT + 6;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, textLeft(), ty, 0xFFE0E6EB, false);
            ty += 10;
        }
        graphics.disableScissor();
        scrollBar(graphics, left + panelWidth - 5, textTop, textBottom, guideScroll, maxScroll);
    }

    private static void scrollBar(GuiGraphics graphics, int x, int top, int bottom, double scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int track = bottom - top;
        int thumb = Math.max(12, track * track / (track + maxScroll));
        int y = top + (int) ((track - thumb) * (scroll / maxScroll));
        graphics.fill(x, top, x + 2, bottom, CELL);
        graphics.fill(x, y, x + 2, y + thumb, ACCENT);
    }

    private List<FormattedCharSequence> pageLines() {
        Component body = Component.translatable("guide.cobblemonranger." + page.id() + ".body", pageArgs(page));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : body.getString().split("\n")) {
            if (paragraph.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(font.split(Component.literal(paragraph), textWidth()));
            }
        }
        return lines;
    }

    /** Numbers from the server's settings, so the Guide always matches the world it's read in. */
    private static Object[] pageArgs(GuidePage page) {
        return switch (page) {
            case STYLER -> new Object[]{RangerConfig.current(RangerConfig.STYLER_RECHARGE_SECONDS),
                    RangerConfig.current(RangerConfig.HQ_CHARGE_COOLDOWN_MINUTES)};
            case CAPTURING -> new Object[]{RangerConfig.current(RangerConfig.CAPTURE_LEVEL_CAP_BASE),
                    RangerConfig.current(RangerConfig.CAPTURE_LEVEL_CAP_PER_LEVEL)};
            case BROWSER -> new Object[]{BrowserManager.NEW_SPECIES_XP, BrowserManager.MILESTONES[0],
                    BrowserManager.MILESTONES[BrowserManager.MILESTONES.length - 1]};
            case ASSISTS -> new Object[]{PartnerCharge.FULL};
            case BOSSES -> new Object[]{BossCapture.ALPHA_STAGES, BossCapture.LEGENDARY_STAGES};
            case PARTNER -> new Object[]{FieldMoveManager.PARTNER_ENERGY_COST,
                    RangerConfig.current(RangerConfig.PARTNER_FIELD_MOVE_COOLDOWN_SECONDS)};
            case FIELD_MOVES -> new Object[]{FieldMoveManager.TELEKINESIS_RANGE};
            case FIELD_MOVE_PICKER -> new Object[]{FieldMoveManager.MAX_CHARMED};
            case MISSIONS -> new Object[]{RangerConfig.current(RangerConfig.MISSION_COOLDOWN_MINUTES)};
            case SIGNS -> new Object[]{RangerConfig.current(RangerConfig.WORLD_SIGN_PERSONAL_COOLDOWN_MINUTES),
                    RangerConfig.current(RangerConfig.WORLD_SIGN_SHARED_COOLDOWN_MINUTES)};
            default -> new Object[0];
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overRelease(mouseX, mouseY)) {
                PacketDistributor.sendToServer(ReleaseFriendsPayload.INSTANCE);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
            for (Tab t : Tab.values()) {
                int x = tabX(t);
                if (mouseX >= x && mouseX < x + 74 && mouseY >= top + 4 && mouseY < top + 4 + TAB_HEIGHT) {
                    tab = t;
                    if (t == Tab.GUIDE) {
                        openPage(page);
                    }
                    return true;
                }
            }
            if (tab == Tab.BROWSER && mouseY >= contentTop() && mouseY < contentTop() + 12
                    && mouseX >= left + panelWidth / 2) {
                onlyBefriended = !onlyBefriended;
                refilter();
                return true;
            }
            if (tab == Tab.BROWSER && mouseY >= gridTop() && mouseY < gridBottom() && mouseX >= gridLeft()
                    && mouseX < gridLeft() + columns() * CELL_WIDTH) {
                int col = (int) ((mouseX - gridLeft()) / CELL_WIDTH);
                int row = (int) ((mouseY - gridTop() + browserScroll) / CELL_HEIGHT);
                int index = row * columns() + col;
                if (index >= 0 && index < shown.size()) {
                    selected = shown.get(index);
                    return true;
                }
            }
            if (tab == Tab.GUIDE && mouseX >= left + 6 && mouseX < left + 6 + PAGE_LIST_WIDTH) {
                int index = (int) ((mouseY - contentTop()) / 17);
                if (mouseY >= contentTop() && index >= 0 && index < GuidePage.values().length) {
                    openPage(GuidePage.values()[index]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.BROWSER) {
            if (mouseX >= detailLeft()) {
                return true;
            }
            browserScroll = Math.max(0, Math.min(maxBrowserScroll(), browserScroll - scrollY * CELL_HEIGHT));
        } else {
            guideScroll = Math.max(0, guideScroll - scrollY * 20);
        }
        return true;
    }
}
