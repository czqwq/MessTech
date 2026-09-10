package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.item.MTNACComponentItem;
import com.MessTech.common.item.MTNACComponentItems;
import com.MessTech.common.misc.MTItemList;

import gregtech.api.enums.ItemList;
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
import gregtech.api.recipe.maps.QuantumComputerFrontend;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;
import tectech.recipe.BECAssemblyFrontend;

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
     * Populates the Assembly Factory's own Assembly Line recipe map from GT's visual recipe pool so
     * it shows an independent NEI tab. Must be called after the base visual recipes exist.
     */
    public static void populateAssFactoryAssemblyLineRecipes() {
        RecipeCategory defaultCategory = assFactoryAssemblyLineRecipes.getDefaultRecipeCategory();
        for (GTRecipe recipe : RecipeMaps.assemblylineVisualRecipes.getAllRecipes()) {
            GTRecipe copy = recipe.copy();
            copy.setRecipeCategory(defaultCategory);
            // Real recipes, not fake: the Assembly Factory uses this map for normal ProcessingLogic
            // lookups (findRecipeQuery does not search fake recipes).
            assFactoryAssemblyLineRecipes.addRecipe(copy, false, false, false);
        }
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
