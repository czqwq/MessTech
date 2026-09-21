package com.MessTech.common.util;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the moving half of {@link MTModuleProjectText}: under the letters a row of module chips - one per
 * {@link MTModuleProjectText#MODULE_SIZE} characters - lights up with the scan of the rack, with a brighter packet
 * on the module that is loading, a scan line travelling above the word and a bracket at either end of it.
 * <p>
 * The letters themselves stay where they are: the animation's own colours already read as a powered rack, so this
 * renderer adds the hardware around them instead of replacing the text ({@link #replacesText()} stays false).
 * <p>
 * Only the word is decorated. The brand line is {@code 添加模组: <word>}, so the prefix is measured with the font and
 * skipped; the chip row, the scan line and the brackets all start at the first glyph of the word and end at its last
 * one. A line that does not carry that prefix (the animation can be registered on any text) is decorated as a whole.
 * <p>
 * The phase is read from the same clock the tooltip was drawn with ({@link MTModuleProjectText#lit}, {@link
 * MTModuleProjectText#head}, {@link MTModuleProjectText#flashing}), so the chips and the letters never drift apart.
 */
@SideOnly(Side.CLIENT)
public final class MTModuleProjectTextRenderer implements MTTextRenderer {

    /** The plane the vanilla tooltip box is drawn on; the chips sit on it so they cannot fall behind it. */
    private static final float Z_LEVEL = 300.0F;

    /** Height of the text of one tooltip line, and of the gap below it before the next line starts. */
    private static final int LINE_HEIGHT = 10;

    /** Top of the chip row, relative to the top of the text: the two pixels below the glyphs. */
    private static final int CHIP_TOP = 8;

    /** Height of a chip. */
    private static final int CHIP_HEIGHT = 2;

    /** A module that is not powered yet. */
    private static final int CHIP_OFF = 0x55102030;
    /** A loaded module. */
    private static final int CHIP_LOADED = 0xAA14586E;
    /** The module the scan is loading, and the packet running along the rack. */
    private static final int CHIP_HOT = 0xEE8FF0FF;
    /** Full load. */
    private static final int CHIP_FULL = 0xEEFFFFFF;
    /** The brackets at both ends of the word and the line above it. */
    private static final int FRAME = 0x88A8E8FF;
    /** The tail of the scan line, over the module the scan just passed: it blinks, so the direction is visible. */
    private static final int SCAN_TAIL_BRIGHT = 0x77A8E8FF;
    /** The same tail, dimmed. */
    private static final int SCAN_TAIL_DIM = 0x33A8E8FF;

    /** How far the brackets stand off the first and last glyph. */
    private static final int BRACKET_GAP = 2;

    @Override
    public void draw(FontRenderer font, String line, int x, int y, long millis) {
        if (font == null || line == null || line.isEmpty()) return;

        String visible = MTTextAnimation.visibleText(line);
        int start = wordStart(visible);
        String word = visible.substring(start);
        if (word.isEmpty()) return;

        int modules = MTModuleProjectText.modules(word.length());
        if (modules <= 0) return;

        // Measure the way the font lays the line out, never by hand: a bold character advances one pixel further
        // than its glyph width (FontRenderer#renderStringAtPos adds the bold copy to the advance, and
        // FontRenderer#getStringWidth does the same), and the animation draws every character bold. A hand summed
        // width would come out one pixel per character short and pull the chips and the right bracket into the text.
        int rawStart = rawIndex(line, start);
        int wordX = x + font.getStringWidth(line.substring(0, rawStart));
        int[] edges = moduleEdges(font, line.substring(rawStart), wordX, word.length());
        double lit = MTModuleProjectText.lit(millis);
        int head = MTModuleProjectText.head(millis, modules);
        boolean flash = MTModuleProjectText.flashing(millis);
        int loaded = (int) Math.round(lit * modules);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        PAINTER.plane(Z_LEVEL, () -> {
            drawChips(edges, y, loaded, head, flash);
            drawScanLine(edges, y, head, millis);
            drawBrackets(edges, y);
        });
    }

    /** The chips: one per module, lit in step with the letters. */
    private static void drawChips(int[] edges, int y, int loaded, int head, boolean flash) {
        for (int module = 0; module < edges.length - 1; module++) {
            int colour = flash ? CHIP_FULL : module == head ? CHIP_HOT : module < loaded ? CHIP_LOADED : CHIP_OFF;
            rect(edges[module], y + CHIP_TOP, edges[module + 1] - 1, y + CHIP_TOP + CHIP_HEIGHT, colour);
        }
    }

    /**
     * The scan line: a bright one pixel line above the module that is loading, with a tail trailing back over the
     * modules it already passed, so the direction of the scan is visible even on a still frame.
     */
    private static void drawScanLine(int[] edges, int y, int head, long millis) {
        if (head < 0) return;

        rect(edges[head], y - 1, edges[head + 1] - 1, y, FRAME);

        int tail = head - 1;
        if (tail >= 0) {
            int colour = millis / 120L % 2L == 0L ? SCAN_TAIL_BRIGHT : SCAN_TAIL_DIM;
            rect(edges[tail], y - 1, edges[tail + 1] - 1, y, colour);
        }
    }

    /** The brackets: two short vertical bars, one before the first glyph and one after the last. */
    private static void drawBrackets(int[] edges, int y) {
        int top = y - 1;
        int bottom = y + LINE_HEIGHT - 1;
        rect(edges[0] - BRACKET_GAP, top, edges[0] - BRACKET_GAP + 1, bottom, FRAME);
        rect(edges[edges.length - 1] + BRACKET_GAP - 1, top, edges[edges.length - 1] + BRACKET_GAP, bottom, FRAME);
    }

    /**
     * The x of every module edge of the word, taken from the font itself: {@code edges[i]} is the left of module
     * {@code i} and {@code edges[modules]} the right of the last one, i.e. the pixel the pen stands on after the
     * last glyph. Every edge is measured on the drawn text (formatting codes included), so the bold advance the
     * animation's own codes add is part of it.
     *
     * @param font          the font the line is drawn with
     * @param raw           the drawn word, formatting codes included
     * @param x             the x the word starts at
     * @param visibleLength how many visible characters that word has
     */
    private static int[] moduleEdges(FontRenderer font, String raw, int x, int visibleLength) {
        int modules = MTModuleProjectText.modules(visibleLength);
        int[] edges = new int[modules + 1];
        edges[0] = x;

        int module = 0;
        int inModule = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == MTTextAnimation.SECTION) {
                i++;
                continue;
            }

            inModule++;
            if (inModule == MTModuleProjectText.MODULE_SIZE && module < modules - 1) {
                inModule = 0;
                edges[++module] = x + font.getStringWidth(raw.substring(0, i + 1));
            }
        }

        edges[modules] = x + font.getStringWidth(raw);
        return edges;
    }

    /**
     * The index of a visible character inside the drawn line, i.e. its index plus the formatting codes in front of
     * it. The prefix of a brand line is plain text, but a resource pack may colour it, so the codes are skipped
     * rather than assumed away.
     *
     * @param raw          the drawn line, formatting codes included
     * @param visibleIndex the index of that character in the visible text
     * @return the index in {@code raw}, the end of the line when it has no such character
     */
    private static int rawIndex(String raw, int visibleIndex) {
        int seen = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == MTTextAnimation.SECTION) {
                i++;
                continue;
            }
            if (seen == visibleIndex) return i;
            seen++;
        }
        return raw.length();
    }

    /**
     * The index of the word inside the visible text of a brand line - an index, not a width: the caller measures it
     * with the font and uses it to cut the string.
     *
     * @param visible the visible text of the line
     * @return the length of the {@code messTech.addBy} prefix and its separator, or 0 when the line does not carry
     *         that prefix
     */
    private static int wordStart(String visible) {
        String prefix = MTTextAnimation.visibleText(StatCollector.translateToLocal("messTech.addBy"))
            + MTPigTechText.PREFIX_SEPARATOR;
        return visible.startsWith(prefix) ? prefix.length() : 0;
    }

    private static void rect(int x1, int y1, int x2, int y2, int colour) {
        PAINTER.rect(x1, y1, x2, y2, colour);
    }

    /** The one painter of this renderer, see {@link MTAnimatedTooltipHandler}'s tooltip box for the same pattern. */
    private static final Painter PAINTER = new Painter();

    /**
     * {@code Gui#drawRect} and {@code Gui#zLevel} are both protected, so the rects and the plane they are drawn on
     * are reached through a subclass of {@code Gui} - the same pattern the tooltip box of
     * {@link MTAnimatedTooltipHandler} uses.
     */
    @SideOnly(Side.CLIENT)
    private static final class Painter extends Gui {

        /** Draws on the plane of the vanilla tooltip box, so nothing falls behind it, and restores the plane after. */
        private void plane(float zLevel, Runnable drawing) {
            this.zLevel = zLevel;
            drawing.run();
            this.zLevel = 0.0F;
        }

        private void rect(int x1, int y1, int x2, int y2, int colour) {
            drawRect(x1, y1, x2, y2, colour);
        }
    }
}
