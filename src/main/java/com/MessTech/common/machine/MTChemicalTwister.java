package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static goodgenerator.loader.Loaders.compactFusionCoil;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.MessTech.common.machine.Base.MTProcessingLogic;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.TAE;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.misc.GTStructureChannels;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

public class MTChemicalTwister extends MTMultiMachineBase<MTChemicalTwister>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final int HORIZONTAL_OFFSET = 8;
    private static final int VERTICAL_OFFSET = 8;
    private static final int DEPTH_OFFSET = 0;

    // region Controller textures
    /**
     * Face of the level 2 (概率毁灭者) controller = GT++ QFT's own four icon containers (what
     * {@code MTEQuantumForceTransformer#getTexture} passes to {@link Textures.BlockIcons#createTextureWithCasing}).
     * <p>
     * Fields, not lazy reads: a {@code TexturesGtBlock.CustomIcon} registers with
     * {@code GregTechAPI.sGTBlockIconload} in its constructor and GT walks that list only while loading textures, so a
     * first-frame read would render an empty face.
     */
    private static final IIconContainer QFT_FACE = TexturesGtBlock.oMCAQFT;
    private static final IIconContainer QFT_FACE_GLOW = TexturesGtBlock.oMCAQFTGlow;
    private static final IIconContainer QFT_FACE_ACTIVE = TexturesGtBlock.oMCAQFTActive;
    private static final IIconContainer QFT_FACE_ACTIVE_GLOW = TexturesGtBlock.oMCAQFTActiveGlow;

    /**
     * Casing of the level 2 controller (element {@code F}): the QFT shell's BulkProductionFrame (GT++
     * {@code blockCasings2Misc:12}), copied as a block because its GT casing id {@code TAE.getIndexFromPage(1, 12)} is
     * an unregistered slot (the block skips meta 12) - what {@code TextureFactory.of} does for any casing-index-less
     * block.
     */
    private static final ITexture TIER2_CASING = TextureFactory
        .of(Casings.BulkProductionFrame.getBlock(), Casings.BulkProductionFrame.getBlockMeta());
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";
    /** The level 2 (概率毁灭者) piece; its hatches sit on the SpaceTimeContinuumRipper. */
    protected static final String STRUCTURE_PIECE_TIER2 = "tier2";

    // spotless:off
    private static final String[][] structure_tier2 = new String[][]{
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        F        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","       FHF       ","       HIH       ","       FHF       ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","      F H F      ","       I I       ","      H   H      ","       I I       ","      F H F      ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","     F  H  F     ","      I J I      ","       JJJ       ","     HJJJJJH     ","       JJJ       ","      I J I      ","     F  H  F     ","                 ","                 ","                 ","                 ","                 "},
        {"        H        ","                 ","                 ","                 ","    F   H   F    ","     I  J  I     ","       JGJ       ","      JGGGJ      ","H   HJGGGGGJH   H","      JGGGJ      ","       JGJ       ","     I  J  I     ","    F   H   F    ","                 ","                 ","                 ","        H        "},
        {"       HHH       ","                 ","                 ","   F    H    F   ","    I       I    ","       JJJ       ","      JGGGJ      ","H    JGGGGGJ    H","H  H JGGGGGJ H  H","H    JGGGGGJ    H","      JGGGJ      ","       JJJ       ","    I       I    ","   F    H    F   ","                 ","                 ","       HHH       "},
        {"     FHH~HHF     ","    F   H   F    ","   F    H    F   ","  FI   H H   IF  "," F    H   H    F ","F    H JJJ H    F","H   H JGGGJ H   H","H  H JGGGGGJ H  H","HHH  JGGGGGJ  HHH","H  H JGGGGGJ H  H","H   H JGGGJ H   H","F    H JJJ H    F"," F    H   H    F ","  FI   H H   IF  ","   F    H    F   ","    F   H   F    ","     FHHHHHF     "},
        {"       HHH       ","                 ","                 ","   F    H    F   ","    I       I    ","       JJJ       ","      JGGGJ      ","H    JGGGGGJ    H","H  H JGGGGGJ H  H","H    JGGGGGJ    H","      JGGGJ      ","       JJJ       ","    I       I    ","   F    H    F   ","                 ","                 ","       HHH       "},
        {"        H        ","                 ","                 ","                 ","    F   H   F    ","     I     I     ","       JJJ       ","      JJGJJ      ","H   H JGGGJ H   H","      JJGJJ      ","       JJJ       ","     I     I     ","    F   H   F    ","                 ","                 ","                 ","        H        "},
        {"                 ","                 ","                 ","                 ","                 ","     F  H  F     ","      I   I      ","       JJJ       ","     H JJJ H     ","       JJJ       ","      I   I      ","     F  H  F     ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","      F H F      ","       I I       ","      H   H      ","       I I       ","      F H F      ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","       FHF       ","       HIH       ","       FHF       ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        F        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "},

    };
    /*
    Level 2 (概率毁灭者 / Probability Destroyer) repeats the shape with its own letters: StructureLib binds the
    element map to the whole structure definition, not per shape, and level 1 already means different casings with A-E.
    F: BulkProductionFrame   G: QuantumForceTransformerCoilCasing   H: SpaceTimeContinuumRipper + its hatches
    I: SpaceTimeBendingCore   J: ForceFieldGlass
    Offsets 8, 8, 0 (same controller position as level 1)
    */

    //spotless:on

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","DDDDDDDDDDDDDDDDD","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","D               D","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","D               D","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","D               D","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","       B B       ","D       B       D","       B B       ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","      B A B      ","       AAA       ","D     AAAAA     D","       AAA       ","      B A B      ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"      DDDDD      ","                 ","                 ","                 ","                 ","     B  A  B     ","       ACA       ","      ACCCA      ","D    ACCACCA    D","      ACCCA      ","       ACA       ","     B  A  B     ","                 ","                 ","                 ","                 ","        D        "},
        {"      DDDDD      ","                 ","                 ","                 ","                 ","     B AAA B     ","      ACCCA      ","     ACCCCCA     ","D    ACCACCA    D","     ACCCCCA     ","      ACCCA      ","     B AAA B     ","                 ","                 ","                 ","                 ","        D        "},
        {"DDDDDDDD~DDDDDDDD","D       B       D","D       B       D","D       B       D","D     BBBBB     D","D    B AAA B    D","D   B ACACA B   D","D   BACCACCAB   D","DBBBBAAA AAABBBBD","D   BACCACCAB   D","D   B ACACA B   D","D    B AAA B    D","D     BBBBB     D","D       B       D","D       B       D","D       B       D","DDDDDDDDDDDDDDDDD"},
        {"      DDDDD      ","                 ","                 ","                 ","                 ","     B AAA B     ","      ACCCA      ","     ACCCCCA     ","D    ACCACCA    D","     ACCCCCA     ","      ACCCA      ","     B AAA B     ","                 ","                 ","                 ","                 ","        D        "},
        {"      DDDDD      ","                 ","                 ","                 ","                 ","     B     B     ","       AAA       ","      AACAA      ","D     ACACA     D","      AACAA      ","       AAA       ","     B     B     ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","      B   B      ","       AAA       ","D      AAA      D","       AAA       ","      B   B      ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","       B B       ","D       B       D","       B B       ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","D               D","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","D               D","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","D               D","                 ","                 ","                 ","                 ","                 ","                 ","                 ","        D        "},
        {"        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","DDDDDDDDDDDDDDDDD","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        ","        D        "},

    };
    // spotless:on

    private static final IStructureDefinition<MTChemicalTwister> STRUCTURE_DEFINITION = StructureDefinition
        .<MTChemicalTwister>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        .addElement(
            'D',
            HatchElementBuilder.<MTChemicalTwister>builder()
                .atLeast(
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputHatch,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy))
                .casingIndex(Casings.ChemicallyInertMachineCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.ChemicallyInertMachineCasing.getBlock(),
                        Casings.ChemicallyInertMachineCasing.getBlockMeta())))
        .addElement('A', Casings.ContainmentFieldMachineCasing.asElement())
        .addElement('B', ofBlock(compactFusionCoil, 3))
        .addElement(
            'C',
            GTStructureChannels.HEATING_COIL
                .use(activeCoils(ofCoil(MTChemicalTwister::setCoilLevel, MTChemicalTwister::getCoilLevel))))
        // level 2 (概率毁灭者): shape as in structure_tier2, letters F-J, hatches on the
        // SpaceTimeContinuumRipper (element H). No coil ring: that mode runs at a fixed 12601 K.
        .addShape(STRUCTURE_PIECE_TIER2, transpose(structure_tier2))
        .addElement('F', Casings.BulkProductionFrame.asElement())
        .addElement('G', Casings.QuantumForceTransformerCoilCasing.asElement())
        .addElement(
            'H',
            HatchElementBuilder.<MTChemicalTwister>builder()
                .atLeast(
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputHatch,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy))
                // The ripper has no GT casing texture page (Casings#textureId is -1) and
                // HatchElementBuilder#casingIndex rejects <= 0 - that crashed the game during class init. GT++'s own
                // QFT uses TAE.getIndexFromPage(0, 10) for this casing's hatches, so we match it.
                .casingIndex(TAE.getIndexFromPage(0, 10))
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.SpaceTimeContinuumRipper.getBlock(),
                        Casings.SpaceTimeContinuumRipper.getBlockMeta())))
        .addElement('I', Casings.SpaceTimeBendingCore.asElement())
        .addElement('J', Casings.ForceFieldGlass.asElement())
        .build();
    // endregion

    // region Coil / heat
    /**
     * Level of the heating coil ring (element {@code C}); {@link HeatingCoilLevel#None} = no coil (or a rejected one).
     */
    private HeatingCoilLevel mCoilLevel = HeatingCoilLevel.None;

    /**
     * Heat the controller works with: coil ring heat plus the energy tier bonus (the EBF formula), see
     * {@link #checkMachine}.
     */
    private int mHeatingCapacity;

    /** @return the level of the coil ring, {@link HeatingCoilLevel#None} when none was found. */
    public HeatingCoilLevel getCoilLevel() {
        return mCoilLevel;
    }

    /**
     * Called by structure element {@code C} once per coil block ({@code GTStructureUtility.ofCoil}); a level differing
     * from the first one fails the structure check.
     */
    public void setCoilLevel(HeatingCoilLevel aCoilLevel) {
        mCoilLevel = aCoilLevel;
    }

    /**
     * @return the heat of the coil ring itself, i.e. the value a heat recipe asks for in its {@code mSpecialValue}.
     */
    public int getCoilHeat() {
        return (int) getCoilLevel().getHeat();
    }

    /**
     * @return the heat the recipes are checked against, i.e. {@link #getCoilHeat()} plus the tier bonus; zero while
     *         the structure is not formed, see {@link #checkMachine}.
     */
    public int getHeatingCapacity() {
        return mHeatingCapacity;
    }
    // endregion

    // region Structure level
    /**
     * Level of the formed structure (0 = not formed; levels 3..4 come later, see {@link #checkMachine}).
     * <p>
     * Independent gate of the coil ring: the ring gives the heat, the level decides which recipes run at all -
     * {@link MTRecipeMaps#CHEMICAL_TWISTER_STRUCTURE_LEVEL} above this gives {@code insufficientMachineTier}. It also
     * picks the controller face ({@link #getTexture}) and is synced with {@link #getUpdateData}.
     */
    private int mStructureLevel;

    /** @return the level of the formed structure (0 when the machine is not formed). */
    public int getStructureLevel() {
        return mStructureLevel;
    }
    // endregion

    public MTChemicalTwister(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTChemicalTwister(String aName) {
        super(aName);
    }

    @Override
    public IStructureDefinition<MTChemicalTwister> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // Coil level, heat and structure level describe the structure: reset all three first, so a check that fails
        // half way cannot leave stale values behind.
        this.mHeatingCapacity = 0;
        this.mStructureLevel = 0;
        setCoilLevel(HeatingCoilLevel.None);

        // Level 2 (概率毁灭者) first, speculatively (null, not the error list - see MTEPCBFactory#checkForNewTier), so a
        // level 1 build reports only its own errors. It has no coil ring - a fixed 12601 K (Hypogen), see
        // MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT - so no coil is read.
        if (checkPiece(STRUCTURE_PIECE_TIER2, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, null)) {
            this.mStructureLevel = 2;
            this.mHeatingCapacity = MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT;
            checkHasAnyInput(errors);
            checkHasAnyOutput(errors);
            checkHasAnyEnergy(errors);
            return;
        }

        // Level 1: coil ring of element 'C' plus the energy tier bonus, the EBF formula (levels 3..4 would go the
        // same way, biggest piece first).
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        this.mStructureLevel = 1;

        if (getCoilLevel() == HeatingCoilLevel.None) {
            errors.add(StructureErrorRegistry.COIL_LEVEL_NOT_ENOUGH);
        }

        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);

        // Without an energy hatch this computes a lower value; the missing hatch is a structure error anyway.
        this.mHeatingCapacity = getCoilHeat() + 100 * (GTUtility.getTier(getMaxInputVoltage()) - 2);
    }

    /** Heat goes to every reader of device information (scanner, sensor card, metrics), like the EBF's own heat. */
    @Override
    public void getExtraInfoData(List<String> info) {
        super.getExtraInfoData(info);
        info.add(IGregTechDeviceInformation.encode("GT5U.EBF.heat.s", NumberFormatUtil.formatNumber(mHeatingCapacity)));
    }

    /**
     * Two gates, in order:
     * <ol>
     * <li><b>structure level</b>: above it, {@link MTRecipeMaps#CHEMICAL_TWISTER_STRUCTURE_LEVEL} (default
     * {@link MTRecipeMaps#DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL}) gives
     * {@code CheckRecipeResultRegistry.insufficientMachineTier};</li>
     * <li><b>heat</b>: {@code mSpecialValue} above coil heat plus tier bonus gives {@code insufficientHeat}.</li>
     * </ol>
     * Heat recipes overclock like the EBF: required vs available heat gives the heat discount and the heat overclocks;
     * {@code mSpecialValue} zero keeps the standard {@link MTMultiMachineBase} overclocking.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new MTProcessingLogic() {

            @Nonnull
            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                int requiredLevel = recipe.getMetadataOrDefault(
                    MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL,
                    MTRecipeMaps.DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL);
                if (requiredLevel > mStructureLevel) {
                    return CheckRecipeResultRegistry.insufficientMachineTier(requiredLevel);
                }

                return recipe.mSpecialValue <= mHeatingCapacity ? CheckRecipeResultRegistry.SUCCESSFUL
                    : CheckRecipeResultRegistry.insufficientHeat(recipe.mSpecialValue);
            }

            @Nonnull
            @Override
            protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                OverclockCalculator calculator = super.createOverclockCalculator(recipe);
                if (recipe.mSpecialValue <= 0) return calculator;

                return calculator.setRecipeHeat(recipe.mSpecialValue)
                    .setMachineHeat(mHeatingCapacity)
                    .setHeatOC(true)
                    .setHeatDiscount(true);
            }

            @Nonnull
            @Override
            public CheckRecipeResult process() {
                setEuModifier(getEuModifier());
                setSpeedBonus(getSpeedBonus());
                setOverclock(isEnablePerfectOverclock() ? 4 : 2, 4);
                return super.process();
            }
        }.setMaxParallelSupplier(this::getLimitedMaxParallel);
    }

    // region Machine modes
    /**
     * Mode 0: the one-step chemical recipes of {@link MTRecipeMaps#MTChemicalTwisterRecipes} on the level 1 structure.
     */
    public static final int MODE_CHEMICAL_TWISTER = 0;
    /** Mode 1: the QFT recipes at 100% output chance on the level 2 (概率毁灭者) structure, fixed 12601 K. */
    public static final int MODE_QFT_PROBABILITY_DESTROYER = 1;
    /**
     * Mode 2: the comb processing recipes of {@link MTRecipeMaps#combProcessingRecipes} on the level 2 (蜂窝处理者)
     * structure, fixed 12601 K. The recipes are generated by {@code MTCombProcessingRecipes} from GT5U's large chemical
     * reactor comb recipes and carry the {@code MTItemList.MTCombProcessingModule} as a non-consumed input.
     */
    public static final int MODE_COMB_PROCESSOR = 2;
    /**
     * Level of the first bigger structure (概率毁灭者), and the trigger stack size at which a build hint / NEI preview
     * switches from level 1 to level 2 - GT's tier convention, see {@link #getStructurePieceForBuild}. From here on
     * the controller renders the QFT face, see {@link #getTexture}.
     */
    public static final int STRUCTURE_LEVEL_TIER2 = 2;

    @Override
    public int totalMachineMode() {
        return 3;
    }

    @Override
    public String getMachineModeName(int mode) {
        return switch (mode) {
            case MODE_QFT_PROBABILITY_DESTROYER -> StatCollector
                .translateToLocal("machine.largechemicaltwister.mode.probability_destroyer");
            case MODE_COMB_PROCESSOR -> StatCollector
                .translateToLocal("machine.largechemicaltwister.mode.comb_processor");
            default -> StatCollector.translateToLocal("machine.largechemicaltwister.mode.chemical_twister");
        };
    }

    /**
     * The cycle follows the structure: 化学扭曲 (0) -> 概率毁灭者 (1) -> 蜂窝处理者 (2) -> 化学扭曲 (0). Mode 2 only
     * runs recipes that ask for structure level 2, so a level 1 build skips it and cycles between modes 0 and 1 only -
     * there is no point in offering a mode whose recipes the current structure can never run. Both callers (screwdriver
     * and the GUI button) go through this method, so the gate holds for both; the level is synced to the client, see
     * {@link #getUpdateData()}.
     */
    @Override
    public int nextMachineMode() {
        return switch (machineMode) {
            case MODE_CHEMICAL_TWISTER -> MODE_QFT_PROBABILITY_DESTROYER;
            // 蜂窝处理者 needs the level 2 shell, otherwise wrap back to the start.
            case MODE_QFT_PROBABILITY_DESTROYER -> mStructureLevel >= STRUCTURE_LEVEL_TIER2 ? MODE_COMB_PROCESSOR
                : MODE_CHEMICAL_TWISTER;
            default -> MODE_CHEMICAL_TWISTER;
        };
    }

    /**
     * The screwdriver switches modes (the base class also offers a GUI button when {@code totalMachineMode() > 1}),
     * refused while the machine runs so the running recipe cannot change under it. When the 概率毁灭者 mode would cycle
     * to 蜂窝处理者 but the structure is not level 2, {@link #nextMachineMode()} skips it and the player is told why.
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isActive()) {
            if (aPlayer != null) {
                aPlayer.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("machine.largechemicaltwister.cannot_switch_active")));
            }
            return;
        }
        boolean combProcessorSkipped = machineMode == MODE_QFT_PROBABILITY_DESTROYER
            && mStructureLevel < STRUCTURE_LEVEL_TIER2;
        setMachineMode(nextMachineMode());
        if (aPlayer != null) {
            if (combProcessorSkipped) {
                aPlayer.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("machine.largechemicaltwister.mode.comb_requires_tier2")));
            }
            aPlayer.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Mode: " + getMachineModeName(machineMode) + EnumChatFormatting.RESET));
        }
    }

    /**
     * Each mode has its own pool; mode 2's recipes ask for structure level 2 via metadata, so an invalid structure
     * reports {@code insufficientMachineTier} rather than silently running the wrong recipes.
     */
    @Override
    public RecipeMap<?> getRecipeMap() {
        return switch (machineMode) {
            case MODE_QFT_PROBABILITY_DESTROYER -> MTRecipeMaps.qftProbabilityDestroyerRecipes;
            case MODE_COMB_PROCESSOR -> MTRecipeMaps.combProcessingRecipes;
            default -> MTRecipeMaps.MTChemicalTwisterRecipes;
        };
    }

    /** All pools are listed so NEI shows this machine as the catalyst for each of them. */
    @Override
    public @Nonnull Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.asList(
            MTRecipeMaps.MTChemicalTwisterRecipes,
            MTRecipeMaps.qftProbabilityDestroyerRecipes,
            MTRecipeMaps.combProcessingRecipes);
    }
    // endregion

    /**
     * Which piece a build hint / survival build / NEI preview shows. The trigger's stack size is GT's "structure tier"
     * switch: {@code 1} = level 1, {@code >= 2} = level 2 - which is what makes BlockRenderer6343 offer its Tier slider
     * ({@code construct()} is called with {@code stackSize = 1, 2, 3, ...} until nothing changes). An explicit tier
     * wins, else the mode decides; the NEI preview builds a fresh machine, so it starts at level 1, while the modes
     * that need the level 2 shell (概率毁灭者, 蜂窝处理者) get level 2 hints in game.
     */
    private String getStructurePieceForBuild(@Nullable ItemStack stackSize) {
        if (stackSize != null && stackSize.stackSize >= STRUCTURE_LEVEL_TIER2) return STRUCTURE_PIECE_TIER2;
        if (machineMode == MODE_QFT_PROBABILITY_DESTROYER || machineMode == MODE_COMB_PROCESSOR) {
            return STRUCTURE_PIECE_TIER2;
        }
        return STRUCTURE_PIECE_MAIN;
    }

    /**
     * Makes the controller render the structure a build hint / survival build is about to place, see
     * {@link #getStructurePieceForBuild}. A formed machine keeps its level ({@link #checkMachine}, synced on its own);
     * an unformed one matters, as BlockRenderer6343 previews it on a dummy multiblock in a world GT never ticks - the
     * build trigger is the only place the preview can learn which structure it shows.
     */
    private void updatePreviewStructureLevel(@Nullable ItemStack stackSize) {
        if (mMachine) return;
        mStructureLevel = getStructurePieceForBuild(stackSize).equals(STRUCTURE_PIECE_TIER2) ? STRUCTURE_LEVEL_TIER2
            : 1;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        updatePreviewStructureLevel(stackSize);
        buildPiece(
            getStructurePieceForBuild(stackSize),
            stackSize,
            hintsOnly,
            HORIZONTAL_OFFSET,
            VERTICAL_OFFSET,
            DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        updatePreviewStructureLevel(stackSize);
        return survivalBuildPiece(
            getStructurePieceForBuild(stackSize),
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
        return new MTChemicalTwister(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.chemicaltwister.machinetype"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.desc"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.structure.header"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.structure.level1"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.structure.level2"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.structure.hatches"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.structure.coil"))
            .addSeparator()
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.modes.header"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.modes.switch"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.modes.chemical"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.modes.qft"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.modes.comb"))
            .addSeparator()
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.header"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.formula"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.oc"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.poc"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.parallel"))
            .addSeparator()
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .addStructureInfo(translateToLocal("machine.chemicaltwister.structureinfo.1"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        // The face follows the formed structure, not the mode: level 1 = Large Chemical Reactor controller (idle or
        // running, each with its glow layer) on Chemically Inert Machine Casing, level 2 = 概率毁灭者 (QFT shell), QFT
        // face on BulkProductionFrame. GT composes both, see getCasingTexture and STRUCTURE_LEVEL_TIER2.
        if (mStructureLevel >= STRUCTURE_LEVEL_TIER2) {
            return Textures.BlockIcons.createTextureWithCasing(
                this,
                side,
                facing,
                aActive,
                QFT_FACE,
                QFT_FACE_GLOW,
                QFT_FACE_ACTIVE,
                QFT_FACE_ACTIVE_GLOW);
        }
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            facing,
            aActive,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW);
    }

    /**
     * Casing of the controller, i.e. what element {@code D} (level 1) or {@code F} (level 2) is built from; see
     * {@link #getTexture}. The level is client side too, see {@link #getUpdateData}.
     */
    @Override
    public ITexture getCasingTexture() {
        if (mStructureLevel >= STRUCTURE_LEVEL_TIER2) return TIER2_CASING;
        return Casings.ChemicallyInertMachineCasing.getCasingTexture();
    }

    /**
     * The structure level decides how the controller looks (see {@link #getTexture}), so the client needs it - and
     * only a structure check can know it ({@code MTEMultiBlockBase#checkStructure} does nothing on the client). GT
     * syncs this byte in the tile data packet whenever it changes, so a loaded chunk renders right away.
     */
    @Override
    public byte getUpdateData() {
        return (byte) mStructureLevel;
    }

    /** Client side mirror of the level the last structure check found, see {@link #getUpdateData}. */
    @Override
    public void onValueUpdate(byte aValue) {
        mStructureLevel = aValue;
    }

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
        return 123123;
    }
}
