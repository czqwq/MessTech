package com.MessTech.common.machine.Base;

import org.jetbrains.annotations.NotNull;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Base class for wireless machines that support **cross-recipe parallelism**.
 * <p>
 * In normal (wired) mode the machine behaves like a regular multi-block. In wireless mode the
 * machine runs {@link #cycleNum} independent recipe cycles per check; each cycle may pick a
 * different recipe from the recipe map (that is the "cross-recipe" part) and its outputs are
 * accumulated before the machine starts working.
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
        // Wireless cross-recipe parallelism: run multiple recipe cycles and merge their outputs.
        if (isEnableWireless()) {
            return checkProcessingWirelessLoop();
        }
        return super.checkProcessing();
    }

    /**
     * Fallback parallel cap. Subclasses should override with their own mode/tier logic.
     */
    @Override
    public int getMaxParallelRecipes() {
        // In cross-recipe wireless mode the meaningful "parallelism" comes from the number of
        // recipe cycles, not from parallel copies of a single recipe.
        return 1;
    }

    /**
     * Subclasses should set the number of cross-recipe cycles used in wireless mode.
     */
    public void setWirelessCycleNum(int cycleNum) {
        this.cycleNum = Math.max(1, cycleNum);
    }

    @Override
    protected void prepareProcessing() {
        super.prepareProcessing();
    }

    // The actual wireless loop, cost tracking, Waila display, and wireless recipe validation are
    // inherited from MTWirelessMultiMachineBase.
}
