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
 * The game console (游戏机): a handheld running a whole copy of DOOM. Right clicking opens a window about half the
 * screen across with the game inside; {@code Esc} closes it.
 * <p>
 * Nothing about the console is server state - the engine runs on the client's own machine, next to Minecraft's - so
 * the item does nothing server-side. Hence both right-click paths:
 * <ul>
 * <li>{@link #onItemRightClick} ("pointing at nothing"): client only;</li>
 * <li>{@link #onItemUseFirst} ("pointing at a block"): Minecraft calls it on the client <em>and</em> the server, so it
 * opens the window on the client and returns true everywhere, keeping the click off the block under the crosshair.</li>
 * </ul>
 */
public class MTItemGameConsole extends Item {

    private static final String TEXTURE = "messtech:gameConsole";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public MTItemGameConsole() {
        // "messtech." prefix like every other item here: the key is item.messtech.gameConsole.name. Without it the
        // key was item.gameConsole.name, undefined in the lang files, so the raw key showed instead of the name.
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

    /** Right click on a block: the console wins over the block's activation. */
    @Override
    public boolean onItemUseFirst(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        if (world.isRemote) {
            MessTech.proxy.openGameConsole(player);
        }
        return true;
    }

    /**
     * House style ({@code docs/knowledge.md}): colours live in the lang files, every line here is one translated key.
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
