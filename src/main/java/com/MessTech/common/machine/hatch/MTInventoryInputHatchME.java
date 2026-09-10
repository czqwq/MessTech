package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * "Inventory Input Hatch (ME)"
 * <p>
 * Like the GT5U stocking ME input hatch, but instead of exposing at most 16 configured fluid types it
 * snapshots the whole AE fluid network during every recipe processing check, so a multiblock can see
 * every fluid type stored in ME in one recipe check.
 * <p>
 * It uses the advanced stocking ME hatch GUI (auto-pull toggle is available). When auto-pull is on,
 * the GUI still displays only the first 16 fluids from AE, while actual recipe processing uses the
 * complete snapshot from {@link #inventorySlots} (filtered by the configured minimum amount).
 * The per-recipe-check snapshot is refreshed on every {@link #startRecipeProcessing()}, matching the
 * GT5U stocking hatch's automatic per-check pull notification.
 * When auto-pull is off, it behaves like the normal GT5U stocking hatch: only the manually configured
 * 16 fluids are exposed and no auto pull happens.
 */
public class MTInventoryInputHatchME extends MTEHatchInputME {

    protected final List<MTEHatchInputME.Slot> inventorySlots = new ArrayList<>();

    public MTInventoryInputHatchME(int aID, String aName, String aNameRegional) {
        super(aID, true, aName, aNameRegional);
    }

    public MTInventoryInputHatchME(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, true, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTInventoryInputHatchME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            // EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.meinventory.inputhatch.desc.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.meinventory.inputhatch.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.meinventory.inputhatch.desc.2") };
    }

    @Override
    public FluidStack[] getStoredFluids() {
        if (!isAllowedToWork()) {
            return new FluidStack[0];
        }
        if (!processingRecipe) {
            return new FluidStack[0];
        }

        List<FluidStack> fluids = new ArrayList<>(inventorySlots.size());
        for (MTEHatchInputME.Slot slot : inventorySlots) {
            if (slot != null && slot.extracted != null) {
                fluids.add(slot.extracted);
            }
        }
        return fluids.toArray(new FluidStack[0]);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        if (side != ForgeDirection.UNKNOWN || !isAllowedToWork() || !processingRecipe) {
            return new FluidTankInfo[0];
        }

        List<FluidTankInfo> tanks = new ArrayList<>(inventorySlots.size());
        for (MTEHatchInputME.Slot slot : inventorySlots) {
            if (slot != null && slot.extracted != null) {
                tanks.add(new FluidTankInfo(slot.extracted, Integer.MAX_VALUE));
            }
        }
        return tanks.toArray(new FluidTankInfo[0]);
    }

    @Override
    public void startRecipeProcessing() {
        cachedActivity = isAllowedToWork();
        processingRecipe = true;
        inventorySlots.clear();

        if (!cachedActivity) {
            return;
        }

        if (autoPullFluidList) {
            // Auto-pull mode: the GUI only shows the first 16 fluids, but every recipe check pulls a
            // fresh full snapshot from AE. This mirrors the original hatch behavior where the recipe
            // check itself notifies the hatch to refresh its available contents, allowing continuous
            // feeding even while autoPullRefreshTime governs the 16-slot GUI refresh.
            try {
                IMEMonitor<IAEFluidStack> sg = getProxy().getStorage()
                    .getFluidInventory();
                for (IAEFluidStack aeStack : sg.getStorageList()) {
                    if (aeStack == null || aeStack.getStackSize() < minAutoPullAmount) continue;

                    FluidStack extracted = aeStack.getFluidStack();
                    if (extracted == null) continue;

                    MTEHatchInputME.Slot slot = new MTEHatchInputME.Slot(GTUtility.copyAmount(1, extracted));
                    slot.extracted = extracted;
                    slot.extractedAmount = extracted.amount;
                    inventorySlots.add(slot);
                }
            } catch (GridAccessException e) {
                inventorySlots.clear();
            }
        } else {
            // Manual stocking mode: only the 16 fluids configured in the GUI are exposed.
            updateAllInformationSlots();
            for (MTEHatchInputME.Slot slot : slots) {
                if (slot != null && slot.extracted != null && slot.extracted.amount > 0) {
                    inventorySlots.add(slot);
                }
            }
        }
    }

    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        CheckRecipeResult checkRecipeResult = CheckRecipeResultRegistry.SUCCESSFUL;

        IMEMonitor<IAEFluidStack> sg;
        IEnergyGrid energy;

        try {
            AENetworkProxy proxy = getProxy();
            if (!proxy.isReady()) proxy.onReady();

            sg = proxy.getStorage()
                .getFluidInventory();
            energy = proxy.getEnergy();
        } catch (GridAccessException e) {
            processingRecipe = false;
            inventorySlots.clear();
            controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_hatch_fail_extraction");
        }

        for (MTEHatchInputME.Slot slot : inventorySlots) {
            if (slot == null || slot.extracted == null || slot.extractedAmount == 0) continue;

            int toExtract = slot.extractedAmount - slot.extracted.amount;
            if (toExtract <= 0) continue;

            slot.extractedAmount = slot.extracted.amount;

            IAEFluidStack request = AEFluidStack.create(slot.extracted);
            request.setStackSize(toExtract);

            IAEFluidStack result = Platform.poweredExtraction(energy, sg, request, getRequestSource());

            if (result == null || result.getStackSize() != toExtract) {
                controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
                checkRecipeResult = SimpleCheckRecipeResult
                    .ofFailurePersistOnShutdown("stocking_hatch_fail_extraction");
            }
        }

        processingRecipe = false;
        inventorySlots.clear();
        return checkRecipeResult;
    }

    protected BaseActionSource getRequestSource() {
        if (requestSource == null) {
            requestSource = new MachineSource((IActionHost) getBaseMetaTileEntity());
        }
        return requestSource;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, boolean doDrain) {
        return drain(side, fluid, fluid == null ? 0 : fluid.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, int amount, boolean doDrain) {
        if (side != ForgeDirection.UNKNOWN || fluid == null) return null;

        if (processingRecipe) {
            MTEHatchInputME.Slot slot = getMatchingSlot(fluid, true);
            if (slot == null || slot.extracted == null) return null;

            int toDrain = Math.min(slot.extracted.amount, amount);
            FluidStack drained = GTUtility.copyAmount(toDrain, slot.extracted);
            if (doDrain) {
                slot.extracted.amount -= toDrain;
            }
            return drained;
        }

        // Outside recipe processing, allow direct simulated/real extraction from the whole ME fluid
        // network for the requested fluid.
        IMEMonitor<IAEFluidStack> sg;
        IEnergyGrid energy;
        try {
            AENetworkProxy proxy = getProxy();
            if (!proxy.isReady()) proxy.onReady();
            sg = proxy.getStorage()
                .getFluidInventory();
            energy = proxy.getEnergy();
        } catch (GridAccessException e) {
            return null;
        }

        IAEFluidStack request = AEFluidStack.create(fluid);
        request.setStackSize(amount);
        IAEFluidStack result = doDrain ? Platform.poweredExtraction(energy, sg, request, getRequestSource())
            : sg.extractItems(request, Actionable.SIMULATE, getRequestSource());

        return result == null ? null : result.getFluidStack();
    }

    @Override
    public FluidStack getFirstValidStack() {
        return getFirstValidStack(false);
    }

    @Override
    public FluidStack getFirstValidStack(boolean slotsMustMatch) {
        if (slotsMustMatch) {
            FluidStack firstValid = null;
            for (MTEHatchInputME.Slot slot : inventorySlots) {
                if (slot == null || slot.extracted == null) continue;
                if (firstValid == null) {
                    firstValid = slot.extracted;
                } else if (!GTUtility.areFluidsEqual(firstValid, slot.extracted)) {
                    return null;
                }
            }
            return firstValid;
        }

        for (MTEHatchInputME.Slot slot : inventorySlots) {
            if (slot != null && slot.extracted != null) return slot.extracted;
        }
        return null;
    }

    protected MTEHatchInputME.Slot getMatchingSlot(FluidStack fluidStack, boolean requireExtracted) {
        if (fluidStack == null || !isAllowedToWork()) return null;
        for (MTEHatchInputME.Slot slot : inventorySlots) {
            if (slot == null) continue;
            if (requireExtracted && (slot.extracted == null || slot.extractedAmount == 0)) continue;
            if (!GTUtility.areFluidsEqual(slot.config, fluidStack)) continue;
            return slot;
        }
        return null;
    }
}
