package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
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
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
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
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import tectech.thing.CustomItemList;

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

    /**
     * Whether an Astral Array Fabricator has been consumed from an input bus to unlock auto-adjust mode. Saved like
     * the Nano-Scale Foundry's array: in the world NBT and in the controller item NBT, so breaking and replacing the
     * machine keeps the unlock. A wire cutter gives the array back and turns auto-adjust off again.
     */
    private boolean autoAdjustUnlocked = false;

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
            // GT5U 5.09.54.183 uses one nanite bus; the legacy Singularity ItemList entry is unset.

            hatch.updateTexture(texture);
            hatch.updateCraftingIcon(machine.getMachineCraftingIcon());
            return machine.naniteHatches.add(hatch);
        }

        @Override
        public String getDisplayName() {
            return ItemList.Hatch_Nanite.getDisplayName();
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

    /**
     * The Astral Array is consumed like the Nano-Scale Foundry's: at the top of the recipe check, inside the
     * {@code startRecipeProcessing()} / {@code endRecipeProcessing()} wrapper GT puts around it, so ME buses commit
     * the extraction correctly.
     */
    @Override
    @NotNull
    public CheckRecipeResult checkProcessing() {
        consumeAstralArrayFromBuses();
        return super.checkProcessing();
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
            // Too low a nanite tier for this step: stall and show the red warning.
            setNaniteStalled(true);
            return;
        }

        long availableNanites = getAvailableNanites();
        if (availableNanites <= 0) {
            setNaniteStalled(true);
            return;
        }

        // Stall clears once both tier and count are available; slow accumulation below must not keep the warning on.
        setNaniteStalled(false);

        // Original BEC divisor formula, with no parallel or condensate slowdown on this standalone machine.
        // Auto-adjust mode first downgrades the swarm tier to the step's target tier, so an over-tiered swarm no
        // longer eats the halving divisor; below the target the stall above still applies and the speed formula itself
        // is untouched.
        int effectiveTier = autoAdjustUnlocked ? Math.min(step.tier.tier, providedTier.tier) : providedTier.tier;
        int aboveTierDivisor = 1 << Math.abs(step.tier.tier - effectiveTier);
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
        // Reuses GT's synced recipe-result widget, drawn above the progress line.
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

    // region Astral Array / auto-adjust mode

    /**
     * Consumes one Astral Array Fabricator from an input bus. Only {@code mInputBusses} is read: the pattern input
     * assemblies (programmable hatches / CRIB and the like) keep their virtual slots to themselves, a star array put
     * there is not consumed. The unlock and its save mechanism are the Nano-Scale Foundry's, copied here: consumed
     * once, kept in the machine NBT and in the controller item NBT, extractable again with a wire cutter.
     */
    private void consumeAstralArrayFromBuses() {
        if (autoAdjustUnlocked) return;
        ItemStack starArray = CustomItemList.astralArrayFabricator.get(1);
        for (MTEHatchInputBus bus : GTUtility.filterValidMTEs(mInputBusses)) {
            if (bus == null) continue;
            IGregTechTileEntity busTile = bus.getBaseMetaTileEntity();
            for (int i = busTile.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack stack = busTile.getStackInSlot(i);
                if (stack != null && GTUtility.areStacksEqual(stack, starArray)) {
                    busTile.decrStackSize(i, 1);
                    autoAdjustUnlocked = true;
                    return;
                }
            }
        }
    }

    /**
     * A wire cutter gives the Astral Array back into an output bus and turns auto-adjust off. Like the Nano-Scale
     * Foundry's, the unlock only drops once the array was actually ejected, so a full output bus never eats it.
     */
    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (!autoAdjustUnlocked) return false;

        ItemStack starArray = CustomItemList.astralArrayFabricator.get(1);
        addOutputPartial(starArray);
        if (starArray.stackSize <= 0) {
            autoAdjustUnlocked = false;
            markDirty();
        }
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("becAutoAdjust", autoAdjustUnlocked);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        autoAdjustUnlocked = aNBT.getBoolean("becAutoAdjust");
    }

    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        if (autoAdjustUnlocked) aNBT.setBoolean("becAutoAdjust", true);
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("becAutoAdjust")) {
            tooltip.add(EnumChatFormatting.DARK_PURPLE + translateToLocal("machine.bosescraftingarray.tooltip.astral"));
        }
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        if (autoAdjustUnlocked) tag.setBoolean("becAutoAdjust", true);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        if (accessor.getNBTData()
            .getBoolean("becAutoAdjust")) {
            currentTip.add(translateToLocal("machine.bosescraftingarray.waila.autoadjust"));
        }
    }

    // endregion

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
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.desc"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.structure.header"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.structure.frame"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.structure.casings"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.structure.hatches"))
            .addSeparator()
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.header"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.bus"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.tier"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.speed"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.steps"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.stall"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.nanite.astral"))
            .addSeparator()
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.process.header"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.process.energy"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.process.parallel"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.process.overclock"))
            .addSeparator()
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.details"))
            .addInfo(translateToLocal("machine.bosescraftingarray.tooltip.details.hint"))
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
