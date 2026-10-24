package com.MessTech.common.machine.Base;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The kinds of module a {@link MTModuleMultiMachineBase} can take. A module reports every type it provides, so one
 * doing two jobs returns both from {@link IMTModule#getModuleTypes()} and needs no own type.
 */
public enum MTModuleType {

    /** EU discount module: multiplies the EU/t of the machine. */
    EU_DISCOUNT,
    /** Speed bonus module: multiplies the duration of the machine's recipes. */
    SPEED_BONUS,
    /** Parallel control module: supplies the parallel of the machine. */
    PARALLEL_CONTROL,
    /** Cross recipe parallel module: runs several recipes at the same time. */
    CROSS_RECIPE_PARALLEL,
    /** Wireless module: runs the machine off the wireless EU network. */
    WIRELESS;

    /**
     * Types every module machine takes; CROSS_RECIPE_PARALLEL and WIRELESS are absent because only machines that opt in
     * via {@link MTModuleMultiMachineBase#getSupportedModuleTypes()} take them.
     */
    private static final Set<MTModuleType> DEFAULT_SUPPORTED = Collections
        .unmodifiableSet(EnumSet.of(EU_DISCOUNT, SPEED_BONUS, PARALLEL_CONTROL));

    /**
     * @return The types every module machine takes, see the field above.
     */
    public static Set<MTModuleType> defaultSupported() {
        return DEFAULT_SUPPORTED;
    }

    /**
     * @return true if the machine has to opt into this type before it is accepted.
     */
    public boolean isOptional() {
        return this == CROSS_RECIPE_PARALLEL || this == WIRELESS;
    }
}
