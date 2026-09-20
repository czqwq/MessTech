package com.MessTech.common.machine.Base;

import java.util.Set;

/**
 * One module of a {@link MTModuleMultiMachineBase}.
 * <p>
 * A module is whatever the machine puts in its module slot - normally a hatch - and it reports what it provides.
 * The machine collects the modules and folds their values into its own EU, speed and parallel values, so the
 * numbers live in the concrete module and the machine only aggregates them.
 * <p>
 * The tier of a module is the GT tier index, IV ({@link #MIN_TIER}) up to MAX ({@link #MAX_TIER}).
 */
public interface IMTModule {

    /** Lowest module tier, the GT tier index of IV. */
    int MIN_TIER = 5;
    /** Highest module tier, the GT tier index of MAX. */
    int MAX_TIER = 14;

    /**
     * @return Every module type this module provides. A composite module returns more than one.
     */
    Set<MTModuleType> getModuleTypes();

    /**
     * @return The tier of this module, IV ({@link #MIN_TIER}) .. MAX ({@link #MAX_TIER}).
     */
    int getModuleTier();

    /**
     * @param type The module type to test.
     * @return If true this module provides that type.
     */
    default boolean provides(MTModuleType type) {
        return getModuleTypes().contains(type);
    }

    /**
     * @param tier The GT tier index to test.
     * @return If true the given tier is a valid module tier, IV .. MAX.
     */
    static boolean isValidTier(int tier) {
        return tier >= MIN_TIER && tier <= MAX_TIER;
    }

    /**
     * Called when this module is linked to a machine, so a hatch module can pick up the casing texture of the
     * structure it was built into. The default does nothing.
     *
     * @param aBaseCasingIndex The casing texture index of the structure.
     */
    default void onLinkedToMachine(int aBaseCasingIndex) {}

    // region Effects. The defaults change nothing, the concrete module fills the numbers in.

    /**
     * @return Multiplier for the EU/t of the machine, below 1 is a discount.
     */
    default float getEuModifier() {
        return 1.0F;
    }

    /**
     * @return Multiplier for the duration of the machine's recipes, below 1 is faster.
     */
    default float getSpeedBonus() {
        return 1.0F;
    }

    /**
     * The parallel this module supplies to the machine.
     * <p>
     * A parallel control module does not add to the parallel of the machine, it replaces it: a machine that has one
     * runs on the parallel the module reports. That is why the numbers are large (64 at IV up to 16,777,216 at MAX,
     * see {@link MTModuleValues#maxParallel}) and why only one parallel control module is allowed per machine.
     *
     * @return The parallel of this module, 0 when it supplies none.
     */
    default int getParallel() {
        return 0;
    }

    /**
     * @return How many recipes this module may run at the same time. Only read by a machine that supports
     *         {@link MTModuleType#CROSS_RECIPE_PARALLEL}.
     */
    default int getCycleNum() {
        return 1;
    }

    /**
     * @return If true this module can run the machine off the wireless EU network. Only read by a machine that
     *         supports {@link MTModuleType#WIRELESS}.
     */
    default boolean providesWireless() {
        return false;
    }

    // endregion
}
