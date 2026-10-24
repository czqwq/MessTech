package com.MessTech.client.gui.config;

import net.minecraft.client.gui.GuiScreen;

import com.MessTech.init.Config;
import com.MessTech.init.MessTech;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The mod's config screen, opened from the Mods menu. {@code categorized} makes the first screen the category list
 * (general / machines / voidstarbow / gameconsole); titles and comments come from the GTNHLib annotations and the
 * generated lang keys, see {@link Config}.
 */
@SideOnly(Side.CLIENT)
public class MTGuiClientConfig extends SimpleGuiConfig {

    public MTGuiClientConfig(GuiScreen parentScreen) throws ConfigException {
        super(parentScreen, MessTech.MODID, "MessTech", true, Config.class);
    }
}
