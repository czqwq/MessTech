package com.MessTech.common.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.MessTech.init.MessTech;

import baubles.api.BaublesApi;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.common.container.InventoryBaubles;

/**
 * 无线ME网络绑定工具的专用饰品栏槽位「无线连接器」，即 GTLCore（1.20.1）里那条 Curios 专用槽的 1.7.10 对应。
 * <p>
 * GTLCore 用 `data/curios/tags/items/wireless_connector.json` 把绑定工具限定在 Curios 的 `wireless_connector` 槽；
 * 1.7.10 没有 Curios，对应的自定义槽位机制来自 GTNH 自带的 Baubles Expanded：
 * <ul>
 * <li>{@link #SLOT_ID} 与 GTLCore 的 Curios 槽 id 完全一致（语言键 `slot.wireless_connector`）；</li>
 * <li>槽位类型必须在 {@code LoaderState.PREINITIALIZATION} 注册并分配（Baubles Expanded 的 API 只在这时接受），
 * 所以 {@link #init()} 由 {@code MTWirelessContent#preInit} 调用；</li>
 * <li>槽位的空槽图标由 Baubles Expanded 的 {@code ItemDebugger} 按 `baubles:textures/items/empty_bauble_slot_&lt;type&gt;.png`
 * 注册（在所有 preInit 之后才拼进图集），因此本模组要在自己的 jar 里提供
 * `assets/baubles/textures/items/empty_bauble_slot_wireless_connector.png`；</li>
 * <li>工具物品在 {@code MTWirelessNetworkBindingTool#getBaubleTypes} 里只上报这一个类型，因此它不能塞进
 * 普通的戒指/护身符等槽位，和 GTLCore 的 item tag 语义一致。</li>
 * </ul>
 * 这里的查找/回写与 GTLCore 的 {@code CuriosCompat} 同职责：找到饰品槽里的工具、把改动过的 ItemStack 同步给
 * 客户端（Baubles Expanded 自己发 {@code PacketSyncBauble}）。
 */
public final class MTWirelessBaubles {

    /** 专用槽位 id，照抄 GTLCore 的 Curios 槽 id。 */
    public static final String SLOT_ID = "wireless_connector";

    /**
     * 绑定工具可用的槽位类型。GTLCore 的 item tag 只列这一个槽，这里同样只上报它。
     * <p>
     * 调用方（Baubles Expanded）只读这个数组，不要原地修改。
     */
    public static final String[] TOOL_SLOT_TYPES = { SLOT_ID };

    private static boolean initialised;

    private MTWirelessBaubles() {}

    /**
     * 注册专用槽位类型并给每个玩家分配一个槽。必须在 preInit 阶段调用：Baubles Expanded 的
     * {@code BaubleExpandedSlots} 只在加载器处于 PREINITIALIZATION 时接受注册/分配，而玩家饰品栏的大小是
     * 按分配数量构造的，晚于 preInit 就补不出这个槽。
     */
    public static void init() {
        if (initialised) return;
        initialised = true;

        if (!BaubleExpandedSlots.tryRegisterType(SLOT_ID)) {
            MessTech.LOG.warn("Could not register the Baubles slot type {}", SLOT_ID);
        }
        if (!BaubleExpandedSlots.tryAssignSlotOfType(SLOT_ID)) {
            MessTech.LOG.warn("Could not assign a Baubles slot of type {}", SLOT_ID);
        }
    }

    /** 玩家饰品栏里的绑定工具（被穿着的那一份），没有则 {@code null}。 */
    public static @Nullable ItemStack findTool(EntityPlayer player) {
        InventoryBaubles baubles = inventory(player);
        if (baubles == null) return null;

        for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (MTWirelessToolLocator.isTool(stack)) return stack;
        }
        return null;
    }

    /** {@code stack} 在玩家饰品栏里的槽位下标，没有则 {@code -1}。 */
    public static int findSlot(EntityPlayer player, ItemStack stack) {
        InventoryBaubles baubles = inventory(player);
        if (baubles == null) return -1;

        for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
            if (baubles.getStackInSlot(slot) == stack) return slot;
        }
        return -1;
    }

    /**
     * 把改动过的工具同步给它的主人；工具不在饰品栏里时返回 {@code false}（调用方再处理主手/背包）。
     * <p>
     * Baubles Expanded 的 {@code InventoryBaubles#syncSlotToClients} 就是它自己改槽时的同步路径
     * （发 {@code PacketSyncBauble}，不需要重新装备 / 不触发 onEquipped），这里直接复用。
     */
    public static boolean syncSlot(EntityPlayer player, ItemStack stack) {
        int slot = findSlot(player, stack);
        if (slot < 0) return false;

        InventoryBaubles baubles = inventory(player);
        if (baubles == null) return false;
        baubles.syncSlotToClients(slot);
        return true;
    }

    /**
     * 玩家的饰品栏。Baubles Expanded 服务端/客户端各有一份缓存，工具改动只发生在服务端。
     */
    private static @Nullable InventoryBaubles inventory(EntityPlayer player) {
        if (player == null) return null;

        IInventory baubles = BaublesApi.getBaubles(player);
        return baubles instanceof InventoryBaubles inventory ? inventory : null;
    }
}
