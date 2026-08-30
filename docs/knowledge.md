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
- Language files: `src/main/resources/assets/megatech/lang/{en_US,zh_CN}.lang`.

## Machine base hierarchy

```
MTMultiMachineBase<T>
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
- NBT saves/loads whitelist flag + filter inventory.
- Remaining relative to original: full asteroid info/calculator panels and 64-slot filter grid (GUI
  currently uses a compact 8-slot filter).
