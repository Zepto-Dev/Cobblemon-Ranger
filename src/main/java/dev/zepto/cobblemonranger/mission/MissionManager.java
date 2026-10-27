package dev.zepto.cobblemonranger.mission;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.field.FieldMove;
import dev.zepto.cobblemonranger.item.StylerItem;
import dev.zepto.cobblemonranger.ranger.RangerData;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.registry.ModItems;
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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranger missions: use a styler on a villager to be offered a job, do it, then report back to the same villager for
 * emeralds, styler XP and Ranger points. Points raise the player's Ranger rank, which unlocks Ranger Signs.
 */
public final class MissionManager {
    /** Game time after which a villager has a new mission to offer. */
    private static final String NEXT_MISSION_TAG = CobblemonRanger.MOD_ID + ":next_mission";
    private static final long VILLAGER_COOLDOWN = 24000;
    private static final long OFFER_TICKS = 20 * 60;

    private static final List<String> CAPTURE_TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice",
            "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel",
            "fairy");

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

        long now = RangerManager.now(player);
        if (villager.getPersistentData().getLong(NEXT_MISSION_TAG) > now) {
            villager.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            player.displayClientMessage(Component.translatable("message.cobblemonranger.mission_none",
                    villager.getName()), true);
            return;
        }
        Mission mission = generate(villager, data.rank(), player.getRandom());
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
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_reward", mission.emeralds(),
                mission.xp(), mission.points()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.empty().append(accept).append("  ").append(decline));
    }

    private static Mission generate(Villager villager, int rank, RandomSource random) {
        float scale = 1 + (rank - 1) * 0.15f;
        Mission.Type type = Mission.Type.values()[random.nextInt(Mission.Type.values().length)];
        String target = "";
        int required;
        int effort;
        switch (type) {
            case CAPTURE -> {
                required = 2 + random.nextInt(2 + rank / 2);
                effort = required * 10;
            }
            case CAPTURE_TYPE -> {
                target = CAPTURE_TYPES.get(random.nextInt(CAPTURE_TYPES.size()));
                required = 1 + random.nextInt(1 + rank / 3);
                effort = required * 20;
            }
            case FIELD_MOVE -> {
                FieldMove move = random.nextBoolean() ? FieldMove.CUT : FieldMove.ROCK_SMASH;
                target = move.id();
                required = 2 + random.nextInt(3);
                effort = required * 10;
            }
            case ASSIST -> {
                required = 1 + random.nextInt(2 + rank / 3);
                effort = required * 15;
            }
            case BATTLE -> {
                required = 1 + random.nextInt(2);
                effort = required * 20;
            }
            default -> {
                Item item = deliveryFor(villager, random);
                target = BuiltInRegistries.ITEM.getKey(item).toString();
                required = Math.max(1, Math.min(item.getDefaultMaxStackSize(),
                        (int) ((4 + random.nextInt(12)) * scale)));
                effort = 15 + required;
            }
        }
        int emeralds = Math.max(1, Math.round(effort / 8f * scale));
        int xp = Math.round(effort * 1.2f);
        int points = Math.max(5, Math.round(effort * 0.6f));
        return new Mission(type, target, required, 0, villager.getUUID(), villager.getName(), emeralds, points, xp);
    }

    /** What a villager wants brought to them, picked from things their profession would use. */
    private static Item deliveryFor(Villager villager, RandomSource random) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        List<Item> items;
        if (profession == VillagerProfession.FARMER) {
            items = List.of(Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.PUMPKIN);
        } else if (profession == VillagerProfession.FISHERMAN) {
            items = List.of(Items.COD, Items.SALMON, Items.STRING);
        } else if (profession == VillagerProfession.LIBRARIAN || profession == VillagerProfession.CARTOGRAPHER) {
            items = List.of(Items.PAPER, Items.BOOK, Items.INK_SAC);
        } else if (profession == VillagerProfession.ARMORER || profession == VillagerProfession.WEAPONSMITH
                || profession == VillagerProfession.TOOLSMITH) {
            items = List.of(Items.COAL, Items.IRON_INGOT, Items.FLINT);
        } else if (profession == VillagerProfession.BUTCHER || profession == VillagerProfession.LEATHERWORKER) {
            items = List.of(Items.LEATHER, Items.PORKCHOP, Items.BEEF, Items.CHICKEN);
        } else if (profession == VillagerProfession.MASON) {
            items = List.of(Items.CLAY_BALL, Items.STONE, Items.GRANITE);
        } else if (profession == VillagerProfession.SHEPHERD) {
            items = List.of(Items.WHITE_WOOL, Items.STRING);
        } else if (profession == VillagerProfession.FLETCHER) {
            items = List.of(Items.STICK, Items.FEATHER, Items.FLINT);
        } else if (profession == VillagerProfession.CLERIC) {
            items = List.of(Items.ROTTEN_FLESH, Items.BONE, Items.REDSTONE);
        } else {
            items = List.of(Items.BREAD, Items.APPLE, Items.OAK_LOG, Items.TORCH);
        }
        return items.get(random.nextInt(items.size()));
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
        if (random.nextFloat() < 0.12f + 0.03f * newRank) {
            Item module = switch (random.nextInt(5)) {
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
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2,
                villager.getZ(), 10, 0.4, 0.4, 0.4, 0);
        player.sendSystemMessage(Component.translatable("message.cobblemonranger.mission_complete",
                villager.getName(), mission.emeralds(), mission.points()).withStyle(ChatFormatting.GREEN));
        StylerItem.addXp(player, styler, mission.xp());

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
        if (player.serverLevel().getEntity(mission.giver()) instanceof Villager villager) {
            villager.getPersistentData().putLong(NEXT_MISSION_TAG, RangerManager.now(player) + VILLAGER_COOLDOWN);
        }
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
