package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: adds a Config button for the mod on the Mods screen. */
@Mod(value = CobblemonRanger.MOD_ID, dist = Dist.CLIENT)
public final class CobblemonRangerClient {
    public CobblemonRangerClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
