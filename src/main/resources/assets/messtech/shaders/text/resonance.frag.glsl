#version 120

#include "common.glsl"

// 共振声纹：30 根频谱柱随拍起伏，波形线横穿字形，节拍环从文字中心扩散。
const vec3 RS0 = vec3(0.027, 0.051, 0.086);
const vec3 RS1 = vec3(0.169, 0.847, 1.000);
const vec3 RS2 = vec3(1.000, 0.310, 0.784);
const vec3 RS3 = vec3(0.949, 1.000, 1.000);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : RS0;
    vec3 c1 = paletteOverridden ? palette[1] : RS1;
    vec3 c2 = paletteOverridden ? palette[2] : RS2;
    vec3 c3 = paletteOverridden ? palette[3] : RS3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;
    float ink = coverage(p * h);
    float xr = clamp(p.x / max(0.001, w), 0.0, 1.0);
    float yr = p.y;

    // 频谱柱：按列取噪声当电平。
    float idx = floor(xr * 30.0);
    float level = 0.28 + 0.72 * noise(vec2(idx * 0.83, time * 2.4 - idx * 0.09));
    float fill = smoothstep(1.0 - level - 0.015, 1.0 - level + 0.015, yr);
    float cap = exp(-abs(yr - (1.0 - level)) * 34.0) * 0.9;

    // 波形线。
    float wave = sin(p.x * 2.4 + time * 4.6 + sin(p.x * 0.66 + time * 1.6) * 2.4);
    float line = exp(-abs(yr - 0.52 - wave * 0.22) * 70.0);

    // 节拍环：按 7.8 的重音从中心扩散。
    float beat = pow(0.5 + 0.5 * sin(time * 7.8), 8.0);
    vec2 q = (p - vec2(w * 0.5, 0.5)) / vec2(w * 0.62, 0.95);
    float ringT = fract(time * 0.55);
    float ring = exp(-abs(length(q) - ringT * 1.5) * 16.0) * (1.0 - ringT) * 0.5;

    vec3 inkColor = mix(c0, c1, 0.35 + 0.55 * fill);
    inkColor = mix(inkColor, c2, pow(0.5 + 0.5 * sin(xr * 8.0 + time * 2.0), 6.0) * 0.6);
    inkColor = mix(inkColor, c3, clamp(cap + line * 0.8, 0.0, 1.0));

    vec4 layer = vec4(0.0);
    overRarity(layer, inkColor, ink);
    layer.rgb += c1 * textBloom(p) * (0.10 + beat * 0.28);
    layer.rgb += c2 * ring * (0.5 + beat * 0.5);
    layer.rgb += c3 * line * ink * 0.35;
    outputRarity(layer);
}
