package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.capture.BossCapture;
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
 * can lend a Poké Assist. {@code assistStrength} scales the Partner Pokémon's assist, which can only be used once
 * {@code partnerCharge} loops (carried over from earlier captures) reach {@code partnerChargeFull}. {@code boss} is a
 * {@link BossCapture.Kind} ordinal, and {@code stages} holds the attack names of each stage of the capture (one empty
 * list for a normal capture; an empty list means the Pokémon's type attacks).
 */
public record OpenCapturePayload(int entityId, Component name, String pokemonType, String secondaryType, int catchRate,
                                 int level, boolean shiny, int energy, int maxEnergy, float lineLength, float loopPower,
                                 int lineColor, List<Assist> assists, float assistStrength, int partnerCharge,
                                 int partnerChargeFull, int boss, List<List<String>> stages) implements CustomPacketPayload {
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
        buf.writeUtf(secondaryType);
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
        buf.writeVarInt(partnerCharge);
        buf.writeVarInt(partnerChargeFull);
        buf.writeVarInt(boss);
        buf.writeVarInt(stages.size());
        for (List<String> stage : stages) {
            buf.writeVarInt(stage.size());
            for (String attack : stage) {
                buf.writeUtf(attack);
            }
        }
    }

    private static OpenCapturePayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        String type = buf.readUtf();
        String secondaryType = buf.readUtf();
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
        float assistStrength = buf.readFloat();
        int partnerCharge = buf.readVarInt();
        int partnerChargeFull = buf.readVarInt();
        int boss = buf.readVarInt();
        int stageCount = Math.min(buf.readVarInt(), 5);
        List<List<String>> stages = new ArrayList<>();
        for (int i = 0; i < stageCount; i++) {
            int attackCount = Math.min(buf.readVarInt(), 16);
            List<String> attacks = new ArrayList<>();
            for (int k = 0; k < attackCount; k++) {
                attacks.add(buf.readUtf(64));
            }
            stages.add(attacks);
        }
        if (stages.isEmpty()) {
            stages.add(List.of());
        }
        return new OpenCapturePayload(entityId, name, type, secondaryType, catchRate, level, shiny, energy, maxEnergy,
                lineLength, loopPower, lineColor, assists, assistStrength, partnerCharge, partnerChargeFull, boss,
                stages);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
