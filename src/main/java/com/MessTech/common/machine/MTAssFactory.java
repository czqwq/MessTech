package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.MessTech.common.block.AssMatrixBlock;
import com.MessTech.common.block.MTBlocks;
import com.MessTech.common.gui.MTAssFactoryGui;
import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.MessTech.common.machine.Base.MTProcessingLogic;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import goodgenerator.api.recipe.GoodGeneratorRecipeMaps;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDataAccess;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;
import lombok.Getter;
import lombok.Setter;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * MessTech Assembly Factory.
 * <p>
 * The 'I' structure element is the tiered Assembly Matrix Block pair:
 * {@link MTBlocks#assMatrixBlock} (Tier 1) and {@link MTBlocks#advAssMatrixBlock} (Tier 2).
 * Mode 0 = Component Assembly Line recipes (generic ProcessingLogic).
 * Mode 1 = Assembly Line recipes (data-stick / Data Access based, requires LevelTier 2).
 */

public class MTAssFactory extends MTMultiMachineBase<MTAssFactory> implements ISurvivalConstructable {

    public MTAssFactory(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        machineMode = 0;
    }

    public MTAssFactory(String aName) {
        super(aName);
        machineMode = 0;
    }

    // region LevelTier
    /** Assembly Matrix tier derived from the 'I' structure blocks: 0=unformed, 1 or 2. */
    @Getter
    @Setter
    private int LevelTier = 0;
    // endregion

    // region Assembly Line data access hatches
    /** Data Access hatches used by Assembly Line mode (Mode 1). */
    public final ArrayList<MTEHatchDataAccess> mDataAccessHatches = new ArrayList<>();

    /** Recipes currently authorised by the controller data stick / Data Access hatches (Mode 1). */
    private final List<GTRecipe.RecipeAssemblyLine> allowedAssemblyRecipes = new ArrayList<>();

    public boolean addDataAccessToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity meta = aTileEntity.getMetaTileEntity();
        if (meta == null) return false;
        if (meta instanceof MTEHatchDataAccess dataAccess) {
            ((MTEHatch) meta).updateTexture(aBaseCasingIndex);
            return mDataAccessHatches.add(dataAccess);
        }
        return false;
    }
    // endregion

    // region GUI / Machine modes
    @Override
    protected @Nonnull MTAssFactoryGui getGui() {
        return new MTAssFactoryGui(this);
    }

    @Override
    public int totalMachineMode() {
        return 2;
    }

    @Override
    public String getMachineModeName(int mode) {
        return mode == 1 ? StatCollector.translateToLocal("machine.assfactory.mode.assemblyline")
            : StatCollector.translateToLocal("machine.assfactory.mode.component");
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isActive()) {
            if (aPlayer != null) {
                aPlayer.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("machine.assfactory.cannot_switch_active")));
            }
            return;
        }
        int next = nextMachineMode();
        if (next == 1 && LevelTier != 2) {
            if (aPlayer != null) {
                aPlayer.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + StatCollector.translateToLocal("machine.assfactory.require_tier2")));
            }
            return;
        }
        setMachineMode(next);
        if (aPlayer != null) {
            aPlayer.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Mode: " + getMachineModeName(machineMode) + EnumChatFormatting.RESET));
        }
    }

    @Override
    public void setMachineMode(int index) {
        super.setMachineMode(index);
    }
    // endregion

    // region Processing configuration
    @Override
    protected boolean isEnablePerfectOverclock() {
        return true;
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        if (this.LevelTier == 2) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.pow(3, getInputVoltageTier());
    }
    // endregion

    // region Structure piece offsets
    private static final int HORIZONTAL_OFFSET = 5;
    private static final int VERTICAL_OFFSET = 7;
    private static final int DEPTH_OFFSET = 0;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"    HHH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HIH    ","    HHH    ","           "},
        {"   HJJJH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HCCCH   ","   HJJJH   ","           "},
        {" HHGJJJGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGBBBGHH "," HHGJJJGHH ","           "},
        {"HJJJJJJJJJH","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","H         H","H         H","H  AA AA  H","HJJJJJJJJJH","           "},
        {"HJJJJJJJJJH","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","J    D    J","J    D    J","J AA D AA J","HJJJJJJJJJH","           "},
        {"HJJJJFJJJJH","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","J   DAD   J","J   DAD   J","JAA DAD AAJ","HJJJJJJJJJH","           "},
        {"HJJJF~FJJJH","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","J  DAEAD  J","J  DAEAD  J","JA DAEAD AJ","HJJJJJJJJJH","           "},
        {"HJJFFFFFJJH","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","J   DAD   J","J   DAD   J","JA  DAD  AJ","HJJJJJJJJJH","           "},
        {"HJFFFFFFFJH","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","J    D    J","J    D    J","JA   D   AJ","HJJJJJJJJJH","           "},
        {"HFFFFFFFFFH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","           "},
        {"HFFFFFFFFFH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","HHHHHHHHHHH","           "},

    };
    //spotless:on
    /*
     * Blocks:
     * A -> addElement('A', Casings.AssemblyLineCasing.asElement())
     * B -> addElement('B', Casings.AssemblerMachineCasing.asElement())
     * C -> addElement('C', Casings.HermeticCasing9.asElement())
     * D -> addElement('D', Casings.ComputerHeatVent.asElement())
     * E -> addElement('E', Casings.AdvancedComputerCasing.asElement())
     * F -> addElement('F', hatch adder: InputBus / InputHatch / Energy.or(ExoticEnergy) / OutputBus /
     * OutputHatch / DataAccess; hatch background texture = AdvancedMolecularCasing; fill block = Advanced
     * Molecular Casing (buildAndChain))
     * G -> addElement('G', Casings.MolecularCoil.asElement())
     * H -> addElement('H', Casings.UltimateMolecularCasing.asElement())
     * I -> addElement('I', ofBlocksTiered(AssMatrixBlock / AdvAssMatrixBlock -> Tier 1 & 2))
     * J -> addElement('J', Casings.QuantumGlass.asElement())
     * Offsets: 5, 7, 0
     * Dimensions (Width, Height, Length): 11, 12, 34
     */

    private static final IStructureDefinition<MTAssFactory> STRUCTURE_DEFINITION = StructureDefinition
        .<MTAssFactory>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        // A: assembly line casing
        .addElement('A', Casings.AssemblyLineCasing.asElement())
        // B: assembler machine casing
        .addElement('B', Casings.AssemblerMachineCasing.asElement())
        // C: hermetic casing IX
        .addElement('C', Casings.HermeticCasing9.asElement())
        // D: computer heat vent
        .addElement('D', Casings.ComputerHeatVent.asElement())
        // E: advanced computer casing
        .addElement('E', Casings.AdvancedComputerCasing.asElement())
        // F: hatch slot - input/output bus & hatch, energy or exotic energy hatch, and Data Access hatch
        // in Assembly Line mode; the fill block is the Advanced Molecular Casing.
        .addElement(
            'F',
            HatchElementBuilder.<MTAssFactory>builder()
                .atLeast(
                    HatchElement.InputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputBus,
                    HatchElement.OutputHatch,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy),
                    AssFactoryHatchElement.DataAccess)
                .casingIndex(Casings.AdvancedMolecularCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.AdvancedMolecularCasing.getBlock(),
                        Casings.AdvancedMolecularCasing.getBlockMeta())))
        // G: molecular coil
        .addElement('G', Casings.MolecularCoil.asElement())
        // H: ultimate molecular casing
        .addElement('H', Casings.UltimateMolecularCasing.asElement())
        // I: Assembly Matrix Block (Tier 1) / Advanced Assembly Matrix Block (Tier 2)
        .addElement(
            'I',
            ofBlocksTiered(
                MTAssFactory::getMatrixBlockTier,
                Arrays.asList(Pair.of(MTBlocks.assMatrixBlock, 0), Pair.of(MTBlocks.advAssMatrixBlock, 0)),
                0,
                MTAssFactory::setLevelTier,
                MTAssFactory::getLevelTier))
        // J: quantum glass
        .addElement('J', Casings.QuantumGlass.asElement())
        .build();
    // endregion

    @Override
    public IStructureDefinition<MTAssFactory> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return super.getWailaStack(accessor, config);
    }

    @Override
    public boolean hasWailaAdvancedBody(ItemStack itemStack, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return super.hasWailaAdvancedBody(itemStack, accessor, config);
    }

    @Override
    public void getWailaAdvancedBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaAdvancedBody(itemStack, currentTip, accessor, config);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        setLevelTier(0);
        mDataAccessHatches.clear();
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        if (machineMode == 1) {
            if (LevelTier != 2) {
                errors.add(StructureErrors.of("machine.assfactory.error.need_tier2"));
                return;
            }
            if (mDataAccessHatches.isEmpty()) {
                errors.add(StructureErrors.of("machine.assfactory.error.need_data_access"));
                return;
            }
        }
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return machineMode == 1 ? MTRecipeMaps.assFactoryAssemblyLineRecipes
            : GoodGeneratorRecipeMaps.componentAssemblyLineRecipes;
    }

    @Override
    public @Nonnull java.util.Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.<RecipeMap<?>>asList(
            GoodGeneratorRecipeMaps.componentAssemblyLineRecipes,
            MTRecipeMaps.assFactoryAssemblyLineRecipes);
    }

    @Override
    public @Nonnull CheckRecipeResult checkProcessing() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        if (machineMode == 1) {
            if (LevelTier != 2) return CheckRecipeResultRegistry.insufficientMachineTier(2);
            if (!collectAllowedAssemblyRecipes()) return CheckRecipeResultRegistry.NO_DATA_STICKS;
        }
        return super.checkProcessing();
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (machineMode == 1) {
            for (MTEHatchDataAccess dataAccess : mDataAccessHatches) {
                dataAccess.getBaseMetaTileEntity()
                    .setActive(true);
            }
        }
        return super.onRunningTick(aStack);
    }

    // region Assembly Line processing (Mode 1)

    /**
     * Reads the recipes authorised by the controller data stick and Data Access hatches.
     * Assembly Line mode does not search the whole registry: you must provide the flash drive.
     */
    private boolean collectAllowedAssemblyRecipes() {
        allowedAssemblyRecipes.clear();
        ItemStack controllerStack = getStackInSlot(getControllerSlotIndex());
        if (AssemblyLineUtils.isItemDataStick(controllerStack)) {
            allowedAssemblyRecipes.addAll(AssemblyLineUtils.findALRecipeFromDataStick(controllerStack));
        }
        for (MTEHatchDataAccess dataAccess : mDataAccessHatches) {
            allowedAssemblyRecipes.addAll(dataAccess.getAssemblyLineRecipes());
        }
        return !allowedAssemblyRecipes.isEmpty();
    }

    /**
     * Uses the standard ProcessingLogic (same input handling as every other GT machine, including
     * debug/phantom and ME buses) against {@link MTRecipeMaps#assFactoryAssemblyLineRecipes}, but
     * restricts matches to the recipes authorised by the flash drives / Data Access hatches.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new MTProcessingLogic() {

            @Override
            protected @NotNull Stream<GTRecipe> findRecipeMatches(@Nullable RecipeMap<?> map) {
                return super.findRecipeMatches(map).filter(MTAssFactory.this::isAssemblyRecipeAllowed);
            }

            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                // Component Assembly Line mode: recipe casing tier is limited by the energy hatch tier.
                if (machineMode == 0) {
                    long energyTier = getInputVoltageTier();
                    if (energyTier <= 0) return CheckRecipeResultRegistry.insufficientMachineTier(recipe.mSpecialValue);
                    if (recipe.mSpecialValue > energyTier) {
                        return CheckRecipeResultRegistry.insufficientMachineTier(recipe.mSpecialValue);
                    }
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            public CheckRecipeResult process() {
                setEuModifier(getEuModifier());
                setSpeedBonus(getSpeedBonus());
                setOverclock(isEnablePerfectOverclock() ? 4 : 2, 4);
                return super.process();
            }
        }.setMaxParallelSupplier(this::getLimitedMaxParallel);
    }

    private boolean isAssemblyRecipeAllowed(@Nullable GTRecipe recipe) {
        // Component Assembly Line mode must not be filtered by Assembly Line data sticks.
        if (machineMode != 1) return true;
        if (recipe == null || allowedAssemblyRecipes.isEmpty()) return false;
        if (recipe.mOutputs == null || recipe.mOutputs.length == 0) return false;
        ItemStack output = recipe.mOutputs[0];
        for (GTRecipe.RecipeAssemblyLine allowed : allowedAssemblyRecipes) {
            if (GTUtility.areStacksEqual(allowed.mOutput, output, true)) return true;
        }
        return false;
    }
    // endregion Assembly Line processing

    /**
     * Tier fetcher for StructureLib's {@code StructureUtility#ofBlocksTiered} used by structure
     * element 'I'.
     */
    private static Integer getMatrixBlockTier(Block block, int meta) {
        if (block instanceof AssMatrixBlock matrixBlock) {
            int tier = matrixBlock.getLevelTier();
            if (tier == 1 || tier == 2) return tier;
        }
        return null;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor) {
        return super.survivalConstruct(stackSize, elementBudget, source, actor);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFFSET,
            VERTICAL_OFFSET,
            DEPTH_OFFSET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTAssFactory(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("machine.assfactory.machinetype"))
            .addSeparator()
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.assfactory.tooltip.0"))
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("machine.assfactory.tooltip.1"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.assfactory.tooltip.2"))
            .addInfo(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("machine.assfactory.tooltip.5"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("machine.assfactory.tooltip.3"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.assfactory.poc"))
            .addStructureInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.assfactory.tooltip.4"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            // Front: Advanced Molecular Casing base + Quantum Force Transformer controller face overlay.
            return new ITexture[] {
                TextureFactory
                    .of(Casings.AdvancedMolecularCasing.getBlock(), Casings.AdvancedMolecularCasing.getBlockMeta()),
                TextureFactory.of(aActive ? TexturesGtBlock.oMCAQFTActive : TexturesGtBlock.oMCAQFT) };
        }
        return new ITexture[] { TextureFactory
            .of(Casings.UltimateMolecularCasing.getBlock(), Casings.UltimateMolecularCasing.getBlockMeta()) };
    }

    private enum AssFactoryHatchElement implements IHatchElement<MTAssFactory> {

        DataAccess;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchDataAccess.class);
        }

        @Override
        public IGTHatchAdder<MTAssFactory> adder() {
            return MTAssFactory::addDataAccessToMachineList;
        }

        @Override
        public long count(MTAssFactory t) {
            return t.mDataAccessHatches.size();
        }
    }
}
