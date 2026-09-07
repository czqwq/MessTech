package com.MessTech.common.gui.base;

import com.MessTech.common.machine.Base.TickableParallelismAcrossMultiMachineBase;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Base GUI for {@link TickableParallelismAcrossMultiMachineBase} machines.
 * <p>
 * Shows one MUI2 progress bar per thread, enabled only while that thread is active.
 */
public class TickableParallelismMultiMachineBaseGui<T extends TickableParallelismAcrossMultiMachineBase<T>>
    extends MTEMultiBlockBaseGui<T> {

    public TickableParallelismMultiMachineBaseGui(T multiblock) {
        super(multiblock);
    }

    @Override
    public void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("tickableThreadStatus", new StringSyncValue(multiblock::getThreadStatusText));

        int count = multiblock.getMaxThreadCount();
        for (int i = 0; i < count; i++) {
            final int index = i;
            syncManager.syncValue("threadName" + i, new StringSyncValue(() -> multiblock.getThreadName(index)));
            syncManager.syncValue("threadActive" + i, new BooleanSyncValue(() -> multiblock.isThreadActive(index)));
            syncManager.syncValue("threadProgress" + i, new DoubleSyncValue(() -> multiblock.getThreadProgress(index)));
        }
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        list.child(createThreadStatusWidget(syncManager));
        return list;
    }

    protected IWidget createThreadStatusWidget(PanelSyncManager syncManager) {
        Flow column = Flow.column()
            .coverChildren()
            .marginTop(2);

        int count = multiblock.getMaxThreadCount();
        for (int i = 0; i < count; i++) {
            StringSyncValue nameSync = syncManager.findSyncHandler("threadName" + i, StringSyncValue.class);
            BooleanSyncValue activeSync = syncManager.findSyncHandler("threadActive" + i, BooleanSyncValue.class);
            DoubleSyncValue progressSync = syncManager.findSyncHandler("threadProgress" + i, DoubleSyncValue.class);
            if (nameSync == null || activeSync == null || progressSync == null) continue;

            column.child(createThreadProgressRow(nameSync, activeSync, progressSync));
        }
        return column;
    }

    protected IWidget createThreadProgressRow(StringSyncValue nameSync, BooleanSyncValue activeSync,
        DoubleSyncValue progressSync) {
        return Flow.row()
            .coverChildren()
            .collapseDisabledChild()
            .child(
                new TextWidget<>(IKey.dynamic(nameSync::getStringValue)).width(90)
                    .height(10))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> activeSync.getBoolValue() ? ""
                            : net.minecraft.util.StatCollector.translateToLocal("GT5U.waila.machine.idle"))).width(70)
                                .height(10)
                                .setEnabledIf(w -> !activeSync.getBoolValue()))
            .child(
                new ProgressWidget().value(progressSync)
                    .texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 40)
                    .size(40, 10)
                    .marginLeft(4)
                    .setEnabledIf(w -> activeSync.getBoolValue()));
    }
}
