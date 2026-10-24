package com.MessTech.common.machine.Base;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

public class MTProcessingLogic extends ProcessingLogic {

    public MTProcessingLogic() {
        super();
        setMaxTierSkips(999);
    }

    @NotNull
    @Override
    protected ParallelHelper createParallelHelper(@Nonnull GTRecipe recipe) {
        return new MTParallelHelper().setRecipe(recipe)
            .setItemInputs(inputItems)
            .setFluidInputs(inputFluids)
            .setAvailableEUt(availableVoltage * availableAmperage)
            .setMachine(machine, protectItems, protectFluids)
            .setRecipeLocked(recipeLockableMachine, isRecipeLocked)
            .setMaxParallel(maxParallel)
            .setEUtModifier(euModifier)
            .enableBatchMode(batchSize)
            .setConsumption(true)
            .setOutputCalculation(true);
    }

    /**
     * One batch mode (see {@code Config.machines.ENABLE_ONE_BATCH_CALCULATOR}) keeps the total EU of a normal run: the
     * {@link MTParallelHelper} computed the whole batch at its real parallel count and scaled the duration up by that
     * count, so the EU/t has to be scaled down by the same count here.
     * <p>
     * This is a copy of GT5U's {@code ProcessingLogic#applyRecipe} (5.09.54.205) with that one division added. It
     * cannot be done after {@code super.applyRecipe}: the division has to happen before {@link #onRecipeStart}, because
     * machines in wireless mode bill the whole run from {@code calculatedEut * duration} there.
     */
    @Nonnull
    @Override
    protected CheckRecipeResult applyRecipe(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
        @Nonnull OverclockCalculator calculator, @Nonnull CheckRecipeResult result) {
        if (!(helper instanceof MTParallelHelper mtHelper) || !mtHelper.isOneBatchMode()) {
            return super.applyRecipe(recipe, helper, calculator, result);
        }

        if (recipe.mCanBeBuffered) {
            lastRecipe = recipe;
        } else {
            lastRecipe = null;
        }
        calculatedParallels = helper.getCurrentParallel();

        if (calculator.getConsumption() == Long.MAX_VALUE) {
            return CheckRecipeResultRegistry.POWER_OVERFLOW;
        }
        if (calculator.getDuration() == Integer.MAX_VALUE) {
            return CheckRecipeResultRegistry.DURATION_OVERFLOW;
        }

        final int parallel = Math.max(1, calculatedParallels);
        final long totalConsumption = calculator.getConsumption();
        // Ceil division: the parallel share of the whole batch's EU/t. GT's consumption already includes the parallel
        // count, so this is exactly what one parallel of the batch costs per tick.
        calculatedEut = totalConsumption / parallel + (totalConsumption % parallel == 0 ? 0 : 1);

        double finalDuration = calculateDuration(recipe, helper, calculator);
        if (finalDuration >= Integer.MAX_VALUE) {
            return CheckRecipeResultRegistry.DURATION_OVERFLOW;
        }
        duration = (int) finalDuration;

        CheckRecipeResult hookResult = onRecipeStart(recipe);
        if (!hookResult.wasSuccessful()) {
            return hookResult;
        }

        outputItems = helper.getItemOutputs();
        outputFluids = helper.getFluidOutputs();

        return result;
    }
}
