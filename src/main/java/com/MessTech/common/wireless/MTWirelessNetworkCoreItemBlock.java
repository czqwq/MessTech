package com.MessTech.common.wireless;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseTileEntity;

/**
 * Item form of the wireless ME network core. The GTLCore-style source line ("Added by GregTech Leisure") is attached
 * centrally by the mod's content registration, so the tooltip here only carries the static usage hints plus the network
 * the item remembers.
 *
 * <p>
 * Since v2 the core item is the carrier of the network across a break/place cycle: {@link MTWirelessNetworkCoreTile}
 * writes {@link #NBT_NETWORK} (and the cached name/color/access level) into the drop stack, and
 * {@code MTWirelessNetworkCoreBlock#onBlockPlacedBy} hands that id to the tile entity so it can re-attach to the
 * network. The keys are intentionally the same names/format the binding tool uses for its one saved network.
 *
 * <p>
 * Placement copies the GregTech hatch orientation: GT5U's {@code ItemMachines} orients a machine with
 * {@code BaseTileEntity.getSideForPlayerPlacing(player, ForgeDirection.UP, validFacings)}, i.e. the face towards the
 * player (UP/DOWN when the player looks steeply down/up). That is what makes the core come out with a sensible
 * connection face instead of always pointing at the block it was clicked against.
 */
public class MTWirelessNetworkCoreItemBlock extends ItemBlock {

    /** NBT key of the remembered network's {@link UUID#toString()} (1.7.10 NBT has no UUID type). */
    public static final String NBT_NETWORK = "WirelessNetworkId";
    /** NBT key of the remembered network's cached display name. */
    public static final String NBT_NETWORK_NAME = "WirelessNetworkName";
    /** NBT key of the remembered network's {@code 0xRRGGBB} color (vanity: the tooltip and GUIs use the saved data). */
    public static final String NBT_NETWORK_COLOR = "WirelessNetworkColor";
    /** NBT key of the remembered network's access level ({@link MTWirelessAccess#id()}, one byte). */
    public static final String NBT_NETWORK_ACCESS = "WirelessNetworkAccess";

    /** Every face may become the connection face, like a GT hatch (only the wrench order differs). */
    private static final boolean[] ALL_FACINGS_ALLOWED = { true, true, true, true, true, true };

    private static final int SHORT_NETWORK_ID_LENGTH = 8;

    public MTWirelessNetworkCoreItemBlock(Block block) {
        super(block);
        // GameRegistry.registerBlock does not copy the block's creative tab to its ItemBlock.
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
    }

    /**
     * Places the core with the GT hatch facing rule. The metadata computed by {@code Block#onBlockPlaced} is replaced
     * on purpose: the player orientation is only available here.
     */
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        ForgeDirection facing = BaseTileEntity.getSideForPlayerPlacing(player, ForgeDirection.UP, ALL_FACINGS_ALLOWED);
        return super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, facing.ordinal());
    }

    // ------------------------------------------------------------------ remembered network

    /**
     * @return the network id remembered by the item, or {@code null} when it was dropped from an unbound core (or the
     *         NBT was hand-edited into garbage).
     */
    public static UUID getStoredNetworkId(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_NETWORK)) return null;

        try {
            return UUID.fromString(tag.getString(NBT_NETWORK));
        } catch (IllegalArgumentException e) {
            // Broken NBT (hand edited, or written by a different build): treat the item as a plain core.
            return null;
        }
    }

    /** @return the cached display name of the remembered network, or an empty string. */
    public static String getStoredNetworkName(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null ? "" : tag.getString(NBT_NETWORK_NAME);
    }

    /** @return the cached color of the remembered network ({@code 0xRRGGBB}), or {@link MTWirelessColors#DEFAULT}. */
    public static int getStoredNetworkColor(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_NETWORK_COLOR)) return MTWirelessColors.DEFAULT;
        return MTWirelessColors.sanitize(tag.getInteger(NBT_NETWORK_COLOR));
    }

    /** @return the cached access level of the remembered network ({@link MTWirelessAccess#PRIVATE} when unknown). */
    public static MTWirelessAccess getStoredNetworkAccess(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_NETWORK_ACCESS)) return MTWirelessAccess.PRIVATE;
        return MTWirelessAccess.byId(tag.getByte(NBT_NETWORK_ACCESS));
    }

    // ------------------------------------------------------------------ tooltip

    /**
     * House style: colours live in the lang files, every line here is one {@code wirelessme.tooltip.*} key. The
     * GTLCore-style source line (「由GregTech Leisure添加」) is attached centrally by the content registration.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.core.0"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.core.1"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.core.2"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.core.usage"));

        UUID network = getStoredNetworkId(stack);
        if (network == null) {
            tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.core.unbound"));
            return;
        }

        String name = getStoredNetworkName(stack);
        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "wirelessme.tooltip.core.bound",
                name.isEmpty() ? shortNetworkId(network) : name));
    }

    /** @return the first eight characters of {@code network}, the display fallback of an unnamed network. */
    private static String shortNetworkId(UUID network) {
        String text = network.toString();
        return text.length() <= SHORT_NETWORK_ID_LENGTH ? text : text.substring(0, SHORT_NETWORK_ID_LENGTH);
    }
}
