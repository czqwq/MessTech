# Agent notes (MessTech)

> **本文件保持简洁**：只写必须遵守的规则和必要索引，不写废话、不展开细节；详细内容一律放 `docs/`。

Process rules for anyone working in this repo - human or agent. The detailed, per-feature knowledge lives in
`docs/`, indexed below.

## Docs index

`docs/` is the long-form knowledge base: check the section you need and read that one, rather than a whole file
end to end - `knowledge.md` alone is over a thousand lines. Find a section with `grep -n '^#' docs/<file>.md`.

### `docs/knowledge.md` - conventions and every implemented feature
- Project layout
- Machine base hierarchy
  - MTMultiMachineBase
  - MTModuleMultiMachineBase (module system)
  - MTWirelessMultiMachineBase
  - MTGeneratorMultiBase
  - ParallelismAcrossMultiMachineBase
  - CalculateMultiMachineBase / MTComputingCenter
  - MTParallelHelper / one batch calculation (`Config.machines.ENABLE_ONE_BATCH_CALCULATOR`)
- Machines
  - MTDTPF
  - MTNQDAFReactor
  - MTReactor (nuclear reactor)
  - Machine tooltips (house style)
  - MTChemicalTwister
  - MTHugeChemicalReactor (巨型化学反应釜)
  - MTComprehensiveMiningPlant (综合采掘工厂, ported from Elinar's standalone mod; 11x10x10 tower, F = output hatch region)
  - MTComputingCenter
  - MTAssFactory
  - MTInventoryInputBusME / MTInventoryInputHatchME
  - MTWirelessVacuumConveyorInput / MTWirelessVacuumConveyorOutput
  - MTWirelessBeamlineInput / MTWirelessBeamlineOutput / MTWirelessBeamlineAdvancedOutput
  - MTNanoScaleFoundry and the one-step circuit pool (incl. programmable pattern hatches and renamed items)
  - SpaceModule Infinity machines (pump / miner / assembler)
  - Space apiary modules (太空蜂箱, MK-I..MK-IV)
  - Space Elevator (`MTSpaceElevator`): copied GT5U elevator logic, own 95×122×78 structure, 64 module bays
  - MTLargeProcessingFactory (LPF, GT++ port) / MTLargeEssentiaGenerator (LEG, GoodGenerator port, incl. the ME
    essentia input hatch and the `essentia_cell` structure sub-channel)
  - Thaumcraft research tab (混乱科技 / MessTech): `MTThaumcraftResearch`, essentia hatch, LEG upgrade items
  - Blocks
- GT5U API knowledge
- NEI collapsible item groups (物品折叠：速度/节能/并行模块仓与 NAC 部件, via `config/NEI/collapsibleitems.cfg`)
- Own-code conventions
- Chaos Matter (混乱物质): GT material registration and the stacked-renderer look
- 混沌终焉·虚无贯星弓 (`MTItemVoidstarBow` / `MTEntityVoidstarArrow`): magic-circle charge FX, straight penetrating
  arrow, tunnel blast, true kill (`MTKillBase` / `MTBowKill`), the DE sub-mode (`MTExplosionDE` + `MTEntityDEBlast` /
  `MTDEBlastRenderer`) and the `magnetar` text-effect name (inline `§[mg]` in its lang entry)
- 彩虹小扳手 (`MTRainbowWrench` / `ToolRainbowWrench` / `MTRainbow`): GT5U `MetaGeneratedTool` wrench registration,
  pinned stats (durability Int.MAX / harvest level 325 / mining speed 999), animated item icon, creative/NEI NBT fix-up
- Galacticraft hook: 高速上升自动开星系 GUI (`MTGalacticraftSpaceHandler`)
- Vanilla "moved too quickly" 上限 (`MixinNetHandlerPlayServer_MovedTooQuickly`)
- `/messtech GoUp [秒数]` (`MTCommand` / `MTGoUpFlight`)

### `docs/GT5U-NOTES.md` - GT5U API notes for the version we build against
- 0. Local reference source trees (offline lookup)
- 1. The project layout
- 2. The machine base-class chain (that MessTech uses)
- 3. StructureLib shape convention (important!)
- 4. Structure elements (`addElement`)
- 5. Blocks used by MTDTPF (registry names → meta → tier)
- 6. Tier enforcement pattern (LevelTier)
  - Reading the tier back (usage examples)
- 7. Recipes: using the Large-Fusion pool with a tier cap
- 8. Structure errors & localization
- 9. Construct / survival construct offsets
- 10. Textures & GUI (MTDTPF example)
- 10.5 Waila (tooltip) data
- 10.6 Runtime efficiency scaling (DTPF-style)
- 10.7 Wireless mode (direct wireless-network power)
- 11. Gotchas
- 11.5 Nano Computing Center (`MTComputingCenter`)
- 11.6 Assembly Factory (`MTAssFactory`)
- 12. Registering a machine + animated authors
  - Registering an MTE so it shows up in game
  - Animated "Author:" tooltip (gtnhlib)
  - MessTech's actual refactor (current layout)
- 13. GT5U API knowledge from recent work
- 14. MessTech's IC2/GT fuel rods (Transcendent Metal)
- 15. MTReactor heat control hatch (`MTReactorHeatHatch`)
- 16. Reactor recipes (`GTRecipes.addReactorRecipes()`)
- 17. Beamline / particle hatch API (GT5U 5.09.54.133)

### `docs/repo-readme.md` - the repo summary it started as
- Machine base hierarchy
  - `MTMultiMachineBase`
  - `MTWirelessMultiMachineBase`
  - `ParallelismAcrossMultiMachineBase`
- Machines
  - MTDTPF
  - MTComputingCenter
  - MTAssFactory
- Own-code conventions / lessons

### `docs/nac-module.md` - NAC module numbers and bonuses (Chinese)
- 0. 所有 NAC 模块共用的基础逻辑
- 1. Assembly Matrix（装配矩阵）
- 2. Part Processor（原 SMD Processor，5.09.54.183 起改名）
- 3. Board Processor（基板处理器）
  - EU 倍率公式（按 impurity y，0~1）
  - 杂质增长
- 4. Etching Array（蚀刻阵列）
  - EU 倍率
  - 时长倍率
- 5. Cutting Chamber（切割室）
- 6. Wire Tracer（导线追踪器）
- 7. Superconductor Splitter（超导分离器）
- 8. Optical Organizer（光学整理器）
  - 水列表
- 9. Encasement Wrapper（封装机）
- 10. Biological Coordinator（生物协调器）
- 11. Splitter（分流器）
- 给 Nano-Scale Foundry 定数值时的建议锚点

### `docs/mochadoom-console.md` - 游戏机 / Mocha Doom port（Chinese）
- 0-7. 可行性研究：Mocha Doom 源码规模与包结构（444 文件 / 86,761 行，无第三方依赖，GPLv3）、
  可替换的宿主接口（`DoomGraphicSystem` / `IDoomSystem` / `IDoomSound` / `ISoundDriver` / `IMusic`）、
  AWT 耦合面的真实范围、WAD 数据与许可、风险
- 9. 实际落地：`com.MessTech.doom.*` 里的 437 个文件与包重定位、11 处引擎补丁
  （`Engine.bootstrap` / `HeadlessRaster` / `System.exit` → `DoomFatalError` / `HostPaths` /
  `aliasKey` 键位别名）、`MTGameConsoleHost` 的线程与帧数据流、键位表、Freedoom 自动下载
- 9.7 声音（javax.sound 混音器；9.7.1 音乐：`s/opl/` 里**自己仿真的 YM3812 (OPL2)** ——
  `GenMidiBank` 解 WAD 的 GENMIDI 音色表、`DoomSong` 兼容 MUS 与 MIDI 两种曲谱、`OplMusicPlayer`
  驱动、`OplMusicModule` 泵进 `MusicMixBuffer`；再混进同一条数据线。要点：GENMIDI 字节布局的推导、
  算子槽位 `n + n/3`、环形空闲空间当节拍器、音乐也要算"有活干"）、9.8 已知限制、
  9.9 按键自定义（`MTGameConsoleBindings`：参考扫描码 + `aliasKey` 槽、`keys.cfg`、
  窗口内的按键设置模式，以及不再回写 `mochadoom.cfg` 的原因）
- 9.6 验证记录（`spotlessApply build`、无头 120 帧、别名生效、音频初始化/收尾、按键模型自检、
  音乐在 Java 8 / JDK 25 两种模式下的证据、引擎端到端 85.5 KB/s）

### `docs/text-fx/design.md` - 文字动态特效（字体级 shader 渲染管线）
- `common/text/effect/*`（标记语法/解析/预设）+ `client/text/*`（布局、遮罩缓存、渲染器、Angelica 兼容）
  + `client/text/effect/*`（`TextShader` / `ShaderTextEffect` / 8 个内置效果）+ `assets/messtech/shaders/text/`
- 8 个原创 frag（`datafall` / `eventhorizon` / `inkbloom` / `magnetar` / `resonance` / `beamcut` /
  `cipher` / `petalfall`），契约见同目录设计稿；`Config.general.TEXT_EFFECTS` 总开关，`/messtechfx` 游戏内预览
- 预览：同目录 `preview.html`（单文件、`file://` 可开，主预览 + 图鉴卡片实时渲染）

## Verification

- **Run the checks once, at the end - not after every edit.** A change that is still in progress does not have to
  be proven compiling at every step: build and format (`./gradlew spotlessApply` and the compile/build below) only
  when the work is finished and about to be handed over. A `compileJava` between edits costs a minute or more of
  gradle time and proves nothing new.
- **`./gradlew compileJava` is the default check for a finished change, and it is all that is expected.**
- Do **not** write new harnesses or verifiers under `tools/**` for a change, and do not extend or run the
  existing ones, unless the user explicitly asks for it.
- Run the wider build (`./gradlew spotlessApply build`) only when the
  user asks for a full build, or when handing work over as finished and the formatter/checkstyle would
  otherwise be the only thing unchecked.
- The harnesses that already exist in `tools/**` stay where they are and are only run on request.
  `tmp/` holds the reference checkouts (GT5U, NEI, TST, ...) and scratch work.
- **Looking a dependency up, in this order:** (1) the "Local reference source trees" table in
  `docs/GT5U-NOTES.md` § 0 - it lists what is already decompiled on disk, notably
  `build/rfg/minecraft-src/java/` for Forge + vanilla `net.minecraft.*` / `cpw.mods.fml.*` and
  `tmp/GT5-Unofficial-beta3/` for the GT5U **5.09.54.133 snapshot** (note: the compile dependency is resolved
  from the `gtnhVersion` manifest in `dependencies.gradle` and is newer - 5.09.54.205 as of 2.9.0-RC-2 - so check
  version-sensitive APIs against the resolved jar and see `tmp/Compare.md` for what moved); (2) the rest of `tmp/`; (3) only if
  neither has it, unpack or decompile the jar from the gradle cache. Do not start at (3).
- When a check *is* asked for, prefer the narrowest thing that proves the change; do not sweep the whole
  `tools/**` tree.

## Working rules

- Do not create git commits unless asked; leave the working tree for the user to review.
- Machine tooltips follow the house style in `docs/knowledge.md` ("Machine tooltips (house style)"): colour
  codes live in the lang files, the Java side is one `addInfo(translate(key))` per line, and the structure
  block calls stay untouched.
- Chinese proper nouns (blocks, casings, hatches, buses, coils, fluids, items) are copied verbatim from the
  GTNH localisation under `tmp/Translation-GTNH`; new user visible text goes into both lang files
  (`src/main/resources/assets/messtech/lang/{zh_CN,en_US}.lang`).
- Numbers and mechanics that a tooltip or a doc claims must be traceable to a source line; do not invent them.
