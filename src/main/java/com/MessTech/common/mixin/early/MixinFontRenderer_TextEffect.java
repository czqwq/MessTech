package com.MessTech.common.mixin.early;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.client.text.EffectTextLayout;
import com.MessTech.client.text.EffectTextRenderer;

/**
 * Routes marked strings through the effect renderer and keeps layout, trimming and wrapping in sync with the visible
 * glyphs. Priority 100 / order 900 puts these callbacks after the font replacements that overwrite the same methods.
 */
@Mixin(value = FontRenderer.class, priority = 100)
public abstract class MixinFontRenderer_TextEffect {

    // Font replacements may answer these overloads without ever reaching the vanilla implementation below them.
    @Inject(method = "drawString(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$drawEffectText(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (EffectTextRenderer.handles(text)) cir
            .setReturnValue(EffectTextRenderer.INSTANCE.draw((FontRenderer) (Object) this, text, x, y, color, false));
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$drawShadowedEffectText(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (EffectTextRenderer.handles(text))
            cir.setReturnValue(EffectTextRenderer.INSTANCE.draw((FontRenderer) (Object) this, text, x, y, color, true));
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$drawEffectText(String text, int x, int y, int color, boolean shadow,
        CallbackInfoReturnable<Integer> cir) {
        if (EffectTextRenderer.handles(text)) cir
            .setReturnValue(EffectTextRenderer.INSTANCE.draw((FontRenderer) (Object) this, text, x, y, color, shadow));
    }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$renderEffectText(String text, int x, int y, int color, boolean shadow,
        CallbackInfoReturnable<Integer> cir) {
        if (EffectTextRenderer.handles(text)) cir
            .setReturnValue(EffectTextRenderer.INSTANCE.draw((FontRenderer) (Object) this, text, x, y, color, shadow));
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$measureEffectText(String text, CallbackInfoReturnable<Integer> cir) {
        if (EffectTextRenderer.handles(text)) cir.setReturnValue(
            (int) Math.ceil(
                EffectTextRenderer.INSTANCE.layout((FontRenderer) (Object) this, text)
                    .width()));
    }

    @Inject(
        method = "trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;",
        at = @At("HEAD"),
        cancellable = true,
        order = 900)
    private void mt$trimEffectText(String text, int width, CallbackInfoReturnable<String> cir) {
        if (EffectTextRenderer.handles(text))
            cir.setReturnValue(EffectTextLayout.trim((FontRenderer) (Object) this, text, width, false));
    }

    @Inject(
        method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;",
        at = @At("HEAD"),
        cancellable = true,
        order = 900)
    private void mt$trimEffectText(String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        if (EffectTextRenderer.handles(text))
            cir.setReturnValue(EffectTextLayout.trim((FontRenderer) (Object) this, text, width, reverse));
    }

    @Inject(method = "sizeStringToWidth", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$effectSourceLength(String text, int width, CallbackInfoReturnable<Integer> cir) {
        if (EffectTextRenderer.handles(text))
            cir.setReturnValue(EffectTextLayout.sizeToWidth((FontRenderer) (Object) this, text, width));
    }

    @Inject(method = "getFormatFromString", at = @At("HEAD"), cancellable = true, order = 900)
    private static void mt$effectContinuation(String text, CallbackInfoReturnable<String> cir) {
        if (EffectTextRenderer.handles(text)) cir.setReturnValue(EffectTextLayout.continuation(text));
    }

    @Inject(method = "listFormattedStringToWidth", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$wrapEffectText(String text, int width, CallbackInfoReturnable<List<String>> cir) {
        if (EffectTextRenderer.handles(text))
            cir.setReturnValue(EffectTextLayout.wrap((FontRenderer) (Object) this, text, width));
    }

    @Inject(method = "wrapFormattedStringToWidth", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$wrapEffectString(String text, int width, CallbackInfoReturnable<String> cir) {
        if (EffectTextRenderer.handles(text))
            cir.setReturnValue(String.join("\n", EffectTextLayout.wrap((FontRenderer) (Object) this, text, width)));
    }

    @Inject(method = "drawSplitString", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$drawWrappedEffect(String text, int x, int y, int width, int color, CallbackInfo ci) {
        if (!EffectTextRenderer.handles(text)) return;
        FontRenderer font = (FontRenderer) (Object) this;
        float lineY = y;
        for (String line : EffectTextLayout.wrap(font, text, width)) {
            EffectTextRenderer.INSTANCE.draw(font, line, x, lineY, color, false);
            lineY += EffectTextLayout.fontHeight(font);
        }
        ci.cancel();
    }

    @Inject(method = "splitStringWidth", at = @At("HEAD"), cancellable = true, order = 900)
    private void mt$wrappedHeight(String text, int width, CallbackInfoReturnable<Integer> cir) {
        if (!EffectTextRenderer.handles(text)) return;
        FontRenderer font = (FontRenderer) (Object) this;
        cir.setReturnValue(
            (int) Math.ceil(
                EffectTextLayout.wrap(font, text, width)
                    .size() * EffectTextLayout.fontHeight(font)));
    }
}
