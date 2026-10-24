package com.MessTech.common.util;

/**
 * The {@code STARFIELD} text animation: a night-sky band flowing through a stretch of text, one colour per character,
 * so the letters read as a drifting nebula before anything is drawn over them.
 * <p>
 * A tooltip line is laid out glyph by glyph with a fixed advance, so this kind of animation can only change each
 * glyph's colour (bold would move the advance by a pixel per character, see {@code MTModuleProjectTextRenderer}, and
 * is deliberately unused). This class answers "which of the sixteen built-in colours does character {@code i} wear
 * this millisecond"; the moving half - twinkling stars, shooting star - is {@link MTStarfieldTextRenderer},
 * registered client-side.
 * <p>
 * The band is a pure function of the clock: character {@code i} wears
 * {@code PALETTE[(i + floor(millis / COLOUR_STEP_MS)) mod PALETTE.length]}, i.e. neighbouring characters wear
 * neighbouring colours (the gradient read as a flow) and the band travels one character every
 * {@link #COLOUR_STEP_MS}. The palette is one whole loop (see {@link #PALETTE}), so every pass has a bright white core
 * with the night sky on one side and a warm ember on the other.
 */
public final class MTStarfieldText implements MTTextAnimation {

    /** The one animation instance; it is stateless, so every registration can share it. */
    public static final MTStarfieldText INSTANCE = new MTStarfieldText();

    /** Milliseconds per colour step: the band travels one character at a time. */
    public static final long COLOUR_STEP_MS = 75L;

    /**
     * The band, as {@code §}-colour characters: light purple, dark purple, blue, dark aqua, aqua, white (the sky
     * and its one bright star) and back through aqua, dark aqua, blue, dark purple, red and gold (the ember on the
     * other side of the white). One whole loop of the animation.
     */
    private static final char[] PALETTE = { 'd', '5', '9', '3', 'b', 'f', 'b', '3', '9', '5', 'c', '6' };

    private MTStarfieldText() {}

    /**
     * @param source the text to wear the starfield; its own colour codes are dropped, the palette replaces them
     * @param millis any clock in milliseconds; the band is a function of it alone, so the same millisecond always
     *               gives the same frame and the renderer can recompute the frame the line was drawn with
     * @return the text with one palette colour per character
     */
    @Override
    public String frame(String source, long millis) {
        String text = MTTextAnimation.visibleText(source);
        if (text.isEmpty()) return "";

        int offset = (int) (Math.floorDiv(millis, COLOUR_STEP_MS) % PALETTE.length);

        StringBuilder line = new StringBuilder(text.length() * 4);
        for (int i = 0; i < text.length(); i++) {
            line.append(SECTION)
                .append(PALETTE[Math.floorMod(i + offset, PALETTE.length)])
                .append(text.charAt(i));
        }
        return line.toString();
    }
}
