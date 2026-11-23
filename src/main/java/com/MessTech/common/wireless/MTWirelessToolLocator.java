package com.MessTech.common.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * 找出玩家携带的无线ME网络绑定工具：先饰品栏（{@link MTWirelessBaubles}），再主手，最后主物品栏。1.7.10
 * 没有原版副手，所以 GTLCore {@code WirelessBindingToolLocator} 的「饰品栏 → 双手 → 背包」在这里是「饰品栏 →
 * 主手 → 背包」。
 * <p>
 * 用途与 GTLCore 相同：绑定工具放进饰品槽后依然要能被网络选择 GUI / 蹲下放置自动绑定使用，这两条路径都不能
 * 依赖「工具正好拿在手上」。
 * <p>
 * 这里只看工具 NBT 里的网络 id，不查 {@link MTWirelessSavedData}：定位也会在客户端（网络选择界面构建时）调用，
 * 而存档数据只在服务端可读。v2 起网络是独立记录、可能暂时没有核心，所以“有已保存网络”仍然只按工具 NBT 判定。
 */
public final class MTWirelessToolLocator {

    private MTWirelessToolLocator() {}

    /** 玩家携带的绑定工具，没有则 {@code null}。 */
    public static @Nullable ItemStack find(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;

        ItemStack slotted = MTWirelessBaubles.findTool(player);
        if (slotted != null) return slotted;

        ItemStack held = player.getHeldItem();
        if (isTool(held)) return held;

        for (ItemStack stack : player.inventory.mainInventory) {
            if (isTool(stack)) return stack;
        }
        return null;
    }

    /**
     * 第一把**带已保存网络**的工具（饰品栏 → 主手 → 主物品栏），没有则 {@code null}。
     * <p>
     * 蹲下放置 ME 仓室的自动绑定用这个：玩家同时带着一把空工具和一把已存网络的工具时，仍然要能自动绑定
     * （原来的实现是扫背包找第一把「有网络」的工具，这里保持同样的语义）。
     */
    public static @Nullable ItemStack findWithNetwork(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;

        ItemStack slotted = MTWirelessBaubles.findTool(player);
        if (hasNetwork(slotted)) return slotted;

        ItemStack held = player.getHeldItem();
        if (hasNetwork(held)) return held;

        for (ItemStack stack : player.inventory.mainInventory) {
            if (hasNetwork(stack)) return stack;
        }
        return null;
    }

    /** 是否就是绑定工具。 */
    public static boolean isTool(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() == MTWirelessNetworkBindingTool.INSTANCE;
    }

    /** 是否是「存了网络」的绑定工具。 */
    private static boolean hasNetwork(@Nullable ItemStack stack) {
        return isTool(stack) && MTWirelessNetworkBindingTool.getSavedNetwork(stack) != null;
    }
}
