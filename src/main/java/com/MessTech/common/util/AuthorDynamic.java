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
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTAuthors;

/**
 * Dynamic animated author "czqwq" for MessTech machine tooltips, on gtnhlib's {@code AnimatedTooltipHandler}:
 * rainbow cycling and a colour "wave" over the letters, as {@code GTAuthors.AuthorThree} /
 * {@code GTAuthors.AuthorSerenibyss} do, plus a ping-pong offset so the band slides back and forth instead of
 * scrolling one way.
 * <p>
 * {@link #author_czqwq()} gives the author for {@link #registerOn(ItemStack)};
 * {@link #register(MTTextAnimation, Supplier, ItemStack)} is the general form, animating the name with a caller
 * {@link MTTextAnimation} that may add a {@link MTTextRenderer} drawn over the finished line. Built-ins:
 * {@link #TRANSCENDENT_METAL}, {@link #MODULE_PROJECT}.
 * <p>
 * The animations register through {@link MTAnimatedTooltipHandler} because a renderer must draw over the font and
 * gtnhlib's {@code AnimatedTooltipHandler} can only add strings.
 */
public final class AuthorDynamic {

    /** The author field. */
    public static final String AUTHOR_CZQWQ = "czqwq";

    /**
     * The author field of Elinar, whose Comprehensive Mining Plant was ported into this mod: {@code §[pf]乳胶芷若喵§r}.
     * The {@code §[pf]} inline marker is the petalfall text effect and the trailing {@code §r} ends its scope; the font
     * pipeline draws it, so this line is plain text and needs no {@link MTTextAnimation}.
     */
    public static final String Author_Elinar = "§[pf]乳胶芷若喵§r";

    /**
     * GT5U's Transcendent Metal for a whole line: the band of {@link MTTranscendentMetalText} plus the tumbler
     * {@link MTTranscendentMetalTextRenderer} draws over the finished font, for
     * {@link #register(MTTextAnimation, Supplier, ItemStack)}.
     */
    public static final MTTextAnimation TRANSCENDENT_METAL = MTTranscendentMetalText.INSTANCE;

    /**
     * The {@code ModularProject} brand look: the rack of {@link MTModuleProjectText}, powered up module by module,
     * with the chip row of {@link MTModuleProjectTextRenderer} under the letters; for
     * {@link #registerAddon(MTTextAnimation, Supplier, ItemStack)}.
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
     * @return the animated author name as a {@link Supplier}{@code <String>}: one wobbling rainbow band (posstep =
     *         1 -> colours glide neighbour to neighbour, giving the left-right wave).
     */
    public static Supplier<String> author_czqwq() {
        return wobbleAnimatedText(AUTHOR_CZQWQ, 1, 140, RAINBOW_BOLD);
    }

    /**
     * Register the author line at the end of a machine item's tooltip, the way {@code LoaderMetaTileEntities} does:
     * {@code AnimatedTooltipHandler.addItemTooltip(...)}.
     */
    public static void registerOn(ItemStack machineStack) {
        registerOn(author_czqwq(), machineStack);
    }

    /**
     * Register the author line + animated MessTech add-on line.
     *
     * @param author author tooltip supplier, e.g. {@link #author_czqwq()} or a GTAuthors supplier
     */
    public static void registerOn(Supplier<String> author, ItemStack machineStack) {
        addAuthorLine(author, machineStack, null);
    }

    /**
     * Register the author line + animated MessTech add-on line, the author name animated by the given animation
     * instead of the built-in rainbow. The visible text of {@code author} is animated: a supplier with its own colour
     * codes (such as {@link #author_czqwq()}) is fine, its codes are dropped in favour of the animation's.
     *
     * <pre>
     * AuthorDynamic.register(AuthorDynamic.TRANSCENDENT_METAL, AuthorDynamic.author_czqwq(), machineStack);
     * </pre>
     *
     * @param animation the look the author name wears, e.g. {@link #TRANSCENDENT_METAL}
     * @param author    the author text to animate, see {@link MTTextAnimation#visibleText}
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
     * @param animation the look the author name wears, e.g. {@link #TRANSCENDENT_METAL}
     */
    public static void register(MTTextAnimation animation, ItemStack machineStack) {
        register(animation, text(AUTHOR_CZQWQ), machineStack);
    }

    /**
     * Register only the animated brand line of a machine, {@code Add by: <word>}, with no author line - the line
     * shape the fuel rods use: {@code AuthorDynamic} puts the static {@code messTech.addBy} prefix and one space in
     * front, the word after it wears the animation. For a machine that should not name an author, or whose tooltip
     * is already long:
     *
     * <pre>
     * AuthorDynamic
     *     .registerAddon(AuthorDynamic.MODULE_PROJECT, () -&gt; translateToLocal("messtech.moduleProject"), machineStack);
     * </pre>
     *
     * @param animation the look the word wears, e.g. {@link #MODULE_PROJECT}
     * @param word      the word to animate, normally a {@code Supplier} of a language key
     */
    public static void registerAddon(MTTextAnimation animation, Supplier<String> word, ItemStack machineStack) {
        if (animation == null || word == null || machineStack == null) return;

        MTAnimatedTooltipHandler
            .addAnimatedText(machineStack, () -> translateToLocal("messTech.addBy"), word, animation);
    }

    /**
     * Registers the hardcoded author line {@code messTech.addByAuthor + " " + Author_Elinar} on a machine stack, and
     * nothing else: no animated "Add by: MessTech" line follows it, unlike {@link #registerOn(Supplier, ItemStack)}.
     * The prefix is re-read from the language files every frame (the supplier runs per tooltip), so a resource pack
     * reload keeps the line in the player's language, and the petalfall marker in {@link #Author_Elinar} is the only
     * animation it carries.
     */
    public static void registerElinarAuthor(ItemStack machineStack) {
        if (machineStack == null) return;

        MTAnimatedTooltipHandler
            .addItemTooltip(machineStack, () -> translateToLocal("messTech.addByAuthor") + " " + Author_Elinar);
    }

    /**
     * Adds the author line - and the animated MessTech line that follows it - to a stack.
     *
     * @param author    the author text, already the way it should be drawn
     * @param animation the animation the author line wears, {@code null} for a plain line
     */
    private static void addAuthorLine(Supplier<String> author, ItemStack machineStack, MTTextAnimation animation) {
        if (author == null || machineStack == null) return;

        MTAnimatedTooltipHandler
            .addItemTooltip(machineStack, GTAuthors.buildAuthorsWithFormatSupplier(author), animation);
        MTAnimatedTooltipHandler
            .addItemTooltip(machineStack, chain(text(translateToLocal("messTech.addBy") + " "), messTechAnimated()));
    }

    /** Animated "MessTech" with a flowing purple gradient. */
    public static Supplier<String> messTechAnimated() {
        // Gentle flowing purple gradient: slow 1000ms per step.
        return animatedText("MessTech", 1, 1000, PURPLE_FLOW);
    }

    /**
     * gtnhlib's {@code animatedText} with a ping-pong (triangle wave) offset, so the colour band sweeps left-right
     * instead of looping one way.
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
