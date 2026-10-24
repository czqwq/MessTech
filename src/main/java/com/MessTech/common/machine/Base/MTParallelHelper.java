package com.MessTech.common.machine.Base;

import static gregtech.api.util.GTRecipe.GTppRecipeHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.util.MTItemID;
import com.MessTech.init.Config;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IRecipeLockable;
import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SingleRecipeCheck;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.api.util.VoidProtectionHelper;
import ic2.core.Ic2Items;

// spotless:off
public class MTParallelHelper extends ParallelHelper {

    // region Variables
    private static final double MAX_BATCH_MODE_TICK_TIME = 128;
    private IVoidable machine;
    private IRecipeLockable singleRecipeMachine;
    private boolean isRecipeLocked;
    private GTRecipe recipe;
    /** The total EUt available to the multiblock */
    private long availableEUt;
    private int currentParallel = 0;
    private int maxParallel = 1;
    /** Batch mode: 1 does nothing, 2 doubles the max possible parallel and the duration */
    private int batchModifier = 1;
    private ItemStack[] itemInputs;
    private ItemStack[] itemOutputs;
    private FluidStack[] fluidInputs;
    private FluidStack[] fluidOutputs;
    private boolean protectExcessItem;
    private boolean protectExcessFluid;
    private boolean consume;
    private boolean batchMode;
    private boolean calculateOutputs;
    private boolean built;
    /** Set when Config.machines.ENABLE_ONE_BATCH_CALCULATOR turned the whole recipe batch into one long run. */
    private boolean oneBatchMode;
    private double durationMultiplier;
    /** Modifier applied on the recipe eut (useful for GT++ machines) */
    private float eutModifier = 1;
    /** Calculates the max parallel from the given inputs */
    private MaxParallelCalculator maxParallelCalculator = MTParallelHelper::maxParallelCalculatedByInputs;
    /** Consumes the inputs once the parallel count is known */
    private InputConsumer inputConsumer = MTParallelHelper::consumeInput;

    private OverclockCalculator calculator;

    private CheckRecipeResult result = CheckRecipeResultRegistry.NONE;

    private Function<Integer, ItemStack[]> customItemOutputCalculation;

    private Function<Integer, FluidStack[]> customFluidOutputCalculation;

    // endregion
    public MTParallelHelper() {
    }

    // region Setters


    /** Sets the machine, taking its own void protection flags. */
    public ParallelHelper setMachine(IVoidable machine) {
        return setMachine(machine, machine.protectsExcessItem(), machine.protectsExcessFluid());
    }

    /** Sets the machine and the void protection flags. */
    public ParallelHelper setMachine(IVoidable machine, boolean protectExcessItem, boolean protectExcessFluid) {
        this.protectExcessItem = protectExcessItem;
        this.protectExcessFluid = protectExcessFluid;
        this.machine = machine;
        return this;
    }

    /** Sets the recipe used for the parallel calculation. */
    public ParallelHelper setRecipe(@Nonnull GTRecipe aRecipe) {
        recipe = Objects.requireNonNull(aRecipe);
        return this;
    }

    public ParallelHelper setRecipeLocked(IRecipeLockable singleRecipeMachine, boolean isRecipeLocked) {
        this.singleRecipeMachine = singleRecipeMachine;
        this.isRecipeLocked = isRecipeLocked;
        return this;
    }


    public ParallelHelper setItemInputs(ItemStack... aItemInputs) {
        this.itemInputs = aItemInputs;
        return this;
    }


    public ParallelHelper setFluidInputs(FluidStack... aFluidInputs) {
        this.fluidInputs = aFluidInputs;
        return this;
    }


    public ParallelHelper setAvailableEUt(long aAvailableEUt) {
        this.availableEUt = aAvailableEUt;
        return this;
    }

    /** Modifier for the recipe eut: 1 does nothing, 0.9 is 10% less, 1.1 is 10% more. */
    public ParallelHelper setEUtModifier(float aEUtModifier) {
        this.eutModifier = aEUtModifier;
        return this;
    }

    public ParallelHelper setCalculator(OverclockCalculator calculator) {
        this.calculator = calculator;
        return this;
    }

    /**
     * Use {@link #setConsumption(boolean)}
     */
    @Deprecated
    public ParallelHelper enableConsumption() {
        return setConsumption(true);
    }


    public ParallelHelper setConsumption(boolean consume) {
        this.consume = consume;
        return this;
    }


    public ParallelHelper setMaxParallel(int maxParallel) {
        this.maxParallel = maxParallel;
        return this;
    }

    /** Enables batch mode: up to mCurrentParallel * mBatchModifier extra recipes, a modifier of 1 does nothing. */
    public ParallelHelper enableBatchMode(int batchModifier) {
        this.batchMode = batchModifier > 1;
        this.batchModifier = batchModifier;
        return this;
    }

    /**
     * Use {@link #setOutputCalculation(boolean)}
     */
    @Deprecated
    public ParallelHelper enableOutputCalculation() {
        return setOutputCalculation(true);
    }


    public ParallelHelper setOutputCalculation(boolean calculateOutputs) {
        this.calculateOutputs = calculateOutputs;
        return this;
    }

    /** Custom item output calculation: gets the parallel count, returns the ItemStack array. */
    public ParallelHelper setCustomItemOutputCalculation(Function<Integer, ItemStack[]> custom) {
        customItemOutputCalculation = custom;
        return this;
    }

    /** Custom fluid output calculation: gets the parallel count, returns the FluidStack array. */
    public ParallelHelper setCustomFluidOutputCalculation(Function<Integer, FluidStack[]> custom) {
        customFluidOutputCalculation = custom;
        return this;
    }

    // endregion

    /** Finishes the ParallelHelper; anything changed after this has no effect. */
    public ParallelHelper build() {
        if (built) {
            throw new IllegalStateException("Tried to build twice");
        }
        if (recipe == null) {
            throw new IllegalStateException("Recipe is not set");
        }
        built = true;
        determineParallel();
        return this;
    }


    public int getCurrentParallel() {
        if (!built) {
            throw new IllegalStateException("Tried to get parallels before building");
        }
        return currentParallel;
    }


    public double getDurationMultiplierDouble() {
        if (!built) {
            throw new IllegalStateException("Tried to get duration multiplier before building");
        }
        if ((oneBatchMode || batchMode) && durationMultiplier > 0) {
            return durationMultiplier;
        }
        return 1;
    }

    /**
     * @return true when the last {@link #build()} calculated the recipe as one batch: EU/t divided by the parallel
     *         count, duration multiplied by it, see {@link #determineParallel()}.
     */
    public boolean isOneBatchMode() {
        return oneBatchMode;
    }

    /**
     * @deprecated Use {@link #getDurationMultiplierDouble()}
     */
    @Deprecated
    public float getDurationMultiplier() {
        return (float) getDurationMultiplierDouble();
    }


    @Nonnull
    public ItemStack[] getItemOutputs() {
        if (!built || !calculateOutputs) {
            throw new IllegalStateException(
                "Tried to get item outputs before building or without enabling calculation of outputs");
        }
        return itemOutputs;
    }


    @Nonnull
    public FluidStack[] getFluidOutputs() {
        if (!built || !calculateOutputs) {
            throw new IllegalStateException(
                "Tried to get fluid outputs before building or without enabling calculation of outputs");
        }
        return fluidOutputs;
    }


    @Nonnull
    public CheckRecipeResult getResult() {
        if (!built) {
            throw new IllegalStateException("Tried to get recipe result before building");
        }
        return result;
    }

    /**
     * Use {@link #tryConsumeRecipeInputs(GTRecipe, FluidStack[], ItemStack[], int)}
     */
    @Deprecated
    protected boolean tryConsumeRecipeInputs(GTRecipe recipe, FluidStack[] fluids, ItemStack[] items) {
        return tryConsumeRecipeInputs(recipe, fluids, items, 1);
    }

    /**
     * Try to consume the inputs of the recipe.
     *
     * @param minParallel minimum amount of parallels to do with this check
     * @return True if recipe was satisfied, else false
     */
    protected boolean tryConsumeRecipeInputs(GTRecipe recipe, FluidStack[] fluids, ItemStack[] items,
                                             int minParallel) {
        return recipe.isRecipeInputEqual(true, false, minParallel, fluids, items);
    }


    /** Called by build(): determines the parallels and everything else needed at build time. */
    protected void determineParallel() {

        if (maxParallel <= 0) {
            return;
        }

        if (itemInputs == null) {
            itemInputs = GTValues.emptyItemStackArray;
        }
        if (fluidInputs == null) {
            fluidInputs = GTValues.emptyFluidStackArray;
        }

        if (!consume) {
            copyInputs();
        }

        if (calculator == null) {
            calculator = new OverclockCalculator().setEUt(availableEUt)
                                                     .setRecipeEUt(recipe.mEUt)
                                                     .setDuration(recipe.mDuration)
                                                     .setEUtDiscount(eutModifier);
        }

        double heatDiscountMultiplier = calculator.calculateHeatDiscountMultiplier();

        final int tRecipeEUt = (int) Math.ceil(recipe.mEUt * eutModifier * heatDiscountMultiplier);

        if (availableEUt < tRecipeEUt) {
            result = CheckRecipeResultRegistry.insufficientPower(tRecipeEUt);
            return;
        }

        // One batch mode: take everything the input buses hold in one run, see the config's comment. Recipes
        // without any consumed input have nothing to batch, so they keep the normal path.
        final boolean oneBatch = Config.machines.ENABLE_ONE_BATCH_CALCULATOR && hasConsumableInputs();

        // Save maxParallel before the under-one-tick overclock correction
        int originalMaxParallel = maxParallel;
        calculator.setParallel(originalMaxParallel);

        if (oneBatch) {
            // "Ignore parallel": the cap is whatever the current inputs can pay for, not the machine's parallel cap.
            int candidate = (int) maxParallelCalculator.calculate(recipe, Integer.MAX_VALUE, fluidInputs, itemInputs);
            if (candidate <= 0) {
                result = CheckRecipeResultRegistry.INTERNAL_ERROR;
                return;
            }
            // The duration is multiplied by the parallel count, so the run has to stay below the int tick limit. The
            // calculator can only shorten a recipe (speed bonus <= 1 on every MT machine), which makes the unscaled
            // recipe duration a safe upper bound.
            candidate = Math.min(candidate, Math.max(1, (Integer.MAX_VALUE - 1) / Math.max(1, recipe.mDuration)));
            maxParallel = candidate;
        } else {
            // With a custom supplier use the old under-one-tick method, otherwise the multiplier
            if (calculator.hasDurationUnderOneTickSupplier()) {
                if (calculator.getDurationUnderOneTickSupplier() < 1) {
                    maxParallel = GTUtility
                        .safeInt((long) (maxParallel / calculator.getDurationUnderOneTickSupplier()), 0);
                }
            } else {
                maxParallel = GTUtility.safeInt((long) (maxParallel * calculator.calculateMultiplierUnderOneTick()), 0);
            }
        }

        int maxParallelBeforeBatchMode = maxParallel;
        if (batchMode && !oneBatch) {
            maxParallel = GTUtility.safeInt((long) maxParallel * batchModifier, 1);
        }

        final ItemStack[] truncatedItemOutputs = recipe.mOutputs != null
                                                     ? Arrays.copyOfRange(recipe.mOutputs, 0, Math.min(machine.getItemOutputLimit(), recipe.mOutputs.length))
                                                     : new ItemStack[0];
        final FluidStack[] truncatedFluidOutputs = recipe.mFluidOutputs != null ? Arrays
                                                                                      .copyOfRange(recipe.mFluidOutputs, 0, Math.min(machine.getFluidOutputLimit(), recipe.mFluidOutputs.length))
                                                       : new FluidStack[0];

        SingleRecipeCheck recipeCheck = null;
        SingleRecipeCheck.Builder tSingleRecipeCheckBuilder = null;
        if (isRecipeLocked && singleRecipeMachine != null) {
            recipeCheck = singleRecipeMachine.getSingleRecipeCheck();
            if (recipeCheck == null) {
                // Locked to a single recipe but the checker is not built yet: build it on the next successful recipe.
                RecipeMap<?> recipeMap = singleRecipeMachine.getRecipeMap();
                if (recipeMap != null) {
                    tSingleRecipeCheckBuilder = SingleRecipeCheck.builder(recipeMap)
                                                                 .setBefore(itemInputs, fluidInputs);
                }
            }
        }

        // How many parallels fit with void protection
        if (protectExcessItem || protectExcessFluid) {
            if (machine == null) {
                throw new IllegalStateException("Tried to calculate void protection, but machine is not set");
            }
            VoidProtectionHelper voidProtectionHelper = new VoidProtectionHelper();
            voidProtectionHelper.setMachine(machine)
                                .setItemOutputs(truncatedItemOutputs)
                                .setFluidOutputs(truncatedFluidOutputs)
                                .setOutputChanceGetter(recipe::getOutputChance)
                                .setOutputChanceMultiplier(chanceMultiplier)
                                .setMaxParallel(maxParallel)
                                .build();
            maxParallel = Math.min(voidProtectionHelper.getMaxParallel(), maxParallel);
            if (voidProtectionHelper.isItemFull()) {
                result = CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
                return;
            }
            if (voidProtectionHelper.isFluidFull()) {
                result = CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
                return;
            }
        }

        maxParallelBeforeBatchMode = Math.min(maxParallel, maxParallelBeforeBatchMode);

        final int actualMaxParallel;
        if (oneBatch) {
            // "Ignore parallel": no power-based parallel cap. The base recipe cost was checked above, and the batch
            // itself is paid for by the longer duration instead of a higher EU/t.
            actualMaxParallel = maxParallelBeforeBatchMode;
        } else {
            actualMaxParallel = tRecipeEUt > 0 ? (int) Math.min(maxParallelBeforeBatchMode, availableEUt / tRecipeEUt)
                                               : maxParallelBeforeBatchMode;
        }
        if (recipeCheck != null) {
            currentParallel = recipeCheck.checkRecipeInputs(true, actualMaxParallel, itemInputs, fluidInputs);
        } else {
            currentParallel = (int) maxParallelCalculator.calculate(recipe, actualMaxParallel, fluidInputs, itemInputs);
            if (currentParallel > 0) {
                if (tSingleRecipeCheckBuilder != null) {
                    // No checker yet: build and set it
                    inputConsumer.consume(recipe, 1, fluidInputs, itemInputs);
                    SingleRecipeCheck builtCheck = tSingleRecipeCheckBuilder.setAfter(itemInputs, fluidInputs)
                                                                            .setRecipe(recipe)
                                                                            .build();
                    singleRecipeMachine.setSingleRecipeCheck(builtCheck);
                    inputConsumer.consume(recipe, currentParallel - 1, fluidInputs, itemInputs);
                } else {
                    inputConsumer.consume(recipe, currentParallel, fluidInputs, itemInputs);
                }
            }
        }

        if (currentParallel <= 0) {
            result = CheckRecipeResultRegistry.INTERNAL_ERROR;
            return;
        }

        if (oneBatch) {
            // The batch is calculated at its real parallel count, so the overclock level matches a normal run. The
            // EU/t is then divided back down by the same count in MTProcessingLogic#applyRecipe and the duration is
            // multiplied back up through getDurationMultiplierDouble().
            calculator.setParallel(currentParallel)
                      .calculate();
            durationMultiplier = currentParallel;
            oneBatchMode = true;
        } else {
            calculator.setParallel(Math.min(currentParallel, originalMaxParallel))
                      .calculate();
        }

        // Batch mode: how many extra parallels fit under MAX_BATCH_MODE_TICK_TIME
        if (batchMode && !oneBatch && currentParallel > 0 && calculator.getDuration() < MAX_BATCH_MODE_TICK_TIME) {
            int tExtraParallels;
            double batchMultiplierMax = MAX_BATCH_MODE_TICK_TIME / calculator.getDuration();
            final int maxExtraParallels = (int) Math.floor(
                Math.min(
                    currentParallel * Math.min(batchMultiplierMax - 1, batchModifier - 1),
                    maxParallel - currentParallel));
            if (recipeCheck != null) {
                tExtraParallels = recipeCheck.checkRecipeInputs(true, maxExtraParallels, itemInputs, fluidInputs);
            } else {
                tExtraParallels = (int) maxParallelCalculator
                                            .calculate(recipe, maxExtraParallels, fluidInputs, itemInputs);
                inputConsumer.consume(recipe, tExtraParallels, fluidInputs, itemInputs);
            }
            durationMultiplier = 1.0f + (float) tExtraParallels / currentParallel;
            currentParallel += tExtraParallels;
        }

        // Outputs, if the helper should calculate them
        if (calculateOutputs && currentParallel > 0) {
            if (recipe.mOutputs != null) {
                calculateItemOutputs(truncatedItemOutputs);
            }
            if (recipe.mFluidOutputs != null) {
                calculateFluidOutputs(truncatedFluidOutputs);
            }
        }
        result = CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** @return true if the recipe consumes at least one item or fluid, i.e. if one batch mode has something to batch. */
    private boolean hasConsumableInputs() {
        for (ItemStack stack : recipe.mInputs) {
            if (stack != null && stack.stackSize > 0) return true;
        }
        for (FluidStack stack : recipe.mFluidInputs) {
            if (stack != null && stack.amount > 0) return true;
        }
        return false;
    }

    /**
     * Shadow the parent input fields: {@link ParallelHelper}'s inherited copyInputs reads fields this class never
     * assigns and throws a {@link NullPointerException} when a machine runs with
     * {@link #setConsumption(boolean) consumption disabled} (e.g. MTDTPF in wireless mode, which has no input bus).
     */
    @Override
    protected void copyInputs() {
        ItemStack[] itemInputsToUse = new ItemStack[itemInputs.length];
        for (int i = 0; i < itemInputs.length; i++) {
            itemInputsToUse[i] = itemInputs[i].copy();
        }
        FluidStack[] fluidInputsToUse = new FluidStack[fluidInputs.length];
        for (int i = 0; i < fluidInputs.length; i++) {
            fluidInputsToUse[i] = fluidInputs[i].copy();
        }
        itemInputs = itemInputsToUse;
        fluidInputs = fluidInputsToUse;
    }

    @Override
    protected void calculateItemOutputs(ItemStack[] truncatedItemOutputs) {
        if (customItemOutputCalculation != null) {
            itemOutputs = customItemOutputCalculation.apply(currentParallel);
            return;
        }
        ArrayList<ItemStack> toOutput = new ArrayList<>();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            int outputChance = recipe.getOutputChance(i);
            ItemStack origin = recipe.getOutput(i).copy();
            if (outputChance < 10000) {
                // parameter of this item final amount
                long outputsParallel = (long) currentParallel * outputChance / 10000;
                long remain = (long) currentParallel * outputChance % 10000;

                if (remain > 0) {
                    if (remain > XSTR.XSTR_INSTANCE.nextInt(10000)) {
                        outputsParallel ++;
                    }
                }

                long outputs = outputsParallel * origin.stackSize;

                while (outputs >= Integer.MAX_VALUE) {
                    toOutput.add(GTUtility.copyAmountUnsafe(Integer.MAX_VALUE, origin));
                    outputs -= Integer.MAX_VALUE;
                }

                if (outputs > 0) {
                    toOutput.add(GTUtility.copyAmountUnsafe((int) outputs, origin));
                }

            } else {
                long outputs = (long) currentParallel * origin.stackSize;
                while (outputs > Integer.MAX_VALUE) {
                    toOutput.add(GTUtility.copyAmountUnsafe(Integer.MAX_VALUE, origin));
                    outputs -= Integer.MAX_VALUE;
                }

                if (outputs > 0) {
                    toOutput.add(GTUtility.copyAmountUnsafe((int) outputs, origin));
                }
            }

        }

        itemOutputs = toOutput.toArray(new ItemStack[0]);
    }

    @Override
    protected void calculateFluidOutputs(FluidStack[] truncatedFluidOutputs) {
        if (customFluidOutputCalculation != null) {
            fluidOutputs = customFluidOutputCalculation.apply(currentParallel);
            return;
        }
        ArrayList<FluidStack> toOutput = new ArrayList<>();
        fluidOutputs = new FluidStack[recipe.mFluidOutputs.length];
        for (int i = 0; i < recipe.mFluidOutputs.length; i++) {
            if (recipe.getFluidOutput(i) != null) {
                FluidStack tFluid = recipe.getFluidOutput(i)
                    .copy();
                long amount = (long) tFluid.amount * currentParallel;
                while (amount >= Integer.MAX_VALUE) {
                    FluidStack fluid = tFluid.copy();
                    fluid.amount = Integer.MAX_VALUE;
                    toOutput.add(fluid);
                    amount -= Integer.MAX_VALUE;
                }
                FluidStack fluid = tFluid.copy();
                fluid.amount = (int) amount;
                toOutput.add(fluid);
            }
        }
        fluidOutputs = toOutput.toArray(new FluidStack[0]);
    }

    /**
     * Rewrite from {@link GTRecipe#consumeInput(int, FluidStack[], ItemStack...)}
     */
    public static void consumeInput(GTRecipe recipe, int amountMultiplier, FluidStack[] aFluidInputs, ItemStack... aInputs) {
        if (amountMultiplier <= 0) return;

        long remainingCost;

        if (aFluidInputs != null) {
            for (FluidStack recipeFluidCost : recipe.mFluidInputs) {
                if (recipeFluidCost != null) {
                    remainingCost = (long) recipeFluidCost.amount * amountMultiplier;

                    for (FluidStack providedFluid : aFluidInputs) {
                        if (providedFluid != null && providedFluid.isFluidEqual(recipeFluidCost)) {
                            if (providedFluid.amount >= remainingCost) {
                                providedFluid.amount -= (int) remainingCost;
                                break;
                            } else {
                                remainingCost -= providedFluid.amount;
                                providedFluid.amount = 0;
                            }
                        }
                    }
                }
            }
        }

        if (aInputs != null) {
            for (ItemStack recipeItemCost : recipe.mInputs) {
                if (recipeItemCost.stackSize == 0) continue;
                ItemStack unifiedItemCost = GTOreDictUnificator.get_nocopy(recipeItemCost);
                if (unifiedItemCost != null) {
                    remainingCost = (long) recipeItemCost.stackSize * amountMultiplier;

                    for (ItemStack providedItem : aInputs) {
                        if (recipe.isNBTSensitive && !GTUtility.areStacksEqual(providedItem, unifiedItemCost, false)) {
                            continue;
                        } else if (!recipe.isNBTSensitive
                                       && !GTOreDictUnificator.isInputStackEqual(providedItem, unifiedItemCost)) {
                            continue;
                        }

                        if (GTppRecipeHelper) { // Please see JavaDoc on GTppRecipeHelper for why this is here.
                            if (GTUtility.areStacksEqual(providedItem, Ic2Items.FluidCell.copy(), true)
                                    || GTUtility.areStacksEqual(providedItem, ItemList.Tool_DataStick.get(1L), true)
                                    || GTUtility.areStacksEqual(providedItem, ItemList.Tool_DataOrb.get(1L), true)) {
                                if (!GTUtility.areStacksEqual(providedItem, recipeItemCost, false)) continue;
                            }
                        }

                        if (providedItem.stackSize >= remainingCost) {
                            providedItem.stackSize -= (int) remainingCost;
                            break;
                        } else {
                            remainingCost -= providedItem.stackSize;
                            providedItem.stackSize = 0;
                        }
                    }
                }
            }
        }
    }

    /**
     * @return The number of parallel recipes, 0 if the recipe is not satisfied at all; 0 < n < 1 means inputs are
     *         found but not enough. Refer to SingleRecipeCheck#checkRecipeInputs.
     */
    public static double maxParallelCalculatedByInputs(GTRecipe recipe, int maxParallel, FluidStack[] aFluidInputs, ItemStack... aInputs) {
        if (recipe.mInputs.length > 0 && aInputs == null) return 0;
        if (recipe.mFluidInputs.length > 0 && aFluidInputs == null) return 0;


        double currentParallel = maxParallel;

        if (recipe.mFluidInputs.length > 0) {
            Map<Fluid, Long> fluidMap = new HashMap<>();
            Map<Fluid, Long> fluidCost = new HashMap<>();
            for (FluidStack fluidStack : aFluidInputs) {
                if (fluidStack == null) continue;
                fluidMap.merge(fluidStack.getFluid(), (long) fluidStack.amount, Long::sum);
            }
            for (FluidStack fluidStack : recipe.mFluidInputs) {
                if (fluidStack == null) continue;
                fluidCost.merge(fluidStack.getFluid(), (long) fluidStack.amount, Long::sum);
            }

            for (Map.Entry<Fluid, Long> costEntry : fluidCost.entrySet()) {
                if (costEntry.getValue() > 0) {
                    currentParallel = Math.min(
                        currentParallel,
                        (double) fluidMap.getOrDefault(costEntry.getKey(), 0L) / costEntry.getValue());
                }
                if (currentParallel <= 0) {
                    return 0;
                }
            }
        }

        boolean isNBTSensitive = recipe.isNBTSensitive;
        double remainingCost;
        long providedAmount;
        if (recipe.mInputs.length > 0) {
            Map<MTItemID, Long> itemCost = new HashMap<>();
            if (isNBTSensitive) {
                for (ItemStack recipeItemCost : recipe.mInputs) {
                    if (recipeItemCost == null) continue;
                    // for non-consumed input
                    if (recipeItemCost.stackSize == 0) continue;
                    itemCost.merge(MTItemID.create(recipeItemCost), (long) recipeItemCost.stackSize, Long::sum);
                }
            } else {
                for (ItemStack recipeItemCost : recipe.mInputs) {
                    if (recipeItemCost == null) continue;
                    // for non-consumed input
                    if (recipeItemCost.stackSize == 0) continue;
                    itemCost.merge(MTItemID.createNoNBT(recipeItemCost), (long) recipeItemCost.stackSize, Long::sum);
                }
            }

            nextRecipeItemCost: for (Map.Entry<MTItemID, Long> itemID : itemCost.entrySet()) {
                ItemStack unifiedItemCost;
                if (isNBTSensitive) {
                    unifiedItemCost = GTOreDictUnificator.get_nocopy(itemID.getKey().getItemStackWithNBT());
                } else {
                    unifiedItemCost = GTOreDictUnificator.get_nocopy(itemID.getKey().getItemStack());
                }
                remainingCost = itemID.getValue() * currentParallel;
                providedAmount = 0;
                for (ItemStack providedItem : aInputs) {
                    if (isNBTSensitive && !GTUtility.areStacksEqual(providedItem, unifiedItemCost, false)) {
                        continue;
                    } else if (!isNBTSensitive
                                   && !GTOreDictUnificator.isInputStackEqual(providedItem, unifiedItemCost)) {
                        continue;
                    }

                    providedAmount += providedItem.stackSize;

                    if (providedAmount >= remainingCost) continue nextRecipeItemCost;
                }
                if (providedAmount == 0) return 0;
                currentParallel = Math.min(currentParallel, (double) providedAmount / itemID.getValue());

            }
        }

        return currentParallel;

    }




}
// spotless:on
