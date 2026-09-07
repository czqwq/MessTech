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
- Item list enum: `com.MessTech.common.misc.MTItemList` (GT-style ItemList pattern).
- Machine IDs: MTDTPF = 32400, MTComputingCenter = 32401, MTHatchRack = 32402, MTAssFactory = 32403.
- Language files: `src/main/resources/assets/messtech/lang/{en_US,zh_CN}.lang`.

## Machine base hierarchy

```
MTMultiMachineBase<T>
├── MTGeneratorMultiBase<T>
├── MTWirelessMultiMachineBase<T>
│   └── ParallelismAcrossMultiMachineBase<T>
└── CalculateMultiMachineBase<T>
    └── MTComputingCenter
```

### MTMultiMachineBase
- Generic multi-block base extends GT `MTEExtendedPowerMultiBlockBase`.
- Provides standard `ProcessingLogic` setup, machine-mode switching, Waila NBT/body.
- No wireless code anymore (moved to `MTWirelessMultiMachineBase`).

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
- Independent Assembly Line recipe map: `MTRecipeMaps.assFactoryAssemblyLineRecipes` populated as
  **real recipes** (not fake) and re-tagged to own default `RecipeCategory`.
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
  declared as `maxIO(48, 1, 18, 0)` and analyzed by `tools/analyze_nanoscale_io.py`.
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

## Known pending / open items

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
