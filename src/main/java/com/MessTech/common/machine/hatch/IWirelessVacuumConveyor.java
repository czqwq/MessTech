package com.MessTech.common.machine.hatch;

import java.util.UUID;

/**
 * Shared wireless-channel state for wireless NAC vacuum conveyor hatches.
 * <p>
 * The API mirrors the advanced wireless redstone covers: a public channel is a bare frequency string,
 * while a private channel additionally requires the same owning player UUID.
 */
public interface IWirelessVacuumConveyor {

    String getFrequency();

    void setFrequency(String frequency);

    boolean isPrivate();

    void setPrivate(boolean isPrivate);

    UUID getOwnerUuid();

    void setOwnerUuid(UUID uuid);
}
