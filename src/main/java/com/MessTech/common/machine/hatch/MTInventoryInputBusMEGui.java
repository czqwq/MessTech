package com.MessTech.common.machine.hatch;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widget.Widget;

import gregtech.common.gui.modularui.hatch.MTEHatchInputBusMEGui;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/**
 * Same GUI as the GT5U stocking ME input bus, but with the MessTech logo.
 */
public class MTInventoryInputBusMEGui extends MTEHatchInputBusMEGui {

    public MTInventoryInputBusMEGui(MTInventoryInputBusME hatch, MTEHatchInputBusME.Slot[] slots) {
        super(hatch, slots);
    }

    /** MessTech logo instead of the default GregTech logo. */
    @Override
    protected Widget<?> makeLogoWidget() {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18);
    }
}
