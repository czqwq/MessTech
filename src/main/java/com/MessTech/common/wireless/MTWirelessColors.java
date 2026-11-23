package com.MessTech.common.wireless;

import java.util.Locale;

/**
 * Network colors of the wireless ME networks: the 14 colors of Flux Networks' {@code EnumNetworkColor} (same rgb
 * values, same order) plus the small helpers {@link MTWirelessSavedData} and the GUIs need.
 *
 * <p>
 * A color is a plain {@code 0xRRGGBB} int; only the low 24 bits are meaningful, so alpha is always opaque when the GUI
 * tints a texture with it.
 */
public final class MTWirelessColors {

    /** Default color of a network: Flux's BLUE. Used for legacy v1 records and for unbound cores. */
    public static final int DEFAULT = 0x295E8A;

    /** The 14 selectable network colors, in Flux Networks' order. */
    public static final int[] PALETTE = { 0x295E8A, 0x343477, 0x582A72, 0x882D60, 0xAA3939, 0xAA6F39, 0xC6B900,
        0x609732, 0x87CEFA, 0x86608E, 0xF08080, 0xFFC0CB, 0xFFDAB9, 0xEEDC82 };

    private MTWirelessColors() {}

    /** @return {@code rgb} reduced to the low 24 bits (the representation the saved data stores). */
    public static int sanitize(int rgb) {
        return rgb & 0xFFFFFF;
    }

    /** @return the {@code "RRGGBB"} spelling of {@code rgb}, for debug output and tests. */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", sanitize(rgb));
    }
}
