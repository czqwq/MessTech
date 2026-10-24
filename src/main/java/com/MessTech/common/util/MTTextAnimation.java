package com.MessTech.common.util;

/**
 * One animated look for a stretch of tooltip text: per frame it answers "what does the line look like now", the
 * contract of the {@code GTAuthors} names and the "PigTech" line, so an animation can go to any tooltip text
 * consumer.
 * <p>
 * The <b>renderer</b> drawn over the finished line - needs a {@code FontRenderer} and OpenGL, so it cannot live in
 * common code - is registered client-side per animation instance, see
 * {@link MTAnimatedTooltipHandler#registerRenderer}. Without one the animation is pure formatting (colour codes and
 * spacing), as {@link MTPigTechText} is. {@link MTTranscendentMetalText} is the built-in {@code TRANSCENDENT_METAL}
 * look.
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
     * The visible characters of a string, i.e. the text without its formatting codes. Used to hand an animation the
     * text a supplier produces (such as {@code author_czqwq()}, which brings its own colour codes) and to recognise
     * an animated line again while it is being drawn.
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
