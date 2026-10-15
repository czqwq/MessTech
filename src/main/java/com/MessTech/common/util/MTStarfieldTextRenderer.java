package com.MessTech.common.util;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the moving half of {@link MTStarfieldText}: the line itself in the animation's flowing colours, and over it
 * the stars the "starfield" name promises - a handful of glinting pixels scattered over the letters, each on its own
 * blink clock, and a shooting star crossing the whole word now and then.
 * <p>
 * The renderer <b>replaces</b> the text ({@link #replacesText()}): the animation is a look the letters wear, not an
 * overlay on a still copy of them, and a plain white copy underneath would be what the player actually reads.
 * <p>
 * Everything here is a pure function of the clock the tooltip is drawn with, and of a hash of the character index,
 * so a star sits in the same pixel of the same glyph on every frame: only its blink phase moves. The stars are drawn
 * on the plane of the vanilla tooltip box (see {@link MTModuleProjectTextRenderer} for the same {@code Gui}
 * subclass trick), and the glyph geometry - where a character starts and how wide it is - comes from the font, never
 * from a hand sum, so a resource pack with different letter widths keeps the stars on their letters.
 */
@SideOnly(Side.CLIENT)
public final class MTStarfieldTextRenderer implements MTTextRenderer {

    /** The plane the vanilla tooltip box is drawn on; the stars sit on it so they cannot fall behind it. */
    private static final float Z_LEVEL = 300.0F;

    /** How long a star stays in one brightness step. */
    private static final long BLINK_STEP_MS = 260L;

    /** How many brightness steps a star runs through: dark, dim, bright, and the bright one with its cross. */
    private static final int BLINK_STEPS = 4;

    /** The colours a star twinkles in: white, pale cyan, pale violet, pale gold. */
    private static final int[] STAR_COLOURS = { 0xFFFFFFFF, 0xFFCFE8FF, 0xFFE9D8FF, 0xFFFFF0C8 };

    /** Alpha of the three brightness steps and of the four pixels of the cross around the brightest one. */
    private static final int ALPHA_FAINT = 0x55, ALPHA_DIM = 0xAA, ALPHA_BRIGHT = 0xFF, ALPHA_CROSS = 0x44;

    /** How long one shooting star takes to cross the line, and how much of that it is visible for. */
    private static final long SHOOTING_STAR_PERIOD_MS = 3600L;
    private static final double SHOOTING_STAR_VISIBLE = 0.35D;

    /** The tail of the shooting star: its length in pixels and the alpha of the pixel behind the head. */
    private static final int SHOOTING_STAR_TAIL = 4;
    private static final int ALPHA_TAIL = 0x99;

    @Override
    public boolean replacesText() {
        return true;
    }

    @Override
    public void draw(FontRenderer font, String line, int x, int y, long millis) {
        if (font == null || line == null) return;

        String visible = MTTextAnimation.visibleText(line);
        int width = font.getStringWidth(visible);
        if (visible.isEmpty() || width <= 0) return;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // The band itself. The frame is recomputed from the same clock the tooltip was drawn with, so the letters
        // here are the letters the tooltip asked for, not a second copy that could drift a step behind.
        font.drawStringWithShadow(MTStarfieldText.INSTANCE.frame(visible, millis), x, y, 0xFFFFFF);

        PAINTER.plane(Z_LEVEL, () -> {
            drawStars(font, visible, x, y, millis);
            drawShootingStar(x, y, width, millis);
        });
    }

    /**
     * One or two stars per glyph, each on its own blink clock: the position comes from a hash of the character
     * index and the star's slot, so a star never moves between frames, while its phase comes from the hash as well
     * and therefore ticks independently of its neighbours.
     */
    private static void drawStars(FontRenderer font, String visible, int x, int y, long millis) {
        long step = Math.floorDiv(millis, BLINK_STEP_MS);

        for (int i = 0; i < visible.length(); i++) {
            int charWidth = font.getCharWidth(visible.charAt(i));
            if (charWidth <= 0) continue;

            int left = x + font.getStringWidth(visible.substring(0, i));
            int seed = hash(i);

            for (int star = 0; star < 1 + (seed >>> 5 & 1); star++) {
                int starSeed = hash(seed ^ star * 0x2F1B3C5D);
                int starX = left + Math.floorMod(starSeed, charWidth);
                int starY = y + Math.floorMod(starSeed >>> 8, 8);

                int phase = (int) Math.floorMod(step + (starSeed >>> 16), BLINK_STEPS);
                if (phase == 0) continue; // dark: the star is not drawn at all, which is what makes it twinkle

                int colour = STAR_COLOURS[Math.floorMod(starSeed >>> 20, STAR_COLOURS.length)];
                int alpha = phase == 1 ? ALPHA_FAINT : phase == 2 ? ALPHA_DIM : ALPHA_BRIGHT;
                rect(starX, starY, starX + 1, starY + 1, withAlpha(colour, alpha));

                if (phase == 3) {
                    int cross = withAlpha(colour, ALPHA_CROSS);
                    rect(starX - 1, starY, starX, starY + 1, cross);
                    rect(starX + 1, starY, starX + 2, starY + 1, cross);
                    rect(starX, starY - 1, starX + 1, starY, cross);
                    rect(starX, starY + 1, starX + 1, starY + 2, cross);
                }
            }
        }
    }

    /**
     * A shooting star: once every {@link #SHOOTING_STAR_PERIOD_MS} one crosses the word from the upper left to the
     * lower right, with a short tail of fading pixels behind it. It is visible for the first
     * {@link #SHOOTING_STAR_VISIBLE} of its period, so it appears and disappears the way a shooting star does.
     */
    private static void drawShootingStar(int x, int y, int width, long millis) {
        double progress = (double) Math.floorMod(millis, SHOOTING_STAR_PERIOD_MS) / SHOOTING_STAR_PERIOD_MS;
        if (progress > SHOOTING_STAR_VISIBLE) return;

        double travel = progress / SHOOTING_STAR_VISIBLE;
        int starX = (int) Math.round(x - 8 + travel * (width + 16));
        int starY = (int) Math.round(y + 6 - travel * 8);

        for (int i = 0; i < SHOOTING_STAR_TAIL; i++) {
            int alpha = ALPHA_TAIL >> i;
            if (alpha <= 0) break;
            rect(starX - i, starY - i, starX - i + 1, starY - i + 1, withAlpha(0xFFFFFFFF, alpha));
        }

        // The head is two pixels, so it reads as the bright point the tail runs out of.
        rect(starX, starY, starX + 2, starY + 2, withAlpha(0xFFFFFFFF, ALPHA_BRIGHT));
    }

    /** Replaces the alpha of an {@code 0xRRGGBB} colour with the given one. */
    private static int withAlpha(int colour, int alpha) {
        return alpha << 24 | colour & 0xFFFFFF;
    }

    /**
     * A cheap avalanche hash, so that two adjacent characters get unrelated stars: the classic
     * multiply-xor-shift mix, which is all this needs - the stars only have to stay put and look scattered.
     */
    private static int hash(int value) {
        int h = value * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    private static void rect(int x1, int y1, int x2, int y2, int colour) {
        PAINTER.rect(x1, y1, x2, y2, colour);
    }

    /** The one painter of this renderer, see {@link MTModuleProjectTextRenderer} for the same pattern. */
    private static final Painter PAINTER = new Painter();

    /**
     * {@code Gui#drawRect} and {@code Gui#zLevel} are both protected, so the stars are drawn through a subclass of
     * {@code Gui}, on the plane of the tooltip box and with the plane restored afterwards.
     */
    @SideOnly(Side.CLIENT)
    private static final class Painter extends Gui {

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
