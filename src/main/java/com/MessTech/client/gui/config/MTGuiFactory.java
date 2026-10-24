package com.MessTech.client.gui.config;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.SimpleGuiFactory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Named by {@code @Mod(guiFactory = ...)}: the Config button in the Mods menu opens {@link MTGuiClientConfig}, which
 * lists the categories of {@link com.MessTech.init.Config}.
 */
@SideOnly(Side.CLIENT)
public class MTGuiFactory implements SimpleGuiFactory {

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return MTGuiClientConfig.class;
    }
}
