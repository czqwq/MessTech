package com.MessTech.common.machine.Base;

import java.util.Set;

/**
 * One module of a {@link MTModuleMultiMachineBase}: whatever sits in its module slot (normally a hatch) and reports
 * what it provides. The machine folds the module values into its own EU, speed and parallel values. A module's tier is
 * the GT tier index, IV ({@link #MIN_TIER}) .. MAX ({@link #MAX_TIER}).
 */
public interface IMTModule {

    /** Lowest module tier, the GT tier index of IV. */
    int MIN_TIER = 5;
    /** Highest module tier, the GT tier index of MAX. */
    int MAX_TIER = 14;

    /** @return Every type this module provides; a composite module returns more than one. */
    Set<MTModuleType> getModuleTypes();

    /** @return The tier, IV ({@link #MIN_TIER}) .. MAX ({@link #MAX_TIER}). */
    int getModuleTier();

    /** @return If true this module provides that type. */
    default boolean provides(MTModuleType type) {
        return getModuleTypes().contains(type);
    }

    /** @return If true the GT tier index is a valid module tier, IV .. MAX. */
    static boolean isValidTier(int tier) {
        return tier >= MIN_TIER && tier <= MAX_TIER;
    }

    /**
     * Called when this module is linked to a machine, so a hatch can pick up the casing texture of its structure.
     *
     * @param aBaseCasingIndex The casing texture index of the structure.
     */
    default void onLinkedToMachine(int aBaseCasingIndex) {}

    // region Effects. The defaults change nothing, the concrete module fills the numbers in.

    /** @return EU/t multiplier of the machine, below 1 is a discount. */
    default float getEuModifier() {
        return 1.0F;
    }

    /** @return Duration multiplier of the machine's recipes, below 1 is faster. */
    default float getSpeedBonus() {
        return 1.0F;
    }

    /**
     * The parallel this module supplies. A parallel control module replaces the machine's parallel instead of adding to
     * it, hence the large numbers (64 at IV .. 16,777,216 at MAX, see {@link MTModuleValues#maxParallel}).
     *
     * @return The parallel of this module, 0 when it supplies none.
     */
    default int getParallel() {
        return 0;
    }

    /**
     * @return The highest parallel this module may supply, at least 1: the GUI parallel ceiling for
     *         {@link MTModuleType#PARALLEL_CONTROL}.
     */
    default int getMaxParallel() {
        return 1;
    }

    /**
     * Sets the parallel this module supplies, clamped to 1..{@link #getMaxParallel()}; the default does nothing.
     *
     * @param value The requested parallel.
     */
    default void setParallelFromGui(int value) {}

    /**
     * @return Recipes this module may run at once, only read for {@link MTModuleType#CROSS_RECIPE_PARALLEL}.
     */
    default int getCycleNum() {
        return 1;
    }

    /**
     * @return If true this module can run the machine off the wireless EU network, only read for
     *         {@link MTModuleType#WIRELESS}.
     */
    default boolean providesWireless() {
        return false;
    }

    // endregion
}
