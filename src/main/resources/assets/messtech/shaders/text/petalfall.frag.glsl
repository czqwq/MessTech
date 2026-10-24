#version 120

#include "common.glsl"

// 落英缤纷：字形是流动的粉色（两层正弦相位 + 移动高光），前后各一组花瓣绕着文字旋转下落。
const vec3 PF0 = vec3(0.290, 0.063, 0.188);
const vec3 PF1 = vec3(1.000, 0.561, 0.639);
const vec3 PF2 = vec3(1.000, 0.843, 0.690);
const vec3 PF3 = vec3(0.690, 0.420, 1.000);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : PF0;
    vec3 c1 = paletteOverridden ? palette[1] : PF1;
    vec3 c2 = paletteOverridden ? palette[2] : PF2;
    vec3 c3 = paletteOverridden ? palette[3] : PF3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;
    float ink = coverage(p * h);
    float breath = 0.5 + 0.5 * sin(time * 1.1);

    // 动态粉：玫瑰 / 桃红 / 浅粉之间漂移，再叠一层移动高光。
    float flowA = 0.5 + 0.5 * sin(time * 1.35 + p.x * 1.15 + p.y * 1.7);
    float flowB = 0.5 + 0.5 * sin(time * 2.20 - p.x * 0.90 + p.y * 0.80);
    vec3 base = mix(vec3(0.92, 0.24, 0.44), vec3(1.00, 0.56, 0.73), flowA);
    base = mix(base, vec3(1.00, 0.80, 0.88), flowB * 0.35);
    base = mix(base, c3, 0.10 * flowB);
    base *= 0.85 + 0.25 * flowB;
    float sheen = pow(0.5 + 0.5 * sin(time * 1.6 - p.x * 1.35 + p.y * 0.9), 6.0);
    base = mix(base, vec3(1.00, 0.93, 0.96), sheen * 0.35);
    base = mix(base, c2, breath * 0.10);

    vec4 layer = vec4(0.0);
    // 字后面的花瓣。
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float ph = fract(time * (0.12 + 0.10 * hash(vec2(fi, 2.0))) + hash(vec2(fi, 3.0)));
        vec2 center = vec2(hash(vec2(fi, 4.0)) * w + sin(time * 0.9 + fi * 2.1) * 0.18, -0.15 + ph * 1.5);
        vec2 d = turn(p - center, -time * (0.6 + 0.8 * hash(vec2(fi, 5.0))));
        float shape = 1.0 - smoothstep(0.85, 1.25, length(vec2(d.x / 0.16, d.y / 0.075)));
        overRarity(layer, mix(c1, c3, 0.55), shape * (0.35 + 0.35 * ph) * 0.30);
    }
    overRarity(layer, base, ink);
    // 字前面的花瓣。
    for (int i = 0; i < 6; i++) {
        float fi = float(i) + 7.0;
        float ph = fract(time * (0.15 + 0.12 * hash(vec2(fi, 2.0))) + hash(vec2(fi, 3.0)));
        vec2 center = vec2(hash(vec2(fi, 4.0)) * w + sin(time * 0.7 + fi * 1.7) * 0.22, -0.12 + ph * 1.5);
        vec2 d = turn(p - center, -time * (0.8 + 0.9 * hash(vec2(fi, 5.0))));
        float shape = 1.0 - smoothstep(0.85, 1.20, length(vec2(d.x / 0.19, d.y / 0.09)));
        overRarity(layer, mix(c2, c1, hash(vec2(fi, 9.0)) * 0.8), shape * (0.45 + 0.4 * ph) * 0.85);
    }
    layer.rgb += c1 * textBloom(p) * (0.06 + breath * 0.08);
    outputRarity(layer);
}
