#version 120

#include "common.glsl"

// 密文解码：未解码的琥珀色乱码，每 3.2 s 从左到右瞬间解密一次（约 0.22 s，4-5 tick），扫描头是字形带内的一条亮线。
const vec3 CP0 = vec3(0.012, 0.071, 0.043);
const vec3 CP1 = vec3(0.208, 0.878, 0.478);
const vec3 CP2 = vec3(0.902, 1.000, 0.937);
const vec3 CP3 = vec3(1.000, 0.690, 0.125);

void main() {
    vec3 c0 = paletteOverridden ? palette[0] : CP0;
    vec3 c1 = paletteOverridden ? palette[1] : CP1;
    vec3 c2 = paletteOverridden ? palette[2] : CP2;
    vec3 c3 = paletteOverridden ? palette[3] : CP3;

    float h = max(1.0, textSize.y);
    float w = textSize.x / h;
    vec2 p = position() / h;
    float ink = coverage(p * h);
    float xr = clamp(p.x / max(0.001, w), 0.0, 1.0);

    // 解码周期：先定格密文，再从左扫到右，然后保持明文并扫回。
    float cycle = fract(time / 3.2);
    float sweep = clamp(smoothstep(0.0, 0.07, cycle) - smoothstep(0.55, 0.62, cycle), 0.0, 1.0);
    float head = sweep * 1.4 - 0.2;
    float unlocked = 1.0 - smoothstep(head - 0.02, head + 0.02, xr);

    // 加密区：按格乱码的字形碎片。
    vec2 cell = floor(p / 0.12);
    vec2 jitter = vec2(hash(cell + 3.0), hash(cell + 7.0)) - 0.5;
    jitter *= 0.35;
    float lockedInk = coverage((p + jitter) * h);
    float noiseBar = step(0.55, hash(vec2(cell.x, floor(time * 9.0)))) * lockedInk;

    vec3 lockedColor = mix(c0, c3, 0.60 + 0.40 * hash(cell));
    vec3 color = mix(lockedColor, c1, unlocked);
    color = mix(color, c2, unlocked * 0.30);
    float flash = step(0.94, hash(vec2(floor(p.y / 0.10), floor(time * 18.0)))) * unlocked;
    color = mix(color, c2, flash * 0.7);

    float band = smoothstep(-0.06, 0.02, p.y) * (1.0 - smoothstep(0.98, 1.06, p.y));
    float headLine = exp(-abs(xr - head) * 160.0) * band;
    float inkAll = max(ink * unlocked, lockedInk * (1.0 - unlocked) * 0.9);
    inkAll = max(inkAll, noiseBar * 0.35);

    vec4 layer = vec4(0.0);
    overRarity(layer, color, inkAll);
    layer.rgb += c3 * noiseBar * (1.0 - unlocked) * 0.45;
    layer.rgb += c1 * headLine * 0.35;
    layer.rgb += c2 * headLine * headLine * 0.5;
    outputRarity(layer);
}
