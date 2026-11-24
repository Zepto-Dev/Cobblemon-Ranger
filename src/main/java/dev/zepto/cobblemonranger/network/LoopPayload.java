package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player closed a loop around the Pokémon. The server counts these to check that a reported
 * capture had enough loops behind it.
 */
public record LoopPayload(int entityId) implements CustomPacketPayload {
    public static final Type<LoopPayload> TYPE = new Type<>(CobblemonRanger.id("capture_loop"));

    public static final StreamCodec<ByteBuf, LoopPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LoopPayload::entityId,
            LoopPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
