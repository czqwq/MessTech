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
}
