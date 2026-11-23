package com.MessTech.common.ae2;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEColor;
import appeng.core.CreativeTab;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Item form of {@link MTUltraDenseCablePart} (超致密压缩ME线缆), see {@code docs/ultra-ae2.md} §1.5.
 * <p>
 * It cannot extend AE2's {@code ItemMultiPart} (final, and the part constructor casts the stack to it anyway), so it is
 * a
 * plain {@link Item} implementing {@link IPartItem}. All 17 {@link AEColor} variants are the item damage
 * ({@link AEColor#ordinal()}), and {@link #createPartFromItemStack} hands the part AE2's own dense cable stack of that
 * colour plus our display stack.
 * <p>
 * {@link #getSpriteNumber()} is 0 like AE2's {@code ItemMultiPart}, so {@link #registerIcons} stitches the item into
 * the block atlas at AE2's own dense cable sprite (it lives in {@code textures/blocks/}) and the item is drawn in the
 * inventory by AE2's bus renderer instead of a flat icon. {@code PartDenseCable#getDenseTexture} falls back to
 * {@code getItemStack().getIconIndex()} for the uncoloured (Transparent) cable, which is exactly this icon.
 */
public class MTUltraDenseCableItem extends Item implements IPartItem {

    /** AE2's dense cable sprite, which lives in the block atlas. */
    private static final String TEXTURE = "appliedenergistics2:ItemPart.CableDense";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public MTUltraDenseCableItem() {
        this.setUnlocalizedName("mtUltraDenseCable");
        this.setMaxStackSize(64);
        this.setHasSubtypes(true);
        // AE2's preInit (which creates its creative tab) runs before this mod's, see docs/ultra-ae2.md.
        this.setCreativeTab(CreativeTab.instance);
        // Draws the part through AE2's BusRenderer in the inventory/NEI.
        AEApi.instance()
            .partHelper()
            .setItemBusRenderer(this);
    }

    /**
     * Builds the part on AE2's dense cable stack of this colour (that is what {@code PartDenseCable} expects, it reads
     * the colour from the stack) plus our own item stack, which the part reports for NBT, drops and WAILA.
     */
    @Override
    public IPart createPartFromItemStack(ItemStack is) {
        final AEColor color = MTUltraDenseCables.colorFromDamage(is.getItemDamage());
        final ItemStack ae2DenseCable = MTUltraDenseCables.ae2DenseCable(color, 1);
        if (ae2DenseCable == null) return null;
        return new MTUltraDenseCablePart(ae2DenseCable, is.copy());
    }

    /**
     * The same call AE2's {@code ItemMultiPart} ends up in ({@code PartPlacement}): attach the cable to the clicked
     * side.
     */
    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ) {
        return AEApi.instance()
            .partHelper()
            .placeBus(is, x, y, z, side, player, world);
    }

    /** All 17 colour variants in the AE2 creative tab (and therefore in NEI). */
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> itemStacks) {
        for (AEColor color : AEColor.VALUES) {
            itemStacks.add(MTUltraDenseCables.ultraDenseStack(color));
        }
    }

    /** {@code item.mtUltraDenseCable.name} plus AE2's localised colour name (e.g. "白色"). */
    @Override
    public String getItemStackDisplayName(ItemStack is) {
        final AEColor color = MTUltraDenseCables.colorFromDamage(is.getItemDamage());
        return StatCollector.translateToLocal(this.getUnlocalizedName(is) + ".name") + " "
            + StatCollector.translateToLocal(color.getUnlocalized());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack is, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("mtultra.tooltip.ultraCable"));
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
