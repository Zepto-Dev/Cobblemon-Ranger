package dev.zepto.cobblemonranger.registry;

import com.mojang.serialization.Codec;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.browser.BrowserData;
import dev.zepto.cobblemonranger.capture.PartnerCharge;
import dev.zepto.cobblemonranger.ranger.RangerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CobblemonRanger.MOD_ID);

    public static final Supplier<AttachmentType<RangerData>> RANGER = ATTACHMENTS.register("ranger",
            () -> AttachmentType.builder(() -> RangerData.EMPTY).serialize(RangerData.CODEC).copyOnDeath().build());

    /** Game time after which Rangers offer this player a new mission. */
    public static final Supplier<AttachmentType<Long>> NEXT_MISSION = ATTACHMENTS.register("next_mission",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    /** Charge of the Partner Pokémon's own Poké Assist. */
    public static final Supplier<AttachmentType<PartnerCharge>> PARTNER_CHARGE = ATTACHMENTS.register("partner_charge",
            () -> AttachmentType.builder(() -> PartnerCharge.EMPTY).serialize(PartnerCharge.CODEC).copyOnDeath().build());
    /** Game time after which a Ranger HQ Terminal will charge this player's styler again. */
    public static final Supplier<AttachmentType<Long>> NEXT_HQ_CHARGE = ATTACHMENTS.register("next_hq_charge",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    /** Species befriended for the Ranger Browser, and Guide pages unlocked and read. */
    public static final Supplier<AttachmentType<BrowserData>> BROWSER = ATTACHMENTS.register("browser",
            () -> AttachmentType.builder(() -> BrowserData.EMPTY).serialize(BrowserData.CODEC).copyOnDeath().build());

    /** Overworld game time of this player's next rescue event (0 = not scheduled yet). */
    public static final Supplier<AttachmentType<Long>> NEXT_RESCUE = ATTACHMENTS.register("next_rescue",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    private ModAttachments() {
    }
}
