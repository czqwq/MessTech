package com.MessTech.common.util;

import static net.minecraft.util.StatCollector.translateToLocal;

import net.minecraft.item.ItemStack;

/**
 * The 1.7.10 counterpart of GTLCore's {@code gtlcore.registry.add} / {@code gtlcore.registry.modify} source markers: a
 * source line appended to the tooltip of content this mod adds, and of content whose behaviour it extends.
 * <p>
 * Both the wording and the colour are copied from GTLCore, not adapted. The text is GTLCore's own
 * 「由GregTech Leisure添加 / Added by GregTech Leisure」 and 「由GregTech Leisure修改 / Modified by GregTech Leisure」;
 * the colour is the GT Modern code GTLCore puts on it - {@code TooltipHelper.RAINBOW_SLOW} for the added marker and
 * {@code TooltipHelper.RAINBOW} for the modified one (see {@link MTTooltipFormattingCode}). That means one flat colour
 * for the whole line, stepping through the same ten colour palette every 25 / 5 client ticks, not a per character
 * rainbow of our own.
 * <p>
 * The text is read from the language file on every frame (the supplier runs per tooltip), so a resource pack reload
 * keeps it localised while the colour keeps moving.
 */
public final class MTRegistryMark {

    /** 「由GregTech Leisure添加」 - GTLCore's wording, kept as-is. */
    public static final String ADD_KEY = "messTech.registry.add";
    /** 「由GregTech Leisure修改」 - GTLCore's wording, kept as-is. */
    public static final String MODIFY_KEY = "messTech.registry.modify";

    private MTRegistryMark() {}

    /** Marks a stack as content added by this mod, with GTLCore's exact source marker (text and colour). */
    public static void markAdded(ItemStack stack) {
        if (stack == null) return;
        MTAnimatedTooltipHandler.addItemTooltip(stack, MTRegistryMark::addedLine);
    }

    /**
     * Marks a stack as existing content whose behaviour this mod extends, with GTLCore's exact source marker (text and
     * colour). Nothing wears it at the moment - the wireless ME hatches deliberately carry no modification marker -
     * and it is the port of GTLCore's {@code gtlcore.registry.modify}, kept for later content (see
     * docs/wireless-me-network.md §4.6).
     */
    public static void markModified(ItemStack stack) {
        if (stack == null) return;
        MTAnimatedTooltipHandler.addItemTooltip(stack, MTRegistryMark::modifiedLine);
    }

    /** The added marker: GTLCore's text and the slow rainbow code, a fresh colour every frame. */
    private static String addedLine() {
        return MTTooltipFormattingCode.RAINBOW_SLOW.toString() + translateToLocal(ADD_KEY);
    }

    /** The modified marker: GTLCore's text and the fast rainbow code, a fresh colour every frame. */
    private static String modifiedLine() {
        return MTTooltipFormattingCode.RAINBOW.toString() + translateToLocal(MODIFY_KEY);
    }
}
