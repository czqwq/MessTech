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
 * Item form of {@link MTCompressedGlassCablePart} (压缩ME玻璃线缆), see {@code docs/ultra-ae2.md} §1.5.
 * <p>
 * The glass counterpart of {@link MTUltraDenseCableItem}: a plain {@link Item} implementing {@link IPartItem} (AE2's
 * {@code ItemMultiPart} is final and the part constructor needs it), 17 {@link AEColor} variants as item damage
 * ({@link AEColor#ordinal()}). {@link #createPartFromItemStack} builds {@link MTCompressedGlassCablePart} on AE2's own
 * glass cable stack, so connection type {@code AECableType.GLASS}, rendering, collision boxes and the cable bus NBT
 * round-trip are AE2's.
 * <p>
 * {@link #getSpriteNumber()} is 0 like AE2's {@code ItemMultiPart}, so the item is drawn by AE2's bus renderer in the
 * inventory; the icon registered from AE2's own glass cable sprite lives in the block atlas and is the item's flat
 * fallback icon ({@code PartCable#getGlassTexture} uses AE2's own glass stack for the Transparent colour, so the
 * registered icon only backs the item form).
 */
public class MTCompressedGlassCableItem extends Item implements IPartItem {

    /** AE2's glass cable sprite, which lives in the block atlas. */
    private static final String TEXTURE = "appliedenergistics2:ItemPart.CableGlass";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public MTCompressedGlassCableItem() {
        this.setUnlocalizedName("mtCompressedGlassCable");
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
     * Builds the part on AE2's glass cable stack of this colour plus our own item stack, which the part reports for
     * NBT, drops and WAILA.
     */
    @Override
    public IPart createPartFromItemStack(ItemStack is) {
        final AEColor color = MTUltraDenseCables.colorFromDamage(is.getItemDamage());
        final ItemStack ae2GlassCable = MTUltraDenseCables.ae2GlassCable(color, 1);
        if (ae2GlassCable == null) return null;
        return new MTCompressedGlassCablePart(ae2GlassCable, is.copy());
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
            itemStacks.add(MTUltraDenseCables.compressedGlassStack(color));
        }
    }

    /** {@code item.mtCompressedGlassCable.name} plus AE2's localised colour name (e.g. "白色"). */
    @Override
    public String getItemStackDisplayName(ItemStack is) {
        final AEColor color = MTUltraDenseCables.colorFromDamage(is.getItemDamage());
        return StatCollector.translateToLocal(this.getUnlocalizedName(is) + ".name") + " "
            + StatCollector.translateToLocal(color.getUnlocalized());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack is, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("mtultra.tooltip.glassCable"));
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
