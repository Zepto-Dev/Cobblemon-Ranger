package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.capture.CaptureManager;
import dev.zepto.cobblemonranger.client.ClientPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // The client handler is only referenced inside the lambda body, so dedicated servers never load client classes.
        registrar.playToClient(OpenCapturePayload.TYPE, OpenCapturePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.openCapture(payload));
        registrar.playToServer(CaptureResultPayload.TYPE, CaptureResultPayload.STREAM_CODEC,
                (payload, context) -> CaptureManager.handleResult((ServerPlayer) context.player(), payload));
    }
}
