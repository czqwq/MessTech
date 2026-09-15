package com.MessTech.common.machine.hatch;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.api.drawable.IDrawable;

import gregtech.common.gui.modularui.hatch.MTEHatchRackGui;

/**
 * Same GUI as the GT5U computer rack, but with the MessTech logo.
 */
public class MTHatchRackGui extends MTEHatchRackGui {

    public MTHatchRackGui(MTHatchRack rack) {
        super(rack);
    }

    /** MessTech logo instead of the TecTech logo GT5U draws on the rack in this GTNH line. */
    @Override
    protected IDrawable.DrawableWidget createLogo() {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18);
    }
}
