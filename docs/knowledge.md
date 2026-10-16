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
- `getEnergyHatchTier()`: the highest `MTEHatch#getInputTier()` over
  `getExoticAndNormalEnergyHatchList()` - the machine's energy tier with laser/multi-amp (exotic) hatches and
  mixed-tier plain hatches included. Every "energy hatch tier" gate and bonus in MessTech reads this instead of
  GT's `getInputVoltageTier()`, which iterates `mEnergyHatches` only and returns 0 for those machines.
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
- Generation: `EU/t = core output × 5 × IC2's balance/energy/generator/nuclear × 2` (`MTReactor#getEuPerOutput()`;
  the trailing `× 2` is `MESSTECH_OUTPUT_MULTIPLIER`). GTNH's `config/IC2.ini` sets `nuclear = 5.0`, so one output
  point is worth 25 EU/t like in IC2 - the factor IC2's own `TileEntityNuclearReactorElectric#getOfferedEnergy()` and
  GT5U's NEI nuclear fake recipe use, and what makes a GT quad uranium rod (12 pulses × `sEnergy` 2 = 24 output)
  generate 600 EU/t in IC2's own reactor. MessTech doubles that to 1200 EU/t: the `× 2` is a deliberate maintainer
  balance decision, not a config value, so it is named instead of being an unexplained literal - do not treat it as a
  defect (see `docs/GT5U-NOTES.md` §15).
- **Heat lives per page, and a page is anchored on its access hatch block.** `MTReactorHeatSystem` keeps
  `Map<ChunkCoordinates, int[]>` keyed by the coordinates of the owning `MTReactorAccessHatch`
  (`MTReactorHeatSystem.PageOwner#mtReactorKey()`), not an `int[]` indexed by the global page order over all access
  hatches. That array renumbered every following page whenever one access hatch was added, removed or moved (the order
  came from sorting the hatches by their block coordinates), so stored heat silently migrated from one hatch's page to
  another hatch's page. `retainOnly()` drops the heat and the countdown of a hatch that left the structure, so a
  removed hatch cannot hand its heat to a hatch that later occupies the same position.
- **The heat cycle runs in `onPostTick()`, not in `checkProcessing()`**, which the base class skips while the machine
  is switched off: the cycle, the passive cooling and the meltdown countdown keep running there. Only the fuel and the
  energy output are gated by the power switch, through `isAllowedToWork()` -> `couldRun()` -> the `mLastCouldRun` flag
  that `ReactorContext#produceEnergy()` reports; IC2's fuel rods test that method themselves, so a switched-off reactor
  burns no fuel and produces nothing while its heat still moves.
- **Meltdown countdown instead of an instant explosion:** a page at or over its ceiling arms a
  `MTReactorHeatSystem.MELTDOWN_COUNTDOWN_TICKS = 600` tick (30 s) deadline. `armCountdowns()` runs at the end of
  every material cycle and keeps the original deadline of a page that is still too hot (pinning the heat at the ceiling
  cannot postpone the meltdown forever), `clearCountdown()` drops a page that came back under its ceiling, and
  `resetCountdown()` cancels every countdown (a player action such as changing the heat hatch or toggling fluid mode).
  The deadline is an absolute game tick that `explodeExpiredCountdowns()` checks every tick in `onPostTick()`, so a
  page detonates 600 ticks after it first crossed the ceiling. A running countdown is **persisted as its remaining
  ticks** (`mtReactorArmedCountdowns`) and re-anchored onto the machine's tick counter on load (`setLoadClock()` ->
  `refreshCountdownClock()`), so a chunk unload or a server restart resumes the meltdown exactly where it stopped
  instead of dropping or restarting it, and it only ticks down while the chunk is loaded. Since that payload holds the
  remainder and nothing else, a reload can neither extend nor shorten the 30 s window.
- **A ceiling change rescales the stored heat instead of detonating the machine** (`onCapacityChanged()`): every
  page's heat is multiplied by `new ceiling / old ceiling`, so a reactor at 90% of its old ceiling ends up at 90% of
  the new one. Before, the effect check was a plain `heat / maxHeat` ratio, so swapping a UHV heat hatch for an EV one
  (or removing it) detonated the reactor on the very next cycle.
- **Reactor plating counts again, IC2 style.** `ReactorContext#setMaxHeat()` is live and feeds
  `MTReactorHeatSystem.setPageMaxHeat()`: plating raises the ceiling of *its own* 9x6 page on top of the heat control
  hatch value (a page with platings must not raise the ceiling of a page without any), and within a page the largest
  plating bonus wins, so the result does not depend on the component layout. `getPageMaxHeat()` is that page's
  ceiling; a single item is bounded by `MAX_HEAT_BONUS_PER_ITEM` and the sum by `MAX_HEAT_CAPACITY`. `resetPageBonus()`
  re-collects the bonus before that page's heat pass, so a removed plating stops contributing immediately. It used to
  be a no-op that left plating with only its heat effect modifier (more heat per cycle) and a bigger explosion - a
  pure downside.
- **Passive cooling** comes from the heat control hatch tier (table in `docs/GT5U-NOTES.md` §15): every tick
  `MTReactorHeatSystem.tick()` removes `rate / 20` HU, accumulated fractionally so no tier's rate is rounded away, from
  the pages that carry at least 5% of the hottest page's heat (the empty pages of a large structure do not absorb the
  budget), halved while a meltdown countdown is running, and it runs while the machine is switched off too - it is the
  only way to bring an already hot reactor back down.
- **World heat effects run once per cycle, not once per page:** `applyHeatEffects()` uses the hottest page's ratio and
  that page's heat effect modifier, so an N-page reactor no longer irradiates the same entity and ignites the same air
  block N times. Which page melts down is still decided per page.
- **Multi-cycle prediction (`STABILITY_PREDICTION_CYCLES = 20`)**: `predictFuture()` repeats the heat pass on copies
  of every page until the heat stops rising (the page found its equilibrium) or crosses the ceiling, so the GUI/Waila
  can report how many cycles are left (`getCyclesToMeltdown()`, `-1` = stable) instead of the old boolean that could
  only answer "is the next cycle fatal".
- **Rated vs actually injected EU/t:** `getRatedEUPerTick()` is what the last completed cycle accumulated,
  `getActualEUPerTick()` is how much of it the dynamo hatches really swallowed; a dynamo that cannot take the output
  silently loses it, so both are shown and a shortfall below 90% of the rated value that persists for
  `OUTPUT_SHORTFALL_REPORT_TICKS = 20` ticks is reported (`hasOutputShortfall()`). A page that explodes still settles
  the EU/t the cycle had accumulated before the detonation.
- **Fluid ("wet") mode** is toggled per controller with a screwdriver (`onScrewdriverRightClick`) and needs a coolant
  input hatch and a hot coolant output hatch, otherwise the machine soft-stops. The loop mirrors IC2's: 1 mB of
  `ic2coolant` per 1 HU of buffered component heat (`MTReactorHeatSystem.coolantForHeat()`,
  `COOLANT_PER_BUFFER_UNIT = 1` - MessTech's own ratio rather than IC2's 2 mB/HU `huOutputModifier`, so the feed rate
  reads as "as much coolant per second as the core makes heat per second"), the same amount of `ic2hotcoolant` is
  filled, and if the output hatch cannot take all of it the drained coolant is refunded, so the loop can neither
  duplicate nor delete fluid. `addEmitHeat`/`isFluidCooled` are live now, which is what makes the mode matter: IC2's
  vents (`ItemReactorVent` / `ItemReactorVentSpread`) hand the heat they removed to the fluid buffer instead of losing
  it, and MOX fuel (`ItemReactorMOX#getFinalHeat`) doubles its heat output above 50% heat. Neither is mode-gated on
  the component side - the condensators, lithium cells and heat switches never asked about the mode at all. Dry mode
  does not lose the buffered heat either: half of it is pushed back onto the core, IC2's `addHeat(huOutput / 2)`.
- **A locked slot holding a full durability component is never pre-emptively output** (P21): only a component that
  already lost durability is salvaged before a cycle that the prediction says destroys it. A fresh component dying in
  the next cycle means the layout itself is wrong, so swapping it cannot help - the machine stays soft-stopped instead
  of consuming input items.
- **The explosion power limit is a config option now** (`Config.REACTOR_IC2_EXPLOSION_POWER_LIMIT`, default 45, IC2's
  own `protection/reactorExplosionPowerLimit`): it used to be a hardcoded `45.0F` and it is the value the summed
  component influence is clamped to before it is mapped onto the DE power scale (`REACTOR_EXPLOSION_DE_POWER_LIMIT`,
  40). `explodeReactor()` also clears the heat of every page on purpose - the controller tile entity and its heat store
  survive the `explodeMultiblock()` call for the rest of the tick, so a hot store would otherwise be left behind on a
  dead machine - and it settles the EU/t the cycle had accumulated first.
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
  unchanged. Reuse this layout (and key naming) for the other machines. The reactor tooltip has no muffler hatch line:
  the structure never accepted one, and the old line advertised a hatch the player could not usefully place.
- Data stick copy (`MTReactorAccessHatch implements IDataCopyable`): the GT5U pattern of `MTEHatchOutputBus` /
  `MTEHatchConfigurableBase` - left click with an `ItemList.Tool_DataStick` saves, right click loads, and a constant
  `"type"` string (`DATA_STICK_DATA_TYPE`) is checked on paste. Safe on a GT machine block: `MaterialMachines` is
  `setRequiresTool()` and the harvest tool is a wrench, so a data stick deals no block damage.
- The payload is settings only - a version (`ver`), the source page count (`page`) and a sparse `locks` list of
  `{slot, threshold, item}` for the locked slots. **The components themselves are never stored or written**: a paste
  only touches this hatch's lock state (`mSlotState` / `mSlotMemory`), a slot the config leaves unlocked is unlocked
  here, and a page this hatch does not have keeps no lock. A locked slot that is empty is reported
  (`copy.empty_slots`) - the lock stands and works as soon as the component is put in.
- **The component type decides where a lock lands, not the slot number** (`remapLocks()`): a stick written on one
  layout and pasted onto another holds the same parts in different slots, so a pure slot-number replay locked the wrong
  slots. Every entry is resolved in three passes: (1) a slot that already holds that component type - the only place
  the existing inventory is read, (2) its original slot number when that slot does not already carry another lock,
  (3) any other free slot on the same page. An occupied slot that carries a lock is never overwritten, a lock that
  cannot be placed anywhere is dropped and reported (`copy.remap_dropped`), and `copy.remapped` reports how many locks
  the paste applied over how many pages.
- Two earlier revisions were wrong and must not come back: (a) storing the components and writing them into the target
  (that is not a settings copy), and (b) matching the config against the target's items by type and *skipping* every
  entry that did not line up - that locked nothing whenever the comparison failed and made the paste depend on the
  inventory. The type-first placement above is not that attempt: it only decides *where* a lock lands and falls back to
  the slot number and then to any free slot on the page, so no entry is lost just because the target layout differs.
- If a paste replays nothing, the hatch logs (logger `MessTech`, WARN) how many lock entries the stick carried, how many
  survived `loadItemStackFromNBT` and how many pages were usable. A bare JVM cannot resolve Forge item ids, so this log
  is the field diagnostic.
- Pages map 0..N-1 one to one: a smaller hatch ignores the pages it does not have (`copy.pages_dropped`). The version
  field exists so a stick written by an earlier layout is refused (`copy.invalid`) instead of half applied. The stick
  itself performs no tier validation - it only carries and replays settings, and whether the target structure may hold
  that hatch is the controller's job (`MTReactor.checkAccessHatchTier()` caps the access hatch tier with the heat
  control hatch tier), so replaying a stick from a large hatch onto a lower tier one is legal and just drops pages.
- **The meltdown is Draconic Evolution's reactor explosion, ported** (`MTExplosionDE` + `MTExplosionDETrace`, 1:1 out
  of `tmp/Draconic-Evolution-master`): one block ring per tick out to `power * 10` blocks, one trace per ring block
  which then blasts its whole vertical column, instead of IC2's `ExplosionIC2` (whose ray count does not scale - a 450
  power explosion fires ~25 million rays and freezes the server before the first block breaks). What DE does *not* have
  in that process is a visual, and neither had this port: DE triggers the fireball from the tile entity
  (`TileReactorCore#goBoom` sends `(int) (power * 10F)`, the client answers with
  `Particles.ReactorExplosionParticle`). MessTech has no packet channel, so the same effect is spawned as a synced
  entity instead - `MTEntityDEBlast`, drawn by `MTDEBlastRenderer`, both documented with DE's own line numbers. Every
  `MTExplosionDE` gets it, the meltdown and the bow's DE mode alike, and its boom is played client side at tick 3,
  exactly where DE's particle plays its sound.

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
  copied verbatim from the GTNH Chinese localisation that ships under `../tmp/Translation-GTNH`: `GregTech_zh_CN.lang`
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
  (`UITexture.fullImage("messtech", "gui/picture/mt_logo")`); `OneStepCircuitPoolFrontend` reuses the same
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
  - 0 = Component Assembly Line: generic `ProcessingLogic` recipe map lookup; recipe casing tier
    (`mSpecialValue`) limited by `MTMultiMachineBase#getEnergyHatchTier()` (the highest energy hatch tier, exotic
    hatches included), so laser/multi-amp hatches count and the best hatch sets the limit. The same tier drives
    the Tier 1 parallel count.
  - 1 = Assembly Line: data-stick / Data Access; LevelTier 2 required; unordered input matching by
    `MTAssemblyLineMatcher` instead of a recipe map lookup (not the original ordered AL), and no
    single-recipe locking (`supportsSingleRecipeLocking()` is false there, like GT's own Assembly Line).
- Structure `F` accepts Data Access hatch.
- Independent Assembly Line recipe map `MTRecipeMaps.assFactoryAssemblyLineRecipes` holds **only fake NEI
  pages**, one per `RecipeAssemblyLine` definition (alternatives kept, so NEI cycles them in one slot); it has
  no runnable recipes on purpose. See `docs/GT5U-NOTES.md` § 11.6.
- `MTAssemblyLineMatcher` resolves an authorised definition against the current input buses on every recipe
  check: every bus is read as one unordered pool (wildcard-damage alias included, NBT ignored), and each slot
  takes the alternative that leaves the most parallels, so the ingredients may sit in any bus in any order and
  a slot written as "A or B" is satisfied by whichever is there. The resulting concrete ingredient list goes
  through the normal `ProcessingLogic` pipeline, so parallels, perfect overclock, void protection, debug/ME
  buses and the consumption itself stay the shared code.
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

### MTNanoScaleFoundry and the one-step circuit pool
- Registered as `32411`, extends `TickableParallelismAcrossMultiMachineBase`, 3x3x3 structure.
- 11 threads/circuits 1-11 map to the 11 normal NAC pools (`getMaxThreadCount()` = 11). The old 12th thread is gone:
  the former "24" pool is now **machine mode 1**.
- **Modes** (`totalMachineMode()` = 2, mode 0 is the default): consuming an Astral Array from an input bus
  (`consumeAstralArrayFromBuses()`, persisted in NBT) unlocks mode 1, i.e. `supportsMachineModeSwitch()` returns
  `astralArrayUnlocked`. `getGui()` only registers the MUI2 mode icons while it is true, so the mode button appears
  and disappears with the unlock; the screwdriver (`onScrewdriverRightClick`) switches the same way. Both refuse a
  switch while the machine is **active** (`isMachineActive()` = `IGregTechTileEntity#isActive`; GT sets that flag from
  `mMaxProgresstime > 0` and mode 0 keeps a one-second cycle going, so a machine in mode 0 is active whenever it is
  switched on). `setMachineMode` also refuses mode 1 without the array. Entering mode 1 **drops** whatever the
  threads still held (`clearAllTasks()`, inputs are not refunded) instead of making the player wait for every thread
  to finish; since a running machine never reaches that point, what gets dropped is what the threads held when the
  machine was switched off - their tasks sit frozen there because `checkProcessing` stops ticking them. The GUI
  refusal is silent - the icon and the `gt.interact.desc.mb.mode` / Waila running-mode lines simply keep their value -
  while the screwdriver writes a chat line from `machine.nanoscale.*`.
- Mode 1 disables every thread and delegates to `super.checkProcessing()`, so the machine's **main thread** runs one
  recipe through the shared GT pipeline (`mMaxProgresstime` / `mOutputItems` / `lEUt`). `getRecipeMap()` returns the
  one-step pool there and `canUseControllerSlotForRecipe()` is false, so the selector comes from an input bus circuit
  slot like an independent machine. `shouldDisplayCheckRecipeResult()` is true only in mode 1.
- GUI/Waila in mode 1: the per-thread rows and the per-thread Waila list are hidden.
  `getWailaNBTData` writes only the `oneStepMainThread` flag, and `getWailaBody` adds one
  `machine.nanoscale.status.mainthread` line ("机器主线程运行" / "Machine main thread running") under GT's own
  progress bar and the "running mode" line that `MTMultiMachineBase#getWailaNBTData` writes while the mode switch is
  supported.
- `onWireCutterRightClick` refuses to take the Astral Array out while mode 1 is running, and a successful extraction
  drops the machine back to mode 0 - the array is what mode 1 exists for.
- Binding in mode 0: a controller-slot circuit (1-11) selects one active pool; with the controller slot empty,
  each input bus circuit slot can bind that bus to its own pool so several pools run simultaneously.
- **Programmable pattern hatches** (可编程仓室: Programmable-Hatches' 编程样板输入总成 MK.III /
  编程样板输入总线) are `IDualInputHatch` implementations, and GT's `MTEMultiBlockBase#addInputBusToMachineList`
  routes every `IDualInputHatch` into `mDualInputHatches`, **never** into `mInputBusses`. The thread scheduler has
  to read `mDualInputHatches` as well or such a hatch is invisible and every thread reports 空闲 while
  `checkProcessing()` still returns SUCCESSFUL on its one-second cycle. Each `IDualInputInventory` counts as its own
  input source (`DualInputBinding`): one buffer per AE pattern, carrying its own unwrapped circuit next to the
  pattern's items, so one hatch feeds several pools at once. That circuit is a **zero-sized integrated circuit** -
  `BufferedDualInputHatch.DualInvBuffer#programLocal` unwraps the pattern's programming circuit into the buffer, and
  the buffer exposes it through `getItemInputs()`; a buffer's own marker wins over the hatch-wide one
  (`getDualHatchCircuitNumber` takes the hatch's first exposed pattern circuit), so a buffer whose pattern carries no
  circuit follows its hatch's first one and only a hatch exposing no circuit at all feeds no pool. Circuit markers
  are filtered out of the ingredient list (they select the recipe page, they are not consumables).
- Consumption from a programmable hatch **is** the stack decrement: `getItemInputs()` hands out the hatch's live
  buffer stacks (`ItemStackG`/`FluidTankG` are the buffer's own objects, not copies), so `depleteThreadItems`
  shrinks the buffer's own stack and the hatch refills it when the AE job pushes the pattern again. This mirrors
  GT's own pipeline, which consumes the stacks a dual hatch hands out the same way (`GTRecipe::consumeInput`). Fluid
  from the same buffer is drained the same way, in `depleteThreadFluid`. Calling `removeResource` on top of that
  would deduct twice.
- `checkProcessing()` keeps mode 0 on a one-second GT cycle and calls the thread scheduler
  inside the standard `checkRecipe()` wrapper, so `startRecipeProcessing()` / `endRecipeProcessing()`
  are active for ME input buses/hatches. `lEUt` is set from running task totals so GT drains energy
  normally.
- Input-bus circuit numbers are snapshotted in `onPostTick` (`duringProcessing = false`) **and** at the top of
  `checkProcessing()` (`duringProcessing = true`). The phase matters: an ME input bus keeps its circuit in
  `getCircuitSlot() + SLOT_COUNT` while `startRecipeProcessing()` is active and in `getCircuitSlot()` outside it, and
  the stocked items take over the low slots inside the window - reading the wrong one binds the bus to an arbitrary
  AE item instead of its circuit. The second refresh runs inside the window because `checkRecipe()` is final and
  calls `startRecipeProcessing()` first, which is the only phase in which GT's own compressed/quantum input bus
  publishes its circuit at all. The `onPostTick` pass stays because the board-fluid helpers call
  `getThreadInputBuses()` outside a recipe check.
- **Renamed items are matched the way GT matches them**: `inputSatisfies` ignores NBT exactly when the recipe is not
  NBT sensitive and the ingredient is not one of the three items GT always compares by NBT - the IC2 universal fluid
  cell, the GT data stick and the data orb (`GTRecipe#shouldCheckNBT`, mirrored from `MTParallelHelper`). So an item
  renamed by the Forming Press with a Mold (Name) - which writes `display.Name` into the stack - satisfies the
  recipe's tag-free input, while a data stick still has to hold the right data. Without this the recipe lookup still
  matched, but the machine's own pre-consumption check rejected it and the thread silently reported 空闲. The
  consumption side has to hand the bus its **own** stack (`findThreadResourceInBus` returns it, `depleteThreadItems`
  passes `found.copy()` to `removeResource`), because GT's removal compares with NBT and the recipe's tag-free copy of
  a renamed item would match nothing.
- Each thread currently runs one active recipe at a time.
- Board Processor uses per-thread internal immersion tanks (`BoardTankState`), not direct recipe fluid depletion: one
  tank per legal immersion fluid, **five** since GT5U 5.09.54.183 added UU-Matter (`MTEBoardProcessorModule#LEGAL_FLUIDS`
  is the source; UU-Matter's impurity is UU-Amplifier). The type list, the fluid each type takes and the impurity it
  turns into all live in `MTNanoScaleFoundry`'s `BOARD_TANK_FLUIDS` / `boardTankFluid` / `boardTankImpurity`, and the
  GUI's tank grid and Waila both read `getBoardTankTypeCount()`, so the next fluid is one table entry plus its GUI row.
- Waila sends per-thread name/index/active/progress/EU/parallel, the first task's output item names/counts, the board
  tanks and `isSneaking` (the same flag AE's `WirelessDataProvider` uses for its connected-device list). Collapsed the
  body prints **one** machine-wide progress bar - the sum over the running threads through GT's
  `GTWaila.getMachineProgressString`, so it reads like any other multiblock - and sneaking expands it into the
  per-thread lines, the board tank lines and the output item lines. That whole block only exists in mode 0.
- The 11 per-module NEI pools (`mt.recipe.nanoscale.*`, one per original NAC pool) copy GT5U's own maps 1:1,
  `maxIO` included. Four envelopes grew in GT5U 5.09.54.183 and are mirrored here: Part Processor (its ident is still
  `smdprocessor`, GT renamed the module in that version) 1/1/0/0 -> 6/4/3/0, Etching Array 2/1/2/0 -> 2/2/2/0,
  Wire Tracer 1/1/0/0 -> 2/4/0/0 and Encasement Wrapper 4/1/0/0 -> 4/1/2/0. `maxIO` is only the NEI page's envelope -
  `RecipeMapBackend#doAdd` validates the minimums, never the maximums - so a stale value mis-draws the wider recipes
  outside the panel instead of losing them. The one-step pool flattens GT5U's own maps, not these copies, so its
  recipes are unaffected either way.
- `MTRecipeMaps.oneStepCircuitPoolRecipes` is the renamed one-step pool
  (`mt.recipe.nanoscale.onestepcircuitpool`, display stack = the controller), using the custom
  `OneStepCircuitPoolFrontend`: 9 slots per row, the progress bar / single item output / logo sharing the header row,
  the item inputs in a 9x6 grid under it and the fluid inputs in a 9x6 grid below that. The selector circuit is a
  normal recipe input now, so there is no special slot any more.
- The pool's recipes are registered as **fake** recipes (`addFakeRecipe`): `RecipeMapBackend#filterFindRecipe` and
  `GTRecipeLookupBuilder#add` both skip `mFakeRecipe`, which is what keeps pages with up to 54 item + 54 fluid inputs
  out of `GTRecipeLookup`. Mode 1 therefore resolves its own match: `createProcessingLogic()` overrides
  `findRecipeMatches(map)` and scans `oneStepCircuitPoolRecipes.getAllRecipes()` with
  `GTRecipe#isRecipeInputEqual(false, false, inputFluids, inputItems)`, then hands the winner to the shared pipeline -
  the same shape `MTAssFactory` uses for its Assembly Line definitions, so parallels, overclock, void protection, ME
  buses and the input consumption stay GT's code. `findRecipeQuery` never sees these recipes.
- `populateOneStepCircuitPoolRecipes()` recursively flattens every original Assembly Matrix
  recipe through Assembly Matrix + all module pools down to `CircuitComponent.realComponent` real
  inputs. It keeps the whole Crystal/Wetware/Bio/Optical line (Processor/Assembly/Supercomputer/
  Mainframe) plus the independent special circuits Pico/Quantum/Planck.
  Board Processor fluid inputs are removed (machine-internal/NEI display only); other module fluids and
  Assembly Matrix fluids are kept; duplicate item/fluid inputs are merged and recipes are deduplicated.
- `packMaterialInputs()` then packs the raw material components. `MOLTEN_PREFIXES` = `bolt`, `foil`,
  `frameGt`, `itemCasing`, `plate`, `screw`, `wireFine` become the same material's molten fluid (a bolt, a fine wire
  or a screw is `M / 8` = 18 mB, a foil `M / 4` = 36 mB, a casing `M / 2` = 72 mB, a plate `M * 1` = 144 mB, a
  frame box `M * 2` = 288 mB, converted with `prefix.getMaterialAmount() * INGOTS / GTValues.M`). The wire rule is
  separate: a 1x wire only becomes a 16x wire (16:1, remainder left as 1x) when its material name starts with
  `Superconductor` - the `CircuitComponent` entries SuperconductorLuV..UMV - and every other wire melts instead, which
  is what the NAC chains' SpaceTime (时空) and Infinity (无尽) wires do. `packChips()` then wraps every part GT has a
  wrap for: it walks `bartworks.system.material.CircuitGeneration.CircuitWraps#VALUES` (chip -> wrap, cached in
  `chipWraps`), so 16 of any wrapped part - RAM/NOR/NAND chips, the CPUs, the circuit boards, the SMD parts - become
  one wrap plus `1 * HALF_INGOTS` = 72 mB molten polyethylene, i.e. exactly what GT's own assembler recipe for a wrap
  spends (`CircuitWraps#registerWrapRecipe`: 16 parts + circuit 16 + 1 * HALF_INGOTS molten polyethylene; the circuit
  is that recipe's selector and the machine's own circuit slot already carries the pool selector, so it is not
  repeated). Each pack keeps the material amount identical (`16 x 1x wire == 1 x 16x wire`, wrap = 16 parts + the
  polyethylene), same-material entries merge into one fluid, and a remainder keeps its item form. No molten form means
  the item is kept as is.
  `populateOneStepCircuitPoolRecipes()` builds `chipWraps` first and defers the whole build while that table is empty
  (GT registers the wraps during its postload), so the server-started fallback builds the pool with the wraps in place
  instead of baking plain chips into every page.
  Material lookup goes through `GTOreDictUnificator.getAssociation()` first; GT++ materials (Rhugnor, Quantum,
  AstralTitanium, ...) are **not** GT `Materials` and their items carry no `ItemData`, so `moltenOfName()` falls
  back to parsing the ore dictionary name (`boltRhugnor`, `screwQuantum`) and resolves it through `Materials.get()`
  / a case-insensitive `Materials.values()` scan, then through GT++'s own `Material.mMaterialsByName` /
  `mMaterialCache` (`getFluidStack`).
- Input order is normalised after packing: entries are merged again (packing can duplicate 16x/leftover wires) and
  sorted by display name - so a whole part family (both RAM chips, every superconductor wire, every board) is
  adjacent instead of one chip landing in slot 1 and the next family member in slot 10 - then by ore dictionary
  signature, item and amount for determinism. Fluids sort by localized name.
- The NEI page list is ordered by `recipeComparator`: selector levels 1-5 (Processor/Assembly/Supercomputer/
  Mainframe, then the original GT ladder), with the level-6 special Pico/Quantum/Planck chains last.
- The selector is 1-6, and every flattened recipe carries it as a real `GTRecipeBuilder.circuit(n)` input, so the
  machine matches it the way any GT machine matches its circuit: the bus circuit slot satisfies the input and the bus
  never consumes its own circuit slot. `getOneStepCircuitLevel()` returns 5 for `CircuitCalibration.PRIMITIVE` (the
  original GT circuit ladder: Nand chip / Microprocessor / IntegratedProcessor / NanoProcessor / QuantumProcessor) and
  `MTRecipeMaps.ONE_STEP_SPECIAL_CIRCUIT_LEVEL` (= 6) for the special independent chains.
- Measured max IO before packing: 61 distinct item inputs / 24 distinct fluid inputs (PlanckCircuit, after the
  producer lookup learned to follow every output slot of a multi-output module recipe). Packing moves entries from
  the item grid into the fluid grid, so the map is declared as `maxIO(54, 1, 54, 0)` = 9x6 item rows + 9x6 fluid
  rows; the even split keeps the page height at 254 while both sides have room. Those 61/24 were measured with
  packing switched off and do not describe the pool any more - the post-packing peaks are nowhere recorded, which is
  fine because only the capacity matters. The circuit input takes one of the 54 item slots.
  `OneStepCircuitPoolFrontend` owns those constants - `maxIO`, every slot position, the recipe background
  (170x254) and the NEI panel height are all derived from them - and `populateOneStepCircuitPoolRecipes()` logs
  the recipe's name with both counts if a flattened recipe ever grows past the capacity, so that warning is the only
  place a current peak shows up.

### Ultimate Pattern Terminal (终极样板编码终端)
- An AE **cable part**, not a block: `PartUltimatePatternTerminal extends PartPatternTerminalEx` (AE's extended
  pattern terminal) and `ItemUltimatePatternTerminal` is its `IPartItem`. The item is placed with AE's own
  `ApiPart#placeBus`, so the part attaches to a cable bus on the clicked side and AE's `BusRenderer` draws it in the
  inventory; there is no block and no tile entity of this mod - the part's host tile is AE's cable bus. It takes a
  channel, pulls blank patterns out of the network and writes AE's own encoded pattern item back, exactly like AE's
  part. Of the GT-Not-Leisure original it started from, the second (Avaritia 9x9) crafting mode, its
  `DireCraftPattern` item, the mode button and the two NEI overlay entries are all gone: none of them belongs to the
  terminal this is a copy of.
- The one deliberate difference from AE's part is the **input** grid: 4x4 x `INPUT_PAGES` (8) = 128 cells instead of
  AE's 4x4 x 2 = 32, which is what the Nano-Scale Foundry one-step circuit pool (up to 54 item plus 54 fluid
  inputs) needs. The
  **outputs** keep AE's own 4x4 x 2 = 32 cells and AE's own placement. The inverted flag and the active page are kept
  on the part instead of using AE's fields, because `PartPatternTerminalEx#setInverted` clears every input cell past
  what the inverted layout shows and this terminal has four times as many.
- Two mixins teach AE's own extended pattern terminal classes about that page count
  (`Mixins.ULTIMATE_PATTERN_TERMINAL`, both gated on the `appliedenergistics2` mod). Both ask the same question first -
  is the container's part a `PartUltimatePatternTerminal` - because both classes are shared with every other extended
  pattern terminal (AE's own, AE2FC's fluid one, any addon's):
  - `MixinContainerPatternTermEx` answers `getPatternInputPages` with that part's own count. AE inlines the constant
    `PartPatternTerminalEx.exPatternInputsPages` (= 2) into that method, so a part subclass cannot change it, and
    `GuiPatternTerm#initVirtualSlots` sizes the whole virtual input grid from that answer; every other terminal still
    gets AE's two pages.
  - `MixinGuiPatternTermEx` redirects the one `GuiScrollbar#setRange(0, 1, 1)` in AE's GUI constructor to that page
    count, so the private `processingScrollBar` (AE hardwires it to two pages) can reach all eight. The redirect fires
    for whichever `GuiPatternTermEx` is being built, so for anything but this terminal it hands AE's own call straight
    back (`bar.setRange(min, max, pageSize)`); without that guard AE's own terminal's scroll range would be
    *recomputed* by this mod, which only happens to match today because AE's container answers the same two pages.
  Note the corollary: sneak-clicking this terminal takes AE's own path (`super.onPartActivate`), so it opens AE's own
  `GuiPatternTermEx` on this mod's container - AE's GUI, this terminal's eight pages.
- `GuiUltimatePatternTerminal extends GuiPatternTermEx` owns the input arrangement: AE's own loop over the
  container's page count, with the inverted single column's row index wrapped (`activePage % rows`, because eight
  pages do not fit AE's four rows). The outputs are AE's own loop unchanged, and the background, item list, view
  cells, buttons, scroll bar and all of the syncing stay AE's code path.
- The terminal opens through Forge instead of AE's `GuiBridge`: the bridge maps a container class to a GUI class by
  name, so a new terminal cannot be added to it without touching AE's own. `PartUltimatePatternTerminal#onPartActivate`
  packs its side into the bits above `GUI_ULTIMATE_PATTERN_TERMINAL` (20) and calls `player.openGui`;
  `CommonProxy`/`ClientProxy` resolve the part back from the cable bus with `Platform.getPartFromTE` and hand the
  server AE's own `ContainerPatternTermEx`, with the `ContainerOpenContext` a GUI opened through Forge's
  `IGuiHandler` has to set itself (AE only fills it in `Platform#openGUI`, and without it AE's middle-click "how
  many to craft" path on the blank pattern slot NPEs). The inherited `getGui(player)` is consulted first, so the
  craft permission stays AE's own check.
- The pattern NBT is exactly what AE writes (`PatternEncodingHelper`: `in`/`out` lists of
  `IAEStack#toNBTGeneric()`, no `crafting` key, item `encodedUltimatePattern`). The expected outputs are placed by
  hand or imported from NEI - there is no crafting page left to derive them from.
- Artwork: the item wears AE's own `appliedenergistics2:ItemPart.PatternTerminalEx` sprite with sprite number 0 (the
  block atlas, where that sprite lives), and the part draws AE's `PartPatternTerm_Bright` face on the cable; the GUI
  is AE's own `pattern3.png`/`pattern4.png`. **A texture name must not carry a `blocks/` prefix**: both atlases
  already prepend `textures/blocks`, so `appliedenergistics2:PartPatternTerm_Bright` is
  `assets/appliedenergistics2/textures/blocks/PartPatternTerm_Bright.png` while `appliedenergistics2:blocks/...`
  looks for a `textures/blocks/blocks/` folder that does not exist and silently lands on the purple-black missing
  texture. `CableBusTextures.registerIcon` names it the same way.
- The recipe is unchanged (a titanium frame around AE's own extended pattern terminal part, two Engineering
  Processors, a Calculation Processor and a Pattern Capacity Card) and now produces the part item; its registry name
  stays
  `UltimatePatternTerminal`. An existing world loses the old block - there is no migration.
- NEI: `NEI_MessTechConfig` makes **every GT machine recipe page** transferable into this terminal - the Nano-Scale
  Foundry one-step circuit pool is one of them - by walking `RecipeCategory.ALL_RECIPE_CATEGORIES` and taking the
  categories whose
  frontend registers NEI pages, exactly like NotEnoughEnergistics picks the idents it serves. Each one gets both
  `API.registerGuiOverlay` (NEI draws that page over this GUI; `RecipeInfo` looks the pair up by the GUI's exact class
  and the page's unlocalized name) and `API.registerGuiOverlayHandler` (the transfer button). A page whose ident is not
  registered simply gets no button, and the terminal is deliberately **not** an `addRecipeCatalyst` anywhere: it is a
  pattern terminal, not a machine that runs those recipes.
- Which handler packs the page depends on the pack: with **NotEnoughEnergistics** loaded - the GTNH transfer for AE's
  pattern terminals, and now a dependency - its `NEEPatternTerminalHandler.instance` is registered for this GUI over
  NEU's own ident set (`RecipeProcessor`'s processors plus a null ident, minus the two crafting idents, since this
  terminal is built on AE's extended part and cannot craft), so the terminal behaves like AE's under NEU and NEU's
  options apply. Without NEU, this mod's own `PatternImportOverlayHandler` takes the GT categories alone: it reads GT's
  `FixedPositionedStack` flags, which a non-GT page does not have. NEU registers itself only for AE's own terminal
  classes and NEI looks a transfer up by exact class, which is why this terminal has to ask for those idents itself.
- The import packs the page's ingredients into input cells 0, 1, 2 .. in the order the page reads them (top to bottom,
  left to right), instead of copying the panel positions into a matching cell layout. That layout does not exist here:
  the terminal's pages are four cells wide while a GT page's panels are much wider, so a position-for-position copy
  would scatter a handful of ingredients over pages of empty cells. Which stacks count as ingredients is asked of GT
  itself (`PatternImportOverlayHandler#isIngredient`): every item and fluid input of a GT NEI page is a
  `GTNEIDefaultHandler.FixedPositionedStack` built with `isInput` true, while outputs are built with it false. Of the
  inputs, `isNotConsumed()` keeps the "required but not consumed" ones out (a mold, a programming circuit ...), which
  GT itself marks with a zero size or amount: a pattern holding them would consume them. NEU makes the same choice by
  default for the AE terminals it serves.
  **Never test that by position against the frontend's constants**: GT's NEI slots sit at the frontend position plus
  `GTNEIDefaultHandler.WINDOW_OFFSET` (`(-5, -11)`, the recipe panel's interior offset) plus the one pixel
  `FixedPositionedStack` adds, so the pool's item panel starts at `rely` 16, not at `ITEM_Y` 26, and the first column of
  both panels is at `relx` -1. A filter built from `ITEM_X`/`ITEM_Y`/`itemRows()`/`fluidRows()` therefore dropped the
  whole first item row (9 of 9), the first item of every following row and the first fluid of every fluid row - the
  imported grid started at the recipe's **eleventh** item - and a page whose item inputs fit into those ten slots lost
  every item and kept only its fluids. The same offset is also why the extra rows GT adds for a recipe wider than the
  pool's `maxIO` are now imported for free: nothing is measured against the pool's rectangles any more.
- The recipe's result is imported too, but **not** from `IRecipeHandler#getResultStack` alone: GT5U's
  `GTNEIDefaultHandler.CachedDefaultRecipe#getResult()` answers `null` and the page draws its item output as an
  "other stack" instead, so `getResultStack` and `getOtherStacks` are both read into the output cells in that order.
  NEI hands results over separately from the ingredients, and reading only the first is why importing a page left the
  terminal's output grid empty - AE then encoded an input-only tunnel pattern instead of a processing one.
- What travels to the server (`PatternImportHandler`) is one compound holding `in` and `out`, each a map of cell
  index to ItemStack, so the server side needs no geometry at all. It finds the target through
  `player.openContainer instanceof ContainerPatternTerm` plus the part check - AE's own container is what the terminal
  opens - and marks its `inputsSync`/`outputsSync` dirty. The overlay recognises its GUI by class.
- Every cell carries its real item count **again** under `PatternImportHandler.COUNT_KEY` (`MTCount`, a long), written
  by `PatternImportHandler#writeCell` and restored by `#readCell`. 1.7.10 stores `ItemStack`'s count in the NBT
  **byte** `Count` (`ItemStack#writeToNBT` line 178) and reads it back as a byte, so a count outside -128..127
  round-trips as a different number: 1024 and 4096 both come back as 0, 1025 and 4097 as 1. The pool's flattened
  recipes do use such counts inside a single cell (reported from the Planck chain's page: 1024 Optical Fiber Cables,
  4096 ASoC RAMs), which is why an imported cell showed a bare item instead of its amount: AE only draws the amount
  for `stackSize > 1`, and its `AEItemStack#drawInGui` draws the icon with the count forced to 1
  (`AEStack#drawOverlayInGui`, reached from the pattern slot's `VirtualMESlot#drawStackAndOverlay`), so a destroyed
  count is indistinguishable from one item. The encoded pattern was worse: the cell became `Cnt: 0`, i.e. the material
  itself was gone from the pattern. AE cannot help here: its own writes put the real size in the long `Cnt` and write
  `Count: 0` on purpose (`PatternHelper` restores the size from `Cnt` when it reads 0), so the count is only ever
  correct while a stack stays an AE stack - the NEI payload is the one hop that goes through a vanilla `ItemStack`.
  `readCell` settles a missing or zero count on one (a zero-count cell is not encodable) and a cell the inventory has
  no room for is reported instead of silently dropped.
- The import also puts the container into **processing** mode (`ContainerPatternTerm#setCraftingMode(false)`) before
  it writes, the same thing NotEnoughEnergistics does for a processing transfer. A pool page is a machine recipe, so
  it can only become a processing pattern, and while AE's container is in crafting mode its next
  `detectAndSendChanges()` mirrors the first nine cells into the crafting matrix (`copyToMatrix`) - clamping them to
  one and clearing them when they are not items. `PartPatternTerminalEx` already defaults to processing mode, so this
  only guards a terminal the player switched by hand.
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
12. An item **count** never travels in a vanilla `ItemStack` NBT tag: 1.7.10 writes `Count` as a byte and reads it
    back as one, so anything outside -128..127 comes out as a different number (1024 and 4096 both become 0). GTNH
    recipes do use those amounts - the Nano-Scale Foundry one-step circuit pool has 1024-count and 4096-count
    inputs - so any
    client to server transfer of stacks carries the count in its own `long` tag next to the vanilla NBT
    (`PatternImportHandler#writeCell`/`#readCell`, the same thing NotEnoughEnergistics does in
    `ItemUtils#writeItemStackToNBT`/`#loadItemStackFromNBT`). AE's own NBT is not affected: it writes the real size
    into the long `Cnt` and leaves `Count` at 0 on purpose.

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
- Structure: the module slot of the Space Elevator (2 wide x 2 deep x 5 tall, controller on the second layer of
  the first row), built from `SpaceModuleInfinityBase#buildModuleStructure` so the elevator registers the module
  through its own structure check. The module is 2 wide and 5 long, so only the orientation that runs its long
  axis along the slot fits.
- Linkage is `SpaceModuleInfinityBase#isMountedOnFormedElevator()` - `parent != null && parent.getMotorTier() >=
  getNeededMotorTier()`, where `parent` is set by the elevator's `connect(this)` every 20 ticks. The modules no
  longer search their neighbourhood for an elevator.
- `checkMachine` reports the linkage through `checkElevatorLinkage(errors, "V")` and tells the two reasons apart:
  `machine.spacemodule.elevator_not_linked` when no elevator has taken the module in, and
  `machine.spacemodule.elevator_level_too_low` (with the required `MK-<level>`) when it is mounted but the
  elevator's motor tier is short.
- `checkProcessing()`: looks up fluid by `(planetType, gasType)`, validates/deducts wireless EU,
  outputs `fluid * parallel`, 20-tick duration.
- `checkProcessing()` refuses to run while `isMountedOnFormedElevator()` is false, i.e. without a Tier V elevator.
- Textures now use the original pump module front screen + `OVERLAY_SIDE_PUMP_MODULE` engraving.

### Miner port status (latest)
- `SpaceModuleMinerInfinity` now uses the real `IGRecipeMaps.spaceMiningRecipes` pool.
- Structure: the Space Elevator module slot (see the pump section above); hatches are an optional casing plus any
  of Input Hatch / Input Bus / Output Bus / Data Input hatch.
- Recipe lookup applies the same two gates as the native miner: `MODULE_TIER` metadata not above
  `getModuleTier()`, and the asteroid's `minDistance..maxDistance` range covering the effective distance. The
  module is constructed with `tModuleTier = 5`, so the tier gate currently passes every asteroid in the pool
  (the pool's own `minModuleTier` values only go up to 3).
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
- Structure: the Space Elevator module slot (2 wide x 2 deep x 5 tall; see the pump section above), hatches are
  one optional casing plus input bus / output bus / input hatch. `checkMachine` reports the linkage through
  `checkElevatorLinkage(errors, getNeedElevatorLevel())` (the level is `I`..`IV` per MK) and the elevator is
  mounted only while its motor tier is at least `getNeededMotorTier()`.
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
- `MTKillBase` (`common/util`) is the "true kill" ladder behind it - shared, because every kind of protection gives
  up somewhere else, and split into one branch per weapon: `MTPiggyKill` for the piggy, `MTBowKill` for the Voidstar
  Piercer. A branch says only two things: how its kills read (the death message key and the "could not kill" line,
  handed to the base constructor) and whom it refuses (`accepts`, overridden only by the bow). The ladder itself is
  branch-less and `kill()` is `final`; each branch is a singleton (`MTPiggyKill.INSTANCE.kill(...)`).
  `MessTech.MT_LOG.debug("[Piggy] true kill of ... needed step N")` reports which step did the kill, with the
  branch name in the brackets ("Piggy", "Bow"), so a test can be traced in the log.
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
  - **The thrower is not exempt in this branch.** Throwing the piggy straight up and letting it land on your own head kills you,
    which is the whole point of the item; previously the ladder returned early for `target == thrower`. Vanilla's
    `EntityThrowable` already keeps the thrower safe for the first five ticks in air (`entity1 != getThrower() ||
    ticksInAir >= 5`, MC `EntityThrowable.onUpdate` line 196), so a piggy thrown at your feet still does not blow
    up in your face.
  - **A death counts only when the death path of the target ran.** `MTKillBase.init()` (called from
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
    fires again, and the health bar just looks empty. `MTKillBase.repair` therefore drops a NaN (or infinite)
    absorption to zero and a NaN health to zero after every hit, after every forced death and before the removal
    step - this is the exact "0 health, `HealF` NaN" state of the report.
  - If every step fails the thrower gets `messtech.piggy.kill.failed` in chat instead of a silent no-op, and the
    target is healed back to `1.0F` if the ladder left it at zero.
  - **The kill is announced by the piggy.** The damage *type* cannot change (see step 1: the armour sets let exactly
    `infinity` through), and the game derives the death message from exactly that type —
    `DamageSource#func_151519_b` builds the key `death.attack.<type>`. As it is, the piggy would be announced with
    `death.attack.infinity`, the key Avaritia's `DamageSourceInfinitySword` uses for its own sword: translating that
    key in the language files of the mod would relabel every infinity sword kill as a piggy kill. The source therefore
    replaces the *message* instead of the type: the nested `TrueKillDamageSource` of `MTKillBase` overrides `func_151519_b` with a key of
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
  - `tmp/mtkill` is the harness for all of this: it compiles the shipped ladder against mirrors of the
    vanilla damage pipeline (`build/rfg/minecraft-src`) and of the two Witchery halves (`EntityVampire.onDeath` +
    `GenericEvents.onLivingDeath`), and asserts the reported bugs (NaN absorption/health, the unkillable vampire,
    the self hit) as well as the old steps. The mirrors include the chat side
    (`ChatComponentTranslation`, `StatCollector`, `CombatTracker`, `DamageSource#func_151519_b`), so the death message
    is asserted end to end against the real `.lang` files of the mod: its key, its two arguments, the reported Chinese
    sentence, the English one, the fallback of an untranslated key and the fact that `death.attack.infinity` stays
    untranslated.
    **It has not been ported to the branches yet**: it still names `MTTrueKill` and calls the ladder statically, and
    that class no longer exists (`MTKillBase` is abstract now, and the calls are `MTPiggyKill.INSTANCE.kill(...)` /
    `MTBowKill.INSTANCE.kill(...)`). `tools/**` is only touched on request, so it is left exactly as it was; porting
    it means renaming the type and routing the calls through the piggy branch. Its command was
    `javac -d tmp/mtkill/out -sourcepath tmp/mtkill/stubs src/main/java/com/MessTech/common/util/MTTrueKill.java tmp/mtkill/*.java`
    followed by `java -cp tmp/mtkill/out MTKillHarness`.

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


## Chaos Matter (混乱物质) material and the stacked-renderer look
- A normal GregTech material that MessTech registers itself (through GT's `MaterialBuilder`, not through a copy of GT's
  pools), so GT generates the items: `gt.metaitem.01` carries `ingotChaosMatter`, its dusts, plates and gears plus the
  ore dictionary entries, because `MetaGeneratedItemX32` walks `GregTechAPI.sGeneratedMaterials` and honours the
  METAL/DUST/GEAR generation bits the builder sets.
- **Registration timing is the whole trick.** GT runs `Materials.init()` in its own preInit (`GTMod.java:343`), which is
  *before* this mod's preInit, and `IMaterialHandler#onMaterialsInit` is only ever called from inside it
  (`Materials.java:1576`, right after `MaterialsIDMap#register()` handed every GT material its `mMetaItemSubID` and
  before the material list is frozen at `initMaterialProperties()`). `MTChaosMatter.register()` therefore runs from
  the `@Mod` constructor, which FML calls during mod construction - before any preInit has run.
- **The id, with room left for GT.** `MaterialsIDMap` is GT's hardcoded 0..999 table, has no free-id API for addons,
  and `fillGeneratedMaterialsMap()` throws when two materials share an id. `findFreeId()` reads the ids that are
  actually taken (`Materials.getMaterialsMap()` -> `mMetaItemSubID`), looks for the widest run of unused ids inside
  `100..949` (the top of the table is where GT registers its newest materials - Infinity, SpaceTime, the Halkonites -
  and is left alone), requires at least 8 free ids in a row so a lone hole is not squatted, and takes the **middle** of
  that run, leaving GT roughly the same amount of room on both sides. The pick is logged with the run and the counts
  left below and above.
- **The textures live in this mod.** `TextureSet`'s constructors hardcode `Mods.GregTech.resourceDomain` and an addon
  cannot put files into another mod's domain, so `chaosTextureSet()` builds a normal set and then rewrites its
  (public, element-mutable) `mTextures` array with `Textures.ItemIcons/BlockIcons.textureSet("messtech", "chaos", ...)`
  containers. The 16x16 icons are drawn as a grey base with messy multi-colour speckles - the ingot is every alloy and
  element at once - and live in `assets/messtech/textures/items/materialicons/chaos/`; any slot without a file falls
  back to GT's global `NONE` set inside the container, and the `VOID` slots keep GT's shared void icon. GT's metal
  *blocks* use the shared `Textures.BlockIcons.STORAGE_BLOCKS*` arrays, not a material set, so no block art is needed.
- **The look is several of GT's renderers playing at the same time** (`MTChaosMatterRenderer`, attached to
  `Materials.renderer` from the client proxy exactly like `Materials.initClient()` does for GT's own materials). GT's
  material effects fall into four kinds and they combine differently:
  - *geometry* - `TranscendentMetalRenderer` only rotates the matrix (0.3/0.5/0.2, 3.5 degrees per client tick); that
    tumble is copied into `applySpin` (plus a second slower axis) but is **currently disabled**: the call in
    `renderItem` is commented out on request, so the icon is drawn straight and only the effects below animate.
  - *colour modulation* - `GaiaSpiritRenderer` (hue over 180 ticks) and `RainbowOverlayRenderer` (base colour x hue
    over 90 ticks) only set `glColor`; both hues are mixed into the single icon draw, each on its own random period and
    phase (see the playback bullet below).
  - *translucent extra passes* - `InfinityRenderer#renderHalo`/`renderPulse`, `CosmicNeutroniumRenderer#renderHalo` and
    `GlitchEffectRenderer#applyRedGlitchEffect`/`applyCyanGlitchEffect` are public statics in inventory space, so they
    are drawn every frame on top of the icon: genuinely simultaneous.
  - *whole-icon replacements* - `UniversiumRenderer` (a shader over the sprite), `CosmicNeutroniumRenderer` and
    `InfinityRenderer` draw the icon themselves, so two of them cannot be stacked without one hiding the other; the
    renderer plays one of them per window over the base, blended either additively (the "everything at once" burst) or
    normally.
  - *the playback is random, not a rotation* - `rollWindow(tick)` re-rolls everything when a window runs out: the layer
    (never the one that just played), the window length (10..39 ticks), whether the window is an additive burst (35%),
    whether the CosmicNeutronium halo joins in (50%) and when within the window the glitch burst starts (60% chance,
    8 ticks, ghost offset re-rolled every 2 ticks). Both hue cycles also get a random period and phase per session
    (`gaiaPeriod`/`rainbowPeriod`/`gaiaPhase`/`rainbowPhase`), so nothing in the look repeats on a fixed clock.
  This is deliberately the opposite of `MTDynamicItemHelper`, which picks exactly one effect per damage value and
  documents that stacking several renderers on one stack is not supported there.
- The display name is the lang key `Material.chaosmatter` in both lang files, which is what
  `IOreMaterial#getLocalizedName()` looks up (`"Material." + name.toLowerCase()`); `mDefaultLocalName` is only the
  fallback for a missing translation.

## Galacticraft hook: 高速上升自动开星系 GUI (`MTGalacticraftSpaceHandler`)
- What it does: a **player** who crosses the altitude a rocket would leave the atmosphere at, while climbing at
  **256 格/s 或更高**, is shown Galacticraft's celestial selection screen pinned to **tier 0**, i.e. Earth is the only
  destination. Server side only - the screen itself arrives over GC's `C_UPDATE_DIMENSION_LIST` packet.
- **Overworld only:** gated on `player.dimension == ConfigManagerCore.idDimensionOverworld` (GC's own field rather than
  a hardcoded 0, so a pack that moves the Overworld still works). A fast climb on the Moon or in orbit does nothing.
- Where GC does the same thing: `EntitySpaceshipBase#onUpdate` (`posY > IExitHeight#getYCoordinateToTeleport()`, or
  `1200` when the dimension does not implement `IExitHeight`) -> `onReachAtmosphere()` ->
  `EntityTieredRocket#onReachAtmosphere` -> `WorldUtil#toCelestialSelection(player, stats, getRocketTier(),
  MapMode.TRAVEL)`. Only `WorldProviderOrbit` implements `IExitHeight` in GC core, so the Overworld's exit altitude is
  the 1200 fallback. This hook copies that altitude rule verbatim so the two agree - with the Overworld-only gate it
  normally resolves to 1200, but staying a copy means a pack whose Overworld provider does implement `IExitHeight`
  cannot make the two disagree.
- **256 格/s can never be a rocket**, which is why the trigger has to be the player: `EntityTier1Rocket` sets
  `motionY = -d * cos(...)` with `d = min(timeSinceLaunch / 150, 1)`, i.e. at most **1 格/tick = 20 格/s**. Getting a
  player up there with the launch still doing ≥ 12.8 格/tick (256 格/s) at `y = 1200` needs **50 TNT** at their feet
  from sea level (~798 格/s off the pad, 267 格/s when it crosses); **49 falls short at 247.7 格/s**, and 40 is the
  smallest pile that reaches 1200 at all (`tmp/tnt-launch/reach1200.py`).
- **It is a living-player feature.** `Explosion` applies damage and knockback in the same block, so the first of those
  50 TNT already deals 46 damage and kills; the corpse goes on flying with the full impulse, but the handler skips
  players that are not `isEntityAlive()`. In practice the screen therefore shows up in **creative**, where the knockback
  is unchanged and the damage is not taken.
- Why tier 0 means Earth only: `WorldUtil#getPossibleDimensionsForSpaceshipTier` always adds the Overworld and filters
  every planet through `IGalacticraftWorldProvider#canSpaceshipTierPass` - the Moon already needs tier 1.
- Implementation notes: registered from `CommonProxy#preInit` **only when `Loader.isModLoaded("GalacticraftCore")`**
  (GC only reaches this mod transitively through Galaxy Space, so its classes must not be touched otherwise), on the
  **FML bus** (`TickEvent.PlayerTickEvent` is an FML event, unlike `MTProcessHandler`'s Forge-bus `WorldEvent`).
  The trigger is the upward crossing alone - `prevPosY <= exit < posY` with `motionY >= 256 / 20` - which fires once
  per flight without any per-player state. Both things that could fake a crossing move `prevPosY` and `posY`
  *together*: `Entity#setPositionAndRotation` (`Entity.java:1259-1261`) is what a teleport and the client's own
  movement packet (`NetHandlerPlayServer#processPlayer:394`) go through, and neither can leave `prevPosY` below the
  altitude while `posY` is above it. That packet is handled outside the entity tick (`MinecraftServer#tick`:
  `worldserver.tick()` at 696 vs `networkTick()` at 730) while `PlayerTickEvent.END` fires inside `EntityPlayer#onUpdate`,
  so whichever of the two runs first, exactly one tick still sees the crossing.
  `player.motionY` is the server-side impulse the explosion applied to the `EntityPlayerMP`.
- Off switch: `Config.GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI` (category `General`, default `true`).
- **Caveat for non-host players:** `NetHandlerPlayServer#processPlayer` (`build/rfg/minecraft-src`) rejects a reported
  move when `max(|dx|, |motionX|)^2 + max(|dy|, |motionY|)^2 + max(|dz|, |motionZ|)^2 > 100` **and** the player is not
  the single-player host, teleporting them back to `lastPos`. A 12.8 格/tick climb clears that threshold on its own, so
  the host of an integrated server is exempt and the flight runs, while on a dedicated server / LAN guest the correction
  may fight the ascent before it ever reaches 1200. That half is a source reading, not an in-game test. The limit is
  raiseable - see "Vanilla 'moved too quickly' 上限" below.

## Vanilla "moved too quickly" 上限 (`MixinNetHandlerPlayServer_MovedTooQuickly`)
- **那行判定是 Forge 的构建期源码补丁，不是任何模组**：`forgepatches.zip` 里的
  `net/minecraft/network/NetHandlerPlayServer.java.patch` 把原版的 `Math.min` 换成了 `Math.max`（注释
  `//BUGFIX: min -> max, grabs the highest distance`），所以 `processPlayer` 比较的平方距离是"客户端上报位移"和
  "实体自身 motion"**逐轴取大者**（结果见 `build/rfg/minecraft-src/.../NetHandlerPlayServer.java:341-347`，阈值在同一
  方法的 347 行）。Forge 不为它提供任何开关，本地 528 个 GTNH 模组 jar 里也没有别的模组改这一行。
- 阈值由本 mod 的 late mixin `MixinNetHandlerPlayServer_MovedTooQuickly` 抬高，配置项
  `Config.MOVED_TOO_QUICKLY_THRESHOLD`（category `General`，**默认 `2000.0`**）。**只升不降**：
  `configured > original` 才返回配置值，所以设成 100 及以下就退回原版行为，等于功能关闭。
- **和 Hodgepodge 共存而不是二选一**：Hodgepodge 的 `MixinNetHandlerPlayServer_DisableMovedTooQuickly` 用同一条
  `@ModifyConstant(method = "processPlayer", constant = @Constant(doubleValue = 100.0D, ordinal = 0))`，且只在
  `FixesConfig.movedTooQuicklyThreshold > 100.0` 时才应用（配置在 `config/hodgepodge.cfg` 的 `[fixes]` 段，键
  `D:movedTooQuicklyThreshold`）。两个 `@ModifyConstant` 打在同一常量上是**串接**而不是冲突——会冲突的是
  `@Redirect`/`@Overwrite`，那种情况游戏直接起不来。因此最终上限 = max(原版 100、Hodgepodge 的值、本 mod 的值)；
  本 mixin 用 `priority = 1500`（Hodgepodge 是 Mixin 默认的 1000）保证后应用，能在 Hodgepodge 的结果上继续抬。
- 配置值是在 `processPlayer` **运行时**读的，而不是在 mixin 收集时用 `setApplyIf` 判定，所以与 GTNHMixins 何时收集
  late mixin 列表无关（本 mod 的配置要到 `preInit` 才读，而那之前静态字段还是各自的初始值）。
- 默认值的锚点：50 个 TNT 在脚下起爆瞬间 |motionY| = 50 x 0.7975 = 39.875 格/tick → 平方 1590.016，这是整段爬升里
  检查看到的**最大值**（只在起飞那一 tick 出现，之后单调衰减，飞到 y=1200 时只剩 ~13.4 格/tick）。默认 `2000.0`
  = 44.72 格/tick，比所需的 1590 多约 26% 冗余，约等于 56 个 TNT；`1.7976931348623157E308` 是彻底关掉。
  判定在**服务端**，所以生效的是服务端那份配置。

## `/messtech GoUp [秒数]` (`MTCommand` / `MTGoUpFlight`)
- 指令本体 `MTCommand`：根指令 `messtech`，`GoUp` 是唯一的子命令，参数是秒数（默认 30）。权限等级 2（OP），
  发送者必须是玩家（没有目标选择器，控制台没有可抛的对象）。注册在 `CommonProxy#serverStarting`
  （`FMLServerStartingEvent#registerServerCommand`），飞行处理器在 `preInit` 里 `MTGoUpFlight.init()`。
- 效果：把玩家以**固定速度 14.0 格/tick = 280 格/s** 抛向空中，持续 N 秒（默认 30 s = 600 tick）。不是"给一次冲量"，
  而是**每 tick 重设 `motionY` 并推进位置**，所以重力和阻力都改不了它。
- 为什么是 14.0：钩子的线是 256 格/s = 12.8 格/tick（`MTGalacticraftSpaceHandler`），14.0 超过去；即使被原版物理衰减
  一 tick（`(14 - 0.08) * 0.98 = 13.64` 格/tick = 272.8 格/s）仍 > 12.8，所以两个 `PlayerTickEvent` 处理器谁先谁后
  都不影响判定；而它的平方 196 又远低于 `MOVED_TOO_QUICKLY_THRESHOLD` 的默认 2000。到 y=1200 约 82 tick（4.1 s），
  默认 30 s 绰绰有余（30 s 大约到 8400 格）。
- **为什么必须 `setPositionAndUpdate` 而不能只设 `motionY`**：1.7.10 里玩家位置由客户端权威
  （`NetHandlerPlayServer#processPlayer` 最后会 `setPositionAndRotation(客户端上报位置)`），服务端单方面设 `motionY`
  会被下一个 `C03PacketPlayer` 顶掉。`EntityPlayerMP#setPositionAndUpdate` → `setPlayerLocation`
  （`NetHandlerPlayServer.java:443-451`）会移动服务端实体、把 `hasMoved` 置 false（于是本次上报的客户端位置不被采用，
  浮空踢人检查也走不到）并回发 `S08PacketPlayerPosLook` 钉住客户端。
- S08 的 Y 特意写成 `y + 1.62`，正好抵消客户端 `posY → boundingBox.minY` 的 1.62 偏移，所以客户端下一包上报的 Y 会
  落在服务端 `lastPosY` 上（`processPlayer` 的 `hasMoved` 追赶判定要求 `Δy² < 0.01`），`hasMoved` 于是每 tick 都能
  重新变 true —— **移动包路径照常运行，钩子依赖的 `PlayerTickEvent` 也就照常触发**，这正是指令存在的目的。
- 处理器挂在 `TickEvent.ServerTickEvent` 的 END（世界与移动包都处理完之后，位置以它为准），并会跳过已死亡、世界为空
  或已登出的玩家，避免 map 里长期持有实体。
- 与配置的耦合：14 格/tick 的平方是 196，**高于原版的 100**，所以这个指令本身就依赖 `MOVED_TOO_QUICKLY_THRESHOLD`
  被抬高（默认 2000 满足）；配置低于该值时指令会额外发一条警告（`command.messtech.goup.threshold.low`），
  而不是让玩家莫名被拉回去。
- 副作用提醒：30 s 飞完玩家在约 8400 格高处，掉下来要很久，落地/掉出世界都按原版规则处理。

## 混沌终焉·虚无贯星弓 (`MTItemVoidstarBow` / `MTEntityVoidstarArrow`)
- Name: `混沌终焉·虚无贯星弓` / `Chaos Finale: Voidstar Piercer` (`item.messtech.voidstarBow.name`), registry name
  `VoidstarBow`, unlocalized `messtech.voidstarBow`, creative tab `tabCombat`, **max stack 1**, durability
  1,024 (`MTItemVoidstarBow.MAX_DAMAGE`, i.e. under three stacks of arrows).
- It is a plain `ItemBow` in everything but its **draw length** and its **sub-modes**: the `f < 0.1` minimum draw that
  does not fire, arrow consumption, the Infinity enchantment that replaces it, Power / Punch / Flame and the
  enchantment table are all vanilla's. Only the projectile, the charging effect, the icons, the glint, the draw
  length and the two sub-modes are this mod's.
- **Sub-modes (`MTItemVoidstarBow.MODE_PIERCE` / `MODE_DE`), switched with sneak + right click.** The mode lives in
  the stack's own NBT (`MTVoidstarMode`), so two bows can be in different modes and it survives a relog; anything
  missing or out of range reads as `MODE_PIERCE`, i.e. a bow that has never been switched behaves exactly like it did
  before the modes existed. The switch is `onItemRightClick`: it **must not fall through to `super`** (vanilla's
  `ItemBow` starts the draw in that very method, so a switch that did would fire an arrow when shift is released),
  both sides return early so neither starts a use, and only the server writes the mode. The chat message is a
  `ChatComponentTranslation` rather than a translated string, so the key travels to the client and each player reads
  it in their own language; the keys are `messtech.voidstarBow.mode.pierce` / `.mode.de` and the tooltip shows the
  current one through `messtech.voidstarBow.tooltip.mode` + `.modeName.*`. A block with its own right-click GUI takes
  the click before the item does (`ItemInWorldManager#activateBlockOrUseItem` calls `onBlockActivated` first) - that
  is the platform, the same in every GTNH tool, so switch the mode while aiming at air.
- **DE mode's one explosion.** In `MODE_DE` the arrow's first block contact calls `MTExplosionDE` (the port of DE's
  reactor explosion, see the reactor section) centred on the block it touched, with
  `Config.VOIDSTAR_BOW_DE_POWER` power - default **20**, which is DE's own full reactor
  ({@code 2 + fuelFraction * 18}, i.e. 2 for empty and 20 for full), i.e. a 200 block radius - and then removes
  itself. None of the tunnel machinery runs: no piece is blasted and no block is drilled, which is the whole
  difference to pierce mode. A shot that hits nothing at all still detonates when its flight ticks run out (the arrow
  must not simply vanish after 600 blocks); an entity hit is unaffected by the mode and still goes through
  `MTBowKill`.
- **The draw is `Config.VOIDSTAR_BOW_DRAW_TICKS` ticks, 80 by default, i.e. four seconds** where vanilla's bow takes
  one. `onPlayerStoppedUsing` keeps vanilla's own curve (`f = (f*f + 2f)/3`, the `f < 0.1` floor that does not fire
  and the cap at 1) and only divides by the configured length instead of vanilla's hardcoded `20.0F`, so the arrow's
  speed (`1.2 + 1.8 * draw`, see the arrow below), the `isCritical` mark at a full draw and the magic circle's unfold
  all stretch with it: the minimum draw that fires at all is the same fifth of the charge, about 0.7 seconds. The
  four seconds are the number the charge effect was designed around - the Yes Steve Model pack it was copied from
  spreads its own unfold over four seconds. **One pose does not follow it**: vanilla's first person bow pose ramps
  over twenty ticks in `ItemRenderer` line 466 (`f11 = f10 / 20.0F`), a hardcoded constant of the client renderer
  that only a coremod could change, so a four second draw finishes its arm movement after the first second and keeps
  charging after that. The config file and the class comment both say so.
- **Icons** (`assets/messtech/textures/items/bow/`): `voidstarBow_standby` plus `_pulling_0/1/2`, each a
  **16 x 256** PNG, i.e. a strip of 16 frames of 16x16 with `{"animation": {"frametime": 2}}` as its `.mcmeta`
  (32 ticks per loop). Animated icons in the item atlas work in 1.7.10; this is the same mechanism as the animated
  piggy strips (see "A Piggy + MTDynamicItemHelper"). The frame alpha of all four strips is bit-identical to the
  corresponding vanilla `bow_standby`/`bow_pulling_N.png` (64/64 frames checked), so the bow is exactly as big as
  vanilla's and only its colours move.
  - The art is the vanilla bow recoloured into a void-metal ramp (`#2a1140` to `#a462e8`, keeping vanilla's own
    shading structure) with a cyan-to-white energy string and a white-lilac shimmer travelling along the arc once
    per 16-frame loop, plus a void-glow pulse that completes two cycles over the same loop, so frame 15 -> frame 0
    does not jump. Generator: `tmp/chaosbow/gen_voidstar_textures.py` (deterministic; reads the vanilla icons out of
    the 1.7.10 client jar). Review sheet: `tmp/chaosbow/preview_voidstar.png`.
  - **The pull frames can only come from the Forge hook.**
    `EntityPlayer#getItemIcon` (line 2051 in `build/rfg/minecraft-src`) selects the draw frame only for
    `itemStackIn.getItem() == Items.bow` - an identity check a subclass never passes - and otherwise falls through to
    `itemStackIn.getItem().getIcon(itemStackIn, pass, this, itemInUse, itemInUseCount)` (line 2070), i.e. Forge's
    player sensitive `Item#getIcon(ItemStack, int, EntityPlayer, ItemStack, int)`. `MTItemVoidstarBow` overrides
    exactly that, with vanilla's own cut-offs: >= 18 ticks drawn is `_pulling_2`, > 13 is `_pulling_1`, > 0 is
    `_pulling_0`. Those two cut-offs are kept as *fractions* of the draw (90% and 65%) and applied to
    `Config.VOIDSTAR_BOW_DRAW_TICKS`, so the three frames spread over the whole four second charge rather than over
    the first second. Because the state the hook is handed is the *player's* (`itemInUse`), the pull animation also shows
    for **other** players: `EntityOtherPlayerMP#onUpdate` (lines 86-96) rebuilds `itemInUse` from the "eating" flag
    that `EntityPlayer#setItemInUse` sets server side (`EntityPlayer.java:2183`), which is what vanilla's own
    third-person bow pose relies on as well.
- **The glint**: `Item#hasEffect(ItemStack)` is overridden to `true`, so vanilla draws the purple enchantment glint
  over the bow wherever it is rendered (`ItemRenderer.java:132` for the item in hand,
  `RenderItem.java:561` for the inventory), whether it is enchanted or not. The animated strip is the static half of
  the "enchanted" look, the glint the moving one.
- **The magic circle** (`MTMagicCircleRenderer`, client only, registered on `MinecraftForge.EVENT_BUS` from
  `ClientProxy#preInit`): textured quads in world space, drawn in `RenderWorldLastEvent` - **not** a particle. The
  first version was a ring of rune particles, which read as a dotted circle; the quads are the circle itself.
  Its construction is copied from the bow charge effect of the Yes Steve Model pack `wine_fox / 05_magical`
  (`animations/arm.animation.json` -> `use_mainhand:bow`, the `ysmGlowdamofazhen` / `ysmGlowmofazhen*` bones of
  `models/main.json`, sprites at `191,191` and `161,50` of `textures/magic.png`). That pack is a *model*, not a
  renderer, so what is copied is its design, rebuilt as world space quads:
  - **Two motifs.** `misc/magicRing.png` is the big circle (a ring of 24 segments, an inscribed square with corner
    marks, an inner circle), `misc/magicFlower.png` is the small one (a four pointed compass flower with four short
    diagonal petals). Thin violet line art, drawn by `tmp/chaosbow/gen_voidstar_ysm_circles.py` (256x256, 2x
    supersampled). The pack's own pixels are another author's art under CC BY-NC-SA: same motifs, **not** the same
    file - do not paste `magic.png` into this repo.
  - **Every circle is two quads.** The pack's small circle bones carry a 27 unit plane and a 16.2 unit plane - 60% -
    turned 45 degrees around its own centre (`INNER_RATIO` / `INNER_ANGLE`). That turned inner copy is what makes it
    read as a magic circle rather than as a decal, and every circle here has one.
  - **One big circle with four around it.** The pack parents four circle bones to the big one, nine units off its
    pivot, i.e. `1.33` big radii (`CORNER_DISTANCE`), each `0.70` big radii across (`CORNER_RADIUS`). Being children
    they are carried **around** it by its own spin, which is reproduced by turning the corner positions with the big
    circle's angle.
  - **The spin rates are the pack's own**, in degrees per second: the big circle 18 (`BIG_SPIN_PER_SECOND` - its keys
    are 0 -> 90 degrees over five seconds), the small ones 180 (`SMALL_SPIN_PER_SECOND`), alternating direction.
  - **The unfold is the pack's too.** Every small circle **bursts**: its scale keys start it at 3 and 4, collapse it
    to 0 and then settle it on its resting value (`burstScale()`, `BURST_SCALE` / `BURST_COLLAPSE`), and the pack
    slides them along the weapon axis while they do it (position keys like `0,0,38` to `0,0,-24`), which
    `AXIAL_FLIGHT` brings back. The corners start at 10%, 17%, 24% and 31% of the draw and settle over the following
    35% each; the big circle joins at 62% (`BIG_FIRST`) and flies forward out of the bow as it grows (`BIG_GROWTH`),
    the way its own position keys do (`0,0,-42` to `0,0,13`).
  - **The card** hangs `1.90` blocks in front of the eyes (`DISTANCE`, lowered `0.25` below the eye) in the plane
    perpendicular to the player's `getLook(partialTicks)`, built from `right = normalize((-fz, 0, fx))` and
    `up = right x forward` (a player looking straight up or down has no such right vector and falls back to the
    world's x axis). Two batches, one per sprite, with the depth mask off and the depth test left on, so walls hide
    the circle exactly like they hide the arrow.
  - **The blending is the pack's**: plain alpha (`GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`, its `entityTranslucent`
    render type), not the additive of the first version, and with culling off so a circle shows from both sides. The
    one deliberate deviation is `setBrightness(0xF000F0)`: the pack leaves those bones to the world light, while a
    circle the player summons should glow in a cave instead of turning grey.
  - Everything is a pure function of the draw (`getMaxItemUseDuration() - getItemInUseCount()`, divided by
    `Config#VOIDSTAR_BOW_DRAW_TICKS` - four seconds, i.e. 80 ticks), so the animation can never drift out of step
    with the bow: at a full draw it is one circle in the middle, four in the diagonal corners around it, all of it
    still turning. The four-second draw is what makes the unfold read exactly like the pack's: the corners burst at
    0.4 / 0.7 / 1.0 / 1.2 seconds and settle by 2.6, the big circle joins at 2.5 and is complete at 3.7.
- **The arrow** (`MTEntityVoidstarArrow`, entity id 1, registered in `CommonProxy#preInit`, rendered with vanilla's
  `RenderArrow` by `ClientProxy#preInit`):
  - **Straight flight.** The launch direction is the shooter's `getLook(1.0F)` and its spread is zero (the vanilla
    `setThrowableHeading` jitter of up to 0.0075 per axis is not applied); the speed is `1.2 + 1.8 * draw` blocks per
    tick. The motion is written back from that aim **at the start and at the end of every tick**, so the two things
    vanilla's arrow does at the end of its own tick - `motion *= 0.99` and `motionY -= 0.05` (`EntityArrow.java`
    lines 454-457) - never reach the next move. A full draw therefore travels exactly 3.0 blocks per tick along the
    line the crosshair points at, with no drop at all; writing it before the move is what makes the flight straight,
    writing it after is what keeps the motion constant for `EntityTrackerEntry` (a motion that changed every tick
    would be re-sent as a velocity update every tick).
  - **It drills on instead of exploding where it hits.** The contact itself is recognised exactly as before -
    vanilla's own ray trace stops the arrow against a block and its entity scan kills the arrow on a mob, so a mob in
    front of a block is still hit first - and the tick of a block contact is the tick that advanced by less than the
    arrow's own speed (`advance < speed * 0.995`; vanilla backs the arrow off by 0.05 blocks, i.e. 4.2% of `MIN_SPEED`
    and 1.7% of `MAX_SPEED`, while an open tick advances by exactly the speed because `applyAim` overwrote the
    motion). On that tick `onBlockContact` blasts the piece of tunnel between the last blast and the face in front of
    the arrow (`World#func_147447_a` from where the arrow sits along its own aim) and **does not remove the arrow**:
    the block vanilla stuck it into is gone, so vanilla's own in-ground branch finds a different block on the next
    tick, clears its in-ground flag and releases the arrow, which flies on with the aim written back. Every contact
    therefore extends the same tunnel and one shot fired into a mountain carves a corridor through it until
    `MAX_FLIGHT_TICKS` (200 ticks = 600 blocks at a full draw) runs out.
    - The pieces are handed out back to back: `blastX/Y/Z` starts at the muzzle and is moved to the face of every
      contact, so no block is ever scanned twice (the tunnel is only ever blasted forwards).
    - `stuckTicks` counts the ticks the arrow spent held up by blocks; above `MAX_STUCK_TICKS` (6) the flight ends
      with the bang (`finish`) instead of the arrow sitting in a wall for the rest of its life. Two of those ticks
      always belong to a contact that *did* go through (the tick vanilla stopped the arrow, and the tick it releases
      itself again), so a bedrock wall - or a protection plugin, or an explosion resistant block - is what the limit
      is for.
    - A tick in which vanilla *bounced* the arrow (an entity that refused the damage, e.g. a player in creative mode:
      the motion ends up pointing backwards) is an entity hit, not a block one, and it is handled by the bow's kill.
  - **The client does not know the aim: it takes it from the velocity the server sends.** `MTEntityVoidstarArrow`
    implements FML's `IThrowableEntity` for exactly that reason - `FMLMessage.EntitySpawnMessage#toBytes` only writes
    the velocity into the spawn packet for an `IThrowableEntity` (the `instanceof` branch next to the data watcher
    stream) and `EntitySpawnHandler#spawnEntity` reads it back and calls `setVelocity`, while vanilla's own
    `EntityArrow` is not one. **Before that interface was there, this was a real bug in game**: the freshly created
    client copy of the arrow had a motion of zero, `applyAim` wrote this class's *default* aim into it - `(0, 0, 1)`,
    i.e. due south and perfectly level - and the copy flew off sideways at three blocks per tick until the position
    updates of the server dragged it back onto the real line. That reads as a curving arrow (a model that points
    away from the aim for a moment and then swings onto it), which is what a report of "the arrow flies flat and to
    my right, then corrects" was. `adoptSyncedAim` now takes `aim`/`speed` from the client's own motion at the start
    of every tick, and the tick before the first velocity arrives (`MIN_AIM_LENGTH`, at most the first tick) holds
    the orientation the spawn packet brought instead of letting vanilla recompute a rotation from a motion of zero -
    `atan2(0, 0)` is 0, i.e. yaw 0 and pitch 0.
  - **A hit on an entity kills it with the bow's true kill.** Vanilla's hit (damage, knockback, the sound) happens
    first inside `super.onUpdate`, and the arrow is then dead - which is how the entity hit is recognised at all,
    since a block contact never kills the arrow any more. The target is found again by `findHitEntity`, which repeats
    exactly the scan vanilla ran a moment ago on the same step: candidates in the box around the step, every box
    expanded by `0.3` blocks, `AxisAlignedBB#calculateIntercept`, closest intercept wins, the shooter excluded. The
    kill is `MTBowKill.INSTANCE.kill(victim, shooter, this)`, i.e. the same branch of the `MTKillBase` ladder the
    tunnel uses (see below), so the two kill paths of this weapon cannot drift apart. A bounce counts as a hit too:
    the ladder does not care that the entity refused the damage.
  - The arrow is never pickable (`canBePickedUp = 0`). Aim, speed, muzzle, the last blast point and the stuck
    counter are saved to NBT, so an arrow saved mid-air by a chunk unload resumes on the same line and does not blast
    its own tunnel a second time.
- **The tunnel** (`MTExplosionTunnel#blast`, server side only; the client learns about it from the ordinary block
  change packets and from the deaths): every block whose centre is within `Config.VOIDSTAR_BOW_TUNNEL_RADIUS`
  (default **6.0**, i.e. a thirteen by thirteen hole; the minimum 0.5 is the single column of blocks the line passes
  through, 1.5 is three by three and 2.5 is five by five) of the blasted piece is destroyed, and every living thing
  within the same radius of it is killed by the bow's branch of the true kill ladder, `MTBowKill` (see below).
  - The line is walked in one block steps and every step only opens a cube of `ceil(radius)` around the point it
    reached - not the bounding box of the whole segment, which for a 128 block flight fired at 45 degrees would be a
    90 x 90 x 90 box (729,000 blocks) scanned to destroy a few hundred. Half a block of slack per step is what makes
    `ceil(radius)` enough to cover a block whose centre can be within the radius.
  - Survives: air, fluids (vanilla explosions do not destroy them either), anything with
    `getBlockHardness < 0` (bedrock, the portal, GT's unbreakable blocks) and anything with
    `getExplosionResistance > 100` (obsidian is 600, stone 30, GT machine casings 10-20, so "everything a player can
    mine, nothing that is meant to be blast proof"). **Nothing is dropped** - the tooltip says so out loud.
  - **The living things inside the tunnel are killed by `MTBowKill`, the bow's branch of `MTKillBase`.** Both kill
    paths of the weapon - a direct hit (`onEntityHit`) and this sweep - go through that one branch, so they can never
    behave differently. What the ladder does is described in the piggy's section above: the plain `Float.MAX_VALUE`
    hit with the `"infinity"` damage type first (which is what walks past the armour sets that cancel the events of
    every other type), then armour piercing and creative mode, then the fire hit against death gates that only open
    for fire, and finally zero health plus `onDeath` by hand, which no damage cap or invulnerability flag can
    intercept. A target that survives the whole ladder is handed back alive (`restore`) and the thrower is told so
    with `messtech.voidstarBow.kill.failed`.
    - **A previous version of this weapon did not use the ladder.** It "killed" its targets with the overheal
      exploit: `heal(1 << 31)` - the amount the item's tooltip used to quote - which on a classpath without Forge's
      heal patch is the instant kill of vanilla's Instant Health potion, and which this mod then had to follow with
      a hand written `setHealth` and `onDeath`. Two platform facts were behind that: Forge patched the vanilla half
      away (`EntityLivingBase#heal`, lines 795-805, starts with
      `amount = ForgeEventFactory.onLivingHeal(this, amount); if (amount <= 0) return;`, so a wrapped heal does
      nothing) and 1.7.10 only runs `onDeath` from inside `attackEntityFrom` (`EntityLivingBase.java:931-941`), so
      zero health alone is a walking corpse. **That class (`MTOverheal`) is gone**: the ladder reaches everything the
      overheal reached and more, because it does not skip the damage pipeline of the target when the target can be
      killed through it.
    - The bow's branch overrides `accepts` to refuse the shooter: the tunnel starts at the muzzle, which is inside
      the player who fired, so without that rule every shot would kill its own archer. The tunnel pass skips
      `victim == source` as well, and the arrow's `findHitEntity` excludes the shooter, so the rule is enforced
      three times over - but only the branch's copy is the one the weapon depends on.
    - The two messages of the branch are `death.attack.messtech.voidstar` (`%1$s` is the victim; the archer is
      deliberately not named) and `messtech.voidstarBow.kill.failed`. They are the branch's whole reason to exist:
      they are handed to the base constructor by `MTBowKill`, and they are what keeps a bow kill from being announced
      with the piggy's line.
    - An entity's own size counts as well as its position: the reach is `radius + max(width, height) / 2`, so a
      horse or a boss whose centre sits outside the tunnel is still caught when half of it is inside.
    - Anything already dead or dying (`isDead` or health <= 0) is skipped, so the ladder is not asked to kill a
      corpse (an entity at zero health whose death path never ran cannot be killed by a second call either - the
      ladder's own `isDead` would not see a completed death).
  - **Letting go heals the archer to full** (`Config.VOIDSTAR_BOW_HEALS_SHOOTER`, default **true**,
    `MTItemVoidstarBow#onPlayerStoppedUsing`). It is `player.heal(max - current)`, i.e. a real heal of the
    difference rather than a health assignment, so the game's heal path runs (the Forge heal event and healing
    effects included) and the archer can never be pushed past their own maximum. It happens in the same
    `!world.isRemote` branch that spawns the arrow, so a shot that does not happen - a draw too weak to fire, no
    arrow in the inventory - heals nothing.
  - A shot fired by a player posts the standard `BlockEvent.BreakEvent` per block and leaves it standing when a
    protection mod cancels it; the vanilla "block destroyed" effect (`World#playAuxSFX` id 2001, what `Explosion`
    sends per block) is only sent for blocks within 12 blocks of the impact, because one packet per block over a
    long tunnel is the thing that makes big explosions lag.
  - A blasted piece longer than `Config.VOIDSTAR_BOW_TUNNEL_LENGTH` (default **128** blocks) keeps its far end and
    drops the near one: the wall the arrow is pressed against is what the player is looking at. The cap is per
    piece, so it only ever applies to a shot that flew further than that before meeting its first block; the pieces
    of the drilling that follow are a few blocks long each.
  - `Config.VOIDSTAR_BOW_BREAKS_BLOCKS` (default **true**, category `VoidstarBow`) turns the whole block destruction
    off; the arrow is then still straight and still trails, it just stops at the wall. The two passes of the tunnel
    have independent switches: `Config.VOIDSTAR_BOW_ANNIHILATES_LIVING` (default **true**) turns the true kill of the
    living things off, so a server can have the tunnel without them, or the kills without the block damage. A third
    switch, `Config.VOIDSTAR_BOW_HEALS_SHOOTER` (default **true**), turns the heal the archer gets on release off.
- **Particles** (`MTEntityFXVoidstar`, `MTVoidstarFx`): the trail spawns three sparks along the step the arrow just
  took (at 3 blocks per tick, one per position would be a dotted line), every drilled block gets a small burst at the
  face, and the end of the flight gets the big one - a flash at the wall, sparks through the whole tunnel (the muzzle
  position travels in data watcher ids 20/21/22, which is why every client can draw the burst locally although only
  the server destroys blocks) and vanilla `largeexplode`/`smoke` particles.
  - One sprite is left, `messtech:particle/voidstarSpark`, registered **into the item atlas** by the bow's own
    `registerIcons`, and the particles use `getFXLayer() == 2`, which is the one atlas
    `EffectRenderer#renderParticles` binds for a custom icon: layer 0 is the vanilla particle sheet, and
    `EntityFX#setParticleIcon` throws for it. Layer 2 is bound to `TextureMap.locationItemsTexture`
    (`EffectRenderer.java` lines 127-144).
  - They are full bright (`getBrightnessForRender` returns `0xF000F0`, the value vanilla's spell particles return),
    so the trail glows in an unlit cave instead of turning into grey dots. `noClip` is on: sparks hang in the air in
    a tunnel that is being dug and must not be stopped by the blocks around them.
  - The drilled block's burst is deliberately cheap (7 sparks): a shot into a mountain triggers it every few ticks
    for as long as the arrow digs.
- **The name wears the `STARFIELD` tooltip animation** (`MTStarfieldText` + `MTStarfieldTextRenderer`, both new):
  - `MTStarfieldText.frame` colours character `i` with
    `PALETTE[(i + floor(millis / 75)) mod 12]`, i.e. neighbouring characters wear neighbouring colours (the
    gradient the player reads as a flow) and the band advances one character every 75 ms. The palette is
    pink - purple - blue - dark aqua - aqua - white and back through a red and a gold, so every pass has a white
    core with a night sky on one side and an ember on the other. Only colour codes are emitted - never bold, which
    would move the advance by a pixel per character and pump the tooltip box (see
    `MTModuleProjectTextRenderer` for that trap).
  - `MTStarfieldTextRenderer` **replaces** the line (`replacesText()`): it draws the frame itself with the font and
    then, on the plane of the tooltip box (`zLevel` 300, through a `Gui` subclass),
    - one or two **twinkling stars per glyph**: the position comes from an avalanche hash of the character index, so
      a star never leaves its pixel, while its blink phase comes from the same hash and a 260 ms clock, so the
      neighbours twinkle independently; the brightest step also gets a four pixel cross,
    - and a **shooting star** crossing the whole line from the upper left every 3.6 s, visible for the first 35% of
      that period, with a fading tail.
  - Glyph geometry (where a character starts, how wide it is) always comes from the `FontRenderer`, never from a
    hand sum, so a resource pack with different letter widths keeps the stars on their letters.
- **`MTAnimatedTooltipHandler#addItemNameAnimation` is the new registration shape this needs.** A tooltip's first
  line is the item's display name, drawn by vanilla before any handler sees the tooltip, so an animation cannot be
  attached by adding a line. The handler recognises the line a renderer belongs to by its **visible text**
  (`rendererFor`), so an entry whose supplier answers with the item's name is enough - it must simply not be
  appended to the tooltip, which is what the new `Entry#drawn` flag is for (`addItemTooltip`/`addAnimatedText` entries
  are drawn, name entries are not). `ClientProxy#preInit` registers `MTStarfieldText.INSTANCE` with the lang key as
  the supplier, so a resource pack rename follows.
  - **The registration has to use `OreDictionary.WILDCARD_VALUE` as the stack's damage, and that was a real bug.**
    The first version registered `new ItemStack(MTItems.voidstarBow)`, i.e. damage 0. The damage of a damageable stack
    *is* its durability damage, `ItemStackMap.actualDamage` is `stack.getItemDamage()` and the handler's map is keyed
    by it - so the effect worked on a freshly spawned bow and stopped working the moment the bow had been fired once,
    which is exactly what "the rainbow effect does not show up" looked like in game. With `WILDCARD_VALUE` on the
    NBT-insensitive map (`ItemStackMap(boolean NBTSensitive)` with `false`, the constructor the handler uses)
    `DetailMap#getKeyType` answers `WildcardAll` - `isWildcard(damage)` and then `!NBTSensitive` -> `withWildcardTag`
    - which lands the entry in the map's single `wildcard` field, and `DetailMap#get` returns that field **before**
    looking at damage or NBT at all. Every damage, every enchantment and a renamed bow are therefore covered, which
    is the same wildcard `MTItemPiggy`'s own registration uses.
  - The other half of the tooltip plumbing was checked while hunting that: NEI does post gtnhlib's
    `RenderTooltipEvent` (`codechicken/nei/util/RenderTooltipEventHelper`, called from `GuiContainerManager` line 633)
    and honours `alternativeRenderer` (line 648), and the marker NEI appends to the first line
    (`GuiDraw.TOOLTIP_LINESPACE = "\u00a7h"`) is a section code, i.e. `MTTextAnimation.visibleText` strips it and the
    name still matches. Neither was the problem.
- **Limitation**: the magic circle reads the use state of the player on the client that draws it, so *other* players
  do not see the circle of somebody else drawing the bow (1.7.10 does not sync item use to other clients - the vanilla
  bow pose has the same hole). The arrow, its trail, its bursts and the tunnel are unaffected - those travel with the
  entity and every client simulates them.
- Verification: `gradlew compileJava`, as always; the texture generators self-check their own output
  (`tmp/chaosbow/gen_voidstar_textures.py`, `tmp/chaosbow/gen_voidstar_ysm_circles.py`) and the shell loops are not
  part of the build. The Yes Steve Model pack the circle's design was read from is not a dependency: the layout,
  timing and spin rates above were read out of its JSON files, and `tmp/chaosbow/inspect_ysm*.py` are the throwaway
  scripts that read them.

