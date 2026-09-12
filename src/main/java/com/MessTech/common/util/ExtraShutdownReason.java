package com.MessTech.common.util;

import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.api.util.shutdown.SimpleShutDownReason;
import lombok.NonNull;

public class ExtraShutdownReason extends ShutDownReasonRegistry {

    @NonNull
    public static final ShutDownReason MISS_DATASTICK = SimpleShutDownReason.ofCritical("datastick");
    @NonNull
    public static final ShutDownReason OverHeating = SimpleShutDownReason.ofCritical("overheating");
    /** A locked reactor slot has no component (or the wrong one). */
    @NonNull
    public static final ShutDownReason MISS_REACTOR_COMPONENT = SimpleShutDownReason
        .ofCritical("reactor_component_missing");
    /** A locked reactor component is at/below its output threshold or would be destroyed next cycle. */
    @NonNull
    public static final ShutDownReason REACTOR_COMPONENT_LOW = SimpleShutDownReason.ofCritical("reactor_component_low");
}
