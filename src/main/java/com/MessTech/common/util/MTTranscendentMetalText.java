package com.MessTech.common.util;

import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.BOLD;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.DARK_GRAY;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.GRAY;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.WHITE;

/**
 * The {@code TRANSCENDENT_METAL} text animation: GT5U's Transcendent Metal look for a whole stretch of text, a flat
 * quad turning about the oblique axis (0.3, 0.5, 0.2) by 3.5 degrees per client tick (see GT5U's
 * {@code TranscendentMetalRenderer} and {@link MTTranscendentMetalTextRenderer}, which turns the tooltip line the same
 * way). A string cannot turn, so this class supplies the other half: a dark metal band (dark grey - grey - white -
 * grey) travelling along the text, one colour per character, so the letters read as polished metal while the renderer
 * turns them. One character per {@link #COLOUR_STEP_MS}, and the four entry palette is a whole number of steps long,
 * so a frame repeats only once the band has walked the text and wrapped around.
 */
public final class MTTranscendentMetalText implements MTTextAnimation {

    /** The one animation instance; it is stateless, so every registration can share it. */
    public static final MTTranscendentMetalText INSTANCE = new MTTranscendentMetalText();

    /** How far one client tick turns the tumbler: the number of GT5U's {@code TranscendentMetalRenderer}. */
    public static final float ROTATION_PER_TICK = 3.5F;

    /**
     * The oblique axis GT5U turns its quad about - the same numbers, in the order the item renderer passes to
     * {@code glRotatef}. The text turns about it too, with the same angle and clock.
     */
    public static final float ROTATION_AXIS_X = 0.3F, ROTATION_AXIS_Y = 0.5F, ROTATION_AXIS_Z = 0.2F;

    /** Milliseconds per colour step: the band advances one character at a time. */
    private static final long COLOUR_STEP_MS = 90L;

    /** The band, from the shadow of the metal to its highlight and back. */
    private static final String DARK = DARK_GRAY + BOLD, MID = GRAY + BOLD, BRIGHT = WHITE + BOLD;
    private static final String[] METAL_BAND = { DARK, MID, BRIGHT, MID };

    private MTTranscendentMetalText() {}

    /**
     * The angle the plate has turned by, straight from GT5U's animation: the item sweeps <code>ticks * 3.5</code>
     * degrees and so does the text, one way round about {@link #ROTATION_AXIS_X the same oblique axis}, passing
     * edge-on (invisible) once per revolution.
     *
     * @param animationTicks the value of {@code GTMod.clientProxy().getAnimationRenderTicks()}, the clock the GT5U
     *                       item renderers animate with
     * @return the angle in degrees, 0 (facing the viewer) to 360
     */
    public static float spinFor(float animationTicks) {
        double swept = animationTicks * (double) ROTATION_PER_TICK;
        return (float) (swept % 360.0D);
    }

    /**
     * @param angle the angle of {@link #spinFor}
     * @return true while the plate shows its back, i.e. while the letters would read mirrored without being flipped
     *         over. Turning about {@code (x, y, z)} leaves the z part of the plate's normal at
     *         {@code cos(a) + z^2 / (x^2 + y^2 + z^2) * (1 - cos(a))}, negative exactly while the plate faces away; the
     *         switch is where it reaches zero - edge-on, no visible area - so it is never seen.
     */
    public static boolean showsBack(float angle) {
        double axisSquared = ROTATION_AXIS_X * ROTATION_AXIS_X + ROTATION_AXIS_Y * ROTATION_AXIS_Y
            + ROTATION_AXIS_Z * ROTATION_AXIS_Z;
        double zFraction = ROTATION_AXIS_Z * ROTATION_AXIS_Z / axisSquared;
        double cosine = Math.cos(Math.toRadians(angle));
        return cosine + zFraction * (1.0D - cosine) < 0.0D;
    }

    /**
     * @param source the text to wear the metal look; its own colour codes are dropped, the look replaces them
     * @param millis any clock in milliseconds; the band is a function of it alone
     * @return the text with one metal colour per character
     */
    @Override
    public String frame(String source, long millis) {
        String text = MTTextAnimation.visibleText(source);
        if (text.isEmpty()) return "";

        int offset = (int) (Math.floorDiv(millis, COLOUR_STEP_MS) % METAL_BAND.length);
        StringBuilder line = new StringBuilder(text.length() * 4);
        for (int i = 0; i < text.length(); i++) {
            line.append(METAL_BAND[Math.floorMod(i - offset, METAL_BAND.length)])
                .append(text.charAt(i));
        }
        return line.toString();
    }
}
