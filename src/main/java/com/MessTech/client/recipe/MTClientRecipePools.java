package com.MessTech.client.recipe;

import com.MessTech.common.recipe.MTCombProcessingRecipes;
import com.MessTech.common.recipe.MTRecipeMaps;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Builds the NEI-only recipe pools on the client, once, on the first client tick: their sources are still being added
 * by other mods during {@code FMLLoadCompleteEvent}, and a remote client never receives {@code FMLServerStartedEvent}.
 */
@SideOnly(Side.CLIENT)
public final class MTClientRecipePools {

    private MTClientRecipePools() {}

    /** Client-only: registered from {@code ClientProxy#preInit}. */
    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MTClientRecipePools());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Safe mid-dispatch: EventBus#post iterates a listener array cached before the call.
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        populate();
    }

    /** Mirrors CommonProxy#serverStarted; every builder is idempotent or re-runnable. */
    private static void populate() {
        MTRecipeMaps.populateNanoScaleFoundryRecipes();
        MTRecipeMaps.populateOneStepCircuitPoolRecipes();
        MTRecipeMaps.populateBosesCraftingArrayRecipes();
        MTRecipeMaps.populateQftProbabilityDestroyerRecipes();
        MTCombProcessingRecipes.populateCombProcessingRecipes();
        MTRecipeMaps.populateAssFactoryAssemblyLineRecipes();
    }
}
