package com.MessTech.common.machine;

/**
 * Implemented by MessTech project modules that want to know the elevator that mounted them. GT5U's own modules only
 * have {@code connect(TileEntitySpaceElevator)}, which the copied {@link MTSpaceElevator} is not, so it calls this
 * hook for MessTech modules and the no-parent {@code connect()} for native ones.
 */
public interface IMTSpaceElevatorModule {

    /**
     * Called by {@link MTSpaceElevator} while charging its modules, once the module's motor tier requirement is met.
     *
     * @param elevator the elevator this module is mounted on
     */
    void connectToElevator(MTSpaceElevator elevator);
}
