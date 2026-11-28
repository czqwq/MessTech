package com.MessTech.common.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.MessTech.common.auth.MTProfileCache;
import com.MessTech.init.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

/**
 * MessTech's admin command for the online-mode session cache ported from ProfileCached: {@code /pfc <subcommand>}.
 * <p>
 * Subcommands (all of them permission level 3, like the ported mod's {@code hasPermission(3)}):
 * <ul>
 * <li>{@code reload} - re-reads {@code config/MessTech.cfg} (the {@code @Config.Reloadable("pfc")} fields) and clears
 * the cache, exactly like the ported mod's reload notice: a reload always starts empty;</li>
 * <li>{@code clean <player>} - drops the entry of one player name (the name is the cache key, so this works for an
 * offline player as well);</li>
 * <li>{@code cleanall} - empties the cache;</li>
 * <li>{@code size} - prints how many names are cached.</li>
 * </ul>
 * Every output line is a {@link ChatComponentTranslation}, so it follows the sender's language (keys
 * {@code command.pfc.*} in both lang files). The command is only registered on the dedicated server (see
 * {@code CommonProxy#serverStarting}), which is the only side the cache exists on.
 */
public class MTProfileCachedCommand extends CommandBase {

    private static final String USAGE_KEY = "command.pfc.usage";

    private static final String SUB_RELOAD = "reload";
    private static final String SUB_CLEAN = "clean";
    private static final String SUB_CLEAN_ALL = "cleanall";
    private static final String SUB_SIZE = "size";

    @Override
    public String getCommandName() {
        return "pfc";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return StatCollector.translateToLocal(USAGE_KEY);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            throw new WrongUsageException(USAGE_KEY);
        }

        // A reload also works while the cache is disabled: the admin may be turning it on.
        if (SUB_RELOAD.equalsIgnoreCase(args[0])) {
            ConfigurationManager.reloadConfig(Config.class, MTProfileCache.RELOAD_ID);
            MTProfileCache.clear();
            sender.addChatMessage(new ChatComponentTranslation("command.pfc.reloaded"));
            return;
        }

        if (!MTProfileCache.enabled()) {
            sender.addChatMessage(new ChatComponentTranslation("command.pfc.disabled"));
            return;
        }

        if (SUB_CLEAN_ALL.equalsIgnoreCase(args[0])) {
            final long size = MTProfileCache.size();
            MTProfileCache.clear();
            sender.addChatMessage(new ChatComponentTranslation("command.pfc.cleanall.done", size));
            return;
        }

        if (SUB_CLEAN.equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                throw new WrongUsageException(USAGE_KEY);
            }
            final String player = args[1];
            if (!MTProfileCache.invalidate(player)) {
                sender.addChatMessage(new ChatComponentTranslation("command.pfc.clean.notcached", player));
                return;
            }
            sender.addChatMessage(new ChatComponentTranslation("command.pfc.clean.done", player));
            return;
        }

        if (SUB_SIZE.equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentTranslation("command.pfc.size", MTProfileCache.size()));
            return;
        }

        throw new WrongUsageException(USAGE_KEY);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUB_RELOAD, SUB_CLEAN, SUB_CLEAN_ALL, SUB_SIZE);
        }
        if (args.length == 2 && SUB_CLEAN.equalsIgnoreCase(args[0])) {
            // The cache is keyed by name, but suggesting the online players is what the ported mod does.
            final MinecraftServer server = MinecraftServer.getServer();
            return server == null ? null : getListOfStringsMatchingLastWord(args, server.getAllUsernames());
        }
        return null;
    }
}
