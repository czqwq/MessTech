package com.MessTech.common.util;

import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.data.IAEItemStack;
import gregtech.api.util.GTUtility;

public class MTItemID extends GTUtility.ItemId {

    // region Member Variables
    private Item item;
    private int metaData;
    private NBTTagCompound nbt;
    // endregion

    // region Class Constructors
    public MTItemID(Item item, int metaData, NBTTagCompound nbt) {
        this.item = item;
        this.metaData = metaData;
        this.nbt = nbt;
    }

    public MTItemID(Item item, int metaData) {
        this.item = item;
        this.metaData = metaData;
    }

    public MTItemID(Item item) {
        this.item = item;
        this.metaData = 0;
    }

    public MTItemID() {}
    // endregion

    // region Static Methods
    public static final MTItemID NULL = new MTItemID();

    public static MTItemID create(ItemStack itemStack) {
        if (null == itemStack) return NULL;
        return new MTItemID(itemStack.getItem(), itemStack.getItemDamage(), itemStack.getTagCompound());
    }

    public static MTItemID create(Item item) {
        if (null == item) return NULL;
        return new MTItemID(item, 0);
    }

    public static MTItemID create(Item item, int metaData) {
        if (null == item) return NULL;
        return new MTItemID(item, metaData);
    }

    public static MTItemID create(Block block) {
        if (null == block) return NULL;
        return create(new ItemStack(block));
    }

    public static MTItemID create(Block block, int metaData) {
        if (null == block) return NULL;
        return create(new ItemStack(block, 1, metaData));
    }

    public static MTItemID createNoNBT(ItemStack itemStack) {
        if (null == itemStack) return NULL;
        return new MTItemID(itemStack.getItem(), itemStack.getItemDamage());
    }

    public static MTItemID createAsWildcard(ItemStack itemStack) {
        if (null == itemStack) return NULL;
        return new MTItemID(itemStack.getItem(), OreDictionary.WILDCARD_VALUE);
    }

    public static MTItemID create(IAEItemStack aeStack) {
        return create(aeStack.getItemStack());
    }

    public static MTItemID[] create(ItemStack... itemStacks) {
        MTItemID[] out = new MTItemID[itemStacks.length];
        for (int i = 0; i < itemStacks.length; i++) {
            out[i] = MTItemID.create(itemStacks[i]);
        }
        return out;
    }
    // endregion

    // region Special Methods
    public ItemStack getItemStack() {
        ItemStack r = new ItemStack(item, 1, metaData);
        if (nbt != null) {
            r.setTagCompound(nbt);
        }
        return r;
    }

    public ItemStack getItemStack(int amount) {
        ItemStack r = new ItemStack(item, amount, metaData);
        if (nbt != null) {
            r.setTagCompound(nbt);
        }
        return r;
    }

    public ItemStack getItemStackWithNBT() {
        ItemStack itemStack = new ItemStack(item, 1, metaData);
        itemStack.setTagCompound(nbt);
        return itemStack;
    }

    public ItemStack getItemStackWithNBT(int amount) {
        ItemStack itemStack = new ItemStack(item, amount, metaData);
        itemStack.setTagCompound(nbt);
        return itemStack;
    }

    public ItemStack getItemStackWithoutNBT() {
        return new ItemStack(item, 1, metaData);
    }

    public ItemStack getItemStackWithoutNBT(int amount) {
        return new ItemStack(item, amount, metaData);
    }

    // endregion

    // region General Methods
    public boolean isWildcard() {
        return this.metaData == OreDictionary.WILDCARD_VALUE;
    }

    public MTItemID setItem(Item item) {
        this.item = item;
        return this;
    }

    public MTItemID setMetaData(int metaData) {
        this.metaData = metaData;
        return this;
    }

    public MTItemID setNbt(NBTTagCompound nbt) {
        this.nbt = nbt;
        return this;
    }

    @Override
    public Item item() {
        return item;
    }

    @Override
    public int metaData() {
        return metaData;
    }

    @Nullable
    @Override
    public NBTTagCompound nbt() {
        return nbt;
    }

    @Nullable
    @Override
    public Integer stackSize() {
        // todo
        return null;
    }

    @Nullable
    protected NBTTagCompound getNBT() {
        return nbt;
    }

    protected Item getItem() {
        return item;
    }

    protected int getMetaData() {
        return metaData;
    }

    public boolean equalItemStack(ItemStack itemStack) {
        if (isWildcard()) return equals(createAsWildcard(itemStack));
        if (nbt != null) return equals(create(itemStack));
        return equals(createNoNBT(itemStack));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MTItemID)) {
            return false;
        }
        MTItemID tstItemID = (MTItemID) o;
        return metaData == tstItemID.metaData && Objects.equals(item, tstItemID.item)
            && Objects.equals(nbt, tstItemID.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, metaData, nbt);
    }
}
