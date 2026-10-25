package com.MessTech.common.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.MessTech.common.machine.MTMiningPlantRegistry;
import com.MessTech.init.Config;

/**
 * MessTech's one command root: {@code /messtech <subcommand>}.
 * <p>
 * Subcommands:
 * <ul>
 * <li>{@code GoUp [seconds]} - hands the sender to {@link MTGoUpFlight}, which pins them to a fixed climb speed for
 * that many seconds (30 by default);</li>
 * <li>{@code infiore} - prints dimension and coordinates of every loaded Comprehensive Mining Plant, from
 * {@link MTMiningPlantRegistry}. The registry only holds raw numbers and the line is a
 * {@link ChatComponentTranslation}, so the output is in the player's language.</li>
 * </ul>
 * The subcommand name is written as it is meant to be typed; only the root name is matched case-insensitively by
 * vanilla.
 * <p>
 * Permission level 2 (operator): throwing a player through the world is not something a random player should do to
 * themselves on a server. {@code GoUp} needs a player sender - no target argument, so the console has nothing to
 * launch; {@code infiore} also works from the console.
 */
public class MTCommand extends CommandBase {

    private static final String USAGE_KEY = "command.messtech.usage";
    private static final String SUBCOMMAND_GO_UP = "GoUp";
    private static final String SUBCOMMAND_INFIORE = "infiore";

    @Override
    public String getCommandName() {
        return "messtech";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return StatCollector.translateToLocal(USAGE_KEY);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) throw new WrongUsageException(USAGE_KEY);

        if (SUBCOMMAND_GO_UP.equalsIgnoreCase(args[0])) {
            final EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            final double seconds = args.length >= 2
                ? Math.min(parseDoubleWithMin(sender, args[1], 0.05D), MTGoUpFlight.MAX_SECONDS)
                : MTGoUpFlight.DEFAULT_SECONDS;

            MTGoUpFlight.start(player, seconds);

            sender.addChatMessage(
                new ChatComponentTranslation(
                    "command.messtech.goup.start",
                    String.format("%.1f", MTGoUpFlight.SPEED * 20.0D),
                    String.format("%.1f", seconds)));

            // The flight only survives if the vanilla "moved too quickly!" ceiling is above the square of the speed it
            // holds (the check compares squared per-tick distances), so warn instead of yanking the player back down.
            final double needed = MTGoUpFlight.SPEED * MTGoUpFlight.SPEED;
            if (Config.general.MOVED_TOO_QUICKLY_THRESHOLD < needed) {
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "command.messtech.goup.threshold.low",
                        String.format("%.1f", Config.general.MOVED_TOO_QUICKLY_THRESHOLD),
                        String.format("%.1f", needed)));
            }

            return;
        }

        if (SUBCOMMAND_INFIORE.equalsIgnoreCase(args[0])) {
            final List<MTMiningPlantRegistry.Entry> plants = MTMiningPlantRegistry.snapshot();
            if (plants.isEmpty()) {
                sender.addChatMessage(new ChatComponentTranslation("command.messtech.infiore.empty"));
                return;
            }
            for (MTMiningPlantRegistry.Entry plant : plants) {
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "command.messtech.infiore.entry",
                        plant.getDimension(),
                        plant.getX(),
                        plant.getY(),
                        plant.getZ()));
            }
            return;
        }

        throw new WrongUsageException(USAGE_KEY);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SUBCOMMAND_GO_UP, SUBCOMMAND_INFIORE) : null;
    }
}
