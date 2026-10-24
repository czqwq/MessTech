package com.MessTech.common.text.effect;

import java.util.List;
import java.util.Objects;

/** Public helpers for building and reading effect-decorated text. */
public final class TextEffects {

    public static final TextEffectStyle DATAFALL = preset("datafall");
    public static final TextEffectStyle EVENTHORIZON = preset("eventhorizon");
    public static final TextEffectStyle INKBLOOM = preset("inkbloom");
    public static final TextEffectStyle MAGNETAR = preset("magnetar");
    public static final TextEffectStyle RESONANCE = preset("resonance");
    public static final TextEffectStyle BEAMCUT = preset("beamcut");
    public static final TextEffectStyle CIPHER = preset("cipher");
    public static final TextEffectStyle PETALFALL = preset("petalfall");

    private TextEffects() {}

    private static TextEffectStyle preset(String name) {
        return new TextEffectStyle(TextEffectFormat.NAMESPACE + ":" + name, List.of(), 1);
    }

    /**
     * Opens an inline effect until the next declaration or {@code §r}. Example:
     * {@code §[datafall;colors=#22E0C0;speed=1.2]text§r}. The compact form accepts aliases and short names:
     * {@code &[df]}, {@code &[df;#22E0C0;2]}.
     */
    public static String format(TextEffectStyle style) {
        Objects.requireNonNull(style, "style");
        String id = style.rendererId();
        String namespace = TextEffectFormat.NAMESPACE + ":";
        if (id.startsWith(namespace)) id = id.substring(namespace.length());
        StringBuilder result = new StringBuilder(TextEffectFormat.INLINE_OPEN).append(id);
        if (!style.colors()
            .isEmpty()) {
            result.append(";colors=");
            for (int i = 0; i < style.colors()
                .size(); i++) {
                if (i > 0) result.append(',');
                String hex = Integer.toHexString(
                    style.colors()
                        .get(i));
                result.append('#')
                    .append("000000", 0, 6 - hex.length())
                    .append(hex);
            }
        }
        if (style.speed() != 1) result.append(";speed=")
            .append(style.speed());
        return result.append(']')
            .toString();
    }

    public static String apply(String text, TextEffectStyle style) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        return text.isEmpty() ? text : opening(style) + text + EffectTextParser.CLOSE;
    }

    public static String opening(TextEffectStyle style) {
        StringBuilder result = new StringBuilder(EffectTextParser.OPEN).append(style.rendererId())
            .append(';')
            .append(style.speed())
            .append(';');
        for (int i = 0; i < style.colors()
            .size(); i++) {
            if (i > 0) result.append(',');
            result.append(
                Integer.toHexString(
                    style.colors()
                        .get(i)));
        }
        return result.append(EffectTextParser.END)
            .toString();
    }

    public static String escapeLiteral(String text) {
        return Objects.requireNonNull(text, "text")
            .replace("\u2064", EffectTextParser.ESCAPE);
    }

    public static String plainText(String text) {
        return EffectTextParser.parse(text)
            .plainText();
    }
}
