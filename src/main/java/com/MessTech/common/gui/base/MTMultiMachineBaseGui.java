package com.MessTech.common.gui.base;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Shared MessTech multiblock GUI base.
 * <p>
 * Replaces the default GregTech logo in the bottom-right corner with the MessTech logo. Space modules
 * ({@code SpaceModuleInfinityGui} and its subclasses) intentionally use their own picture and are not derived from
 * this class.
 */
public class MTMultiMachineBaseGui<T extends MTEMultiBlockBase> extends MTEMultiBlockBaseGui<T> {

    public MTMultiMachineBaseGui(T multiblock) {
        super(multiblock);
    }

    @Override
    protected Widget<? extends Widget<?>> makeLogoWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18)
            .marginTop(4);
    }
}
