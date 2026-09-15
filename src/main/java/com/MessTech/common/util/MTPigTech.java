package com.MessTech.common.util;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler;

/**
 * The "PigTech" brand line: {@link MTPigTechText}'s two second pig animation, behind the same static "Add by:" prefix
 * {@link AuthorDynamic} puts in front of the animated MessTech name, on an item tooltip through the same gtnhlib
 * mechanism the GT5U author names use.
 * <p>
 * "Add by:" is a fixed string ({@code messTech.addBy}), copied into the line verbatim once per frame, so it never
 * moves, changes width or takes part in the animation - only the word after it does, and the animation reserves its
 * own margin so the two can never touch. See {@link MTPigTechText} for what the medium can express.
 * <p>
 * {@code AnimatedTooltipHandler} keeps a {@code Supplier<String>} per {@link ItemStack} and re-evaluates it once per
 * frame ({@code renderTooltip} splits the result on {@code '\n'}), so the whole animation is simply the string the
 * supplier returns - there is no renderer, no event handler and no per frame state in this class.
 * <p>
 * {@link #pigRegisterOn(ItemStack)} is the "PigRegisterOn" entry point: it adds the animated line and nothing else.
 * Unlike {@link AuthorDynamic#registerOn} it adds no author line.
 */
public final class MTPigTech {

    /** Translation key of the animated string; translating it also translates what is being animated. */
    public static final String LANG_KEY = "messtech.pigTech";

    /** Translation key of the static prefix, shared with the "Add by: MessTech" line so both read the same. */
    public static final String PREFIX_LANG_KEY = "messTech.addBy";

    private MTPigTech() {}

    /** @return the animated line behind the static prefix, using the translated strings and the real clock. */
    public static Supplier<String> animation() {
        return animation(prefix(), StatCollector.translateToLocal(LANG_KEY));
    }

    /**
     * @param text the string to animate instead of the translated brand name
     * @return the animated line behind the static prefix
     */
    public static Supplier<String> animation(String text) {
        return animation(prefix(), text);
    }

    /**
     * @param prefix the static text in front of the animation; {@code null} or empty leaves it out
     * @param text   the string to animate
     * @return the animated line, re-evaluated once per frame while the tooltip is open
     */
    public static Supplier<String> animation(String prefix, String text) {
        return () -> MTPigTechText.frame(prefix, text, System.currentTimeMillis());
    }

    /** @return the static "Add by:" prefix, translated. */
    public static String prefix() {
        return StatCollector.translateToLocal(PREFIX_LANG_KEY);
    }

    /**
     * PigRegisterOn: adds the animated "PigTech" line to the tooltips of the given stack, and nothing else.
     *
     * @param stack the stack to decorate; {@code OreDictionary.WILDCARD_VALUE} as its damage covers every variant
     */
    public static void pigRegisterOn(ItemStack stack) {
        if (stack == null) return;
        AnimatedTooltipHandler.addItemTooltip(stack, animation());
    }

    /**
     * PigRegisterOn with an explicit string instead of the translation. The static prefix is still added.
     *
     * @param stack the stack to decorate
     * @param text  the string to animate
     */
    public static void pigRegisterOn(ItemStack stack, String text) {
        if (stack == null || text == null) return;
        AnimatedTooltipHandler.addItemTooltip(stack, animation(text));
    }
}
