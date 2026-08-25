# GT5-Unofficial / GTNH Multiblock knowledge notes (for writing new machines)

> What was learned while implementing `MTDTPF`, distilled so future machines can be written
> (and debugged) without re-reading the whole monorepo.

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
