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
 * Item form of {@link PartUltimatePatternTerminal}: the AE way to put a terminal on a cable - an {@link IPartItem}
 * that AE's bus renderer draws in the inventory, placed by a right click on a cable via {@code ApiPart#placeBus} onto
 * that cable's bus on the clicked side. No block, no tile entity: the part's "tile" is the cable bus.
 * <p>
 * The icon is AE's {@code ItemPart.PatternTerminalEx} sprite; {@code getSpriteNumber} is 0 like AE's
 * {@code ItemMultiPart} so the item is stitched into the block atlas, where that sprite lives.
 */
public class ItemUltimatePatternTerminal extends Item implements IPartItem {

    /** AE's extended pattern terminal sprite, in the block atlas. */
    private static final String TEXTURE = "appliedenergistics2:ItemPart.PatternTerminalEx";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public ItemUltimatePatternTerminal() {
        this.setUnlocalizedName("UltimatePatternTerminal");
        this.setCreativeTab(GregTechAPI.TAB_GREGTECH);
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
        // Same call AE's ItemMultiPart makes: attach the part to the cable bus at the clicked block/side.
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
