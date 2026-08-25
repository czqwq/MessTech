package com.MessTech.common.gui;

import static net.minecraft.util.StatCollector.translateToLocal;

import net.minecraft.util.StatCollector;

import com.MessTech.common.machine.MTComputingCenter;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * MUI2 GUI for {@link MTComputingCenter}.
 * <p>
 * Nano Computing mode shows wireless + overclock controls; Research mode only shows terminal text.
 */
public class MTComputingCenterGui extends MTEMultiBlockBaseGui<MTComputingCenter> {

    public MTComputingCenterGui(MTComputingCenter multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "wireless",
            new BooleanSyncValue(multiblock::isWirelessModeEnabled, multiblock::setWirelessModeEnabled).allowC2S());
        syncManager.syncValue("mode", new IntSyncValue(multiblock::getMachineMode));
        syncManager.syncValue("compRemaining", new LongSyncValue(() -> multiblock.getComputationRemaining()));
        syncManager.syncValue("compRequired", new LongSyncValue(() -> multiblock.getComputationRequired()));
        syncManager.syncValue("availableData", new LongSyncValue(() -> multiblock.getAvailableData()));
        syncManager.syncValue("eut", new IntSyncValue(multiblock::getCurrentEUt));
        syncManager.syncValue(
            "overclockEnabled",
            new BooleanSyncValue(multiblock::isOverclockEnabled, multiblock::setOverclockEnabled).allowC2S());
        syncManager.syncValue(
            "overclockRatio",
            new IntSyncValue(multiblock::getOverclockRatio, multiblock::setOverclockRatio).allowC2S());
        syncManager.syncValue(
            "overvoltageRatio",
            new IntSyncValue(multiblock::getOvervoltageRatio, multiblock::setOvervoltageRatio).allowC2S());
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue modeSync = syncManager.findSyncHandler("mode", IntSyncValue.class);
        LongSyncValue compRemainingSync = syncManager.findSyncHandler("compRemaining", LongSyncValue.class);
        LongSyncValue compRequiredSync = syncManager.findSyncHandler("compRequired", LongSyncValue.class);
        LongSyncValue availableDataSync = syncManager.findSyncHandler("availableData", LongSyncValue.class);
        IntSyncValue eutSync = syncManager.findSyncHandler("eut", IntSyncValue.class);
        BooleanSyncValue wirelessSync = syncManager.findSyncHandler("wireless", BooleanSyncValue.class);
        BooleanSyncValue overclockSync = syncManager.findSyncHandler("overclockEnabled", BooleanSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> modeSync.getIntValue() == 0
                            ? StatCollector.translateToLocalFormatted(
                                "machine.computingcenter.gui.output",
                                availableDataSync.getLongValue())
                            : StatCollector.translateToLocalFormatted(
                                "machine.computingcenter.gui.progress",
                                compRequiredSync.getLongValue() - compRemainingSync.getLongValue(),
                                compRequiredSync.getLongValue()))))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> StatCollector
                            .translateToLocalFormatted("machine.computingcenter.gui.eut", eutSync.getIntValue()))))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> modeSync.getIntValue() == 0 && overclockSync.getBoolValue()
                            ? translateToLocal("machine.computingcenter.gui.overclocked")
                            : "")))
            .child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> modeSync.getIntValue() == 0 && wirelessSync.getBoolValue()
                            ? translateToLocal("machine.computingcenter.gui.wireless")
                            : "")));
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        IntSyncValue modeSync = syncManager.findSyncHandler("mode", IntSyncValue.class);
        Flow column = super.createButtonColumn(panel, syncManager);
        if (modeSync.getIntValue() == 0) {
            column.child(createWirelessButton(syncManager));
        }
        return column;
    }

    @Override
    protected Flow createLeftPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        IntSyncValue modeSync = syncManager.findSyncHandler("mode", IntSyncValue.class);
        Flow row = super.createLeftPanelGapRow(parent, syncManager);
        if (modeSync.getIntValue() == 0) {
            row.child(createOverclockButton(syncManager, parent));
        }
        return row;
    }

    protected IWidget createWirelessButton(PanelSyncManager syncManager) {
        BooleanSyncValue wirelessSyncer = syncManager.findSyncHandler("wireless", BooleanSyncValue.class);
        IntSyncValue modeSync = syncManager.findSyncHandler("mode", IntSyncValue.class);
        return new ButtonWidget<>().size(18, 18)
            .playClickSound(true)
            .setEnabledIf(w -> modeSync.getIntValue() == 0)
            .tooltip(t -> t.addLine(translateToLocal("machine.computingcenter.wireless")))
            .overlay(new DynamicDrawable(() -> {
                if (modeSync.getIntValue() != 0) return null;
                if (wirelessSyncer.getBoolValue()) {
                    return GTGuiTextures.OVERLAY_BUTTON_WIRELESS_ON;
                }
                return GTGuiTextures.OVERLAY_BUTTON_WIRELESS_OFF;
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0) {
                    wirelessSyncer.setBoolValue(!wirelessSyncer.getBoolValue());
                }
                return true;
            });
    }

    protected IWidget createOverclockButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler overclockPanel = syncManager.syncedPanel(
            "overclockPanel",
            true,
            (p_syncManager, syncHandler) -> openOverclockPanel(syncManager, parent));

        BooleanSyncValue overclockSyncer = syncManager.findSyncHandler("overclockEnabled", BooleanSyncValue.class);
        IntSyncValue modeSync = syncManager.findSyncHandler("mode", IntSyncValue.class);
        return new ButtonWidget<>().size(18, 18)
            .playClickSound(true)
            .setEnabledIf(w -> modeSync.getIntValue() == 0)
            .tooltip(t -> t.addLine(translateToLocal("machine.computingcenter.overclock")))
            .overlay(new DynamicDrawable(() -> {
                if (modeSync.getIntValue() != 0) return null;
                return overclockSyncer.getBoolValue() ? GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_ON
                    : GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_OFF;
            }))
            .onMousePressed(mouseButton -> {
                if (!overclockPanel.isPanelOpen()) {
                    overclockPanel.openPanel();
                } else {
                    overclockPanel.closePanel();
                }
                return true;
            });
    }

    private static final int WIDTH = 100;
    private static final int HEIGHT = 90;
    private static final int PADDING_SIDES = 4;

    private ModularPanel openOverclockPanel(PanelSyncManager syncManager, ModularPanel parent) {
        ModularPanel panel = new ModularPanel("overclockPanel").size(WIDTH, HEIGHT)
            .relative(parent)
            .leftRel(1)
            .topRel(0.7f);

        BooleanSyncValue enableSync = syncManager.findSyncHandler("overclockEnabled", BooleanSyncValue.class);
        IntSyncValue ocSync = syncManager.findSyncHandler("overclockRatio", IntSyncValue.class);
        IntSyncValue ovSync = syncManager.findSyncHandler("overvoltageRatio", IntSyncValue.class);

        Flow column = Flow.column()
            .full()
            .paddingTop(4);

        column.child(
            new ButtonWidget<>().size(18, 18)
                .overlay(
                    new DynamicDrawable(
                        () -> enableSync.getBoolValue() ? GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_ON
                            : GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_OFF))
                .onMousePressed(mouseButton -> {
                    enableSync.setBoolValue(!enableSync.getBoolValue());
                    return true;
                }));

        column.child(
            IKey.lang("machine.computingcenter.overclock.ratio")
                .asWidget()
                .marginTop(3));
        column.child(
            new TextFieldWidget().formatAsInteger(true)
                .numbersInt(1, 100)
                .setTextAlignment(Alignment.CENTER)
                .defaultNumber(1)
                .value(ocSync)
                .size(WIDTH - PADDING_SIDES * 2, 16));

        column.child(
            IKey.lang("machine.computingcenter.overclock.overvoltage")
                .asWidget()
                .marginTop(3));
        column.child(
            new TextFieldWidget().formatAsInteger(true)
                .numbersInt(1, 100)
                .setTextAlignment(Alignment.CENTER)
                .defaultNumber(1)
                .value(ovSync)
                .size(WIDTH - PADDING_SIDES * 2, 16));

        panel.child(column);
        return panel;
    }
}
