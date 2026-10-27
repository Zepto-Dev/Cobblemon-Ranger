package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player drew the given Ranger Sign. */
public record UseSignPayload(String sign) implements CustomPacketPayload {
    public static final Type<UseSignPayload> TYPE = new Type<>(CobblemonRanger.id("use_sign"));

    public static final StreamCodec<ByteBuf, UseSignPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(32).map(UseSignPayload::new, UseSignPayload::sign);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
