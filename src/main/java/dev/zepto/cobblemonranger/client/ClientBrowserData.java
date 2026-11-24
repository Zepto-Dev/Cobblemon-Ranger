package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.browser.BrowserData;
import dev.zepto.cobblemonranger.browser.GuidePage;
import dev.zepto.cobblemonranger.network.BrowserSyncPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The local player's Ranger Browser, as last sent by the server. */
public final class ClientBrowserData {
    private static final Map<String, BrowserData.Entry> SPECIES = new HashMap<>();
    private static final Set<String> UNLOCKED = new HashSet<>();
    private static final Set<String> READ = new HashSet<>();

    private ClientBrowserData() {
    }

    static void update(BrowserSyncPayload payload) {
        if (payload.full()) {
            SPECIES.clear();
        }
        SPECIES.putAll(payload.species());
        UNLOCKED.clear();
        UNLOCKED.addAll(payload.unlocked());
        READ.clear();
        READ.addAll(payload.read());
    }

    /** The entries for a species: its own key and those of its forms. */
    public static BrowserData.Entry entryFor(String speciesId) {
        int count = 0;
        boolean alpha = false;
        for (Map.Entry<String, BrowserData.Entry> e : SPECIES.entrySet()) {
            String key = e.getKey();
            if (key.equals(speciesId) || key.startsWith(speciesId + "/")) {
                count += e.getValue().count();
                alpha |= e.getValue().alpha();
            }
        }
        return count == 0 ? null : new BrowserData.Entry(count, alpha);
    }

    public static int speciesCount() {
        return new BrowserData(SPECIES, Set.of(), Set.of(), false).speciesCount();
    }

    public static boolean isUnlocked(GuidePage page) {
        return page.startsUnlocked() || UNLOCKED.contains(page.id());
    }

    public static boolean isNew(GuidePage page) {
        return isUnlocked(page) && !READ.contains(page.id());
    }

    static void markReadLocally(GuidePage page) {
        READ.add(page.id());
    }

    public static boolean hasNewPages() {
        for (GuidePage page : GuidePage.values()) {
            if (isNew(page)) {
                return true;
            }
        }
        return false;
    }
}
