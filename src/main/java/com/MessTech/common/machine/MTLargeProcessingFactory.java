package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.machine.Base.MTModuleHatchElement;
import com.MessTech.common.machine.Base.MTModuleMultiMachineBase;
import com.MessTech.common.machine.Base.MTProcessingLogic;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.metadata.CompressionTierKey;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.block.ModBlocks;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSolidifier;

/**
 * Large Processing Factory: the nine-in-one GT++ machine. Three machine modes (metal / fluid / misc) pick a block of
 * three recipe pools through the circuit (20, 21, 22); the base bonuses are the original ones - 2x the energy hatch
 * tier as parallel, 250% faster and 80% of the EU - and the module hatches multiply their speed/EU bonus on top and
 * <b>add</b> their parallel ({@link #isModuleParallelAdditive()}).
 */
public class MTLargeProcessingFactory extends MTModuleMultiMachineBase<MTLargeProcessingFactory>
    implements ISurvivalConstructable {

    // region Modes / recipe pools
    private static final int MODE_METAL = 0;
    private static final int MODE_FLUID = 1;
    private static final int MODE_MISC = 2;

    private static final int[][] POOL_MAP = { { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 } };

    private static final RecipeMap<?>[] POOLS = { RecipeMaps.compressorRecipes, RecipeMaps.latheRecipes,
        RecipeMaps.polarizerRecipes, RecipeMaps.fermentingRecipes, RecipeMaps.fluidExtractionRecipes,
        RecipeMaps.extractorRecipes, RecipeMaps.laserEngraverRecipes, RecipeMaps.autoclaveRecipes,
        RecipeMaps.fluidSolidifierRecipes };

    private static final int POLLUTION_METAL = 400;
    private static final int POLLUTION_FLUID = 400;
    private static final int POLLUTION_MISC = 600;
    // endregion

    // region Structure
    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFFSET = 1;
    private static final int VERTICAL_OFFSET = 1;
    private static final int DEPTH_OFFSET = 0;
    /** Casing texture of the GT++ Multi-Use Casing. */
    private static final int CASING_INDEX = Casings.MultiUseCasing.getTextureId();

    private static final String[][] STRUCTURE_STRING = { { "CCC", "CCC", "CCC" }, { "C~C", "C-C", "CCC" },
        { "CCC", "CCC", "CCC" } };

    private static IStructureDefinition<MTLargeProcessingFactory> STRUCTURE_DEFINITION;

    @SideOnly(Side.CLIENT)
    private static IIconContainer FACE;
    @SideOnly(Side.CLIENT)
    private static IIconContainer FACE_ACTIVE;
    // endregion

    private int mCasing;

    public MTLargeProcessingFactory(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTLargeProcessingFactory(String aName) {
        super(aName);
    }

    // region Base bonuses and module integration
    /** @return Original LPF parallel: 2x the tier of the highest energy hatch. */
    @Override
    protected int getBaseMaxParallelRecipes() {
        return Math.max(1, 2 * GTUtility.getTier(getMaxInputVoltage()));
    }

    @Override
    protected float getBaseSpeedBonus() {
        return 1F / 3.5F;
    }

    @Override
    protected float getBaseEuModifier() {
        return 0.8F;
    }

    /** A parallel control module adds its parallel to the original 2xtier instead of replacing it. */
    @Override
    protected boolean isModuleParallelAdditive() {
        return true;
    }

    /** The original LPF always ran with input separation (one recipe per input bus); keep that as the default. */
    @Override
    public boolean getDefaultInputSeparationMode() {
        return true;
    }
    // endregion

    // region Structure definition
    @Override
    public IStructureDefinition<MTLargeProcessingFactory> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTLargeProcessingFactory>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(STRUCTURE_STRING))
                .addElement(
                    'C',
                    buildHatchAdder(MTLargeProcessingFactory.class)
                        .atLeast(
                            HatchElement.InputBus,
                            HatchElement.OutputBus,
                            HatchElement.InputHatch,
                            HatchElement.OutputHatch,
                            HatchElement.Energy,
                            HatchElement.Muffler,
                            MTModuleHatchElement.Module)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(onElementPass(x -> ++x.mCasing, ofBlock(ModBlocks.blockCasings3Misc, 2))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET);
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
    protected void checkMachineStructure(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        List<StructureError> errors) {
        mCasing = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        if (mCasing < 6) {
            errors.add(StructureErrors.missingCasings(mCasing, 6));
        }
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);
    }
    // endregion

    // region Recipe pools
    private static ItemStack findCircuit(ItemStack[] inputs) {
        if (inputs == null) return null;
        for (ItemStack stack : inputs) {
            if (stack == null) continue;
            if (stack.getItem() == GTUtility.getIntegratedCircuit(0)
                .getItem() && stack.getItemDamage() >= 20 && stack.getItemDamage() <= 22) {
                return stack;
            }
        }
        return null;
    }

    private int poolFor(ItemStack circuit) {
        int column = circuit.getItemDamage() - 20;
        if (column < 0 || column > 2) return -1;
        return POOL_MAP[machineMode][column];
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return null;
    }

    @Nonnull
    @Override
    public Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.asList(POOLS);
    }

    @Override
    public int getRecipeCatalystPriority() {
        return -10;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new MTProcessingLogic() {

            private ItemStack lastCircuit;
            private int lastMode = -1;

            @Nonnull
            @Override
            public CheckRecipeResult process() {
                // Same dynamic hooks as MTMultiMachineBase#createProcessingLogic (module values fold in there).
                setEuModifier(getEuModifier());
                setSpeedBonus(getSpeedBonus());
                setOverclock(isEnablePerfectOverclock() ? 4 : 2, 4);
                return super.process();
            }

            @Nonnull
            @Override
            protected Stream<GTRecipe> findRecipeMatches(@Nullable RecipeMap<?> map) {
                ItemStack circuit = findCircuit(inputItems);
                if (circuit == null) return Stream.empty();
                if (!GTUtility.areStacksEqual(circuit, lastCircuit)) {
                    lastRecipe = null;
                    lastCircuit = circuit;
                }
                if (machineMode != lastMode) {
                    lastRecipe = null;
                    lastMode = machineMode;
                }
                int pool = poolFor(circuit);
                if (pool < 0) return Stream.empty();
                return super.findRecipeMatches(POOLS[pool]);
            }

            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                if (recipe.getMetadataOrDefault(CompressionTierKey.INSTANCE, 0) > 0) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
                return super.validateRecipe(recipe);
            }
        }.setMaxParallelSupplier(this::getLimitedMaxParallel);
    }

    /**
     * A GT++ solidifier hatch feeds the fluid solidifier pool of the misc mode with its mold and fluid. The hatch's
     * fluid is kept out of the normal search by {@link #getStoredFluidsForColor}.
     */
    @Nonnull
    @Override
    protected CheckRecipeResult checkRecipeForCustomHatches(@Nonnull CheckRecipeResult lastResult) {
        if (machineMode != MODE_MISC) return lastResult;
        for (MTEHatchInput hatch : mInputHatches) {
            if (!(hatch instanceof MTEHatchSolidifier solidifier)) continue;
            ItemStack mold = solidifier.getStackInSlot(MTEHatchSolidifier.moldSlot);
            FluidStack fluid = hatch.getFluid();
            if (mold == null || fluid == null) continue;

            List<ItemStack> inputs = new ArrayList<>(2);
            inputs.add(mold);
            inputs.add(GTUtility.getIntegratedCircuit(22));
            processingLogic.setInputItems(inputs);
            processingLogic.setInputFluids(fluid);

            CheckRecipeResult result = processingLogic.process();
            if (result.wasSuccessful()) return result;
            if (result != CheckRecipeResultRegistry.NO_RECIPE) lastResult = result;
        }
        processingLogic.clear();
        return lastResult;
    }

    /** The solidifier hatch's fluid belongs to its own recipes, not to the pools of the machine. */
    @Override
    public ArrayList<FluidStack> getStoredFluidsForColor(java.util.Optional<Byte> color) {
        ArrayList<FluidStack> fluids = super.getStoredFluidsForColor(color);
        for (MTEHatchInput hatch : mInputHatches) {
            if (!(hatch instanceof MTEHatchSolidifier)) continue;
            FluidStack stored = hatch.getFillableStack();
            if (stored == null) continue;
            // Remove one occurrence per solidifier hatch: its own stored fluid, not an equal one from another hatch.
            for (int i = 0; i < fluids.size(); i++) {
                if (fluids.get(i)
                    .isFluidEqual(stored)) {
                    fluids.remove(i);
                    break;
                }
            }
        }
        return fluids;
    }

    @Override
    public int getPollutionPerSecond(ItemStack aStack) {
        return switch (machineMode) {
            case MODE_METAL -> POLLUTION_METAL;
            case MODE_FLUID -> POLLUTION_FLUID;
            default -> POLLUTION_MISC;
        };
    }
    // endregion

    // region Machine modes
    @Override
    public int totalMachineMode() {
        return 3;
    }

    @Override
    public String getMachineModeName(int mode) {
        return translateToLocal("machine.lpf.mode." + mode);
    }

    @Override
    public void setMachineModeIcons() {
        machineModeIcons.clear();
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_LPF_METAL);
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_LPF_FLUID);
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT);
    }
    // endregion

    // region Rendering / registration
    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTLargeProcessingFactory(mName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        FACE = gregtech.api.enums.Textures.BlockIcons
            .custom("messtech", "iconsets/controllerFaces/industrialMultiMachine");
        FACE_ACTIVE = gregtech.api.enums.Textures.BlockIcons
            .custom("messtech", "iconsets/controllerFaces/industrialMultiMachineActive");
        super.registerIcons(register);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture casing = Casings.MultiUseCasing.getCasingTexture();
        if (side == facing) {
            return new ITexture[] { casing, TextureFactory.of(aActive ? FACE_ACTIVE : FACE) };
        }
        return new ITexture[] { casing };
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.lpf.machinetype"))
            .addInfo(translateToLocal("machine.lpf.tooltip.desc"))
            .addInfo(translateToLocal("machine.lpf.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.lpf.tooltip.bonus.header"))
            .addInfo(translateToLocal("machine.lpf.tooltip.bonus.speed"))
            .addInfo(translateToLocal("machine.lpf.tooltip.bonus.eu"))
            .addInfo(translateToLocal("machine.lpf.tooltip.bonus.parallel"))
            .addInfo(translateToLocal("machine.lpf.tooltip.bonus.module"))
            .addSeparator()
            .addInfo(translateToLocal("machine.lpf.tooltip.modes.header"))
            .addInfo(translateToLocal("machine.lpf.tooltip.modes.metal"))
            .addInfo(translateToLocal("machine.lpf.tooltip.modes.fluid"))
            .addInfo(translateToLocal("machine.lpf.tooltip.modes.misc"))
            .addInfo(translateToLocal("machine.lpf.tooltip.modes.circuit"))
            .addSeparator()
            .addInfo(translateToLocal("machine.lpf.tooltip.structure.header"))
            .addInfo(translateToLocal("machine.lpf.tooltip.structure.casing"))
            .addInfo(translateToLocal("machine.lpf.tooltip.structure.hatches"))
            .addSeparator()
            .addInfo(translateToLocal("machine.lpf.tooltip.details"))
            .addInfo(translateToLocal("machine.lpf.tooltip.details.hint"))
            .addPollutionAmount(getPollutionPerSecond(null))
            .beginStructureBlock(3, 3, 3, true)
            .addController(translateToLocal("machine.lpf.tooltip.controller"))
            .addCasingInfoMin(translateToLocal("machine.lpf.tooltip.casing_name"), 6, false)
            .addInputBus(translateToLocal("machine.lpf.tooltip.hatch_info"), 1)
            .addOutputBus(translateToLocal("machine.lpf.tooltip.hatch_info"), 1)
            .addInputHatch(translateToLocal("machine.lpf.tooltip.hatch_info"), 1)
            .addOutputHatch(translateToLocal("machine.lpf.tooltip.hatch_info"), 1)
            .addEnergyHatch(translateToLocal("machine.lpf.tooltip.hatch_info"), 1)
            .addMufflerHatch(translateToLocal("machine.lpf.tooltip.hatch_info"), 1)
            .addOtherStructurePart(
                translateToLocal("machine.lpf.tooltip.module_hatch"),
                translateToLocal("machine.lpf.tooltip.hatch_info"),
                1)
            .toolTipFinisher();
        return tt;
    }
    // endregion
}
