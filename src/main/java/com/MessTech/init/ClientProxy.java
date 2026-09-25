package com.MessTech.init;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.common.entity.MTEntityPiggy;
import com.MessTech.common.entity.MTPiggyHatRenderer;
import com.MessTech.common.entity.MTRenderPiggy;
import com.MessTech.common.gui.GuiUltimatePatternTerminal;
import com.MessTech.common.items.MTFuelRodItemRenderer;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.items.MTNACComponentItemRenderer;
import com.MessTech.common.parts.PartUltimatePatternTerminal;
import com.MessTech.common.util.MTAnimatedTooltipHandler;
import com.MessTech.common.util.MTDynamicItemHelper;
import com.MessTech.common.util.MTPigTech;

import appeng.api.parts.IPart;
import appeng.util.Platform;
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
        // The worn piggy: vanilla cannot draw a plain item on the head (only blocks and skulls), so the helmet slot
        // is watched and the pig is drawn on top of the head, from inside the model of the player.
        MTPiggyHatRenderer.init();
        // PigRegisterOn: the animated "PigTech" line on the piggy's tooltip (wildcard damage = every variant), behind
        // the same static "Add by:" prefix the MessTech line uses. No author line, unlike AuthorDynamic.registerOn.
        MTPigTech.pigRegisterOn(new ItemStack(MTItems.piggy, 1, OreDictionary.WILDCARD_VALUE));
        // MessTech's own animated tooltip handler: the registry AuthorDynamic writes its author lines into, plus the
        // tooltip renderer that draws an animation's renderer (e.g. TRANSCENDENT_METAL) over the finished font.
        MTAnimatedTooltipHandler.init();
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if ((ID & 0xFF) != GUI_ULTIMATE_PATTERN_TERMINAL) {
            return null;
        }

        final ForgeDirection side = ultimatePatternTerminalSide(ID);
        final IPart part = Platform.getPartFromTE(world.getTileEntity(x, y, z), side);
        if (part instanceof PartUltimatePatternTerminal terminal) {
            return new GuiUltimatePatternTerminal(player.inventory, terminal);
        }
        return null;
    }

}
