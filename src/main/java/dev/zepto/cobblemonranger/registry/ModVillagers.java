package dev.zepto.cobblemonranger.registry;

import com.google.common.collect.ImmutableSet;
import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Ranger villager profession and its job site, the Ranger HQ terminal. */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CobblemonRanger.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, CobblemonRanger.MOD_ID);

    public static final ResourceKey<PoiType> RANGER_HQ_POI =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CobblemonRanger.id("ranger_hq"));

    public static final DeferredHolder<PoiType, PoiType> RANGER_HQ = POI_TYPES.register("ranger_hq",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.RANGER_HQ.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> RANGER = PROFESSIONS.register("ranger",
            () -> new VillagerProfession("ranger", poi -> poi.is(RANGER_HQ_POI), poi -> poi.is(RANGER_HQ_POI),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    private ModVillagers() {
    }
}
