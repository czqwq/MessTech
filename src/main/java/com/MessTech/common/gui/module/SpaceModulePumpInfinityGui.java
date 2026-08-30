package com.MessTech.common.gui.module;

import com.MessTech.common.machine.module.SpaceModulePumpInfinity;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;

public class SpaceModulePumpInfinityGui extends SpaceModuleInfinityGui {

    private final SpaceModulePumpInfinity pump;

    public SpaceModulePumpInfinityGui(SpaceModulePumpInfinity multiblock) {
        super(multiblock);
        this.pump = multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        for (int i = 0; i < SpaceModulePumpInfinity.PARALLEL_RECIPES; i++) {
            final int index = i;
            syncManager.syncValue(
                "planet" + index,
                new IntSyncValue(() -> pump.getPlanetType(index), v -> pump.setPlanetType(index, v)).allowC2S());
            syncManager.syncValue(
                "gas" + index,
                new IntSyncValue(() -> pump.getGasType(index), v -> pump.setGasType(index, v)).allowC2S());
            syncManager.syncValue(
                "parallel" + index,
                new IntSyncValue(() -> pump.getParallel(index), v -> pump.setParallel(index, v)).allowC2S());
        }
        syncManager.syncValue("batch", new IntSyncValue(pump::getBatchSize, pump::setBatchSize).allowC2S());
    }

    @Override
    protected Flow createLeftPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        // Pump has no standalone parallel button on the left; all recipe settings live in the
        // right-side Edit Parameters panel.
        return Flow.row()
            .fullWidth()
            .height(getTextBoxToInventoryGap());
    }

    @Override
    protected boolean shouldShowParallelField() {
        // Pump parallel is configured per recipe sub-panel, not via the standalone field.
        return false;
    }

    @Override
    protected boolean shouldShowCrossRecipeParallelField() {
        // Pump already has a fixed set of 4 recipe sub-panels; no extra cross-recipe parallel needed.
        return false;
    }

    @Override
    protected void initPanelMap(ModularPanel parent, PanelSyncManager syncManager) {
        panelMap.put(
            "editParameters",
            syncManager.syncedPanel(
                "editParameters",
                true,
                (p_syncManager, syncHandler) -> openEditParametersPanel(parent, syncManager)));
        for (int i = 0; i < SpaceModulePumpInfinity.PARALLEL_RECIPES; i++) {
            final int index = i;
            panelMap.put(
                "recipeSubPanel" + index,
                syncManager.syncedPanel(
                    "recipeSubPanel" + index,
                    true,
                    (p_syncManager, syncHandler) -> openRecipeSubPanel(parent, syncManager, index)));
        }
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        Flow row = super.createRightPanelGapRow(parent, syncManager);
        IPanelHandler editParametersPanel = panelMap.get("editParameters");
        row.child(
            new ButtonWidget<>().size(18, 18)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_CRYOTHEUM_OFF)
                .tooltip(t -> t.addLine(IKey.lang("machine.spacemodulepump.editParameters")))
                .onMousePressed(mouseButton -> {
                    if (!editParametersPanel.isPanelOpen()) {
                        editParametersPanel.openPanel();
                    } else {
                        editParametersPanel.closePanel();
                    }
                    return true;
                }));
        return row;
    }

    private ModularPanel openEditParametersPanel(ModularPanel parent, PanelSyncManager syncManager) {
        ModularPanel panel = new ModularPanel("editParameters").size(130, 180)
            .relative(parent)
            .leftRel(1)
            .topRel(0.7f);
        Flow column = Flow.column()
            .full()
            .paddingTop(4);

        IntSyncValue batchSync = syncManager.findSyncHandler("batch", IntSyncValue.class);
        column.child(
            IKey.lang("machine.spacemodulepump.batch")
                .asWidget()
                .marginBottom(2));
        column.child(
            new TextFieldWidget().formatAsInteger(true)
                .numbersInt(1, 128)
                .setTextAlignment(Alignment.CENTER)
                .defaultNumber(1)
                .value(batchSync)
                .size(100, 16));

        for (int i = 0; i < SpaceModulePumpInfinity.PARALLEL_RECIPES; i++) {
            final int index = i;
            IPanelHandler subPanel = panelMap.get("recipeSubPanel" + index);
            column.child(
                new ButtonWidget<>().size(100, 16)
                    .overlay(IKey.lang("machine.spacemodulepump.recipe", index + 1))
                    .tooltip(t -> t.addLine(IKey.lang("machine.spacemodulepump.recipe", index + 1)))
                    .onMousePressed(mouseButton -> {
                        if (!subPanel.isPanelOpen()) {
                            subPanel.openPanel();
                        } else {
                            subPanel.closePanel();
                        }
                        return true;
                    })
                    .marginTop(3));
        }

        panel.child(column);
        return panel;
    }

    private ModularPanel openRecipeSubPanel(ModularPanel parent, PanelSyncManager syncManager, int index) {
        IntSyncValue planetSync = syncManager.findSyncHandler("planet" + index, IntSyncValue.class);
        IntSyncValue gasSync = syncManager.findSyncHandler("gas" + index, IntSyncValue.class);
        IntSyncValue parallelSync = syncManager.findSyncHandler("parallel" + index, IntSyncValue.class);

        ModularPanel panel = new ModularPanel("recipeSubPanel" + index).size(130, 120)
            .relative(parent)
            .leftRel(1)
            .topRel(0.7f);
        Flow column = Flow.column()
            .full()
            .paddingTop(4);

        column.child(
            IKey.lang("machine.spacemodulepump.recipe", index + 1)
                .asWidget()
                .marginBottom(3));

        addField(column, "machine.spacemodulepump.planet", planetSync, 1, Integer.MAX_VALUE, 1);
        addField(column, "machine.spacemodulepump.gas", gasSync, 1, Integer.MAX_VALUE, 1);
        addField(column, "machine.spacemodulepump.parallel", parallelSync, 1, Integer.MAX_VALUE, 1);

        panel.child(column);
        return panel;
    }

    private void addField(Flow column, String langKey, IntSyncValue syncer, int min, int max, int def) {
        column.child(
            IKey.lang(langKey)
                .asWidget()
                .marginTop(2));
        column.child(
            new TextFieldWidget().formatAsInteger(true)
                .numbersInt(min, max)
                .setTextAlignment(Alignment.CENTER)
                .defaultNumber(def)
                .value(syncer)
                .size(100, 16));
    }
}
