package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.zepto.cobblemonranger.mission.MissionManager;
import dev.zepto.cobblemonranger.ranger.RangerData;
import dev.zepto.cobblemonranger.ranger.RangerManager;
import dev.zepto.cobblemonranger.signs.RangerSign;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /ranger friends} lists friend Pokémon and {@code /ranger release} sends them all home; {@code /ranger partner},
 * {@code /ranger mission} and {@code /ranger rank} show the Partner Pokémon, the current mission and Ranger rank.
 */
public final class RangerCommands {
    private RangerCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ranger")
                .then(Commands.literal("friends").executes(RangerCommands::listFriends))
                .then(Commands.literal("partners").executes(RangerCommands::listFriends))
                .then(Commands.literal("release").executes(RangerCommands::releaseFriends))
                .then(Commands.literal("partner").executes(RangerCommands::showPartner)
                        .then(Commands.literal("clear").executes(RangerCommands::clearPartner)))
                .then(Commands.literal("rank").executes(RangerCommands::showRank))
                .then(Commands.literal("mission").executes(RangerCommands::showMission)
                        .then(Commands.literal("accept").executes(c -> MissionManager.accept(player(c)) ? 1 : 0))
                        .then(Commands.literal("decline").executes(c -> {
                            MissionManager.decline(player(c));
                            return 1;
                        }))
                        .then(Commands.literal("abandon").executes(RangerCommands::abandonMission))));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static void reply(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }

    private static int listFriends(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<Pokemon> friends = FriendManager.getFriends(player(context));
        if (friends.isEmpty()) {
            reply(context, Component.translatable("message.cobblemonranger.no_friends"));
            return 0;
        }
        for (Pokemon pokemon : friends) {
            reply(context, Component.translatable("message.cobblemonranger.friend_entry",
                    pokemon.getSpecies().getTranslatedName(), pokemon.getLevel()));
        }
        return friends.size();
    }

    private static int releaseFriends(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int released = FriendManager.releaseAll(player(context));
        if (released == 0) {
            reply(context, Component.translatable("message.cobblemonranger.no_friends"));
        }
        return released;
    }

    private static int showPartner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = player(context);
        Pokemon partner = PartnerManager.getPartner(player);
        if (partner != null) {
            reply(context, Component.translatable("message.cobblemonranger.partner_info",
                    partner.getSpecies().getTranslatedName(), partner.getLevel(), partner.getFriendship()));
            return 1;
        }
        reply(context, Component.translatable(RangerManager.get(player).partner().isPresent()
                ? "message.cobblemonranger.partner_missing" : "message.cobblemonranger.no_partner"));
        return 0;
    }

    private static int clearPartner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PartnerManager.clearPartner(player(context));
        reply(context, Component.translatable("message.cobblemonranger.partner_cleared"));
        return 1;
    }

    private static int showRank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RangerData data = RangerManager.get(player(context));
        int rank = data.rank();
        int next = RangerData.pointsForNextRank(rank);
        reply(context, Component.translatable("message.cobblemonranger.rank_info", rank, RangerManager.rankTitle(rank),
                data.points(), next < 0 ? "-" : String.valueOf(next), data.missionsDone()));
        for (RangerSign sign : RangerSign.values()) {
            reply(context, Component.translatable(rank >= sign.rank()
                            ? "message.cobblemonranger.sign_entry" : "message.cobblemonranger.sign_entry_locked",
                    sign.displayName(), sign.description(), sign.rank()));
        }
        return rank;
    }

    private static int showMission(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RangerData data = RangerManager.get(player(context));
        if (data.mission().isEmpty()) {
            reply(context, Component.translatable("message.cobblemonranger.no_mission"));
            return 0;
        }
        reply(context, Component.translatable("message.cobblemonranger.mission_info",
                RangerManager.missionLine(data.mission().get()), data.mission().get().giverName()));
        return 1;
    }

    private static int abandonMission(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!MissionManager.abandon(player(context))) {
            reply(context, Component.translatable("message.cobblemonranger.no_mission"));
            return 0;
        }
        return 1;
    }
}
