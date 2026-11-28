package dev.zepto.cobblemonranger.browser;

import net.minecraft.network.chat.Component;

/**
 * Pages of the Ranger Guide, the tutorial tab of the Ranger Browser, in reading order. Pages that don't start
 * unlocked appear the first time the player meets their feature, with a "new" badge until they are read.
 */
public enum GuidePage {
    STYLER("styler", true),
    CAPTURING("capturing", true),
    BROWSER("browser", true),
    FRIENDS("friends", false),
    ASSISTS("assists", false),
    BOSSES("bosses", false),
    PARTNER("partner", false),
    FIELD_MOVES("field_moves", false),
    FIELD_MOVE_PICKER("field_move_picker", false),
    RESCUES("rescues", false),
    MISSIONS("missions", false),
    UPGRADES("upgrades", false),
    SIGNS("signs", false);

    private final String id;
    private final boolean startsUnlocked;

    GuidePage(String id, boolean startsUnlocked) {
        this.id = id;
        this.startsUnlocked = startsUnlocked;
    }

    public String id() {
        return id;
    }

    public boolean startsUnlocked() {
        return startsUnlocked;
    }

    public Component title() {
        return Component.translatable("guide.cobblemonranger." + id);
    }

    public static GuidePage byId(String id) {
        for (GuidePage page : values()) {
            if (page.id.equals(id)) {
                return page;
            }
        }
        return null;
    }
}
