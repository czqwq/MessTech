package com.MessTech.common.mixin.late;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.MessTech.common.text.effect.EffectTextParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import codechicken.nei.FormattedTextField;

/** Keeps editable effect declarations in NEI's text field and reuses the source-aware vanilla input renderer. */
@Mixin(value = FormattedTextField.class, remap = false)
public abstract class MixinFormattedTextField_TextEffect extends GuiTextField {

    public MixinFormattedTextField_TextEffect(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @WrapOperation(
        method = "setText",
        remap = true,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/EnumChatFormatting;getTextWithoutFormattingCodes(Ljava/lang/String;)Ljava/lang/String;",
            remap = true))
    private String mt$preserveEditableEffects(String text, Operation<String> original) {
        return EffectTextParser.containsMarkers(text) ? text : original.call(text);
    }

    @Inject(method = "drawTextBox", remap = true, at = @At("HEAD"), cancellable = true)
    private void mt$drawEffectInput(CallbackInfo ci) {
        if (EffectTextParser.containsMarkers(getText())) {
            super.drawTextBox();
            ci.cancel();
        }
    }
}
