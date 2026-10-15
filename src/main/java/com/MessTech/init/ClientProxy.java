package com.MessTech.init;

import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.common.entity.MTEntityPiggy;
import com.MessTech.common.entity.MTEntityVoidstarArrow;
import com.MessTech.common.entity.MTPiggyHatRenderer;
import com.MessTech.common.entity.MTRenderPiggy;
import com.MessTech.common.gui.GuiUltimatePatternTerminal;
import com.MessTech.common.items.MTFuelRodItemRenderer;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.items.MTNACComponentItemRenderer;
import com.MessTech.common.parts.PartUltimatePatternTerminal;
import com.MessTech.common.render.MTDEBlastRenderer;
import com.MessTech.common.render.MTMagicCircleRenderer;
import com.MessTech.common.util.MTAnimatedTooltipHandler;
import com.MessTech.common.util.MTDynamicItemHelper;
import com.MessTech.common.util.MTPigTech;
import com.MessTech.common.util.MTStarfieldText;

import appeng.api.parts.IPart;
import appeng.util.Platform;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Chaos Matter: the look is a client-side material renderer, attached the same way GT's own
        // Materials.initClient
        // attaches the Infinity/TranscendentMetal ones.
        gregtech.api.enums.Materials chaosMatter = com.MessTech.common.material.MTChaosMatter.getMaterial();
        if (chaosMatter != null) {
            chaosMatter.renderer = new com.MessTech.common.render.MTChaosMatterRenderer();
        }
        MinecraftForgeClient.registerItemRenderer(MTItems.nacComponentItem, new MTNACComponentItemRenderer());
        // Fuel rods: same oblique-axis tumble preview as Transcendent Metal (burnable + depleted).
        MTFuelRodItemRenderer.registerItemRenderers();
        // A Piggy: one shared renderer that picks the effect (and therefore the GT5U renderer) from the stack damage.
        MTDynamicItemHelper.registerItemRenderer(MTItems.piggy);
        // The thrown piggy: billboarded icon of the stack it was thrown with.
        RenderingRegistry.registerEntityRenderingHandler(MTEntityPiggy.class, new MTRenderPiggy());
        // The Voidstar Piercer's arrow: vanilla's own arrow renderer, registered for the subclass explicitly rather
        // than left to the render manager's walk up the class hierarchy.
        RenderingRegistry.registerEntityRenderingHandler(MTEntityVoidstarArrow.class, new RenderArrow());
        // The worn piggy: vanilla cannot draw a plain item on the head (only blocks and skulls), so the helmet slot
        // is watched and the pig is drawn on top of the head, from inside the model of the player.
        MTPiggyHatRenderer.init();
        // PigRegisterOn: the animated "PigTech" line on the piggy's tooltip (wildcard damage = every variant), behind
        // the same static "Add by:" prefix the MessTech line uses. No author line, unlike AuthorDynamic.registerOn.
        MTPigTech.pigRegisterOn(new ItemStack(MTItems.piggy, 1, OreDictionary.WILDCARD_VALUE));
        // MessTech's own animated tooltip handler: the registry AuthorDynamic writes its author lines into, plus the
        // tooltip renderer that draws an animation's renderer (e.g. TRANSCENDENT_METAL) over the finished font.
        MTAnimatedTooltipHandler.init();
        // The Voidstar Piercer's name wears the STARFIELD animation: the handler identifies the line a renderer
        // belongs to by its visible text, so a name entry attaches the starfield to the item's own name line without
        // adding a second copy of it to the tooltip.
        // The damage has to be the wildcard one: the damage of a damageable stack is its durability damage and the
        // handler's map is keyed by it, so an entry registered at damage 0 stops matching as soon as the bow has been
        // fired once (the same wildcard the piggy's own registration uses).
        MTAnimatedTooltipHandler.addItemNameAnimation(
            new ItemStack(MTItems.voidstarBow, 1, OreDictionary.WILDCARD_VALUE),
            () -> StatCollector.translateToLocal("item.messtech.voidstarBow.name"),
            MTStarfieldText.INSTANCE);
        // The magic circle of the Voidstar Piercer: a textured quad in world space, drawn while the bow is charged
        // (see MTMagicCircleRenderer; the particles the arrow leaves behind are MTVoidstarFx's).
        MTMagicCircleRenderer.init();
        // And the fireball of a DE explosion: MTEntityDEBlast carries it to the client, this draws it.
        MTDEBlastRenderer.init();
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
