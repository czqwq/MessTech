package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.init.MessTech;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.items.ItemIntegratedCircuit;

/**
 * Finds Assembly Line definitions that the {@code MTAssFactory}'s unordered matching can confuse ("串配方") and gives
 * every definition involved in such a conflict its own GT programming circuit number.
 * <p>
 * <b>The problem.</b> {@link MTAssemblyLineMatcher} reads all input buses as one item pool, so a definition {@code R}
 * whose whole ingredient list is contained in another definition {@code S}'s input list also matches while the player
 * loaded {@code S}'s materials - and the matcher tries the cheaper EU/t first, so {@code R} can take the run. One
 * recipe per line: with {@code D+A+B+C}, {@code A+B+C} and {@code E+B+A+C} registered, loading the first recipe's
 * materials also satisfies {@code A+B+C}, which then runs and leaves {@code D} behind.
 * <p>
 * <b>The fix.</b> For every definition {@code S} this solver simulates "the player loaded exactly S's inputs" - item
 * counts as the definition asks, each alternative slot taken from {@code mOreDictAlt} (the first one plus one variant
 * per single-slot substitution), fluids as the definition asks - and asks {@link MTAssemblyLineMatcher} which other
 * definitions the resulting pool also satisfies. Every definition that shows up on either side of such a pair is
 * marked conflicting, and each connected group is assigned programming circuits: definitions are grouped by the
 * conflict pairs, groups are taken smallest first while the GT circuit numbers {@code 1..24} last, and inside a group
 * the definitions get consecutive numbers in registration order. Definitions keep those numbers in
 * {@link MTAssemblyLineMatcher} (only a definition carrying its own number in an input bus' ghost circuit slot
 * matches) and in the NEI pages ({@code MTRecipeMaps#ASS_FACTORY_CONFLICT_CIRCUIT}). Groups that no longer fit the 24
 * circuit numbers are left without one; the matcher's "most input items first" order keeps the bigger recipe winning
 * for them.
 * <p>
 * Runs on both sides inside {@code MTRecipeMaps#populateAssFactoryAssemblyLineRecipes}, is deterministic for a given
 * recipe set and re-runnable (it is called again on {@code FMLServerStartedEvent}, when every addon has registered its
 * definitions).
 */
public final class MTAssemblyLineConflictSolver {

    /** GT5U's programming circuit item only exists for configurations {@code 1..24}. */
    private static final int MAX_CIRCUIT = ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER;

    /**
     * Cap on the simulated supply variants of one definition: the first {@code 1 + Σ(slot alternatives - 1)} variants
     * (primary, then one variant per single-slot alternative). Real Assembly Line recipes stay far below this; a
     * definition exceeding it is reported and only its first variants are simulated.
     */
    private static final int MAX_SUPPLY_VARIANTS = 96;

    /** Cap on {@code materialise} pair tests; only a pathological recipe set gets near it. */
    private static final int MAX_PAIR_TESTS = 3_000_000;

    /** Definition to assigned circuit number; empty when nothing conflicts. Replaced atomically by {@link #solve()}. */
    private static volatile Map<GTRecipe.RecipeAssemblyLine, Integer> circuits = Collections.emptyMap();

    private MTAssemblyLineConflictSolver() {}

    /** @return the circuit number a definition needs, or null when it runs without one. */
    @Nullable
    public static Integer circuitFor(@Nullable GTRecipe.RecipeAssemblyLine definition) {
        return definition == null ? null : circuits.get(definition);
    }

    /**
     * Rebuilds the conflict graph and the circuit assignment. Called before the NEI pages are built, so the pages can
     * show the assigned number; safe to call repeatedly.
     */
    public static void solve() {
        final long startNanos = System.nanoTime();
        // The simulation below calls MTAssemblyLineMatcher#materialise, which appends the circuit of the *previous*
        // assignment; clear it first so the scan only ever sees the plain definitions.
        circuits = Collections.emptyMap();
        final List<GTRecipe.RecipeAssemblyLine> definitions = loadableDefinitions();
        final int count = definitions.size();
        if (count == 0) {
            circuits = Collections.emptyMap();
            MessTech.MT_LOG.info("[AssFactory] Assembly Line conflict solver: no definitions to check");
            return;
        }

        // Union-find over the definitions, so every conflict pair ends up in one connected group.
        final int[] group = new int[count];
        for (int index = 0; index < count; index++) group[index] = index;

        final Map<GTUtility.ItemId, List<Integer>> users = indexDefinitions(definitions);
        final boolean[] conflicting = new boolean[count];
        final int[] considered = new int[count];
        int stamp = 0;
        int conflictingCount = 0;
        int truncatedDefinitions = 0;
        int pairTests = 0;
        boolean scanTruncated = false;

        scan: for (int victimIndex = 0; victimIndex < count; victimIndex++) {
            final GTRecipe.RecipeAssemblyLine victim = definitions.get(victimIndex);
            final Map<Fluid, Long> fluidSupply = fluidSupply(victim);
            final SupplyVariants supply = supplyVariants(victim);
            if (supply.truncated) truncatedDefinitions++;

            for (ItemStack[] loaded : supply.variants) {
                final Map<GTUtility.ItemId, Long> pool = MTAssemblyLineMatcher.poolOf(loaded);
                if (pool.isEmpty()) continue;

                // The stamp keeps one candidate from being tested more than once per loaded variant.
                if (++stamp == Integer.MAX_VALUE) {
                    Arrays.fill(considered, 0);
                    stamp = 1;
                }

                for (GTUtility.ItemId item : pool.keySet()) {
                    final List<Integer> candidates = users.get(item);
                    if (candidates == null) continue;
                    for (int thiefIndex : candidates) {
                        if (thiefIndex == victimIndex || considered[thiefIndex] == stamp) continue;
                        considered[thiefIndex] = stamp;

                        if (pairTests >= MAX_PAIR_TESTS) {
                            scanTruncated = true;
                            break scan;
                        }
                        pairTests++;

                        final GTRecipe.RecipeAssemblyLine thief = definitions.get(thiefIndex);
                        if (!fluidsAvailable(thief, fluidSupply)) continue;
                        // The machine's own containment test: can the loaded pool fill every one of the thief's slots?
                        if (MTAssemblyLineMatcher.materialise(thief, pool) == null) continue;

                        if (!conflicting[victimIndex]) {
                            conflicting[victimIndex] = true;
                            conflictingCount++;
                        }
                        if (!conflicting[thiefIndex]) {
                            conflicting[thiefIndex] = true;
                            conflictingCount++;
                        }
                        union(group, victimIndex, thiefIndex);
                    }
                }
            }
        }

        // No conflicts: nothing to assign, and the pages/matcher need no circuit at all.
        if (conflictingCount == 0) {
            circuits = Collections.emptyMap();
            MessTech.MT_LOG.info(
                "[AssFactory] Assembly Line conflict solver: {} definitions, no recipe conflicts, {} ms",
                count,
                elapsedMillis(startNanos));
            return;
        }

        // One circuit group per connected component of the conflict graph.
        final Map<Integer, List<Integer>> componentsByRoot = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (!conflicting[index]) continue;
            componentsByRoot.computeIfAbsent(find(group, index), root -> new ArrayList<>())
                .add(index);
        }
        final List<List<Integer>> components = new ArrayList<>(componentsByRoot.values());
        // Smallest groups first so the 24 circuit numbers cover as many definitions as possible; registration order
        // then keeps the assignment deterministic across client and server.
        components.sort(
            Comparator.comparingInt((List<Integer> component) -> component.size())
                .thenComparingInt(MTAssemblyLineConflictSolver::lowestIndex));

        final Map<GTRecipe.RecipeAssemblyLine, Integer> assigned = new IdentityHashMap<>();
        int nextCircuit = 1;
        int skipped = 0;
        for (List<Integer> component : components) {
            if (nextCircuit + component.size() - 1 > MAX_CIRCUIT) {
                skipped += component.size();
                continue;
            }
            final List<Integer> ordered = new ArrayList<>(component);
            Collections.sort(ordered);
            for (int index : ordered) assigned.put(definitions.get(index), nextCircuit++);
        }
        circuits = Collections.unmodifiableMap(assigned);

        MessTech.MT_LOG.info(
            "[AssFactory] Assembly Line conflict solver: {} definitions, {} conflicting recipes in {} groups, {} ms",
            count,
            conflictingCount,
            components.size(),
            elapsedMillis(startNanos));
        if (!assigned.isEmpty()) {
            MessTech.MT_LOG.info(
                "[AssFactory] Assigned programming circuits 1..{} to {} conflicting Assembly Line definitions",
                nextCircuit - 1,
                assigned.size());
            if (MessTech.MT_LOG.isDebugEnabled()) {
                for (int index = 0; index < count; index++) {
                    final Integer circuit = assigned.get(definitions.get(index));
                    if (circuit != null) {
                        MessTech.MT_LOG.debug(
                            "[AssFactory] assembly line circuit {} -> {}",
                            circuit,
                            definitions.get(index).mOutput);
                    }
                }
            }
        }
        if (skipped > 0) {
            MessTech.MT_LOG.warn(
                "[AssFactory] {} conflicting Assembly Line definitions stay without a programming circuit (the {} circuit numbers are used up); they rely on the specificity-first match order",
                skipped,
                MAX_CIRCUIT);
        }
        if (truncatedDefinitions > 0) {
            MessTech.MT_LOG.info(
                "[AssFactory] {} definition(s) have more than {} alternative supply variants; only the first variants were simulated",
                truncatedDefinitions,
                MAX_SUPPLY_VARIANTS);
        }
        if (scanTruncated) {
            MessTech.MT_LOG
                .warn("[AssFactory] Assembly Line conflict scan stopped after {} pair tests", MAX_PAIR_TESTS);
        }
    }

    // region Conflict detection

    /** The definitions the NEI pages are built for too: a null output, no inputs, negative EU/t or no duration. */
    private static List<GTRecipe.RecipeAssemblyLine> loadableDefinitions() {
        final List<GTRecipe.RecipeAssemblyLine> definitions = new ArrayList<>(
            GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes.size());
        for (GTRecipe.RecipeAssemblyLine definition : GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes) {
            if (definition == null || definition.mOutput == null
                || definition.mInputs == null
                || definition.mInputs.length == 0
                || definition.mEUt < 0
                || definition.mDuration <= 0) {
                continue;
            }
            definitions.add(definition);
        }
        return definitions;
    }

    /**
     * One definition per item it could ever consume: a slot's ingredient plus the {@code mOreDictAlt} alternatives.
     * Lets the scan start from the items actually loaded instead of testing every definition pair.
     */
    private static Map<GTUtility.ItemId, List<Integer>> indexDefinitions(
        List<GTRecipe.RecipeAssemblyLine> definitions) {
        final Map<GTUtility.ItemId, List<Integer>> users = new HashMap<>();
        for (int index = 0; index < definitions.size(); index++) {
            final GTRecipe.RecipeAssemblyLine definition = definitions.get(index);
            for (int slot = 0; slot < definition.mInputs.length; slot++) {
                for (ItemStack choice : slotChoices(definition, slot)) {
                    users.computeIfAbsent(itemKey(choice), key -> new ArrayList<>())
                        .add(index);
                }
            }
        }
        return users;
    }

    /**
     * The stacks a player can put into one slot: the definition's ingredient plus every alternative it accepts. They
     * are deduplicated because GT's own builder usually repeats the first alternative in {@code mInputs}.
     */
    private static List<ItemStack> slotChoices(GTRecipe.RecipeAssemblyLine definition, int slot) {
        final List<ItemStack> choices = new ArrayList<>(4);
        addChoice(choices, definition.mInputs[slot]);
        final ItemStack[][] alternatives = definition.mOreDictAlt;
        if (alternatives != null && slot < alternatives.length && alternatives[slot] != null) {
            for (ItemStack alternative : alternatives[slot]) addChoice(choices, alternative);
        }
        return choices;
    }

    private static void addChoice(List<ItemStack> choices, @Nullable ItemStack stack) {
        if (!GTUtility.isStackValid(stack)) return;
        final GTUtility.ItemId key = itemKey(stack);
        for (ItemStack choice : choices) {
            if (choice.stackSize == stack.stackSize && key.equals(itemKey(choice))) return;
        }
        choices.add(stack);
    }

    /** The item key {@link MTAssemblyLineMatcher} uses: a damage wildcard slot is looked up under its alias. */
    private static GTUtility.ItemId itemKey(ItemStack stack) {
        return stack.getItemDamage() == GTRecipeBuilder.WILDCARD ? GTUtility.ItemId.createAsWildcard(stack)
            : GTUtility.ItemId.createWithoutNBT(stack);
    }

    /**
     * The item pools a player can load for one definition: the primary choice of every slot, then one variant per
     * single-slot alternative so {@code mOreDictAlt} slots are not all forced onto their first choice.
     */
    private static SupplyVariants supplyVariants(GTRecipe.RecipeAssemblyLine definition) {
        final int slots = definition.mInputs.length;
        final List<List<ItemStack>> choices = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) choices.add(slotChoices(definition, slot));

        final ItemStack[] primary = new ItemStack[slots];
        for (int slot = 0; slot < slots; slot++) {
            final List<ItemStack> slotChoices = choices.get(slot);
            primary[slot] = supplyStack(slotChoices.isEmpty() ? null : slotChoices.get(0));
        }

        final List<ItemStack[]> variants = new ArrayList<>();
        variants.add(primary);
        boolean truncated = false;
        for (int slot = 0; slot < slots && !truncated; slot++) {
            final List<ItemStack> options = choices.get(slot);
            for (int option = 1; option < options.size(); option++) {
                if (variants.size() >= MAX_SUPPLY_VARIANTS) {
                    truncated = true;
                    break;
                }
                final ItemStack[] variant = primary.clone();
                variant[slot] = supplyStack(options.get(option));
                variants.add(variant);
            }
        }
        return new SupplyVariants(variants, truncated);
    }

    /**
     * One recipe slot as the stack the player puts in the bus. A {@code stackSize == 0} ingredient is GT's
     * "non-consumed input" convention (a mold, or a programming circuit in a recipe): the player still has to supply a
     * real stack of it, so the simulation uses one item.
     */
    @Nullable
    private static ItemStack supplyStack(@Nullable ItemStack recipeStack) {
        if (recipeStack == null) return null;
        if (recipeStack.stackSize > 0) return GTUtility.copy(recipeStack);
        return GTUtility.copyAmount(1, recipeStack);
    }

    /** The fluids one loaded definition supplies, merged per fluid. */
    private static Map<Fluid, Long> fluidSupply(GTRecipe.RecipeAssemblyLine definition) {
        final Map<Fluid, Long> supply = new HashMap<>();
        if (definition.mFluidInputs == null) return supply;
        for (FluidStack fluid : definition.mFluidInputs) {
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) continue;
            supply.merge(fluid.getFluid(), (long) fluid.amount, Long::sum);
        }
        return supply;
    }

    /** Whether the loaded definition's fluids cover every fluid the candidate definition needs. */
    private static boolean fluidsAvailable(GTRecipe.RecipeAssemblyLine definition, Map<Fluid, Long> supply) {
        if (definition.mFluidInputs == null) return true;
        final Map<Fluid, Long> need = new HashMap<>();
        for (FluidStack fluid : definition.mFluidInputs) {
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) continue;
            need.merge(fluid.getFluid(), (long) fluid.amount, Long::sum);
        }
        for (Map.Entry<Fluid, Long> entry : need.entrySet()) {
            if (supply.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return false;
        }
        return true;
    }

    private record SupplyVariants(List<ItemStack[]> variants, boolean truncated) {}

    // endregion

    // region Union-find

    private static int find(int[] group, int index) {
        int root = index;
        while (group[root] != root) root = group[root];
        while (group[index] != root) {
            final int next = group[index];
            group[index] = root;
            index = next;
        }
        return root;
    }

    /** Keeps the lower index as the root, so a component's root is deterministic. */
    private static void union(int[] group, int left, int right) {
        final int leftRoot = find(group, left);
        final int rightRoot = find(group, right);
        if (leftRoot == rightRoot) return;
        group[Math.max(leftRoot, rightRoot)] = Math.min(leftRoot, rightRoot);
    }

    private static int lowestIndex(List<Integer> indices) {
        int lowest = Integer.MAX_VALUE;
        for (int index : indices) lowest = Math.min(lowest, index);
        return lowest;
    }

    // endregion

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
