package com.MessTech.common.machine.module;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.module.SpaceModuleAssemblerInfinityGui;
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
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhintergalactic.recipe.ResultNoSpaceProject;
import gtnhintergalactic.tile.multi.elevator.ElevatorUtil;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

/**
 * Infinite Space Assembler module.
 * <p>
 * Uses GTNH Intergalactic's real space assembler recipe pool, draws power from the GT wireless
 * network and runs an unlimited number of independent recipe cycles per machine cycle (cross-recipe
 * parallelism with Integer.MAX_VALUE parallel per recipe).
 */
public class SpaceModuleAssemblerInfinity extends SpaceModuleInfinityBase<SpaceModuleAssemblerInfinity> {

    private static final int MAX_CROSS_RECIPE_CYCLES = 256;

    protected static IIconContainer engraving;

    public SpaceModuleAssemblerInfinity(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public SpaceModuleAssemblerInfinity(String aName) {
        super(aName);
    }

    public int getModuleTier() {
        return 5;
    }

    @Override
    protected int getBatchTaskCount() {
        return 1;
    }

    @Override
    protected boolean generateOneBatchTask() {
        return false;
    }

    @Override
    protected int getMainBatchDuration() {
        return 20;
    }

    @Override
    protected boolean usesTickBatchedOutputs() {
        return false;
    }

    @Override
    protected String getMachineTypeKey() {
        return "machine.spacemoduleassembler.machinetype";
    }

    @Override
    protected RecipeMap<?> getRecipeMapImpl() {
        return IGRecipeMaps.spaceAssemblerRecipes;
    }

    @Override
    protected SpaceModuleAssemblerInfinityGui getGui() {
        return new SpaceModuleAssemblerInfinityGui(this);
    }

    @Override
    public int getMaxParallelRecipes() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                String neededProject = recipe.getMetadata(IGRecipeMaps.SPACE_PROJECT);
                String neededLocation = recipe.getMetadata(IGRecipeMaps.SPACE_LOCATION);
                if (getBaseMetaTileEntity() != null && !ElevatorUtil
                    .isProjectAvailable(getBaseMetaTileEntity().getOwnerUuid(), neededProject, neededLocation)) {
                    return new ResultNoSpaceProject(neededProject, neededLocation);
                }

                int recipeTier = recipe.getMetadataOrDefault(IGRecipeMaps.MODULE_TIER, 1);
                if (recipeTier > getModuleTier()) {
                    return CheckRecipeResultRegistry.insufficientMachineTier(recipeTier);
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected gregtech.api.util.OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return super.createOverclockCalculator(recipe).setNoOverclock(true);
            }
        }.setAmperageOC(false)
            .setMaxParallelSupplier(() -> Integer.MAX_VALUE)
            .setSpeedBonus(1.0F);
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing_EM() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        if (parentElevator == null || parentElevator.getMotorTier() < 5) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return checkProcessingWirelessLoop(MAX_CROSS_RECIPE_CYCLES);
    }

    public boolean addModuleHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex)
            || addInputBusToMachineList(aTileEntity, aBaseCasingIndex)
            || addOutputBusToMachineList(aTileEntity, aBaseCasingIndex);
    }

    @Override
    public IStructureDefinition<? extends tectech.thing.metaTileEntity.multi.base.TTMultiblockBase> getStructure_EM() {
        return StructureDefinition.<SpaceModuleAssemblerInfinity>builder()
            .addShape(
                "main",
                transpose(new String[][] { { "H", "H" }, { "~", "H" }, { "H", "H" }, { "H", "H" }, { "H", "H" } }))
            .addElement(
                'H',
                GTStructureUtility.ofHatchAdderOptional(
                    SpaceModuleAssemblerInfinity::addModuleHatchToMachineList,
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
        checkHasAnyInput(errors);
        checkHasOutputBus(errors);
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
        engraving = Textures.BlockIcons.custom("iconsets/OVERLAY_SIDE_ASSEMBLER_MODULE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(getMachineTypeKey()))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE.toString() + EnumChatFormatting.BOLD.toString()
                    + StatCollector.translateToLocal("machine.spacemoduleassembler.tooltip.meme"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleassembler.tooltip.need_t5"))
            .addInfo(StatCollector.translateToLocal("machine.spacemoduleassembler.tooltip.wireless"))
            .addInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("machine.spacemoduleassembler.tooltip.parallel"))
            .addInfo(
                EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("machine.spacemoduleassembler.tooltip.crossrecipe"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("machine.spacemoduleassembler.tooltip.project"))
            .beginStructureBlock(1, 5, 2, false)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_4th_layer"))
            // .addCasing("0-8", StatCollector.translateToLocal("gt.blockcasings.ig.0.name"), false)
            // .addInputAny("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            // .addOutputBus("1+", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addStructureInfo("")
            // .addStructureFooter(StatCollector.translateToLocal("ig.elevator.structure.SharedPower"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new SpaceModuleAssemblerInfinity(mName);
    }
}
