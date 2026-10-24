package com.MessTech.client.text.compat;

import java.util.Objects;

import net.minecraft.client.gui.FontRenderer;

import com.MessTech.client.text.GlyphTextureMetrics;
import com.gtnewhorizons.angelica.client.font.FontProvider;
import com.gtnewhorizons.angelica.client.font.FontStrategist;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;

/**
 * Angelica hooks. Its batching font renderer collects glyphs into shared vertex buffers, so effect text has to pause
 * that batch, capture a mask of its own and draw through the ordinary GL state instead.
 * <p>
 * Every reference to an Angelica class sits inside a method Forge removes when Angelica is absent, so this class loads
 * without it and the callers fall back to plain text.
 */
public final class AngelicaTextAdapter {

    public static final String MODID = "angelica";

    private static FontSettings settings;

    private AngelicaTextAdapter() {}

    /** Immutable cache discriminator; native font settings only change through Angelica's config screen. */
    public record FontSettings(boolean custom, String primary, String fallback, int quality, float scale,
        int boldCopies, int shadowCopies, int aaMode, int aaStrength, boolean replacements,
        float unicodeShadowOffset) {}

    /** The current native font configuration, or {@code null} when Angelica is not installed. */
    public static FontSettings fontSettings() {
        return Loader.isModLoaded(MODID) ? loadFontSettings() : null;
    }

    @Optional.Method(modid = MODID)
    private static FontSettings loadFontSettings() {
        FontSettings cached = settings;
        if (cached != null && cached.custom() == FontConfig.enableCustomFont
            && Objects.equals(cached.primary(), FontConfig.customFontNamePrimary)
            && Objects.equals(cached.fallback(), FontConfig.customFontNameFallback)
            && cached.quality() == FontConfig.customFontQuality
            && cached.scale() == FontConfig.customFontScale
            && cached.boldCopies() == FontConfig.boldCopies
            && cached.shadowCopies() == FontConfig.shadowCopies
            && cached.aaMode() == FontConfig.fontAAMode
            && cached.aaStrength() == FontConfig.fontAAStrength
            && cached.replacements() == FontConfig.enableGlyphReplacements
            && cached.unicodeShadowOffset() == FontConfig.fontShadowOffsetUC) return cached;
        settings = new FontSettings(
            FontConfig.enableCustomFont,
            FontConfig.customFontNamePrimary,
            FontConfig.customFontNameFallback,
            FontConfig.customFontQuality,
            FontConfig.customFontScale,
            FontConfig.boldCopies,
            FontConfig.shadowCopies,
            FontConfig.fontAAMode,
            FontConfig.fontAAStrength,
            FontConfig.enableGlyphReplacements,
            FontConfig.fontShadowOffsetUC);
        return settings;
    }

    /** True while the font draws through a smooth, linearly filtered glyph atlas rather than the bitmap one. */
    public static boolean usesCustomFont(FontRenderer font) {
        return Loader.isModLoaded(MODID) && detectsCustomFont(font);
    }

    @Optional.Method(modid = MODID)
    private static boolean detectsCustomFont(FontRenderer font) {
        return FontConfig.enableCustomFont && font instanceof FontRendererAccessor accessor
            && accessor.angelica$getBatcher() != null
            && !accessor.angelica$getBatcher()
                .forceDefaults();
    }

    /** True while deferred world geometry would take the effect quads into the wrong buffer. */
    public static boolean shouldDeferEffects() {
        return Loader.isModLoaded(MODID) && hasPendingGeometry();
    }

    @Optional.Method(modid = MODID)
    private static boolean hasPendingGeometry() {
        return GLStateManager.isMainThread()
            && (TesrBatchRenderer.INSTANCE.hasPendingGeometry() || ModelPartBatcher.INSTANCE.isActive());
    }

    /**
     * Forces sharp glyph coverage for the duration of a bitmap-font mask capture: the mask wants the crisp atlas, not
     * the antialiasing the user configured for on-screen text.
     * <p>
     * This writes Angelica's config value around the draw instead of redirecting its read of the value: another mod
     * patching the same method would make whichever {@code @Redirect} applies second fail to find its target, while a
     * temporarily changed value is simply read by everyone. Angelica notices the change on its next flush and updates
     * its shader uniform (and its per-instance cache) itself.
     *
     * @param font the font about to rasterise the mask
     * @return the previous mode for {@link #endSharpMask(int)}, or -1 when nothing was changed
     */
    public static int beginSharpMask(FontRenderer font) {
        return Loader.isModLoaded(MODID) ? forceSharpMask(font) : -1;
    }

    public static void endSharpMask(int previous) {
        if (previous >= 0) restoreSharpMask(previous);
    }

    @Optional.Method(modid = MODID)
    private static int forceSharpMask(FontRenderer font) {
        if (usesCustomFont(font)) return -1;
        int previous = FontConfig.fontAAMode;
        FontConfig.fontAAMode = 0;
        return previous;
    }

    @Optional.Method(modid = MODID)
    private static void restoreSharpMask(int previous) {
        FontConfig.fontAAMode = previous;
    }

    public static void beginForeignDraw() {
        if (Loader.isModLoaded(MODID)) beginAngelicaDraw();
    }

    public static void endForeignDraw() {
        if (Loader.isModLoaded(MODID)) endAngelicaDraw();
    }

    @Optional.Method(modid = MODID)
    private static void beginAngelicaDraw() {
        GLStateManager.beginForeignDraw();
    }

    @Optional.Method(modid = MODID)
    private static void endAngelicaDraw() {
        GLStateManager.endForeignDraw();
    }

    /** The pause handle of this font instance, or {@code null} when it has none to pause. */
    public static FontBatchBridge bridge(FontRenderer font) {
        return Loader.isModLoaded(MODID) ? angelicaBridge(font) : null;
    }

    @Optional.Method(modid = MODID)
    private static FontBatchBridge angelicaBridge(FontRenderer font) {
        if (font instanceof FontRendererAccessor accessor
            && accessor.angelica$getBatcher() instanceof FontBatchBridge batch) return batch;
        return null;
    }

    /** False only for an Angelica font renderer that has no batch to pause. */
    public static boolean supportsEffects(FontRenderer font) {
        return !Loader.isModLoaded(MODID) || bridge(font) != null;
    }

    /** Where the glyph's rows sit in the current atlas, or {@code null} for the bitmap fallback. */
    public static GlyphTextureMetrics glyphTextureMetrics(FontRenderer font, char character) {
        return Loader.isModLoaded(MODID) ? angelicaGlyphTextureMetrics(font, character) : null;
    }

    @Optional.Method(modid = MODID)
    private static GlyphTextureMetrics angelicaGlyphTextureMetrics(FontRenderer font, char character) {
        if (!(font instanceof FontRendererAccessor accessor) || accessor.angelica$getBatcher() == null) return null;
        FontProvider provider = FontStrategist.getFontProvider(
            accessor.angelica$getBatcher(),
            character,
            FontConfig.enableCustomFont,
            font.getUnicodeFlag());
        return new GlyphTextureMetrics(provider.getVStart(character), provider.getVSize(character));
    }
}
