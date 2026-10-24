package com.MessTech.common.machine.Base;

import org.jetbrains.annotations.NotNull;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Base class for wireless machines with **cross-recipe parallelism**: in wireless mode the machine runs
 * {@link #cycleNum} independent recipe cycles per check, each picking its own recipe from the recipe map, and
 * accumulates their outputs before it starts working; wired mode is a regular multi-block.
 */
public abstract class ParallelismAcrossMultiMachineBase<T extends ParallelismAcrossMultiMachineBase<T>>
    extends MTWirelessMultiMachineBase<T> {

    public ParallelismAcrossMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public ParallelismAcrossMultiMachineBase(String aName) {
        super(aName);
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        // Wireless: run the recipe cycles and merge their outputs.
        if (isEnableWireless()) {
            return checkProcessingWirelessLoop();
        }
        return super.checkProcessing();
    }

    /** Fallback parallel cap; override with the machine's own mode/tier logic. */
    @Override
    public int getMaxParallelRecipes() {
        // In cross-recipe wireless mode the "parallelism" is the number of recipe cycles, not copies of one recipe.
        return 1;
    }

    /** Sets the number of cross-recipe cycles used in wireless mode. */
    public void setWirelessCycleNum(int cycleNum) {
        this.cycleNum = Math.max(1, cycleNum);
    }

    @Override
    protected void prepareProcessing() {
        super.prepareProcessing();
    }

    // The wireless loop, cost tracking, Waila display and recipe validation come from
    // MTWirelessMultiMachineBase.
}
