package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player opened a Guide page, so it loses its "new" badge. */
public record GuideReadPayload(String page) implements CustomPacketPayload {
    public static final Type<GuideReadPayload> TYPE = new Type<>(CobblemonRanger.id("guide_read"));

    public static final StreamCodec<ByteBuf, GuideReadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), GuideReadPayload::page,
            GuideReadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
