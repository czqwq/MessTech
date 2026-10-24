package com.MessTech.client.command;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.MessTech.common.text.effect.TextEffectStyle;
import com.MessTech.common.text.effect.TextEffects;

/**
 * {@code /messtechfx [id]} - local-only preview: prints one sample line per text effect into the chat, where the font
 * renderer picks the shader up. No server round trip, and the command is not sent to the server as a chat message.
 */
public class MTTextEffectCommand extends CommandBase {

    public static final String USAGE_KEY = "command.messtechfx.usage";

    private static final String[] IDS = { "datafall", "eventhorizon", "inkbloom", "magnetar", "resonance", "beamcut",
        "cipher", "petalfall" };

    private static final TextEffectStyle[] STYLES = { TextEffects.DATAFALL, TextEffects.EVENTHORIZON,
        TextEffects.INKBLOOM, TextEffects.MAGNETAR, TextEffects.RESONANCE, TextEffects.BEAMCUT, TextEffects.CIPHER,
        TextEffects.PETALFALL };

    @Override
    public String getCommandName() {
        return "messtechfx";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return StatCollector.translateToLocal(USAGE_KEY);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String filter = args.length < 1 ? "all" : args[0].toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int i = 0; i < IDS.length; i++) {
            if (filter.equals("all") || filter.equals(IDS[i])) {
                preview(i);
                shown++;
            }
        }
        if (shown == 0) {
            send(EnumChatFormatting.RED + getCommandUsage(sender));
        }
    }

    private static void preview(int index) {
        String name = StatCollector.translateToLocal("messtech.text_effect." + IDS[index]);
        String sample = StatCollector.translateToLocal("messtech.text_effect." + IDS[index] + ".sample");
        send(
            EnumChatFormatting.DARK_GRAY + name
                + EnumChatFormatting.RESET
                + "  "
                + TextEffects.apply(sample, STYLES[index]));
    }

    private static void send(String text) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(text));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length != 1) return null;
        String[] options = new String[IDS.length + 1];
        System.arraycopy(IDS, 0, options, 0, IDS.length);
        options[IDS.length] = "all";
        return getListOfStringsMatchingLastWord(args, options);
    }
}
