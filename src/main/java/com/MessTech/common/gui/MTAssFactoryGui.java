package com.MessTech.common.gui;

import com.MessTech.common.machine.MTAssFactory;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;

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
    protected Widget<? extends Widget<?>> makeLogoWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18)
            .marginTop(4);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
    }
}
