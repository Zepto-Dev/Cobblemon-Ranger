package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.network.OpenCapturePayload;
import dev.zepto.cobblemonranger.network.RangerSyncPayload;
import net.minecraft.client.Minecraft;

/** Client-only payload handlers, kept apart so the common network code never loads client classes. */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    public static void openCapture(OpenCapturePayload payload) {
        Minecraft.getInstance().setScreen(new CaptureScreen(payload));
    }

    public static void rangerSync(RangerSyncPayload payload) {
        ClientRangerData.update(payload);
    }
}
