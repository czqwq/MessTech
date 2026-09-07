package com.MessTech.common.machine.module;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.module.SpaceModuleMinerInfinityGui;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.utils.item.LimitingItemStackHandler;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.ParallelHelper;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.misc.spaceprojects.enums.SolarSystem;
import gregtech.common.misc.spaceprojects.interfaces.ISpaceProject;
import gtPlusPlus.core.material.MaterialsElements;
import gtnhintergalactic.item.ItemMiningDrones;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhintergalactic.recipe.SpaceMiningData;
import gtnhintergalactic.recipe.SpaceMiningRecipes.WeightedAsteroidList;
import gtnhintergalactic.spaceprojects.ProjectAsteroidOutpost;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import lombok.Getter;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataInput;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

/**
 * Infinite Space Mining module.
 * <p>
 * Uses the real GTNH Intergalactic space mining recipe pool, consumes drones/drills/rods and plasma,
 * draws computation from the parent Space Elevator and power from the GT wireless network. It is an
 * Orbital Tier V module and therefore requires {@code getMotorTier() >= 5} to run.
 */
public class SpaceModuleMinerInfinity extends SpaceModuleInfinityBase<SpaceModuleMinerInfinity> {

    public static final int MAX_DISTANCE = 300;
    private static final int MAX_CROSS_RECIPE_CYCLES = 64;
    private static final int MAX_MINING_PARALLEL = Integer.MAX_VALUE;
    private static final int MAX_OUTPUT_ITERATIONS = 16384 * 400;
    protected static final int BONUS_STACK_MAX_CHANCE = 7500;
    protected static final int PLASMA_HELIUM_USAGE = 825;
    protected static final int PLASMA_BISMUTH_USAGE = 550;
    protected static final int PLASMA_RADON_USAGE = 375;
    protected static final int PLASMA_TECHNETIUM_USAGE = 250;
    protected static final int PLASMA_PLUTONIUM241_USAGE = 150;

    protected static final ISpaceProject ASTEROID_OUTPOST = SpaceProjectManager.getProject("AsteroidOutput");
    protected ProjectAsteroidOutpost asteroidOutpost;

    public boolean isWhitelisted = false;
    public boolean wasFilterPasted = false;
    public int currentDroneMask = 0;
    public ItemStackHandler filterInventory = new LimitingItemStackHandler(64, 1);
    protected HashSet<String> configuredOres = new HashSet<>();

    public int getWhitelistedInt() {
        return isWhitelisted ? 1 : 0;
    }

    public void setWhitelistedInt(int value) {
        this.isWhitelisted = value != 0;
        generateOreConfigurationList();
    }

    @Getter
    private int distance = 30;
    @Getter
    private int overdrive = 100;
    @Getter
    private boolean cycleEnabled = false;
    @Getter
    private int range = 0;
    @Getter
    private int step = 1;
    @Getter
    private int cycleDistance = 30;

    public SpaceModuleMinerInfinity(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public SpaceModuleMinerInfinity(String aName) {
        super(aName);
    }

    public void setDistance(int value) {
        this.distance = Math.clamp(value, 0, MAX_DISTANCE);
    }

    public void setOverdrive(int value) {
        this.overdrive = Math.clamp(value, 100, 200);
    }

    public void setCycleEnabled(boolean value) {
        this.cycleEnabled = value;
        if (!value) {
            this.cycleDistance = this.distance;
        }
    }

    public int getCycleEnabledInt() {
        return cycleEnabled ? 1 : 0;
    }

    public void setCycleEnabledInt(int value) {
        setCycleEnabled(value != 0);
    }

    public void setRange(int value) {
        this.range = Math.clamp(value, 0, 150);
    }

    public void setStep(int value) {
        this.step = Math.clamp(value, 0, 20);
    }

    public void setCycleDistance(int value) {
        this.cycleDistance = Math.clamp(value, 0, MAX_DISTANCE);
    }

    private int getEffectiveDistance() {
        return cycleEnabled ? cycleDistance : distance;
    }

    private void cycleDistance() {
        if (cycleEnabled) {
            int min = Math.max(0, distance - range);
            int max = Math.min(MAX_DISTANCE, distance + range);
            if (cycleDistance + step <= max) {
                cycleDistance += step;
            } else {
                cycleDistance = min;
            }
        } else {
            cycleDistance = distance;
        }
    }

    public void generateOreConfigurationList() {
        if (configuredOres == null) {
            configuredOres = new HashSet<>();
        } else {
            configuredOres.clear();
        }
        if (filterInventory != null) {
            for (ItemStack item : filterInventory.getStacks()) {
                String oreString = getOreString(item);
                if (oreString != null) {
                    configuredOres.add(oreString);
                }
            }
        }
    }

    protected String getOreString(ItemStack oreStack) {
        if (oreStack == null || oreStack.getItem() == null) {
            return null;
        }
        if (oreStack.getUnlocalizedName()
            .startsWith("gt.blockores")) {
            return oreStack.getItem()
                .getUnlocalizedName() + ":"
                + oreStack.getItemDamage() % 1000;
        }
        return oreStack.getItem()
            .getUnlocalizedName() + ":"
            + oreStack.getItemDamage();
    }

    public boolean addModuleHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex)
            || addInputBusToMachineList(aTileEntity, aBaseCasingIndex)
            || addOutputBusToMachineList(aTileEntity, aBaseCasingIndex)
            || addDataInputToMachineList(aTileEntity, aBaseCasingIndex);
    }

    private long getLocalComputation() {
        long result = 0;
        for (MTEHatchDataInput in : eInputData) {
            if (in.q != null) {
                result += in.q.getContent();
            }
        }
        return result;
    }

    @Override
    public long getAvailableDataForModule() {
        long local = getLocalComputation();
        if (local > 0) return local;
        return super.getAvailableDataForModule();
    }

    @Override
    public boolean isDataInputListEmpty() {
        return eInputData.isEmpty();
    }

    public int getModuleTier() {
        return 5;
    }

    @Override
    protected String getMachineTypeKey() {
        return "machine.spacemoduleminer.machinetype";
    }

    @Override
    protected RecipeMap<?> getRecipeMapImpl() {
        return IGRecipeMaps.spaceMiningRecipes;
    }

    @Override
    protected SpaceModuleMinerInfinityGui getGui() {
        return new SpaceModuleMinerInfinityGui(this);
    }

    @Override
    public IStructureDefinition<? extends tectech.thing.metaTileEntity.multi.base.TTMultiblockBase> getStructure_EM() {
        return StructureDefinition.<SpaceModuleMinerInfinity>builder()
            .addShape(
                "main",
                transpose(new String[][] { { "H", "H" }, { "~", "H" }, { "H", "H" }, { "H", "H" }, { "H", "H" } }))
            .addElement(
                'H',
                GTStructureUtility.ofHatchAdderOptional(
                    SpaceModuleMinerInfinity::addModuleHatchToMachineList,
                    TileEntitySpaceElevator.CASING_INDEX_BASE,
                    1,
                    GregTechAPI.sBlockCasingsSE,
                    0))
            .build();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        eInputData.clear();
        parentElevator = findConnectedElevator(aBaseMetaTileEntity);
        if (parentElevator == null || parentElevator.getMotorTier() < 5) {
            errors.add(StructureErrors.of("machine.spacemodule.need_elevator_t5"));
            return;
        }
        if (!checkPiece("main", 0, 1, 0, errors)) return;
        if (eInputData.isEmpty() && !parentElevator.hasDataHatches()) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_data_input_hatch"));
        }
        checkHasInputHatch(errors);
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
        if (errors.isEmpty() && getBaseMetaTileEntity() != null) {
            if (SpaceProjectManager.teamHasProject(getBaseMetaTileEntity().getOwnerUuid(), ASTEROID_OUTPOST)) {
                ISpaceProject project = SpaceProjectManager
                    .getTeamProject(getBaseMetaTileEntity().getOwnerUuid(), SolarSystem.KuiperBelt, "AsteroidOutpost");
                asteroidOutpost = project != null && project.isFinished() ? (ProjectAsteroidOutpost) project : null;
            } else {
                asteroidOutpost = null;
            }
        }
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("main", stackSize, hintsOnly, 0, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor) {
        return super.survivalConstruct(stackSize, elementBudget, source, actor);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        return survivalBuildPiece("main", stackSize, 0, 1, 0, elementBudget, env, false, true);
    }

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
                        && gt.getMetaTileEntity() instanceof TileEntitySpaceElevator elevator
                        && elevator.getMotorTier() >= 5) {
                        return elevator;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing_EM() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        if (parentElevator == null || parentElevator.getMotorTier() < 5) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // Bracketing is required for ME fluid hatches to report their real drainable amounts.
        startRecipeProcessing();
        try {
            return startBatchedProcessing();
        } finally {
            endRecipeProcessing();
        }
    }

    @Override
    protected int getBatchTaskCount() {
        return Math.clamp(getCrossRecipeParallel(), 1, MAX_CROSS_RECIPE_CYCLES);
    }

    /**
     * The custom mining check already caps parallels using the data available at recipe-start time.
     * TT's generic computation-timeout check can otherwise see a temporarily lower
     * {@code eAvailableData} right after the module becomes active (elevator data is divided among
     * active miners) and shut the machine down with a false "computation_loss" even though enough
     * computation exists. Keep {@code eAvailableData} at least at the amount this recipe actually
     * reserved.
     */
    @Override
    public boolean onRunningTickCheck(ItemStack aStack) {
        if (eRequiredData > eAvailableData) {
            eAvailableData = eRequiredData;
        }
        return super.onRunningTickCheck(aStack);
    }

    @Override
    protected int getMainBatchDuration() {
        return 20;
    }

    @Override
    protected boolean generateOneBatchTask() {
        CheckRecipeResult result = doMiningCheck();
        if (!result.wasSuccessful()) {
            return false;
        }
        if (mOutputItems != null) {
            Collections.addAll(batchItemOutputs, mOutputItems);
            mOutputItems = null;
        }
        if (mOutputFluids != null) {
            Collections.addAll(batchFluidOutputs, mOutputFluids);
            mOutputFluids = null;
        }
        return true;
    }

    private CheckRecipeResult doMiningCheck() {
        // Cap the per-recipe parallel to prevent Int.MAX from turning generateOutputs into a
        // multi-billion iteration loop (the main spark hotspot).
        int parallel = Math.max(1, Math.min(getWirelessParallel(), MAX_MINING_PARALLEL));
        long eut = GTValues.V[14] + (long) (parallel - 1) * GTValues.V[13];
        int effectiveDistance = getEffectiveDistance();

        ItemStack[] itemInputs = gatherItemInputs();
        currentDroneMask = getAvailDroneMask(itemInputs);
        ArrayList<FluidStack> allFluids = new ArrayList<>();
        if (parentElevator != null) {
            allFluids.addAll(parentElevator.getStoredFluids());
        }
        allFluids.addAll(getStoredFluidsWithDualInput());
        FluidStack[] fluidInputs = allFluids.toArray(new FluidStack[0]);
        if (itemInputs.length == 0 || fluidInputs.length == 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        int plasmaTier = getBestPlasmaTier(fluidInputs);
        if (plasmaTier <= 0) {
            return SimpleCheckRecipeResult.ofFailure("no_plasma");
        }
        FluidStack plasma = findPlasma(fluidInputs, plasmaTier);
        if (plasma == null) {
            return SimpleCheckRecipeResult.ofFailure("no_plasma");
        }

        WeightedAsteroidList candidates = new WeightedAsteroidList(
            IGRecipeMaps.spaceMiningRecipes.findRecipeQuery()
                .items(itemInputs)
                .fluids(fluidInputs)
                .voltage(Long.MAX_VALUE)
                .findAll()
                .filter(r -> {
                    SpaceMiningData data = r.getMetadata(IGRecipeMaps.SPACE_MINING_DATA);
                    return data != null && data.minDistance <= effectiveDistance
                        && data.maxDistance >= effectiveDistance;
                })
                .distinct());
        if (candidates.totalWeight == 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        GTRecipe recipe = candidates.getRandom();
        SpaceMiningData data = recipe.getMetadata(IGRecipeMaps.SPACE_MINING_DATA);
        if (data == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        int duration = getRecipeTime(recipe.mDuration, plasmaTier);

        float compModifier = 1f;
        float plasmaModifier = 1f;
        if (asteroidOutpost != null) {
            compModifier -= asteroidOutpost.getComputationDiscount();
            plasmaModifier -= asteroidOutpost.getPlasmaDiscount();
        }

        int plasmaUsage = getPlasmaUsageFromTier(plasmaTier);
        long availableData = getAvailableDataForModule();
        int compParallels = (int) (availableData / Math.max(1, data.computation * compModifier));
        if (compParallels <= 0) {
            return SimpleCheckRecipeResult.ofFailure("insufficient_computation");
        }
        int maxParallels = Math.min(parallel, compParallels);
        maxParallels = Math.min(
            maxParallels,
            (int) (getTotalPlasmaAmount(fluidInputs, plasmaTier) / Math.max(1, plasmaUsage * plasmaModifier)));
        if (maxParallels <= 0) {
            return SimpleCheckRecipeResult.ofFailure("no_plasma");
        }

        // Use the same ParallelHelper path as the original module: it caps by inputs and consumes
        // the item/fluid inputs once parallels are known.
        ParallelHelper helper = new ParallelHelper().setMaxParallel(maxParallels)
            .setRecipe(recipe)
            .setFluidInputs(fluidInputs)
            .setItemInputs(itemInputs)
            .setAvailableEUt(Long.MAX_VALUE / 4)
            .setMachine(this, false, false)
            .setConsumption(true)
            .build();
        maxParallels = helper.getCurrentParallel();
        if (maxParallels <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        CheckRecipeResult powerResult = validateWirelessPowerForRecipe(eut, duration, 1);
        if (!powerResult.wasSuccessful()) return powerResult;

        BigInteger cost = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(duration));
        if (ownerUUID == null || !addEUToGlobalEnergyMap(ownerUUID, cost.negate())) {
            return CheckRecipeResultRegistry.insufficientStartupPower(cost);
        }
        costingEU = cost;
        costingEUText = String.valueOf(cost);

        int plasmaToConsume = (int) Math.ceil(maxParallels * plasmaUsage * plasmaModifier);
        if (plasmaToConsume > 0) {
            depleteInput(new FluidStack(plasma.getFluid(), plasmaToConsume), false);
        }

        mOutputItems = generateOutputs(recipe, data, maxParallels, plasmaTier);
        mOutputFluids = null;
        // Wireless power was already deducted in one lump; don't drain energy hatches too.
        lEUt = 0;
        eRequiredData = (int) Math.ceil(data.computation * maxParallels * compModifier);
        // The custom check above already reserved only as much computation as was available. Keep TT's
        // internal view consistent so its generic computation timeout does not fire a false loss right
        // after this module becomes active and the elevator recalculates the per-module share.
        eAvailableData = Math.max(eAvailableData, availableData);
        eComputationTimeout = 100;
        // Duration is managed by the tick-batched main batch; do not overwrite it here.
        mEfficiencyIncrease = 10000;
        cycleDistance();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private ItemStack[] gatherItemInputs() {
        ArrayList<ItemStack> inputs = getStoredInputsNoSeparation();
        ItemStack controllerSlot = getControllerSlot();
        if (controllerSlot != null && controllerSlot.getItem() instanceof ItemMiningDrones) {
            inputs.add(controllerSlot);
        }
        return inputs.toArray(new ItemStack[0]);
    }

    protected int getAvailDroneMask(ItemStack[] inputs) {
        Map<GTUtility.ItemId, Long> itemCounts = new HashMap<>();
        for (ItemStack input : inputs) {
            if (input == null) continue;
            GTUtility.ItemId key = GTUtility.ItemId.createWithoutNBT(input);
            itemCounts.merge(key, (long) input.stackSize, Long::sum);
        }
        int res = 0;
        for (int tier = ItemMiningDrones.DroneTiers.LV.ordinal(); tier
            <= ItemMiningDrones.DroneTiers.UXV.ordinal(); ++tier) {
            if (Arrays.stream(gtnhintergalactic.recipe.SpaceMiningRecipes.getTieredInputs(tier))
                .allMatch(
                    input -> itemCounts.getOrDefault(GTUtility.ItemId.createWithoutNBT(input), 0L)
                        >= Math.max(input.stackSize, 1))) {
                res |= 1 << tier;
            }
        }
        return res;
    }

    private int getBestPlasmaTier(FluidStack[] fluids) {
        for (int tier = 5; tier >= 1; tier--) {
            if (getTotalPlasmaAmount(fluids, tier) >= getPlasmaUsageFromTier(tier)) {
                return tier;
            }
        }
        return 0;
    }

    private long getTotalPlasmaAmount(FluidStack[] fluids, int tier) {
        long total = 0;
        for (FluidStack fluid : fluids) {
            if (getPlasmaTierType(fluid) == tier) {
                total += fluid.amount;
            }
        }
        return total;
    }

    private int getPlasmaTierType(FluidStack fluidStack) {
        if (fluidStack == null) return 0;
        if (fluidStack.isFluidEqual(Materials.Plutonium241.getPlasma(1))) return 5;
        if (fluidStack.isFluidEqual(new FluidStack(MaterialsElements.getInstance().TECHNETIUM.getPlasma(), 1)))
            return 4;
        if (fluidStack.isFluidEqual(Materials.Radon.getPlasma(1))) return 3;
        if (fluidStack.isFluidEqual(Materials.Bismuth.getPlasma(1))) return 2;
        if (fluidStack.isFluidEqual(Materials.Helium.getPlasma(1))) return 1;
        return 0;
    }

    private int getTierFromPlasma(FluidStack fluidStack) {
        int tier = getPlasmaTierType(fluidStack);
        if (tier <= 0) return 0;
        int usage = getPlasmaUsageFromTier(tier);
        return fluidStack.amount >= usage ? tier : 0;
    }

    private FluidStack findPlasma(FluidStack[] fluids, int tier) {
        for (FluidStack fluid : fluids) {
            if (getPlasmaTierType(fluid) == tier) {
                return fluid;
            }
        }
        return null;
    }

    private int getPlasmaUsageFromTier(int plasmaTier) {
        return switch (plasmaTier) {
            case 1 -> PLASMA_HELIUM_USAGE;
            case 2 -> PLASMA_BISMUTH_USAGE;
            case 3 -> PLASMA_RADON_USAGE;
            case 4 -> PLASMA_TECHNETIUM_USAGE;
            case 5 -> PLASMA_PLUTONIUM241_USAGE;
            default -> 0;
        };
    }

    private ItemStack[] generateOutputs(GTRecipe recipe, SpaceMiningData data, int parallels, int plasmaTier) {
        // Safety cap: never let an oversized parallel setting turn this into an enormous loop.
        parallels = Math.max(1, Math.min(parallels, MAX_MINING_PARALLEL));
        long totalIterations = (long) data.maxSize * parallels;
        if (totalIterations > MAX_OUTPUT_ITERATIONS) {
            parallels = Math.max(1, (int) (MAX_OUTPUT_ITERATIONS / Math.max(1, data.maxSize)));
        }

        // Use a plain long[] indexed by recipe output slot. This avoids HashMap/ItemId hashing and
        // merging entirely, which was the main server-thread hotspot in spark.
        long[] outputCounts = new long[recipe.mOutputs.length];
        String[] outputOreStrings = null;
        boolean hasFilter = configuredOres != null && !configuredOres.isEmpty();
        if (hasFilter) {
            outputOreStrings = new String[recipe.mOutputs.length];
            for (int j = 0; j < recipe.mOutputs.length; j++) {
                if (recipe.mOutputs[j] != null) {
                    outputOreStrings[j] = getOreString(recipe.mOutputs[j]);
                }
            }
        }

        int totalChance = 0;
        final int[] outputChances;
        if (recipe.mOutputChances == null) {
            totalChance = recipe.mOutputs.length * 10000;
            outputChances = new int[recipe.mOutputs.length];
            Arrays.fill(outputChances, 10000);
        } else {
            outputChances = recipe.mOutputChances;
            for (int chance : outputChances) totalChance += chance;
        }

        int bonusStackChance = getBonusStackChance(plasmaTier);
        for (int i = 0; i < data.maxSize * parallels; i++) {
            if (i < data.minSize * parallels || bonusStackChance > XSTR.XSTR_INSTANCE.nextInt(10000)) {
                int random = XSTR.XSTR_INSTANCE.nextInt(totalChance);
                for (int j = 0; j < outputChances.length; j++) {
                    random -= outputChances[j];
                    if (random < 0) {
                        ItemStack generatedOre = recipe.mOutputs[j];
                        if (generatedOre != null) {
                            if (!hasFilter || isWhitelisted == configuredOres.contains(outputOreStrings[j])) {
                                outputCounts[j] += generatedOre.stackSize;
                            }
                        }
                        break;
                    }
                }
            }
        }

        ArrayList<ItemStack> outputItems = new ArrayList<>();
        for (int j = 0; j < outputCounts.length; j++) {
            if (outputCounts[j] > 0 && recipe.mOutputs[j] != null) {
                ParallelHelper.addItemsLong(outputItems, recipe.mOutputs[j], outputCounts[j]);
            }
        }
        return outputItems.toArray(new ItemStack[0]);
    }

    private int getRecipeTime(int unboostedTime, int plasmaTier) {
        double overdriveValue = overdrive / 100.0D;
        if (plasmaTier <= 0) return unboostedTime;
        return (int) (unboostedTime * Math.max((1D - 0.1D * (plasmaTier - 1)) / overdriveValue, 0.5D));
    }

    private int getBonusStackChance(int plasmaTier) {
        if (plasmaTier <= 0 || plasmaTier > 5) return 0;
        double overdriveValue = overdrive / 100.0D;
        return Math.min(
            (int) ((GTUtility.powInt((double) plasmaTier / 6, 3) * 10000) * (2.0D - overdriveValue)),
            BONUS_STACK_MAX_CHANCE);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("distance", distance);
        aNBT.setInteger("overdrive", overdrive);
        aNBT.setBoolean("cycleEnabled", cycleEnabled);
        aNBT.setInteger("range", range);
        aNBT.setInteger("step", step);
        aNBT.setInteger("cycleDistance", cycleDistance);
        aNBT.setBoolean("isWhitelisted", isWhitelisted);
        if (filterInventory != null) {
            aNBT.setTag("whitelist", filterInventory.serializeNBT());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("distance")) setDistance(aNBT.getInteger("distance"));
        if (aNBT.hasKey("overdrive")) setOverdrive(aNBT.getInteger("overdrive"));
        if (aNBT.hasKey("cycleEnabled")) setCycleEnabled(aNBT.getBoolean("cycleEnabled"));
        if (aNBT.hasKey("range")) setRange(aNBT.getInteger("range"));
        if (aNBT.hasKey("step")) setStep(aNBT.getInteger("step"));
        if (aNBT.hasKey("cycleDistance")) setCycleDistance(aNBT.getInteger("cycleDistance"));
        isWhitelisted = aNBT.getBoolean("isWhitelisted");
        if (filterInventory != null) {
            filterInventory.deserializeNBT(aNBT.getCompoundTag("whitelist"));
        }
        generateOreConfigurationList();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(getMachineTypeKey()))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE.toString() + EnumChatFormatting.BOLD.toString()
                    + StatCollector.translateToLocal("machine.spacemoduleminer.tooltip.meme"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleminer.tooltip.need_t5"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleminer.tooltip.plasma"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleminer.tooltip.computation"))
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("machine.spacemodule.tooltip.0"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.spacemodule.tooltip.1"))
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("machine.spacemodule.tooltip.crossparallel"))
            .beginStructureBlock(1, 5, 2, false)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_4th_layer"))
            .addCasing("0-8", StatCollector.translateToLocal("gt.blockcasings.ig.0.name"), false)
            .addInputHatch("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addInputBus("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addOutputBus("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addMiscHatch(
                "1+",
                StatCollector.translateToLocal("ig.elevator.structure.DataInputHatch"),
                StatCollector.translateToLocal("gt.mbtt.structure.any_casing"),
                1)
            .addStructureInfo("")
            .addStructureFooter(StatCollector.translateToLocal("ig.elevator.structure.SharedResources"))
            .toolTipFinisher();
        return tt;
    }

    private static IIconContainer engraving;

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] {
                Textures.BlockIcons.getCasingTextureForId(TileEntitySpaceElevator.CASING_INDEX_BASE),
                new TTRenderedExtendedFacingTexture(aActive ? ScreenON : ScreenOFF) };
        } else if (facing.getRotation(ForgeDirection.UP) == side || facing.getRotation(ForgeDirection.DOWN) == side) {
            return new ITexture[] {
                Textures.BlockIcons.getCasingTextureForId(TileEntitySpaceElevator.CASING_INDEX_BASE),
                new TTRenderedExtendedFacingTexture(engraving) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(TileEntitySpaceElevator.CASING_INDEX_BASE) };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        engraving = Textures.BlockIcons.custom("iconsets/OVERLAY_SIDE_MINER_MODULE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new SpaceModuleMinerInfinity(mName);
    }
}
