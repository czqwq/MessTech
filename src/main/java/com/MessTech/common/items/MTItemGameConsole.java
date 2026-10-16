package com.MessTech.common.items;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.MessTech.init.MessTech;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

/**
 * The game console (游戏机): a handheld that runs a whole copy of DOOM.
 * <p>
 * Right clicking opens a window about half the screen across with the game inside it; {@code Esc}
 * closes it again. Nothing about the console is server state - the engine runs on the client's own
 * machine, next to Minecraft's - so the item deliberately does nothing at all server-side. That is
 * also why both right-click paths have to be covered here:
 * <ul>
 * <li>{@link #onItemRightClick} is the "pointing at nothing" case, and it only runs on the
 * client;</li>
 * <li>{@link #onItemUseFirst} is the "pointing at a block" case. Minecraft calls it on the client
 * <em>and</em> the server, so it opens the window on the client and returns true everywhere, which
 * keeps the click from also reaching whatever block is under the crosshair.</li>
 * </ul>
 */
public class MTItemGameConsole extends Item {

    private static final String TEXTURE = "messtech:gameConsole";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public MTItemGameConsole() {
        // "messtech." prefix like every other item here, which makes the key
        // item.messtech.gameConsole.name. Without the prefix the key was item.gameConsole.name,
        // which no language file defines, so the item showed the raw key instead of its name.
        this.setUnlocalizedName("messtech.gameConsole");
        this.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        this.setMaxStackSize(1);
    }

    /** Right click on air. */
    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        if (world.isRemote) {
            MessTech.proxy.openGameConsole(player);
        }
        return stack;
    }

    /** Right click on a block: the console wins over the block's own activation. */
    @Override
    public boolean onItemUseFirst(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        if (world.isRemote) {
            MessTech.proxy.openGameConsole(player);
        }
        return true;
    }

    /**
     * The tooltip follows the house style of {@code docs/knowledge.md}: the colours live in the
     * lang files and every line here is one translated key.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List<String> tooltip,
        final boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("messtech.gameConsole.tooltip.open"));
        tooltip.add(StatCollector.translateToLocal("messtech.gameConsole.tooltip.keys"));
        tooltip.add(StatCollector.translateToLocal("messtech.gameConsole.tooltip.menu"));
        tooltip.add(StatCollector.translateToLocal("messtech.gameConsole.tooltip.data"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(final IIconRegister register) {
        this.icon = register.registerIcon(TEXTURE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(final int damage) {
        return this.icon;
    }
}
