package com.MessTech.common.item;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * Creative AE cell inventory for the ME Creative Ore Disk.
 * <p>
 * It exposes every item registered under an ore dictionary name starting with {@code ore}, each with
 * the configured ME quantity from the disk's NBT. Extraction behaves like a creative cell (matching
 * requests always succeed), while the quantity controls how much of each ore type is shown/available
 * in the ME network.
 */
public class MECreativeOreDiskInventory implements IMEInventoryHandler<IAEItemStack> {

    private final IItemList<IAEItemStack> listCache;
    private final long quantity;

    public MECreativeOreDiskInventory(ItemStack diskStack) {
        this.quantity = getQuantity(diskStack);
        this.listCache = AEItemStackType.ITEM_STACK_TYPE.createPrimitiveList();
        refreshCache();
    }

    private static long getQuantity(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null
            || !stack.getTagCompound()
                .hasKey("quantity")) {
            return 64;
        }
        return Math.max(
            1,
            stack.getTagCompound()
                .getInteger("quantity"));
    }

    private void refreshCache() {
        for (String oreName : OreDictionary.getOreNames()) {
            if (oreName == null || !oreName.startsWith("ore")) continue;
            for (ItemStack stack : OreDictionary.getOres(oreName)) {
                if (stack == null || stack.getItem() == null) continue;
                IAEItemStack aeStack = AEItemStack.create(stack);
                if (aeStack == null) continue;
                aeStack.setStackSize(quantity);
                listCache.add(aeStack);
            }
        }
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
        if (input == null) return null;
        IAEItemStack local = listCache.findPrecise(input);
        return local == null ? input : null;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        if (request == null) return null;
        IAEItemStack local = listCache.findPrecise(request);
        if (local == null) return null;
        // This disk is bounded by the configured quantity. AE's stocking bus asks for Integer.MAX_VALUE
        // to learn the full available amount, so we must cap the returned stack to the configured
        // quantity instead of echoing the unbounded request (which made the first auto-pull GUI refresh
        // show 2.14G / Integer.MAX_VALUE).
        IAEItemStack result = request.copy();
        result.setStackSize(Math.min(request.getStackSize(), local.getStackSize()));
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
        for (IAEItemStack stack : listCache) {
            out.add(stack.copy());
        }
        return out;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration,
        Optional<Predicate<IAEItemStack>> filter) {
        if (!filter.isPresent()) return getAvailableItems(out, iteration);
        for (IAEItemStack stack : listCache) {
            if (filter.get()
                .test(stack)) out.add(stack.copy());
        }
        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(IAEItemStack request, int iteration) {
        IAEItemStack local = listCache.findPrecise(request);
        return local == null ? null : local.copy();
    }

    @Override
    public IAEStackType<?> getStackType() {
        return AEItemStackType.ITEM_STACK_TYPE;
    }

    @Override
    @Deprecated
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return input != null && listCache.findPrecise(input) != null;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return input != null && listCache.findPrecise(input) != null;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }
}
