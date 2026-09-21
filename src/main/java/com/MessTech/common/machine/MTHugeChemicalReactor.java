package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.machine.Base.MTModuleHatchElement;
import com.MessTech.common.machine.Base.MTModuleMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;

/**
 * Huge Chemical Reactor: the modular machine that runs the recipe pool of the Large Chemical Reactor.
 * <p>
 * The machine folds the values of its {@link MTModuleMultiMachineBase modules} into the three knobs every MessTech
 * multiblock has, and it has no bonus of its own: {@code getBaseSpeedBonus()}, {@code getBaseEuModifier()} and
 * {@code getBaseMaxParallelRecipes()} all stay at 1, so the recipe duration, the EU/t and the parallel of the machine
 * are whatever its modules supply ({@code MTModuleMultiMachineBase.getSpeedBonus()},
 * {@code getEuModifier()} and {@code getMaxParallelRecipes()}).
 * <p>
 * Recipes: {@link RecipeMaps#chemicalReactorRecipes}, the pool of the Large Chemical Reactor, with
 * {@link #isEnablePerfectOverclock()} enabled - every overclock is EU/t x4 and duration /4, which leaves the total
 * energy of a craft untouched.
 * <p>
 * The coil band of the shell (element {@code A}) takes a heating coil of any tier. Its level is read from the
 * structure ({@link #getCoilLevel()}) and published as the {@link GTStructureChannels#HEATING_COIL} sub channel, so
 * the hologram preview and BlockRenderer6343 offer the coil slider.
 */
public class MTHugeChemicalReactor extends MTModuleMultiMachineBase<MTHugeChemicalReactor>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final int HORIZONTAL_OFFSET = 2;
    private static final int VERTICAL_OFFSET = 2;
    private static final int DEPTH_OFFSET = 0;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"BBBBB","BBBBB","BBBBB","BBBBB","BBBBB"},
        {"BBBBB","B   B","B C B","B   B","BAAAB"},
        {"BB~BB","B C B","BCCCB","B C B","BABAB"},
        {"BBBBB","B   B","B C B","B   B","BAAAB"},
        {"BBBBB","BBBBB","BBBBB","BBBBB","BBBBB"},

    };
    // spotless:on

    private static final IStructureDefinition<MTHugeChemicalReactor> STRUCTURE_DEFINITION = StructureDefinition
        .<MTHugeChemicalReactor>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        // A: a heating coil of any tier; the coil element writes the level it finds, see getCoilLevel().
        .addElement(
            'A',
            GTStructureChannels.HEATING_COIL
                .use(activeCoils(ofCoil(MTHugeChemicalReactor::setCoilLevel, MTHugeChemicalReactor::getCoilLevel))))
        .addElement(
            'B',
            HatchElementBuilder.<MTHugeChemicalReactor>builder()
                .atLeast(
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputHatch,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy),
                    MTModuleHatchElement.Module)
                .casingIndex(Casings.ChemicallyInertMachineCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.ChemicallyInertMachineCasing.getBlock(),
                        Casings.ChemicallyInertMachineCasing.getBlockMeta())))
        .addElement('C', Casings.PTFEPipeCasing.asElement())
        .build();
    // endregion

    public MTHugeChemicalReactor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTHugeChemicalReactor(String aName) {
        super(aName);
    }

    // region Coil

    /**
     * Level of the heating coil band of the structure (element {@code A}). The structure element writes it, once per
     * coil block it accepts; {@link HeatingCoilLevel#None} means the structure has no coil, and a band of mixed levels
     * is refused by the coil element itself.
     */
    private HeatingCoilLevel mCoilLevel = HeatingCoilLevel.None;

    /** @return The level of the coil band, {@link HeatingCoilLevel#None} while no coil was found. */
    public HeatingCoilLevel getCoilLevel() {
        return mCoilLevel;
    }

    /**
     * Called by the structure element {@code A}, once per coil block of the band, see
     * {@code GTStructureUtility#ofCoil}.
     */
    public void setCoilLevel(HeatingCoilLevel aCoilLevel) {
        mCoilLevel = aCoilLevel;
    }

    // endregion

    @Override
    public IStructureDefinition<MTHugeChemicalReactor> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    /**
     * The module list of the base class is rebuilt by every structure check, so the coil level - the only other
     * property that comes from the structure - is forgotten here as well: a check that fails half way cannot leave
     * the coil of the previous structure behind.
     */
    @Override
    protected void checkMachineStructure(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        List<StructureError> errors) {
        mCoilLevel = HeatingCoilLevel.None;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);
    }

    /**
     * @return The pool of the Large Chemical Reactor: this machine runs chemical reactor recipes.
     */
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.multiblockChemicalReactorRecipes;
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
        return new MTHugeChemicalReactor(mName);
    }

    /**
     * The tooltip follows the house style of {@code docs/knowledge.md}: one {@code addInfo(translate(key))} per line,
     * so the layout and every colour code live in the language files, and the coil is offered as a structure sub
     * channel so the hologram preview has the coil slider.
     */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.hugechemicalreactor.machinetype"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.desc"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.recipe.header"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.recipe.pool"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.recipe.overclock"))
            .addSeparator()
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.modules.header"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.modules.types"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.modules.values"))
            .addSeparator()
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.structure.header"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.structure.shell"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.structure.coil"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.structure.pipe"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.structure.hatches"))
            .addSeparator()
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.details"))
            .addInfo(translateToLocal("machine.hugechemicalreactor.tooltip.details.hint"))
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .toolTipFinisher();
        return tt;
    }

    /**
     * The shell of the structure is chemically inert machine casing, so the controller wears the face of the machine
     * whose recipes it runs - the Large Chemical Reactor - on that casing, idle and running, each with its glow
     * layer; GT composes it from {@link #getCasingTexture()}.
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
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
     * @return The casing of the controller, i.e. what element {@code B} of the structure is built from.
     */
    @Override
    public ITexture getCasingTexture() {
        return Casings.ChemicallyInertMachineCasing.getCasingTexture();
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return true;
    }
}
