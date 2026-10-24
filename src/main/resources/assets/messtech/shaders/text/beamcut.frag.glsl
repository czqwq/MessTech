#version 120

#include "common.glsl"

// 光刃斩击：一个字高左右的短刃从左扫到右，切过的笔画留下白热 -> 熔渣 -> 冷铁的热历史，刃尖带火花。
const vec3 BC0 = vec3(0.039, 0.055, 0.086);
const vec3 BC1 = vec3(0.310, 0.450, 0.640);
const vec3 BC2 = vec3(1.000, 0.965, 0.847);
const vec3 BC3 = vec3(1.000, 0.416, 0.102);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : BC0;
    vec3 c1 = paletteOverridden ? palette[1] : BC1;
    vec3 c2 = paletteOverridden ? palette[2] : BC2;
    vec3 c3 = paletteOverridden ? palette[3] : BC3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;
    float ink = coverage(p * h);

    // 刃前锋每 2 s 从左扫到右。
    float head = fract(time / 2.0) * 1.4 - 0.2;
    float vis = smoothstep(-0.15, 0.0, head) * (1.0 - smoothstep(1.0, 1.15, head));
    float frontX = head * w;
    float behind = frontX - p.x;
    float cut = step(0.0, behind);
    float cool = clamp(behind / 1.6, 0.0, 1.0);

    vec3 uncut = mix(c0, c1, 0.50);
    vec3 cutColor = mix(c1, c0, cool * 0.35);
    cutColor = mix(cutColor, c2, pow(1.0 - cool, 5.0));
    cutColor = mix(cutColor, c3, smoothstep(0.08, 0.40, cool) * (1.0 - smoothstep(0.60, 1.0, cool)) * 0.95);
    vec3 color = mix(uncut, cutColor, cut);

    // 短刃：沿刃约一个字高、横向很窄的旋转各向异性高斯。
    vec2 bladeDir = normalize(vec2(0.45, 1.0));
    vec2 bladePerp = vec2(bladeDir.y, -bladeDir.x);
    vec2 rel = p - vec2(frontX, 0.5);
    float along = dot(rel, bladeDir);
    float across = dot(rel, bladePerp);
    float blade = exp(-(along * along) / 0.45 - (across * across) / 0.0022) * vis;
    float core = exp(-(along * along) / 0.22 - (across * across) / 0.0005) * vis;

    // 刃尖飞出的火花。
    float sparks = 0.0;
    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        float side = hash(vec2(fi, 1.3)) > 0.5 ? 1.0 : -1.0;
        float t = fract(time * 2.2 + hash(vec2(fi, 4.1)));
        vec2 start = vec2(frontX, 0.45) + bladeDir * side * 0.45;
        vec2 dir = normalize(bladePerp * side + bladeDir * (hash(vec2(fi, 7.7)) - 0.5));
        vec2 pos = start + dir * t * 0.8;
        sparks += starLight(turn(pos - p, 0.9), vec2(0.015, 0.09)) * (1.0 - t) * vis;
    }

    // 切面细线，只画在字形带内。
    float band = smoothstep(-0.10, 0.0, p.y) * (1.0 - smoothstep(1.0, 1.10, p.y));
    float front = exp(-abs(p.x - frontX) * 90.0) * band * vis * 0.5;

    vec4 layer = vec4(0.0);
    overRarity(layer, color, ink);
    layer.rgb += mix(c2, c3, 0.4) * (blade * 1.1 + core * 1.6);
    layer.rgb += c3 * sparks * 0.9;
    layer.rgb += c2 * sparks * 0.5;
    layer.rgb += c2 * front;
    outputRarity(layer);
}
