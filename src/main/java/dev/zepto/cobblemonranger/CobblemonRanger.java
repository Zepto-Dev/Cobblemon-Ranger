package dev.zepto.cobblemonranger;

import dev.zepto.cobblemonranger.capture.CaptureManager;
import dev.zepto.cobblemonranger.capture.FriendManager;
import dev.zepto.cobblemonranger.capture.PartnerManager;
import dev.zepto.cobblemonranger.capture.RangerCommands;
import dev.zepto.cobblemonranger.field.SurfHandler;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.network.ModNetwork;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.registry.ModAttachments;
import dev.zepto.cobblemonranger.registry.ModCreativeTabs;
import dev.zepto.cobblemonranger.registry.ModDataComponents;
import dev.zepto.cobblemonranger.registry.ModEffects;
import dev.zepto.cobblemonranger.registry.ModItems;
import dev.zepto.cobblemonranger.registry.ModRecipes;
import dev.zepto.cobblemonranger.signs.SignManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CobblemonRanger.MOD_ID)
public final class CobblemonRanger {
    public static final String MOD_ID = "cobblemonranger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CobblemonRanger(IEventBus modBus) {
        ModDataComponents.COMPONENTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(ModNetwork::register);

        NeoForge.EVENT_BUS.register(CaptureManager.class);
        NeoForge.EVENT_BUS.register(FriendManager.class);
        NeoForge.EVENT_BUS.register(PartnerManager.class);
        NeoForge.EVENT_BUS.register(RangerManager.class);
        NeoForge.EVENT_BUS.register(MissionManager.class);
        NeoForge.EVENT_BUS.register(SignManager.class);
        NeoForge.EVENT_BUS.register(SurfHandler.class);
        NeoForge.EVENT_BUS.addListener(RangerCommands::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
