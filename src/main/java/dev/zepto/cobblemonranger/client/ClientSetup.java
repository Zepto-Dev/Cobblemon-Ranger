package dev.zepto.cobblemonranger.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CobblemonRanger.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping SIGN_KEY = new KeyMapping("key.cobblemonranger.sign", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.cobblemonranger");

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

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SIGN_KEY);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, CobblemonRanger.id("ranger_hud"),
                (graphics, deltaTracker) -> renderHud(graphics));
    }

    /** The Ranger Sign key opens the sign pad when the player carries a styler. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (SIGN_KEY.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            if (hasStyler(minecraft.player)) {
                minecraft.setScreen(new SignScreen());
            } else {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.cobblemonranger.sign_need_styler"), true);
            }
        }
    }

    private static boolean hasStyler(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof StylerItem) {
                return true;
            }
        }
        return player.getOffhandItem().getItem() instanceof StylerItem;
    }

    /** While a styler is held: Ranger rank in the top left, and the current mission under it. */
    private static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()
                || !(player.getMainHandItem().getItem() instanceof StylerItem
                || player.getOffhandItem().getItem() instanceof StylerItem)) {
            return;
        }
        int rank = ClientRangerData.rank();
        int next = ClientRangerData.nextRankPoints();
        Component rankLine = Component.translatable("hud.cobblemonranger.rank", rank,
                Component.translatable("rank.cobblemonranger." + rank),
                next < 0 ? Component.literal(String.valueOf(ClientRangerData.points()))
                        : Component.literal(ClientRangerData.points() + "/" + next));
        graphics.drawString(minecraft.font, rankLine, 4, 4, 0xFF5BC0EB, true);
        Component mission = ClientRangerData.mission();
        if (!mission.getString().isEmpty()) {
            graphics.drawString(minecraft.font, Component.translatable("hud.cobblemonranger.mission", mission), 4, 15,
                    0xFFFFFFFF, true);
        }
    }
}
