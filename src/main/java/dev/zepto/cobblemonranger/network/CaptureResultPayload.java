package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the capture minigame ended. {@code damage} is the styler energy lost to hits. */
public record CaptureResultPayload(int entityId, Outcome outcome, int damage) implements CustomPacketPayload {
    public static final Type<CaptureResultPayload> TYPE = new Type<>(CobblemonRanger.id("capture_result"));

    public static final StreamCodec<ByteBuf, CaptureResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CaptureResultPayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Outcome.values()[i], Enum::ordinal), CaptureResultPayload::outcome,
            ByteBufCodecs.VAR_INT, CaptureResultPayload::damage,
            CaptureResultPayload::new);

    public enum Outcome {
        CAPTURED,
        STYLER_EMPTY,
        GAVE_UP
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
