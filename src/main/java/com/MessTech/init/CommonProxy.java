package com.MessTech.init;

import static com.MessTech.init.MessTech.MT_LOG;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.block.MTBlocks;
import com.MessTech.common.entity.MTEntityPiggy;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.machine.loaders.MTMachineLoader;
import com.MessTech.common.network.MTNetwork;
import com.MessTech.common.parts.PartUltimatePatternTerminal;
import com.MessTech.common.process.MTProcessHandler;
import com.MessTech.common.recipe.MTChemicalTwisterRecipes;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.MessTech.common.recipe.RecipeMessFood;
import com.MessTech.common.util.MTTrueKill;

import appeng.api.parts.IPart;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.util.Platform;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;

public class CommonProxy implements IGuiHandler {

    /**
     * GUI id of the Ultimate Pattern Terminal, handed to {@code EntityPlayer#openGui}. The part's side is packed into
     * the bits above the id, because Forge's GUI handler is only given the cable bus' coordinates and the terminal is
     * one part among six sides.
     */
    public static final int GUI_ULTIMATE_PATTERN_TERMINAL = 20;

    /** The GUI id to open {@code side} of the Ultimate Pattern Terminal's cable bus with. */
    public static int ultimatePatternTerminalGuiId(ForgeDirection side) {
        return GUI_ULTIMATE_PATTERN_TERMINAL | (side.ordinal() << 8);
    }

    /** The side packed into a GUI id by {@link #ultimatePatternTerminalGuiId}. */
    public static ForgeDirection ultimatePatternTerminalSide(int guiId) {
        return ForgeDirection.getOrientation((guiId >> 8) & 7);
    }

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        MTNetwork.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(MessTech.instance, this);
        MTItems.registerItems();
        // The thrown "A Piggy" (ids are per mod, so the single entity of this mod is id 0).
        MTEntityPiggy.register();
        // The piggy's true kill needs to know whether a death path really ran (see MTTrueKill), so it watches the
        // death event of the game.
        MTTrueKill.init();
        MTProcessHandler.init();
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
        RecipeMessFood.register();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        MT_LOG.info("Loading Machine!");
        MTBlocks.registerBlocks();
        MTMachineLoader.loadMachines();
        MT_LOG.info("Ciallo～(∠・ω< )⌒★");
        MTChemicalTwisterRecipes.loadRecipePostInit();
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        MT_LOG.debug("Hello the mess world!");
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if ((ID & 0xFF) != GUI_ULTIMATE_PATTERN_TERMINAL) {
            return null;
        }

        final ForgeDirection side = ultimatePatternTerminalSide(ID);
        final IPart part = Platform.getPartFromTE(world.getTileEntity(x, y, z), side);
        if (!(part instanceof PartUltimatePatternTerminal terminal)) {
            return null;
        }

        // AE only fills the open context in Platform#openGUI, and its middle-click "how many to craft" path on the
        // blank pattern slot reads it, so a GUI opened through Forge's IGuiHandler has to set it itself.
        final ContainerOpenContext context = new ContainerOpenContext(terminal);
        context.setWorld(world);
        context.setX(x);
        context.setY(y);
        context.setZ(z);
        context.setSide(side);

        final ContainerPatternTermEx container = new ContainerPatternTermEx(player.inventory, terminal);
        container.setOpenContext(context);
        return container;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    public void serverStarted(FMLServerStartedEvent event) {
        // Fallback in case GT5U's NAC pools are only populated after MessTech postInit.
        MTRecipeMaps.populateNanoScaleFoundryRecipes();
        MTRecipeMaps.populateNanoScaleFoundry24PoolRecipes();
        // BEC recipes are registered by GT5U's postload recipe loader.
        MTRecipeMaps.populateBosesCraftingArrayRecipes();
        // The QFT pool is filled while the GT recipe loaders run, i.e. possibly after our postInit.
        MTRecipeMaps.populateQftProbabilityDestroyerRecipes();
        // Same for the Assembly Factory's Assembly Line pool: it is built from GT's Assembly Line definitions, which
        // other mods (TST's circuit lines, for one) only finish registering during their own postInit.
        MTRecipeMaps.populateAssFactoryAssemblyLineRecipes();
    }
}
