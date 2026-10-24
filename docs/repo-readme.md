# MessTech Repo Notes

Own-code notes (architecture, conventions, past fixes); GT5U internals: `GT5U-NOTES.md`.

## Machine base hierarchy

```
MTMultiMachineBase<T>
├── MTWirelessMultiMachineBase<T>            wireless
│   └── ParallelismAcrossMultiMachineBase<T> cross-recipe parallelism
└── CalculateMultiMachineBase<T>             computation multiblock
    └── MTComputingCenter
```

### `MTMultiMachineBase`
`MTEExtendedPowerMultiBlockBase` + `ProcessingLogic` setup, mode switching, Waila mode NBT/body, shared
helpers; wireless in `MTWirelessMultiMachineBase`.

### `MTWirelessMultiMachineBase`
= old `MTMultiMachineBase` wireless region + `MTDTPF` mechanism.
- `ownerUUID`, `EnableWirelessFunc`, `EnableWireless`, `wirelessParallel`; `startRecipeProcessing()` /
  `endRecipeProcessing()` / `isRecipeProcessing`; `costingEU` / `costingEUText`.
- Waila NBT/body `无线模式` / `耗电`.
- Safe hooks: `checkWirelessPower(eut, duration, maxParallel)` simulates first;
  `validateWirelessPowerForRecipe(...)` balance first; `startWirelessRecipe(...)` power, then inputs.
- Loop `checkProcessingWirelessLoop()` + `wirelessModeProcessOnce()`; `prepareProcessing()`,
  `getExtraEUCostMultiplier()`, `getWirelessModeProcessingTime()`, `getDefaultWirelessMode()`,
  `setupWirelessProcessingPowerLogic()`.

### `ParallelismAcrossMultiMachineBase`
Cross-recipe parallelism, external-mod inspired.
- `checkProcessing()` runs `checkProcessingWirelessLoop()`; each cycle may pick a **different recipe**, outputs
  accumulate.
- `cycleNum` via `setWirelessCycleNum(int)`; override `getMaxParallelRecipes()` /
  `getWirelessModeProcessingTime()`.

## Machines

### MTDTPF
`MTWirelessMultiMachineBase`; `detecttier()` needs AAF + tier >= 5 for wireless.
- No overclock, fixed 0.75 EU/duration modifier, player-selected wireless parallel.
- Order: machine tier + recipe voltage; wireless balance caps parallel;
  `validateWirelessPowerForRecipe(...)`; `onRecipeStart` → `startWirelessRecipe(...)` deduct power, then
  inputs.
- `running_time` 3600s ramp to full efficiency, decay when idle.

### MTComputingCenter
- Modes: 0 = Nano Computing, 1 = Research Station, 2 = Scanner (research-style computation/packet-loss logic).
- Research/Scanner: holder hatch + controller special slot, `computationRemaining`/`packetLoss`.
- `machineHeat`, cryotheum active cooling, passive cooling when stopped, overheat melts racks.
- Waila values reset when not active (manual shutdown too); mode tag is the string
  `setString("mode", getMachineModeName(...))`.

### MTAssFactory
- Mode 0 = Component Assembly Line: generic `ProcessingLogic` recipe map lookup; energy hatch tier caps
  recipe casing tier.
- Mode 1 = Assembly Line: data-stick / Data Access, LevelTier 2, unordered input matching by
  `MTAssemblyLineMatcher`, no single-recipe locking.
- Structure `F` accepts Data Access hatch.
- `MTRecipeMaps.assFactoryAssemblyLineRecipes`: fake display recipe per definition, NEI only; runnable form
  resolved per check by `MTAssemblyLineMatcher` against input buses (`docs/GT5U-NOTES.md` § 11.6).

## Own-code conventions / lessons

1. **Reuse GT methods first.** Inputs `depleteInput(ItemStack)` / `depleteInput(FluidStack)`; recipes
   `ProcessingLogic` / `findRecipeQuery` / `getStoredInputs`. Never mutate `ItemStack.stackSize` /
   `FluidStack.amount` or `setInventorySlotContents` on bus slots from custom processing code.
2. **Waila mode display**: `MTEMultiBlockBase#getWailaBody` reads `tag.getString("mode")` — localized mode name
   as a string, not an integer.
3. **Fake recipes are invisible to `findRecipeQuery`**: custom pool → real recipes
   (`addRecipe(..., false, false, false)`), not `addFakeRecipe`. Exception: form unknowable upfront
   (AssFactory Assembly Line: per-slot alternatives = cartesian product) → fake NEI pages only, override
   `ProcessingLogic#findRecipeMatches` per check.
4. **NEI category lookup** = `getRecipesByCategory(defaultCategory)`; copying needs
   `recipe.setRecipeCategory(targetDefaultCategory)`.
5. **Block helpers**: `AssMatrixBlock` / `AdvAssMatrixBlock` give `getBlock()`, `getItem()`, `getItemStack()`,
   `getItemStack(int)`; also in `MTItemList`.
6. **Registration** single path: `postInit -> MTMachineLoader.loadMachines()`.
