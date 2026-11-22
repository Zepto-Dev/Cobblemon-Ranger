package dev.zepto.cobblemonranger.capture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * How charged the Partner Pokémon's own Poké Assist is: loops drawn around wild Pokémon fill it, over as many captures
 * as it takes, and using the assist empties it. It belongs to one partner, so picking a new partner starts from zero.
 */
public record PartnerCharge(Optional<UUID> partner, int loops) {
    /** Loops around wild Pokémon it takes to charge the Partner's assist. */
    public static final int FULL = 10;
    public static final PartnerCharge EMPTY = new PartnerCharge(Optional.empty(), 0);

    public static final Codec<PartnerCharge> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(PartnerCharge::partner),
            Codec.INT.optionalFieldOf("loops", 0).forGetter(PartnerCharge::loops)
    ).apply(i, PartnerCharge::new));

    /** The charge for this partner: whatever was saved if it is the same Pokémon, otherwise none. */
    public int loopsFor(UUID partnerId) {
        return partner.map(partnerId::equals).orElse(false) ? Math.min(FULL, loops) : 0;
    }
}
