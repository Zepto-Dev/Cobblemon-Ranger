package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Server to client: the player's Ranger rank, points, active mission (as display text, empty when there is none) and
 * the ticks left on each Ranger Sign that is cooling down.
 */
public record RangerSyncPayload(int rank, int points, int nextRankPoints, Component mission,
                                Map<String, Integer> signCooldowns) implements CustomPacketPayload {
    public static final Type<RangerSyncPayload> TYPE = new Type<>(CobblemonRanger.id("ranger_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RangerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RangerSyncPayload::rank,
            ByteBufCodecs.VAR_INT, RangerSyncPayload::points,
            ByteBufCodecs.INT, RangerSyncPayload::nextRankPoints,
            ComponentSerialization.STREAM_CODEC, RangerSyncPayload::mission,
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            RangerSyncPayload::signCooldowns,
            RangerSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
