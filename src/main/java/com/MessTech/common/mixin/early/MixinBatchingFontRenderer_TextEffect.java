package com.MessTech.common.mixin.early;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.client.text.DeferredTextEffects;
import com.MessTech.client.text.EffectTextRenderer;
import com.MessTech.client.text.compat.FontBatchBridge;
import com.MessTech.common.text.effect.EffectTextParser;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;

/**
 * Angelica's font renderer batches glyphs into shared buffers. Effect text has to pause that batch before it can own
 * the render target, and the coverage mask needs straight alpha compositing so bold copies accumulate instead of
 * erasing each other's strokes.
 * <p>
 * This is an <b>early</b> mixin on purpose: Angelica's coremod constructs the font renderer during launch, so a late
 * config would find the target class already loaded. The class lives in the early config's client list; without
 * Angelica (or on a dedicated server) the target is never loaded and the mixin stays inactive.
 * <p>
 * Only injection kinds that compose with another mod patching the same methods are used here: HEAD/RETURN injections
 * and one {@code @ModifyArg}. The sharp mask coverage is forced by {@code AngelicaTextAdapter} writing the config
 * value for the duration of a capture instead of a {@code @Redirect} on the same field read, because two mods
 * redirecting one instruction would make whichever applies second fail to find its target.
 */
@Mixin(value = BatchingFontRenderer.class, remap = false)
public abstract class MixinBatchingFontRenderer_TextEffect implements FontBatchBridge {

    @Shadow
    protected FontRenderer underlying;

    @Shadow
    private int batchDepth;

    @Shadow
    private static BatchingFontRenderer arenaOwner;

    @Shadow
    private void flushBatch() {}

    @Shadow
    public static void flushDeferredText() {}

    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true)
    private void mt$directEffectText(float anchorX, float anchorY, int inputColor, boolean enableShadow,
        boolean unicodeFlag, CharSequence string, int stringOffset, int stringLength,
        CallbackInfoReturnable<Float> cir) {
        if (EffectTextRenderer.isBypassingEffects() || string == null) return;
        int start = Math.max(0, Math.min(stringOffset, string.length()));
        int end = start + Math.max(0, Math.min(stringLength, string.length() - start));
        if (!EffectTextParser.containsMarkers(string, start, end)) return;
        String text = start == 0 && end == string.length() ? string.toString()
            : string.subSequence(start, end)
                .toString();
        boolean previousUnicode = underlying.getUnicodeFlag();
        underlying.setUnicodeFlag(unicodeFlag);
        try {
            EffectTextRenderer.INSTANCE.draw(underlying, text, anchorX, anchorY, inputColor, enableShadow);
            cir.setReturnValue(
                anchorX + EffectTextRenderer.INSTANCE.layout(underlying, text)
                    .width() + (enableShadow ? ((BatchingFontRenderer) (Object) this).getShadowOffset() : 0));
        } finally {
            underlying.setUnicodeFlag(previousUnicode);
        }
    }

    @Override
    public int mt$suspendBatch() {
        flushDeferredText();
        if (arenaOwner instanceof FontBatchBridge owner) owner.mt$flushBatch();
        flushBatch();
        int depth = batchDepth;
        batchDepth = 0;
        return depth;
    }

    @Override
    public void mt$resumeBatch(int depth) {
        batchDepth = depth;
    }

    @Override
    public void mt$flushBatch() {
        flushBatch();
    }

    @Inject(method = "flushDeferredText", at = @At("RETURN"))
    private static void mt$drawDeferredEffects(CallbackInfo ci) {
        DeferredTextEffects.flush();
    }

    @Inject(method = "shouldDeferNow", at = @At("HEAD"), cancellable = true)
    private static void mt$immediateMask(CallbackInfoReturnable<Boolean> cir) {
        if (EffectTextRenderer.isCapturing()) cir.setReturnValue(false);
    }

    @Inject(method = "shouldDrawThroughPipeline", at = @At("HEAD"), cancellable = true)
    private void mt$localMaskTarget(CallbackInfoReturnable<Boolean> cir) {
        if (EffectTextRenderer.isCapturing()) cir.setReturnValue(false);
    }

    @ModifyArg(
        method = "setupFontDrawState",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/glsm/GLSMBlend;tryBlendFuncSeparate(IIII)V",
            ordinal = 0),
        index = 3,
        remap = false,
        require = 1)
    private int mt$compositeMaskCoverage(int destinationAlpha) {
        // Bold glyph copies must add coverage rather than erase the translucent edges of the previous one. Multiple
        // @ModifyArg handlers on one call compose, so another mod's own tweak here is kept.
        // Angelica 2.2.42 (2.9.0) split GLStateManager into the GLSM* wrappers: the same call is now
        // GLSMBlend.tryBlendFuncSeparate(...), so the target is the new owner class.
        return EffectTextRenderer.isCapturing() ? GL11.GL_ONE_MINUS_SRC_ALPHA : destinationAlpha;
    }
}
