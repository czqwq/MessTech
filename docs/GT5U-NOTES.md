# GT5-Unofficial / GTNH Multiblock knowledge notes (for writing new machines)

> What was learned while implementing `MTDTPF`, distilled so future machines can be written
> (and debugged) without re-reading the whole monorepo.

## 0. Local reference source trees (offline lookup)

The workspace already contains decompiled/source copies of the common dependencies. **Check these local paths
first** when verifying API behaviour instead of guessing:

| Path | Contents / use |
|---|---|
| `tmp/GT5-Unofficial-5.09.54.133/` | **GT5U source matching the current compile dependency** (5.09.54.133); first choice for `gregtech.api.*` / `gregtech.common.*` |
| `tmp/GT5-Unofficial-master/` | GT5U master-branch snapshot, **noticeably older than the versioned 5.09.54.133**; historical/diff reference only — always verify against `tmp/GT5-Unofficial-5.09.54.133/` |
| `tmp/ic2-decompiled/`, `tmp/ic2-src/` | Decompiled IC2 source incl. `ic2.core.*` internals (`ItemReactorUranium`, `ItemReactorHeatStorage`, `TileEntityNuclearReactorElectric`, ...) |
| `build/rfg/minecraft-src/java/` | **Forge + Minecraft 1.7.10 decompiled source** (`net.minecraft.*` such as `RenderItem`, `ItemRenderer`, `FontRenderer`, plus `cpw.mods.fml.*`) |
| `build/rfg/minecraft-src/resources/` | Vanilla MC assets |
| `tmp/GTNHLib-master/` | GTNHLib source (`ItemRenderUtil`, `ItemRenderUtils`, `TexturedItemRenderer`, `AnimatedTooltipHandler`) |
| `tmp/ModularUI2-master/` | ModularUI2 source (widgets, sync handlers, animation, slot layout) |
| `tmp/NotEnoughItems-master/` | NEI source (how NEI invokes `IItemRenderer`, item preview, ...) |
| `tmp/waila-master/` | Waila source (`getWailaBody`, ...) |
| `tmp/StructureLib-master/` | StructureLib source (multiblock structure matching) |
| `tmp/NewHorizonsCoreMod-2.9.61/`, `tmp/AppleCore-master/`, `tmp/SpiceOfLife-master/`, `tmp/Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/`, `tmp/Twist-Space-Technology-Mod-main/`, `tmp/BlockRenderer6343-master/`, `tmp/UniMixins-0.3.1/` | Other reference implementations (recipes, compat, rendering, ...) |
| `tmp/FuelRod_backup/`, `tmp/fuelrod_recolor/` | Transcendent Metal fuel rod texture sources and recolour intermediates (PNG / rgba data) |

> Unpacking/decompiling other dependencies (e.g. the IC2 jar):
> `java -cp tools/java-decompiler.jar org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=true <in.jar> <outDir>`

## 1. The project layout

`tmp/GT5-Unofficial-master` is a **monorepo**: many "mods" live inside it as plain packages
(packages are NOT separate gradle modules). Everything is on the compile classpath when you depend
on `GT5-Unofficial`:

| Package              | What it is                                    | Typical imports                              |
|----------------------|-----------------------------------------------|----------------------------------------------|
| `gregtech.*`         | GT 5 Unofficial core                          | `GregTechAPI`, `GTValues`, `RecipeMaps`, ... |
| `goodgenerator.*`    | GoodGenerator (Large Fusion Computers etc.)   | `goodgenerator.loader.Loaders.compactFusionCoil` |
| `gtPlusPlus.*`       | GT++ (GT Plus Plus)                           | `gtPlusPlus.core.block.ModBlocks.blockCasings3Misc` |
| `bartworks.*`        | BartWorks                                      | `ItemRegistry.bw_realglas`, `Werkstoff`      |
| `tectech.*`          | TecTech                                        | `TTMultiblockBase`, `MTEHatchEnergyMulti`    |
| `gtnhintergalactic.*`| GTNH Intergalactic / space                     | space project APIs                           |

So when you need a block from GoodGenerator you just `import goodgenerator.loader.Loaders;`
(e.g. `Loaders.compactFusionCoil`), you do **not** add another gradle dependency.

## 2. The machine base-class chain (that MessTech uses)

```
MTEBase -> ... -> MTEMultiBlockBase<T>
        -> MTEEnhancedMultiBlockBase<T>      (checkPiece, hatch checkers, structure status)
        -> MTEExtendedPowerMultiBlockBase<T> (long-power / exotic energy helpers)
        -> MTMultiMachineBase<T> (MessTech's own base, in com.MessTech.common.machine.Base)
        -> your machine (fully generic self type)
```

Key inherited tools you actually use:

* `checkPiece(String piece, int hOff, int vOff, int dOff, List<StructureError> errors)`
  — walks the shape; fills `errors`; returns boolean. Your `checkMachine` calls it first.
* `checkHasAnyInput / checkHasAnyOutput / checkHasAnyEnergy(List<StructureError>)`
  — hatch sanity checks after a successful piece check.
* `checkStructure(boolean forceReset, IGregTechTileEntity)` — the caller: calls `checkMachine`,
  then sets `mMachine = structureErrors.isEmpty()`.
* `clearHatches()` — clears all hatch lists before each check.
* `buildPiece`, `survivalBuildPiece`, `getStructureDefinition`.

**Contract:** you only override
`checkMachine(IGregTechTileEntity, ItemStack, List<StructureError>)` (void). Add an error + `return`
on any failure; leave the list empty for success. Do NOT set `mMachine` yourself.

`checkMachine` runs **server-side only** (`checkStructure` guards with `isServerSide()`).

## 3. StructureLib shape convention (important!)

* `addShape(pieceName, String[][] shape)` expects **`shape[z][y]` = a `String` spanning x**.
* A `~` inside the shape marks the **controller** position (no element needed for it).
* The GTNH "structure writer" tool prints shapes in a **tower-first** orientation: the first array
  dimension (`z`) is the vertical axis. To make a normal multiblock that works when the controller
  faces any horizontal direction, you usually need **`transpose(shape)`** when registering, so the
  tall axis becomes `y` (vertical) and `z` becomes depth. Look at each machine to tell which
  convention it uses:
  * `MTELargeFusionComputer` and **MTDTPF** author the array as vertical layers and call
    `.addShape(MAIN, transpose(structure_string))`.
  * `MTEPlasmaForge`'s `structure_string` is already depth-first and registers directly
    (offsets `16, 21, 16`).
* The offsets passed to `checkPiece/buildPiece(...)` are the (x, y, z) coordinates of the `~` in the
  **registered (post-transpose)** shape:
  * `horizontalOffset = x` of `~`
  * `verticalOffset   = y` of `~`
  * `depthOffset      = z` of `~`
* For MTDTPF: raw array is 32 layers × 33 rows × 33 wide, `~` at raw `(z=29, y=16, x=16)`; after
  `transpose` it becomes `(z=16, y=29, x=16)` → offsets `(16, 29, 16)`.
  If you ever register it without transpose the controller would need to face **up** for the shape to
  line up (the "only works facing up" bug).

## 4. Structure elements (`addElement`)

* `StructureUtility.ofBlock(Block, intMeta)` — exact block+meta match.
* `StructureUtility.ofBlocksTiered(tierFetcher, List<Pair<Block,Integer>> blockList, int notSet,
  BiConsumer<T,Integer> setter, Function<T,Integer> getter [, List<String> description])`
  — the standard way to accept a whole "family" of blocks at one letter and record which tier was found:
  * the first matched position calls `setter` with the tier,
  * later positions must report the same tier via `getter` (mixed tiers → piece check fails),
  * `tierFetcher` returns `null` for a block that isn't in the family (structure rejects it).
  This is how `MTDTPF` auto-detects the reactor-coil tier (A) and the fusion-casing tier (B)
  into two independent `int` fields with getters/setters.
* `HatchElementBuilder.<T>builder().anyOf(HatchElement...).casingIndex(int).hint(int)
  .buildAndChain(ofBlock(fallbackBlock, meta))` — a "functional" element that accepts the listed
  hatches at the letter position and falls back to a plain casing. Used for D (input/output
  bus/hatch + energy hatch compartments).

## 5. Blocks used by MTDTPF (registry names → meta → tier)

| Letter | Meaning                          | Block                          | meta | tier |
|--------|----------------------------------|--------------------------------|------|------|
| A      | reactor coil                     | `Loaders.compactFusionCoil`    | 0..4 | 1..5 |
| B (1)  | LuV machine casing ("gt.blockcasings") | `GregTechAPI.sBlockCasings1` | 6    | 1    |
| B (2)  | Fusion Machine Casing ("gt.blockcasings4") | `GregTechAPI.sBlockCasings4` | 6 | 2 |
| B (3)  | Fusion Machine Casing Mk-II      | `GregTechAPI.sBlockCasings4`   | 8    | 3    |
| B (4)  | Fusion Machine Casing Mk-III     | `ModBlocks.blockCasings3Misc` (GT++) | 12 | 4 |
| B (5)  | Fusion Machine Casing Mk-IV      | `ModBlocks.blockCasings6Misc` (GT++) | 0 | 5 |
| C      | solenoid superconductor coil ("gt.blockcasings.cyclotron_coils") | `GregTechAPI.sSolenoidCoilCasings` | 5 (ZPM) | static |
| D      | hatch compartments, fill casing ("gt.blockcasings8") | `GregTechAPI.sBlockCasings8` | 7 | fill |

These mirror `MTELargeFusionComputer1..5` (goodgenerator) — see
`goodgenerator/blocks/tileEntity/MTELargeFusionComputer{1..5}.java`:
coil meta 0/1/2/3/4 ↔ casing LuV / Fusion / Fusion-MkII / Fusion-MkIII / Fusion-MkIV
and voltage tiers LuV(6)/ZPM(7)/UV(8)/UHV(9)/UEV(10).

Registry-name helpers: `BlockCasings1` = "gt.blockcasings", `BlockCasings4` = "gt.blockcasings4",
`BlockCasings8` = "gt.blockcasings8", `BlockCyclotronCoils` = "gt.blockcasings.cyclotron_coils".
`GregTechAPI.sSolenoidCoilCasings` is the cyclotron-coil block field.

## 6. Tier enforcement pattern (LevelTier)

* Keep a `LevelTier` enum containing, per tier: `tier` (1..5), `voltageTier` (GT voltage index),
  the A (coil) block+meta and B (casing) block+meta. Add an `INVALID` entry with tier -1.
* The structure elements write `fusionCoilTier` / `fusionMachineTier` ints (independent getters/setters).
* In `checkMachine`:
  1. reset the two int fields + the resolved `levelTier` to INVALID,
  2. `checkPiece(...)`; if it fails `return`,
  3. resolve `LevelTier` from each int; if either is invalid or they differ →
     add a **localized** `StructureErrors.of("key", ...)` error and `return`,
  4. otherwise store the matched tier and run the hatch sanity checks.
* Note the piece-check already rejects *mixed tiers inside A (or inside B)* — checkMachine only has
  to compare A vs B.

### Reading the tier back (usage examples)

```java
LevelTier tier = getLevelTier();        // LevelTier.INVALID when not formed / tier mismatch
int n = getStructureTier();             // 1..5, or -1 when INVALID
boolean ok = isTierAtLeast(LevelTier.TIER3); // true for TIER3/4/5, false for 1/2/INVALID
boolean ok2 = isTierAtLeast(3);         // numeric <<levelTier >= 3>>, same rule
boolean exact = getLevelTier() == LevelTier.TIER2;
LevelTier fromInt = LevelTier.fromTier(n);
LevelTier fromBlock = LevelTier.getFromCoilBlock(block, meta);
```
All "at least" checks return `false` when the machine is `INVALID` (unformed). The recipe cap in
`validateRecipe()` already uses `getLevelTier().isValid()` + `voltageTier`; reuse
`isTierAtLeast(...)` anywhere you need tier-gated logic (e.g. max parallel, overclock choice).

## 7. Recipes: using the Large-Fusion pool with a tier cap

* `getRecipeMap()` → `RecipeMaps.fusionRecipes` (same pool as MTELargeFusionComputer).
* Override `createProcessingLogic()`: keep the base `process()` behavior
  (`setEuModifier / setSpeedBonus / setOverclock`) and override `validateRecipe(GTRecipe)`:
  ```java
  LevelTier current = getLevelTier();
  if (!current.isValid() || recipe.mEUt > GTValues.V[current.voltageTier]) {
      return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
  }
  return CheckRecipeResultRegistry.SUCCESSFUL;
  ```
  This is exactly the FusionComputer voltage restriction ("tier N can only do recipes of tier N and
  below"), **without** the startup-energy (`FUSION_THRESHOLD`) system.
* `GTValues.V[v]` holds the EU/t per voltage tier; `GTValues.VN[v]` holds short names ("LuV"...).

## 8. Structure errors & localization

* Use `StructureErrors.of("your.key", TranslatableText.literal(...))` to build a localized error.
* Add the keys to **your own** lang files (`assets/<modid>/lang/en_US.lang`, `zh_CN.lang`).
  `%1$s`/`%2$s` are the argument slots (see `structure.error.tier_mismatch`).
* Do the `errors.add(...); return;` dance for fatal problems; hatch-count errors just append.
* **The GUI only shows the list if the terminal widget contains it.**
  `MTEMultiBlockBaseGui.createTerminalTextWidget()` puts `createStructureErrorWidget(syncManager)` into the
  terminal flow. A machine that overrides `createTerminalTextWidget()` (as `MTReactorGui` does) silently drops
  every detailed structure error and only shows whatever the override itself draws — the reactor then showed a
  generic "incomplete structure" line while `missing_access_hatch` / `missing_heat_hatch` were in the error list
  all along. Fix: append `.child(createStructureErrorWidget(syncManager))` to your own terminal list as well.
  The widget is gated on `shouldDisplayShutDownReason() && !isActive && !isAllowedToWork()`, so it appears after
  the controller stopped itself with `STRUCTURE_INCOMPLETE` (that shutdown calls `disableWorking()`).
* A hatch-count error for a *custom* (non-`HatchElement`) hatch: use
  `StructureErrors.of("your.missing_key")` / `StructureErrors.of("your.too_many_key")` and check
  `list.isEmpty()` / `list.size() > 1` in `checkMachine(..., List<StructureError> errors)`.

## 9. Construct / survival construct offsets

`construct` → `buildPiece(piece, stack, hintsOnly, h, v, d)`.
`survivalConstruct` → `survivalBuildPiece(piece, stack, h, v, d, elementBudget, env, false, true)`
(with `if (mMachine) return -1;`). Offsets are the same `~` coordinates as `checkPiece`.

Import trap: `ISurvivalBuildEnvironment` lives in
`com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment` — it is **NOT** in
`alignment.constructable` (that package only holds `ISurvivalConstructable`).

## 10. Textures & GUI (MTDTPF example)

* **Textures** — `getTexture(...)`: the *front* face (`side == facing`) uses the Large Fusion
  Computer Mk-V look (`Textures.BlockIcons.MACHINE_CASING_FUSION_GLASS` + a GT++ screen overlay from
  `TexturesGtBlock.Casing_Machine_Screen_Rainbow/1`); every other face uses the fusion machine casing
  texture matching the current tier (`INVALID` falls back to MKI). This is done with
  `Textures.BlockIcons.getCasingTextureForId(GTUtility.getCasingTextureIndex(tier.machineBlock, tier.machineMeta))`,
  following the same tier-to-casing idea as `MTEPreciseAssembler` (`CASING_INDEX + casingTier`).
  The side texture is built with `TextureFactory.of(tier.machineBlock, tier.machineMeta)` so it uses
  the actual block icon directly (avoids missing casing-texture entries for GT++/higher-tier casings).
  The tier is synced to the client through `getUpdateData()` / `receiveClientEvent()` using
  `GregTechTileClientEvents.CHANGE_CUSTOM_DATA` (same pattern as Precise Assembler).
  The old Plasma Forge casing is still available through `ICasingTextureProvider#getCasingTexture()`
  returning `Textures.BlockIcons.casingTexturePages[0][14]`.
* **GUI** — machines override `protected @NotNull MTEMultiBlockBaseGui<?> getGui()` (in
  `gregtech.api...MTEMultiBlockBase`). Modern GTNH GUI code is built on **CleanroomModularUI**
  (`com.cleanroommc.modularui.*`: `ModularPanel`, `PanelSyncManager`, `BooleanSyncValue`,
  `IntSyncValue`, `ButtonWidget`, `Flow`, `TextFieldWidget`), while the GT base keeps a few
  `com.gtnewhorizons.modularui` helpers around — don't mix them up.
  `MTEPlasmaForgeGui` is a good template: override `registerSyncValues(PanelSyncManager)` to bind
  server fields (e.g. `convergence` + `catalystType`) with `.allowC2S()`, and override
  `createButtonColumn(...)` to add a custom button. MTDTPF's `MTDTPFGui` is a verbatim port.
* Sync-able fields on the MTE should be persisted in `saveNBTData/loadNBTData` (see the
  `convergence` / `catalystTypeForRecipesWithoutCatalyst` pair in MTDTPF).

## 10.5 Waila (tooltip) data

Waila lines are split across two callbacks:

* `getWailaNBTData(EntityPlayerMP, TileEntity, NBTTagCompound, World, x,y,z)` — **server side**, write any
  runtime value into the tag, e.g. `tag.setBoolean("enablePOC", isTierAtLeast(5))`.
* `getWailaBody(ItemStack, List<String>, IWailaDataAccessor, IWailaConfigHandler)` — **client side**, read
  `accessor.getNBTData()`, **gate on `tag.hasKey(...)`**, then `currentTip.add(...)`.

The `hasKey` gate is the "should I add this line" check (see `MTEPlasmaForge.getWailaBody`). MTDTPF uses the
same pattern to show perfect-overclock state with the existing `machine.dtpf.perfectoverclock*` lang keys,
mirroring its own `getInfoData()` (yellow = on, green = off).

## 10.6 Runtime efficiency scaling (DTPF-style)

MTDTPF uses a DTPF-like `running_time` ramp (unlike DTPF's 8h target, this machine uses **3600s**):

```
p        = min(running_time / 72000, 1)          // 72000 ticks = 3600s
EU(p)    = p >= 1 ? 0.5 : 1.0                    // no reduction until 3600s, then halved (step, not linear)
time(p)  = p >= 1 ? 0.5 : 1.0                    // same step behaviour
P        = (1 + machineTier - recipeTier) * 64   // parallel, min 1; machineTier/recipeTier are MKI..MKV (1..5)
```

Implementation notes (all verified against GT source):
* Parallel is **tier-difference based**, not runtime based: `validateRecipe()` sets
  `maxParallel = max(1, (1 + current.tier - recipeTier) * 64)` where
  `recipeTier = max(1, GTUtility.getTier(recipe.mEUt) - 5)` (LuV=1, ZPM=2, ... UEV=5).
  `getMaxParallelRecipes()` only returns a tier-based fallback for the GUI. GT's `ParallelHelper` still caps
  the real parallel by `availableEUt / recipeEUt` and by input counts.
* `getEuModifier()` returns `EU(p)` — `OverclockCalculator` uses it as the EU/t discount.
* `getSpeedBonus()` returns `time(p)` — it is the **duration multiplier** (`durationModifier`), not a speed
  multiplier: `0.5` = half recipe time, `1.0` = normal.
* `checkProcessing()` adds `mMaxProgresstime` to `running_time` on success; `onPostTick()` decays
  `running_time` by `EFFICIENCY_DECAY_RATE = 100` per tick when `mMaxProgresstime == 0` (same as MTEPlasmaForge).
* NBT persists `eRunningTime`; Waila shows `running_time`; item tooltip has four colored, localized lines
  (yellow "EU/duration ramp", gray "full efficiency after 3600s", gold "parallel formula", aqua "EU|duration halved").
* Fluid output is DTPF-style: `addOutput(FluidStack)` copies the stack and calls `addOutputPartial`, and
  `addFluidOutputs(...)` is overridden to route normal completion outputs through that same `addOutput` path.

## 10.7 Wireless mode (direct wireless-network power)

Reference: `MTETranscendentPlasmaMixer` (`gregtech/common/tileentities/machines/multi/MTETranscendentPlasmaMixer.java`)
and `WirelessNetworkManager` (`gregtech/common/misc/WirelessNetworkManager.java`).

* The machine does **not** use a wireless energy hatch. In wireless mode it draws directly from the
  global wireless network via `WirelessNetworkManager`.
* `MTMultiMachineBase` owns the shared helpers:
  * `initWirelessNetwork(IGregTechTileEntity)` — calls `processInitialSettings` once on the server.
  * `checkWirelessPower(eut, duration, maxParallel)` — called from `validateRecipe`; returns
    `insufficientStartupPower` before any input is consumed.
  * `startWirelessRecipe(...)` — called from `onRecipeStart`; deducts the actual EU cost first, and
    only then calls `GTRecipe.consumeInput`. This is what prevents “insufficient power swallowing inputs”.
* Subclasses override `isWirelessModeAvailable()` / `isWirelessModeEnabled()` (MTDTPF maps them to
  `EnableWirelessFunc` / `EnableWireless`).
* In wireless mode:
  * `createParallelHelper` sets `setConsumption(false)` so `ParallelHelper` does not consume inputs
    before the wireless deduction.
  * `setProcessingLogicPower` gives `Long.MAX_VALUE` voltage / 1 amp / unlimited tier skips (like TPM).
  * `createOverclockCalculator` uses `OverclockCalculator.ofNoOverclock(...)` while still applying
    MTDTPF's EU/duration efficiency modifiers, so the wireless network pays the actual no-overclock cost.
  * `checkMachine` skips `checkHasAnyEnergy` and, like `OTEBBPlasmaForge`, **forbids** normal energy
    hatches while wireless mode is active (`ErrorType.TOO_MANY`).
  * `setEnableWireless` only enables when the feature is available and no energy hatches are present.
  * Wireless parallel is user-selectable (like `MTETranscendentPlasmaMixer`): `wirelessParallel` field,
    right-click on the wireless button opens a selector, max `Integer.MAX_VALUE`.
  * Wireless mode disables the 3600s runtime ramp: `getEuModifier()`/`getSpeedBonus()` return a fixed
    `0.75` and `running_time` is neither incremented nor decayed.
  * GUI button uses `GTGuiTextures.TT_SAFE_VOID_ON/OFF` to toggle `EnableWireless`, gated by
    `EnableWirelessFunc`.

## 11. Gotchas

* The tile factory pattern: to make `newMetaEntity(...)` work you need a **String constructor**
  (`public MTDTPF(String aName) { super(aName); }`) and `newMetaEntity` returns `new MTDTPF(mName)`
  — same as `MTELargeFusionComputer5`. MTDTPF now has both.
* The front overlay is active-aware exactly like LFC5's `getTextureOverlay()` (`Casing_Machine_Screen_Rainbow`
  while active / `Casing_Machine_Screen_1` idle); the non-front faces stay on the DTPF casing.
* `getMaxParallelRecipes()` is a separate concept from the recipe *tier* restriction; leave it
  according to the machine's intended parallelism.
* Tier block lookups (`Loaders.compactFusionCoil`, `GregTechAPI.sBlockCasings*`) are populated during
  mod pre-init, so classes that reference them in enum/static initializers must load after blocks exist
  (normal MTE registration order is fine).
* When copying a shape from the structure writer, keep it under `// spotless:off ... spotless:on`.
* Don't "fix" the base class to make one machine work — override in the concrete MTE instead.
* **Always end `createTooltip()` with `.toolTipFinisher()`** — the tooltip arrays (`iArray`/`sArray`/`hArray`)
  are built there and nowhere else. If you skip it, `getStructureDescription()` (=
  `getTooltip().getStructureHint()`) returns `null`, and BlockRenderer6343's NEI preview
  (`GTGuiMultiblockHandler.findHints`) crashes with `NullPointerException: Cannot read the array length`
  while rendering the usage GUI. (That is exactly why MTDTPF crashed — see the 2026-08-21 crash report.)
* **`getMaxParallelRecipes()` must be > 0.** `ParallelHelper.determineParallel()` returns immediately when
  `maxParallel <= 0`, so the recipe is found/matched but the machine never starts (no progress, empty GUI).
  MTDTPF's skeleton returned 0; it now returns 1 so the machine actually runs.
* **Don't set absurd parallel caps (e.g. millions).** GT has at least one O(parallel) loop
  (`ParallelHelper.calculateChancedOutputMultiplier` does `for roll < parallel`) plus output splitting loops,
  so a huge cap that is actually reached freezes the game with no crash/log. Keep caps sane and let
  `availableEUt / recipeEUt` and input counts do the real limiting.
* **`IVoidable.canDumpItemToME/canDumpFluidToME` are implemented in `MTMultiMachineBase`**
  so every MessTech multiblock satisfies `IVoidable` regardless of whether the GT5U `MTEMultiBlockBase`
  in the active dependency provides them. The implementation mirrors upstream
  (`tmp/GT5-Unofficial-master/.../MTEMultiBlockBase.java` ~line 3194/3218), including the
  `hasPhysicalSpace()` and `getCheckMode()` checks. If the IDE claims a signature conflict, re-import
  the Gradle project so the IDE picks up the same GT5U API as the compiler (`GTUtility.FluidId` is
  `public abstract static class` in `GTUtility.java`).

## 11.5 Nano Computing Center (`MTComputingCenter`)

MessTech's computation multiblock, based on `CalculateMultiMachineBase`.

* Modes:
  * Mode 1 = **Nano Computing** (quantum-computer style): racks produce computation, data input is
    forwarded to output automatically, wireless output is enabled with a wire cutter.
  * Mode 2 = **Research Station** style: holder hatch (`F`) holds the research item, controller slot
    holds a Data Stick, computation is produced internally by racks, and the finished Data Stick is
    output to an OutputBus or left in the controller slot.
  * Mode 3 = **Scanner** style: same research-style computation/packet-loss logic as the original
    Research Station scanner, using `RecipeMaps.scannerHandlers` and `RecipeMaps.scannerFakeRecipes`.
* Structure:
  * `A` = normal hatches + Uncertainty + Data + WirelessComputationOutput
  * `D` = `MTHatchRack`
  * `F` = research holder or air
* Custom recipe map: `MTRecipeMaps.computingCenterFakeRecipes`.
  * NEI: `MTMachineLoader` calls `MTRecipeMaps.populateComputingCenterFakeRecipes()` after
    `MTEHatchRack.run()`. For each recipe copied from the shared rack-component pool it
    **re-tags the recipe to this map's default `RecipeCategory`** before adding it — GT's NEI handler
    reads `backend.getRecipesByCategory(defaultCategory)`, not `getAllRecipes()`, so without the
    re-tag the independent Computing Center list stays empty. Title localized via
    `mt.recipe.computingcenter`.
* Rack class: `MTHatchRack` extends GT5U `MTEHatchRack`.
  * `tickComponents(oc, ov)` is overridden to soften the overclock penalty: the stock
    `(oc-ov)^2` denominator term is scaled by `PENALTY_FACTOR = 0.8f` (20% softer).
    Component stats are looked up from the shared QC fake-recipe pool
    (`QuantumComputerRecipeData`, cached by unique id).
  * `getDescription()` is overridden so the item tooltip omits the "TecTech: Elemental Matter" line.
* Research packet-loss / `checkComputationTimeout` (grace/decay/full windows) is ported into
  `MTComputingCenter.onRunningTick`, with NBT persistence for `ticksUntilPacketLossFail` and
  `packetLossDecayFrom`.
* Wireless: `WirelessComputationPacket.uploadData` with a real world tick (obtained by casting the
  base meta tile entity to `TileEntity`).
* Heat/overclock:
  * `machineHeat` is internal storage (max `10,000,000`), saved in NBT.
  * While heat is present, overclock cannot be disabled, rack components stay locked, and the
    screwdriver cannot switch modes until the heat is fully dissipated.
  * Cooling:
    * **Active**: `consumeCryotheumForHeat()` removes one `10,000` heat batch per `100,000L`
      Gelid Cryotheum, looping until no full batch remains (or the fluid runs out) — applied
      both while running and while stopped.
    * **Passive** (machine stopped only): every second `machineHeat -= max(machineHeat/1000, 20)`
      (mirrors the stock rack decay).
  * If heat exceeds max, all rack components are melted and the machine stops with
    `ExtraShutdownReason.OverHeating`.
  * Manual shutdown (GUI on/off / redstone) never reaches `stopMachine`, so `onPostTick` clears
    leftover Waila/GUI values (`eAvailableData`, `mEUt`, and any unfinished research progress)
    whenever the controller is not active (`resetStoppedDisplay()`).
  * On controller removal with residual heat, all rack components are burned (`meltAllComponents`).
  * Lock handling: `setRacksActive(false)` is a no-op while `machineHeat > 0`, and
    `onPostTick` re-asserts `setRacksActive(true)` whenever heat is present (covers chunk reloads).
* Animated tooltip:
  * `AuthorDynamic.registerOn` adds `Add by: <animated MessTech>` below the author line.
  * The "MessTech" text uses a flowing purple gradient via gtnhlib `AnimatedTooltipHandler.animatedText`.

## 11.6 Assembly Factory (`MTAssFactory`)

* Two recipe pools, switched by screwdriver (also via the built-in mode button):
  * Mode 0 = **Component Assembly Line**: `GoodGeneratorRecipeMaps.componentAssemblyLineRecipes`,
    generic `ProcessingLogic` via the base machine; recipe casing tier (`mSpecialValue`) is limited
    to the current energy hatch tier (`getInputVoltageTier()`).
  * Mode 1 = **Assembly Line**: reads the authorised recipes from the controller data stick and
    Data Access hatches, resolves them against the input buses itself (`MTAssemblyLineMatcher`) and hands the
    winner to the standard `ProcessingLogic` pipeline; only usable when `LevelTier == 2`. No single-recipe
    locking in this mode (`supportsSingleRecipeLocking()` is false, like GT's own `MTEAssemblyLine`).
* Structure:
  * `F` accepts the usual buses/hatches/energy plus **Data Access hatches**.
  * `I` = tiered Assembly Matrix Blocks (Tier 1 / Tier 2), derived into `LevelTier`.
* GUI: `MTAssFactoryGui` (standard multi-block GUI; mode button comes from the base machine-mode stack).
* Independent Assembly Line NEI pool: `MTRecipeMaps.assFactoryAssemblyLineRecipes`, built in
  `MTRecipeMaps.populateAssFactoryAssemblyLineRecipes()` from `GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes`
  (the authoritative list a data stick / Data Access hatch resolves against):
  * one **fake** recipe per definition, keeping the per-slot alternatives, is what NEI draws (one page per
    Assembly Line recipe). `RecipeMapBackend#filterFindRecipe` rejects fake recipes, so nothing runs straight out of
    this pool.
    Its special slot carries the definition's flash drive (`displayDataStick`), the same data stick GT puts into every
    `assemblylineVisualRecipes` entry, so the page shows the "reads research result" stick. GT fills its own sticks from
    `RecipeAssemblyLine#reInit()`, which runs at postInit (ore dictionary activation) - before this pool is built at
    `serverStarted` - and only again on an item remap, so the NBT is written when the stick is made. The sticks are
    cached in `DISPLAY_DATA_STICKS` per definition because `newDataStickForNEI` appends to the strongly held
    `RecipeAssemblyLine.dataSticksForNEI` and a populate happens on every world load.
  * **no runnable recipes**, deliberately. One recipe per alternative combination - the obvious way to make the
    alternatives matchable - is the cartesian product of a definition's slots, and that product reaches six digits for
    a single definition (Wetware Mainframe: five ASMD/XSMD slots plus six superconductor wires), so building that table
    exhausted the heap on `serverStarted`. The machine resolves the definitions on the fly instead, see below.
  * `RecipeAssemblyLine` is still the only source used: `displayInputSpec` keeps a slot's alternatives in the NEI page
    as a plain `ItemStack[]`, which `GTRecipe_WithAlt#buildItemInputCache` drops for anything that is not an
    ore-dictionary slot (`mOreDictIds[i] >= 0`) - fine for a page, since NEI reads `mOreDictAlt` itself.
* Assembly Line mode requires at least one Data Access hatch; `checkMachine` reports
  `machine.assfactory.error.need_data_access` / `need_tier2`.
* Assembly Line mode is **unordered**: `MTAssemblyLineMatcher` reads every input bus as one pool keyed by
  `GTUtility.ItemId` (exact stack with NBT ignored, plus a wildcard-damage alias), and each slot takes the alternative
  that leaves the most parallels once the slots before it are paid for. The outcome is a plain
  `GTRecipe` built with `GTRecipeBuilder.builder()` and handed to `ProcessingLogic#findRecipeMatches`, so parallels,
  perfect overclock, void protection, debug/phantom and ME buses and the consumption itself stay the shared pipeline.
* Debug input buses are supported natively: Assembly Line mode delegates consumption to the
  normal `ProcessingLogic` / `getStoredInputs` path, so phantom/debug-marked items are recognised.
* **Always reuse existing inventory methods first.** Consuming inputs must go through the machine's
  existing `depleteInput(ItemStack)` / `depleteInput(FluidStack)` (which properly handles regular,
  debug/phantom and ME buses/hatches). Do not directly mutate `ItemStack.stackSize` /
  `FluidStack.amount` or call `setInventorySlotContents` on bus slots from custom processing code.
* Controller front texture: Advanced Molecular Casing base + Quantum Force Transformer face overlay.
* Tooltips refer to the energy hatch option as **Multi-Amp Energy (多安能源仓)** (display naming in
  the lang files uses 多安能源仓 instead of 异域能源仓).
* Tooltips refer to the energy hatch option as **Multi-Amp Energy (多安能源仓)** (display naming in
  the lang files uses 多安能源仓 instead of 异域能源仓).

## 12. Registering a machine + animated authors

### Registering an MTE so it shows up in game

The MTE **constructor itself registers the id** (see `MetaTileEntity(int aID, ...)`: "This registers your
Machine at the List"). You don't call any `registerMetaTileEntity`. Pattern (from `goodgenerator/loader/Loaders.java`
and `gregtech/loaders/preload/LoaderMetaTileEntities.java`):

```java
ItemStack stack = new MTDTPF(ID, "mt.dtpf.controller", "MT DTPF").getStackForm(1L);
```

* `getStackForm(long)` returns `new ItemStack(GregTechAPI.sBlockMachines, size, metaTileID)` — the machine item.
* **IDs**: GT reserves 0..2047; addon machines use the **32000+** range (goodgenerator 32019..32029,
  NeutronAccelerator 32003.., Wires 32737+, ...). Pick a free value for your pack.
* **When**: register in `postInit` if your machine's statics reference **other mods' blocks** (e.g. MTDTPF's
  `LevelTier` reads `goodgenerator.loader.Loaders.compactFusionCoil`). Doing it in `postInit` guarantees every
  mod's blocks are already loaded. (The MTE javadoc says "Load phase"; cross-mod refs are the exception that
  justifies postInit.)

### Animated "Author:" tooltip (gtnhlib)

`AnimatedTooltipHandler` (gtnhlib: `com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler`) appends an animated
line at the **end** of an item tooltip and is client-only (`@EventBusSubscriber(side = CLIENT)`):

* primitives: `text(String)`, `animatedText(text, posstep, delay, colors...)`, `chain(Supplier...)`,
  color constants (`AQUA`, `RED`, `BOLD`, ...), `addItemTooltip(ItemStack, Supplier<String>)`.
* `animatedText` paints each char with `colors[(i*posstep - offset) mod len]`; `posstep` controls how fast the
  color band moves across the text, `delay` the per-step ms.
* Author composition: `GTAuthors.buildAuthorsWithFormatSupplier(Supplier<String>...)` wraps into the localized
  `"gt.authors"` ("Author:"/"Authors:") prefix — that's the `LoaderMetaTileEntities` pattern:
  ```java
  addItemTooltip(ItemList.Machine_Multi_PurificationPlant.get(1),
                 GTAuthors.buildAuthorsWithFormatSupplier(GTAuthors.AuthorNotAPenguinAnimated));
  ```
* `GTAuthors` has many static colored `String` authors and animated `Supplier<String>` authors
  (`AuthorCloud`, `AuthorNoc`, `AuthorThree`, `fancyAuthorChrom`, `AuthorSerenibyss`, ...) — all built on
  gtnhlib's `animatedText`/`chain`.

MessTech's `com.MessTech.common.util.AuthorDynamic` is a self-contained "czqwq" author:
rainbow ramp (from `GTAuthors.AuthorThree` style) + color wave (`AuthorSerenibyss` style) + a custom
**ping-pong offset** (triangle wave) so the rainbow band wobbles **left-right** instead of scrolling one way.
`AuthorDynamic.author()` -> `Supplier<String>`; `AuthorDynamic.registerOn(stack)` =
`AnimatedTooltipHandler.addItemTooltip(stack, GTAuthors.buildAuthorsWithFormatSupplier(AuthorDynamic.author()))`.

### MessTech's actual refactor (current layout)

The registration was split out of `CommonProxy` into three small pieces:

| file | responsibility |
|---|---|
| `com/MessTech/common/misc/MTItemList.java` | GT-style `ItemList` enum (copy of `gregtech.api.enums.ItemList` pattern): one entry per machine (`MTDTPF`), `set(Item/ItemStack/IMetaTileEntity)` + `get(int, Object...)` |
| `com/MessTech/common/machine/loaders/MTMachineLoader.java` | `loadMachines()`: `MTItemList.MTDTPF.set(new MTDTPF(ID, ...).getStackForm(1L))` then `AuthorDynamic.registerOn(MTItemList.MTDTPF.get(1))` |
| `com/MessTech/init/CommonProxy.java` | `postInit = MTMachineLoader.loadMachines()` (kept in postInit so cross-mod blocks are loaded) |

Keep it as ONE registration path: the loader calls `AuthorDynamic.registerOn(...)`, never a second inline
`addItemTooltip`. Click-through: `postInit -> MTMachineLoader.loadMachines -> MTItemList.set(stack) +
AuthorDynamic.registerOn(stack) -> AnimatedTooltipHandler.addItemTooltip(stack,
GTAuthors.buildAuthorsWithFormatSupplier(AuthorDynamic.author()))`.

## 13. GT5U API knowledge from recent work

* Upstream `MTEMultiBlockBase#getWailaBody` displays a running-mode line by reading `tag.getString("mode")`.
  To show a localized machine-mode name, write `tag.setString("mode", getMachineModeName(machineMode))`.
  Writing `tag.setInteger("mode", machineMode)` makes Waila show `运行模式: 0/1`.
* `ProcessingLogic#findRecipeMatches` uses `recipeMap.findRecipeQuery().items(...).fluids(...).specialSlot(...).findAll()`.
  Fake recipes (`addFakeRecipe`) are **not** found by `findRecipeQuery`; a machine that processes from a custom
  pool needs real recipes (`addRecipe(copy, false, false, false)`).
* NEI's `GTNEIDefaultHandler` reads `recipeMap.getBackend().getRecipesByCategory(defaultCategory)`.
  When copying recipes from one recipe map to another, call `recipe.setRecipeCategory(targetDefaultCategory)`
  or the copied list will stay empty in the new NEI tab.
* Scanner mode (original Research Station scanner): `RecipeMaps.scannerHandlers.findRecipe(this, holder, special, fluid)`
  returns `GTScannerResult`; required computation = `researchTime * 2^(tier-1)`, `eRequiredData = 1`,
  EU = `max(|recipeEUt|, TierEU.RECIPE_UV)`.
* `MTEHatchDataAccess#getAssemblyLineRecipes()` returns recipes from the data sticks stored inside the hatch.
  Original Assembly Line processing is **ordered** by input-bus slots; an "unordered assembly line" must instead
  delegate to normal `ProcessingLogic` / `getStoredInputs`.
* `MTEHatchInputBusDebug` exposes phantom inventory via `getStackInSlot()` returning copied stacks with a huge
  stack size (`Integer.MAX_VALUE` when not finite). Standard `getStoredInputs()` / `depleteInput()` handle it.
* `COAL_CASING_TIER` is aliased into `GTRecipe.mSpecialValue` (`GTRecipeConstants.SPECIAL_VALUE_ALIASES`).
* `RecipeMaps.scannerFakeRecipes` / `RecipeMaps.assemblylineVisualRecipes` are NEI/visual pools; they are not
  meant to be searched by normal recipe processing unless copied into a real recipe map.
* `WirelessNetworkManager` helpers: `processInitialSettings` (owner UUID), `getUserEU`, `addEUToGlobalEnergyMap`.
  Safe wireless order: validate balance first (`checkWirelessPower`/`validateWirelessPowerForRecipe`), then
  `startWirelessRecipe` deducts and consumes inputs.

## 14. MessTech's IC2/GT fuel rods (Transcendent Metal)

* Classes:
  * `com.MessTech.common.items.MTFuelRod extends gregtech.api.items.ItemRadioactiveCellIC` — burnable rods.
  * `com.MessTech.common.items.MTDepletedFuelRod extends gregtech.common.items.ItemDepletedCell` — inert rods.
  * `com.MessTech.common.items.MTFuelRodItemRenderer` — client `IItemRenderer`; copies
    `TranscendentalMetaItemRenderer`'s oblique-axis tumble (3.5 deg per client tick around (0.3, 0.5, 0.2),
    then 180 deg around X, pivot = quad centre; angle from `GTMod.clientProxy().getAnimationRenderTicks()`).
  * Instances + stats live in `com.MessTech.common.items.MTItems` (region "Transcendent Metal fuel rods").
* Why extend GT instead of IC2: `ItemReactorUranium`'s constructor needs an IC2 `InternalName` and hardcodes the
  IC2 depleted stacks, while `ItemRadioactiveCellIC` is the public addon API: custom depleted `ItemStack`,
  `IReactorComponent`, radiation, NBT `advDmg` + vanilla 0..99 damage bar, and the NEI nuclear fake recipe.
* Registration gotcha: `GTGenericItem`'s constructor already calls
  `GameRegistry.registerItem(this, "gt." + aUnlocalized)` (the 3-arg FML method ignores its modId in 1.7.10),
  so the rods must NOT be registered again in `MTItems.registerItems()`. Display name / lang key:
  `gt.rodTranscendentMetal.name` (and `...2` / `...4` / `...Depleted...`).
* Current stats (`MTItems`): `maxDamage 250_000`, `radiation 32`, `MOX=true`, `heatBonus=2`,
  **`heat 4_096`** and one energy per size — `ENERGY_SINGLE 9_000_000`, `ENERGY_DUAL 4_500_000`,
  `ENERGY_QUAD 3_000_000`. MTReactor formulas: `output += pulses * sEnergy`, `EU/t = output * 5 *
  balance/energy/generator/nuclear` (`MTReactor#getEuPerOutput()`; GTNH's `config/IC2.ini` ships that factor as
  5.0, so one output point is 25 EU/t there - the same number `TileEntityNuclearReactorElectric#getOfferedEnergy()`
  and GT5U's NEI nuclear fake recipe build with);
  a lone rod adds `1 + cells / 2` pulses on each of its `cells` passes, so
  `EU/t = sEnergy * cells * (1 + cells / 2) * 25` → **single 225,000,000 / dual 450,000,000 / quad 900,000,000
  EU/t** (exact 1x/2x/4x scaling, the single rod is 6.7A UIV). The same formula is what the NEI nuclear fake recipe
  prints, so the tooltip numbers match the real output.
  Heat per cycle is `cells * triangular(1 + cells / 2) * sHeat` → 4,096 / 24,576 / 98,304 HU/s bare, which a
  single `ItemList.neutroniumHeatCapacitor` (1G Neutronium Heat Capacitor, 1,000,000,000 HU) buffers for ~2.8 h.
* `MTReactorAccessHatch.isFuelRod` was widened to `ItemReactorUranium || (ItemRadioactiveCell
  && !ItemDepletedCell)` so GT-style rods also get the fuel durability line in the slot sub-panel.

## 15. MTReactor heat control hatch (`MTReactorHeatHatch`)

* `com.MessTech.common.machine.hatch.MTReactorHeatHatch extends MTEHatch` — no inventory (0 slots, like
  `MTEHatchMuffler`), tier EV..UIV, front decal
  `assets/messtech/textures/blocks/hatch/Hatch_Reactor_Temp_Control.png` (base texture = multiblock casing,
  same `updateTexture(aBaseCasingIndex)` + `withOverlay` pattern as `MTReactorAccessHatch`).
* Heat ceilings (`HEAT_CAPACITY`, index `tier - MIN_TIER`): EV 10,000 / IV 20,000 / LuV 50,000 / ZPM 100,000 /
  UV 200,000 / UHV 500,000 / UEV 1,000,000 / **UIV `Integer.MAX_VALUE`**. EV equals the old hardcoded default.
* Structure: registered as another custom `IHatchElement` (`MTReactor.REACTOR_HEAT_HATCH`, `name() =
  "mt_reactor_heat_hatch"`, `count()` = `getReactorHeatHatchCount()`) and added to the `'C'`
  `HatchElementBuilder.atLeast(...)` group, so it can go on any of the 0-25 casings of the shell.
  `checkMachine()` requires **exactly one**: empty → `missing_heat_hatch`, `size() > 1` → `too_many_heat_hatch`
  (both are our own lang keys; a duplicate hatch cannot be resolved by picking one, so it is a structure error).
* The hatch sets the heat ceiling of the reactor: `MTReactor.getReactorHeatCapacity()` returns the (lowest)
  hatch value or `DEFAULT_HEAT_CAPACITY = 10,000` while the structure is broken.
  `ReactorContext.bind()` / `bindSimulation()` seed `maxHeat` with it, and **`ReactorContext.setMaxHeat()` is a
  no-op on purpose** — in IC2 reactor plating (`ItemReactorHeatStorage`) raises `maxHeat` through that call, but
  here the hatch is the only thing that may set the ceiling, so plating no longer stacks. If plating should stack
  again, change that no-op back to `this.maxHeat = newMaxHeat` and start `maxHeat` from the hatch value.
* Explosion/stability math is unchanged: `calculateHeatEffects()` does `power = heat / maxHeat` with the hatch
  value, so `>= 1.0F` explodes and `>= 0.85F` ignites the surroundings.
* **The heat effects never replace a block** (the IC2 code they were copied from did): the `>= 0.85F` branch only
  places `Blocks.fire` into **air** (`isAir` check), the `Blocks.flowing_lava` replacement is gone. In IC2 that
  branch turned any non-air block into fire or lava — GT casings use their own `MaterialCasings`, so they became
  fire and stone/ground became lava, which ate the reactor's own casings/hatches and dissolved the multiblock.
  Fire in an air block cannot break the structure: every structure position requires a specific non-air block and
  the unchecked `' '` positions ignore the block. The `>= 0.5F` water removal and the `>= 0.4F` wood/leaves/cloth
  ignition are kept (they can never touch a casing/hatch).
* Explosion power is **IC2's number remapped onto the Draconic Evolution scale**: `explodeReactor()` still mirrors
  IC2's `explode()` (start at 10, add every component's `influenceExplosion`, multiply the `0 < influence < 1` ones
  into `boomMod`, then `* hem * boomMod`) and clamps the result to `IC2_EXPLOSION_POWER_LIMIT = 45` (IC2's own
  `protection/reactorExplosionPowerLimit` default — the config is **not** read, no IC2 dependency/mixin), but the
  blast itself is now `MTExplosionDE`:
  `dePower = min(boomPower, 45) / 45 * Config.REACTOR_EXPLOSION_DE_POWER_LIMIT` (default 40, `Reactor` category).
  `explodeMultiblock()` + `doExplosion(GTValues.V[8])` on the access hatches still remove the machine itself first.
* **Why IC2's `ExplosionIC2` was dropped** (it looked like a "powerless" explosion in game): IC2 shoots
  `2 * steps^2` rays with `steps = ceil(pi / atan(0.4 / power))`, i.e. ~250k rays at power 45 but ~**25 million** at
  power 450, each ray stepping through the blocks until its energy runs out (up to 900 blocks in air). The whole
  destruction list is only written back *after* every ray finished, so a 450 power IC2 explosion freezes the server
  for minutes and effectively removes nothing — the only blocks the player sees vanishing are the small
  `doExplosion(V[8])` craters from the controller/hatches. DE's algorithm scales instead: one ring per tick, one
  column per ring block.
* `MTExplosionDE` / `MTExplosionDETrace` are a 1:1 port of Draconic Evolution's `ReactorExplosion` /
  `ReactorExplosionTrace` (+ its `IProcess` / `ProcessHandler`, kept as `IMTProcess` / `MTProcessHandler` and ticked
  on `TickEvent.ServerTickEvent`): the ring expands to `power * 10` blocks of radius, every ring block spawns a
  column trace that blasts its column downwards (`energy = power * 10`, resisted per block, fire/lava puddles at the
  end) and then upwards (`energy = power * 20`), damaging every entity it passes with `power * 100` of the
  `damage.messtech.reactorExplode` damage source (armour bypassing, creative allowed). The port hard caps the power
  at `MTExplosionDE.MAX_POWER = 40` — twice DE's own full reactor (2..20), one power unit being ~10 blocks of
  radius, so the worst meltdown vaporises a 400 block radius. Cost notice: 40 power is ~500k columns and therefore
  tens of millions of block updates spread over 400 ticks; lower `REACTOR_EXPLOSION_DE_POWER_LIMIT` if that is too
  heavy for the server.
* **Never cache the ceiling.** It used to live in a per-page `int[] mPageMaxHeat` that was only written during a
  reactor cycle / stability simulation of a non-empty page and was also persisted to NBT. Consequences: an empty,
  idle or freshly built reactor kept showing the old 10,000, and old saves reloaded the stale array. The array is
  gone; `MTReactor.getHottestPageMaxHeat()` reads `getReactorHeatCapacity()` live (used by the GUI sync value,
  Waila, the scanner info and the heat percentage), so replacing the hatch is visible immediately.
* **Sentinel trap:** `getReactorHeatCapacity()` searches for the lowest hatch value and used `Integer.MAX_VALUE`
  as the starting/sentinel value — but UIV's real ceiling *is* `Integer.MAX_VALUE`, so a UIV heat hatch was
  mistaken for "no hatch" and fell back to 10,000. Use a separate `boolean found` flag for the empty case.
* Registration: `MTItemList.MTReactorHeatHatch_EV..UIV`, IDs `MT_ID + 24 .. + 31` (32424..32431), built in
  `MTMachineLoader` exactly like the access hatches. MTEs self-register in the `CommonMetaTileEntity(int, ...)`
  constructor, so the ID must be unused (it throws otherwise) — the access hatches occupy `MT_ID + 16 .. + 23`.
  Both loops now iterate the shared arrays `MTItemList.REACTOR_ACCESS_HATCHES` / `REACTOR_HEAT_HATCHES`, which
  `GTRecipes.addReactorRecipes()` also uses so the recipe order cannot drift from the registration order.

## 16. Reactor recipes (`GTRecipes.addReactorRecipes()`)

* Tier helpers: `ItemList.MACHINE_CASINGS[tier]` gives the tier machine casing (index 9 = UHV, GT calls it
  `Casing_MAX`); `OrePrefixes.circuit.get(Materials.<TIER>)` gives the tier circuit, the same mapping
  `GTModHandler.addMachineCraftingRecipe` uses (tier 4 → `Materials.EV`, ..., tier 11 → `Materials.UIV`).
  `TIER_RECIPE_EU[]` / `TIER_CIRCUIT_MATERIALS[]` in `GTRecipes` hold both tables.
* Access hatch (assembler, `TierEU.RECIPE_<TIER>`, 2 min, circuit 1): tier casing + lever + 4 tier circuits.
* Heat control hatch (assembler, same EU/t and time, circuit 2): tier casing + 4 solid steel casings
  (`ItemList.Casing_SolidSteel`, "脱氧钢机械方块") + 4 tier circuits.
* Reactor controller (assembler, `RECIPE_EV`, 2 min, circuit 1): 16 solid steel casings + 16
  `Ic2Items.nuclearReactor` + 64 `Ic2Items.reactorChamber` + 144×256 molten lead (36864 L). Use
  `GTUtility.copyAmount(...)` on the IC2 static stacks — they are singletons and the recipe builder must not
  mutate them (and `copyAmount` clamps at 64, use `copyAmountUnsafe` for bigger stacks).
* Fuel rods (mirrors `FissionFuelLoader`: single = canner, dual/quad = assembler):
  * single: The Core (`ItemList.RodNaquadah32`) + Avaritia Star Fuel (`getModItem(Avaritia, "Resource", 1, 8)`,
    meta 8 in Avaritia 1.97/1.99) → canner, `RECIPE_UEV`, 32 s, guarded by `Mods.Avaritia.isModLoaded()`.
  * dual: 2 single rods + 4 `stick` Transcendent Metal, circuit 2, UIV assembler, 100 s.
  * quad: 4 single rods + 6 `stickLong`, circuit 4, UIV assembler, 100 s; alternative 2 dual rods + 2
    `stickLong`, UIV assembler, 50 s.
  * "NC 编程电路 N" just means GT's programmed circuit with config N → `.circuit(N)` (N = the "not consumed"
    number shown in NEI).
* Depleted rod recycling (`addDepletedRodRecycling`, `centrifugeRecipes`, `RECIPE_UIV`, 50/100/200 s) copies GT's
  own depleted naquadah rod recycling chances (100/50/50/25/100/100 %) and scales the amounts by the rod size
  (single 1x / dual 2x / quad 4x): Transcendent Metal dust 4/8/16, Naquadah dust 8/16/32 (100 %) plus the same
  amount again at 50 %, Naquadria dustSmall 4/8/16 (50 %), NaquadahEnriched dustTiny 8/16/32 (25 %),
  TungstenSteel dust 16/32/64 and Platinum dust 2/4/8.

## 17. Beamline / particle hatch API (GT5U 5.09.54.133)

Used as the reference for MessTech's wireless beamline hatches (`docs/knowledge.md`).

* Classes: `gtnhlanth.common.hatch.MTEHatchBeamlineConnector` (abstract, extends `MTEHatch`, holds the public
  `BeamLinePacket dataPacket`, ticks `moveAround` at `tectech.util.CommonValues.MOVE_AT` = `tick % 20 == 4`,
  reports the beam through `getInfoData()`), `MTEHatchInputBeamline` (`setContents(BeamLinePacket)`, impl.
  `ISmartInputHatch`, `newMetaEntity` returns **`MetaTileEntity`**, `getDescription()` returns `null`) and
  `MTEHatchOutputBeamline` (`moveAround` walks up to 128 beamline pipes in a straight line and calls
  `setContents` on the first input hatch, then clears its packet; `newMetaEntity` returns `IMetaTileEntity`).
* Filtered variant: `gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline`
  extends the output hatch, is `@IMetaTileEntity.SkipGenerateDescription`, tier 8, and keeps
  `Map<Particle, Boolean> acceptedInputMap` (+ `getParticleList()`/`getParticleMap()`/`setAcceptedInputMap`).
  The LHC/beam splitter fill it via `MTEBeamMultiBase.addAdvancedBeamlineOutputHatch(...)` (which calls
  `setInitialParticleList(LHCModule.<force>.acceptedParticles)`) and read it back before pushing a packet.
  Its ModularUI (`gregtech.common.gui.modularui.hatch.MTEHatchAdvancedOutputBeamlineGui`) edits the map through
  GenericMap/List sync handlers, so a subclass only needs to inherit `buildUI`.
* Data: `BeamInformation` (`float energy` keV, `int rate` = flux, `int particleId`, `float focus`) and
  `BeamLinePacket extends tectech.mechanics.dataTransport.DataPacket<BeamInformation>` (NBT round-trip ready).
  `BeamInformation` validates the particle id; `Particle.VALUES[id]` is the enum table.
* Machines push the beam by assigning `hatch.dataPacket = new BeamLinePacket(...)` (e.g. `MTESourceChamber`,
  `MTESynchrotron`, `MTELINAC`, `MTEBeamStabilizer`, `MTEBeamSplitter`, `MTELargeHadronCollider`) and read inputs
  with `mInputBeamline.get(n).dataPacket`. `MTEBeamMultiBase` keeps `mInputBeamline` / `mOutputBeamline` /
  `mAdvancedOutputBeamline` and clears them in `clearHatches()`.
* Hatch slots are `instanceof` based (`addBeamLineInputHatch` / `addBeamLineOutputHatch` /
  `addAdvancedBeamlineOutputHatch`), and `BeamHatchElement.BeamlineInput/Output` match on `mteClasses()`, so a
  subclass is accepted by every beam structure without touching GT5U. `.hatchId(<MetaTileEntityIDs id>)` feeds
  `couldBeValid` and the autoplace item filter (both item-damage based), so it does *not* affect the structure
  check but it does make the hologram flag a placed add-on hatch as an error and prevents auto-placing it.
* The wired output hatch's `moveAround` walks its straight line and calls `setContents` on any
  `MTEHatchInputBeamline` it meets **without checking `canConnect`** (`MTEHatchOutputBeamline.java:97-99`), so an
  add-on input hatch has to reject foreign packets itself if it wants to stay channel-pure.
* The wired output hatch overwrites `canConnect` per side (`isOutputFacing`); the input hatch allows pipes only on
  its input face. Both are tier 6 (LuV) hatches, registered as `HATCH_BEAMLINE_INPUT/OUTPUT` and
  `HATCH_ADVANCED_BEAMLINE_OUTPUT` in `MetaTileEntityIDs`; names come from `gt.blockmachines.hatch.beamlineinput`
  (束流输入仓), `...beamlineoutput` (束流输出仓) and `...hatch.advancedbeamlineoutput` (过滤式束流输出仓).
* `MTEHatchBeamlineConnector.getDescription()` contains an "Must be painted to work" line, but colour is only
  used for the texture modulation (`Dyes.getModulation`) - the wired pipe path ignores it entirely. Colour is
  therefore free to be used as a channel key by addons (which is what MessTech does).
* `IMetaTileEntity.SkipGenerateDescription` (not inherited by subclasses, `ItemMachines.addDescription` /
  `registerDescription`): without it, `getDescription()` is dumped once into `GregTech.lang` at client setup and
  read back from there, so per-instance text ends up frozen.
