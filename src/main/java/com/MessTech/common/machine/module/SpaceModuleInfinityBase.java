package com.MessTech.common.machine.module;

import static gregtech.common.misc.WirelessNetworkManager.processInitialSettings;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.module.SpaceModuleInfinityGui;
import com.MessTech.common.machine.IMTSpaceElevatorModule;
import com.MessTech.common.machine.MTSpaceElevator;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMultiInput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.structure.error.TranslatableText;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import lombok.Getter;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Shared base for the wireless MessTech space modules: they extend GTNH's native {@link TileEntityModuleBase}, so
 * they are real Space Elevator project modules rather than machines standing next to one. Each subclass builds the
 * elevator's module slot via {@link #buildModuleStructure} - only a controller standing on such a slot is registered.
 * <p>
 * Two elevators can mount these modules: GT5U's own Space Elevator calls {@code connect(TileEntitySpaceElevator)},
 * while MessTech's copied {@link MTSpaceElevator} calls {@link #connectToElevator(MTSpaceElevator)}. Whichever one
 * takes the module in is remembered and read back by {@link #isMountedOnFormedElevator()}; the module is disconnected
 * when its elevator stops working.
 * <p>
 * The module is 2 wide, 5 long and 2 deep, so only the rotations the elevator's own modules use fit the slot. Power
 * comes from the GT wireless network, not from the elevator's internal EU buffer.
 */
@Getter
public abstract class SpaceModuleInfinityBase<T extends SpaceModuleInfinityBase<T>> extends TileEntityModuleBase
    implements ISurvivalConstructable, IMTSpaceElevatorModule {

    /**
     * @return the GT5U Space Elevator that mounted this module, or null (MessTech elevators are in the field below).
     */
    protected TileEntitySpaceElevator parentElevator;
    /** @return the MessTech Space Elevator that mounted this module, or null. */
    protected MTSpaceElevator messTechElevator;

    private int crossRecipeParallel = 1;
    protected boolean shutdownRequestedDuringBatch = false;

    // One continuous main batch, one sub-output per tick, accumulated here.
    protected int batchRemainingTasks = 0;
    protected ArrayList<ItemStack> batchItemOutputs = new ArrayList<>();
    protected ArrayList<FluidStack> batchFluidOutputs = new ArrayList<>();

    protected static IIconContainer ScreenOFF;
    protected static IIconContainer ScreenON;

    // Wireless state
    protected UUID ownerUUID;
    protected boolean EnableWirelessFunc = false;
    protected boolean EnableWireless = false;
    protected int wirelessParallel = 1;
    protected BigInteger costingEU = BigInteger.ZERO;
    protected String costingEUText = "0";

    public SpaceModuleInfinityBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 14, 5, 5);
        setEnableWirelessFunc(true);
        setEnableWireless(true);
        setWirelessParallel(1);
    }

    public SpaceModuleInfinityBase(String aName) {
        super(aName, 14, 5, 5);
        setEnableWirelessFunc(true);
        setEnableWireless(true);
        setWirelessParallel(1);
    }

    /**
     * The same module on a tier of its own; the Infinity modules are all 14, 5, 5. {@code TileEntityModuleBase}
     * indexes {@code GTValues.V[tTier]} and shifts the EU buffer by {@code tTier - 7}, so the tier must be a real
     * voltage table entry (0..15) and at least 7.
     *
     * @param aTier         voltage tier of this module, see {@code TileEntityModuleBase#getTier()}
     * @param aModuleTier   module tier, how far up the elevator's own ladder it sits
     * @param aMinMotorTier motor tier of the Space Elevator needed to run it, 1..5
     */
    protected SpaceModuleInfinityBase(int aID, String aName, String aNameRegional, int aTier, int aModuleTier,
        int aMinMotorTier) {
        super(aID, aName, aNameRegional, aTier, aModuleTier, aMinMotorTier);
        setEnableWirelessFunc(true);
        setEnableWireless(true);
        setWirelessParallel(1);
    }

    protected SpaceModuleInfinityBase(String aName, int aTier, int aModuleTier, int aMinMotorTier) {
        super(aName, aTier, aModuleTier, aMinMotorTier);
        setEnableWirelessFunc(true);
        setEnableWireless(true);
        setWirelessParallel(1);
    }

    public void setCrossRecipeParallel(int value) {
        this.crossRecipeParallel = Math.clamp(value, 1, 64);
    }

    public void setWirelessParallel(int value) {
        this.wirelessParallel = Math.max(1, value);
    }

    // region Elevator module slot

    /**
     * The Space Elevator's module slot as {@link TileEntityModuleBase} defines it: two rows of two, five layers,
     * controller on the second layer of the first row. The elevator only registers a controller on that slot.
     *
     * @param hatchAdder adder that harvests the hatches of this module from the {@code H} positions
     */
    @SuppressWarnings("unchecked")
    protected IStructureDefinition<T> buildModuleStructure(IGTHatchAdder<? super T> hatchAdder) {
        // The wildcard lets callers pass a method reference on their own class; the element is this module's, hence
        // the cast.
        IStructureElement<T> hatchElement = (IStructureElement<T>) GTStructureUtility.ofHatchAdderOptional(
            hatchAdder,
            TileEntitySpaceElevator.CASING_INDEX_BASE,
            1,
            GregTechAPI.sBlockCasingsSE,
            0);
        return StructureDefinition.<T>builder()
            .addShape(
                "main",
                StructureUtility
                    .transpose(new String[][] { { "H", "H" }, { "~", "H" }, { "H", "H" }, { "H", "H" }, { "H", "H" } }))
            .addElement('H', hatchElement)
            .build();
    }

    /**
     * @return {@code tModuleTier}: what the native modules read back through {@code getModuleTier()}; recipes only
     *         match when their {@code MODULE_TIER} metadata is not above this value.
     */
    public int getModuleTier() {
        return tModuleTier;
    }

    /** @return true while linked to either a GT5U or a MessTech Space Elevator. */
    public boolean hasLinkedElevator() {
        return parentElevator != null || messTechElevator != null;
    }

    /** @return true when the linked elevator has computation (data input) hatches of its own. */
    public boolean linkedElevatorHasDataHatches() {
        if (messTechElevator != null) {
            return messTechElevator.hasDataHatches();
        }
        return parentElevator != null && parentElevator.hasDataHatches();
    }

    /** @return the fluids stored in the linked elevator's hatches (the miner can take plasma from there). */
    public List<FluidStack> linkedElevatorStoredFluids() {
        if (messTechElevator != null) {
            return messTechElevator.getStoredFluids();
        }
        if (parentElevator != null) {
            return parentElevator.getStoredFluids();
        }
        return Collections.emptyList();
    }

    /** @return true while mounted on a formed elevator whose motor tier is at least {@code getNeededMotorTier()}. */
    public boolean isMountedOnFormedElevator() {
        if (messTechElevator != null) {
            return messTechElevator.getMotorTier() >= getNeededMotorTier();
        }
        return parentElevator != null && parentElevator.getMotorTier() >= getNeededMotorTier();
    }

    /**
     * Adds the "why is this module not running" structure error: the elevator has not taken the module in (structure
     * incomplete or switched off) or its motor tier is below {@code getNeededMotorTier()}.
     *
     * @param errors error list of the structure check
     * @param level  level suffix this module needs, e.g. {@code V} for {@code MK-V}
     */
    protected void addElevatorError(List<StructureError> errors, String level) {
        if (!hasLinkedElevator()) {
            errors.add(StructureErrors.of("machine.spacemodule.elevator_not_linked"));
            return;
        }
        errors.add(
            StructureErrors.of("machine.spacemodule.elevator_level_too_low", TranslatableText.literal("MK-" + level)));
    }

    /** Reports the elevator linkage once the module's own structure is complete ({@code checkPiece}). */
    protected void checkElevatorLinkage(List<StructureError> errors, String level) {
        if (!isMountedOnFormedElevator()) {
            addElevatorError(errors, level);
        }
    }
    // endregion

    // region Wireless API

    public boolean isWirelessModeAvailable() {
        return EnableWirelessFunc;
    }

    public boolean isWirelessModeEnabled() {
        return EnableWireless;
    }

    public boolean isEnableWireless() {
        return EnableWireless;
    }

    public void setEnableWireless(boolean value) {
        this.EnableWireless = value && EnableWirelessFunc && areEnergyHatchesEmpty();
    }

    public int getWirelessParallel() {
        return wirelessParallel;
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

    public boolean areEnergyHatchesEmpty() {
        return mEnergyHatches.isEmpty() && mExoticEnergyHatches.isEmpty() && eEnergyMulti.isEmpty();
    }

    protected void initWirelessNetwork(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ownerUUID = processInitialSettings(aBaseMetaTileEntity);
        }
    }

    @Override
    public void onFirstTick_EM(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick_EM(aBaseMetaTileEntity);
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
            currentTip.add(
                net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("machine.wirelessbase.mode"));
            currentTip.add(
                net.minecraft.util.EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.wirelessbase.cost")
                    + net.minecraft.util.EnumChatFormatting.RESET
                    + ": "
                    + net.minecraft.util.EnumChatFormatting.GOLD
                    + formatWirelessEU(tag.getString("costingEUText"))
                    + net.minecraft.util.EnumChatFormatting.RESET
                    + " EU");
        }
    }

    /** {@code 1,000,000 (1.00e6)}: thousands separators plus a compact scientific-notation suffix. */
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
    // endregion

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setBoolean("enableWireless", EnableWireless);
        aNBT.setInteger("wirelessParallel", wirelessParallel);
        super.saveNBTData(aNBT);
        aNBT.setInteger("crossRecipeParallel", crossRecipeParallel);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("enableWireless")) EnableWireless = aNBT.getBoolean("enableWireless");
        if (aNBT.hasKey("wirelessParallel")) wirelessParallel = Math.max(1, aNBT.getInteger("wirelessParallel"));
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("crossRecipeParallel")) {
            crossRecipeParallel = Math.max(1, aNBT.getInteger("crossRecipeParallel"));
        }
    }

    // region Native module lifecycle

    @Override
    public void connect(TileEntitySpaceElevator parent) {
        this.parentElevator = parent;
        this.messTechElevator = null;
        super.connect(parent);
    }

    @Override
    public void connectToElevator(MTSpaceElevator elevator) {
        this.messTechElevator = elevator;
        this.parentElevator = null;
        connect();
    }

    @Override
    public void disconnect() {
        this.parentElevator = null;
        this.messTechElevator = null;
        super.disconnect();
    }

    @Override
    public long increaseStoredEU(long amount) {
        // Wireless modules draw from the network only; no elevator EU buffer is used.
        return 0;
    }

    @Override
    protected long getAvailableData_EM() {
        if (eInputData.isEmpty()) {
            if (messTechElevator != null) {
                return messTechElevator.getAvailableDataForModules();
            }
            if (parentElevator == null) return 0;
            return parentElevator.getAvailableDataForModules();
        }
        return super.getAvailableData_EM();
    }

    /** @return the computation this module may draw from the connected Space Elevator. */
    public long getAvailableDataForModule() {
        return getAvailableData_EM();
    }

    /** Keeps a fake internal EU buffer while valid: power is paid wirelessly, nothing may halt on an empty tank. */
    @Override
    protected void chargeController_EM(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mMachine) {
            setEUVar(maxEUStore());
        }
    }

    @Override
    public boolean isAllowedToWork() {
        // Mid-batch, keep running even if toggled off, so every planned output finishes.
        if (hasPendingBatchWork()) {
            return true;
        }
        return super.isAllowedToWork();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide() && hasPendingBatchWork() && !aBaseMetaTileEntity.isAllowedToWork()) {
            // Stop requested (player or redstone) mid-batch: remember it and force the machine to continue.
            shutdownRequestedDuringBatch = true;
            aBaseMetaTileEntity.enableWorking();
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && !hasPendingBatchWork() && shutdownRequestedDuringBatch) {
            // Batch finished: honour the requested shutdown.
            shutdownRequestedDuringBatch = false;
            aBaseMetaTileEntity.disableWorking();
        }
    }
    // endregion

    // region Tick-batched processing

    /** @return number of sub-outputs in the current tick-batched batch. */
    protected abstract int getBatchTaskCount();

    /**
     * Generates one sub-output into {@link #batchItemOutputs} / {@link #batchFluidOutputs}.
     *
     * @return false once there are no more valid sub-outputs.
     */
    protected abstract boolean generateOneBatchTask();

    /** @return base duration of the main batch, before the 1-tick distributed sub-outputs. */
    protected abstract int getMainBatchDuration();

    /** Called when a new batch starts; subclasses reset per-batch cursors here. */
    protected void onBatchStart() {}

    /** Starts a continuous tick-batched batch: the first sub-output at once (a valid recipe), the rest one per tick. */
    protected CheckRecipeResult startBatchedProcessing() {
        batchItemOutputs.clear();
        batchFluidOutputs.clear();
        mOutputItems = null;
        mOutputFluids = null;
        batchRemainingTasks = getBatchTaskCount();
        if (batchRemainingTasks <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        onBatchStart();
        if (!generateOneBatchTask()) {
            batchRemainingTasks = 0;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        batchRemainingTasks--;
        mMaxProgresstime = getMainBatchDuration() + batchRemainingTasks;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        boolean result = super.onRunningTick(aStack);
        if (result && batchRemainingTasks > 0) {
            if (generateOneBatchTask()) {
                batchRemainingTasks--;
            } else {
                batchRemainingTasks = 0;
            }
        }
        // GUI/Waila shows the running total; modules that finish the whole batch in checkProcessing must not lose their
        // real mOutputItems to the (empty) lists.
        if (mMaxProgresstime > 0 && usesTickBatchedOutputs()) {
            mOutputItems = batchItemOutputs.toArray(new ItemStack[0]);
            mOutputFluids = batchFluidOutputs.toArray(new FluidStack[0]);
        }
        return result;
    }

    /** @return true if outputs are spread over the ticks (miner/pump), false if all at once in checkProcessing. */
    protected boolean usesTickBatchedOutputs() {
        return true;
    }

    @Override
    public int getMaxParallelRecipes() {
        return Math.max(1, getWirelessParallel());
    }

    /** @return true while a tick-batched output sequence is still running and must not be interrupted. */
    protected boolean hasPendingBatchWork() {
        return batchRemainingTasks > 0;
    }

    /** TT module recipe hook: override this, not the final {@code checkProcessing()}, like every native module. */
    @Override
    public @NotNull CheckRecipeResult checkProcessing_EM() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        int parallel = Math.max(1, getWirelessParallel());
        long eut = gregtech.api.enums.GTValues.V[14] + (long) (parallel - 1) * gregtech.api.enums.GTValues.V[13];
        int duration = 20;
        CheckRecipeResult result = validateWirelessPowerForRecipe(eut, duration, 1);
        if (!result.wasSuccessful()) return result;
        java.math.BigInteger cost = java.math.BigInteger.valueOf(eut)
            .multiply(java.math.BigInteger.valueOf(duration));
        if (ownerUUID == null
            || !gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, cost.negate())) {
            return CheckRecipeResultRegistry.insufficientStartupPower(cost);
        }
        costingEU = cost;
        costingEUText = String.valueOf(cost);
        // Wireless power was already deducted in one lump; don't drain energy hatches (instant power-loss shutdown).
        lEUt = 0;
        mMaxProgresstime = duration;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** Simulated wireless power check, called before any input is consumed. */
    protected CheckRecipeResult checkWirelessPower(long eut, int duration, int maxParallel) {
        if (!isWirelessModeActive()) return CheckRecipeResultRegistry.SUCCESSFUL;
        if (ownerUUID == null) return CheckRecipeResultRegistry.insufficientPower(eut);
        BigInteger required = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(duration))
            .multiply(BigInteger.valueOf(maxParallel));
        if (gregtech.common.misc.WirelessNetworkManager.getUserEU(ownerUUID)
            .compareTo(required) < 0) {
            return CheckRecipeResultRegistry.insufficientStartupPower(required);
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** The network must cover {@code eut * duration * maxParallel}, else an insufficient-power result. */
    protected CheckRecipeResult validateWirelessPowerForRecipe(long eut, int duration, int maxParallel) {
        if (!isEnableWireless()) return CheckRecipeResultRegistry.SUCCESSFUL;
        if (ownerUUID == null) return CheckRecipeResultRegistry.insufficientPower(eut);
        BigInteger required = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(duration))
            .multiply(BigInteger.valueOf(maxParallel));
        if (gregtech.common.misc.WirelessNetworkManager.getUserEU(ownerUUID)
            .compareTo(required) < 0) {
            return CheckRecipeResultRegistry.insufficientStartupPower(required);
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** Wireless power setup for {@link ProcessingLogic} modules: the wireless network is the only power source. */
    protected void setupWirelessProcessingPowerLogic(ProcessingLogic logic) {
        logic.setAvailableVoltage(Long.MAX_VALUE);
        logic.setAvailableAmperage(1);
        logic.setAmperageOC(false);
    }

    /** One wireless recipe cycle through the inherited {@link ProcessingLogic}, paid from the wireless network. */
    protected CheckRecipeResult wirelessModeProcessOnce() {
        if (processingLogic == null) return CheckRecipeResultRegistry.NO_RECIPE;

        setupProcessingLogic(processingLogic);
        setupWirelessProcessingPowerLogic(processingLogic);

        CheckRecipeResult result = doCheckRecipe();
        if (!result.wasSuccessful()) return result;

        long calculatedEut = processingLogic.getCalculatedEut();
        int duration = Math.max(1, processingLogic.getDuration());
        BigInteger cost = BigInteger.valueOf(Math.max(0, calculatedEut))
            .multiply(BigInteger.valueOf(duration));

        if (cost.signum() > 0) {
            if (ownerUUID == null || gregtech.common.misc.WirelessNetworkManager.getUserEU(ownerUUID)
                .compareTo(cost) < 0) {
                return CheckRecipeResultRegistry.insufficientStartupPower(cost);
            }
            if (!gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, cost.negate())) {
                return CheckRecipeResultRegistry.insufficientStartupPower(cost);
            }
            costingEU = costingEU.add(cost);
            costingEUText = String.valueOf(costingEU);
        }

        if (processingLogic.getOutputItems() != null) {
            mOutputItems = ArrayUtils.addAll(mOutputItems, processingLogic.getOutputItems());
        }
        if (processingLogic.getOutputFluids() != null) {
            mOutputFluids = ArrayUtils.addAll(mOutputFluids, processingLogic.getOutputFluids());
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** Up to {@code maxCycles} independent cycles, each matching a different recipe: the cross-recipe parallelism. */
    protected CheckRecipeResult checkProcessingWirelessLoop(int maxCycles) {
        costingEU = BigInteger.ZERO;
        costingEUText = "0";
        mOutputItems = null;
        mOutputFluids = null;

        CheckRecipeResult failure = CheckRecipeResultRegistry.NO_RECIPE;
        boolean anySuccess = false;

        startRecipeProcessing();
        try {
            for (int i = 0; i < maxCycles; i++) {
                CheckRecipeResult result = wirelessModeProcessOnce();
                if (!result.wasSuccessful()) {
                    failure = result;
                    break;
                }
                anySuccess = true;
                updateSlots();
            }
        } finally {
            endRecipeProcessing();
        }

        if (!anySuccess) {
            return failure;
        }

        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = 1;
        lEUt = 0;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    // endregion

    // Input helpers of the former MessTech base: TileEntityModuleBase has none, the mining code needs them.
    public ArrayList<ItemStack> getStoredInputsNoSeparation() {
        ArrayList<ItemStack> rList = new ArrayList<>();

        if (supportsCraftingMEBuffer()) {
            for (IDualInputHatch dualInputHatch : mDualInputHatches) {
                Iterator<? extends IDualInputInventory> inventoryIterator = dualInputHatch.inventories();
                while (inventoryIterator.hasNext()) {
                    ItemStack[] items = inventoryIterator.next()
                        .getItemInputs();
                    if (items == null || items.length == 0) continue;

                    for (int i = 0; i < items.length; i++) {
                        if (items[i] != null) {
                            rList.add(items[i]);
                        }
                    }
                }
            }
        }

        Map<GTUtility.ItemId, ItemStack> inputsFromME = new java.util.HashMap<>();
        for (MTEHatchInputBus tHatch : GTUtility.filterValidMTEs(mInputBusses)) {
            tHatch.mRecipeMap = getRecipeMap();
            IGregTechTileEntity tileEntity = tHatch.getBaseMetaTileEntity();
            boolean isMEBus = tHatch instanceof MTEHatchInputBusME;
            for (int i = tileEntity.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack itemStack = tileEntity.getStackInSlot(i);
                if (itemStack != null) {
                    if (isMEBus) {
                        // Prevent the same item from different ME buses from being recognized
                        inputsFromME.put(GTUtility.ItemId.createNoCopy(itemStack), itemStack);
                    } else {
                        rList.add(itemStack);
                    }
                }
            }
        }

        if (getStackInSlot(1) != null && getStackInSlot(1).getUnlocalizedName()
            .startsWith("gt.integrated_circuit")) rList.add(getStackInSlot(1));
        if (!inputsFromME.isEmpty()) {
            rList.addAll(inputsFromME.values());
        }
        return rList;
    }

    public ArrayList<FluidStack> getStoredFluidsWithDualInput() {
        ArrayList<FluidStack> rList = new ArrayList<>();
        Map<Fluid, FluidStack> inputsFromME = new java.util.HashMap<>();
        for (MTEHatchInput tHatch : GTUtility.filterValidMTEs(mInputHatches)) {
            setHatchRecipeMap(tHatch);
            if (tHatch instanceof MTEHatchMultiInput multiInputHatch) {
                for (FluidStack tFluid : multiInputHatch.getStoredFluid()) {
                    if (tFluid != null) {
                        rList.add(tFluid);
                    }
                }
            } else if (tHatch instanceof MTEHatchInputME meHatch) {
                for (FluidStack fluidStack : meHatch.getStoredFluids()) {
                    if (fluidStack != null) {
                        // Prevent the same fluid from different ME hatches from being recognized
                        inputsFromME.put(fluidStack.getFluid(), fluidStack);
                    }
                }
            } else {
                if (tHatch.getFillableStack() != null) {
                    rList.add(tHatch.getFillableStack());
                }
            }
        }

        if (!inputsFromME.isEmpty()) {
            rList.addAll(inputsFromME.values());
        }

        // get all fluids from Dual input
        if (supportsCraftingMEBuffer()) {
            for (IDualInputHatch dualInputHatch : mDualInputHatches) {
                Iterator<? extends IDualInputInventory> inventoryIterator = dualInputHatch.inventories();
                while (inventoryIterator.hasNext()) {
                    FluidStack[] fluids = inventoryIterator.next()
                        .getFluidInputs();
                    if (fluids == null || fluids.length == 0) continue;

                    for (int i = 0; i < fluids.length; i++) {
                        if (fluids[i] != null && fluids[i].amount > 0) {
                            rList.add(fluids[i]);
                        }
                    }
                }
            }
        }

        return rList;
    }

    // endregion

    // The original space modules expose no batch / input-separation / void-protection / power-panel buttons.
    @Override
    public boolean supportsVoidProtection() {
        return false;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return getRecipeMapImpl();
    }

    @Override
    protected SpaceModuleInfinityGui getGui() {
        return new SpaceModuleInfinityGui(this);
    }

    /**
     * The GTNH miner/pump skip the TecTech LED GUI of {@code TTMultiblockBase} and use MUI2, hence
     * {@link SpaceModuleInfinityGui}.
     */
    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister aBlockIconRegister) {
        ScreenOFF = gregtech.api.enums.Textures.BlockIcons.custom("gregtech", "iconsets/EM_CONTROLLER");
        ScreenON = gregtech.api.enums.Textures.BlockIcons.custom("gregtech", "iconsets/EM_CONTROLLER_ACTIVE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.common.util.ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        return new ITexture[] { gregtech.api.render.TextureFactory.of(
            gregtech.api.casing.Casings.UltimateMolecularCasing.getBlock(),
            gregtech.api.casing.Casings.UltimateMolecularCasing.getBlockMeta()) };
    }

    protected abstract String getMachineTypeKey();

    protected abstract RecipeMap<?> getRecipeMapImpl();

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);
}
