package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.items.MTItemList;
import com.MessTech.init.MessTech;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.items.CombType;
import gregtech.common.items.ItemComb;

/**
 * The converter behind {@link MTRecipeMaps#combProcessingRecipes}, the Chemical Twister's mode 2 (蜂窝处理者): it reads
 * the comb recipes of GT5U's <b>large chemical reactor</b> pool - {@link RecipeMaps#multiblockChemicalReactorRecipes} -
 * and rewrites each of them as one recipe of the comb processing pool.
 * <p>
 * The base recipes come from GT5U itself ({@code ItemComb#addProcessGT}, added through
 * {@code GTRecipeConstants.UniversalChemical}) and from NHCoreMod ({@code ChemicalReactorRecipes#multiBlockOnly}, which
 * adds its indium comb recipes to the same large chemical reactor map), so a single scan covers both mods. A base
 * recipe is recognised by a bee comb input ({@code OreDict} {@code beeComb}, i.e. GT5U's {@code ItemComb}).
 * <p>
 * Conversion rules (agreed in the feature request):
 * <ul>
 * <li><b>inputs</b>: only the module (stack size 0, first input, never consumed - see
 * {@link MTItemList#MTCombProcessingModule}) and {@link #BASE_COMB_COUNT} of the comb. Everything else the base recipe
 * asks for (acid, catalyst dusts, circuits, ...) is dropped.</li>
 * <li><b>item outputs</b>: each product at {@link #OUTPUT_MULTIPLIER} times the base amount scaled to
 * {@link #BASE_COMB_COUNT} combs, plus one extra "base amount" product at {@link #BONUS_CHANCE} (50%).
 * {@code crushedPurified} ores are replaced 1:1 by the material's dust ("洁净粉碎X矿" -> "X粉"), every other item is
 * kept as it is.</li>
 * <li><b>nuggets</b> (the neutronium/osmium/platinum/iridium comb recipes): become a molten fluid output of the same
 * material ({@link #NUGGET_MB} mB per nugget) with the same x4 + 50% rule.</li>
 * <li><b>fluid outputs</b>: kept unchanged, including the acidic metal solutions of the special combs and NHCoreMod's
 * indium byproduct fluids.</li>
 * <li><b>indium comb</b>: special-cased on request - one fixed recipe, {@code 4} indium combs to
 * {@link #INDIUM_DUST} indium dust plus {@link #INDIUM_BONUS_DUST} more at {@link #BONUS_CHANCE}, and <b>no</b> fluid
 * product (the base recipe's 72000 mB lead-zinc solution is dropped). Duration and EU/t follow the usual rules from the
 * first indium comb recipe of the pool.</li>
 * <li><b>time</b>: {@link #DURATION_MULTIPLIER} times the base duration; <b>EU/t</b>:
 * {@link #EU_MULTIPLIER} times the base EU/t (one voltage tier up, e.g. HV -> EV).</li>
 * <li><b>structure</b>: structure level 2 and the mode's fixed heat {@link MTRecipeMaps#QFT_PROBABILITY_DESTROYER_HEAT}
 * (12601 K).</li>
 * </ul>
 * A comb gets at most one converted recipe: GT's recipe lookup keys a recipe by its input items (stack sizes ignored
 * when checking for collisions), so a second recipe with the <b>same</b> comb would never be found. Recipes are
 * therefore scanned in pool order and the first one of each comb type wins; the rest are reported once. This is what
 * makes NHCoreMod's four indium comb recipes collapse into one page.
 */
public final class MTCombProcessingRecipes {

    /** Comb count every converted recipe is normalised to, and the base amount its products are scaled from. */
    private static final int BASE_COMB_COUNT = 4;

    /** Multiplier of the base recipe's product amounts. */
    private static final int OUTPUT_MULTIPLIER = 4;

    /** Multiplier of the base recipe's duration. */
    private static final int DURATION_MULTIPLIER = 4;

    /** Multiplier of the base recipe's EU/t: one voltage tier up (e.g. HV -> EV). */
    private static final int EU_MULTIPLIER = 4;

    /** Chance (per 10000) of the extra "one base amount" product: 50%. */
    private static final int BONUS_CHANCE = 5_000;

    /** Indium comb special case: the guaranteed indium dust of the converted recipe. */
    private static final int INDIUM_DUST = 12;

    /** Indium comb special case: the extra indium dust at {@link #BONUS_CHANCE} (50%). */
    private static final int INDIUM_BONUS_DUST = 3;

    /** mB of a molten fluid that one nugget of that material is worth (144 mB per ingot / 9). */
    private static final int NUGGET_MB = 16;

    private static final int FULL_CHANCE = 10_000;

    private MTCombProcessingRecipes() {}

    /**
     * Re-runnable: clears the pool and rebuilds it from the current large chemical reactor recipe list. Called once at
     * {@code FMLLoadComplete} (both sides, after GT's and NHCoreMod's postInit have registered every comb recipe) and
     * again from {@code CommonProxy#serverStarted} as the fallback the other cross-pool converters use, in case a
     * recipe loader only ran after the load-complete pass.
     */
    public static void populateCombProcessingRecipes() {
        RecipeMap<RecipeMapBackend> map = MTRecipeMaps.combProcessingRecipes;
        map.getBackend()
            .clearRecipes();
        RecipeCategory category = map.getDefaultRecipeCategory();

        // One recipe per comb: GT's collision check ignores stack sizes, so equal comb inputs are the same recipe.
        Set<String> claimedCombs = new HashSet<>();
        List<String> skipped = new ArrayList<>();
        int converted = 0;
        for (GTRecipe recipe : RecipeMaps.multiblockChemicalReactorRecipes.getAllRecipes()) {
            ItemStack comb = findCombInput(recipe);
            if (comb == null) continue;

            String combKey = combKey(comb);
            boolean claimedBefore = !claimedCombs.add(combKey);
            if (claimedBefore) {
                skipped.add(comb.getDisplayName());
                continue;
            }
            if (convertRecipe(recipe, comb, category)) {
                converted++;
            } else {
                // Let a later recipe of the same comb have a shot.
                claimedCombs.remove(combKey);
                skipped.add(comb.getDisplayName());
            }
        }

        if (converted == 0) {
            MessTech.MT_LOG.warn("Comb processing pool: no comb recipe found in the large chemical reactor pool");
        } else {
            MessTech.MT_LOG.info(
                "Comb processing pool: {} comb recipes converted, {} duplicate/failed recipes skipped{}",
                converted,
                skipped.size(),
                skipped.isEmpty() ? "" : " (" + String.join(", ", skipped) + ")");
        }
    }

    /** The first bee comb among the base recipe's item inputs, or null when it has none. */
    @Nullable
    private static ItemStack findCombInput(GTRecipe recipe) {
        if (recipe.mInputs == null) return null;
        for (ItemStack stack : recipe.mInputs) {
            if (stack == null || stack.stackSize <= 0) continue;
            if (GTOreDictUnificator.isItemStackInstanceOf(stack, OrePrefixes.beeComb)) return stack;
        }
        return null;
    }

    /**
     * Converts one large chemical reactor comb recipe into the comb processing pool.
     *
     * @return whether the recipe was accepted by the pool (GT can still refuse it, e.g. on a collision we did not
     *         predict).
     */
    private static boolean convertRecipe(GTRecipe base, ItemStack comb, RecipeCategory category) {
        // Indium comb: pinned special case (feature request) - no fluid product, fixed dust amounts.
        if (isIndiumComb(comb)) {
            return addIndiumCombRecipe(base, comb, category);
        }

        double productFactor = (double) OUTPUT_MULTIPLIER * BASE_COMB_COUNT / comb.stackSize;
        double bonusFactor = (double) BASE_COMB_COUNT / comb.stackSize;

        List<ItemStack> itemOutputs = new ArrayList<>();
        List<Integer> itemChances = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        List<Integer> fluidChances = new ArrayList<>();

        if (base.mOutputs != null) {
            for (ItemStack stack : base.mOutputs) {
                if (stack == null || stack.stackSize <= 0) continue;
                int amount = scale(stack.stackSize, productFactor);
                int bonus = scale(stack.stackSize, bonusFactor);

                if (OrePrefixes.crushedPurified.contains(stack)) {
                    // 洁净粉碎X矿 -> X粉 (macerate-into material, as GT's own purified-ore processing does).
                    addGuaranteedItem(itemOutputs, itemChances, oreToDust(stack, amount, comb));
                    addBonusItem(itemOutputs, itemChances, oreToDust(stack, bonus, comb));
                } else if (OrePrefixes.nugget.contains(stack)) {
                    // Nuggets become the material's molten fluid, same x4 + 50% rule.
                    FluidStack molten = nuggetToMolten(stack, amount, comb);
                    if (molten != null) {
                        addGuaranteedFluid(fluidOutputs, fluidChances, molten);
                        addBonusFluid(fluidOutputs, fluidChances, nuggetToMolten(stack, bonus, comb));
                    } else {
                        addGuaranteedItem(itemOutputs, itemChances, GTUtility.copyAmountUnsafe(amount, stack));
                        if (bonus > 0) {
                            addBonusItem(itemOutputs, itemChances, GTUtility.copyAmountUnsafe(bonus, stack));
                        }
                    }
                } else {
                    addGuaranteedItem(itemOutputs, itemChances, GTUtility.copyAmountUnsafe(amount, stack));
                    if (bonus > 0) addBonusItem(itemOutputs, itemChances, GTUtility.copyAmountUnsafe(bonus, stack));
                }
            }
        }

        // The base recipe's fluid products stay as they are (acidic metal solutions, NHCoreMod's indium byproducts).
        if (base.mFluidOutputs != null) {
            for (FluidStack fluid : base.mFluidOutputs) {
                if (fluid == null || fluid.amount <= 0) continue;
                addGuaranteedFluid(fluidOutputs, fluidChances, fluid.copy());
            }
        }

        if (itemOutputs.isEmpty() && fluidOutputs.isEmpty()) {
            MessTech.MT_LOG.warn(
                "Comb processing pool: {} of {} converts to no output, skipped",
                base.mOutputs == null ? "recipe" : base.mOutputs.length + " outputs",
                comb.getDisplayName());
            return false;
        }

        GTRecipeBuilder builder = newRecipeBuilder(base, comb, category);

        if (!itemOutputs.isEmpty()) {
            builder.itemOutputs(itemOutputs.toArray(new ItemStack[0]));
            if (hasBonus(itemChances)) builder.outputChances(toIntArray(itemChances));
        }
        if (!fluidOutputs.isEmpty()) {
            builder.fluidOutputs(fluidOutputs.toArray(new FluidStack[0]));
            if (hasBonus(fluidChances)) builder.fluidOutputChances(toIntArray(fluidChances));
        }

        return !builder.addTo(MTRecipeMaps.combProcessingRecipes)
            .isEmpty();
    }

    /**
     * The indium comb's pinned recipe: no fluid product (the base recipe's lead-zinc solution is dropped), just
     * {@link #INDIUM_DUST} indium dust plus {@link #INDIUM_BONUS_DUST} at {@link #BONUS_CHANCE}. Duration, EU/t,
     * structure level and heat follow the usual conversion of the first indium comb recipe of the pool.
     */
    private static boolean addIndiumCombRecipe(GTRecipe base, ItemStack comb, RecipeCategory category) {
        return !newRecipeBuilder(base, comb, category)
            .itemOutputs(Materials.Indium.getDust(INDIUM_DUST), Materials.Indium.getDust(INDIUM_BONUS_DUST))
            .outputChances(FULL_CHANCE, BONUS_CHANCE)
            .addTo(MTRecipeMaps.combProcessingRecipes)
            .isEmpty();
    }

    /** The module + {@code 4} comb inputs, converted duration/EU/t, heat and structure level every recipe shares. */
    private static GTRecipeBuilder newRecipeBuilder(GTRecipe base, ItemStack comb, RecipeCategory category) {
        return GTRecipeBuilder.builder()
            .itemInputsUnsafe(
                MTItemList.MTCombProcessingModule.get(0),
                GTUtility.copyAmountUnsafe(BASE_COMB_COUNT, comb))
            .duration((long) base.mDuration * DURATION_MULTIPLIER)
            .eut((long) base.mEUt * EU_MULTIPLIER)
            .specialValue(MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 2)
            .recipeCategory(category);
    }

    /** Whether the stack is GT5U's indium comb ({@code GTBees} combs are one item, the variant is the metadata). */
    private static boolean isIndiumComb(ItemStack comb) {
        return comb.getItem() instanceof ItemComb && comb.getItemDamage() == CombType.INDIUM.getId();
    }

    /** {@code amount * factor}, floored so a normalised recipe never hands out more than the ratio allows. */
    private static int scale(int amount, double factor) {
        return (int) Math.floor(amount * factor + 1.0e-9);
    }

    /**
     * A {@code crushedPurified} product as {@code amount} dust of the material it macerates into; the ore item itself
     * (still x4 and chanced the same way) when no dust can be resolved, so a modded comb never loses its product.
     */
    @Nullable
    private static ItemStack oreToDust(ItemStack ore, int amount, ItemStack comb) {
        if (amount <= 0) return null;
        Materials material = macerateInto(ore);
        ItemStack dust = material == null ? null : GTOreDictUnificator.get(OrePrefixes.dust, material, amount);
        if (dust != null) return dust;
        MessTech.MT_LOG.warn(
            "Comb processing pool: no dust for {} of {}, keeping the ore item",
            ore.getDisplayName(),
            comb.getDisplayName());
        return GTUtility.copyAmountUnsafe(amount, ore);
    }

    /** The material a {@code crushedPurified} ore macerates into (GT's purified-ore chain), or null. */
    @Nullable
    private static Materials macerateInto(ItemStack ore) {
        ItemData data = GTOreDictUnificator.getAssociation(ore);
        if (data == null || data.mMaterial == null) return null;
        Materials material = data.mMaterial.mMaterial;
        if (material == null || material == Materials._NULL) return null;
        return material.mMacerateInto;
    }

    /**
     * A nugget product as {@code nuggetCount} nuggets worth of the material's molten fluid, or null when that cannot be
     * resolved (the caller then keeps the nugget item).
     */
    @Nullable
    private static FluidStack nuggetToMolten(ItemStack nugget, int nuggetCount, ItemStack comb) {
        if (nuggetCount <= 0) return null;
        ItemData data = GTOreDictUnificator.getAssociation(nugget);
        Materials material = data == null || data.mMaterial == null ? null : data.mMaterial.mMaterial;
        FluidStack molten = material == null || material == Materials._NULL ? null
            : material.getMolten((long) nuggetCount * NUGGET_MB);
        if (molten != null) return molten;
        MessTech.MT_LOG.warn(
            "Comb processing pool: no molten fluid for {} of {}, keeping the nugget item",
            nugget.getDisplayName(),
            comb.getDisplayName());
        return null;
    }

    private static void addGuaranteedItem(List<ItemStack> outputs, List<Integer> chances, @Nullable ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        outputs.add(stack);
        chances.add(FULL_CHANCE);
    }

    private static void addBonusItem(List<ItemStack> outputs, List<Integer> chances, @Nullable ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        outputs.add(stack);
        chances.add(BONUS_CHANCE);
    }

    /** Adds a guaranteed fluid output, merging it into an equal one already listed. */
    private static void addGuaranteedFluid(List<FluidStack> outputs, List<Integer> chances,
        @Nullable FluidStack stack) {
        if (stack == null || stack.amount <= 0) return;
        for (int i = 0; i < outputs.size(); i++) {
            if (chances.get(i) != FULL_CHANCE) continue;
            FluidStack existing = outputs.get(i);
            if (existing.isFluidEqual(stack)) {
                existing.amount += stack.amount;
                return;
            }
        }
        outputs.add(stack);
        chances.add(FULL_CHANCE);
    }

    private static void addBonusFluid(List<FluidStack> outputs, List<Integer> chances, @Nullable FluidStack stack) {
        if (stack == null || stack.amount <= 0) return;
        outputs.add(stack);
        chances.add(BONUS_CHANCE);
    }

    private static boolean hasBonus(List<Integer> chances) {
        for (int chance : chances) {
            if (chance != FULL_CHANCE) return true;
        }
        return false;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /** Identity of a comb type, independent of its stack size. */
    private static String combKey(ItemStack comb) {
        return comb.getItem()
            .getUnlocalizedName() + "@"
            + comb.getItemDamage()
            + (comb.hasTagCompound() ? ":" + comb.getTagCompound() : "");
    }
}
