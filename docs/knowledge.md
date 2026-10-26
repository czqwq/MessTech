# MessTech Knowledge Dump

**Context-preservation knowledge base** (work continues after context loss). Own-code notes `repo-readme.md`, GT5U
notes `GT5U-NOTES.md`.

---

## Project layout

- Mod package `com.MessTech`.
- Machines: `com.MessTech.common.machine.loaders.MTMachineLoader.loadMachines()` in `CommonProxy.postInit()`;
  blocks: `com.MessTech.common.block.MTBlocks.registerBlocks()`, first.
- Item list enum: `com.MessTech.common.item.MTItemList` (GT ItemList pattern).
- Machine IDs: MTDTPF = 32400, MTComputingCenter = 32401, MTHatchRack = 32402, MTAssFactory = 32403.
- Lang: `src/main/resources/assets/messtech/lang/{en_US,zh_CN}.lang`.

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
- Base on GT `MTEExtendedPowerMultiBlockBase`: `ProcessingLogic` setup, mode switching, Waila NBT/body; no wireless
  code (-> `MTWirelessMultiMachineBase`).
- `getEnergyHatchTier()`: highest `MTEHatch#getInputTier()` over `getExoticAndNormalEnergyHatchList()` (exotic
  laser/multi-amp + mixed-tier plain hatches); every "energy hatch tier" gate reads it, not GT's
  `getInputVoltageTier()` (only `mEnergyHatches`, 0 there).

### MTModuleMultiMachineBase (module system)
- Files: `common/machine/Base` - `MTModuleMultiMachineBase<T>`, `IMTModule`, `MTModuleType`, `MTModuleValues`,
  `MTModuleHatchElement`; `common/machine/hatch` - `MTModuleHatchBase` (plain `MTEHatch`, no inventory, one decal
  per module), `MTModuleSpeedHatch`, `MTModuleEuHatch`, `MTModuleParallelHatch` + `MTModuleParallelHatchGui`;
  `common/gui/base/MTModuleMultiMachineBaseGui`.
- Decal `getOverlayPath()`: icon under `textures/blocks` (`ModuleHatch/OVERLAY_PowerController`,
  `..._SpeedController`, `..._ParallelController`), resolved once per class in `registerIcons`, over the front-face
  casing, active/inactive (like `MTReactorAccessHatch`/`MTReactorHeatHatch`); needs GT's
  `BlockMachines#registerBlockIcons` (`registerIcons` *before* `sGTBlockIconload`, so a
  `Textures.BlockIcons.custom` container survives). 40-frame `.mcmeta` (`frametime` 2, 4 s loop) -> animated.
- Module = whatever the machine links into its module slot (a hatch); it reports, the machine aggregates.
- `MTModuleType`: `EU_DISCOUNT`, `SPEED_BONUS`, `PARALLEL_CONTROL` (= `MTModuleType.defaultSupported()`), plus
  opt-in `CROSS_RECIPE_PARALLEL`, `WIRELESS` (`getSupportedModuleTypes()`).
- Types are a **set** (composite module needs no own type); `acceptsModule()` needs every type supported + tier in
  range. Tier = GT tier index: `IMTModule.MIN_TIER` = 5 (IV) .. `MAX_TIER` = 14 (MAX), `isValidTier()` else.
- Folding: EU/speed **multiply** into `getBaseEuModifier()` / `getBaseSpeedBonus()`; parallel **supplies** -
  `getMaxParallelRecipes()` = `getModuleParallel()` (highest `IMTModule#getParallel`), else
  `getBaseMaxParallelRecipes()`; the number **is** the parallel (`baseParallel = module.getParallel()`), 64 at IV
  stay 64. One only (`addModule`), like TST's `checkSingleModularHatch` (= second controller a structure error).
  `isModuleParallelAdditive()` opts into `base + module` instead (the LPF is the only user).
  Three methods **final**, `getBase*` hooks (defaults 1 / 1 / 1).
- `checkMachine` final: `clearModules()` + `checkMachineStructure(...)`; rebuilt each check, so a pulled hatch loses
  its bonus (TST: `resetModularHatchCollections()`).
- Linking: `addModuleHatchToMachineList(te, casingIndex)` behind `MTModuleHatchElement.Module`
  (`IHatchElement<MTModuleMultiMachineBase<?>>`); opt in with
  `buildHatchAdder(MyMachine.class).casingIndex(casing).hint(1).atLeast(MTModuleHatchElement.Module).build()`.
  `IMTModule#onLinkedToMachine(casingIndex)` passes the casing texture, so the base never names hatch types. Unused.
- Values (`MTModuleValues`, IV..MAX):
  - speed: `{0.95,0.85,0.75,0.70,0.65,0.40,0.35,0.20,0.10,0.01}` = 5% faster at IV up to 100x at MAX;
    `getSpeedBonus()` **is** the entry = fraction of duration left (no TST `SpeedMultiplierOfSpeedController` ×2).
  - EU: TST `PowerConsumptionMultiplierOfPowerConsumptionController` `{0.95,0.9,0.85,0.8,0.75,0.7,0.5,0.25}`, tail
    halved -> `{...,0.125,0.0625}` = 5% saved at IV up to 93.75% at MAX.
  - parallel: `1 << (2 * (tier - 2))` = 64 at IV up to 16,777,216 at MAX, GUI-lowerable (1..ceiling,
    `setParallelFromGui`, NBT `parallel`).
  - TST controllers ZPM..MAX (T1..T8), ours IV..MAX: T1 = IV, only UXV/MAX EU entries extrapolated (speed ours).
- Items: `MT_ID + 40..49` speed, `+50..59` EU, `+60..69` parallel (one per tier IV..MAX), looped from
  `MTItemList.SPEED_MODULES` / `EU_MODULES` / `PARALLEL_MODULES` in `MTMachineLoader`; lang `machine.module.*` both
  files (`speed.name`, `speed.desc.0`, `eu.name`, `eu.desc.0`, `parallel.name`, `parallel.desc.0/1`,
  `parallel.label`, `desc.install`).
- Tooltip factor, decimal: speed `MTModuleValues#speedBonusText` = `0.95` .. `0.01`, two decimals (hides float
  noise: `0.95F` is `0.949999988079071`); EU `MTModuleValues#euModifierText` = `0.95` .. `0.0625`, four decimals, no
  trailing zeros (the last two entries need them), percent `euDiscountText` = 5% .. 93.75% kept for the harness;
  parallel = ceiling + "configurable in the GUI" (the `1..ceiling` range is the GUI field's); all close with
  `desc.install` = 可用于模块化机器 / usable in a modular machine. **No author line**:
  `MTMachineLoader#brandAsModuleProject` -> animated `Add by: ModularProject` brand
  (`AuthorDynamic.MODULE_PROJECT` / `MTModuleProjectText`, Huge Chemical Reactor look), not the
  `AuthorDynamic#registerOn` pair.
- Recipes (`GTRecipes#addModuleRecipes`): 30 **assembler**, one per family per tier = TST's `ModularHatchesRecipes`
  crossed with GTNL's parallel controller hatch: 4 `TIER_HULLS[tier]`, flat 16 of that tier's components, 16
  circuits (`TIER_CIRCUIT_MATERIALS[tier]`), 16 plates of `MODULE_MATERIALS[i]`, plus `144 * 16` L of that material
  as melt - flat counts on purpose, the tier is paid for in *which* material and which `TIER_RECIPE_EU[tier]`.
  Duration `MINUTES * (i + 1)` (1 minute at IV, 10 at MAX). Components per family (TST's split): speed 3x field
  generator + motor + piston, parallel field generator + robot arm + conveyor, EU 2x field generator + emitter;
  circuits 4 / 5 / 6 mark them in NEI, like the `4` GTNL puts on its controller hatch.
  - `TIER_HULLS` GT-tier indexed, two names GT's own: `Hull_MAX` = **UHV** hull (MAX was the top tier then), MAX
    uses `Hull_MAXV`; TST's `Hull_ZPM, Hull_UV, Hull_MAX, Hull_UEV, ...` agrees.
  - `MODULE_MATERIALS` by module index: tungstensteel, enderium, naquadah alloy, neutronium (GT5U's muffler hatches
    for those tiers), then TST's cosmic neutronium, infinity, transcendent metal, space time, MHDCSM, magmatter.
- Waila (`MTModuleMultiMachineBase#getWailaBody`, `machine.module.waila.*` both languages): three module lines last,
  tooltip order - 耗时减免 / Duration reduction, 耗电减免 / EU discount, 并行 / Parallel; values = tooltip ones
  (`MTModuleValues#speedBonusText` / `#euModifierText`, parallel via `NumberFormatUtil#formatNumber`), colours in
  the lang text (`§7` label, `§b` / `§a` / `§e` values), a line only while linked; flags/numbers via
  `getWailaNBTData` (server-side only, no structure scan on a client).
- Parallel GUI field (`common/gui/base/MTModuleMultiMachineBaseGui`, `getGui()`): linked parallel module's label +
  text field beside the machine data; `getParallelForGui()` / `getParallelCeilingForGui()` in, `setParallelForGui()`
  -> `IMTModule#setParallelFromGui` out (new interface defaults + `IMTModule#getMaxParallel`), `IntSyncValue`s
  (`allowC2S`, only the server knows the module). Hatch keeps its own MUI2 GUI (`MTModuleParallelHatchGui`),
  `useMui2()` overridden there: `MTEHatch#useMui2()` is `false` by default (GT's MUI1 GUI), while
  `MTEMultiBlockBase#useMui2()` (any machine) is MUI2.
- Optional types only *reported*: `hasModule(type)`, `getModules(type)`, `getModuleCycleNum()` (highest
  `IMTModule#getCycleNum`), `hasWirelessModule()`; real loops need the wireless (`MTWirelessMultiMachineBase`) or
  cross recipe (`TickableParallelismAcrossMultiMachineBase` / `ParallelismAcrossMultiMachineBase`) base - Java cannot
  bridge both chains, so modules *and* wireless needs a composition registry helper. None yet.
- References: TST `ModularizedMachineBase` / `ModularizedMachineSupportAllModuleBase` (modules as hatches,
  static/dynamic controllers into machine accumulators, `MultiExecutionCoreMachineBase` = cross recipe core);
  EU/parallel from TST + the ceiling formula, speed ours.
- Harness `tools/modulebase/verify_module_base.py`: 97 source checks + `ModuleBaseHarness` (102 runtime,
  `tools/modulebase/stubs`); EU table vs TST's `Config.java`, parallel vs `1 << (2 * (tier - 2))`, tier names vs
  `GTValues.VN`; mutation-tested (EU tail, additive parallel, single-parallel rule, `clearModules()`,
  `isOptional()`). Its speed checks (`SPEED_MULTIPLIER`, TST ×2 law, `speedMultiplier`) still encode the old table,
  stale; request-only.
- Next: wire `MTModuleHatchElement.Module` into a real structure, add the composite module (one hatch, two types;
  the API allows it), fix the numbers of the optional cross recipe and wireless modules.

### MTWirelessMultiMachineBase
- State: `ownerUUID`, `EnableWirelessFunc`, `EnableWireless`, `wirelessParallel`, `isRecipeProcessing`, `costingEU`,
  `costingEUText`, `cycleNum`.
- `isWirelessModeAvailable()`, `isWirelessModeEnabled()`, `isEnableWireless()`, `setEnableWireless(boolean)`,
  `setWirelessParallel(int)`, `getWirelessParallel()`, `startRecipeProcessing()` / `endRecipeProcessing()`,
  `initWirelessNetwork()`, `onFirstTick()`, `onPreTick()`.
- Power path: `checkWirelessPower(eut,duration,maxParallel)` (simulate before consume),
  `validateWirelessPowerForRecipe(eut,duration,maxParallel)` (shared validation), `startWirelessRecipe(...)` (power
  first, then inputs), `wirelessModeProcessOnce()` (one cycle), `checkProcessingWirelessLoop()` (opt-in multi-cycle).
- Hooks: `prepareProcessing()`, `getExtraEUCostMultiplier()`, `getWirelessModeProcessingTime()`,
  `getDefaultWirelessMode()`, `setupWirelessProcessingPowerLogic()`; Waila writes `wirelessMode` + `costingEUText`
  (`无线模式` / `耗电`), NBT saves `enableWireless`, `wirelessParallel`.

### MTGeneratorMultiBase
- Extends `MTMultiMachineBase` (not the wireless-consumption base); keeps `lEUt` positive, `checkProcessing()`
  returns `GENERATING` on a generic ProcessingLogic success with `lEUt > 0`.
- Wireless EU output supported, defaults **off**: state `ownerUUID`, `EnableWirelessFunc` (available, default true),
  `EnableWireless`; `setEnableWireless(true)` needs wireless available and no dynamo/exotic-dynamo hatches;
  `onRunningTick()` in wireless mode feeds `getCurrentGenerationEUt()` to the owner's network, not dynamo hatches;
  `addToMachineList()` also takes dynamo, exotic dynamo (multi-Amp) and laser-output hatches, and
  `addEnergyOutputMultipleDynamos()` fills both dynamo lists.
- Waila: wired mode = GT's generation line; wireless writes `wirelessMode` + `generatedEUText`, shows `无线模式` /
  `无线发电` (same comma + scientific formatting as `MTWirelessMultiMachineBase`) plus current-process `总发电量`
  (compact `(n MAX)` suffix above MAX-tier voltage; `n = floor(total / 2147483640)`), computed as
  `wirelessGenerationPerTick * mMaxProgresstime` (BigInteger) = whole process output, not a running total. NBT:
  `enableWireless`.
- Parallelism: default `getMaxParallelRecipes()` = `Integer.MAX_VALUE` (subclasses may lower);
  `getParallelForAvailable(availableAmount, requiredAmount)` caps by the GT power-panel parallel override and the
  available fuel/input amount. Helpers: `getCurrentGenerationEUt()`, `startGenerating(...)`, `setEnergyUsage()`,
  `getParallelForAvailable(...)`.

### ParallelismAcrossMultiMachineBase
- Extends `MTWirelessMultiMachineBase`; wireless `checkProcessing()` calls `checkProcessingWirelessLoop()`, each
  cycle may pick a different recipe, outputs accumulate.
- `setWirelessCycleNum(int)` sets `cycleNum`; validation inherited (`getUserEU` + `addEUToGlobalEnergyMap`).

### MTParallelHelper / one batch calculation (`Config.machines.ENABLE_ONE_BATCH_CALCULATOR`)
- `MTProcessingLogic#createParallelHelper` builds the mod's fork of GT's `ParallelHelper` (`MTParallelHelper`, the
  GT++ fork with private redeclared fields - see `docs/GT5U-NOTES.md` §11). Every machine that runs recipes through
  `MTProcessingLogic` goes through it: DTPF, Assembly Factory, LPF, DBB Furnace, BosesCraftingArray, Chemical Twister,
  Nano-Scale Foundry mode 1, MachineTemplate and the other `MTMultiMachineBase` subclasses. Generators
  (`MTGeneratorMultiBase`: MTReactor / MTNQDAFReactor / MTLargeEssentiaGenerator) override `checkProcessing` and start
  runs through `startGenerating`, and the space modules use GT's own `ParallelHelper`/`ProcessingLogic` - neither
  reaches this helper.
- `Config.machines.ENABLE_ONE_BATCH_CALCULATOR` (default **false**, config screen → Machines, title "Enable One Batch
  Calculator" / 启用单批计算器) turns the whole batch into one long run. The machine's parallel cap and the
  power-based parallel limit (`availableEUt / recipeEUt`) are ignored; the parallel count becomes everything the
  input buses hold, still capped by void protection (when the machine has it on) and by the `int` tick limit
  (`(Integer.MAX_VALUE - 1) / recipe.mDuration`). The old 128-tick batch mode extra-parallel logic is skipped.
- The calculation runs at that real parallel count (same overclock level a normal batch would get), then
  `MTProcessingLogic#applyRecipe` divides the EU/t by the count (ceil) and `getDurationMultiplierDouble()` multiplies
  the duration by it. Total EU is therefore the same as a normal run - only the draw per tick drops and the run gets
  longer. Recipes without consumed inputs skip one batch mode (nothing to batch).
- `MTProcessingLogic#applyRecipe` is a copy of GT5U 5.09.54.205's method with only that division added. It must run
  before `onRecipeStart`, because wireless machines bill `calculatedEut * duration` in one lump
  (`MTDTPF#startWirelessRecipe`, `MTWirelessMultiMachineBase#wirelessModeProcessOnce`); a wireless machine therefore
  needs the whole run's EU in the network before it starts.

### CalculateMultiMachineBase / MTComputingCenter
- `CalculateMultiMachineBase<T>` extends `MTMultiMachineBase<T>`; adds uncertainty, data input/output, racks
  (`MTHatchRack`), holders and wireless computation output hatch lists.
- Modes: 0 = Nano Computing, 1 = Research Station, 2 = Scanner; research/scanner share the computation/packet-loss
  logic. Heat: `machineHeat` MAX 10,000,000, cryotheum active cooling, passive when stopped, overheat melts rack
  components and locks racks (also the mode switch). Waila values reset while inactive (manual shutdown too).

## Machines

### MTDTPF
- Extends `MTWirelessMultiMachineBase`; wireless needs an AAF in the controller slot + tier >= 5 (`detecttier()`),
  then no-overclock, 0.75 EU/duration modifier, wireless parallel selector.
- Runtime ramp: `running_time` 3600s to full efficiency; decay when idle.
- Validation order (original design): 1. machine tier / recipe voltage; 2. cap wireless parallel by wireless
  balance; 3. `validateWirelessPowerForRecipe(...)` — insufficient -> insufficient power; 4. `onRecipeStart` ->
  `startWirelessRecipe(...)` — deduct power + consume inputs -> start.

### MTNQDAFReactor
- Extends `MTGeneratorMultiBase` (generator + optional wireless output); Naquadah-fuel reactor casings/coils, no
  energy input, dynamo or wireless.
- Work efficiency: 100% at start, +1% per 30 s running, max 400%; a fuel batch burns
  `baseDuration * efficiency / 100` ticks; idle decay DTPF/Plasma Forge style, floor 100%.
- Fluids (from Large Naquadah Reactor): Liquid Air 1,000,000 L/s while running; coolant
  IC2/Super Coolant/Cryotheum/Tachyon -> 105/150/275/500% output; excited liquids
  Caesium/U-235/Naquadah/Atomic Separation Catalyst/Space -> 2/3/4/16/64x output and fuel usage.
- Coil tier I-IV gives 1x-4x EU/t output multiplier.
- Auto parallel = fuel amount / per-batch fuel requirement (capped by the GT power-panel parallel override); each
  parallel multiplies fuel consumption, EU/t and byproducts.
- Spacetime: molten spacetime freezes efficiency decay 30 s; cost doubles per dose, from 1 L/s (one drain per dose)
  to max 1,073,741,824 L/s; does **not** pause efficiency growth.
- No dynamo hatch in `checkMachine` -> auto enables wireless output (`MTGeneratorMultiBase`).

### MTReactor (nuclear reactor)
- Generation (`MTReactor#getEuPerOutput()`): `EU/t = core output × 5 × IC2's balance/energy/generator/nuclear × 2`;
  `× 2` = `MESSTECH_OUTPUT_MULTIPLIER`, a maintainer balance decision, not config - named on purpose, not a defect
  (`docs/GT5U-NOTES.md` §15). GTNH `config/IC2.ini` `nuclear = 5.0` -> 1 output point = 25 EU/t as in IC2
  (`TileEntityNuclearReactorElectric#getOfferedEnergy()`, GT5U's NEI nuclear fake recipe); quad uranium rod
  12 pulses × `sEnergy` 2 = 24 output -> 600 EU/t, 1200 here.
- **Heat per page, anchored on the access hatch block**: `Map<ChunkCoordinates, int[]>` keyed by the owning
  `MTReactorAccessHatch` (`MTReactorHeatSystem.PageOwner#mtReactorKey()`); the old global-order `int[]` renumbered on
  add/remove/move (block-coordinate sort) -> heat migrated silently. `retainOnly()` drops a departed hatch's heat +
  countdown.
- **Cycle in `onPostTick()`, not `checkProcessing()`** (the base skips that while off): cycle, cooling and countdown
  run; only fuel/energy are gated, via `isAllowedToWork()` -> `couldRun()` -> `mLastCouldRun`
  (`ReactorContext#produceEnergy()`, tested by IC2's fuel rods) -> off reactor burns nothing, heat still moves.
- **Countdown, not explosion**: a page at/over its ceiling arms `MTReactorHeatSystem.MELTDOWN_COUNTDOWN_TICKS = 600`
  (30 s); `armCountdowns()` per material cycle keeps the original deadline (ceiling pinning cannot postpone it),
  `clearCountdown()` a page back under, `resetCountdown()` all (player action: heat hatch change, fluid toggle);
  `explodeExpiredCountdowns()` per `onPostTick()` on an absolute tick. Persisted as remaining ticks
  (`mtReactorArmedCountdowns`), re-anchored on load (`setLoadClock()` -> `refreshCountdownClock()`), ticking only
  while loaded.
- **Ceiling change rescales heat, no detonation** (`onCapacityChanged()`): × `new ceiling / old ceiling` (90% stays
  90%); the old `heat / maxHeat` check detonated next cycle on a UHV -> EV heat hatch swap.
- **Plating counts again (IC2 style)**: live `ReactorContext#setMaxHeat()` -> `MTReactorHeatSystem.setPageMaxHeat()`;
  plating lifts *its own* 9x6 page's ceiling over the heat control hatch value, largest bonus per page wins
  (layout-independent); `getPageMaxHeat()` = ceiling, `MAX_HEAT_BONUS_PER_ITEM` per item, `MAX_HEAT_CAPACITY` for the
  sum; `resetPageBonus()` per page pass -> a removed plating stops at once. Before: no-op, so plating was heat effect
  modifier + bigger explosion only.
- **Passive cooling** = heat control hatch tier (`docs/GT5U-NOTES.md` §15): `MTReactorHeatSystem.tick()` removes
  `rate / 20` HU per tick, fractionally accumulated (no rate rounded away), from pages holding >= 5% of the hottest
  page's heat (empty pages must not eat the budget), halved during a countdown; runs while off too.
- **World heat effects once per cycle, not per page**: `applyHeatEffects()` on the hottest page's ratio + heat effect
  modifier (no N-fold irradiation/ignition); meltdown still per page.
- **`STABILITY_PREDICTION_CYCLES = 20`**: `predictFuture()` repeats the heat pass on page copies until equilibrium or
  the ceiling -> GUI/Waila `getCyclesToMeltdown()` (`-1` = stable), not a "next cycle fatal" boolean.
- **Rated vs injected EU/t**: `getRatedEUPerTick()` = last cycle's accumulation, `getActualEUPerTick()` = what the
  dynamos took (untakeable output is lost silently, so both are shown); shortfall under 90% of rated for
  `OUTPUT_SHORTFALL_REPORT_TICKS = 20` ticks -> `hasOutputShortfall()`. An exploding page still settles its EU/t.
- **Fluid ("wet") mode** (`onScrewdriverRightClick`, per controller): coolant input + hot coolant output hatch, else
  soft-stop; 1 mB `ic2coolant` per 1 HU component heat (`MTReactorHeatSystem.coolantForHeat()`,
  `COOLANT_PER_BUFFER_UNIT = 1` - MessTech's ratio, not IC2's 2 mB/HU `huOutputModifier`), same `ic2hotcoolant`
  out, refund if the hatch cannot take it (no dupe/loss). Live `addEmitHeat`/`isFluidCooled`: vents
  (`ItemReactorVent` / `ItemReactorVentSpread`) feed removed heat to the buffer, MOX (`ItemReactorMOX#getFinalHeat`)
  doubles output above 50% heat; not mode-gated per component. Dry mode: half the buffer back on the core
  (`addHeat(huOutput / 2)`).
- **A locked slot with a full durability component is never pre-emptively output** (P21): only an already damaged
  component is salvaged before a predicted-destroying cycle; a fresh one dying next cycle = wrong layout, so swapping
  cannot help -> soft-stop instead of eating inputs.
- **Explosion limit is a config** (`Config.machines.reactor.REACTOR_IC2_EXPLOSION_POWER_LIMIT`, default 45 = IC2's
  `protection/reactorExplosionPowerLimit`, was hardcoded `45.0F`): clamps summed component influence before the DE
  power scale (`REACTOR_EXPLOSION_DE_POWER_LIMIT`, 40). `explodeReactor()` also clears every page's heat
  (controller TE + heat store survive `explodeMultiblock()` for the rest of the tick) and settles the accumulated
  EU/t first.
- Controller face: GoodGenerator's neutron activator art (`icons/NeutronActivator_Off` / `_On` + two optional glow
  layers, gregtech assets, five animated 16x16 frames, `frametime: 8`) over the casing already under the face
  (`Casings.AssemblyLineCasing`), other five `Casings.AssemblerMachineCasing`; overlay only, base layer unchanged
  (the activator would otherwise bring its own casing, `getCasingTextureForId(49)`); art = 12x12 panel in a two pixel
  transparent border where the casing shows through.
- Tooltip `machine.mtreactor.tooltip.*`, TST style: `desc` + flavour, then `§6` groups 结构 / 堆芯 / 发电 / 细节 with
  `addSeparator()`; colours in the lang text (`§b` values, `§c` danger thresholds, `§e` hatch tiers) -> one
  `addInfo(translate(key))` per line, structure block / `toolTipFinisher` untouched. Reuse this layout + key naming.
  No muffler hatch line: never accepted, and the old line advertised a hatch that could not be placed usefully.
- Data stick copy (`MTReactorAccessHatch implements IDataCopyable`), GT5U's `MTEHatchOutputBus` /
  `MTEHatchConfigurableBase` pattern: left click with an `ItemList.Tool_DataStick` saves, right click loads, constant
  `"type"` (`DATA_STICK_DATA_TYPE`) checked on paste; safe on a GT machine block (`MaterialMachines` is
  `setRequiresTool()`, wrench harvest tool).
- Payload = **settings only**: version (`ver`), source page count (`page`), sparse `locks` of
  `{slot, threshold, item}`. **Components are never stored or written** - a paste touches only this hatch's lock
  state (`mSlotState` / `mSlotMemory`), an unlocked slot is unlocked here, a page this hatch lacks keeps no lock; an
  empty locked slot is reported (`copy.empty_slots`) and works once the component is put in.
- **Component type decides where a lock lands, not the slot number** (`remapLocks()`): the same parts sit in other
  slots across layouts, so a slot-number replay locked the wrong slots. Three passes per entry: (1) a slot already
  holding that type (the only inventory read), (2) its original slot number if it carries no other lock, (3) any
  other free slot on that page. A locked occupied slot is never overwritten; an unfittable lock is dropped and
  reported (`copy.remap_dropped`); `copy.remapped` = locks applied over pages.
- Do not reintroduce: (a) storing components and writing them into the target (not a settings copy), (b) type
  matching that *skips* mismatches (locked nothing; paste depended on the inventory). Type-first placement only
  decides *where* a lock lands (then slot number, then any free slot), so no entry is lost across layouts.
- A paste that replays nothing logs (`MessTech`, WARN) entries carried, `loadItemStackFromNBT` survivors and usable
  pages (a bare JVM cannot resolve Forge item ids, so this is the field diagnostic).
- Pages map 0..N-1 one to one; a smaller hatch ignores pages it lacks (`copy.pages_dropped`); the version field
  refuses an earlier layout's stick (`copy.invalid`) rather than half apply it. The stick validates no tier - the
  controller does (`MTReactor.checkAccessHatchTier()` caps the access hatch tier with the heat control hatch tier),
  so a large-hatch stick onto a lower tier one is legal and just drops pages.
- **Meltdown = Draconic Evolution's reactor explosion, ported** (`MTExplosionDE` + `MTExplosionDETrace`, 1:1 from
  `tmp/Draconic-Evolution-master`): one block ring per tick out to `power * 10` blocks, one trace per ring block
  blasting its whole vertical column - not IC2's `ExplosionIC2` (a 450 power explosion fires ~25 million rays and
  freezes the server before the first block breaks). DE's process has no visual: it fires the fireball from the tile
  entity (`TileReactorCore#goBoom` sends `(int) (power * 10F)`, the client answers
  `Particles.ReactorExplosionParticle`); MessTech has no packet channel -> synced entity `MTEntityDEBlast`, drawn by
  `MTDEBlastRenderer`, both documented with DE's own line numbers, for every `MTExplosionDE` (meltdown, bow's DE
  mode), boom client-side at tick 3 - where DE's particle plays its sound.

### MTLargeProcessingFactory (LPF, ported from GT++ 5.09.51.242)
- Port of `MTEIndustrialMultiMachine` (deleted from GT5U by PR 4384). Extends `MTModuleMultiMachineBase`: base parallel
  `2 × GTUtility.getTier(getMaxInputVoltage())`, duration ×1/3.5 ("250% faster"), EU ×0.8; a parallel module **adds** to
  the base (`isModuleParallelAdditive()`), speed/EU modules multiply the base.
- Three machine modes (metal / fluid / misc) × circuit 20/21/22 select one of nine recipe maps (`POOLS`).
  `getRecipeMap()` is null and a custom `ProcessingLogic` picks the pool from the found circuit, dropping
  `CompressionTierKey` recipes like the original; `getAvailableRecipeMaps()` returns all nine for NEI.
- Misc mode keeps the GT++ solidifier hatch: `checkRecipeForCustomHatches` feeds its mold + circuit 22 to the logic and
  `getStoredFluidsForColor` hides that hatch's own fluid from the pool search. Like the original, input separation is on
  by default (`getDefaultInputSeparationMode() = true`), so each input bus (and the solidifier hatch) is matched on its own.
- Structure: 3×3×3 shell of GT++ Multi-Use Casing (`Casings.MultiUseCasing`, texture id 98), 6 casing minimum; every
  casing takes the IO hatches, the energy hatch, the muffler and the module hatch (`MTModuleHatchElement.Module`).
- Pollution 400 / 400 / 600 gibbl/s per mode (GT5U's removed `PollutionConfig` defaults). Controller faces are the GT++
  `industrialMultiMachine(Active)` PNGs copied to `assets/messtech/textures/blocks/iconsets/controllerFaces`. GT5U had no
  recipe for it, `GTRecipes#addLegacyMachineRecipes()` adds the assembler one.

### MTLargeEssentiaGenerator (LEG, ported from GoodGenerator 5.09.51.242)
- Port of `MTELargeEssentiaGenerator` (deleted from GT5U by PR 4387). Extends `MTGeneratorMultiBase` with wireless
  output **hard off** (`setEnableWirelessFunc(false)` in the constructors and after every structure check).
- Structure 9×9×3 (offsets 4,0,4): 25 Essentia Diffusion Cells (`Loaders.essentiaCell`) + Magic Casing + TC amber bricks
  (`ConfigBlocks.blockCosmeticOpaque:1`) + arcane stone bricks (`blockCosmeticSolid:7`). The cells are one
  `ofBlocksTiered` element wrapped in `GTStructureChannels.LES_ESSENTIA_CELL`, so **all 25 must be the same tier**
  (like the Large Essentia Smeltery) and a blueprint can pick that tier with the `essentia_cell` sub-channel; stable
  points 1/2/5/10 and the hatch cap EV/IV/LuV - Grandmaster cells (meta 3) remove the cap (`mTierLimit =
  Integer.MAX_VALUE`). EU per essentia unit = base fuel value × auxiliary fluid multiplier × `mStableValue / 25`.
  `checkMachine` resets the tier field before `checkPiece` and derives both numbers from it afterwards.
- Exactly one dynamo hatch, normal / multi-amp / **or laser** (`HatchElement.Dynamo.or(ExoticDynamo).or(LaserSource)`):
  `getVoltageLimit()` / `getAmpLimit()` follow it, `lEUt` is `voltage × amps` as a long, so the per-tick output can pass
  the MAX tier voltage. The original rejected laser hatches (`checkNoLaser`); allowing them is intentional here, so the
  multi-amp/laser output is what "generates past MAX" means. Wireless output stays off. Fuel table =
  `assets/messtech/data/essentia.json` (GoodGenerator's file) through
  `MTEssentiaEnergyData`; `checkProcessing()` keeps `mMaxProgresstime = 1` and calls `convertEssentia(...)` every tick,
  burning until `voltage × amps` and carrying the rest in `mLeftEnergy`.
- Hatch counts are checked by hand in `checkMachine` (the structure hatch element is a preview filter only, see
  `GT5U-NOTES.md` §4): exactly one input hatch, exactly one dynamo, at least one essentia hatch; errors are
  `machine.leg.structure.error.*`.
- Upgrades: right-click the controller with `MTEssentiaUpgradeItem` meta 1..9 to set its category bit (`mUpgrade` starts at
  1 = NORMAL); the bits go to every essentia hatch's `mState`. `MTEssentiaHatchTileEntity` (own block
  `MTEssentiaHatchBlock`) holds 1,000 essentia, pulls one unit per tick from adjacent TC tubes, right-click locks it to
  a jar's aspect / empty hand clears it.
- **ME essentia input hatch** (`MTEssentiaHatchMETileEntity` / `essentiaHatchME`, a subclass of the normal hatch, only
  registered when **Thaumic Energistics** is loaded via `MTThaumcraftME.register()`): an `IGridProxyable` AE network
  device (one channel); right-click with an essentia container marks the aspect as on the normal hatch, empty hand opens
  its ModularUI2 GUI. Every tick it extracts up to the configured limit (`maxInput`, NBT, default 64) of that aspect from
  the network's essentia monitor (`getMEMonitor(AEEssentiaStackType.ESSENTIA_STACK_TYPE)` +
  `extractItems(..., MachineSource)`) and stores it like any other essentia hatch, so the LEG burns it unchanged. The GUI
  shows stored/capacity, network online state and the marked aspect, and has a clear-filter button
  (`registerSyncedAction`).
- ME hatch capacity is per hatch (`capacity` + `maxInput` in the tile entity NBT, default 1,000): the HV assembler
  recipe (normal essentia hatch + AE parts) makes the default one; the **int-capacity upgrade** is a UV assembler recipe
  (1× ME hatch + 4× KekzTech ichor essentia jar + 8× ThE essentia storage bus + 64× UV circuit, 60 s) whose output item
  carries `capacity = maxInput = Integer.MAX_VALUE` in its NBT. Like GT's ME hatches (`setItemNBT`), the block copies
  those settings into the dropped item (`onBlockPreDestroy` -> `getDrops`) and reads them back on placement
  (`onBlockPlacedBy`), so breaking and replacing does not lose the upgrade; `MTEssentiaHatchMEItemBlock` shows both
  numbers in its tooltip. `dependencies.gradle` has ThE as `compileOnly` + `runtimeOnlyNonPublishable` (KekzTech ships
  inside GT5U: `kekztech.common.Blocks.jarIchor`).
- Thaumcraft is a hard dependency (`required-after:Thaumcraft` in `@Mod`, `compileOnly thaumcraft:...:dev` in
  `dependencies.gradle`). Hatch block/item, upgrade item textures (`textures/items/upgradeEssentia/`) and the hull texture
  come from GT5U 5.09.51.242 (the PRs deleted Java, not PNGs).

### Thaumcraft research tab (混乱科技 / MessTech)
- `MTThaumcraftResearch` registers its own `messtech` category (`ResearchCategories.registerCategory`, run from
  `CommonProxy#postInit` after Thaumcraft's own postInit thanks to the hard dependency) and eleven research entries with
  the original LEG recipes: infusion for the essentia hatch, cell T1, cell T2/T3/T4 and the nine upgrades; arcane crafting
  for the magic casing and the empty upgrade. Keys are `messtech.*`, lang keys `tc.research_name.<key>` /
  `tc.research_text.<key>[/.<n>]` (the page texts are the ones GT5U removed in PR 4387, both languages).
- GT5U's Large Essentia Smeltery research still owns the cells and the magic casing recipes, so they are duplicated here
  on purpose: the LEG tab has to be craftable on its own.
- Tab icon `MTResearchTabIcon` (client): uploads a `DynamicTexture` under `MTThaumcraftResearch.TAB_ICON` - the Chaos
  Matter gear (`materialicons/chaos/gearGt.png`, nearest-neighbour to 32×32) plus an overlay animation (ten hue-cycling
  sparks orbiting it and a pulsing halo ring), redrawn every other client tick from the FML client tick bus.

### Machine tooltips (house style)
- One `addInfo(translate(key))` per line, keys `<namespace>.tooltip.desc` / `.flavour`, then two or more
  `.<group>.header` + `.<group>.<bullet>` groups with `addSeparator()` between them, then `.details` / `.details.hint`.
  The look lives in the lang text: `§6` heading ending in a colon, `§7· ` bullets (the space is part of the style),
  `§b` values, `§e` tiers/hatches/coils, `§c` dangers, `§a` buffs, `§d` details. No colour code or concatenation in
  Java, no `§r`, no passing one key's text into another key as an argument; structure block calls stay
  byte-identical, and a hardcoded English machine type moves into `<namespace>.machinetype`.
- Tooling `tools/mttooltip` (gitignored like every harness): `machines.py` = refactored machines, key namespaces,
  frozen `beginStructureBlock` arguments; `verify_tooltips.py` enforces the style (Java layout, lang text of every
  line, placeholder/`%` handling, frozen structure block, no orphan tooltip key); `merge_fragments.py` splices a
  pending `tmp/mttooltip/frag_<id>.{zh,en}.lang` into both lang files and deletes keys no Java file references.
- Chinese terminology: proper nouns (blocks, casings, hatches, buses, coils, fluids, items) verbatim from the GTNH
  Chinese localisation under `../tmp/Translation-GTNH`: `GregTech_zh_CN.lang` plus per-mod overrides in
  `config/txloader/forceload/<Mod>[<id>]/lang/zh_CN.lang` (`GregTech[gregtech]`, `GregTech[tectech]`,
  `GregTech[goodgenerator]`, `GregTech[gtnhlanth]`, ...). `python tools/mttooltip/zh_terms.py lookup <regex>` searches
  it, `... check` fails on a wrong variant of a canonical name and on a raw code identifier in the text - it caught
  相干性保持 -> 相干性维持等离子导管, 电磁隔离外壳 -> 电磁隔离机械方块, 和平执行外壳 -> 和平执行机械方块,
  凝聚态转化线圈 -> 凝聚态物质转换线圈, 极寒凛冰 -> 极寒之凛冰, 数据棒 -> 闪存, 纯化水 -> 净化水,
  奇点纳米收容总线 -> 奇点纳米蜂群收容总线, 消音仓 -> 消声仓.

### MTChemicalTwister
- Structure: `A` containment field machine casing, `B` fusion coil block, `C` **heating coils**, `D` chemically inert
  machine casing with the hatches.
- Face + `getCasingTexture()` = **the formed structure**, not the mode. Level 1: GT LCR face
  (`createTextureWithCasing`, four `OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR*` overlays, idle/running + glow) on
  `Casings.ChemicallyInertMachineCasing` (element `D`, also the LCR's casing). Level 2: GT++ QFT face (four
  `TexturesGtBlock.oMCAQFT*` icon containers) on `Casings.BulkProductionFrame` (`blockCasings2Misc:12`, element `F`) -
  概率毁灭者 looks like the QFT its recipes come from; the published casing keeps it a proper GT face.
- Element `C` = `GTStructureChannels.HEATING_COIL.use(activeCoils(ofCoil(this::setCoilLevel, this::getCoilLevel)))` -
  EBF wiring: `coil` channel (StructureLib can hint/register tiers), active-coil bookkeeping, GT coil element (rejects
  mixed-level rings). API: `getCoilLevel()` / `setCoilLevel(HeatingCoilLevel)` (what `ofCoil` calls), `getCoilHeat()`
  (raw `HeatingCoilLevel#getHeat()`), `getHeatingCapacity()` (heat limit for recipes).
- `checkMachine`: forgets coil level, heat *and* structure level; missing coil -> GT's
  `StructureErrorRegistry.COIL_LEVEL_NOT_ENOUGH`; then
  `getHeatingCapacity() = getCoilHeat() + 100 * (GTUtility.getTier(getMaxInputVoltage()) - 2)` (EBF formula, MV the
  neutral tier: cupronickel = 1701 K on LV energy, 1801 K on MV).
- Level 2 (概率毁灭者): `structure_tier2` = piece `tier2`, same bounding box/controller spot as level 1 (`8, 8, 0`
  offsets work for both). Letters **F-J**, not A-E: one element map per definition, level 1 uses A-E, a collision would
  let each piece accept the other's blocks (hatches anywhere). F `BulkProductionFrame`, G
  `QuantumForceTransformerCoilCasing`, **H `SpaceTimeContinuumRipper` + that piece's hatches**, I
  `SpaceTimeBendingCore`, J `ForceFieldGlass`; geometry untouched. No coil ring: level 1 keeps EBF formula + coil
  error, level 2 runs at `MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT = 12601` (Hypogen); `checkMachine` checks level 2
  first, speculatively (`null` error list).
- Modes: `totalMachineMode() = 3` (0 化学扭曲, 1 概率毁灭者, 2 蜂窝处理者); base class adds GUI button, NBT round-trip,
  Waila line, screwdriver (`onScrewdriverRightClick`, refused while running) switches. `getRecipeMap()` =
  `MTRecipeMaps.combProcessingRecipes` (mode 2) / `MTRecipeMaps.qftProbabilityDestroyerRecipes` (mode 1) /
  `MTRecipeMaps.MTChemicalTwisterRecipes` (mode 0); `getAvailableRecipeMaps()` lists all three (NEI catalyst on every
  page). Mode 1 and 2 recipes need level 2 -> a switch without the tier-2 structure gives `insufficientMachineTier(2)`;
  mode 0 still runs there (level 2 >= 1, heat 12601). `construct`/`survivalConstruct` build the selected mode's piece.
- Mode cycle gate (`nextMachineMode()`): 0 -> 1 -> 2 -> 0, but mode 2 needs the level 2 shell - below it the cycle
  skips 2 (0 <-> 1) and the screwdriver prints `machine.largechemicaltwister.mode.comb_requires_tier2`. Both the
  screwdriver and the GUI button go through `nextMachineMode()`, so the gate holds for both; `mStructureLevel` is the
  client-synced value, and a machine left in mode 2 by a broken structure reports `insufficientMachineTier(2)`.
- Level in NEI preview / build hints: trigger stack size (`MTChemicalTwister.STRUCTURE_LEVEL_TIER2 = 2`: 1 = level 1,
  >= 2 = level 2), mode as fallback - GT's usual structure tier switch (PCB Factory picks 1/2/3 the same way). Hence
  BlockRenderer6343's **Tier slider**: BR6343 calls `construct()` with `stackSize = 1, 2, 3, ...` until the world stops
  changing (`ObserverWorld#estimateTier`), slider up to the highest tier that changed, rebuild via
  `survivalConstruct(getBuildTriggerStack(), ...)` / `construct(trigger, false)`; 2 = 概率毁灭者 / 蜂窝处理者 shell,
  1 = level 1. Own "Heating Coil" channel slider (coil element channel wrapped) can exceed 2 - any value >= 2 is level 2.
  No mixin, no extra dependency on BlockRenderer6343; hologram projector same rule (scroll to tier 2).
- Client: `getUpdateData()` returns `mStructureLevel`; GT's `BaseMetaTileEntity` compares it every tick server side
  (`handleUpdateDataChangeServer`), sends `CHANGE_CUSTOM_DATA` on change, writes it into the tile data packet
  (`tileWriteToStream`, read back as `receiveClientEvent(..., buffer.readByte() & 0x7F)` at chunk load), calls
  `onValueUpdate(byte)` - nothing else works, `MTEMultiBlockBase#checkStructure` returns early on the client. Masked
  with `0x7F`, so keep it below 128 (here 0/1/2). Previews publish their own level: `construct`/`survivalConstruct` call
  `updatePreviewStructureLevel(trigger)` (*formed* machine skipped); BR6343's dummy multiblock (never ticked,
  `DummyWorld.isRemote` even `false` -> GT sees server side) learns the tier only from the trigger.
- 概率毁灭者 pool (`mt.recipe.qft_probability_destroyer`, NEI name `概率毁灭者(QFT)`): independent copy of
  `RecipeMaps.quantumForceTransformerRecipes`, filled by `MTRecipeMaps.populateQftProbabilityDestroyerRecipes()`
  (`CommonProxy.serverStarted`, idempotent) with **all item and fluid output chances at 10000 = 100%**,
  `metadata(CHEMICAL_TWISTER_STRUCTURE_LEVEL, 2)`, `specialValue(12601)` (QFT recipes carry the focus tier in
  `mSpecialValue` via `GTRecipeMapUtil.SPECIAL_VALUE_ALIASES`, read as heat here, hence explicit), QFT catalyst as a
  **non-consumed input** (stackSize 0: required in the bus, never consumed), QFT metadata for NEI. Page:
  `maxIO(9, 9, 9, 9)`, `LargeNEIFrontend`, height 166, MessTech logo.
- 蜂窝处理者 pool (`mt.recipe.comb_processing`, NEI name `蜂窝处理` / `Comb Processing`, icon = the new
  `MTItemList.MTCombProcessingModule`): converted from the comb recipes of `RecipeMaps.multiblockChemicalReactorRecipes`
  by `com.MessTech.common.recipe.MTCombProcessingRecipes` (`populateCombProcessingRecipes()`, re-runnable, called from
  `GTRecipes.loadRecipes()` at FMLLoadComplete - both sides, after GT's and NHCoreMod's postInit - and again from
  `CommonProxy.serverStarted` as the usual fallback). Source recipes: GT5U's `ItemComb.addProcessGT` (via
  `GTRecipeConstants.UniversalChemical`) and NHCoreMod's `ChemicalReactorRecipes#multiBlockOnly` indium ones; recognised
  by a `beeComb` item input. Conversion, per recipe: **inputs** = the module (stackSize 0, first, never consumed) +
  `4` of the comb (base comb count normalised to 4, everything else - acids, catalyst dusts, circuits - dropped);
  **item outputs** = base amounts x4 (`crushedPurified` "洁净粉碎X矿" replaced 1:1 by the `mMacerateInto` material's
  dust, other items as-is) plus one extra base amount at 50% chance; **nuggets** (neutronium/osmium/platinum/iridium
  comb recipes) become the material's molten fluid (1 nugget = 16 mB) with the same x4 + 50%; **fluid outputs** kept
  unchanged (the acidic metal solutions); **indium comb** is a pinned special case: 4 indium combs -> 12 indium dust +
  50% of 3 indium dust, no fluid product (NHCoreMod's 72000 mB lead-zinc solution is dropped); **time** x4, **EU/t** x4
  (one tier up, e.g. HV -> EV); `metadata(CHEMICAL_TWISTER_STRUCTURE_LEVEL, 2)` and `specialValue(12601)`. One recipe per
  comb: GT's collision check keys by input items ignoring stack sizes, so a second recipe with the same comb would never
  be found - the first pool entry wins and the rest are logged (NHCoreMod's four indium comb recipes collapse into one,
  its converted duration/EU/t comes from the first of them). Page: `maxIO(4, 6, 0, 2)`, default frontend, MessTech logo,
  `HeatingCoilSpecialValueFormatter`.
- `MTItemCombProcessingModule` (`蜂窝处理模块` / `Comb Processing Module`): plain `Item` in
  `common/items/MTItemCombProcessingModule.java`, registered by `MTItems.registerItems()` (`CombProcessingModule`),
  texture `assets/messtech/textures/items/comb_processing_module.png` (512x512 source, used as-is), no crafting recipe
  on purpose. It is the mode 2 catalyst and the pool's NEI display stack.
- Recipe level: `MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL` (`RecipeMetadataKey<Integer>`, `drawInfo` draws
  "Required Structure Level: N"; absent = `MTRecipeMaps.DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL = 1`), checked *before*
  the heat; too big -> `CheckRecipeResultRegistry.insufficientMachineTier(requiredLevel)`.
- `createProcessingLogic()`: heat requirement (`mSpecialValue`) above the available heat ->
  `CheckRecipeResultRegistry.insufficientHeat(...)`; heat recipes overclock through the EBF's
  `.setRecipeHeat(...).setMachineHeat(...).setHeatOC(true).setHeatDiscount(true)`; `mSpecialValue == 0` (e.g. the
  template's assembler recipes) keeps standard overclocking. Heat -> scanner/sensor card/metrics via `getExtraInfoData`,
  key `GT5U.EBF.heat.s`.
- Registered as `MTItemList.MTChemicalTwister` by `MTMachineLoader` (lang key `machine.largechemicaltwister.name`) with
  its own map `MTRecipeMaps.MTChemicalTwisterRecipes`. A normal GT map registers itself: `MTRecipeMap` ->
  `RecipeMap.ALL_RECIPE_MAPS` (duplicate names throw) + default `RecipeCategory` = NEI page. Tab
  `mt.recipe.chemicaltwister` (lang keys in `en_US`/`zh_CN`), icon
  `neiHandlerInfo(setDisplayStack(MTItemList.MTChemicalTwister.get(1)))`; NEI catalyst automatic -
  `MTEMultiBlockBase` is a `RecipeMapWorkable` (via `IControllerWithOptionalFeatures` -> `IRecipeLockable`) and
  `MTChemicalTwister.getRecipeMap()` returns it. `MTRecipeMaps` class-initialised from `GTRecipes.loadRecipes()`
  (FMLLoadComplete), before NEI loads its plugins on world enter;
  `neiSpecialInfoFormatter(HeatingCoilSpecialValueFormatter.INSTANCE)` shows heat like the EBF page.
- NEI layout: `LargeNEIFrontend`, `maxIO(15, 15, 15, 15)` (3 x 5 slots per block), `logo(MTRecipeMaps.MT_LOGO)`,
  `setShiftY(8).setHeight(240)` - LCR / Plasma Forge family: item inputs top left, fluid inputs *below*
  (y = 8 + item rows * 18), outputs top right with fluids below; 5 + 5 rows -> 170x190 (items y = 8..80, fluids
  y = 98..170), text below, handler ~240. `MT_LOGO` = GregTech/ModularUI flavour of the MessTech logo
  (`UITexture.fullImage("messtech", "gui/picture/mt_logo")`), reused by `OneStepCircuitPoolFrontend`;
  `LargeNEIFrontend` overrides only the logo *position* (80, 62). Not the default frontend:
  `UIHelper.getFluidInputPositions` pins fluid slots to row y=62, `UIHelper.getItemInputPositions` grows the item grid
  down from y=6, so a big `maxIO` (the old `18, 18, 12, 12`) overlaps them; `maxIO` is display-only ("does not
  actually restrict the number of items that can be used in recipes") but sizes that phantom grid - raise it when a
  recipe outgrows it.
- Recipe classes: `com.MessTech.common.recipe.MTChemicalTwisterRecipes` (`addChemicalTwisterRecipes()`, once from
  `GTRecipes.loadRecipes()` at FMLLoadComplete) + `loadRecipePostInit()` (once from `CommonProxy.postInit()`) for recipes
  waiting on other mods (the H2O2 one). Builders grab `RecipeMap<RecipeMapBackend> MT = MTRecipeMaps.MTChemicalTwisterRecipes;`
  and end with `.addTo(MT)`. Audit: one call site (no double registration), all builders on
  `MT`, duration/EU/t/heat/structure-level on every recipe, no >64 stack clamped by the stack factories, no >64 stack in
  a plain `itemInputs`, every recipe fits the NEI grid, no shared input signature.
- One-step platinum-group-metal recipe (first builder): 45 `WerkstoffLoader.PTMetallicPowder` +
  NaOH/saltpeter/zinc/calcium + 81 potassium disulfate dust (the chain burns 11579 mB molten; 1 dust == 144 mB, so dust
  keeps the fluid list short) + ammonia/HCl/HNO3/CO/salt water -> 32 Pt, 21 Pd, 10 Ir, 24 Ru, 7 Rh, 1 Os **plus the
  whole net byproduct list of the line** (every species nothing consumes again): 162288 mB chlorine, 144737 mB NO2,
  51265 mB water, 30000 mB calcium chloride (fluid), 10338 mB ethylene, 3333 mB steam, 2105 mB molten potassium, and 172
  sodium nitrate / 69 zinc sulfate / 48 calcium chloride / 22 salt / 10 + 10 PGS residue dust. `RECIPE_IV` (7680 EU/t) /
  256 s / heat 2701 K / level 1 (`.metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)`). Werkstoff ingredients
  need `Werkstoff#get`; `GTOreDictUnificator.get(prefix, werkstoff, amount)` resolves the ore dict by
  `Werkstoff#toString` (not overridden) -> silently `null`.
- Stack sizes above 64: `GTUtility.copyAmountUnsafe(amount, stack)` (`copyAmount`/`Materials#getDust` clamp to 64)
  **and `itemInputsUnsafe(...)`** - plain `itemInputs(ItemStack...)` re-copies through `GTUtility.copyAmount`
  (`fixItemArray(inputs, false)` -> `GTOreDictUnificator.setStackArray(.., false, ..)`), silently clamping 124 to 64.
  Outputs stay as stored (`itemOutputs`), so 172/69 survive. `GTRecipe#buildItemInputCache` merges duplicate inputs of a
  type by summing: the old "two 62 stacks" form matched identically, but NEI draws one slot per input entry; one unsafe
  stack keeps an ingredient on one NEI slot (NaOH 124, K2S2O7 81, sodium nitrate 172, zinc sulfate 69).
- That recipe is the *net* stoichiometry of the whole GT5U platinum line (`bartworks` `PlatinumSludgeRecipes`), not a
  guess: each recipe as a stoichiometric vector, cyclic intermediates balanced exactly, everything produced but never
  eaten again dropped, free directions fixed by requiring a physically realisable steady state (no negative recipe
  runs), cross-checked against the Java recipe, pool registration and heat semantics. Platinum Metallic Powder is the
  only metal feed - the Pt line's "palladium enriched ammonia" byproduct carries the palladium, so no Palladium Metallic
  Powder (palladium ore). The Pd line's circuit-2 recipe (`PDAmmonia` -> `PDSalt`) is excluded on purpose: a palladium
  printer (ammonia in, palladium out) would make the ratio meaningless. Sifter yields at expected value (0.95), amounts
  rounded from the exact ratio (worst case 6 %, rhodium 6.6 -> 7), sodium hydroxide as two 62 dust stacks because
  `Materials#getDust` caps a stack at 64.
- Heat: 2701 K asked for and IV energy reaches 3001 K, so Kanthal is the minimum coil; cupronickel at IV reaches only
  2101 K and is refused. The IV EU/t needs an IV energy hatch (the search only returns payable recipes).

### MTHugeChemicalReactor (巨型化学反应釜)

- First machine on the module base: the **Large Chemical Reactor's pool** (`RecipeMaps.chemicalReactorRecipes`) with
  **perfect overclock** (`isEnablePerfectOverclock()` -> `true`, so `MTMultiMachineBase#createProcessingLogic` calls
  `setOverclock(4, 4)`: EU/t x4, duration /4, total energy untouched).
- No bonus of its own: the `getBase*` hooks of `MTModuleMultiMachineBase` stay 1, so `getSpeedBonus()`,
  `getEuModifier()` and `getMaxParallelRecipes()` are exactly what the modules supply (speed and EU multiply, the
  parallel module replaces). One parallel control module at most (`addModule`), any number of speed/EU modules.
- Module slot = element `'B'`: its `HatchElementBuilder.atLeast(...)` list ends with `MTModuleHatchElement.Module`,
  adder `MTModuleMultiMachineBase#addModuleHatchToMachineList` - the scan links the hatch and refuses a module the
  machine does not take (or a second parallel one).
- `checkMachineStructure` (base hook - `checkMachine` is final) forgets the coil level, then checks the piece plus
  `checkHasAnyInput/Output/Energy`; the base clears the module list first, so coil and modules belong to the current
  structure.
- Coil band (element `'A'`) =
  `GTStructureChannels.HEATING_COIL.use(activeCoils(ofCoil(this::setCoilLevel, this::getCoilLevel)))`: any tier taken,
  level in `mCoilLevel` (`getCoilLevel()`) and published as the coil sub channel that gives the hologram preview and
  BlockRenderer6343 their coil slider. No pool recipe asks for the level - structure requirement, not a heat source.
- Tooltip: house style, one `addInfo(translate(key))` per line, text/colours in the lang files
  (`machine.hugechemicalreactor.machinetype`, `machine.hugechemicalreactor.tooltip.*`: 配方 / 模块 / 结构 groups plus
  the `details` pair). The written 5x5x5: chemically inert machine casing shell with the hatches, a cross of PTFE pipe
  casing in the chamber middle, 8 heating coils on the face opposite the controller.
- Face: the Large Chemical Reactor's (idle + running, each with glow layer,
  `Textures.BlockIcons#createTextureWithCasing` + `ICasingTextureProvider#getCasingTexture` =
  `Casings.ChemicallyInertMachineCasing.getCasingTexture()`) - the Chemical Twister's level 1 face.
- Assembler (`RecipeMaps.assemblerRecipes`, in `GTRecipes#loadRecipes`, right after the Chemical Twister): 64
  `Machine_Multi_LargeChemicalReactor`, 4 `Machine_IV_ChemicalReactor`, 4 `Casing_Pipe_Polytetrafluoroethylene`, 8
  `Casing_Coil_Cupronickel` (the level 1 coil), 16 IV circuits
  (`new Object[] { OrePrefixes.circuit.get(Materials.IV), 16 }`, GT5U's own form), 18432 L molten PTFE
  (`Materials.Polytetrafluoroethylene.getMolten(144 * 128)`), integrated circuit 17, `eut(RECIPE_IV)`,
  `duration(MINUTES * 2)` -> one Huge Chemical Reactor. PTFE pipes and coils are the two ingredients its own structure
  is built from.
- Registered as `MTItemList.MTHugeChemicalReactor` by `MTMachineLoader` under `MT_ID + 34`, lang key
  `machine.hugechemicalreactor.name`. Brand line names no author:
  `AuthorDynamic.registerAddon(AuthorDynamic.MODULE_PROJECT, () -> translateToLocal("messtech.moduleProject"), ...)`
  prints `添加模组: ModularProject` with the word wearing the modular animation, see `MTModuleProjectText`.

### MTComprehensiveMiningPlant (综合采掘工厂)

- Ported from Elinar's standalone verification mod (`tmp/ElinarInfMiner/`), then given the **real structure** that mod
  shipped with 1×1×1 placeholder: 11 wide × 10 high × 10 deep, controller at `5, 7, 4` (front face, 8th layer up,
  centred). Shape string is the in-game Structure Writer's **transposed scan** (`[y][z]` layers of rows, chars along x),
  so `addShape(MAIN, transpose(structure_string))` and `checkPiece/buildPiece(MAIN, 5, 7, 4, ...)`; `construct` /
  `survivalConstruct` use the same offsets (`survivalBuildPiece(..., skipPlaceHints = false, skipSolidBlocks = true)`).
  Elements: A = `Casings.SteelGearBoxCasing`, B = `ofFrame(Materials.Steel)`, C = `ofFrame(Materials.Tritanium)`, D =
  `ofBlock(GregTechAPI.sBlockMetal3, 8)` (HSLA Steel Block), E = `ofBlock(GregTechAPI.sBlockMetal6, 13)` (Steel Block),
  G = `Casings.ReboltedNaquadahCasing` (the scan's "special tile" `BWTileEntityMetaGeneratedBlocksCasingAdvanced` - the
  scanner prints every tile entity as a class, so the scan could not say the meta; the chosen casing is the fixed one),
  F = `HatchElementBuilder.atLeast(OutputBus, OutputHatch).casingIndex(Casings.HighPowerCasing.getTextureId())
  .hint(1).buildAndChain(Casings.HighPowerCasing.asElement())` - the hatch region. `atLeast` is only the preview filter,
  so `checkMachine` enforces the real requirement itself: `mOutputBusses` and `mOutputHatches` must both be non-empty
  (`StructureErrors.of("machine.comprehensiveminingplant.structure.error.no_output_(bus|hatch)")`, shown by
  `createStructureErrorWidget` in the GUI terminal). No energy/maintenance hatch: `getDefaultHasMaintenanceChecks()` /
  `shouldCheckMaintenance()` are `false` and `checkMaintenance()` is a no-op - but `doRandomMaintenanceDamage()` **must
  return `true`** (the source mod returned `false`, and GT's `runMachine` guard
  `mMaxProgresstime > 0 && doRandomMaintenanceDamage()` then skipped the whole recipe tick, freezing the cycle; same rule
  as `MTMultiMachineBase`).
- **Hatch casing index must stay below 16384**: the controller's `getCasingTexture()` and F's `casingIndex` both use
  `Casings.HighPowerCasing` (TecTech `gt.blockcasingsTT:0`, casing texture page 8 → id 1024, zh name 高能机械方块).
  A BartWorks advanced casing id such as `Casings.ReboltedNaquadahCasing.getTextureId()` = 32090 **crashes the hatch
  renderer**: `MTEHatch#updateTexture` splits the id as `texturePage = id >> 7` without masking and `MTEHatch#getCasingTexture`
  then indexes `casingTexturePages[250]` while the table only has 128 pages (`ArrayIndexOutOfBoundsException: Index 250 out
  of bounds for length 128`, hit from the NEI multiblock preview through Angelica's ISBRH pass). G is therefore only a
  structure block, never a hatch background. The casing page math: `Textures.BlockIcons.casingTexturePages[128][128]`,
  valid ids `0..16383`, `getCasingTextureForId` masks but `MTEHatch` does not.
- The tooltip intentionally has **no hand-written structure description** (the "结构：" block was removed); the standard
  `beginStructureBlock / addController / addCasing / addOutputBus / addOutputHatch` section is kept for the NEI
  structure info and hint dots.
- Real work is in the GT recipe cycle (`checkProcessing`, one cycle = one second), the rest in `onPostTick`:
  - ores: `VoidMinerUtility.dropMapsByDimName` (+ `extraDropsByDimName`, merged via `isDistributionCached`) of the
    dimension, `ORE_PER_SECOND_PER_PARALLEL = 64` ores/s per ore parallel. The roll is skipped while the map's
    `getTotalWeight() <= 0`: `DropMap#nextOre` dereferences an `ores` array that is only built by
    `isDistributionCached`, so the source mod's unconditional roll could NPE on a void-mineable dimension without
    entries;
  - fluids: `GTMod.proxy.mUndergroundOil.GetDimension(dim)` underground oil field, rate =
    `GTUOFluid#getRandomAmount(Random(name.hashCode())) * fluidParallel / UndergroundOil.DIVIDER` (5000) mB/s;
  - power: no energy hatch - `WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -n)` in bulk, `EU_PER_PARALLEL =
    32768` EU/t per parallel, buffer = 2 minutes of the current draw refilled every `REFILL_INTERVAL = 1200` ticks; a
    failed refill disables working;
  - output: **standard GT output** - `checkProcessing` rolls the second into `mOutputItems` / `mOutputFluids` and GT
    ejects them into the F-region output buses and output hatches. The AE proxy, the `poweredInsert` buffers and the
    channel requirement of the source mod are gone (an ME output bus does that job now);
  - chunk: implements GT's `IChunkLoader` (`getActiveChunk`) and forces it through `GTChunkManager`.
- `checkProcessing` starts with `if (!wasWorking) prepareWorkData()`: the first recipe check can arrive in `onPostTick`
  before `onWorkStart` ran, so parallels/maps would otherwise be stale for the first second. `prepareWorkData` applies
  `configuredOre` / `configuredFluid` and re-reads the dimension data; `onWorkStart` additionally fills the EU buffer and
  loads the chunk.
- Parallel settings are GUI-only and take effect on the next start (`configuredOre` / `configuredFluid`); the GUI is
  `MTComprehensiveMiningPlantGui` (extends `MTMultiMachineBaseGui`, so the MT logo stays) whose terminal text is
  replaced - and ends with `createStructureErrorWidget`, so a missing output bus/hatch is visible - and whose button
  column has the power switch, the structure update button (the tower supports construct/survival build) and the
  screwdriver button opening the parallel sub panel. `shouldDisplayVoidExcess/InputSeparation/BatchMode/RecipeLock` are
  `false` (no recipe map).
- **Localization rule for this machine** (it has no recipe map, so all its text is its own): the sync values, WAILA NBT
  and `MTMiningPlantRegistry` only carry raw numbers and fluid registry names; every visible string is formatted client
  side from `machine.comprehensiveminingplant.*` keys in both lang files. `getInfoData()` is the exception (GT calls it
  server side, like every other machine here). The structure part names in the tooltip come from
  `Casings#getLocalizedName` / GT item display names, so they follow the pack's own localisation.
- Registered as `MTItemList.MTComprehensiveMiningPlant` by `MTMachineLoader` under `MT_ID + 79`, lang key
  `machine.comprehensiveminingplant.name`. Author line is **hardcoded**:
  `AuthorDynamic.registerElinarAuthor(...)` prints `messTech.addByAuthor` (`添加者:` / `Added by:`) + one space +
  `AuthorDynamic.Author_Elinar = "§[pf]乳胶芷若喵§r"` (the `§[pf]` marker is the petalfall text effect, closed by `§r`)
  and adds **no** `添加模组: MessTech` line. `messTech.addByAuthor` lives next to `messTech.addBy` in both lang files.
- Assembler recipe in `GTRecipes#addComprehensiveMiningPlantRecipe`, following the structure: LuV hull, 8 Steel Gearbox
  Casings (A), 4 Steel frames (B), 19 Tritanium frames (C), 7 HSLA Steel Blocks (D), 4 Steel Blocks (E), 8 Rebolted
  Naquadah Casings (G), 16 High Power Casings (F, 16 of 19 because the hatches take the rest), 8 `ItemList.OilDrill4`
  and `bartworks.common.loaders.ItemRegistry.voidminer[0]` (Void Miner I) as the functional core; 2304 L molten PTFE,
  circuit 6, `eut(RECIPE_LuV)`, 2 minutes. Casing stacks are built with the local `casingStack(Casing, amount)` helper:
  GT's `ICasing#toStack(int)` **ignores its amount argument** in 5.09.54.205 (always returns a stack of one), so
  `.toStack(8)` would silently make the recipe need a single casing - note that several older recipes in
  `GTRecipes` still use `.toStack(N)` and therefore under-count the same way. The source mod scanned
  `GregTechAPI.METATILEENTITIES` for the Void Miner class name; the BartWorks list is the direct reference.
- `/messtech infiore` lists the loaded machines: `MTMiningPlantRegistry` is a static, save-local `LinkedHashMap` keyed
  `dimension:x:y:z`, filled on `onFirstTick`, dropped on `onRemoval` and **cleared on server stop**
  (`CommonProxy#serverStopped`, wired in `MessTech#serverStopped`; the source mod leaked entries across worlds). The
  command sends a `ChatComponentTranslation` (`command.messtech.infiore.entry`), so the output is localized client side.

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
- Mode 0 = Component Assembly Line: generic `ProcessingLogic` recipe map lookup; recipe casing tier (`mSpecialValue`)
  capped by `MTMultiMachineBase#getEnergyHatchTier()` (highest energy hatch tier, exotic hatches included) - so
  laser/multi-amp hatches count, the best hatch sets the limit, and that tier also drives the Tier 1 parallel count.
- Mode 1 = Assembly Line: data-stick / Data Access, LevelTier 2, unordered input matching by `MTAssemblyLineMatcher`
  instead of a recipe map lookup (not the original ordered AL), no single-recipe locking
  (`supportsSingleRecipeLocking()` false, like GT's own Assembly Line).
- Structure `F` accepts the Data Access hatch.
- `MTRecipeMaps.assFactoryAssemblyLineRecipes` = independent Assembly Line map holding **only fake NEI pages**, one per
  `RecipeAssemblyLine` definition (alternatives kept, NEI cycles them in one slot), no runnable recipes on purpose - see
  `docs/GT5U-NOTES.md` § 11.6.
- `MTAssemblyLineMatcher` resolves an authorised definition per recipe check against the input buses: each bus one
  unordered pool (wildcard-damage alias in, NBT ignored), each slot taking the alternative leaving the most parallels -
  ingredients may sit in any bus in any order, "A or B" satisfied by whichever is there. That list then runs the normal
  `ProcessingLogic` pipeline: parallels, perfect overclock, void protection, debug/ME buses and consumption stay shared
  code.
- Front texture: Advanced Molecular Casing base + Quantum Force Transformer face overlay; tooltips mention modes, data
  access, energy tier limit.

### MTInventoryInputBusME / MTInventoryInputHatchME
- ME hatches `32407` (item bus) / `32408` (fluid hatch); extend the GT5U advanced stocking ME bus/hatch classes ->
  advanced GUI with auto-pull toggle (only the first 16 entries shown).
- Auto-pull: every `startRecipeProcessing()` snapshots the whole AE storage, exposing every distinct ME item/fluid type
  above the configured minimum stock size/amount for one recipe check - the original stocking hatch's per-recipe-check
  pull, so machines feed continuously.
- `autoPullRefreshTime` still governs the parent GUI's first-16 auto-refresh, not the full snapshot.
- Manual mode: normal GT5U stocking bus, only the manually configured 16 slots, no full-network pull.
- `endRecipeProcessing` extracts only the consumed difference from AE; registered in `MTMachineLoader` and
  `MTItemList`.

### MTWirelessVacuumConveyorInput / MTWirelessVacuumConveyorOutput
- Registered as `32409` (input) / `32410` (output); extend the normal GT5U VCI/VCO classes, so existing NAC module
  structure code accepts them. They keep the plain GT5U vacuum hatch GUI; the frequency/private GUI of the first
  implementation is gone.
- Pairing is **data stick address pairing**, adapted from GT-Not-Leisure's `WirelessVacuumConveyor*Hatch`
  (`tmp/GT-Not-Leisure/src/main/java/com/science/gtnl/common/machine/hatch/`): left-click the output hatch with a
  data stick -> the stick stores `{type, dim, x, y, z}` (`type = messtechWirelessVacuumConveyorOutput`) plus a
  display name; right-click an input hatch with that stick -> both hatches store each other's address
  (`wirelessVacuumLink` NBT on each side, dimension included) and the link is ready. Rebinding detaches the old
  partners first; an unloaded or foreign old partner is left to self-heal (`isDeadLink`). Binding loads the target
  chunk once (`GTUtil.getTileEntity(..., true)`) so a far-away output can be paired from an input on the other side of
  the map; the move tick itself never loads chunks.
- **Strict 1:1**: the output keeps one input address and the input keeps one output address. The transfer is push-only
  from the output - a single mover at `tick % 20 == VACUUM_MOVE_TICK` (= 17) - and only while both reverse references
  match, so the old pull+push double path (two outputs merging into one input, two inputs racing for one output)
  cannot happen. A missing/wrong-type/re-pointed counterpart is dropped by the tick; a colour mismatch only pauses the
  link.
- **Same colour is mandatory** and unpainted never links: the dye is still the NAC module routing channel, so it is
  checked at bind time and re-checked on every move tick - repainting one end pauses the link until both match again.
- Cross-container: works across NACs and dimensions while both chunks are loaded (`DimensionManager.getWorld` +
  `blockExists`, never force-loaded); an unloaded counterpart reports `chunk_unloaded` / `dim_unloaded` and the packet
  stays in the output hatch.
- Both hatches: `canConnectOnSide()` false, `getNeighbours()` empty and removed from `VacuumFactoryGrid` on first
  tick / colour / facing change, so they never join a wired vacuum network. `@IMetaTileEntity.SkipGenerateDescription`
  on both classes - GT checks the **concrete** class (`ItemMachines#isSkipGenerateDescription`), so it has to be
  repeated on our subclasses - and `getDescription()` is rebuilt by `WirelessVacuumConveyorUtil#decorateDescription`.
  `getInfoData()` appends one live link-state line from `WirelessVacuumConveyorUtil#appendLinkInfo` (unpaired / linked
  with dim+coords / dim or chunk unloaded / target missing / wrong hatch type / colour mismatch / stale link, plus the
  expected counterpart address when not cleanly linked), encoded with `IGregTechDeviceInformation.encode` so the client
  translates it.
- Unbind: sneak + wire cutter right-click on either hatch (`onWireCutterRightClick`). The own side is always cleared,
  the counterpart only when the player may configure it.
- Permissions: binding/unbinding re-points the *output* hatch, so `canConfigure` requires the player to be its owner or
  an operator (`player.canCommandSenderUseCommand(2, ...)`).
- Legacy: the first implementation stored `wirelessFreq` / `wirelessOwner`; `loadNBTData` reads its own address and then
  drops both old keys, so an old world's wireless hatches start unpaired and are re-paired with a data stick.
- Crafting is unchanged: one assembler recipe per hatch on its wired counterpart (`ItemList.Hatch_VacuumConveyor_Input`
  / `_Output`), UIV.

### MTWirelessBeamlineInput / MTWirelessBeamlineOutput / MTWirelessBeamlineAdvancedOutput
- Registered as `32470` (input), `32471` (output), `32472` (filtered output); extend GT5U's
  `MTEHatchInputBeamline` / `MTEHatchOutputBeamline` / `MTEHatchAdvancedOutputBeamline`, so every beam machine (LHC,
  beam splitter, beam crafter, target chamber, LINAC, source chamber, synchrotron, beam mirror, beam stabilizer) accepts
  them unchanged - the structure adders and `BeamHatchElement` only test `instanceof` / `mteClasses()`. Caveat: machines
  building slots with `buildBeamline*Hatch(...)`/`hatchId(...)` (LHC, beam splitter, beam crafter, LINAC, beam mirror,
  stabilizer) filter the *hologram* by MTE id, so a correctly placed wireless hatch is flagged by the "show errors"
  channel and autoplace can only pull the wired hatch; the structure still validates and the machine runs - place them
  by hand.
- Pairing key = **only the hatch dye colour** (`getBaseMetaTileEntity().getColorization()`, `-1` = unpainted, never
  links; re-read live, so repainting re-pairs immediately). `WirelessBeamlineUtil` keeps a dedicated registry because two
  isolated hatches have no beamline pipe edge; pairing works across machines and dimensions **as long as both chunks are
  loaded** - a chunk unload unregisters the hatch (`onUnload`), so an unloaded counterpart counts as "no counterpart"
  and the beam is dropped instead of being pushed into an unloaded tile entity.
- 1:1 is enforced at push time: the colour channel must hold **exactly one output and exactly one input**, else the
  packet is dropped, never handed to an arbitrary hatch. `moveBeam()` replaces the wired hatch's pipe walk on the same
  `MOVE_AT` hook (`tick % 20 == 4`), so the cadence matches a wired beamline.
- `canConnect()` false on both sides, so beamline pipes neither connect to nor render a connection for them. The wired
  output hatch ignores `canConnect` when walking its line of sight, so the wireless input also overrides `setContents()`
  and only accepts the packet its own `WirelessBeamlineUtil.moveBeam()` is handing over: a wired hatch parked in front
  cannot bypass the colour channel.
- The input keeps GT's inherited expiry logic: a beam not refreshed within one move tick is dropped, so repainting a
  hatch or breaking the pair stops the machine instead of caching a stale particle stream.
- The filtered variant inherits the LHC/beam splitter particle blacklist: the machine writes `acceptedInputMap` and
  `dataPacket`, GT5U's ModularUI edits the map - only the transport changed.
- Scanner info (`getInfoData()`) gains two lines and stays inside the eight documented by `IGregTechDeviceInformation`:
  the colour name (GT5U's `GT5U.infinite_spray_can.color.*` key with a `Dyes` name fallback) and the live link state
  (unpainted / no counterpart / conflict / linked), emitted through `IGregTechDeviceInformation.encode(...)` /
  `translatable(...)` - or as a bare lang key - so the client translates them; registry state stays server-side.
- Lang in `machine.wirelessbeamline.*`. The hatches carry `@IMetaTileEntity.SkipGenerateDescription` because their
  description goes straight to the tooltip: without it GT5U dumps one instance's description into `GregTech.lang` at
  client setup. GT5U checks the **concrete** class (`ItemMachines#isSkipGenerateDescription`), so the annotation cannot
  be inherited - the wireless vacuum conveyor hatches carry it on their own classes for the same reason.
- Crafting: one assembler recipe per hatch in `GTRecipes.loadRecipes()`, each on its wired counterpart
  (`LanthItemList.LUV_BEAMLINE_INPUT_HATCH` / `LUV_BEAMLINE_OUTPUT_HATCH`, filtered variant on
  `ItemList.AdvancedBeamlineOutputHatch`) reusing the wireless vacuum conveyor kit verbatim: AE2
  `item.ItemMultiMaterial` 8:47 x8, `ItemList.WormholeGenerator`, UIV sensor (input) / emitter (output) x4, UIV
  circuit x4, `wireGt16` SuperconductorUIV x64 and the advanced redstone receiver (input) / transmitter (output) cover,
  on `MUTATED_LIVING_SOLDER` (input) or `Lubricant` (output), UIV, 2 minutes. Not retiered to the hatch's own LuV/UV
  tier, so the wireless upgrade is priced as a UIV build.

### MTNanoScaleFoundry and the one-step circuit pool
- Registered as `32411`, extends `TickableParallelismAcrossMultiMachineBase`, 3x3x3 structure.
- 11 threads/circuits 1-11 = the 11 normal NAC pools (`getMaxThreadCount()` = 11); the old 12th thread is gone - the
  former "24" pool is **machine mode 1**.
- **Modes** (`totalMachineMode()` = 2, mode 0 default): an Astral Array consumed from an input bus
  (`consumeAstralArrayFromBuses()`, NBT-persisted) unlocks mode 1 (`supportsMachineModeSwitch()` ->
  `astralArrayUnlocked`); `getGui()` adds the MUI2 mode icons only then, the screwdriver (`onScrewdriverRightClick`)
  switches too. Both refuse while **active** (`isMachineActive()` = `IGregTechTileEntity#isActive`, from
  `mMaxProgresstime > 0`; mode 0's one-second cycle keeps it active when on); `setMachineMode` refuses mode 1 without
  the array. Entering mode 1 **drops** the held tasks (`clearAllTasks()`, no refund), only reachable switched off since
  `checkProcessing` stops ticking them. Silent GUI refusal (icon, `gt.interact.desc.mb.mode`, Waila running-mode line),
  `machine.nanoscale.*` chat line from the screwdriver.
- Mode 1 disables every thread, delegates to `super.checkProcessing()` -> the **main thread** runs one recipe through
  the shared GT pipeline (`mMaxProgresstime` / `mOutputItems` / `lEUt`); `getRecipeMap()` = the one-step pool,
  `canUseControllerSlotForRecipe()` false -> selector from an input bus circuit slot like an independent machine;
  `shouldDisplayCheckRecipeResult()` only true here.
- Mode 1 GUI/Waila: per-thread rows and Waila list hidden; `getWailaNBTData` writes only `oneStepMainThread`,
  `getWailaBody` adds `machine.nanoscale.status.mainthread` ("机器主线程运行" / "Machine main thread running") under GT's
  progress bar and the `MTMultiMachineBase#getWailaNBTData` "running mode" line.
- `onWireCutterRightClick` refuses to remove the Astral Array while mode 1 runs; a successful extraction drops back to
  mode 0.
- Mode 0 binds **per input source**: an input bus by its own circuit slot, a programmable hatch's pattern buffer by its
  carried circuit; no machine-wide selector (the controller slot selects nothing) - several pools at once, items never
  cross.
- **Programmable hatches** (`IDualInputHatch`, e.g. Programmable-Hatches' pattern hatches) go into
  `mDualInputHatches`, **never** `mInputBusses` - the scheduler must read both or the hatch is invisible. Each
  `IDualInputInventory` is its own source (`DualInputBinding`): one buffer per AE pattern with its own unwrapped circuit
  beside the items, so one hatch feeds several pools; that circuit is zero-sized
  (`BufferedDualInputHatch.DualInvBuffer#programLocal`), a buffer marker beating the hatch-wide one, which falls back to
  the shared (ghost) slot. Markers never enter the ingredient list.
- Consumption from such a hatch **is** the stack decrement: `getItemInputs()` hands the live buffer stacks,
  `depleteThreadItems` shrinks them, the hatch refills on the next AE push - GT's own dual-hatch behaviour;
  `removeResource` on top would deduct twice. Buffer fluid drains the same way (Board Processor immersion tank too).
- `checkProcessing()` keeps mode 0 on a one-second GT cycle and calls the scheduler inside the standard `checkRecipe()`
  wrapper, so `startRecipeProcessing()` / `endRecipeProcessing()` run for ME buses/hatches; `lEUt` from running task
  totals so GT drains energy normally.
- Input-bus circuit numbers are snapshotted in `onPostTick` (`duringProcessing = false`) and at the top of
  `checkProcessing()` (`duringProcessing = true`): an ME bus exposes its circuit only during the recipe check, at the
  second-to-last virtual slot (`getSizeInventory() - 2`) - fine for GT's fixed 16+2 layout and variable-size ME bus
  subclasses; a wrong slot binds the bus to an arbitrary AE item or no pool.
- **Renamed items match like GT**: `inputSatisfies` ignores NBT exactly when the recipe is not NBT sensitive and the
  ingredient is not one of GT's three NBT-always items (IC2 universal fluid cell, GT data stick, data orb;
  `GTRecipe#shouldCheckNBT`, mirrored from `MTParallelHelper`) - a Forming-Press Mold (Name) rename (`display.Name`)
  satisfies the tag-free input, a data stick must still carry the right data. Without it the lookup matched but the
  pre-consumption check rejected (thread reported 空闲). Consumption hands the bus its **own** stack
  (`findThreadResourceInBus`, then `found.copy()` into `removeResource` via `depleteThreadItems`), GT's removal
  comparing NBT.
- Each thread runs one active recipe at a time.
- Board Processor uses per-thread internal immersion tanks (`BoardTankState`), not recipe fluid depletion: one per legal
  fluid, **five** since GT5U 5.09.54.183 added UU-Matter (`MTEBoardProcessorModule#LEGAL_FLUIDS`; its impurity is
  UU-Amplifier). Type list, fluid and resulting impurity live in `MTNanoScaleFoundry`'s `BOARD_TANK_FLUIDS` /
  `boardTankFluid` / `boardTankImpurity`; GUI tank grid and Waila read `getBoardTankTypeCount()`, so a new fluid is one
  table entry plus a GUI row. Tanks fill from fluid hatches and from the Board pool's pattern buffers.
- Waila sends per-thread name/index/active/progress/EU/parallel, first task's output item names/counts, board tanks,
  `isSneaking` (AE's `WirelessDataProvider` flag); collapsed: **one** machine-wide progress bar (sum over running
  threads, GT's `GTWaila.getMachineProgressString`), sneaking expands per-thread, board tank and output item lines.
  Mode 0 only.
- The 11 per-module NEI pools (`mt.recipe.nanoscale.*`, one per original NAC pool) copy GT5U's maps 1:1, `maxIO`
  included. Four envelopes grew in GT5U 5.09.54.183 and are mirrored: Part Processor (ident still `smdprocessor`) 1/1/0/0
  -> 6/4/3/0, Etching Array 2/1/2/0 -> 2/2/2/0, Wire Tracer 1/1/0/0 -> 2/4/0/0, Encasement Wrapper 4/1/0/0 -> 4/1/2/0.
  `maxIO` is only the NEI envelope (`RecipeMapBackend#doAdd` validates minimums, never maximums), so a stale value
  mis-draws wider recipes outside the panel. The one-step pool flattens GT5U's own maps, not these copies.
- `MTRecipeMaps.oneStepCircuitPoolRecipes` = the renamed one-step pool (`mt.recipe.nanoscale.onestepcircuitpool`,
  display stack = controller) with `OneStepCircuitPoolFrontend`: 9 slots per row, progress bar / single item output /
  logo on the header row, item inputs 9x6 under it, fluid inputs 9x6 below; the selector circuit is a normal recipe
  input, no special slot.
- Its recipes are **fake** (`addFakeRecipe`): `RecipeMapBackend#filterFindRecipe` and `GTRecipeLookupBuilder#add` skip
  `mFakeRecipe`, keeping pages with up to 54 item + 54 fluid inputs out of `GTRecipeLookup`. Mode 1 resolves its own
  match: `createProcessingLogic()` overrides `findRecipeMatches(map)`, scans
  `oneStepCircuitPoolRecipes.getAllRecipes()` with `GTRecipe#isRecipeInputEqual(false, false, inputFluids, inputItems)`
  and hands the winner to the shared pipeline - `MTAssFactory`'s Assembly Line shape, so parallels, overclock, void
  protection, ME buses and consumption stay GT's. `findRecipeQuery` never sees them.
- `populateOneStepCircuitPoolRecipes()` recursively flattens every original Assembly Matrix recipe through Assembly
  Matrix + all module pools down to `CircuitComponent.realComponent` real inputs; keeps the Crystal/Wetware/Bio/Optical
  line (Processor/Assembly/Supercomputer/Mainframe) plus the special Pico/Quantum/Planck/Raw Manifold Bud products;
  drops Board Processor fluid inputs (machine-internal/NEI display only), keeps other module and Assembly Matrix
  fluids, merges duplicates and deduplicates recipes.
- `packMaterialInputs()`: `MOLTEN_PREFIXES` = `bolt`, `foil`, `frameGt`, `itemCasing`, `plate`, `screw`, `wireFine` ->
  the same material's molten fluid (bolt/fine wire/screw `M / 8` = 18 mB, foil `M / 4` = 36 mB, casing `M / 2` = 72 mB,
  plate `M * 1` = 144 mB, frame box `M * 2` = 288 mB, computed as `prefix.getMaterialAmount() * INGOTS / GTValues.M`).
  Wires: 1x -> 16x (16:1, remainder stays 1x) only for `Superconductor*` (`CircuitComponent` SuperconductorLuV..UMV);
  the rest melt, as the NAC chains' SpaceTime (时空) and Infinity (无尽) wires do. `packChips()`: walks
  `bartworks.system.material.CircuitGeneration.CircuitWraps#VALUES` (chip -> wrap, cached in `chipWraps`) - 16 wrapped
  parts (RAM/NOR/NAND chips, CPUs, circuit boards, SMD parts) = one wrap + `1 * HALF_INGOTS` = 72 mB molten
  polyethylene, exactly GT's `CircuitWraps#registerWrapRecipe` (16 parts + circuit 16 + 1 * HALF_INGOTS; that circuit is
  its selector, the machine's own slot already carrying the pool selector). Amounts identical
  (`16 x 1x wire == 1 x 16x wire`, wrap = 16 parts + polyethylene); same-material entries merge, a remainder keeps its
  item form, no molten form -> item kept. `chipWraps` is built first and the build deferred while empty (GT registers
  the wraps in postload), so the server-started fallback has them instead of baking plain chips in. Lookup:
  `GTOreDictUnificator.getAssociation()`, else `moltenOfName()` parses the ore dictionary name (`boltRhugnor`,
  `screwQuantum`) -> `Materials.get()` / case-insensitive `Materials.values()` -> GT++ `Material.mMaterialsByName` /
  `mMaterialCache` (`getFluidStack`) - GT++ materials (Rhugnor, Quantum, AstralTitanium, ...) are **not** GT `Materials`
  and carry no `ItemData`.
- Input order is normalised after packing: merged again (packing can duplicate 16x/leftover wires) and sorted by
  display name, so a part family (both RAM chips, every superconductor wire, every board) stays adjacent, then by ore
  dictionary signature, item and amount; fluids by localized name.
- NEI page order from `recipeComparator`: selector levels 1-5 (Processor/Assembly/Supercomputer/Mainframe, then the
  original GT ladder), then one selector per special chain (6 Pico, 7 Quantum, 8 Planck, 9 Raw Manifold Bud) last.
- Selector 1-9; every flattened recipe carries it as a real `GTRecipeBuilder.circuit(n)` input, so it matches like any
  GT machine (the bus circuit slot satisfies the input and is never consumed). `getOneStepCircuitLevel()` returns 5 for
  `CircuitCalibration.PRIMITIVE` (original GT ladder: Nand chip / Microprocessor / IntegratedProcessor / NanoProcessor /
  QuantumProcessor), 1-4 by name, and one dedicated circuit per special chain,
  `MTRecipeMaps.ONE_STEP_PICO_CIRCUIT_LEVEL` .. `ONE_STEP_RAW_MANIFOLD_BUD_CIRCUIT_LEVEL` (= 6-9). The specials **used
  to share 6**, but the Quantum page flattens the whole Pico chain into its own inputs (the Quantum chain consumes Pico
  circuits through the Pico Circuit Rack), so its inputs are a superset of the Pico page's: a shared selector matched
  the cheaper Pico page first and no Quantum circuit was ever made. The selector is the only thing that can tell the
  two apart - do not fold them back under one number.
- Measured max IO before packing: 61 distinct item inputs / 24 distinct fluid inputs (PlanckCircuit, after the producer
  lookup learned to follow every output slot of a multi-output module recipe). Packing moves entries from the item grid
  into the fluid grid, so the map is declared `maxIO(54, 1, 54, 0)` = 9x6 item + 9x6 fluid rows; the even split keeps
  the page height at 254 with room on both sides. The 61/24 were measured with packing off and no longer describe the
  pool; only the capacity matters. The circuit input takes one of the 54 item slots. `OneStepCircuitPoolFrontend` owns
  the constants (`maxIO`, slot positions, recipe background 170x254, NEI panel height), and
  `populateOneStepCircuitPoolRecipes()` logs a recipe's name with both counts if it grows past the capacity - the only
  place a current peak shows up.

### Ultimate Pattern Terminal (终极样板编码终端)
- AE **cable part**, not a block: `PartUltimatePatternTerminal extends PartPatternTerminal` + `IPatternTerminalEx`;
  `ItemUltimatePatternTerminal` = `IPartItem`; `ApiPart#placeBus` on the clicked side's cable bus, AE's `BusRenderer` draws
  it in the inventory; host tile = AE's cable bus. Takes a channel, pulls blank patterns, writes AE's encoded pattern item back.
- Copied from AE's `PartPatternTerminalEx` (template, not superclass - GTNL bullet): `GUI_PATTERN_TERMINAL_EX` bridge,
  forced processing mode, `exPatternTerminalCall`'s orientation pick, NBT keys, GUI icon. Not carried over from
  GT-Not-Leisure: Avaritia 9x9 crafting mode, `DireCraftPattern`, mode button, both NEI overlays.
- **Input** grid 4x4 x `INPUT_PAGES` (8) = 128 cells (AE: 4x4 x 2 = 32) for the Nano-Scale Foundry one-step pool (54 item +
  54 fluid inputs); **outputs** = AE's 4x4 x 2 = 32 cells and placement; inverted flag + active page in own fields, so a flip
  loses nothing (AE's `PartPatternTerminalEx#setInverted` clears every input cell past the collapsed layout).
- Not extending `PartPatternTerminalEx` also dodges other mods' `instanceof PartPatternTerminalEx` gates. GTNL's
  `enableProcessingPatternCapacityMixin` (default on) resizes **every** `PartPatternTerminalEx`'s `crafting`/`output`
  `IAEStackInventory` to `16 x multiplier` cells when the container is constructed, clamping saved NBT to it -> 128 to 32
  cells under an eight-page GUI -> page three threw `ArrayIndexOutOfBoundsException: Index 32 out of bounds for length 32`
  (`IAEStackInventory#getAEStackInSlot`, virtual pattern slot). Plain `PartPatternTerminal` escapes it; GTNL's
  container/GUI page-count mixins are harmless (own container + GUI override the counts in subclasses).
- GUI + container own, no AE mixins (`MixinContainerPatternTermEx` / `MixinGuiPatternTermEx`, group
  `Mixins.ULTIMATE_PATTERN_TERMINAL`: gone):
  - `GuiUltimatePatternTerminal` = copy of AE rv3-beta-1080-GTNH's `GuiPatternTermEx` (`extends GuiPatternTerm`) owning
    what subclassing cannot reach: the private `processingScrollBar` range (container page count, eight pages) and the
    client-side container class.
  - `ContainerUltimatePatternTerminal extends ContainerPatternTermEx` answers **both** page counts from the part (AE inlines
    `PartPatternTerminalEx.exPatternInputsPages` = 2 / `exPatternOutputPages` = 2); outputs stay 2, answering them keeps
    GTNL's mixin out. `CommonProxy` builds it server-side, the GUI client-side; sync handlers, pattern slots, virtual-slot
    write path = AE's.
  Netting: no shared AE class patched -> AE's extended terminal (AE2FC's fluid one, addons') unaffected.
- Input layout (`GuiUltimatePatternTerminal`): AE's page-count loop, inverted column's row index wrapped
  (`activePage % rows`; eight pages vs AE's four rows). Outputs differ - 8 input pages grouped four per output page: the
  normal four-cell output column walks all 32 cells (pages 0-3 -> outputs 0-15, 4-7 -> 16-31), the inverted 16-cell output
  page on output page 0 for input pages 0-3, page 1 for 4-7. AE's loop wraps page 4 to page 1's outputs
  (`activePage % outputRows` / `% outputPages`) - the "Page 4 shows page 1's output" bug.
- Open path: Forge, not AE's `GuiBridge` (container->GUI class by name; a new terminal would touch AE's own):
  `PartUltimatePatternTerminal#onPartActivate` packs the side above `GUI_ULTIMATE_PATTERN_TERMINAL` (20) and
  `player.openGui`s every click incl. sneak (AE's path -> AE's two input pages = a quarter of the grid).
  `CommonProxy`/`ClientProxy`: `Platform.getPartFromTE` for the part, `ContainerUltimatePatternTerminal` to the server, plus
  the `ContainerOpenContext` a Forge `IGuiHandler` GUI must set itself (AE fills it only in `Platform#openGUI`; missing ->
  middle-click "how many to craft" on the blank pattern slot NPEs). `getGui(player)` first -> craft permission stays AE's.
- Pattern NBT = AE's (`PatternEncodingHelper`: `in`/`out` of `IAEStack#toNBTGeneric()`, no `crafting` key, item
  `encodedUltimatePattern`); expected outputs hand-placed or NEI-imported - no crafting page.
- Artwork: AE's `appliedenergistics2:ItemPart.PatternTerminalEx` sprite (number 0, block atlas), cable face
  `PartPatternTerm_Bright`, GUI `pattern3.png`/`pattern4.png`. **No `blocks/` prefix in a texture name**: both atlases
  prepend `textures/blocks`, so `appliedenergistics2:PartPatternTerm_Bright` ->
  `assets/appliedenergistics2/textures/blocks/PartPatternTerm_Bright.png`; `appliedenergistics2:blocks/...` wants a
  non-existent `textures/blocks/blocks/` -> purple-black missing texture (`CableBusTextures.registerIcon` names it alike).
- Recipe unchanged (titanium frame around AE's extended pattern terminal part, two Engineering Processors, one Calculation
  Processor, one Pattern Capacity Card) -> part item, registry name `UltimatePatternTerminal`; existing worlds lose the old
  block, no migration.
- NEI: `NEI_MessTechConfig` makes **every GT machine recipe page** transferable (Nano-Scale Foundry one-step pool included)
  via `RecipeCategory.ALL_RECIPE_CATEGORIES`, taking categories whose frontend registers NEI pages (like
  NotEnoughEnergistics picks idents); each gets `API.registerGuiOverlay` (`RecipeInfo` matches GUI exact class + page
  unlocalized name) + `API.registerGuiOverlayHandler` (transfer button). Unregistered ident -> no button; no
  `addRecipeCatalyst` deliberately - pattern terminal, not a machine.
- Handler by pack: **NotEnoughEnergistics** (GTNH transfer, now a dependency) registers `NEEPatternTerminalHandler.instance`
  over NEU's idents (`RecipeProcessor`'s processors + a null ident, minus the two crafting idents - no crafting), so NEU's
  options apply; without NEU this mod's `PatternImportOverlayHandler` takes GT categories only (reads GT's
  `FixedPositionedStack` flags, absent elsewhere). NEU registers only for AE's terminal classes, NEI matches by exact class
  -> explicit idents here.
- Import into input cells 0, 1, 2 .. in page reading order (top to bottom, left to right), not by panel position: pages are
  four cells wide, GT panels much wider, so a positional copy scatters ingredients over empty cells. Ingredients from GT
  (`PatternImportOverlayHandler#isIngredient`): item/fluid inputs = a `GTNEIDefaultHandler.FixedPositionedStack` with
  `isInput` true, outputs false; `isNotConsumed()` drops "required but not consumed" ones (mold, programming circuit ...) -
  GT marks them zero size/amount, a pattern holding them would consume them; NEU defaults alike.
- **Never test that by position against the frontend's constants**: GT NEI slots = frontend position +
  `GTNEIDefaultHandler.WINDOW_OFFSET` (`(-5, -11)`, panel interior) + the pixel `FixedPositionedStack` adds -> item panel at
  `rely` 16, not `ITEM_Y` 26, both panels' first column at `relx` -1; a filter from
  `ITEM_X`/`ITEM_Y`/`itemRows()`/`fluidRows()` misses the whole first item row (9 of 9), the first item of every following
  row and the first fluid of every fluid row, and a page whose item inputs fit those ten slots lost every item, keeping only
  fluids. The same offset imports for free the extra rows GT adds beyond the pool's `maxIO` - nothing is measured against the
  pool's rectangles any more.
- Result too, but **not** from `IRecipeHandler#getResultStack` alone: `GTNEIDefaultHandler.CachedDefaultRecipe#getResult()`
  answers `null` (the item output is drawn as an "other stack"), so `getResultStack` + `getOtherStacks`, in that order -
  first only left the output grid empty and AE encoded an input-only tunnel pattern, no processing pattern.
- Server payload (`PatternImportHandler`): one compound, `in` + `out`, cell index -> ItemStack, so no geometry server side;
  target via `player.openContainer instanceof ContainerPatternTerm` + part check, then `inputsSync`/`outputsSync` dirty; the
  overlay recognises its GUI by class.
- Count **again** per cell under `PatternImportHandler.COUNT_KEY` (`MTCount`, a long) by
  `PatternImportHandler#writeCell`/`#readCell`: 1.7.10 stores `ItemStack`'s count in the NBT **byte** `Count`
  (`ItemStack#writeToNBT` line 178) and reads it as a byte, so outside -128..127 it changes (1024 and 4096 -> 0, 1025 and
  4097 -> 1), and the flattened pool uses those (Planck chain's page: 1024 Optical Fiber Cables, 4096 ASoC RAMs). Such a
  cell then showed a bare item (AE draws an amount only for `stackSize > 1`; `AEItemStack#drawInGui` forces the icon count
  to 1 via `AEStack#drawOverlayInGui`, from `VirtualMESlot#drawStackAndOverlay`) and the encoded pattern got `Cnt: 0` -
  material gone. AE's writes keep the size in the long `Cnt` with `Count: 0` on purpose (`PatternHelper` restores from
  `Cnt`), so counts survive only inside AE stacks; the NEI payload is the one hop through a vanilla `ItemStack`. `readCell`
  settles a missing/zero count on 1 (zero-count is not encodable) and reports a cell with no inventory room.
- Import sets **processing** mode first (`ContainerPatternTerm#setCraftingMode(false)`), like NotEnoughEnergistics: a pool
  page is a machine recipe, and in crafting mode AE's next `detectAndSendChanges()` mirrors the first nine cells into the
  matrix (`copyToMatrix`), clamping to 1 and clearing non-items. `PartPatternTerminalEx` already defaults to it - this guards
  hand-switched terminals.
### Blocks
- `AssMatrixBlock` (Tier 1) / `AdvAssMatrixBlock` (Tier 2).
- Static helpers: `getBlock()`, `getItem()`, `getItemStack()`, `getItemStack(int)`.
- Added to `MTItemList` as `AssMatrixBlock`, `AdvAssMatrixBlock`.

## NEI collapsible item groups (物品折叠：模块仓与 NAC 部件)

NEI has no API for the collapsible item groups, so the groups are installed the way Twist Space Technology does it:
`NEI_MessTechConfig#loadConfig()` (an `IConfigureNEI`) calls `MTNEICollapsibleItems#load()`, which rewrites
`config/NEI/collapsibleitems.cfg` with a marker block (`# BEGIN/END MESSTECH COLLAPSIBLE ITEMS`). NEI's plugin loader
runs every `loadConfig()` and only then `CollapsibleItems.load()`, which parses that file - so the groups are live in the
same startup. `MTNEICollapsibleConfig` is the pure text part: it replaces/append the block and leaves the GTNH groups,
other mods' blocks and player edits alone.

- The block format is NEI's: before each filter line an optional `; {"unlocalizedName": "..."}` line names the group
  (resolved against the client language, hence the `messtech.nei.collapsible.*` keys in both lang files), then one
  `ItemStackFilterParser` expression per group. NEI strips `#` comments before parsing, so the markers are invisible to
  it. If the file does not exist yet the NEI jar's `/assets/nei/cfg/collapsibleitems.cfg` is the base (NEI would copy it
  later anyway); writes go through a temp file + atomic move, and a file whose content is unchanged is left untouched.
- Groups: speed module hatches, EU discount module hatches and parallel control module hatches, each folded over its ten
  IV..MAX tiers with exact `<gregtech:gt.blockmachines:meta>` selectors computed from `MTItemList.SPEED_MODULES` /
  `EU_MODULES` / `PARALLEL_MODULES` (so moving `MT_ID` cannot leave stale ids); NAC components fold all 105
  `CircuitComponent` metadata values of `MTNACComponentItem` with one item-wide `r/^<registry name>$/` filter. Groups are
  collapsed by default (`expanded` defaults to false in NEI).
- Only the item panel is affected - recipe pages, catalysts and every real stack keep working. The rewrite is idempotent:
  the same groups produce the same file, so nothing is written after the first launch. Parallel NEI plugins
  (`loadPluginsInParallel`) could in theory race another mod writing the same file; their blocks are regenerated on the
  next launch, the marker design is the same one TST uses.

## GT5U API knowledge

- Upstream `MTEMultiBlockBase#getWailaBody` reads `tag.getString("mode")` - write the mode as a string name, not an integer,
  to avoid `运行模式: 0/1`; `tag.setString("mode", getMachineModeName(...))`.
- `ProcessingLogic#findRecipeMatches` uses `findRecipeQuery`; fake recipes are not found.
- NEI `GTNEIDefaultHandler` reads `getRecipesByCategory(defaultCategory)`. Re-tag copied recipes.
- `MTEHatchDataAccess#getAssemblyLineRecipes()` reads data sticks in the hatch.
- Original Assembly Line is ordered by input bus slots; unordered AL must use `ProcessingLogic` / `getStoredInputs`.
- `MTEHatchInputBusDebug` phantom inventory: `getStackInSlot()` returns copies with huge stack size; the standard
  `getStoredInputs()` / `depleteInput()` handle it.
- `COAL_CASING_TIER` aliased to `GTRecipe.mSpecialValue`.
- `RecipeMaps.scannerFakeRecipes` / `assemblylineVisualRecipes` are visual/NEI pools.
- Wireless helpers: `processInitialSettings`, `getUserEU`, `addEUToGlobalEnergyMap`; safe order = validate balance first,
  then deduct + consume.

## Own-code conventions

1. Reuse GT methods (`depleteInput`, `ProcessingLogic`, `getStoredInputs`, `findRecipeQuery`), never manual inventory/fluid
   code.
2. Waila mode as a string, never an integer (see above).
3. Real recipes for processing, fake ones only for NEI.
4. Re-tag copied recipes to the target map's `RecipeCategory`.
5. One registration path: `postInit -> MTMachineLoader.loadMachines()`.
6. Do not commit randomly: `git fetch origin` was requested, not `git commit`.
7. Borrowed face art keeps the machine's own casing as base layer: `MTNQDAFReactor` = Antimatter Generator fusion overlay on
   Naquadah Fuel Refinery Casing, `MTReactor` = neutron activator art on its existing casing; never import the donor's
   casing. When the *machine itself* changes structure the layer may follow: `MTChemicalTwister` = QFT face on the
   BulkProductionFrame, its level 2 shell's casing.
8. `HatchElementBuilder#casingIndex` > 0 only, and `Casings#getTextureId()` throws for casings declared `-1` (GT++:
   `SpaceTimeContinuumRipper`, `ForceFieldGlass`, `BulkProductionFrame`, `SpaceTimeBendingCore`, `NeutronShieldingCore` ...
   have no GT casing texture page entry). Such a `textureId` sits in the structure definition's static initialiser -> mod
   loading fails with `ExceptionInInitializerError: IllegalArgumentException` (crashed once in `MTChemicalTwister`). Use
   `TAE.getIndexFromPage(page, slot)`: GT++ casings live at `64 + page * 16 + slot` (e.g. `TAE.getIndexFromPage(0, 10)` = 74,
   GT++'s Quantum Force Transformer index for the SpaceTimeContinuumRipper's hatch host). Texture-less casings are fine as
   `.asElement()` or the `buildAndChain` block - only the hatch texture index needs a real one. `textureId == -1` usually
   means **no** registered slot either (`BulkProductionFrame` = `blockCasings2Misc:12`; `GregtechMetaCasingBlocks2` skips
   meta 12, so `getCasingTextureForId(TAE.getIndexFromPage(1, 12))` is null): copy the block with
   `TextureFactory.of(casing.getBlock(), casing.getBlockMeta())`, as the level 2 controller casing does.
9. A lazily created `IIconContainer` is never stitched: `TexturesGtBlock.CustomIcon` adds itself to
   `GregTechAPI.sGTBlockIconload` in its constructor and GT walks that list only in `BlockMachines#registerBlockIcons`
   during the icon load phase, so reading such a field in `getTexture(...)` (first frame) leaves it `null`. Icon containers
   not forced by the donor mod belong in `static final` fields of a class loaded while the mods load (ours are:
   `MTMachineLoader` runs from `postInit`, before the client's first resource reload). `MTAssFactory`'s inline
   `TexturesGtBlock.oMCAQFT*` reads work only because such a field loads the class early - do not copy that.
10. Render-only machine state -> `getUpdateData()`/`onValueUpdate(byte)`, not a custom packet: GT compares the byte every tick
   (`handleUpdateDataChangeServer`), sends `CHANGE_CUSTOM_DATA` on change and writes it into the tile data packet, so it
   survives a chunk reload for free. Values masked with `0x7F`; structure checks never run on the client, so the client has
   to be told.
11. Verify with the build, not harnesses: `gradlew compileJava` is the check; no new `tools/**` verifiers, no extending
    existing ones, no `tools/**` sweep unless asked. `gradlew spotlessApply spotlessCheck checkstyleMain processResources` =
    full build or hand-over. Harnesses stay in `tools/**`, on request only (see `AGENTS.md`).
12. Item **count** never rides in a vanilla `ItemStack` NBT tag: 1.7.10 writes `Count` as a byte, so outside -128..127 it
    changes (1024 and 4096 -> 0). GTNH recipes use those (Nano-Scale Foundry one-step circuit pool: 1024 and 4096 counts), so
    client->server transfers carry the count in their own `long` tag next to the vanilla NBT
    (`PatternImportHandler#writeCell`/`#readCell`, like NotEnoughEnergistics in
    `ItemUtils#writeItemStackToNBT`/`#loadItemStackFromNBT`). AE's NBT unaffected: size in the long `Cnt`, `Count` 0 on
    purpose.
13. Config is `com.MessTech.init.Config`, GTNHLib's annotation config (`com.gtnewhorizon.gtnhlib.config`): the nested
    classes are the categories (`general`, `machines` with the `reactor` subcategory, `voidstarbow`, `gameconsole`) and
    the Java field names **are** the `.cfg` property names, so renaming a field breaks existing configs. Reference
    options as `Config.<category>.<FIELD>` (e.g. `Config.machines.ENABLE_ONE_BATCH_CALCULATOR`). It is registered in
    `CommonProxy#preInit`; the in-game screen is `@Mod(guiFactory = "com.MessTech.client.gui.config.MTGuiFactory")` +
    `MTGuiClientConfig` (GTNHLib `SimpleGuiConfig`). Titles come from `@Config.LangKeyPattern("%mod.config.%cat.%field")`
    and the `messtech.config.<category>.<field>` keys, which **must be localized in both** lang files (categories, every
    field title, and `.tooltip` when a comment override is wanted). The old top-level `[reactor]` category is now
    `[machines.reactor]`, so a pre-migration config's reactor values reset to their defaults once.

## Known pending / open items

- The module system (`MTModuleMultiMachineBase`) has its three standard hatches with TST's values and an `IHatchElement`, but
  no machine uses it yet: the module slot still needs wiring into a real structure, and the composite plus the optional cross
  recipe / wireless modules are open (see its section above).
- Overclock GUI / button sizing may still need visual verification.
- `ParallelismAcrossMultiMachineBase` has a trivial `getMaxParallelRecipes()` fallback; subclasses should override.
- Generic `wirelessModeProcessOnce()` order may differ from strict "validate before consume"; DTPF implements the strict order
  in its own processing logic.

## SpaceModule Infinity machines (latest)

- `SpaceModulePumpInfinity` / `SpaceModuleMinerInfinity` extend `SpaceModuleInfinityBase<T>`, itself extending
  `ParallelismAcrossMultiMachineBase<T>`.
- Always wireless (`EnableWirelessFunc=true`, `EnableWireless=true`), drawing directly from the wireless network.
- GUI parallel selector (`SpaceModuleInfinityGui`): default 1, max Integer.MAX_VALUE, synced via `wirelessParallel`.
- Power: base 1A MAX (`GTValues.V[14]`), each extra parallel +1A UXV (`GTValues.V[13]`).
- `checkProcessing()`: validates wireless balance, deducts cost, sets `lEUt` and 20-tick duration.

### Tick-batched output framework (latest)
- `SpaceModuleInfinityBase` owns a shared tick-batched output framework:
  - `batchRemainingTasks`, `batchItemOutputs`, `batchFluidOutputs`
  - `startBatchedProcessing()`, `generateOneBatchTask()`, `getMainBatchDuration()`
- A batch runs as one continuous main recipe:
  - total duration = main duration + remaining sub-outputs × 1 tick
  - each tick generates one sub-output and accumulates it
  - final `mOutputItems`/`mOutputFluids` are the accumulated total
- Shutdown is deferred until all sub-outputs finish (`shutdownRequestedDuringBatch`).

### Mixin / elevator mounting (latest)
- Late Mixin infra:
  - `com.MessTech.common.mixin.LateMixinPlugin` (implements `ILateMixinLoader`, `@LateMixin`)
  - `com.MessTech.common.mixin.Mixins` (registry with target-mod filter)
  - `com.MessTech.common.mixin.TargetMod` (`gtnhintergalactic`)
  - `src/main/resources/mixins.MessTech.late.json`
- `MixinTileEntitySpaceElevator` adds a separate `mMessTechModuleHatches` list of `ISpaceElevatorModule`:
  - accepts `ISpaceElevatorModule` in `addProjectModuleToMachineList` (without changing the original list)
  - clears/disconnects/charges/connects them like the original modules
  - includes them in `getNumberOfModules` and the module-slot limit check
  - includes active MessTech miners in the `getAvailableDataForModules()` computation split

### Pump port status (latest)
- `SpaceModulePumpInfinity` uses the real `SpacePumpingRecipes.RECIPES`; `getRecipeMap()` returns `null` - the pump has no GT
  RecipeMap (custom map only).
- NBT saves/loads all 4 recipe sub-panels (planet/gas/parallel arrays) and the batch size.
- GUI: `SpaceModulePumpInfinityGui` with separate Parallel / Planet Type / Gas Type buttons/panels.
- Structure: the Space Elevator module slot (2 wide x 2 deep x 5 tall, controller on the second layer of the first row),
  built from `SpaceModuleInfinityBase#buildModuleStructure` so the elevator registers the module through its own structure
  check. Module is 2 wide and 5 long - only the orientation running its long axis along the slot fits.
- Linkage `SpaceModuleInfinityBase#isMountedOnFormedElevator()` = `parent != null && parent.getMotorTier() >=
  getNeededMotorTier()`, where `parent` is set by the elevator's `connect(this)` every 20 ticks; no neighbourhood search any
  more.
- `checkMachine` reports the linkage through `checkElevatorLinkage(errors, "V")`, telling two reasons apart:
  `machine.spacemodule.elevator_not_linked` (no elevator took the module in) and
  `machine.spacemodule.elevator_level_too_low` (with the required `MK-<level>`) when mounted but the motor tier is short.
- `checkProcessing()`: looks up the fluid by `(planetType, gasType)`, validates/deducts wireless EU, outputs
  `fluid * parallel`, 20-tick duration; refuses to run while `isMountedOnFormedElevator()` is false, i.e. without a Tier V
  elevator.
- Textures: original pump module front screen + `OVERLAY_SIDE_PUMP_MODULE` engraving.

### Miner port status (latest)
- `SpaceModuleMinerInfinity` uses the real `IGRecipeMaps.spaceMiningRecipes` pool.
- Structure: the Space Elevator module slot (see the pump section); hatches = one optional casing plus any of Input Hatch /
  Input Bus / Output Bus / Data Input hatch.
- Recipe lookup applies the native miner's two gates: `MODULE_TIER` metadata not above `getModuleTier()`, and the asteroid's
  `minDistance..maxDistance` range covering the effective distance. Constructed with `tModuleTier = 5`, so the tier gate
  passes every asteroid (the pool's `minModuleTier` values only go up to 3).
- Needs a local Data Input hatch for computation input; without a packet it falls back to the parent elevator's shared
  computation (`getAvailableDataForModule()`).
- GUI: full port of the original GT5U Miner GUI - Filter (64-slot whitelist/blacklist + asteroid buttons), Calculator,
  Utility asteroid list, drone selectors, asteroid info panels, plus the wireless Parallel button and
  Distance/Overdrive/Cycle/Range/Step controls.
- Textures: original miner module front screen + `OVERLAY_SIDE_MINER_MODULE` engraving.
- `checkProcessing()`:
  - requires a Tier V elevator
  - uses the existing `getStoredInputsNoSeparation()` / `getStoredFluidsWithDualInput()` for inputs
  - filters/randomly picks a real space mining recipe by effective distance
  - supports distance cycling (`cycle/range/step`) and overdrive time/bonus modifiers
  - applies Asteroid Outpost computation/plasma discounts when the team project is finished
  - caps parallels by wireless parallel, local/parent computation, plasma and input stack sizes
  - validates/deducts wireless EU, consumes inputs via `recipe.consumeInput` + `depleteInput` for plasma, generates weighted
    ore outputs and applies whitelist/blacklist filtering
- NBT saves/loads distance, overdrive, cycle/range/step, cycleDistance, the whitelist flag and the filter inventory.
- Remaining vs the original: full asteroid info/calculator panels and the 64-slot filter grid (GUI has a compact 8-slot
  filter).

### Space apiary modules (太空蜂箱, latest)
- `SpaceModuleApiary` + inner `MK1`..`MK4`, registered as `MT_ID + 73..76` (`MTItemList.SpaceModuleApiaryMK1..4`).
- Four tiers, one class each, re-seated on this GT5U's 16-entry `GTValues.V` (TST asks for tiers 18/25, which do not exist
  here): MK-I UEV (tier 10) 256 parallel / 16 slots / motor tier 1; MK-II UIV (12) 4096 / 32 / 2; MK-III MAX (14) 32768 / 64
  / 3; MK-IV tier 15 `Integer.MAX_VALUE` / 128 / 4.
- Structure: the Space Elevator module slot (see the pump section), hatches = one optional casing plus input bus / output bus
  / input hatch. `checkMachine` reports the linkage through `checkElevatorLinkage(errors, getNeedElevatorLevel())` (level
  `I`..`IV` per MK); mounted only while the elevator's motor tier is at least `getNeededMotorTier()`.
- `checkProcessing_EM()` runs one 100-tick cycle (`CYCLE_TICKS`): `takeBeesFromInputs()` pulls queens from the input bus into
  the free bee slots and they are **never consumed**; every occupied slot runs once through
  `MTBeeSimulator.simulate(queen, world, t)` with `t = MTBeeSimulator.voltageTierExact(getTier())`, drops multiplied by
  `getMaxParallelRecipes()` (wireless parallel capped by the module's own ceiling).
- Power is wireless only: `GTValues.V[tier] * CYCLE_TICKS * parallel` EU taken from the player's global energy map in one
  lump via `addEUToGlobalEnergyMap`, then `lEUt = 0` so the machine does not also drain its hatches.
  `validateWirelessPowerForRecipe` first; no energy hatch, no liquid DNA.
- No recipe map: `getRecipeMapImpl()` returns `null` (like TST's `TST_SpaceApiary`); the module produces from the bee slots,
  not from a pool.
- Bee slots live in the machine (`ItemStack[] beeSlots`, NBT key `beeSlots`, size 1 each) so they survive a reload.
- Structure hooks: `construct(...)` -> `buildPiece("main", stackSize, hintsOnly, 0, 1, 0)` **and**
  `survivalConstruct(stackSize, elementBudget, ISurvivalBuildEnvironment)` ->
  `survivalBuildPiece("main", stackSize, 0, 1, 0, elementBudget, env, false, true)` (the miner/pump pair). The
  `ISurvivalBuildEnvironment` variant must build for real: BlockRenderer6343 (NEI structure preview) calls it with a **fake
  player**, and neither `TileEntityModuleBase` nor GT's multiblock bases override it - `super` lands in StructureLib's
  interface default, which answers `-2` ("not supported"), leaving the preview with only the controller.
- Recipes: four Space Assembler recipes (`GTRecipes.addSpaceApiaryRecipes()`), one per tier, `specialValue(1)` (inert: the
  assembler's tier gate reads the `MODULE_TIER` metadata, which neither mod sets, so all four count as module tier 1). Each
  spends 4 stacks of 64 Industrial Apiaries + 4 stacks of 64 upgraded acceleration upgrades, 16 of that tier's Field
  Generator / Conveyor / Robot Arm / Electric Pump, 64 tier circuits, a solder fluid and Honey (doubling per tier), at
  RECIPE_UHV/UEV/UIV/UMV and 20*300*scale ticks.
  - MK-III/MK-IV pay **UU-Matter** (1000*128*scale), MK-I/MK-II **Mutated Living Solder** (16 ingots * scale, i.e. 2304 /
    4608 mB). TST fills the same `IGRecipeMaps.spaceAssemblerRecipes` pool with `SpaceApiaryT1..T4`, and MK-I/MK-II once
    spent exactly what TST's T1/T2 spend (same parts, circuit, UU-Matter, honey, EU/t, duration, only the output different),
    so one assembler matched both and took whichever came first - one mod's module unobtainable. MK-III/MK-IV never collided
    (our circuit UXV/MAX, TST's UMV/UXV). The solder is what makes MK-I/MK-II exclusive with TST's - **adding** an item or
    fluid would not, a recipe matching as soon as its inputs are present. 16 ingots doubling follows the pool's ladder (Pump
    Module MK-I 9 ingots, MK-II 32, MK-III 1 stack, `MachineRecipes:279/297/332`) and is a fraction of one GT++ solder batch
    (`RecipeLoaderGenericChem:163` yields 4 stacks + 24 ingots).
- GUI: `SpaceModuleApiaryGui extends SpaceModuleInfinityGui`. The terminal area toggles between the standard status text and
  a scrollable grid of `SlotLikeButtonWidget`s (10 per row), toggled by a button right of the panel gap.
  - The grid shows bee slots like the Mega Industrial Apiary: queens sharing species, secondary species and speed allele
    collapse into **one** button carrying their number (`MTBeeSimulator.speciesKey`, `GuiDraw.drawStandardSlotAmountText`),
    remaining capacity = the single empty button at the end. Missing entries are disabled; a disabled button collapses in
    its row and an empty row in the `ListWidget`, so no rebuild is needed.
  - One parallel knob, one bee cycle: `shouldShowCrossRecipeParallelField()` is `false`, so the cross-recipe parallel field
    is hidden.
  - `createButtonColumn` is overridden to drop the controller slot the shared space-module GUI adds: it takes anything,
    nothing reads it, and it was the only shift-click target - a shift-clicked queen landed there, not in a bee slot.
  - Shift-click goes to an invisible one-item **queen buffer** slot (`ItemSlotSH` over a 1-slot `ItemStackHandler`,
    `singletonSlotGroup(SlotGroup.STORAGE_SLOT_PRIO)`, filter = queen and a free bee slot). MUI2 finds transfer targets
    through the slot group, so no widget; its change listener moves what it receives into the first free bee slot.
  - Slot contents: one `GenericListSyncHandler<ItemStack>` over the machine's array (null-safe item serialization, change
    listener invalidating the aggregation); every edit travels as one encoded integer (`apiaryBeeClick`, `allowC2S`) handled
    on the server - `((slot + 2) << 4) | button | shift`, `slot = -1` meaning the empty entry (server resolves it to its
    first free slot). Click = queen to cursor, shift-click = to player inventory, held queen swaps, right-click = single
    queen (filling continues into the following empty slots), middle-click = creative pick.
- Liquid DNA intentionally not used, unlike TST's optional `SpaceApiaryCycleTime` addition.
- Forestry is a hard dependency (`implementation(gtnhDev("ForestryMC"))` in `dependencies.gradle`); simulations go through
  `forestry.api.apiculture.BeeManager`.

## Mess Food (混乱大杂烩)

- Item: `com.MessTech.common.item.ItemMessFood`, registered as `MessFood` in `MTItems`, texture
  `assets/messtech/textures/items/food.png`.
- NBT key `Ingredients` stores the ordered ingredient list (one `ItemStack` with stack size 1 per entry), also the food's
  identity.
- Recipe: `com.MessTech.common.recipe.RecipeMessFood`, a custom `IRecipe` appended to the crafting manager in
  `CommonProxy.init()`. Shapeless but order sensitive: any 2-9 AppleCore foods anywhere in the grid, recorded in crafting
  slot order (top-left -> bottom-right). Every ingredient distinct (item + damage + NBT), so apple+apple is rejected; Mess
  Food itself excluded, so it cannot craft another. Appending keeps every pre-existing recipe's priority.
- Hunger = sum of the ingredients' AppleCore hunger values; saturation = sum of their saturation increments, converted back
  into a `FoodValues` modifier, via AppleCore `IEdible#getFoodValues` plus `func_150905_g`/`func_150906_h` overrides.
- Display name: localized base name plus `(ingredient1,ingredient2,...)`, e.g. `Mess Food(apple,bread)` /
  `混乱大杂烩(苹果,面包)`. Key `item.messtech.messFood.display=%s(%s)`, so Spice of Life's journal tells every variant apart.
- Tooltip: ingredient names one per line, in crafting order.
- Spice of Life compat (late mixins, only when `SpiceOfLife` is loaded):
  - `MixinFoodEaten` adds the NBT tag to `FoodEaten.equals`/`hashCode` for Mess Foods only.
  - `MixinFoodHistory` redirects `ItemStack.isItemEqual` to `MessFoodHelper.sameFoodIdentity` in `getFoodCountForFoodGroup`,
    `containsFoodOrItsFoodGroups` and `getTotalFoodValuesForFoodGroup`.
  - Result: every ordered combination gets its own journal entry, diminishing-returns counter and extra-heart milestone.
- `AppleCore` is a required mod dependency (`required-after:AppleCore`).
- Dev smoke test (temporary, already removed): NBT round trip, order sensitivity, hunger/saturation sums, 2-9 food recipe
  matching, identity/hash consistency.

## A Piggy + MTDynamicItemHelper (dynamic items)

- `com.MessTech.common.items.MTItemPiggy` (`MTItems`, tab `tabMisc`): 7 damage variants, icons `pigs/pig` under
  `textures/items`.
- `com.MessTech.common.util.MTDynamicItemHelper`: one item, look (icon + renderer) from `ItemStack#getItemDamage()`, so
  no renderer class per item.
  - `Effect` enum, ordinal == damage value: `NONE` (plain pig), `TRANSCENDENT_METAL`, `INFINITY`, `MAGMATTER`,
    `ETERNITY`, `UNIVERSIUM`, `SIX_PHASED_COPPER`; `NONE` = index 0, fallback for out-of-range damage (old worlds,
    `/give` with a random meta).
  - `registerIcons(register, basePath)` -> `<basePath><iconSuffix>` per effect (`pigs/pig` + `TranscendentMetal` =
    `textures/items/pigs/pigTranscendentMetal.png`).
  - **No `items/` in icon names**: `TextureMap` resolves `basePath/<name>.png`, `basePath` = `textures/items` (the
    `textures/items-atlas` log line), so `items/pigs/pig` = `textures/items/items/pigs/pig.png`; same for
    `textures/blocks`, cf. `MTFuelRod` (`messtech:FuelRod/...`). Wrong path: silent - 1.7.10 commented out the "using
    missing texture" error (`TextureMap` line 189), only FML's `trackMissingTexture` is fed (checker board, empty log).
    `registerIcons` therefore checks icons via `IResourceManager.getResource` (no `resourceExists` in 1.7.10) and logs.
  - `registerItemRenderer(item)`: one shared `IItemRenderer`; `getEffect`/`setEffect`/`cycleEffect` server safe, icon +
    renderer code `@SideOnly(Side.CLIENT)`.
  - `Style` = drawing code, a copy of the matching GT5U renderer:
    - `TUMBLE` (`TranscendentMetalRenderer`): tumbled about (0.3, 0.5, 0.2), 3.5 degrees per client tick, angle from
      `GTMod.clientProxy().getAnimationRenderTicks()`.
    - `HALO_PULSE` (`InfinityRenderer`): inventory only, as GT5U - `Textures.ItemIcons.HALO` behind the icon plus a
      gaussian 60% alpha copy; Infinity/Eternity/MagMatter use it.
    - `UNIVERSIUM` (`UniversiumRenderer`): icon, then gtnhlib's `UniversiumShader` star field
      (`setRenderInInventory()` in the GUI, `GL_EQUAL` depth + `GL20.glUseProgram` restore outside); it writes its own
      colour where the icon is opaque, keeping only its alpha
      (`gl_FragColor = vec4(col * shade, mask.a * opacity)` in `universium.frag`) - a window into the sky, *everything*
      the sprite draws behind the stars.
    - `GLITCH` (`GlitchEffectRenderer`): red/cyan ghosts, 10 ms frames, glitching on frames 0-40 of every 200.
  - `registerUniversiumOverlay(item, register, basePath)`: registers `<basePath>UniversiumFace`
    (`pigs/pigUniversiumFace.png`), remembers it per item, `UNIVERSIUM` draws it third (after
    `UniversiumShader.clear()`/`unbind()`) in front of the sky. *Untinted* art only (sky is dark); no overlay =
    unchanged; client only at both ends, hence the client-only inner class.
  - Overlay = **features only**: eyes, snout + nostrils, own ears, pixel for pixel from the plain sprite, rest
    transparent so the stars paint the body (a skin patch reads as a sticker). Ears (pink crescent 3..5, 7..12; pink
    ring 14..18, 11..15) too: the shader keeps only alpha, they would be swallowed.
  - Eyes: the sky's `bgColor = (0.10, 0.225 +- 0.075, 0.30 +- 0.05)` is as bright as the near black eye (luma 0.17), so
    a dark dot vanishes; 2x2 pupils kept, a pale rim (`#cfe4f2` at alpha 190) around each, readable over star or
    background through the pulse.
  - GT5U accessibility switches honoured: `Client.render.renderTransMetalFancy`, `renderInfinityFancy`,
    `renderUniversiumFancy`, `renderGlitchFancy` -> plain icon.
- Shift + right click cycles: damage value both sides, oink server side (heard once), chat line client side.
- Right click throws without consuming - hence **one** per stack (pet and hat, not ammunition); projectile
  `MTEntityPiggy`, effect in the thrown stack's damage value.
- **Helmet slot**: `MTItemPiggy#isValidArmor` true for armor type `0`, the hook `ContainerPlayer.SlotArmor#isItemValid`
  asks (`Item#isValidArmor`: 0 helmet, 1 chest, 2 legs, 3 boots - *not* `InventoryPlayer#armorItemInSlot`, helmet = 3);
  without it only armour, pumpkins and skulls fit.
- Worn: `common/entity/MTPiggyHatRenderer` (client only, `ClientProxy.preInit`). 1.7.10 has no plain head-item renderer
  (`RenderPlayer#renderEquippedItems`: `ItemBlock` blocks and skulls only), so it hangs on
  `RenderPlayerEvent.Specials.Post` (end of that method, **inside the player model**): `inventory.armorItemInSlot(3)` ->
  `event.renderer.modelBipedMain.bipedHead.postRender(0.0625F)` (head pose included), 8 px up (half a block, head box
  height), `SCALE / 0.9375` -> `SCALE = 0.5` blocks tall. Crouch, riding, death pose, the `0.125` sneak drop: free;
  sleeping/dead/invisible skipped.
- Space facts: `RendererLivingEntity#doRender` flips the model (`glScalef(-1, -1, 1)`) first: **`-Y` up, face along
  `-Z`, one unit = a sixteenth of the model** (`postRender`'s `0.0625`); `RenderPlayer#preRenderCallback` scales
  `0.9375`, divided back out (`SCALE` in blocks); `bipedHead` turns about the **neck**, head box growing 8 px up. With
  the half block of `HEAD_HEIGHT` the feet land where pumpkin and skull are built around,
  `(24 + 8) / 16 * 0.9375 + 0.9375 / 128 = 1.8823` blocks above the feet (one pixel lower crouching).
- `MTDynamicItemHelper#renderOnHead(wearer, stack)`: `Item#getIcon(stack, 0)` on the origin of the hat matrix, facing
  `-Z` (player in front sees the face); mirrored X and the turn into the facing direction cancel, so the texture is not
  mirrored. Effect style on top:
  - `TUMBLE`: 3.5 degrees per client tick from `getAnimationRenderTicks()` about `(0.3, -0.5, -0.2)` - the mirrored copy
    of GT5U's `(0.3, 0.5, 0.2)`, model space having Y and Z flipped - about the sprite centre.
  - `UNIVERSIUM` in three passes: sprite, cosmic shader over the same geometry at depth `GL_EQUAL` (stars on the sprite,
    not the head), then `pigUniversiumFace` in front of the sky.
  - `HALO_PULSE`/`GLITCH` stay plain (GT5U draws those in the inventory only; Infinity/MagMatter/Eternity strips animate
    themselves); fancy switches apply (`isFancyEnabled`).
  - Helper binds the items atlas, culling off for the single quad, `GL_ENABLE_BIT` pushed/popped.
- **A Transcendent Metal pig tumbles the wearer.** `MTPiggyHatRenderer` also watches `RenderPlayerEvent.Pre`/`Post`
  (wrapping `RenderPlayer#doRender`), turning the model by the same `MTDynamicItemHelper#tumbleAngle()` about
  `(0.3, 0.5, 0.2)`. Pivot = body middle: at `Pre` the matrix is the camera's, so the origin is the interpolated entity
  position (`lastTickPos + (pos - lastTickPos) * partialTicks` minus `RenderManager.renderPosX/Y/Z`, minus
  `Entity.yOffset` as `RenderPlayer#doRender` drops it), lifted by `TUMBLE_PIVOT_Y = 0.9` (half of 1.8 blocks).
  - Push/pop are a pair: `Pre` is `@Cancelable`, `Post` only posted for an uncancelled `Pre`; `Pre` at
    `EventPriority.LOWEST` (all handlers done, a cancel visible), `onRenderWorldLast` pops a leftover push at the end of
    the world render - no leak.
  - The pig keeps its own tumble, so the two compose; pinning it to the head would mean dropping the transform in
    `MTDynamicItemHelper#renderOnHead`. Client-side switch `Config.general.PIGGY_TUMBLES_WEARER` (on).
- Textures `assets/messtech/textures/items/pigs/pig*.png` from the GT5U material icons
  (`assets/gregtech/textures/items/materialicons/...`): in-painted to full coverage, tinted with the material colour,
  multiplied by the pig's shading plus a fake top/bottom bevel.
  - Frames: `pigInfinity` 18, `pigMagMatter` 8, `pigEternity` 20, `.mcmeta` `{"animation":{"frametime":1}}`; `pig`,
    `pigTranscendentMetal`, `pigUniversium`, `pigSixPhasedCopper` single (animated atlas sprites work in 1.7.10).
  - Pattern normalised over the whole strip, never per frame: per frame cancels motion (it flattened MagMatter, grey
    pattern keeping its mean brightness); MagMatter `pattern_weight = 1.0`.
  - MagMatter (look = grain): `pattern_tile = 2` walks the 16x16 icon across the 32x32 pig 1:1 and mirrored (second copy
    backwards, no seam) - speckle density up (mean neighbour difference 12.4 -> 22.3, "edges" 27% -> 47%), face legible,
    about 5% darker; others keep tile 1 with smoothing, PNGs byte identical.
  - `pig.png` from the hero render `tmp/pig_hero.png` (eyes 2x2, nostrils 1x2, symmetric); `pigUniversiumFace.png` is
    the plain pig's face (eyes, snout, nostrils) in a feathered superellipse the shader cannot paint over, so the pig is
    not a hole into space: eyes upper right, snout lower left, 2 px smoothstep fade, full alpha there
    (`tmp/piggen/universium_face_preview.png`; GPU needed for the real shader).
- Other items reuse the look via `registerIcons` + `registerItemRenderer` plus `item.<unlocalized>.<effect>.name` and
  `messtech.itemEffect.<effect>` lang entries.
- Renderers are copies, so GT5U's GL state leaks are kept on purpose (halo layer leaves depth/alpha test disabled for the
  item pass, like `InfinityRenderer`).

### The thrown piggy

- `com.MessTech.common.entity.MTEntityPiggy`, a plain `EntityThrowable`: `CommonProxy.preInit` registers it
  (`MTEntityPiggy.register()`, entity id 0, tracking 64 / update 10, velocity updates on), `ClientProxy.preInit` gives it
  `com.MessTech.common.entity.MTRenderPiggy`; `EntityRegistry.registerModEntity` needs the mod object, hence
  `MessTech.instance`.
- Plain right click throws one (one item consumed, not creative), shift + right click cycles the look; effect (damage
  value) in data watcher id 20 and NBT (`Effect`), so the client projectile draws the held icon.
- Impact vanilla style: server `setEntityState(this, 3)`, every client spawns heart puff and oink; the renderer draws the
  effect icon as a billboard (`TextureMap.locationItemsTexture`, so animated effects animate), scaled to 0.6, spinning 24
  degrees per tick around the view axis.
- `MTKillBase` (`common/util`): the shared "true kill" ladder (every protection gives up elsewhere), one branch per weapon
  (`MTPiggyKill` piggy, `MTBowKill` Voidstar Piercer). A branch says only how its kills read (death message key, "could
  not kill" line, into the base constructor) and whom it refuses (`accepts`, bow only); the ladder is branch-less,
  `kill()` `final`, each branch a singleton (`MTPiggyKill.INSTANCE.kill(...)`).
  `MessTech.MT_LOG.debug("[Piggy] true kill of ... needed step N")` names the step and branch ("Piggy", "Bow").
  1. `attackEntityFrom` with exactly `Float.MAX_VALUE`, type `"infinity"`, `setDamageIsAbsolute()`, **neither** armour
     piercing **nor** creative mode: set based immunity skips every other type, and shielded armour sets escalate on
     exactly that amount through their internal `ADMIN_KILL` source (their death handler skips it) - how those mods keep
     `/kill`. Plain matters: the bypass/creative flags make their adjuster double it instead, and *indirect* keeps an
     Infinity Sword in the thrower's hand from fixing it at 300 - cost: the piggy's credit, no loot. Do not "fix" the
     `EntityDamageSourceIndirect` argument order.
  2. Same with `setDamageBypassesArmor().setDamageAllowedInCreativeMode()`: creative mode
     (`EntityPlayer.attackEntityFrom` needs `canHarmInCreative()`, MC source line 1110) and source filters.
  3. Same plus `setFireDamage()`, `FIRE_ATTEMPTS` (6) times, each as a hit and, if refused, a forced death. Some gates
     open for one kind of hit: Witchery's vampire (`CreatureUtil.checkForVampireDeath`) dies to fire, sunlight, the void,
     a wall, refuses every sword, and `ItemVampireClothes.isFlameProtectionActive` *rolls* (one in four); Avaritia's
     infinity sword lands it the same way (`setHealth(0)` +
     `onDeath(new EntityDamageSource("infinity", player).setFireDamage())`, `ItemSwordInfinity.hitEntity`). Hit first
     keeps the vanilla path (hurt animation, knockback, `recentlyHit`); the forced death reaches a target already at zero
     health, where `attackEntityFrom` returns at once.
  4. `setHealth(0)` + `onDeath(source)` by hand, no `attackEntityFrom`: entity level invulnerability, damage caps and
     custom overrides cannot intercept it; same call GT5U uses in `EIGSeedBucket`.
  5. Alive players: everything carried is taken (armour + main inventory + optional bauble inventory, looked up
     reflectively) and the player hit again - set based immunity gone (set incomplete), item based "second life" gone.
     Slots cleared directly, stacks spawned as `EntityItem`s with a 40 tick pickup delay (a vanilla toss can be handed
     back by the item's own handler); through the bauble inventory its "on unequipped" hook also runs.
  6. Players: three more death events, counting down single use protection.
  7. Everything else: `setDead()` + `World#removeEntity` after resolving `EntityDragonPart#entityDragonObj` - the boss,
     not the part.
  - **The thrower is not exempt** (`target == thrower` used to return early): a piggy thrown straight up that lands on
    you kills you - the point of the item. Vanilla `EntityThrowable` shields the thrower five ticks in air
    (`entity1 != getThrower() || ticksInAir >= 5`, MC `EntityThrowable.onUpdate` line 196), so one at your feet is safe.
  - **A death counts only when the death path ran.** `MTKillBase.init()` (from `CommonProxy.preInit`) subscribes to
    `LivingDeathEvent` at `EventPriority.LOWEST` with `receiveCanceled = true` (final cancel state) and records only
    uncancelled deaths; a canceled post *removes* the record. Player death posts several times (`EntityPlayerMP.onDeath`
    -> `EntityPlayer.onDeath` -> `EntityLivingBase.onDeath`, innermost decides) and a per-post roll (vampire flame
    protection) would leave a "half dead" record - so record, not health: `EntityLivingBase.onDeath` can be refused
    (Witchery, unless `CreatureUtil.checkForVampireDeath` agrees) and a gate can leave a walking entity at zero health
    (`EntityVampire` restores `1.0F` and returns), which made step 1 "succeed" on a walking vampire. Trusted only while
    health agrees, so a respawned player or revived mob is a target again and nothing is killed twice.
  - **NaN repaired after every step.** `Float.MAX_VALUE` overflows to infinity in
    `EntityLivingBase.applyArmorCalculations` (MC source line 1192: `damage * (25 - armour)`), and of the two absorption
    values of `damageEntity` (lines 1266-1274) the first is `absorption - (infinity - infinity)` = NaN; `Math.max(NaN, 0)`
    is NaN, so the next hit makes `health - NaN` a NaN health which `MathHelper.clamp_float` passes through - then
    `health <= 0` is false forever, no death check fires, the bar just looks empty. `MTKillBase.repair` drops NaN (or
    infinite) absorption and NaN health to zero after every hit, every forced death and before removal (the reported
    "0 health, `HealF` NaN"). If every step fails: `messtech.piggy.kill.failed` in chat.
  - **The piggy announces the kill.** The damage *type* cannot change (step 1: only `infinity` passes the armour sets)
    and `DamageSource#func_151519_b` builds `death.attack.<type>`, so the piggy would read as `death.attack.infinity` -
    the key Avaritia's `DamageSourceInfinitySword` uses - and translating it would relabel every infinity sword kill.
    The *message* is replaced instead: the nested `TrueKillDamageSource` of `MTKillBase` overrides `func_151519_b` with
    `death.attack.messtech.piggy`. Victim `%1$s`, piggy `%2$s`; shipped `%1$s被猪猪创飞了` (zh_CN) and
    `%1$s was knocked flying by a Piggy` (en_US). A `ChatComponentTranslation` is translated on the *clients*
    (`EntityPlayerMP.onDeath` -> `ServerConfigurationManager#sendChatMsg`), so a client without the key reads the raw
    key - hence both `.lang` files.
  - **A forced death leaves the piggy the cause.** `forceDeath` runs `setHealth(0)` + `onDeath` without
    `EntityLivingBase.damageEntity`, which fills the combat tracker (`CombatTracker#func_151521_b` uses the *last* hit),
    so it writes the piggy in by hand (`func_94547_a`) first; otherwise targets that only die there are announced with
    somebody else's hit or `death.attack.generic`.
  - `tmp/mtkill` harness: the shipped ladder against mirrors of `build/rfg/minecraft-src` and the Witchery halves
    (`EntityVampire.onDeath` + `GenericEvents.onLivingDeath`), asserting those bugs and the old steps, chat side too
    (`ChatComponentTranslation`, `StatCollector`, `CombatTracker`). Stale and not ported: still names `MTTrueKill` and
    calls the ladder statically (`MTPiggyKill.INSTANCE.kill(...)` / `MTBowKill.INSTANCE.kill(...)`), `tools/**` on
    request. Command:
    `javac -d tmp/mtkill/out -sourcepath tmp/mtkill/stubs src/main/java/com/MessTech/common/util/MTTrueKill.java tmp/mtkill/*.java`
    then `java -cp tmp/mtkill/out MTKillHarness`.

## The "PigTech" text animation (`MTPigTechText` / `MTPigTech`)

- `MTPigTechText` (`common/util`) renders the animation of a string, `MTPigTech` puts it on an item tooltip;
  `ClientProxy.preInit` calls `MTPigTech.pigRegisterOn(new ItemStack(MTItems.piggy, 1, OreDictionary.WILDCARD_VALUE))`,
  the wildcard damage covering all seven variants.
- Reads like MessTech's: static "Add by:" prefix (`messTech.addBy`, the lang key `AuthorDynamic` uses) then the animated
  name. `MTPigTechText.frame(prefix, text, millis)` copies the prefix into every frame verbatim (byte for byte
  identical, never animates) and puts `PREFIX_SEPARATOR` (one space) in front of the animated band; the word comes from
  `messtech.pigTech`, so the translation changes what is animated. `pigRegisterOn` is the "PigRegisterOn" entry point and
  adds **only** that line - unlike `AuthorDynamic.registerOn`, no author line.
- Mechanism: gtnhlib's `AnimatedTooltipHandler` keeps one `Supplier<String>` per `ItemStack`, re-evaluated once per
  frame; the animation *is* the returned string - no renderer, no per frame state.
- A tooltip line, and why it looks like this:
  - Glyph by glyph at fixed height: no vertical motion, no glyph scaling. **Spaces are the only size control** - letter
    gaps open (stretch) and close (squash), the word travels sideways one space (charge).
  - Same glyphs and space count in every frame, so the width never changes: box cannot pump, word cannot leave its frame.
    Band `letters - 1 + 2` spaces wide, `MIN_MARGIN` (one space) each side in *every* frame, so the gap after the prefix
    stays over two spaces (~8 px); the empty room right of the word is that reservation, which stretch and charge fill.
  - `§l` (impacts) and `§o` (leaning into the charge) do not change the advance; a **colour code clears bold and
    italic**, so style codes come after each letter's colour code.
  - **Single line**: no ground bar, no second tooltip line. A jump needs a vertical axis, so take off and landing are
    told by squash and stretch, the ear twitch and the impact flash - ears `^` (top font row), snout puffs `.` (bottom
    row).
  - ASCII glyphs only: `ascii.png` is CP437, so `°` would render as a shade block, while `^ .` look the same in both
    built-in fonts.
  - Anything that must appear and disappear keeps its slot and is painted `§0` (invisible on the dark background,
    constant width).
- Timeline (2 s, seamless): wind up 0.00-0.40 (two side swings, an impatient shiver, then loaded), charge and impact
  0.40-0.70 (lunge and stretch, the two snout puffs, then jam shut with a white flash on the wall), recoil and jump
  0.70-1.10 (spring back, ear twitches), landing 1.10-1.40 (squash, two bounces, white flash), settle 1.40-2.00 (one slow
  breath). Last frame is exactly frame 0, so the loop never jumps; every beat is a `curve(...)` or `pulse(...)` keyframe,
  so timing is retuned in one place.
- Colour band steps every 100 ms through a four entry pink/white/gold/white palette; 2000 / 100 = 20 steps, which the
  palette divides, so colours line up when the loop restarts.
- `MTPigTechText` deliberately has no Minecraft imports: `tmp/pigtext/PigTechFrames.java` compiles it with `javac` and
  checks single line, static prefix, loop, constant layout, reserved margins, travel limit and every phase against the
  real strings (22866 checks, no game needed).

### The "ModuleProject" tooltip animation (`MTModuleProjectText` / `MTModuleProjectTextRenderer`)

- The modular machine look: the word is a rack of modules of `MODULE_SIZE` (3) characters ("Mod|ula|rPr|ojc|et"). One
  1.6 s loop powers it up - the scan walks the rack module by module (`POWER_UP_MS` 900 ms), the rack flashes white twice
  at full load (`FLASH_MS` 250 ms), is held (`HOLD_MS` 150 ms), then goes dark left to right (`POWER_DOWN_MS` 300 ms).
  Dark module `§8§l`, loaded `§3§l` with a data flicker up to `§b§l`, module under the scan `§b§l`, flash `§f§l`; bold
  and colour codes never move the advance, so the width is constant.
- Nothing is stored: `lit(millis)` (fraction powered), `head(millis, modules)` (module being loaded, -1 while parked) and
  `flashing(millis)` are pure functions of the clock, so the renderer computes the exact phase of the frame the tooltip
  was drawn with - chips and letters cannot drift apart.
- `MTModuleProjectTextRenderer` (client only, registered in `MTAnimatedTooltipHandler#init`) draws the hardware around the
  letters, it does not replace them (`replacesText()` stays false): a chip row two pixels below the glyphs, one chip per
  module, lit with the letters and a brighter packet on the loading module; a scan line with a trailing tail; a bracket at
  either end of the word. `Gui#drawRect` is protected, so the rects go through a small `Gui` subclass, the trick
  `MTAnimatedTooltipHandler`'s box uses.
- Only the word is decorated: the line is `添加模组: <word>`, so the renderer measures the translated `messTech.addBy`
  plus `MTPigTechText.PREFIX_SEPARATOR` and starts its chips at the first glyph of the word; a line without the prefix is
  decorated whole.
- **Every width comes from the font, never a hand sum of `getCharWidth`.** A bold character advances one pixel further
  than its glyph width (`FontRenderer#renderStringAtPos` adds the bold copy, `++f`; `#getStringWidth` the same with
  `if (flag && k > 0) ++i`) and the animation draws *every* character bold, so a hand summed word is one pixel per
  character short - on a 13 character word the right bracket landed two glyphs inside it, at the `e` of `Project`.
  `moduleEdges` therefore measures drawn substrings (formatting codes included, which makes the font count the bold
  advance) and `rawIndex` maps a visible index onto the drawn line, so chips and brackets share the glyphs' geometry.
- Brand line of `MTHugeChemicalReactor`, registered with `AuthorDynamic.registerAddon(MODULE_PROJECT, ...)` (the add-on
  line, no author line); the word comes from `messtech.moduleProject`, so a resource pack can rename it.

## Animated tooltips with a renderer (`MTAnimatedTooltipHandler` / `MTTextAnimation`)

- gtnhlib's `AnimatedTooltipHandler` only adds **strings**: registry `ItemStack -> Supplier<String>`, the animation *is*
  the string. An effect that has to touch the letters - rotate, darken, blend - has nowhere to run, so MessTech has its
  own `MTAnimatedTooltipHandler` (`common/util`): same shape (`addItemTooltip(stack, line)`, once per frame, split on
  `'\n'`), one animation per line, the handler `AuthorDynamic` writes through. `MTPigTech` keeps gtnhlib's handler; both
  add lines in `ItemTooltipEvent`, so they coexist.
- The split: `MTTextAnimation` is the **common** half ("what does the line look like this frame"), so the machine loader
  registers animated lines on the server too; `MTTextRenderer` is the **client** half, the drawing.
  `registerRenderer(animation, renderer)` pairs them client-side, `init()` (from `ClientProxy.preInit`) registers the
  built-in pair and the event handler. An animation without a renderer is pure formatting, which is what `MTPigTechText`
  is.
- **The client half must not live in the static initializer.** `SideTransformer`
  (`cpw.mods.fml.common.asm.transformers.SideTransformer#transform`, field loop 53-65, method loop 66-78) deletes every
  `@SideOnly(Side.CLIENT)` **field and method** on a dedicated server but never `<clinit>` (no annotation): a client-only
  field *with an initializer* keeps its `putstatic` in the initializer of a class common code loads, and the server dies
  on the POSTINITIALIZATION -> AVAILABLE transition with `NoSuchFieldError: RENDERERS` in
  `MTAnimatedTooltipHandler.<clinit>`. Hence `renderers()` / `background()` build map and box on first use; the nested
  `Background` class would also be loaded by `<clinit>` and rejected by the side check
  (`Attempted to load class ... for invalid side`). Lazy client-only state in client-only methods is safe: the two entry
  points common code uses, `addItemTooltip` and `addAnimatedText`, never touch it.
- Drawing: a stack with a *rendered* animation has its tooltip taken over via gtnhlib's
  `RenderTooltipEvent#alternativeRenderer`; the vanilla box is redrawn (nine `drawGradientRect`s, then
  `font.drawStringWithShadow` per line) and each line's renderer called. `replacesText()` means the renderer draws the line
  itself and the font copy is skipped, so an effect can turn the letters instead of drawing over them. NEI posts the same
  event (`RenderTooltipEventHelper`, NEI 2.7.8-GTNH and newer), so it works in the inventory and NEI panels. A stack
  without a rendered line is left alone, an existing `alternativeRenderer` is never fought over, and NEI's paging for
  taller-than-screen tooltips is not reproduced (vanilla layout).
- Which renderer a line gets follows **visible text**, not index: NEI inserts its second display name at index 1, so
  positions move. `MTTextAnimation.visibleText` strips the `§x` pairs and the entry with the same visible text wins -
  also immune to colour codes a supplier such as `author_czqwq()` brings along.
- `AuthorDynamic.register(animation, author, stack)` is the entry point (arg order: animation, author text, stack),
  `register(animation, stack)` for the default name: it animates the *visible* text of the author supplier with
  `animation.frame(...)` and adds the line plus the usual animated "Add by: MessTech" line. `registerOn(...)` is
  behaviourally unchanged, same handler, no animation.
- `TRANSCENDENT_METAL`, the built-in rendered animation (GT5U's Transcendent Metal look worn by a whole line):
  - `MTTranscendentMetalText.frame` recolours every character with a dark grey / grey / white / grey band advancing one
    character per 90 ms, so the letters read as polished metal before anything moves.
  - `MTTranscendentMetalTextRenderer` **replaces** the line and draws it itself, turned about its own centre by GT5U's
    transform - `glRotatef(angle, 0.3, 0.5, 0.2)`, `angle = getAnimationRenderTicks() * 3.5 % 360` - so the word turns
    like a Transcendent Metal item lying next to it. The turning copy is the only copy (a still line under a turning one
    shows both). The metal is the frame's band with the font's shadow plus the same glyphs one pixel along with additive
    blending - the glint of the turned plate.
  - One way round, continuously: no folding, no bounce, no reset at a quarter turn. The plate is edge-on (invisible) once
    per revolution, exactly like the item, and the line swings with it (a long word tips out of its tooltip row at times,
    as the item's quad leaves its slot).
  - `MTTranscendentMetalText.showsBack` is the flat plate's substitute for an item's back face: while the plate faces away
    the letters are mirrored back (`glScalef(-1, 1, 1)`), so they read the same way on both sides. With the oblique axis
    the z part of the normal is `cos(a) + z^2 / (x^2 + y^2 + z^2) * (1 - cos(a))`, so the switch lands where that is zero
    - the edge-on pose, where the flip cannot be seen.
- Verification: per repo rule `gradlew compileJava`; `tools/textanim` is stale since the version it tests was replaced -
  do not run it as is.
- The bigger version this repo deliberately does not take: render at the **font** level (inline marker, `FontRenderer`
  mixin, glyph masks, GLSL, marker stripped again wherever text is measured, wrapped or typed - chat, text fields, NEI
  search fields, Angelica-style batched font renderers). It shows up outside tooltips too, but needs a mixin into every
  font consumer. This repo keeps the smaller shape: an effect is a registered renderer, animation data common, drawing
  client-only.

## Chaos Matter (混乱物质) material and the stacked-renderer look
- `MaterialBuilder` registers a normal GT material of MessTech's own (no copy of GT's pools), so GT generates the items:
  `gt.metaitem.01` carries `ingotChaosMatter`, dusts, plates, gears and ore dictionary entries - `MetaGeneratedItemX32`
  walks `GregTechAPI.sGeneratedMaterials` and honours the METAL/DUST/GEAR bits the builder sets.
- **Timing is the trick**: GT runs `Materials.init()` in its own preInit (`GTMod.java:343`, before this mod's) and
  `IMaterialHandler#onMaterialsInit` is only called from inside it (`Materials.java:1576`, after
  `MaterialsIDMap#register()` set every `mMetaItemSubID`, before `initMaterialProperties()` freezes the list) - so
  `MTChaosMatter.register()` runs from the `@Mod` constructor.
- **The id, room left for GT**: `MaterialsIDMap` is GT's hardcoded 0..999 table with no free-id API;
  `fillGeneratedMaterialsMap()` throws on a duplicate. `findFreeId()` reads the taken ids
  (`Materials.getMaterialsMap()` -> `mMetaItemSubID`), takes the widest unused run in `100..949` (top = GT's newest
  materials, Infinity/SpaceTime/Halkonites), needs 8 free in a row, and picks its **middle** so GT keeps equal room on
  both sides; run and counts logged.
- **Textures live here**: `TextureSet` hardcodes `Mods.GregTech.resourceDomain`, so `chaosTextureSet()` rewrites the
  (public, element-mutable) `mTextures` with `Textures.ItemIcons/BlockIcons.textureSet("messtech", "chaos", ...)`
  containers. 16x16 icons (grey base, messy multi-colour speckles = every alloy and element at once) in
  `assets/messtech/textures/items/materialicons/chaos/`; a missing file falls back to GT's global `NONE` set, `VOID`
  slots keep GT's void icon. Metal *blocks* use `Textures.BlockIcons.STORAGE_BLOCKS*`: no block art.
- **Several GT renderers at once** (`MTChaosMatterRenderer`, on `Materials.renderer` from the client proxy like
  `Materials.initClient()`), four kinds combined differently:
  - *geometry* - `TranscendentMetalRenderer` only rotates the matrix (0.3/0.5/0.2, 3.5 degrees per client tick), copied
    into `applySpin` (+ a slower second axis) but **disabled** (the `renderItem` call commented out on request).
  - *colour modulation* - `GaiaSpiritRenderer` (hue over 180 ticks) and `RainbowOverlayRenderer` (base colour x hue over
    90 ticks) only set `glColor`; both hues mix into the one icon draw, each on its own random period and phase.
  - *translucent extra passes* - `InfinityRenderer#renderHalo`/`renderPulse`, `CosmicNeutroniumRenderer#renderHalo` and
    `GlitchEffectRenderer#applyRedGlitchEffect`/`applyCyanGlitchEffect`, public statics in inventory space, drawn every
    frame on top: genuinely simultaneous.
  - *whole-icon replacements* - `UniversiumRenderer` (shader over the sprite), `CosmicNeutroniumRenderer` and
    `InfinityRenderer` draw the icon themselves, so two cannot stack; one plays per window over the base, additively (the
    "everything at once" burst) or normally.
  - *playback is random, not a rotation* - `rollWindow(tick)` re-rolls per window: layer (never the one just played),
    length (10..39 ticks), additive burst (35%), CosmicNeutronium halo (50%), where the glitch burst starts (60% chance,
    8 ticks, ghost offset re-rolled every 2 ticks). Hue cycles get a random period and phase per session
    (`gaiaPeriod`/`rainbowPeriod`/`gaiaPhase`/`rainbowPhase`): nothing repeats on a fixed clock.
  The opposite of `MTDynamicItemHelper` (one effect per damage value; stacking renderers not supported).
- Name: lang key `Material.chaosmatter` in both lang files, looked up by `IOreMaterial#getLocalizedName()`
  (`"Material." + name.toLowerCase()`); `mDefaultLocalName` only the fallback.

## Galacticraft hook: 高速上升自动开星系 GUI (`MTGalacticraftSpaceHandler`)
- A **player** crossing a rocket's atmosphere-leave altitude while climbing at **256 格/s 或更高** gets GC's celestial
  selection screen pinned to **tier 0** (Earth only), server side; the screen arrives over GC's
  `C_UPDATE_DIMENSION_LIST` packet.
- **Overworld only**: `player.dimension == ConfigManagerCore.idDimensionOverworld` (GC's field, not a hardcoded 0). On
  the Moon or in orbit nothing happens.
- GC's own site: `EntitySpaceshipBase#onUpdate` (`posY > IExitHeight#getYCoordinateToTeleport()`, or `1200` when the
  dimension does not implement `IExitHeight`) -> `onReachAtmosphere()` -> `EntityTieredRocket#onReachAtmosphere` ->
  `WorldUtil#toCelestialSelection(player, stats, getRocketTier(), MapMode.TRAVEL)`. Only `WorldProviderOrbit` implements
  `IExitHeight` in GC core, so the Overworld's altitude is the 1200 fallback - copied verbatim, so a pack's own provider
  cannot disagree with it.
- **256 格/s can never be a rocket**, hence the player: `EntityTier1Rocket` sets `motionY = -d * cos(...)` with
  `d = min(timeSinceLaunch / 150, 1)` = at most **1 格/tick = 20 格/s**. `y = 1200` at still >= 12.8 格/tick needs **50
  TNT** at the feet from sea level (~798 格/s off the pad, 267 格/s at the crossing); **49 falls short at 247.7 格/s**; 40
  is the smallest pile reaching 1200 at all (`tmp/tnt-launch/reach1200.py`).
- **A living-player feature**: `Explosion` applies damage and knockback together, so the first of the 50 TNT deals 46
  damage and kills; the corpse flies on, but non-`isEntityAlive()` players are skipped. Hence **creative**.
- Tier 0 = Earth only: `WorldUtil#getPossibleDimensionsForSpaceshipTier` always adds the Overworld and filters planets
  through `IGalacticraftWorldProvider#canSpaceshipTierPass` - the Moon already needs tier 1.
- Registered from `CommonProxy#preInit` **only when `Loader.isModLoaded("GalacticraftCore")`** (GC arrives only through
  Galaxy Space, so its classes must not be touched otherwise), on the **FML bus**: `TickEvent.PlayerTickEvent` is FML,
  unlike `MTProcessHandler`'s Forge-bus `WorldEvent`. Trigger is the upward crossing alone (`prevPosY <= exit < posY`,
  `motionY >= 256 / 20`), once per flight, no state. Both fake-crossing paths move `prevPosY` and `posY` *together*
  (`Entity#setPositionAndRotation`, `Entity.java:1259-1261`, is the path of a teleport and of the client's movement packet
  `NetHandlerPlayServer#processPlayer:394`), and that packet is handled outside the entity tick (`MinecraftServer#tick`:
  `worldserver.tick()` at 696 vs `networkTick()` at 730) while `PlayerTickEvent.END` fires inside `EntityPlayer#onUpdate` -
  either order leaves exactly one tick seeing the crossing. `player.motionY` is the server-side impulse the explosion
  applied to the `EntityPlayerMP`.
- Off switch `Config.general.GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI` (category `General`, default `true`).
- **Caveat for non-host players:** `NetHandlerPlayServer#processPlayer` (`build/rfg/minecraft-src`) rejects a reported
  move when `max(|dx|, |motionX|)^2 + max(|dy|, |motionY|)^2 + max(|dz|, |motionZ|)^2 > 100` **and** the player is not
  the single-player host, teleporting to `lastPos`. 12.8 格/tick clears it alone: the integrated-server host flies, a
  dedicated server / LAN guest may be corrected below 1200 (a source reading, not an in-game test). Raiseable - see
  "Vanilla 'moved too quickly' 上限".

## Vanilla "moved too quickly" 上限 (`MixinNetHandlerPlayServer_MovedTooQuickly`)
- **那行判定是 Forge 的构建期源码补丁，不是任何模组**：`forgepatches.zip` 的
  `net/minecraft/network/NetHandlerPlayServer.java.patch` 把 `Math.min` 换成 `Math.max`（注释
  `//BUGFIX: min -> max, grabs the highest distance`），所以 `processPlayer` 的平方距离是"客户端上报位移"与"实体自身
  motion"**逐轴取大者**（见 `build/rfg/minecraft-src/.../NetHandlerPlayServer.java:341-347`，阈值在同方法 347 行）。
  Forge 无开关，本地 528 个 GTNH 模组 jar 也没别的模组改这行。
- 阈值由本 mod 的 late mixin `MixinNetHandlerPlayServer_MovedTooQuickly` 抬高：`Config.general.MOVED_TOO_QUICKLY_THRESHOLD`
  （category `General`，**默认 `2000.0`**），**只升不降**（`configured > original` 才用配置值，设成 100 及以下等于
  关闭）。配置值在 `processPlayer` **运行时**读，不是收集时用 `setApplyIf` 判定，所以与 GTNHMixins 何时收集 late
  mixin 列表无关（本 mod 配置要到 `preInit` 才读）。
- **和 Hodgepodge 共存**：它的 `MixinNetHandlerPlayServer_DisableMovedTooQuickly` 也改同一常量
  （`@ModifyConstant(method = "processPlayer", constant = @Constant(doubleValue = 100.0D, ordinal = 0))`，只在
  `FixesConfig.movedTooQuicklyThreshold > 100.0` 时应用，配置在 `config/hodgepodge.cfg` 的 `[fixes]` 段键
  `D:movedTooQuicklyThreshold`）。同一常量上的两个 `@ModifyConstant` 是**串接**而非冲突（冲突的是
  `@Redirect`/`@Overwrite`，游戏起不来），故最终上限 = max(原版 100、Hodgepodge、本 mod)；本 mixin 用
  `priority = 1500`（Hodgepodge 继承默认 1000）保证后应用。
- 默认值锚点：50 个 TNT 脚下起爆瞬间 |motionY| = 50 x 0.7975 = 39.875 格/tick → 平方 1590.016，是整段爬升里检查看到
  的**最大值**（只出现在起飞那 tick，之后单调衰减，y=1200 时约 13.4 格/tick）。默认 `2000.0` = 44.72 格/tick，比
  1590 多约 26% 冗余（≈56 个 TNT）；`1.7976931348623157E308` 是彻底关掉。判定在**服务端**，生效的是服务端那份配置。

## `/messtech GoUp [秒数]` (`MTCommand` / `MTGoUpFlight`)
- `MTCommand`：根指令 `messtech`，唯一子命令 `GoUp`，参数秒数（默认 30），权限等级 2（OP），发送者必须是玩家（没有
  目标选择器）。注册在 `CommonProxy#serverStarting`（`FMLServerStartingEvent#registerServerCommand`），飞行处理器
  在 `preInit` 里 `MTGoUpFlight.init()`。
- 效果：以**固定速度 14.0 格/tick = 280 格/s** 抛向空中 N 秒（默认 30 s = 600 tick）。不是给一次冲量，而是**每 tick
  重设 `motionY` 并推进位置**，重力和阻力都改不了它。
- 为什么是 14.0：钩子的线是 256 格/s = 12.8 格/tick（`MTGalacticraftSpaceHandler`），14.0 超过去；被原版物理衰减一
  tick（`(14 - 0.08) * 0.98 = 13.64` 格/tick = 272.8 格/s）仍 > 12.8，所以两个 `PlayerTickEvent` 谁先谁后都不影响；
  平方 196 又远低于 `MOVED_TOO_QUICKLY_THRESHOLD` 的默认 2000。到 y=1200 约 82 tick（4.1 s），默认 30 s 约到 8400 格。
- **为什么必须 `setPositionAndUpdate` 而不能只设 `motionY`**：1.7.10 里玩家位置客户端权威
  （`NetHandlerPlayServer#processPlayer` 最后 `setPositionAndRotation(客户端上报位置)`），服务端单方面设 `motionY` 会被
  下一个 `C03PacketPlayer` 顶掉。`EntityPlayerMP#setPositionAndUpdate` → `setPlayerLocation`
  （`NetHandlerPlayServer.java:443-451`）会移动服务端实体、把 `hasMoved` 置 false（本次上报位置不被采用，浮空踢人
  检查也走不到）并回发 `S08PacketPlayerPosLook` 钉住客户端。
- S08 的 Y 特意写成 `y + 1.62`，抵消客户端 `posY → boundingBox.minY` 的 1.62 偏移，客户端下一包上报的 Y 就落在
  `lastPosY` 上（`processPlayer` 的 `hasMoved` 追赶判定要求 `Δy² < 0.01`），于是 `hasMoved` 每 tick 都能重新变
  true —— **移动包照常跑，钩子依赖的 `PlayerTickEvent` 照常触发**，这正是指令存在的目的。
- 处理器挂在 `TickEvent.ServerTickEvent` 的 END（世界与移动包之后，位置以它为准），跳过已死亡、世界为空或已登出的
  玩家，避免 map 里长期持有实体。
- 与配置耦合：平方 196 **高于原版的 100**，指令本身就依赖 `MOVED_TOO_QUICKLY_THRESHOLD` 被抬高（默认 2000 满足）；
  配置更低时额外发一条警告（`command.messtech.goup.threshold.low`），而不是让玩家莫名被拉回去。
- 副作用：30 s 飞完在约 8400 格，掉下来要很久，落地/掉出世界按原版规则处理。

## 混沌终焉·虚无贯星弓 (`MTItemVoidstarBow` / `MTEntityVoidstarArrow`)
- `item.messtech.voidstarBow.name` = `混沌终焉·虚无贯星弓` / `Chaos Finale: Voidstar Piercer`; registry `VoidstarBow`,
  unlocalized `messtech.voidstarBow`, tab `tabCombat`, max stack 1, durability 1,024 (`MTItemVoidstarBow.MAX_DAMAGE`,
  under three stacks of arrows).
- Plain `ItemBow` except **draw length** and **sub-modes**: `f < 0.1` no-fire floor, arrow consumption, the Infinity
  enchantment, Power / Punch / Flame and the enchantment table are vanilla's; projectile, charge effect, icons, glint,
  draw length and sub-modes are this mod's.
- **Sub-modes (`MTItemVoidstarBow.MODE_PIERCE` / `MODE_DE`) via sneak + right click.** Mode in the stack's NBT
  (`MTVoidstarMode`): per-bow, survives a relog, missing or out of range = `MODE_PIERCE`. `onItemRightClick` **must not
  fall through to `super`** (vanilla's `ItemBow` starts the draw there, so it would fire on shift release), returns early
  on both sides, only the server writes. `ChatComponentTranslation` message (not a translated string) so each player reads
  their own language: `messtech.voidstarBow.mode.pierce` / `.mode.de`, tooltip `messtech.voidstarBow.tooltip.mode` +
  `.modeName.*`. A block GUI takes the click first (`ItemInWorldManager#activateBlockOrUseItem` ->
  `onBlockActivated`): switch while aiming at air.
- **DE mode's one explosion**: first block contact calls `MTExplosionDE` (DE's reactor explosion port) on that block with
  `Config.voidstarbow.VOIDSTAR_BOW_DE_POWER` - default **20**, DE's full reactor, {@code 2 + fuelFraction * 18} (2 empty, 20 full),
  a 200 block radius - then removes the arrow. No tunnel: nothing blasted, nothing drilled, the whole difference to pierce
  mode. A shot hitting nothing still detonates when its flight ticks run out (never vanishes after 600 blocks); an entity
  hit ignores the mode and still goes through `MTBowKill`.
- **Draw = `Config.voidstarbow.VOIDSTAR_BOW_DRAW_TICKS` ticks, 80 by default (four seconds)** against vanilla's one.
  `onPlayerStoppedUsing` keeps vanilla's curve (`f = (f*f + 2f)/3`, the `f < 0.1` floor, cap 1) and only divides by the
  configured length instead of `20.0F`, so arrow speed (`1.2 + 1.8 * draw`), the `isCritical` mark and the circle's unfold
  stretch with it; the firing minimum stays the same fifth (~0.7 s). Four seconds is what the charge effect was designed
  around (the Yes Steve Model pack unfolds over four seconds). **One pose does not follow**: vanilla's first-person bow
  pose ramps over twenty ticks in `ItemRenderer` line 466 (`f11 = f10 / 20.0F`), a hardcoded client constant only a coremod
  could change, so the arm stops after 1 s while the charge goes on - the config file and the class comment both say so.
- **Icons** (`assets/messtech/textures/items/bow/`): `voidstarBow_standby` + `_pulling_0/1/2`, each a **16 x 256** PNG =
  16 frames of 16x16, `.mcmeta` `{"animation": {"frametime": 2}}` (32 ticks per loop) - animated item-atlas icons work in
  1.7.10, like the piggy strips (see "A Piggy + MTDynamicItemHelper"). Frame alpha of all four is bit-identical to vanilla's
  `bow_standby`/`bow_pulling_N.png` (64/64 frames): vanilla's size, colours only.
  - Art: vanilla bow in a void-metal ramp (`#2a1140` to `#a462e8`, vanilla's shading), cyan-to-white energy string,
    white-lilac shimmer along the arc once per loop, void-glow pulse of two cycles per loop (no 15 -> 0 jump). Generator
    `tmp/chaosbow/gen_voidstar_textures.py` (deterministic, reads the 1.7.10 client jar); sheet
    `tmp/chaosbow/preview_voidstar.png`.
  - **The pull frames can only come from the Forge hook**: `EntityPlayer#getItemIcon` (line 2051 in
    `build/rfg/minecraft-src`) picks the draw frame only for `itemStackIn.getItem() == Items.bow`, an identity check a
    subclass never passes, else
    `itemStackIn.getItem().getIcon(itemStackIn, pass, this, itemInUse, itemInUseCount)` (line 2070) - Forge's player
    sensitive `Item#getIcon(ItemStack, int, EntityPlayer, ItemStack, int)`, which `MTItemVoidstarBow` overrides with
    vanilla's cut-offs (>= 18 ticks `_pulling_2`, > 13 `_pulling_1`, > 0 `_pulling_0`) kept as *fractions* (90%, 65%) of
    `Config.voidstarbow.VOIDSTAR_BOW_DRAW_TICKS`. It is handed the *player's* `itemInUse`, so others see the pull too:
    `EntityOtherPlayerMP#onUpdate` (lines 86-96) rebuilds it from the "eating" flag `EntityPlayer#setItemInUse` sets server
    side (`EntityPlayer.java:2183`).
- **The glint**: `Item#hasEffect(ItemStack)` returns `true`, so the purple enchantment glint is drawn wherever the bow
  renders (`ItemRenderer.java:132` in hand, `RenderItem.java:561` in the inventory), enchanted or not.
- **The magic circle** (`MTMagicCircleRenderer`, client only, on `MinecraftForge.EVENT_BUS` from `ClientProxy#preInit`):
  world-space textured quads in `RenderWorldLastEvent`, **not** a particle (v1 was rune particles, a dotted circle). Design
  from the Yes Steve Model pack's bow charge effect `wine_fox / 05_magical` (`animations/arm.animation.json` ->
  `use_mainhand:bow`, `ysmGlowdamofazhen` / `ysmGlowmofazhen*` bones of `models/main.json`, sprites `191,191` / `161,50` of
  `textures/magic.png`) - a *model*, not a renderer, so it is rebuilt as world space quads:
  - Two motifs, thin violet line art by `tmp/chaosbow/gen_voidstar_ysm_circles.py` (256x256, 2x supersampled):
    `misc/magicRing.png` big (24 segment ring, inscribed square with corner marks, inner circle), `misc/magicFlower.png`
    small (four pointed compass flower, four short diagonal petals). The pack's pixels are another author's CC BY-NC-SA art:
    same motifs, **not** the same file - do not paste `magic.png` here.
  - Two quads per circle: a 27 unit plane + a 16.2 unit plane (60%) turned 45 degrees about its centre (`INNER_RATIO` /
    `INNER_ANGLE`); that turned inner copy is what reads as a magic circle rather than a decal.
  - Four bones parented to the big one, nine units off its pivot = `1.33` big radii (`CORNER_DISTANCE`), each `0.70` big
    radii across (`CORNER_RADIUS`), carried **around** it by its spin - reproduced by turning the corner positions with the
    big circle's angle.
  - Pack spin rates, degrees per second: big 18 (`BIG_SPIN_PER_SECOND`, keys 0 -> 90 over five seconds), small 180
    (`SMALL_SPIN_PER_SECOND`), alternating direction. Every small circle **bursts** (scale keys 3 and 4, collapse to 0,
    settle: `burstScale()`, `BURST_SCALE` / `BURST_COLLAPSE`) while sliding along the weapon axis (`0,0,38` to `0,0,-24`,
    `AXIAL_FLIGHT`). Corners start at 10%, 17%, 24%, 31% of the draw, settling over the next 35% each; the big circle joins
    at 62% (`BIG_FIRST`) and flies forward as it grows (`BIG_GROWTH`, keys `0,0,-42` to `0,0,13`).
  - The card hangs `1.90` blocks ahead (`DISTANCE`, `0.25` below the eye), perpendicular to `getLook(partialTicks)`, from
    `right = normalize((-fz, 0, fx))` and `up = right x forward` (straight up/down: the world's x axis). Two batches, one
    per sprite, depth mask off, depth test on.
  - Pack blending: plain alpha (`GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`, its `entityTranslucent` render type), not v1's
    additive, culling off. One deviation, `setBrightness(0xF000F0)`: the pack uses world light, a summoned circle should
    glow in a cave.
  - Pure function of the draw (`getMaxItemUseDuration() - getItemInUseCount()` over `Config#VOIDSTAR_BOW_DRAW_TICKS` = 80
    ticks): full draw = one circle centred, four in the diagonal corners, all turning (corners burst at 0.4 / 0.7 / 1.0 /
    1.2 s, settled by 2.6; big circle joins at 2.5, complete at 3.7).
- **The arrow** (`MTEntityVoidstarArrow`, entity id 1, registered in `CommonProxy#preInit`, rendered with vanilla's
  `RenderArrow` from `ClientProxy#preInit`):
  - **Straight flight**: `getLook(1.0F)` direction, zero spread (no `setThrowableHeading` jitter of up to 0.0075 per axis),
    speed `1.2 + 1.8 * draw` blocks per tick, written into the motion at the start *and* end of every tick so vanilla's
    `motion *= 0.99` and `motionY -= 0.05` (`EntityArrow.java` lines 454-457) never apply - a full draw is exactly 3.0
    blocks per tick, no drop. Writing after the move keeps the motion constant for `EntityTrackerEntry`.
  - **It drills on**: contact is vanilla's (ray trace stops the arrow on a block, entity scan kills it on a mob, so a mob in
    front of a block is hit first); a block contact tick is the one that advanced less than the arrow's speed
    (`advance < speed * 0.995` - vanilla backs off 0.05 blocks, 4.2% of `MIN_SPEED` and 1.7% of `MAX_SPEED`, while an open
    tick advances exactly the speed because `applyAim` overwrote the motion). Then `onBlockContact` blasts the piece between
    the last blast and the face ahead (`World#func_147447_a` from where the arrow sits along its aim) and **does not remove
    the arrow**: the block it was stuck in is gone, so vanilla's in-ground branch finds another block next tick, clears the
    flag and releases it. One shot into a mountain carves a corridor until `MAX_FLIGHT_TICKS` (200 ticks = 600 blocks at
    full draw).
    - Pieces back to back: `blastX/Y/Z` starts at the muzzle and moves to the face of every contact, so no block is scanned
      twice.
    - `stuckTicks` counts ticks held by blocks; above `MAX_STUCK_TICKS` (6) the flight ends with the bang (`finish`). Two of
      those ticks belong to a contact that *did* go through, so the limit is for bedrock, a protection plugin or an
      explosion resistant block.
    - A tick where vanilla *bounced* the arrow (an entity refusing the damage, e.g. a creative player: the motion ends up
      backwards) is an entity hit, handled by the bow's kill.
  - **The client does not know the aim: it takes the spawn velocity.** FML's `IThrowableEntity` is why:
    `FMLMessage.EntitySpawnMessage#toBytes` writes velocity only for one (the `instanceof` branch next to the data watcher
    stream) and `EntitySpawnHandler#spawnEntity` calls `setVelocity`; vanilla's `EntityArrow` is not one. **Without it a real
    bug in game**: zero motion on the fresh client copy, `applyAim` wrote the *default* `(0, 0, 1)` (due south, level), so
    the arrow flew sideways at three blocks per tick until position updates dragged it back - "the arrow flies flat and to
    my right, then corrects". `adoptSyncedAim` now takes `aim`/`speed` from the client's motion each tick, and
    `MIN_AIM_LENGTH` (at most the first tick) holds the spawn packet's orientation instead of recomputing from zero motion
    (`atan2(0, 0)` is 0: yaw 0, pitch 0).
  - **Entity hits use the bow's true kill**: vanilla's hit (damage, knockback, sound) happens inside `super.onUpdate` and the
    arrow is then dead, which is how an entity hit is recognised at all (a block contact never kills the arrow now).
    `findHitEntity` repeats vanilla's scan on the same step: box around the step, every box expanded by `0.3` blocks,
    `AxisAlignedBB#calculateIntercept`, closest intercept, shooter excluded. The kill is
    `MTBowKill.INSTANCE.kill(victim, shooter, this)`, the same `MTKillBase` branch the tunnel uses, so the two kill paths
    cannot drift apart. A bounce counts too.
  - Never pickable (`canBePickedUp = 0`). Aim, speed, muzzle, last blast point and stuck counter go to NBT: an arrow saved
    mid-air by a chunk unload resumes on the same line without re-blasting its tunnel.
- **The tunnel** (`MTExplosionTunnel#blast`, server side; the client learns of it from block change packets and deaths):
  blocks whose centre is within `Config.voidstarbow.VOIDSTAR_BOW_TUNNEL_RADIUS` (default **6.0** = thirteen by thirteen; 0.5 = the
  line's single column, 1.5 = three by three, 2.5 = five by five) of a blast piece are destroyed, and every living thing
  within that radius dies by `MTBowKill`.
  - One block steps, each opening only a `ceil(radius)` cube around the point reached - not the segment's bounding box,
    which for a 128 block flight at 45 degrees would be 90 x 90 x 90 (729,000 blocks) scanned to destroy a few hundred. Half
    a block of slack per step is what makes `ceil(radius)` enough.
  - Survives: air, fluids (vanilla explosions do not destroy them either), `getBlockHardness < 0` (bedrock, the portal, GT's
    unbreakable blocks), `getExplosionResistance > 100` (obsidian 600, stone 30, GT machine casings 10-20: "everything a
    player can mine, nothing meant to be blast proof"). **Nothing is dropped** - the tooltip says so.
  - **Living things die by `MTBowKill`, the bow's branch of `MTKillBase`**, shared by both kill paths - a direct hit
    (`onEntityHit`) and this sweep. Ladder (see the piggy's section): `Float.MAX_VALUE` with the `"infinity"` type (walks
    past armour sets that cancel every other type), armour piercing + creative mode, the fire hit for fire-only death gates,
    then zero health + `onDeath` by hand (no damage cap or invulnerability flag intercepts). A survivor is handed back
    (`restore`) and the thrower told with `messtech.voidstarBow.kill.failed`.
    - **v1 used the overheal exploit instead**: `heal(1 << 31)` (the old tooltip amount) plus hand written `setHealth` and
      `onDeath`, because `EntityLivingBase#heal` (lines 795-805) starts with
      `amount = ForgeEventFactory.onLivingHeal(this, amount); if (amount <= 0) return;` (a wrapped heal does nothing) and
      1.7.10 runs `onDeath` only from `attackEntityFrom` (`EntityLivingBase.java:931-941`). `MTOverheal` is gone; the ladder
      reaches everything it did and more.
    - The branch's `accepts` refuses the shooter (the tunnel starts at the muzzle, inside the archer); the tunnel pass also
      skips `victim == source` and `findHitEntity` excludes the shooter - three times over, but only the branch's copy is
      load-bearing.
    - The branch exists for its two messages, `death.attack.messtech.voidstar` (`%1$s` is the victim, the archer
      deliberately not named) and `messtech.voidstarBow.kill.failed`: `MTBowKill` hands them to the base constructor, so a
      bow kill is not announced with the piggy's line.
    - Size counts as well as position: reach = `radius + max(width, height) / 2`, so half a horse or boss inside is caught.
    - Already dead or dying (`isDead` or health <= 0) is skipped - an entity at zero health whose death path never ran
      cannot be killed by a second call either.
  - **Letting go heals the archer to full** (`Config.voidstarbow.VOIDSTAR_BOW_HEALS_SHOOTER`, default **true**,
    `MTItemVoidstarBow#onPlayerStoppedUsing`): `player.heal(max - current)`, a real heal of the difference, so the heal path
    runs (Forge heal event included) and the archer cannot pass their maximum. In the same `!world.isRemote` branch that
    spawns the arrow, so a failed shot (draw too weak, no arrow) heals nothing.
  - A player's shot posts `BlockEvent.BreakEvent` per block and leaves it standing if a protection mod cancels it; the
    vanilla "block destroyed" effect (`World#playAuxSFX` id 2001, what `Explosion` sends per block) goes out only within 12
    blocks of the impact - one packet per block over a long tunnel is what makes big explosions lag.
  - A piece longer than `Config.voidstarbow.VOIDSTAR_BOW_TUNNEL_LENGTH` (default **128** blocks) keeps its far end, drops the near one:
    the wall the arrow is pressed against is what the player looks at. Per piece, so only a first-block shot that far is
    affected.
  - `Config.voidstarbow.VOIDSTAR_BOW_BREAKS_BLOCKS` (default **true**, category `VoidstarBow`) turns block destruction off - the arrow
    still flies straight and trails, it just stops at the wall. Per pass: `Config.voidstarbow.VOIDSTAR_BOW_ANNIHILATES_LIVING` (default
    **true**) turns the true kill off, so a server can have the tunnel without the living, or the kills without the damage.
- **Particles** (`MTEntityFXVoidstar`, `MTVoidstarFx`): three sparks along the step just taken (at 3 blocks per tick one per
  position would be a dotted line), a small burst at the face of every drilled block, the big one at the end - flash at the
  wall, sparks through the whole tunnel (muzzle position in data watcher ids 20/21/22, so every client can draw the burst
  although only the server destroys blocks) and vanilla `largeexplode`/`smoke`.
  - `messtech:particle/voidstarSpark`, registered **into the item atlas** by the bow's own `registerIcons`; particles use
    `getFXLayer() == 2`, the one atlas `EffectRenderer#renderParticles` binds for a custom icon (layer 0 is the vanilla
    particle sheet, `EntityFX#setParticleIcon` throws for it). Layer 2 -> `TextureMap.locationItemsTexture`
    (`EffectRenderer.java` lines 127-144).
  - Full bright (`getBrightnessForRender` -> `0xF000F0`, what vanilla's spell particles return) so the trail glows in an
    unlit cave; `noClip` on, sparks hang in the tunnel being dug.
  - Cheap on purpose (7 sparks): a shot into a mountain triggers it every few ticks.
- **The name wears the `magnetar` text effect** (`docs/text-fx/`): the lang entry itself carries the inline marker -
  `item.messtech.voidstarBow.name=§[mg]混沌终焉·虚无贯星弓§r` (`mg` is the short name, `§r` closes it), in both
  `zh_CN.lang` and `en_US.lang`. Effect text is ordinary text, so no Java hook and no `AnimatedTooltipHandler` is
  involved: whatever draws the name with the font - the tooltip's first line (`ItemStack#getTooltip` takes
  `getDisplayName()`), the inventory label, a chat item link - gets the field-line shader.
  - Markers are inline declarations (`§[name...]`) or the scoped form built from U+2064. Both alphabets are
    deliberately disjoint from the other marker-based text renderer in the pack (it uses U+2063 and the brace form),
    so the two systems claim only their own strings and can be installed together.
  - `EnumChatFormatting#getTextWithoutFormattingCodes` (patched by `MixinEnumChatFormatting_TextEffect`) strips them
    wherever vanilla wants unformatted text: search, command parsing, NEI lookups. A renamed stack shows the NBT custom
    name instead; put the same marker in the custom name to keep the effect.
  - If the effect stops showing up: check `Config.general.TEXT_EFFECTS`, and that
    `assets/messtech/shaders/text/magnetar.frag.glsl` is packed; the renderer falls back to plain text (first palette
    colour) when the shader or the mask cannot be used.
  - The old `STARFIELD` name animation is no longer registered on the bow. `MTStarfieldText` / `MTStarfieldTextRenderer`
    and `MTAnimatedTooltipHandler#addItemNameAnimation` remain as the tooltip-animation API: `init()` still registers
    their renderers, and the registration shape below is what any future name animation would use.
- **`MTAnimatedTooltipHandler#addItemNameAnimation` is the registration shape a name animation needs.** The first tooltip line is the
  display name, drawn by vanilla before any handler sees the tooltip, so it cannot be animated by adding a line. The handler
  recognises a renderer's line by its **visible text** (`rendererFor`), so an entry answering with the item's name
  suffices - it must not be appended, which is what the new `Entry#drawn` flag is for (`addItemTooltip`/`addAnimatedText`
  entries are drawn, name entries are not). The bow used to register `MTStarfieldText.INSTANCE` with the lang key as
  supplier this way; if a name animation comes back, the same supplier shape lets a resource pack rename follow.
  - **A registration like that has to use `OreDictionary.WILDCARD_VALUE` as the stack's damage - a real bug.** The first version
    registered `new ItemStack(MTItems.voidstarBow)`, damage 0; a damageable stack's damage *is* its durability damage,
    `ItemStackMap.actualDamage` is `stack.getItemDamage()` and the handler's map is keyed by it, so the effect worked on a
    fresh bow and stopped once fired - what "the rainbow effect does not show up" looked like. With `WILDCARD_VALUE` on the
    NBT-insensitive map (`ItemStackMap(boolean NBTSensitive)` with `false`, the constructor the handler uses)
    `DetailMap#getKeyType` answers `WildcardAll` (`isWildcard(damage)` and then `!NBTSensitive` -> `withWildcardTag`),
    landing the entry in the map's single `wildcard` field, and `DetailMap#get` returns that field **before** damage or NBT:
    every damage, every enchantment and a renamed bow covered - the same wildcard `MTItemPiggy`'s registration uses.
  - Tooltip plumbing underneath: NEI does post gtnhlib's `RenderTooltipEvent`
    (`codechicken/nei/util/RenderTooltipEventHelper`, from `GuiContainerManager` line 633) and honours `alternativeRenderer`
    (line 648), and the marker NEI appends to the first line (`GuiDraw.TOOLTIP_LINESPACE = "\u00a7h"`) is a section code, so
    `MTTextAnimation.visibleText` strips it and the name still matches.
- **Limitation**: the magic circle reads the drawing client's own use state, so *other* players do not see somebody else's
  circle (1.7.10 does not sync item use - the vanilla bow pose has the same hole). Arrow, trail, bursts and tunnel are
  unaffected: they travel with the entity.
- Verification: `gradlew compileJava`; the texture generators self-check their output
  (`tmp/chaosbow/gen_voidstar_textures.py`, `tmp/chaosbow/gen_voidstar_ysm_circles.py`) and the shell loops are not part of
  the build. The Yes Steve Model pack is not a dependency: its JSON files only supplied the layout, timing and spin rates
  (read with the throwaway `tmp/chaosbow/inspect_ysm*.py`).

## 彩虹小扳手 (Rainbow Wrench) - GT5U tool / wrench registration

A GT5U tool item with an animated rainbow icon and three pinned numbers (耐久 `Integer.MAX_VALUE`、等级 325、挖掘速度
999). It is the reference for how this repo registers a **tool** rather than a machine/item:

- **Item**: `com.MessTech.common.items.MTRainbowWrench extends gregtech.api.items.MetaGeneratedTool` (`gt.metatool.messtech`,
  tool ID 100). One item hosts all tool IDs, like GT's `MetaGeneratedTool01`; `addTool(ID, name, tooltip, IToolStats,
  ToolDictNames.craftingToolWrench)` creates the entry, `GregTechAPI.registerWrench(stack)` puts it on
  `GregTechAPI.sWrenchList` (machine right-click wrenching, tool-box slot, pipe overlay, maintenance hatch).
  `MetaGeneratedTool` is created once from `MTItems` (`rainbowWrench`, static field, i.e. our preInit) and the item is
  registered by `GTGenericItem`'s constructor - not by `MTItems.registerItems()`.
- **Rendering comes for free**: GT's client init registers `MetaGeneratedToolRenderer` for every
  `MetaGeneratedTool.sInstances` entry (`gregtech/common/GTClient.java`), so no `IItemRenderer` is needed. The icon is
  `IToolStats#getIcon` -> `Textures.ItemIcons.custom("messtech", "tools/rainbowWrench")`, and `getRGBa` returns white so
  the art is untinted.
- **Icon asset**: `assets/messtech/textures/items/tools/rainbowWrench.png` is a vertical strip of 7 frames of 16x16 with
  a `.mcmeta` (`frametime: 2` = 100 ms/frame, the source GIF's delay; 7 frames = one turn/hue cycle). The frames were
  split from `tmp/wrench.gif` by `tmp/gen_wrench_complete.ps1`; the finished PNG is a placeholder **with the GIF's grey
  backdrop still opaque** - the art itself is expected to be re-cut with an alpha channel later (same command regenerates
  the strip from full-resolution frames in `tmp/wrench_icon_complete/`).
- **Stats**: `com.MessTech.common.items.tools.ToolRainbowWrench extends gregtech.common.tools.ToolWrench` inherits the
  whole wrench behaviour (dismantle machines, rotate blocks/pipes, AE2/IC2 wrenchables, 3 modes) and only overrides:
  `getBaseQuality() = 325`, `getSpeedMultiplier() = 999`, `getIcon`/`getRGBa`. GT computes the tooltip values as
  `harvest level = getBaseQuality() + material.mToolQuality` and `mining speed = getSpeedMultiplier() *
  material.mToolSpeed`, so the material must contribute **0** and **1.0**.
- **Material**: `com.MessTech.common.material.MTRainbow` - a `MaterialBuilder` material registered in an
  `IMaterialHandler` from the `@Mod` constructor (same timing as Chaos Matter), with `setTool(Integer.MAX_VALUE, 0,
  1.0F)` and **no** `add*Items()` calls, so no ingots/dusts are generated. It exists only as the stack's NBT
  `PrimaryMaterial` (tooltip's material name, "彩虹 lvl 325"). Its `mMetaItemSubID` stays -1 (nothing is generated).
- **Durability `Integer.MAX_VALUE`**: GT derives `GT.ToolStats.MaxDamage` as `100 * material durability * multiplier`, so
  only the stack NBT can hold the exact number. `MTRainbowWrench#prepareStack` writes the primary/secondary material,
  `MaxDamage = Integer.MAX_VALUE`, `Damage = 0` and `Mode = 0` when missing (idempotent, no writes afterwards).
- **Why `isItemStackUsable` is overridden**: `MetaGeneratedTool#getSubItems` is final and always feeds the creative tab
  (NEI follows) the bare stack plus a **Neutronium**-material copy - neither carries the Rainbow material. That method is
  the hook that sees both (it is called by `getSubItems`, by `getToolWithStats`, and by `getToolStats` before every
  tooltip/render), and GT already uses it for NBT fix-up (enchantments), so `prepareStack` runs there. Result: both
  creative entries, NEI's copies and every stack later held read the intended stats. There is deliberately **no crafting
  recipe** - the wrench is a creative/NEI item for now; `MTRainbowWrench#createStack()` builds the proper stack if a
  recipe or command is ever added.
- **Text**: lang keys are GT's meta-tool shape `gt.metatool.messtech.100.name` / `.tooltip` (see `zh_CN.lang`:
  `彩虹小扳手`, `按住左键以拆卸机器`) plus `Material.rainbowwrench` (the material's internal name is `RainbowWrench`,
  namespaced so it cannot collide with another addon's material); the tooltip's 耐久/等级/伤害/挖掘速度 lines are GT's
  own (`gt.item.desc.*`), nothing custom is added.
- **Verification**: `gradlew compileJava` (and `spotlessApply`), no runtime-only hooks. Gotchas that cost the most time
  are in `docs/GT5U-NOTES.md` §13 ("Registering an addon GT tool/wrench").
