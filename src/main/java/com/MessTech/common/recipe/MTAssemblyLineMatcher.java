package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;

/**
 * Order-independent ("unordered") input matching for GT Assembly Line definitions.
 * <p>
 * A definition carries, per input slot, the ingredient and - when that slot accepts several items - every accepted
 * alternative ({@code RecipeAssemblyLine#mOreDictAlt}). GT's own Assembly Line is ordered: it compares the first slot
 * of
 * an input bus with the first input slot, so an alternative only ever matters in the bus slot it was put in. This
 * matcher instead reads all buses as one pool, which is what lets the ingredients sit in any bus in any order.
 * <p>
 * The pool still has to be resolved against the per-slot alternatives somewhere. Registering one recipe per
 * alternative combination is the obvious way, but the count is the product of every slot's alternative count (one
 * Wetware Mainframe definition reaches six digits on its own), so that table cannot be built at world load.
 * <p>
 * Instead one definition is resolved against the current pool on demand: every slot picks the alternative that leaves
 * the most parallels for the whole definition, and the result is a single concrete ingredient list. The caller hands
 * that list to the ordinary GT recipe pipeline (parallels, overclock, void protection, ME buses, input consumption),
 * which matches and consumes it against the same pool, in the same order-independent way.
 */
public final class MTAssemblyLineMatcher {

    private MTAssemblyLineMatcher() {}

    /**
     * Every definition the pool can satisfy, cheapest EU/t first so the caller's recipe search takes the same one a
     * player would expect. Definitions whose slots cannot all be filled are left out.
     */
    public static Stream<GTRecipe> matches(Collection<GTRecipe.RecipeAssemblyLine> definitions, ItemStack[] pool) {
        if (definitions == null || definitions.isEmpty()) return Stream.empty();
        Map<GTUtility.ItemId, Long> availability = poolOf(pool);
        return definitions.stream()
            .sorted(Comparator.comparingInt(definition -> definition.mEUt))
            .map(definition -> materialise(definition, availability))
            .filter(Objects::nonNull);
    }

    /**
     * Every input stack as one pool of {@code item -> amount}. Each stack is also filed under its wildcard-damage
     * alias, because a definition slot may ask for any damage value of an item. NBT is left out of the keys, matching
     * how the recipe pipeline itself compares an ingredient with the stored stacks.
     */
    private static Map<GTUtility.ItemId, Long> poolOf(@Nullable ItemStack[] pool) {
        Map<GTUtility.ItemId, Long> availability = new HashMap<>();
        if (pool == null) return availability;
        for (ItemStack stack : pool) {
            if (stack == null || stack.stackSize <= 0) continue;
            long amount = stack.stackSize;
            availability.merge(GTUtility.ItemId.createWithoutNBT(stack), amount, Long::sum);
            availability.merge(GTUtility.ItemId.createAsWildcard(stack), amount, Long::sum);
        }
        return availability;
    }

    /**
     * One definition as a concrete recipe against the given pool, or null when a slot cannot be filled. The amounts are
     * the definition's; the pool is only read.
     */
    @Nullable
    private static GTRecipe materialise(GTRecipe.RecipeAssemblyLine definition, Map<GTUtility.ItemId, Long> pool) {
        if (definition.mOutput == null || definition.mInputs == null || definition.mInputs.length == 0) return null;
        if (definition.mEUt < 0 || definition.mDuration <= 0) return null;

        List<ItemStack> ingredients = chooseIngredients(definition, pool);
        if (ingredients == null) return null;

        return GTRecipeBuilder.builder()
            .itemInputsUnsafe(ingredients.toArray(new ItemStack[0]))
            .itemOutputs(definition.mOutput)
            .fluidInputs(nonNullFluids(definition.mFluidInputs))
            .eut(definition.mEUt)
            .duration(definition.mDuration)
            .build()
            .orElse(null);
    }

    /**
     * The fluid inputs of a definition without the null slots, for {@code GTRecipeBuilder#fluidInputs(FluidStack...)}.
     * <p>
     * A definition may have no fluids at all or leave a fluid slot empty, and the registry is filled by every addon,
     * not only by GT - {@code GTRecipeConstants#addAssemblingLineRecipe} skips null entries itself when it hashes what
     * it registers. The builder does not tolerate either: with {@code gt.recipebuilder.panic.null} set (GTNH ships it
     * set) a null array or a null entry throws {@code IllegalArgumentException("null in argument")}, which is what
     * stopped {@code CommonProxy#serverStarted} from finishing. The nulls are dropped here instead, which is what the
     * builder does to whatever it is handed ({@code ArrayExt#removeNullFluids}) - a definition with an empty fluid slot
     * keeps its page and its recipe.
     */
    static FluidStack[] nonNullFluids(@Nullable FluidStack[] fluids) {
        if (fluids == null || fluids.length == 0) return GTValues.emptyFluidStackArray;
        int count = 0;
        for (FluidStack fluid : fluids) {
            if (fluid != null) count++;
        }
        if (count == fluids.length) return fluids;
        if (count == 0) return GTValues.emptyFluidStackArray;
        FluidStack[] nonNull = new FluidStack[count];
        int index = 0;
        for (FluidStack fluid : fluids) {
            if (fluid != null) nonNull[index++] = fluid;
        }
        return nonNull;
    }

    /**
     * The ingredient to use for every slot, or null when one of them cannot be filled. Slots are resolved in order and
     * every choice is charged to the pool immediately, so a slot cannot pick an item another slot already needs.
     */
    @Nullable
    private static List<ItemStack> chooseIngredients(GTRecipe.RecipeAssemblyLine definition,
        Map<GTUtility.ItemId, Long> pool) {
        List<ItemStack> ingredients = new ArrayList<>(definition.mInputs.length);
        Map<GTUtility.ItemId, Long> required = new HashMap<>();
        for (int slot = 0; slot < definition.mInputs.length; slot++) {
            ItemStack ingredient = chooseIngredient(
                definition.mInputs[slot],
                alternativesOf(definition, slot),
                pool,
                required);
            if (ingredient == null) return null;
            if (ingredient.stackSize > 0) {
                required.merge(ingredientKey(ingredient), (long) ingredient.stackSize, Long::sum);
            }
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    /** The alternatives a slot accepts, or null when it only accepts the ingredient it was written with. */
    @Nullable
    private static ItemStack[] alternativesOf(GTRecipe.RecipeAssemblyLine definition, int slot) {
        if (definition.mOreDictAlt == null || slot >= definition.mOreDictAlt.length) return null;
        ItemStack[] alternatives = definition.mOreDictAlt[slot];
        return alternatives != null && alternatives.length > 0 ? alternatives : null;
    }

    /**
     * The ingredient or alternative that leaves the most parallels for the whole definition. A tie keeps the earlier
     * candidate, i.e. the ingredient itself before its alternatives.
     */
    @Nullable
    private static ItemStack chooseIngredient(ItemStack ingredient, @Nullable ItemStack[] alternatives,
        Map<GTUtility.ItemId, Long> pool, Map<GTUtility.ItemId, Long> required) {
        ItemStack best = better(ingredient, pool, required, null, 0);
        long bestParallels = remainingParallels(best, pool, required);
        if (alternatives == null) return best;

        for (ItemStack alternative : alternatives) {
            // identity comparison is intended here: better() hands back the current best when it is not improved on
            ItemStack candidate = better(alternative, pool, required, best, bestParallels);
            if (candidate != best) {
                best = candidate;
                bestParallels = remainingParallels(best, pool, required);
            }
        }
        return best;
    }

    @Nullable
    private static ItemStack better(@Nullable ItemStack candidate, Map<GTUtility.ItemId, Long> pool,
        Map<GTUtility.ItemId, Long> required, @Nullable ItemStack best, long bestParallels) {
        if (GTUtility.isStackInvalid(candidate)) return best;
        long candidateParallels = remainingParallels(candidate, pool, required);
        if (candidateParallels <= 0) return best;
        if (best == null || candidateParallels > bestParallels) return candidate;
        return best;
    }

    /** How often the slot can still be filled once every ingredient chosen so far has been paid for. */
    private static long remainingParallels(@Nullable ItemStack ingredient, Map<GTUtility.ItemId, Long> pool,
        Map<GTUtility.ItemId, Long> required) {
        if (GTUtility.isStackInvalid(ingredient)) return 0;
        long available = pool.getOrDefault(ingredientKey(ingredient), 0L);
        // an ingredient of size 0 is a catalyst: it has to be there, but it is never consumed
        if (ingredient.stackSize <= 0) return available > 0 ? Long.MAX_VALUE : 0;
        long needed = required.getOrDefault(ingredientKey(ingredient), 0L) + ingredient.stackSize;
        return needed <= 0 ? 0 : available / needed;
    }

    private static GTUtility.ItemId ingredientKey(ItemStack ingredient) {
        // a slot asking for any damage value is looked up under the wildcard alias the pool filed the stack under
        return ingredient.getItemDamage() == GTRecipeBuilder.WILDCARD ? GTUtility.ItemId.createAsWildcard(ingredient)
            : GTUtility.ItemId.createWithoutNBT(ingredient);
    }
}
