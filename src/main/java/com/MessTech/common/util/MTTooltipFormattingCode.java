package com.MessTech.common.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The 1.7.10 counterpart of GT Modern's {@code TooltipHelper.GTFormattingCode}: a tooltip colour that steps through a
 * palette as a whole, one entry per {@code rate} client ticks.
 * <p>
 * GTLCore styles its source markers with GT Modern's {@code TooltipHelper.RAINBOW_SLOW} / {@code TooltipHelper.RAINBOW}
 * (see {@code GTLMachines.GTL_ADD} / {@code GTL_MODIFY}); this class is that mechanism ported, not an imitation of the
 * effect. The palette, its order and the rates are the values in {@code gtceu-1.20.1-1.4.4}
 * {@code com/gregtechceu/gtceu/client/util/TooltipHelper}: {@code ALL_COLORS} is
 * {@code RED, GOLD, YELLOW, GREEN, AQUA, DARK_AQUA, DARK_BLUE, BLUE, DARK_PURPLE, LIGHT_PURPLE},
 * {@code RAINBOW = createNewCode(5, ALL_COLORS)} and {@code RAINBOW_SLOW = createNewCode(25, ALL_COLORS)}. Its
 * {@code GTFormattingCode#updateIndex()} advances the index while {@code GTValues.CLIENT_TIME} is a multiple of the
 * rate, and the time is incremented afterwards, so the very first client tick already moves the palette on once.
 * <p>
 * A 1.7.10 tooltip line is a formatted string, so {@link #toString()} answers the control string of the current
 * colour ("\u00a7c", ...): the caller prefixes it to the line. That gives the same one flat colour per line that GT's
 * {@code Style#withColor} produces; both games share the vanilla 16 colour palette, so the rendered colours match.
 * <p>
 * The clock is a client tick counter driven by the listener that {@link com.MessTech.init.ClientProxy} registers;
 * nothing here touches client-only classes, so the class itself is safe to load on a dedicated server.
 */
public final class MTTooltipFormattingCode {

    /** GT Modern's {@code TooltipHelper.ALL_COLORS}, in its order: the palette every rainbow code cycles through. */
    private static final EnumChatFormatting[] ALL_COLORS = { EnumChatFormatting.RED, EnumChatFormatting.GOLD,
        EnumChatFormatting.YELLOW, EnumChatFormatting.GREEN, EnumChatFormatting.AQUA, EnumChatFormatting.DARK_AQUA,
        EnumChatFormatting.DARK_BLUE, EnumChatFormatting.BLUE, EnumChatFormatting.DARK_PURPLE,
        EnumChatFormatting.LIGHT_PURPLE };

    /** Every code created, GT Modern's {@code TooltipHelper.CODES}: the client tick advances all of them. */
    private static final List<MTTooltipFormattingCode> CODES = new ArrayList<>();

    /** GT Modern's {@code TooltipHelper.RAINBOW}: one palette entry per 5 client ticks. */
    public static final MTTooltipFormattingCode RAINBOW = createNewCode(5);

    /** GT Modern's {@code TooltipHelper.RAINBOW_SLOW}: one palette entry per 25 client ticks. */
    public static final MTTooltipFormattingCode RAINBOW_SLOW = createNewCode(25);

    /** GT Modern's {@code GTValues.CLIENT_TIME}: the client tick counter the indices advance on. */
    private static long clientTime;

    /** Client ticks between two palette entries. */
    private final int rate;
    /** The palette to cycle through. */
    private final EnumChatFormatting[] codes;
    /** The current palette entry; starts at the first one, like GT's code. */
    private int index;

    private MTTooltipFormattingCode(int rate, EnumChatFormatting[] codes) {
        this.rate = rate;
        this.codes = codes;
    }

    /** GT Modern's {@code TooltipHelper#createNewCode}: makes a rainbow code and adds it to the ticked list. */
    private static MTTooltipFormattingCode createNewCode(int rate) {
        MTTooltipFormattingCode code = new MTTooltipFormattingCode(rate, ALL_COLORS);
        CODES.add(code);
        return code;
    }

    /** The colour of this moment, i.e. of the line being built right now. */
    public EnumChatFormatting getCurrent() {
        return codes[index];
    }

    /** The current colour as the control string ("\u00a7c", ...) a 1.7.10 tooltip line can carry. */
    @Override
    public String toString() {
        return getCurrent().toString();
    }

    /** One client tick: GT updates every code first and bumps {@code CLIENT_TIME} afterwards. */
    public static void onClientTick() {
        for (MTTooltipFormattingCode code : CODES) {
            code.updateIndex();
        }
        clientTime++;
    }

    /** GT Modern's {@code GTFormattingCode#updateIndex}: advance one entry on every {@code rate}-th tick. */
    private void updateIndex() {
        if (clientTime % rate != 0) return;
        index = index + 1 >= codes.length ? 0 : index + 1;
    }

    /**
     * Registers this class's clock with FML. Client side only, called by the client proxy: a dedicated server has no
     * tooltips to colour.
     */
    @SideOnly(Side.CLIENT)
    public static void initClient() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientTick());
    }

    /**
     * The client tick listener. It is <b>public</b> because FML's {@code ASMEventHandler} builds its caller in another
     * class loader, which cannot access a non-public class (an {@code IllegalAccessError} on every client tick).
     */
    @SideOnly(Side.CLIENT)
    public static final class ClientTick {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            MTTooltipFormattingCode.onClientTick();
        }
    }
}
