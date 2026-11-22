package com.MessTech.common.command.kill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;

import com.MessTech.init.Config;

/**
 * The 1.20.1 {@code /kill} command ported onto the 1.7.10 command dispatcher. It is registered in place of the vanilla
 * {@code CommandKill} by {@code MixinCommandHandler}, so the command name, its {@code /help} entry and its tab
 * completion are the ones of a normal vanilla command ("the command still is {@code kill}").
 * <p>
 * Behaviour follows 1.20.1's {@code KillCommand}: no argument kills the sender, an argument is an entity selector
 * resolved by {@link MTEntitySelectorParser}; every selected entity is killed ({@code LivingEntity#kill} in 1.20.1 is
 * 1.7.10's {@code attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE)}, while non-living entities are simply
 * removed); one target reports {@code commands.kill.success.single}, several report
 * {@code commands.kill.success.multiple}. The permission level is 2, as in 1.20.1, so command blocks may still run it
 * (1.7.10 grants them levels up to 2) but ordinary players cannot.
 * <p>
 * The command is installed in place of vanilla's command object by {@code MixinCommandHandler}; while
 * {@code Config.commands.OVERRIDE_KILL} is on (the default) it behaves as described above, and with the switch off it
 * reproduces vanilla {@code /kill} exactly (permission 0, kills only the sender, ignores arguments) so servers can
 * opt out without losing the command.
 */
public class MTCommandKill extends CommandBase {

    private static final String USAGE_KEY = "commands.kill.usage.targets";
    private static final String VANILLA_USAGE_KEY = "commands.kill.usage";
    private static final List<String> SELECTOR_TYPES = Arrays.asList("@p", "@a", "@r", "@e", "@s");
    private static final List<String> OPTION_KEYS = Arrays.asList(
        "name=",
        "distance=",
        "level=",
        "x=",
        "y=",
        "z=",
        "dx=",
        "dy=",
        "dz=",
        "x_rotation=",
        "y_rotation=",
        "limit=",
        "sort=",
        "gamemode=",
        "team=",
        "type=",
        "nbt=",
        "scores=");
    private static final List<String> SORT_VALUES = Arrays.asList("nearest", "furthest", "random", "arbitrary");
    private static final List<String> GAMEMODE_VALUES = Arrays.asList("survival", "creative", "adventure");

    @Override
    public String getCommandName() {
        return "kill";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return Config.commands.OVERRIDE_KILL ? 2 : 0;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return Config.commands.OVERRIDE_KILL ? USAGE_KEY : VANILLA_USAGE_KEY;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!Config.commands.OVERRIDE_KILL) {
            // Vanilla 1.7.10 CommandKill: no argument handling at all, the sender is always the victim.
            getCommandSenderAsPlayer(sender).attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
            sender.addChatMessage(new ChatComponentTranslation("commands.kill.success"));
            return;
        }

        if (args.length > 1) {
            throw new WrongUsageException(USAGE_KEY);
        }

        final List<Entity> targets;
        if (args.length == 0) {
            // 1.20.1: the source's own entity; console and command blocks have none, so they fail like 1.20.1 does.
            if (!(sender instanceof EntityPlayerMP)) {
                throw new CommandException("argument.entity.notfound.entity");
            }
            targets = Collections.<Entity>singletonList((Entity) sender);
        } else {
            targets = new MTEntitySelectorParser(args[0]).parse()
                .findEntities(sender);
            if (targets.isEmpty()) {
                throw new CommandException("argument.entity.notfound.entity");
            }
        }

        for (Entity target : targets) {
            kill(target);
        }

        if (targets.size() == 1) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "commands.kill.success.single",
                    targets.get(0)
                        .func_145748_c_()));
        } else {
            sender.addChatMessage(new ChatComponentTranslation("commands.kill.success.multiple", targets.size()));
        }
    }

    /**
     * 1.20.1's {@code Entity#kill} / {@code LivingEntity#kill}: living things take vanilla 1.7.10's own {@code /kill}
     * damage (out-of-world, maximum, bypasses armour and creative mode), everything else - dropped items, arrows,
     * primed TNT, boats, XP orbs - is removed from the world.
     */
    private static void kill(Entity entity) {
        if (entity == null) return;
        if (entity instanceof EntityLivingBase) {
            entity.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
        } else {
            entity.setDead();
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (!Config.commands.OVERRIDE_KILL || args.length != 1) return null;
        String token = args[0];

        if (!token.startsWith("@")) {
            return getListOfStringsMatchingLastWord(
                args,
                MinecraftServer.getServer()
                    .getAllUsernames());
        }

        int optionsStart = token.indexOf('[');
        if (optionsStart < 0) {
            return getListOfStringsMatchingLastWord(args, SELECTOR_TYPES.toArray(new String[0]));
        }
        if (token.endsWith("]")) return null;

        String head = token.substring(0, optionsStart + 1);
        String body = token.substring(optionsStart + 1);
        int lastComma = body.lastIndexOf(',');
        String done = lastComma < 0 ? "" : body.substring(0, lastComma + 1);
        String partial = lastComma < 0 ? body : body.substring(lastComma + 1);
        List<String> suggestions = new ArrayList<>();

        int equals = partial.indexOf('=');
        if (equals < 0) {
            for (String key : OPTION_KEYS) {
                if (key.startsWith(partial)) suggestions.add(head + done + key);
            }
            return suggestions;
        }

        String key = partial.substring(0, equals);
        String value = partial.substring(equals + 1)
            .toLowerCase(Locale.ROOT);
        if ("type".equals(key)) {
            for (String type : entityTypeNames()) {
                if (type.startsWith(value)) suggestions.add(head + done + "type=" + type);
            }
        } else if ("sort".equals(key)) {
            for (String sort : SORT_VALUES) {
                if (sort.startsWith(value)) suggestions.add(head + done + "sort=" + sort);
            }
        } else if ("gamemode".equals(key)) {
            for (String mode : GAMEMODE_VALUES) {
                if (mode.startsWith(value)) suggestions.add(head + done + "gamemode=" + mode);
            }
        }
        return suggestions;
    }

    /** The 1.7.10 entity registry keys, lower case, plus {@code player} - the closest to 1.20.1 type ids. */
    private static Set<String> entityTypeNames() {
        Set<String> names = new TreeSet<>();
        names.add("player");
        for (Object key : EntityList.stringToClassMapping.keySet()) {
            names.add(
                String.valueOf(key)
                    .toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
