#version 120

#include "common.glsl"

// 事件视界：引力透镜把字形拉向核心，外面是吸积盘、光子环和绕环飞行的光子。
const vec3 EH0 = vec3(0.055, 0.025, 0.125);
const vec3 EH1 = vec3(1.000, 0.620, 0.180);
const vec3 EH2 = vec3(1.000, 0.953, 0.816);
const vec3 EH3 = vec3(0.541, 0.361, 1.000);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : EH0;
    vec3 c1 = paletteOverridden ? palette[1] : EH1;
    vec3 c2 = paletteOverridden ? palette[2] : EH2;
    vec3 c3 = paletteOverridden ? palette[3] : EH3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;
    vec2 center = vec2(w * 0.5, 0.5);
    vec2 rel = p - center;

    // 归一化椭圆：吸积盘所在的平面。
    vec2 q = rel / vec2(w * 0.5 + 0.35, 0.62);
    float r = length(q);
    float ang = atan(q.y, q.x);

    // 引力透镜：字形向核心偏移。
    vec2 pull = -normalize(rel + vec2(0.001, 0.0)) * (0.07 / (1.0 + r * r * 5.0));
    float ink = max(coverage(p * h), coverage((p + pull) * h) * 0.9);

    // 吸积盘：环上的螺旋条纹。
    float spiral = sin(ang * 3.0 - log(max(r, 0.05)) * 6.0 + time * 2.4);
    float disk = exp(-abs(r - 1.02) * 6.0) * pow(0.5 + 0.5 * spiral, 3.0);
    float edge = exp(-abs(r - 1.02) * 26.0) * 0.8;
    float glow = exp(-abs(r - 1.02) * 2.2) * 0.18;

    // 光子环贴着字形外缘，外面再一层光晕。
    float ring = clamp(outline(p * h, 0.035 * h) - ink, 0.0, 1.0);
    float halo = clamp(outline(p * h, 0.10 * h) - ink, 0.0, 1.0);
    float abyss = 1.0 - smoothstep(0.35, 0.95, r);

    // 沿环追逐的光子。
    float photons = 0.0;
    for (int i = 0; i < 3; i++) {
        float a = time * 1.6 + float(i) * 2.094;
        vec2 spot = center + vec2(cos(a) * (w * 0.5 + 0.30), sin(a) * 0.64);
        photons += exp(-dot(p - spot, p - spot) * 34.0);
    }

    vec4 layer = vec4(0.0);
    layer.rgb += c1 * (disk * 1.1 + edge * 0.6) * (1.0 - 0.65 * ink);
    layer.rgb += c3 * glow * (1.0 - ink);
    layer.rgb += c2 * photons * 0.9;

    vec3 inkColor = mix(c3 * 0.75, c0, 0.42 + 0.40 * abyss);
    overRarity(layer, inkColor, ink * 0.94);
    overRarity(layer, mix(c2, c1, 0.35), ring * 0.95);
    overRarity(layer, c1 * 0.55, halo * 0.35);
    layer.rgb += c2 * ring * 0.5;
    outputRarity(layer);
}
