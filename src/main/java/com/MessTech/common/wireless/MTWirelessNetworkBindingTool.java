package com.MessTech.common.wireless;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.MessTech.common.wireless.gui.MTWirelessNetworkPickerGui;

import baubles.api.BaubleType;
import baubles.api.expanded.BaubleItemHelper;
import baubles.api.expanded.IBaubleExpanded;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;

/**
 * 无线ME网络绑定工具 (the wireless ME network binding tool), 1.7.10 counterpart of GTLCore's
 * {@code WirelessNetworkBindingToolItem}.
 * <p>
 * The tool caches one network (its id, last known name and access level) in its NBT and binds ME hatches to that
 * network through {@link MTWirelessNetworkRuntime}. Every interaction is server logic:
 * <ul>
 * <li>sneak + right click a bound {@link MTWirelessNetworkCoreTile}: save that core's network into the tool (an unbound
 * core answers with {@code wirelessme.chat.core_no_network});</li>
 * <li>right click an ME hatch: bind it (or report that it is already bound);</li>
 * <li>sneak + right click an ME hatch: re-bind it, replacing the previous network;</li>
 * <li>sneak + left click an ME hatch: unbind it (see {@link MTWirelessToolEvents});</li>
 * <li>right click the air: open {@link MTWirelessNetworkPickerGui} to pick an accessible network.</li>
 * </ul>
 * The right-click paths use {@link #onItemUseFirst} so the tool sees the click before the block under the crosshair
 * does. On the client that method returns {@code false} on purpose: a client-side {@code true} would make
 * {@code PlayerControllerMP} swallow the click without sending the block-placement packet, and everything this item
 * does happens on the server.
 * <p>
 * Since v2 a network is a persistent record independent of its core, so a saved network may temporarily have no online
 * core (breaking the core keeps the network). The cached name/access level are refreshed from the
 * {@code WorldSavedData} every {@value #NAME_REFRESH_INTERVAL} ticks as long as the record still exists; that cache is
 * also what makes the access level visible in the tooltip on the client, which never reads the saved data.
 * <p>
 * The tool can also be worn in the dedicated Baubles accessory slot {@link MTWirelessBaubles#SLOT_ID} (the 1.7.10
 * counterpart of GTLCore's Curios {@code wireless_connector} slot); a worn item sees no item right clicks, so the
 * picker is additionally bound to a key ({@code MTWirelessKeyBindings}) and every path that consumes the tool
 * locates it through {@link MTWirelessToolLocator} instead of the hands.
 */
public class MTWirelessNetworkBindingTool extends Item implements IBaubleExpanded {

    /** The one instance, registered by {@code MTWirelessContent#preInit}. */
    public static final MTWirelessNetworkBindingTool INSTANCE = new MTWirelessNetworkBindingTool();

    /** NBT key of the saved network's {@link UUID#toString()} (1.7.10 NBT has no UUID type). */
    public static final String NBT_NETWORK = "WirelessNetworkId";
    /** NBT key of the saved network's cached display name. */
    public static final String NBT_NETWORK_NAME = "WirelessNetworkName";
    /** NBT key of the saved network's access level ({@link MTWirelessAccess#id()}, one byte). */
    public static final String NBT_NETWORK_ACCESS = "WirelessNetworkAccess";

    /** The tool's saved network cache is refreshed from the {@code WorldSavedData} this often (ticks). */
    private static final int NAME_REFRESH_INTERVAL = 20;

    public MTWirelessNetworkBindingTool() {
        setUnlocalizedName("wirelessNetworkBindingTool");
        setTextureName("messtech:wireless_advanced_tool");
        setMaxStackSize(1);
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
    }

    // ------------------------------------------------------------------ saved network

    /** The network saved in the tool, or {@code null} when nothing (valid) is saved. */
    public static UUID getSavedNetwork(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_NETWORK)) return null;

        try {
            return UUID.fromString(tag.getString(NBT_NETWORK));
        } catch (IllegalArgumentException e) {
            // Broken NBT (hand edited, or written by a different build): treat the tool as empty.
            return null;
        }
    }

    /** The cached display name of the saved network, or an empty string. */
    public static String getSavedNetworkName(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null ? "" : tag.getString(NBT_NETWORK_NAME);
    }

    /** @return the cached access level of the saved network, or {@code null} while it has not been synced yet. */
    public static @Nullable MTWirelessAccess getSavedNetworkAccess(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_NETWORK_ACCESS)) return null;
        return MTWirelessAccess.byId(tag.getByte(NBT_NETWORK_ACCESS));
    }

    /**
     * Saves {@code frequency} (and its current name/access level) into the tool and tells the player. Refreshes the
     * client's copy of the held item, which the GUI container does not sync itself.
     */
    public static void saveNetwork(EntityPlayerMP player, ItemStack stack, UUID frequency) {
        if (player == null || stack == null || frequency == null) return;

        MTWirelessSavedData data = MTWirelessSavedData.get(player.mcServer);
        String name = networkName(player, frequency);
        MTWirelessAccess access = data == null ? MTWirelessAccess.PRIVATE : data.getAccess(frequency);

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(NBT_NETWORK, frequency.toString());
        tag.setString(NBT_NETWORK_NAME, name);
        tag.setByte(NBT_NETWORK_ACCESS, (byte) access.id());

        syncTool(player, stack);
        GTUtility.sendChatTrans(player, "wirelessme.chat.saved", name);
    }

    /** Forgets the network saved in the tool, telling the player when there was one. */
    public static void clearNetwork(EntityPlayerMP player, ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (player == null || tag == null || !tag.hasKey(NBT_NETWORK)) return;

        tag.removeTag(NBT_NETWORK);
        tag.removeTag(NBT_NETWORK_NAME);
        tag.removeTag(NBT_NETWORK_ACCESS);
        if (tag.hasNoTags()) stack.setTagCompound(null);

        syncTool(player, stack);
        GTUtility.sendChatTrans(player, "wirelessme.chat.cleared");
    }

    // ------------------------------------------------------------------ interactions

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // The client must not swallow the click: see the class javadoc.
        if (world.isRemote) return false;
        if (!(player instanceof EntityPlayerMP serverPlayer)) return true;

        TileEntity tile = world.getTileEntity(x, y, z);

        // Sneak + right click a core saves its network.
        if (player.isSneaking() && tile instanceof MTWirelessNetworkCoreTile core) {
            UUID frequency = core.getNetworkId();
            if (frequency == null) {
                GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.core_no_network");
                return true;
            }

            if (!MTWirelessNetworkRuntime.canAccess(serverPlayer, frequency)) {
                GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.no_access");
                return true;
            }
            saveNetwork(serverPlayer, stack, frequency);
            return true;
        }

        MTWirelessMemberKey target = MTWirelessNetworkRuntime.resolveTarget(world, x, y, z);
        // Not an ME hatch: let the block handle the click itself.
        if (target == null) return false;

        UUID frequency = getSavedNetwork(stack);
        if (frequency == null) {
            GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.select_core");
            return true;
        }

        // The switch is written over the returned enum without naming its type: the result values are the contract's
        // BindResult constants (§4.3 / §9.2).
        switch (MTWirelessNetworkRuntime.bindTarget(serverPlayer, target, frequency, player.isSneaking())) {
            case OK -> GTUtility.sendChatTrans(
                serverPlayer,
                "wirelessme.chat.linked",
                cacheNetworkName(serverPlayer, stack, frequency));
            case OK_PENDING -> GTUtility.sendChatTrans(
                serverPlayer,
                "wirelessme.chat.linked_pending",
                cacheNetworkName(serverPlayer, stack, frequency));
            case ALREADY_BOUND -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.already_bound");
            case ALREADY_CONNECTED -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.already_connected");
            case WIRED -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.wired");
            case NO_ACCESS -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.no_access");
            case NO_CORE -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.missing_core");
            case CORE_NOT_CONNECTED -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.core_not_connected");
            default -> GTUtility.sendChatTrans(serverPlayer, "wirelessme.chat.invalid_target");
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP serverPlayer) {
            MTWirelessNetworkPickerGui.open(serverPlayer);
        }
        return stack;
    }

    /**
     * Keeps the cached network name/access level close to the {@code WorldSavedData} ones, so a network renamed
     * from the core GUI shows its new name in the tooltip without saving it again. The record itself may currently have
     * no core (v2 keeps networks after their core is broken), so the existence check is the record, not its core slot.
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world == null || world.isRemote || !(entity instanceof EntityPlayerMP serverPlayer)) return;
        if (world.getTotalWorldTime() % NAME_REFRESH_INTERVAL != 0) return;

        if (refreshCachedName(serverPlayer, stack) && selected) syncTool(serverPlayer, stack);
    }

    /**
     * 饰品栏里的工具不会走 {@link #onUpdate}（那只覆盖主物品栏），由 Baubles Expanded 对「穿着的饰品」每 tick
     * 调一次 {@link #onWornTick}；缓存的刷新就挂在这里，保持和主手/背包里的工具同样的 tooltip 行为。
     */
    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayerMP serverPlayer)) return;

        World world = serverPlayer.worldObj;
        if (world == null || world.isRemote) return;
        if (world.getTotalWorldTime() % NAME_REFRESH_INTERVAL != 0) return;

        if (refreshCachedName(serverPlayer, stack)) MTWirelessBaubles.syncSlot(serverPlayer, stack);
    }

    /**
     * 把 {@code WorldSavedData} 里的当前名字/访问等级写回工具的 NBT；真的改了才返回 {@code true}。
     * 记录不存在（网络已被删除）时保持缓存不动，等玩家重新选网络。
     */
    private static boolean refreshCachedName(EntityPlayerMP player, ItemStack stack) {
        UUID frequency = getSavedNetwork(stack);
        if (frequency == null) return false;

        MTWirelessSavedData data = MTWirelessSavedData.get(player.mcServer);
        if (data == null) return false;

        // v2: a network exists as soon as its record does; it may legitimately have no core right now.
        String name = data.getName(frequency);
        if (name == null) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        boolean changed = false;
        if (!name.equals(tag.getString(NBT_NETWORK_NAME))) {
            tag.setString(NBT_NETWORK_NAME, name);
            changed = true;
        }
        int access = data.getAccess(frequency)
            .id();
        if (!tag.hasKey(NBT_NETWORK_ACCESS) || tag.getByte(NBT_NETWORK_ACCESS) != (byte) access) {
            tag.setByte(NBT_NETWORK_ACCESS, (byte) access);
            changed = true;
        }
        return changed;
    }

    // ------------------------------------------------------------------ helpers
    public static String networkName(EntityPlayerMP player, UUID frequency) {
        if (frequency == null) return "";

        MTWirelessSavedData data = player == null ? null : MTWirelessSavedData.get(player.mcServer);
        String name = data == null ? null : data.getName(frequency);
        if (name == null || name.trim()
            .isEmpty()) {
            return shortFrequency(frequency);
        }
        return name;
    }

    /** The first eight characters of a network ID, the fallback name of an unnamed network. */
    public static String shortFrequency(UUID frequency) {
        return frequency == null ? ""
            : frequency.toString()
                .substring(0, 8);
    }

    /** Refreshes the name cached in the tool and returns the name to show in chat. */
    private static String cacheNetworkName(EntityPlayerMP player, ItemStack stack, UUID frequency) {
        String name = networkName(player, frequency);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) tag.setString(NBT_NETWORK_NAME, name);
        return name;
    }

    /**
     * 把改过 NBT 的工具同步给它的主人，工具在哪里都行：饰品栏（{@link MTWirelessBaubles#syncSlot}，Baubles
     * Expanded 的 PacketSyncBauble）、主手/物品栏（{@code S2FPacketSetSlot}）。
     * <p>
     * 主物品栏这一路是必需的：MUI2 的容器没有玩家物品格，常规的容器 tick 不会同步 GUI 打开期间改动的物品；而
     * 饰品栏那一路不能走容器路径，Baubles Expanded 自己维护一份背包缓存。
     */
    private static void syncTool(EntityPlayerMP player, ItemStack stack) {
        if (MTWirelessBaubles.syncSlot(player, stack)) return;

        int hotbarSlot = player.inventory.currentItem;
        if (hotbarSlot >= 0 && hotbarSlot < 9 && player.inventory.getStackInSlot(hotbarSlot) == stack) {
            // Slots 36..44 of the player inventory container are the hotbar.
            player.sendSlotContents(player.inventoryContainer, 36 + hotbarSlot, stack);
            return;
        }

        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            if (player.inventory.mainInventory[slot] != stack) continue;
            // Main inventory slots 0..8 are the hotbar (container slots 36..44), 9..35 map to themselves.
            player.sendSlotContents(player.inventoryContainer, slot < 9 ? 36 + slot : slot, stack);
            return;
        }
    }

    // ------------------------------------------------------------------ Baubles（饰品栏）

    /**
     * 只上报专用槽位 {@link MTWirelessBaubles#SLOT_ID}：GTLCore 用 Curios 的 item tag 把工具限死在那条
     * `wireless_connector` 槽里，不让它和无线终端抢通用的 `curio` 槽；这里同样不给戒指/护身符等槽位开口子。
     */
    @Override
    public String[] getBaubleTypes(ItemStack stack) {
        return MTWirelessBaubles.TOOL_SLOT_TYPES;
    }

    /** {@link IBaubleExpanded} 会忽略它，接口要求实现；返回 {@code null} 即可。 */
    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return null;
    }

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase entity) {}

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase entity) {}

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase entity) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase entity) {
        return true;
    }

    // ------------------------------------------------------------------ tooltip

    /**
     * House style: colours live in the lang files, every line here is one {@code wirelessme.tooltip.*} key. The
     * GTLCore-style source line (「由GregTech Leisure添加」) is registered once by {@code MTWirelessContent#registerBlocks}
     * (a second {@code MTRegistryMark.markAdded} call would append the line twice, since its stack map ignores NBT).
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.tool.select"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.tool.pick"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.tool.bind"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.tool.rebind"));
        tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.tool.disconnect"));

        if (getSavedNetwork(stack) == null) {
            tooltip.add(StatCollector.translateToLocal("wirelessme.tooltip.tool.empty"));
        } else {
            String name = getSavedNetworkName(stack);
            tooltip.add(
                StatCollector.translateToLocalFormatted(
                    "wirelessme.tooltip.tool.network",
                    name.isEmpty() ? StatCollector.translateToLocal("wirelessme.tooltip.tool.unknown") : name));

            // The access level lives in the saved data, which the client never reads: its cached copy is refreshed on
            // the server every NAME_REFRESH_INTERVAL ticks and synced with the tool's NBT.
            MTWirelessAccess access = getSavedNetworkAccess(stack);
            if (access != null) {
                tooltip.add(
                    StatCollector.translateToLocalFormatted(
                        "wirelessme.tooltip.tool.access",
                        StatCollector.translateToLocal(access.langKey())));
            }
        }

        // Baubles Expanded's standard "compatible slots" hint (SHIFT shows the slot's localized name). The slot is
        // the same dedicated one GTLCore tags the tool into, see MTWirelessBaubles.
        BaubleItemHelper.addSlotInformation(tooltip, getBaubleTypes(stack));
    }
}
