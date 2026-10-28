package dev.zepto.cobblemonranger.registry;

import com.mojang.serialization.Codec;
import dev.zepto.cobblemonranger.CobblemonRanger;
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

    private ModAttachments() {
    }
}
