package com.MessTech.common.items;

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
    SpaceModuleApiaryMK1,
    SpaceModuleApiaryMK2,
    SpaceModuleApiaryMK3,
    SpaceModuleApiaryMK4,
    MTNQDAFReactor,
    MTInventoryInputBusME,
    MTInventoryInputHatchME,
    MTWirelessVacuumConveyorInput,
    MTWirelessVacuumConveyorOutput,
    MTWirelessBeamlineInput,
    MTWirelessBeamlineOutput,
    MTWirelessBeamlineAdvancedOutput,
    MTNanoScaleFoundry,
    MTDBBFurnace,
    BosesCraftingArray,
    MTReactor,
    MTReactorAccessHatch_EV,
    MTReactorAccessHatch_IV,
    MTReactorAccessHatch_LuV,
    MTReactorAccessHatch_ZPM,
    MTReactorAccessHatch_UV,
    MTReactorAccessHatch_UHV,
    MTReactorAccessHatch_UEV,
    MTReactorAccessHatch_UIV,
    MTReactorHeatHatch_EV,
    MTReactorHeatHatch_IV,
    MTReactorHeatHatch_LuV,
    MTReactorHeatHatch_ZPM,
    MTReactorHeatHatch_UV,
    MTReactorHeatHatch_UHV,
    MTReactorHeatHatch_UEV,
    MTReactorHeatHatch_UIV,
    MTChemicalTwister,
    MTHugeChemicalReactor,
    MTModuleSpeed_IV,
    MTModuleSpeed_LuV,
    MTModuleSpeed_ZPM,
    MTModuleSpeed_UV,
    MTModuleSpeed_UHV,
    MTModuleSpeed_UEV,
    MTModuleSpeed_UIV,
    MTModuleSpeed_UMV,
    MTModuleSpeed_UXV,
    MTModuleSpeed_MAX,
    MTModuleEu_IV,
    MTModuleEu_LuV,
    MTModuleEu_ZPM,
    MTModuleEu_UV,
    MTModuleEu_UHV,
    MTModuleEu_UEV,
    MTModuleEu_UIV,
    MTModuleEu_UMV,
    MTModuleEu_UXV,
    MTModuleEu_MAX,
    MTModuleParallel_IV,
    MTModuleParallel_LuV,
    MTModuleParallel_ZPM,
    MTModuleParallel_UV,
    MTModuleParallel_UHV,
    MTModuleParallel_UEV,
    MTModuleParallel_UIV,
    MTModuleParallel_UMV,
    MTModuleParallel_UXV,
    MTModuleParallel_MAX;

    /** Reactor access hatches ordered EV..UIV (index {@code tier - MTReactorAccessHatch.MIN_TIER}). */
    public static final MTItemList[] REACTOR_ACCESS_HATCHES = { MTReactorAccessHatch_EV, MTReactorAccessHatch_IV,
        MTReactorAccessHatch_LuV, MTReactorAccessHatch_ZPM, MTReactorAccessHatch_UV, MTReactorAccessHatch_UHV,
        MTReactorAccessHatch_UEV, MTReactorAccessHatch_UIV };

    /** Reactor heat control hatches ordered EV..UIV (index {@code tier - MTReactorHeatHatch.MIN_TIER}). */
    public static final MTItemList[] REACTOR_HEAT_HATCHES = { MTReactorHeatHatch_EV, MTReactorHeatHatch_IV,
        MTReactorHeatHatch_LuV, MTReactorHeatHatch_ZPM, MTReactorHeatHatch_UV, MTReactorHeatHatch_UHV,
        MTReactorHeatHatch_UEV, MTReactorHeatHatch_UIV };

    /** Speed modules ordered IV..MAX (index {@code tier - IMTModule.MIN_TIER}). */
    public static final MTItemList[] SPEED_MODULES = { MTModuleSpeed_IV, MTModuleSpeed_LuV, MTModuleSpeed_ZPM,
        MTModuleSpeed_UV, MTModuleSpeed_UHV, MTModuleSpeed_UEV, MTModuleSpeed_UIV, MTModuleSpeed_UMV, MTModuleSpeed_UXV,
        MTModuleSpeed_MAX };

    /** EU discount modules ordered IV..MAX (index {@code tier - IMTModule.MIN_TIER}). */
    public static final MTItemList[] EU_MODULES = { MTModuleEu_IV, MTModuleEu_LuV, MTModuleEu_ZPM, MTModuleEu_UV,
        MTModuleEu_UHV, MTModuleEu_UEV, MTModuleEu_UIV, MTModuleEu_UMV, MTModuleEu_UXV, MTModuleEu_MAX };

    /** Parallel control modules ordered IV..MAX (index {@code tier - IMTModule.MIN_TIER}). */
    public static final MTItemList[] PARALLEL_MODULES = { MTModuleParallel_IV, MTModuleParallel_LuV,
        MTModuleParallel_ZPM, MTModuleParallel_UV, MTModuleParallel_UHV, MTModuleParallel_UEV, MTModuleParallel_UIV,
        MTModuleParallel_UMV, MTModuleParallel_UXV, MTModuleParallel_MAX };

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
