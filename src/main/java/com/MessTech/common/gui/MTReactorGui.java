package com.MessTech.common.gui;

import java.util.function.Supplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.MessTech.common.gui.base.MTMultiMachineBaseGui;
import com.MessTech.common.machine.MTReactor;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongArraySyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;

/**
 * Controller GUI for the nuclear reactor. The default terminal text widget is replaced on purpose: only reactor status
 * numbers, without the recipe progress bar and the machine work progress - core heat and its ceiling, the three-state
 * meltdown report ({@code stable} / {@code N cycles} / countdown running, plus a red countdown alarm), the rated EU/t,
 * fluid (wet) mode with its passive cooling, and the per-page heat map.
 */
public class MTReactorGui extends MTMultiMachineBaseGui<MTReactor> {

    /** Longest page list the heat map line shows before it collapses into a "+N more" tail. */
    private static final int HEATMAP_MAX_ENTRIES = 12;

    public MTReactorGui(MTReactor multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("mtReactorHatches", new IntSyncValue(multiblock::getReactorAccessHatchCountInt));
        syncManager.syncValue("mtReactorComponents", new IntSyncValue(multiblock::getReactorComponentCount));
        syncManager.syncValue("mtReactorHeat", new IntSyncValue(multiblock::getHottestPageHeat));
        syncManager.syncValue("mtReactorMaxHeat", new IntSyncValue(multiblock::getHottestPageMaxHeat));
        syncManager.syncValue("mtReactorHeatPercent", new IntSyncValue(multiblock::getHottestPageHeatPercent));
        syncManager.syncValue("mtReactorStable", new BooleanSyncValue(multiblock::isReactorStable));
        syncManager.syncValue(
            "mtReactorAutoMatch",
            new BooleanSyncValue(multiblock::isAutoMatchTarget, multiblock::setAutoMatchTarget).allowC2S());
        syncManager.syncValue("mtReactorMissingLocked", new BooleanSyncValue(multiblock::hasMissingLockedComponents));
        syncManager.syncValue("mtReactorLowLocked", new BooleanSyncValue(multiblock::hasLowOrDoomedLockedComponents));
        syncManager.syncValue("mtReactorMeltdown", new BooleanSyncValue(multiblock::isMeltdownCountdownActive));
        syncManager.syncValue("mtReactorMeltdownSeconds", new IntSyncValue(multiblock::getMeltdownCountdownSeconds));
        syncManager.syncValue("mtReactorCycles", new IntSyncValue(multiblock::getCyclesToMeltdown));
        syncManager.syncValue("mtReactorRatedOutput", new LongSyncValue(multiblock::getRatedEUPerTick));
        syncManager.syncValue("mtReactorFluidMode", new BooleanSyncValue(multiblock::isFluidMode));
        syncManager.syncValue("mtReactorCooling", new LongSyncValue(multiblock::getPassiveCoolingPerSecond));
        syncManager.syncValue("mtReactorPendingHeat", new IntSyncValue(multiblock::getPendingCoolantHeat));
        // MUI2 has no int[] sync value, so the per-page heat map travels as long[] (see pageHeatPercents).
        syncManager.syncValue("mtReactorPageHeat", new LongArraySyncValue(this::pageHeatPercents, null));
    }

    /**
     * The controller's per-page heat percentages as the {@code long[]} {@link LongArraySyncValue} needs, in the
     * controller's order: hatch by hatch, page by page (`MTReactor#getPageHeatPercents`).
     */
    private long[] pageHeatPercents() {
        int[] percents = multiblock.getPageHeatPercents();
        if (percents == null) return new long[0];
        long[] result = new long[percents.length];
        for (int i = 0; i < percents.length; i++) {
            result[i] = percents[i];
        }
        return result;
    }

    @Override
    protected Flow createLeftPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        BooleanSyncValue autoMatch = syncManager.findSyncHandler("mtReactorAutoMatch", BooleanSyncValue.class);
        return super.createLeftPanelGapRow(parent, syncManager).child(
            new ButtonWidget<>().size(18, 18)
                .overlay(
                    new DynamicDrawable(
                        () -> autoMatch.getBoolValue() ? GTGuiTextures.OVERLAY_BUTTON_CHECKMARK
                            : GTGuiTextures.OVERLAY_BUTTON_CROSS))
                .tooltip(
                    t -> t.addLine(IKey.lang("machine.mtreactor.gui.auto_match"))
                        .addLine(
                            IKey.dynamic(
                                () -> (autoMatch.getBoolValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                                    + StatCollector.translateToLocal(
                                        autoMatch.getBoolValue() ? "machine.mtreactor.gui.auto_match.on"
                                            : "machine.mtreactor.gui.auto_match.off")))
                        .addLine(IKey.lang("machine.mtreactor.gui.auto_match.tooltip")))
                .onMousePressed(mouseButton -> {
                    if (mouseButton == 0) {
                        autoMatch.setBoolValue(!autoMatch.getBoolValue());
                    }
                    return true;
                }));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue hatches = syncManager.findSyncHandler("mtReactorHatches", IntSyncValue.class);
        IntSyncValue components = syncManager.findSyncHandler("mtReactorComponents", IntSyncValue.class);
        IntSyncValue heat = syncManager.findSyncHandler("mtReactorHeat", IntSyncValue.class);
        IntSyncValue maxHeat = syncManager.findSyncHandler("mtReactorMaxHeat", IntSyncValue.class);
        IntSyncValue heatPercent = syncManager.findSyncHandler("mtReactorHeatPercent", IntSyncValue.class);
        BooleanSyncValue stable = syncManager.findSyncHandler("mtReactorStable", BooleanSyncValue.class);
        BooleanSyncValue missingLocked = syncManager.findSyncHandler("mtReactorMissingLocked", BooleanSyncValue.class);
        BooleanSyncValue lowLocked = syncManager.findSyncHandler("mtReactorLowLocked", BooleanSyncValue.class);
        BooleanSyncValue meltdown = syncManager.findSyncHandler("mtReactorMeltdown", BooleanSyncValue.class);
        IntSyncValue meltdownSeconds = syncManager.findSyncHandler("mtReactorMeltdownSeconds", IntSyncValue.class);
        IntSyncValue cycles = syncManager.findSyncHandler("mtReactorCycles", IntSyncValue.class);
        LongSyncValue ratedOutput = syncManager.findSyncHandler("mtReactorRatedOutput", LongSyncValue.class);
        BooleanSyncValue fluidMode = syncManager.findSyncHandler("mtReactorFluidMode", BooleanSyncValue.class);
        LongSyncValue cooling = syncManager.findSyncHandler("mtReactorCooling", LongSyncValue.class);
        IntSyncValue pendingHeat = syncManager.findSyncHandler("mtReactorPendingHeat", IntSyncValue.class);
        LongArraySyncValue pageHeat = syncManager.findSyncHandler("mtReactorPageHeat", LongArraySyncValue.class);

        return new ListWidget<>().fullWidth()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                line(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.access_hatches", hatches.getValue())))
            .child(
                line(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.components", components.getValue())))
            .child(
                line(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.heat", heat.getValue(), maxHeat.getValue())))
            .child(
                line(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.heat_percent", heatPercent.getValue())))
            // Only shown while a page is counting down: the terminal stays short in the normal case, and the line reads
            // as an alarm rather than as decoration.
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD
                        + StatCollector
                            .translateToLocalFormatted("machine.mtreactor.gui.meltdown", meltdownSeconds.getIntValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2)
                    .setEnabledIf(w -> meltdown.getBoolValue()))
            .child(stableLine(stable, cycles, meltdown))
            .child(
                line(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.output_rated", ratedOutput.getLongValue())))
            .child(
                line(
                    () -> StatCollector.translateToLocalFormatted(
                        "machine.mtreactor.gui.fluidmode",
                        StatCollector.translateToLocal(
                            fluidMode.getBoolValue() ? "machine.mtreactor.gui.fluidmode.on"
                                : "machine.mtreactor.gui.fluidmode.off"))))
            .child(
                line(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.cooling", cooling.getLongValue())))
            // Fluid mode only: the component heat the coolant loop has not carried away yet, and whatever of it the
            // loop does not take ends up on the core.
            .child(
                IKey.dynamic(
                    () -> StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.pending_heat", pendingHeat.getIntValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2)
                    .setEnabledIf(w -> fluidMode.getBoolValue()))
            .child(line(() -> heatMapText(pageHeat)))
            .child(IKey.dynamic(() -> {
                if (missingLocked.getBoolValue()) {
                    return EnumChatFormatting.RED
                        + StatCollector.translateToLocal("machine.mtreactor.structure.error.missing_locked_component");
                }
                if (lowLocked.getBoolValue()) {
                    return EnumChatFormatting.RED
                        + StatCollector.translateToLocal("machine.mtreactor.gui.component_low");
                }
                return "";
            })
                .asWidget()
                .fullWidth()
                .marginBottom(2)
                .setEnabledIf(w -> missingLocked.getBoolValue() || lowLocked.getBoolValue()))
            // Without this the terminal only showed the generic "incomplete structure" line, so a missing access or
            // heat control hatch was never listed; it is the widget the GT base GUI adds to its own terminal.
            .child(createStructureErrorWidget(syncManager));
    }

    @Override
    protected UITexture getTextureForReason(String key) {
        return switch (key) {
            case "reactor_component_missing", "reactor_component_low", "reactor_coolant_missing", "reactor_too_many_pages" -> GTGuiTextures.OVERLAY_STRUCTURE_INCOMPLETE;
            default -> super.getTextureForReason(key);
        };
    }

    /**
     * Every reactor shutdown reason is a {@code SimpleShutDownReason.ofCritical(id)} whose text lives in the GT5U
     * namespace under that id ({@code ExtraShutdownReason}); MessTech defines those keys in its own lang files.
     */
    @Override
    protected String getToolTipForReason(String key) {
        return switch (key) {
            case "reactor_component_missing", "reactor_component_low", "reactor_coolant_missing", "reactor_too_many_pages" -> EnumChatFormatting.RED
                + StatCollector.translateToLocal("GT5U.gui.text.shutdown_reason." + key);
            default -> super.getToolTipForReason(key);
        };
    }

    /**
     * The reactor's locked-component and coolant stops do not disable the machine (they resume by themselves once the
     * situation is fixed), so the default {@code !isAllowedToWork} gate is relaxed for our reasons.
     */
    @Override
    protected boolean shouldShutdownReasonBeDisplayed(String shutdownString) {
        if ("reactor_component_missing".equals(shutdownString) || "reactor_component_low".equals(shutdownString)
            || "reactor_coolant_missing".equals(shutdownString)
            || "reactor_too_many_pages".equals(shutdownString)) {
            return GTUtility.isStringValid(shutdownString);
        }
        return super.shouldShutdownReasonBeDisplayed(shutdownString);
    }

    // A reactor has no recipes, so the default void/separation/batch/recipe-lock buttons are meaningless.
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

    private static IWidget line(Supplier<String> text) {
        return IKey.dynamic(text)
            .asWidget()
            .fullWidth()
            .marginBottom(2);
    }

    private static IWidget stableLine(BooleanSyncValue stable, IntSyncValue cycles, BooleanSyncValue meltdown) {
        return IKey.dynamic(() -> {
            if (meltdown.getBoolValue()) {
                return EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD
                    + StatCollector.translateToLocal("machine.mtreactor.gui.stable.meltdown");
            }
            if (cycles.getIntValue() >= 0) {
                return EnumChatFormatting.RED + StatCollector
                    .translateToLocalFormatted("machine.mtreactor.gui.stable.cycles", cycles.getIntValue());
            }
            boolean isStable = stable.getBoolValue();
            return (isStable ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + StatCollector.translateToLocalFormatted(
                    "machine.mtreactor.gui.stable",
                    StatCollector.translateToLocal(
                        isStable ? "machine.mtreactor.gui.stable.yes" : "machine.mtreactor.gui.stable.no"));
        })
            .asWidget()
            .fullWidth()
            .marginBottom(2)
            .tooltip(t -> t.addLine(IKey.lang("machine.mtreactor.gui.stable.tooltip")));
    }

    /**
     * The per-page heat map as one line. The terminal is a flat list of text lines, so the pages are joined with the
     * {@code gui.heatmap.entry} pattern and capped at {@link #HEATMAP_MAX_ENTRIES} entries plus a {@code +N} tail - a
     * 25 hatch structure would otherwise put 200 entries into one widget. Order is the controller's
     * ({@code MTReactor#getPageHeatPercents}: hatch by hatch, page by page).
     */
    private static String heatMapText(LongArraySyncValue pageHeat) {
        long[] percents = pageHeat == null ? null : pageHeat.getValue();
        if (percents == null || percents.length == 0) {
            return StatCollector.translateToLocal("machine.mtreactor.gui.heatmap.empty");
        }
        StringBuilder builder = new StringBuilder();
        int shown = Math.min(percents.length, HEATMAP_MAX_ENTRIES);
        for (int i = 0; i < shown; i++) {
            if (i > 0) builder.append(' ');
            builder.append(
                StatCollector.translateToLocalFormatted("machine.mtreactor.gui.heatmap.entry", i + 1, percents[i]));
        }
        if (percents.length > shown) {
            builder.append(' ')
                .append(
                    StatCollector
                        .translateToLocalFormatted("machine.mtreactor.gui.heatmap.more", percents.length - shown));
        }
        return StatCollector.translateToLocalFormatted("machine.mtreactor.gui.heatmap", builder.toString());
    }
}
