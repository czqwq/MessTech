package com.MessTech.common.machine.Base;

import static com.MessTech.common.util.Utils.filterValidMTEs;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
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

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Base class for MessTech generator multiblocks: unlike {@link MTMultiMachineBase} (which consumes EU from energy
 * hatches) these <b>produce</b> EU. Output is wired through dynamo / exotic-dynamo hatches by default, optionally
 * straight into the wireless network; the generator-oriented processing-logic energy handling ({@code lEUt} stays
 * positive), dynamo registration and shared wireless-owner/Waila support live here.
 * <p>
 * Wireless output counts as available by default; subclasses needing an unlock call
 * {@link #setEnableWirelessFunc(boolean)} during {@code checkMachine}.
 */
public abstract class MTGeneratorMultiBase<T extends MTGeneratorMultiBase<T>> extends MTMultiMachineBase<T> {

    // region Wireless output state
    protected UUID ownerUUID;
    protected boolean EnableWirelessFunc = true;
    protected boolean EnableWireless = getDefaultWirelessMode();
    protected boolean isRecipeProcessing = false;
    /** Wireless-only single-tick generation for output that would overflow a {@code long}; sent as BigInteger. */
    protected BigInteger wirelessGenerationPerTick = BigInteger.ZERO;
    /** MAX tier voltage (V[14] / Integer.MAX_VALUE - 7), used for compact total-generation display. */
    private static final BigInteger TOTAL_DISPLAY_CHUNK = BigInteger.valueOf(Integer.MAX_VALUE - 7);
    // endregion

    // region Class Constructor
    public MTGeneratorMultiBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTGeneratorMultiBase(String aName) {
        super(aName);
    }
    // endregion

    // region Wireless output mode
    public boolean isWirelessModeAvailable() {
        return EnableWirelessFunc;
    }

    public boolean isWirelessModeEnabled() {
        return EnableWireless;
    }

    /** Alias used by code shared with the wireless-consumption base. */
    public boolean isEnableWireless() {
        return EnableWireless;
    }

    public void setEnableWireless(boolean value) {
        this.EnableWireless = value && EnableWirelessFunc && areDynamoHatchesEmpty();
    }

    public void setEnableWirelessFunc(boolean value) {
        this.EnableWirelessFunc = value;
        if (!value) {
            this.EnableWireless = false;
        }
    }

    public boolean getDefaultWirelessMode() {
        return false;
    }

    protected boolean isWirelessModeActive() {
        return isWirelessModeAvailable() && isWirelessModeEnabled();
    }

    public boolean areDynamoHatchesEmpty() {
        return mDynamoHatches.isEmpty() && mExoticDynamoHatches.isEmpty();
    }

    public BigInteger getWirelessGenerationPerTick() {
        return wirelessGenerationPerTick;
    }

    public void setWirelessGenerationPerTick(BigInteger value) {
        this.wirelessGenerationPerTick = value != null && value.signum() > 0 ? value : BigInteger.ZERO;
    }

    public void resetWirelessGenerationPerTick() {
        this.wirelessGenerationPerTick = BigInteger.ZERO;
    }

    /**
     * @return The expected total EU of the current process, bonuses included: per-tick generation * full duration
     *         (does not grow with progress).
     */
    public BigInteger getCurrentProcessTotalEU() {
        if (mMaxProgresstime <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger perTick = wirelessGenerationPerTick.signum() > 0 ? wirelessGenerationPerTick
            : BigInteger.valueOf(Math.max(0, getCurrentGenerationEUt()));
        return perTick.multiply(BigInteger.valueOf(mMaxProgresstime));
    }

    private void initWirelessNetwork(IGregTechTileEntity aBaseMetaTileEntity) {
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
    // endregion

    // region Parallelism
    /**
     * Structural/upper parallel cap for generator machines, {@link Integer#MAX_VALUE} by default, so fuel/input based
     * auto-parallel is limited only by the fuel/inputs and the GT power-panel limit.
     */
    protected int generatorMaxParallel = Integer.MAX_VALUE;

    @Override
    public int getMaxParallelRecipes() {
        return Math.max(1, generatorMaxParallel);
    }

    public void setGeneratorMaxParallel(int maxParallel) {
        this.generatorMaxParallel = Math.max(1, maxParallel);
    }

    public int getGeneratorMaxParallel() {
        return generatorMaxParallel;
    }

    /**
     * @return The parallel batches startable from an available input/fuel amount: {@link #getTrueParallel()} (GT
     *         power-panel limit) capped by {@code availableAmount / requiredAmount}.
     */
    public int getParallelForAvailable(long availableAmount, long requiredAmount) {
        if (requiredAmount <= 0) {
            return 1;
        }
        long maxByInput = availableAmount / requiredAmount;
        long maxParallel = Math.min(getTrueParallel(), maxByInput);
        if (maxParallel <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, maxParallel);
    }
    // endregion

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

    // region Generator EU handling
    /** @return The EU/t currently generated, after efficiency is applied. */
    public long getCurrentGenerationEUt() {
        if (lEUt <= 0) {
            return 0;
        }
        return (lEUt * Math.max(mEfficiency, 0)) / 10000;
    }

    /** Starts a generating recipe with the given positive EU/t output and optional item/fluid byproducts. */
    protected CheckRecipeResult startGenerating(long outputEUt, int duration, ItemStack[] itemOutputs,
        FluidStack[] fluidOutputs) {
        if (outputEUt < 0) {
            outputEUt = -outputEUt;
        }
        if (duration <= 0) {
            duration = 1;
        }
        lEUt = outputEUt;
        wirelessGenerationPerTick = BigInteger.ZERO;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = duration;
        mOutputItems = itemOutputs;
        mOutputFluids = fluidOutputs;
        return CheckRecipeResultRegistry.GENERATING;
    }

    /** Generator logic keeps {@link #lEUt} positive; the consumer base would negate the ProcessingLogic result. */
    @Override
    protected void setEnergyUsage(ProcessingLogic processingLogic) {
        long calculatedEut = processingLogic.getCalculatedEut();
        lEUt = calculatedEut < 0 ? -calculatedEut : calculatedEut;
        // Generic ProcessingLogic cannot produce a >long value, so clear any stale BigInteger output.
        wirelessGenerationPerTick = BigInteger.ZERO;
    }

    /** Generic recipe path whose map's EU/t is the output: reports {@code GENERATING} after a successful run. */
    @Override
    public CheckRecipeResult checkProcessing() {
        CheckRecipeResult result = super.checkProcessing();
        if (result.wasSuccessful() && lEUt > 0) {
            return CheckRecipeResultRegistry.GENERATING;
        }
        return result;
    }

    /** Every running tick: feed the dynamo hatches (wired) or push the EU into the owner's wireless network. */
    @Override
    public boolean onRunningTick(ItemStack aStack) {
        IGregTechTileEntity tileEntity = getBaseMetaTileEntity();
        if (tileEntity != null && tileEntity.isServerSide()
            && isWirelessModeActive()
            && (lEUt > 0 || wirelessGenerationPerTick.signum() > 0)) {
            if (ownerUUID == null) {
                ownerUUID = processInitialSettings(tileEntity);
            }

            BigInteger toSend;
            if (wirelessGenerationPerTick.signum() > 0) {
                toSend = wirelessGenerationPerTick;
            } else {
                long generation = getCurrentGenerationEUt();
                toSend = generation > 0 ? BigInteger.valueOf(generation) : BigInteger.ZERO;
            }
            if (toSend.signum() > 0) {
                addEUToGlobalEnergyMap(ownerUUID, toSend);
            }
            return true;
        }
        return super.onRunningTick(aStack);
    }
    // endregion

    // region Waila
    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        final IGregTechTileEntity tileEntity = getBaseMetaTileEntity();
        if (tileEntity != null && tileEntity.isActive() && isWirelessModeEnabled()) {
            tag.setBoolean("wirelessMode", true);
            BigInteger generation = wirelessGenerationPerTick.signum() > 0 ? wirelessGenerationPerTick
                : BigInteger.valueOf(Math.max(0, getCurrentGenerationEUt()));
            if (generation.bitLength() <= 63) {
                tag.setLong("wirelessGenerationEUt", generation.longValue());
            } else {
                tag.setLong("wirelessGenerationEUt", Long.MAX_VALUE);
            }
            tag.setString("generatedEUText", generation.toString());
            tag.setString("totalGeneratedEUText", getCurrentProcessTotalEU().toString());
            // No wired-mode energy lines for wireless output.
            tag.removeTag("energyUsage");
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("wirelessMode")) {
            currentTip
                .add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("machine.generatorbase.mode"));
            currentTip.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.generatorbase.generation")
                    + EnumChatFormatting.RESET
                    + ": "
                    + EnumChatFormatting.GOLD
                    + formatWirelessEU(tag.getString("generatedEUText"))
                    + EnumChatFormatting.RESET
                    + " EU/t");
            currentTip.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.generatorbase.total")
                    + EnumChatFormatting.RESET
                    + ": "
                    + EnumChatFormatting.GOLD
                    + formatTotalWirelessEU(tag.getString("totalGeneratedEUText"))
                    + EnumChatFormatting.RESET);
        }
    }

    /**
     * @return The value with thousands separators and a compact scientific suffix, e.g. {@code 1,000,000 (1.00e6)};
     *         identical to the one in {@link MTWirelessMultiMachineBase}.
     */
    protected String formatWirelessEU(String raw) {
        try {
            BigInteger value = new BigInteger(raw);
            String grouped = String.format("%,d", value);
            String scientific = String.format("%.2e", value.doubleValue());
            scientific = scientific.replace("e+", "e")
                .replace("e-", "e-");
            scientific = scientific.replaceAll("e(-?)0(\\d)", "e$1$2");
            return grouped + " (" + scientific + ")";
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * @return The current-process total in the wireless format, plus a compact {@code (n MAX)} suffix above
     *         2,147,483,640 where {@code n = floor(total / 2147483640)}.
     */
    protected String formatTotalWirelessEU(String raw) {
        try {
            BigInteger value = new BigInteger(raw);
            String formatted = formatWirelessEU(raw);
            if (value.compareTo(TOTAL_DISPLAY_CHUNK) <= 0) {
                return formatted;
            }
            BigInteger count = value.divide(TOTAL_DISPLAY_CHUNK);
            return EnumChatFormatting.RESET + " ("
                + String.format("%,d", count)
                + " "
                + EnumChatFormatting.RED
                + "MAX"
                + EnumChatFormatting.RESET
                + ")";
        } catch (Exception e) {
            return raw;
        }
    }
    // endregion

    // region NBT
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("enableWireless", EnableWireless);
        aNBT.setString("wirelessGenerationPerTick", wirelessGenerationPerTick.toString());
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("enableWireless")) {
            EnableWireless = aNBT.getBoolean("enableWireless");
        }
        if (aNBT.hasKey("wirelessGenerationPerTick")) {
            try {
                wirelessGenerationPerTick = new BigInteger(aNBT.getString("wirelessGenerationPerTick"));
            } catch (NumberFormatException e) {
                wirelessGenerationPerTick = BigInteger.ZERO;
            }
            if (wirelessGenerationPerTick.signum() < 0) {
                wirelessGenerationPerTick = BigInteger.ZERO;
            }
        }
        super.loadNBTData(aNBT);
    }
    // endregion

    // region Hatch identification
    /** Accepts ordinary/exotic dynamo hatches plus the input/output hatches of {@link MTMultiMachineBase}. */
    @Override
    public boolean addToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return super.addToMachineList(aTileEntity, aBaseCasingIndex)
            || addDynamoToMachineList(aTileEntity, aBaseCasingIndex)
            || addExoticDynamoToMachineList(aTileEntity, aBaseCasingIndex)
            || addLaserSourceToMachineList(aTileEntity, aBaseCasingIndex);
    }

    @Override
    public boolean addEnergyOutput(long aEU) {
        if (aEU <= 0) {
            return true;
        }
        if (!mDynamoHatches.isEmpty() || !mExoticDynamoHatches.isEmpty()) {
            return addEnergyOutputMultipleDynamos(aEU, true);
        }
        return false;
    }

    @Override
    public boolean addEnergyOutputMultipleDynamos(long aEU, boolean aAllowMixedVoltageDynamos) {
        long totalEU = Math.max(aEU, 0);
        long injected = 0;

        for (MTEHatch aDynamo : filterValidMTEs(mDynamoHatches)) {
            injected = injectEnergyIntoDynamo(totalEU, injected, aDynamo);
            if (injected >= totalEU) {
                break;
            }
        }

        if (injected < totalEU) {
            for (MTEHatch aDynamo : filterValidMTEs(mExoticDynamoHatches)) {
                injected = injectEnergyIntoDynamo(totalEU, injected, aDynamo);
                if (injected >= totalEU) {
                    break;
                }
            }
        }

        return injected > 0;
    }

    private long injectEnergyIntoDynamo(long totalEU, long injected, MTEHatch aDynamo) {
        long leftToInject = totalEU - injected;
        if (leftToInject <= 0 || aDynamo == null || aDynamo.getBaseMetaTileEntity() == null) {
            return injected;
        }
        long voltage = aDynamo.maxEUOutput();
        if (voltage <= 0) {
            return injected;
        }
        long ampsToInject = Math.min(aDynamo.maxAmperesOut(), leftToInject / voltage);
        long remainder = leftToInject - (ampsToInject * voltage);

        aDynamo.getBaseMetaTileEntity()
            .increaseStoredEnergyUnits(voltage * ampsToInject, false);
        injected += voltage * ampsToInject;

        if (remainder > 0 && ampsToInject < aDynamo.maxAmperesOut()) {
            aDynamo.getBaseMetaTileEntity()
                .increaseStoredEnergyUnits(remainder, false);
            injected += remainder;
        }
        return injected;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        // Upstream GT does not clear the exotic dynamo list in MTEMultiBlockBase#clearHatches.
        mExoticDynamoHatches.clear();
    }
    // endregion
}
