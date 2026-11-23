package dev.zepto.cobblemonranger.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zepto.cobblemonranger.hq.RangerHq;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Lets a village have at most one Ranger HQ: once one has been placed, the jigsaw placer stops offering it for the
 * village's remaining house slots.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerMixin {
    @Shadow
    @Final
    private List<?> pieces;

    @WrapOperation(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;"
                    + "getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<StructurePoolElement> cobblemonranger$oneHqPerVillage(StructureTemplatePool pool, RandomSource random,
                                                                      Operation<List<StructurePoolElement>> original) {
        return RangerHq.withoutPlacedHq(original.call(pool, random), pieces);
    }
}
