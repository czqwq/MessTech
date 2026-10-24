package com.MessTech.common.util;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The renderer half of an animation: drawn <b>over</b> a tooltip line already laid out and drawn with the font, which
 * lets an effect do what a string cannot - move, rotate or blend the letters themselves.
 * <p>
 * Client-only, registered per animation instance with {@link MTAnimatedTooltipHandler#registerRenderer}, so the
 * animation itself ({@link MTTextAnimation}) stays in common code. {@link MTAnimatedTooltipHandler} calls it once per
 * drawn line whose visible text matches the animated line, see {@link #draw}.
 */
@SideOnly(Side.CLIENT)
public interface MTTextRenderer {

    /**
     * Draws this renderer for one tooltip line.
     * <p>
     * State: GUI projection, {@code zLevel} already raised by the caller, depth test and lighting off, blending on.
     * The current colour and blend function are <b>not</b> defined, so set what is needed and restore what is
     * changed. Use the font as given - the coordinates are exactly the line's.
     *
     * @param font   the font the tooltip is drawn with
     * @param line   the drawn line, formatting codes included; its visible text is the animation's source text
     * @param x      the x the line was drawn at
     * @param y      the y the line was drawn at, i.e. the top of the 8 pixel tall text
     * @param millis the clock the tooltip is drawn with, normally {@link System#currentTimeMillis()}; the line's
     *               animation was evaluated a moment earlier in the same frame, so both are within one step
     */
    void draw(FontRenderer font, String line, int x, int y, long millis);

    /**
     * @return true when {@link #draw} renders the line itself, so the tooltip must not draw the plain font copy
     *         underneath. The Transcendent Metal tumbler replaces the text: a line drawn twice, once still and once
     *         turning, shows both at once.
     */
    default boolean replacesText() {
        return false;
    }
}
