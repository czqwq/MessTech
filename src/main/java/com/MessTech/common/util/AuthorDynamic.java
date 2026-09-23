package com.MessTech.common.util;

import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.AQUA;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.BLUE;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.BOLD;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.DARK_PURPLE;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.GOLD;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.GREEN;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.LIGHT_PURPLE;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.RED;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.YELLOW;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.animatedText;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.chain;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.text;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import gregtech.api.enums.GTAuthors;

/**
 * Dynamic animated author "czqwq" for MessTech machine tooltips.
 * <p>
 * Built on top of gtnhlib's {@code AnimatedTooltipHandler}, combining several of the animation
 * styles found in {@link GTAuthors}:
 * <ul>
 * <li>rainbow colour cycling (like {@code GTAuthors.AuthorThree}),</li>
 * <li>a colour "wave" sweeping across the letters (like {@code GTAuthors.AuthorSerenibyss}),</li>
 * <li>extended with a ping-pong (left-right wobble) offset so the rainbow band slides back and
 * forth instead of only scrolling one way.</li>
 * </ul>
 * Use {@link #author_czqwq()} anywhere a {@code Supplier<String>} author is expected, and
 * {@link #registerOn(ItemStack)} to append it at the end of a machine's tooltip.
 * <p>
 * {@link #register(MTTextAnimation, Supplier, ItemStack)} is the general form: instead of the built-in rainbow it
 * animates the author name with a caller supplied {@link MTTextAnimation}, which may also bring a
 * {@link MTTextRenderer} - a renderer drawn over the finished line, not just colour codes inside it.
 * {@link #TRANSCENDENT_METAL} is the built-in one: GT5U's Transcendent Metal animation worn by the whole line, see
 * {@link MTTranscendentMetalText} and {@link MTTranscendentMetalTextRenderer}.
 */
public final class AuthorDynamic {

    /** The author field. */
    public static final String AUTHOR_CZQWQ = "czqwq";

    /**
     * GT5U's Transcendent Metal look, worn by a whole line of text: the metal band of
     * {@link MTTranscendentMetalText} plus the tumbler {@link MTTranscendentMetalTextRenderer} draws over the
     * finished font. Hand it to {@link #register(MTTextAnimation, Supplier, ItemStack)}.
     * <p>
     * The animations are registered through {@link MTAnimatedTooltipHandler}, which owns the registry and the
     * client side renderer, because a rendering animation has to draw over the font and gtnhlib's
     * {@code AnimatedTooltipHandler} can only add strings.
     */
    public static final MTTextAnimation TRANSCENDENT_METAL = MTTranscendentMetalText.INSTANCE;

    /**
     * The modular machine look of the {@code ModularProject} brand line: the rack of {@link MTModuleProjectText},
     * powered up module by module, with the chip row of {@link MTModuleProjectTextRenderer} under the letters. Hand
     * it to {@link #registerAddon(MTTextAnimation, Supplier, ItemStack)}.
     */
    public static final MTTextAnimation MODULE_PROJECT = MTModuleProjectText.INSTANCE;

    /** Rainbow ramp (bold), used as the cycling / wobbling palette. */
    private static final String[] RAINBOW_BOLD = { RED + BOLD, GOLD + BOLD, YELLOW + BOLD, GREEN + BOLD, AQUA + BOLD,
        BLUE + BOLD, LIGHT_PURPLE + BOLD };

    /** Purple gradient used for the animated "MessTech" mod line. */
    private static final String[] PURPLE_FLOW = { DARK_PURPLE + BOLD, LIGHT_PURPLE + BOLD, BLUE + BOLD,
        LIGHT_PURPLE + BOLD };

    private AuthorDynamic() {}

    /**
     * @return the animated author name as a {@link Supplier}{@code <String>}.
     *         The whole name is rendered as one wobbling rainbow band (posstep = 1 -> colours glide
     *         neighbour to neighbour, giving the left-right wave).
     */
    public static Supplier<String> author_czqwq() {
        return wobbleAnimatedText(AUTHOR_CZQWQ, 1, 140, RAINBOW_BOLD);
    }

    /**
     * Register the author line at the end of the given machine item's tooltip.
     * <p>
     * Same pattern as {@code LoaderMetaTileEntities}: {@code AnimatedTooltipHandler.addItemTooltip(...)}.
     *
     * @param machineStack the machine's {@link ItemStack}
     */
    public static void registerOn(ItemStack machineStack) {
        registerOn(author_czqwq(), machineStack);
    }

    /**
     * Register the author line + animated MessTech add-on line.
     *
     * @param author       author tooltip supplier (e.g. {@link #author_czqwq()} or a GTAuthors supplier)
     * @param machineStack the machine's {@link ItemStack}
     */
    public static void registerOn(Supplier<String> author, ItemStack machineStack) {
        addAuthorLine(author, machineStack, null);
    }

    /**
     * Register the author line + animated MessTech add-on line, with the author name animated by the given
     * animation instead of the built-in rainbow.
     * <p>
     * The visible text of {@code author} is what gets animated: a supplier that brings its own colour codes
     * (such as {@link #author_czqwq()}) is fine, its codes are dropped and the animation's own ones are used
     * instead. The same call shape the built-in look uses, with one extra argument - the animation to wear:
     *
     * <pre>
     * AuthorDynamic.register(AuthorDynamic.TRANSCENDENT_METAL, AuthorDynamic.author_czqwq(), machineStack);
     * </pre>
     *
     * @param animation    the look the author name wears, e.g. {@link #TRANSCENDENT_METAL}
     * @param author       the author text to animate, see {@link MTTextAnimation#visibleText}
     * @param machineStack the machine's {@link ItemStack}
     */
    public static void register(MTTextAnimation animation, Supplier<String> author, ItemStack machineStack) {
        if (animation == null) return;

        addAuthorLine(
            () -> animation
                .frame(MTTextAnimation.visibleText(author == null ? null : author.get()), System.currentTimeMillis()),
            machineStack,
            animation);
    }

    /**
     * {@link #register(MTTextAnimation, Supplier, ItemStack)} for the default author name, {@link #AUTHOR_CZQWQ}.
     *
     * @param animation    the look the author name wears, e.g. {@link #TRANSCENDENT_METAL}
     * @param machineStack the machine's {@link ItemStack}
     */
    public static void register(MTTextAnimation animation, ItemStack machineStack) {
        register(animation, text(AUTHOR_CZQWQ), machineStack);
    }

    /**
     * Register only the animated brand line of a machine, {@code Add by: <word>}, with no author line.
     * <p>
     * The line has the shape the fuel rods already use: {@code AuthorDynamic} puts the static prefix
     * ({@code messTech.addBy}) and one space in front, and the word after it wears the animation. A machine that
     * should not name an author - or whose tooltip is already long - can carry an animated brand word this way:
     *
     * <pre>
     * AuthorDynamic
     *     .registerAddon(AuthorDynamic.MODULE_PROJECT, () -&gt; translateToLocal("messtech.moduleProject"), machineStack);
     * </pre>
     *
     * @param animation    the look the word wears, e.g. {@link #MODULE_PROJECT}
     * @param word         the word to animate, normally a {@code Supplier} of a language key
     * @param machineStack the machine's {@link ItemStack}
     */
    public static void registerAddon(MTTextAnimation animation, Supplier<String> word, ItemStack machineStack) {
        if (animation == null || word == null || machineStack == null) return;

        MTAnimatedTooltipHandler
            .addAnimatedText(machineStack, () -> StatCollector.translateToLocal("messTech.addBy"), word, animation);
    }

    /**
     * Adds the author line - and the animated MessTech line that follows it - to a stack.
     *
     * @param author       the author text, already the way it should be drawn
     * @param machineStack the stack to decorate
     * @param animation    the animation the author line wears, {@code null} for a plain line
     */
    private static void addAuthorLine(Supplier<String> author, ItemStack machineStack, MTTextAnimation animation) {
        if (author == null || machineStack == null) return;

        MTAnimatedTooltipHandler
            .addItemTooltip(machineStack, GTAuthors.buildAuthorsWithFormatSupplier(author), animation);
        MTAnimatedTooltipHandler.addItemTooltip(
            machineStack,
            chain(text(StatCollector.translateToLocal("messTech.addBy") + " "), messTechAnimated()));
    }

    /** Animated "MessTech" with a flowing purple gradient. */
    public static Supplier<String> messTechAnimated() {
        // Gentle flowing purple gradient: slow 1000ms per step.
        return animatedText("MessTech", 1, 1000, PURPLE_FLOW);
    }

    /**
     * gtnhlib's {@code animatedText} but with a ping-pong (triangle wave) offset, so the colour
     * band sweeps left-right instead of looping in one direction.
     */
    private static Supplier<String> wobbleAnimatedText(String text, int posstep, int delay, String... colors) {
        if (text == null || text.isEmpty() || colors == null || colors.length == 0) return () -> "";
        if (colors.length == 1) return () -> colors[0] + text;

        final int finalDelay = Math.max(delay, 1);
        final int finalPosstep = Math.max(posstep, 0);
        final int period = colors.length * 2; // ping-pong range: 0..len-1..0

        return () -> {
            StringBuilder sb = new StringBuilder(text.length() * 3);
            long raw = (System.currentTimeMillis() / finalDelay) % period;
            int offset = (int) (raw < colors.length ? raw : period - 1 - raw);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int indexColorArray = Math.floorMod(i * finalPosstep + colors.length - offset, colors.length);
                sb.append(colors[indexColorArray]);
                sb.append(c);
            }
            return sb.toString();
        };
    }
}
