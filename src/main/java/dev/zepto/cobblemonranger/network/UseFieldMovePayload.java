package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player picked a field move in the move picker. */
public record UseFieldMovePayload(String move) implements CustomPacketPayload {
    public static final Type<UseFieldMovePayload> TYPE = new Type<>(CobblemonRanger.id("use_field_move"));

    public static final StreamCodec<ByteBuf, UseFieldMovePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), UseFieldMovePayload::move,
            UseFieldMovePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
