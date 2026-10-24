package com.MessTech.client.text.effect;

import net.minecraft.util.ResourceLocation;

import com.MessTech.client.text.TextEffectRegistry;
import com.MessTech.common.text.effect.TextEffectStyle;
import com.MessTech.common.text.effect.TextEffects;

/** The eight built-in shaders and their default palettes. */
public final class BuiltinTextEffects {

    private BuiltinTextEffects() {}

    public static void register() {
        register(TextEffects.DATAFALL, 0x050E13, 0x25EDCC, 0xE0FFFA, 0x4785FF);
        register(TextEffects.EVENTHORIZON, 0x0E0620, 0xFF9E2E, 0xFFF3D0, 0x8A5CFF);
        register(TextEffects.INKBLOOM, 0x291F17, 0x735438, 0xF2BD5C, 0xFFF7E6);
        register(TextEffects.MAGNETAR, 0x07131C, 0x5EF2FF, 0xE8FFFF, 0x3A6BFF);
        register(TextEffects.RESONANCE, 0x070D16, 0x2BD8FF, 0xFF4FC8, 0xF2FFFF);
        register(TextEffects.BEAMCUT, 0x0A0E16, 0x4F73A3, 0xFFF6D8, 0xFF6A1A);
        register(TextEffects.CIPHER, 0x03120B, 0x35E07A, 0xE6FFEF, 0xFFB020);
        register(TextEffects.PETALFALL, 0x4A1030, 0xFF8FA3, 0xFFD7B0, 0xB06BFF);
    }

    private static void register(TextEffectStyle style, int... palette) {
        ResourceLocation id = new ResourceLocation(style.rendererId());
        TextEffectRegistry.register(
            style.rendererId(),
            new ShaderTextEffect(
                new ResourceLocation(id.getResourceDomain(), "shaders/text/" + id.getResourcePath() + ".frag.glsl"),
                palette));
    }
}
