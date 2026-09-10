package com.MessTech.init;

import static com.MessTech.init.MessTech.MT_LOG;

import com.MessTech.common.block.MTBlocks;
import com.MessTech.common.item.MTItems;
import com.MessTech.common.machine.loaders.MTMachineLoader;
import com.MessTech.common.recipe.MTRecipeMaps;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        MTItems.registerItems();
        if (Loader.isModLoaded("Torcherino")) {
            MT_LOG.info("拿火把捅你皮撅子");
        }
        if (Loader.isModLoaded("EZMiner")) {
            MT_LOG.info("别把家拆了哦");
        }
        if (Loader.isModLoaded("123Technology")) {
            MT_LOG.info("检测到123整活科技!前面忘了中间忘了后面忘了,反正艾萨天下无敌啊!");
        }
        if (Loader.isModLoaded("EZNuclear")) {
            MT_LOG.info("核电轻而易举啊!");
        }
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        MT_LOG.info("I'm a info here watching you");
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        MT_LOG.info("Loading Machine!");
        MTBlocks.registerBlocks();
        MTMachineLoader.loadMachines();
        MT_LOG.info("Ciallo～(∠・ω< )⌒★");
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        MT_LOG.debug("Hello the mess world!");
    }

    public void serverStarted(FMLServerStartedEvent event) {
        // Fallback in case GT5U's NAC pools are only populated after MessTech postInit.
        MTRecipeMaps.populateNanoScaleFoundryRecipes();
        MTRecipeMaps.populateNanoScaleFoundry24PoolRecipes();
        // BEC recipes are registered by GT5U's postload recipe loader.
        MTRecipeMaps.populateBosesCraftingArrayRecipes();
    }
}
