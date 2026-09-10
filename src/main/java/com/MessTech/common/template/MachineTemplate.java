package com.MessTech.common.template;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * MessTech machine template class.
 * <p>
 * A complete, copy-paste ready reference implementation of a
 * {@link MTMultiMachineBase} multi-block. Every hatch type of the base class is accepted by the
 * single structure element 'A':
 * <ul>
 * <li>InputBus / OutputBus</li>
 * <li>InputHatch / OutputHatch</li>
 * <li>Energy / ExoticEnergy (either one at any 'A' position)</li>
 * <li>Dynamo</li>
 * <li>Maintenance</li>
 * <li>Muffler</li>
 * </ul>
 * The controller '~' sits in the middle of the front face of a 3x3x3 cube; all other positions
 * are 'A' blocks (hatch or Assembler Machine Casing fill).
 */
public class MachineTemplate extends MTMultiMachineBase<MachineTemplate> implements ISurvivalConstructable {

    // region Structure piece offsets
    // '~' (controller) is at char index 1 (X), outer array index 1 (Y), inner string index 2 (Z).
    private static final int HORIZONTAL_OFFSET = 1;
    private static final int VERTICAL_OFFSET = 1;
    private static final int DEPTH_OFFSET = 2;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        // Y = 2 (top layer)
        {"AAA", "AAA", "AAA"},
        // Y = 1 (controller layer): '~' on the front face (Z = 2), hollow interior (Z = 1)
        {"AAA", "A A", "A~A"},
        // Y = 0 (bottom layer)
        {"AAA", "AAA", "AAA"},
    };
    // spotless:on

    private static final IStructureDefinition<MachineTemplate> STRUCTURE_DEFINITION = StructureDefinition
        .<MachineTemplate>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        // A: any hatch of the base class, or the Assembler Machine Casing as the fill block.
        .addElement(
            'A',
            HatchElementBuilder.<MachineTemplate>builder()
                .atLeast(
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputHatch,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy),
                    HatchElement.Dynamo,
                    HatchElement.Maintenance,
                    HatchElement.Muffler)
                .casingIndex(Casings.AssemblerMachineCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(Casings.AssemblerMachineCasing.getBlock(), Casings.AssemblerMachineCasing.getBlockMeta())))
        .build();
    // endregion

    public MachineTemplate(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MachineTemplate(String aName) {
        super(aName);
    }

    @Override
    public IStructureDefinition<MachineTemplate> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // Template machines use the Assembler recipe pool.
        return RecipeMaps.assemblerRecipes;
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
        return new MachineTemplate(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("machine.template.machinetype"))
            .addSeparator()
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.template.tooltip.0"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.template.tooltip.1"))
            .addStructureInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("machine.template.tooltip.2"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            // Front face: Assembly Line Casing.
            return new ITexture[] {
                TextureFactory.of(Casings.AssemblyLineCasing.getBlock(), Casings.AssemblyLineCasing.getBlockMeta()) };
        }
        // Other faces: Assembler Machine Casing (same as the fill block of element 'A').
        return new ITexture[] { TextureFactory
            .of(Casings.AssemblerMachineCasing.getBlock(), Casings.AssemblerMachineCasing.getBlockMeta()) };
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }
}
