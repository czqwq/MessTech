package com.MessTech.common.util;

import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.AQUA;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.BOLD;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.DARK_AQUA;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.DARK_GRAY;
import static com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.WHITE;

/**
 * The {@code MODULE_PROJECT} text animation: a rack of modules being powered up, module by module.
 * <p>
 * The word is read as a row of modules of {@link #MODULE_SIZE} characters each ("Mod|ule|Pro|jec|t"), i.e. the same
 * grouping the chip row of {@link MTModuleProjectTextRenderer} draws underneath it. One loop powers the rack up from
 * the left - the module under the scan is bright, the ones already loaded keep flickering like a data bus - then
 * flashes at full load and lets the modules go dark again, left to right:
 * <ul>
 * <li><b>power up</b> {@code 0..}{@link #POWER_UP_MS}: the scan walks the rack, one module at a time;</li>
 * <li><b>full load</b> {@link #POWER_UP_MS}..{@link #POWER_UP_MS}+{@link #FLASH_MS}+{@link #HOLD_MS}: every module
 * is lit, with two quick white blinks at the start of the phase;</li>
 * <li><b>power down</b> the rest of the loop: the modules drop out from the left, so the loop ends with a dark rack
 * and starts over.</li>
 * </ul>
 * The animation is a pure function of the clock, which is what lets the renderer compute exactly the same phase from
 * the same millisecond the tooltip was drawn with.
 * <p>
 * Everything below is a string: the colour per character, one entry per glyph, so the line never changes width (bold
 * and colour codes do not move the advance). The drawing half - the chip row, the scan line and the brackets - is
 * the client-only {@link MTModuleProjectTextRenderer}.
 */
public final class MTModuleProjectText implements MTTextAnimation {

    /** The one animation instance; it is stateless, so every registration can share it. */
    public static final MTModuleProjectText INSTANCE = new MTModuleProjectText();

    /** How many glyphs one module of the rack covers. */
    public static final int MODULE_SIZE = 3;

    /** Milliseconds the scan takes to walk the rack from the first module to the last. */
    public static final long POWER_UP_MS = 900L;

    /** Milliseconds of the full load flash: two quick blinks, then the rack settles. */
    public static final long FLASH_MS = 250L;

    /** Milliseconds one blink of the flash lasts. */
    private static final long BLINK_MS = 60L;

    /** Milliseconds the fully lit rack is held after the flash. */
    public static final long HOLD_MS = 150L;

    /** Milliseconds the rack takes to go dark again, left to right. */
    public static final long POWER_DOWN_MS = 300L;

    /** Duration of one loop in milliseconds: power up, flash, hold, power down. */
    public static final long LOOP_MS = POWER_UP_MS + FLASH_MS + HOLD_MS + POWER_DOWN_MS;

    /** Milliseconds per data step: how often the flicker of a loaded module changes. */
    private static final long DATA_STEP_MS = 70L;

    /** A module that is not powered (yet): dark, but still readable on the tooltip background. */
    private static final String OFF = DARK_GRAY + BOLD;
    /** A loaded module: dark aqua, the "data is sitting in it" state. */
    private static final String LOADED = DARK_AQUA + BOLD;
    /** The module the scan is loading right now. */
    private static final String SCANNING = AQUA + BOLD;
    /** Full load: every module at once. */
    private static final String FULL = WHITE + BOLD;

    /**
     * How many of the data steps a loaded module spends in {@link #SCANNING} before it drops back to {@link #LOADED}.
     */
    private static final int DATA_FLICKER = 6;

    private MTModuleProjectText() {}

    /**
     * @param length the number of visible characters of the word
     * @return how many modules the rack has for that word, i.e. {@code ceil(length / MODULE_SIZE)}
     */
    public static int modules(int length) {
        if (length <= 0) return 0;
        return (length + MODULE_SIZE - 1) / MODULE_SIZE;
    }

    /**
     * How much of the rack is powered, as a fraction: this is the value the renderer lights its chips with, so the
     * chip row and the letters are always in step.
     *
     * @param millis any clock in milliseconds
     * @return 0 while the rack is dark, 1 while it is fully lit
     */
    public static double lit(long millis) {
        long phase = Math.floorMod(millis, LOOP_MS);
        if (phase < POWER_UP_MS) return (double) phase / POWER_UP_MS;
        if (phase < POWER_UP_MS + FLASH_MS + HOLD_MS) return 1.0D;

        long down = phase - (POWER_UP_MS + FLASH_MS + HOLD_MS);
        return Math.max(0.0D, 1.0D - (double) down / POWER_DOWN_MS);
    }

    /**
     * The module the scan is loading.
     *
     * @param millis  any clock in milliseconds
     * @param modules the module count of {@link #modules(int)}
     * @return the index of that module, or -1 while the scan is not running (full load, or power down)
     */
    public static int head(long millis, int modules) {
        if (modules <= 0) return -1;

        long phase = Math.floorMod(millis, LOOP_MS);
        if (phase >= POWER_UP_MS) return -1;
        return Math.min((int) (lit(millis) * modules), modules - 1);
    }

    /**
     * @param millis any clock in milliseconds
     * @return true during the two blinks of the full load flash
     */
    public static boolean flashing(long millis) {
        long phase = Math.floorMod(millis, LOOP_MS);
        if (phase < POWER_UP_MS || phase >= POWER_UP_MS + FLASH_MS) return false;
        return (phase - POWER_UP_MS) / BLINK_MS % 2 == 0;
    }

    /**
     * @param source the word to power up; the prefix of the brand line is not passed in here, it is copied by
     *               {@code MTAnimatedTooltipHandler#addAnimatedText}
     * @param millis any clock in milliseconds
     * @return the word with one colour per character, see the class comment for the timeline
     */
    @Override
    public String frame(String source, long millis) {
        String text = MTTextAnimation.visibleText(source);
        if (text.isEmpty()) return "";

        long phase = Math.floorMod(millis, LOOP_MS);
        int modules = modules(text.length());
        double lit = lit(phase);
        int head = head(phase, modules);
        boolean flash = flashing(phase);
        int loaded = (int) Math.round(lit * modules);

        StringBuilder line = new StringBuilder(text.length() * 6);
        for (int i = 0; i < text.length(); i++) {
            int module = i / MODULE_SIZE;
            line.append(colour(phase, module, loaded, head, flash))
                .append(text.charAt(i));
        }
        return line.toString();
    }

    /** @return the colour code of one module, see the state constants above. */
    private static String colour(long phase, int module, int loaded, int head, boolean flash) {
        if (flash) return FULL;
        if (module >= loaded) return OFF;
        if (module == head) return SCANNING;
        // A loaded module flickers: the data bus of the rack keeps moving even while the scan is elsewhere.
        int step = Math.floorMod((int) (phase / DATA_STEP_MS) + module * 2, DATA_FLICKER);
        return step == 0 ? SCANNING : LOADED;
    }
}
