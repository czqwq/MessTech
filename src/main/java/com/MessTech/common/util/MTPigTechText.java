package com.MessTech.common.util;

/**
 * The "PigTech" brand line: the static "Add by:" prefix the MessTech line uses, then one two second loop in which
 * the word winds up, charges, slams into an invisible elastic wall, is thrown back, lands and settles again.
 * <p>
 * All of it is the <b>string</b> a {@code Supplier<String>} hands to gtnhlib's {@code AnimatedTooltipHandler}, which
 * re-evaluates it once per frame - the mechanism of the "Add by: MessTech" line and the {@code GTAuthors} names. The
 * prefix ({@code messTech.addBy}, "添加模组:" / "Add by:") is copied in verbatim by
 * {@link #frame(String, String, long)}; only the word after it animates.
 * <p>
 * A tooltip line is laid out glyph by glyph at a fixed height, so there are four handles:
 * <ul>
 * <li><b>Spaces.</b> A fixed advance: gaps pulled apart ("stretch"), pushed into the margins ("squash"), the word
 * moved sideways ("charge"). Same glyphs and same space count in every frame, so neither the line nor the tooltip box
 * changes width and the word stays in its band of {@code letters - 1 + 2} spaces - {@code MIN_MARGIN} on each side
 * plus {@link #PREFIX_SEPARATOR} - leaving at least two spaces (~8 px) between the prefix's ":" and the nearest
 * glyph. Travel budget from the brief: one space, ~10% of the word.</li>
 * <li><b>{@code §l} bold, {@code §o} italic.</b> Impact frames go bold (fat glyphs read as a squash), the charge
 * leans forward; neither changes the advance.</li>
 * <li><b>Colour.</b> A four entry pig palette (pink, white, gold, white), 100 ms a step: 2000 / 100 = 20 steps, which
 * the palette divides, so the loop restarts in its frame 0 colours. Parts that vanish (the two snout puffs, the ears)
 * keep their slot and are painted black - invisible on the dark tooltip background.</li>
 * <li><b>One line only.</b> No vertical axis, so the jump is told by squash and stretch, the ear twitch and the
 * landing flash; deliberately no ground bar and no second line. Ears are the two {@code ^} (top row of the font), the
 * puffs {@code .} (bottom row).</li>
 * </ul>
 * ASCII only: the built-in font is CP437, so {@code °} would render as a shade block, while {@code ^ .} look the same
 * in both of the game's fonts. Plain Java, no Minecraft imports, so it compiles and can be inspected on its own (see
 * {@code tmp/pigtext}); {@link #frame(String, String, long)} is all the game needs.
 * <p>
 * Timeline (seconds): wind up {@code 0.00-0.40}, charge and impact {@code 0.40-0.70}, recoil and jump
 * {@code 0.70-1.10}, landing with two bounces {@code 1.10-1.40}, settling back to the frame 0 pose
 * {@code 1.40-2.00}, so {@code frame(prefix, text, t)} always equals {@code frame(prefix, text, t + 2000)}.
 */
public final class MTPigTechText {

    /** Duration of one loop in milliseconds. */
    public static final long LOOP_MS = 2_000L;

    /**
     * The one space {@link #frame(String, String, long)} puts between a static prefix and the animation - the same
     * separator the MessTech line uses after {@code messTech.addBy}.
     */
    public static final String PREFIX_SEPARATOR = " ";

    /** Milliseconds per colour step: 2000 / 100 = 20 steps, and the four entry palette divides 20. */
    private static final long COLOUR_STEP_MS = 100L;

    /** Formatting codes; the section sign is escaped so the source file stays pure ASCII. */
    private static final String BLACK = "\u00a70", DARK_GREY = "\u00a78", GREY = "\u00a77", WHITE = "\u00a7f",
        PINK = "\u00a7d", GOLD = "\u00a7e", BOLD = "\u00a7l", ITALIC = "\u00a7o";

    /** The band the letters cycle through: pig pink, white, gold, white. */
    private static final String[] FLOW = { PINK, WHITE, GOLD, WHITE };

    /** Brightness ramp for the parts that fade in and out; index 0 is invisible on the dark tooltip background. */
    private static final String[] FADE_CODES = { BLACK, DARK_GREY, GREY, WHITE };

    /** Glyphs: ears on the top row of the font, tiny dots for the snout puffs. */
    private static final char EAR = '^', DOT = '.', SPACE = ' ';

    /** How far the word may travel sideways, in spaces. */
    private static final double MAX_SHIFT = 1.0D;

    /**
     * Spaces of margin the animated band keeps on each side in every frame, so neither the prefix on the left nor the
     * tooltip edge on the right can be touched, not even in the frames that squash hardest.
     */
    private static final int MIN_MARGIN = 1;

    private MTPigTechText() {}

    /**
     * Renders one frame of the animation without a prefix.
     *
     * @param text   the string to animate, normally {@code "PigTech"}; the layout adapts to any length
     * @param millis any clock in milliseconds, normally {@code System.currentTimeMillis()}
     * @return that frame, as one tooltip line
     */
    public static String frame(String text, long millis) {
        return frame("", text, millis);
    }

    /**
     * Renders one frame of the animation behind a static prefix.
     *
     * @param prefix the static text in front, normally the translated {@code messTech.addBy}; copied verbatim into
     *               every frame, an empty or {@code null} prefix leaves it out
     * @param text   the string to animate, normally {@code "PigTech"}; the layout adapts to any length
     * @param millis any clock in milliseconds, normally {@code System.currentTimeMillis()}
     * @return that frame, as one tooltip line
     */
    public static String frame(String prefix, String text, long millis) {
        String head = prefix == null ? "" : prefix;
        String word = text == null ? "" : text;
        long phase = Math.floorMod(millis, LOOP_MS);
        Pose pose = poseAt(phase / 1000.0D);

        StringBuilder line = new StringBuilder(head.length() + word.length() * 4 + 16);
        if (!head.isEmpty()) {
            line.append(head)
                .append(PREFIX_SEPARATOR);
        }
        return line.append(wordLine(pose, word, phase))
            .toString();
    }

    /** @return the number of spaces a line of the given text length always keeps, i.e. one margin per side. */
    private static int spaceBudget(int letters) {
        return Math.max(0, letters - 1) + 2;
    }

    /** The word: ears, letters (with the stretch spaces spread between them) and the two snout puffs. */
    private static String wordLine(Pose pose, String word, long millis) {
        int letters = word.length();
        int slots = spaceBudget(letters);
        int gaps = (int) Math.round(pose.stretch * Math.max(0, letters - 1));
        gaps = (int) clamp(gaps, 0, Math.max(0, slots - 2));
        int free = slots - gaps;
        int low = Math.min(MIN_MARGIN, free);
        int lead = (int) clamp(Math.round(free / 2.0D + pose.shift), low, Math.max(low, free - MIN_MARGIN));
        int trail = free - lead;

        StringBuilder line = new StringBuilder(letters * 4 + slots + 8);
        spaces(line, lead);
        line.append(fade(pose.ear))
            .append(EAR);
        for (int i = 0; i < letters; i++) {
            if (i > 0 && hasGap(i, letters, gaps)) line.append(SPACE);
            // The style codes go after the colour: in Minecraft a colour code clears bold and italic.
            line.append(letterColour(pose, millis, i, letters));
            if (pose.italic) line.append(ITALIC);
            if (pose.bold) line.append(BOLD);
            line.append(word.charAt(i));
        }
        line.append(fade(pose.puffA))
            .append(DOT);
        line.append(fade(pose.puffB))
            .append(DOT);
        line.append(fade(pose.ear))
            .append(EAR);
        spaces(line, trail);
        return line.toString();
    }

    /** @return true when the gap in front of letter {@code index} is one of the {@code gaps} stretch spaces. */
    private static boolean hasGap(int index, int letters, int gaps) {
        if (gaps <= 0 || letters < 2) return false;
        int before = (index - 1) * gaps / (letters - 1);
        int after = index * gaps / (letters - 1);
        return after > before;
    }

    /** The colour of one letter: the flowing palette, dimmed into a trail while the pig charges, white on impact. */
    private static String letterColour(Pose pose, long millis, int index, int letters) {
        if (pose.flash > 0.5D) return WHITE;
        String colour = FLOW[(int) ((millis / COLOUR_STEP_MS + index) % FLOW.length)];
        // The smear trails the leading edge, so the first letters drag behind the word that is rushing away from them.
        if (pose.blur > 0.05D && index < Math.max(1, letters / 2)) return fade(1.0D - 0.34D * pose.blur);
        return colour;
    }

    /** @return a brightening colour code, 0 = invisible black on the dark tooltip background, 1 = white. */
    private static String fade(double level) {
        int index = (int) Math.round(clamp(level, 0, 1) * (FADE_CODES.length - 1));
        return FADE_CODES[index];
    }

    private static void spaces(StringBuilder line, int count) {
        for (int i = 0; i < count; i++) line.append(SPACE);
    }

    /** The animation state of one frame; every level runs 0..1 unless stated otherwise. */
    private static final class Pose {

        /** 0 = the letters sit at their normal spacing, 1 = all the gaps between them are open. */
        double stretch;
        /** Sideways travel in spaces, -1..1. */
        double shift;
        /** 0..1 white impact flash. */
        double flash;
        /** 0..1 trail smear on the leading letters. */
        double blur;
        /** The two snout puffs, 0 = gone, 1 = bright white. */
        double puffA, puffB;
        /** The ear twitch. */
        double ear;
        /** Heavy (impact or loaded) glyphs. */
        boolean bold;
        /** Leaning into the charge. */
        boolean italic;
    }

    /**
     * The whole timeline as functions of the time inside the loop. The word starts and ends at its rest pose - normal
     * spacing, no travel, no effects - so frame 2000 is frame 0 again.
     */
    private static Pose poseAt(double t) {
        Pose pose = new Pose();

        // 0.00-0.40 wind up: press down, wobble left and right twice, then shiver impatiently.
        pose.stretch = curve(
            t,
            0.00,
            0.00D,
            0.40,
            0.00D,
            // 0.40-0.70 charge: lunge forward and stretch out, then slam into the elastic wall and jam shut.
            0.50,
            1.00D,
            0.52,
            0.95D,
            0.56,
            0.00D,
            // 0.70-1.10 recoil and jump: spring back, lift off (stretched) and fall again.
            0.70,
            0.45D,
            0.84,
            0.30D,
            0.98,
            0.55D,
            1.08,
            0.20D,
            // 1.10-1.40 landing: squash, bounce, bounce, settle.
            1.125,
            0.00D,
            1.20,
            0.50D,
            1.27,
            0.08D,
            1.30,
            0.30D,
            1.36,
            0.00D,
            2.00,
            0.00D);
        pose.shift = curve(
            t,
            0.00,
            0.00D,
            0.40,
            0.00D,
            0.50,
            1.00D,
            0.56,
            1.00D,
            0.63,
            -0.35D,
            0.70,
            -0.15D,
            0.78,
            0.12D,
            0.84,
            0.00D,
            2.00,
            0.00D);
        if (t < 0.40D) {
            // Two side to side swings, then the "impatient" shiver just before the charge.
            pose.shift += 0.75D * Math.sin(2 * Math.PI * 2 * ramp(t, 0.06D, 0.32D));
            if (t > 0.34D) pose.shift += 0.60D * Math.sin(2 * Math.PI * 11 * (t - 0.34D));
            pose.shift = clamp(pose.shift, -MAX_SHIFT, MAX_SHIFT);
        }

        // Impacts and effects are pulses: they rise, peak and fall back to exactly nothing.
        pose.flash = pulse(t, 0.52D, 0.535D, 0.60D) + pulse(t, 1.10D, 1.115D, 1.16D);
        pose.blur = pulse(t, 0.40D, 0.47D, 0.58D);
        pose.puffA = pulse(t, 0.47D, 0.53D, 0.63D);
        pose.puffB = pulse(t, 0.52D, 0.585D, 0.70D);
        pose.ear = pulse(t, 0.86D, 0.885D, 0.915D) + pulse(t, 0.93D, 0.95D, 0.98D);

        // 1.40-2.00 settle: one slow breath (colour and puffs only, spacing steps are too coarse to breathe with).
        if (t >= 1.40D) {
            double breath = 0.5D - 0.5D * Math.cos(2 * Math.PI * (t - 1.40D) / 0.60D);
            pose.puffA = 0.25D * breath;
            pose.puffB = 0.25D * breath;
        }

        pose.bold = curve(
            t,
            0.28,
            0.00D,
            0.32,
            1.00D,
            0.40,
            1.00D,
            0.402,
            0.00D,
            // the impact frame only: bold arrives with the wall, not with the run up
            0.50,
            0.00D,
            0.52,
            1.00D,
            0.58,
            1.00D,
            0.582,
            0.00D,
            1.10,
            1.00D,
            1.145,
            1.00D,
            1.15,
            0.00D,
            1.26,
            1.00D,
            1.275,
            0.00D,
            2.00,
            0.00D) > 0.5D;
        pose.italic = curve(t, 0.26, 0.00D, 0.32, 1.00D, 0.58, 1.00D, 0.62, 0.00D, 2.00, 0.00D) > 0.5D;
        return pose;
    }

    /**
     * Piecewise smooth curve through {@code time, value} pairs, one pair per two arguments. Outside the range the
     * first (or last) value is held, which keeps the pose at both ends of the loop identical.
     */
    private static double curve(double t, double... points) {
        int last = points.length - 2;
        if (t <= points[0]) return points[1];
        if (t >= points[last]) return points[last + 1];
        for (int i = 0; i < last; i += 2) {
            double from = points[i], to = points[i + 2];
            if (t > to) continue;
            return lerp(points[i + 1], points[i + 3], smooth(ramp(t, from, to)));
        }
        return points[last + 1];
    }

    /** A single rise and fall between {@code start} and {@code end}, peaking at {@code peak}. */
    private static double pulse(double t, double start, double peak, double end) {
        if (t <= start || t >= end) return 0.0D;
        if (t < peak) return smooth(ramp(t, start, peak));
        return smooth(1.0D - ramp(t, peak, end));
    }

    private static double ramp(double t, double from, double to) {
        if (to <= from) return t < from ? 0.0D : 1.0D;
        return clamp((t - from) / (to - from), 0.0D, 1.0D);
    }

    private static double smooth(double u) {
        double x = clamp(u, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double lerp(double from, double to, double u) {
        return from + (to - from) * u;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
