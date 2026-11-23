package com.MessTech.common.wireless.gui;

import java.util.function.IntSupplier;

import com.MessTech.common.wireless.MTWirelessColors;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;

/**
 * The shared Flux Networks 1.20 style kit of the wireless ME network GUIs (see {@code docs/wireless-me-network.md}
 * §9.4): panel geometry, palette, and the tintable background/frame/bar/icon drawables.
 *
 * <p>
 * Everything a GUI needs is exposed as a {@code static} method returning an {@link IDrawable}, so a caller can hand the
 * result to a widget's {@code background()}/{@code overlay()} or draw it manually. Drawables that carry a network color
 * are built on top of a single white texture and tinted with {@link UITexture#withColorOverride(int)}; a caller that
 * wants a color that changes at runtime (a sync value) wraps the call in a
 * {@link com.cleanroommc.modularui.drawable.DynamicDrawable} or uses {@link #dynamicPanel(IntSupplier)}.
 *
 * <p>
 * All sizes are the 1:1 Flux geometry: the content area is a {@value #CONTENT_SIZE}x{@value #CONTENT_SIZE} square, the
 * tab row is {@value #TAB_ROW_HEIGHT} px high above it, and the whole panel is {@value #PANEL_WIDTH}x
 * {@value #PANEL_HEIGHT}. The textures are stretched to the widget size; a caller that draws them at another size (the
 * 176 px wide picker popup) still gets the same rounded look.
 *
 * <p>
 * The three PNGs are copied from Flux Networks 1.20 (MIT License, Copyright (c) 2017-2021 SonarSonic, BloCamLimb) and
 * only used as pure client side textures, see §9.8 of the design document.
 */
public final class MTWirelessGuiStyle {

    // ------------------------------------------------------------------ geometry

    /** Size of the square content area, i.e. the inside of the rounded background and frame. */
    public static final int CONTENT_SIZE = 172;
    /** Height of the navigation tab row above the content area. */
    public static final int TAB_ROW_HEIGHT = 16;
    /** Panel width; the content area fills it completely. */
    public static final int PANEL_WIDTH = 172;
    /** Panel height: tab row (16) + 2 px gap + content area (172) + 4 px bottom margin. */
    public static final int PANEL_HEIGHT = 194;
    /** Panel-local y of the content area: widgets inside a page use coordinates relative to this point. */
    public static final int CONTENT_Y = TAB_ROW_HEIGHT + 2;

    /** Logical tab indices of the core GUI, in page order. */
    public static final int TAB_HOME = 0;
    public static final int TAB_SELECTION = 1;
    public static final int TAB_MEMBERS = 2;
    public static final int TAB_SETTINGS = 3;
    public static final int TAB_CREATE = 4;
    /** Number of tabs the core GUI shows. */
    public static final int TAB_COUNT = 5;

    /** Edge length of a tab icon, drawn at 16x16. */
    public static final int TAB_SIZE = 16;
    /** Horizontal distance between two packed tab icons (1 icon + 2 px gap). */
    public static final int TAB_SPACING = 18;
    /** Panel-local x of the first tab icon. */
    public static final int TAB_FIRST_X = 12;
    /** Panel-local x of the "create network" tab, which Flux keeps apart on the right. */
    public static final int TAB_CREATE_X = 140;

    /** Width of the network name bar on the home page. */
    public static final int NETWORK_BAR_WIDTH = 135;
    /** Height of the network name bar. */
    public static final int NETWORK_BAR_HEIGHT = 12;
    /** Width of one network/member list row bar. */
    public static final int ROW_BAR_WIDTH = 146;
    /** Height of one network/member list row bar. */
    public static final int ROW_BAR_HEIGHT = 12;
    /** Edge length of the lock icon drawn on a row. */
    public static final int LOCK_SIZE = 10;
    /** Edge length of one palette swatch. */
    public static final int SWATCH_SIZE = 10;

    // ------------------------------------------------------------------ sprite indices (gui_icon.png, Flux order)

    /**
     * Tab icon sprite indices in Flux {@code EnumNavigationTab} order: the order used by {@link #tabIcon(int, float)}.
     */
    public static final int SPRITE_HOME = 0, SPRITE_SELECTION = 1, SPRITE_WIRELESS = 2, SPRITE_CONNECTION = 3,
        SPRITE_STATISTICS = 4, SPRITE_MEMBERS = 5, SPRITE_SETTINGS = 6, SPRITE_CREATE = 7;

    /** Sprite index of every logical tab index ({@link #TAB_HOME} ... {@link #TAB_CREATE}). */
    private static final int[] TAB_SPRITES = { SPRITE_HOME, SPRITE_SELECTION, SPRITE_MEMBERS, SPRITE_SETTINGS,
        SPRITE_CREATE };

    // ------------------------------------------------------------------ colors

    /** The 14 Flux network colors ({@link MTWirelessColors#PALETTE}); treated as read only. */
    public static final int[] PALETTE = MTWirelessColors.PALETTE;
    /** The fallback network color, {@link MTWirelessColors#DEFAULT}. */
    public static final int DEFAULT_COLOR = MTWirelessColors.DEFAULT;

    /** Plain white text. */
    public static final int TEXT_COLOR = 0xFFFFFFFF;
    /** Dim grey text of a non-current list row. */
    public static final int TEXT_COLOR_DIM = 0xFF606060;
    /** Light grey text, used for the page headers. */
    public static final int TEXT_COLOR_LIGHT = 0xFFB4B4B4;

    /** Alpha applied to a palette swatch, matching Flux's semi transparent color buttons. */
    private static final int SWATCH_ALPHA = 0xAA000000;
    /** Base color of the dark panel background, used by {@link #panelBackgroundColor(int)}. */
    private static final int PANEL_BASE_COLOR = 0x101010;

    // ------------------------------------------------------------------ textures

    private static final UITexture BACKGROUND = UITexture.builder()
        .location("messtech", "gui/wireless/gui_background")
        .imageSize(688, 688)
        .fullImage()
        .nonOpaque()
        .build();

    private static final UITexture FRAME = UITexture.builder()
        .location("messtech", "gui/wireless/gui_frame")
        .imageSize(688, 688)
        .fullImage()
        .nonOpaque()
        .build();

    /** The eight 64x64 tab icon sprites, top left of the sprite row at y = 192. */
    private static final UITexture[] TAB_ICONS = new UITexture[8];

    private static final UITexture NETWORK_BAR = sprite(0, 320, 270, 24);
    private static final UITexture ROW_BAR = sprite(0, 352, 292, 24);
    private static final UITexture LOCK = sprite(384, 256, 64, 64);

    static {
        for (int index = 0; index < TAB_ICONS.length; index++) {
            TAB_ICONS[index] = sprite(64 * index, 192, 64, 64);
        }
    }

    /** A sprite of {@code gui_icon.png}, in the texture's own pixel coordinates. */
    private static UITexture sprite(int x, int y, int width, int height) {
        return UITexture.builder()
            .location("messtech", "gui/wireless/gui_icon")
            .imageSize(512, 512)
            .subAreaXYWH(x, y, width, height)
            .nonOpaque()
            .build();
    }

    private MTWirelessGuiStyle() {}

    // ------------------------------------------------------------------ panel background and frame

    /** Deep rounded panel background, stretched to the widget size (used at 172x172). */
    public static IDrawable background() {
        return BACKGROUND;
    }

    /**
     * White rounded frame ring stretched to the widget size, tinted with {@code rgb} (a {@code 0xRRGGBB} color; the
     * opaque alpha is added here).
     */
    public static IDrawable frame(int rgb) {
        return FRAME.withColorOverride(argb(rgb));
    }

    /** {@link #background()} plus {@link #frame(int)} stacked, i.e. the complete content area in one drawable. */
    public static IDrawable panel(int rgb) {
        return IDrawable.of(BACKGROUND, frame(rgb));
    }

    /** {@link #panel(int)} whose network color is read every frame from {@code rgb}. */
    public static IDrawable dynamicPanel(IntSupplier rgb) {
        if (rgb == null) return BACKGROUND;
        return new DynamicDrawable(() -> panel(rgb.getAsInt()));
    }

    // ------------------------------------------------------------------ bars and icons

    /** Network name bar (135x12), tinted with {@code rgb} ({@code 0xRRGGBB}). */
    public static IDrawable networkBar(int rgb) {
        return NETWORK_BAR.withColorOverride(argb(rgb));
    }

    /** Network/member list row bar (146x12), tinted with {@code rgb} ({@code 0xRRGGBB}). */
    public static IDrawable rowBar(int rgb) {
        return ROW_BAR.withColorOverride(argb(rgb));
    }

    /** White lock icon (10x10). */
    public static IDrawable lockIcon() {
        return LOCK;
    }

    /** Lock icon shaded with {@code shade} (1 = white, 0.7 = clickable but not selected, 0.5 = disabled). */
    public static IDrawable lockIcon(float shade) {
        return LOCK.withColorOverride(shadeColor(shade));
    }

    /**
     * Tab icon ({@code 16x16}) of the given sprite index in Flux {@code EnumNavigationTab} order, shaded with
     * {@code shade} (1 = selected/white, 0.7 = clickable but not selected, 0.5 = disabled).
     */
    public static IDrawable tabIcon(int tabIndex, float shade) {
        return TAB_ICONS[clampSprite(tabIndex)].withColorOverride(shadeColor(shade));
    }

    /** Sprite index of a logical tab index ({@link #TAB_HOME} ... {@link #TAB_CREATE}). */
    public static int tabSprite(int tabIndex) {
        return TAB_SPRITES[Math.max(0, Math.min(TAB_SPRITES.length - 1, tabIndex))];
    }

    // ------------------------------------------------------------------ palette

    /** A 10x10 color swatch of the 14 color palette, semi transparent like Flux's color buttons. */
    public static IDrawable paletteSwatch(int rgb) {
        return new Rectangle().color(SWATCH_ALPHA | (rgb & 0xFFFFFF));
    }

    /** A 10x10 color swatch with the white 1 px selection border Flux draws around the selected color. */
    public static IDrawable paletteSwatchSelected(int rgb) {
        return IDrawable.of(
            paletteSwatch(rgb),
            new Rectangle().hollow(1)
                .color(TEXT_COLOR));
    }

    // ------------------------------------------------------------------ buttons

    /** A 1 px outline button frame in the network color, stretched to the button size (Flux's {@code SimpleButton}). */
    public static IDrawable buttonFrame(int rgb) {
        return new Rectangle().hollow(1)
            .color(argb(rgb));
    }

    /** {@link #buttonFrame(int)} whose network color is read every frame from {@code rgb}. */
    public static IDrawable dynamicButtonFrame(IntSupplier rgb) {
        if (rgb == null) return new Rectangle().hollow(1)
            .color(TEXT_COLOR);
        return new DynamicDrawable(() -> buttonFrame(rgb.getAsInt()));
    }

    // ------------------------------------------------------------------ color helpers

    /** Adds the opaque alpha to a {@code 0xRRGGBB} color. */
    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** A grey {@code 0xRRGGBB} color of the given brightness, used for the tab/icon shading. */
    public static int shade(int shade) {
        return 0xFF000000 | (shade << 16) | (shade << 8) | shade;
    }

    /** {@link #shade(int)} as an ARGB color, for {@code shade} in 0..1. */
    public static int shadeColor(float shade) {
        float clamped = Math.max(0f, Math.min(1f, shade));
        return shade((int) (clamped * 255f + 0.5f));
    }

    /**
     * An opaque dark color derived from a network color, for decorations/text that must read against the dark panel:
     * the network color at 35% over the {@code 0x101010} panel base.
     */
    public static int panelBackgroundColor(int rgb) {
        int r = mix((rgb >> 16) & 0xFF, (PANEL_BASE_COLOR >> 16) & 0xFF);
        int g = mix((rgb >> 8) & 0xFF, (PANEL_BASE_COLOR >> 8) & 0xFF);
        int b = mix(rgb & 0xFF, PANEL_BASE_COLOR & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int networkChannel, int baseChannel) {
        return (int) (networkChannel * 0.35f + baseChannel * 0.65f + 0.5f);
    }

    private static int clampSprite(int tabIndex) {
        return Math.max(0, Math.min(TAB_ICONS.length - 1, tabIndex));
    }
}
