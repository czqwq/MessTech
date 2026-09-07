package com.MessTech.init;

import net.minecraftforge.client.MinecraftForgeClient;

import com.MessTech.common.item.MTItems;
import com.MessTech.common.item.MTNACComponentItemRenderer;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForgeClient.registerItemRenderer(MTItems.nacComponentItem, new MTNACComponentItemRenderer());
    }

}
