package com.MessTech.common.util;

import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.AQUA;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.BLUE;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.BOLD;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.GOLD;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.GREEN;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.LIGHT_PURPLE;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.RED;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.YELLOW;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.addItemTooltip;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

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
 * Use {@link #author()} anywhere a {@code Supplier<String>} author is expected, and
 * {@link #registerOn(ItemStack)} to append it at the end of a machine's tooltip.
 */
public final class AuthorDynamic {

    /** The author field. */
    public static final String AUTHOR_CZQWQ = "czqwq";

    /** Rainbow ramp (bold), used as the cycling / wobbling palette. */
    private static final String[] RAINBOW_BOLD = { RED + BOLD, GOLD + BOLD, YELLOW + BOLD, GREEN + BOLD, AQUA + BOLD,
        BLUE + BOLD, LIGHT_PURPLE + BOLD };

    private AuthorDynamic() {}

    /**
     * @return the animated author name as a {@link Supplier}{@code <String>}.
     *         The whole name is rendered as one wobbling rainbow band (posstep = 1 -> colours glide
     *         neighbour to neighbour, giving the left-right wave).
     */
    public static Supplier<String> author() {
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
        if (machineStack == null) return;
        addItemTooltip(machineStack, GTAuthors.buildAuthorsWithFormatSupplier(AuthorDynamic.author()));
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
