package com.MessTech.common.machine.module;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.module.SpaceModulePumpInfinityGui;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
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
import gtnhintergalactic.recipe.SpacePumpingRecipes;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

public class SpaceModulePumpInfinity extends SpaceModuleInfinityBase<SpaceModulePumpInfinity> {

    public static final int PARALLEL_RECIPES = 4;

    private final int[] planetTypes = { 1, 1, 1, 1 };
    private final int[] gasTypes = { 1, 1, 1, 1 };
    private final int[] parallels = { 1, 1, 1, 1 };
    private int batchSize = 1;
    private int pumpBatchIndex = 0;

    public SpaceModulePumpInfinity(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public SpaceModulePumpInfinity(String aName) {
        super(aName);
    }

    public int getPlanetType(int index) {
        return planetTypes[Math.clamp(index, 0, PARALLEL_RECIPES - 1)];
    }

    public void setPlanetType(int index, int value) {
        planetTypes[Math.clamp(index, 0, PARALLEL_RECIPES - 1)] = Math.max(1, value);
    }

    public int getGasType(int index) {
        return gasTypes[Math.clamp(index, 0, PARALLEL_RECIPES - 1)];
    }

    public void setGasType(int index, int value) {
        gasTypes[Math.clamp(index, 0, PARALLEL_RECIPES - 1)] = Math.max(1, value);
    }

    public int getParallel(int index) {
        return parallels[Math.clamp(index, 0, PARALLEL_RECIPES - 1)];
    }

    public void setParallel(int index, int value) {
        parallels[Math.max(0, Math.min(index, PARALLEL_RECIPES - 1))] = Math.max(1, Math.min(value, 1000000));
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int value) {
        this.batchSize = Math.clamp(value, 1, 128);
    }

    public int getParallelRecipes() {
        return PARALLEL_RECIPES;
    }

    @Override
    protected String getMachineTypeKey() {
        return "machine.spacemodulepump.machinetype";
    }

    @Override
    protected RecipeMap<?> getRecipeMapImpl() {
        // The pump uses the custom SpacePumpingRecipes map, not a GT RecipeMap.
        return null;
    }

    @Override
    protected @NotNull SpaceModulePumpInfinityGui getGui() {
        return new SpaceModulePumpInfinityGui(this);
    }

    @Override
    public IStructureDefinition<SpaceModulePumpInfinity> getStructureDefinition() {
        return StructureDefinition.<SpaceModulePumpInfinity>builder()
            .addShape(
                "main",
                transpose(new String[][] { { "H", "H" }, { "~", "H" }, { "H", "H" }, { "H", "H" }, { "H", "H" } }))
            .addElement(
                'H',
                GTStructureUtility.ofHatchAdderOptional(
                    SpaceModulePumpInfinity::addOutputHatchToMachineList,
                    TileEntitySpaceElevator.CASING_INDEX_BASE,
                    1,
                    GregTechAPI.sBlockCasingsSE,
                    0))
            .build();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        parentElevator = findConnectedElevator(aBaseMetaTileEntity);
        if (parentElevator == null || parentElevator.getMotorTier() < 5) {
            errors.add(StructureErrors.of("machine.spacemodule.need_elevator_t5"));
            return;
        }
        if (!checkPiece("main", 0, 1, 0, errors)) return;
        checkHasOutputHatch(errors);
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
        TileEntity self = (TileEntity) aBaseMetaTileEntity;
        if (self.getWorldObj() == null) return null;
        World world = self.getWorldObj();
        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        int range = 8;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    TileEntity te = world.getTileEntity(x + dx, y + dy, z + dz);
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
    public @NotNull CheckRecipeResult checkProcessing() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        if (parentElevator == null || parentElevator.getMotorTier() < 5) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return startBatchedProcessing();
    }

    @Override
    protected int getBatchTaskCount() {
        return PARALLEL_RECIPES;
    }

    @Override
    protected int getMainBatchDuration() {
        return 20 * Math.clamp(batchSize, 1, 128);
    }

    @Override
    protected void onBatchStart() {
        pumpBatchIndex = 0;
    }

    @Override
    protected boolean generateOneBatchTask() {
        int batch = Math.clamp(batchSize, 1, 128);
        while (pumpBatchIndex < PARALLEL_RECIPES) {
            int index = pumpBatchIndex++;
            FluidStack fluid = SpacePumpingRecipes.RECIPES.get(Pair.of(planetTypes[index], gasTypes[index]));
            if (fluid == null) continue;

            int parallel = Math.max(1, Math.min(parallels[index], 1000000));
            long amount = (long) fluid.amount * parallel * batch;
            FluidStack out = fluid.copy();
            out.amount = (int) Math.min(amount, Integer.MAX_VALUE);

            long eut = gregtech.api.enums.GTValues.V[14] + (long) (parallel - 1) * gregtech.api.enums.GTValues.V[13];
            int duration = 20 * batch;
            CheckRecipeResult result = validateWirelessPowerForRecipe(eut, duration, 1);
            if (!result.wasSuccessful()) return false;
            java.math.BigInteger cost = java.math.BigInteger.valueOf(eut)
                .multiply(java.math.BigInteger.valueOf(duration));
            if (ownerUUID == null
                || !gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, cost.negate())) {
                return false;
            }
            costingEU = cost;
            costingEUText = String.valueOf(cost);

            batchFluidOutputs.add(out);
            // Wireless power was already deducted in one lump; don't drain energy hatches too.
            lEUt = 0;
            mEfficiencyIncrease = 10000;
            return true;
        }
        return false;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(getMachineTypeKey()))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE.toString() + EnumChatFormatting.BOLD.toString()
                    + StatCollector.translateToLocal("machine.spacemodulepump.tooltip.meme"))
            .addInfo(StatCollector.translateToLocal("machine.spacemodulepump.tooltip.need_t5"))
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("machine.spacemodule.tooltip.0"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.spacemodule.tooltip.1"))
            .beginStructureBlock(1, 5, 2, false)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_4th_layer"))
            .addStructureInfo("")
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
        engraving = Textures.BlockIcons.custom("iconsets/OVERLAY_SIDE_PUMP_MODULE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setIntArray("planetTypes", planetTypes);
        aNBT.setIntArray("gasTypes", gasTypes);
        aNBT.setIntArray("parallels", parallels);
        aNBT.setInteger("batchSize", batchSize);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        int[] loadedPlanets = aNBT.getIntArray("planetTypes");
        int[] loadedGases = aNBT.getIntArray("gasTypes");
        int[] loadedParallels = aNBT.getIntArray("parallels");
        if (loadedPlanets.length == PARALLEL_RECIPES) {
            System.arraycopy(loadedPlanets, 0, planetTypes, 0, PARALLEL_RECIPES);
        }
        if (loadedGases.length == PARALLEL_RECIPES) {
            System.arraycopy(loadedGases, 0, gasTypes, 0, PARALLEL_RECIPES);
        }
        if (loadedParallels.length == PARALLEL_RECIPES) {
            for (int i = 0; i < PARALLEL_RECIPES; i++) {
                parallels[i] = Math.max(1, Math.min(loadedParallels[i], 1000000));
            }
        }
        if (aNBT.hasKey("batchSize")) {
            batchSize = Math.max(1, Math.min(aNBT.getInteger("batchSize"), 128));
        }
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new SpaceModulePumpInfinity(mName);
    }
}
