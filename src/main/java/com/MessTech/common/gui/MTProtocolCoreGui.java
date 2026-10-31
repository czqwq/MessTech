package com.MessTech.common.gui;

import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import com.MessTech.common.gui.base.MTMultiMachineBaseGui;
import com.MessTech.common.machine.MTProtocolCore;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;

/**
 * The 协议核心 / Protocol Core machine GUI: the normal MessTech multiblock screen plus a read-only block showing where
 * the machine currently sends the wireless EU.
 * <p>
 * There are no buttons of its own - the machine has no program to configure any more. The lines show the running state,
 * the placer's GTNHLib team, the found target energy hatches, the placer's wireless network balance, the current output
 * and the total already delivered; the values are pushed from the server through {@link PanelSyncManager} sync values.
 * GT's own terminal lines (structure errors, recipe result, ...) are appended below the block.
 */
public class MTProtocolCoreGui extends MTMultiMachineBaseGui<MTProtocolCore> {

    // region Sync keys
    private static final String SYNC_TEAM = "protocolTeam";
    private static final String SYNC_OWNER = "protocolOwner";
    private static final String SYNC_TARGETS = "protocolTargets";
    private static final String SYNC_BALANCE = "protocolBalance";
    private static final String SYNC_OUTPUT = "protocolOutput";
    private static final String SYNC_TOTAL = "protocolTotal";
    // endregion

    public MTProtocolCoreGui(MTProtocolCore multiblock) {
        super(multiblock);
    }

    // region Sync values
    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(SYNC_TEAM, new StringSyncValue(multiblock::getTeamNameForGui));
        syncManager.syncValue(SYNC_OWNER, new StringSyncValue(multiblock::getOwnerNameForGui));
        syncManager.syncValue(SYNC_TARGETS, new StringSyncValue(multiblock::getTargetCountText));
        syncManager.syncValue(SYNC_BALANCE, new StringSyncValue(multiblock::getNetworkBalanceText));
        syncManager.syncValue(SYNC_OUTPUT, new StringSyncValue(multiblock::getOutputRateText));
        syncManager.syncValue(SYNC_TOTAL, new StringSyncValue(multiblock::getTotalDeliveredText));
    }
    // endregion

    // region Terminal lines
    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        BooleanSyncValue active = syncManager.findSyncHandler("machineActive", BooleanSyncValue.class);
        StringSyncValue team = syncManager.findSyncHandler(SYNC_TEAM, StringSyncValue.class);
        StringSyncValue owner = syncManager.findSyncHandler(SYNC_OWNER, StringSyncValue.class);
        StringSyncValue targets = syncManager.findSyncHandler(SYNC_TARGETS, StringSyncValue.class);
        StringSyncValue balance = syncManager.findSyncHandler(SYNC_BALANCE, StringSyncValue.class);
        StringSyncValue output = syncManager.findSyncHandler(SYNC_OUTPUT, StringSyncValue.class);
        StringSyncValue total = syncManager.findSyncHandler(SYNC_TOTAL, StringSyncValue.class);

        ListWidget<IWidget, ?> info = new ListWidget<>().fullWidth()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                IKey.dynamic(
                    () -> translateToLocal(
                        active.getBoolValue() ? "machine.protocolcore.gui.status.running"
                            : "machine.protocolcore.gui.status.idle"))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2))
            .child(
                IKey.dynamic(
                    () -> translateToLocalFormatted(
                        "machine.protocolcore.gui.team",
                        team.getStringValue()
                            .isEmpty() ? translateToLocal("machine.protocolcore.gui.team.none")
                                : team.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2))
            .child(
                IKey.dynamic(
                    () -> translateToLocalFormatted(
                        "machine.protocolcore.gui.owner",
                        owner.getStringValue()
                            .isEmpty() ? translateToLocal("machine.protocolcore.gui.unknown") : owner.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2))
            .child(
                IKey.dynamic(
                    () -> translateToLocalFormatted("machine.protocolcore.gui.targets", targets.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2))
            .child(
                IKey.dynamic(
                    () -> translateToLocalFormatted("machine.protocolcore.gui.balance", balance.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2))
            .child(
                IKey.dynamic(
                    () -> translateToLocalFormatted("machine.protocolcore.gui.output", output.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2))
            .child(
                IKey.dynamic(() -> translateToLocalFormatted("machine.protocolcore.gui.total", total.getStringValue()))
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2));

        // GT's own terminal lines (structure errors, recipe result, ...) come after the wireless status block.
        return info.children(super.createTerminalTextWidget(syncManager, parent).getChildren());
    }
    // endregion
}
