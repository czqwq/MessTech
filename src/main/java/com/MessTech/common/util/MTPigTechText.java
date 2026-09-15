package com.MessTech.common.util;

/**
 * The "PigTech" brand line: the static "Add by:" prefix the MessTech line uses, followed by one two second loop in
 * which the word winds up, charges, slams into an invisible elastic wall, is thrown back, lands and settles again.
 * <p>
 * Everything is expressed in the <b>string</b> that a {@code Supplier<String>} hands to gtnhlib's
 * {@code AnimatedTooltipHandler}, which re-evaluates it once per frame - the same mechanism the "Add by: MessTech"
 * line and the animated author names in {@code GTAuthors} use. The prefix ({@code messTech.addBy}, "添加模组:" /
 * "Add by:") is copied into the line verbatim by {@link #frame(String, String, long)}, so it is byte for byte
 * identical in every frame and only the word after it animates.
 * <p>
 * A tooltip line is laid out glyph by glyph at a fixed height, so the animation uses the four handles that medium
 * really has:
 * <ul>
 * <li><b>Spaces.</b> Every space is a fixed advance, so the gaps between the letters can be pulled apart ("stretch")
 * or pushed into the margins ("squash"), and the whole word can travel sideways ("charge"). Every frame uses the same
 * glyphs and the same number of spaces, so the line - and therefore the tooltip box - never changes width, and the
 * word stays inside its own band of {@code letters - 1 + 2} spaces. That band keeps {@code MIN_MARGIN} space of
 * margin on each side in every frame, and {@link #frame(String, String, long)} puts {@link #PREFIX_SEPARATOR} in front
 * of it as well, so however hard the pig squashes there are always at least two spaces (~8 px) between the ":" of the
 * prefix and the nearest glyph of the animation. The brief's "no more than 20% of the type size" is one space of
 * travel (~10% of the word).</li>
 * <li><b>{@code §l} bold and {@code §o} italic.</b> The impact frames go bold - fat glyphs read as a squash - and the
 * charge leans forward. Neither changes the advance, so the width stays fixed.</li>
 * <li><b>Colour.</b> A four entry pig palette (pink, white, gold, white) steps once every 100 ms. 2000 / 100 is 20
 * steps and the palette divides 20, so the colour band is back where it started when the loop restarts. Everything
 * that has to appear and disappear (the two snout puffs and the ears) keeps its slot in the string and is painted
 * black while it is "gone" - black text on the dark tooltip background is invisible.</li>
 * <li><b>One line only.</b> There is no vertical axis: a tooltip line cannot be moved up or down, so the jump is told
 * by the squash and stretch of the letters, the ear twitch and the landing flash instead of by moving the word. This
 * line is deliberately a single line - no ground bar and no second tooltip line under it - so all the pig's weight
 * lands in the letters themselves. The ears are the two {@code ^} glyphs, which sit on the top row of the font, and
 * the snout puffs are {@code .}, which sit on the bottom row.</li>
 * </ul>
 * Only plain ASCII glyphs are used: the built-in font texture is a CP437 layout, so a code point such as {@code °}
 * would render as a shade block instead of a degree sign, while {@code ^ .} look the same in both of the game's
 * fonts.
 * <p>
 * The class is deliberately plain Java - no Minecraft imports - so the animation can be compiled and inspected on its
 * own, see {@code tmp/pigtext}. {@link #frame(String, String, long)} is all the game needs.
 * <p>
 * The timeline follows the brief (all times in seconds): wind up {@code 0.00-0.40}, charge and impact
 * {@code 0.40-0.70}, recoil and jump {@code 0.70-1.10}, landing with two bounces {@code 1.10-1.40}, then settling
 * back to the exact frame 0 pose {@code 1.40-2.00}, so {@code frame(prefix, text, t)} always equals
 * {@code frame(prefix, text, t + 2000)}.
 */
public final class MTPigTechText {

    /** Duration of one loop in milliseconds. */
    public static final long LOOP_MS = 2_000L;

    /**
     * The one space {@link #frame(String, String, long)} puts between a static prefix and the animation, the same
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

    /** Brightness ramp for the parts that fade in and out. Index 0 is invisible on the dark tooltip background. */
    private static final String[] FADE_CODES = { BLACK, DARK_GREY, GREY, WHITE };

    /** Glyphs: ears on the top row of the font, tiny dots for the snout puffs. */
    private static final char EAR = '^', DOT = '.', SPACE = ' ';

    /** How far the word may travel sideways, in spaces. */
    private static final double MAX_SHIFT = 1.0D;

    /**
     * Spaces of margin the animated band keeps on each side in every frame, so neither the prefix on the left nor the
     * edge of the tooltip on the right can ever be touched - not even in the frames that squash and push hardest.
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
     * @param prefix the static text in front of the animation, normally the translated {@code messTech.addBy}; it is
     *               copied verbatim into every frame, an empty or {@code null} prefix leaves it out
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
     * The whole timeline as functions of the time inside the loop.
     * <p>
     * The word starts and ends at its rest pose: normal spacing, no travel and no effects, so frame 2000 is frame 0
     * again.
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
     * Piecewise smooth curve through {@code time, value} pairs, with one pair per two arguments.
     * <p>
     * Outside the given range the first (or last) value is held, which is what keeps the pose at both ends of the
     * loop identical.
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
