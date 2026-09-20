package com.MessTech.common.util;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The renderer half of an animation: it is drawn <b>over</b> a tooltip line that has already been laid out and
 * drawn with the font, which is what lets an effect do things a string cannot - move, rotate, or blend the
 * letters themselves.
 * <p>
 * Renderers are client-only and are registered per animation instance with
 * {@link MTAnimatedTooltipHandler#registerRenderer}, so the animation itself (see {@link MTTextAnimation}) can
 * stay in common code. {@link MTAnimatedTooltipHandler} calls the renderer once per drawn line whose visible
 * text matches the animated line, see {@link #draw}.
 */
@SideOnly(Side.CLIENT)
public interface MTTextRenderer {

    /**
     * Draws this renderer for one tooltip line.
     * <p>
     * The state is the one a tooltip is drawn in: the GUI projection, {@code zLevel} already raised by the
     * caller, depth test and lighting off, blending on. The current colour and blend function are <b>not</b>
     * defined, so an implementation has to set what it needs and restore what it changes. The font has to be
     * used as given - the renderer draws in the same space the line was drawn in, so the coordinates it gets
     * are exactly the ones the line was drawn at.
     *
     * @param font   the font the tooltip is drawn with
     * @param line   the drawn line, formatting codes included; its visible text is the animation's source text
     * @param x      the x the line was drawn at
     * @param y      the y the line was drawn at, i.e. the top of the 8 pixel tall text
     * @param millis the clock the tooltip is drawn with, normally {@link System#currentTimeMillis()}; the
     *               animation of the same line was evaluated with a value a moment earlier in the same frame, so
     *               both are within one animation step of each other
     */
    void draw(FontRenderer font, String line, int x, int y, long millis);

    /**
     * @return true when {@link #draw} renders the line itself, so the tooltip must not draw the plain font copy
     *         underneath it. The Transcendent Metal tumbler replaces the text: a line drawn twice, once still and
     *         once turning, shows both at once.
     */
    default boolean replacesText() {
        return false;
    }
}
