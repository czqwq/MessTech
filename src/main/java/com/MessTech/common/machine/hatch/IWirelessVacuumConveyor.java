package com.MessTech.common.machine.hatch;

import java.util.UUID;

/**
 * Shared wireless-channel state of the NAC wireless vacuum conveyor hatches.
 * <p>
 * Mirrors the advanced wireless redstone covers: a public channel is a bare frequency string, a private one also needs
 * the same owning player UUID.
 */
public interface IWirelessVacuumConveyor {

    String getFrequency();

    void setFrequency(String frequency);

    boolean isPrivate();

    void setPrivate(boolean isPrivate);

    UUID getOwnerUuid();

    void setOwnerUuid(UUID uuid);
}
