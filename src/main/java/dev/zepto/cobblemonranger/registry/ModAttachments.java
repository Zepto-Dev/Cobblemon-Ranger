package dev.zepto.cobblemonranger.registry;

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

    private ModAttachments() {
    }
}
