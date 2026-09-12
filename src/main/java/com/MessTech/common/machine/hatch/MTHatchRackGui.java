package com.MessTech.common.machine.hatch;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widget.Widget;

import gregtech.common.gui.modularui.hatch.MTEHatchRackGui;

/**
 * Same GUI as the GT5U computer rack, but with the MessTech logo.
 */
public class MTHatchRackGui extends MTEHatchRackGui {

    public MTHatchRackGui(MTHatchRack rack) {
        super(rack);
    }

    /** MessTech logo instead of the default GregTech logo. */
    @Override
    protected Widget<?> makeLogoWidget() {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18);
    }
}
