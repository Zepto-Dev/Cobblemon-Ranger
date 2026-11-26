package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server to client: opens the field move picker with the moves somebody in the party can do right now. */
public record OpenFieldMovePickerPayload(List<Entry> moves) implements CustomPacketPayload {
    public static final Type<OpenFieldMovePickerPayload> TYPE = new Type<>(CobblemonRanger.id("open_field_moves"));

    /** One move, and whether the Partner (rather than a friend, who then leaves) would do it. */
    public record Entry(String id, boolean partner) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Entry::id,
                ByteBufCodecs.BOOL, Entry::partner,
                Entry::new);
    }

    public static final StreamCodec<ByteBuf, OpenFieldMovePickerPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(32)), OpenFieldMovePickerPayload::moves,
            OpenFieldMovePickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
