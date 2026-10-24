package com.MessTech.client.text;

import com.MessTech.client.text.TextMaskCache.Mask;
import com.MessTech.common.text.effect.TextEffectStyle;

public record TextRenderContext(Mask mask, TextEffectStyle style, float x, float y, int color, double seconds,
    boolean shadow) {}
