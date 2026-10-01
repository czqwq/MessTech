package com.MessTech.common.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.MessTech.init.Config;

/**
 * MessTech's one command root: {@code /messtech <subcommand>}.
 * <p>
 * Currently only {@code GoUp [seconds]} exists - it hands the sender to {@link MTGoUpFlight}, which pins them to a
 * fixed climb speed for that many seconds (30 by default). The subcommand name is written as it is meant to be typed;
 * only the root name is matched case-insensitively by vanilla.
 * <p>
 * The whole command is gated at permission level 2 (operator), because throwing a player through the world is not
 * something a random player should be able to do to themselves on a server. The sender has to be a player: there is
 * no target argument, so the console has nothing to launch.
 */
public class MTCommand extends CommandBase {

    private static final String USAGE_KEY = "command.messtech.usage";

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

        if ("GoUp".equalsIgnoreCase(args[0])) {
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

            // The flight only survives if the vanilla "moved too quickly!" ceiling is above the square of the speed
            // it holds (the check compares squared per-tick distances), so say so instead of letting the player be
            // yanked back down with no explanation.
            final double needed = MTGoUpFlight.SPEED * MTGoUpFlight.SPEED;
            if (Config.MOVED_TOO_QUICKLY_THRESHOLD < needed) {
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "command.messtech.goup.threshold.low",
                        String.format("%.1f", Config.MOVED_TOO_QUICKLY_THRESHOLD),
                        String.format("%.1f", needed)));
            }

            return;
        }

        throw new WrongUsageException(USAGE_KEY);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "GoUp") : null;
    }
}
