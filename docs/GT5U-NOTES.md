# GT5-Unofficial / GTNH Multiblock knowledge notes (for writing new machines)

> `MTDTPF` lessons, distilled so a new machine can be written and debugged without re-reading the monorepo.

## 0. Local reference source trees (offline lookup)

**Check these first** for API behaviour:

| Path | Contents / use |
|---|---|
| `tmp/GT5U-264-src/` | **GT5U 5.09.54.264 源码快照 = 当前编译依赖**（`dependencies.gradle` 的 `gtnhVersion` 2.9.0）；查 `gregtech.*` / `bartworks.*` / `tectech.*` / `goodgenerator.*` 等 API 首选；205→264 的移除/变化见 §18 |
| `tmp/GT5U-205-src/` | GT5U 5.09.54.205 源码快照（2.9.0-RC-2 的上一版编译依赖），只用于和 264 对照 |
| `tmp/GT5-Unofficial-beta3/` | **GT5U 5.09.54.133 源码快照**（更早的历史基线）；`tmp/GT5-Unofficial-master/` 更旧 |
| `tmp/ic2-decompiled/`, `tmp/ic2-src/` | IC2 source, incl. `ic2.core.*` internals (`ItemReactorUranium`, `ItemReactorHeatStorage`, `TileEntityNuclearReactorElectric`, ...) |
| `build/rfg/minecraft-src/java/` | **Forge + MC 1.7.10 decompiled source** (`net.minecraft.*`: `RenderItem`, `ItemRenderer`, `FontRenderer`; `cpw.mods.fml.*`) |
| `build/rfg/minecraft-src/resources/` | Vanilla MC assets |
| `tmp/GTNHLib-master/` | GTNHLib (`ItemRenderUtil`, `ItemRenderUtils`, `TexturedItemRenderer`, `AnimatedTooltipHandler`) |
| `tmp/ModularUI2-master/` | ModularUI2 (widgets, sync, animation, slots) |
| `tmp/NotEnoughItems-master/` | NEI (`IItemRenderer` invocation, preview) |
| `tmp/waila-master/` | Waila (`getWailaBody`) |
| `tmp/StructureLib-master/` | StructureLib (matching) |
| `tmp/GT-Not-Leisure/` | **modern GT5U API port of the same GoodGenerator LEG** (`common/machine/multiblock/LargeEssentiaGenerator.java`, `TileEntityEssentiaHatch`, `utils/machine/LargeEssentiaEnergyData`) plus a Thaumcraft research tab (`common/recipe/thaumcraft/TCResearches.java`) - the pattern used by MessTech's LEG / research port (its values were rebalanced, mechanics copied from the 5.09.51.242 original) |
| `tmp/thaumicenergistics-src/` | **Thaumic Energistics 1.7.64-GTNH** decompiled with `tools/java-decompiler.jar` from `tmp/ThaumicEnergistics-1.7.64-GTNH-dev.jar`: AE essentia stack/monitor API (`common/storage/AEEssentiaStack`, `AEEssentiaStackType`), grid proxy examples (`MTEEssentiaOutputHatchME` in the GT5U tree is the closest one), import/export bus parts |
| `tmp/NewHorizonsCoreMod-2.9.61/`, `tmp/AppleCore-master/`, `tmp/SpiceOfLife-master/`, `tmp/Applied-Energistics-2-Unofficial-rv3-beta-1050-GTNH/`, `tmp/Twist-Space-Technology-Mod-main/`, `tmp/BlockRenderer6343-master/`, `tmp/UniMixins-0.3.1/` | other references (recipes, compat, rendering) |
| `tmp/FuelRod_backup/`, `tmp/fuelrod_recolor/` | Transcendent Metal fuel rod textures / recolour intermediates (PNG, rgba) |

> Decompiling another dependency (e.g. the IC2 jar):
> `java -cp tools/java-decompiler.jar org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=true <in.jar> <outDir>`

## 1. The project layout

`tmp/GT5-Unofficial-master` is a **monorepo**: its "mods" are plain packages, not gradle modules, all on the compile classpath of `GT5-Unofficial`:

| Package | What it is | Typical imports |
|---|---|---|
| `gregtech.*` | GT 5 Unofficial core | `GregTechAPI`, `GTValues`, `RecipeMaps`, ... |
| `goodgenerator.*` | GoodGenerator (Large Fusion Computers) | `goodgenerator.loader.Loaders.compactFusionCoil` |
| `gtPlusPlus.*` | GT++ (GT Plus Plus) | `gtPlusPlus.core.block.ModBlocks.blockCasings3Misc` |
| `bartworks.*` | BartWorks | `ItemRegistry.bw_realglas`, `Werkstoff` |
| `tectech.*` | TecTech | `TTMultiblockBase`, `MTEHatchEnergyMulti` |
| `gtnhintergalactic.*` | GTNH Intergalactic / space | space project APIs |

A GoodGenerator block is just `import goodgenerator.loader.Loaders;` (e.g. `Loaders.compactFusionCoil`) — no extra gradle dependency.

## 2. The machine base-class chain (that MessTech uses)

```
MTEBase -> ... -> MTEMultiBlockBase<T>
        -> MTEEnhancedMultiBlockBase<T>      (checkPiece, hatch checkers, structure status)
        -> MTEExtendedPowerMultiBlockBase<T> (long-power / exotic energy helpers)
        -> MTMultiMachineBase<T> (MessTech's own base, in com.MessTech.common.machine.Base)
        -> your machine (fully generic self type)
```

* `checkPiece(String piece, int hOff, int vOff, int dOff, List<StructureError> errors)` — walks the shape, fills `errors`, returns boolean; call first in `checkMachine`.
* `checkHasAnyInput / checkHasAnyOutput / checkHasAnyEnergy(List<StructureError>)` — hatch sanity after a good piece check, `clearHatches()` first; also `buildPiece`, `survivalBuildPiece`, `getStructureDefinition`.
* `checkStructure(boolean forceReset, IGregTechTileEntity)` — calls `checkMachine`, sets `mMachine = structureErrors.isEmpty()`. **Contract:** override only `checkMachine(IGregTechTileEntity, ItemStack, List<StructureError>)` (void) — error + `return` on failure, empty list = success, never set `mMachine`; server-side only (`isServerSide()` guard).

## 3. StructureLib shape convention (important!)

* `addShape(pieceName, String[][] shape)` = **`shape[z][y]`**, each a `String` spanning x; `~` = **controller** (no element).
* Structure-writer shapes are **tower-first** (`z` vertical) → machines facing any horizontal direction usually need **`transpose(shape)`** (`y` tall, `z` depth): `MTELargeFusionComputer` / **MTDTPF** → `.addShape(MAIN, transpose(structure_string))`; `MTEPlasmaForge`'s `structure_string` is already depth-first (offsets `16, 21, 16`).
* `checkPiece/buildPiece(...)` offsets = `~` in the **registered (post-transpose)** shape: `horizontalOffset = x` · `verticalOffset   = y` · `depthOffset      = z`.
* MTDTPF: raw 32 layers × 33 rows × 33 wide, `~` at raw `(z=29, y=16, x=16)` → `(z=16, y=29, x=16)` = offsets `(16, 29, 16)`; without transpose it only forms facing **up** (the "only works facing up" bug).

## 4. Structure elements (`addElement`)

* `StructureUtility.ofBlock(Block, intMeta)` — exact block+meta.
* `StructureUtility.ofBlocksTiered(tierFetcher, List<Pair<Block,Integer>> blockList, int notSet, BiConsumer<T,Integer> setter, Function<T,Integer> getter [, List<String> description])` — a "family" at one letter: first match calls `setter` with the tier, later positions must agree via `getter` (mixed → piece check fails), `tierFetcher` returns `null` outside the family. Used by `MTDTPF` for the reactor-coil tier (A) and fusion-casing tier (B), two independent `int` fields.
* `HatchElementBuilder.<T>builder().anyOf(HatchElement...).casingIndex(int).hint(int).buildAndChain(ofBlock(fallbackBlock, meta))` — "functional" element: listed hatches at that letter, else a plain casing (D = bus/hatch + energy hatch compartments).
* **Structure channels**: `IStructureChannels.use(element)` = `StructureUtility.withChannel(name, element)` and `MultiblockTooltipBuilder.addSubChannel(channel)` advertise it. `withChannel` swaps the trigger stack's master channel for that sub-channel, so `ofBlocksTiered`'s hint/auto-build (which reads `trigger.stackSize`, 1-based into the `allKnownTiers` list) is selected per sub-channel on a blueprint. Applied on LEG's `E`: `GTStructureChannels.LES_ESSENTIA_CELL.use(ofBlocksTiered(...))`; the field the getter reads must be reset to `notSet` before every `checkPiece` (LEG: `mCellTier = -1` in `checkMachine`).
* **`atLeast(...)` is a preview filter, not a structure requirement** (checked on 5.09.54.183: its `shouldReject` predicate, the only thing counting the required hatches, has no consumer outside the builder). Only the item filter/auto-build ordering uses the counts; the actual `checkPiece` accepts any number of those hatches. Required hatches must be checked by hand in `checkMachine` — `checkHasAnyInput/Output/Energy(errors)` or explicit list sizes and `StructureErrors.of(...)` (LEG: exactly one input hatch, one dynamo, at least one essentia hatch).

## 5. Blocks used by MTDTPF (registry names → meta → tier)

| Letter | Meaning | Block | meta | tier |
|---|---|---|---|---|
| A | reactor coil | `Loaders.compactFusionCoil` | 0..4 | 1..5 |
| B (1) | LuV machine casing ("gt.blockcasings") | `GregTechAPI.sBlockCasings1` | 6 | 1 |
| B (2) | Fusion Machine Casing ("gt.blockcasings4") | `GregTechAPI.sBlockCasings4` | 6 | 2 |
| B (3) | Fusion Machine Casing Mk-II | `GregTechAPI.sBlockCasings4` | 8 | 3 |
| B (4) | Fusion Machine Casing Mk-III | `ModBlocks.blockCasings3Misc` (GT++) | 12 | 4 |
| B (5) | Fusion Machine Casing Mk-IV | `ModBlocks.blockCasings6Misc` (GT++) | 0 | 5 |
| C | solenoid superconductor coil ("gt.blockcasings.cyclotron_coils") | `GregTechAPI.sSolenoidCoilCasings` | 5 (ZPM) | static |
| D | hatch compartments, fill casing ("gt.blockcasings8") | `GregTechAPI.sBlockCasings8` | 7 | fill |

Mirrors `MTELargeFusionComputer1..5` (`goodgenerator/blocks/tileEntity/MTELargeFusionComputer{1..5}.java`): coil meta 0/1/2/3/4 ↔ casing LuV / Fusion / Fusion-MkII / Fusion-MkIII / Fusion-MkIV, voltage LuV(6)/ZPM(7)/UV(8)/UHV(9)/UEV(10). Registry names: `BlockCasings1` = "gt.blockcasings", `BlockCasings4` = "gt.blockcasings4", `BlockCasings8` = "gt.blockcasings8", `BlockCyclotronCoils` = "gt.blockcasings.cyclotron_coils"; `GregTechAPI.sSolenoidCoilCasings` = the cyclotron-coil field.

## 6. Tier enforcement pattern (LevelTier)

* `LevelTier` enum, per tier: `tier` (1..5), `voltageTier` (GT voltage index), A (coil) block+meta, B (casing) block+meta; `INVALID` with tier -1.
* Structure elements write `fusionCoilTier` / `fusionMachineTier` ints (independent getters/setters).
* `checkMachine`: both ints + `levelTier` → INVALID → `checkPiece(...)`, `return` on failure → resolve `LevelTier` per int, invalid or differing = localized `StructureErrors.of("key", ...)` + `return` → else store the tier, run the hatch checks. The piece check already rejects mixed tiers inside A (or B); only A vs B is compared.

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
"At least" checks return `false` when unformed (`INVALID`). The `validateRecipe()` cap already uses `getLevelTier().isValid()` + `voltageTier`; reuse `isTierAtLeast(...)` for tier-gated logic (max parallel, overclock choice).

## 7. Recipes: using the Large-Fusion pool with a tier cap

* `getRecipeMap()` → `RecipeMaps.fusionRecipes` (same pool as MTELargeFusionComputer).
* `createProcessingLogic()`: keep the base `process()` behavior (`setEuModifier / setSpeedBonus / setOverclock`), override `validateRecipe(GTRecipe)`:
  ```java
  LevelTier current = getLevelTier();
  if (!current.isValid() || recipe.mEUt > GTValues.V[current.voltageTier]) {
      return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
  }
  return CheckRecipeResultRegistry.SUCCESSFUL;
  ```
  = the FusionComputer voltage restriction ("tier N does recipes of tier N and below") **without** the startup-energy (`FUSION_THRESHOLD`) system. `GTValues.V[v]` = EU/t per tier, `GTValues.VN[v]` = short names ("LuV"...).

## 8. Structure errors & localization

* `StructureErrors.of("your.key", TranslatableText.literal(...))`; keys in **your own** lang files (`assets/<modid>/lang/en_US.lang`, `zh_CN.lang`), `%1$s`/`%2$s` = argument slots (`structure.error.tier_mismatch`); `errors.add(...); return;` on fatal, hatch-count errors append.
* **The GUI only shows the list if the terminal widget contains it.** `MTEMultiBlockBaseGui.createTerminalTextWidget()` adds `createStructureErrorWidget(syncManager)` to the terminal flow → overriding `createTerminalTextWidget()` (as `MTReactorGui` does) drops every detailed error (`missing_access_hatch` / `missing_heat_hatch` invisible). Fix: append `.child(createStructureErrorWidget(syncManager))`. Gated on `shouldDisplayShutDownReason() && !isActive && !isAllowedToWork()`, i.e. after `STRUCTURE_INCOMPLETE` (`disableWorking()`).
* Custom (non-`HatchElement`) hatch: `StructureErrors.of("your.missing_key")` / `StructureErrors.of("your.too_many_key")` with `list.isEmpty()` / `list.size() > 1` in `checkMachine(..., List<StructureError> errors)`.

## 9. Construct / survival construct offsets

`construct` → `buildPiece(piece, stack, hintsOnly, h, v, d)`; `survivalConstruct` → `survivalBuildPiece(piece, stack, h, v, d, elementBudget, env, false, true)` (with `if (mMachine) return -1;`); same `~` coordinates as `checkPiece`.

Import trap: `ISurvivalBuildEnvironment` is in `com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment`, **NOT** in `alignment.constructable` (that only holds `ISurvivalConstructable`).

## 10. Textures & GUI (MTDTPF example)

* **Textures** — `getTexture(...)`: *front* face (`side == facing`) = Large Fusion Computer Mk-V look (`Textures.BlockIcons.MACHINE_CASING_FUSION_GLASS` + GT++ overlay `TexturesGtBlock.Casing_Machine_Screen_Rainbow/1`), other faces the current tier's casing (`INVALID` → MKI) via `Textures.BlockIcons.getCasingTextureForId(GTUtility.getCasingTextureIndex(tier.machineBlock, tier.machineMeta))` (`MTEPreciseAssembler` idea, `CASING_INDEX + casingTier`). Side texture = `TextureFactory.of(tier.machineBlock, tier.machineMeta)` (real block icon, no missing casing entries for GT++/higher tiers). Tier sync: `getUpdateData()` / `receiveClientEvent()` + `GregTechTileClientEvents.CHANGE_CUSTOM_DATA`. Old Plasma Forge casing: `ICasingTextureProvider#getCasingTexture()` → `Textures.BlockIcons.casingTexturePages[0][14]`.
* **GUI** — override `protected @NotNull MTEMultiBlockBaseGui<?> getGui()` (in `gregtech.api...MTEMultiBlockBase`). Modern GTNH GUI is **CleanroomModularUI** (`com.cleanroommc.modularui.*`: `ModularPanel`, `PanelSyncManager`, `BooleanSyncValue`, `IntSyncValue`, `ButtonWidget`, `Flow`, `TextFieldWidget`); the GT base keeps a few `com.gtnewhorizons.modularui` helpers — don't mix them. Template `MTEPlasmaForgeGui`: `registerSyncValues(PanelSyncManager)` binds server fields (`convergence` + `catalystType`) with `.allowC2S()`, `createButtonColumn(...)` adds a button; `MTDTPFGui` is a verbatim port.
* Sync-able MTE fields belong in `saveNBTData/loadNBTData` (`convergence` / `catalystTypeForRecipesWithoutCatalyst`).

## 10.5 Waila (tooltip) data

* `getWailaNBTData(EntityPlayerMP, TileEntity, NBTTagCompound, World, x,y,z)` — **server**: runtime values into the tag, e.g. `tag.setBoolean("enablePOC", isTierAtLeast(5))`.
* `getWailaBody(ItemStack, List<String>, IWailaDataAccessor, IWailaConfigHandler)` — **client**: `accessor.getNBTData()`, **gate on `tag.hasKey(...)`** (see `MTEPlasmaForge.getWailaBody`), then `currentTip.add(...)`.

MTDTPF shows perfect-overclock state the same way with the `machine.dtpf.perfectoverclock*` keys, mirroring `getInfoData()` (yellow = on, green = off).

## 10.6 Runtime efficiency scaling (DTPF-style)

DTPF-like `running_time` ramp (target **3600s**, not DTPF's 8h):

```
p        = min(running_time / 72000, 1)          // 72000 ticks = 3600s
EU(p)    = p >= 1 ? 0.5 : 1.0                    // no reduction until 3600s, then halved (step, not linear)
time(p)  = p >= 1 ? 0.5 : 1.0                    // same step behaviour
P        = (1 + machineTier - recipeTier) * 64   // parallel, min 1; machineTier/recipeTier are MKI..MKV (1..5)
```

* Parallel is **tier-difference based**, not runtime based: `validateRecipe()` sets `maxParallel = max(1, (1 + current.tier - recipeTier) * 64)`, `recipeTier = max(1, GTUtility.getTier(recipe.mEUt) - 5)` (LuV=1, ZPM=2, ... UEV=5); `getMaxParallelRecipes()` only feeds the GUI, GT's `ParallelHelper` still caps by `availableEUt / recipeEUt` and input counts.
* `getEuModifier()` = `EU(p)`, the `OverclockCalculator` EU/t discount; `getSpeedBonus()` = `time(p)`, the **duration multiplier** (`durationModifier`), not a speed multiplier (`0.5` = half recipe time, `1.0` = normal).
* `checkProcessing()` adds `mMaxProgresstime` to `running_time`; `onPostTick()` decays it by `EFFICIENCY_DECAY_RATE = 100` per tick while `mMaxProgresstime == 0` (as `MTEPlasmaForge`).
* NBT `eRunningTime`, Waila `running_time`, four colored localized tooltip lines (yellow "EU/duration ramp", gray "full efficiency after 3600s", gold "parallel formula", aqua "EU|duration halved").
* Fluid output: `addOutput(FluidStack)` copies the stack → `addOutputPartial`; `addFluidOutputs(...)` routes normal outputs through it.

## 10.7 Wireless mode (direct wireless-network power)

Reference: `MTETranscendentPlasmaMixer` (`gregtech/common/tileentities/machines/multi/MTETranscendentPlasmaMixer.java`), `WirelessNetworkManager` (`gregtech/common/misc/WirelessNetworkManager.java`).

* No wireless energy hatch — it draws straight from the global `WirelessNetworkManager` network.
* `MTMultiMachineBase` helpers: `initWirelessNetwork(IGregTechTileEntity)` (`processInitialSettings` once, server); `checkWirelessPower(eut, duration, maxParallel)` (from `validateRecipe`, returns `insufficientStartupPower` before inputs); `startWirelessRecipe(...)` (from `onRecipeStart`, deducts the EU first, then `GTRecipe.consumeInput`). Subclasses override `isWirelessModeAvailable()` / `isWirelessModeEnabled()` (MTDTPF: `EnableWirelessFunc` / `EnableWireless`).
* In wireless mode: `createParallelHelper` → `setConsumption(false)` · `setProcessingLogicPower` → `Long.MAX_VALUE` voltage / 1 amp / unlimited tier skips (like TPM) · `createOverclockCalculator` → `OverclockCalculator.ofNoOverclock(...)` with MTDTPF's EU/duration modifiers · `checkMachine` skips `checkHasAnyEnergy` and, like `OTEBBPlasmaForge`, **forbids** normal energy hatches (`ErrorType.TOO_MANY`) · `setEnableWireless` only when available and hatch-free · wireless parallel user-selectable (as `MTETranscendentPlasmaMixer`): `wirelessParallel`, right-click the wireless button opens a selector, max `Integer.MAX_VALUE` · 3600s ramp off: `getEuModifier()`/`getSpeedBonus()` fixed `0.75`, `running_time` neither incremented nor decayed · GUI button toggles `EnableWireless` via `GTGuiTextures.TT_SAFE_VOID_ON/OFF`, gated by `EnableWirelessFunc`.

## 11. Gotchas

* Tile factory: `newMetaEntity(...)` needs a **String constructor** (`public MTDTPF(String aName) { super(aName); }`) returning `new MTDTPF(mName)` — as `MTELargeFusionComputer5`.
* Front overlay active-aware like LFC5 `getTextureOverlay()`: `Casing_Machine_Screen_Rainbow` active / `Casing_Machine_Screen_1` idle; other faces = DTPF casing.
* `getMaxParallelRecipes()` ≠ the recipe *tier* restriction — follow the intended parallelism.
* Tier block lookups (`Loaders.compactFusionCoil`, `GregTechAPI.sBlockCasings*`) appear after mod pre-init → enum/static initializers must load after blocks (normal MTE order fine).
* Structure-writer shapes stay under `// spotless:off ... spotless:on`.
* **`getInputVoltageTier()` = plain energy hatches only.** `MTEMultiBlockBase#getInputVoltageTier()` iterates `mEnergyHatches`, returns **0** when empty (exotic hatch — laser / multi-amp → `mExoticEnergyHatches`) or tiers differ; that 0 in a recipe gate rejects *every* recipe with `insufficientMachineTier` ("配方需要更高等级的结构方块。此配方需要：N" / "needs a higher tier structure block") — looks like a structure bug. Real hatch tier: `getExoticAndNormalEnergyHatchList()` → `MTEHatch#getInputTier()` (`mTier`, a `GTValues.VN` index, plain *and* exotic); `MTMultiMachineBase#getEnergyHatchTier()` keeps the highest and every MessTech gate/bonus reads it (`MTAssFactory` component tier + Tier 1 parallel; `MTNanoScaleFoundry` assembly matrix module gate, etching speed bonus, work-thread overclock count).
* **Never "fix" the base class for one machine — override in the concrete MTE.**
* **Always end `createTooltip()` with `.toolTipFinisher()`** — it alone builds the arrays (`iArray`/`sArray`/`hArray`); without it `getStructureDescription()` (= `getTooltip().getStructureHint()`) is `null` and BlockRenderer6343's NEI preview (`GTGuiMultiblockHandler.findHints`) crashes with `NullPointerException: Cannot read the array length` (MTDTPF, 2026-08-21).
* **`getMaxParallelRecipes()` > 0**: `ParallelHelper.determineParallel()` bails on `maxParallel <= 0` — recipe matches, machine never starts (no progress, empty GUI); MTDTPF 0 → 1.
* **No absurd caps (millions)**: O(parallel) loops (`ParallelHelper.calculateChancedOutputMultiplier`: `for roll < parallel`, plus output splitting) freeze the game without a crash/log; `availableEUt / recipeEUt` + input counts limit for real.
* **`MTParallelHelper` re-declares `ParallelHelper`'s fields as privates** (GT++ fork) → inherited methods read never-assigned parent fields. `copyInputs()` runs when consumption is off (`if (!consume) copyInputs();`), wireless MTDTPF does that — no input bus → parent `null` → every-tick `NullPointerException: Cannot read the array length because "this.itemInputs" is null` (`ParallelHelper.copyInputs`, `MTDTPF$1.createParallelHelper` → `setConsumption(false)`); now overridden to clone its own arrays. Still broken, recorded (fixing changes balance): `setMaxParallelCalculator`/`setInputConsumer` write parent fields = no-ops on the fork (its `determineParallel` reads its own); `setEUtModifier(float)` dead — GT5U takes a `double`, so `euModifier` never reaches `determineParallel` → parallels use the undiscounted `recipe.mEUt`. New consumption-disabling machine or state-touching `ParallelHelper` method → check the fork. The one batch mode (`Config.machines.ENABLE_ONE_BATCH_CALCULATOR`) has its own early path in `determineParallel()` (parallel cap and power-based cap ignored, `calculator.setParallel(currentParallel)` used directly) and `MTProcessingLogic#applyRecipe` (the GT method copied with the EU/t divided by the parallel count) - see `knowledge.md` §MTParallelHelper.
* **`IVoidable.canDumpItemToME/canDumpFluidToME` live in `MTMultiMachineBase`** → every MessTech multiblock is `IVoidable` regardless of the active GT5U `MTEMultiBlockBase`; mirrors upstream (`tmp/GT5-Unofficial-master/.../MTEMultiBlockBase.java` ~line 3194/3218) incl. `hasPhysicalSpace()`/`getCheckMode()`. IDE conflict → re-import Gradle (`GTUtility.FluidId` is `public abstract static class` in `GTUtility.java`).
* **`ICasing#toStack(int)` ignores the amount** in GT5U 5.09.54.205 (`ICasing.toStack` always returns `new ItemStack(block, 1, meta)`) — `.toStack(8)` silently gives a single casing; recipes that need more must build `new ItemStack(casing.getBlock(), amount, casing.getBlockMeta())` (`GTRecipes#casingStack`), which also means older `.toStack(N)` recipes in `GTRecipes` under-count.
* **`HatchElementBuilder.atLeast(...)` counts only judge survival auto-place**, not the structure check: `check()` always calls the adder for any matching hatch, so an unlimited number of those hatches may be placed manually; the `shouldReject` count only stops the auto-builder, and the `buildAndChain(fallback)` element then fills the remaining positions with the fallback block. Structure requirements ("at least one output bus and one output hatch") therefore have to be enforced in `checkMachine` by inspecting `mOutputBusses` / `mOutputHatches` after `checkPiece`.
* **Structure-writer "special tiles" are printed as the tile-entity class name** (e.g. `BWTileEntityMetaGeneratedBlocksCasingAdvanced`), because StructureLib has no `ofSpecialTileAdder` any more (the shape language only has `~`; special elements are Java objects). Identify the actual casing by the class/meta and use `ofBlock(...)` / `Casings.X.asElement()`; BartWorks advanced casings are `WerkstoffLoader.BWBlockCasingsAdvanced` with the werkstoff id as meta (`Casings.ReboltedNaquadahCasing` = meta 32090).
* **A hatch's `casingIndex` must be a casing texture id below 16384** (`casingTexturePages` is `ITexture[128][128]`). `MTEHatch#updateTexture` splits the id as `texturePage = id >> 7` **without masking** (`saveNBTData`/`loadNBTData` commute the same way), so a BartWorks advanced casing id such as `Casings.ReboltedNaquadahCasing.getTextureId()` = 32090 makes `MTEHatch#getCasingTexture` read `casingTexturePages[250]` → `ArrayIndexOutOfBoundsException: Index 250 out of bounds for length 128`, thrown from the ISBRH pass (seen through Angelica while rendering the NEI multiblock preview). `Textures.BlockIcons.getCasingTextureForId` masks with `& 0x7f` and works, so such a texture is fine as a **controller/block** texture (`getCasingTexture()`, `ICasing#asElement()`), but never as `HatchElementBuilder#casingIndex`. Stay on GT/TT ids, e.g. `Casings.HighPowerCasing` (`gt.blockcasingsTT:0`, TecTech texture page 8 → id 1024).

## 11.5 Nano Computing Center (`MTComputingCenter`)

Computation multiblock on `CalculateMultiMachineBase`, three modes: **Nano Computing** (QC style, data input auto-forwarded, wireless output toggled with a wire cutter) · **Research Station** (`F` holder hatch + Data Stick in the controller slot, finished stick → OutputBus) · **Scanner** (`RecipeMaps.scannerHandlers` / `RecipeMaps.scannerFakeRecipes`, same packet-loss logic).

* Structure: `A` = normal hatches + Uncertainty + Data + WirelessComputationOutput, `D` = `MTHatchRack`, `F` = research holder or air.
* `MTRecipeMaps.computingCenterFakeRecipes`: `MTMachineLoader` calls `MTRecipeMaps.populateComputingCenterFakeRecipes()` after `MTEHatchRack.run()`; copied recipes are **re-tagged to the map's default `RecipeCategory`** — GT's NEI handler reads `backend.getRecipesByCategory(defaultCategory)`, not `getAllRecipes()`. Title `mt.recipe.computingcenter`.
* `MTHatchRack` extends GT5U `MTEHatchRack`: `tickComponents(oc, ov)` scales the stock `(oc-ov)^2` term by `PENALTY_FACTOR = 0.8f` (20% softer), stats from the QC fake-recipe pool (`QuantumComputerRecipeData`, cached by unique id); `getDescription()` drops the "TecTech: Elemental Matter" line.
* `checkComputationTimeout` (grace/decay/full windows) ported into `MTComputingCenter.onRunningTick`, NBT `ticksUntilPacketLossFail` / `packetLossDecayFrom`; wireless `WirelessComputationPacket.uploadData` with a real world tick (base meta tile entity cast to `TileEntity`).
* Heat `machineHeat` (max `10,000,000`, NBT): overclock cannot be disabled, racks stay locked, screwdriver mode switching blocked until it dissipates. **Active** `consumeCryotheumForHeat()`: one `10,000` heat batch per `100,000L` Gelid Cryotheum, looping while possible (running and stopped); **passive** (stopped only) `machineHeat -= max(machineHeat/1000, 20)` per second (stock rack decay); above max → all components melted, `ExtraShutdownReason.OverHeating`.
* Manual shutdown (GUI on/off, redstone) never reaches `stopMachine` → `onPostTick` clears stale Waila/GUI values (`eAvailableData`, `mEUt`, research progress) while inactive (`resetStoppedDisplay()`); controller removed with heat → `meltAllComponents`; `setRacksActive(false)` no-ops while `machineHeat > 0`, `onPostTick` re-asserts `setRacksActive(true)` while hot (chunk reloads).
* Tooltip: `AuthorDynamic.registerOn` adds `Add by: <animated MessTech>` under the author line, the name a purple gradient via gtnhlib `AnimatedTooltipHandler.animatedText`.

## 11.6 Assembly Factory (`MTAssFactory`)

* Two pools, screwdriver-switched (or the mode button):
  * Mode 0 **Component Assembly Line**: `GoodGeneratorRecipeMaps.componentAssemblyLineRecipes`, generic `ProcessingLogic`; recipe casing tier (`mSpecialValue`, the recipe's `COAL_CASING_TIER`) capped by the energy hatch tier = **highest** `MTEHatch#getInputTier()` over `getExoticAndNormalEnergyHatchList()` (`MTMultiMachineBase#getEnergyHatchTier()`, laser / multi-amp included), never `getInputVoltageTier()` (plain only, gates at 0); same helper → Tier 1 parallel `3^能源仓等级`.
  * Mode 1 **Assembly Line**: authorised recipes from the data stick + Data Access hatches, matched against the input buses by `MTAssemblyLineMatcher`, winner to `ProcessingLogic`; only `LevelTier == 2`; no single-recipe locking (`supportsSingleRecipeLocking()` false, like `MTEAssemblyLine`).
* Structure: `F` = buses/hatches/energy + **Data Access hatches**; `I` = Assembly Matrix Blocks (Tier 1 / Tier 2) → `LevelTier`; GUI `MTAssFactoryGui`.
* NEI pool `MTRecipeMaps.assFactoryAssemblyLineRecipes` ← `MTRecipeMaps.populateAssFactoryAssemblyLineRecipes()` ← `GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes` (what a data stick resolves against): fake recipe per definition = one page, special slot `displayDataStick` = GT's every-`assemblylineVisualRecipes` stick, filled in `RecipeAssemblyLine#reInit()` at postInit (before `serverStarted`, else on item remap), cached in `DISPLAY_DATA_STICKS` per definition (`newDataStickForNEI` → `RecipeAssemblyLine.dataSticksForNEI`, every world load); `RecipeMapBackend#filterFindRecipe` rejects fakes; `displayInputSpec` alternatives stay `ItemStack[]`, dropped by `GTRecipe_WithAlt#buildItemInputCache` for non-ore-dictionary slots (`mOreDictIds[i] >= 0`), NEI reads `mOreDictAlt`.
  * **no runnable recipes**: per-combination = cartesian product of the slots (six digits; Wetware Mainframe: five ASMD/XSMD slots + six superconductor wires) → heap exhaustion on `serverStarted`; resolved on the fly.
* Assembly Line needs a Data Access hatch: `machine.assfactory.error.need_data_access` / `need_tier2`.
* **Unordered**: all input buses in one pool keyed by `GTUtility.ItemId` (exact stack, NBT ignored, wildcard-damage alias), each slot the alternative leaving the most parallels; result = `GTRecipe` from `GTRecipeBuilder.builder()` → `ProcessingLogic#findRecipeMatches` (parallels, perfect overclock, void protection, debug/phantom/ME buses and consumption stay shared); debug buses work natively (`ProcessingLogic` / `getStoredInputs`).
* **Reuse the existing inventory methods**: `depleteInput(ItemStack)` / `depleteInput(FluidStack)` (regular, debug/phantom, ME); never `ItemStack.stackSize` / `FluidStack.amount` / `setInventorySlotContents` on bus slots.
* Front texture: Advanced Molecular Casing + Quantum Force Transformer overlay. Tooltips: **Multi-Amp Energy (多安能源仓)** — lang files use 多安能源仓 instead of 异域能源仓.

## 12. Registering a machine + animated authors

### Registering an MTE so it shows up in game

The MTE **constructor registers the id** (`MetaTileEntity(int aID, ...)`: "This registers your Machine at the
List"); no `registerMetaTileEntity` call. Pattern (`goodgenerator/loader/Loaders.java`,
`gregtech/loaders/preload/LoaderMetaTileEntities.java`):

```java
ItemStack stack = new MTDTPF(ID, "mt.dtpf.controller", "MT DTPF").getStackForm(1L);
```

* `getStackForm(long)` → `new ItemStack(GregTechAPI.sBlockMachines, size, metaTileID)`.
* **IDs**: GT 0..2047 reserved; addons **32000+** (goodgenerator 32019..32029, NeutronAccelerator 32003.., Wires
  32737+, ...).
* **When**: `postInit` if statics touch **other mods' blocks** (MTDTPF's `LevelTier` reads
  `goodgenerator.loader.Loaders.compactFusionCoil`); MTE javadoc says "Load phase", cross-mod refs are the exception.

### Animated "Author:" tooltip (gtnhlib)

`AnimatedTooltipHandler` (`com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler`): animated line at the **end** of
a tooltip, client-only (`@EventBusSubscriber(side = CLIENT)`).

* primitives: `text(String)`, `animatedText(text, posstep, delay, colors...)`, `chain(Supplier...)`, colors
  (`AQUA`, `RED`, `BOLD`, ...), `addItemTooltip(ItemStack, Supplier<String>)`.
* `animatedText`: char i → `colors[(i*posstep - offset) mod len]`; `posstep` = band speed, `delay` = ms/step.
* **GT5U 5.09.54.264 removed author credits**: the `GTAuthors` class, the `gt.author`/`gt.authors` lang keys and the
  author lines of `MultiblockTooltipBuilder` (`toolTipFinisher(String...)` still exists but ignores its arguments,
  `addAuthorsIfNEI` is gone). Addons that want an author line build it themselves; MessTech keeps
  `com.MessTech.common.util.AuthorDynamic`, which formats the line locally with
  `StatCollector.translateToLocalFormatted("messTech.author", GREEN + author + RESET + GRAY)` (same "Author: %s"
  shape `GTAuthors.buildAuthorsWithFormatSupplier` used to produce) and then hands it to `AnimatedTooltipHandler`.

`com.MessTech.common.util.AuthorDynamic` = self-contained "czqwq" author: rainbow ramp + color wave +
**ping-pong offset** (triangle wave) → the band wobbles **left-right**, not scrolling one way.
`AuthorDynamic.author_czqwq()` → `Supplier<String>`; `AuthorDynamic.registerOn(stack)` = the `messTech.author` line
above + the animated `messTech.addBy` line.

### MessTech's actual refactor (current layout)

Registration moved out of `CommonProxy` into three pieces:

| file | responsibility |
|---|---|
| `com/MessTech/common/misc/MTItemList.java` | GT-style `ItemList` enum (`gregtech.api.enums.ItemList` pattern): one entry per machine (`MTDTPF`), `set(Item/ItemStack/IMetaTileEntity)` + `get(int, Object...)` |
| `com/MessTech/common/machine/loaders/MTMachineLoader.java` | `loadMachines()`: `MTItemList.MTDTPF.set(new MTDTPF(ID, ...).getStackForm(1L))`, then `AuthorDynamic.registerOn(MTItemList.MTDTPF.get(1))` |
| `com/MessTech/init/CommonProxy.java` | `postInit = MTMachineLoader.loadMachines()` (cross-mod blocks) |

ONE path (the loader calls `AuthorDynamic.registerOn(...)`, never a second inline `addItemTooltip`): `postInit` →
`MTMachineLoader.loadMachines` → `MTItemList.set(stack)` + `AuthorDynamic.registerOn(stack)` →
`AnimatedTooltipHandler.addItemTooltip(stack, AuthorDynamic.authorLine(...))` (the local `Author:` formatter, §12).

## 13. GT5U API knowledge from recent work

* `MTEMultiBlockBase#getWailaBody`: running-mode line from `tag.getString("mode")`; localized name =
  `tag.setString("mode", getMachineModeName(machineMode))` (`tag.setInteger("mode", machineMode)` →
  `运行模式: 0/1`).
* `ProcessingLogic#findRecipeMatches` = `recipeMap.findRecipeQuery().items(...).fluids(...).specialSlot(...).findAll()`;
  fake recipes (`addFakeRecipe`) are **not** found → a custom-pool machine needs real recipes
  (`addRecipe(copy, false, false, false)`).
* NEI `GTNEIDefaultHandler` reads `recipeMap.getBackend().getRecipesByCategory(defaultCategory)`; copied maps need
  `recipe.setRecipeCategory(targetDefaultCategory)`, else the tab stays empty.
* Scanner (Research Station): `RecipeMaps.scannerHandlers.findRecipe(this, holder, special, fluid)` →
  `GTScannerResult`; computation `researchTime * 2^(tier-1)`, `eRequiredData = 1`, EU =
  `max(|recipeEUt|, TierEU.RECIPE_UV)`.
* `MTEHatchDataAccess#getAssemblyLineRecipes()` = recipes from the hatch's data sticks; the original Assembly Line
  is **ordered** by input-bus slots → an "unordered assembly line" delegates to normal `ProcessingLogic` /
  `getStoredInputs`.
* `MTEHatchInputBusDebug`: phantom inventory (`getStackInSlot()` returns copied stacks of huge size,
  `Integer.MAX_VALUE` when not finite); `getStoredInputs()` / `depleteInput()` handle it.
* `COAL_CASING_TIER` aliased into `GTRecipe.mSpecialValue` (`GTRecipeConstants.SPECIAL_VALUE_ALIASES`).
* `RecipeMaps.scannerFakeRecipes` / `RecipeMaps.assemblylineVisualRecipes` = NEI/visual pools, not searchable by
  normal processing unless copied into a real map.
* `WirelessNetworkManager`: `processInitialSettings` (owner UUID), `getUserEU`, `addEUToGlobalEnergyMap`; validate
  first (`checkWirelessPower`/`validateWirelessPowerForRecipe`), then `startWirelessRecipe` (deducts + consumes).
* **Registering an addon GT tool/wrench** (the 彩虹小扳手 pattern; files under `gregtech/api/items/MetaGeneratedTool.java`,
  `gregtech/api/interfaces/IToolStats.java`, `gregtech/common/tools/ToolWrench.java`,
  `gregtech/common/items/MetaGeneratedTool01.java`, `gregtech/common/GTClient.java`):
  * item: `extends MetaGeneratedTool`, constructor `super("metatool.<name>")`. `GTGenericItem`'s constructor already calls
    `GameRegistry.registerItem(this, "gt." + unlocalized, GregTech.ID)` - do **not** register it again. GT's client init
    then registers `MetaGeneratedToolRenderer` for every `MetaGeneratedTool.sInstances` entry, so rendering needs no
    `IItemRenderer` registration.
  * tool type: `addTool(aID, name, tooltip, IToolStats, ToolDictNames...)`; `aID` must be **even** (`aID + 1` is the
    "empty"/discharged state, names are auto-added). `addTool` also registers the ore-dict name (pass
    `ToolDictNames.craftingToolWrench`); `GregTechAPI.registerWrench(stack)` (or `registerCrowbar`, ...) adds it to
    `GregTechAPI.sWrenchList`, which gates machine right-click wrenching (`BaseMetaTileEntity`/`BaseMetaPipeEntity`), the
    tool-box slot, the pipe overlay and maintenance hatches. `GTItemIterator` skips every `GTGenericItem`, so no
    auto-classification can fight the explicit registration.
  * stats are per tool type on `IToolStats`, not per stack: mining speed = `getSpeedMultiplier() * material.mToolSpeed`,
    harvest level = `getBaseQuality() + material.mToolQuality` (`mToolQuality` is a **byte**), durability =
    `100 * material.mDurability * getMaxDurabilityMultiplier()` stored as `GT.ToolStats.MaxDamage` (NBT, **long**). Exact
    values therefore need the material for the neutral parts and, for the durability, writing the stack NBT.
  * `getSubItems` is final and always adds the bare stack plus a **Neutronium**-material copy to the creative tab (NEI
    follows). To give those the intended stats the only hook is `isItemStackUsable(ItemStack)` (non-final; GT already
    uses it for enchantment fix-up): normalise the `GT.ToolStats` NBT there, idempotently. Getter/tooltip/render paths all
    pass through `getToolStats` -> `isItemStackUsable` first.
  * icon: `IToolStats#getIcon(isToolHead, stack)` returns an `IIconContainer`, e.g.
    `Textures.ItemIcons.custom(domain, path)` for `assets/<domain>/textures/items/<path>.png` - no `getIconFromDamage`
    (it is final and returns null). The container must be created **before** GT's item icon load:
    `ItemIntegratedCircuit#registerIcons` consumes `GregTechAPI.sGTItemIconload` once `GregTechAPI.sPostloadFinished` is
    set, i.e. during the client item-atlas load - creating the container in preInit is fine, doing it lazily at render
    time is too late. Animated item textures work there: vertical frame strip + `.mcmeta` `{"animation":{"frametime":N}}`
    (N in ticks; 2 = 100 ms), `TextureAtlasSprite` updates the UVs each tick, and `getRGBa` is the tint
    (`{255,255,255,255}` = untinted).
  * carrier material without items: `MaterialBuilder` in an `IMaterialHandler` added from the `@Mod` constructor (same
    timing as Chaos Matter - `Materials.init()` runs in GT's preInit). A material with `mMetaItemSubID == -1` and no
    `add*Items()` call generates nothing and only supplies the NBT `PrimaryMaterial` name.

## 14. MessTech's IC2/GT fuel rods (Transcendent Metal)

* `com.MessTech.common.items.MTFuelRod extends gregtech.api.items.ItemRadioactiveCellIC` (burnable),
  `com.MessTech.common.items.MTDepletedFuelRod extends gregtech.common.items.ItemDepletedCell` (inert),
  `com.MessTech.common.items.MTFuelRodItemRenderer` (client `IItemRenderer`, `TranscendentalMetaItemRenderer`'s
  oblique-axis tumble: 3.5 deg per client tick around (0.3, 0.5, 0.2), then 180 deg around X, pivot = quad centre,
  angle from `GTMod.clientProxy().getAnimationRenderTicks()`); instances + stats in
  `com.MessTech.common.items.MTItems` ("Transcendent Metal fuel rods").
* GT over IC2: `ItemReactorUranium`'s constructor needs an IC2 `InternalName` and hardcodes the IC2 depleted stacks;
  `ItemRadioactiveCellIC` is the public addon API (custom depleted `ItemStack`, `IReactorComponent`, radiation, NBT
  `advDmg`, vanilla 0..99 damage bar, NEI nuclear fake recipe).
* Gotcha: `GTGenericItem`'s constructor already calls `GameRegistry.registerItem(this, "gt." + aUnlocalized)` (the
  3-arg FML method ignores modId in 1.7.10) → do NOT re-register in `MTItems.registerItems()`. Lang keys:
  `gt.rodTranscendentMetal.name` (+ `...2` / `...4` / `...Depleted...`).
* Stats (`MTItems`): `maxDamage 250_000`, `radiation 32`, `MOX=true`, `heatBonus=2`, **`heat 4_096`**; energy per
  size: `ENERGY_SINGLE 9_000_000`, `ENERGY_DUAL 4_500_000`, `ENERGY_QUAD 3_000_000`. MTReactor:
  `output += pulses * sEnergy`, `EU/t = output * 5 * balance/energy/generator/nuclear`
  (`MTReactor#getEuPerOutput()`; GTNH's `config/IC2.ini` ships 5.0 → 25 EU/t per output point, as in
  `TileEntityNuclearReactorElectric#getOfferedEnergy()` and GT5U's NEI nuclear fake recipe). Lone rod:
  `1 + cells / 2` pulses per `cells` pass → `EU/t = sEnergy * cells * (1 + cells / 2) * 25` = **single 225,000,000 /
  dual 450,000,000 / quad 900,000,000 EU/t** (exact 1x/2x/4x, single = 6.7A UIV) = the NEI fake-recipe number. Heat
  per cycle `cells * triangular(1 + cells / 2) * sHeat` → 4,096 / 24,576 / 98,304 HU/s bare; ~2.8 h on one
  `ItemList.neutroniumHeatCapacitor` (1G Neutronium Heat Capacitor, 1,000,000,000 HU).
* `MTReactorAccessHatch.isFuelRod` widened to `ItemReactorUranium || (ItemRadioactiveCell && !ItemDepletedCell)` →
  GT rods get the fuel durability line in the slot sub-panel.

## 15. MTReactor heat control hatch (`MTReactorHeatHatch`)

* `com.MessTech.common.machine.hatch.MTReactorHeatHatch extends MTEHatch`: 0 slots (as `MTEHatchMuffler`), tier
  EV..UIV, decal `assets/messtech/textures/blocks/hatch/Hatch_Reactor_Temp_Control.png` (base = multiblock casing,
  `updateTexture(aBaseCasingIndex)` + `withOverlay`, as `MTReactorAccessHatch`).
* Ceilings (`HEAT_CAPACITY`, index `tier - MIN_TIER`): EV 10,000 / IV 20,000 / LuV 50,000 / ZPM 100,000 / UV
  200,000 / UHV 500,000 / UEV 1,000,000 / **UIV `MTReactorHeatSystem.MAX_HEAT_CAPACITY` = 50,000,000**; EV = the old
  hardcoded default.
* **UIV finite**: `heat / maxHeat` must reach the 40 / 50 / 70 / 85 / 100% thresholds and `Integer.MAX_VALUE`
  (2.1e9) never does (risk model silently off); 50,000,000 exceeds one 9x6 page (IC2's largest heat storage item is
  well under 100,000) yet keeps every threshold reachable; `MTReactorHeatSystem.getEffectiveMaxHeat()` clamps
  `ceiling + plating bonus` too.
* Structure: custom `IHatchElement` (`MTReactor.REACTOR_HEAT_HATCH`, `name() =
  "mt_reactor_heat_hatch"`, `count()` = `getReactorHeatHatchCount()`) and added to the `'K'`
  `HatchElementBuilder.atLeast(...)` group (I/O buses, hot coolant output) → any of the 0-25 shell casings (`'C'`
  group = access hatches, input hatch, coolant input). `checkMachine()`: **exactly one** - empty →
  `missing_heat_hatch`, `size() > 1` → `too_many_heat_hatch` (our lang keys; a duplicate cannot be resolved by
  picking one → structure error).
* **Heat hatch tier caps the access hatch tier** (`MTReactor.checkAccessHatchTier()`, structure error
  `machine.mtreactor.structure.error.access_hatch_tier`): an access hatch above `getHeatControlTier()` invalidates
  the structure → pages cost heat headroom. Check in the controller: a hatch only carries data, hence the data stick
  replays settings across tiers unvalidated (`docs/knowledge.md`).
* Ceiling: `MTReactor.getReactorHeatCapacity()` = (lowest) hatch value, `DEFAULT_HEAT_CAPACITY = 10,000` while
  broken, clamped to `MAX_HEAT_CAPACITY`; `getHeatControlTier()` = (highest) hatch tier → passive cooling + access
  hatch limit. `ReactorContext.bind()` / `bindSimulation()` seed `maxHeat`. **Plating stacks IC2-style**:
  `ReactorContext.setMaxHeat()` resolves IC2's `setMaxHeat(getMaxHeat() + maxHeatAdd)` into
  `MTReactorHeatSystem.setPageMaxHeat()`, bonus **per page** (`pageBonus`, keyed like the heat store) - a 9x6 page
  is an independent reactor grid. Largest `maxHeatAdd` wins per page (layout independent);
  `getPageMaxHeat(owner, page)` = hatch ceiling + page bonus, `MAX_HEAT_BONUS_PER_ITEM` (100,000) per item,
  `MAX_HEAT_CAPACITY` overall; `resetPageBonus()` before the page's heat pass → a removed plating stops counting at
  once. Was a no-op → plating was a pure downside (higher heat effect modifier, unchanged ceiling, bigger
  explosion).
* **Hatch change rescales heat, never detonates**: `MTReactorHeatSystem.onCapacityChanged()` scales every page by
  `new ceiling / old ceiling` and clears running countdowns; with the old plain `heat / maxHeat` check 900,000 HU on
  a UHV hatch detonated one cycle after switching to EV.
* **`Integer.MAX_VALUE` sentinel** (the `mPageMaxHeat` era): `getReactorHeatCapacity()` scans from
  `Integer.MAX_VALUE` for the lowest hatch value → "no hatch" needs a separate `boolean found` flag, so the starter
  value cannot read as a ceiling.
* Heat math still `power = heat / maxHeat` per page, but `>= 1.0F` no longer explodes on the spot (the page arms a
  **meltdown countdown**) and `>= 0.85F` still ignites the surroundings. World effects (0.85 ignite air / 0.7
  irradiate entities / 0.5 evaporate water / 0.4 ignite wood) run **once per cycle** on the hottest page's ratio and
  modifier, not per page → an N-page reactor no longer irradiates an entity N times; detonation stays per page.
* **Meltdown countdown, 600 ticks (30 s)** (`MTReactorHeatSystem.MELTDOWN_COUNTDOWN_TICKS`): `armCountdown()` sets
  the absolute deadline `now + 600`; `MTReactor.armCountdowns()` runs at every material cycle's end, keeps a still
  at/over-ceiling page's deadline (pinning heat cannot postpone the explosion), `clearCountdown()` drops a page back
  under. Absolute because a page is visited once per cycle - "20 ticks per visit" stretched the window to 30 s x
  page count. `explodeExpiredCountdowns()` runs every tick in `MTReactor.onPostTick()` → detonation 600 ticks after
  first crossing, machine on or off (the heat cycle runs either way); `resetCountdown()` cancels on a player action
  (hatch change, fluid mode toggle).
* **Countdown survives reload** as *remaining* ticks, never an absolute deadline (the tick counter is not
  persisted): `saveNBTData()` writes `mtReactorArmedCountdowns` (`{x, y, z, page, ticks}`),
  `MTReactor.loadNBTData()` hands its counter over with `setLoadClock(getTimer())` before the store reads,
  `refreshCountdownClock()` re-anchors every deadline onto the running counter. Resumes where it stopped after a
  chunk unload or restart, ticks only while loaded, cannot extend or shorten the 30 s window (restored `ticks`
  clamped to [1, 600]; expired entries unsaved). A backwards counter jump = reset: every deadline re-expressed by
  the window it had left.
* **Passive cooling per hatch tier** (`MTReactorHeatSystem.PASSIVE_COOLING_PER_SECOND`, index `tier - MIN_TIER`,
  EV..UIV): 83 / 167 / 417 / 833 / 1,667 / 4,167 / 8,333 / 200,000 HU/s. The seven real tiers are exactly
  `round(ceiling / 120)` = one ceiling per 2400 ticks (two minutes): too slow to out-cool a running reactor, fast
  enough that shutting down saves it. UIV decoupled on purpose: `ceiling / 120` would be 416,667 HU/s (faster than
  the reactor makes heat), while 200,000 HU/s drains 50,000,000 in 250 s. Per tick as a fraction (`rate / 20`,
  accumulated in `coolingProgress`; truncation made every tier below ZPM cool at the same 4 HU/tick), only for pages
  carrying at least 5% of the hottest page's heat (`PASSIVE_COOLING_SHARE_THRESHOLD`), halved during a countdown.
  `MTReactorHeatSystem.tick()` runs from `MTReactor.onPostTick()` every tick → works with the machine off (the only
  way to cool a hot reactor down).
* **Fluid ("wet") mode**: screwdriver toggle (`onScrewdriverRightClick`, chat keys `machine.mtreactor.fluidmode.on` /
  `.off`), clears a running countdown; then requires `MTReactorCoolantInputHatch` + `MTReactorHotCoolantOutputHatch`
  (`ExtraShutdownReason.MISS_COOLANT_HATCH` soft-stop, not a structure error). Exchange
  `MTReactorHeatSystem.coolantForHeat()`: `COOLANT_PER_BUFFER_UNIT = 1` → **1 mB of `ic2coolant` per 1 HU of
  buffered component heat**, same amount of `ic2hotcoolant` out. MessTech's ratio, not IC2's (`huOutputModifier`,
  2 mB/HU default): 1:1 reads "as much coolant per second as the core makes heat per second". Unabsorbed heat
  returns as `getPendingEmitHeat() / 2` onto the page (IC2's `addHeat(huOutput / 2)`); dry mode has no coolant, so
  the same half is pushed back instead of dropped. Fluids from GT accessors
  `MTReactorCoolantInputHatch.getCoolantFluid()` (cold), `GTModHandler.getHotCoolant(1)` (hot) - never
  `GTModHandler.getIC2Coolant(long)`, which falls back to water without IC2 (`GTModHandler` 212-215).
* **Both IC2 fluid hooks are live** (what makes the mode matter): `ReactorContext#isFluidCooled()` reports the mode
  (`ItemReactorMOX#getFinalHeat` doubles MOX heat output above 50% heat only in wet mode);
  `ReactorContext#addEmitHeat()` buffers instead of dropping (`ItemReactorVent` / `ItemReactorVentSpread` send removed
  heat there → a vent's contribution is mode-dependent). Condensators, lithium cells and heat switches query neither
  hook (checked against `tmp/ic2-decompiled`, constant pool of `tmp/ic2-242638-2353971-deobf.jar`) → identical in
  both modes.
* **Heat effects never replace a block** (the IC2 original did): `>= 0.85F` only places `Blocks.fire` into **air**
  (`isAir`); the `Blocks.flowing_lava` replacement is gone. IC2 turned any non-air block into fire or lava - GT
  casings use `MaterialCasings`, so casings became fire and stone/ground lava, dissolving the multiblock. Fire in
  air cannot break the structure: every position needs a specific non-air block, unchecked `' '` positions ignore
  it. `>= 0.5F` water removal and `>= 0.4F` wood/leaves/cloth ignition stay (never touch a casing/hatch).
* Explosion power = **IC2's number remapped onto the Draconic Evolution scale**: `explodeReactor()` mirrors IC2's
  `explode()` (start at 10, add each component's `influenceExplosion`, multiply the `0 < influence < 1` ones into
  `boomMod`, then `* hem * boomMod`), clamps to `Config.machines.reactor.REACTOR_IC2_EXPLOSION_POWER_LIMIT` (default 45, IC2's
  `protection/reactorExplosionPowerLimit`; was a hardcoded `45.0F`; IC2's ini still **not** read - no IC2
  dependency/mixin, the option only lets a pack change it), and the blast is `MTExplosionDE`:
  `dePower = min(boomPower, ic2Limit) / ic2Limit * Config.machines.reactor.REACTOR_EXPLOSION_DE_POWER_LIMIT` (default 40, `Reactor`
  category, floored at 1). `explodeMultiblock()` + `doExplosion(GTValues.V[8])` on the access hatches remove the
  machine first; then every page's heat is cleared **on purpose** (the controller TE + heat store outlive
  `explodeMultiblock()` for the rest of the tick), after settling the cycle's EU/t (`mPendingOutput`) so a
  melted-down page does not cost the reactor's whole output.
* **IC2's `ExplosionIC2` dropped** ("powerless" in game): IC2 shoots `2 * steps^2` rays with
  `steps = ceil(pi / atan(0.4 / power))` - ~250k at power 45, ~**25 million** at 450 - each stepping through blocks
  until its energy runs out (up to 900 in air) and writing the destruction list only *after* every ray finished →
  power 450 freezes the server for minutes and removes nothing; only the small `doExplosion(V[8])` craters vanish.
  DE scales instead: one ring per tick, one column per ring block.
* `MTExplosionDE` / `MTExplosionDETrace` = 1:1 port of Draconic Evolution's `ReactorExplosion` /
  `ReactorExplosionTrace` (+ its `IProcess` / `ProcessHandler` kept as `IMTProcess` / `MTProcessHandler`, ticked on
  `TickEvent.ServerTickEvent`): ring to `power * 10` blocks of radius, each ring block spawning a column trace that
  blasts downwards (`energy = power * 10`, resisted per block, fire/lava puddles at the end) then upwards
  (`energy = power * 20`), damaging each entity passed with `power * 100` of `damage.messtech.reactorExplode` (armour
  bypassing, creative allowed). Hard cap `MTExplosionDE.MAX_POWER = 40` (twice DE's own 2..20; 1 power = ~10 blocks
  radius) → worst meltdown vaporises a 400 block radius. Cost: 40 power = ~500k columns, tens of millions of block
  updates over 400 ticks; lower `REACTOR_EXPLOSION_DE_POWER_LIMIT` if too heavy.
* **Never cache the ceiling**: the per-page `int[] mPageMaxHeat`, written only during a reactor cycle / stability
  simulation of a non-empty page and persisted to NBT, left an empty, idle or fresh reactor showing the old 10,000
  and old saves reloading the stale array. Gone; `MTReactor.getHottestPageMaxHeat()` reads
  `getReactorHeatCapacity()` live (GUI sync value, Waila, scanner info, heat percentage) → a hatch swap shows
  immediately.
* **Extra `× 2` on purpose** (`MTReactor.MESSTECH_OUTPUT_MULTIPLIER`): `getEuPerOutput()` =
  `IC2_EU_PER_OUTPUT * ConfigUtil.getFloat(MainConfig.get(), "balance/energy/generator/nuclear") * 2` (fallback
  `* 2 * 5` without IC2's config) → 50 EU/t per output point at GTNH's `nuclear = 5.0`, not IC2's 25. Deliberate
  balance decision (the source names the constant and points here), not config and not a bug - do not "fix" it back.
* Registration: `MTItemList.MTReactorHeatHatch_EV..UIV`, IDs `MT_ID + 24 .. + 31` (32424..32431), in
  `MTMachineLoader` like the access hatches. MTEs self-register in the `CommonMetaTileEntity(int, ...)` constructor
  → the ID must be unused (throws otherwise); access hatches hold `MT_ID + 16 .. + 23`. Both loops iterate the
  shared arrays `MTItemList.REACTOR_ACCESS_HATCHES` / `REACTOR_HEAT_HATCHES`, which
  `GTRecipes.addReactorRecipes()` also uses → recipe order cannot drift from registration order.
* Fluid hatch pair: `MTItemList.MTReactorCoolantInputHatch` at `MT_ID + 32` (32432),
  `MTItemList.MTReactorHotCoolantOutputHatch` at `MT_ID + 35` (32435) - `MT_ID + 33` / `+ 34` taken by
  `MTChemicalTwister` / `MTHugeChemicalReactor`, so the pair is split around them. One EV variant each by design
  (plumbing, not a tier gate: `MTReactorCoolantInputHatch.TIER = 4`, whose `8000 * 2^4 = 128,000 mB` tank holds
  twice what one cycle asks), outside the tier loops, listed in `MTItemList.REACTOR_FLUID_HATCHES`.

## 16. Reactor recipes (`GTRecipes.addReactorRecipes()`)

* Tier helpers: `ItemList.MACHINE_CASINGS[tier]` = machine casing (index 9 = UHV, GT's `Casing_MAX`);
  `OrePrefixes.circuit.get(Materials.<TIER>)` = tier circuit, the `GTModHandler.addMachineCraftingRecipe` mapping
  (tier 4 → `Materials.EV`, ..., tier 11 → `Materials.UIV`); `TIER_RECIPE_EU[]` / `TIER_CIRCUIT_MATERIALS[]` in
  `GTRecipes` hold both tables.
* Access hatch (assembler, `TierEU.RECIPE_<TIER>`, 2 min, circuit 1): tier casing + lever + 4 tier circuits.
* Heat control hatch (assembler, same EU/t + time, circuit 2): tier casing + 4 solid steel casings
  (`ItemList.Casing_SolidSteel`, "脱氧钢机械方块") + 4 tier circuits.
* Coolant pair (assembler, EV casing + 2 solid steel casings + 2 EV tier circuits, circuit 3, 2 min,
  `TIER_RECIPE_EU[MTReactorCoolantInputHatch.TIER]`): the `MTItemList.REACTOR_FLUID_HATCHES` loop builds the
  identical recipe for cold input and hot output, so neither end is gated; cheaper than the heat control hatch on
  purpose - plumbing, the coolant is the real cost.
* Reactor controller (assembler, `RECIPE_EV`, 2 min, circuit 1): 16 solid steel casings + 16 `Ic2Items.nuclearReactor`
  + 64 `Ic2Items.reactorChamber` + 144×256 molten lead (36864 L). `GTUtility.copyAmount(...)` on the IC2 static
  stacks (singletons, must not be mutated; `copyAmount` clamps at 64, use `copyAmountUnsafe` above).
* Fuel rods (mirrors `FissionFuelLoader`: single = canner, dual/quad = assembler):
  * single: The Core (`ItemList.RodNaquadah32`) + Avaritia Star Fuel (`getModItem(Avaritia, "Resource", 1, 8)`, meta
    8 in Avaritia 1.97/1.99) → canner, `RECIPE_UEV`, 32 s, guarded by `Mods.Avaritia.isModLoaded()`.
  * dual: 2 single rods + 4 `stick` Transcendent Metal, circuit 2, UIV assembler, 100 s.
  * quad: 4 single rods + 6 `stickLong`, circuit 4, UIV assembler, 100 s; alt 2 dual rods + 2 `stickLong`, UIV
    assembler, 50 s.
  * "NC 编程电路 N" = GT's programmed circuit with config N → `.circuit(N)` (N = the "not consumed" number in NEI).
* Depleted rod recycling (`addDepletedRodRecycling`, `centrifugeRecipes`, `RECIPE_UIV`, 50/100/200 s) copies GT's own
  depleted naquadah rod chances (100/50/50/25/100/100 %) scaled by rod size (single 1x / dual 2x / quad 4x): Transcendent
  Metal dust 4/8/16, Naquadah dust 8/16/32 (100 %) plus the same again at 50 %, Naquadria dustSmall 4/8/16 (50 %),
  NaquadahEnriched dustTiny 8/16/32 (25 %), TungstenSteel dust 16/32/64, Platinum dust 2/4/8.

## 17. Beamline / particle hatch API (GT5U 5.09.54.133)

Reference for MessTech's wireless beamline hatches (`docs/knowledge.md`).

* `gtnhlanth.common.hatch.MTEHatchBeamlineConnector` (abstract, extends `MTEHatch`, public
  `BeamLinePacket dataPacket`, ticks `moveAround` at `tectech.util.CommonValues.MOVE_AT` = `tick % 20 == 4`, reports
  via `getInfoData()`), `MTEHatchInputBeamline` (`setContents(BeamLinePacket)`, impl. `ISmartInputHatch`,
  `newMetaEntity` returns **`MetaTileEntity`**, `getDescription()` returns `null`), `MTEHatchOutputBeamline`
  (`moveAround` walks up to 128 beamline pipes straight, calls `setContents` on the first input hatch, clears its
  packet; `newMetaEntity` returns `IMetaTileEntity`).
* Filtered: `gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline` (extends the
  output hatch, `@IMetaTileEntity.SkipGenerateDescription`, tier 8) keeps `Map<Particle, Boolean> acceptedInputMap`
  (+ `getParticleList()`/`getParticleMap()`/`setAcceptedInputMap`); the LHC/beam splitter fill it via
  `MTEBeamMultiBase.addAdvancedBeamlineOutputHatch(...)` (calls
  `setInitialParticleList(LHCModule.<force>.acceptedParticles)`) and read it back before pushing. Its ModularUI
  (`gregtech.common.gui.modularui.hatch.MTEHatchAdvancedOutputBeamlineGui`) edits the map through GenericMap/List
  sync handlers → a subclass only inherits `buildUI`.
* Data: `BeamInformation` (`float energy` keV, `int rate` = flux, `int particleId`, `float focus`);
  `BeamLinePacket extends tectech.mechanics.dataTransport.DataPacket<BeamInformation>` (NBT round-trip ready);
  `BeamInformation` validates the particle id, `Particle.VALUES[id]` is the enum table.
* Machines push via `hatch.dataPacket = new BeamLinePacket(...)` (`MTESourceChamber`, `MTESynchrotron`, `MTELINAC`,
  `MTEBeamStabilizer`, `MTEBeamSplitter`, `MTELargeHadronCollider`), read inputs with
  `mInputBeamline.get(n).dataPacket`; `MTEBeamMultiBase` keeps `mInputBeamline` / `mOutputBeamline` /
  `mAdvancedOutputBeamline`, clears them in `clearHatches()`.
* Hatch slots `instanceof`-based (`addBeamLineInputHatch` / `addBeamLineOutputHatch` /
  `addAdvancedBeamlineOutputHatch`); `BeamHatchElement.BeamlineInput/Output` match on `mteClasses()` → a subclass is
  accepted by every beam structure without touching GT5U. `.hatchId(<MetaTileEntityIDs id>)` feeds `couldBeValid`
  and the autoplace item filter (both item-damage based): no effect on the structure check, but the hologram flags a
  placed add-on hatch as an error and blocks auto-placing.
* The wired output hatch calls `setContents` on any `MTEHatchInputBeamline` it meets **without checking
  `canConnect`** (`MTEHatchOutputBeamline.java:97-99`) → an add-on input hatch must reject foreign packets itself to
  stay channel-pure.
* The wired output hatch overwrites `canConnect` per side (`isOutputFacing`); the input hatch allows pipes only on
  its input face. Both tier 6 (LuV), registered as `HATCH_BEAMLINE_INPUT/OUTPUT` and
  `HATCH_ADVANCED_BEAMLINE_OUTPUT` in `MetaTileEntityIDs`; names from `gt.blockmachines.hatch.beamlineinput`
  (束流输入仓), `...beamlineoutput` (束流输出仓), `...hatch.advancedbeamlineoutput` (过滤式束流输出仓).
* `MTEHatchBeamlineConnector.getDescription()` has a "Must be painted to work" line, but colour only modulates the
  texture (`Dyes.getModulation`) - the wired pipe path ignores it → colour is free as an addon channel key (what
  MessTech does).
* `IMetaTileEntity.SkipGenerateDescription` (not inherited by subclasses, `ItemMachines.addDescription` /
  `registerDescription`): without it `getDescription()` is dumped once into `GregTech.lang` at client setup and read
  back → per-instance text ends up frozen.

## 18. 2.9.0 迁移：GT5U 5.09.54.205 → 5.09.54.264

`dependencies.gradle` 的 `gtnhVersion = "2.9.0"` 解析到的关键版本：GT5U **5.09.54.264**、GTNHLib **0.11.54**
（`configurations.all { force ... }` 必须跟着升 - RC 期钉的 0.11.51 已不是 2.9.0 的版本）、
NewHorizonsCoreMod 2.9.99、AE2 rv3-beta-1095、NEI 2.8.171、ThaumicEnergistics 1.7.68、Angelica 2.2.42、
StructureLib 1.4.47。Et-Futurum-Requiem 2.6.65-GTNH 目前用 `libs/etfuturum-2.6.65-GTNH-dev.jar` 本地 jar
（maven 上也有 `ganymedes01.etfuturum:Et-Futurum-Requiem:2.6.65-GTNH:dev`）。

源码对照：`tmp/GT5U-264-src/`（当前依赖）vs `tmp/GT5U-205-src/`（上一版 2.9.0-RC-2 的依赖）。我们引用的
297 个 GT5U 家族类型**没有一个被删除**，编译期唯一的破坏点就是下面的 `GTAuthors`；其余 75 个变更文件都是
新增/内部逻辑，行为由继承自动跟上。

### 18.1 作者信息整体移除（已适配）

* 删类 / 删 lang key：`gregtech.api.enums.GTAuthors`、`gt.author` / `gt.authors`（en_US 里是
  `Author: %s` / `Authors: %s`）、`GT5U.MBTT.Mod`、`GT5U.MBTT.StructureBy`。
* `MultiblockTooltipBuilder`：`authors` / `structureAuthors` 列表与 `addAuthorsIfNEI` 等删除；
  `toolTipFinisher(String... authors)` **保留但忽略参数**（binary compatibility 注释写得很明白），我们的
  `toolTipFinisher()` 无参调用不受影响。
* `MTEHatch.formatEnergyInfoDesc(String[] author, ...)` 重载删除（`author` 以前只用于追加作者行）。
* `MTEHatch.updateCraftingIcon(ItemStack)` 变 `@Deprecated`：没有 owner 时 `clearHatches` 清不掉旧图标 →
  新写法 `updateCraftingIcon(icon, getBaseMetaTileEntity())`（GT5U 内部全部改成两参）。
* MessTech 的适配：`AuthorDynamic.authorLine(...)` 本地复刻 `"Author: %s"`（GREEN + name + RESET + GRAY），
  lang 加 `messTech.author`；`CalculateMultiMachineBase` / `MTSteamMultiBase` / `BosesCraftingArray` 的 8 处
  `updateCraftingIcon` 全部改成两参版本。

### 18.2 `MultiblockTooltipBuilder` 新增 / 行为变化

* 新增 `addTierNote(String)`、`addEnergyHatchGlassTier()`；`addMinGlassForLaser(t)` 现在走 `addTierNote`
  （插在结构行前面）；`addMaxTierSkips` 措辞/位置有调整（`GT5U.MBTT.Structure.MaxTierSkips`）。
* 结构尺寸标签本地化：`GT5U.MBTT.Dimensions.Length/Width/Height`（原来硬编码 `L/W/H`）。
* `addDeprecatedLine` 会置 deprecated 标记（GUI 里机器人图标）。作者行不再由 GT5U 添加。

### 18.3 基类 / 杂项（无需改代码，行为自动继承）

* `MTEMultiBlockBase`：新增 `protected tryActivateCoilLease()`（线圈租约 hook，替换 `onStructureFormed` 里
  的内联逻辑）与 `clearCraftingIcons(Collection<? extends MTEHatch>)`；删除 `setMaxParallelForPanel(int)`；
  **合成输入仓按颜色分桶**（`mDualInputHatches` 先查无颜色、再查 0-15 色）、`getWailaBody` 的进度行受
  `showRecipeTextInGUI()` 控制、控制器槽 0 大小堆会清空。
* `MTEEnhancedMultiBlockBase`：新增 `protected getSoundRadiusMultiplier()`。
* `MTEHatchOutputBus#getStackSizeLimit(int, ItemStack)` 上移到 `IMetaTileEntity` default 方法（`MTDTPF` /
  `MTComputingCenter` 的 `@Override` 仍有效）。
* gtnhintergalactic `TileEntityModuleBase`：删除 `addGregTechLogo(ModularWindow.Builder)` /
  `drawTexts(DynamicPositionedColumn, SlotWidget)`（我们没重写这两个）；`MTEHatchVacuumConveyor` 新增
  `getItemsForHoloGlasses()`。
* 构建环境：`gradle.properties` 现在带 `org.gradle.jvmargs = -Xmx4G -XX:MaxMetaspaceSize=1G`。默认 512 MiB
  堆在 `downgradeMainClasses` / `downgradeJar`（jvmDowngrader）阶段会 GC thrash，Gradle 直接报
  `Gradle build daemon has been stopped: since the JVM garbage collector is thrashing`。
* NAC 配方脚本 `ScriptNanochipRecipes.java` 在 NH-Core 2.9.76 / 2.9.84 / 2.9.99 完全一致（SHA-256 相同），
  所以 `MTRecipeMaps` 的 24 池扁平化逻辑（`tmp/Compare.md` E2）**不需要**再改。
* 顺带清掉的 deprecated 调用（编译 `-Xlint:deprecation` 扫出来、行为不变）：17 处冗余的
  `survivalConstruct(ItemStack,int,IItemSource,EntityPlayerMP)`（StructureLib 1.4.47 起推荐
  `ISurvivalBuildEnvironment` 版本，我们本来就有新版本，旧的直接删）、`Textures.BlockIcons.custom(String)` →
  `custom(domain, path)`、`TextureFactory.of(ITexture)`（空操作）→ 直接用 builder、`GenericSyncValue` 构造器 →
  `GenericSyncValue.builder(...)`、ModularUI2 `Rectangle#setColor` → `color`、`GTUtility.sendChatToPlayer` →
  `sendChatTrans`。

### 18.4 Angelica 2.2.42：2.9.0 真正的运行时破坏点（已适配）

* Angelica 2.2.42 把 `com.gtnewhorizons.angelica.glsm.GLStateManager` 拆成一组 `glsm.GLSM*` 包装类：
  `BatchingFontRenderer.setupFontDrawState()` 里的混合设置从 `GLStateManager.tryBlendFuncSeparate(IIII)V` 变成
  `GLSMBlend.tryBlendFuncSeparate(IIII)V`。
* 我们 `MixinBatchingFontRenderer_TextEffect.mt$compositeMaskCoverage` 的 `@ModifyArg` 目标必须跟着换类，否则
  启动时 `Critical injection failure: ... failed injection check, (0/1) succeeded. Scanned 0 target(s)` →
  `NoClassDefFoundError: com/gtnewhorizons/angelica/client/font/BatchingFontRenderer`，
  客户端崩在 SplashProgress（`FontRenderer` 构造 Angelica batcher）。
* 已验证：日志里出现 `RemapperAdapterFML is remapping ... tryBlendFuncSeparate(IIII)V for .../GLSMBlend` 与
  `Forge Mod Loader has successfully loaded 124 mods` 即恢复正常。
* 其余被注入/影子的成员（`drawString(FFIZZLjava/lang/CharSequence;II)`、`flushDeferredText`、`shouldDeferNow`、
  `shouldDrawThroughPipeline`、`underlying`、`batchDepth`、`arenaOwner`、`flushBatch`）未变；
  `AngelicaTextAdapter` 用到的 `FontProvider` / `FontStrategist` / `FontConfig` / `GLStateManager` /
  `FontRendererAccessor` / `ModelPartBatcher` / `TesrBatchRenderer` 也都还在。细节见 `docs/text-fx/design.md`。
