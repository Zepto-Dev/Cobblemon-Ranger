package dev.zepto.cobblemonranger.network;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.browser.BrowserData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server to client: the player's Ranger Browser. {@code full} replaces everything the client had (sent on login);
 * otherwise {@code species} holds only the entries that changed.
 */
public record BrowserSyncPayload(boolean full, Map<String, BrowserData.Entry> species, List<String> unlocked,
                                 List<String> read) implements CustomPacketPayload {
    public static final Type<BrowserSyncPayload> TYPE = new Type<>(CobblemonRanger.id("browser_sync"));

    public static final StreamCodec<ByteBuf, BrowserSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BrowserSyncPayload::full,
            ByteBufCodecs.<ByteBuf, String, BrowserData.Entry, Map<String, BrowserData.Entry>>map(HashMap::new,
                    ByteBufCodecs.STRING_UTF8, BrowserData.Entry.STREAM_CODEC),
            BrowserSyncPayload::species,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BrowserSyncPayload::unlocked,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BrowserSyncPayload::read,
            BrowserSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
