package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/**
 * "Inventory Input Bus (ME)"
 * <p>
 * Like the GT5U stocking ME input bus, but instead of exposing at most 16 configured item types it
 * snapshots the whole AE item network during every recipe processing check, so a multiblock can see
 * every item type stored in ME in one recipe check.
 * <p>
 * It uses the advanced stocking ME hatch GUI (auto-pull toggle is available). When auto-pull is on,
 * the GUI still displays only the first 16 items from AE, while actual recipe processing uses the
 * complete snapshot from {@link #inventorySlots} (filtered by the configured minimum stock size).
 * The per-recipe-check snapshot is refreshed on every {@link #startRecipeProcessing()}, matching the
 * GT5U stocking hatch's automatic per-check pull notification.
 * When auto-pull is off, it behaves like the normal GT5U stocking bus: only the manually configured
 * 16 slots are exposed and no auto pull happens.
 */
public class MTInventoryInputBusME extends MTEHatchInputBusME {

    protected final List<MTEHatchInputBusME.Slot> inventorySlots = new ArrayList<>();

    public MTInventoryInputBusME(int aID, String aName, String aNameRegional) {
        super(aID, true, aName, aNameRegional);
    }

    public MTInventoryInputBusME(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, true, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTInventoryInputBusME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            // EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.meinventory.inputbus.desc.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.meinventory.inputbus.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.meinventory.inputbus.desc.2") };
    }

    @Override
    public int getSizeInventory() {
        // Circuit + manual slot are always present; stock slots only appear while a recipe is running.
        return inventorySlots.size() + 2;
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        int virtualSlotOffset = processingRecipe ? inventorySlots.size() : 0;

        if (slotIndex < 0 || slotIndex >= getSizeInventory()) return null;

        if (slotIndex == getCircuitSlot() + virtualSlotOffset) return mInventory[getCircuitSlot()];
        if (slotIndex == getManualSlot() + virtualSlotOffset) return mInventory[getManualSlot()];
        if (!processingRecipe) return null;
        if (slotIndex >= inventorySlots.size()) return null;

        MTEHatchInputBusME.Slot slot = inventorySlots.get(slotIndex);
        return slot == null ? null : slot.extracted;
    }

    @Override
    public void startRecipeProcessing() {
        cachedActivity = isAllowedToWork();
        processingRecipe = true;
        inventorySlots.clear();

        if (!cachedActivity) {
            return;
        }

        if (autoPullItemList) {
            // Auto-pull mode: the GUI only shows the first 16 items, but every recipe check pulls a
            // fresh full snapshot from AE. This mirrors the original hatch behavior where the recipe
            // check itself notifies the hatch to refresh its available contents, allowing continuous
            // feeding even while autoPullRefreshTime governs the 16-slot GUI refresh.
            try {
                IMEMonitor<IAEItemStack> sg = getProxy().getStorage()
                    .getItemInventory();
                for (IAEItemStack aeStack : sg.getStorageList()) {
                    if (aeStack == null || aeStack.getStackSize() < minAutoPullStackSize) continue;

                    ItemStack extracted = aeStack.getItemStack();
                    if (extracted == null) continue;

                    MTEHatchInputBusME.Slot slot = new MTEHatchInputBusME.Slot(GTUtility.copyAmount(1, extracted));
                    slot.extracted = extracted;
                    slot.extractedAmount = extracted.stackSize;
                    inventorySlots.add(slot);
                }
            } catch (GridAccessException e) {
                inventorySlots.clear();
            }
        } else {
            // Manual stocking mode: only the 16 slots configured in the GUI are exposed.
            updateAllInformationSlots();
            for (MTEHatchInputBusME.Slot slot : slots) {
                if (slot != null && slot.extracted != null && slot.extracted.stackSize > 0) {
                    inventorySlots.add(slot);
                }
            }
        }
    }

    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        CheckRecipeResult checkRecipeResult = CheckRecipeResultRegistry.SUCCESSFUL;

        IMEMonitor<IAEItemStack> sg;
        IEnergyGrid energy;

        try {
            AENetworkProxy proxy = getProxy();
            if (!proxy.isReady()) proxy.onReady();

            sg = proxy.getStorage()
                .getItemInventory();
            energy = proxy.getEnergy();
        } catch (GridAccessException e) {
            processingRecipe = false;
            inventorySlots.clear();
            controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_bus_fail_extraction");
        }

        for (MTEHatchInputBusME.Slot slot : inventorySlots) {
            if (slot == null || slot.extracted == null || slot.extractedAmount == 0) continue;

            int toExtract = slot.extractedAmount - slot.extracted.stackSize;
            if (toExtract <= 0) continue;

            IAEItemStack request = slot.createAEStack(toExtract);
            IAEItemStack result = Platform.poweredExtraction(energy, sg, request, getRequestSource());

            if (result == null || result.getStackSize() != toExtract) {
                controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
                checkRecipeResult = SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_bus_fail_extraction");
            }
        }

        processingRecipe = false;
        inventorySlots.clear();
        return checkRecipeResult;
    }

    @Override
    public ItemStack getFirstValidStack() {
        return getFirstValidStack(false);
    }

    @Override
    public ItemStack getFirstValidStack(boolean slotsMustMatch) {
        if (slotsMustMatch) {
            ItemStack firstValid = null;
            for (MTEHatchInputBusME.Slot slot : inventorySlots) {
                if (slot == null || slot.extracted == null) continue;
                if (firstValid == null) {
                    firstValid = slot.extracted;
                } else if (!GTUtility.areStacksEqual(firstValid, slot.extracted)) {
                    return null;
                }
            }
            return firstValid;
        }

        for (MTEHatchInputBusME.Slot slot : inventorySlots) {
            if (slot != null && slot.extracted != null) return slot.extracted;
        }
        return null;
    }

    @Override
    public ItemStack findResource(ItemStack[] targets) {
        if (targets == null || targets.length == 0) return null;
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack slotStack = getStackInSlot(i);
            if (slotStack == null) continue;
            for (ItemStack target : targets) {
                if (target != null && GTUtility.areStacksEqual(slotStack, target)) {
                    return slotStack.copy();
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasResource(ItemStack[] targets) {
        if (targets == null || targets.length == 0) return false;
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack slotStack = getStackInSlot(i);
            if (slotStack == null) continue;
            for (ItemStack target : targets) {
                if (target != null && GTUtility.areStacksEqual(slotStack, target)) return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack removeResource(ItemStack[] targets, int amount) {
        if (targets == null || targets.length == 0 || amount <= 0 || getBaseMetaTileEntity() == null) return null;

        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack slotStack = getStackInSlot(i);
            if (slotStack == null || slotStack.stackSize < amount) continue;
            for (ItemStack target : targets) {
                if (target != null && GTUtility.areStacksEqual(slotStack, target)) {
                    ItemStack removed = decrStackSize(i, amount);
                    if (removed != null) {
                        updateSlots();
                        return removed;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public ItemStack removeAllResource(ItemStack[] targets) {
        if (targets == null || targets.length == 0 || getBaseMetaTileEntity() == null) return null;

        ItemStack result = null;
        boolean updated = false;
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack slotStack = getStackInSlot(i);
            if (slotStack == null) continue;
            for (ItemStack target : targets) {
                if (target != null && GTUtility.areStacksEqual(slotStack, target)) {
                    ItemStack removed = decrStackSize(i, slotStack.stackSize);
                    if (removed != null) {
                        if (result == null) {
                            result = removed.copy();
                        } else if (GTUtility.areStacksEqual(result, removed)) {
                            result.stackSize += removed.stackSize;
                        }
                        updated = true;
                    }
                }
            }
        }
        if (updated) updateSlots();
        return result;
    }
}
