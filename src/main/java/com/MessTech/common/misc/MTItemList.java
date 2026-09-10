package com.MessTech.common.misc;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.MessTech.common.util.Utils;
import com.MessTech.init.MessTech;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

@SuppressWarnings("SpellCheckingInspection")
public enum MTItemList {

    MTDTPF,
    MTComputingCenter,
    MTHatchRack,
    MTAssFactory,
    AssMatrixBlock,
    AdvAssMatrixBlock,
    SpaceModulePumpInfinity,
    SpaceModuleMinerInfinity,
    SpaceModuleAssemblerInfinity,
    MTNQDAFReactor,
    MTInventoryInputBusME,
    MTInventoryInputHatchME,
    MTWirelessVacuumConveyorInput,
    MTWirelessVacuumConveyorOutput,
    MTNanoScaleFoundry,
    MTDBBFurnace,
    BosesCraftingArray;

    private boolean mHasNotBeenSet;
    private boolean mDeprecated;
    private boolean mWarned;
    private ItemStack mStack;

    MTItemList() {
        mHasNotBeenSet = true;
    }

    MTItemList(boolean aDeprecated) {
        if (aDeprecated) {
            mDeprecated = true;
            mHasNotBeenSet = true;
        }
    }

    public int getMeta() {
        return mStack.getItemDamage();
    }

    public MTItemList set(Item aItem) {
        mHasNotBeenSet = false;
        if (aItem == null) return this;
        ItemStack aStack = new ItemStack(aItem, 1, 0);
        mStack = Utils.copyAmount(1, aStack);
        return this;
    }

    public MTItemList set(ItemStack aStack) {
        if (aStack != null) {
            mHasNotBeenSet = false;
            mStack = Utils.copyAmount(1, aStack);
        }
        return this;
    }

    public MTItemList set(IMetaTileEntity metaTileEntity) {
        if (metaTileEntity == null) throw new IllegalArgumentException("Invalid Meta Tile Entity");
        set(metaTileEntity.getStackForm(1L));
        return this;
    }

    public boolean hasBeenSet() {
        return !mHasNotBeenSet;
    }

    public ItemStack getInternalStack_unsafe() {
        return mStack;
    }

    public Item getItem() {
        sanityCheck();
        if (Utils.isStackInvalid(mStack)) return null;
        return mStack.getItem();
    }

    public Block getBlock() {
        sanityCheck();
        return Block.getBlockFromItem(getItem());
    }

    public ItemStack get(int aAmount, Object... aReplacements) {
        sanityCheck();
        // if invalid, return a replacements
        if (Utils.isStackInvalid(mStack)) {
            MessTech.LOG.error("Object in the ItemList is null at:");
        }
        return Utils.copyAmount(aAmount, mStack);
    }

    private void sanityCheck() {
        if (mHasNotBeenSet)
            throw new IllegalAccessError("The Enum '" + name() + "' has not been set to an Item at this time!");
        if (mDeprecated && !mWarned) {
            new Exception(this + " is now deprecated").printStackTrace();
            // warn only once
            mWarned = true;
        }
    }
}
