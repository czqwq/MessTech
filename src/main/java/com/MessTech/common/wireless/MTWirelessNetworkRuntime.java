package com.MessTech.common.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.MessTech.init.Config;

import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/**
 * Server side driver of the wireless ME network: binds ME hatches (members) to a {@link MTWirelessNetworkCoreTile} and
 * keeps one logical AE2 grid connection per member alive.
 *
 * <p>
 * The connection is created with {@code AEApi.instance().createGridConnection(coreNode, targetNode)} — the same API
 * AE2's own wireless connector uses — so it works across any distance and any dimension. AE2 destroys the connection
 * itself when one of its endpoints unloads or is removed, therefore every cached connection is treated as possibly
 * dead: it is only destroyed when it is provably still alive, otherwise it is just dropped from the cache. All AE2
 * work runs on the server thread, driven by the 20-tick maintenance tick; nothing ever forces a chunk to load.
 *
 * <p>
 * Since v2 the networks are persistent archive objects of their own ({@link MTWirelessSavedData}): the core binding is
 * just one slot of a record, so breaking the core keeps the record and every hatch binding (the members simply wait for
 * a core to come back), and only the owner's explicit delete ({@link #deleteNetwork}) removes a network. The runtime
 * owns the policy layer on top of the saved data: access level checks ({@link #canAccess}/{@link #canEdit}), the
 * per-player creation limit ({@link #getNetworkLimit}) and the create/delete/select/detach core actions used by the
 * GUI.
 */
public final class MTWirelessNetworkRuntime {

    /** Result of one immediate connection attempt. */
    public enum ConnectionResult {
        CONNECTED,
        ALREADY_CONNECTED,
        CORE_MISSING,
        CORE_NOT_CONNECTED,
        TARGET_MISSING,
        TARGET_WIRED,
        FAILED
    }

    /** Result of a bind/unbind request coming from the binding tool or a GUI. */
    public enum BindResult {
        OK,
        OK_PENDING,
        ALREADY_BOUND,
        ALREADY_CONNECTED,
        WIRED,
        NO_ACCESS,
        /** The network exists but has no online core right now (a network record may legally be core-less). */
        NO_CORE,
        CORE_NOT_CONNECTED,
        INVALID_TARGET
    }

    /** Result of binding (or unbinding) a core to a network from the core GUI. */
    public enum SelectResult {
        OK,
        NO_ACCESS,
        NETWORK_NOT_FOUND,
        /** Another core already serves that network: one network has exactly one core. */
        CORE_IN_USE,
        INVALID
    }

    private static final Logger LOG = LogManager.getLogger("MessTech");

    private static final int MAINTENANCE_INTERVAL_TICKS = 20;
    private static final int RETRY_BACKOFF_TICKS = 100;
    private static final int MAX_Y = 255;

    /** Member -> the wireless grid connection created for it. Authoritative only while the connection is alive. */
    private static final Map<MTWirelessMemberKey, TrackedConnection> CONNECTIONS = new HashMap<>();
    /** Member -> tick after which the next connection attempt is allowed (creation failures / foreign wires). */
    private static final Map<MTWirelessMemberKey, Integer> RETRY_AT_TICK = new HashMap<>();
    /** Networks that asked for a full reconnect on the next maintenance tick. */
    private static final Set<UUID> RECONNECT_REQUESTS = new LinkedHashSet<>();

    private static boolean initialized;
    private static int tickCounter;
    private static boolean maintenanceDue;

    private MTWirelessNetworkRuntime() {}

    // region Lifecycle

    /** Registers the server tick and the server-stop cleanup. Idempotent; called from the mod's preInit. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        MTWirelessNetworkRuntime handler = new MTWirelessNetworkRuntime();
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
        MinecraftForge.EVENT_BUS.register(handler);
    }

    /**
     * Drops every server-scoped cache. The AE2 connections themselves are destroyed by AE2 while the worlds unload, so
     * they must not be destroyed here — doing so could tear down grids in the middle of world shutdown.
     */
    public static void onServerStopped() {
        CONNECTIONS.clear();
        RETRY_AT_TICK.clear();
        RECONNECT_REQUESTS.clear();
        maintenanceDue = false;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            if (!CONNECTIONS.isEmpty() || !RETRY_AT_TICK.isEmpty() || !RECONNECT_REQUESTS.isEmpty()) onServerStopped();
            return;
        }

        tickCounter++;
        if (!maintenanceDue && tickCounter % MAINTENANCE_INTERVAL_TICKS != 0) return;
        maintenanceDue = false;

        try {
            maintain(server);
        } catch (RuntimeException error) {
            LOG.warn("Wireless ME network maintenance failed", error);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.world;
        if (world != null && world.provider != null && world.provider.dimensionId == 0) onServerStopped();
    }

    // endregion

    // region Lookup / access

    /** @return whether the block at that position is an ME hatch that can be bound to a wireless network. */
    public static boolean isBindableTarget(World world, int x, int y, int z) {
        if (world == null || y < 0 || y > MAX_Y) return false;
        return isBindableTile(world.getTileEntity(x, y, z));
    }

    /** @return the member key of a bindable target, or {@code null} when the block is not a bindable ME hatch. */
    public static MTWirelessMemberKey resolveTarget(World world, int x, int y, int z) {
        if (!isBindableTarget(world, x, y, z)) return null;
        return new MTWirelessMemberKey(world.provider.dimensionId, x, y, z);
    }

    /**
     * @return whether {@code player} may use the network under its access level: the owner always may,
     *         {@link MTWirelessAccess#TEAM} adds ServerUtilities teammates and {@link MTWirelessAccess#PUBLIC} adds
     *         everyone else.
     */
    public static boolean canAccess(EntityPlayer player, UUID frequency) {
        if (player == null || frequency == null) return false;
        MinecraftServer server = getServer();
        if (server == null) return false;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null || !data.isNetwork(frequency)) return false;

        UUID playerId = player.getUniqueID();
        UUID owner = data.getOwner(frequency);
        if (owner != null && owner.equals(playerId)) return true;

        return switch (data.getAccess(frequency)) {
            case PUBLIC -> true;
            case TEAM -> owner != null && MTWirelessTeamAccess.arePlayersInSameTeam(owner, playerId);
            case PRIVATE -> false;
        };
    }

    /** @return whether {@code player} may edit the network (rename, recolor, change access, delete): only its owner. */
    public static boolean canEdit(EntityPlayer player, UUID frequency) {
        if (player == null || frequency == null) return false;
        MinecraftServer server = getServer();
        if (server == null) return false;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null || !data.isNetwork(frequency)) return false;

        UUID owner = data.getOwner(frequency);
        return owner != null && owner.equals(player.getUniqueID());
    }

    /** @return all networks {@code player} may use, sorted by display name (empty when there is no server). */
    public static List<MTWirelessSavedData.NetworkInfo> getAccessibleNetworks(EntityPlayer player) {
        List<MTWirelessSavedData.NetworkInfo> accessible = new ArrayList<>();
        if (player == null) return accessible;

        MinecraftServer server = getServer();
        if (server == null) return accessible;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return accessible;

        for (MTWirelessSavedData.NetworkInfo info : data.getNetworks()) {
            if (canAccess(player, info.frequency)) accessible.add(info);
        }
        return accessible;
    }

    /** @return the networks {@code player} created and may therefore edit, sorted by display name. */
    public static List<MTWirelessSavedData.NetworkInfo> getEditableNetworks(EntityPlayer player) {
        List<MTWirelessSavedData.NetworkInfo> editable = new ArrayList<>();
        if (player == null) return editable;

        MinecraftServer server = getServer();
        if (server == null) return editable;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return editable;

        UUID playerId = player.getUniqueID();
        for (MTWirelessSavedData.NetworkInfo info : data.getNetworks()) {
            if (playerId.equals(info.owner)) editable.add(info);
        }
        return editable;
    }

    /**
     * @return how many networks {@code player} may create, or {@code -1} for "no limit"
     *         ({@code Config.wireless.NETWORK_LIMIT_PER_PLAYER}). Reaching the limit never stops the player from using
     *         networks that were shared with them.
     */
    public static int getNetworkLimit(EntityPlayer player) {
        return Config.wireless.NETWORK_LIMIT_PER_PLAYER;
    }

    /** @return whether {@code player} is still below the per-player creation limit. */
    public static boolean canCreateNetwork(EntityPlayer player) {
        if (player == null) return false;

        int limit = getNetworkLimit(player);
        if (limit < 0) return true;

        MinecraftServer server = getServer();
        if (server == null) return false;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        return data != null && data.countOwnedNetworks(player.getUniqueID()) < limit;
    }

    /**
     * Creates a new network for {@code player}.
     *
     * @return the new network id, or {@code null} when the name is empty, the player reached the creation limit or
     *         there is no server side saved data yet
     */
    public static UUID createNetwork(EntityPlayer player, String name, int color, MTWirelessAccess access) {
        if (player == null) return null;

        String sanitized = name == null ? "" : name.trim();
        if (sanitized.isEmpty()) return null;
        if (!canCreateNetwork(player)) return null;

        MinecraftServer server = getServer();
        if (server == null) return null;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return null;

        return data.createNetwork(player.getUniqueID(), sanitized, color, access);
    }

    /**
     * Deletes a network and everything attached to it: its hatch bindings, its AE2 links and the core's binding to it.
     * Only the owner may do this; the network record, its name, color and access level are removed for good.
     *
     * @return whether the network was deleted
     */
    public static boolean deleteNetwork(EntityPlayer player, UUID frequency) {
        if (player == null || frequency == null) return false;

        MinecraftServer server = getServer();
        if (server == null) return false;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null || !data.isNetwork(frequency)) return false;
        if (!canEdit(player, frequency)) return false;

        MTWirelessNetworkCoreTile core = findLoadedCore(server, frequency);
        List<MTWirelessMemberKey> members = data.getMembers(frequency);

        data.deleteNetwork(frequency);
        RECONNECT_REQUESTS.remove(frequency);
        dropTrackedConnections(frequency, true);
        for (MTWirelessMemberKey member : members) {
            RETRY_AT_TICK.remove(member);
        }

        if (core != null && frequency.equals(core.getNetworkId())) core.setNetworkId(null);
        return true;
    }

    /** @return the network the member is bound to, whether or not the connection is up right now. */
    public static UUID getMemberNetwork(MinecraftServer server, MTWirelessMemberKey member) {
        if (server == null || member == null) return null;
        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        return data == null ? null : data.getMemberNetwork(member);
    }

    /** @return the network the member is currently connected to, or {@code null} when it is not connected. */
    public static UUID findConnectedNetwork(MinecraftServer server, MTWirelessMemberKey member) {
        if (server == null || member == null) return null;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        UUID bound = data == null ? null : data.getMemberNetwork(member);
        if (bound != null && isMemberConnected(server, bound, member)) return bound;

        TrackedConnection tracked = CONNECTIONS.get(member);
        if (tracked != null && isConnectionAlive(tracked.connection)) return tracked.frequency;
        return null;
    }

    public static boolean isMemberConnected(MinecraftServer server, UUID frequency, MTWirelessMemberKey member) {
        if (server == null || frequency == null || member == null) return false;

        TrackedConnection tracked = CONNECTIONS.get(member);
        if (tracked != null && frequency.equals(tracked.frequency) && isConnectionAlive(tracked.connection))
            return true;

        MTWirelessNetworkCoreTile core = findLoadedCore(server, frequency);
        if (core == null) return false;

        IGridNode coreNode = getCoreNode(core);
        IGridNode targetNode = findTargetNode(server, member);
        if (coreNode == null || targetNode == null) return false;
        return isSameGrid(coreNode, targetNode) || areDirectlyConnected(coreNode, targetNode);
    }

    /** @return whether the member has a wired/shared connection that the wireless network must not replace. */
    public static boolean isWired(MinecraftServer server, MTWirelessMemberKey member) {
        IGridNode targetNode = findTargetNode(server, member);
        if (targetNode == null) return false;

        try {
            for (IGridConnection connection : targetNode.getConnections()) {
                if (!isTrackedConnection(connection)) return true;
            }
        } catch (RuntimeException ignored) {
            // A hatch that is being torn down can throw here; treat it as "no information".
        }
        return false;
    }

    // endregion

    // region Core binding (v2)

    /**
     * Binds a core to a network, or unbinds it when {@code frequency} is {@code null}.
     *
     * <p>
     * The network record must exist, the player must be allowed to use it and its {@code core} slot must be empty or
     * already this core (one network has exactly one core). The core's previous network, if any, keeps its record and
     * its hatch bindings — only its core slot is freed.
     *
     * @return {@link SelectResult#OK} when the binding changed (or was confirmed), otherwise the reason it did not
     */
    public static SelectResult selectNetworkForCore(EntityPlayer player, MTWirelessNetworkCoreTile core,
        UUID frequency) {
        if (player == null || core == null) return SelectResult.INVALID;
        World coreWorld = core.getWorldObj();
        if (coreWorld == null || coreWorld.isRemote) return SelectResult.INVALID;

        MinecraftServer server = getServer();
        if (server == null) return SelectResult.INVALID;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return SelectResult.INVALID;

        int dim = coreWorld.provider.dimensionId;
        int x = core.xCoord;
        int y = core.yCoord;
        int z = core.zCoord;

        if (frequency == null) {
            // The GUI's "detach" action: nothing to do for an already unbound core.
            UUID bound = core.getNetworkId();
            if (bound != null && !canAccess(player, bound)) return SelectResult.NO_ACCESS;
            detachCore(core);
            return SelectResult.OK;
        }

        if (!canAccess(player, frequency)) return SelectResult.NO_ACCESS;
        if (!data.isNetwork(frequency)) return SelectResult.NETWORK_NOT_FOUND;

        int[] currentCore = data.getCore(frequency);
        if (currentCore != null && !isSamePosition(currentCore, dim, x, y, z)) return SelectResult.CORE_IN_USE;

        // Free the core slot of the core's previous network (its record and hatch bindings stay).
        UUID previous = core.getNetworkId();
        if (previous != null && !previous.equals(frequency)) {
            int[] previousCore = data.getCore(previous);
            if (previousCore != null && isSamePosition(previousCore, dim, x, y, z)) data.clearCore(previous);
            disconnectFrequency(server, previous);
        }
        // A stale record may still claim this position (e.g. the tile entity NBT was reset): free that slot as well.
        UUID stale = data.findCoreNetwork(dim, x, y, z);
        if (stale != null && !stale.equals(frequency)) {
            data.clearCore(stale);
            disconnectFrequency(server, stale);
        }

        data.setCore(frequency, dim, x, y, z);
        core.setNetworkId(frequency);
        requestReconnect(server, frequency);
        return SelectResult.OK;
    }

    /**
     * Unbinds the core from its network when the core block is broken: clears the record's {@code core} slot and tears
     * the network's links down, but keeps the record, its settings and every hatch binding so the network survives and
     * can be continued by placing the core again. No permission check: breaking a block is not a GUI action.
     */
    public static void detachCore(MTWirelessNetworkCoreTile core) {
        if (core == null) return;
        World coreWorld = core.getWorldObj();
        if (coreWorld == null || coreWorld.isRemote) return;

        MinecraftServer server = getServer();
        if (server == null) return;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        int dim = coreWorld.provider.dimensionId;
        int x = core.xCoord;
        int y = core.yCoord;
        int z = core.zCoord;

        UUID frequency = core.getNetworkId();
        if (frequency == null && data != null) frequency = data.findCoreNetwork(dim, x, y, z);
        if (frequency != null && data != null) {
            int[] corePos = data.getCore(frequency);
            if (corePos != null && isSamePosition(corePos, dim, x, y, z)) data.clearCore(frequency);
            disconnectFrequency(server, frequency);
        }
        core.setNetworkId(null);
    }

    /**
     * Called from the core's {@code onReady}: (re-)claims the network whose record points at this core position and
     * schedules the hatch links to be rebuilt. A core without a network is a legal state and is left alone; if another
     * core already serves the remembered network, this core becomes unbound.
     */
    public static void onCoreReady(MTWirelessNetworkCoreTile core) {
        if (core == null) return;
        World coreWorld = core.getWorldObj();
        if (coreWorld == null || coreWorld.isRemote) return;

        MinecraftServer server = getServer();
        if (server == null) return;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return;

        int dim = coreWorld.provider.dimensionId;
        int x = core.xCoord;
        int y = core.yCoord;
        int z = core.zCoord;

        UUID frequency = core.getNetworkId();
        if (frequency == null) {
            // Unbound cores are allowed; only adopt a record that still claims this exact position.
            UUID claimed = data.findCoreNetwork(dim, x, y, z);
            if (claimed == null) return;
            core.setNetworkId(claimed);
            frequency = claimed;
        }

        if (!data.isNetwork(frequency)) {
            UUID claimed = data.findCoreNetwork(dim, x, y, z);
            if (claimed == null) {
                // The network was deleted while the core was unloaded: the core stays, unbound.
                core.setNetworkId(null);
                return;
            }
            core.setNetworkId(claimed);
            frequency = claimed;
        }

        int[] corePos = data.getCore(frequency);
        if (corePos == null) {
            data.setCore(frequency, dim, x, y, z);
        } else if (!isSamePosition(corePos, dim, x, y, z)) {
            // Another core already serves this network: this duplicate must not steal it.
            core.setNetworkId(null);
            return;
        }
        requestReconnect(server, frequency);
    }

    // endregion

    // region Connect / disconnect

    /** Tries to connect the member to the network right now. */
    public static ConnectionResult connectMemberNow(MinecraftServer server, UUID frequency,
        MTWirelessMemberKey member) {
        if (server == null || frequency == null || member == null) return ConnectionResult.FAILED;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null || !data.isNetwork(frequency) || data.getCore(frequency) == null) {
            dropTrackedConnections(frequency, true);
            return ConnectionResult.CORE_MISSING;
        }

        MTWirelessNetworkCoreTile core = findLoadedCore(server, frequency);
        if (core == null) {
            dropTrackedConnections(frequency, true);
            return ConnectionResult.CORE_MISSING;
        }

        IGridNode coreNode = getCoreNode(core);
        if (coreNode == null || coreNode.getGrid() == null
            || coreNode.getConnections()
                .isEmpty()) {
            dropTrackedConnections(frequency, true);
            return ConnectionResult.CORE_NOT_CONNECTED;
        }

        World targetWorld = server.worldServerForDimension(member.dim);
        if (!isChunkLoaded(targetWorld, member.x, member.y, member.z)) {
            dropConnection(member, true);
            return ConnectionResult.TARGET_MISSING;
        }

        TileEntity targetTile = targetWorld.getTileEntity(member.x, member.y, member.z);
        if (!isBindableTile(targetTile)) {
            dropConnection(member, true);
            return ConnectionResult.TARGET_MISSING;
        }

        IGridNode targetNode = getGridNode(targetTile);
        if (targetNode == null) {
            // The hatch's AE2 node is not built yet (e.g. a freshly loaded chunk); retry on a later tick.
            RETRY_AT_TICK.put(member, tickCounter + RETRY_BACKOFF_TICKS);
            return ConnectionResult.TARGET_MISSING;
        }

        TrackedConnection tracked = CONNECTIONS.get(member);
        if (tracked != null && frequency.equals(tracked.frequency)
            && isConnectionAlive(tracked.connection)
            && connectionConnects(tracked.connection, coreNode, targetNode)) {
            RETRY_AT_TICK.remove(member);
            return ConnectionResult.ALREADY_CONNECTED;
        }
        if (tracked != null) dropConnection(member, true);

        if (isSameGrid(coreNode, targetNode)) {
            RETRY_AT_TICK.remove(member);
            return ConnectionResult.ALREADY_CONNECTED;
        }

        if (hasForeignConnection(targetNode, coreNode)) {
            RETRY_AT_TICK.put(member, tickCounter + RETRY_BACKOFF_TICKS);
            return ConnectionResult.TARGET_WIRED;
        }

        try {
            IGridConnection connection = AEApi.instance()
                .createGridConnection(coreNode, targetNode);
            CONNECTIONS.put(member, new TrackedConnection(frequency, connection));
            RETRY_AT_TICK.remove(member);
            return ConnectionResult.CONNECTED;
        } catch (FailedConnection | RuntimeException error) {
            RETRY_AT_TICK.put(member, tickCounter + RETRY_BACKOFF_TICKS);
            return ConnectionResult.FAILED;
        }
    }

    /** Removes every binding of the member and tears its wireless connection down. */
    public static void disconnectMember(MinecraftServer server, MTWirelessMemberKey member) {
        if (member == null) return;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data != null) data.removeMember(member);

        dropConnection(member, true);
        RETRY_AT_TICK.remove(member);
    }

    /** {@link #disconnectMember(MinecraftServer, MTWirelessMemberKey)} by block position. */
    public static void disconnectMembersAt(MinecraftServer server, int dim, int x, int y, int z) {
        MTWirelessMemberKey member = new MTWirelessMemberKey(dim, x, y, z);

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data != null) data.removeMembersAt(dim, x, y, z);

        dropConnection(member, true);
        RETRY_AT_TICK.remove(member);
    }

    /** Tears down the wireless connections of one network; the persisted bindings stay in the saved data. */
    public static void disconnectFrequency(MinecraftServer server, UUID frequency) {
        if (frequency == null) return;
        RECONNECT_REQUESTS.remove(frequency);
        dropTrackedConnections(frequency, true);
    }

    /** Schedules a full reconnect of the network on the next tick. */
    public static void requestReconnect(MinecraftServer server, UUID frequency) {
        if (frequency == null) return;
        RECONNECT_REQUESTS.add(frequency);
        maintenanceDue = true;
    }

    // endregion

    // region Bind / unbind

    /** Binds (or re-binds, when {@code replace}) the target to the network. Called by the binding tool. */
    public static BindResult bindTarget(EntityPlayer player, MTWirelessMemberKey target, UUID frequency,
        boolean replace) {
        if (player == null || target == null || frequency == null) return BindResult.INVALID_TARGET;

        MinecraftServer server = getServer();
        if (server == null) return BindResult.NO_CORE;

        if (!canAccess(player, frequency)) return BindResult.NO_ACCESS;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        // v2: the network exists independently of its core; NO_CORE means "no online core right now".
        if (data == null || !data.isNetwork(frequency) || data.getCore(frequency) == null) return BindResult.NO_CORE;

        MTWirelessNetworkCoreTile core = findLoadedCore(server, frequency);
        if (core == null) return BindResult.NO_CORE;

        IGridNode coreNode = getCoreNode(core);
        if (coreNode == null || coreNode.getGrid() == null
            || coreNode.getConnections()
                .isEmpty()) {
            return BindResult.CORE_NOT_CONNECTED;
        }

        World targetWorld = server.worldServerForDimension(target.dim);
        if (!isChunkLoaded(targetWorld, target.x, target.y, target.z)) return BindResult.INVALID_TARGET;
        TileEntity targetTile = targetWorld.getTileEntity(target.x, target.y, target.z);
        if (!isBindableTile(targetTile)) return BindResult.INVALID_TARGET;

        IGridNode targetNode = getGridNode(targetTile);
        UUID currentNetwork = data.getMemberNetwork(target);

        if (targetNode != null && hasForeignConnection(targetNode, coreNode)) return BindResult.WIRED;
        if (!replace && currentNetwork != null && !currentNetwork.equals(frequency)) return BindResult.ALREADY_BOUND;

        if (currentNetwork != null && !currentNetwork.equals(frequency)) {
            // Moving the member away from another network's binding needs access to that network as well.
            if (!canAccess(player, currentNetwork)) return BindResult.NO_ACCESS;
            // replace == true: the member moves to the new network.
            data.removeMember(target);
            dropConnection(target, true);
            RETRY_AT_TICK.remove(target);
        }

        if (targetNode == null) {
            // The hatch's AE2 node is not ready yet; persist the binding and let maintenance connect it.
            data.addMember(frequency, target);
            requestReconnect(server, frequency);
            return BindResult.OK_PENDING;
        }

        if (isSameGrid(coreNode, targetNode)) {
            data.addMember(frequency, target);
            return BindResult.ALREADY_CONNECTED;
        }

        data.addMember(frequency, target);
        ConnectionResult result = connectMemberNow(server, frequency, target);
        if (result == ConnectionResult.CONNECTED || result == ConnectionResult.ALREADY_CONNECTED) {
            return BindResult.OK;
        }
        return BindResult.OK_PENDING;
    }

    /** Removes the member's binding from whatever network it belongs to. */
    public static BindResult unbindTarget(EntityPlayer player, MTWirelessMemberKey target) {
        if (player == null || target == null) return BindResult.INVALID_TARGET;

        MinecraftServer server = getServer();
        if (server == null) return BindResult.INVALID_TARGET;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        UUID bound = data == null ? null : data.getMemberNetwork(target);
        if (bound == null) {
            dropConnection(target, true);
            RETRY_AT_TICK.remove(target);
            return BindResult.INVALID_TARGET;
        }
        if (!canAccess(player, bound)) return BindResult.NO_ACCESS;

        data.removeMember(target);
        dropConnection(target, true);
        RETRY_AT_TICK.remove(target);
        return BindResult.OK;
    }

    // endregion

    // region Maintenance

    private static void maintain(MinecraftServer server) {
        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return;

        Set<UUID> frequencies = new LinkedHashSet<>(data.getFrequencies());
        frequencies.addAll(trackedFrequencies());

        Set<UUID> requested = new LinkedHashSet<>(RECONNECT_REQUESTS);
        RECONNECT_REQUESTS.clear();
        for (UUID frequency : requested) {
            if (frequency != null) dropTrackedConnections(frequency, true);
        }

        for (UUID frequency : frequencies) {
            if (frequency == null) continue;
            if (!data.isNetwork(frequency)) {
                // The record is gone (network deleted): only the connections are left to clean up.
                dropTrackedConnections(frequency, true);
                continue;
            }

            int[] corePos = data.getCore(frequency);
            if (corePos == null) {
                // v2: a network without a core is a legal, persisted state. Keep the record and every hatch binding;
                // only drop the links, there is nothing to link to right now.
                dropTrackedConnections(frequency, true);
                continue;
            }

            World coreWorld = server.worldServerForDimension(corePos[0]);
            if (!isChunkLoaded(coreWorld, corePos[1], corePos[2], corePos[3])) {
                // Core chunk not loaded: keep the bindings and the record, only forget links AE2 already destroyed.
                dropDeadTrackedConnections(frequency);
                continue;
            }

            TileEntity coreTile = coreWorld.getTileEntity(corePos[1], corePos[2], corePos[3]);
            if (!(coreTile instanceof MTWirelessNetworkCoreTile core) || !frequency.equals(core.getNetworkId())) {
                // The recorded core is gone or no longer claims this network: free the slot, keep the network itself.
                data.clearCore(frequency);
                dropTrackedConnections(frequency, true);
                continue;
            }

            IGridNode coreNode = getCoreNode(core);
            if (coreNode == null || coreNode.getGrid() == null) {
                dropDeadTrackedConnections(frequency);
                continue;
            }

            maintainMembers(server, data, frequency, coreNode);
        }

        cleanupOrphanConnections(data);
    }

    private static void maintainMembers(MinecraftServer server, MTWirelessSavedData data, UUID frequency,
        IGridNode coreNode) {
        for (MTWirelessMemberKey member : data.getMembers(frequency)) {
            World targetWorld = server.worldServerForDimension(member.dim);
            if (!isChunkLoaded(targetWorld, member.x, member.y, member.z)) {
                // Chunk not loaded: keep the binding and reconnect when it comes back.
                continue;
            }

            TileEntity targetTile = targetWorld.getTileEntity(member.x, member.y, member.z);
            if (!isBindableTile(targetTile)) {
                // The ME hatch is gone (broken / replaced by another block): drop the binding.
                data.removeMember(member);
                dropConnection(member, true);
                RETRY_AT_TICK.remove(member);
                continue;
            }

            IGridNode targetNode = getGridNode(targetTile);
            if (targetNode == null) continue;

            TrackedConnection tracked = CONNECTIONS.get(member);
            if (tracked != null && !frequency.equals(tracked.frequency)) {
                dropConnection(member, true);
                tracked = null;
            }
            if (tracked != null) {
                if (!isConnectionAlive(tracked.connection)) {
                    CONNECTIONS.remove(member);
                } else if (connectionConnects(tracked.connection, coreNode, targetNode)) {
                    RETRY_AT_TICK.remove(member);
                    continue;
                } else {
                    // Both endpoints still exist but the link no longer joins them: drop it and reconnect.
                    destroyConnectionQuietly(tracked.connection);
                    CONNECTIONS.remove(member);
                }
            }

            if (isSameGrid(coreNode, targetNode)) {
                // Wired connection, or the grids are already merged: no additional wireless link is needed.
                RETRY_AT_TICK.remove(member);
                continue;
            }

            Integer retryAt = RETRY_AT_TICK.get(member);
            if (retryAt != null && tickCounter < retryAt) continue;

            if (hasForeignConnection(targetNode, coreNode)) {
                // The hatch has a wired/shared connection we must not cut; retry later in case it is removed.
                RETRY_AT_TICK.put(member, tickCounter + RETRY_BACKOFF_TICKS);
                continue;
            }

            try {
                IGridConnection connection = AEApi.instance()
                    .createGridConnection(coreNode, targetNode);
                CONNECTIONS.put(member, new TrackedConnection(frequency, connection));
                RETRY_AT_TICK.remove(member);
            } catch (FailedConnection | RuntimeException error) {
                RETRY_AT_TICK.put(member, tickCounter + RETRY_BACKOFF_TICKS);
            }
        }
    }

    private static void cleanupOrphanConnections(MTWirelessSavedData data) {
        List<MTWirelessMemberKey> orphaned = null;
        for (Map.Entry<MTWirelessMemberKey, TrackedConnection> entry : CONNECTIONS.entrySet()) {
            UUID bound = data.getMemberNetwork(entry.getKey());
            if (bound == null || !bound.equals(entry.getValue().frequency)) {
                if (orphaned == null) orphaned = new ArrayList<>();
                orphaned.add(entry.getKey());
            }
        }
        if (orphaned != null) {
            for (MTWirelessMemberKey member : orphaned) {
                dropConnection(member, true);
                RETRY_AT_TICK.remove(member);
            }
        }
        RETRY_AT_TICK.keySet()
            .removeIf(member -> data.getMemberNetwork(member) == null);
    }

    // endregion

    // region Connection cache helpers

    private static final class TrackedConnection {

        private final UUID frequency;
        private final IGridConnection connection;

        private TrackedConnection(UUID frequency, IGridConnection connection) {
            this.frequency = frequency;
            this.connection = connection;
        }
    }

    private static Set<UUID> trackedFrequencies() {
        Set<UUID> frequencies = new LinkedHashSet<>();
        for (TrackedConnection tracked : CONNECTIONS.values()) {
            if (tracked.frequency != null) frequencies.add(tracked.frequency);
        }
        return frequencies;
    }

    private static void dropConnection(MTWirelessMemberKey member, boolean destroyWhenAlive) {
        TrackedConnection tracked = CONNECTIONS.remove(member);
        if (tracked == null) return;
        if (destroyWhenAlive && isConnectionAlive(tracked.connection)) {
            destroyConnectionQuietly(tracked.connection);
        }
    }

    private static void dropTrackedConnections(UUID frequency, boolean destroyWhenAlive) {
        List<MTWirelessMemberKey> members = new ArrayList<>();
        for (Map.Entry<MTWirelessMemberKey, TrackedConnection> entry : CONNECTIONS.entrySet()) {
            if (frequency.equals(entry.getValue().frequency)) members.add(entry.getKey());
        }
        for (MTWirelessMemberKey member : members) {
            RETRY_AT_TICK.remove(member);
            dropConnection(member, destroyWhenAlive);
        }
    }

    /** Drops connections of the network that AE2 already destroyed (e.g. while its chunk was unloaded). */
    private static void dropDeadTrackedConnections(UUID frequency) {
        Iterator<Map.Entry<MTWirelessMemberKey, TrackedConnection>> iterator = CONNECTIONS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<MTWirelessMemberKey, TrackedConnection> entry = iterator.next();
            if (!frequency.equals(entry.getValue().frequency)) continue;
            if (!isConnectionAlive(entry.getValue().connection)) iterator.remove();
        }
    }

    /**
     * AE2 destroys the connections of a node whose chunk unloads or block is removed. A cached connection that is no
     * longer listed by both endpoints must never be destroyed again — just forgotten.
     */
    private static boolean isConnectionAlive(IGridConnection connection) {
        if (connection == null) return false;
        try {
            IGridNode first = connection.a();
            IGridNode second = connection.b();
            if (first == null || second == null) return false;
            if (first.getGrid() == null || second.getGrid() == null) return false;
            return first.getConnections()
                .contains(connection)
                && second.getConnections()
                    .contains(connection);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void destroyConnectionQuietly(IGridConnection connection) {
        if (connection == null) return;
        try {
            connection.destroy();
        } catch (RuntimeException ignored) {
            // AE2 may have destroyed the grid around the connection while a node was invalidated.
        }
    }

    private static boolean connectionConnects(IGridConnection connection, IGridNode first, IGridNode second) {
        if (connection == null) return false;
        try {
            IGridNode a = connection.a();
            IGridNode b = connection.b();
            return (a == first && b == second) || (a == second && b == first);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean isTrackedConnection(IGridConnection connection) {
        if (connection == null) return false;
        for (TrackedConnection tracked : CONNECTIONS.values()) {
            if (tracked.connection == connection) return true;
        }
        return false;
    }

    /** @return whether the target node has a connection that was not created by the wireless network. */
    private static boolean hasForeignConnection(IGridNode targetNode, IGridNode coreNode) {
        if (targetNode == null) return false;
        try {
            for (IGridConnection connection : targetNode.getConnections()) {
                if (connection == null) continue;
                IGridNode other;
                try {
                    other = connection.getOtherSide(targetNode);
                } catch (RuntimeException error) {
                    continue;
                }
                if (other == coreNode || isTrackedConnection(connection)) continue;
                return true;
            }
        } catch (RuntimeException ignored) {
            // Treat an unreadable connection list as "unknown", i.e. do not claim the hatch is wired.
        }
        return false;
    }

    private static boolean isSameGrid(IGridNode coreNode, IGridNode targetNode) {
        if (coreNode == null || targetNode == null) return false;
        try {
            return coreNode.getGrid() != null && coreNode.getGrid() == targetNode.getGrid();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean areDirectlyConnected(IGridNode coreNode, IGridNode targetNode) {
        if (coreNode == null || targetNode == null) return false;
        try {
            for (IGridConnection connection : coreNode.getConnections()) {
                if (connection.getOtherSide(coreNode) == targetNode) return true;
            }
        } catch (RuntimeException ignored) {
            // Node teardown; no reliable information.
        }
        return false;
    }

    // endregion

    // region World / tile helpers

    private static MinecraftServer getServer() {
        return FMLCommonHandler.instance()
            .getMinecraftServerInstance();
    }

    private static boolean isChunkLoaded(World world, int x, int y, int z) {
        if (world == null || y < 0 || y > MAX_Y) return false;
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4);
    }

    /** @return whether the core block position stored in the record is this very core block. */
    private static boolean isSamePosition(int[] corePos, int dim, int x, int y, int z) {
        return corePos != null && corePos[0] == dim && corePos[1] == x && corePos[2] == y && corePos[3] == z;
    }

    /**
     * A wireless target is an ME "chamber": a GregTech ME hatch (a GT meta tile entity that is both an {@link MTEHatch}
     * and {@link IGridProxyable}), or a plain tile entity that hosts an AE2 grid proxy itself, such as this mod's
     * Thaumcraft ME essentia hatches. AE2's own tiles are excluded on purpose, so the tool cannot wire arbitrary ME
     * blocks like a drive or an interface into a wireless network.
     */
    private static boolean isBindableTile(TileEntity tile) {
        if (tile == null) return false;
        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity meta = gtTile.getMetaTileEntity();
            return meta instanceof MTEHatch && meta instanceof IGridProxyable;
        }
        return tile instanceof IGridProxyable && !(tile instanceof AEBaseTile);
    }

    private static IGridNode getGridNode(TileEntity tile) {
        if (!(tile instanceof IGridProxyable proxyable)) return null;
        try {
            AENetworkProxy proxy = proxyable.getProxy();
            return proxy == null ? null : proxy.getNode();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static IGridNode getCoreNode(MTWirelessNetworkCoreTile core) {
        if (core == null) return null;
        try {
            AENetworkProxy proxy = core.getProxy();
            return proxy == null ? null : proxy.getNode();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static MTWirelessNetworkCoreTile findLoadedCore(MinecraftServer server, UUID frequency) {
        if (server == null || frequency == null) return null;

        MTWirelessSavedData data = MTWirelessSavedData.get(server);
        if (data == null) return null;

        int[] corePos = data.getCore(frequency);
        if (corePos == null) return null;

        World world = server.worldServerForDimension(corePos[0]);
        if (!isChunkLoaded(world, corePos[1], corePos[2], corePos[3])) return null;

        TileEntity tile = world.getTileEntity(corePos[1], corePos[2], corePos[3]);
        if (tile instanceof MTWirelessNetworkCoreTile core && frequency.equals(core.getNetworkId())) return core;
        return null;
    }

    private static IGridNode findTargetNode(MinecraftServer server, MTWirelessMemberKey member) {
        if (server == null || member == null) return null;
        World world = server.worldServerForDimension(member.dim);
        if (!isChunkLoaded(world, member.x, member.y, member.z)) return null;
        TileEntity tile = world.getTileEntity(member.x, member.y, member.z);
        return isBindableTile(tile) ? getGridNode(tile) : null;
    }

    // endregion
}
