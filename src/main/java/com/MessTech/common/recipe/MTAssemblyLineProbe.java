package com.MessTech.common.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.MessTech.common.machine.Base.MTParallelHelper;
import com.MessTech.init.MessTech;

import gregtech.api.enums.ItemList;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * Behind-the-scenes probe for the Assembly Factory's Assembly Line mode, run at {@code FMLServerStartedEvent} when a
 * {@code messtech-probe.enabled} marker file sits in the server's working directory (see
 * {@code CommonProxy#serverStarted}); it shuts the server down again when done, so a regression test is one
 * {@code ./gradlew runServer} with the marker in place.
 * <p>
 * It checks the parts of the recipe recognition a plain compile cannot:
 * <ol>
 * <li>every conflict-circuit recipe ("检测到冲突" NEI page) when the machine is fed through a <b>Crafting Input Buffer
 * (ME)</b>: GT5U passes the buffer's ghost circuit to the recipe check through its shared items and keeps the hatch in
 * {@code mDualInputHatches}, never in {@code mInputBuses}. The old bus-only selector scan matched none of them, the
 * item-based one must match all;</li>
 * <li>the classic Wetware Mainframe Assembly Line recipe - the alternative-input recipe family from issue #4 - fed
 * through its primary inputs and one variant per single-slot alternative: every variant must materialise and be
 * accepted by the shared parallel calculation;</li>
 * <li>all Assembly Line definitions materialised from their own inputs (regression net for {@code materialise}).</li>
 * </ol>
 * Failures are logged with {@code [ALProbe]} and the server still shuts down; {@code runIfRequested} returns nothing so
 * a normal game start is completely unaffected.
 */
public final class MTAssemblyLineProbe {

    private static final String MARKER_FILE = "messtech-probe.enabled";
    private static final String PREFIX = "[ALProbe] ";

    private MTAssemblyLineProbe() {}

    /** Entry point; only does something when the marker file exists. */
    public static void runIfRequested() {
        if (!new File(MARKER_FILE).isFile()) return;
        int failures;
        try {
            failures = run();
        } catch (Throwable t) {
            MessTech.MT_LOG.error(PREFIX + "probe crashed", t);
            failures = 1;
        }
        MessTech.MT_LOG.info(PREFIX + "done, failures={}", failures);
        stopServer();
    }

    private static int run() {
        // Rebuild the pool and the conflict assignment: addons keep registering Assembly Line definitions until load
        // complete, so this is the first moment the full registry is visible.
        MTRecipeMaps.populateAssFactoryAssemblyLineRecipes();

        final List<GTRecipe.RecipeAssemblyLine> definitions = MTAssemblyLineConflictSolver.loadableDefinitions();
        final List<GTRecipe.RecipeAssemblyLine> gated = new ArrayList<>();
        for (GTRecipe.RecipeAssemblyLine definition : definitions) {
            if (MTAssemblyLineConflictSolver.circuitFor(definition) != null) gated.add(definition);
        }
        MessTech.MT_LOG.info(PREFIX + "definitions={} conflictGated={}", definitions.size(), gated.size());

        int failures = 0;
        failures += probeCraftingInputBuffer(gated);
        failures += probeWetwareMainframe(definitions);
        failures += probeSelfMaterialisation(definitions);
        return failures;
    }

    // region 1) Crafting Input Buffer machine

    /**
     * A Crafting Input Buffer machine keeps its conflict circuit in the buffer - GT5U's {@code doCheckRecipe} passes an
     * {@code IDualInputHatch}'s shared items (the circuit slot included) to the recipe check and never lists the hatch
     * in {@code mInputBusses}. Emulates exactly that input item array per gated recipe and compares the two selector
     * sources.
     */
    private static int probeCraftingInputBuffer(List<GTRecipe.RecipeAssemblyLine> gated) {
        int busOnlyMatches = 0;
        int itemSelectorMatches = 0;
        int pipelineAccepted = 0;
        int detailed = 0;
        for (GTRecipe.RecipeAssemblyLine definition : gated) {
            final Integer circuit = MTAssemblyLineConflictSolver.circuitFor(definition);
            final ItemStack[] inputs = craftingBufferInputs(definition, circuit == null ? 0 : circuit);

            final boolean busOnly = MTAssemblyLineMatcher
                .matches(
                    List.of(definition),
                    inputs,
                    MTAssemblyLineCircuitSelectors.fromInputBuses(Collections.emptyList()))
                .findAny()
                .isPresent();
            final List<GTRecipe> matched = MTAssemblyLineMatcher
                .matches(
                    List.of(definition),
                    inputs,
                    MTAssemblyLineCircuitSelectors.resolve(Collections.emptyList(), Collections.emptyList(), inputs))
                .toList();
            if (busOnly) busOnlyMatches++;
            boolean accepted = false;
            if (!matched.isEmpty()) {
                itemSelectorMatches++;
                accepted = pipelineAccepts(matched.get(0), inputs);
                if (accepted) pipelineAccepted++;
            }
            if (detailed < 5 || !accepted) {
                detailed++;
                MessTech.MT_LOG.info(
                    PREFIX + "CRIB gated output={} circuit={} busOnlySelectors={} itemSelectors={} pipeline={}",
                    definition.mOutput,
                    circuit,
                    busOnly,
                    !matched.isEmpty(),
                    accepted);
            }
        }
        MessTech.MT_LOG.info(
            PREFIX
                + "Crafting Input Buffer gated recipes: bus-slot-only selectors matched {}/{}, item-based selectors matched {}/{}, pipeline accepted {}/{}",
            busOnlyMatches,
            gated.size(),
            itemSelectorMatches,
            gated.size(),
            pipelineAccepted,
            gated.size());
        return itemSelectorMatches == gated.size() && pipelineAccepted == gated.size() ? 0 : 1;
    }

    /** The recipe check input array of a CRIB-fed machine: the buffer's ghost circuit first, then the pattern items. */
    private static ItemStack[] craftingBufferInputs(GTRecipe.RecipeAssemblyLine definition, int circuit) {
        final List<ItemStack> items = new ArrayList<>();
        items.add(GTUtility.getIntegratedCircuit(circuit));
        final List<ItemStack[]> variants = MTAssemblyLineConflictSolver.supplyVariants(definition)
            .variants();
        final ItemStack[] primary = variants.isEmpty() ? new ItemStack[0] : variants.get(0);
        for (ItemStack stack : primary) {
            if (stack != null) items.add(stack);
        }
        return items.toArray(new ItemStack[0]);
    }

    // endregion

    // region 2) Wetware Mainframe

    /**
     * The Wetware Mainframe definition is the reference recipe for {@code mOreDictAlt} inputs (issue #4): every slot
     * with alternatives has to materialise whichever alternative the player loaded, and the shared parallel
     * calculation has to accept the concrete recipe.
     */
    private static int probeWetwareMainframe(List<GTRecipe.RecipeAssemblyLine> definitions) {
        final ItemStack target = ItemList.Circuit_Wetwaremainframe.get(1);
        GTRecipe.RecipeAssemblyLine wetware = null;
        for (GTRecipe.RecipeAssemblyLine definition : definitions) {
            if (GTUtility.areStacksEqual(definition.mOutput, target, true)) {
                wetware = definition;
                break;
            }
        }
        if (wetware == null) {
            MessTech.MT_LOG.info(PREFIX + "Wetware Mainframe: no Assembly Line definition in this pack, skipped");
            return 0;
        }

        final Integer circuit = MTAssemblyLineConflictSolver.circuitFor(wetware);
        final MTAssemblyLineConflictSolver.SupplyVariants supply = MTAssemblyLineConflictSolver.supplyVariants(wetware);
        int matched = 0;
        int pipelineAccepted = 0;
        int alternativeSlots = 0;
        for (int slot = 0; slot < wetware.mInputs.length; slot++) {
            if (MTAssemblyLineConflictSolver.slotChoices(wetware, slot)
                .size() > 1) alternativeSlots++;
        }
        for (ItemStack[] variant : supply.variants()) {
            final ItemStack[] inputs = withCircuit(variant, circuit);
            final List<GTRecipe> recipes = MTAssemblyLineMatcher
                .matches(
                    List.of(wetware),
                    inputs,
                    MTAssemblyLineCircuitSelectors.resolve(Collections.emptyList(), Collections.emptyList(), inputs))
                .toList();
            if (recipes.isEmpty()) continue;
            matched++;
            if (pipelineAccepts(recipes.get(0), inputs)) pipelineAccepted++;
        }
        MessTech.MT_LOG.info(
            PREFIX
                + "Wetware Mainframe: output={} circuit={} slots={} alternativeSlots={} variants={} matched={} pipelineAccepted={}",
            wetware.mOutput,
            circuit,
            wetware.mInputs.length,
            alternativeSlots,
            supply.variants()
                .size(),
            matched,
            pipelineAccepted);
        return matched == supply.variants()
            .size() && pipelineAccepted == matched ? 0 : 1;
    }

    // endregion

    // region 3) whole registry regression

    /** Every definition must materialise from its own (primary) inputs; catches broken {@code materialise} paths. */
    private static int probeSelfMaterialisation(List<GTRecipe.RecipeAssemblyLine> definitions) {
        int failed = 0;
        int reported = 0;
        for (GTRecipe.RecipeAssemblyLine definition : definitions) {
            final Integer circuit = MTAssemblyLineConflictSolver.circuitFor(definition);
            final List<ItemStack[]> variants = MTAssemblyLineConflictSolver.supplyVariants(definition)
                .variants();
            final ItemStack[] inputs = withCircuit(variants.isEmpty() ? new ItemStack[0] : variants.get(0), circuit);
            final List<GTRecipe> recipes = MTAssemblyLineMatcher
                .matches(
                    List.of(definition),
                    inputs,
                    MTAssemblyLineCircuitSelectors.resolve(Collections.emptyList(), Collections.emptyList(), inputs))
                .toList();
            final boolean ok = !recipes.isEmpty() && pipelineAccepts(recipes.get(0), inputs);
            if (ok) continue;
            failed++;
            if (reported++ < 10) {
                MessTech.MT_LOG.warn(
                    PREFIX + "self-materialisation failed output={} circuit={} matched={}",
                    definition.mOutput,
                    circuit,
                    !recipes.isEmpty());
                logSlotDiagnostics(definition);
            }
        }
        MessTech.MT_LOG.info(
            PREFIX + "self-materialisation: {}/{} definitions accepted",
            definitions.size() - failed,
            definitions.size());
        return failed == 0 ? 0 : 1;
    }

    /**
     * For an unmatchable definition: the slot that can never be filled (usually a null ingredient from a broken addon).
     */
    private static void logSlotDiagnostics(GTRecipe.RecipeAssemblyLine definition) {
        for (int slot = 0; slot < definition.mInputs.length; slot++) {
            final ItemStack ingredient = definition.mInputs[slot];
            final int choices = MTAssemblyLineConflictSolver.slotChoices(definition, slot)
                .size();
            if (GTUtility.isStackValid(ingredient) && choices > 0) continue;
            MessTech.MT_LOG.warn(
                PREFIX + "  output={} slot={} ingredient={} valid={} choices={}",
                definition.mOutput,
                slot,
                ingredient,
                GTUtility.isStackValid(ingredient),
                choices);
        }
    }

    // endregion

    // region helpers

    /** The loaded variant plus the ghost circuit item a conflict-gated definition needs in its input array. */
    private static ItemStack[] withCircuit(ItemStack[] variant, @Nullable Integer circuit) {
        if (circuit == null) return variant;
        final ItemStack[] inputs = new ItemStack[variant.length + 1];
        inputs[0] = GTUtility.getIntegratedCircuit(circuit);
        System.arraycopy(variant, 0, inputs, 1, variant.length);
        return inputs;
    }

    /**
     * Both matching paths the machine uses: GT's own {@code GTRecipe#maxParallelCalculatedByInputs} (recipe map / dual
     * input hatch cache) and the mod's {@link MTParallelHelper} copy (the ProcessingLogic pipeline). The appended
     * zero-sized circuit input must be treated as "present, never consumed" by both.
     */
    private static boolean pipelineAccepts(GTRecipe recipe, ItemStack[] inputs) {
        final double gtParallel = recipe.maxParallelCalculatedByInputs(1, recipe.mFluidInputs, inputs);
        final double mtParallel = MTParallelHelper
            .maxParallelCalculatedByInputs(recipe, 1, recipe.mFluidInputs, inputs);
        return gtParallel >= 1 && mtParallel >= 1;
    }

    private static void stopServer() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        MessTech.MT_LOG.info(PREFIX + "shutting the probe server down");
        server.initiateShutdown();
    }

    // endregion
}
