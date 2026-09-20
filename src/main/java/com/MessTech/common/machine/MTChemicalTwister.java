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
import net.minecraft.entity.player.EntityPlayerMP;
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
import com.gtnewhorizon.structurelib.structure.IItemSource;
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
    // endregion

    // region Controller textures
    /**
     * Face of the level 2 (概率毁灭者) controller: the four icon containers of GT++'s Quantum Force Transformer, i.e.
     * exactly what {@code MTEQuantumForceTransformer#getTexture} passes to
     * {@link Textures.BlockIcons#createTextureWithCasing}. The structure of that level is the QFT shell, so its
     * controller looks like the QFT controller.
     * <p>
     * These are fields and not inline reads on purpose: a {@code TexturesGtBlock.CustomIcon} adds itself to
     * {@code GregTechAPI.sGTBlockIconload} in its constructor, and GT only walks that list while it loads the block
     * textures. Read lazily (first frame) the icons would never be registered and the face would render empty.
     */
    private static final IIconContainer QFT_FACE = TexturesGtBlock.oMCAQFT;
    private static final IIconContainer QFT_FACE_GLOW = TexturesGtBlock.oMCAQFTGlow;
    private static final IIconContainer QFT_FACE_ACTIVE = TexturesGtBlock.oMCAQFTActive;
    private static final IIconContainer QFT_FACE_ACTIVE_GLOW = TexturesGtBlock.oMCAQFTActiveGlow;

    /**
     * Casing of the level 2 controller, i.e. what element {@code F} of that structure is built from: the
     * BulkProductionFrame (GT++ {@code blockCasings2Misc:12}) of the QFT shell. The frame has no texture id in the GT
     * casing index (its own id would be {@code TAE.getIndexFromPage(1, 12)}, and that slot is not registered - the
     * block skips meta 12), so the block itself is copied, the same way {@code TextureFactory.of} does it for the
     * casing of any casing-index-less block.
     */
    private static final ITexture TIER2_CASING = TextureFactory
        .of(Casings.BulkProductionFrame.getBlock(), Casings.BulkProductionFrame.getBlockMeta());
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";
    /** The level 2 (概率毁灭者) piece: a QFT shaped shell whose hatches sit on the SpaceTimeContinuumRipper. */
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
    Level 2 (概率毁灭者 / Probability Destroyer) uses its own letters, because StructureLib binds the element map to
    the whole structure definition, not per shape - level 1 already means different casings with A-E:

    F-> addElement('F', Casings.BulkProductionFrame.asElement()          (was 'A')
    G-> addElement('G', Casings.QuantumForceTransformerCoilCasing.asElement()   (was 'B')
    H-> addElement('H', Casings.SpaceTimeContinuumRipper ...)  + the hatches of that piece    (was 'C')
    I-> addElement('I', Casings.SpaceTimeBendingCore.asElement()         (was 'D')
    J-> addElement('J', Casings.ForceFieldGlass.asElement()              (was 'E')
    Offsets:
    8, 8, 0  (same controller position as level 1)
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
        // level 2 (概率毁灭者): same shape as written in structure_tier2, letters F-J, hatches on the
        // SpaceTimeContinuumRipper (element H). It has no heating coil ring: that mode runs at a fixed 12601 K.
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
                // The ripper is a GT++ casing that has no entry in the GT casing texture pages: its
                // Casings#textureId is -1 and HatchElementBuilder#casingIndex rejects anything <= 0 (passing it
                // crashed the game while this class was being initialised). GT++'s own Quantum Force Transformer
                // uses TAE.getIndexFromPage(0, 10) for the hatch host of this casing, so the hatches here get the
                // same texture as there.
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
     * Level of the heating coil ring of the structure (element {@code C}). The structure element writes it, once per
     * coil block it accepts; {@link HeatingCoilLevel#None} means the structure has no coil (or a rejected one).
     */
    private HeatingCoilLevel mCoilLevel = HeatingCoilLevel.None;

    /**
     * Heat the controller works with: the heat of the coil ring plus the heat bonus of the energy tier, the same
     * formula the EBF uses. It is recomputed by every structure check, see {@link #checkMachine}.
     */
    private int mHeatingCapacity;

    /** @return the level of the coil ring, {@link HeatingCoilLevel#None} when no coil was found. */
    public HeatingCoilLevel getCoilLevel() {
        return mCoilLevel;
    }

    /**
     * Called by the structure element {@code C}, once per coil block of the ring (see
     * {@code GTStructureUtility.ofCoil}); a coil of a different level than the first one fails the structure check.
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
     * @return the heat the recipes of this machine are checked against, i.e. {@link #getCoilHeat()} plus the tier
     *         bonus. Zero while the structure is not formed, see {@link #checkMachine}.
     */
    public int getHeatingCapacity() {
        return mHeatingCapacity;
    }
    // endregion

    // region Structure level
    /**
     * Level of the structure that is currently formed, zero while the machine is not formed. Levels 3..4 (bigger
     * structures) will be added later, see {@link #checkMachine}.
     * <p>
     * This is an independent gate from the coil ring: the coil ring only decides how much heat is available, the
     * structure level decides which recipes are allowed at all. Recipes ask for a level with
     * {@link MTRecipeMaps#CHEMICAL_TWISTER_STRUCTURE_LEVEL}; the processing logic refuses a recipe whose level is
     * higher than this one with {@code insufficientMachineTier}.
     * <p>
     * The same level also decides what the controller looks like, see {@link #getTexture}, which is why it travels to
     * the client with {@link #getUpdateData}.
     */
    private int mStructureLevel;

    /** @return the level of the formed structure, zero when the machine is not formed. */
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
        // The coil level, the heat and the structure level are properties of the structure, not a state of the
        // machine: forget all three first, so a check that fails half way cannot leave the values of the previous
        // structure behind.
        this.mHeatingCapacity = 0;
        this.mStructureLevel = 0;
        setCoilLevel(HeatingCoilLevel.None);

        // Level 2 (概率毁灭者) is checked first and speculatively (null instead of the error list, see
        // MTEPCBFactory#checkForNewTier), so a level 1 build keeps reporting only its own structure errors.
        // That piece has no heating coil ring - the mode runs at a fixed 12601 K (Hypogen), see
        // MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT - so no coil is required and none is read.
        // The level it sets here is also what the controller renders with, see getTexture.
        if (checkPiece(STRUCTURE_PIECE_TIER2, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, null)) {
            this.mStructureLevel = 2;
            this.mHeatingCapacity = MTRecipeMaps.QFT_PROBABILITY_DESTROYER_HEAT;
            checkHasAnyInput(errors);
            checkHasAnyOutput(errors);
            checkHasAnyEnergy(errors);
            return;
        }

        // Level 1: the coil ring of element 'C' plus the energy tier bonus, the EBF formula (levels 3..4 would be
        // checked the same way, biggest piece first, before the level 1 fallback below).
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        this.mStructureLevel = 1;

        if (getCoilLevel() == HeatingCoilLevel.None) {
            errors.add(StructureErrorRegistry.COIL_LEVEL_NOT_ENOUGH);
        }

        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);

        // A structure without an energy hatch computes a lower value - the missing hatch is a structure error, so the
        // machine cannot run with it anyway.
        this.mHeatingCapacity = getCoilHeat() + 100 * (GTUtility.getTier(getMaxInputVoltage()) - 2);
    }

    /**
     * The heat is not only used by the recipe check below, it is also published to everything that reads device
     * information (scanner, sensor card, metrics), the same way the EBF publishes its own heat.
     */
    @Override
    public void getExtraInfoData(List<String> info) {
        super.getExtraInfoData(info);
        info.add(IGregTechDeviceInformation.encode("GT5U.EBF.heat.s", NumberFormatUtil.formatNumber(mHeatingCapacity)));
    }

    /**
     * Two independent gates, checked in this order:
     * <ol>
     * <li><b>structure level</b>: the recipe must not need a bigger structure than the one that is formed. A recipe
     * without {@link MTRecipeMaps#CHEMICAL_TWISTER_STRUCTURE_LEVEL} needs the base structure,
     * {@link MTRecipeMaps#DEFAULT_CHEMICAL_TWISTER_STRUCTURE_LEVEL}. Too low is reported with
     * {@code CheckRecipeResultRegistry.insufficientMachineTier}.</li>
     * <li><b>heat</b>: the heat of the coil ring (plus the tier bonus) must be at least the heat the recipe asks for,
     * otherwise the recipe is refused as {@code insufficientHeat}.</li>
     * </ol>
     * The overclocking of a recipe that asks for heat follows the EBF: the difference between the required and the
     * available heat is the heat discount and the heat overclocks. A recipe without a heat requirement (its
     * {@code mSpecialValue} is zero, e.g. the assembler recipes of the template) keeps the standard overclocking of
     * {@link MTMultiMachineBase}.
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
     * Level of the first of the bigger structures (概率毁灭者), and at the same time the trigger stack size at which a
     * build hint / NEI preview switches from level 1 to level 2 - GT's tier convention, see
     * {@link #getStructurePieceForBuild}. From this level on the controller renders the QFT face, see
     * {@link #getTexture}.
     */
    public static final int STRUCTURE_LEVEL_TIER2 = 2;

    @Override
    public int totalMachineMode() {
        return 2;
    }

    @Override
    public String getMachineModeName(int mode) {
        return mode == MODE_QFT_PROBABILITY_DESTROYER
            ? StatCollector.translateToLocal("machine.largechemicaltwister.mode.probability_destroyer")
            : StatCollector.translateToLocal("machine.largechemicaltwister.mode.chemical_twister");
    }

    /**
     * The screwdriver switches between the two modes; the base class also offers the same switch as a GUI button
     * ({@code totalMachineMode() > 1} is what enables both). While the machine runs the switch is refused, so the
     * recipe that is being processed cannot change under it.
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
        setMachineMode(nextMachineMode());
        if (aPlayer != null) {
            aPlayer.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.AQUA + "Mode: " + getMachineModeName(machineMode) + EnumChatFormatting.RESET));
        }
    }

    /**
     * Mode 1 asks for a level 2 structure through the recipe metadata, so a wrong structure is reported as
     * {@code insufficientMachineTier} by the processing logic instead of silently running the wrong recipes.
     */
    @Override
    public RecipeMap<?> getRecipeMap() {
        if (machineMode == MODE_QFT_PROBABILITY_DESTROYER) return MTRecipeMaps.qftProbabilityDestroyerRecipes;
        return MTRecipeMaps.MTChemicalTwisterRecipes;
    }

    /** Both pools are listed so NEI shows this machine as the catalyst for either of them. */
    @Override
    public @Nonnull Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.asList(MTRecipeMaps.MTChemicalTwisterRecipes, MTRecipeMaps.qftProbabilityDestroyerRecipes);
    }
    // endregion

    /**
     * Which piece a build hint / survival build / NEI preview shows.
     * <p>
     * The trigger's stack size is GT's usual "structure tier" switch (the PCB Factory picks its tier 1/2/3 the same
     * way): {@code 1} = level 1, {@code >= 2} = level 2. That is what makes BlockRenderer6343 offer the Tier slider on
     * this machine's NEI preview - it discovers the tiers by calling {@code construct()} with {@code stackSize =
     * 1, 2, 3, ...} until nothing changes any more, and then offers a slider up to the highest tier that did. Selecting
     * the tier explicitly always wins; without a tier (or with tier 1) the machine's current mode decides, so a player
     * in 概率毁灭者 mode gets the level 2 hints in game while the NEI preview, which always previews a fresh machine in
     * mode 0, still starts at level 1.
     */
    private String getStructurePieceForBuild(@Nullable ItemStack stackSize) {
        if (stackSize != null && stackSize.stackSize >= STRUCTURE_LEVEL_TIER2) return STRUCTURE_PIECE_TIER2;
        if (machineMode == MODE_QFT_PROBABILITY_DESTROYER) return STRUCTURE_PIECE_TIER2;
        return STRUCTURE_PIECE_MAIN;
    }

    /**
     * Makes the controller render the structure a build hint / survival build is about to place, see
     * {@link #getStructurePieceForBuild}.
     * <p>
     * A formed machine is left alone - its level is the one {@link #checkMachine} found and is synced to the client on
     * its own. An unformed one is the interesting case: BlockRenderer6343 previews this machine on a dummy multiblock
     * in a dummy world that GT never ticks, so the trigger of the build is the only place the preview can learn which
     * structure it is showing.
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
    public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor) {
        return super.survivalConstruct(stackSize, elementBudget, source, actor);
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
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.modes.catalyst"))
            .addSeparator()
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.header"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.formula"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.oc"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.poc"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.heat.parallel"))
            .addSeparator()
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.details"))
            .addInfo(translateToLocal("machine.chemicaltwister.tooltip.details.hint"))
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .addStructureInfo(translateToLocal("machine.chemicaltwister.structureinfo.1"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        // Which face the controller shows follows the structure that is formed, not the mode: level 1 is the base
        // structure and its controller looks like a Large Chemical Reactor controller (idle or running, each with its
        // glow layer) on a Chemically Inert Machine Casing, level 2 is the 概率毁灭者 (QFT shell) with the QFT face on
        // the BulkProductionFrame. GT composes both for us, see getCasingTexture and STRUCTURE_LEVEL_TIER2.
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
     * The casing of the controller, i.e. what element {@code D} (level 1) or {@code F} (level 2) of the structure is
     * built from; see {@link #getTexture}. The level is the one on the client side too, see {@link #getUpdateData}.
     */
    @Override
    public ITexture getCasingTexture() {
        if (mStructureLevel >= STRUCTURE_LEVEL_TIER2) return TIER2_CASING;
        return Casings.ChemicallyInertMachineCasing.getCasingTexture();
    }

    /**
     * The structure level decides how the controller looks (see {@link #getTexture}), so it has to be on the client as
     * well - and only a structure check can know it, {@code MTEMultiBlockBase#checkStructure} does nothing on the
     * client. This byte is what GT syncs for that: the value is sent whenever it changes and is part of the tile data
     * packet, so a freshly loaded chunk renders correctly right away.
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
