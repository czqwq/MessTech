package com.MessTech.common.machine.module;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.module.SpaceModuleApiaryGui;
import com.MessTech.common.gui.module.SpaceModuleInfinityGui;
import com.MessTech.common.util.MTBeeSimulator;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

/**
 * Space apiary modules: Space Elevator modules that run Forestry bees without an apiary, modelled on TST's
 * {@code TST_SpaceApiary} and GT5's own Mega Industrial Apiary.
 * <p>
 * Four tiers (MK-I .. MK-IV), one class each below. A tier decides three things: the {@code tTier} it runs on (which is
 * both its input voltage and the {@code t} term of the apiary production formula, see
 * {@link MTBeeSimulator#voltageTierExact(int)}), the parallel ceiling, and how many bees it can hold. The numbers are
 * TST's, with the tiers re-seated on this GT5U's voltage table - TST asks for tiers 18 and 25 on its last two modules,
 * which {@code GTValues.V} (16 entries) cannot hold.
 * <p>
 * Power comes from the GT wireless network like the other MessTech space modules, at one amp of the module's own tier
 * per parallel per cycle. Liquid DNA, which TST optionally consumes, is not used here.
 * <p>
 * Bees are not consumed: a queen put into the module keeps working, which is what the apiary does and what makes the
 * bee slots meaningful. The GUI that edits those slots is {@code SpaceModuleApiaryGui}.
 */
public abstract class SpaceModuleApiary extends SpaceModuleInfinityBase<SpaceModuleApiary> {

    /** Ticks one run takes - TST's {@code SpaceApiaryCycleTime}. */
    public static final int CYCLE_TICKS = 100;

    /** Highest parallel a module of this tier may be set to. */
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

    /** @return the parallel ceiling of this tier, 256 at MK-I up to {@code Integer.MAX_VALUE} at MK-IV. */
    public int getMaxParallel() {
        return maxParallel;
    }

    /** @return the bee slots, for the GUI to edit and for {@link #checkProcessing_EM()} to run. */
    public ItemStack[] getBeeSlots() {
        return beeSlots;
    }

    // region Processing

    @Override
    public int getMaxParallelRecipes() {
        return Math.max(1, Math.min(getWirelessParallel(), maxParallel));
    }

    /** The whole run happens at once, so the tick-batched output display of the base must stay out of the way. */
    @Override
    protected boolean usesTickBatchedOutputs() {
        return false;
    }

    // The apiary does not use the tick-batched framework of the base - it overrides checkProcessing_EM directly - so
    // the three hooks stay unused. They are abstract on the base and have to be answered anyway.
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
        // The wireless network was charged in one lump; keep the machine from draining its own energy hatches too.
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

    /**
     * The apiary uses {@link SpaceModuleApiaryGui}, which adds the bee slot grid to the shared space-module GUI.
     */
    @Override
    protected SpaceModuleInfinityGui getGui() {
        return new SpaceModuleApiaryGui(this);
    }

    /**
     * @return {@code null}, because the apiary runs bees and not a recipe pool.
     *         <p>
     *         That is what TST does as well: its {@code TST_SpaceApiary} extends {@code TileEntityModuleBase}, which
     *         never overrides {@code getRecipeMap()}, and GT5U's own {@code MTEMultiBlockBase#getRecipeMap()} returns
     *         {@code null} by default. The multiblock GUI is written for that default - {@code MTEMultiBlockBaseGui}
     *         never dereferences the map - so a module without recipes simply reports none.
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
        return StructureDefinition.<SpaceModuleApiary>builder()
            .addShape(
                "main",
                transpose(new String[][] { { "H", "H" }, { "~", "H" }, { "H", "H" }, { "H", "H" }, { "H", "H" } }))
            .addElement(
                'H',
                GTStructureUtility.ofHatchAdderOptional(
                    SpaceModuleApiary::addModuleHatchToMachineList,
                    TileEntitySpaceElevator.CASING_INDEX_BASE,
                    1,
                    GregTechAPI.sBlockCasingsSE,
                    0))
            .build();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        parentElevator = findConnectedElevator(aBaseMetaTileEntity);
        if (parentElevator == null || parentElevator.getMotorTier() < getNeededMotorTier()) {
            errors.add(StructureErrors.of(getNeedElevatorKey()));
            return;
        }
        if (!checkPiece("main", 0, 1, 0, errors)) return;
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
    }

    /**
     * The Space Elevator this module is mounted on. The elevator takes its modules in through the normal structure
     * check, so the module only has to find it in the neighbourhood - the same lookup the other space modules use.
     * Unlike them it does not filter on a motor tier here: which tier is needed is a property of the module
     * ({@code getNeededMotorTier()}), and {@link #checkMachine} reports the shortfall.
     */
    private TileEntitySpaceElevator findConnectedElevator(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity == null) return null;
        net.minecraft.tileentity.TileEntity self = (net.minecraft.tileentity.TileEntity) aBaseMetaTileEntity;
        if (self.getWorldObj() == null) return null;
        net.minecraft.world.World world = self.getWorldObj();
        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        int range = 8;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    net.minecraft.tileentity.TileEntity te = world.getTileEntity(x + dx, y + dy, z + dz);
                    if (te instanceof IGregTechTileEntity gt
                        && gt.getMetaTileEntity() instanceof TileEntitySpaceElevator elevator) {
                        return elevator;
                    }
                }
            }
        }
        return null;
    }

    /** @return the lang key of the structure error that says which elevator motor tier this module needs. */
    protected abstract String getNeedElevatorKey();

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("main", stackSize, hintsOnly, 0, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor) {
        return super.survivalConstruct(stackSize, elementBudget, source, actor);
    }

    /**
     * The structure build used by the NEI structure preview (and by StructureLib's autoplace): it has to build through
     * {@link #survivalBuildPiece} like the other space modules do. Delegating to {@code super} instead finds no
     * implementation up the chain - neither {@code TileEntityModuleBase} nor GT's multiblock bases override the
     * {@link ISurvivalBuildEnvironment} variant - so it falls into StructureLib's interface default, which cannot serve
     * the fake player the preview uses and answers "-2 not supported". The preview then builds nothing and shows only
     * the controller.
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
        protected String getNeedElevatorKey() {
            return "machine.spacemoduleapiary.need_elevator_t1";
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
        protected String getNeedElevatorKey() {
            return "machine.spacemoduleapiary.need_elevator_t2";
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
        protected String getNeedElevatorKey() {
            return "machine.spacemoduleapiary.need_elevator_t3";
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
            return new MK3(mName);
        }
    }

    /** MK-IV: the top table entry (tier 15), no parallel ceiling, 128 bee slots, needs an elevator motor tier 4. */
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
        protected String getNeedElevatorKey() {
            return "machine.spacemoduleapiary.need_elevator_t4";
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
            return new MK4(mName);
        }
    }

    // endregion
}
