package com.MessTech.common.machine.module;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;

import java.math.BigInteger;
import java.util.ArrayList;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.MessTech.common.gui.module.SpaceModuleInfinityGui;
import com.MessTech.common.machine.Base.ParallelismAcrossMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import lombok.Getter;

/**
 * Shared base for the infinite space modules.
 * <p>
 * Always wireless: draws power directly from the GT wireless network, with a GUI-adjustable
 * parallel count. Base power = 1A MAX; every extra parallel adds 1A UXV.
 * Implements {@link ISpaceElevatorModule} so the vanilla Space Elevator can mount it via the
 * MessTech Mixin.
 */
@Getter
public abstract class SpaceModuleInfinityBase<T extends SpaceModuleInfinityBase<T>>
    extends ParallelismAcrossMultiMachineBase<T> implements ISurvivalConstructable, ISpaceElevatorModule {

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

    public SpaceModuleInfinityBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setEnableWirelessFunc(true);
        setEnableWireless(true);
        setWirelessParallel(1);
    }

    public SpaceModuleInfinityBase(String aName) {
        super(aName);
        setEnableWirelessFunc(true);
        setEnableWireless(true);
        setWirelessParallel(1);
    }

    public void setCrossRecipeParallel(int value) {
        this.crossRecipeParallel = Math.clamp(value, 1, 64);
    }

    @Override
    public void setWirelessParallel(int value) {
        super.setWirelessParallel(value);
    }

    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("crossRecipeParallel", crossRecipeParallel);
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("crossRecipeParallel")) {
            crossRecipeParallel = Math.max(1, aNBT.getInteger("crossRecipeParallel"));
        }
    }

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
        if (mMaxProgresstime > 0) {
            mOutputItems = batchItemOutputs.toArray(new ItemStack[0]);
            mOutputFluids = batchFluidOutputs.toArray(new FluidStack[0]);
        }
        return result;
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
        if (aBaseMetaTileEntity.isServerSide() && hasPendingBatchWork()
            && !aBaseMetaTileEntity.isAllowedToWork()) {
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
    public int getWirelessModeProcessingTime() {
        return 20;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        int parallel = Math.max(1, getWirelessParallel());
        long eut = GTValues.V[14] + (long) (parallel - 1) * GTValues.V[13];
        int duration = 20;
        CheckRecipeResult result = validateWirelessPowerForRecipe(eut, duration, 1);
        if (!result.wasSuccessful()) return result;
        BigInteger cost = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(duration));
        if (ownerUUID == null || !addEUToGlobalEnergyMap(ownerUUID, cost.negate())) {
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

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        java.util.List<StructureError> errors) {
        if (!checkPiece("main", 0, 0, 0, errors)) return;
    }

    @Override
    public IStructureDefinition<T> getStructureDefinition() {
        return StructureDefinition.<T>builder()
            .addShape("main", transpose(new String[][] { { "~" } }))
            .build();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("main", stackSize, hintsOnly, 0, 0, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor) {
        return super.survivalConstruct(stackSize, elementBudget, source, actor);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        return survivalBuildPiece("main", stackSize, 0, 0, 0, elementBudget, env, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(getMachineTypeKey()))
            .addSeparator()
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("machine.spacemodule.tooltip.0"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.spacemodule.tooltip.1"))
            .addStructureInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.spacemodule.tooltip.2"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        ScreenOFF = Textures.BlockIcons.custom("iconsets/EM_CONTROLLER");
        ScreenON = Textures.BlockIcons.custom("iconsets/EM_CONTROLLER_ACTIVE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        return new ITexture[] { TextureFactory
            .of(Casings.UltimateMolecularCasing.getBlock(), Casings.UltimateMolecularCasing.getBlockMeta()) };
    }

    // region ISpaceElevatorModule
    @Override
    public void connect(TileEntitySpaceElevator parent) {
        this.parentElevator = parent;
    }

    @Override
    public void disconnect() {
        this.parentElevator = null;
    }

    /**
     * @return The amount of computation this module may draw from the connected Space Elevator.
     */
    public long getAvailableDataForModule() {
        return parentElevator == null ? 0 : parentElevator.getAvailableDataForModules();
    }

    @Override
    public int getNeededMotorTier() {
        return 5;
    }

    @Override
    public long increaseStoredEU(long amount) {
        // Wireless modules draw directly from the wireless network; no elevator EU buffer is used.
        return 0;
    }

    @Override
    public boolean isDataInputListEmpty() {
        return true;
    }
    // endregion

    protected abstract String getMachineTypeKey();

    protected abstract RecipeMap<?> getRecipeMapImpl();

    @Override
    public RecipeMap<?> getRecipeMap() {
        return getRecipeMapImpl();
    }

    @Override
    protected SpaceModuleInfinityGui getGui() {
        return new SpaceModuleInfinityGui(this);
    }

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);
}
