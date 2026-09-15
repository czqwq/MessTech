package com.MessTech.common.machine.hatch;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;

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
    protected UITexture getLogoTexture() {
        return MTGuiTextures.PICTURE_MT_LOGO;
    }
}
