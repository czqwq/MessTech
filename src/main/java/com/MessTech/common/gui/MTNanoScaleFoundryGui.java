package com.MessTech.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.MessTech.common.gui.base.TickableParallelismMultiMachineBaseGui;
import com.MessTech.common.machine.MTNanoScaleFoundry;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.gtnewhorizons.modularui.common.fluid.FluidStackTank;

import gregtech.api.modularui2.GTWidgetThemes;

/**
 * Nano-Scale Foundry GUI: uses the shared tickable multi-thread GUI base and shows the Board
 * Processor immersion tank.
 */
public class MTNanoScaleFoundryGui extends TickableParallelismMultiMachineBaseGui<MTNanoScaleFoundry> {

    private final FluidSlotSyncHandler[] boardTankSyncHandlers = new FluidSlotSyncHandler[5];

    public MTNanoScaleFoundryGui(MTNanoScaleFoundry multiblock) {
        super(multiblock);
    }

    @Override
    public void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);

        for (int type = 1; type <= multiblock.getBoardTankTypeCount(); type++) {
            final int tankType = type;
            FluidStackTank tank = new FluidStackTank(
                () -> multiblock.getBoardTankStoredFluid(tankType),
                ignored -> {},
                multiblock::getBoardTankCapacity);
            FluidSlotSyncHandler handler = new FluidSlotSyncHandler(tank).canFillSlot(false)
                .controlsAmount(false);
            syncManager.syncValue("boardTank" + tankType, handler);
            boardTankSyncHandlers[tankType] = handler;
        }

        int count = multiblock.getMaxThreadCount();
        for (int i = 0; i < count; i++) {
            final int index = i;
            syncManager
                .syncValue("threadProgressTime" + i, new IntSyncValue(() -> multiblock.getThreadProgressTime(index)));
            syncManager.syncValue(
                "threadMaxProgressTime" + i,
                new IntSyncValue(() -> multiblock.getThreadMaxProgressTime(index)));
            syncManager.syncValue("threadParallel" + i, new IntSyncValue(() -> multiblock.getThreadParallel(index)));
            syncManager.syncValue("threadEUt" + i, new LongSyncValue(() -> multiblock.getThreadEUt(index)));
            syncManager.syncValue("threadOutput" + i, new StringSyncValue(() -> multiblock.getThreadOutputText(index)));
        }
    }

    @Override
    protected IWidget createThreadStatusWidget(PanelSyncManager syncManager) {
        Flow column = Flow.column()
            .coverChildren()
            .marginTop(2);

        int count = multiblock.getThreadCount();
        for (int i = 0; i < count; i++) {
            StringSyncValue nameSync = syncManager.findSyncHandler("threadName" + i, StringSyncValue.class);
            BooleanSyncValue activeSync = syncManager.findSyncHandler("threadActive" + i, BooleanSyncValue.class);
            IntSyncValue progressTimeSync = syncManager.findSyncHandler("threadProgressTime" + i, IntSyncValue.class);
            IntSyncValue maxProgressTimeSync = syncManager
                .findSyncHandler("threadMaxProgressTime" + i, IntSyncValue.class);
            IntSyncValue parallelSync = syncManager.findSyncHandler("threadParallel" + i, IntSyncValue.class);
            LongSyncValue eutSync = syncManager.findSyncHandler("threadEUt" + i, LongSyncValue.class);
            StringSyncValue outputSync = syncManager.findSyncHandler("threadOutput" + i, StringSyncValue.class);
            if (nameSync == null || activeSync == null
                || progressTimeSync == null
                || maxProgressTimeSync == null
                || parallelSync == null
                || eutSync == null
                || outputSync == null) {
                continue;
            }
            column.child(
                createThreadStatusBlock(
                    i + 1,
                    nameSync,
                    activeSync,
                    progressTimeSync,
                    maxProgressTimeSync,
                    parallelSync,
                    eutSync,
                    outputSync));
        }

        return column;
    }

    @Override
    protected ParentWidget<?> createTerminalParentWidget(ModularPanel panel, PanelSyncManager syncManager) {
        // Keep the original GT terminal text layout on the left, and put the four Board immersion
        // tanks to the right (outside the text ListWidget so they are not clipped).
        return Flow.row()
            .size(getTerminalWidgetWidth(), getTerminalWidgetHeight())
            .paddingTop(4)
            .paddingBottom(4)
            .paddingLeft(4)
            .paddingRight(0)
            .childPadding(2)
            .widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL)
            .child(
                createTerminalTextWidget(syncManager, panel)
                    .size(getTerminalWidgetWidth() - 48, getTerminalWidgetHeight() - 8)
                    .collapseDisabledChild())
            .child(createBoardTankWidget());
    }

    private IWidget createThreadStatusBlock(int displayIndex, StringSyncValue nameSync, BooleanSyncValue activeSync,
        IntSyncValue progressTimeSync, IntSyncValue maxProgressTimeSync, IntSyncValue parallelSync,
        LongSyncValue eutSync, StringSyncValue outputSync) {
        return Flow.column()
            .coverChildren()
            .child(new TextWidget<>(IKey.dynamic(() -> {
                String name = MTNanoScaleFoundry.getLocalizedThreadName(nameSync.getStringValue());
                if (!activeSync.getBoolValue()) {
                    return EnumChatFormatting.GRAY + "#"
                        + displayIndex
                        + " "
                        + name
                        + ": "
                        + EnumChatFormatting.DARK_GRAY
                        + StatCollector.translateToLocal("GT5U.waila.machine.idle")
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.AQUA + "#"
                    + displayIndex
                    + " "
                    + name
                    + EnumChatFormatting.RESET
                    + EnumChatFormatting.GRAY
                    + " | "
                    + EnumChatFormatting.WHITE
                    + parallelSync.getValue()
                    + StatCollector.translateToLocal("machine.nanoscale.unit.parallel")
                    + " "
                    + EnumChatFormatting.RED
                    + eutSync.getValue()
                    + StatCollector.translateToLocal("machine.nanoscale.unit.eut");
            })).height(10)
                .scale(0.7f))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                if (!activeSync.getBoolValue()) return "";
                double current = progressTimeSync.getValue() / 20.0;
                double max = maxProgressTimeSync.getValue() / 20.0;
                double percent = max > 0 ? current / max * 100.0 : 0;
                return StatCollector.translateToLocalFormatted(
                    "GT5U.gui.text.progress_recipe",
                    String.format("%.2f", current),
                    String.format("%.2f", max),
                    String.format("%.0f", percent));
            })).height(10)
                .scale(0.7f)
                .setEnabledIf(w -> activeSync.getBoolValue()))
            .child(
                new TextWidget<>(IKey.dynamic(outputSync::getStringValue)).height(10)
                    .scale(0.7f)
                    .setEnabledIf(
                        w -> activeSync.getBoolValue() && !outputSync.getStringValue()
                            .isEmpty()));
    }

    private IWidget createBoardTankWidget() {
        Flow grid = Flow.column()
            .coverChildren()
            .marginLeft(4);
        for (int row = 0; row < 2; row++) {
            Flow line = Flow.row()
                .coverChildren();
            for (int col = 0; col < 2; col++) {
                int type = row * 2 + col + 1;
                FluidSlotSyncHandler handler = boardTankSyncHandlers[type];
                if (handler == null) continue;
                FluidSlot fluidSlot = new FluidSlot().syncHandler(handler)
                    .alwaysShowFull(false)
                    .size(18, 36)
                    .background(IDrawable.EMPTY)
                    .tooltipBuilder(t -> {
                        t.clearText();
                        net.minecraftforge.fluids.FluidStack fluid = handler.getValue();
                        if (fluid != null) {
                            t.addLine(EnumChatFormatting.AQUA + fluid.getLocalizedName() + EnumChatFormatting.RESET);
                            t.addLine(
                                StatCollector.translateToLocal("GT5U.tooltip.nac.module.boardprocessor.immersion_fluid")
                                    + " "
                                    + type
                                    + ": "
                                    + fluid.amount
                                    + " / "
                                    + multiblock.getBoardTankCapacity()
                                    + " L");
                        } else {
                            t.addLine(
                                EnumChatFormatting.GRAY
                                    + StatCollector.translateToLocal("GT5U.tooltip.nac.module.boardprocessor.empty"));
                        }
                        t.addLine(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("GT5U.tooltip.nac.module.boardprocessor.impurity")
                                + ": "
                                + String.format("%.1f%%", multiblock.getBoardTankImpurityPercentage(type) * 100));
                    });
                line.child(fluidSlot);
            }
            grid.child(line);
        }
        return grid;
    }
}
