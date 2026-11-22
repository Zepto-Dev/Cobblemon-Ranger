package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client to server: the player called a Poké Assist from the given party Pokémon during a capture. {@code loops} are
 * the loops drawn around the Pokémon so far in this capture, which also charge the Partner's assist.
 */
public record UseAssistPayload(int entityId, UUID pokemonId, int loops) implements CustomPacketPayload {
    public static final Type<UseAssistPayload> TYPE = new Type<>(CobblemonRanger.id("use_assist"));

    public static final StreamCodec<ByteBuf, UseAssistPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UseAssistPayload::entityId,
            UUIDUtil.STREAM_CODEC, UseAssistPayload::pokemonId,
            ByteBufCodecs.VAR_INT, UseAssistPayload::loops,
            UseAssistPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
