# MessTech Repo Notes

This file records knowledge about **MessTech's own code** (architecture, conventions, and past fixes).
GT5U-internal knowledge is kept in `GT5U-NOTES.md`.

## Machine base hierarchy

```
MTMultiMachineBase<T>
├── MTWirelessMultiMachineBase<T>          (wireless network support)
│   └── ParallelismAcrossMultiMachineBase<T> (cross-recipe parallelism)
└── CalculateMultiMachineBase<T>           (computation multiblock base)
    └── MTComputingCenter
```

### `MTMultiMachineBase`
- Generic MessTech multi-block base extending GT's `MTEExtendedPowerMultiBlockBase`.
- Provides the standard `ProcessingLogic` setup, machine-mode switching, Waila mode NBT/body, and shared helpers.
- It intentionally does **not** contain wireless code anymore; wireless machines use `MTWirelessMultiMachineBase`.

### `MTWirelessMultiMachineBase`
Extracted from the old `MTMultiMachineBase` wireless region + `MTDTPF`'s wireless mechanism.
- State: `ownerUUID`, `EnableWirelessFunc`, `EnableWireless`, `wirelessParallel`.
- Lifecycle: `startRecipeProcessing()` / `endRecipeProcessing()` / `isRecipeProcessing`.
- Wireless cost: `costingEU` / `costingEUText`, Waila NBT/body for `无线模式` / `耗电`.
- Safe hooks:
  - `checkWirelessPower(eut, duration, maxParallel)` — simulate before consuming.
  - `validateWirelessPowerForRecipe(...)` — shared recipe validation (check balance first).
  - `startWirelessRecipe(...)` — deduct power first, then consume inputs.
- Generic wireless processing loop: `checkProcessingWirelessLoop()` + `wirelessModeProcessOnce()`.
- Hooks: `prepareProcessing()`, `getExtraEUCostMultiplier()`, `getWirelessModeProcessingTime()`, `getDefaultWirelessMode()`, `setupWirelessProcessingPowerLogic()`.

### `ParallelismAcrossMultiMachineBase`
Cross-recipe parallelism design (inspired by external mod reference).
- In wireless mode `checkProcessing()` runs `checkProcessingWirelessLoop()`.
- Each wireless cycle may pick a **different recipe** from the recipe map; outputs are accumulated.
- Subclasses configure `cycleNum` via `setWirelessCycleNum(int)` and override `getMaxParallelRecipes()` / `getWirelessModeProcessingTime()`.

## Machines

### MTDTPF
- Extends `MTWirelessMultiMachineBase`.
- Wireless availability: `detecttier()` requires AAF + tier >= 5.
- Wireless mode: no-overclock, fixed 0.75 EU/duration modifier, player-selected wireless parallel.
- Recipe validation order (kept from original design):
  1. validate machine tier and recipe voltage;
  2. cap wireless parallel by wireless balance;
  3. `validateWirelessPowerForRecipe(...)` — if insufficient, return insufficient power;
  4. `onRecipeStart` → `startWirelessRecipe(...)` — deduct power + consume inputs → start.
- Runtime ramp: `running_time` 3600s to full efficiency, decay when idle.

### MTComputingCenter
- Modes:
  - 0 = Nano Computing
  - 1 = Research Station
  - 2 = Scanner (same research-style computation/packet-loss logic)
- Research/Scanner use holder hatch + controller special slot, `computationRemaining`/`packetLoss` logic.
- Heat/overclock: `machineHeat`, cryotheum active cooling, passive cooling when stopped, overheat melts racks.
- Waila values are reset whenever machine is not active (manual shutdown included).
- Waila mode uses string mode tag (`setString("mode", getMachineModeName(...))`) because upstream GT reads a string.

### MTAssFactory
- Modes:
  - 0 = Component Assembly Line (generic `ProcessingLogic`, energy hatch tier limits recipe casing tier)
  - 1 = Assembly Line (data-stick / Data Access, LevelTier 2 required, unordered input matching via standard `ProcessingLogic`)
- Structure `F` accepts Data Access hatch.
- Independent Assembly Line recipe map (`MTRecipeMaps.assFactoryAssemblyLineRecipes`) is populated with **real recipes** (not fake) and re-tagged to its own `RecipeCategory` for NEI.

## Own-code conventions / lessons

1. **Always reuse existing GT methods first.**
   - Input consumption: `depleteInput(ItemStack)` / `depleteInput(FluidStack)`.
   - Recipe finding: `ProcessingLogic` / `findRecipeQuery` / `getStoredInputs`.
   - Do **not** directly mutate `ItemStack.stackSize` / `FluidStack.amount` or call `setInventorySlotContents` on bus slots from custom processing code.
2. **Waila mode display**: upstream GT `MTEMultiBlockBase#getWailaBody` reads `tag.getString("mode")`, so write the localized mode name as a string, not an integer.
3. **Fake recipes are invisible to `findRecipeQuery`.** If a machine needs to process from a custom recipe pool, register real recipes (`addRecipe(..., false, false, false)`), not `addFakeRecipe`.
4. **NEI category lookup** reads `getRecipesByCategory(defaultCategory)`. When copying recipes into another map, call `recipe.setRecipeCategory(targetDefaultCategory)`.
5. **Block helpers**: `AssMatrixBlock` / `AdvAssMatrixBlock` expose `getBlock()`, `getItem()`, `getItemStack()`, `getItemStack(int)`; also registered in `MTItemList`.
6. **Registration** stays in one path: `postInit -> MTMachineLoader.loadMachines()`.
