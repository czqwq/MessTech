#version 120

#include "common.glsl"

// 磁星场线：过两磁极的圆族当偶极场线，等离子在笔画里流动，每 2.6 s 两极之间劈一次电弧。
const vec3 MG0 = vec3(0.027, 0.075, 0.110);
const vec3 MG1 = vec3(0.369, 0.949, 1.000);
const vec3 MG2 = vec3(0.910, 1.000, 1.000);
const vec3 MG3 = vec3(0.227, 0.419, 1.000);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : MG0;
    vec3 c1 = paletteOverridden ? palette[1] : MG1;
    vec3 c2 = paletteOverridden ? palette[2] : MG2;
    vec3 c3 = paletteOverridden ? palette[3] : MG3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;
    float ink = coverage(p * h);
    vec2 center = vec2(w * 0.5, 0.5);
    float halfSpan = w * 0.5;

    // 偶极场线：所有过两磁极的圆。
    float field = 0.0;
    float flow = 0.0;
    for (int i = 0; i < 7; i++) {
        float k = (float(i) - 3.0) * 0.34;
        vec2 cc = center + vec2(0.0, k);
        float radius = sqrt(halfSpan * halfSpan + k * k);
        vec2 rel = p - cc;
        float dist = abs(length(rel) - radius);
        float band = exp(-dist * dist / 0.0045);
        float ang = atan(rel.y, rel.x);
        float pulse = pow(0.5 + 0.5 * sin(ang * 2.0 - time * 3.2 + float(i) * 1.7), 6.0);
        field += band;
        flow += band * pulse;
    }
    field = clamp(field, 0.0, 1.0);

    // 两磁极之间的间歇电弧。
    float phase = fract(time / 2.6);
    float burst = smoothstep(0.0, 0.04, phase) * (1.0 - smoothstep(0.22, 0.34, phase));
    float jag = 0.26 * sin(p.x * 4.0 - time * 21.0) + 0.15 * sin(p.x * 11.0 + time * 33.0)
        + (hash(vec2(floor(p.x / 0.45), floor(time * 30.0))) - 0.5) * 0.24;
    float arcLine = exp(-abs(p.y - 0.5 - jag) * 120.0) * burst;

    // 两极的辉光。
    float pole = exp(-length(p - (center + vec2(halfSpan * 0.98, 0.0))) * 2.4)
        + exp(-length(p - (center - vec2(halfSpan * 0.98, 0.0))) * 2.4);

    // 场线只留在文字附近的一条带里，别铺满整个面板。
    float region = smoothstep(-0.55, -0.10, p.y) * (1.0 - smoothstep(1.10, 1.55, p.y));

    vec4 layer = vec4(0.0);
    // 带电字形：亮青核心，等离子在笔画里流动。
    vec3 inkColor = mix(vec3(0.100, 0.380, 0.520), c1, 0.45 + 0.55 * clamp(flow, 0.0, 1.0));
    inkColor = mix(inkColor, c2, clamp(flow * 1.4 + arcLine * 0.9, 0.0, 1.0));
    overRarity(layer, inkColor, ink);
    overRarity(layer, c2, ink * clamp(flow - 0.55, 0.0, 1.0) * 0.35);

    layer.rgb += c1 * field * (0.24 + 0.30 * ink) * region;
    layer.rgb += c1 * flow * 0.30 * region;
    layer.rgb += c2 * arcLine * 1.1;
    layer.rgb += mix(c1, c2, 0.5) * pole * 0.30 * region;
    layer.rgb += c1 * textBloom(p) * 0.05;
    outputRarity(layer);
}
