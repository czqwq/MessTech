package com.MessTech.common.machine.hatch;

import static net.minecraft.util.StatCollector.translateToLocal;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.MTEHatchVacuumConveyorGui;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyor;

/**
 * Same GUI as the normal NAC Vacuum Conveyor hatch, plus wireless channel controls.
 * <p>
 * One wireless config button: left-click toggles public/private, right-click opens the frequency
 * text-field panel.
 */
public class MTWirelessVacuumConveyorGui extends MTEHatchVacuumConveyorGui {

    public MTWirelessVacuumConveyorGui(MTEHatchVacuumConveyor hatch) {
        super(hatch);
        if (!(hatch instanceof IWirelessVacuumConveyor)) {
            throw new IllegalArgumentException("MTWirelessVacuumConveyorGui requires an IWirelessVacuumConveyor");
        }
    }

    private IWirelessVacuumConveyor wireless() {
        return (IWirelessVacuumConveyor) machine;
    }

    @Override
    public void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "wirelessFreq",
            new StringSyncValue(wireless()::getFrequency, wireless()::setFrequency).allowC2S());
        syncManager.syncValue(
            "wirelessPrivate",
            new BooleanSyncValue(wireless()::isPrivate, wireless()::setPrivate).allowC2S());
    }

    @Override
    protected Flow createButtonHoldingColumn(ModularPanel panel, PanelSyncManager syncManager) {
        Flow column = super.createButtonHoldingColumn(panel, syncManager);
        column.child(createWirelessConfigButton(syncManager, panel));
        return column;
    }

    protected ButtonWidget<?> createWirelessConfigButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler frequencyPanel = syncManager.syncedPanel(
            "wirelessFrequencyPanel",
            true,
            (manager, syncHandler) -> openFrequencyPanel(syncManager, parent));

        BooleanSyncValue privateSyncer = syncManager.findSyncHandler("wirelessPrivate", BooleanSyncValue.class);
        StringSyncValue frequencySyncer = syncManager.findSyncHandler("wirelessFreq", StringSyncValue.class);
        return new ButtonWidget<>().size(18, 18)
            .marginBottom(2)
            .overlay(
                new DynamicDrawable(
                    () -> privateSyncer.getBoolValue() ? GTGuiTextures.OVERLAY_BUTTON_CHECKMARK
                        : GTGuiTextures.OVERLAY_BUTTON_CROSS))
            .tooltip(t -> {
                t.addLine(translateToLocal("machine.wirelessvacuum.gui.button"));
                t.addLine(translateToLocal("machine.wirelessvacuum.gui.button.lmb"));
                t.addLine(translateToLocal("machine.wirelessvacuum.gui.button.rmb"));
                t.addLine(
                    IKey.dynamic(
                        () -> translateToLocal("machine.wirelessvacuum.gui.current") + " "
                            + translateToLocal(
                                privateSyncer.getBoolValue() ? "machine.wirelessvacuum.desc.private"
                                    : "machine.wirelessvacuum.desc.public")
                            + " / "
                            + frequencySyncer.getValue()));
            })
            .onMousePressed(mouseButton -> {
                if (mouseButton == 1) { // right-click: frequency input panel
                    if (!frequencyPanel.isPanelOpen()) {
                        frequencyPanel.openPanel();
                    } else {
                        frequencyPanel.closePanel();
                    }
                } else if (mouseButton == 0) { // left-click: toggle public/private
                    privateSyncer.setBoolValue(!privateSyncer.getBoolValue());
                }
                return true;
            });
    }

    private static final int WIDTH = 120;
    private static final int HEIGHT = 50;
    private static final int PADDING_SIDES = 4;

    protected ModularPanel openFrequencyPanel(PanelSyncManager syncManager, ModularPanel parent) {
        ModularPanel panel = new ModularPanel("wirelessFrequencyPanel").size(WIDTH, HEIGHT)
            .relative(parent)
            .leftRel(1)
            .topRel(0.8f);

        StringSyncValue frequencySyncer = syncManager.findSyncHandler("wirelessFreq", StringSyncValue.class);
        Flow column = Flow.column()
            .full()
            .padding(PADDING_SIDES);
        column.child(
            IKey.lang("machine.wirelessvacuum.gui.freq")
                .asWidget()
                .marginBottom(4));
        column.child(
            new TextFieldWidget().value(frequencySyncer)
                .setTextAlignment(Alignment.CENTER)
                .setMaxLength(32)
                .size(WIDTH - PADDING_SIDES * 2, 16));

        panel.child(column);
        return panel;
    }
}
