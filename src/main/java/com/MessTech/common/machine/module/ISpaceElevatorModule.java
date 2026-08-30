package com.MessTech.common.machine.module;

import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;

/**
 * Marker/contract interface for MessTech space modules that should be mountable by the vanilla
 * {@link TileEntitySpaceElevator} even though they do not extend {@code TileEntityModuleBase}.
 */
public interface ISpaceElevatorModule {

    void connect(TileEntitySpaceElevator parent);

    void disconnect();

    int getNeededMotorTier();

    long increaseStoredEU(long amount);

    boolean isDataInputListEmpty();
}
