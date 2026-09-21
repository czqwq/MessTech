package com.MessTech.common.gui.base;

import com.MessTech.common.machine.Base.MTModuleMultiMachineBase;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

/**
 * GUI base of {@link MTModuleMultiMachineBase} machines.
 * <p>
 * Adds the parallel field of the linked parallel control module to the terminal, next to the machine's own data: the
 * machine runs on the parallel of that module, so the machine is where it is set. The field used to live in the GUI
 * of the module hatch; only one parallel module is ever taken ({@code MTModuleMultiMachineBase#addModule}), so the
 * machine owns the single field. The row is hidden while the machine has no such module, because its ceiling stays 1.
 */
public class MTModuleMultiMachineBaseGui extends MTMultiMachineBaseGui<MTModuleMultiMachineBase<?>> {

    public MTModuleMultiMachineBaseGui(MTModuleMultiMachineBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    public void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        // The parallel and its ceiling come from the linked module, which only the server knows: the sync value
        // carries both to the client, and an edit travels back to the server setter (allowC2S).
        syncManager.syncValue(
            "moduleParallel",
            new IntSyncValue(multiblock::getParallelForGui, multiblock::setParallelForGui).allowC2S());
        syncManager.syncValue("moduleParallelCeiling", new IntSyncValue(multiblock::getParallelCeilingForGui));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        list.child(createParallelRow(syncManager));
        return list;
    }

    /**
     * The parallel row: the label of the module and the field that sets the parallel the machine may use, between 1
     * and the ceiling of the linked module. Modelled on the GUI the parallel module hatch used to have.
     *
     * @param syncManager The sync manager of the open panel.
     * @return The row, or an empty row when the sync values are missing.
     */
    protected IWidget createParallelRow(PanelSyncManager syncManager) {
        IntSyncValue parallelSyncer = syncManager.findSyncHandler("moduleParallel", IntSyncValue.class);
        IntSyncValue ceilingSyncer = syncManager.findSyncHandler("moduleParallelCeiling", IntSyncValue.class);
        if (parallelSyncer == null || ceilingSyncer == null) return Flow.row()
            .coverChildren();

        return Flow.row()
            .coverChildren()
            .marginTop(4)
            .setEnabledIf(widget -> ceilingSyncer.getIntValue() > 1)
            .child(
                IKey.lang("machine.module.parallel.label")
                    .asWidget()
                    .marginRight(4))
            .child(
                new TextFieldWidget().value(parallelSyncer)
                    .numbersInt(() -> 1L, ceilingSyncer::getValue)
                    .formatAsInteger(true)
                    .setTextAlignment(Alignment.Center)
                    .setMaxLength(20)
                    .scrollValues(1, 64, 4, 16)
                    .width(70));
    }
}
