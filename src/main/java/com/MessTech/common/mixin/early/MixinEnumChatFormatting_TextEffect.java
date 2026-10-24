package com.MessTech.common.mixin.early;

import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.MessTech.common.text.effect.EffectTextParser;
import com.MessTech.common.text.effect.TextEffects;

/** Drops effect declarations wherever vanilla explicitly asks for unformatted text. */
@Mixin(EnumChatFormatting.class)
public abstract class MixinEnumChatFormatting_TextEffect {

    @ModifyVariable(method = "getTextWithoutFormattingCodes", at = @At("HEAD"), argsOnly = true)
    private static String mt$stripEffects(String text) {
        return EffectTextParser.containsMarkers(text) ? TextEffects.plainText(text) : text;
    }
}
