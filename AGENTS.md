# Agent notes (MessTech)

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
- Machines
  - MTDTPF
  - MTNQDAFReactor
  - MTReactor (nuclear reactor)
  - Machine tooltips (house style)
  - MTChemicalTwister
  - MTHugeChemicalReactor (巨型化学反应釜)
  - MTComputingCenter
  - MTAssFactory
  - MTInventoryInputBusME / MTInventoryInputHatchME
  - MTWirelessVacuumConveyorInput / MTWirelessVacuumConveyorOutput
  - MTWirelessBeamlineInput / MTWirelessBeamlineOutput / MTWirelessBeamlineAdvancedOutput
  - MTNanoScaleFoundry and the 24 pool
  - SpaceModule Infinity machines (pump / miner / assembler)
  - Space apiary modules (太空蜂箱, MK-I..MK-IV)
  - Blocks
- GT5U API knowledge
- Own-code conventions

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
- 2. SMD Processor（SMD 处理器）
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
  `tmp/GT5-Unofficial-5.09.54.133/` for the GT5U we compile against; (2) the rest of `tmp/`; (3) only if
  neither has it, unpack or decompile the jar from the gradle cache. Do not start at (3).
- When a check *is* asked for, prefer the narrowest thing that proves the change; do not sweep the whole
  `tools/**` tree.

## Working rules

- Do not create git commits unless asked; leave the working tree for the user to review.
- Machine tooltips follow the house style in `docs/knowledge.md` ("Machine tooltips (house style)"): colour
  codes live in the lang files, the Java side is one `addInfo(translate(key))` per line, and the structure
  block calls stay untouched.
- Chinese proper nouns (blocks, casings, hatches, buses, coils, fluids, items) are copied verbatim from the
  GTNH localisation under `tmp/ZH-CN`; new user visible text goes into both lang files
  (`src/main/resources/assets/messtech/lang/{zh_CN,en_US}.lang`).
- Numbers and mechanics that a tooltip or a doc claims must be traceable to a source line; do not invent them.
