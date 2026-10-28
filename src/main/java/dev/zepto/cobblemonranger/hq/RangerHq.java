package dev.zepto.cobblemonranger.hq;

import com.mojang.datafixers.util.Pair;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.registry.ModItems;
import dev.zepto.cobblemonranger.registry.ModVillagers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ranger villager: levels up only by having missions completed for it (its trades give no XP), sells styler gear,
 * and has its own HQ building in villages.
 */
public final class RangerHq {
    /** Villager XP a completed mission gives, by the Ranger's current level: 2, 4, 4 and 4 missions per level. */
    private static final int[] MISSION_XP = {5, 15, 20, 25};
    private static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    /** Share of a village's house slots that go to the HQ. */
    private static final double HQ_SHARE = 0.06;

    private RangerHq() {
    }

    /** Adds mission XP to a Ranger and levels it up when it has enough, like a trade would for other villagers. */
    public static void addMissionXp(ServerPlayer player, Villager villager) {
        VillagerData data = villager.getVillagerData();
        int level = data.getLevel();
        if (!VillagerData.canLevelUp(level)) {
            return;
        }
        villager.setVillagerXp(villager.getVillagerXp() + MISSION_XP[Math.min(level, MISSION_XP.length) - 1]);
        if (villager.getVillagerXp() < VillagerData.getMaxXpPerLevel(level)) {
            return;
        }
        villager.increaseMerchantCareer();
        int newLevel = villager.getVillagerData().getLevel();
        villager.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 1f);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1,
                villager.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.hq_level_up", villager.getName(),
                Component.translatable("merchant.level." + newLevel)).withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.RANGER.get()) {
            return;
        }
        // No trade XP: Rangers only level up from missions.
        add(event, 1, new BasicItemListing(1, new ItemStack(Items.REDSTONE, 8), 16, 0));
        add(event, 1, new BasicItemListing(6, new ItemStack(ModItems.STYLERS.get(DyeColor.RED).get()), 3, 0));
        add(event, 2, new BasicItemListing(16, new ItemStack(ModItems.ENERGY_UPGRADE.get()), 2, 0));
        add(event, 3, new BasicItemListing(16, new ItemStack(ModItems.LINE_UPGRADE.get()), 2, 0));
        add(event, 4, new BasicItemListing(28, new ItemStack(ModItems.POWER_UPGRADE.get()), 2, 0));
        add(event, 5, new BasicItemListing(new ItemStack(Items.EMERALD, 20), new ItemStack(Items.DIAMOND),
                new ItemStack(ModItems.POWER_UPGRADE.get()), 3, 0, 0.05f));
    }

    private static void add(VillagerTradesEvent event, int level, VillagerTrades.ItemListing listing) {
        event.getTrades().computeIfAbsent(level, l -> new ArrayList<>()).add(listing);
    }

    /** Adds the HQ house to the vanilla village house pools. */
    @SubscribeEvent
    public static void addToVillages(ServerAboutToStartEvent event) {
        Registry<StructureTemplatePool> pools = event.getServer().registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL);
        for (String village : VILLAGES) {
            StructureTemplatePool pool = pools.get(ResourceLocation.withDefaultNamespace("village/" + village
                    + "/houses"));
            if (pool == null) {
                continue;
            }
            StructurePoolElement element = StructurePoolElement.single(CobblemonRanger.MOD_ID + ":village/" + village
                    + "_ranger_hq").apply(StructureTemplatePool.Projection.RIGID);
            int weight = Math.max(1, (int) Math.round(pool.templates.size() * HQ_SHARE));
            for (int i = 0; i < weight; i++) {
                pool.templates.add(element);
            }
            List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(pool.rawTemplates);
            raw.add(Pair.of(element, weight));
            pool.rawTemplates = raw;
        }
    }
}
