#version 120

#include "common.glsl"

// 墨韵流金：双层噪声墨晕，覆盖率梯度当笔锋，外圈洇边，墨上飘金箔并有一道流金扫光。
const vec3 IB0 = vec3(0.160, 0.120, 0.090);
const vec3 IB1 = vec3(0.450, 0.330, 0.220);
const vec3 IB2 = vec3(0.950, 0.740, 0.360);
const vec3 IB3 = vec3(1.000, 0.970, 0.900);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : IB0;
    vec3 c1 = paletteOverridden ? palette[1] : IB1;
    vec3 c2 = paletteOverridden ? palette[2] : IB2;
    vec3 c3 = paletteOverridden ? palette[3] : IB3;

    float h = max(1.0, textSize.y);
    vec2 p = position() / h;
    float ink = coverage(p * h);

    // 笔画内部的墨晕。
    float wash = noise(p * 1.2 + vec2(time * 0.05, time * 0.08)) * 0.7
        + noise(p * 3.3 - vec2(time * 0.04, time * 0.03)) * 0.3;

    // 覆盖率梯度就是笔锋。
    float gx = coverage((p + vec2(0.025, 0.0)) * h) - coverage((p - vec2(0.025, 0.0)) * h);
    float gy = coverage((p + vec2(0.0, 0.025)) * h) - coverage((p - vec2(0.0, 0.025)) * h);
    float rim = clamp(length(vec2(gx, gy)) * 0.5, 0.0, 1.0);

    float bleed = clamp(outline(p * h, 0.05 * h) - ink, 0.0, 1.0) * (0.35 + 0.65 * noise(p * 3.6 + time * 0.1));
    float halo = clamp(outline(p * h, 0.14 * h) - ink, 0.0, 1.0);

    float topLight = 1.0 - smoothstep(0.05, 0.55, p.y);
    vec3 inkColor = mix(c0 * 1.25, c1, 0.25 + 0.45 * wash);
    inkColor = mix(inkColor, c2, rim * 0.55);
    inkColor = mix(inkColor, c3, topLight * 0.06);
    inkColor *= 0.90 + 0.25 * wash;

    vec4 layer = vec4(0.0);
    // 先压一层暗晕，暖墨才从底板上立起来。
    overRarity(layer, vec3(0.035, 0.024, 0.020), clamp(halo * 0.8, 0.0, 1.0));
    layer.rgb += c1 * bleed * 0.30;
    layer.rgb += c2 * halo * 0.10;
    overRarity(layer, inkColor, ink);

    // 墨上漂的金箔。
    vec2 g = p / 0.30 + vec2(time * 0.05, time * 0.035);
    vec2 gid = floor(g);
    vec2 gf = fract(g) - 0.5;
    float pick = step(0.88, hash(gid));
    vec2 flake = turn(gf, -hash(gid + 7.3) * 6.2831853);
    float shape = 1.0 - smoothstep(0.14, 0.24, max(abs(flake.x), abs(flake.y) * 1.8));
    float twinkle = 0.55 + 0.45 * sin(time * 3.2 + hash(gid) * 6.2831853);
    float region = clamp(ink * 0.9 + bleed * 0.8, 0.0, 1.0);
    layer.rgb += c2 * pick * shape * twinkle * region * 1.25;
    layer.rgb += c3 * pick * shape * shape * region * 0.30;

    // 横向流金扫光。
    float sweep = pow(0.5 + 0.5 * sin(p.x * 1.05 - time * 1.3), 20.0);
    overRarity(layer, mix(c2, c3, 0.35), ink * sweep * 0.85);
    layer.rgb += c2 * textBloom(p) * 0.10;
    outputRarity(layer);
}
