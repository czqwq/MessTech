package com.MessTech.common.machine.module;

import static gregtech.common.misc.WirelessNetworkManager.processInitialSettings;

import java.math.BigInteger;
import java.util.ArrayList;
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
 * Shared base for the wireless MessTech space modules.
 * <p>
 * This class extends GTNH's native {@link TileEntityModuleBase}, so the modules are real Space Elevator
 * project modules rather than machines that merely stand next to one. Two things make that work:
 * <ul>
 * <li>the module's own structure is the module slot of the elevator - every subclass builds its piece
 * from {@link #buildModuleStructure}. The elevator walks its own structure and only registers a module
 * whose controller stands on one of its module slots, so the shape has to be the slot, not a lookalike;</li>
 * <li>the elevator hands the module its parent: every 20 ticks it walks {@code mProjectModuleHatches} and
 * calls {@code connect(this)} on each module whose {@code getNeededMotorTier()} the motor tier satisfies,
 * and {@code disconnect()} on all of them when it stops working. {@link #isMountedOnFormedElevator()} reads
 * exactly that, so the module never has to search the neighbourhood for an elevator.</li>
 * </ul>
 * Note that the slot only accepts a module in the right orientation: the module is 2 wide, 5 long and 2
 * deep, so its long axis has to run along the cell of the slot. The four rotations the player can give it
 * are not interchangeable; the ones that fit are the ones the elevator's own modules use.
 * <p>
 * The modules remain wireless: they draw power directly from the GT wireless network and do not draw
 * from the elevator's internal EU buffer.
 */
@Getter
public abstract class SpaceModuleInfinityBase<T extends SpaceModuleInfinityBase<T>> extends TileEntityModuleBase
    implements ISurvivalConstructable {

    /**
     * -- GETTER --
     *
     * @return The connected Space Elevator, or null when not mounted.
     */
    protected TileEntitySpaceElevator parentElevator;

    private int crossRecipeParallel = 1;
    protected boolean shutdownRequestedDuringBatch = false;

    // Tick-batched output state: the machine runs one continuous main batch and generates one
    // sub-output per tick, accumulating everything into these lists until the batch finishes.
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
     * The same module, on a tier of its own. The Infinity modules are all a single MAX tier (14, 5, 5); a family
     * that comes in several tiers - the space apiary modules - passes its own instead. Note that
     * {@code TileEntityModuleBase} indexes {@code GTValues.V[tTier]} and shifts the EU buffer by {@code tTier - 7}, so
     * the tier has to be a real entry of the voltage table (0..15) and at least 7.
     *
     * @param aTier         voltage tier of this module, see {@code TileEntityModuleBase#getTier()}
     * @param aModuleTier   module tier, i.e. how far up the elevator's own ladder it sits
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
     * The module slot of the Space Elevator, exactly as {@link TileEntityModuleBase} defines it for the native
     * modules: two rows of two, five layers tall, controller on the second layer of the first row. The elevator
     * only calls {@code addProjectModuleToMachineList} on a controller standing on its own module slot, so a
     * module that wants to be registered has to occupy that slot and not merely stand next to the elevator.
     *
     * @param hatchAdder adder that harvests the hatches of this module from the {@code H} positions
     */
    @SuppressWarnings("unchecked")
    protected IStructureDefinition<T> buildModuleStructure(IGTHatchAdder<? super T> hatchAdder) {
        // The wildcard in the adder is what lets the callers pass a method reference that takes their own
        // concrete class; the element itself belongs to this module, hence the cast.
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
     * @return the module tier this module was constructed with, i.e. {@code tModuleTier} - the same number the
     *         native modules read back through their own {@code getModuleTier()}. Recipes carry a
     *         {@code MODULE_TIER} metadata and only match when it is not above this value.
     */
    public int getModuleTier() {
        return tModuleTier;
    }

    /**
     * The elevator this module is mounted on, or null while it is not mounted on one. The elevator sets this in
     * {@code connect(this)}; the module never looks for an elevator on its own.
     */
    public TileEntitySpaceElevator getConnectedElevator() {
        return parentElevator;
    }

    /**
     * @return true while the module is mounted on an elevator whose structure is formed and whose motor tier is
     *         at least {@code getNeededMotorTier()}. The elevator only connects modules that satisfy both.
     */
    public boolean isMountedOnFormedElevator() {
        return parentElevator != null && parentElevator.getMotorTier() >= getNeededMotorTier();
    }

    /**
     * Adds the "why is this module not running" structure error, telling the two reasons apart: the module is
     * standing on a module slot but the elevator has not taken it in (its structure is incomplete, or it is
     * switched off), or it is mounted but the elevator's motor tier is below {@code getNeededMotorTier()}.
     *
     * @param errors error list of the structure check
     * @param level  level suffix this module needs, e.g. {@code V} for {@code MK-V}
     */
    protected void addElevatorError(List<StructureError> errors, String level) {
        if (parentElevator == null) {
            errors.add(StructureErrors.of("machine.spacemodule.elevator_not_linked"));
            return;
        }
        errors.add(
            StructureErrors.of("machine.spacemodule.elevator_level_too_low", TranslatableText.literal("MK-" + level)));
    }

    /**
     * Reports the elevator linkage of this module after its own structure turned out to be complete. Call it from
     * {@code checkMachine} once {@code checkPiece} succeeded.
     */
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

    /**
     * Formats a wireless EU value with thousands separators and a compact scientific-notation
     * suffix, e.g. {@code 1,000,000 (1.00e6)}.
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
        super.connect(parent);
    }

    @Override
    public void disconnect() {
        this.parentElevator = null;
        super.disconnect();
    }

    @Override
    public long increaseStoredEU(long amount) {
        // Wireless modules draw directly from the wireless network; no elevator EU buffer is used.
        return 0;
    }

    @Override
    protected long getAvailableData_EM() {
        if (eInputData.isEmpty()) {
            if (parentElevator == null) return 0;
            return parentElevator.getAvailableDataForModules();
        }
        return super.getAvailableData_EM();
    }

    /**
     * @return The amount of computation this module may draw from the connected Space Elevator.
     */
    public long getAvailableDataForModule() {
        return getAvailableData_EM();
    }

    /**
     * Keep a non-zero fake internal EU buffer while the module is structurally valid and connected.
     * The real power is paid wirelessly, so this only prevents the module base from stopping us for
     * an empty internal EU tank.
     */
    @Override
    protected void chargeController_EM(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mMachine) {
            setEUVar(maxEUStore());
        }
    }

    @Override
    public boolean isAllowedToWork() {
        // If a cross-recipe / multi-recipe batch is mid-way, keep the machine running even if the
        // player toggled it off, so all planned outputs are finished before shutdown.
        if (hasPendingBatchWork()) {
            return true;
        }
        return super.isAllowedToWork();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide() && hasPendingBatchWork() && !aBaseMetaTileEntity.isAllowedToWork()) {
            // The player (or redstone) asked to stop mid-batch; remember that and force the machine
            // to continue until every planned output has been emitted.
            shutdownRequestedDuringBatch = true;
            aBaseMetaTileEntity.enableWorking();
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && !hasPendingBatchWork() && shutdownRequestedDuringBatch) {
            // Batch fully finished; now honor the previously requested shutdown.
            shutdownRequestedDuringBatch = false;
            aBaseMetaTileEntity.disableWorking();
        }
    }
    // endregion

    // region Tick-batched processing

    /**
     * @return number of sub-outputs in the current tick-batched batch.
     */
    protected abstract int getBatchTaskCount();

    /**
     * Generates one sub-output and adds it to {@link #batchItemOutputs} / {@link #batchFluidOutputs}.
     *
     * @return true if a sub-output was generated, false if there are no more valid sub-outputs.
     */
    protected abstract boolean generateOneBatchTask();

    /**
     * @return base duration of the main batch, before the 1-tick distributed sub-outputs.
     */
    protected abstract int getMainBatchDuration();

    /**
     * Called when a new tick-batched batch starts. Subclasses can reset per-batch cursors here.
     */
    protected void onBatchStart() {}

    /**
     * Starts a continuous tick-batched batch. The first sub-output is generated immediately so the
     * machine has a valid recipe; remaining sub-outputs are generated one per tick.
     */
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
        // Keep the GUI/Waila display updated with the accumulated total output while the batch runs.
        // Modules that finish their whole batch inside checkProcessing must not have their real
        // mOutputItems overwritten by the (empty) tick-batched lists.
        if (mMaxProgresstime > 0 && usesTickBatchedOutputs()) {
            mOutputItems = batchItemOutputs.toArray(new ItemStack[0]);
            mOutputFluids = batchFluidOutputs.toArray(new FluidStack[0]);
        }
        return result;
    }

    /**
     * @return true for modules that spread their outputs over the running ticks (miner/pump),
     *         false for modules that generate all outputs at once inside checkProcessing.
     */
    protected boolean usesTickBatchedOutputs() {
        return true;
    }

    @Override
    public int getMaxParallelRecipes() {
        return Math.max(1, getWirelessParallel());
    }

    /**
     * @return true while a tick-batched output sequence is still running and must not be interrupted.
     */
    protected boolean hasPendingBatchWork() {
        return batchRemainingTasks > 0;
    }

    /**
     * TT module recipe hook. MessTech modules no longer override the final {@code checkProcessing()};
     * they override this method like every native elevator module does.
     */
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
        // Wireless power was already deducted in one lump; keep the machine from also trying to
        // drain energy hatches (which would cause an instant power-loss shutdown).
        lEUt = 0;
        mMaxProgresstime = duration;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * Simulated wireless power check. Called before any input is consumed.
     */
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

    /**
     * Wireless recipe validation shared by subclasses: checks that the wireless network can cover
     * {@code eut * duration * maxParallel}. If the balance is too low, returns an insufficient-power
     * result; otherwise returns SUCCESSFUL so the caller can continue to consuming inputs + power.
     */
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

    /**
     * Wireless-mode power setup for {@link ProcessingLogic}-driven modules. The module has no real
     * energy hatches; the wireless network is the only power source.
     */
    protected void setupWirelessProcessingPowerLogic(ProcessingLogic logic) {
        logic.setAvailableVoltage(Long.MAX_VALUE);
        logic.setAvailableAmperage(1);
        logic.setAmperageOC(false);
    }

    /**
     * Runs one wireless recipe cycle through the inherited {@link ProcessingLogic}. Used by modules that
     * want normal recipe lookup/parallel calculation but still pay from the GT wireless network.
     */
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

    /**
     * Runs up to {@code maxCycles} independent wireless recipe cycles. Each cycle may match a different
     * recipe against the remaining inputs, giving cross-recipe parallelism.
     */
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

    // Input helpers preserved from the former MessTech multiblock base. These are not provided by
    // TileEntityModuleBase but are still required by the custom mining code.
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

    // Original space modules don't expose batch/input-separation/void-protection/power-panel buttons.
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
     * The original GTNH space modules (miner/pump) do not use the TecTech LED GUI inherited from
     * {@code TTMultiblockBase}; they explicitly opt back into MUI2 so the custom
     * {@link SpaceModuleInfinityGui} subclasses are used.
     */
    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister aBlockIconRegister) {
        ScreenOFF = gregtech.api.enums.Textures.BlockIcons.custom("iconsets/EM_CONTROLLER");
        ScreenON = gregtech.api.enums.Textures.BlockIcons.custom("iconsets/EM_CONTROLLER_ACTIVE");
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
