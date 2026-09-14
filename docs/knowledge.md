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

### MTReactor (nuclear reactor)
- Controller face: the face art of GoodGenerator's neutron activator - `icons/NeutronActivator_Off` / `_On` and their
  two optional glow layers from the gregtech assets (five animated 16x16 frames each, `frametime: 8`) - over the casing
  that was already under the face (`Casings.AssemblyLineCasing`); the other five faces stay
  `Casings.AssemblerMachineCasing`. Only the overlay is taken from the other machine: the base layer is unchanged, the
  neutron activator would otherwise bring a casing texture of its own (`getCasingTextureForId(49)`) along. The art is a
  12x12 panel inside a two pixel transparent border, so that border is where the casing shows through.
- `tmp/reactorface/verify_reactor_face.py` checks that wiring (the icon names against the donor class, the three layer
  face, the untouched base) and writes the previews of the art into `tmp/reactorface/`.

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
  of that piece**, I `SpaceTimeBendingCore`, J `ForceFieldGlass` (`tmp/chemicaltwister/remap_tier2_letters.py` did the
  letter move, the geometry is untouched). The piece has **no heating coil ring**, so level 1 keeps the EBF heat
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
- `tmp/chemicaltwister/verify_tier2_qft.py` checks all of the above against the GT sources (shape integrity, letters,
  hatch host, mode wiring, fixed heat, pool copy semantics, and in section X the level-dependent face/casing plus the
  client sync and the icon-load timing it depends on).
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
  be raised together with any recipe that outgrows it. `tmp/chemicaltwister/verify_nei_layout.py` re-implements both
  layouts from the GT sources and checks the geometry (old config overlaps, new one does not, everything fits the
  background and the text area, maxIO is the requested 3 x 5 grid, the logo is ours);
  `make_layout_preview.py` renders the before/after mock.
- Recipe classes: the pool's recipes live in `com.MessTech.common.recipe.MTChemicalTwisterRecipes`
  (`addChemicalTwisterRecipes()`, called once from `GTRecipes.loadRecipes()` at FMLLoadComplete) plus
  `loadRecipePostInit()` (called once from `CommonProxy.postInit()`) for recipes that have to wait for other mods (the
  H2O2 one). Every builder grabs the pool as `RecipeMap<RecipeMapBackend> MT = MTRecipeMaps.MTChemicalTwisterRecipes;`
  and ends with `.addTo(MT)`. `tmp/chemicaltwister/verify_recipe_pool.py` audits the whole pool: exactly one call site
  (no double registration), all builders added to `MT`, duration/EU/t/heat/structure-level present on every recipe, no
  >64 stack clamped by the stack factories, no >64 stack inside a plain `itemInputs`, every recipe fits the NEI grid
  and no two recipes share the same input signature.
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
  guess: every recipe of the line is transcribed as a stoichiometric vector in `tmp/platinum/solve_pgm.py`, all cyclic
  intermediates are balanced exactly, everything the line produces but never eats again is dropped, and the remaining
  free directions were fixed by requiring a physically realisable steady state (no negative recipe runs).
  `tmp/platinum/derive_platinum_recipe.py` prints the derivation, `tmp/platinum/verify_platinum_recipe.py` re-derives
  the numbers from the chain and cross-checks the Java recipe, the pool registration and the heat semantics.
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
- `tmp/chemicaltwister/verify_chemical_twister.py` checks the machine side (coil wiring, heat formula, formula table)
  against the EBF sources and prints the heat table of every coil level on every energy tier.

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
   controller casing does. `tmp/chemicaltwister/verify_tier2_qft.py` (T12-T18, X4-X7) guards this for the whole repo.
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
    Contrast is checked at several points of the background's pulse by `tmp/piggen/verify_universium_face.py`,
    which also fails if any plain skin pixel ever creeps back into the overlay.
  - GT5U's accessibility switches are honoured: `Client.render.renderTransMetalFancy`,
    `renderInfinityFancy`, `renderUniversiumFancy`, `renderGlitchFancy` fall back to the plain icon.
- Shift + right click cycles the effect. The damage value is written on both sides, the oink is played
  server side (so it is heard once) and the chat line is sent client side (so it uses the client language).
- Textures: `assets/messtech/textures/items/pigs/pig*.png`, built by `tmp/piggen/generate.py` from the
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
    "edges" 27% -> 47% (checked by `tmp/piggen/magmatter_grain.py`) - while the face stays legible and the sprite is
    about 5% darker from the sharper grain. Every other mode still uses tile 1 with smoothing and their PNGs are
    byte identical to before the change.
  - `pig.png` itself was recovered from the hero render `tmp/pig_hero.png` by `tmp/piggen/rebuild_v3.py`
    (grid snapped, eyes to 2x2 and both nostrils to 1x2 blocks so the face is symmetric).
  - `pigUniversiumFace.png` is the one icon that is not a material build: it is the plain pig's face (both
    eyes, the snout, the nostrils - copied, not re-tinted) inside a feathered superellipse that the shader is
    not allowed to paint over, and it is what keeps the Universium pig from being a faceless hole into space.
    The patch is a superellipse rather than an ellipse because the eyes sit in the upper right and the snout in
    the lower left of the face, i.e. in two opposite corners; its edge fades over 2 px with a smoothstep so the
    skin dissolves into the stars. `tmp/piggen/verify_universium_face.py` asserts that the patch covers every
    eye/nostril/snout pixel at full alpha, that it stays on the pig's silhouette, that it is feathered, and that
    the eyes stay readable against the face (it also writes `tmp/piggen/universium_face_preview.png`, a
    before/after composite with an approximated star field, since the real shader needs the GPU).
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
    and `java -cp tmp/mtkill/out MTKillHarness`. `python tmp/mtkill/verify_mutations.py` is the mutation test of that
    harness: it copies the sources into `tmp/mtkill/mutation`, drops one piece of the behaviour at a time (the tracker
    call of the forced death path, the message key, the Chinese text, the language key itself) and asserts the exact
    set of checks that then fail, plus a control mutation that has to fail nothing.

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
  travel limit and every phase against the real strings (22866 checks, no game needed); it dumps the frames that
  `tmp/pigtext/render_preview.py` draws with the game's own font glyphs.

