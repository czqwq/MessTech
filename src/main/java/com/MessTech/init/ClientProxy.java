package com.MessTech.init;

import net.minecraftforge.client.MinecraftForgeClient;

import com.MessTech.common.items.MTFuelRodItemRenderer;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.items.MTNACComponentItemRenderer;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForgeClient.registerItemRenderer(MTItems.nacComponentItem, new MTNACComponentItemRenderer());
        // Fuel rods: same oblique-axis tumble preview as Transcendent Metal (burnable + depleted).
        MTFuelRodItemRenderer.registerItemRenderers();
    }

}
