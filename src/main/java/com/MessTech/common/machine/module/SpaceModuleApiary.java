package com.MessTech.common.machine.module;

import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.module.SpaceModuleApiaryGui;
import com.MessTech.common.gui.module.SpaceModuleInfinityGui;
import com.MessTech.common.util.MTBeeSimulator;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

/**
 * Space apiary modules: Space Elevator modules that run Forestry bees without an apiary, modelled on TST's
 * {@code TST_SpaceApiary} and GT5's Mega Industrial Apiary.
 * <p>
 * Four tiers (MK-I .. MK-IV), one class each below. The tier sets {@code tTier} (input voltage and the {@code t} term
 * of the apiary production formula, see {@link MTBeeSimulator#voltageTierExact(int)}), the parallel ceiling and the
 * bee slot count. The numbers are TST's, re-seated on this GT5U's table: TST asks for tiers 18 and 25, which
 * {@code GTValues.V} (16 entries) cannot hold.
 * <p>
 * Power comes from the GT wireless network, one amp of the module's tier per parallel per cycle; liquid DNA (TST
 * consumes it optionally) is not used. Bees are not consumed - a queen put in keeps working, which makes the bee
 * slots meaningful; the GUI is {@code SpaceModuleApiaryGui}.
 */
public abstract class SpaceModuleApiary extends SpaceModuleInfinityBase<SpaceModuleApiary> {

    /** Ticks one run takes - TST's {@code SpaceApiaryCycleTime}. */
    public static final int CYCLE_TICKS = 100;

    /** Highest parallel this tier may be set to. */
    private final int maxParallel;

    /** The bees this module runs. Queens, never consumed. */
    private final ItemStack[] beeSlots;

    protected SpaceModuleApiary(int aID, String aName, String aNameRegional, int aTier, int aModuleTier,
        int aMinMotorTier, int aMaxParallel, int aBeeSlots) {
        super(aID, aName, aNameRegional, aTier, aModuleTier, aMinMotorTier);
        this.maxParallel = aMaxParallel;
        this.beeSlots = new ItemStack[aBeeSlots];
    }

    protected SpaceModuleApiary(String aName, int aTier, int aModuleTier, int aMinMotorTier, int aMaxParallel,
        int aBeeSlots) {
        super(aName, aTier, aModuleTier, aMinMotorTier);
        this.maxParallel = aMaxParallel;
        this.beeSlots = new ItemStack[aBeeSlots];
    }

    public int getMaxParallel() {
        return maxParallel;
    }

    /** @return the bee slots, edited by the GUI and run by {@link #checkProcessing_EM()}. */
    public ItemStack[] getBeeSlots() {
        return beeSlots;
    }

    // region Processing

    @Override
    public int getMaxParallelRecipes() {
        return Math.max(1, Math.min(getWirelessParallel(), maxParallel));
    }

    /** The whole run happens at once: no tick-batched output display. */
    @Override
    protected boolean usesTickBatchedOutputs() {
        return false;
    }

    // Unused: checkProcessing_EM is overridden directly, but the abstract tick-batch hooks still have to be answered.
    @Override
    protected int getBatchTaskCount() {
        return 0;
    }

    @Override
    protected boolean generateOneBatchTask() {
        return false;
    }

    @Override
    protected int getMainBatchDuration() {
        return CYCLE_TICKS;
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing_EM() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        // Like the other space modules: only run while mounted on the elevator.
        if (!isMountedOnFormedElevator()) return CheckRecipeResultRegistry.NO_RECIPE;

        takeBeesFromInputs();
        if (!hasAnyBee()) return CheckRecipeResultRegistry.NO_RECIPE;

        int parallel = getMaxParallelRecipes();
        long eut = GTValues.V[Math.max(0, Math.min(GTValues.V.length - 1, getTier()))];
        CheckRecipeResult power = validateWirelessPowerForRecipe(eut, CYCLE_TICKS, parallel);
        if (!power.wasSuccessful()) return power;

        BigInteger cost = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(CYCLE_TICKS))
            .multiply(BigInteger.valueOf(parallel));
        if (ownerUUID == null || !addEUToGlobalEnergyMap(ownerUUID, cost.negate())) {
            return CheckRecipeResultRegistry.insufficientStartupPower(cost);
        }
        costingEU = cost;
        costingEUText = String.valueOf(cost);
        // The network was charged in one lump; don't drain its energy hatches too.
        lEUt = 0;

        mOutputItems = runBees(parallel);
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** Moves queens from the input bus into the empty bee slots, without consuming them. */
    private void takeBeesFromInputs() {
        if (!hasEmptyBeeSlot()) return;
        for (ItemStack input : getStoredInputsNoSeparation()) {
            if (!MTBeeSimulator.isQueen(input)) continue;
            for (int i = 0; i < beeSlots.length; i++) {
                if (beeSlots[i] != null) continue;
                beeSlots[i] = input.copy();
                beeSlots[i].stackSize = 1;
                break;
            }
            if (!hasEmptyBeeSlot()) return;
        }
    }

    private boolean hasEmptyBeeSlot() {
        for (ItemStack slot : beeSlots) if (slot == null) return true;
        return false;
    }

    private boolean hasAnyBee() {
        for (ItemStack slot : beeSlots) if (MTBeeSimulator.isQueen(slot)) return true;
        return false;
    }

    /** Runs every occupied bee slot once and multiplies the drops by the parallel. */
    private ItemStack[] runBees(int parallel) {
        List<ItemStack> out = new ArrayList<>();
        float t = MTBeeSimulator.voltageTierExact(getTier());
        for (ItemStack queen : beeSlots) {
            if (!MTBeeSimulator.isQueen(queen)) continue;
            for (ItemStack drop : MTBeeSimulator.simulate(queen, getBaseMetaTileEntity().getWorld(), t)) {
                long amount = (long) drop.stackSize * parallel;
                while (amount > 0) {
                    ItemStack copy = drop.copy();
                    copy.stackSize = (int) Math.min(Integer.MAX_VALUE, amount);
                    amount -= copy.stackSize;
                    out.add(copy);
                }
            }
        }
        return out.toArray(new ItemStack[0]);
    }

    /** {@link SpaceModuleApiaryGui}: the shared space-module GUI plus the bee slot grid. */
    @Override
    protected SpaceModuleInfinityGui getGui() {
        return new SpaceModuleApiaryGui(this);
    }

    /**
     * @return {@code null}: the apiary runs bees, not a recipe pool. TST does the same ({@code TST_SpaceApiary} extends
     *         {@code TileEntityModuleBase}, which never overrides {@code getRecipeMap()}) and
     *         {@code MTEMultiBlockBaseGui}
     *         never dereferences it.
     */
    @Override
    protected RecipeMap<?> getRecipeMapImpl() {
        return null;
    }

    // endregion

    // region Structure

    public boolean addModuleHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex)
            || addInputBusToMachineList(aTileEntity, aBaseCasingIndex)
            || addOutputBusToMachineList(aTileEntity, aBaseCasingIndex);
    }

    @Override
    public IStructureDefinition<? extends tectech.thing.metaTileEntity.multi.base.TTMultiblockBase> getStructure_EM() {
        return buildModuleStructure(SpaceModuleApiary::addModuleHatchToMachineList);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece("main", 0, 1, 0, errors)) return;
        // The needed motor tier is a module property, so the shortfall is reported here, not in the structure lookup.
        if (!isMountedOnFormedElevator()) {
            addElevatorError(errors, getNeedElevatorLevel());
            return;
        }
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
    }

    /** @return the level suffix of the elevator motor tier this module needs, e.g. {@code I} for {@code MK-I}. */
    protected abstract String getNeedElevatorLevel();

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("main", stackSize, hintsOnly, 0, 1, 0);
    }

    /**
     * The build used by the NEI structure preview and StructureLib's autoplace: it must go through
     * {@link #survivalBuildPiece}. {@code super} finds no implementation up the chain ({@code TileEntityModuleBase},
     * GT's bases), so it lands in StructureLib's interface default, which cannot serve the preview's fake player and
     * answers "-2 not supported", leaving only the controller.
     */
    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        return survivalBuildPiece("main", stackSize, 0, 1, 0, elementBudget, env, false, true);
    }

    // endregion

    // region NBT

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        NBTTagList list = new NBTTagList();
        for (ItemStack slot : beeSlots) {
            NBTTagCompound tag = new NBTTagCompound();
            if (slot != null) slot.writeToNBT(tag);
            list.appendTag(tag);
        }
        aNBT.setTag("beeSlots", list);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        NBTTagList list = aNBT.getTagList("beeSlots", 10);
        for (int i = 0; i < beeSlots.length && i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            beeSlots[i] = tag.hasNoTags() ? null : ItemStack.loadItemStackFromNBT(tag);
        }
    }

    // endregion

    // region Look

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(getMachineTypeKey()))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleapiary.tooltip.desc"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleapiary.tooltip.bees"))
            .addInfo(StatCollector.translateToLocalFormatted("machine.spacemoduleapiary.tooltip.parallel", maxParallel))
            .addInfo(
                StatCollector.translateToLocalFormatted("machine.spacemoduleapiary.tooltip.slots", beeSlots.length))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleapiary.tooltip.power"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleapiary.tooltip.details"))
            .beginStructureBlock(1, 5, 2, false)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_4th_layer"))
            .addCasing("0-8", StatCollector.translateToLocal("gt.blockcasings.ig.0.name"), false)
            .addInputBus("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addOutputBus("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addStructureFooter(StatCollector.translateToLocal("ig.elevator.structure.SharedResources"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] {
                gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(TileEntitySpaceElevator.CASING_INDEX_BASE),
                new TTRenderedExtendedFacingTexture(aActive ? ScreenON : ScreenOFF) };
        }
        return new ITexture[] {
            gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(TileEntitySpaceElevator.CASING_INDEX_BASE) };
    }

    // endregion

    // region Tiers

    /** MK-I: UEV (tier 10), 256 parallel, 16 bee slots, needs an elevator motor tier 1. */
    public static class MK1 extends SpaceModuleApiary {

        public MK1(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 10, 1, 1, 256, 16);
        }

        public MK1(String aName) {
            super(aName, 10, 1, 1, 256, 16);
        }

        @Override
        protected String getMachineTypeKey() {
            return "machine.spacemoduleapiary1.machinetype";
        }

        @Override
        protected String getNeedElevatorLevel() {
            return "I";
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
            return new MK1(mName);
        }
    }

    /** MK-II: UIV (tier 12), 4096 parallel, 32 bee slots, needs an elevator motor tier 2. */
    public static class MK2 extends SpaceModuleApiary {

        public MK2(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 12, 2, 2, 4096, 32);
        }

        public MK2(String aName) {
            super(aName, 12, 2, 2, 4096, 32);
        }

        @Override
        protected String getMachineTypeKey() {
            return "machine.spacemoduleapiary2.machinetype";
        }

        @Override
        protected String getNeedElevatorLevel() {
            return "II";
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
            return new MK2(mName);
        }
    }

    /** MK-III: MAX (tier 14), 32768 parallel, 64 bee slots, needs an elevator motor tier 3. */
    public static class MK3 extends SpaceModuleApiary {

        public MK3(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 14, 3, 3, 32768, 64);
        }

        public MK3(String aName) {
            super(aName, 14, 3, 3, 32768, 64);
        }

        @Override
        protected String getMachineTypeKey() {
            return "machine.spacemoduleapiary3.machinetype";
        }

        @Override
        protected String getNeedElevatorLevel() {
            return "III";
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
            return new MK3(mName);
        }
    }

    /** MK-IV: top table entry (tier 15), no parallel ceiling, 128 bee slots, needs an elevator motor tier 4. */
    public static class MK4 extends SpaceModuleApiary {

        public MK4(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 15, 4, 4, Integer.MAX_VALUE, 128);
        }

        public MK4(String aName) {
            super(aName, 15, 4, 4, Integer.MAX_VALUE, 128);
        }

        @Override
        protected String getMachineTypeKey() {
            return "machine.spacemoduleapiary4.machinetype";
        }

        @Override
        protected String getNeedElevatorLevel() {
            return "IV";
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
            return new MK4(mName);
        }
    }

    // endregion
}
