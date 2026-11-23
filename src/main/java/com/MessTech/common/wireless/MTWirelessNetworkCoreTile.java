package com.MessTech.common.wireless;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.ae2.IMTChannelCapacity;
import com.MessTech.common.wireless.gui.MTWirelessCoreGui;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTUtility;

/**
 * Tile entity of the wireless ME network core. Only the face given by the block metadata (a {@link ForgeDirection}
 * ordinal) exposes an AE2 grid node, so the core joins the ME network it is placed against. The node carries the ultra
 * dense capacity (2.1G = {@link Integer#MAX_VALUE} channels, see {@link #mtChannelCapacity()}) with no idle power draw;
 * the wireless links created by {@link MTWirelessNetworkRuntime} do not cost channels.
 *
 * <p>
 * Since v2 (see {@code docs/wireless-me-network.md} §9) a network is a world-level record that is independent of any
 * core: breaking a core only clears the record's {@code core} slot and keeps the record, its name/color/access level
 * and
 * every member binding. A freshly placed core therefore starts <b>unbound</b> ({@link #getNetworkId()} is {@code null})
 * and the player picks or creates a network in the core GUI; the item form remembers the network when the core is
 * broken ({@link #createDropStack()}) and re-attaches on placement ({@link #adoptFromItem(EntityPlayer, ItemStack)}).
 *
 * <p>
 * The proxy is created by AE2's {@link AENetworkTile} lifecycle ({@code validate -> onReady -> invalidate}), i.e. the
 * grid node is rebuilt after a chunk unload without us doing anything. {@link #onReady()} hands the core to
 * {@link MTWirelessNetworkRuntime#onCoreReady} so the runtime registers the network's core slot and rebuilds the
 * wireless links; the saved data is intentionally not touched on chunk unload, so member bindings survive.
 */
public class MTWirelessNetworkCoreTile extends AENetworkTile implements IGuiHolder<PosGuiData>, IMTChannelCapacity {

    public static final String TE_ID = "messtech.wirelessNetworkCore";
    public static final int MAX_NAME_LENGTH = 32;

    /** Legacy v1 NBT keys of the network id; kept so worlds saved before v2 keep their network. */
    private static final String NBT_NETWORK = "freq";
    private static final String NBT_NETWORK_MOST = "freqMost";
    private static final String NBT_NETWORK_LEAST = "freqLeast";
    /** UUID string of the player that placed this core; only used while the core is unbound (v2). */
    private static final String NBT_PLACED_BY = "placedBy";
    private static final String PROXY_NAME = "proxy";
    private static final int SHORT_NETWORK_ID_LENGTH = 8;

    private UUID networkId;
    private UUID placedBy;

    // region AE network

    @Override
    protected AENetworkProxy createProxy() {
        // Called from the AENetworkTile constructor, i.e. before the world position exists: only use static data.
        Item coreItem = Item.getItemFromBlock(MTWirelessNetworkCoreBlock.INSTANCE);
        ItemStack visual = coreItem == null ? null : new ItemStack(coreItem, 1, 0);

        AENetworkProxy proxy = new AENetworkProxy(this, PROXY_NAME, visual, true);
        proxy.setFlags(GridFlags.DENSE_CAPACITY);
        proxy.setIdlePowerUsage(0.0D);
        proxy.setValidSides(EnumSet.of(getConnectionSide()));
        return proxy;
    }

    @Override
    public void onReady() {
        // The metadata is authoritative once the tile entity is in the world. Apply it before AE2 builds the grid node,
        // so the proxy never advertises a wrong face even for a single tick.
        if (worldObj != null && !worldObj.isRemote) {
            adoptNetworkFromSavedData();
            getProxy().setValidSides(EnumSet.of(getConnectionSide()));
        }
        super.onReady();
        if (worldObj == null || worldObj.isRemote) return;

        refreshConnectionSide();
        // Registers the network's core slot (when this core still owns one) and asks for a reconnect of its members.
        // An unbound core is a valid state: onCoreReady leaves it alone until a network is picked in the GUI.
        MTWirelessNetworkRuntime.onCoreReady(this);
    }

    /**
     * Recovers the network id by position when the tile entity tag lost it (a broken tile entity tag, a world-editor
     * paste, ...). Only a network whose core slot still points at this position may be reclaimed: in v2 an unbound core
     * is a normal state, so no id is ever generated here.
     */
    private void adoptNetworkFromSavedData() {
        if (this.networkId != null) return;

        MTWirelessSavedData data = getSavedData();
        if (data == null) return;

        UUID claimed = data.findCoreNetwork(worldObj.provider.dimensionId, xCoord, yCoord, zCoord);
        if (claimed != null) setNetworkId(claimed);
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return dir == getConnectionSide() ? getProxy().getNode() : null;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return dir == getConnectionSide() ? AECableType.SMART : AECableType.NONE;
    }

    /**
     * The core's own node is the uplink of its whole wireless network, so it carries the ultra dense capacity (2.1G =
     * {@link Integer#MAX_VALUE} channels): AE2's native dense cable value of 32 would otherwise bottleneck every ME
     * network that runs through a wireless core. Resolved by the {@code MixinGridNode_ChannelCapacity} mixin.
     */
    @Override
    public int mtChannelCapacity() {
        return Integer.MAX_VALUE;
    }

    // endregion

    // region Core identity

    /** @return the id of the network this core is bound to, or {@code null} while it has none. */
    public UUID getNetworkId() {
        return this.networkId;
    }

    /**
     * Stores the network id in the tile entity NBT only. The {@code WorldSavedData} side (core slot, reconnects) is
     * owned by {@link MTWirelessNetworkRuntime} and is never touched from here.
     */
    public void setNetworkId(UUID id) {
        if (Objects.equals(this.networkId, id)) return;
        this.networkId = id;
        markDirty();
    }

    /** @return whether this core belongs to a network; an unbound core is functional and can open its GUI. */
    public boolean isNetworkBound() {
        return this.networkId != null;
    }

    /** @return the display name of the bound network ({@code ""} for an unbound core). */
    public String getNetworkName() {
        UUID id = getNetworkId();
        if (id == null) return "";

        MTWirelessSavedData data = getSavedData();
        String name = data == null ? null : data.getName(id);
        if (name == null || name.trim()
            .isEmpty()) {
            return getShortNetworkId();
        }
        return name;
    }

    /**
     * @return the first eight characters of the bound network id, the fallback display name ({@code ""} if unbound).
     */
    public String getShortNetworkId() {
        UUID id = getNetworkId();
        if (id == null) return "";

        String text = id.toString();
        return text.length() <= SHORT_NETWORK_ID_LENGTH ? text : text.substring(0, SHORT_NETWORK_ID_LENGTH);
    }

    /** @return the color of the bound network, or {@link MTWirelessColors#DEFAULT} for an unbound core. */
    public int getNetworkColor() {
        UUID id = getNetworkId();
        if (id == null) return MTWirelessColors.DEFAULT;

        MTWirelessSavedData data = getSavedData();
        return data == null ? MTWirelessColors.DEFAULT : data.getColor(id);
    }

    /** @return the access level of the bound network, or {@link MTWirelessAccess#PRIVATE} for an unbound core. */
    public MTWirelessAccess getNetworkAccess() {
        UUID id = getNetworkId();
        if (id == null) return MTWirelessAccess.PRIVATE;

        MTWirelessSavedData data = getSavedData();
        return data == null ? MTWirelessAccess.PRIVATE : data.getAccess(id);
    }

    /** @return whether this core's grid node currently sits on an ME network. */
    public boolean isLinkedToAeNetwork() {
        IGridNode node = getProxy().getNode();
        return node != null && node.getGrid() != null;
    }

    /**
     * @return whether {@code player} may open this core's GUI and configure it. A bound core follows the network's
     *         access
     *         level. An unbound core has no network record to check against (v2 networks are separate objects), so it
     *         is
     *         limited to the player that placed it; cores placed by an older build or by a block placer have no
     *         recorded
     *         placer and stay open. Every network operation additionally checks the network's own access level.
     */
    public boolean canAccess(EntityPlayer player) {
        if (player == null) return false;

        UUID network = getNetworkId();
        if (network == null) {
            return this.placedBy == null || this.placedBy.equals(player.getUniqueID());
        }
        return MTWirelessNetworkRuntime.canAccess(player, network);
    }

    /**
     * Marks {@code player} as the owner of this core's AE2 node and remembers them as the placer (the only access rule
     * an unbound core has, see {@link #canAccess}). In v2 the owner of a network is fixed when the network is created,
     * so
     * placing a core must not steal an existing network: the owner is only written into a legacy record that has no
     * owner yet, and unbound cores have no record at all.
     */
    public void setOwner(EntityPlayer player) {
        if (player == null) return;

        getProxy().setOwner(player);
        // The placer is remembered even for an unbound core; this is what keeps a freshly placed core configurable by
        // its owner only (no network record exists yet to carry the owner).
        if (!Objects.equals(this.placedBy, player.getUniqueID())) {
            this.placedBy = player.getUniqueID();
            markDirty();
        }
        if (worldObj == null || worldObj.isRemote) return;

        UUID network = getNetworkId();
        if (network == null) return;

        MTWirelessSavedData data = getSavedData();
        if (data == null || data.getOwner(network) != null) return;
        data.setOwner(network, player.getUniqueID());
    }

    /** Claims an owner-less (legacy v1) network for {@code player}. @return whether the owner was migrated. */
    public boolean migrateLegacyOwner(EntityPlayer player) {
        if (player == null || worldObj == null || worldObj.isRemote) return false;

        UUID network = getNetworkId();
        if (network == null) return false;

        MTWirelessSavedData data = getSavedData();
        if (data == null || data.getOwner(network) != null) return false;

        setOwner(player);
        return true;
    }

    /**
     * Called by the block when the core is broken: detaches the core from its network. The network record itself, its
     * name/color/access level and its member bindings survive (see {@link MTWirelessNetworkRuntime#detachCore}); the
     * drop stack remembers the network so a replaced core re-attaches to it.
     */
    public void releaseOnBreak() {
        if (worldObj == null || worldObj.isRemote) return;
        MTWirelessNetworkRuntime.detachCore(this);
    }

    /**
     * @return the item form of this core with the bound network remembered in its NBT ({@code WirelessNetworkId},
     *         {@code WirelessNetworkName}, {@code WirelessNetworkColor}, {@code WirelessNetworkAccess}), or a plain
     *         item
     *         stack for an unbound core.
     */
    public ItemStack createDropStack() {
        Item coreItem = Item.getItemFromBlock(MTWirelessNetworkCoreBlock.INSTANCE);
        if (coreItem == null) return null;

        ItemStack stack = new ItemStack(coreItem, 1, 0);
        UUID id = getNetworkId();
        if (id == null) return stack;

        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(MTWirelessNetworkCoreItemBlock.NBT_NETWORK, id.toString());
        tag.setString(MTWirelessNetworkCoreItemBlock.NBT_NETWORK_NAME, getNetworkName());
        tag.setInteger(MTWirelessNetworkCoreItemBlock.NBT_NETWORK_COLOR, getNetworkColor());
        tag.setByte(MTWirelessNetworkCoreItemBlock.NBT_NETWORK_ACCESS, (byte) getNetworkAccess().id());
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * Re-attaches this freshly placed core to the network remembered by the item it was placed from. Called from the
     * block's {@code onBlockPlacedBy} on the server; the core stays unbound (with a chat hint) when the network is
     * gone,
     * the player has no access to it, or another core already serves it.
     */
    public void adoptFromItem(EntityPlayer player, ItemStack stack) {
        if (player == null || worldObj == null || worldObj.isRemote) return;

        UUID network = MTWirelessNetworkCoreItemBlock.getStoredNetworkId(stack);
        if (network == null) return;

        switch (MTWirelessNetworkRuntime.selectNetworkForCore(player, this, network)) {
            case OK -> GTUtility.sendChatTrans(player, "wirelessme.chat.network_selected", getNetworkName());
            case NO_ACCESS -> GTUtility.sendChatTrans(player, "wirelessme.chat.no_access");
            case NETWORK_NOT_FOUND -> GTUtility.sendChatTrans(player, "wirelessme.chat.network_missing");
            case CORE_IN_USE -> GTUtility.sendChatTrans(player, "wirelessme.chat.core_in_use");
            case INVALID -> {}
        }
    }

    private MTWirelessSavedData getSavedData() {
        // The saved data is authoritative on the logical server only; never read it from the client thread.
        if (worldObj != null && worldObj.isRemote) return null;
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? null : MTWirelessSavedData.get(server);
    }

    // endregion

    // region Connection side

    /** @return the face that exposes the AE2 node, stored as the block metadata ordinal (0 = DOWN .. 5 = EAST). */
    public ForgeDirection getConnectionSide() {
        World world = worldObj;
        if (world == null) return ForgeDirection.DOWN;

        int meta = world.getBlockMetadata(xCoord, yCoord, zCoord);
        if (meta < 0 || meta >= ForgeDirection.VALID_DIRECTIONS.length) return ForgeDirection.DOWN;
        return ForgeDirection.getOrientation(meta);
    }

    public void setConnectionSide(ForgeDirection side) {
        if (side == null || worldObj == null) return;

        int meta = side.ordinal();
        if (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != meta) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 3);
        }
        refreshConnectionSide();
    }

    /** Re-applies the connectable side to the proxy/node and notifies neighbours and the client renderer. */
    public void refreshConnectionSide() {
        if (worldObj == null) return;

        ForgeDirection side = getConnectionSide();
        AENetworkProxy proxy = getProxy();
        proxy.setValidSides(EnumSet.of(side));

        IGridNode node = proxy.getNode();
        if (node != null) node.updateState();

        worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    // endregion

    // region Persistence

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_MTWireless(NBTTagCompound data) {
        this.networkId = null;
        if (data.hasKey(NBT_NETWORK_MOST) && data.hasKey(NBT_NETWORK_LEAST)) {
            this.networkId = new UUID(data.getLong(NBT_NETWORK_MOST), data.getLong(NBT_NETWORK_LEAST));
        } else if (data.hasKey(NBT_NETWORK)) {
            try {
                this.networkId = UUID.fromString(data.getString(NBT_NETWORK));
            } catch (IllegalArgumentException ignored) {
                this.networkId = null;
            }
        }

        this.placedBy = null;
        if (data.hasKey(NBT_PLACED_BY)) {
            try {
                this.placedBy = UUID.fromString(data.getString(NBT_PLACED_BY));
            } catch (IllegalArgumentException ignored) {
                this.placedBy = null;
            }
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_MTWireless(NBTTagCompound data) {
        if (this.placedBy != null) data.setString(NBT_PLACED_BY, this.placedBy.toString());

        UUID id = this.networkId;
        // An unbound core writes no id: generating one here would turn every chunk save into a fresh, orphan network.
        if (id == null) return;

        data.setLong(NBT_NETWORK_MOST, id.getMostSignificantBits());
        data.setLong(NBT_NETWORK_LEAST, id.getLeastSignificantBits());
        data.setString(NBT_NETWORK, id.toString());
    }

    // endregion

    // region GUI

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return MTWirelessCoreGui.build(this, data, syncManager, settings);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return MTWirelessCoreGui.createScreen(data, mainPanel);
    }

    // endregion
}
