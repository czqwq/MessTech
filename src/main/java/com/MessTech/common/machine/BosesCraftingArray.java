package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.MessTech.common.machine.Base.MTProcessingLogic;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.NaniteTier;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchNanite;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;

public class BosesCraftingArray extends MTMultiMachineBase<BosesCraftingArray> implements ISurvivalConstructable {

    private static final int HORIZONTAL_OFFSET = 8;
    private static final int VERTICAL_OFFSET = 6;
    private static final int DEPTH_OFFSET = 0;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     ","      DDDDD     "},
        {"     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    ","     DBBEBBD    "},
        {"    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   ","    DB  E  BD   "},
        {"   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  ","   DB AAEAA BD  "},
        {"  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD ","  DB AADDDAA BD "},
        {" DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"},
        {" DEEEEDF~FDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"," DEEEEDFEFDEEEED"},
        {" DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"," DB AADEFEDAA BD"},
        {"  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD ","  DB AADCDAA BD "},
        {"   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  ","   DB AADAA BD  "},
        {"    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   ","    DB AEA BD   "},
        {"     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    ","     DB E BD    "},
        {"      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     ","      DBEBD     "},
        {"       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      ","       DDD      "},

    };
    // spotless:on

    private static final IStructureDefinition<BosesCraftingArray> STRUCTURE_DEFINITION = StructureDefinition
        .<BosesCraftingArray>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        // A: any hatch of the base class, or the Assembler Machine Casing as the fill block.
        .addElement(
            'A',
            HatchElementBuilder.<BosesCraftingArray>builder()
                .atLeast(
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy),
                    NaniteHatchElement.INSTANCE)
                .casingIndex(Casings.CoherencePreservingPlasmaConduit.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.CoherencePreservingPlasmaConduit.getBlock(),
                        Casings.CoherencePreservingPlasmaConduit.getBlockMeta())))
        .addElement('B', Casings.ElectromagneticallyIsolatedCasing.asElement())
        .addElement('C', Casings.FineStructureConstantManipulator.asElement())
        .addElement('D', Casings.PeaceEnforcementCasing.asElement())
        .addElement('E', Casings.CondensateTransformativeCoil.asElement())
        .addElement('F', Casings.CondensateGuidanceCoil.asElement())
        .build();
    // endregion

    private static final CheckRecipeResult NANITE_STALLED = SimpleCheckRecipeResult.ofFailure("nanite_stalled");

    private final List<MTEHatchNanite> naniteHatches = new ArrayList<>();

    private NaniteTier[] requiredNanites;
    private List<NaniteStep> naniteSteps;
    private long naniteSubticks;
    private boolean naniteStalled;

    private static final class NaniteStep {

        private final NaniteTier tier;
        private final int index;
        private final int start;
        private final int end;

        private NaniteStep(NaniteTier tier, int index, int start, int end) {
            this.tier = tier;
            this.index = index;
            this.start = start;
            this.end = end;
        }
    }

    public enum NaniteHatchElement implements IHatchElement<BosesCraftingArray> {

        INSTANCE;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return List.of(MTEHatchNanite.class);
        }

        @Override
        public IGTHatchAdder<? super BosesCraftingArray> adder() {
            return NaniteHatchElement::adder;
        }

        private static boolean adder(BosesCraftingArray machine, IGregTechTileEntity baseTile, Short texture) {
            IMetaTileEntity metaTileEntity = baseTile.getMetaTileEntity();
            if (!(metaTileEntity instanceof MTEHatchNanite hatch)) return false;
            if (!ItemList.Hatch_Nanite_Singularity.isStackEqual(hatch.getStackForm(1), true, true)) return false;

            hatch.updateTexture(texture);
            hatch.updateCraftingIcon(machine.getMachineCraftingIcon());
            return machine.naniteHatches.add(hatch);
        }

        @Override
        public String getDisplayName() {
            return ItemList.Hatch_Nanite_Singularity.getDisplayName();
        }

        @Override
        public long count(BosesCraftingArray machine) {
            return machine.naniteHatches.size();
        }
    }

    public BosesCraftingArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public BosesCraftingArray(String aName) {
        super(aName);
    }

    @Override
    public IStructureDefinition<BosesCraftingArray> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        naniteHatches.clear();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        if (naniteHatches.isEmpty()) {
            errors.add(StructureErrors.of("machine.bosescraftingarray.need_nanite_bus"));
        }
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkHasAnyEnergy(errors);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return MTRecipeMaps.bosesCraftingArrayRecipes;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new MTProcessingLogic() {

            @NotNull
            @Override
            public CheckRecipeResult process() {
                setEuModifier(getEuModifier());
                setSpeedBonus(getSpeedBonus());
                setOverclock(isEnablePerfectOverclock() ? 4 : 2, 4);
                return super.process();
            }

            @NotNull
            @Override
            protected CheckRecipeResult onRecipeStart(@NotNull GTRecipe recipe) {
                beginNaniteRecipe(recipe);
                return super.onRecipeStart(recipe);
            }
        }.setMaxParallelSupplier(this::getLimitedMaxParallel);
    }

    @Override
    protected void outputAfterRecipe() {
        super.outputAfterRecipe();
        clearNaniteRecipe();
    }

    @Override
    protected void incrementProgressTime() {
        if (naniteSteps == null || naniteSteps.isEmpty()) {
            setNaniteStalled(false);
            super.incrementProgressTime();
            return;
        }

        NaniteStep step = getCurrentNaniteStep();
        if (step == null) {
            setNaniteStalled(false);
            super.incrementProgressTime();
            return;
        }

        NaniteTier providedTier = getHighestNaniteTier();
        if (providedTier == null || providedTier.tier < step.tier.tier) {
            // Not enough nanite tier for the current step: stall and show the red warning.
            setNaniteStalled(true);
            return;
        }

        long availableNanites = getAvailableNanites();
        if (availableNanites <= 0) {
            setNaniteStalled(true);
            return;
        }

        // The stall condition is resolved once both tier and count are available; slow accumulation
        // below should not keep the red warning on.
        setNaniteStalled(false);

        // Original BEC divisor formula, with no parallel or condensate slowdown on this standalone machine.
        int aboveTierDivisor = 1 << Math.abs(step.tier.tier - providedTier.tier);
        int divisor = Math.max(1, aboveTierDivisor);

        // Each nanite gives a fixed 0.5 speed bonus, so keep progress in half-tick units.
        naniteSubticks += availableNanites;
        long fullTicks = naniteSubticks / (2L * divisor);
        naniteSubticks -= fullTicks * 2L * divisor;
        if (fullTicks <= 0) return;

        long nextProgress = (long) mProgresstime + fullTicks;
        NaniteStep nextGate = getNextNaniteGate(providedTier);
        if (nextGate != null) {
            nextProgress = Math.min(nextProgress, nextGate.start);
        }
        nextProgress = Math.min(nextProgress, mMaxProgresstime);
        mProgresstime = (int) nextProgress;
    }

    private void setNaniteStalled(boolean stalled) {
        if (naniteStalled == stalled) return;
        naniteStalled = stalled;
        // Reuses GT's normal synced recipe-result widget, which is rendered above the progress line.
        checkRecipeResult = stalled ? NANITE_STALLED : CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private void beginNaniteRecipe(GTRecipe recipe) {
        naniteSubticks = 0;
        setNaniteStalled(false);
        requiredNanites = recipe.getMetadata(GTRecipeConstants.NANITE_TIERS);

        if (requiredNanites == null || requiredNanites.length == 0) {
            naniteSteps = null;
            return;
        }

        int duration = Math.max(1, recipe.mDuration);
        int count = requiredNanites.length;
        int base = duration / count;
        int remainder = duration % count;
        int current = 0;

        naniteSteps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int length = base + (i < remainder ? 1 : 0);
            naniteSteps.add(new NaniteStep(requiredNanites[i], i, current, current + length));
            current += length;
        }
    }

    private void clearNaniteRecipe() {
        requiredNanites = null;
        naniteSteps = null;
        naniteSubticks = 0;
        setNaniteStalled(false);
    }

    private NaniteStep getCurrentNaniteStep() {
        if (naniteSteps == null) return null;
        for (NaniteStep step : naniteSteps) {
            if (step.start <= mProgresstime && mProgresstime < step.end) return step;
        }
        return null;
    }

    private NaniteStep getNextNaniteGate(NaniteTier providedTier) {
        NaniteStep current = getCurrentNaniteStep();
        if (current == null) return null;

        for (int i = current.index + 1; i < naniteSteps.size(); i++) {
            NaniteStep next = naniteSteps.get(i);
            if (next.tier.tier > providedTier.tier) return next;
        }
        return null;
    }

    private NaniteTier getHighestNaniteTier() {
        NaniteTier highest = null;
        for (MTEHatchNanite hatch : naniteHatches) {
            if (hatch == null || !hatch.isValid()) continue;
            NaniteTier tier = NaniteTier.fromStack(hatch.getItemStack());
            if (tier != null && (highest == null || tier.tier > highest.tier)) {
                highest = tier;
            }
        }
        return highest;
    }

    private long getAvailableNanites() {
        long total = 0;
        for (MTEHatchNanite hatch : naniteHatches) {
            if (hatch == null || !hatch.isValid()) continue;
            total += hatch.getItemCount();
        }
        return total;
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
        return new BosesCraftingArray(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.bosescraftingarray.machinetype"))
            .addSeparator()
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + translateToLocal("machine.bosescraftingarray.tooltip.0"))
            .addInfo(EnumChatFormatting.GRAY + translateToLocal("machine.bosescraftingarray.tooltip.1"))
            .addInfo(EnumChatFormatting.GREEN + translateToLocal("machine.bosescraftingarray.tooltip.2"))
            .addInfo(EnumChatFormatting.YELLOW + translateToLocal("machine.bosescraftingarray.tooltip.3"))
            .addInfo(EnumChatFormatting.GOLD + translateToLocal("machine.bosescraftingarray.poc"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture casing = TextureFactory
            .of(Casings.CondensateTransformativeCoil.getBlock(), Casings.CondensateTransformativeCoil.getBlockMeta());

        if (side == facing) {
            if (aActive) {
                return new ITexture[] { casing, TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.BEC_CONTROLLER_BACKGROUND)
                    .extFacing()
                    .build(),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.BEC_ASSEMBLER_ACTIVE)
                        .extFacing()
                        .glow()
                        .build() };
            }
            return new ITexture[] { casing, TextureFactory.builder()
                .addIcon(Textures.BlockIcons.BEC_CONTROLLER_BACKGROUND)
                .extFacing()
                .build() };
        }

        return new ITexture[] { casing };
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
        return Integer.MAX_VALUE;
    }
}
