package com.MessTech.common.wireless;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.util.GTUtility;

/**
 * The world interaction half of {@link MTWirelessNetworkBindingTool}:
 * <ul>
 * <li>sneak + left click an ME hatch unbinds it ({@code onItemUseFirst} only sees right clicks, so the unbind path
 * listens to Forge's {@link PlayerInteractEvent} and cancels the block break);</li>
 * <li>sneak + right click a block while the tool (with a saved network) sits anywhere in the inventory and the held
 * item is the block item of an ME hatch, binds the freshly placed hatch to that network - the 1.7.10 port of GTLCore's
 * {@code WirelessBindingToolPlacementHandler}: 1.7.10 has no vanilla offhand, so the tool is looked up through
 * {@link MTWirelessToolLocator} (Baubles accessory slot first, then the main hand and the main inventory) instead of
 * the offhand; the held item being a block item also guarantees the tool is not the item that receives the click (the
 * tool intercepts right clicks in {@code onItemUseFirst} before a block is ever placed).</li>
 * </ul>
 */
public final class MTWirelessToolEvents {

    /** Placement runs before the block entity is ready to be a target, so bind a couple of ticks later. */
    private static final int BIND_DELAY_TICKS = 2;
    /** The block entity may still be settling; retry a few times before giving up. */
    private static final int MAX_ATTEMPTS = 10;

    private static final List<PendingBind> PENDING = new ArrayList<>();

    private static boolean initialised;

    private MTWirelessToolEvents() {}

    /** Registers the listener. Called once from {@code MTWirelessContent#preInit}. */
    public static void init() {
        if (initialised) return;
        initialised = true;
        MTWirelessToolEvents handler = new MTWirelessToolEvents();
        MinecraftForge.EVENT_BUS.register(handler);
        // The delayed auto bind needs the server tick, which is not a Forge bus event.
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || event.world == null || event.world.isRemote) return;

        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            onLeftClick(event, player);
        } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            queueSneakPlaceAutoBind(event, player);
        }
    }

    /** Sneak + left click an ME hatch: unbind it instead of breaking it. */
    private void onLeftClick(PlayerInteractEvent event, EntityPlayer player) {
        if (!player.isSneaking()) return;

        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != MTWirelessNetworkBindingTool.INSTANCE) return;

        MTWirelessMemberKey target = MTWirelessNetworkRuntime.resolveTarget(event.world, event.x, event.y, event.z);
        // Not an ME hatch: this is a normal block break, leave it alone.
        if (target == null) return;

        event.setCanceled(true);
        if (!(player instanceof EntityPlayerMP serverPlayer)) return;

        switch (MTWirelessNetworkRuntime.unbindTarget(serverPlayer, target)) {
            case OK -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.disconnected");
            case NO_ACCESS -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.no_access");
            default -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.not_bound");
        }
    }

    /**
     * Sneak placement of a block item: remember the spot and bind the hatch once its tile entity exists. The block that
     * will appear there is known from the held item, so a failed placement can never bind whatever already occupies the
     * position.
     */
    private void queueSneakPlaceAutoBind(PlayerInteractEvent event, EntityPlayer player) {
        if (!player.isSneaking()) return;

        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;

        UUID frequency = findSavedNetwork(player);
        if (frequency == null) return;

        Block placedBlock = Block.getBlockFromItem(held.getItem());
        if (placedBlock == null || placedBlock == Blocks.air) return;
        // Placing a wireless core is not placing a hatch: it has no ME member to bind (the core GUI picks its network).
        if (placedBlock == MTWirelessNetworkCoreBlock.INSTANCE) return;

        int[] pos = placementPosition(event.world, event.x, event.y, event.z, event.face);
        if (pos == null) return;
        PENDING.add(
            new PendingBind(player, event.world.provider.dimensionId, placedBlock, pos[0], pos[1], pos[2], frequency));
    }

    /**
     * @return the network saved in the binding tool the player carries (accessory slot first), or {@code null}. Since
     *         v2 a network may exist without an online core, but a deleted network must not keep auto-binding hatches,
     *         so the record's existence (not its core slot) decides whether the saved network is still usable.
     */
    private static UUID findSavedNetwork(EntityPlayer player) {
        ItemStack stack = MTWirelessToolLocator.findWithNetwork(player);
        if (stack == null) return null;

        UUID frequency = MTWirelessNetworkBindingTool.getSavedNetwork(stack);
        if (frequency == null) return null;

        MTWirelessSavedData data = MTWirelessSavedData.get(
            FMLCommonHandler.instance()
                .getMinecraftServerInstance());
        if (data != null && data.getName(frequency) == null) return null;
        return frequency;
    }

    /** Mirrors the position adjustment of {@code ItemBlock#onItemUse} for the block the click places on. */
    private static int[] placementPosition(World world, int x, int y, int z, int side) {
        int face = side;
        Block clicked = world.getBlock(x, y, z);
        if (clicked == Blocks.snow_layer && (world.getBlockMetadata(x, y, z) & 7) < 1) {
            face = 1;
        } else if (clicked != Blocks.vine && clicked != Blocks.tallgrass
            && clicked != Blocks.deadbush
            && !clicked.isReplaceable(world, x, y, z)) {
                switch (face) {
                    case 0 -> y--;
                    case 1 -> y++;
                    case 2 -> z--;
                    case 3 -> z++;
                    case 4 -> x--;
                    case 5 -> x++;
                    default -> {
                        return null;
                    }
                }
            }
        if (y < 0 || y > 255) return null;
        return new int[] { x, y, z };
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (FMLCommonHandler.instance()
            .getMinecraftServerInstance() == null) {
            PENDING.clear();
            return;
        }
        if (PENDING.isEmpty()) return;

        Iterator<PendingBind> iterator = PENDING.iterator();
        List<PendingBind> retry = new ArrayList<>();
        while (iterator.hasNext()) {
            PendingBind pending = iterator.next();
            if (--pending.remaining > 0) continue;
            iterator.remove();

            if (pending.player.isDead) continue;
            if (pending.player instanceof EntityPlayerMP serverPlayer && serverPlayer.playerNetServerHandler == null) {
                // The player disconnected while the bind was waiting.
                continue;
            }
            World world = FMLCommonHandler.instance()
                .getMinecraftServerInstance()
                .worldServerForDimension(pending.dimension);
            if (world == null) continue;
            // Never force-load a chunk for the delayed bind: if the chunk is gone (or reloaded meanwhile) the tile
            // entity cannot be resolved, and retrying would only generate load.
            if (!world.getChunkProvider()
                .chunkExists(pending.x >> 4, pending.z >> 4)) {
                if (pending.attempts++ < MAX_ATTEMPTS) {
                    pending.remaining = 1;
                    retry.add(pending);
                }
                continue;
            }
            if (world.getBlock(pending.x, pending.y, pending.z) != pending.placedBlock) {
                // The placement did not happen (or something replaced it): give up instead of binding a stale spot.
                continue;
            }

            MTWirelessMemberKey target = MTWirelessNetworkRuntime.resolveTarget(world, pending.x, pending.y, pending.z);
            if (target == null) {
                // The block entity is not ready yet; try again next tick until we run out of attempts.
                if (pending.attempts++ < MAX_ATTEMPTS) {
                    pending.remaining = 1;
                    retry.add(pending);
                }
                continue;
            }

            if (!(pending.player instanceof EntityPlayerMP serverPlayer)) continue;
            if (pending.frequency.equals(
                MTWirelessNetworkRuntime.getMemberNetwork(
                    FMLCommonHandler.instance()
                        .getMinecraftServerInstance(),
                    target))) {
                // Already bound to the network we would bind: stay quiet.
                continue;
            }
            bind(serverPlayer, target, pending.frequency);
        }
        PENDING.addAll(retry);
    }

    private static void bind(EntityPlayerMP player, MTWirelessMemberKey target, UUID frequency) {
        switch (MTWirelessNetworkRuntime.bindTarget(player, target, frequency, true)) {
            case OK -> GTUtility.sendChatTrans(
                player,
                "wirelessme.chat.linked",
                MTWirelessNetworkBindingTool.networkName(player, frequency));
            case OK_PENDING -> GTUtility.sendChatTrans(
                player,
                "wirelessme.chat.linked_pending",
                MTWirelessNetworkBindingTool.networkName(player, frequency));
            case NO_ACCESS -> GTUtility.sendChatTrans(player, "wirelessme.chat.no_access");
            case NO_CORE -> GTUtility.sendChatTrans(player, "wirelessme.chat.missing_core");
            case CORE_NOT_CONNECTED -> GTUtility.sendChatTrans(player, "wirelessme.chat.core_not_connected");
            case WIRED, ALREADY_BOUND, ALREADY_CONNECTED, INVALID_TARGET -> {
                // Not errors worth a chat line: the player placed a hatch that is already wired/connected.
            }
            default -> {}
        }
    }

    private static final class PendingBind {

        private final EntityPlayer player;
        private final int dimension;
        private final Block placedBlock;
        private final int x;
        private final int y;
        private final int z;
        private final UUID frequency;
        private int remaining = BIND_DELAY_TICKS;
        private int attempts;

        private PendingBind(EntityPlayer player, int dimension, Block placedBlock, int x, int y, int z,
            UUID frequency) {
            this.player = player;
            this.dimension = dimension;
            this.placedBlock = placedBlock;
            this.x = x;
            this.y = y;
            this.z = z;
            this.frequency = frequency;
        }
    }
}
