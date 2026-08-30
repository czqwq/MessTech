package com.MessTech.common.gui;

import static net.minecraft.util.StatCollector.translateToLocal;

import net.minecraft.util.EnumChatFormatting;

import com.MessTech.common.machine.MTDTPF;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * DTPF (Plasma Forge) style GUI with a wireless-mode toggle button.
 * <p>
 * Left-click toggles wireless mode; right-click opens the wireless parallel selector.
 * The parallel selector is the same pattern as {@code MTETranscendentPlasmaMixerGui}.
 */
public class MTDTPFGui extends MTEMultiBlockBaseGui<MTDTPF> {

    public MTDTPFGui(MTDTPF multiblock) {
        super(multiblock);
    }

    @Override
    protected Widget<? extends Widget<?>> makeLogoWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_LOGO).size(18)
            .marginTop(4);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("wirelessFunc", new BooleanSyncValue(multiblock::isWirelessModeAvailable));
        syncManager.syncValue(
            "wireless",
            new BooleanSyncValue(multiblock::isWirelessModeEnabled, multiblock::setEnableWireless).allowC2S());
        syncManager.syncValue(
            "wirelessParallel",
            new IntSyncValue(multiblock::getWirelessParallel, multiblock::setWirelessParallel).allowC2S());
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createButtonColumn(panel, syncManager).child(createWirelessButton(syncManager, panel));
    }

    protected IWidget createWirelessButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler parallelSelectPanel = syncManager.syncedPanel(
            "wirelessParallelPanel",
            true,
            (p_syncManager, syncHandler) -> openParallelSelectPanel(syncManager, parent));

        BooleanSyncValue wirelessFuncSyncer = syncManager.findSyncHandler("wirelessFunc", BooleanSyncValue.class);
        BooleanSyncValue wirelessSyncer = syncManager.findSyncHandler("wireless", BooleanSyncValue.class);
        return new ButtonWidget<>().marginBottom(2)
            .tooltip(
                t -> t.addLine(translateToLocal("machine.dtpf.wireless"))
                    .addLine(EnumChatFormatting.GRAY + translateToLocal("machine.dtpf.wireless.tooltip.0"))
                    .addLine(EnumChatFormatting.GRAY + translateToLocal("machine.dtpf.wireless.tooltip.1"))
                    .addLine(EnumChatFormatting.GRAY + translateToLocal("machine.dtpf.wireless.tooltip.2")))
            .overlay(new DynamicDrawable(() -> {
                if (wirelessFuncSyncer.getBoolValue() && wirelessSyncer.getBoolValue()) {
                    return GTGuiTextures.TT_SAFE_VOID_ON;
                }
                return GTGuiTextures.TT_SAFE_VOID_OFF;
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 1) { // right click: open wireless parallel selector
                    if (!parallelSelectPanel.isPanelOpen()) {
                        parallelSelectPanel.openPanel();
                    } else {
                        parallelSelectPanel.closePanel();
                    }
                } else if (mouseButton == 0 && wirelessFuncSyncer.getBoolValue()) { // left click: toggle wireless
                    wirelessSyncer.setBoolValue(!wirelessSyncer.getBoolValue());
                }
                return true;
            });
    }

    private static final int WIDTH = 120;
    private static final int HEIGHT = 50;
    private static final int PADDING_SIDES = 4;

    private ModularPanel openParallelSelectPanel(PanelSyncManager syncManager, ModularPanel parent) {
        ModularPanel returnPanel = new ModularPanel("wirelessParallelPanel").size(WIDTH, HEIGHT)
            .relative(parent)
            .leftRel(1)
            .topRel(0.8f);

        IntSyncValue parallelSyncer = syncManager.findSyncHandler("wirelessParallel", IntSyncValue.class);
        Flow holdingColumn = Flow.column()
            .full()
            .paddingTop(4);
        holdingColumn.child(
            IKey.lang("machine.dtpf.wireless.parallel")
                .asWidget()
                .marginBottom(4));
        holdingColumn.child(
            new TextFieldWidget().formatAsInteger(true)
                .numbersInt(1, Integer.MAX_VALUE)
                .setTextAlignment(Alignment.CENTER)
                .defaultNumber(1)
                .value(parallelSyncer)
                .size(WIDTH - PADDING_SIDES * 2, 18));

        returnPanel.child(holdingColumn);

        return returnPanel;
    }
}
