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
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;

/**
 * Controller GUI for the nuclear reactor.
 * <p>
 * The default terminal text widget is replaced on purpose: only the reactor status numbers are shown, without the
 * recipe progress bar and without the machine work progress. The "stable" line is the result of the next-cycle heat
 * simulation implemented in {@link MTReactor}.
 */
public class MTReactorGui extends MTMultiMachineBaseGui<MTReactor> {

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
            .child(stableLine(stable))
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
            // Without this the terminal only held the generic "incomplete structure" line, so a missing access or
            // heat control hatch was never listed. It is the same widget the GT base GUI adds to its own terminal.
            .child(createStructureErrorWidget(syncManager));
    }

    @Override
    protected UITexture getTextureForReason(String key) {
        return switch (key) {
            case "reactor_component_missing", "reactor_component_low" -> GTGuiTextures.OVERLAY_STRUCTURE_INCOMPLETE;
            default -> super.getTextureForReason(key);
        };
    }

    @Override
    protected String getToolTipForReason(String key) {
        return switch (key) {
            case "reactor_component_missing" -> EnumChatFormatting.RED
                + StatCollector.translateToLocal("GT5U.gui.text.shutdown_reason.reactor_component_missing");
            case "reactor_component_low" -> EnumChatFormatting.RED
                + StatCollector.translateToLocal("GT5U.gui.text.shutdown_reason.reactor_component_low");
            default -> super.getToolTipForReason(key);
        };
    }

    /**
     * The reactor's locked-component stop does not disable the machine (it resumes automatically once the auto
     * input/output refills the slot), so the default {@code !isAllowedToWork} gate must be relaxed for our reasons.
     */
    @Override
    protected boolean shouldShutdownReasonBeDisplayed(String shutdownString) {
        if ("reactor_component_missing".equals(shutdownString) || "reactor_component_low".equals(shutdownString)) {
            return GTUtility.isStringValid(shutdownString);
        }
        return super.shouldShutdownReasonBeDisplayed(shutdownString);
    }

    // A reactor has no recipes, so the default void/input-separation/batch/recipe-lock buttons are meaningless.
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

    private static IWidget stableLine(BooleanSyncValue stable) {
        return IKey.dynamic(() -> {
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
}
