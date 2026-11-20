package com.MessTech.common.render.highlight;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One kind of world-space highlight, drawn by {@link HighlighterManager}.
 * <p>
 * Ported from Applied Energistics 2's {@code appeng.client.render.highlighter.IHighlighter}: the manager owns the
 * shared timing (the 500 ms blink phase and the expiry check), an implementation only says whether it has anything to
 * draw, draws its own world-space geometry and can be cleared. That split is what lets a highlight keep blinking after
 * the GUI that started it has closed - nothing here runs on the GUI's tick.
 */
@SideOnly(Side.CLIENT)
public interface IHighlighter {

    /** True when this highlighter currently has nothing to draw. */
    boolean noWork();

    /** Draws the highlighted blocks for this world render frame; the manager has already done the expiry check. */
    void renderHighlightedBlocks(RenderWorldLastEvent event);

    /** Drops everything currently highlighted. */
    void clear();

    /**
     * Wall-clock time in milliseconds after which the highlight expires; only read while {@link #noWork()} is false, so
     * a highlighter with nothing to draw may leave it at any value.
     */
    long getExpireTime();
}
