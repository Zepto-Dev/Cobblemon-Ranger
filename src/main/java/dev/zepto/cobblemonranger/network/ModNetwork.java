package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.capture.CaptureManager;
import dev.zepto.cobblemonranger.client.ClientPayloads;
import dev.zepto.cobblemonranger.signs.SignManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        // Client handlers are only referenced inside lambda bodies, so dedicated servers never load client classes.
        registrar.playToClient(OpenCapturePayload.TYPE, OpenCapturePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.openCapture(payload));
        registrar.playToClient(RangerSyncPayload.TYPE, RangerSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.rangerSync(payload));
        registrar.playToServer(CaptureResultPayload.TYPE, CaptureResultPayload.STREAM_CODEC,
                (payload, context) -> CaptureManager.handleResult((ServerPlayer) context.player(), payload));
        registrar.playToServer(UseAssistPayload.TYPE, UseAssistPayload.STREAM_CODEC,
                (payload, context) -> CaptureManager.handleAssist((ServerPlayer) context.player(), payload));
        registrar.playToServer(UseSignPayload.TYPE, UseSignPayload.STREAM_CODEC,
                (payload, context) -> SignManager.use((ServerPlayer) context.player(), payload.sign()));
    }
}
