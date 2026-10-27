package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** {@code /ranger partners} lists partner Pokémon and {@code /ranger release} sends them all home. */
public final class RangerCommands {
    private RangerCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ranger")
                .then(Commands.literal("partners").executes(RangerCommands::listPartners))
                .then(Commands.literal("release").executes(RangerCommands::releasePartners)));
    }

    private static int listPartners(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<Pokemon> partners = PartnerManager.getPartners(player);
        if (partners.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("message.cobblemonranger.no_partners"), false);
            return 0;
        }
        for (Pokemon pokemon : partners) {
            context.getSource().sendSuccess(() -> Component.translatable("message.cobblemonranger.partner_entry",
                    pokemon.getSpecies().getTranslatedName(), pokemon.getLevel()), false);
        }
        return partners.size();
    }

    private static int releasePartners(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int released = PartnerManager.releaseAll(player);
        if (released == 0) {
            context.getSource().sendSuccess(() -> Component.translatable("message.cobblemonranger.no_partners"), false);
        }
        return released;
    }
}
