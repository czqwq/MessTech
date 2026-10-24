package com.MessTech.common.mixin.early;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.client.text.DeferredTextEffects;
import com.MessTech.client.text.EffectTextRenderer;
import com.MessTech.client.text.compat.AngelicaTextAdapter;
import com.MessTech.client.text.compat.FontBatchBridge;
import com.MessTech.common.text.effect.EffectTextParser;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

/**
 * Angelica's font renderer batches glyphs into shared buffers. Effect text has to pause that batch before it can own
 * the render target, and the coverage mask needs straight alpha compositing so bold copies accumulate instead of
 * erasing each other's strokes.
 * <p>
 * This is an <b>early</b> mixin on purpose: Angelica's coremod constructs the font renderer during launch, so a late
 * config would find the target class already loaded. The class lives in the early config's client list; without
 * Angelica (or on a dedicated server) the target is never loaded and the mixin simply stays inactive.
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
    @Final
    private int fontShaderId;

    @Shadow
    @Final
    private int AAMode;

    @Shadow
    private int fontAAModeLast;

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
            target = "Lcom/gtnewhorizons/angelica/glsm/GLStateManager;tryBlendFuncSeparate(IIII)V",
            ordinal = 0),
        index = 3,
        remap = false,
        require = 1)
    private int mt$compositeMaskCoverage(int destinationAlpha) {
        // Bold glyph copies must add coverage rather than erase the translucent edges of the previous one.
        return EffectTextRenderer.isCapturing() ? GL11.GL_ONE_MINUS_SRC_ALPHA : destinationAlpha;
    }

    @Redirect(
        method = "setupFontDrawState",
        at = @At(
            value = "FIELD",
            target = "Lcom/gtnewhorizons/angelica/config/FontConfig;fontAAMode:I",
            opcode = Opcodes.GETSTATIC),
        remap = false,
        require = 3)
    private int mt$sharpMaskCoverage() {
        // Keep the comparison, the cached mode and the uniform consistent across the three reads in that method.
        return EffectTextRenderer.isCapturing() && !AngelicaTextAdapter.usesCustomFont(underlying) ? 0
            : FontConfig.fontAAMode;
    }

    @Inject(method = "flushBatch", at = @At("RETURN"))
    private void mt$restoreFontAntialiasing(CallbackInfo ci) {
        int configuredMode = FontConfig.fontAAMode;
        if (!EffectTextRenderer.isCapturing() || fontAAModeLast == configuredMode) return;
        // The shader is shared by every font instance, while Angelica caches the mode per instance.
        int previousProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GLStateManager.glUseProgram(fontShaderId);
        try {
            GLStateManager.glUniform1i(AAMode, configuredMode);
            fontAAModeLast = configuredMode;
        } finally {
            GLStateManager.glUseProgram(previousProgram);
        }
    }
}
