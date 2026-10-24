package com.MessTech.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.client.command.MTTextEffectCommand;
import com.MessTech.client.text.EffectTextRenderer;
import com.MessTech.client.text.effect.BuiltinTextEffects;
import com.MessTech.common.entity.MTEntityPiggy;
import com.MessTech.common.entity.MTEntityVoidstarArrow;
import com.MessTech.common.entity.MTPiggyHatRenderer;
import com.MessTech.common.entity.MTRenderPiggy;
import com.MessTech.common.gui.GuiGameConsole;
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

import appeng.api.parts.IPart;
import appeng.util.Platform;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Chaos Matter's look: a client-side material renderer, attached like GT's own Materials.initClient attaches
        // the
        // Infinity/TranscendentMetal ones.
        gregtech.api.enums.Materials chaosMatter = com.MessTech.common.material.MTChaosMatter.getMaterial();
        if (chaosMatter != null) {
            chaosMatter.renderer = new com.MessTech.common.render.MTChaosMatterRenderer();
        }
        MinecraftForgeClient.registerItemRenderer(MTItems.nacComponentItem, new MTNACComponentItemRenderer());
        // Fuel rods: the same oblique-axis tumble preview as Transcendent Metal (burnable + depleted).
        MTFuelRodItemRenderer.registerItemRenderers();
        // A Piggy: one shared renderer picking the effect (and so the GT5U renderer) from the stack damage.
        MTDynamicItemHelper.registerItemRenderer(MTItems.piggy);
        // The thrown piggy: billboarded icon of the stack it was thrown with.
        RenderingRegistry.registerEntityRenderingHandler(MTEntityPiggy.class, new MTRenderPiggy());
        // The arrow: vanilla's arrow renderer, registered for the subclass rather than left to the render manager's
        // hierarchy walk.
        RenderingRegistry.registerEntityRenderingHandler(MTEntityVoidstarArrow.class, new RenderArrow());
        // The worn piggy: vanilla cannot draw a plain item on the head (only blocks and skulls), so the helmet slot is
        // watched and the pig drawn over the head from inside the player's model.
        MTPiggyHatRenderer.init();
        // PigRegisterOn: the animated "PigTech" tooltip line (wildcard damage = every variant), behind the same static
        // "Add by:" prefix; no author line, unlike AuthorDynamic.registerOn.
        MTPigTech.pigRegisterOn(new ItemStack(MTItems.piggy, 1, OreDictionary.WILDCARD_VALUE));
        // MessTech's animated tooltip handler: the registry AuthorDynamic writes author lines into, plus the renderer
        // that
        // draws an animation (e.g. TRANSCENDENT_METAL) over the finished font.
        MTAnimatedTooltipHandler.init();
        // The bow's magic circle: a textured quad in world space, drawn while the bow is charged
        // (MTMagicCircleRenderer; the
        // trail particles are MTVoidstarFx's).
        MTMagicCircleRenderer.init();
        // The fireball of a DE explosion: MTEntityDEBlast carries it to the client, this draws it.
        MTDEBlastRenderer.init();
        // Text effects: the eight shaders with their palettes, the mask cache's reload hook (shader resources and font
        // atlases both live there), and the local /messtechfx preview command.
        BuiltinTextEffects.register();
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(EffectTextRenderer.INSTANCE);
        ClientCommandHandler.instance.registerCommand(new MTTextEffectCommand());
        // The MessTech Thaumonomicon tab icon: a dynamic texture the client tick redraws; the tick handler registers
        // it under MTThaumcraftResearch.TAB_ICON before any research GUI can open.
        com.MessTech.client.thaumcraft.MTResearchTabIcon.init();
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

    /**
     * The game console's window: a plain {@code GuiScreen}, not a container, because the DOOM
     * engine behind it runs on this client and nothing about it is shared with the server.
     */
    @Override
    public void openGameConsole(EntityPlayer player) {
        FMLClientHandler.instance()
            .showGuiScreen(new GuiGameConsole());
    }

}
