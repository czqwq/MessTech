package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.items.MTItemList;
import com.MessTech.common.items.MTNACComponentItem;
import com.MessTech.common.items.MTNACComponentItems;
import com.MessTech.init.MessTech;
import com.gtnewhorizons.modularui.api.drawable.UITexture;

import gregtech.api.enums.CondensateType;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.NaniteTier;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.items.CircuitComponentFakeItem;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.maps.AssemblyLineFrontend;
import gregtech.api.recipe.maps.LargeNEIFrontend;
import gregtech.api.recipe.maps.QuantumComputerFrontend;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;
import gregtech.nei.RecipeDisplayInfo;
import gregtech.nei.formatter.HeatingCoilSpecialValueFormatter;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;
import tectech.recipe.BECAssemblyFrontend;
import tectech.recipe.TecTechRecipeMaps;

/**
 * Custom recipe maps for MessTech machines.
 */
public final class MTRecipeMaps {

    private MTRecipeMaps() {}

    /**
     * Optional 1-4 circuit selector for the 24 one-step pool.
     * <p>
     * 1 = Processor, 2 = Processor Cluster/Assembly, 3 = Supercomputer/Computer,
     * 4 = Mainframe. Special independent circuits (Piko/Quantum/Planck etc.) have level 0.
     */
    public static final RecipeMetadataKey<Integer> ONE_STEP_CIRCUIT_LEVEL = SimpleRecipeMetadataKey
        .create(Integer.class, "mt_onestep_circuit_level");

    /**
     * Minimum structure level a Chemical Twister recipe needs, as
     * {@link com.MessTech.common.machine.MTChemicalTwister#getStructureLevel()}. Levels 2..4 structures will be added
     * later; a recipe without this metadata needs level {@link #DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL}.
     * <p>
     * This is independent from the heat requirement ({@code mSpecialValue}): the structure level says whether the
     * machine is big enough for the recipe at all, the heat says whether its coil ring is hot enough. A recipe can
     * fail either check on its own, and the NEI page shows the required level through {@link #drawInfo}.
     */
    public static final ChemicalTwisterStructureLevelKey CHEMICAL_TWISTER_STRUCTURE_LEVEL = ChemicalTwisterStructureLevelKey.INSTANCE;

    /** Level a recipe needs when it does not carry {@link #CHEMICAL_TWISTER_STRUCTURE_LEVEL}: the base structure. */
    public static final int DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL = 1;

    /**
     * The MessTech logo drawn on the NEI pages of this mod (17x17, the size of GT's own recipe logo). This is the
     * GregTech/ModularUI flavour of {@code MTGuiTextures.PICTURE_MT_LOGO}, which is the same texture in the MUI2 type
     * that {@code RecipeMapBuilder#logo} does not accept.
     */
    public static final UITexture MT_LOGO = UITexture.fullImage("messtech", "gui/picture/mt_logo");

    /**
     * {@link RecipeMetadataKey} for the required Chemical Twister structure level. Metadata keys draw their own NEI
     * line, so the NEI page of the pool shows "Structure Level: N" next to the heat requirement.
     */
    public static final class ChemicalTwisterStructureLevelKey extends RecipeMetadataKey<Integer> {

        public static final ChemicalTwisterStructureLevelKey INSTANCE = new ChemicalTwisterStructureLevelKey();

        private ChemicalTwisterStructureLevelKey() {
            super(Integer.class, "mt_chemical_twister_structure_level");
        }

        @Override
        public void drawInfo(RecipeDisplayInfo recipeInfo, @Nullable Object value) {
            recipeInfo.drawText(
                StatCollector.translateToLocal("mt.recipe.chemicaltwister.structure_level") + ": " + cast(value, 1));
        }
    }

    /** Fake recipe pool for the Computing Center (Nano Computing mode), with proper NEI/frontend registration. */
    public static final RecipeMap<RecipeMapBackend> computingCenterFakeRecipes = RecipeMapBuilder
        .of("mt.recipe.computingcenter")
        .maxIO(1, 0, 0, 0)
        .minInputs(1, 0)
        .dontUseProgressBar()
        .frontend(QuantumComputerFrontend::new)
        .neiHandlerInfo(builder -> builder.setHeight(110))
        .build();

    /**
     * Populates {@link #computingCenterFakeRecipes} with the same rack-component fake recipes that
     * {@code MTEHatchRack.run()} already registers into {@code quantumComputerFakeRecipes}. This gives
     * the Computing Center its own independent NEI list while reusing the shared rack components.
     * Must be called after {@code MTEHatchRack.run()}.
     */
    public static void populateComputingCenterFakeRecipes() {
        RecipeCategory defaultCategory = computingCenterFakeRecipes.getDefaultRecipeCategory();
        for (GTRecipe recipe : RecipeMaps.quantumComputerFakeRecipes.getAllRecipes()) {
            // Recipes copied from the shared QC pool keep their original recipe category. NEI's handler
            // reads getRecipesByCategory(defaultCategory) — not getAllRecipes() — so we must re-tag every
            // recipe to this map's own category, otherwise our independent list stays empty.
            recipe.setRecipeCategory(defaultCategory);
            computingCenterFakeRecipes.addFakeRecipe(false, recipe);
        }
    }

    /** Independent Assembly Line recipe pool for {@code MTAssFactory} (Mode 2, requires LevelTier 2). */
    public static final RecipeMap<RecipeMapBackend> assFactoryAssemblyLineRecipes = RecipeMapBuilder
        .of("mt.recipe.assfactory.assemblyline")
        .maxIO(16, 1, 4, 0)
        .minInputs(1, 0)
        .useSpecialSlot()
        .slotOverlays((index, isFluid, isOutput, isSpecial) -> isSpecial ? GTUITextures.OVERLAY_SLOT_DATA_ORB : null)
        .slotOverlaysMUI2(
            (index, isFluid, isOutput, isSpecial) -> isSpecial ? GTGuiTextures.OVERLAY_SLOT_DATA_ORB : null)
        .neiTransferRect(88, 8, 18, 72)
        .neiTransferRect(124, 8, 18, 72)
        .neiTransferRect(142, 26, 18, 18)
        .frontend(AssemblyLineFrontend::new)
        .neiHandlerInfo(builder -> builder.setHeight(110))
        .build();

    /**
     * The NEI flash drive of every Assembly Line definition, one per definition, kept across populate calls.
     * <p>
     * GT registers one such stick next to every {@code RecipeAssemblyLine} it turns into an
     * {@code assemblylineVisualRecipes} entry, and the machine relies on the very same stick a player writes. Creating
     * a new one on every populate would pile another full set onto
     * {@code RecipeAssemblyLine.dataSticksForNEI} - a list GT holds strongly on purpose - on every world load, so the
     * sticks are made once per definition and reused afterwards.
     */
    private static final Map<GTRecipe.RecipeAssemblyLine, ItemStack> DISPLAY_DATA_STICKS = new IdentityHashMap<>();

    /**
     * Builds the Assembly Factory's Assembly Line map from GT's authoritative Assembly Line definitions.
     * <p>
     * The source is {@code GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes} - the same list a flash drive and a Data
     * Access hatch resolve against, and the only place where a recipe's per-slot alternatives are recorded - and not
     * the {@code assemblylineVisualRecipes} pool, whose entries are <em>fake</em> display recipes. This follows TST's
     * {@code AssemblyLineWithoutResearchRecipePool}, which is what makes the recipes the machine runs the real ones.
     * <p>
     * Every definition is registered twice, splitting "what NEI shows" from "what the machine runs" exactly the way GT
     * splits {@code assemblylineVisualRecipes} from {@code sAssemblylineRecipes}:
     * <ul>
     * <li>one <b>fake</b> recipe per definition, keeping that definition's per-slot alternatives - see
     * {@link #addAssemblyLineDisplayRecipe}. Fake recipes are rejected by {@code RecipeMapBackend#filterFindRecipe},
     * so the machine can never match one; they exist only to give NEI one page per Assembly Line recipe.</li>
     * <li>one <b>real</b>, <b>hidden</b> recipe per concrete input combination - see {@link #materialiseInputs}. These
     * are what the machine matches ({@code mHidden} is read by NEI alone, never by recipe lookup). Registering the
     * definition itself instead is not an option: a slot's alternatives are only honoured at match time for ore
     * dictionary slots, so a single recipe keeping them is found through its first alternative only, and a machine
     * stocked with any other one reports no recipe at all.</li>
     * </ul>
     * Re-runnable: the map is cleared first, and the call is repeated from {@code CommonProxy#serverStarted} because
     * the Assembly Line registry keeps growing until the last recipe loader has run.
     */
    public static void populateAssFactoryAssemblyLineRecipes() {
        assFactoryAssemblyLineRecipes.getBackend()
            .clearRecipes();

        int definitions = 0;
        int displayed = 0;
        int registered = 0;
        for (GTRecipe.RecipeAssemblyLine recipe : GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes) {
            definitions++;
            if (recipe == null || recipe.mOutput == null || recipe.mInputs == null || recipe.mInputs.length == 0) {
                continue;
            }
            if (addAssemblyLineDisplayRecipe(recipe)) {
                displayed++;
            }
            List<ItemStack[]> combinations = materialiseInputs(recipe);
            if (combinations.size() > 64) {
                // TST logs the same thing ("inputCombine.size {}") for its wildcard definitions. A definition that
                // expands this far is what fills the registry: the Wetware Mainframe's five ASMD/XSMD slots and its
                // six superconductor wires are 192 combinations before its rubber-foil slot is even counted
                // (ResearchStationAssemblyLine:428-446), so this line names the handful responsible for the total.
                MessTech.MT_LOG
                    .info("[AssFactory] {} expands to {} input combinations", recipe.mOutput, combinations.size());
            }
            for (ItemStack[] inputs : combinations) {
                Collection<GTRecipe> added = GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(recipe.mOutput)
                    .fluidInputs(recipe.mFluidInputs)
                    .eut(recipe.mEUt)
                    .duration(recipe.mDuration)
                    .hidden()
                    .addTo(assFactoryAssemblyLineRecipes);
                if (added.isEmpty()) {
                    MessTech.MT_LOG.warn(
                        "[AssFactory] Assembly Line recipe for {} was refused by assFactoryAssemblyLineRecipes",
                        recipe.mOutput);
                    continue;
                }
                registered += added.size();
            }
        }
        MessTech.MT_LOG.info(
            "[AssFactory] Assembly Line pool: {} runnable recipes ({} NEI pages) built from {} Assembly Line definitions",
            registered,
            displayed,
            definitions);
    }

    /**
     * Registers the single NEI page of one Assembly Line definition into {@link #assFactoryAssemblyLineRecipes},
     * mirroring the fake {@code assemblylineVisualRecipes} entry GT creates next to every {@code RecipeAssemblyLine}.
     * <p>
     * The recipe is fake, so recipe lookup skips it ({@code RecipeMapBackend#filterFindRecipe} requires
     * {@code !mFakeRecipe}) and it never takes part in matching; keeping the alternatives per slot is what makes NEI
     * cycle through them inside one input slot instead of showing only the first. Collision checking is off because
     * this recipe's inputs are exactly the first combination registered as a runnable recipe below, which the check
     * would otherwise report as a duplicate and drop.
     * <p>
     * Its special slot carries the flash drive of the definition - see {@link #displayDataStick} - so the page looks
     * like the one GT draws for the same recipe in {@code assemblylineVisualRecipes}.
     *
     * @return whether the page was registered.
     */
    private static boolean addAssemblyLineDisplayRecipe(GTRecipe.RecipeAssemblyLine recipe) {
        Collection<GTRecipe> added = GTValues.RA.stdBuilder()
            .itemInputs(displayInputSpec(recipe.mInputs, recipe.mOreDictAlt))
            .itemOutputs(recipe.mOutput)
            .fluidInputs(recipe.mFluidInputs)
            .special(displayDataStick(recipe))
            .eut(recipe.mEUt)
            .duration(recipe.mDuration)
            .fake()
            .ignoreCollision()
            .addTo(assFactoryAssemblyLineRecipes);
        if (added.isEmpty()) {
            MessTech.MT_LOG.warn(
                "[AssFactory] Assembly Line NEI page for {} was refused by assFactoryAssemblyLineRecipes",
                recipe.mOutput);
            return false;
        }
        return true;
    }

    /**
     * The flash drive the NEI page of one definition shows in its special slot: a data stick carrying that recipe's
     * data, so NEI draws the same "this stick holds recipe X" item the original Assembly Line page shows, exactly what
     * GT puts next to every {@code RecipeAssemblyLine} in {@code assemblylineVisualRecipes}.
     * <p>
     * GT fills its own display sticks from {@code RecipeAssemblyLine#reInit()}, which runs while GT activates the ore
     * dictionary during postInit - before this pool is built in {@code CommonProxy#serverStarted} - and it is only
     * called again when items were remapped, so the data has to be written here instead of waited for. The stick is
     * registered with the definition as well ({@code newDataStickForNEI}), which is what lets a later {@code reInit()}
     * refresh it after a remap.
     */
    private static ItemStack displayDataStick(GTRecipe.RecipeAssemblyLine recipe) {
        return DISPLAY_DATA_STICKS.computeIfAbsent(recipe, definition -> {
            ItemStack stick = definition.newDataStickForNEI("Reads Research result", 0);
            AssemblyLineUtils.setAssemblyLineRecipeOnDataStick(stick, definition, false);
            return stick;
        });
    }

    /**
     * The per-slot input spec {@code GTRecipeBuilder#itemInputs(Object...)} expects for {@link
     * #addAssemblyLineDisplayRecipe}: a slot that lists alternatives is passed as its whole {@code ItemStack[]} (so the
     * builder records it in {@code mOreDictAlt} and NEI offers every choice), a slot without them as the plain stack.
     */
    private static Object[] displayInputSpec(ItemStack[] inputs, ItemStack[][] alternatives) {
        Object[] spec = new Object[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            ItemStack[] slot = alternatives != null && i < alternatives.length ? alternatives[i] : null;
            spec[i] = slot != null && slot.length > 0 ? slot : inputs[i];
        }
        return spec;
    }

    /**
     * Every concrete input array one Assembly Line definition stands for: the cartesian product of its per-slot
     * alternatives, ported from TST's {@code AssemblyLineWithoutResearchRecipePool#generateAllItemInput}.
     * <p>
     * Expanding is not a convenience here, it is what makes these recipes matchable at all. GT's lookup does index a
     * slot's alternatives, but the match check does not use them: {@code GTRecipe_WithAlt#buildItemInputCache} passes
     * {@code mOreDictAlt} to {@code RecipeItemInput} only for ore dictionary slots ({@code mOreDictIds[i] >= 0}) and
     * falls back to a plain {@code RecipeItemInput(mInputs[i], nbtSensitive)} otherwise. So one recipe per definition
     * is found by the lookup yet rejected whenever the machine offers any alternative but the first - a recipe written
     * as "16 advanced SMD or 4 optical SMD" is invisible to a machine stocked with the optical ones, and the machine
     * reports no recipe at all. Registering one plain recipe per combination takes the alternatives out of the
     * equation.
     * <p>
     * A slot whose alternatives are circuits collapses to TST's any-circuit wildcard rather than expanding, because
     * such a slot lists every circuit item of its ore dictionary and expanding it would register dozens of equivalent
     * variants for one slot.
     */
    private static List<ItemStack[]> materialiseInputs(GTRecipe.RecipeAssemblyLine recipe) {
        ItemStack[] base = recipe.mInputs.clone();
        ItemStack[][] wildcards = new ItemStack[base.length][];

        if (recipe.mOreDictAlt != null) {
            for (int i = 0; i < recipe.mOreDictAlt.length && i < base.length; i++) {
                ItemStack[] alternatives = recipe.mOreDictAlt[i];
                if (alternatives == null || alternatives.length == 0) continue;

                ItemStack wildcardCircuit = toWildcardCircuit(alternatives[0]);
                if (wildcardCircuit != null) {
                    // A circuit slot: one stack that stands for any circuit of that tier, so it adds no combinations.
                    base[i] = wildcardCircuit;
                } else {
                    base[i] = alternatives[0];
                    wildcards[i] = alternatives;
                }
            }
        }

        List<ItemStack[]> combinations = new ArrayList<>();
        combinations.add(GTUtility.copyItemArray(base));
        for (int i = 0; i < base.length; i++) {
            if (wildcards[i] == null) continue;
            for (int j = 1; j < wildcards[i].length; j++) {
                if (wildcards[i][j] == null) continue;
                ItemStack variation = wildcards[i][j].copy();
                int size = combinations.size();
                for (int k = 0; k < size; k++) {
                    ItemStack[] combined = GTUtility.copyItemArray(combinations.get(k));
                    combined[i] = variation;
                    combinations.add(combined);
                }
            }
        }
        return combinations;
    }

    /** TST's {@code transToWildCircuit}: the any-circuit stack when the given stack is a circuit, null otherwise. */
    private static ItemStack toWildcardCircuit(ItemStack stack) {
        var association = GTOreDictUnificator.getAssociation(stack);
        if (association == null || !association.hasValidPrefixMaterialData()) return null;
        if (association.mPrefix != OrePrefixes.circuit) return null;
        return GTOreDictUnificator.get(false, stack, true);
    }

    // --- Nano-Scale Foundry separate NEI pools (one per original NAC pool) ---
    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryConversionRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.conversion")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.Machine_Multi_NanochipAssemblyComplex.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryAssemblyMatrixRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.assemblymatrix")
        .maxIO(16, 1, 4, 0)
        .minInputs(0, 0)
        .frontend(AssemblyLineFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setDisplayStack(ItemList.NanoChipModule_AssemblyMatrix.get(1))
                .setHeight(110))
        .build();

    public static final RecipeMap<RecipeMapBackend> Steel_brick_Recipes = RecipeMapBuilder.of("mt.recipe.steel_brick")
        .maxIO(4, 3, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(MTItemList.MTDBBFurnace.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundrySMDProcessorRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.smdprocessor")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_SMDProcessor.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryBoardProcessorRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.boardprocessor")
        .maxIO(1, 1, 1, 1)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_BoardProcessor.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryEtchingArrayRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.etchingarray")
        .maxIO(2, 1, 2, 0)
        .minInputs(0, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_EtchingArray.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryCuttingChamberRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.cuttingchamber")
        .maxIO(1, 1, 1, 0)
        .minInputs(1, 1)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_CuttingChamber.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryWireTracerRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.wiretracer")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_WireTracer.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundrySuperconductorSplitterRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.superconductorsplitter")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_SuperconductorSplitter.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryOpticalOrganizerRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.opticalorganizer")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_OpticalOrganizer.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryEncasementWrapperRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.encasementwrapper")
        .maxIO(4, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_EncasementWrapper.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryBiologicalCoordinatorRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.biologicalcoordinator")
        .maxIO(1, 1, 1, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(MTItemList.MTNanoScaleFoundry.get(1)))
        .build();

    /**
     * Recipe pool of the {@code MTChemicalTwister}. A recipe's {@code mSpecialValue} is the heat it needs: the machine
     * checks it against {@code coil heat + 100 K per energy tier above MV}, exactly like the EBF, so the NEI page uses
     * GT's own heating-coil formatter to show the requirement.
     * <p>
     * The page uses GT's large layout, the same one the Large Chemical Reactor and the Plasma Forge use: item inputs on
     * the top left with the fluid inputs <i>below</i> them, item outputs on the top right with the fluid outputs below
     * them. The default frontend instead pins the fluid row at a fixed y=62 while the item grid grows downward from
     * y=6, so with a big {@code maxIO} the two grids are drawn on top of each other; {@code maxIO} therefore has to
     * stay in sync with the biggest recipe of the pool (the platinum recipe fills 6 item input slots, 5 fluid inputs,
     * 12 item outputs and 7 fluid outputs).
     * <p>
     * {@code maxIO(15, 15, 15, 15)} is a 3 x 5 grid per block, so the page is 170 x 190: five item rows
     * (y = 8..80), then five fluid rows below them (y = 98..170).
     */
    public static final RecipeMap<RecipeMapBackend> MTChemicalTwisterRecipes = RecipeMapBuilder
        .of("mt.recipe.chemicaltwister")
        .maxIO(15, 15, 15, 15)
        .minInputs(1, 0)
        .frontend(LargeNEIFrontend::new)
        .logo(MT_LOGO)
        .neiHandlerInfo(
            builder -> builder.setDisplayStack(MTItemList.MTChemicalTwister.get(1))
                .setShiftY(8)
                .setHeight(240))
        .neiSpecialInfoFormatter(HeatingCoilSpecialValueFormatter.INSTANCE)
        .build();

    /** Coil temperature the Chemical Twister's mode 1 (概率毁灭者) runs at, fixed: that structure has no coil ring. */
    public static final int QFT_PROBABILITY_DESTROYER_HEAT = 12601;

    /**
     * Recipe pool of the Chemical Twister's mode 1, the 概率毁灭者 (Probability Destroyer): the Quantum Force
     * Transformer recipes, copied out into an independent pool with <b>every output chance at 100%</b>, filled by
     * {@link #populateQftProbabilityDestroyerRecipes()}. Its recipes ask for structure level 2 and for the mode's fixed
     * {@link #QFT_PROBABILITY_DESTROYER_HEAT}, so they can only run in the QFT shaped structure.
     */
    public static final RecipeMap<RecipeMapBackend> qftProbabilityDestroyerRecipes = RecipeMapBuilder
        .of("mt.recipe.qft_probability_destroyer")
        .maxIO(9, 9, 9, 9)
        .minInputs(0, 0)
        .frontend(LargeNEIFrontend::new)
        .logo(MT_LOGO)
        .neiHandlerInfo(
            builder -> builder.setDisplayStack(GregtechItemList.QuantumForceTransformer.get(1))
                .setShiftY(8)
                .setHeight(166))
        .neiSpecialInfoFormatter(HeatingCoilSpecialValueFormatter.INSTANCE)
        .build();

    /**
     * Copies every Quantum Force Transformer recipe into {@link #qftProbabilityDestroyerRecipes}, with all item and
     * fluid output chances set to 10000 (= 100%). The QFT catalyst from the recipe metadata becomes a non-consumed
     * input (stack size 0, GT only requires it to be present in the bus), the focus tier metadata is kept for NEI, the
     * recipes get structure level 2 and the mode's fixed heat, and every recipe is re-tagged to this map's own
     * {@link RecipeCategory} so it shows up on its own NEI page. Idempotent, like the other populate* methods.
     */
    public static void populateQftProbabilityDestroyerRecipes() {
        if (!qftProbabilityDestroyerRecipes.getAllRecipes()
            .isEmpty()) {
            return;
        }

        RecipeCategory defaultCategory = qftProbabilityDestroyerRecipes.getDefaultRecipeCategory();
        for (GTRecipe recipe : RecipeMaps.quantumForceTransformerRecipes.getAllRecipes()) {
            List<ItemStack> itemInputs = new ArrayList<>(Arrays.asList(recipe.mInputs));
            // The QFT catalyst is a metadata stack (usually size 0); GT enforces stackSize 0 inputs as "present in
            // the bus, not consumed", which is exactly the QFT behaviour.
            ItemStack catalyst = recipe.getMetadata(GTRecipeConstants.QFT_CATALYST);
            if (catalyst != null && catalyst.getItem() != null) {
                // stackSize 0 = non-consumed input: GT requires the item to be in the bus but does not consume it
                itemInputs.add(GTUtility.copyAmountUnsafe(0, catalyst));
            }

            GTRecipeBuilder builder = GTRecipeBuilder.builder()
                .itemInputsUnsafe(itemInputs.toArray(new ItemStack[0]))
                .fluidInputs(recipe.mFluidInputs)
                .itemOutputs(recipe.mOutputs)
                .fluidOutputs(recipe.mFluidOutputs)
                .duration(recipe.mDuration)
                .eut(recipe.mEUt)
                .specialValue(QFT_PROBABILITY_DESTROYER_HEAT)
                .metadata(CHEMICAL_TWISTER_STRUCTURE_LEVEL, 2)
                .recipeCategory(defaultCategory)
                .ignoreCollision();

            if (recipe.mOutputs != null && recipe.mOutputs.length > 0) {
                int[] chances = new int[recipe.mOutputs.length];
                Arrays.fill(chances, 10000);
                builder.outputChances(chances);
            }
            if (recipe.mFluidOutputs != null && recipe.mFluidOutputs.length > 0) {
                int[] chances = new int[recipe.mFluidOutputs.length];
                Arrays.fill(chances, 10000);
                builder.fluidOutputChances(chances);
            }
            // keep the QFT's own metadata (catalyst + focus tier) for the NEI page
            for (Map.Entry<RecipeMetadataKey<?>, Object> entry : recipe.getMetadataStorage()
                .getEntries()) {
                builder.metadata((RecipeMetadataKey<Object>) entry.getKey(), entry.getValue());
            }

            builder.addTo(qftProbabilityDestroyerRecipes);
        }
    }

    /**
     * Independent one-step NEI pool for the future "24" Nano-Scale Foundry mode. Not used by the
     * multiblock yet; this map only shows the direct real-item -> real-circuit recipes that would
     * otherwise require a chain of NAC packets/modules.
     */
    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundry24PoolRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.pool24")
        // Measured from the full recursive expansion: PlanckCircuit peaks at 47 distinct item inputs
        // and 18 distinct fluid inputs. 48 fills a 6x8 item grid; 18 fills a 6x3 fluid grid below it.
        .maxIO(48, 1, 18, 0)
        .minInputs(0, 0)
        .useSpecialSlot()
        .frontend(NanoScaleFoundry24PoolFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setDisplayStack(MTItemList.MTNanoScaleFoundry.get(1))
                .setHeight(230))
        .build();

    /**
     * Independent recipe pool for {@code BosesCraftingArray}.
     * <p>
     * BEC's assembling recipes store their original entangled condensate inputs in metadata and clear the
     * real fluid input array. This pool is the converted version: entangled condensate is replaced 1:1 by
     * its corresponding real source fluid/molten, while the nanite tier metadata is preserved.
     */
    public static final RecipeMap<RecipeMapBackend> bosesCraftingArrayRecipes = RecipeMapBuilder
        .of("mt.recipe.boses_crafting_array")
        .maxIO(16, 1, 4, 0)
        .minInputs(1, 0)
        .frontend(BECAssemblyFrontend::new)
        .neiRecipeBackgroundSize(170, 90)
        .neiHandlerInfo(builder -> builder.setDisplayStack(MTItemList.BosesCraftingArray.get(1)))
        .build();

    /**
     * Copies every original NAC pool into its own Nano-Scale Foundry pool. Each map keeps the original
     * module's NEI layout/max IO, so no single giant pool is used.
     */
    public static void populateNanoScaleFoundryRecipes() {
        copyPoolIfEmpty(RecipeMaps.nanochipConversionRecipes, nanoScaleFoundryConversionRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipAssemblyMatrixRecipes, nanoScaleFoundryAssemblyMatrixRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipSMDProcessorRecipes, nanoScaleFoundrySMDProcessorRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipBoardProcessorRecipes, nanoScaleFoundryBoardProcessorRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipEtchingArray, nanoScaleFoundryEtchingArrayRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipCuttingChamber, nanoScaleFoundryCuttingChamberRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipWireTracer, nanoScaleFoundryWireTracerRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipSuperconductorSplitter, nanoScaleFoundrySuperconductorSplitterRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipOpticalOrganizer, nanoScaleFoundryOpticalOrganizerRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipEncasementWrapper, nanoScaleFoundryEncasementWrapperRecipes);
        copyPoolIfEmpty(RecipeMaps.nanochipBiologicalCoordinator, nanoScaleFoundryBiologicalCoordinatorRecipes);
        addConversionReverseRecipes();
    }

    /**
     * Original NAC can eject any unprocessed CircuitComponent packet back to its real item form.
     * Add those reverse recipes to the Foundry Conversion pool so circuit 1 mode can also turn
     * packaged boards/components back into real GT items.
     */
    private static void addConversionReverseRecipes() {
        if (RecipeMaps.nanochipConversionRecipes.getAllRecipes()
            .isEmpty()) {
            return;
        }
        if (hasConversionReverseRecipe()) return;

        RecipeCategory defaultCategory = nanoScaleFoundryConversionRecipes.getDefaultRecipeCategory();
        for (CircuitComponent component : CircuitComponent.VALUES) {
            if (component.isProcessed || component.realComponent == null) continue;
            ItemStack real = component.realComponent.get();
            if (real == null) continue;
            GTRecipeBuilder.builder()
                .itemInputs(MTNACComponentItems.getRealItemStack(component, 1))
                .itemOutputs(real.copy())
                .duration(5 * 20)
                .eut(0)
                .recipeCategory(defaultCategory)
                .ignoreCollision()
                .addTo(nanoScaleFoundryConversionRecipes);
        }
    }

    private static boolean hasConversionReverseRecipe() {
        for (GTRecipe recipe : nanoScaleFoundryConversionRecipes.getAllRecipes()) {
            if (recipe.mInputs != null && recipe.mInputs.length > 0
                && recipe.mInputs[0] != null
                && recipe.mInputs[0].getItem() == MTNACComponentItem.INSTANCE
                && recipe.mOutputs != null
                && recipe.mOutputs.length > 0
                && recipe.mOutputs[0] != null
                && recipe.mOutputs[0].getItem() != MTNACComponentItem.INSTANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Populates the separate 24 pool from every original NAC Assembly Matrix recipe. Each recipe is
     * recursively flattened through Assembly Matrix and the module pools into a single
     * real-item-input -> real-circuit-output recipe. Board Processor immersion fluids are display-only
     * in its own NEI and are not real recipe consumption, so they are omitted here.
     * <p>
     * Duration/EUt are intentionally only informative until the 24 machine mode is implemented:
     * duration is the sum of expanded steps and EU/t is the maximum expanded step EU/t.
     */
    public static void populateNanoScaleFoundry24PoolRecipes() {
        if (!nanoScaleFoundry24PoolRecipes.getAllRecipes()
            .isEmpty()) {
            return;
        }

        List<GTRecipe> generated = new ArrayList<>();
        for (GTRecipe assembly : RecipeMaps.nanochipAssemblyMatrixRecipes.getAllRecipes()) {
            GTRecipe flat = flattenAssemblyTo24Pool(assembly);
            if (flat != null) generated.add(flat);
        }

        RecipeCategory defaultCategory = nanoScaleFoundry24PoolRecipes.getDefaultRecipeCategory();
        Set<String> seen = new HashSet<>();
        for (GTRecipe recipe : generated) {
            String signature = recipeSignature(recipe);
            if (!seen.add(signature)) continue;
            recipe.setRecipeCategory(defaultCategory);
            // This pool is currently NEI-only (the 24 machine mode is not implemented), so add it as a
            // fake recipe. Real recipes with up to ~64 inputs would make GTRecipeLookup expand every
            // input alternative/unification branch exponentially and hang world load.
            nanoScaleFoundry24PoolRecipes.addFakeRecipe(false, recipe);
        }
    }

    /**
     * Converts TecTech's {@code condensateAssemblingRecipes} into the independent
     * {@link #bosesCraftingArrayRecipes} pool used by BosesCraftingArray. Entangled condensate fluids are
     * replaced 1:1 by their real source fluid/molten; the {@code NANITE_TIERS} metadata is kept so the
     * machine can enforce the original BEC nanite-tier steps.
     */
    public static void populateBosesCraftingArrayRecipes() {
        if (!bosesCraftingArrayRecipes.getAllRecipes()
            .isEmpty()) {
            return;
        }

        for (GTRecipe becRecipe : TecTechRecipeMaps.condensateAssemblingRecipes.getAllRecipes()) {
            FluidStack[] condensateInputs = becRecipe.getMetadata(GTRecipeConstants.CONDENSATE_INPUT);
            if (condensateInputs == null || condensateInputs.length == 0) continue;

            FluidStack[] realFluidInputs = new FluidStack[condensateInputs.length];
            boolean valid = true;
            for (int i = 0; i < condensateInputs.length; i++) {
                FluidStack converted = convertCondensateToSourceFluid(condensateInputs[i]);
                if (converted == null) {
                    valid = false;
                    break;
                }
                realFluidInputs[i] = converted;
            }
            if (!valid) continue;

            NaniteTier[] naniteTiers = becRecipe.getMetadata(GTRecipeConstants.NANITE_TIERS);
            GTRecipeBuilder builder = GTRecipeBuilder.builder()
                .itemInputs(becRecipe.mInputs)
                .itemOutputs(becRecipe.mOutputs)
                .fluidInputs(realFluidInputs)
                .duration(becRecipe.mDuration)
                .eut(becRecipe.mEUt);
            if (naniteTiers != null) {
                builder.metadata(GTRecipeConstants.NANITE_TIERS, naniteTiers);
            }
            builder.addTo(bosesCraftingArrayRecipes);
        }
    }

    /**
     * Converts one entangled condensate stack to its real source fluid, preserving the original mB amount.
     *
     * @return converted stack, or null when the fluid is not a known condensate type.
     */
    private static FluidStack convertCondensateToSourceFluid(FluidStack condensate) {
        if (condensate == null || condensate.getFluid() == null) return null;
        CondensateType type = CondensateType.getCondensateType(condensate.getFluid());
        if (type == null) return null;
        FluidStack source = type.getSourceFluid();
        if (source == null || source.getFluid() == null) return null;
        FluidStack converted = source.copy();
        converted.amount = condensate.amount;
        return converted;
    }

    private static GTRecipe flattenAssemblyTo24Pool(GTRecipe assembly) {
        if (assembly == null || assembly.mOutputs == null || assembly.mOutputs.length == 0) return null;

        CircuitComponent outputComponent = getNACComponent(assembly.mOutputs[0]);
        if (outputComponent == null || outputComponent.realComponent == null) return null;

        // Keep every Assembly Matrix product: the special independent circuits (Pico/Quantum/Planck)
        // plus the full Crystal/Wetware/Bio/Optical lines, including Processor/Assembly/Supercomputer
        // and their Mainframe/主机 tier. Mainframes are intentionally retained.
        ItemStack realOutput = outputComponent.realComponent.get();
        if (realOutput == null) return null;
        int circuitLevel = getOneStepCircuitLevel(outputComponent);

        FlattenContext ctx = new FlattenContext();
        Set<Integer> visiting = new HashSet<>();
        if (!expandRecipeInto(assembly, RecipeMaps.nanochipAssemblyMatrixRecipes, 1, ctx, visiting)) {
            return null;
        }

        ItemStack[] itemInputs = mergeSameItem(ctx.items.toArray(new ItemStack[0]));
        FluidStack[] fluidInputs = mergeSameFluid(ctx.fluids.toArray(new FluidStack[0]));
        if (itemInputs.length == 0) return null;

        ItemStack output = realOutput.copy();
        output.stackSize = Math.max(1, assembly.mOutputs[0].stackSize);

        // A flattened one-step recipe must never require its own output as an input; that only happens
        // when the Assembly Matrix chain is cyclic and the fallback could not resolve it.
        for (ItemStack input : itemInputs) {
            if (input != null && GTUtility.areStacksEqual(input, output)) return null;
        }

        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(1, ctx.duration));
        int eut = (int) Math.min(Integer.MAX_VALUE, Math.max(1, ctx.maxEUt));

        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .itemInputs(itemInputs)
            .itemOutputs(new ItemStack[] { output })
            .fluidInputs(fluidInputs)
            .duration(duration)
            .eut(eut)
            .metadata(ONE_STEP_CIRCUIT_LEVEL, circuitLevel);
        // Show the required non-consumed selector as a ghost/special slot in NEI. It is not part of
        // mInputs, so it never affects real recipe matching, parallel calculations or consumption.
        if (circuitLevel >= 1 && circuitLevel <= 4) {
            builder.special(GTUtility.getIntegratedCircuit(circuitLevel));
        }
        return builder.build()
            .orElse(null);
    }

    private static int getOneStepCircuitLevel(CircuitComponent component) {
        if (component == null) return 0;
        String name = component.name();
        if (name.contains("Mainframe")) return 4;
        if (name.contains("Computer")) return 3;
        if (name.contains("Assembly")) return 2;
        if (name.contains("Processor")) return 1;
        return 0;
    }

    /**
     * Expands one recipe run into its real item inputs. Board Processor fluid inputs are skipped.
     * Assembly/other module recipe fluid inputs are kept (they are actual recipe consumption).
     */
    private static boolean expandRecipeInto(GTRecipe recipe, RecipeMap<?> sourceMap, int runs, FlattenContext ctx,
        Set<Integer> visiting) {
        if (recipe == null || runs <= 0) return true;

        Map<CircuitComponent, Long> componentNeeds = new LinkedHashMap<>();
        if (recipe.mInputs != null) {
            for (ItemStack input : recipe.mInputs) {
                if (input == null || input.stackSize <= 0) continue;
                CircuitComponent cc = getNACComponent(input);
                long amount = (long) input.stackSize * runs;
                if (cc != null) {
                    componentNeeds.merge(cc, amount, Long::sum);
                } else {
                    ctx.items.add(copyWithAmount(input, amount));
                }
            }
        }

        for (Map.Entry<CircuitComponent, Long> entry : componentNeeds.entrySet()) {
            if (!expandComponent(entry.getKey(), entry.getValue(), ctx, visiting)) {
                return false;
            }
        }

        if (recipe.mFluidInputs != null && !isBoardProcessorMap(sourceMap)) {
            for (FluidStack fluid : recipe.mFluidInputs) {
                if (fluid == null || fluid.amount <= 0) continue;
                ctx.fluids.add(copyWithAmount(fluid, (long) fluid.amount * runs));
            }
        }

        ctx.duration += (long) recipe.mDuration * runs;
        ctx.maxEUt = Math.max(ctx.maxEUt, (long) recipe.mEUt * runs);
        return true;
    }

    private static boolean expandComponent(CircuitComponent component, long amount, FlattenContext ctx,
        Set<Integer> visiting) {
        if (component == null || amount <= 0) return true;

        Producer producer = findNACProducer(component, ctx);
        if (producer == null) {
            // No module/Assembly recipe produces this component. It is a raw NAC component, so use its
            // real item form directly.
            if (component.realComponent != null && component.realComponent.get() != null) {
                ctx.items.add(copyWithAmount(component.realComponent.get(), amount));
                return true;
            }
            return false;
        }

        GTRecipe producerRecipe = producer.recipe;
        if (producerRecipe.mOutputs == null || producerRecipe.mOutputs.length == 0
            || producerRecipe.mOutputs[0] == null) {
            return false;
        }
        int outputAmount = Math.max(1, producerRecipe.mOutputs[0].stackSize);
        int runs = (int) Math.max(1, (amount + outputAmount - 1) / outputAmount);

        // Guard against recursive cycles (for example Assembly Matrix feeding earlier circuit tiers).
        if (!visiting.add(component.metaId)) {
            if (component.realComponent != null && component.realComponent.get() != null) {
                ctx.items.add(copyWithAmount(component.realComponent.get(), amount));
                return true;
            }
            return false;
        }

        try {
            return expandRecipeInto(producerRecipe, producer.map, runs, ctx, visiting);
        } finally {
            visiting.remove(component.metaId);
        }
    }

    private static Producer findNACProducer(CircuitComponent component, FlattenContext ctx) {
        Producer cached = ctx.producerCache.get(component);
        if (cached != null) return cached;
        for (RecipeMap<?> map : NAC_24_RECIPE_MAPS) {
            for (GTRecipe recipe : map.getAllRecipes()) {
                if (recipe.mOutputs == null || recipe.mOutputs.length == 0) continue;
                if (getNACComponent(recipe.mOutputs[0]) == component) {
                    Producer producer = new Producer(recipe, map);
                    ctx.producerCache.put(component, producer);
                    return producer;
                }
            }
        }
        return null;
    }

    private static boolean isBoardProcessorMap(RecipeMap<?> map) {
        return map == RecipeMaps.nanochipBoardProcessorRecipes;
    }

    private static CircuitComponent getNACComponent(ItemStack stack) {
        if (stack == null) return null;
        if (stack.getItem() != CircuitComponentFakeItem.INSTANCE && stack.getItem() != MTNACComponentItem.INSTANCE) {
            return null;
        }
        return CircuitComponent.tryGetFromFakeStack(stack);
    }

    private static ItemStack copyWithAmount(ItemStack template, long amount) {
        if (template == null) return null;
        ItemStack copy = template.copy();
        copy.stackSize = (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
        return copy;
    }

    private static FluidStack copyWithAmount(FluidStack template, long amount) {
        if (template == null) return null;
        FluidStack copy = template.copy();
        copy.amount = (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
        return copy;
    }

    private static ItemStack[] mergeSameItem(ItemStack[] stacks) {
        if (stacks == null) return new ItemStack[0];
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) continue;
            boolean found = false;
            for (ItemStack existing : merged) {
                if (GTUtility.areStacksEqual(stack, existing)) {
                    long total = (long) existing.stackSize + stack.stackSize;
                    existing.stackSize = (int) Math.min(Integer.MAX_VALUE, total);
                    found = true;
                    break;
                }
            }
            if (!found) merged.add(stack.copy());
        }
        return merged.toArray(new ItemStack[0]);
    }

    private static FluidStack[] mergeSameFluid(FluidStack[] stacks) {
        if (stacks == null) return new FluidStack[0];
        List<FluidStack> merged = new ArrayList<>();
        for (FluidStack stack : stacks) {
            if (stack == null || stack.amount <= 0) continue;
            boolean found = false;
            for (FluidStack existing : merged) {
                if (existing.isFluidEqual(stack)) {
                    long total = (long) existing.amount + stack.amount;
                    existing.amount = (int) Math.min(Integer.MAX_VALUE, total);
                    found = true;
                    break;
                }
            }
            if (!found) merged.add(stack.copy());
        }
        return merged.toArray(new FluidStack[0]);
    }

    private static String recipeSignature(GTRecipe recipe) {
        Set<String> parts = new TreeSet<>();
        if (recipe.mOutputs != null) {
            for (ItemStack stack : recipe.mOutputs) {
                if (stack != null) parts.add("o:" + itemSignature(stack));
            }
        }
        if (recipe.mInputs != null) {
            for (ItemStack stack : recipe.mInputs) {
                if (stack != null) parts.add("i:" + itemSignature(stack));
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack fluid : recipe.mFluidInputs) {
                if (fluid != null) {
                    parts.add(
                        "f:" + fluid.getFluid()
                            .getName() + ":" + fluid.amount);
                }
            }
        }
        return String.join("|", parts);
    }

    private static String itemSignature(ItemStack stack) {
        return stack.getItem()
            .getUnlocalizedName() + ":"
            + stack.getItemDamage()
            + ":"
            + stack.stackSize;
    }

    private static final RecipeMap<?>[] NAC_24_RECIPE_MAPS = new RecipeMap<?>[] {
        RecipeMaps.nanochipAssemblyMatrixRecipes, RecipeMaps.nanochipSMDProcessorRecipes,
        RecipeMaps.nanochipBoardProcessorRecipes, RecipeMaps.nanochipEtchingArray, RecipeMaps.nanochipCuttingChamber,
        RecipeMaps.nanochipWireTracer, RecipeMaps.nanochipSuperconductorSplitter, RecipeMaps.nanochipOpticalOrganizer,
        RecipeMaps.nanochipEncasementWrapper, RecipeMaps.nanochipBiologicalCoordinator };

    private static final class Producer {

        private final GTRecipe recipe;
        private final RecipeMap<?> map;

        private Producer(GTRecipe recipe, RecipeMap<?> map) {
            this.recipe = recipe;
            this.map = map;
        }
    }

    private static final class FlattenContext {

        private final Map<CircuitComponent, Producer> producerCache = new HashMap<>();
        private final List<ItemStack> items = new ArrayList<>();
        private final List<FluidStack> fluids = new ArrayList<>();
        private long duration = 0;
        private long maxEUt = 0;
    }

    /**
     * Copies one of GT5U's nanochip pools into MessTech's own copy of it, once.
     * <p>
     * Known sharp edge, left as it is on purpose: the guard makes every call after the first a no-op, so a call made
     * from {@code CommonProxy#serverStarted} only helps when the first copy found nothing at all. If a source pool is
     * still being filled when the first copy runs, whatever is registered later never reaches the copy. That is not
     * what breaks the Wetware Mainframe (its recipe is an Assembly Line one, see
     * {@code populateAssFactoryAssemblyLineRecipes}), so this stays the behaviour it always had.
     */
    private static void copyPoolIfEmpty(RecipeMap<?> source, RecipeMap<RecipeMapBackend> target) {
        if (!target.getAllRecipes()
            .isEmpty()) {
            return;
        }

        RecipeCategory defaultCategory = target.getDefaultRecipeCategory();
        for (GTRecipe recipe : source.getAllRecipes()) {
            GTRecipeBuilder builder = GTRecipeBuilder.builder()
                .itemInputs(replaceFakeItems(recipe.mInputs))
                .itemOutputs(replaceFakeItems(recipe.mOutputs))
                .fluidInputs(recipe.mFluidInputs)
                .fluidOutputs(recipe.mFluidOutputs)
                .duration(recipe.mDuration)
                .eut(recipe.mEUt)
                .specialValue(recipe.mSpecialValue)
                .recipeCategory(defaultCategory)
                .ignoreCollision();
            if (recipe.mHidden) builder.hidden();
            if (recipe.mFakeRecipe) builder.fake();
            if (!recipe.mCanBeBuffered) builder.noBuffer();
            if (recipe.mNeedsEmptyOutput) builder.needsEmptyOutput();
            if (!recipe.mEnabled) builder.disabled();

            // GTRecipe.copy() drops metadata; rebuild via the builder to keep Board fluid type,
            // Assembly Matrix tier and circuit calibration intact.
            for (Map.Entry<RecipeMetadataKey<?>, Object> entry : recipe.getMetadataStorage()
                .getEntries()) {
                builder.metadata((RecipeMetadataKey<Object>) entry.getKey(), entry.getValue());
            }

            builder.addTo(target);
        }
    }

    private static ItemStack[] replaceFakeItems(ItemStack[] stacks) {
        if (stacks == null) return null;
        List<ItemStack> result = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            if (stack.getItem() == CircuitComponentFakeItem.INSTANCE) {
                CircuitComponent cc = CircuitComponent.tryGetFromFakeStack(stack);
                if (cc != null) {
                    result.add(MTNACComponentItems.getRealItemStack(cc, stack.stackSize));
                    continue;
                }
            }
            result.add(stack.copy());
        }
        return result.toArray(new ItemStack[0]);
    }
}
