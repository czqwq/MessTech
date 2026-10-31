# 文字动态特效 — 设计稿

> 状态：已落地。Java 在 `com.MessTech.common.text.effect`（格式）与 `com.MessTech.client.text`（渲染），
> 着色器在 `assets/messtech/shaders/text/`。
> 预览：同目录 [`preview.html`](preview.html)（单文件 WebGL1，`file://` 可开；主预览 + 8 张图鉴卡片实时渲染）。
> 游戏内预览：`/messtechfx [id|all]`（客户端命令，不经过服务端）。
> 所以为什么要弄这么多花里胡哨的特效呢？谁知道呢,估计是吃饱了闲得慌

## 1. 在文本里使用

| 写法 | 说明 |
|---|---|
| `TextEffects.apply(text, TextEffects.DATAFALL)` | 代码里最省事：自动加作用域标记 |
| `§[datafall]文字§r` | 手写内联：到 `§r` 或本行结束 |
| `§[magnetar;1.4;#25edcc,#e0fffa]文字` | `id;速度;颜色表`（进度 1 = 原速，颜色最多 8 个 24 位 RGB 十六进制） |
| `&[df]文字` / `&[df;#25edcc,#4785ff;2]` | 紧凑内联式，支持短名 |

- 短名：`df` `eh` `ib` `mg` `rs` `bc` `cp` `pf`；完整 id 为 `messtech:<name>`。
- 标记字符刻意与其他文字特效模组错开：作用域标记用 U+2064（不是 U+2063），内联用方括号 `§[` / `&[`（不是花括号
  `§{` / `&{`）。这样两个特效系统可以同时装在同一个包里而不会互相认领对方的文本——各自只处理自己的标记。
- 颜色表会覆盖效果默认 4 色（`palette[0..]`）；`§x` / `§g` / `§u` 等原生颜色码与加粗、斜体、下划线照常生效。
- 需要「无格式文本」的场合（物品搜索、`getTextWithoutFormattingCodes` 等）会自动剥掉标记。
- 现成用例：弓名直接写在 lang 里（`item.messtech.voidstarBow.name=§[mg]混沌终焉·虚无贯星弓§r`），字体画到它的
  地方（tooltip 首行、物品栏、聊天物品链接）都会走磁星场线着色器，不需要额外的 tooltip 钩子。

## 2. 渲染管线

| 位置 | 职责 |
|---|---|
| `common.text.effect.TextEffectFormat` | 标记语法、颜色表解析、原生格式码长度、短名表 |
| `common.text.effect.EffectTextParser` | 扫描声明与原生格式码，产出「可见字符 + 样式 + 源下标」 |
| `common.text.effect.TextEffects` / `TextEffectStyle` | 构造标记、8 个预设、样式值对象 |
| `client.text.EffectTextRenderer` | 入口：布局缓存、遮罩捕获、阴影 `pass`、异常退回纯文本；`IResourceManagerReloadListener` |
| `client.text.EffectTextLayout` | 可见字符布局，供绘制、量宽、裁剪、换行、输入框切片共用 |
| `client.text.TextMaskCache` | 用 FBO 光栅整行文字当 alpha 遮罩（位图字体 2×、平滑字体 4×），LRU + 像素预算 |
| `client.text.TextRenderState` | 临时改渲染目标/矩阵前后保存并恢复 GL 状态 |
| `client.text.EffectTextFieldView` | 光标/滚动/选区按源下标投影到可见片段 |
| `client.text.DeferredTextEffects` | Angelica 延迟批次结束时重放特效文字 |
| `client.text.compat.AngelicaTextAdapter` / `FontBatchBridge` | 可选：暂停 Angelica 字形批处理，取字形在图集里的区间 |
| `client.text.effect.TextShader` | `#include` 展开、UTF-8 读取、编译链接、uniform 缓存 |
| `client.text.effect.ShaderTextEffect` | 上传共享 uniform 与色板，画遮罩四边形；阴影 pass 用遮罩的深色副本 |

Mixin：`MixinFontRenderer_TextEffect`（draw/width/trim/wrap/split）、`MixinGuiNewChat_TextEffect`（跨行续接）、
`MixinGuiTextField_TextEffect`（输入框光标与滚动）、`MixinEnumChatFormatting_TextEffect`（剥离标记），这四个在 early
配置的 `client` 列表里；`MixinBatchingFontRenderer_TextEffect`（仅 Angelica）也必须在 **early** 配置——Angelica 的
coremod 在启动阶段就构造字体渲染器，放在 late 配置会报 `MixinTargetAlreadyLoadedException`（目标类已被提前加载）；
`MixinFormattedTextField_TextEffect`（仅 NEI）走 late 配置，由 `Mixins` 枚举按模组过滤。

**Angelica 2.2.42 / GTNH 2.9.0 的变化**：`com.gtnewhorizons.angelica.glsm.GLStateManager` 被拆成一组 `GLSM*` 包装类，
`BatchingFontRenderer.setupFontDrawState()` 里的混合设置从 `GLStateManager.tryBlendFuncSeparate(...)` 变成
`GLSMBlend.tryBlendFuncSeparate(IIII)V`。`MixinBatchingFontRenderer_TextEffect.mt$compositeMaskCoverage` 的
`@ModifyArg` 目标必须跟着改，否则启动时整类注入失败：

```
Critical injection failure: Argument modifier method mt$compositeMaskCoverage(I)I ... failed injection check,
(0/1) succeeded. Scanned 0 target(s).
→ NoClassDefFoundError: com/gtnewhorizons/angelica/client/font/BatchingFontRenderer
```

其余被注入/影子的成员（`drawString(FFIZZLjava/lang/CharSequence;II)`、`flushDeferredText`、`shouldDeferNow`、
`shouldDrawThroughPipeline`、`underlying`、`batchDepth`、`arenaOwner`、`flushBatch`）在 2.2.42 未变。

## 3. 着色器契约

每个效果 = 一个 `<id>.frag.glsl`，`#version 120` + `#include "common.glsl"`，只写亮部（阴影由管线画）。

| uniform | 说明 |
|---|---|
| `textMask` / `maskSize` / `maskResolution` | 整行文字 alpha 遮罩；`maskSize` 是 GUI 像素尺寸，`maskResolution` 是每个 GUI 像素的采样数 |
| `textSize` / `textOrigin` / `padding` | 文字包围盒与四周留白；**`h = textSize.y` 是所有距离的单位** |
| `time` / `opacity` | 秒（已乘速度）、整体透明度 |
| `shadowPass` | 保留位（当前阴影不走效果 shader） |
| `paletteCount` / `paletteOverridden` / `palette[8]` | 默认 4 色；外部覆盖时用 `palette` |

| 工具函数 | 说明 |
|---|---|
| `position()` | 相对包围盒左上角的 GUI 像素坐标，y 向下 |
| `coverage(p)` | 该点的字形覆盖率；超出遮罩返回 0 |
| `outline(p, r)` | 12 方向采样的外圈覆盖（描边/光晕/洇边） |
| `hash` / `noise` / `turn` / `starLight` / `textBloom` | 噪声、旋转、十字星光、包围盒底光 |
| `overRarity` / `outputRarity` | 预乘 alpha 叠层与输出（含整体透明度、全黑 `discard`） |

约定：效果体统一写 `float h = max(1.0, textSize.y); vec2 p = position() / h;`，于是与字号、GUI 缩放、
字体无关；每个效果默认 4 色写成 `const vec3 X0..X3`，开头按 `paletteOverridden` 取 `c0..c3`。
输出是预乘 alpha（`ShaderTextEffect` 用 `GL_ONE, GL_ONE_MINUS_SRC_ALPHA` 混合）。

## 4. 八个效果

| # | id | 中文名 | 机制 | 默认色板 |
|---|---|---|---|---|
| 1 | `datafall` | 数据坠流 | 数据雨 + 块状乱码；解码头每 2.4 s 用约 0.12 s 自上而下扫过 | `#050e13 #25edcc #e0fffa #4785ff` |
| 2 | `eventhorizon` | 事件视界 | 引力透镜拉向核心 + 吸积盘螺旋 + 光子环 + 绕环光子 | `#0e0620 #ff9e2e #fff3d0 #8a5cff` |
| 3 | `inkbloom` | 墨韵流金 | 双层噪声墨晕 + 覆盖率梯度笔锋 + 洇边 + 金箔 + 流金扫光 | `#291f17 #735438 #f2bd5c #fff7e6` |
| 4 | `magnetar` | 磁星场线 | 过磁极的圆族 + 等离子流动 + 每 2.6 s 一次锯齿电弧 | `#07131c #5ef2ff #e8ffff #3a6bff` |
| 5 | `resonance` | 共振声纹 | 30 根频谱柱 + 波形线 + 7.8 重音驱动的节拍环 | `#070d16 #2bd8ff #ff4fc8 #f2ffff` |
| 6 | `beamcut` | 光刃斩击 | 一个字高的短刃扫切；切过的笔画白热 → 熔渣 → 冷铁 + 刃尖火花 | `#0a0e16 #4f73a3 #fff6d8 #ff6a1a` |
| 7 | `cipher` | 密文解码 | 琥珀乱码；每 3.2 s 用约 0.22 s 左→右解密，扫描头是字形带内的亮线 | `#03120b #35e07a #e6ffef #ffb020` |
| 8 | `petalfall` | 落英缤纷 | 两层正弦相位混出流动的粉 + 移动高光 + 前后两组旋转下落花瓣 | `#4a1030 #ff8fa3 #ffd7b0 #b06bff` |

可读性从高到低大致：`resonance` ≈ `datafall` > `beamcut` / `magnetar` / `eventhorizon` > `cipher` /
`petalfall` / `inkbloom`；前四个适合常驻物品名，后四个更适合短名、触发状态或彩蛋。

## 5. 性能与开关

- 布局缓存 256 条、遮罩缓存 256 张且总量 4 M 像素，超出按 LRU 释放；不换字体/字号/文字时命中缓存。
- 每个特效文字段：一次遮罩捕获（命中则零成本）+ 一到两次包围盒大小的 shader 四边形（阴影 + 亮部）。
- `Config.general.TEXT_EFFECTS`（默认开，`General` 分类）：关闭时同一段文字仍按第一色板颜色显示纯文本，标记不会漏字。
- 缺 GLSL、无 FBO、Angelica 字体没有可暂停的批处理、着色器编译失败等情况都会退回纯文本，不崩客户端。

## 6. 加一个效果

1. 写 `assets/messtech/shaders/text/<id>.frag.glsl`（`#version 120` + `#include "common.glsl"` + `main`）。
2. `TextEffects` 加一个预设常量，`TextEffectFormat` 加短名（可选）。
3. `BuiltinTextEffects.register()` 加一行：预设 + 默认色板。
4. 需要的话补 lang（`messtech.text_effect.<id>` 和 `.sample`）与 `MTTextEffectCommand` 的 id 表。

## 7. 与其它文字特效模组共存

同一个包里装两套“用标记驱动字体渲染”的模组时，冲突只来自两处，这里都已避开：

- **标记字母表**：作用域标记的隐形字符用 U+2064（对方多用 U+2063），内联用 `§[` / `&[`（对方多用 `§{` / `&{`）。
  `EffectTextParser.containsMarkers` 也只认真实的前缀，不认“任意 U+2063 / 花括号”，所以两边各自只认领自己的文本；
  对方的 `getTextWithoutFormattingCodes` 补丁也不会去剥我们的标记。
- **mixin 注入种类**：FontRenderer / GuiNewChat / GuiTextField / 聊天框剥离 / NEI 输入框全部只用 HEAD·RETURN
  `@Inject`、`@ModifyArg`、`@ModifyVariable` 和 MixinExtras `@WrapOperation`，这几类可以叠加；**不用 `@Redirect`**。
  唯一需要改 Angelica 字形抗锯齿的地方（`AngelicaTextAdapter#beginSharpMask`）改成在遮罩捕获期间临时写
  `FontConfig.fontAAMode`，而不是 `@Redirect` 那三次字段读取——两个 `@Redirect` 撞同一条指令时后应用的那个会
  找不到目标，是硬崩溃。

两套系统各自持有自己的布局/遮罩缓存/渲染器/资源重载监听，互不共享静态状态；同一行文字里同时出现两套标记的
情况极少，遇到时先认领的一方会把它当成普通可见字符，不做跨系统组合。
