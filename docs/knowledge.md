# MessTech Knowledge Dump

This file is a **context-preservation knowledge base**. It collects everything important from the
current and previous work sessions so that work can continue even if earlier conversation context is
compressed/removed. See also `repo-readme.md` (own-code notes) and `GT5U-NOTES.md` (GT5U notes).

---

## Project layout

- Mod package: `com.MessTech`
- Machine registration: `com.MessTech.common.machine.loaders.MTMachineLoader.loadMachines()` called in
  `CommonProxy.postInit()`.
- Block registration: `com.MessTech.common.block.MTBlocks.registerBlocks()` called before machine loading.
- Item list enum: `com.MessTech.common.item.MTItemList` (GT-style ItemList pattern).
- Machine IDs: MTDTPF = 32400, MTComputingCenter = 32401, MTHatchRack = 32402, MTAssFactory = 32403.
- Language files: `src/main/resources/assets/messtech/lang/{en_US,zh_CN}.lang`.

## Machine base hierarchy

```
MTMultiMachineBase<T>
├── MTGeneratorMultiBase<T>
├── MTModuleMultiMachineBase<T>
├── MTWirelessMultiMachineBase<T>
│   └── ParallelismAcrossMultiMachineBase<T>
└── CalculateMultiMachineBase<T>
    └── MTComputingCenter
```

### MTMultiMachineBase
- Generic multi-block base extends GT `MTEExtendedPowerMultiBlockBase`.
- Provides standard `ProcessingLogic` setup, machine-mode switching, Waila NBT/body.
- No wireless code anymore (moved to `MTWirelessMultiMachineBase`).

### MTModuleMultiMachineBase (module system)
- `common/machine/Base`: `MTModuleMultiMachineBase<T>`, `IMTModule`, `MTModuleType`, `MTModuleValues`,
  `MTModuleHatchElement`. The hatches live in `common/machine/hatch`: `MTModuleHatchBase` (plain `MTEHatch`, no
  inventory, one decal per module), `MTModuleSpeedHatch`, `MTModuleEuHatch`, `MTModuleParallelHatch` +
  `MTModuleParallelHatchGui`; the machine side is `common/gui/base/MTModuleMultiMachineBaseGui`.
- Decal: each module names its own face art in `getOverlayPath()`, an icon path relative to `textures/blocks`
  (`ModuleHatch/OVERLAY_PowerController`, `..._SpeedController`, `..._ParallelController`). The base resolves it
  once per class in `registerIcons` and layers it over the casing on the front face, active and inactive alike -
  the same shape as `MTReactorAccessHatch`/`MTReactorHeatHatch`. GT's `BlockMachines#registerBlockIcons` calls
  every meta tile entity's `registerIcons` *before* it runs the `sGTBlockIconload` phase, which is what makes a
  `Textures.BlockIcons.custom` container resolved there usable. The three textures are 40-frame `.mcmeta`
  animations (`frametime` 2, one 4 s loop), so the decals are animated in the world.
- A module is whatever the machine links into its module slot (here: a hatch). The module reports what it
  provides, the machine aggregates - the numbers live in the concrete module.
- `MTModuleType`: `EU_DISCOUNT`, `SPEED_BONUS`, `PARALLEL_CONTROL`, `CROSS_RECIPE_PARALLEL`, `WIRELESS` (working
  titles). The first three are the standard set (`MTModuleType.defaultSupported()`); the other two only work after
  a machine opted in by overriding `getSupportedModuleTypes()`, because not every machine can use them.
- A module returns a **set** of types, so a composite module (e.g. speed + parallel in one module) needs no type of
  its own. `acceptsModule()` takes a module only when the machine supports every type it provides and the tier is
  in range.
- Module tier is the GT tier index: `IMTModule.MIN_TIER` = 5 (IV) .. `MAX_TIER` = 14 (MAX), `isValidTier()` rejects
  everything outside.
- Folding: the EU and speed modules **multiply** onto `getBaseEuModifier()` / `getBaseSpeedBonus()`, the parallel
  module **supplies**: `getMaxParallelRecipes()` returns `getModuleParallel()` (the highest `IMTModule#getParallel`
  of the linked parallel control modules) and falls back to `getBaseMaxParallelRecipes()` only when there is none.
  The module's number **is** the parallel the machine runs at (`baseParallel = module.getParallel()`), so 64
  parallel at IV stay 64 instead of becoming 68 on a machine that had 4. Only **one** parallel control module is
  taken (`addModule`), the way TST's `checkSingleModularHatch` treats a second controller as a structure error.
  Those three methods are **final**; subclasses implement the `getBase*` hooks (defaults 1 / 1 / 1), so a machine
  cannot lose its module values by accident.
- `checkMachine` is final and runs `clearModules()` before delegating to the subclass'
  `checkMachineStructure(...)`: the module list is rebuilt by every structure check, so pulling a module hatch
  removes its bonus at the next check. (TST solves the same problem with `resetModularHatchCollections()`.)
- Linking: `addModuleHatchToMachineList(te, casingIndex)` is the adder behind `MTModuleHatchElement.Module`
  (`IHatchElement<MTModuleMultiMachineBase<?>>`); a machine opts in with one line,
  `buildHatchAdder(MyMachine.class).casingIndex(casing).hint(1).atLeast(MTModuleHatchElement.Module).build()`.
  `IMTModule#onLinkedToMachine(casingIndex)` is the hook that lets a hatch take the casing texture, so the base
  class never has to reference the hatch types. No machine uses this yet.
- Values (`MTModuleValues`, tier IV..MAX):
  - speed module: MessTech's own fixed duration table → `{0.95,0.85,0.75,0.70,0.65,0.40,0.35,0.20,0.10,0.01}`, i.e.
    5% faster at IV up to 100x at MAX. `getSpeedBonus()` **is** the table entry, the fraction of the original
    duration that is left; the module no longer follows TST's `SpeedMultiplierOfSpeedController` ×2 law;
  - EU module: TST's `PowerConsumptionMultiplierOfPowerConsumptionController` `{0.95,0.9,0.85,0.8,0.75,0.7,0.5,0.25}`
    with the tail halving → `{...,0.125,0.0625}` (5% saved at IV up to 93.75% at MAX);
  - parallel module: `1 << (2 * (tier - 2))` = 64 at IV up to 16,777,216 at MAX, player-lowerable in the GUI
    (1..ceiling, clamped by `setParallelFromGui`, saved as NBT `parallel`).
  - TST registers its controllers on ZPM..MAX (its T1..T8). Our modules run IV..MAX, so TST's T1 sits on IV and the
    UXV/MAX EU entries are the only extrapolated TST numbers (the speed table is ours now).
- Items/IDs: `MT_ID + 40..49` speed, `+50..59` EU, `+60..69` parallel - one per tier IV..MAX, registered by looping
  over `MTItemList.SPEED_MODULES` / `EU_MODULES` / `PARALLEL_MODULES` in `MTMachineLoader`. Lang keys
  `machine.module.*` in both languages (`speed.name`, `speed.desc.0`, `eu.name`, `eu.desc.0`, `parallel.name`,
  `parallel.desc.0/1`, `parallel.label`, `desc.install`). A tooltip states the *factor* the module grants, in the
  decimal form of the tables above: the speed module prints the recipe duration reduction, i.e. the fraction of the
  old duration that is left (`MTModuleValues#speedBonusText`, `0.95` at IV up to `0.01` at MAX, always two
  decimals - which also keeps the float entries from leaking noise, `0.95F` is `0.949999988079071`), the EU module
  the EU/t modifier (`MTModuleValues#euModifierText`, `0.95` at IV down to `0.0625` at MAX, rounded to four
  decimals and without trailing zeros, because the last two table entries need those decimals; the percent form
  `euDiscountText` - 5% at IV up to 93.75% at MAX - is kept for the harness), and the parallel module only its
  ceiling plus "configurable in the GUI" - the `1..ceiling` range is what the GUI field enforces, not tooltip text.
  Every module closes with `desc.install` = 可用于模块化机器 / usable in a modular machine. The modules carry **no
  author line**: `MTMachineLoader#brandAsModuleProject` gives every one of them the animated `Add by: ModularProject`
  brand line (`AuthorDynamic.MODULE_PROJECT` / `MTModuleProjectText`, the same look the Huge Chemical Reactor wears)
  in place of the author + MessTech pair `AuthorDynamic#registerOn` would add.
- Recipes (`GTRecipes#addModuleRecipes`): 30 **assembler** recipes, one per family per tier. The shape is TST's
  controller recipes (`ModularHatchesRecipes`) crossed with GTNL's parallel controller hatch - 4 `TIER_HULLS[tier]`,
  a flat 16 of that tier's components, 16 of that tier's circuit (`TIER_CIRCUIT_MATERIALS[tier]`) and 16 plates of
  `MODULE_MATERIALS[i]`, plus `144 * 16` L of the same material as melt. Counts stay flat on purpose: a tier is
  paid for in *which* material and which `TIER_RECIPE_EU[tier]` it is made at, the way both of those mods do it, so
  the recipe reads the same at IV and at MAX. Duration is `MINUTES * (i + 1)`, i.e. 1 minute at IV to 10 at MAX.
  The three families differ only in the components they spend (TST's own split): speed is 3x field generator + motor
  + piston, parallel is field generator + robot arm + conveyor, EU is 2x field generator + emitter. Integrated
  circuits 4 / 5 / 6 mark the speed / parallel / EU family in NEI, the same idea as the `4` GTNL puts on its
  controller hatch.
  - `TIER_HULLS` is indexed by GT tier and the two names that break the pattern are GT's own: `Hull_MAX` is the
    **UHV** hull (MAX was the top tier when it was added) and the MAX tier uses `Hull_MAXV` - TST's controller array
    reads `Hull_ZPM, Hull_UV, Hull_MAX, Hull_UEV, ...` for ZPM..MAX and agrees.
  - `MODULE_MATERIALS` is indexed by module index (IV..MAX): tungstensteel, enderium, naquadah alloy, neutronium -
    the materials GT5U itself builds those tiers' muffler hatches from - then TST's chain cosmic neutronium,
    infinity, transcendent metal, space time, MHDCSM, magmatter.
- Waila (`MTModuleMultiMachineBase#getWailaBody`, keys `machine.module.waila.*` in both languages): the three
  module lines, below everything the bases above put into the body and in the order of the module tooltips -
  耗时减免 / Duration reduction, 耗电减免 / EU discount, then 并行 / Parallel. The values are the ones the tooltips print
  (`MTModuleValues#speedBonusText` / `#euModifierText`, the parallel through `NumberFormatUtil#formatNumber`), the
  colour codes live in the lang text (`§7` label, `§b` / `§a` / `§e` values), and a line is only added while the
  matching module is linked. The flags and numbers travel through `getWailaNBTData`, because the module list only
  exists on the server: the structure scan does not run on a client.
- Parallel field in the machine GUI: `common/gui/base/MTModuleMultiMachineBaseGui` (returned by the base's
  `getGui()`) adds the label plus text field of the linked parallel control module to the terminal, next to the
  machine's own data. It reads `getParallelForGui()` / `getParallelCeilingForGui()`, writes through
  `setParallelForGui()` -> `IMTModule#setParallelFromGui` (new interface defaults, together with
  `IMTModule#getMaxParallel`), and syncs both values with `IntSyncValue`s (`allowC2S`), because only the server
  knows the module. The hatch keeps its own MUI2 GUI (`MTModuleParallelHatchGui`) - `useMui2()` must stay
  overridden there: `MTEHatch#useMui2()` is `false` by default, so a hatch without it falls back to GT's MUI1 GUI,
  while `MTEMultiBlockBase#useMui2()` (and therefore any machine) is MUI2 already.
- The two optional types are only *reported*, the logic stays in the machine: `hasModule(type)`,
  `getModules(type)`, `getModuleCycleNum()` (highest `IMTModule#getCycleNum`) and `hasWirelessModule()`. A machine
  that needs the real loops uses the existing wireless (`MTWirelessMultiMachineBase`) or cross recipe
  (`TickableParallelismAcrossMultiMachineBase` / `ParallelismAcrossMultiMachineBase`) bases - Java cannot extend
  both chains, so a machine that needs modules *and* wireless means lifting the registry into a shared helper
  (composition). No such machine exists yet.
- Design references: TST `ModularizedMachineBase` / `ModularizedMachineSupportAllModuleBase` (modules as hatches,
  static/dynamic controllers push into machine accumulators, `MultiExecutionCoreMachineBase` for the cross recipe
  execution core); the EU and parallel numbers come from TST's controllers and the parallel ceiling formula above
  and the speed table is MessTech's own.
- Harness: `tools/modulebase/verify_module_base.py` (97 source checks plus `ModuleBaseHarness` with 102 runtime
  checks compiled against `tools/modulebase/stubs`). It cross-checks the EU table against TST's `Config.java`, the
  parallel table against the `1 << (2 * (tier - 2))` ceiling and the tier names against `GTValues.VN`. Mutation
  tested: a wrong EU tail, the additive parallel of the older design, a dropped single-parallel rule, a skipped
  `clearModules()` and a false `isOptional()` are all caught. Its speed checks (`SPEED_MULTIPLIER`, the TST ×2
  law, `speedMultiplier`) still encode the old TST table and are stale since the speed module got its own fixed
  table; the harness is only run on request and was left untouched.
- Next steps: wire `MTModuleHatchElement.Module` into a real machine structure, add the composite module (one hatch
  that provides two types - the API already allows it), and decide the numbers of the optional cross recipe and
  wireless modules.

### MTWirelessMultiMachineBase
- State: `ownerUUID`, `EnableWirelessFunc`, `EnableWireless`, `wirelessParallel`,
  `isRecipeProcessing`, `costingEU`, `costingEUText`, `cycleNum`.
- Methods:
  - `isWirelessModeAvailable()`, `isWirelessModeEnabled()`, `isEnableWireless()`
  - `setEnableWireless(boolean)`, `setWirelessParallel(int)`, `getWirelessParallel()`
  - `startRecipeProcessing()` / `endRecipeProcessing()`
  - `initWirelessNetwork()`, `onFirstTick()`, `onPreTick()`
  - `checkWirelessPower(eut,duration,maxParallel)` — simulate before consume
  - `validateWirelessPowerForRecipe(eut,duration,maxParallel)` — shared recipe validation
  - `startWirelessRecipe(...)` — deduct power first, then consume inputs
  - `wirelessModeProcessOnce()` — one wireless recipe cycle
  - `checkProcessingWirelessLoop()` — opt-in multi-cycle wireless processing
  - hooks: `prepareProcessing()`, `getExtraEUCostMultiplier()`, `getWirelessModeProcessingTime()`,
    `getDefaultWirelessMode()`, `setupWirelessProcessingPowerLogic()`
- Waila: writes `wirelessMode` + `costingEUText`; displays `无线模式` / `耗电`.
- NBT: saves `enableWireless`, `wirelessParallel`.

### MTGeneratorMultiBase
- Extends `MTMultiMachineBase` (not the wireless-consumption base).
- For generator multiblocks: keeps `lEUt` positive; `checkProcessing()` returns `GENERATING`
  when the generic ProcessingLogic path succeeds with `lEUt > 0`.
- Wireless EU output is supported and defaults **off**.
  - State: `ownerUUID`, `EnableWirelessFunc` (available, default true), `EnableWireless`.
  - `setEnableWireless(true)` only works when wireless is available and no dynamo/exotic-dynamo
    hatches are present.
  - `onRunningTick()` in wireless mode sends `getCurrentGenerationEUt()` directly to the owner's
    wireless network instead of feeding dynamo hatches.
- Hatches: `addToMachineList()` also accepts dynamo, exotic dynamo (multi-Amp) and laser-output
  hatches; `addEnergyOutputMultipleDynamos()` fills both normal and exotic dynamo lists.
- Waila: wired mode uses GT's normal generation line; wireless mode writes `wirelessMode` +
  `generatedEUText` and displays `无线模式` / `无线发电` with the same comma + scientific formatting
  as `MTWirelessMultiMachineBase`, followed by current-process `总发电量` (compact `(n MAX)`
  suffix when it exceeds MAX-tier voltage; `n = floor(total / 2147483640)`).
- Current-process wireless total is computed as
  `wirelessGenerationPerTick * mMaxProgresstime` (BigInteger), so it shows the complete process
  output instead of a real-time running total.
- NBT: saves `enableWireless`.
- Parallelism:
  - Default `getMaxParallelRecipes()` is `Integer.MAX_VALUE` (subclasses may lower it).
  - `getParallelForAvailable(availableAmount, requiredAmount)` caps by the GT power-panel parallel
    override and by the actual available fuel/input amount.
- Helpers: `getCurrentGenerationEUt()`, `startGenerating(...)`, `setEnergyUsage()` keeps EU positive,
  `getParallelForAvailable(...)` for fuel/input based auto-parallel.

### ParallelismAcrossMultiMachineBase
- Extends `MTWirelessMultiMachineBase`.
- Cross-recipe parallelism: in wireless mode, `checkProcessing()` calls `checkProcessingWirelessLoop()`.
- Each cycle can pick a different recipe; outputs accumulate.
- `setWirelessCycleNum(int)` configures `cycleNum`.
- Wireless network validation inherited from parent (`getUserEU` + `addEUToGlobalEnergyMap`).

### CalculateMultiMachineBase / MTComputingCenter
- `CalculateMultiMachineBase<T>` extends `MTMultiMachineBase<T>`; adds computation-machine hatch lists:
  uncertainty, data input/output, racks (`MTHatchRack`), holders, wireless computation output.
- `MTComputingCenter` modes:
  - 0 = Nano Computing
  - 1 = Research Station
  - 2 = Scanner
- Research/Scanner share computation/packet-loss logic.
- Heat/overclock: `machineHeat`, MAX 10,000,000; cryotheum active cooling; passive cooling when stopped;
  overheat melts rack components; heat locks racks and blocks mode switch.
- Waila values reset when machine not active (manual shutdown included).

## Machines

### MTDTPF
- Extends `MTWirelessMultiMachineBase`.
- Wireless availability: AAF in controller slot + tier >= 5 (`detecttier()`).
- Wireless mode: no-overclock, 0.75 EU/duration modifier, wireless parallel selector.
- Runtime ramp: `running_time` 3600s to full efficiency; decay when idle.
- Recipe validation order (original design):
  1. validate machine tier / recipe voltage;
  2. cap wireless parallel by wireless balance;
  3. `validateWirelessPowerForRecipe(...)` — insufficient -> return insufficient power;
  4. `onRecipeStart` -> `startWirelessRecipe(...)` — deduct power + consume inputs -> start.

### MTNQDAFReactor
- Extends `MTGeneratorMultiBase` (generator + optional wireless output).
- Structure uses Naquadah-fuel reactor casings/coils; no energy input; dynamo or wireless.
- Work efficiency: starts 100%, +1% per 30 s running, max 400%; a fuel batch burns
  `baseDuration * efficiency / 100` ticks. Idle decay follows DTPF/Plasma Forge style, floor 100%.
- Fluid mechanics copied from Large Naquadah Reactor:
  - Liquid Air: 1,000,000 L/s while running.
  - Coolant: IC2/Super Coolant/Cryotheum/Tachyon -> 105/150/275/500% output.
  - Excited liquids: Caesium/U-235/Naquadah/Atomic Separation Catalyst/Space ->
    2/3/4/16/64x output and fuel usage.
- Coil tier I-IV gives 1x-4x EU/t output multiplier.
- Auto parallel: fuel amount / per-batch fuel requirement decides parallel (capped by the GT
  power-panel parallel override); each parallel multiplies fuel consumption, EU/t, and byproducts.
- Spacetime: input molten spacetime to freeze efficiency decay for 30 s; cost doubles per dose,
  starts at 1 L/s (one drain per dose), max 1,073,741,824 L/s. It does **not** pause efficiency growth.
- No dynamo hatch in `checkMachine` -> auto enables wireless output mode (`MTGeneratorMultiBase`).

### MTReactor (nuclear reactor)
- Generation: `EU/t = core output × 5 × IC2's balance/energy/generator/nuclear` (`MTReactor#getEuPerOutput()`).
  GTNH's `config/IC2.ini` sets `nuclear = 5.0`, so one output point is worth 25 EU/t there - the factor IC2's own
  `TileEntityNuclearReactorElectric#getOfferedEnergy()` and GT5U's NEI nuclear fake recipe use, and what makes a
  GT quad uranium rod (12 pulses × `sEnergy` 2 = 24 output) generate 600 EU/t like the single block reactor.
- Controller face: the face art of GoodGenerator's neutron activator - `icons/NeutronActivator_Off` / `_On` and their
  two optional glow layers from the gregtech assets (five animated 16x16 frames each, `frametime: 8`) - over the casing
  that was already under the face (`Casings.AssemblyLineCasing`); the other five faces stay
  `Casings.AssemblerMachineCasing`. Only the overlay is taken from the other machine: the base layer is unchanged, the
  neutron activator would otherwise bring a casing texture of its own (`getCasingTextureForId(49)`) along. The art is a
  12x12 panel inside a two pixel transparent border, so that border is where the casing shows through.
- Tooltip: `machine.mtreactor.tooltip.*`, laid out the way TST writes its machine tooltips - a `desc` line plus a
  flavour line, then the `§6` group headings 结构 / 堆芯 / 发电 / 细节, with `addSeparator()` between the groups. The
  colour emphasis lives in the lang text itself (`§b` for values, `§c` for the danger thresholds, `§e` for the hatch
  tiers), so the Java side is one `addInfo(translate(key))` per line, and the structure block/`toolTipFinisher` follow
  unchanged. Reuse this layout (and key naming) for the other machines.

### Machine tooltips (house style)
- Every machine controller tooltip is one `addInfo(translate(key))` per line with the keys
  `<namespace>.tooltip.desc` / `.flavour`, then two or more `.<group>.header` + `.<group>.<bullet>`
  groups with `addSeparator()` between them, then `.details` / `.details.hint`. The whole look lives in
  the lang text: `§6` heading ending in a colon, `§7· ` bullets (the space is part of the style),
  `§b` values, `§e` tiers/hatches/coils, `§c` dangers, `§a` buffs, `§d` details. No colour code and no
  concatenation on the Java side, no `§r`, no passing one tooltip key's text into another key as an
  argument. The structure block calls stay byte-identical; a machine type that used to be a hardcoded
  English string moves into `<namespace>.machinetype`.
- Tooling in `tools/mttooltip` (gitignored, like every harness): `machines.py` lists the refactored
  machines with their key namespaces and frozen `beginStructureBlock` arguments, `verify_tooltips.py`
  enforces the style above (Java layout, lang text of every line, placeholder/`%` handling, frozen
  structure block, no orphan tooltip key), `merge_fragments.py` splices a machine's pending
  `tmp/mttooltip/frag_<id>.{zh,en}.lang` fragment into both lang files and deletes the old keys that no
  Java file references any more.
- Chinese terminology: proper nouns - blocks, casings, hatches, buses, coils, fluids, items - have to be
  copied verbatim from the GTNH Chinese localisation that ships under `tmp/ZH-CN`: `GregTech_zh_CN.lang`
  plus the per-mod overrides in `config/txloader/forceload/<Mod>[<id>]/lang/zh_CN.lang`
  (`GregTech[gregtech]`, `GregTech[tectech]`, `GregTech[goodgenerator]`, `GregTech[gtnhlanth]`, ...).
  `python tools/mttooltip/zh_terms.py lookup <regex>` searches that library, `... check` fails on a wrong
  variant of a canonical name and on a raw code identifier leaking into the text - it caught
  相干性保持 -> 相干性维持等离子导管, 电磁隔离外壳 -> 电磁隔离机械方块, 和平执行外壳 -> 和平执行机械方块,
  凝聚态转化线圈 -> 凝聚态物质转换线圈, 极寒凛冰 -> 极寒之凛冰, 数据棒 -> 闪存, 纯化水 -> 净化水,
  奇点纳米收容总线 -> 奇点纳米蜂群收容总线, 消音仓 -> 消声仓.

### MTChemicalTwister
- Structure: `A` containment field machine casing, `B` fusion coil block, `C` **heating coils**, `D` chemically inert
  machine casing with the hatches.
- Controller face/casing: both follow **the structure that is formed**, not the mode (see the structure level below).
  Level 1 draws the face of GT's Large Chemical Reactor - `createTextureWithCasing` with the four
  `OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR*` overlays (idle/running plus their glow layers) - on
  `Casings.ChemicallyInertMachineCasing`, i.e. the casing the machine itself (element `D`) and the LCR are built from.
  Level 2 draws the face of GT++'s Quantum Force Transformer (the four `TexturesGtBlock.oMCAQFT*` icon containers) on
  `Casings.BulkProductionFrame` (`blockCasings2Misc:12`), the casing its shell (element `F`) is made of - so a machine
  built as 概率毁灭者 looks like the QFT it took the recipes from. `getCasingTexture()` publishes that casing, so the
  controller is a proper GT face and not a bare casing.
- Element `C` is `GTStructureChannels.HEATING_COIL.use(activeCoils(ofCoil(this::setCoilLevel, this::getCoilLevel)))`,
  i.e. the EBF wiring: the `coil` channel (StructureLib can hint/register the tiers with it), the active-coil
  bookkeeping for the coil upgrades, and GT's coil element, which rejects a ring of mixed coil levels.
- Coil/heat API: `getCoilLevel()` / `setCoilLevel(HeatingCoilLevel)` (the pair `ofCoil` calls, as in the EBF),
  `getCoilHeat()` (the raw `HeatingCoilLevel#getHeat()`) and `getHeatingCapacity()` (the heat the recipes are checked
  against).
- `checkMachine` forgets the coil level, the heat *and* the structure level before it checks the structure, reports a
  missing coil with GT's own `StructureErrorRegistry.COIL_LEVEL_NOT_ENOUGH`, and then computes
  `getHeatingCapacity() = getCoilHeat() + 100 * (GTUtility.getTier(getMaxInputVoltage()) - 2)` - the EBF formula, so
  MV energy is the neutral tier: a cupronickel ring on LV energy gives 1701 K, on MV energy 1801 K.
- Structure level 2 (概率毁灭者): `structure_tier2` is registered as the piece `tier2` (same bounding box and the same
  controller spot as level 1, so the `8, 8, 0` offsets work for both). Its letters are **F-J**, not the comment's A-E:
  StructureLib binds one element map to the whole structure definition, and level 1 already means other casings with
  A-E, so a collision would make both pieces accept each other's blocks (and let hatches be placed anywhere).
  Mapping: F `BulkProductionFrame`, G `QuantumForceTransformerCoilCasing`, **H `SpaceTimeContinuumRipper` + the hatches
  of that piece**, I `SpaceTimeBendingCore`, J `ForceFieldGlass` (only the letters were moved, the geometry is
  untouched). The piece has **no heating coil ring**, so level 1 keeps the EBF heat
  formula and the coil error, while level 2 runs at the fixed `MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT = 12601`
  (Hypogen) and needs no coil. `checkMachine` checks level 2 first, speculatively (`null` error list).
- Machine modes: `totalMachineMode() = 2` (mode 0 化学扭曲, mode 1 概率毁灭者); the base class then enables the GUI
  button, the NBT round-trip and the Waila line, and the screwdriver (`onScrewdriverRightClick`, refused while the
  machine runs) switches. `getRecipeMap()` picks `MTRecipeMaps.qftProbabilityDestroyerRecipes` in mode 1 and
  `MTRecipeMaps.MTChemicalTwisterRecipes` in mode 0; `getAvailableRecipeMaps()` lists both, so NEI shows the machine as
  the catalyst for either page. A mode 1 recipe asks for structure level 2, so switching mode without the tier-2
  structure is reported as `insufficientMachineTier(2)` instead of running; mode 0 still runs on the level 2 structure
  (level 2 >= 1, heat 12601). `construct`/`survivalConstruct` build the piece of the selected mode.
- Structure level in the NEI preview / build hints: the piece is chosen from the trigger's stack size
  (`MTChemicalTwister.STRUCTURE_LEVEL_TIER2 = 2`: tier 1 = level 1, tier >= 2 = level 2), with the machine's mode as
  the fallback. That is GT's usual structure tier switch (the PCB Factory picks its tier 1/2/3 the same way) and it is
  what makes BlockRenderer6343's preview offer its **Tier slider** for this machine: BR6343 finds the tiers by calling
  `construct()` with `stackSize = 1, 2, 3, ...` until the world stops changing (`ObserverWorld#estimateTier`), then
  creates its tier slider up to the highest tier that changed, and rebuilds with
  `survivalConstruct(getBuildTriggerStack(), ...)` / `construct(trigger, false)`. So sliding that slider to 2 shows the
  level 2 (概率毁灭者) shell, and 1 shows the level 1 shell. The machine also offers its own "Heating Coil" channel
  slider (the coil element is channel wrapped), which is why the tier slider's range can go above 2 - any value >= 2
  means level 2. No mixin and no extra dependency on BlockRenderer6343 is involved; the in-game hologram projector
  follows the same rule (scroll it to tier 2 for the level 2 hints).
- The level reaches the client through GT's own update-data byte: `getUpdateData()` returns `mStructureLevel`, and GT's
  `BaseMetaTileEntity` compares it every tick server side (`handleUpdateDataChangeServer`), sends
  `CHANGE_CUSTOM_DATA` when it changed, writes it into the tile data packet (`tileWriteToStream`, read back as
  `receiveClientEvent(..., buffer.readByte() & 0x7F)` when the chunk loads) and hands it to `onValueUpdate(byte)`, which
  stores it. That is the only way the client can know: `MTEMultiBlockBase#checkStructure` returns early on the client, so
  a client copy never finds the level itself. The byte is masked with `0x7F` - keep the value below 128 (here 0/1/2).
- A build hint / preview publishes the level it is showing itself: `construct`/`survivalConstruct` call
  `updatePreviewStructureLevel(trigger)` (a *formed* machine is skipped - its level is the structure-checked one). The
  BlockRenderer6343 preview is a dummy multiblock in a dummy world that is never ticked (`DummyWorld.isRemote` is even
  `false`, so GT considers that copy server side), so its controller can only learn the tier from the trigger: moving its
  Tier slider to 2 now switches both the shape and the face of the previewed controller.
- The 概率毁灭者 pool (`mt.recipe.qft_probability_destroyer`, NEI name `概率毁灭者(QFT)`): an independent copy of
  `RecipeMaps.quantumForceTransformerRecipes` filled by `MTRecipeMaps.populateQftProbabilityDestroyerRecipes()` (called
  from `CommonProxy.serverStarted`, idempotent like the other populate* methods) with **all item and fluid output
  chances at 10000 = 100%**, `metadata(CHEMICAL_TWISTER_STRUCTURE_LEVEL, 2)`, `specialValue(12601)` (QFT recipes carry
  their focus tier in `mSpecialValue` through `GTRecipeMapUtil.SPECIAL_VALUE_ALIASES`, which this machine would read as
  heat, so it is set explicitly), the QFT catalyst turned into a **non-consumed input** (stackSize 0: GT requires it in
  the bus but does not consume it) and the QFT metadata kept for NEI. Page: `maxIO(9, 9, 9, 9)`, `LargeNEIFrontend`,
  height 166, MessTech logo.
- Recipes ask for a level with `MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL` (a `RecipeMetadataKey<Integer>` whose
  `drawInfo` draws "Required Structure Level: N" on the NEI page; a recipe without it needs
  `MTRecipeMaps.DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL = 1`). The processing logic checks the level *before* the
  heat and refuses a too big recipe with `CheckRecipeResultRegistry.insufficientMachineTier(requiredLevel)`.
- `createProcessingLogic()` refuses a recipe whose heat requirement (`mSpecialValue`) is above the available heat with
  `CheckRecipeResultRegistry.insufficientHeat(...)`, and overclocks a recipe that asks for heat with the EBF's
  `.setRecipeHeat(...).setMachineHeat(...).setHeatOC(true).setHeatDiscount(true)`. A recipe without a heat requirement
  (`mSpecialValue == 0`, e.g. the assembler recipes of the template) keeps the standard overclocking.
- The heat is published to the scanner/sensor card/metrics with `getExtraInfoData` under GT's own `GT5U.EBF.heat.s`.
- Registered as `MTItemList.MTChemicalTwister` by `MTMachineLoader` (lang key `machine.largechemicaltwister.name`),
  with its own recipe map `MTRecipeMaps.MTChemicalTwisterRecipes`.
- The pool is a normal GT recipe map, so it registers itself: `MTRecipeMap` -> `RecipeMap.ALL_RECIPE_MAPS`
  (duplicate names throw) plus a default `RecipeCategory`, i.e. a NEI page. The NEI tab name is
  `mt.recipe.chemicaltwister` (lang keys exist in `en_US`/`zh_CN`), its icon comes from
  `neiHandlerInfo(setDisplayStack(MTItemList.MTChemicalTwister.get(1)))`, and the machine becomes the NEI recipe
  catalyst automatically because `MTEMultiBlockBase` is a `RecipeMapWorkable` (through
  `IControllerWithOptionalFeatures` -> `IRecipeLockable`) and `MTChemicalTwister.getRecipeMap()` returns this pool.
  `MTRecipeMaps` is class-initialised from `GTRecipes.loadRecipes()` (FMLLoadComplete), i.e. before NEI loads its
  plugins when a world is entered, so the category is registered in time. The pool uses
  `neiSpecialInfoFormatter(HeatingCoilSpecialValueFormatter.INSTANCE)` so NEI shows the heat requirement exactly like
  the EBF page.
- NEI page layout: the map uses GT's `LargeNEIFrontend` with `maxIO(15, 15, 15, 15)` (a 3 x 5 slot grid per block),
  `logo(MTRecipeMaps.MT_LOGO)` and `setShiftY(8).setHeight(240)`, i.e. the layout family the Large Chemical Reactor and
  the Plasma Forge use - item inputs top left with the fluid inputs *below* them (y = 8 + item rows * 18) and item
  outputs top right with the fluid outputs below them. With 5 item rows and 5 fluid rows the background is 170x190
  (items y = 8..80, fluids y = 98..170) and the description text is drawn below it, so the handler needs ~240.
  `MT_LOGO` is the GregTech/ModularUI flavour of the MessTech logo texture
  (`UITexture.fullImage("messtech", "gui/picture/mt_logo")`); `NanoScaleFoundry24PoolFrontend` reuses the same
  constant. `LargeNEIFrontend` only overrides the logo *position* (80, 62), not the texture.
  The default frontend must not be used here: `UIHelper.getFluidInputPositions` pins every fluid slot to the fixed row
  y=62 while `UIHelper.getItemInputPositions` grows the item grid downward from y=6, so with a big `maxIO` (the old
  `18, 18, 12, 12`) the item slots and the fluid row are drawn on top of each other. `maxIO` is display-only ("does not
  actually restrict the number of items that can be used in recipes"), but it does size that phantom grid, so it has to
  be raised together with any recipe that outgrows it. The geometry was checked against both layouts as the GT
  sources build them: the old config overlaps, the new one does not, everything fits the background and the text
  area, maxIO is the requested 3 x 5 grid, and the logo is ours.
- Recipe classes: the pool's recipes live in `com.MessTech.common.recipe.MTChemicalTwisterRecipes`
  (`addChemicalTwisterRecipes()`, called once from `GTRecipes.loadRecipes()` at FMLLoadComplete) plus
  `loadRecipePostInit()` (called once from `CommonProxy.postInit()`) for recipes that have to wait for other mods (the
  H2O2 one). Every builder grabs the pool as `RecipeMap<RecipeMapBackend> MT = MTRecipeMaps.MTChemicalTwisterRecipes;`
  and ends with `.addTo(MT)`. The whole pool was audited: exactly one call site (no double registration), all builders
  added to `MT`, duration/EU/t/heat/structure-level present on every recipe, no >64 stack clamped by the stack
  factories, no >64 stack inside a plain `itemInputs`, every recipe fits the NEI grid and no two recipes share the
  same input signature.
- One-step platinum-group-metal recipe in that pool (the first builder of `addChemicalTwisterRecipes()`): 45
  `WerkstoffLoader.PTMetallicPowder` + NaOH/saltpeter/zinc/calcium + 81 potassium disulfate dust (the chain burns
  11579 mB of the molten form; 1 dust == 144 mB, so it is fed as dust to keep the fluid list short) + ammonia/HCl/HNO3/
  CO/salt water -> 32 Pt, 21 Pd, 10 Ir, 24 Ru, 7 Rh, 1 Os **plus the whole net byproduct list of the line** (every
  species nothing in the chain consumes again): 162288 mB chlorine, 144737 mB NO2, 51265 mB water, 30000 mB calcium
  chloride (fluid), 10338 mB ethylene, 3333 mB steam, 2105 mB molten potassium, and 172 sodium nitrate / 69 zinc
  sulfate / 48 calcium chloride / 22 salt / 10 + 10 PGS residue dust. At `RECIPE_IV` (7680 EU/t) / 256 s / heat
  requirement 2701 K / required structure level 1 (`.metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)`).
  Werkstoff ingredients must be built with `Werkstoff#get`; `GTOreDictUnificator.get(prefix, werkstoff, amount)` looks
  the ore dictionary up by `Werkstoff#toString`, which a Werkstoff does not override, so it returns `null` silently.
- Stack sizes above 64 go through `GTUtility.copyAmountUnsafe(amount, stack)` (that is exactly what it is for -
  `copyAmount`/`Materials#getDust` clamp to 64) **and the builder must be `itemInputsUnsafe(...)`**: the plain
  `itemInputs(ItemStack...)` re-copies every input through `GTUtility.copyAmount` (`fixItemArray(inputs, false)` ->
  `GTOreDictUnificator.setStackArray(.., false, ..)`), which would silently clamp 124 back to 64, while
  `itemInputsUnsafe` is the `copyAmountUnsafe` branch. Outputs are stored as they are (`itemOutputs`), so 172/69 survive
  there. `GTRecipe#buildItemInputCache` merges duplicate inputs of the same type by summing, so the old "two 62 stacks"
  form matched identically, but NEI draws one slot per recipe input entry, i.e. it showed two slots; a single unsafe
  stack keeps every ingredient on one NEI slot (NaOH 124, K2S2O7 81, sodium nitrate 172, zinc sulfate 69).
- That recipe is the *net* stoichiometry of the whole GT5U platinum line (`bartworks` `PlatinumSludgeRecipes`), not a
  guess: every recipe of the line was transcribed as a stoichiometric vector, all cyclic intermediates are balanced
  exactly, everything the line produces but never eats again is dropped, and the remaining free directions were fixed
  by requiring a physically realisable steady state (no negative recipe runs). The numbers were re-derived from the
  chain and cross-checked against the Java recipe, the pool registration and the heat semantics.
- Notes on the derivation: Platinum Metallic Powder is the only metal feed - the Pt line's "palladium enriched
  ammonia" byproduct carries the palladium, so no Palladium Metallic Powder (palladium ore) is needed. The Pd line's
  circuit-2 recipe (`PDAmmonia` -> `PDSalt`) is deliberately excluded: it turns the loop into a palladium printer
  (ammonia in, palladium out), which is not the intended flow and would make the recipe ratio meaningless. Sifter
  yields are taken at their expected value (0.95). The final amounts are rounded from the exact ratio (worst case
  6 %, rhodium 6.6 -> 7) and the sodium hydroxide is written as two 62 dust stacks because `Materials#getDust` caps a
  stack at 64.
- Heat: the recipe asks for 2701 K (Kanthal), and at IV energy the machine reaches 3001 K, so a Kanthal ring is the
  minimum coil; a cupronickel ring at IV only reaches 2101 K and is refused. The IV EU/t of the recipe itself needs an
  IV energy hatch (the recipe search only returns recipes the machine's voltage can pay for).

### MTHugeChemicalReactor (巨型化学反应釜)

- The first machine on the module base: it runs the **Large Chemical Reactor's pool**
  (`RecipeMaps.chemicalReactorRecipes`) and enables **perfect overclock** (`isEnablePerfectOverclock()` returns
  `true`, so `MTMultiMachineBase#createProcessingLogic` calls `setOverclock(4, 4)` - EU/t x4 and duration /4 per
  overclock, which leaves the total energy of a craft untouched).
- It has no bonus of its own: the `getBase*` hooks of `MTModuleMultiMachineBase` stay at 1, so `getSpeedBonus()`,
  `getEuModifier()` and `getMaxParallelRecipes()` are exactly what the linked modules supply (the speed and EU
  modules multiply, the parallel module replaces). At most one parallel control module is taken (`addModule`), any
  number of speed and EU modules.
- The module slot is element `'B'` of the structure: its `HatchElementBuilder.atLeast(...)` list ends with
  `MTModuleHatchElement.Module`, whose adder is `MTModuleMultiMachineBase#addModuleHatchToMachineList`, so the
  structure scan links the hatch and refuses a module the machine does not take (or a second parallel module).
- `checkMachineStructure` (the base's hook - `checkMachine` itself is final) forgets the coil level first and then
  checks the piece plus `checkHasAnyInput/Output/Energy`; the base clears the module list before the call, so both the
  coil and the modules are properties of the current structure.
- The coil band (element `'A'`) is
  `GTStructureChannels.HEATING_COIL.use(activeCoils(ofCoil(this::setCoilLevel, this::getCoilLevel)))`: a heating
  coil of **any** tier is accepted, the level is kept in `mCoilLevel` (`getCoilLevel()`) and published as the coil
  sub channel - that channel is what gives the hologram preview and BlockRenderer6343 the coil slider. No recipe of
  the chemical reactor pool asks for the level, so the band is a structure requirement, not a heat source.
- Tooltip: house style, every line one `addInfo(translate(key))` with the text and colours in the lang files
  (`machine.hugechemicalreactor.machinetype`, `machine.hugechemicalreactor.tooltip.*`: 配方 / 模块 / 结构 groups plus
  the `details` pair). The structure it describes is the written 5x5x5 shape: a shell of chemically inert machine
  casing that hosts the hatches, a cross of PTFE pipe casing in the middle of the chamber and 8 heating coils on the
  face opposite the controller.
- Face: the controller wears the Large Chemical Reactor's face (idle and running, each with its glow layer,
  `Textures.BlockIcons#createTextureWithCasing` + `ICasingTextureProvider#getCasingTexture` =
  `Casings.ChemicallyInertMachineCasing.getCasingTexture()`), so it looks like the reactor it runs, on the casing of
  its own shell - the same face the Chemical Twister's level 1 uses.
- Crafted in the **assembler** (`RecipeMaps.assemblerRecipes`, in `GTRecipes#loadRecipes`, right after the Chemical
  Twister): 64 `Machine_Multi_LargeChemicalReactor`, 4 `Machine_IV_ChemicalReactor`, 4
  `Casing_Pipe_Polytetrafluoroethylene`, 8 `Casing_Coil_Cupronickel` (the level 1 coil), 16 IV circuits
  (`new Object[] { OrePrefixes.circuit.get(Materials.IV), 16 }`, the form GT5U itself uses for a tier's circuit) and
  18432 L molten PTFE (`Materials.Polytetrafluoroethylene.getMolten(144 * 128)`), integrated circuit 17,
  `eut(RECIPE_IV)`, `duration(MINUTES * 2)`, one Huge Chemical Reactor out. The PTFE pipes and the coils are the two
  ingredients its own structure is built from.
- Registered as `MTItemList.MTHugeChemicalReactor` by `MTMachineLoader` under `MT_ID + 34` with the lang key
  `machine.hugechemicalreactor.name`. Its brand line names no author:
  `AuthorDynamic.registerAddon(AuthorDynamic.MODULE_PROJECT, () -> translateToLocal("messtech.moduleProject"), ...)`
  prints `添加模组: ModularProject` with the word wearing the modular animation, see `MTModuleProjectText`.

### MTComputingCenter
- 3 modes, screwdriver switches, blocked while active or heat present.
- Mode 0: racks produce computation; energy = 1A UEV + 1A UEV per 1,000,000 computation (ceil).
- Mode 1: research station; holder + data stick; consumes nothing until completion; packet-loss logic.
- Mode 2: scanner; same research-style logic; `RecipeMaps.scannerHandlers.findRecipe(...)`;
  required computation = `researchTime * 2^(tier-1)`, `eRequiredData = 1`,
  EU = `max(|eut|, TierEU.RECIPE_UV)`.
- Wireless computation output via `WirelessComputationPacket.uploadData(...)` with real world tick.
- Waila mode uses string mode tag.

### MTAssFactory
- Extends `MTMultiMachineBase` (not wireless).
- Modes:
  - 0 = Component Assembly Line: generic `ProcessingLogic`; recipe casing tier (`mSpecialValue`) limited
    by energy hatch tier (`getInputVoltageTier()`).
  - 1 = Assembly Line: data-stick / Data Access; LevelTier 2 required; unordered input matching via
    standard `ProcessingLogic` (not original ordered AL).
- Structure `F` accepts Data Access hatch.
- Independent Assembly Line recipe map `MTRecipeMaps.assFactoryAssemblyLineRecipes`: one **fake** recipe per
  `RecipeAssemblyLine` definition for NEI (alternatives kept, so NEI cycles them in one slot) plus one **real,
  hidden** recipe per input combination for actual matching. See `docs/GT5U-NOTES.md` § 11.6.
- Front texture: Advanced Molecular Casing base + Quantum Force Transformer face overlay.
- Tooltips mention modes, data access, energy tier limit.

### MTInventoryInputBusME / MTInventoryInputHatchME
- New ME hatches registered as `32407` (item bus) and `32408` (fluid hatch).
- They extend the GT5U advanced stocking ME bus/hatch classes, so they use the advanced GUI with the
  auto-pull toggle (only the first 16 entries are shown in the GUI).
- In auto-pull mode they snapshot the whole AE storage list at every `startRecipeProcessing()`; every
  distinct ME item/fluid type meeting the configured minimum stock size/amount is exposed to the
  multiblock during one recipe check. This mirrors the original GT5U stocking hatch's per-recipe-check
  pull notification, so machines can be fed continuously.
- `autoPullRefreshTime` continues to govern the parent GT5U GUI's first-16 auto-refresh; it does not
  throttle the per-recipe-check full snapshot.
- In manual mode (auto-pull off) they behave like the normal GT5U stocking bus and only expose the
  manually configured 16 slots; no automatic full-network pull happens.
- End-of-processing extracts only the consumed difference from AE (`endRecipeProcessing`).
- Registered in `MTMachineLoader` and `MTItemList`.

### MTWirelessVacuumConveyorInput / MTWirelessVacuumConveyorOutput
- Registered as `32409` (wireless input) and `32410` (wireless output).
- Extend the normal GT5U VCI/VCO classes so existing NAC module structure code accepts them.
- Wireless pairing uses string frequency + optional owner UUID (private), mirroring advanced wireless
  redstone covers. The hatch dye colour is also part of the match, so NAC colour routing from VCI to
  VCO is preserved. Uncoloured hatches (`color == -1`) do not form wireless links.
- A dedicated registry tracks all wireless hatches because `VacuumFactoryGrid.vertices` only contains
  elements that already have an edge.
- `getNeighbours()` returns every registered wireless hatch with the same colour/frequency/private key.
- `canConnectOnSide()` is false so normal Vacuum Conveyor Pipes cannot connect to these hatches.
- The wireless input overrides `onPostTick()` to allow cross-NAC output extraction (no `mainController`
  equality requirement) while keeping the original 1:1 output rule. It queries the dedicated registry
  directly instead of depending on `VacuumFactoryGrid` edge formation, so isolated wireless pairs still
  transfer reliably.
- Same GUI as the GT5U vacuum hatch plus frequency/popup and private toggle controls.

### MTWirelessBeamlineInput / MTWirelessBeamlineOutput / MTWirelessBeamlineAdvancedOutput
- Registered as `32470` (input), `32471` (output) and `32472` (filtered output). They extend GT5U's
  `MTEHatchInputBeamline` / `MTEHatchOutputBeamline` / `MTEHatchAdvancedOutputBeamline`, so every beam machine
  (LHC, beam splitter, beam crafter, target chamber, LINAC, source chamber, synchrotron, beam mirror, beam
  stabilizer) accepts them unchanged: the structure adders and `BeamHatchElement` only test `instanceof` /
  `mteClasses()`, so a subclass is accepted. Caveat: the machines that build their slots with
  `buildBeamline*Hatch(...)`/`hatchId(...)` (LHC, beam splitter, beam crafter, LINAC, beam mirror,
  stabilizer) filter the *hologram* by MTE id, so a correctly placed wireless hatch is flagged by the
  "show errors" channel and autoplace can only pull the wired hatch from inventory. The structure itself
  still validates and the machine runs; place these hatches by hand.
- The pairing key is **only the hatch dye colour** (`getBaseMetaTileEntity().getColorization()`, where `-1` means
  unpainted and never links; the value is re-read live, so repainting re-pairs immediately).
  `WirelessBeamlineUtil` keeps a dedicated registry, because two isolated hatches have no beamline pipe edge
  that could carry the connection; pairing therefore works across machines and dimensions **as long as both
  chunks are loaded** - a chunk unload unregisters the hatch (`onUnload`), so an unloaded counterpart counts as
  "no counterpart" and the beam is dropped instead of being pushed into an unloaded tile entity.
- 1:1 is enforced at push time instead of being assumed: the colour channel must contain **exactly one output and
  exactly one input**, otherwise the packet is dropped and never handed to an arbitrary hatch. `moveBeam()`
  replaces the wired hatch's pipe walk and is invoked from the same `MOVE_AT` hook (`tick % 20 == 4`), so the
  transfer cadence is identical to a wired beamline.
- `canConnect()` is false on both sides, so beamline pipes neither connect to nor render a connection for them.
  The wired output hatch does not consult `canConnect` when it walks its line of sight, so the wireless input
  additionally overrides `setContents()` and only accepts the packet its own 
  `WirelessBeamlineUtil.moveBeam()` is handing over at that moment: a wired hatch parked in front of the
  wireless input cannot inject a beam that bypasses the colour channel.
- The input keeps GT's inherited expiry logic: a beam that is not refreshed within one move tick is dropped, so
  repainting a hatch or breaking the pair stops the machine instead of caching a stale particle stream.
- The filtered variant inherits the LHC/beam splitter particle blacklist: the machine writes `acceptedInputMap`
  and `dataPacket`, and GT5U's ModularUI edits the map. Nothing about the filter was changed, only the transport.
- Scanner info (`getInfoData()`) gains two lines and stays inside the eight documented by
  `IGregTechDeviceInformation`: the colour name (GT5U's `GT5U.infinite_spray_can.color.*` key with a `Dyes` name
  fallback) and the live link state (unpainted / no counterpart / conflict / linked). They are emitted through
  `IGregTechDeviceInformation.encode(...)` / `translatable(...)` - or as a bare lang key - so the client
  translates them in its own language, and the registry state itself stays server-side only.
- Lang lives in `machine.wirelessbeamline.*`. The hatches carry `@IMetaTileEntity.SkipGenerateDescription`
  because their description is handed straight to the tooltip: without the annotation GT5U dumps one instance's
  description into `GregTech.lang` at client setup, which is why the older `MTWirelessVacuumConveyor*` tooltips
  show a frozen frequency line.
- Crafting recipes live in `GTRecipes.loadRecipes()`, one assembler recipe per hatch, each built on its wired
  counterpart (`LanthItemList.LUV_BEAMLINE_INPUT_HATCH` / `LUV_BEAMLINE_OUTPUT_HATCH`, filtered variant on
  `ItemList.AdvancedBeamlineOutputHatch`) and reusing the wireless vacuum conveyor kit verbatim: AE2
  `item.ItemMultiMaterial` 8:47 x8, `ItemList.WormholeGenerator`, UIV sensor (input) or emitter (output) x4,
  UIV circuit x4, `wireGt16` SuperconductorUIV x64 and the advanced redstone receiver (input) / transmitter
  (output) cover, on `MUTATED_LIVING_SOLDER` (input) or `Lubricant` (output), UIV, 2 minutes. The kit is not
  retiered to the hatch's own LuV/UV tier, so the wireless upgrade is priced as a UIV build.

### MTNanoScaleFoundry and the 24 pool
- Registered as `32411`, extends `TickableParallelismAcrossMultiMachineBase`, 3x3x3 structure.
- 11 normal threads/circuits 1-11 map to the 11 normal NAC pools; 24 pool is intentionally not a
  machine thread yet.
- Binding: a controller-slot circuit (1-11) selects one active pool; with the controller slot empty,
  each input bus circuit slot can bind that bus to its own pool so several pools run simultaneously.
- `checkProcessing()` now keeps the machine on a one-second GT cycle and calls the thread scheduler
  inside the standard `checkRecipe()` wrapper, so `startRecipeProcessing()` / `endRecipeProcessing()`
  are active for ME input buses/hatches. `lEUt` is set from running task totals so GT drains energy
  normally.
- Input-bus circuit numbers are snapshotted in `onPostTick` before GT recipe processing starts,
  because ME buses move their circuit to a virtual offset during recipe processing.
- Each thread currently runs one active recipe at a time.
- Board Processor uses per-thread internal immersion tanks (`BoardTankState`), not direct recipe fluid
  depletion.
- Waila now sends per-thread name/index/active/progress/EU/parallel plus first-task output item
  names/counts; body prints progress bar then output item lines.
- `MTRecipeMaps.nanoScaleFoundry24PoolRecipes` is a separate one-step NEI pool
  (`mt.recipe.nanoscale.pool24`, display stack = circuit 24), using `LargeNEIFrontend`.
- `populateNanoScaleFoundry24PoolRecipes()` recursively flattens every original Assembly Matrix
  recipe through Assembly Matrix + all module pools down to `CircuitComponent.realComponent` real
  inputs. It keeps the whole Crystal/Wetware/Bio/Optical line (Processor/Assembly/Supercomputer/
  Mainframe) plus the independent special circuits Pico/Quantum/Planck.
  Board Processor fluid inputs are removed (machine-internal/NEI display only); other module fluids and
  Assembly Matrix fluids are kept; duplicate item/fluid inputs are merged and recipes are deduplicated.
- Measured max IO: 47 distinct item inputs / 18 distinct fluid inputs (PlanckCircuit); the map is
  declared as `maxIO(48, 1, 18, 0)`.
- 24 pool duration/EUt are informational only until the actual 24 machine mode is implemented.

### Blocks
- `AssMatrixBlock` (Tier 1) / `AdvAssMatrixBlock` (Tier 2).
- Static helpers: `getBlock()`, `getItem()`, `getItemStack()`, `getItemStack(int)`.
- Added to `MTItemList` as `AssMatrixBlock`, `AdvAssMatrixBlock`.

## GT5U API knowledge

- Upstream `MTEMultiBlockBase#getWailaBody` reads `tag.getString("mode")`. Write mode as a string name,
  not integer, to avoid `运行模式: 0/1`.
- `ProcessingLogic#findRecipeMatches` uses `findRecipeQuery`; fake recipes are not found.
- NEI `GTNEIDefaultHandler` reads `getRecipesByCategory(defaultCategory)`. Re-tag copied recipes.
- `MTEHatchDataAccess#getAssemblyLineRecipes()` reads data sticks in the hatch.
- Original Assembly Line is ordered by input bus slots; unordered AL must use `ProcessingLogic` /
  `getStoredInputs`.
- `MTEHatchInputBusDebug` phantom inventory: `getStackInSlot()` returns copies with huge stack size;
  standard `getStoredInputs()` / `depleteInput()` handle it.
- `COAL_CASING_TIER` aliased to `GTRecipe.mSpecialValue`.
- `RecipeMaps.scannerFakeRecipes` / `assemblylineVisualRecipes` are visual/NEI pools.
- Wireless network helpers: `processInitialSettings`, `getUserEU`, `addEUToGlobalEnergyMap`.
- Safe wireless order: validate balance first, then deduct + consume.

## Own-code conventions

1. Always reuse existing GT methods (`depleteInput`, `ProcessingLogic`, `getStoredInputs`,
   `findRecipeQuery`) instead of manual inventory/fluid manipulation.
2. Waila mode display uses string `tag.setString("mode", getMachineModeName(...))`.
3. Real recipes for processing; fake recipes only for NEI.
4. Re-tag copied recipes to the target map's default `RecipeCategory`.
5. Registration stays in one path: `postInit -> MTMachineLoader.loadMachines()`.
6. Do not commit randomly; `git fetch origin` was requested, not `git commit`.
7. A borrowed face art keeps the machine's own casing as the base layer: `MTNQDAFReactor` draws the Antimatter
   Generator's fusion overlay on Naquadah Fuel Refinery Casing, `MTReactor` the neutron activator art on the casing it
   already had. Never import the donor's casing with the overlay. When the *machine itself* changes structure, though,
   the layer may change with it - `MTChemicalTwister` draws the QFT face on the BulkProductionFrame, which is the casing
   its level 2 shell is built from, so the look stays self-consistent.
8. `HatchElementBuilder#casingIndex` only accepts values > 0, and `Casings#getTextureId()` throws for every casing that
   is declared with `-1` (the GT++ casings such as `SpaceTimeContinuumRipper`, `ForceFieldGlass`, `BulkProductionFrame`,
   `SpaceTimeBendingCore`, `NeutronShieldingCore` ... have no entry in the GT casing texture pages). Passing such a
   `textureId` happens in the structure definition's static initialiser, so it fails mod loading with
   `ExceptionInInitializerError: IllegalArgumentException` (this crashed once in `MTChemicalTwister`).
   Use `TAE.getIndexFromPage(page, slot)` instead: GT++ registers its casings into the casing texture pages at
   `64 + page * 16 + slot` (e.g. `TAE.getIndexFromPage(0, 10)` = 74, the index GT++'s Quantum Force Transformer uses
   for the hatch host of the SpaceTimeContinuumRipper). A texture-less casing is still fine as a plain
   `.asElement()` or as the `buildAndChain` block - only the hatch texture index needs a real one.
   A casing with `textureId == -1` usually has **no** registered slot of its own either (e.g. `BulkProductionFrame` =
   `blockCasings2Misc:12`, and `GregtechMetaCasingBlocks2` skips meta 12, so
   `getCasingTextureForId(TAE.getIndexFromPage(1, 12))` would return null). For those, copy the block with
   `TextureFactory.of(casing.getBlock(), casing.getBlockMeta())` instead of looking up an id - that is what the level 2
   controller casing does.
9. An `IIconContainer` created lazily is never stitched: `TexturesGtBlock.CustomIcon` adds itself to
   `GregTechAPI.sGTBlockIconload` in its constructor and GT only walks that list in `BlockMachines#registerBlockIcons`
   during the icon load phase. Reading such a field inside `getTexture(...)` (i.e. on the first frame) therefore leaves
   it `null`, so icon containers that are not already forced by the donor mod must be held in `static final` fields of a
   class that is loaded while the mods load (our machines are, `MTMachineLoader` is called from `postInit`, and
   `postInit` runs before the client's first resource reload). `MTAssFactory` reads `TexturesGtBlock.oMCAQFT*` inline -
   that only works because the class is loaded early by such a field, so do not copy that part of it.
10. A machine state that only changes how it renders belongs in `getUpdateData()`/`onValueUpdate(byte)` rather than in a
   custom packet: GT compares that byte every tick (`handleUpdateDataChangeServer`), sends `CHANGE_CUSTOM_DATA` when it
   changes and writes it into the tile data packet, so it survives a chunk reload for free. Values are masked with
   `0x7F`, and multiblock structure checks never run on the client - the client has to be told.
11. Verify with the build, not with harnesses: `gradlew compileJava` is the check for a change, and the user does
    not want harness work for it - do not add new `tools/**` verifiers (or extend the existing ones) unless asked,
    and do not run the `tools/**` sweep. The wider `gradlew spotlessApply spotlessCheck checkstyleMain
    processResources` is for when a full build is asked for or when the work is being handed over as finished. The
    existing harnesses stay in `tools/**` and are only run on request - their results are not part of the normal
    loop any more. (This supersedes the earlier "pick the harnesses by the file that was touched" rule; see also
    `AGENTS.md`.)

## Known pending / open items

- The module system (`MTModuleMultiMachineBase`) has its three standard hatches with TST's values and a
  `IHatchElement`, but no machine uses it yet: the module slot still has to be wired into a real structure, and the
  composite plus the optional cross recipe / wireless modules are still open, see its section above.
- Overclock GUI / button sizing may still need visual verification.
- `ParallelismAcrossMultiMachineBase` currently has a trivial `getMaxParallelRecipes()` fallback;
  subclasses should override.
- Generic `wirelessModeProcessOnce()` order may differ from strict "validate before consume" for some
  machines; DTPF already implements the strict order in its own processing logic.

## SpaceModule Infinity machines (latest)

- `SpaceModulePumpInfinity` / `SpaceModuleMinerInfinity`
- Extend `SpaceModuleInfinityBase<T>` which extends `ParallelismAcrossMultiMachineBase<T>`.
- Always wireless (`EnableWirelessFunc=true`, `EnableWireless=true`), draws directly from wireless network.
- GUI parallel selector (`SpaceModuleInfinityGui`): default 1, max Integer.MAX_VALUE, synced via `wirelessParallel`.
- Power formula: base 1A MAX (`GTValues.V[14]`), each extra parallel adds 1A UXV (`GTValues.V[13]`).
- `checkProcessing()`: validates wireless balance, deducts cost, sets `lEUt` and 20-tick duration.

### Tick-batched output framework (latest)
- `SpaceModuleInfinityBase` now owns a shared tick-batched output framework:
  - `batchRemainingTasks`, `batchItemOutputs`, `batchFluidOutputs`
  - `startBatchedProcessing()`, `generateOneBatchTask()`, `getMainBatchDuration()`
- A batch runs as one continuous main recipe:
  - total duration = main duration + remaining sub-outputs × 1 tick
  - each tick generates one sub-output and accumulates it
  - final `mOutputItems`/`mOutputFluids` are the accumulated total
- Shutdown is deferred until all sub-outputs finish (`shutdownRequestedDuringBatch`).

### Mixin / elevator mounting (latest)
- New late Mixin infra:
  - `com.MessTech.common.mixin.LateMixinPlugin` (implements `ILateMixinLoader`, `@LateMixin`)
  - `com.MessTech.common.mixin.Mixins` (registry with target-mod filter)
  - `com.MessTech.common.mixin.TargetMod` (`gtnhintergalactic`)
  - `src/main/resources/mixins.MessTech.late.json`
- `MixinTileEntitySpaceElevator` adds a separate `mMessTechModuleHatches` list of
  `ISpaceElevatorModule` and:
  - accepts `ISpaceElevatorModule` in `addProjectModuleToMachineList` (without changing original list)
  - clears/disconnects/charges/connects them like the original modules
  - includes them in `getNumberOfModules` and the module-slot limit check
  - includes active MessTech miners in `getAvailableDataForModules()` computation split

### Pump port status (latest)
- `SpaceModulePumpInfinity` now uses real `SpacePumpingRecipes.RECIPES`; `getRecipeMap()` returns
  `null` because the pump has no GT RecipeMap (custom map only).
- NBT saves/loads all 4 recipe sub-panels (planet/gas/parallel arrays) and batch size.
- GUI: `SpaceModulePumpInfinityGui` has separate Parallel / Planet Type / Gas Type buttons/panels.
- Structure: original module frame (1 wide × 5 tall × 2 deep) with optional casing/output hatch.
- `checkProcessing()`: looks up fluid by `(planetType, gasType)`, validates/deducts wireless EU,
  outputs `fluid * parallel`, 20-tick duration.
- Requires `getMotorTier() >= 5` (Orbital Tier V) in `checkMachine`; otherwise structure error
  `machine.spacemodule.need_elevator_t5`.
- `checkProcessing()` also refuses to run without a connected Tier V elevator.
- Textures now use the original pump module front screen + `OVERLAY_SIDE_PUMP_MODULE` engraving.

### Miner port status (latest)
- `SpaceModuleMinerInfinity` now uses the real `IGRecipeMaps.spaceMiningRecipes` pool.
- Structure: original module frame (1 wide × 5 tall × 2 deep) with optional casing and any of
  Input Hatch / Input Bus / Output Bus / Data Input hatch.
- Requires a local Data Input hatch for computation input; if no packet is present it falls back to
  the parent elevator's shared computation (`getAvailableDataForModule()`).
- GUI: full port of the original GT5U Miner GUI — Filter (64-slot whitelist/blacklist + asteroid
  buttons), Calculator, Utility asteroid list, drone selectors, asteroid info panels, plus the
  wireless Parallel button and Distance/Overdrive/Cycle/Range/Step controls.
- Textures now use the original miner module front screen + `OVERLAY_SIDE_MINER_MODULE` engraving.
- `checkProcessing()`:
  - requires Tier V elevator
  - uses existing `getStoredInputsNoSeparation()` / `getStoredFluidsWithDualInput()` for inputs
  - filters/randomly picks a real space mining recipe by effective distance
  - supports distance cycling (`cycle/range/step`) and overdrive time/bonus modifiers
  - applies Asteroid Outpost computation/plasma discounts when the team project is finished
  - caps parallels by wireless parallel, local/parent computation, plasma, and input stack sizes
  - validates/deducts wireless EU, consumes inputs via `recipe.consumeInput` + `depleteInput` for plasma,
    generates weighted ore outputs and applies whitelist/blacklist filtering
- NBT saves/loads distance, overdrive, cycle/range/step, cycleDistance, whitelist flag, and filter inventory.
- Remaining relative to original: full asteroid info/calculator panels and 64-slot filter grid (GUI
  currently uses a compact 8-slot filter).

### Space apiary modules (太空蜂箱, latest)
- `SpaceModuleApiary` + inner `MK1`..`MK4`, registered as `MT_ID + 73..76` (`MTItemList.SpaceModuleApiaryMK1..4`).
- Four tiers, each one class, re-seated on this GT5U's 16-entry `GTValues.V` (TST asks for tiers 18/25, which do
  not exist here): MK-I UEV (tier 10) 256 parallel / 16 slots / motor tier 1; MK-II UIV (12) 4096 / 32 / 2;
  MK-III MAX (14) 32768 / 64 / 3; MK-IV tier 15 `Integer.MAX_VALUE` / 128 / 4.
- Structure: the usual space-module frame (`main` shape 2 wide x 5 tall, one optional casing plus
  input bus / output bus / input hatch). `checkMachine` needs a connected `TileEntitySpaceElevator` whose motor tier
  is at least `getNeededMotorTier()`, otherwise `machine.spacemoduleapiary.need_elevator_t{1..4}`.
- `checkProcessing_EM()` runs one 100-tick cycle (`CYCLE_TICKS`): queens are pulled from the input bus into the free
  bee slots by `takeBeesFromInputs()` and are **never consumed**; every occupied slot runs once through
  `MTBeeSimulator.simulate(queen, world, t)` with `t = MTBeeSimulator.voltageTierExact(getTier())`, and the drops are
  multiplied by `getMaxParallelRecipes()` (wireless parallel capped by the module's own ceiling).
- Power is wireless only: `GTValues.V[tier] * CYCLE_TICKS * parallel` EU is taken from the player's global energy map
  in one lump via `addEUToGlobalEnergyMap`, then `lEUt = 0` so the machine does not also drain its own hatches.
  `validateWirelessPowerForRecipe` runs first; no energy hatch and no liquid DNA are involved.
- No recipe map: `getRecipeMapImpl()` returns `null` (the same as TST's `TST_SpaceApiary`), the module produces from
  the bee slots and not from a pool.
- Bee slots live in the machine (`ItemStack[] beeSlots`, NBT key `beeSlots`, size 1 each) so they survive a reload.
- Structure hooks: `construct(...)` -> `buildPiece("main", stackSize, hintsOnly, 0, 1, 0)` **and**
  `survivalConstruct(stackSize, elementBudget, ISurvivalBuildEnvironment)` ->
  `survivalBuildPiece("main", stackSize, 0, 1, 0, elementBudget, env, false, true)`, the same pair the miner and the
  pump use. The `ISurvivalBuildEnvironment` variant must build for real: BlockRenderer6343 (the NEI structure preview)
  places the machine in a dummy world and calls it with a **fake player**, and neither `TileEntityModuleBase` nor
  GT's multiblock bases override that variant - delegating to `super` therefore lands in StructureLib's interface
  default, which answers `-2` ("not supported") for a fake player and leaves the preview with nothing but the
  controller.
- Recipes: four Space Assembler recipes (`GTRecipes.addSpaceApiaryRecipes()`), one per tier, `specialValue(1)`
  (inert: the assembler's tier gate reads the `MODULE_TIER` metadata, which neither mod sets, so all four count as
  module tier 1). Each spends 4 stacks of 64 Industrial Apiaries + 4 stacks of 64 upgraded acceleration upgrades, 16 of
  that tier's Field Generator / Conveyor / Robot Arm / Electric Pump, 64 tier circuits, a solder fluid and Honey
  (doubling per tier), at RECIPE_UHV/UEV/UIV/UMV and 20*300*scale ticks.
  - MK-III/MK-IV pay **UU-Matter** (1000*128*scale) and MK-I/MK-II pay **Mutated Living Solder** (16 ingots * scale,
    i.e. 2304 / 4608 mB). TST fills the same pool (`IGRecipeMaps.spaceAssemblerRecipes`) with its `SpaceApiaryT1..T4`,
    and MK-I/MK-II originally spent exactly what TST's T1/T2 spend (same parts, circuit, UU-Matter, honey, EU/t and
    duration, only the output different) - so a Space Assembler holding those components matched both and took
    whichever recipe the lookup yielded first, leaving one mod's module unobtainable. MK-III/MK-IV never collided
    because their circuit differs from TST's (ours UXV/MAX, TST's UMV/UXV). The solder is what makes MK-I/MK-II
    mutually exclusive with TST's - **adding** an item or fluid would not, since a recipe matches as soon as its
    inputs are present. 16 ingots doubling follows the pool's own module ladder (Pump Module MK-I 9 ingots, MK-II 32,
    MK-III 1 stack, `MachineRecipes:279/297/332`) and is a fraction of one GT++ solder batch
    (`RecipeLoaderGenericChem:163` yields 4 stacks + 24 ingots).
- GUI: `SpaceModuleApiaryGui extends SpaceModuleInfinityGui`. The terminal area toggles between the standard status
  text and a scrollable grid of `SlotLikeButtonWidget`s (10 per row); the toggle is the button the GUI adds to the
  right of the panel gap.
  - The grid shows the bee slots the way the Mega Industrial Apiary does: queens sharing a species, secondary species
    and speed allele collapse into **one** button carrying their number (`MTBeeSimulator.speciesKey`, drawn with
    `GuiDraw.drawStandardSlotAmountText`), and the remaining capacity is the single empty button at the end. Buttons
    for entries that do not exist are disabled; a disabled button collapses in its row and an empty row collapses in
    the `ListWidget`, so the grid needs no rebuilding.
  - The apiary has one parallel knob and one bee cycle, so `shouldShowCrossRecipeParallelField()` is `false` and the
    cross-recipe parallel field is hidden.
  - `createButtonColumn` is overridden to drop the controller slot the shared space-module GUI adds. That slot takes
    anything and nothing in this machine reads it, and it was the only shift-click target - so a shift-clicked queen
    landed there instead of in a bee slot.
  - Shift-click is instead served by an invisible one-item **queen buffer** slot (`ItemSlotSH` over a 1-slot
    `ItemStackHandler`, `singletonSlotGroup(SlotGroup.STORAGE_SLOT_PRIO)`, filter = queen and a free bee slot). MUI2
    finds transfer targets through the slot group, so the buffer needs no widget; its change listener moves what it
    receives straight into the first free bee slot.
  - Slot contents are one `GenericListSyncHandler<ItemStack>` over the machine's array (null-safe item serialization,
    change listener invalidating the aggregation) and every edit travels as a single encoded integer
    (`apiaryBeeClick`, `allowC2S`) handled on the server. The encoding is `((slot + 2) << 4) | button | shift`, where
    `slot = -1` means the empty entry and the server resolves it to its first free slot. Click takes the queen to the
    cursor, shift-click sends it to the player inventory, a held queen swaps, right-click inserts a single queen and
    filling continues into the following empty slots, middle-click is the creative pick.
- Liquid DNA is intentionally not used, unlike TST's optional `SpaceApiaryCycleTime` addition.
- Forestry is a hard dependency (`implementation(gtnhDev("ForestryMC"))` in `dependencies.gradle`); simulations go
  through `forestry.api.apiculture.BeeManager`.

## Mess Food (混乱大杂烩)

- Item: `com.MessTech.common.item.ItemMessFood`, registered as `MessFood` in `MTItems`, texture
  `assets/messtech/textures/items/food.png`.
- NBT key `Ingredients` stores the ordered ingredient list (one `ItemStack` with stack size 1 per
  entry), which is also the food's identity.
- Recipe: `com.MessTech.common.recipe.RecipeMessFood` — a custom `IRecipe` appended to the crafting
  manager in `CommonProxy.init()`. It is shapeless but order sensitive: any 2-9 AppleCore foods may
  be placed anywhere in the grid, and the ingredients are recorded in crafting slot order
  (top-left -> bottom-right). Every ingredient must be distinct (item + damage + NBT), so duplicates
  such as apple+apple are rejected. Mess Food itself is excluded, so it cannot be used to craft
  another mess food. Appending keeps every pre-existing recipe's priority.
- Hunger: the sum of the ingredients' AppleCore hunger values. Saturation: the sum of the
  ingredients' saturation increments, converted back into a `FoodValues` modifier. Implemented with
  AppleCore `IEdible#getFoodValues` plus `func_150905_g`/`func_150906_h` overrides.
- Display name: localized base name plus `(ingredient1,ingredient2,...)`, e.g.
  `Mess Food(apple,bread)` / `混乱大杂烩(苹果,面包)`. Key: `item.messtech.messFood.display=%s(%s)`,
  so Spice of Life's journal can tell every variant apart.
- Tooltip: ingredient names one per line, in crafting order.
- Spice of Life compat (late mixins, applied only when `SpiceOfLife` is loaded):
  - `MixinFoodEaten` adds the NBT tag to `FoodEaten.equals`/`hashCode` for Mess Foods only.
  - `MixinFoodHistory` redirects `ItemStack.isItemEqual` to `MessFoodHelper.sameFoodIdentity` in
    `getFoodCountForFoodGroup`, `containsFoodOrItsFoodGroups` and `getTotalFoodValuesForFoodGroup`.
  - Result: every ordered combination has its own journal entry, diminishing-returns counter and
    extra-heart milestone.
- `AppleCore` was added as a required mod dependency (`required-after:AppleCore`).
- Dev smoke test (temporary, already removed): NBT round trip, order sensitivity, hunger/saturation
  sums, 2-9 food recipe matching, identity/hash consistency.

## A Piggy + MTDynamicItemHelper (dynamic items)

- Item: `com.MessTech.common.items.MTItemPiggy`, registered as `MTItemPiggy` in `MTItems`, creative tab
  `tabMisc`, 7 damage variants (one per effect), icons from `pigs/pig` (relative to `textures/items`).
- Helper: `com.MessTech.common.util.MTDynamicItemHelper`. "Dynamic" means: one item whose look (icon +
  renderer) is selected by `ItemStack#getItemDamage()`, so no renderer class is needed per item.
  - `Effect` enum, ordinal == damage value: `NONE` (plain pig), `TRANSCENDENT_METAL`, `INFINITY`,
    `MAGMATTER`, `ETERNITY`, `UNIVERSIUM`, `SIX_PHASED_COPPER`. `NONE` has to stay at index 0, it is the
    fallback for out-of-range damage (old worlds, /give with a random meta).
  - `registerIcons(register, basePath)` registers `<basePath><iconSuffix>` per effect, e.g.
    `pigs/pig` + `TranscendentMetal` -> `textures/items/pigs/pigTranscendentMetal.png`.
  - **Icon names must not start with `items/`**: `TextureMap` resolves registered names as
    `basePath/<name>.png` with `basePath` = `textures/items` (that is the `textures/items-atlas` line in the
    log), so `items/pigs/pig` would look for `textures/items/items/pigs/pig.png`. The same rule applies to
    blocks (`textures/blocks`); `MTFuelRod` (`messtech:FuelRod/...`) and GT's own item icons confirm it.
    A wrong path is silent — 1.7.10 commented out the "using missing texture" error
    (`TextureMap` line 189) and only feeds FML's `trackMissingTexture`, which is why such a typo shows up
    as the purple/black checker board with nothing in the log. `registerIcons` therefore looks every icon up
    through `IResourceManager.getResource` (the 1.7.10 way; there is no `resourceExists`) and logs the warning
    itself.
  - `registerItemRenderer(item)` registers one shared `IItemRenderer`; `getEffect`/`setEffect`/
    `cycleEffect` are server safe, everything icon/renderer related is `@SideOnly(Side.CLIENT)`.
  - `Style` picks the drawing code, each one a copy of the matching GT5U renderer:
    - `TUMBLE`: `TranscendentMetalRenderer` — quad tumbled around (0.3, 0.5, 0.2) by 3.5 degrees per
      client tick, angle from `GTMod.clientProxy().getAnimationRenderTicks()`.
    - `HALO_PULSE`: `InfinityRenderer` — inventory only (GT5U does the same): `Textures.ItemIcons.HALO`
      behind the icon plus a gaussian scaled 60% alpha copy. GT5U wires Infinity, Eternity and MagMatter
      to this renderer; their own texture strip is the animated part.
    - `UNIVERSIUM`: `UniversiumRenderer` — icon first, then gtnhlib's `UniversiumShader` star field
      (`setRenderInInventory()` inside the GUI, `GL_EQUAL` depth + `GL20.glUseProgram` restore outside).
      The shader writes its own colour wherever the icon is opaque and keeps only the icon's alpha
      (`gl_FragColor = vec4(col * shade, mask.a * opacity)` in `universium.frag`), i.e. the icon becomes a
      window into the sky and *everything* the sprite draws ends up behind the stars.
    - `GLITCH`: `GlitchEffectRenderer` — red/cyan ghost copies, 10 ms frames, glitching on frames
      0-40 of every 200.
  - `registerUniversiumOverlay(item, register, basePath)` is the way back from that: it registers
    `<basePath>UniversiumFace` (the pig's `pigs/pigUniversiumFace.png`) and remembers it per item, and the
    `UNIVERSIUM` pass draws it as a third pass, after `UniversiumShader.clear()`/`unbind()`, so the item's
    details land in front of the finished sky. It has to be the *untinted* art: the sky is dark, so a feature
    in the material's own colour would be invisible on it. Items without an overlay render exactly as before.
    Both ends of that map are client only, which is why it lives in the client-only inner renderer class.
  - The overlay is a cut-out of the pig's **features only**, not of its face: the two eyes, the snout with its
    nostrils and the pig's own ears, copied pixel for pixel out of the plain sprite. Every other pixel stays
    transparent, so the star field keeps painting the whole body - an earlier version put a patch of plain pig
    skin around the face, which does dissolve the edge but reads as a sticker over the stars. The ears (the
    pink crescent at 3..5, 7..12 and the pink ring at 14..18, 11..15) need the pass as much as the face does:
    the shader only keeps the icon's alpha, so they would otherwise be swallowed by the body.
  - The eyes are the one thing a plain copy cannot carry. The sky's own background is
    `bgColor = (0.10, 0.225 +- 0.075, 0.30 +- 0.05)`, and the pig's near black eye (luma 0.17) is exactly as
    bright, so a bare dark dot disappears into it. The 2x2 pupils are kept and the 12 pixels around each get a
    pale rim (`#cfe4f2` at alpha 190), which reads whether a star or the plain background sits behind it.
    Contrast holds at every point of the background's pulse, and no plain skin pixel ever creeps back into the
    overlay.
  - GT5U's accessibility switches are honoured: `Client.render.renderTransMetalFancy`,
    `renderInfinityFancy`, `renderUniversiumFancy`, `renderGlitchFancy` fall back to the plain icon.
- Shift + right click cycles the effect. The damage value is written on both sides, the oink is played
  server side (so it is heard once) and the chat line is sent client side (so it uses the client language).
- Right click throws the piggy and does not consume it - the stack comes back untouched - which is why the item
  stacks to **one**: it is a pet and a hat, not ammunition. The projectile itself is unchanged
  (`MTEntityPiggy`, effect in the damage value of the thrown stack).
- The piggy fits into the **helmet slot**: `MTItemPiggy#isValidArmor` answers true for armor type `0`, i.e. the hook
  vanilla's `ContainerPlayer.SlotArmor#isItemValid` asks (`Item#isValidArmor` numbers the slots 0 helmet, 1 chest,
  2 legs, 3 boots - *not* the numbering of `InventoryPlayer#armorItemInSlot`, where the helmet is 3). Without that
  override the slot only takes armour, pumpkins and skulls.
- Wearing it is drawn by `common/entity/MTPiggyHatRenderer` (client only, registered from `ClientProxy.preInit`):
  1.7.10 has no renderer for a plain item on the head - `RenderPlayer#renderEquippedItems` only knows `ItemBlock`
  blocks (the pumpkin) and skulls - so the handler watches `RenderPlayerEvent.Specials.Post`, which is posted at the
  end of exactly that method, i.e. **inside the model of the player**, in the same space vanilla draws the pumpkin
  and the skull in. It reads `inventory.armorItemInSlot(3)` and, if that stack is a piggy, puts the model on the
  head: `event.renderer.modelBipedMain.bipedHead.postRender(0.0625F)` - the head part carries the whole pose of the
  head, its angles are the head's own yaw and pitch - then 8 px (half a block, the height of the head box) up onto
  the top of the head, and finally scales by `SCALE / 0.9375` so the pig is `SCALE = 0.5` blocks tall in the world.
  Because the pose comes from the head part itself, yaw, pitch, the crouch (whose head pivot drops a pixel), riding,
  the death pose and the `0.125` a sneaking player that is not the camera is dropped by all come along by
  themselves; sleeping, dead and invisible players are skipped.
- The three facts that space is made of (all of them read out of the decompiled Minecraft sources, not guessed):
  `RendererLivingEntity#doRender` flips the model (`glScalef(-1, -1, 1)`) before it hands it to the renderer, so
  **`-Y` is up in the world, the face of the wearer points along `-Z` and one unit is a sixteenth of the model**
  (`postRender` takes its `0.0625`); `RenderPlayer#preRenderCallback` scales everything by `0.9375`, which the
  pig divides back out so `SCALE` keeps meaning blocks; and the rotation point of `bipedHead` is the **neck**, not
  the top of the head - the head box grows 8 px up from it. With the half block of `HEAD_HEIGHT` added, the feet of
  the pig end up exactly where the pumpkin and the skull are built around and where the old billboard put them,
  `(24 + 8) / 16 * 0.9375 + 0.9375 / 128 = 1.8823` blocks above the feet (one pixel lower when crouching).
- What stands on the head is `MTDynamicItemHelper#renderOnHead(wearer, stack)`: the sprite of
  `Item#getIcon(stack, 0)` drawn standing on the origin of the matrix the hat renderer set up, facing `-Z`, so a
  player in front of the wearer sees the pig's face. Neither the mirrored X of the model space nor the turn into the
  facing direction flips it - the two cancel out - so the texture is not mirrored. The style of the stack's effect is
  drawn on top of that sprite, which is what makes the hat the item and not a still picture:
  - `TUMBLE` (Transcendent Metal) tumbles exactly like the item does: 3.5 degrees per client tick from
    `GTMod.clientProxy().getAnimationRenderTicks()` about the oblique axis `(0.3, -0.5, -0.2)` - the mirrored copy
    of GT5U's `(0.3, 0.5, 0.2)`, because the model space has Y and Z flipped, which keeps the tumble turning the way
    it does in the hand - about the centre of the sprite.
  - `UNIVERSIUM` runs its three passes again: the sprite, then gtnhlib's cosmic shader over the same geometry with
    the depth function at `GL_EQUAL` (so the stars land on the sprite and not on the head behind it) and finally the
    stack's overlay (`pigUniversiumFace`) in front of the finished sky.
  - `HALO_PULSE` and `GLITCH` stay the plain sprite on the head: GT5U draws the halo, the pulse and the glitch
    ghosts in the inventory only, and the animated strips of Infinity/MagMatter/Eternity animate by themselves. GT5U's
    fancy switches are honoured on the head as well (`isFancyEnabled` falls back to the plain sprite).
  - The items atlas is bound by the helper (no vanilla item pass did that for this draw) and culling is off for the
    single quad, with `GL_ENABLE_BIT` pushed and popped around the whole thing.
- **A Transcendent Metal pig tumbles the wearer with it.** `MTPiggyHatRenderer` also watches
  `RenderPlayerEvent.Pre`/`Post`, which wrap the whole `RenderPlayer#doRender`, and turns the player model by the
  same `MTDynamicItemHelper#tumbleAngle()` about the same oblique axis `(0.3, 0.5, 0.2)`. The pivot is the middle
  of the body: at `Pre` the matrix is still the one the camera left, so the model's own origin is the interpolated
  entity position the renderer is handed (`lastTickPos + (pos - lastTickPos) * partialTicks` minus
  `RenderManager.renderPosX/Y/Z`, and minus `Entity.yOffset` the way `RenderPlayer#doRender` drops it), lifted by
  `TUMBLE_PIVOT_Y = 0.9` - half of the 1.8 blocks a player is tall, the same thing the pig does about the middle of
  its sprite.
  - The push and the pop have to be a pair, and `RenderPlayerEvent.Pre` is `@Cancelable` while its `Post` is only
    posted for a `Pre` that was not cancelled. `Pre` is therefore registered at `EventPriority.LOWEST` - every
    other handler has run, so a cancel is already visible - and `onRenderWorldLast` is the net under the remaining
    case (a cancel from a handler that runs after this one): it pops the leftover push at the end of the world
    render, where that push is still the top of the stack, so the matrix stack cannot leak.
  - The pig keeps its own tumble as well, so on the head the two compose; pinning the pig to the head instead would
    mean dropping the transform in `MTDynamicItemHelper#renderOnHead` for the wearer case. The whole thing is the
    client-side look switch `Config.PIGGY_TUMBLES_WEARER` (default on).
- The thrown piggy is unchanged by all of this: it stays the camera facing billboard of
  `MTRenderPiggy`, scaled to 0.6 and spinning around the view axis.
- Textures: `assets/messtech/textures/items/pigs/pig*.png`, built from the
  GT5U material icons (`assets/gregtech/textures/items/materialicons/...`): the material icon is
  in-painted to full coverage, tinted with the material colour and multiplied by the pig's own shading
  plus a fake top/bottom bevel.
  - Animated materials keep their frame strip and get `{"animation":{"frametime":1}}` as `.mcmeta`:
    `pigInfinity` 18 frames, `pigMagMatter` 8, `pigEternity` 20; `pig`, `pigTranscendentMetal`,
    `pigUniversium` and `pigSixPhasedCopper` are single frame. Animated sprites in the item atlas work
    in 1.7.10; GT5U's own animated material icons do exactly this.
  - The pattern is normalised against the whole strip, never per frame: per-frame normalisation cancels
    an animation whose frames only move, which is what flattened MagMatter (its grey pattern keeps its
    mean brightness). MagMatter also uses `pattern_weight = 1.0` so the flow stays visible on the pig.
  - MagMatter is the one material whose look *is* its grain, so its icon is not averaged 2x2 and blown up like the
    others: `pattern_tile = 2` walks the 16x16 icon across the 32x32 pig 1:1 and mirrored (the second copy runs
    backwards, so no seam shows). That roughly doubles the speckle density - mean neighbour difference 12.4 -> 22.3,
    "edges" 27% -> 47% - while the face stays legible and the sprite is
    about 5% darker from the sharper grain. Every other mode still uses tile 1 with smoothing and their PNGs are
    byte identical to before the change.
  - `pig.png` itself was recovered from the hero render `tmp/pig_hero.png`
    (grid snapped, eyes to 2x2 and both nostrils to 1x2 blocks so the face is symmetric).
  - `pigUniversiumFace.png` is the one icon that is not a material build: it is the plain pig's face (both
    eyes, the snout, the nostrils - copied, not re-tinted) inside a feathered superellipse that the shader is
    not allowed to paint over, and it is what keeps the Universium pig from being a faceless hole into space.
    The patch is a superellipse rather than an ellipse because the eyes sit in the upper right and the snout in
    the lower left of the face, i.e. in two opposite corners; its edge fades over 2 px with a smoothstep so the
    skin dissolves into the stars. The patch covers every eye/nostril/snout pixel at full alpha, stays on the pig's
    silhouette and is feathered, and the eyes stay readable against the face (see
    `tmp/piggen/universium_face_preview.png`, a before/after composite with an approximated star field, since the
    real shader needs the GPU).
- Any other item can reuse the same look by calling `registerIcons` + `registerItemRenderer` and adding
  its own `item.<unlocalized>.<effect>.name` and `messtech.itemEffect.<effect>` language entries.
- The renderers are copies, so the same GL state leaks GT5U has are kept on purpose: the halo layer
  leaves depth/alpha test disabled for the item pass, exactly like `InfinityRenderer` does.

### The thrown piggy

- `com.MessTech.common.entity.MTEntityPiggy`: a plain `EntityThrowable`. `CommonProxy.preInit` registers it
  (`MTEntityPiggy.register()`, entity id 0, tracking 64 / update 10, velocity updates on) and
  `ClientProxy.preInit` gives it `com.MessTech.common.entity.MTRenderPiggy`.
  - `EntityRegistry.registerModEntity` wants the mod object itself, which is what `MessTech.instance` is for.
  - Plain right click throws one (one item is consumed, not in creative mode), shift + right click still
    cycles the look. The effect (the damage value) travels in data watcher id 20 and in NBT (`Effect`), so the
    projectile on the client draws exactly the icon that was held.
  - Impact is vanilla style: the server flags `setEntityState(this, 3)` and every client spawns the heart puff
    and the oink itself. The renderer draws the effect icon as a billboard
    (`TextureMap.locationItemsTexture`, so animated effects animate on their own), scaled to 0.6 and spinning
    24 degrees per tick around the view axis.
- `MTTrueKill` (`common/util`) is the "true kill" behind it. It is a ladder, because every kind of protection
  gives up somewhere else; `MessTech.MT_LOG.debug("[Piggy] true kill of ... needed step N")` reports which step
  did the kill, so a test can be traced in the log.
  1. `attackEntityFrom` with exactly `Float.MAX_VALUE`, damage type `"infinity"`, `setDamageIsAbsolute()` and
     **neither** armour piercing **nor** creative mode. Set based immunity cancels its hurt/attack/death events
     for every damage type *except* that one, and the shielded armour sets escalate on exactly that amount by
     re-attacking with their own internal `ADMIN_KILL` source (which their own death handler skips) — that is how
     those mods keep `/kill` and their own weapons working. Keeping this source plain is essential: with
     `setDamageBypassesArmor()` or `setDamageAllowedInCreativeMode()` their damage adjuster doubles the amount
     instead of escalating, and the escalation never happens. It is also deliberately *indirect*: an indirect
     source has no player as its attacker, so an Infinity Sword in the thrower's hand cannot turn the amount into
     a fixed 300 instead of an escalation. The cost of that choice is that the game credits the piggy, not the
     player, so a mob killed by it counts as "not hit by a player" and drops no loot. Do not "fix" the argument
     order of `EntityDamageSourceIndirect` without checking that trade off again.
  2. The same, but with `setDamageBypassesArmor().setDamageAllowedInCreativeMode()`: catches creative mode
     (`EntityPlayer.attackEntityFrom` needs `canHarmInCreative()`, MC source line 1110) and source filters.
  3. The same, but marked with `setFireDamage()`, repeated `FIRE_ATTEMPTS` (6) times, each time as a hit and, if
     that is refused, as a forced death. Some death gates only open for one specific kind of hit: Witchery's
     vampire (`CreatureUtil.checkForVampireDeath`) dies to fire, sunlight, the void and a wall, and refuses every
     sword, and `ItemVampireClothes.isFlameProtectionActive` *rolls* for a vampire wearing its clothes (one in
     four) instead of deciding it. This is also exactly how Avaritia's infinity sword finishes a vampire
     (`setHealth(0)` + `onDeath(new EntityDamageSource("infinity", player).setFireDamage())`,
     `ItemSwordInfinity.hitEntity`). The hit comes first because it keeps the vanilla path (hurt animation,
     knockback, `recentlyHit` bookkeeping) complete; the forced death behind it is what reaches a target whose
     health a refused death already left at zero, because `attackEntityFrom` returns immediately at that point.
  4. `setHealth(0)` + `onDeath(source)` by hand, i.e. without calling `attackEntityFrom` at all. An entity level
     invulnerability flag, damage caps and custom `attackEntityFrom` overrides cannot intercept this; it is the
     same call GT5U uses in `EIGSeedBucket` for forced kills.
  5. For players that are still alive: everything they carry is taken away (armour + the whole main inventory +
     the optional bauble inventory, looked up reflectively so no extra dependency is needed) and the player is
     hit again. That is what removes set based immunity (the set is no longer complete) and item based "second
     life" protection (the item is no longer in the inventory). Slots are cleared directly and the stacks are
     spawned as `EntityItem`s with a 40 tick pickup delay, because an item tossed with the vanilla helper can be
     handed straight back to its owner by its own handler; going through the bauble inventory (instead of the
     array behind it) also makes the removed item run its own "on unequipped" hook.
  6. Players again: three more death events, which is what counts down single use protection.
  7. Everything else: `setDead()` + `World#removeEntity`. Multi part bosses are resolved through
     `EntityDragonPart#entityDragonObj` first, so the boss is killed and not the part.
  - **The thrower is not exempt.** Throwing the piggy straight up and letting it land on your own head kills you,
    which is the whole point of the item; previously the ladder returned early for `target == thrower`. Vanilla's
    `EntityThrowable` already keeps the thrower safe for the first five ticks in air (`entity1 != getThrower() ||
    ticksInAir >= 5`, MC `EntityThrowable.onUpdate` line 196), so a piggy thrown at your feet still does not blow
    up in your face.
  - **A death counts only when the death path of the target ran.** `MTTrueKill.init()` (called from
    `CommonProxy.preInit`) subscribes to `LivingDeathEvent` at `EventPriority.LOWEST` with
    `receiveCanceled = true`, i.e. it sees the final cancel state of the whole handler chain, and records the
    entity when the event was *not* cancelled. A canceled post *removes* the record again: the death of a player
    posts the event more than once (`EntityPlayerMP.onDeath` -> `EntityPlayer.onDeath` ->
    `EntityLivingBase.onDeath`, only the innermost one decides), and a death gate that rolls per post — the flame
    protection of the vampire clothes does — could otherwise leave a "half dead" record behind and the ladder would
    stop on a player still standing at zero health. The ladder stops on that record, not on the health:
    `EntityLivingBase.onDeath` can be refused (Witchery cancels the death of a vampire player unless
    `CreatureUtil.checkForVampireDeath` agrees) and a death gate can leave a walking entity at zero health
    (Witchery's `EntityVampire` even puts its own health back to `1.0F` and returns). Checking the health instead
    was the reported bug: the first step "succeeded" on a vampire that was still walking. The record is only
    trusted while the health of the entity still agrees with it, so a respawned player or a revived mob is a
    target again instead of being skipped forever, and a dying entity is not killed twice (which would run drops
    and the death event twice). A target that survives the whole ladder is put back to `1.0F` when a refused death
    left it at zero, so a failed kill does not leave a walking corpse behind.
  - **NaN is repaired after every step.** `Float.MAX_VALUE` overflows to infinity in
    `EntityLivingBase.applyArmorCalculations` (MC source line 1192: `damage * (25 - armour)`), and of the two
    absorption bookkeeping values of `damageEntity` (lines 1266-1274) the first is
    `absorption - (infinity - infinity)`, i.e. NaN. From there `Math.max(NaN, 0)` is NaN, so the *next* hit turns
    `health - NaN` into a NaN health, and `MathHelper.clamp_float` passes that NaN straight through. The entity
    then is unkillable for good: `health <= 0` is false for NaN, so not even a normal death check of the game ever
    fires again, and the health bar just looks empty. `MTTrueKill.repair` therefore drops a NaN (or infinite)
    absorption to zero and a NaN health to zero after every hit, after every forced death and before the removal
    step - this is the exact "0 health, `HealF` NaN" state of the report.
  - If every step fails the thrower gets `messtech.piggy.kill.failed` in chat instead of a silent no-op, and the
    target is healed back to `1.0F` if the ladder left it at zero.
  - **The kill is announced by the piggy.** The damage *type* cannot change (see step 1: the armour sets let exactly
    `infinity` through), and the game derives the death message from exactly that type —
    `DamageSource#func_151519_b` builds the key `death.attack.<type>`. As it is, the piggy would be announced with
    `death.attack.infinity`, the key Avaritia's `DamageSourceInfinitySword` uses for its own sword: translating that
    key in the language files of the mod would relabel every infinity sword kill as a piggy kill. The source therefore
    replaces the *message* instead of the type: `MTTrueKill.PiggyDamageSource` overrides `func_151519_b` with a key of
    its own (`death.attack.messtech.piggy`) — the same trick Avaritia uses for its sword. The victim is handed over as
    `%1$s` and the piggy as `%2$s`; the mod ships `%1$s被猪猪创飞了` (zh_CN) and
    `%1$s was knocked flying by a Piggy` (en_US). A death message is a `ChatComponentTranslation` that travels to the
    clients and is translated *there* (`EntityPlayerMP.onDeath` -> `ServerConfigurationManager#sendChatMsg`), so each
    player reads it in the language of their own client, and a client that has no translation for the key would read
    the raw key — which is why both language files carry it.
  - **A forced death leaves the piggy as the cause of the death.** `forceDeath` runs `setHealth(0)` + `onDeath` without
    going through `EntityLivingBase.damageEntity`, and that method is what fills the combat tracker the death message
    is built from (`CombatTracker#func_151521_b` uses the *last* tracked hit). `forceDeath` therefore writes the piggy
    into the tracker by hand (`func_94547_a`) before it runs the death. Without that, the targets that only die there —
    the ones whose hit never reaches the damage pipeline — would be announced with the last hit somebody else landed on
    them, or with the generic `death.attack.generic`.
  - `tmp/mtkill` is the harness for all of this: it compiles the shipped `MTTrueKill` against mirrors of the
    vanilla damage pipeline (`build/rfg/minecraft-src`) and of the two Witchery halves (`EntityVampire.onDeath` +
    `GenericEvents.onLivingDeath`), and asserts the reported bugs (NaN absorption/health, the unkillable vampire,
    the self hit) as well as the old steps. The mirrors include the chat side
    (`ChatComponentTranslation`, `StatCollector`, `CombatTracker`, `DamageSource#func_151519_b`), so the death message
    is asserted end to end against the real `.lang` files of the mod: its key, its two arguments, the reported Chinese
    sentence, the English one, the fallback of an untranslated key and the fact that `death.attack.infinity` stays
    untranslated. Run it with
    `javac -d tmp/mtkill/out -sourcepath tmp/mtkill/stubs src/main/java/com/MessTech/common/util/MTTrueKill.java tmp/mtkill/*.java`
    and `java -cp tmp/mtkill/out MTKillHarness`.

## The "PigTech" text animation (`MTPigTechText` / `MTPigTech`)

- `MTPigTechText` (`common/util`) renders the animation of a string, `MTPigTech` puts it on an item tooltip, and
  `ClientProxy.preInit` calls `MTPigTech.pigRegisterOn(new ItemStack(MTItems.piggy, 1, OreDictionary.WILDCARD_VALUE))`,
  where the wildcard damage covers all seven piggy variants.
- The line reads exactly like MessTech's: a static "Add by:" prefix (`messTech.addBy`, the same lang key
  `AuthorDynamic` uses) and then the animated name. `MTPigTechText.frame(prefix, text, millis)` copies the prefix into
  every frame verbatim - it is byte for byte identical for the whole loop and never animates - and puts
  `PREFIX_SEPARATOR` (one space) in front of the animated band. The word comes from the lang key `messtech.pigTech`, so
  changing the translation changes what is animated.
- `pigRegisterOn` is the "PigRegisterOn" entry point and adds **only** that line: unlike `AuthorDynamic.registerOn`
  there is no author line.
- Mechanism: gtnhlib's `AnimatedTooltipHandler` keeps one `Supplier<String>` per `ItemStack` and re-evaluates it once
  per frame, and the whole animation *is* the string the supplier returns - there is no renderer and no per frame
  state.
- What a tooltip line can and cannot do (this is why the animation looks the way it does):
  - A tooltip line is drawn glyph by glyph at a fixed height, so text cannot move up or down and glyphs cannot be
    scaled. **Spaces are the only size control the medium has**: the gaps between the letters open (stretch) and
    close (squash), and the whole word travels sideways by up to one space (charge).
  - Every line keeps the same glyphs and the same number of spaces in every frame, so the rendered width never
    changes: the tooltip box cannot pump and the word can never leave its own frame. The band is `letters - 1 + 2`
    spaces wide and keeps `MIN_MARGIN` (one space) of margin on each side in *every* frame, so the gap after the
    prefix is never smaller than two spaces (~8 px), however hard the pig squashes. The empty room to the right of the
    word at rest is that reservation: it is what the stretch and the charge expand into.
  - `§l` (impacts) and `§o` (leaning into the charge) do not change the advance either. In Minecraft a **colour code
    clears bold and italic**, so the style codes are emitted after each letter's colour code.
  - The line is a **single line**: there is no ground bar and no second tooltip line under the word any more. A jump
    cannot be drawn without a vertical axis, so take off and landing are told by the squash and stretch, the ear twitch
    and the impact flash instead. The ears are `^` (top row of the font) and the snout puffs are `.` (bottom row).
  - Only ASCII glyphs are used: `ascii.png` is a CP437 layout, so e.g. `°` would render as a shade block, while
    `^ .` look the same in both built-in fonts.
  - Everything that has to appear and disappear keeps its slot in the string and is painted `§0`: black is invisible
    on the dark tooltip background, and the width stays constant.
- Timeline (2 s, seamless): wind up 0.00-0.40 (two side swings, an impatient shiver, then loaded), charge and impact
  0.40-0.70 (lunge and stretch, the two snout puffs, then jam shut with a white flash on the wall), recoil and jump
  0.70-1.10 (spring back, ear twitches), landing 1.10-1.40 (squash, two bounces, white flash) and settle 1.40-2.00
  (one slow breath). The last frame is exactly frame 0, so the loop never jumps. Every beat is a `curve(...)` or
  `pulse(...)` keyframe, so the timing can be retuned in one place.
- The colour band steps every 100 ms through a four entry pink/white/gold/white palette. 2000 / 100 = 20 steps and the
  palette divides 20, so the colours also line up when the loop restarts.
- `MTPigTechText` deliberately has no Minecraft imports: `tmp/pigtext/PigTechFrames.java` compiles it on its own with
  `javac` and checks the single line, the static prefix, the loop, the constant layout, the reserved margins, the
  travel limit and every phase against the real strings (22866 checks, no game needed).

### The "ModuleProject" tooltip animation (`MTModuleProjectText` / `MTModuleProjectTextRenderer`)

- The modular machine look: the word is read as a rack of modules of `MODULE_SIZE` (3) characters
  ("Mod|ula|rPr|ojc|et"). One 1.6 s loop powers it up - the scan walks the rack module by module (`POWER_UP_MS` 900 ms), the rack flashes
  white twice at full load (`FLASH_MS` 250 ms), is held (`HOLD_MS` 150 ms) and then goes dark again, left to right
  (`POWER_DOWN_MS` 300 ms). A dark module is `§8§l`, a loaded one `§3§l` with a data flicker up to `§b§l`, the module
  under the scan is `§b§l` and the flash is `§f§l`; bold and colour codes never move the advance, so the line width
  is constant.
- Nothing is stored: `lit(millis)` (the fraction of the rack that is powered), `head(millis, modules)` (the module
  being loaded, -1 while the scan is parked) and `flashing(millis)` are pure functions of the clock, which is what
  lets the renderer compute the exact phase of the frame the tooltip was drawn with - the chips and the letters can
  never drift apart.
- `MTModuleProjectTextRenderer` (client only, the renderer is registered in `MTAnimatedTooltipHandler#init`) draws the
  hardware around the letters, it does not replace them (`replacesText()` stays false): a chip row two pixels below
  the glyphs, one chip per module, lit in step with the letters with a brighter packet on the module being loaded; a
  scan line with a trailing tail above it; and a bracket at either end of the word. `Gui#drawRect` is protected, so
  the rects go through a small `Gui` subclass, the same trick `MTAnimatedTooltipHandler`'s tooltip box uses.
- Only the word is decorated: the line is `添加模组: <word>`, so the renderer measures the translated
  `messTech.addBy` prefix plus `MTPigTechText.PREFIX_SEPARATOR` with the font and starts its chips at the first glyph
  of the word. A line without that prefix is decorated whole.
- **Every width comes from the font, never from a hand sum of `getCharWidth`.** A bold character advances one pixel
  further than its glyph width (`FontRenderer#renderStringAtPos` adds the bold copy to the advance, `++f`, and
  `#getStringWidth` does the same with `if (flag && k > 0) ++i`), and the animation draws *every* character bold, so
  a hand summed word comes out one pixel per character short - on a 13 character word the right bracket landed two
  glyphs inside it, exactly at the `e` of `Project`, which is how this was found. `moduleEdges` therefore measures
  drawn substrings (formatting codes included, which is what makes the font count the bold advance) and `rawIndex`
  maps a visible index onto the drawn line, so the chips and the brackets share the glyphs' own geometry.
- It is the brand line of `MTHugeChemicalReactor`, registered with
  `AuthorDynamic.registerAddon(MODULE_PROJECT, ...)` - the add-on line with no author line - and the word comes from
  the lang key `messtech.moduleProject`, so a resource pack can rename it.

## Animated tooltips with a renderer (`MTAnimatedTooltipHandler` / `MTTextAnimation`)

- gtnhlib's `AnimatedTooltipHandler` can only add **strings** to a tooltip: its registry is
  `ItemStack -> Supplier<String>` and the animation *is* the string. An effect that has to touch the letters
  themselves - rotate them, darken them, blend them - has nowhere to run there, so MessTech has its own
  `MTAnimatedTooltipHandler` (`common/util`). It keeps the same registration shape (`addItemTooltip(stack, line)`,
  re-evaluated once per frame, split on `'\n'`), remembers an animation per line, and is the handler
  `AuthorDynamic` writes through. `MTPigTech` still uses gtnhlib's handler; both add their lines in
  `ItemTooltipEvent`, so they coexist.
- The split that makes this work on both sides: `MTTextAnimation` is the **common** half - it only answers "what
  does the line look like this frame", so the machine loader can register animated lines while running on the
  server too - and `MTTextRenderer` is the **client** half, the drawing. `registerRenderer(animation, renderer)`
  pairs them client-side; `init()` (called from `ClientProxy.preInit`) registers the built-in pair and the event
  handler. An animation without a renderer is a pure formatting animation, which is what `MTPigTechText` is.
- **The client half must not live in the class's static initializer.** `SideTransformer`
  (`cpw.mods.fml.common.asm.transformers.SideTransformer#transform`, the field loop at lines 53-65 and the method
  loop at 66-78) deletes every `@SideOnly(Side.CLIENT)` **field and method** from the class on a dedicated server,
  but it never touches `<clinit>`, which carries no annotation. A client-only field *with an initializer* keeps its
  `putstatic` in the initializer of a class that common code loads, and the server then dies on the
  POSTINITIALIZATION -> AVAILABLE transition with `NoSuchFieldError: RENDERERS` in
  `MTAnimatedTooltipHandler.<clinit>`. That is why the renderer map and the tooltip box are built by
  `renderers()` / `background()` on first use; the nested `Background` class would be loaded by `<clinit>`
  as well and then rejected by the side check (`Attempted to load class ... for invalid side`). Client-only state
  built lazily inside client-only methods is safe: the two entry points common code uses, `addItemTooltip` and
  `addAnimatedText`, never touch it.
- Drawing: when a tooltip of a stack with a *rendered* animation is about to be drawn, the handler takes the
  tooltip over through gtnhlib's `RenderTooltipEvent#alternativeRenderer`, redraws the vanilla box (the nine
  `drawGradientRect`s, then `font.drawStringWithShadow` per line) and calls the renderer of every line that wears
  one. A renderer with `replacesText()` draws the line itself and the plain font copy is skipped for it, so an
  effect can turn the letters instead of drawing a second copy over them; everything else stays a normal line.
  The same event is posted by NEI (`RenderTooltipEventHelper`, NEI 2.7.8-GTNH and newer), so it works in the
  inventory and in the NEI panels. A stack whose lines carry no renderer is left completely alone (the vanilla
  tooltip is not touched), an already set `alternativeRenderer` is never fought over, and NEI's paging for
  taller-than-screen tooltips is not reproduced (vanilla layout instead).
- Which line gets which renderer is decided by **visible text**, not by index: NEI inserts its second display
  name at index 1, so positions move. `MTTextAnimation.visibleText` strips the `§x` pairs and the entry whose
  frame has the same visible text wins, which also makes the animation immune to whatever colour codes a supplier
  such as `author_czqwq()` brings along.
- `AuthorDynamic.register(animation, author, stack)` is the new entry point (arg order: animation, author text,
  stack), with `register(animation, stack)` for the default name: it animates the *visible* text of the author
  supplier with `animation.frame(...)` and adds the line - plus the usual animated "Add by: MessTech" line - to
  the stack. `registerOn(...)` is unchanged behaviourally: it now goes through the same handler with no animation.
- `TRANSCENDENT_METAL` is the built-in rendered animation, GT5U's Transcendent Metal look worn by a whole line:
  - `MTTranscendentMetalText.frame` recolours every character with a dark grey / grey / white / grey band that
    advances one character per 90 ms, so the letters read as polished metal before anything moves.
  - `MTTranscendentMetalTextRenderer` **replaces** the line (`replacesText()`): it draws it itself, turned about
    its own centre by GT5U's own transform, verbatim - `glRotatef(angle, 0.3, 0.5, 0.2)` with
    `angle = getAnimationRenderTicks() * 3.5 % 360` - so the word turns exactly like a Transcendent Metal item
    lying next to it. The turning copy is the only copy: a still line with a turning one over it shows both at
    once. The metal itself is the frame's own band with the font's shadow, plus the same glyphs one pixel along
    with additive blending, which is the glint of the turned plate.
  - The turn is one way round, continuously: no folding, no bounce and no reset at a quarter turn. The plate is
    edge-on (invisible) once per revolution, exactly like the item. The line swings with it - a long word tips
    diagonally out of its own tooltip row at times, the way the item's quad leaves its slot - because the motion
    is the item's and nothing is tamed down.
  - `MTTranscendentMetalText.showsBack` drives the one thing a flat plate needs and an item quad gets from its
    back face: while the plate faces away, the letters are mirrored back (`glScalef(-1, 1, 1)`) so they read the
    same way round on both sides. With the oblique axis the z part of the plate normal is
    `cos(a) + z^2 / (x^2 + y^2 + z^2) * (1 - cos(a))`, so the switch lands where that is zero - the edge-on pose,
    where the plate has no visible area and the flip cannot be seen.
- Verification: by the repository rule this is `gradlew compileJava`, and `tools/textanim` (the source+behaviour
  harness of the first, folding version) is stale since that version was replaced - do not run it as is; refresh
  it only if a check of the turning text is wanted again.
- The bigger version of this idea, which this repo deliberately does not take: render the effect at the **font**
  level - an inline marker in the string, a `FontRenderer` mixin that parses it, glyph masks and GLSL, with the
  marker stripped again wherever the text is measured, wrapped or typed (chat, text fields, NEI search fields,
  Angelica-style batched font renderers). That is what makes an effect show up in chat, item names and search
  fields as well, but it needs a mixin into every font consumer, which is only worth it for effects that must
  appear outside tooltips. This repo uses the smaller shape instead: an effect is a registered renderer, and the
  animation data stays common while the drawing stays client-only.

