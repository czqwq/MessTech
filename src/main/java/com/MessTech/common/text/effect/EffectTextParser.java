package com.MessTech.common.text.effect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Scans effect declarations and native formatting without depending on client classes.
 * <p>
 * The marker alphabet is deliberately disjoint from other marker-using mods: U+2064 and a square-bracket inline form
 * here, where the other renderer uses U+2063 and a brace form. Each renderer then claims exactly its own text, so two
 * effect systems can coexist on the same kind of string. Keep the two inline openers and the scoped markers in sync
 * with {@link TextEffectFormat}.
 */
public final class EffectTextParser {

    public static final String OPEN = "\u2064[mt:";
    public static final String CLOSE = "\u2064[/mt]\u2064";
    public static final String END = "]\u2064";
    /** A literal marker character in visible text is doubled, exactly like a native formatting escape. */
    public static final String ESCAPE = "\u2064\u2064";

    private EffectTextParser() {}

    public static boolean containsMarkers(String text) {
        if (text == null) return false;
        return text.contains(OPEN) || text.contains(CLOSE)
            || text.contains(TextEffectFormat.INLINE_OPEN)
            || text.contains(TextEffectFormat.AMP_INLINE_OPEN);
    }

    public static boolean containsMarkers(CharSequence text, int start, int end) {
        if (text instanceof String value && start == 0 && end == value.length()) return containsMarkers(value);
        for (int i = start; i < end; i++) {
            char character = text.charAt(i);
            if (character == '\u2064') {
                if (startsWith(text, i, end, OPEN) || startsWith(text, i, end, CLOSE)) return true;
            } else if ((character == '\u00a7' || character == '&') && i + 1 < end && text.charAt(i + 1) == '[') {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(CharSequence text, int offset, int end, String prefix) {
        if (offset + prefix.length() > end) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (text.charAt(offset + i) != prefix.charAt(i)) return false;
        }
        return true;
    }

    public static Parsed parse(String text) {
        if (text == null || text.isEmpty()) return new Parsed("", List.of());
        if (!containsMarkers(text)) return new Parsed(text, List.of(new Run(text, null)));
        List<Run> runs = new ArrayList<>();
        StringBuilder plain = new StringBuilder(text.length());
        StringBuilder run = new StringBuilder();
        Cursor cursor = new Cursor(text);
        TextEffectStyle style = null;
        for (Token token; (token = cursor.next()) != null;) {
            if (!Objects.equals(style, token.style())) flush(runs, run, style);
            style = token.style();
            run.append(token.text());
            // The plain projection answers getTextWithoutFormattingCodes: native codes go, only visible text stays.
            if (!token.formattingCode()) plain.append(token.text());
        }
        flush(runs, run, style);
        return new Parsed(plain.toString(), List.copyOf(runs));
    }

    private static void flush(List<Run> runs, StringBuilder text, TextEffectStyle style) {
        if (text.length() == 0) return;
        runs.add(new Run(text.toString(), style));
        text.setLength(0);
    }

    private static int headerEnd(String text, int start, String terminator) {
        int limit = Math.min(text.length() - terminator.length(), start + TextEffectFormat.MAX_HEADER_LENGTH);
        for (int i = start; i <= limit; i++) {
            if (text.startsWith(terminator, i)) return i;
        }
        return -1;
    }

    /** Source-aware scanner shared by rendering and string operations. */
    public static final class Cursor {

        private final String source;
        private final UnaryOperator<String> preprocessor;
        private final boolean hexResetsStyles;
        private final Predicate<String> rendererAvailable;
        private final Deque<Scope> scopes = new ArrayDeque<>();
        private TextEffectStyle style;
        private String formatting = "";
        private int position;

        public Cursor(String source) {
            this(source, UnaryOperator.identity(), false);
        }

        public Cursor(String source, UnaryOperator<String> preprocessor, boolean hexResetsStyles) {
            this(source, preprocessor, hexResetsStyles, identifier -> true);
        }

        public Cursor(String source, UnaryOperator<String> preprocessor, boolean hexResetsStyles,
            Predicate<String> rendererAvailable) {
            this.source = Objects.requireNonNull(source, "source");
            this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
            this.hexResetsStyles = hexResetsStyles;
            this.rendererAvailable = Objects.requireNonNull(rendererAvailable, "rendererAvailable");
        }

        public Token next() {
            while (position < source.length()) {
                int start = position;
                if (source.startsWith(ESCAPE, start)) {
                    position += 2;
                    return new Token("\u2064", style, formatting, start, position, false);
                }
                if (source.startsWith(CLOSE, start)) {
                    if (!scopes.isEmpty()) style = scopes.pop()
                        .saved();
                    position += CLOSE.length();
                    continue;
                }
                boolean scoped = source.startsWith(OPEN, start);
                boolean inline = source.startsWith(TextEffectFormat.INLINE_OPEN, start)
                    || source.startsWith(TextEffectFormat.AMP_INLINE_OPEN, start);
                if (scoped || inline) {
                    String prefix = scoped ? OPEN : TextEffectFormat.INLINE_OPEN;
                    String terminator = scoped ? END : "]";
                    int end = headerEnd(source, start + prefix.length(), terminator);
                    String header = end < 0 ? "" : source.substring(start + prefix.length(), end);
                    TextEffectStyle declared = end < 0 ? null
                        : scoped ? readStyle(header) : TextEffectFormat.readInline(header);
                    if (declared != null && (!inline || rendererAvailable.test(declared.rendererId()))) {
                        if (scoped) scopes.push(new Scope(style, declared));
                        style = declared;
                        position = end + terminator.length();
                        continue;
                    }
                }
                int length = TextEffectFormat.nativeLength(source, start);
                String value;
                if (length > 0) {
                    position += length;
                    value = source.substring(start, position);
                } else {
                    int candidateLength = preprocessorCandidateLength(start);
                    String original = source.substring(start, start + candidateLength);
                    value = TextEffectFormat.normalizeAmpersand(original);
                    if (value.equals(original) && candidateLength > 1) value = preprocessor.apply(original);
                    if (value.equals(original)) {
                        candidateLength = Character.charCount(source.codePointAt(start));
                        value = source.substring(start, start + candidateLength);
                    }
                    position += candidateLength;
                    length = TextEffectFormat.nativeLength(value, 0);
                }
                boolean nativeCode = length > 0 && length == value.length();
                if (nativeCode) {
                    style = TextEffectFormat.applyColor(style, value);
                    formatting = TextEffectFormat.applyFormatting(formatting, value, hexResetsStyles);
                }
                return new Token(value, style, formatting, start, position, nativeCode);
            }
            return null;
        }

        /** Restores the active native formatting and every open effect scope after a source split. */
        public String continuation() {
            StringBuilder result = new StringBuilder("\u00a7r").append(formatting);
            Iterator<Scope> iterator = scopes.descendingIterator();
            Scope scope = iterator.hasNext() ? iterator.next() : null;
            TextEffectStyle root = scope == null ? style : scope.saved();
            if (root != null) result.append(TextEffects.format(root));
            while (scope != null) {
                Scope next = iterator.hasNext() ? iterator.next() : null;
                TextEffectStyle active = next == null ? style : next.saved();
                result.append(TextEffects.opening(active == null ? scope.opened() : active));
                if (active == null) result.append("\u00a7r")
                    .append(formatting);
                scope = next;
            }
            return result.toString();
        }

        private int preprocessorCandidateLength(int start) {
            char character = source.charAt(start);
            if (character == '\\' && start + 1 < source.length() && source.charAt(start + 1) == '&') return 2;
            if (character != '&' || start + 1 >= source.length()) return Character.charCount(source.codePointAt(start));
            char code = Character.toLowerCase(source.charAt(start + 1));
            if (code == 'g' && ampHex(start + 2) && ampHex(start + 10)) return 18;
            if (code == 'u' && ampHex(start + 2)) return 10;
            if (ampHex(start)) return 8;
            if (code == 'x' && ampHexPairs(start)) return 14;
            return 2;
        }

        private boolean ampHexPairs(int start) {
            if (start + 14 > source.length()) return false;
            for (int i = start + 2; i < start + 14; i += 2) {
                if (source.charAt(i) != '&' || Character.digit(source.charAt(i + 1), 16) < 0) return false;
            }
            return true;
        }

        private boolean ampHex(int start) {
            if (start + 8 > source.length() || source.charAt(start) != '&' || source.charAt(start + 1) != '#')
                return false;
            for (int i = start + 2; i < start + 8; i++) {
                if (Character.digit(source.charAt(i), 16) < 0) return false;
            }
            return true;
        }
    }

    private record Scope(TextEffectStyle saved, TextEffectStyle opened) {}

    public record Token(String text, TextEffectStyle style, String formatting, int start, int end,
        boolean formattingCode) {}

    /** Scoped header syntax: {@code name;speed;color,color}. */
    private static TextEffectStyle readStyle(String header) {
        String[] fields = header.split(";", -1);
        if (fields.length != 3) return null;
        try {
            List<Integer> colors = new ArrayList<>();
            if (!fields[2].isEmpty()) {
                String[] values = fields[2].split(",", -1);
                if (values.length > 8) return null;
                for (String value : values) {
                    if (value.isEmpty() || value.length() > 6) return null;
                    colors.add(Integer.parseInt(value, 16));
                }
            }
            return new TextEffectStyle(fields[0], colors, Float.parseFloat(fields[1]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record Run(String text, TextEffectStyle style) {}

    public record Parsed(String plainText, List<Run> runs) {}
}
