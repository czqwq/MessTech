package com.MessTech.common.machine.Base;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The kinds of module a {@link MTModuleMultiMachineBase} can take.
 * <p>
 * The names are working titles. A module reports every type it provides, so one that does two jobs at once - for
 * example speed and parallel in a single module - simply returns both types from {@link IMTModule#getModuleTypes()}
 * and needs no type of its own.
 */
public enum MTModuleType {

    /** EU discount module: multiplies the EU/t of the machine. */
    EU_DISCOUNT,
    /** Speed bonus module: multiplies the duration of the machine's recipes. */
    SPEED_BONUS,
    /** Parallel control module: supplies the parallel of the machine. */
    PARALLEL_CONTROL,
    /** Cross recipe parallel module: runs several recipes at the same time, the execution core idea. */
    CROSS_RECIPE_PARALLEL,
    /** Wireless module: runs the machine off the wireless EU network. */
    WIRELESS;

    /**
     * The module types every module machine takes. The cross recipe parallel and the wireless module are missing on
     * purpose: not every machine can use them, so such a module is only taken after the machine opted in by
     * overriding {@link MTModuleMultiMachineBase#getSupportedModuleTypes()}.
     */
    private static final Set<MTModuleType> DEFAULT_SUPPORTED = Collections
        .unmodifiableSet(EnumSet.of(EU_DISCOUNT, SPEED_BONUS, PARALLEL_CONTROL));

    /**
     * @return The module types every module machine takes, see the field above.
     */
    public static Set<MTModuleType> defaultSupported() {
        return DEFAULT_SUPPORTED;
    }

    /**
     * @return If true this module type has to be opted into by the machine before it is accepted.
     */
    public boolean isOptional() {
        return this == CROSS_RECIPE_PARALLEL || this == WIRELESS;
    }
}
