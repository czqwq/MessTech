package com.MessTech.common.mixin.late;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.common.wireless.gui.MTWirelessHatchTab;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import appeng.me.helpers.IGridProxyable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.common.gui.modularui.singleblock.base.MTETieredMachineBlockBaseGui;

/**
 * Attaches the 「无线网络」 tab to the GUI of every AE2 based GregTech hatch.
 *
 * <p>
 * {@code MTETieredMachineBlockBaseGui#build} is the shared {@code build} implementation of the whole GT5U single block
 * hatch GUI hierarchy ({@code MTEHatchInputBusMEGui}, {@code MTEHatchInputMEGui}, {@code MTEHatchOutputBusMEGui},
 * {@code MTEHatchOutputMEGui}, {@code MTEHatchCraftingInputMEGui}, ...) and it is inherited unchanged by this mod's
 * {@code MTInventoryInputBusMEGui}/{@code MTInventoryInputHatchMEGui}, so one RETURN injection covers all of them. GUIs
 * of machines that are not an {@link IGridProxyable} {@link MTEHatch} are returned untouched.
 *
 * <p>
 * {@code build} runs on both the client and the server (ModularUI2 builds the server side GUI first), and
 * {@link MTWirelessHatchTab#attach} only registers synced panels/sync values, so both sides stay in sync without
 * touching the server/client split.
 */
@Mixin(value = MTETieredMachineBlockBaseGui.class, remap = false)
public class MixinMTETieredMachineBlockBaseGui_WirelessTab {

    /**
     * The GUI's machine, typed as the raw {@code MTETieredMachineBlock} by erasure of the target's {@code T} type
     * parameter (the target GUI class is generic, this mixin must not be).
     */
    @Shadow(remap = false)
    @Final
    protected MTETieredMachineBlock machine;

    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private void messtech$attachWirelessTab(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings,
        CallbackInfoReturnable<ModularPanel> callbackInfo) {
        ModularPanel panel = callbackInfo.getReturnValue();
        if (panel == null) return;
        MetaTileEntity metaTileEntity = this.machine;
        if (metaTileEntity == null) return;
        if (!(metaTileEntity instanceof MTEHatch)) return;
        if (!(metaTileEntity instanceof IGridProxyable)) return;
        MTWirelessHatchTab.attach(panel, syncManager, metaTileEntity);
    }
}
