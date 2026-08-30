package com.MessTech.common.machine.Base;

import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;
import static gregtech.common.misc.WirelessNetworkManager.processInitialSettings;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.ArrayUtils;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Base class for MessTech multiblocks that can draw power from the GT wireless network.
 * <p>
 * Holds the shared wireless state (availability flag, enabled flag, parallel selector), the owner
 * UUID initialisation, wireless cost tracking / Waila display, recipe-processing lifecycle hooks,
 * and a generic wireless-mode processing path. Subclasses that need stricter validation should
 * override {@link #wirelessModeProcessOnce()} or the processing-logic validation hooks; the default
 * path follows the same rough order as the original design: find/validate a recipe, check the
 * wireless balance is sufficient, then deduct power and consume inputs before starting the recipe.
 */
public abstract class MTWirelessMultiMachineBase<T extends MTWirelessMultiMachineBase<T>>
    extends MTMultiMachineBase<T> {

    public MTWirelessMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTWirelessMultiMachineBase(String aName) {
        super(aName);
    }

    // region Wireless network state
    protected UUID ownerUUID;
    protected boolean EnableWirelessFunc = false;
    protected boolean EnableWireless = false;
    protected int wirelessParallel = 1;
    protected boolean isRecipeProcessing = false;
    protected BigInteger costingEU = BigInteger.ZERO;
    protected String costingEUText = "0";
    protected int cycleNum = 1;

    /** Whether the wireless feature is currently available (e.g. AAF + max tier). */
    public boolean isWirelessModeAvailable() {
        return EnableWirelessFunc;
    }

    /** Whether the player has activated wireless mode through the GUI. */
    public boolean isWirelessModeEnabled() {
        return EnableWireless;
    }

    /** Lombok-style getter used by MTDTPF processing code. */
    public boolean isEnableWireless() {
        return EnableWireless;
    }

    public void setEnableWireless(boolean value) {
        this.EnableWireless = value && EnableWirelessFunc && areEnergyHatchesEmpty();
    }

    public int getWirelessParallel() {
        return wirelessParallel;
    }

    public void setWirelessParallel(int value) {
        this.wirelessParallel = Math.max(1, value);
    }

    public void setEnableWirelessFunc(boolean value) {
        this.EnableWirelessFunc = value;
        if (!value) {
            this.EnableWireless = false;
        }
    }

    /** Default wireless mode for new instances. */
    public boolean getDefaultWirelessMode() {
        return false;
    }
    // endregion

    protected boolean isWirelessModeActive() {
        return isWirelessModeAvailable() && isWirelessModeEnabled();
    }

    public boolean areEnergyHatchesEmpty() {
        return mEnergyHatches.isEmpty() && mExoticEnergyHatches.isEmpty();
    }

    protected void initWirelessNetwork(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ownerUUID = processInitialSettings(aBaseMetaTileEntity);
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isServerSide()) {
            ownerUUID = processInitialSettings(aBaseMetaTileEntity);
        }
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && ownerUUID == null) {
            initWirelessNetwork(aBaseMetaTileEntity);
        }
    }

    // region Recipe lifecycle
    @Override
    public void startRecipeProcessing() {
        isRecipeProcessing = true;
        super.startRecipeProcessing();
    }

    @Override
    public void endRecipeProcessing() {
        super.endRecipeProcessing();
        isRecipeProcessing = false;
    }
    // endregion

    // region Wireless cost + Waila
    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        if (isWirelessModeEnabled()) {
            tag.setBoolean("wirelessMode", true);
            tag.setString("costingEUText", costingEUText);
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("wirelessMode")) {
            currentTip.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("machine.wirelessbase.mode"));
            currentTip.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.wirelessbase.cost")
                    + EnumChatFormatting.RESET
                    + ": "
                    + EnumChatFormatting.GOLD
                    + tag.getString("costingEUText")
                    + EnumChatFormatting.RESET
                    + " EU");
        }
    }
    // endregion

    // region Generic wireless processing
    /**
     * Hook called before wireless processing starts.
     */
    protected void prepareProcessing() {}

    /**
     * Wireless mode ignores the normal energy-hatch voltage limit.
     */
    protected void setupWirelessProcessingPowerLogic(ProcessingLogic logic) {
        logic.setAvailableVoltage(Long.MAX_VALUE);
        logic.setAvailableAmperage(1);
        logic.setAmperageOC(false);
    }

    /**
     * Extra EU multiplier for wireless processing (default 1).
     */
    public int getExtraEUCostMultiplier() {
        return 1;
    }

    /**
     * Recipe duration the machine should report while in wireless mode.
     */
    public int getWirelessModeProcessingTime() {
        return Math.max(1, mMaxProgresstime);
    }

    /**
     * Performs one wireless recipe cycle. The default implementation mirrors the original design:
     * run the recipe check, then require enough wireless EU before deducting it and letting the
     * processing logic consume inputs. Subclasses may override for stricter validation order.
     */
    public CheckRecipeResult wirelessModeProcessOnce() {
        if (!isRecipeProcessing) startRecipeProcessing();
        setupProcessingLogic(processingLogic);
        setupWirelessProcessingPowerLogic(processingLogic);

        CheckRecipeResult result = doCheckRecipe();
        if (!result.wasSuccessful()) {
            return result;
        }

        long calculatedEut = processingLogic.getCalculatedEut();
        int duration = processingLogic.getDuration();
        int multiplier = Math.max(1, getExtraEUCostMultiplier());
        BigInteger costEU = BigInteger.valueOf(calculatedEut)
            .multiply(BigInteger.valueOf(duration))
            .multiply(BigInteger.valueOf(multiplier));

        // Require the wireless balance to be sufficient before consuming anything.
        if (ownerUUID == null || getUserEU(ownerUUID).compareTo(costEU) < 0) {
            return CheckRecipeResultRegistry.insufficientStartupPower(costEU);
        }
        if (!addEUToGlobalEnergyMap(ownerUUID, costEU.negate())) {
            return CheckRecipeResultRegistry.insufficientStartupPower(costEU);
        }

        costingEU = costingEU.add(costEU);
        costingEUText = String.valueOf(costingEU);

        if (processingLogic.getOutputItems() != null) {
            mOutputItems = ArrayUtils.addAll(mOutputItems, processingLogic.getOutputItems());
        }
        if (processingLogic.getOutputFluids() != null) {
            mOutputFluids = ArrayUtils.addAll(mOutputFluids, processingLogic.getOutputFluids());
        }

        endRecipeProcessing();
        return result;
    }

    /**
     * Generic wireless-mode processing loop: resets cost tracking, then runs up to {@link #cycleNum}
     * wireless recipe cycles. This is opt-in for subclasses; machines with their own recipe validation
     * (such as MTDTPF) keep their own checkProcessing and do not call this.
     */
    public CheckRecipeResult checkProcessingWirelessLoop() {
        costingEU = BigInteger.ZERO;
        costingEUText = "0";
        prepareProcessing();
        if (!isEnableWireless()) return super.checkProcessing();

        boolean succeeded = false;
        CheckRecipeResult finalResult = CheckRecipeResultRegistry.SUCCESSFUL;
        for (int i = 0; i < cycleNum; i++) {
            CheckRecipeResult r = wirelessModeProcessOnce();
            if (!r.wasSuccessful()) {
                finalResult = r;
                break;
            }
            succeeded = true;
        }

        updateSlots();
        if (!succeeded) return finalResult;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = getWirelessModeProcessingTime();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }
    // endregion

    /**
     * Simulated wireless power check. Called before any input is consumed.
     */
    protected CheckRecipeResult checkWirelessPower(long eut, int duration, int maxParallel) {
        if (!isWirelessModeActive()) return CheckRecipeResultRegistry.SUCCESSFUL;
        if (ownerUUID == null) return CheckRecipeResultRegistry.insufficientPower(eut);
        BigInteger required = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(duration))
            .multiply(BigInteger.valueOf(maxParallel));
        if (getUserEU(ownerUUID).compareTo(required) < 0) {
            return CheckRecipeResultRegistry.insufficientStartupPower(required);
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * Safely starts a wireless recipe: deducts the actual EU cost from the wireless network first, and only
     * consumes inputs after the deduction succeeds.
     */
    protected CheckRecipeResult startWirelessRecipe(GTRecipe recipe, int parallel, long eut, int duration,
        FluidStack[] fluidInputs, ItemStack[] itemInputs) {
        if (!isWirelessModeActive()) return CheckRecipeResultRegistry.SUCCESSFUL;
        if (ownerUUID == null) return CheckRecipeResultRegistry.insufficientStartupPower(BigInteger.ZERO);
        BigInteger required = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(duration));
        if (!addEUToGlobalEnergyMap(ownerUUID, required.negate())) {
            return CheckRecipeResultRegistry.insufficientStartupPower(required);
        }
        recipe.consumeInput(parallel, fluidInputs, itemInputs);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setBoolean("enableWireless", EnableWireless);
        aNBT.setInteger("wirelessParallel", wirelessParallel);
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("enableWireless")) EnableWireless = aNBT.getBoolean("enableWireless");
        if (aNBT.hasKey("wirelessParallel")) wirelessParallel = Math.max(1, aNBT.getInteger("wirelessParallel"));
        super.loadNBTData(aNBT);
    }
}
