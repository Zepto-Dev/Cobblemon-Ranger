package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the "Release all friends" button in the Ranger Browser. */
public record ReleaseFriendsPayload() implements CustomPacketPayload {
    public static final Type<ReleaseFriendsPayload> TYPE = new Type<>(CobblemonRanger.id("release_friends"));
    public static final ReleaseFriendsPayload INSTANCE = new ReleaseFriendsPayload();

    public static final StreamCodec<ByteBuf, ReleaseFriendsPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
