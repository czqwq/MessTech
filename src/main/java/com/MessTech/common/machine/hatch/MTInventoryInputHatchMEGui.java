package com.MessTech.common.machine.hatch;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;

import gregtech.common.gui.modularui.hatch.MTEHatchInputMEGui;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Same GUI as the GT5U stocking ME input hatch, but with the MessTech logo.
 */
public class MTInventoryInputHatchMEGui extends MTEHatchInputMEGui {

    public MTInventoryInputHatchMEGui(MTInventoryInputHatchME hatch, MTEHatchInputME.Slot[] slots) {
        super(hatch, slots);
    }

    /** MessTech logo instead of the default GregTech logo. */
    @Override
    protected UITexture getLogoTexture() {
        return MTGuiTextures.PICTURE_MT_LOGO;
    }
}
