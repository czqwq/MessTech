package com.MessTech.common.gui.module;

import static net.minecraft.util.StatCollector.translateToLocal;

import com.MessTech.common.gui.MTGuiTextures;
import com.MessTech.common.machine.Base.ParallelismAcrossMultiMachineBase;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Shared MUI2 GUI for the infinite space modules: shows a wireless parallel selector.
 */
public class SpaceModuleInfinityGui extends MTEMultiBlockBaseGui<ParallelismAcrossMultiMachineBase<?>> {

    public SpaceModuleInfinityGui(ParallelismAcrossMultiMachineBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected Widget<? extends Widget<?>> makeLogoWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return new IDrawable.DrawableWidget(MTGuiTextures.PICTURE_MT_SPACE).size(18)
            .marginTop(4);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "parallel",
            new IntSyncValue(multiblock::getWirelessParallel, multiblock::setWirelessParallel).allowC2S());
        if (multiblock instanceof com.MessTech.common.machine.module.SpaceModuleInfinityBase<?>base) {
            syncManager.syncValue(
                "crossParallel",
                new IntSyncValue(base::getCrossRecipeParallel, base::setCrossRecipeParallel).allowC2S());
        }
    }

    @Override
    protected Flow createLeftPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        IntSyncValue parallelSyncer = syncManager.findSyncHandler("parallel", IntSyncValue.class);
        IPanelHandler parallelPanel = syncManager
            .syncedPanel("parallelPanel", true, (p_syncManager, syncHandler) -> openParallelPanel(syncManager, parent));

        Flow row = super.createLeftPanelGapRow(parent, syncManager);
        row.child(
            new ButtonWidget<>().size(18, 18)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_OFF)
                .tooltip(t -> t.addLine(translateToLocal("machine.spacemodule.parallel")))
                .onMousePressed(mouseButton -> {
                    if (!parallelPanel.isPanelOpen()) {
                        parallelPanel.openPanel();
                    } else {
                        parallelPanel.closePanel();
                    }
                    return true;
                }));
        addExtraPanelButtons(row, syncManager, parent);
        return row;
    }

    private ModularPanel openParallelPanel(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue parallelSyncer = syncManager.findSyncHandler("parallel", IntSyncValue.class);
        IntSyncValue crossParallelSyncer = syncManager.findSyncHandler("crossParallel", IntSyncValue.class);
        int height = 50;
        if (shouldShowParallelField()) height += 40;
        if (shouldShowCrossRecipeParallelField()) height += 40;
        ModularPanel panel = new ModularPanel("parallelPanel").size(90, height)
            .relative(parent)
            .leftRel(1)
            .topRel(0.7f);
        Flow column = Flow.column()
            .full()
            .paddingTop(4);
        if (shouldShowParallelField()) {
            column.child(
                IKey.lang("machine.spacemodule.parallel")
                    .asWidget()
                    .marginBottom(3));
            column.child(
                new TextFieldWidget().formatAsInteger(true)
                    .numbersInt(1, Integer.MAX_VALUE)
                    .setTextAlignment(Alignment.CENTER)
                    .defaultNumber(1)
                    .value(parallelSyncer)
                    .size(80, 16));
        }
        if (shouldShowCrossRecipeParallelField()) {
            column.child(
                IKey.lang("machine.spacemodule.crossparallel")
                    .asWidget()
                    .marginTop(3)
                    .marginBottom(2));
            column.child(
                new TextFieldWidget().formatAsInteger(true)
                    .numbersInt(1, 64)
                    .setTextAlignment(Alignment.CENTER)
                    .defaultNumber(1)
                    .value(crossParallelSyncer)
                    .size(80, 16));
        }
        panel.child(column);
        return panel;
    }

    /** Whether the independent wireless parallel field should be shown (Pump uses per-recipe parallel). */
    protected boolean shouldShowParallelField() {
        return true;
    }

    /** Whether the cross-recipe parallel field should be shown (Pump already has fixed 4 recipes). */
    protected boolean shouldShowCrossRecipeParallelField() {
        return true;
    }

    /** Hook for subclasses to add separate configuration buttons/panels. */
    protected void addExtraPanelButtons(Flow row, PanelSyncManager syncManager, ModularPanel parent) {}

    // Original space modules don't show the default void/input-separation/batch/recipe-lock buttons.
    @Override
    protected boolean shouldDisplayVoidExcess() {
        return false;
    }

    @Override
    protected boolean shouldDisplayInputSeparation() {
        return false;
    }

    @Override
    protected boolean shouldDisplayBatchMode() {
        return false;
    }

    @Override
    protected boolean shouldDisplayRecipeLock() {
        return false;
    }

    /**
     * Adds a separate small panel with a single integer text field.
     *
     * @return the created panel handler, in case the caller wants to keep a reference.
     */
    protected IPanelHandler addIntPanelButton(Flow row, PanelSyncManager syncManager, ModularPanel parent,
        String panelName, String langKey, IntSyncValue syncer, int min, int max, int defaultValue) {
        IPanelHandler panel = syncManager.syncedPanel(panelName, true, (p_syncManager, syncHandler) -> {
            ModularPanel p = new ModularPanel(panelName).size(90, 50)
                .relative(parent)
                .leftRel(1)
                .topRel(0.7f);
            Flow column = Flow.column()
                .full()
                .paddingTop(4);
            column.child(
                IKey.lang(langKey)
                    .asWidget()
                    .marginBottom(3));
            column.child(
                new TextFieldWidget().formatAsInteger(true)
                    .numbersInt(min, max)
                    .setTextAlignment(Alignment.CENTER)
                    .defaultNumber(defaultValue)
                    .value(syncer)
                    .size(80, 16));
            p.child(column);
            return p;
        });
        row.child(
            new ButtonWidget<>().size(18, 18)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_OFF)
                .tooltip(t -> t.addLine(IKey.lang(langKey)))
                .onMousePressed(mouseButton -> {
                    if (!panel.isPanelOpen()) {
                        panel.openPanel();
                    } else {
                        panel.closePanel();
                    }
                    return true;
                }));
        return panel;
    }
}
