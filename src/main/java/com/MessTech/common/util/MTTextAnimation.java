package com.MessTech.common.util;

/**
 * One animated look for a stretch of tooltip text.
 * <p>
 * An animation is a plain object that answers one question per frame: <i>what does the line look like right
 * now</i>. That is the same contract the animated author names in {@code GTAuthors} and the "PigTech" line use,
 * so an animation can be handed to any tooltip text consumer.
 * <p>
 * The <b>renderer</b> that is drawn over the finished line - the part that needs a {@code FontRenderer} and
 * OpenGL, and therefore cannot live in common code - is registered client-side for the animation instance, see
 * {@link MTAnimatedTooltipHandler#registerRenderer}. An animation without a renderer is a pure formatting
 * animation (colour codes and spacing), which is what {@link MTPigTechText} is.
 * <p>
 * {@link MTTranscendentMetalText} is the built-in {@code TRANSCENDENT_METAL} look: GT5U's Transcendent Metal
 * animation, a renderer that tumbles a metallic copy of the line over the font.
 */
public interface MTTextAnimation {

    /** The section sign that introduces a formatting code in every Minecraft string. */
    char SECTION = '\u00a7';

    /**
     * @param source the visible text to animate, formatting codes already removed
     * @param millis any clock in milliseconds, normally {@link System#currentTimeMillis()}; the animation is a
     *               pure function of it, so the same millisecond always gives the same frame
     * @return this frame as one tooltip line, formatting codes allowed
     */
    String frame(String source, long millis);

    /**
     * The visible characters of a string, i.e. the text without its formatting codes.
     * <p>
     * Used to hand an animation the text a supplier produces (a supplier such as {@code author_czqwq()} returns
     * its own colour codes) and to recognise an animated line again while it is being drawn.
     *
     * @param line a line that may contain formatting codes, {@code null} is treated as empty
     * @return the same line without any formatting code pair
     */
    static String visibleText(String line) {
        if (line == null || line.isEmpty()) return "";

        StringBuilder visible = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == SECTION) {
                i++; // the code character itself is never visible
                continue;
            }
            visible.append(c);
        }
        return visible.toString();
    }
}
