package com.MessTech.common.mixin.early;

import net.minecraft.command.CommandHandler;
import net.minecraft.command.CommandKill;
import net.minecraft.command.ICommand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.MessTech.common.command.kill.MTCommandKill;

/**
 * Replaces vanilla's {@code /kill} command object with {@link MTCommandKill} while it is being registered, instead of
 * registering a second command or mutating the vanilla command class. {@code ServerCommandManager}'s constructor
 * calls {@code registerCommand(new CommandKill())}; swapping the argument here means the command map and the command
 * set only ever contain our command - no duplicate {@code /help} entry, and the permission level/usage/tab completion
 * all come from {@link MTCommandKill}.
 * <p>
 * The command object is always swapped; {@code Config.commands.OVERRIDE_KILL} is read by {@link MTCommandKill} at
 * execution time, so flipping the switch does not depend on the order in which classes happen to load. With the
 * switch off it reproduces vanilla {@code /kill} exactly (permission 0, kills only the sender, ignores arguments).
 */
@Mixin(CommandHandler.class)
public abstract class MixinCommandHandler {

    @ModifyVariable(method = "registerCommand", at = @At("HEAD"), argsOnly = true)
    private ICommand messtech$overrideKillCommand(ICommand command) {
        return command instanceof CommandKill ? new MTCommandKill() : command;
    }
}
