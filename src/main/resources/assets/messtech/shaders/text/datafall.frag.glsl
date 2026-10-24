#version 120

#include "common.glsl"

// 数据坠流：数据雨落下，块状乱码保持可读轮廓，解码头每隔几秒自上而下瞬间扫过（约 0.12 s，2-3 tick）。
const vec3 DF0 = vec3(0.020, 0.055, 0.075);
const vec3 DF1 = vec3(0.145, 0.930, 0.800);
const vec3 DF2 = vec3(0.880, 1.000, 0.980);
const vec3 DF3 = vec3(0.280, 0.520, 1.000);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : DF0;
    vec3 c1 = paletteOverridden ? palette[1] : DF1;
    vec3 c2 = paletteOverridden ? palette[2] : DF2;
    vec3 c3 = paletteOverridden ? palette[3] : DF3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;

    // 解码头：先停留，再用约 0.12 s 从顶扫到底。
    float cycle = fract(time / 2.4);
    float sweep = clamp(smoothstep(0.0, 0.05, cycle) - smoothstep(0.55, 0.60, cycle), 0.0, 1.0);
    float head = sweep * 1.5 - 0.25;
    float below = smoothstep(-0.03, 0.03, p.y - head);

    // 未解码区：按块横向抖动，保留可读的字形轮廓。
    vec2 block = floor(vec2(p.x / 0.14, p.y / 0.12));
    vec2 jitter = vec2(hash(block + 1.0) - 0.5, 0.0) * 0.26;
    float inkEnc = coverage((p + jitter) * h);
    float inkClean = coverage(p * h);
    float ink = mix(inkClean, max(inkEnc, inkClean * 0.65), below);

    // 数据雨：横过字形带的短划线。
    vec2 g = vec2(p.x / 0.05, (p.y - time * 1.25) / 0.16);
    float bit = step(0.62, hash(floor(g)));
    float dash = bit * (1.0 - fract(g.y) * 0.85);
    float rain = dash * smoothstep(-0.10, 0.02, p.x) * (1.0 - smoothstep(w - 0.02, w + 0.10, p.x))
        * smoothstep(-0.10, 0.02, p.y) * (1.0 - smoothstep(0.98, 1.12, p.y));

    vec3 color = mix(c1, c2, 0.20 + 0.55 * smoothstep(0.0, 1.0, p.y));
    color = mix(color, c3, below * 0.7);
    color *= 0.55 + 0.45 * inkClean;
    color = mix(color, c2, clamp(rain * 1.4, 0.0, 1.0) * (0.35 + 0.65 * ink));

    // 扫描细线与加密闪烁块，只画在字形带内。
    float band = smoothstep(-0.06, 0.02, p.y) * (1.0 - smoothstep(0.98, 1.06, p.y));
    float line = exp(-abs(p.y - head) * 45.0) * band;
    float flick = step(0.93, hash(floor(vec2(p.x / 0.12, p.y / 0.12)) + floor(time * 24.0))) * below * band;
    color = mix(color, c2, clamp(line * 0.9 + flick * 0.7, 0.0, 1.0));

    float alpha = clamp(max(ink, max(rain * 0.15, max(line * 0.55, flick * 0.4))), 0.0, 1.0);
    outputRarity(vec4(color * alpha, alpha));
}
