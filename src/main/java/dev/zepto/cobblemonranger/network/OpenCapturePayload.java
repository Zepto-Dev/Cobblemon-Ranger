package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: start the capture minigame against the given wild Pokémon. {@code assists} are the Pokémon that
 * can lend a Poké Assist, and {@code assistStrength} scales their effects (higher with a Partner Pokémon around).
 */
public record OpenCapturePayload(int entityId, Component name, String pokemonType, int catchRate, int level, boolean shiny,
                                 int energy, int maxEnergy, float lineLength, float loopPower, int lineColor,
                                 List<Assist> assists, float assistStrength) implements CustomPacketPayload {
    public static final Type<OpenCapturePayload> TYPE = new Type<>(CobblemonRanger.id("open_capture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCapturePayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenCapturePayload::write, OpenCapturePayload::read);

    /** A friend Pokémon, or the Partner Pokémon, that can help during the capture. */
    public record Assist(UUID pokemonId, Component name, String type, boolean partner) {
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        ComponentSerialization.STREAM_CODEC.encode(buf, name);
        buf.writeUtf(pokemonType);
        buf.writeVarInt(catchRate);
        buf.writeVarInt(level);
        buf.writeBoolean(shiny);
        buf.writeVarInt(energy);
        buf.writeVarInt(maxEnergy);
        buf.writeFloat(lineLength);
        buf.writeFloat(loopPower);
        buf.writeInt(lineColor);
        buf.writeVarInt(assists.size());
        for (Assist assist : assists) {
            UUIDUtil.STREAM_CODEC.encode(buf, assist.pokemonId());
            ComponentSerialization.STREAM_CODEC.encode(buf, assist.name());
            buf.writeUtf(assist.type());
            buf.writeBoolean(assist.partner());
        }
        buf.writeFloat(assistStrength);
    }

    private static OpenCapturePayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        String type = buf.readUtf();
        int catchRate = buf.readVarInt();
        int level = buf.readVarInt();
        boolean shiny = buf.readBoolean();
        int energy = buf.readVarInt();
        int maxEnergy = buf.readVarInt();
        float lineLength = buf.readFloat();
        float loopPower = buf.readFloat();
        int lineColor = buf.readInt();
        int count = Math.min(buf.readVarInt(), 6);
        List<Assist> assists = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            assists.add(new Assist(UUIDUtil.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readUtf(), buf.readBoolean()));
        }
        return new OpenCapturePayload(entityId, name, type, catchRate, level, shiny, energy, maxEnergy, lineLength,
                loopPower, lineColor, assists, buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
