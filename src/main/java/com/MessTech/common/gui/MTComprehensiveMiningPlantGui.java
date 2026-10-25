package com.MessTech.common.gui;

import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.function.Supplier;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.gui.base.MTMultiMachineBaseGui;
import com.MessTech.common.machine.MTComprehensiveMiningPlant;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Alignment.MainAxis;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * Controller GUI for the Comprehensive Mining Plant. The default terminal text widget is replaced on purpose - the
 * machine has no recipe map and no maintenance, so all it has to show is its own status, the wireless EU draw and
 * buffer, the fluid extraction rates and the ore speed; GT's structure error list is attached at the end so a missing
 * output bus/hatch is visible.
 * <p>
 * Everything the sync values carry is raw and locale free (numbers, {@code fluidRegistry|mB/s} lines); the formatting
 * and the language lookup happen in the widget suppliers, i.e. client side. The server side only produces the values
 * ({@code PanelSyncManager#detectAndSendChanges} runs there), which is why no translated text may come from the
 * machine.
 * <p>
 * The button column drops the controller slot (nothing to put there) and otherwise mirrors the base one: power switch,
 * structure update button and the screwdriver button that opens the parallel settings sub panel instead; those settings
 * apply on the next start of the machine, as in the source mod.
 */
public class MTComprehensiveMiningPlantGui extends MTMultiMachineBaseGui<MTComprehensiveMiningPlant> {

    private static final int PARALLEL_PANEL_WIDTH = 180;
    private static final int PARALLEL_PANEL_HEIGHT = 96;
    private static final int PARALLEL_ROW_WIDTH = 172;
    private static final int PARALLEL_ROW_HEIGHT = 20;
    private static final int PARALLEL_FIELD_WIDTH = 70;

    public MTComprehensiveMiningPlantGui(MTComprehensiveMiningPlant multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("cmpRunning", new BooleanSyncValue(multiblock::isWorkEnabled));
        syncManager.syncValue("cmpEUt", new LongSyncValue(multiblock::getPerTickEUt));
        syncManager.syncValue("cmpEUtSec", new LongSyncValue(multiblock::getPerSecondEUt));
        syncManager.syncValue("cmpPct", new LongSyncValue(multiblock::getEnergyPercent));
        syncManager.syncValue("cmpOreSpeed", new LongSyncValue(multiblock::getOrePerSecond));
        syncManager.syncValue("cmpFluidCount", new IntSyncValue(multiblock::getFluidTypeCount));
        syncManager.syncValue("cmpEmptyDim", new BooleanSyncValue(multiblock::isDimensionEmpty));
        // One raw "fluidRegistry|mB/s" line per visible row, localized client side.
        for (int i = 0; i < MTComprehensiveMiningPlant.FLUID_LINE_COUNT; i++) {
            final int index = i;
            syncManager.syncValue("cmpFluidLine" + i, new StringSyncValue(() -> multiblock.getFluidRateRaw(index)));
        }
        syncManager.syncValue(
            "cmpOre",
            new IntSyncValue(multiblock::getConfiguredOre, multiblock::setConfiguredOre).allowC2S());
        syncManager.syncValue(
            "cmpFluid",
            new IntSyncValue(multiblock::getConfiguredFluid, multiblock::setConfiguredFluid).allowC2S());
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        BooleanSyncValue running = syncManager.findSyncHandler("cmpRunning", BooleanSyncValue.class);
        LongSyncValue perTick = syncManager.findSyncHandler("cmpEUt", LongSyncValue.class);
        LongSyncValue perSecond = syncManager.findSyncHandler("cmpEUtSec", LongSyncValue.class);
        LongSyncValue percent = syncManager.findSyncHandler("cmpPct", LongSyncValue.class);
        LongSyncValue oreSpeed = syncManager.findSyncHandler("cmpOreSpeed", LongSyncValue.class);
        IntSyncValue fluidCount = syncManager.findSyncHandler("cmpFluidCount", IntSyncValue.class);
        BooleanSyncValue emptyDimension = syncManager.findSyncHandler("cmpEmptyDim", BooleanSyncValue.class);

        ListWidget<IWidget, ?> list = new ListWidget<>().fullWidth()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .collapseDisabledChild(true)
            .child(
                line(
                    () -> translateToLocalFormatted(
                        "machine.comprehensiveminingplant.gui.status",
                        translateToLocal(
                            running.getBoolValue() ? "machine.comprehensiveminingplant.status.running"
                                : "machine.comprehensiveminingplant.status.idle"))))
            .child(line(() -> translateToLocal("machine.comprehensiveminingplant.gui.power")));

        list.child(
            line(() -> translateToLocalFormatted("machine.comprehensiveminingplant.gui.eut", perTick.getValue())));
        list.child(
            line(
                () -> translateToLocalFormatted(
                    "machine.comprehensiveminingplant.gui.eutSecond",
                    perSecond.getValue(),
                    percent.getValue())));
        list.child(
            IKey.dynamic(() -> translateToLocal("machine.comprehensiveminingplant.gui.fluidHeader"))
                .asWidget()
                .fullWidth()
                .marginBottom(2)
                .setEnabledIf(w -> fluidCount.getIntValue() > 0));
        for (int i = 0; i < MTComprehensiveMiningPlant.FLUID_LINE_COUNT; i++) {
            final int index = i;
            StringSyncValue fluidLine = syncManager.findSyncHandler("cmpFluidLine" + i, StringSyncValue.class);
            list.child(
                IKey.dynamic(() -> fluidLineText(fluidLine.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2)
                    .setEnabledIf(w -> fluidCount.getIntValue() > index));
        }
        list.child(
            line(
                () -> translateToLocalFormatted("machine.comprehensiveminingplant.gui.oreSpeed", oreSpeed.getValue())));
        list.child(
            IKey.dynamic(() -> translateToLocal("machine.comprehensiveminingplant.gui.empty"))
                .asWidget()
                .fullWidth()
                .marginBottom(2)
                .setEnabledIf(w -> emptyDimension.getBoolValue()));
        // The terminal is replaced wholesale, so GT's own error list has to be attached here - otherwise a missing
        // output bus/hatch (checked in the machine's checkMachine) would never be visible in the GUI.
        list.child(createStructureErrorWidget(syncManager));
        return list;
    }

    /**
     * The localizable form of one raw fluid line, {@code fluidRegistry|mB/s}. Runs client side (inside a widget
     * supplier), so the fluid's display name is the client's.
     */
    private static String fluidLineText(String raw) {
        if (raw == null) return "";
        int separator = raw.indexOf('|');
        if (separator < 0) return "";
        long rate;
        try {
            rate = Long.parseLong(raw.substring(separator + 1));
        } catch (NumberFormatException e) {
            return "";
        }
        String registryName = raw.substring(0, separator);
        Fluid fluid = FluidRegistry.getFluid(registryName);
        String name = fluid == null ? registryName : new FluidStack(fluid, 1).getLocalizedName();
        return translateToLocalFormatted("machine.comprehensiveminingplant.gui.fluidLine", name, rate / 1000L);
    }

    /** A terminal text line: full width, small gap below, text supplied per frame. */
    private static IWidget line(Supplier<String> text) {
        return IKey.dynamic(text)
            .asWidget()
            .fullWidth()
            .marginBottom(2);
    }

    /**
     * The machine has no item inventory (no controller slot to tool), so the default column's controller slot is left
     * out; the power switch, the structure update button (the structure is a real 11×10×10 tower with construct
     * support)
     * and the parallel settings button make up the column.
     */
    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.column()
            .width(18)
            .leftRel(1, -2, 1)
            .mainAxisAlignment(MainAxis.END)
            .reverseLayout(true)
            .child(createPowerSwitchButton())
            .child(createParallelButton(syncManager, panel))
            .child(createStructureUpdateButton(syncManager));
    }

    private ButtonWidget<?> createParallelButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler parallelPanel = syncManager.syncedPanel(
            "cmpParallelPanel",
            true,
            (panelSyncManager, syncHandler) -> createParallelPanel(syncManager, parent));
        return new ButtonWidget<>().size(18, 18)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_SCREWDRIVER)
            .tooltip(t -> t.addLine(IKey.lang("machine.comprehensiveminingplant.gui.parallel.title")))
            .onMousePressed(mouseButton -> {
                if (!parallelPanel.isPanelOpen()) {
                    parallelPanel.openPanel();
                } else {
                    parallelPanel.closePanel();
                }
                return true;
            });
    }

    private ModularPanel createParallelPanel(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue ore = syncManager.findSyncHandler("cmpOre", IntSyncValue.class);
        IntSyncValue fluid = syncManager.findSyncHandler("cmpFluid", IntSyncValue.class);

        return new ModularPanel("cmpParallelPanel").relative(parent)
            .rightRel(1)
            .bottom(40)
            .size(PARALLEL_PANEL_WIDTH, PARALLEL_PANEL_HEIGHT)
            .child(
                Flow.column()
                    .full()
                    .padding(4)
                    .child(
                        IKey.lang("machine.comprehensiveminingplant.gui.parallel.title")
                            .asWidget()
                            .marginBottom(4))
                    .child(
                        Flow.row()
                            .size(PARALLEL_ROW_WIDTH, PARALLEL_ROW_HEIGHT)
                            .child(
                                IKey.lang("machine.comprehensiveminingplant.gui.parallel.ore")
                                    .asWidget()
                                    .marginRight(4))
                            .child(
                                new TextFieldWidget().formatAsInteger(true)
                                    .numbersInt(1, MTComprehensiveMiningPlant.MAX_PARALLEL)
                                    .setTextAlignment(Alignment.CENTER)
                                    .defaultNumber(1)
                                    .value(ore)
                                    .size(PARALLEL_FIELD_WIDTH, 18)))
                    .child(
                        Flow.row()
                            .size(PARALLEL_ROW_WIDTH, PARALLEL_ROW_HEIGHT)
                            .child(
                                IKey.lang("machine.comprehensiveminingplant.gui.parallel.fluid")
                                    .asWidget()
                                    .marginRight(4))
                            .child(
                                new TextFieldWidget().formatAsInteger(true)
                                    .numbersInt(0, MTComprehensiveMiningPlant.MAX_PARALLEL)
                                    .setTextAlignment(Alignment.CENTER)
                                    .defaultNumber(0)
                                    .value(fluid)
                                    .size(PARALLEL_FIELD_WIDTH, 18)))
                    .child(
                        IKey.lang("machine.comprehensiveminingplant.gui.parallel.hint")
                            .asWidget()
                            .marginTop(4)));
    }

    // The machine has no recipes, so the recipe-shaped buttons of the base GUI are meaningless.
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
}
