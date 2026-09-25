package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.common.items.MTItemList;
import com.MessTech.common.items.MTNACComponentItem;
import com.MessTech.common.items.MTNACComponentItems;
import com.MessTech.init.MessTech;
import com.gtnewhorizons.modularui.api.drawable.UITexture;

import gregtech.api.enums.CondensateType;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.NaniteTier;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.items.CircuitComponentFakeItem;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.objects.ItemData;
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
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitCalibration;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;
import gregtech.nei.RecipeDisplayInfo;
import gregtech.nei.formatter.HeatingCoilSpecialValueFormatter;
import gtPlusPlus.core.util.minecraft.MaterialUtils;
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
     * Builds the Assembly Factory's Assembly Line NEI pages from GT's authoritative Assembly Line definitions.
     * <p>
     * The source is {@code GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes} - the same list a flash drive and a Data
     * Access hatch resolve against, and the only place where a recipe's per-slot alternatives are recorded - and not
     * the {@code assemblylineVisualRecipes} pool. Every definition becomes one <b>fake</b> recipe here, which is what
     * NEI draws: one page per Assembly Line recipe, with the per-slot alternatives cycling inside its input slots.
     * Fake recipes are rejected by {@code RecipeMapBackend#filterFindRecipe}, so nothing can run out of this pool.
     * <p>
     * There are deliberately no runnable recipes in this map. The machine has to accept a definition's ingredients in
     * any bus in any order and pick one of the alternatives a slot accepts, and a pre-computed table cannot express
     * that without expanding the cartesian product of the alternatives - the count reaches six digits for a single
     * definition and used to exhaust the heap at world load. The machine instead resolves the authorised definitions
     * against its own buses while it checks a recipe, see {@link MTAssemblyLineMatcher}, which leaves this pool what it
     * looks like from the outside: NEI pages.
     * <p>
     * Re-runnable: the map is cleared first, and the call is repeated from {@code CommonProxy#serverStarted} because
     * the Assembly Line registry keeps growing until the last recipe loader has run.
     */
    public static void populateAssFactoryAssemblyLineRecipes() {
        assFactoryAssemblyLineRecipes.getBackend()
            .clearRecipes();

        int definitions = 0;
        int displayed = 0;
        for (GTRecipe.RecipeAssemblyLine recipe : GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes) {
            definitions++;
            if (recipe == null || recipe.mOutput == null || recipe.mInputs == null || recipe.mInputs.length == 0) {
                continue;
            }
            if (addAssemblyLineDisplayRecipe(recipe)) {
                displayed++;
            }
        }
        MessTech.MT_LOG.info(
            "[AssFactory] Assembly Line pool: {} NEI pages built from {} Assembly Line definitions",
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
     * every definition of GT's Assembly Line registry is a page of its own, even when two of them share their first
     * alternative in every slot.
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
            .fluidInputs(MTAssemblyLineMatcher.nonNullFluids(recipe.mFluidInputs))
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
     * <p>
     * Definitions come from every addon, and GT only logs a null ingredient or a null alternative instead of refusing
     * the definition ({@code GTRecipeConstants#addAssemblingLineRecipe} writes one error per such slot). The builder
     * refuses them: with {@code gt.recipebuilder.panic.null} set (GTNH ships it set) a null entry throws {@code
     * IllegalArgumentException("null in argument")} and the server never finishes starting. The null alternatives are
     * dropped here, and a slot with nothing usable left is passed as an empty array - which records the empty slot the
     * builder's own null branch produces, without the panic.
     */
    private static Object[] displayInputSpec(ItemStack[] inputs, ItemStack[][] alternatives) {
        Object[] spec = new Object[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            ItemStack[] slot = displayAlternatives(alternatives, i);
            if (slot != null) {
                spec[i] = slot;
            } else if (inputs[i] != null) {
                spec[i] = inputs[i];
            } else {
                spec[i] = GTValues.emptyItemStackArray;
            }
        }
        return spec;
    }

    /**
     * The alternatives slot {@code index} accepts, without its null entries, or null when the slot lists none at all -
     * in which case the caller falls back to the slot's own ingredient.
     */
    @Nullable
    private static ItemStack[] displayAlternatives(ItemStack[][] alternatives, int index) {
        if (alternatives == null || index >= alternatives.length) return null;
        ItemStack[] slot = alternatives[index];
        if (slot == null || slot.length == 0) return null;
        List<ItemStack> valid = new ArrayList<>(slot.length);
        for (ItemStack alternative : slot) {
            if (alternative != null) valid.add(alternative);
        }
        return valid.isEmpty() ? null : valid.toArray(new ItemStack[0]);
    }

    // --- Nano-Scale Foundry separate NEI pools (one per original NAC pool) ---
    // Every maxIO below mirrors the GT5U recipe map it copies (gregtech/api/recipe/RecipeMaps.java, checked against
    // 5.09.54.183). Four of them grew in that version: Part Processor (still "smdprocessor" here) 1/1/0 -> 6/4/3,
    // Etching Array 2/1/2 -> 2/2/2, Wire Tracer 1/1/0 -> 2/4/0 and Encasement Wrapper 4/1/0 -> 4/1/2. maxIO is only
    // the NEI page's envelope - gt's RecipeMapBackend#doAdd validates the minimums, never the maximums - so a stale
    // value does not lose a recipe, it draws the wider ones outside the panel.
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
        .maxIO(6, 4, 3, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_PartProcessor.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryBoardProcessorRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.boardprocessor")
        .maxIO(1, 1, 1, 1)
        .minInputs(1, 0)
        .neiHandlerInfo(builder -> builder.setDisplayStack(ItemList.NanoChipModule_BoardProcessor.get(1)))
        .build();

    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundryEtchingArrayRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.etchingarray")
        .maxIO(2, 2, 2, 0)
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
        .maxIO(2, 4, 0, 0)
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
        .maxIO(4, 1, 2, 0)
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
     * <p>
     * The slot limits and the NEI grid come from {@link NanoScaleFoundry24PoolFrontend}, so the two can never
     * disagree. The Planck chain is the widest recipe: it measured 61 item inputs and 24 fluid inputs before
     * {@link #packMaterialInputs} moved the raw material parts into the fluid grid, which is why the map declares an
     * even 54 item / 54 fluid split instead of those numbers.
     */
    public static final RecipeMap<RecipeMapBackend> nanoScaleFoundry24PoolRecipes = RecipeMapBuilder
        .of("mt.recipe.nanoscale.pool24")
        .maxIO(
            NanoScaleFoundry24PoolFrontend.MAX_ITEM_INPUTS,
            NanoScaleFoundry24PoolFrontend.MAX_ITEM_OUTPUTS,
            NanoScaleFoundry24PoolFrontend.MAX_FLUID_INPUTS,
            NanoScaleFoundry24PoolFrontend.MAX_FLUID_OUTPUTS)
        .minInputs(0, 0)
        .useSpecialSlot()
        .frontend(NanoScaleFoundry24PoolFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setDisplayStack(MTItemList.MTNanoScaleFoundry.get(1))
                .setHeight(NanoScaleFoundry24PoolFrontend.handlerHeight()))
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
        copyPoolIfEmpty(RecipeMaps.nanochipPartProcessorRecipes, nanoScaleFoundrySMDProcessorRecipes);
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
            if (flat == null) continue;
            warnIfWiderThanPool(flat);
            generated.add(flat);
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

    /**
     * The NEI grid of this pool is a fixed {@code maxIO} envelope, so a recipe wider than the pool capacity would
     * spill outside the drawn background. NAC recipes keep growing, so report it instead of drawing a broken page.
     */
    private static void warnIfWiderThanPool(GTRecipe recipe) {
        int itemInputs = recipe.mInputs == null ? 0 : recipe.mInputs.length;
        int fluidInputs = recipe.mFluidInputs == null ? 0 : recipe.mFluidInputs.length;
        if (itemInputs <= NanoScaleFoundry24PoolFrontend.MAX_ITEM_INPUTS
            && fluidInputs <= NanoScaleFoundry24PoolFrontend.MAX_FLUID_INPUTS) {
            return;
        }
        String output = recipe.mOutputs != null && recipe.mOutputs.length > 0 && recipe.mOutputs[0] != null
            ? recipe.mOutputs[0].getDisplayName()
            : "unknown";
        MessTech.MT_LOG.warn(
            "Nano-Scale Foundry 24 pool: the flattened recipe for {} needs {} item inputs and {} fluid inputs, "
                + "more than the pool allows ({} / {}). Raise the MAX_* constants in NanoScaleFoundry24PoolFrontend.",
            output,
            itemInputs,
            fluidInputs,
            NanoScaleFoundry24PoolFrontend.MAX_ITEM_INPUTS,
            NanoScaleFoundry24PoolFrontend.MAX_FLUID_INPUTS);
    }

    /** Prefixes that this pool shows as molten material instead of as items. */
    private static final List<OrePrefixes> MOLTEN_PREFIXES = Arrays
        .asList(OrePrefixes.bolt, OrePrefixes.plate, OrePrefixes.wireFine, OrePrefixes.screw, OrePrefixes.foil);

    /** 16 x 1x wire holds the same material as 1 x 16x wire, so the two are interchangeable. */
    private static final int WIRE_PACK_SIZE = 16;

    /** Result of {@link #packMaterialInputs}: the surviving items plus the fluids they turned into. */
    private static final class PackedInputs {

        private final ItemStack[] items;
        private final FluidStack[] fluids;

        private PackedInputs(ItemStack[] items, FluidStack[] fluids) {
            this.items = items;
            this.fluids = fluids;
        }
    }

    /**
     * Packs the raw material components of a flattened recipe: bolts, plates, fine wires, screws and foils become the
     * molten fluid of their material, and 1x wires are re-expressed as 16x wires 16:1. Both keep the material amount
     * identical - a bolt, a fine wire or a screw is an eighth of an ingot, a foil is a quarter, a plate is one ingot,
     * and a 16x wire is exactly sixteen 1x wires - so the recipe costs the same while the NEI page needs far fewer
     * slots. Entries that share a material merge, because they all become the same fluid. Anything without an ore
     * dictionary association, and anything whose material has no molten form, stays an item; a 1x wire count that is
     * not a multiple of 16 keeps its remainder as 1x wires rather than rounding the material amount up.
     */
    private static PackedInputs packMaterialInputs(ItemStack[] stacks) {
        List<ItemStack> items = new ArrayList<>(stacks.length);
        List<FluidStack> fluids = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) continue;
            ItemData data = GTOreDictUnificator.getAssociation(stack);
            OrePrefixes prefix = data == null ? null : data.mPrefix;
            Materials material = data == null || data.mMaterial == null ? null : data.mMaterial.mMaterial;
            if (prefix == null || material == null) {
                // Most GT++ (and some addon) items never register an ItemData, but their ore dictionary name still
                // carries both halves - "boltRhugnor", "screwQuantum", "plateAstralTitanium" - so that is the
                // fallback used to find their molten fluid.
                OreNameParts parts = parseOreDictionary(stack);
                if (parts != null && MOLTEN_PREFIXES.contains(parts.prefix)) {
                    FluidStack molten = moltenOfName(parts.materialName, materialAmount(parts.prefix, stack.stackSize));
                    if (molten != null) {
                        fluids.add(molten);
                        continue;
                    }
                }
            } else if (MOLTEN_PREFIXES.contains(prefix)) {
                FluidStack molten = material.getMolten(materialAmount(prefix, stack.stackSize));
                if (molten != null) {
                    fluids.add(molten);
                    continue;
                }
            } else if (prefix == OrePrefixes.wireGt01 && stack.stackSize >= WIRE_PACK_SIZE) {
                ItemStack packed = GTOreDictUnificator
                    .get(OrePrefixes.wireGt16, material, stack.stackSize / WIRE_PACK_SIZE);
                if (packed != null && packed.stackSize > 0) {
                    items.add(packed);
                    int rest = stack.stackSize % WIRE_PACK_SIZE;
                    if (rest > 0) {
                        ItemStack leftover = stack.copy();
                        leftover.stackSize = rest;
                        items.add(leftover);
                    }
                    continue;
                }
            }
            items.add(stack);
        }
        return new PackedInputs(items.toArray(new ItemStack[0]), fluids.toArray(new FluidStack[0]));
    }

    /** Ore dictionary name of a packed prefix, split into the prefix itself and the material name behind it. */
    private static final class OreNameParts {

        private final OrePrefixes prefix;
        private final String materialName;

        private OreNameParts(OrePrefixes prefix, String materialName) {
            this.prefix = prefix;
            this.materialName = materialName;
        }
    }

    private static OreNameParts parseOreDictionary(ItemStack stack) {
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            String ore = OreDictionary.getOreName(oreId);
            if (ore == null) continue;
            for (OrePrefixes prefix : MOLTEN_PREFIXES) {
                String prefixName = prefix.getName();
                if (ore.length() > prefixName.length() && ore.startsWith(prefixName)) {
                    return new OreNameParts(prefix, ore.substring(prefixName.length()));
                }
            }
        }
        return null;
    }

    /**
     * Molten fluid of the material behind an ore dictionary suffix. GT's own materials are asked first (their names
     * may differ in case), then GT++'s material registry, whose items never carry an ItemData.
     */
    private static FluidStack moltenOfName(String name, long amount) {
        Materials material = Materials.get(name);
        if (material == null || MaterialUtils.isNullGregtechMaterial(material)) {
            material = null;
            for (Materials candidate : Materials.values()) {
                if (candidate.mName.equalsIgnoreCase(name)) {
                    material = candidate;
                    break;
                }
            }
        }
        if (material != null && !MaterialUtils.isNullGregtechMaterial(material)) {
            FluidStack molten = material.getMolten(amount);
            if (molten != null) return molten;
        }
        gtPlusPlus.core.material.Material gtpp = gtPlusPlus.core.material.Material.mMaterialsByName.get(name);
        if (gtpp == null) {
            gtpp = gtPlusPlus.core.material.Material.mMaterialCache.get(name.toLowerCase());
        }
        return gtpp == null ? null : gtpp.getFluidStack((int) amount);
    }

    /**
     * Material amount in mB for {@code count} items of {@code prefix}. GT stores material amounts in units of
     * {@link GTValues#M} per ingot and an ingot is {@link GTRecipeBuilder#INGOTS} mB, so the two cancel out for whole
     * fractions of an ingot ({@code M / 8} for a bolt, {@code M * 1} for a plate) and the division stays exact.
     */
    private static long materialAmount(OrePrefixes prefix, long count) {
        return Math.max(1, prefix.getMaterialAmount() * GTRecipeBuilder.INGOTS * count / GTValues.M);
    }

    private static FluidStack[] appendFluids(FluidStack[] base, FluidStack[] extra) {
        if (extra.length == 0) return base;
        FluidStack[] all = Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    /**
     * Puts one recipe's inputs into a stable, readable order. The display name comes first so that a whole family of
     * related parts sits together - the two different RAM chips, every superconductor wire, every board - instead of
     * one landing in the first slot and the next one ten slots later. The ore dictionary signature, the item and the
     * amount only break ties, so the order is deterministic between runs.
     */
    private static void sortInputs(List<ItemStack> items, List<FluidStack> fluids) {
        items.sort(
            Comparator.comparing(MTRecipeMaps::itemSortKey)
                .thenComparingInt(stack -> -stack.stackSize));
        fluids.sort(
            Comparator.comparing((FluidStack fluid) -> fluid.getLocalizedName())
                .thenComparingInt(fluid -> -fluid.amount));
    }

    private static String itemSortKey(ItemStack stack) {
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        String signature = data != null && data.mPrefix != null && data.mMaterial != null
            ? data.mPrefix.getName() + data.mMaterial.mMaterial.mName
            : "";
        String displayName;
        try {
            displayName = stack.getDisplayName();
        } catch (RuntimeException ignored) {
            // A broken item name must not take the whole pool down with it.
            displayName = stack.getItem()
                .getUnlocalizedName();
        }
        return displayName + "|"
            + signature
            + "|"
            + stack.getItem()
                .getUnlocalizedName()
            + "|"
            + stack.getItemDamage();
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

        // Show the raw material components as molten fluids and as packed wires, see packMaterialInputs().
        PackedInputs packed = packMaterialInputs(itemInputs);
        // Packing can produce entries that already exist (packed 16x wires, leftover 1x wires), so merge again and
        // then sort: NEI fills the grid in array order, and an unsorted array scatters equal stacks across the page.
        List<ItemStack> sortedItems = new ArrayList<>(Arrays.asList(mergeSameItem(packed.items)));
        List<FluidStack> sortedFluids = new ArrayList<>(
            Arrays.asList(mergeSameFluid(appendFluids(fluidInputs, packed.fluids))));
        sortInputs(sortedItems, sortedFluids);
        itemInputs = sortedItems.toArray(new ItemStack[0]);
        fluidInputs = sortedFluids.toArray(new FluidStack[0]);
        if (itemInputs.length == 0 && fluidInputs.length == 0) return null;

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
        if (circuitLevel >= 1 && circuitLevel <= 5) {
            builder.special(GTUtility.getIntegratedCircuit(circuitLevel));
        }
        return builder.build()
            .orElse(null);
    }

    private static int getOneStepCircuitLevel(CircuitComponent component) {
        if (component == null) return 0;
        // The primitive line is the original GT circuit ladder (Nand chip / Microprocessor / IntegratedProcessor /
        // NanoProcessor / QuantumProcessor), so it gets the selector value 5 instead of falling into the
        // "Processor" name match below - see MTNanoScaleFoundry's 1-5 selector.
        if (component.circuitType == CircuitCalibration.PRIMITIVE) return 5;
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
        int outputAmount = producer.outputAmount;
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

    /**
     * Finds the module recipe that produces {@code component}. Every output slot is checked, not just the
     * first one: NAC recipes may emit several different components in one run (the Wire Tracer spool
     * splits yield three strands at once), and those extra outputs have no recipe of their own.
     */
    private static Producer findNACProducer(CircuitComponent component, FlattenContext ctx) {
        Producer cached = ctx.producerCache.get(component);
        if (cached != null) return cached;
        for (RecipeMap<?> map : NAC_24_RECIPE_MAPS) {
            for (GTRecipe recipe : map.getAllRecipes()) {
                if (recipe.mOutputs == null || recipe.mOutputs.length == 0) continue;
                for (int slot = 0; slot < recipe.mOutputs.length; slot++) {
                    ItemStack output = recipe.mOutputs[slot];
                    if (output == null || getNACComponent(output) != component) continue;
                    Producer producer = new Producer(recipe, map, Math.max(1, output.stackSize));
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
        RecipeMaps.nanochipAssemblyMatrixRecipes, RecipeMaps.nanochipPartProcessorRecipes,
        RecipeMaps.nanochipBoardProcessorRecipes, RecipeMaps.nanochipEtchingArray, RecipeMaps.nanochipCuttingChamber,
        RecipeMaps.nanochipWireTracer, RecipeMaps.nanochipSuperconductorSplitter, RecipeMaps.nanochipOpticalOrganizer,
        RecipeMaps.nanochipEncasementWrapper, RecipeMaps.nanochipBiologicalCoordinator };

    private static final class Producer {

        private final GTRecipe recipe;
        private final RecipeMap<?> map;
        /** Stack size of the matched output slot, used to work out how many runs are needed. */
        private final int outputAmount;

        private Producer(GTRecipe recipe, RecipeMap<?> map, int outputAmount) {
            this.recipe = recipe;
            this.map = map;
            this.outputAmount = outputAmount;
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
