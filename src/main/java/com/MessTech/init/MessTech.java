package com.MessTech.init;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.MessTech.common.recipe.ExtraRecipes;
import com.MessTech.common.recipe.GTRecipes;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import gregtech.api.enums.Mods;

@Mod(
    modid = MessTech.MODID,
    version = com.MessTech.init.Tags.VERSION,
    name = "MessTech",
    guiFactory = "com.MessTech.client.gui.config.MTGuiFactory",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech; required-after:tectech; required-after:structurelib; required-after:AppleCore; required-after:Thaumcraft")
public class MessTech {

    public static final String MODID = "MessTech";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static final Logger MT_LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.MessTech.init.ClientProxy", serverSide = "com.MessTech.init.CommonProxy")
    public static CommonProxy proxy;

    /** The mod object itself; {@code EntityRegistry#registerModEntity} wants it. Set by FML's constructor call. */
    public static MessTech instance;

    public MessTech() {
        instance = this;
        // The materials must be handed to GT here: Materials.init() runs in GT's own preInit, before this mod's, and
        // IMaterialHandler#onMaterialsInit is only ever called from inside it.
        com.MessTech.common.material.MTChaosMatter.register();
        com.MessTech.common.material.MTRainbow.register();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void completeInit(FMLLoadCompleteEvent event) {
        GTRecipes.loadRecipes();
        if (Mods.Computronics.isModLoaded() && Mods.Thaumcraft.isModLoaded() && Mods.Avaritia.isModLoaded()) {
            ExtraRecipes.loadReplacementRecipes();
        }
        if (Mods.Forestry.isModLoaded() && Mods.ExtraBees.isModLoaded() && Mods.MagicBees.isModLoaded()) {
            ExtraRecipes.loadBeeRecipes();
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        proxy.serverStopped(event);
    }
}
