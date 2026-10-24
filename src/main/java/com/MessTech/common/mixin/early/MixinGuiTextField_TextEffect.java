package com.MessTech.common.mixin.early;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.MessTech.client.text.EffectTextFieldView;
import com.MessTech.client.text.EffectTextLayout.Layout;
import com.MessTech.client.text.EffectTextRenderer;
import com.MessTech.common.text.effect.EffectTextParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Keeps effect declarations intact while the vanilla text field scrolls and draws either side of its cursor. */
@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField_TextEffect {

    @Shadow
    @Final
    private FontRenderer field_146211_a;

    @Shadow
    private String text;

    @Shadow
    private int lineScrollOffset;

    @Shadow
    private int cursorPosition;

    @Unique
    private EffectTextFieldView mt$effectView;

    @Unique
    private EffectTextFieldView mt$view() {
        if (!EffectTextParser.containsMarkers(text)) {
            mt$effectView = null;
            return null;
        }
        Layout layout = EffectTextRenderer.INSTANCE.layout(field_146211_a, text);
        if (mt$effectView == null || !mt$effectView.matches(text, layout))
            mt$effectView = new EffectTextFieldView(text, layout);
        return mt$effectView;
    }

    @WrapOperation(
        method = { "drawTextBox", "mouseClicked", "setSelectionPos" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;"))
    private String mt$trimVisibleRange(FontRenderer font, String value, int width, Operation<String> original) {
        EffectTextFieldView view = mt$view();
        return view == null ? original.call(font, value, width)
            : view.trim(lineScrollOffset, lineScrollOffset + value.length(), width, false);
    }

    @WrapOperation(
        method = "setSelectionPos",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;"))
    private String mt$trimScrollRange(FontRenderer font, String value, int width, boolean reverse,
        Operation<String> original) {
        EffectTextFieldView view = mt$view();
        return view == null ? original.call(font, value, width, reverse) : view.trim(0, value.length(), width, reverse);
    }

    @WrapOperation(
        method = "drawTextBox",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 0))
    private int mt$drawBeforeCursor(FontRenderer font, String value, int x, int y, int color,
        Operation<Integer> original) {
        EffectTextFieldView view = mt$view();
        return original.call(
            font,
            view == null ? value : view.slice(lineScrollOffset, lineScrollOffset + value.length()),
            x,
            y,
            color);
    }

    @WrapOperation(
        method = "drawTextBox",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 1))
    private int mt$drawAfterCursor(FontRenderer font, String value, int x, int y, int color,
        Operation<Integer> original) {
        EffectTextFieldView view = mt$view();
        return original.call(
            font,
            view == null ? value : view.slice(cursorPosition, cursorPosition + value.length()),
            x,
            y,
            color);
    }

    @WrapOperation(
        method = "drawTextBox",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I"))
    private int mt$measureSelection(FontRenderer font, String value, Operation<Integer> original) {
        EffectTextFieldView view = mt$view();
        return view == null ? original.call(font, value)
            : view.width(lineScrollOffset, lineScrollOffset + value.length());
    }
}
