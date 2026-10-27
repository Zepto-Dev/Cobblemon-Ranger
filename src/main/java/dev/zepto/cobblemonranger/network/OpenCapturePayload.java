package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.item.StylerStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: start the capture minigame against the given wild Pokémon. */
public record OpenCapturePayload(int entityId, Component name, String type, int catchRate, int level, boolean shiny,
                                 int energy, int maxEnergy, StylerStats stats, int lineColor) implements CustomPacketPayload {
    public static final Type<OpenCapturePayload> TYPE = new Type<>(CobblemonRanger.id("open_capture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCapturePayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenCapturePayload::write, OpenCapturePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        ComponentSerialization.STREAM_CODEC.encode(buf, name);
        buf.writeUtf(type);
        buf.writeVarInt(catchRate);
        buf.writeVarInt(level);
        buf.writeBoolean(shiny);
        buf.writeVarInt(energy);
        buf.writeVarInt(maxEnergy);
        StylerStats.STREAM_CODEC.encode(buf, stats);
        buf.writeInt(lineColor);
    }

    private static OpenCapturePayload read(RegistryFriendlyByteBuf buf) {
        return new OpenCapturePayload(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                StylerStats.STREAM_CODEC.decode(buf), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
