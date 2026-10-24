// 文字特效公共契约：整行文字的 alpha 遮罩、包围盒、时钟和色板。
// position() 返回相对包围盒左上角的 GUI 像素坐标（y 向下）；字高 textSize.y 是距离的自然单位。
// 阴影由渲染管线单独画（遮罩的深色副本），效果主体只管亮部。

uniform sampler2D textMask;
uniform vec2 maskSize;
uniform float maskResolution;
uniform vec2 textSize;
uniform vec2 textOrigin;
uniform float padding;
uniform float time;
uniform float opacity;
uniform bool shadowPass;
uniform int paletteCount;
uniform bool paletteOverridden;
uniform vec3 palette[8];
varying vec2 uv;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 base = floor(p);
    vec2 weight = fract(p);
    weight = weight * weight * (3.0 - 2.0 * weight);
    return mix(
        mix(hash(base), hash(base + vec2(1.0, 0.0)), weight.x),
        mix(hash(base + vec2(0.0, 1.0)), hash(base + vec2(1.0, 1.0)), weight.x),
        weight.y);
}

vec2 position() {
    return vec2(uv.x, 1.0 - uv.y) * maskSize - padding;
}

// 遮罩里 alpha 是字形覆盖率，RGB 是字形元数据，所以只取 alpha。
float coverage(vec2 point) {
    vec2 coord = (point + padding) / maskSize;
    if (coord.x < 0.0 || coord.y < 0.0 || coord.x > 1.0 || coord.y > 1.0) return 0.0;
    return texture2D(textMask, vec2(coord.x, 1.0 - coord.y)).a;
}

// 12 个方向采样出字形外圈，减去覆盖率即描边/光晕。
float outline(vec2 point, float radius) {
    float result = 0.0;
    for (int i = 0; i < 12; i++) {
        float angle = float(i) * 0.5235987756;
        result = max(result, coverage(point + vec2(cos(angle), sin(angle)) * radius));
    }
    return result;
}

vec2 turn(vec2 point, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c) * point;
}

float starLight(vec2 delta, vec2 radius) {
    vec2 q = abs(delta) / max(radius, vec2(0.001));
    return exp(-q.x * 14.0 - q.y * 2.0) + exp(-q.y * 14.0 - q.x * 2.0);
}

// 文字包围盒是椭圆形的淡淡底光。
float textBloom(vec2 point) {
    float aspect = textSize.x / max(1.0, textSize.y);
    vec2 q = (point - vec2(aspect * 0.5, 0.45)) / vec2(aspect * 0.55 + 0.25, 0.62);
    return exp(-dot(q, q) * 3.0);
}

// 预乘 alpha 叠加一层，供发光/描边等非字形像素使用。
void overRarity(inout vec4 layer, vec3 color, float alpha) {
    alpha = clamp(alpha, 0.0, 1.0);
    layer.rgb = color * alpha + layer.rgb * (1.0 - alpha);
    layer.a = alpha + layer.a * (1.0 - alpha);
}

// 输出预乘 alpha；整体透明度、全黑像素都在这里收尾。
void outputRarity(vec4 layer) {
    layer *= opacity;
    if (max(layer.a, max(layer.r, max(layer.g, layer.b))) <= 0.001) discard;
    gl_FragColor = layer;
}
