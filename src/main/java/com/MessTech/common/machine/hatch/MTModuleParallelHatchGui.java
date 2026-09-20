package com.MessTech.common.machine.hatch;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.GregTechAPI;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/**
 * GUI of {@link MTModuleParallelHatch}: one text field that sets the parallel the machine may use, between 1 and the
 * ceiling of the module tier.
 * <p>
 * Modelled on GT5U's {@code MTEHatchWirelessMultiGui}.
 */
public class MTModuleParallelHatchGui extends MTEHatchBaseGui<MTModuleParallelHatch> {

    public MTModuleParallelHatchGui(MTModuleParallelHatch hatch) {
        super(hatch);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createContentSection(panel, syncManager).child(
            Flow.column()
                .coverChildren()
                .center()
                .childPadding(4)
                .child(
                    IKey.lang("machine.module.parallel.label")
                        .asWidget())
                .child(
                    new TextFieldWidget().value(new IntSyncValue(machine::getParallel, this::setParallel).allowC2S())
                        .numbersInt(1, machine.getMaxParallel())
                        .formatAsInteger(true)
                        .setTextAlignment(Alignment.Center)
                        .setMaxLength(20)
                        .scrollValues(1, 64, 4, 16)
                        .width(70)));
    }

    /** Push the requested parallel into the hatch; the machine re-reads its parallel on the next structure check. */
    private void setParallel(int value) {
        machine.setParallelFromGui(value);
        if (baseMetaTileEntity != null && baseMetaTileEntity.isServerSide()) {
            GregTechAPI.causeMachineUpdate(
                baseMetaTileEntity.getWorld(),
                baseMetaTileEntity.getXCoord(),
                baseMetaTileEntity.getYCoord(),
                baseMetaTileEntity.getZCoord());
        }
    }

    /** MessTech logo instead of the default GregTech logo. */
    @Override
    protected Widget<?> makeLogoWidget() {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18);
    }
}
