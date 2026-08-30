package com.MessTech.common.machine.Base;

public interface ISmartInputHatch {

    /*
     * An interface to allow hatch to emit change notification for instant recipe check
     */

    void addWatcher(IHatchWatcher watcher);

    void removeWatcher(IHatchWatcher watcher);

    default boolean needsPeriodicChecks() {
        return false;
    }

}
