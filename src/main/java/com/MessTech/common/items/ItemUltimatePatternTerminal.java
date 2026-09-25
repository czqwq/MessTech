package com.MessTech.common.items;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.MessTech.common.parts.PartUltimatePatternTerminal;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

/**
 * Item form of {@link PartUltimatePatternTerminal}.
 * <p>
 * This is the AE way to put a terminal on a cable: the item is an {@link IPartItem}, AE's bus renderer draws it as the
 * part in the inventory, and a right click on a cable runs {@code ApiPart#placeBus}, which attaches the part to that
 * cable's bus on the clicked side. There is no block and no tile entity - the part's "tile" is the cable bus.
 * <p>
 * The icon is AE's own {@code ItemPart.PatternTerminalEx} sprite. {@code getSpriteNumber} is 0 for the same reason
 * AE's {@code ItemMultiPart} returns it: with sprite number 0 the item is stitched into the block atlas, which is
 * where that sprite lives.
 */
public class ItemUltimatePatternTerminal extends Item implements IPartItem {

    /** AE's own item sprite of the extended pattern terminal, in the block atlas. */
    private static final String TEXTURE = "appliedenergistics2:ItemPart.PatternTerminalEx";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public ItemUltimatePatternTerminal() {
        this.setUnlocalizedName("UltimatePatternTerminal");
        this.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        // AE's bus renderer draws the part form of this item in the inventory and in the player's hand.
        AEApi.instance()
            .partHelper()
            .setItemBusRenderer(this);
    }

    @Override
    public IPart createPartFromItemStack(ItemStack is) {
        return new PartUltimatePatternTerminal(is);
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ) {
        // Same call AE's own ItemMultiPart makes: attach the part to the cable bus at the clicked block/side.
        return AEApi.instance()
            .partHelper()
            .placeBus(is, x, y, z, side, player, world);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.icon = register.registerIcon(TEXTURE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return this.icon;
    }
}
