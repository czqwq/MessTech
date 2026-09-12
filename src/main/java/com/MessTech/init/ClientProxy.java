package com.MessTech.init;

import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.common.entity.MTEntityPiggy;
import com.MessTech.common.entity.MTRenderPiggy;
import com.MessTech.common.items.MTFuelRodItemRenderer;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.items.MTNACComponentItemRenderer;
import com.MessTech.common.util.MTDynamicItemHelper;
import com.MessTech.common.util.MTPigTech;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForgeClient.registerItemRenderer(MTItems.nacComponentItem, new MTNACComponentItemRenderer());
        // Fuel rods: same oblique-axis tumble preview as Transcendent Metal (burnable + depleted).
        MTFuelRodItemRenderer.registerItemRenderers();
        // A Piggy: one shared renderer that picks the effect (and therefore the GT5U renderer) from the stack damage.
        MTDynamicItemHelper.registerItemRenderer(MTItems.piggy);
        // The thrown piggy: billboarded icon of the stack it was thrown with.
        RenderingRegistry.registerEntityRenderingHandler(MTEntityPiggy.class, new MTRenderPiggy());
        // PigRegisterOn: the animated "PigTech" line on the piggy's tooltip (wildcard damage = every variant), behind
        // the same static "Add by:" prefix the MessTech line uses. No author line, unlike AuthorDynamic.registerOn.
        MTPigTech.pigRegisterOn(new ItemStack(MTItems.piggy, 1, OreDictionary.WILDCARD_VALUE));
    }

}
