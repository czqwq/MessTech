package com.MessTech.common.gui;

import com.MessTech.common.machine.MTAssFactory;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * MUI2 GUI for {@link MTAssFactory}.
 * <p>
 * The standard multi-block GUI already provides the progress/energy widgets and, because
 * {@code MTAssFactory} supports machine-mode switching, the base class also creates the
 * screwdriver/mode-switch button automatically.
 */
public class MTAssFactoryGui extends MTEMultiBlockBaseGui<MTAssFactory> {

    public MTAssFactoryGui(MTAssFactory multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
    }
}
