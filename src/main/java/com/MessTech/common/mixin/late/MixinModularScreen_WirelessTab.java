package com.MessTech.common.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.MessTech.common.wireless.gui.MTWirelessHatchTab;
import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Opens the always visible 「无线网络」 tab panel right after a ModularUI2 screen was opened.
 *
 * <p>
 * The tab lives in its own synced sub panel that hangs outside of the machine panel (a widget outside of the panel
 * would be clipped by the panel viewport), so something has to open it once the machine GUI shows up. The tab panel
 * handler is registered on the screen's main {@code PanelSyncManager} by
 * {@link MTWirelessHatchTab#attach}, which makes this a plain hash map lookup - no per screen static registry needed
 * (a static panel map would keep the whole machine panel and widget tree alive through its values).
 *
 * <p>
 * This mixin targets {@link ModularScreen}, which is {@code @SideOnly(Side.CLIENT)}; the mixin enum only registers it
 * when the client side loads the late mixins, otherwise the dedicated server would try to load a class FML strips.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = ModularScreen.class, remap = false)
public class MixinModularScreen_WirelessTab {

    @Inject(method = "onOpen", at = @At("RETURN"), remap = false)
    private void messtech$openWirelessTab(CallbackInfo callbackInfo) {
        ModularScreen screen = (ModularScreen) (Object) this;
        IMuiScreen wrapper = screen.getScreenWrapper();
        if (wrapper == null || !wrapper.isGuiContainer()) return;
        // Client only screens have no sync manager, so there cannot be a tab panel handler either.
        if (screen.isClientOnly()) return;

        IPanelHandler tabPanel = screen.getSyncManager()
            .getMainPSM()
            .findPanelHandlerNullable(MTWirelessHatchTab.TAB_PANEL_KEY);
        if (tabPanel != null && !tabPanel.isPanelOpen()) tabPanel.openPanel();
    }
}
