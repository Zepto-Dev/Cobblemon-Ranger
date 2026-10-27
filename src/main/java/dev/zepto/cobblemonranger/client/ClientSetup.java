package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = CobblemonRanger.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /** Styler models have a tinted shell (layer 0) and an untinted screen and buttons (layer 1). */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        ModItems.STYLERS.values().forEach(item -> event.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof StylerItem styler)) {
                return -1;
            }
            return 0xFF000000 | styler.getColor().getTextureDiffuseColor();
        }, item.get()));
    }
}
