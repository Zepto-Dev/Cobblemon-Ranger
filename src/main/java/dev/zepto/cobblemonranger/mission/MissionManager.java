package dev.zepto.cobblemonranger.mission;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.field.FieldMove;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.ranger.RangerData;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.hq.RangerHq;
import dev.zepto.cobblemonranger.registry.ModItems;
import dev.zepto.cobblemonranger.registry.ModVillagers;
import dev.zepto.cobblemonranger.signs.RangerSign;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranger missions: use a styler on a Ranger (a villager working at a Ranger HQ terminal) to be offered a job, do it,
 * then report back to the same Ranger for emeralds, styler XP and Ranger points. Points raise the player's Ranger rank,
 * which unlocks Ranger Signs. Completed missions also level the Ranger up, and higher level Rangers give harder
 * missions (tiers 1 to 5) with bigger rewards; the tier is also capped by the player's rank.
 */
public final class MissionManager {
    /** Game time after which a Ranger has a new mission to offer. */
    private static final String NEXT_MISSION_TAG = CobblemonRanger.MOD_ID + ":next_mission";
    /** A short break after a mission is handed in, so the HQ isn't an instant mission vending machine. */
    private static final long VILLAGER_COOLDOWN = 20 * 60 * 2;
    private static final long OFFER_TICKS = 20 * 60;
    public static final int MAX_TIER = 5;

    private static final List<String> CAPTURE_TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice",
            "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel",
            "fairy");

    /** Reward multiplier, bonus upgrade module chance and per-item delivery effort for each tier. */
    private static final float[] REWARD_SCALE = {1f, 1.5f, 2.2f, 3f, 4f};
    private static final float[] MODULE_CHANCE = {0.05f, 0.12f, 0.2f, 0.3f, 0.45f};
    private static final int[] DELIVERY_EFFORT = {1, 2, 3, 5, 12};
    private static final int[] DELIVERY_COUNT = {16, 16, 12, 8, 3};

    /** Supplies the HQ asks for, by tier. */
    private static final List<List<Item>> DELIVERIES = List.of(
            List.of(Items.BREAD, Items.APPLE, Items.OAK_LOG, Items.TORCH, Items.WHEAT, Items.PAPER),
            List.of(Items.COAL, Items.LEATHER, Items.BOOK, Items.COOKED_BEEF, Items.STRING, Items.FEATHER),
            List.of(Items.IRON_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.HONEY_BOTTLE, Items.SLIME_BALL),
            List.of(Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.PRISMARINE_SHARD),
            List.of(Items.DIAMOND, Items.GOLDEN_APPLE, Items.ECHO_SHARD, Items.NAUTILUS_SHELL));

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    private record Offer(Mission mission, long expiresAt) {
    }

    private MissionManager() {
    }

    // ------------------------------------------------------------------ villagers

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof StylerItem) || !(event.getTarget() instanceof Villager villager)
                || villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            talk(player, villager, event.getItemStack());
        }
    }

    public static boolean isRanger(Villager villager) {
        return villager.getVillagerData().getProfession() == ModVillagers.RANGER.get();
    }

    /**
     * How hard a mission this Ranger can give this player: the Ranger's level, held back by the player's rank so a
     * rookie can't take on master missions at someone else's HQ.
     */
    public static int tierFor(Villager villager, int rank) {
        return Math.max(1, Math.min(villager.getVillagerData().getLevel(), maxTierForRank(rank)));
    }

    public static int maxTierForRank(int rank) {
        return Math.min(MAX_TIER, 1 + rank / 2);
    }

    private static void talk(ServerPlayer player, Villager villager, ItemStack styler) {
        RangerData data = RangerManager.get(player);
        Optional<Mission> current = data.mission();
        if (current.isPresent()) {
            Mission mission = current.get();
            if (!mission.giver().equals(villager.getUUID())) {
                player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_busy",
                        mission.giverName(), mission.description()).withStyle(ChatFormatting.GRAY));
                return;
            }
            if (tryTurnIn(player, villager, mission, styler)) {
                return;
            }
            villager.playSound(SoundEvents.VILLAGER_AMBIENT, 1f, 1f);
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_reminder",
                    villager.getName(), RangerManager.missionLine(mission)));
            return;
        }

        if (!isRanger(villager)) {
            villager.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            player.displayClientMessage(Component.translatable("message.cobblemonranger.mission_not_ranger",
                    villager.getName()), true);
            return;
        }
        long now = RangerManager.now(player);
        if (villager.getPersistentData().getLong(NEXT_MISSION_TAG) > now) {
            villager.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            player.displayClientMessage(Component.translatable("message.cobblemonranger.mission_none",
                    villager.getName()), true);
            return;
        }
        int tier = tierFor(villager, data.rank());
        Mission mission = generate(villager, tier, player.getRandom());
        OFFERS.put(player.getUUID(), new Offer(mission, now + OFFER_TICKS));
        villager.playSound(SoundEvents.VILLAGER_TRADE, 1f, 1f);
        MutableComponent accept = Component.translatable("message.cobblemonranger.mission_accept")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ranger mission accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.cobblemonranger.mission_accept_hover"))));
        MutableComponent decline = Component.translatable("message.cobblemonranger.mission_decline")
                .withStyle(s -> s.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ranger mission decline")));
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_offer", villager.getName(),
                mission.description()));
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_tier", tierName(tier))
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_reward", mission.emeralds(),
                mission.xp(), mission.points()).withStyle(ChatFormatting.GRAY));
        if (tier < villager.getVillagerData().getLevel()) {
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_tier_capped",
                    tierName(villager.getVillagerData().getLevel())).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.empty().append(accept).append("  ").append(decline));
    }

    public static Component tierName(int tier) {
        return Component.translatable("merchant.level." + tier);
    }

    private static Mission generate(Villager villager, int tier, RandomSource random) {
        List<Mission.Type> types = new ArrayList<>(List.of(Mission.Type.CAPTURE, Mission.Type.FIELD_MOVE,
                Mission.Type.DELIVER));
        if (tier >= 2) {
            types.add(Mission.Type.CAPTURE_TYPE);
            types.add(Mission.Type.ASSIST);
        }
        if (tier >= 3) {
            types.add(Mission.Type.BATTLE);
        }
        Mission.Type type = types.get(random.nextInt(types.size()));
        String target = "";
        int required;
        int effort;
        switch (type) {
            case CAPTURE -> {
                required = 1 + tier + random.nextInt(tier + 1);
                effort = required * 10;
            }
            case CAPTURE_TYPE -> {
                target = CAPTURE_TYPES.get(random.nextInt(CAPTURE_TYPES.size()));
                required = tier / 2 + random.nextInt(tier / 2 + 1);
                effort = required * 20;
            }
            case FIELD_MOVE -> {
                List<FieldMove> moves = tier >= 3 ? List.of(FieldMove.CUT, FieldMove.ROCK_SMASH, FieldMove.SOAK)
                        : List.of(FieldMove.CUT, FieldMove.ROCK_SMASH);
                target = moves.get(random.nextInt(moves.size())).id();
                required = 1 + tier + random.nextInt(tier + 1);
                effort = required * 10;
            }
            case ASSIST -> {
                required = tier - 1 + random.nextInt(tier);
                effort = required * 15;
            }
            case BATTLE -> {
                required = tier / 2 + random.nextInt(2);
                effort = required * 20;
            }
            default -> {
                List<Item> items = DELIVERIES.get(tier - 1);
                Item item = items.get(random.nextInt(items.size()));
                target = BuiltInRegistries.ITEM.getKey(item).toString();
                int most = DELIVERY_COUNT[tier - 1];
                required = Math.min(item.getDefaultMaxStackSize(), most / 4 + random.nextInt(most - most / 4) + 1);
                effort = 10 + required * DELIVERY_EFFORT[tier - 1];
            }
        }
        float scale = REWARD_SCALE[tier - 1];
        int emeralds = Math.max(1, Math.round(effort / 8f * scale));
        int xp = Math.round(effort * 1.2f * (0.8f + 0.2f * tier));
        int points = Math.max(5, Math.round(effort * 0.4f * scale));
        return new Mission(type, target, required, 0, villager.getUUID(), villager.getName(), emeralds, points, xp,
                tier);
    }

    private static boolean tryTurnIn(ServerPlayer player, Villager villager, Mission mission, ItemStack styler) {
        if (mission.type() == Mission.Type.DELIVER) {
            Item item = mission.deliveryItem();
            if (player.getInventory().countItem(item) < mission.required()) {
                return false;
            }
            int remaining = mission.required();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(item)) {
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    remaining -= taken;
                }
            }
        } else if (!mission.isComplete()) {
            return false;
        }
        complete(player, villager, mission, styler);
        return true;
    }

    private static void complete(ServerPlayer player, Villager villager, Mission mission, ItemStack styler) {
        int oldRank = RangerManager.get(player).rank();
        RangerManager.update(player, data -> data.withMissionCompleted(mission.points()));
        int newRank = RangerManager.get(player).rank();

        give(player, new ItemStack(Items.EMERALD, mission.emeralds()));
        RandomSource random = player.getRandom();
        int tier = Math.max(1, Math.min(MAX_TIER, mission.tier()));
        if (random.nextFloat() < MODULE_CHANCE[tier - 1]) {
            int roll = random.nextInt(5) + (tier >= 4 ? 1 : 0);
            Item module = switch (roll) {
                case 0, 1 -> ModItems.ENERGY_UPGRADE.get();
                case 2, 3 -> ModItems.LINE_UPGRADE.get();
                default -> ModItems.POWER_UPGRADE.get();
            };
            give(player, new ItemStack(module));
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_bonus",
                    module.getDescription()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        villager.playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
        villager.setUnhappyCounter(0);
        villager.getPersistentData().putLong(NEXT_MISSION_TAG, RangerManager.now(player) + VILLAGER_COOLDOWN);
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2,
                villager.getZ(), 10, 0.4, 0.4, 0.4, 0);
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_complete",
                villager.getName(), mission.emeralds(), mission.points()).withStyle(ChatFormatting.GREEN));
        StylerItem.addXp(player, styler, mission.xp());
        if (isRanger(villager)) {
            RangerHq.addMissionXp(player, villager);
        }

        if (newRank > oldRank) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1f, 1f);
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.rank_up", newRank,
                    RangerManager.rankTitle(newRank)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (RangerSign sign : RangerSign.values()) {
                if (sign.rank() > oldRank && sign.rank() <= newRank) {
                    player.sendSystemMessage(Component.translatable("message.cobblemonranger.sign_unlocked",
                            sign.displayName()).withStyle(ChatFormatting.AQUA));
                }
            }
            if (maxTierForRank(newRank) > maxTierForRank(oldRank)) {
                player.sendSystemMessage(Component.translatable("message.cobblemonranger.tier_unlocked",
                        tierName(maxTierForRank(newRank))).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------ commands

    public static boolean accept(ServerPlayer player) {
        Offer offer = OFFERS.remove(player.getUUID());
        if (offer == null || offer.expiresAt() < RangerManager.now(player)) {
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_no_offer"));
            return false;
        }
        if (RangerManager.get(player).mission().isPresent()) {
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_already"));
            return false;
        }
        Mission mission = offer.mission();
        RangerManager.update(player, data -> data.withMission(Optional.of(mission)));
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_accepted",
                mission.description(), mission.giverName()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static void decline(ServerPlayer player) {
        if (OFFERS.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_declined"));
        }
    }

    public static boolean abandon(ServerPlayer player) {
        if (RangerManager.get(player).mission().isEmpty()) {
            return false;
        }
        RangerManager.update(player, data -> data.withMission(Optional.empty()));
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_abandoned"));
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OFFERS.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ progress

    private static void progress(ServerPlayer player, Mission.Type type, String target, int amount) {
        Optional<Mission> current = RangerManager.get(player).mission();
        if (current.isEmpty() || current.get().type() != type || current.get().isComplete()
                || (!target.isEmpty() && !current.get().target().equals(target))) {
            return;
        }
        Mission updated = current.get().withProgress(amount);
        RangerManager.update(player, data -> data.withMission(Optional.of(updated)));
        if (updated.isComplete()) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.PLAYERS, 1f, 1.5f);
            player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_done",
                    updated.giverName()).withStyle(ChatFormatting.GREEN));
        } else {
            player.displayClientMessage(RangerManager.missionLine(updated), true);
        }
    }

    public static void onCapture(ServerPlayer player, Pokemon pokemon) {
        progress(player, Mission.Type.CAPTURE, "", 1);
        Optional<Mission> current = RangerManager.get(player).mission();
        if (current.isPresent() && current.get().type() == Mission.Type.CAPTURE_TYPE) {
            for (ElementalType type : pokemon.getTypes()) {
                if (type.getName().toLowerCase(Locale.ROOT).equals(current.get().target())) {
                    progress(player, Mission.Type.CAPTURE_TYPE, current.get().target(), 1);
                    break;
                }
            }
        }
    }

    public static void onFieldMove(ServerPlayer player, FieldMove move) {
        progress(player, Mission.Type.FIELD_MOVE, move.id(), 1);
    }

    public static void onAssist(ServerPlayer player) {
        progress(player, Mission.Type.ASSIST, "", 1);
    }

    public static void onBattleWithFriends(ServerPlayer player) {
        progress(player, Mission.Type.BATTLE, "", 1);
    }
}
