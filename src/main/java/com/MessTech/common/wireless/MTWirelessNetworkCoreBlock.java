package com.MessTech.common.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

import com.cleanroommc.modularui.factory.GuiFactories;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

/**
 * The wireless ME network core block: a plain single block whose tile entity exposes one AE2 grid node on its
 * connection face. That face is chosen like a GregTech hatch is oriented on placement (see
 * {@link MTWirelessNetworkCoreItemBlock#placeBlockAt}: the side facing the player, GT5U's
 * {@code BaseTileEntity#getSideForPlayerPlacing}); a GT wrench rotates it, otherwise right-clicking opens the core GUI.
 *
 * <p>
 * Its metadata is the {@link ForgeDirection} ordinal (0 = DOWN .. 5 = EAST) of the connection face. That face draws the
 * animated front texture (GTLCore's overlay composited onto the EV casing side, so it is opaque); the other faces use
 * the EV machine casing textures, which are copied from GT5U instead of being taken from GT's icon containers: those
 * are only guaranteed to be stitched for GT's own blocks and a null icon would fall back to a transparent texture.
 *
 * <p>
 * Since v2 the core is a persistent network's <i>core slot</i> holder, and it can be unbound. The item form must keep
 * the network it belonged to, which 1.7.10 makes surprisingly awkward: {@code breakBlock} runs before the block
 * harvest drops, and by then the tile entity (the only thing that knows the network) is removed. The block therefore
 * snapshots the drop stack into {@link #PENDING_DROPS} during {@code breakBlock} and hands it back from
 * {@link #getDrops} a moment later. Entries that are never collected (creative mode, world edits) expire after
 * {@link #PENDING_DROP_LIFETIME_TICKS}.
 */
public class MTWirelessNetworkCoreBlock extends BlockContainer {

    public static final MTWirelessNetworkCoreBlock INSTANCE = new MTWirelessNetworkCoreBlock();

    /**
     * Inventory rendering has no world context, so the front is pinned to NORTH. In the world
     * {@link #getIcon(IBlockAccess, int, int, int, int)} decides by the tile entity's connection side.
     */
    private static final int INVENTORY_FRONT_SIDE = ForgeDirection.NORTH.ordinal();

    /** How long a snapshotted drop stays valid; the harvest drop follows its block break within the same tick. */
    private static final int PENDING_DROP_LIFETIME_TICKS = 40;

    /** Drop stacks prepared in {@link #breakBlock} and picked up by {@link #getDrops}, per position. */
    private static final Map<DropPos, PendingDrop> PENDING_DROPS = new HashMap<>();

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;
    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;
    @SideOnly(Side.CLIENT)
    private IIcon bottomIcon;

    private MTWirelessNetworkCoreBlock() {
        super(Material.iron);
        setBlockName("wirelessNetworkCore");
        // Deliberately no setBlockTextureName: a block texture name makes ItemBlock render as a flat 2D item sprite
        // (ItemBlock#getSpriteNumber() -> 1) instead of the 3D machine block built from the icons below.
        setHardness(5.0F);
        setResistance(5.0F);
        setHarvestLevel("wrench", 2);
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    // region Rendering

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.frontIcon = register.registerIcon("messtech:wireless_network_core_front");
        this.sideIcon = register.registerIcon("messtech:wireless_network_core_side");
        this.topIcon = register.registerIcon("messtech:wireless_network_core_top");
        this.bottomIcon = register.registerIcon("messtech:wireless_network_core_bottom");
        this.blockIcon = this.sideIcon != null ? this.sideIcon : this.frontIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == INVENTORY_FRONT_SIDE ? frontIcon() : casingIcon(side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof MTWirelessNetworkCoreTile core && side == core.getConnectionSide()
            .ordinal()) {
            return frontIcon();
        }
        return casingIcon(side);
    }

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon() {
        return this.frontIcon != null ? this.frontIcon : casingIcon(ForgeDirection.NORTH.ordinal());
    }

    /** @return the EV machine casing icon of that face, with the opaque casing side as the final fallback. */
    @SideOnly(Side.CLIENT)
    private IIcon casingIcon(int side) {
        IIcon icon = switch (side) {
            case 0 -> this.bottomIcon;
            case 1 -> this.topIcon;
            default -> this.sideIcon;
        };
        if (icon != null) return icon;
        return this.sideIcon != null ? this.sideIcon : this.blockIcon;
    }

    // endregion

    // region Placement / breaking

    /**
     * Fallback for placement paths that do not go through {@link MTWirelessNetworkCoreItemBlock#placeBlockAt} (world
     * generation, block placers): face the block the core was placed against. Player placement uses the GT hatch
     * orientation instead - the side towards the player - so it is handled by the item block.
     */
    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        ForgeDirection clicked = ForgeDirection.getOrientation(side);
        if (clicked == ForgeDirection.UNKNOWN) return meta & 7;
        return clicked.getOpposite()
            .ordinal();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayer player)) return;

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof MTWirelessNetworkCoreTile core)) return;

        core.setOwner(player);
        // The item remembers the network it was broken from: re-attach to it when the network still has no other core
        // and the player may use it (a failed claim leaves the core unbound and tells the player why).
        core.adoptFromItem(player, stack);
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (GregTechAPI.isMachineBlock(this, world.getBlockMetadata(x, y, z))) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
    }

    /**
     * Snapshots the drop stack while the tile entity still knows its network, then detaches the core from the network.
     * 1.7.10 calls this before the harvest drop is computed, so {@link #getDrops} can hand the snapshotted stack back.
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof MTWirelessNetworkCoreTile core) {
                ItemStack drop = core.createDropStack();
                if (drop != null) rememberPendingDrop(world, x, y, z, drop);
                core.releaseOnBreak();
            }
        }
        if (GregTechAPI.isMachineBlock(this, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /**
     * Returns the stack snapshotted by {@link #breakBlock} when this block was just broken (so it carries the network
     * NBT), otherwise the vanilla drops. Silk touch must stay off ({@link #canSilkHarvest()}), otherwise that path
     * would
     * bypass this method and duplicate the block without its NBT.
     * <p>
     * Explosions are the reason for the tile-entity fallback: 1.7.10 computes the drops ({@code getDrops})
     * <em>before</em>
     * {@code onBlockExploded} runs {@code breakBlock} (Explosion.java:221 then 224), so a pending snapshot does not
     * exist
     * yet at that point. The core tile entity is still in the world, so its stack is taken directly; the snapshot that
     * {@code breakBlock} stores a moment later is never collected and simply expires.
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        if (!world.isRemote) {
            ItemStack pending = takePendingDrop(world, x, y, z);
            if (pending == null) {
                TileEntity tile = world.getTileEntity(x, y, z);
                if (tile instanceof MTWirelessNetworkCoreTile core) pending = core.createDropStack();
            }
            if (pending != null) {
                ArrayList<ItemStack> drops = new ArrayList<>(1);
                drops.add(pending);
                return drops;
            }
        }
        return super.getDrops(world, x, y, z, metadata, fortune);
    }

    /**
     * Silk touch would harvest via {@code createStackedBlock}, skipping {@link #getDrops} and the remembered network.
     */
    @Override
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MTWirelessNetworkCoreTile();
    }

    // endregion

    // region Pending drops

    /** Remembers {@code stack} as the drop of the core that is being broken at that position right now. */
    private static void rememberPendingDrop(World world, int x, int y, int z, ItemStack stack) {
        long now = world.getTotalWorldTime();
        purgePendingDrops(now);
        PENDING_DROPS.put(new DropPos(world.provider.dimensionId, x, y, z), new PendingDrop(stack, now));
    }

    /** @return the snapshotted drop of that position, removing it, or {@code null} when there is none. */
    private static ItemStack takePendingDrop(World world, int x, int y, int z) {
        purgePendingDrops(world.getTotalWorldTime());
        PendingDrop pending = PENDING_DROPS.remove(new DropPos(world.provider.dimensionId, x, y, z));
        return pending == null ? null : pending.stack();
    }

    /** Drops entries whose harvest never happened (creative break, world edit, a failed block event, ...). */
    private static void purgePendingDrops(long now) {
        PENDING_DROPS.values()
            .removeIf(pending -> Math.abs(now - pending.storedAt()) > PENDING_DROP_LIFETIME_TICKS);
    }

    /** Position key of the pending drop map; the dimension is part of it because the map is shared by every world. */
    private record DropPos(int dimension, int x, int y, int z) {}

    private record PendingDrop(ItemStack stack, long storedAt) {}

    // endregion

    // region Interaction

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof MTWirelessNetworkCoreTile core)) return false;

        ItemStack held = player.getHeldItem();

        // The binding tool always consumes the click, so the core GUI never opens while the tool is held. Saving the
        // network here as well (not only in the tool's onItemUseFirst) fixes the Sneak + right click race: the sneak
        // flag is sent in the movement packet, so a click in the same tick can reach the server before it.
        if (held != null && held.getItem() == MTWirelessNetworkBindingTool.INSTANCE) {
            if (world.isRemote) return true;
            if (player instanceof EntityPlayerMP serverPlayer) {
                UUID network = core.getNetworkId();
                if (network == null) {
                    GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.core_no_network");
                } else if (!MTWirelessNetworkRuntime.canAccess(serverPlayer, network)) {
                    GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.no_access");
                } else {
                    MTWirelessNetworkBindingTool.saveNetwork(serverPlayer, held, network);
                }
            }
            return true;
        }

        if (held != null && GTUtility.isStackInList(held, GregTechAPI.sWrenchList)) {
            if (world.isRemote) return true;
            if (GTModHandler.damageOrDechargeItem(held, 1, 1000, player)) {
                rotateConnectionSide(core);
                if (held.stackSize <= 0) ForgeEventFactory.onPlayerDestroyItem(player, held);
            }
            return true;
        }

        if (world.isRemote) return true;

        core.migrateLegacyOwner(player);
        if (!core.canAccess(player)) {
            GTUtility.sendChatTrans(player, "wirelessme.chat.no_access");
            return true;
        }
        GuiFactories.tileEntity()
            .open(player, core);
        return true;
    }

    /** Advances the connection face by one orientation and refreshes the tile entity/node. */
    private static void rotateConnectionSide(MTWirelessNetworkCoreTile core) {
        ForgeDirection current = core.getConnectionSide();
        ForgeDirection next = ForgeDirection.VALID_DIRECTIONS[(current.ordinal() + 1)
            % ForgeDirection.VALID_DIRECTIONS.length];
        core.setConnectionSide(next);
    }

    // endregion
}
