package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_NANOCHIP_ASSEMBLY_COMPLEX;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_NANOCHIP_ASSEMBLY_COMPLEX_ACTIVE;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.gui.MTNanoScaleFoundryGui;
import com.MessTech.common.machine.Base.TickableParallelismAcrossMultiMachineBase;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import goodgenerator.items.GGMaterial;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.metadata.BoardProcessingModuleFluidKey;
import gregtech.api.recipe.metadata.NanochipAssemblyMatrixTierKey;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTWaila;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.overlay.tooltiprenderers.TTRenderStack;
import tectech.thing.CustomItemList;

/**
 * Nano-Scale Foundry.
 * <p>
 * AIO machine intended to aggregate all NAC processing in one multiblock. Currently cloned from
 * {@code MachineTemplate}: same 3x3x3 structure and no structure changes yet.
 */
public class MTNanoScaleFoundry extends TickableParallelismAcrossMultiMachineBase<MTNanoScaleFoundry>
    implements ISurvivalConstructable, RecipeMapWorkable {

    private final Map<String, BoardTankState> boardTanks = new HashMap<>();

    /** Whether an Astral Array Fabricator has been consumed to unlock thread 12 / the 24 pool. */
    private boolean astralArrayUnlocked = false;

    /** Snapshot of each input bus's circuit number, captured while recipe processing is not active. */
    private final Map<MTEHatchInputBus, Integer> inputBusCircuitNumbers = new HashMap<>();

    // region Structure piece offsets
    private static final int HORIZONTAL_OFFSET = 16;
    private static final int VERTICAL_OFFSET = 55;
    private static final int DEPTH_OFFSET = 12;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","               A                ","                                ","                                ","               A                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","               B                ","              AB                ","               B                ","               B                ","              AB                ","               B                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","               B                ","              AB                ","               B                ","               B                ","              AB                ","               B                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","              BB                ","             ABB                ","              BB                ","              BB                ","             ABB                ","              BB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","              BB                ","             ABB                ","              BB                ","              BB                ","             ABB                ","              BB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","             BBB                ","            ABBB                ","             BBB                ","             BBB                ","            ABBB                ","             BBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","             BBB                ","            AB B                ","             B B                ","             B B                ","            AB B                ","             BBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                  A             ","                                ","                                ","            BBBB                ","           ABB B  A             ","            BB B                ","            BB B                ","           ABB B                ","            BBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                  B             ","                  B             ","                  BA            ","                  B             ","                  B             ","            BBBB  B             ","           AB  B  BA            ","            B  B  B             ","            B  B  B             ","           AB  B                ","            BBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                A               ","                                ","                  B             ","                A B             ","                  BA            ","                  B             ","                  B             ","           BBBBB  B             ","          ABB  B  BA            ","           BB  BEEB             ","           BB  B  B             ","          ABB  B                ","           BBBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                B               ","               AB               ","                B               ","                B BB            ","               AB BB            ","                B BBA           ","                  BB            ","                  BB            ","           BBBBB  BB            ","          AB   B  BBA           ","           B   B  BB            ","           B   B  BB            ","          AB   B                ","           BBBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                B               ","               AB               ","                B               ","                B BB            ","               AB BB            ","                B BBA           ","                  BB            ","                  BB            ","          BBBBBB  BB            ","         ABCCCCB  BBA           ","          BCCCCB  BB            ","          BCCCCB  BB            ","         ABCCCCB                ","          BBBBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                B               ","               AB               ","                B               ","                B BBB           ","               ABEBBB           ","                B BBBA          ","                  BBB           ","                  BBB           ","          BBBBBB  BBB           ","         ABCCCCB  BBBA          ","          BCCCCB  BBB           ","          BCCCCB  BBB           ","         ABCCCCB                ","          BBBBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                B               ","               AB               ","                B               ","                B BBB           ","               AB BBB           ","                B BBBA          ","                  BBB           ","                  BBB           ","          BBBBBB  BBB           ","         ABCCCCB  BBBA          ","          BCCCCB  BBB           ","          BCCCCB  BBB           ","         ABCCCCB                ","          BBBBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","               BB               ","              ABB               ","               BB               ","               BB BBBB          ","              ABB BBBB          ","               BB BBBBA         ","                  BBBB          ","                  BBBB          ","          BBBBBB  BBBB          ","         ABCCCCB  BBBBA         ","          BCCCCB  BBBB          ","          BCCCCB  BBBB          ","         ABCCCCB                ","          BBBBBB                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","               BB               ","              ABB               ","               BB               ","               BB BBBB          ","              ABB BBBB          ","               BB BBBBA         ","                  BBBB          ","                  BBBB          ","          BBBBB   BBBB          ","         ABCCCC   BBBBA         ","          BCCCC   BBBB          ","          BCCCC   BBBB          ","         ABCCCC                 ","          BBBBB                 ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","               BB               ","              ABB               ","               BB               ","               BB BBBBB         ","              ABB CCCBB         ","               BB CCCBBA        ","               E  CCCBB         ","               E  CCCBB         ","          BBBBBE  CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCC   CCCBB         ","          BCCCC   BBBBB         ","         ABCCCC                 ","          BBBBB                 ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","               BB               ","              ABB               ","               BB               ","               BB BBBBB         ","              ABB CCCBB         ","               BB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBB   CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCC   CCCBB         ","          BCCCC   BBBBB         ","         ABCCCC                 ","          BBBBB                 ","           A                    ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","              BBB               ","             ABBB A             ","              BBB               ","              BBB BBBBB         ","             ABBB CCCBB         ","              BBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBB   CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCCEEECCCBB         ","          BCCCC   BBBBB         ","         ABCCCC                 ","          BBBBB                 ","           A                    ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","              BBB               ","             ABBB A             ","              BBB               ","          A   BBB BBBBB         ","             ABBB CCCBB         ","              BBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBB   CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCC   CCCBB         ","          BCCCC   BBBBB         ","         ABCCCC                 ","          BBBBB                 ","           A                    ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","              BBB               ","             ABBB A             ","          B   BBB               ","         ABEEEBBB BBBBB         ","          B  ABBB CCCBB         ","              BBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBB   CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCC   CCCBB         ","          BCCCC   BBBBB         ","         ABCCCC                 ","          BBBBB                 ","           A                    ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","              BBB A             ","             ABBB BB            ","          B   BBB               ","         AB   BBB BBBBB         ","          B  ABBBECCCBB         ","              BBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBB   CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCC   CCCBB         ","          BCCCC   BBBBB         ","         ABCCCC    A            ","          BBBBB                 ","           A                    ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","             BBBB A             ","            ABCCC BB            ","         BB  BCCC  E            ","        ABB  BCCC BBBBB         ","         BB ABCCC CCCBB         ","             BBBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBB   CCCBB         ","         ABCCCC   CCCBBA        ","          BCCCC   CCCBB         ","          BCCCC   BBBBB         ","         ABCCCC    A            ","          BBBBB                 ","           A A                  ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","             AA                 ","            ABBBB A             ","            ABCCC BB            ","         BB  BCCC               ","        ABB  BCCC BBBBB         ","         BB ABCCC CCCBB         ","             BBBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCB  CCCBBA        ","          BCCCCB  CCCBB         ","          BCCCCB  BBBBB         ","         ABCCCCB   A            ","          BBBBBB                ","           A A                  ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   A             ","             BBBB BB            ","            ABCCC BB            ","         BB  BCCC               ","        ABB  BCCC BBBBB         ","         BB ABCCC CCCBB         ","             BBBB CCCBBA        ","              E   CCCBB         ","              E   CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCBEECCCBBA        ","          BCCCCB  CCCBB         ","          BCCCCB  BBBBB         ","         ABCCCCB   A            ","          BBBBBB                ","           A A                  ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              AA  A             ","             BBBB BB            ","            ABCCC BB            ","         BB  BCCCEE             ","        ABBEEBCCC BBBBB         ","         BB ABCCC CCCBB         ","             BBBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCB  CCCBBA        ","          BCCCCB  CCCBBA        ","          BCCCCB  BBBBBA        ","         ABCCCCB  AAAAA         ","          BBBBBB                ","           A A                  ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   A             ","             BBBB BB            ","            ABCCC BB            ","         BB  BCCC               ","        ABB  BCCC BBBBB         ","         BB ABCCC CCCBB         ","             BBBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCB  CCCBBA        ","          BCCCCB  CCCBB         ","          BCCCCB  BBBBB         ","         ABCCCCB  A  A          ","         ABBBBBB                ","          AAAAA                 ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   A             ","             BBBB BB            ","             BCCC BB            ","         BB ABCCC               ","        ABB  BCCC BBBBB         ","         BB ABCCC CCCBB         ","             BBBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCB  CCCBBA        ","          BCCCCB  CCCBB         ","         ABCCCCB  BBBBB         ","          BCCCCB  A  A          ","          BBBBBB                ","            A A                 ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   A             ","             BBBB BB            ","             BCCC BB            ","         BB  BCCC               ","        ABB ABCCC BBBBB         ","         BB ABCCC CCCBB         ","           AABBBB CCCBBA        ","                  CCCBB         ","                  CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCB  CCCBBA        ","         ABCCCCB  CCCBB         ","          BCCCCB  BBBBB         ","          BCCCCB  A             ","          BBBBBB                ","            A A                 ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   AA            ","             BBBBBBBA           ","             BCCC BBBA          ","         BBBBBCCC BBBBA         ","        ABBBBBCCC BBBBBA        ","         BBBBBCCC CCCBBA        ","          ABBBBBBBCCCBBA        ","           B  B   CCCBB         ","           B  B   CCCBB         ","          BBBBBB  CCCBB         ","         ABCCCCB  CCCBBA        ","          BCCCCB  CCCBB         ","          BCCCCBBBBBBBB         ","          BCCCCBBBABBB          ","          BBBBBBBBAB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   A             ","             BBBBBBB            ","           BB   B   BB          ","         BB           B         ","        ABB           B         ","         BB           B         ","         AB           BA        ","          B           B         ","          B           B         ","          B           B         ","         AB           BA        ","          B           B         ","          B           B         ","          B           B         ","          BBBBB     BB          ","             BB   BB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              AAA A             ","             BBBBBBB            ","           BB   B   BB          ","         BB     B     B         ","        ABB           B         ","        AB             B        ","        AB             BA       ","         B             B        ","         BBB         BBB        ","         B             B        ","        AB             BA       ","         B             B        ","          B           B         ","          B           B         ","          BBB       BB          ","             BB   BB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","              A   A             ","             BBBBBBB            ","           BB       BB          ","         BB     B     B         ","         BB     B     B         ","         B      E      B        ","        AB    EEEEE    BA       ","         B    E   E    B        ","         B BBEE   EEBB B        ","         B    E   E    B        ","        AB    EEEEE    BA       ","         B      E      B        ","          B     B     B         ","          B     B     B         ","          BBB   B   BB          ","             BBBBBBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","              A   A             ","             BBBBBBB            ","           BB       BB          ","          B           B         ","         B             B        ","         B      B      B        ","        B       B       B       ","       AB               BA      ","        B               B       ","        B   BB     BB   B       ","        B               B       ","       AB               BA      ","        B       B       B       ","         B      B      B        ","         B             B        ","          B           B         ","           BB       BB          ","             BBBBBBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","              A   A             ","             BBFBFBB            ","           FB       BF          ","          B           B         ","         F             F        ","         B             B        ","        B       B       B       ","       AB       B       BA      ","        F               F       ","        B    BB   BB    B       ","        F               F       ","       AB       B       BA      ","        B       B       B       ","         B             B        ","         F             F        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","              A   A             ","             BBFBFBB            ","           FB       BF          ","          B           B         ","         F             F        ","         B             B        ","        B               B       ","       AB       B       BA      ","        F      EBE      F       ","        B     BBBBB     B       ","        F      EBE      F       ","       AB       B       BA      ","        B               B       ","         B             B        ","         F             F        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","              A   A             ","             BBFBFBB            ","           FB       BF          ","          B           B         ","         F             F        ","         B             B        ","        B               B       ","       AB               BA      ","        F      BBB      F       ","        B      BBB      B       ","        F      BBB      F       ","       AB               BA      ","        B               B       ","         B             B        ","         F             F        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","              A   A             ","             BBFBFBB            ","           FB       BF          ","          B           B         ","         F             F        ","         B             B        ","        B               B       ","       AB               BA      ","        F      EAE      F       ","        B      ABA      B       ","        F      EAE      F       ","       AB               BA      ","        B               B       ","         B             B        ","         F             F        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","              A   A             ","             BBBBBBB            ","           BB       BB          ","          B           B         ","         B             B        ","         B             B        ","        B               B       ","       AB               BA      ","        B       E       B       ","        B      AAA      B       ","        B      EAE      B       ","       AB               BA      ","        B               B       ","         B             B        ","         B             B        ","          B           B         ","           BB       BB          ","             BBBBBBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","              A   A             ","             BBBBBBB            ","           BB       BB          ","          B           B         ","         B             B        ","        B               B       ","        B               B       ","       B                 B      ","      AB                 BA     ","       B        A        B      ","       B       AAA       B      ","       B        A        B      ","      AB                 BA     ","       B                 B      ","        B               B       ","        B               B       ","         B             B        ","          B           B         ","           BB       BB          ","             BBBBBBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","              A   A             ","             B     B            ","           FB       BF          ","          B           B         ","         B             B        ","        F               F       ","        B               B       ","       B                 B      ","      AB                 BA     ","       F                 F      ","       B        ~        B      ","       F                 F      ","      AB                 BA     ","       B                 B      ","        B               B       ","        F               F       ","         B             B        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","              A   A             ","             B     B            ","           FB       BF          ","          B           B         ","         B             B        ","        F               F       ","        B               B       ","       B                 B      ","      AB                 BA     ","       F                 F      ","       B                 B      ","       F                 F      ","      AB                 BA     ","       B                 B      ","        B               B       ","        F               F       ","         B             B        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","              A   A             ","             B     B            ","           FB       BF          ","          B           B         ","         BHHH       HHHB        ","        F HHH       HHH F       ","        B HHH       HHH B       ","       B                 B      ","      AB                 BA     ","       F                 F      ","       B                 B      ","       F                 F      ","      AB                 BA     ","       B                 B      ","        B HHH       HHH B       ","        F HHH       HHH F       ","         BHHH       HHHB        ","          B           B         ","           FB       BF          ","             BBFBFBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","              A   A             ","             B     B            ","           BB       BB          ","          B           B         ","         BHHH       HHHB        ","        B HCH       HCH B       ","        B HHH       HHH B       ","       B                 B      ","      AB                 BA     ","       B                 B      ","       B                 B      ","       B                 B      ","      AB                 BA     ","       B                 B      ","        B HHH       HHH B       ","        B HCH       HCH B       ","         BHHH       HHHB        ","          B           B         ","           BB       BB          ","             BBBBBBB            ","              A   A             ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","             BB   BB            ","           BBB     BBB          ","          BB         BB         ","         B B         B B        ","        B HHH       HHH B       ","       BBBHCH       HCHBBB      ","       B  HHH       HHH  B      ","      BBBBB              BB     ","      B                   B     ","      B                   B     ","      B                   B     ","      B                   B     ","      B                   B     ","      BB                 BB     ","       B  HHH       HHH  B      ","       BBBHCH       HCHBBB      ","        B HHH       HHH B       ","         B B         B B        ","          BB         BB         ","           BBB     BBB          ","             BBBBBBB            ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","             BB   BB            ","           BB       BB          ","          B           B         ","         B             B        ","        B HHH       HHH B       ","       B  HCH       HCH  B      ","       B  HHH       HHH  B      ","      B                   B     ","      B                   B     ","      B                   B     ","      B                   B     ","      B                   B     ","      B                   B     ","      B                   B     ","       B  HHH       HHH  B      ","       B  HCH       HCH  B      ","        B HHH       HHH B       ","         B             B        ","          B           B         ","           BB       BB          ","             BBBBBBB            ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","             BBBBBBB            ","           BBFFFFFFFBB          ","          BFFFFFFFFFFFB         ","         BFFFFFFFFFFFFFB        ","        BFHHHFFFFFFFHHHFB       ","       BFFHCHFFFFFFFHCHFFB      ","       BFFHHHFFFFFFFHHHFFB      ","      BFFFFFFDDDDDDDFFFFFFB     ","      BFFFFFFDAAAAADFFFFFFB     ","      BFFFFFFDAGGGADFFFFFFB     ","      BFFFFFFDAGGGADFFFFFFB     ","      BFFFFFFDAGGGADFFFFFFB     ","      BFFFFFFDAAAAADFFFFFFB     ","      BFFFFFFDDDDDDDFFFFFFB     ","       BFFHHHFFFFFFFHHHFFB      ","       BFFHCHFFFFFFFHCHFFB      ","        BFHHHFFFFFFFHHHFB       ","         BFFFFFFFFFFFFFB        ","          BFFFFFFFFFFFB         ","           BBFFFFFFFBB          ","             BBBBBBB            ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","             BBBBBBB            ","           BB  B B  BB          ","          B    B B    B         ","         B     B B     B        ","        B      B B      B       ","       B   BBBBB BBBBB   B      ","       B   B   B B   B   B      ","      B    B AAAAAAA B    B     ","      B    B AAAAAAA B    B     ","      BBBBBBBAAGGGAABBBBBBB     ","      B      AAGGGAA      B     ","      BBBBBBBAAGGGAABBBBBBB     ","      B    B AAAAAAA B    B     ","      B    B AAAAAAA B    B     ","       B   B   B B   B   B      ","       B   BBBBB BBBBB   B      ","        B      B B      B       ","         B     B B     B        ","          B    B B    B         ","           BB  B B  BB          ","             BBBBBBB            ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},

    };
    // spotless:on

    private static final IStructureDefinition<MTNanoScaleFoundry> STRUCTURE_DEFINITION = StructureDefinition
        .<MTNanoScaleFoundry>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        .addElement(
            'H',
            HatchElementBuilder.<MTNanoScaleFoundry>builder()
                .anyOf(HatchElement.InputBus, HatchElement.OutputBus, HatchElement.InputHatch, HatchElement.OutputHatch)
                .casingIndex(Casings.NanochipMeshInterfaceCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.NanochipMeshInterfaceCasing.getBlock(),
                        Casings.NanochipMeshInterfaceCasing.getBlockMeta())))
        .addElement('A', Casings.NanochipMeshInterfaceCasing.asElement())
        .addElement('B', Casings.NanochipReinforcementCasing.asElement())
        .addElement('C', Casings.NanochipComputationalMatrixCasing.asElement())
        .addElement('D', Casings.NanochipFirewallProjectionCasing.asElement())
        .addElement('E', ofFrame(Materials.Naquadah))
        .addElement('F', Casings.NanochipComplexGlass.asElement())
        .addElement(
            'G',
            HatchElementBuilder.<MTNanoScaleFoundry>builder()
                .anyOf(HatchElement.Energy.or(HatchElement.ExoticEnergy))
                .hint(2)
                .casingIndex(Casings.NanochipComputationalMatrixCasing.textureId)
                .buildAndChain(
                    ofBlock(
                        Casings.NanochipComputationalMatrixCasing.getBlock(),
                        Casings.NanochipComputationalMatrixCasing.getBlockMeta())))
        .build();
    // endregion

    public MTNanoScaleFoundry(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        initThreads();
    }

    public MTNanoScaleFoundry(String aName) {
        super(aName);
        initThreads();
    }

    @Override
    protected MTNanoScaleFoundryGui getGui() {
        return new MTNanoScaleFoundryGui(this);
    }

    public static String getLocalizedThreadName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return internalName;
        return StatCollector.translateToLocal("machine.nanoscale.thread." + internalName.toLowerCase());
    }

    private void initThreads() {
        if (getThreadCount() > 0) return;

        addThread("Conversion").setCircuitNumber(1)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryConversionRecipes);
        addThread("AssemblyMatrix").setCircuitNumber(2)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryAssemblyMatrixRecipes);
        addThread("SMDProcessor").setCircuitNumber(3)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundrySMDProcessorRecipes);
        addThread("BoardProcessor").setCircuitNumber(4)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryBoardProcessorRecipes);
        boardTanks.put("BoardProcessor", new BoardTankState());
        addThread("EtchingArray").setCircuitNumber(5)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryEtchingArrayRecipes);
        addThread("CuttingChamber").setCircuitNumber(6)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryCuttingChamberRecipes);
        addThread("WireTracer").setCircuitNumber(7)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryWireTracerRecipes);
        addThread("SuperconductorSplitter").setCircuitNumber(8)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundrySuperconductorSplitterRecipes);
        addThread("OpticalOrganizer").setCircuitNumber(9)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryOpticalOrganizerRecipes);
        addThread("EncasementWrapper").setCircuitNumber(10)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryEncasementWrapperRecipes);
        addThread("BiologicalCoordinator").setCircuitNumber(11)
            .setRecipeMap(MTRecipeMaps.nanoScaleFoundryBiologicalCoordinatorRecipes);
    }

    private void ensureAstralArrayThread() {
        if (astralArrayUnlocked) {
            if (getThread("OneStepCircuitPool") == null) {
                addThread("OneStepCircuitPool").setCircuitNumber(12)
                    .setRecipeMap(MTRecipeMaps.nanoScaleFoundry24PoolRecipes);
            }
        } else if (getThread("OneStepCircuitPool") != null) {
            removeThread("OneStepCircuitPool");
        }
    }

    @Override
    public int getMaxThreadCount() {
        // 11 normal NAC pools + thread 12 (one-step 24 pool) while an Astral Array is consumed.
        return 12;
    }

    @Override
    protected String getDefaultThreadName(int index) {
        return switch (index) {
            case 0 -> "Conversion";
            case 1 -> "AssemblyMatrix";
            case 2 -> "SMDProcessor";
            case 3 -> "BoardProcessor";
            case 4 -> "EtchingArray";
            case 5 -> "CuttingChamber";
            case 6 -> "WireTracer";
            case 7 -> "SuperconductorSplitter";
            case 8 -> "OpticalOrganizer";
            case 9 -> "EncasementWrapper";
            case 10 -> "BiologicalCoordinator";
            default -> "Thread_" + index;
        };
    }

    @Override
    public IStructureDefinition<MTNanoScaleFoundry> getStructureDefinition() {
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
        ItemStack circuit = getStackInSlot(1);
        if (circuit == null || !circuit.getUnlocalizedName()
            .startsWith("gt.integrated_circuit")) {
            return null;
        }

        return switch (circuit.getItemDamage()) {
            case 1 -> MTRecipeMaps.nanoScaleFoundryConversionRecipes;
            case 2 -> MTRecipeMaps.nanoScaleFoundryAssemblyMatrixRecipes;
            case 3 -> MTRecipeMaps.nanoScaleFoundrySMDProcessorRecipes;
            case 4 -> MTRecipeMaps.nanoScaleFoundryBoardProcessorRecipes;
            case 5 -> MTRecipeMaps.nanoScaleFoundryEtchingArrayRecipes;
            case 6 -> MTRecipeMaps.nanoScaleFoundryCuttingChamberRecipes;
            case 7 -> MTRecipeMaps.nanoScaleFoundryWireTracerRecipes;
            case 8 -> MTRecipeMaps.nanoScaleFoundrySuperconductorSplitterRecipes;
            case 9 -> MTRecipeMaps.nanoScaleFoundryOpticalOrganizerRecipes;
            case 10 -> MTRecipeMaps.nanoScaleFoundryEncasementWrapperRecipes;
            case 11 -> MTRecipeMaps.nanoScaleFoundryBiologicalCoordinatorRecipes;
            default -> null;
        };
    }

    @Override
    public Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.asList(
            MTRecipeMaps.nanoScaleFoundryConversionRecipes,
            MTRecipeMaps.nanoScaleFoundryAssemblyMatrixRecipes,
            MTRecipeMaps.nanoScaleFoundrySMDProcessorRecipes,
            MTRecipeMaps.nanoScaleFoundryBoardProcessorRecipes,
            MTRecipeMaps.nanoScaleFoundryEtchingArrayRecipes,
            MTRecipeMaps.nanoScaleFoundryCuttingChamberRecipes,
            MTRecipeMaps.nanoScaleFoundryWireTracerRecipes,
            MTRecipeMaps.nanoScaleFoundrySuperconductorSplitterRecipes,
            MTRecipeMaps.nanoScaleFoundryOpticalOrganizerRecipes,
            MTRecipeMaps.nanoScaleFoundryEncasementWrapperRecipes,
            MTRecipeMaps.nanoScaleFoundryBiologicalCoordinatorRecipes);
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        // The per-thread terminal rows already show active/idle state. Suppress the generic
        // "No valid recipe found" / "Processing recipe" line that would otherwise always be present
        // while this machine is in its constant one-second polling cycle.
        return false;
    }

    @Override
    public boolean hasRunningText() {
        // The machine is intentionally always active for one-second input polling; the per-thread
        // rows are the real status display, so don't show a generic "Running..." line.
        return false;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null || !base.isAllowedToWork()) {
            mMaxProgresstime = 0;
            mProgresstime = 0;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Keep the machine on a one-second GT cycle. The standard checkRecipe() wrapper calls
        // startRecipeProcessing() before this method and endRecipeProcessing() after it, so ME input
        // buses/hatches can snapshot and commit correctly. All thread input reads/consumption happen
        // inside that wrapper.
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = 20;
        mProgresstime = 0;

        consumeAstralArrayFromBuses();
        ensureAstralArrayThread();

        tickThreadScheduler(base, 0);
        updateSlots();

        long totalEUt = getTotalThreadEUt();
        lEUt = (int) (totalEUt > 0 ? -Math.min(Integer.MAX_VALUE, totalEUt) : 0);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    protected int getMaxNewTasksPerThread(WorkThread thread) {
        // Each thread runs one independent recipe at a time; multiple threads provide cross-pool
        // parallel processing. This keeps GUI/Waila progress per-thread accurate and independent.
        return 1;
    }

    @Override
    protected int getMaxParallelForThread(WorkThread thread) {
        // The concrete machine is allowed to decide. Foundry lets each thread run as much as inputs
        // and remaining power allow, up to int max.
        return Integer.MAX_VALUE;
    }

    @Override
    protected CheckRecipeResult checkAndStartThread(IGregTechTileEntity base, WorkThread thread) {
        RecipeMap<?> recipeMap = thread.getRecipeMap();
        if (recipeMap == null || !mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        if (thread.isActive()) return CheckRecipeResultRegistry.NO_RECIPE;

        if (thread.getCircuitNumber() == 12) {
            return checkAndStartOneStepPool(base, thread);
        }

        int circuit = thread.getCircuitNumber();
        ArrayList<MTEHatchInputBus> buses = getThreadInputBuses(circuit);
        if (buses.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

        ArrayList<ItemStack> items = new ArrayList<>();
        for (MTEHatchInputBus bus : buses) {
            IGregTechTileEntity busTile = bus.getBaseMetaTileEntity();
            int circuitSlot = getBusCircuitSlotDuringProcessing(bus);
            for (int i = busTile.getSizeInventory() - 1; i >= 0; i--) {
                if (i == circuitSlot) continue;
                ItemStack stack = busTile.getStackInSlot(i);
                if (stack != null) items.add(stack);
            }
        }
        if (items.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

        // Fluid isolation follows GT5U's color model: only fluid hatches whose color matches the
        // thread's input-bus colors (plus uncolored hatches) are visible to this thread.
        ArrayList<FluidStack> fluidStacks = getThreadFluidStacks(buses);

        GTRecipe recipe = recipeMap.findRecipeQuery()
            .items(items.toArray(new ItemStack[0]))
            .fluids(fluidStacks.toArray(new FluidStack[0]))
            .find();
        if (recipe == null) return CheckRecipeResultRegistry.NO_RECIPE;

        if (thread.getCircuitNumber() == 2) {
            int recipeTier = recipe.getMetadataOrDefault(NanochipAssemblyMatrixTierKey.INSTANCE, 1);
            if (getInputVoltageTier() < recipeTier) {
                return CheckRecipeResultRegistry.insufficientMachineTier(recipeTier);
            }
        }

        if (thread.getCircuitNumber() == 4) {
            BoardTankState tank = getBoardTank(thread);
            if (tank == null || !tank.canProcess(recipe)) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
        }

        if (thread.getCircuitNumber() == 9 && getOpticalBoostingWaters(fluidStacks).size() < 2) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        long availableEUt = getMaxInputEu();
        long currentEUt = 0;
        for (WorkThread t : getThreads()) {
            if (t.isActive()) currentEUt += t.getEUt();
        }
        if (currentEUt > availableEUt) {
            return CheckRecipeResultRegistry.insufficientPower(currentEUt);
        }

        long powerRoom = availableEUt - currentEUt;
        int[] overclocked = calculateOverclockedTask(thread, recipe, fluidStacks, powerRoom);
        long perUnitEUt = Math.max(1, overclocked[0]);
        int duration = Math.max(1, overclocked[1]);
        int machineCap = getMaxParallelForThread(thread);
        double inputParallel = recipe.maxParallelCalculatedByInputs(
            machineCap,
            fluidStacks.toArray(new FluidStack[0]),
            items.toArray(new ItemStack[0]));
        long powerParallel = powerRoom / perUnitEUt;
        int parallel = (int) Math.max(1, Math.min(machineCap, Math.min(inputParallel, powerParallel)));

        if (parallel <= 0 || powerParallel <= 0) {
            // If nothing else is running and even one copy cannot be powered, report insufficient power.
            if (currentEUt == 0) {
                return CheckRecipeResultRegistry.insufficientPower(perUnitEUt);
            }
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // Simulate first so we do not partially consume inputs if one slot is short.
        if (recipe.mInputs != null) {
            for (ItemStack input : recipe.mInputs) {
                if (input != null && !depleteThreadItem(buses, scaleItem(input, parallel), true)) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack input : recipe.mFluidInputs) {
                if (input != null && !depleteThreadFluid(buses, scaleFluid(input, parallel), true)) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
            }
        }

        // Commit consumption.
        if (recipe.mInputs != null) {
            for (ItemStack input : recipe.mInputs) {
                if (input != null) depleteThreadItem(buses, scaleItem(input, parallel), false);
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack input : recipe.mFluidInputs) {
                if (input != null) depleteThreadFluid(buses, scaleFluid(input, parallel), false);
            }
        }

        RecipeTask task = thread.start(
            duration,
            recipeMap,
            scaleOutputItems(recipe.mOutputs, parallel),
            scaleOutputFluids(recipe.mFluidOutputs, parallel));
        task.setEUt(perUnitEUt * parallel);
        task.setParallel(parallel);
        if (thread.getCircuitNumber() == 9) {
            task.setModuleData(createOpticalTaskData(fluidStacks));
        } else if (thread.getCircuitNumber() == 4) {
            task.setModuleData(recipe.getMetadataOrDefault(BoardProcessingModuleFluidKey.INSTANCE, 1));
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private CheckRecipeResult checkAndStartOneStepPool(IGregTechTileEntity base, WorkThread thread) {
        if (!astralArrayUnlocked) return CheckRecipeResultRegistry.NO_RECIPE;

        ArrayList<MTEHatchInputBus> allBuses = new ArrayList<>(GTUtility.filterValidMTEs(mInputBusses));
        if (allBuses.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

        ArrayList<ItemStack> items = new ArrayList<>();
        for (MTEHatchInputBus bus : allBuses) {
            IGregTechTileEntity busTile = bus.getBaseMetaTileEntity();
            int circuitSlot = getBusCircuitSlotDuringProcessing(bus);
            for (int i = busTile.getSizeInventory() - 1; i >= 0; i--) {
                if (i == circuitSlot) continue;
                ItemStack stack = busTile.getStackInSlot(i);
                if (stack != null) items.add(stack);
            }
        }
        if (items.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

        ArrayList<FluidStack> fluidStacks = getStoredFluids();

        GTRecipe recipe = null;
        for (GTRecipe candidate : MTRecipeMaps.nanoScaleFoundry24PoolRecipes.getAllRecipes()) {
            if (candidate.isRecipeInputEqual(
                false,
                false,
                fluidStacks.toArray(new FluidStack[0]),
                items.toArray(new ItemStack[0]))) {
                recipe = candidate;
                break;
            }
        }
        if (recipe == null) return CheckRecipeResultRegistry.NO_RECIPE;

        long availableEUt = getMaxInputEu();
        long currentEUt = 0;
        for (WorkThread t : getThreads()) {
            if (t.isActive()) currentEUt += t.getEUt();
        }
        if (currentEUt > availableEUt) {
            return CheckRecipeResultRegistry.insufficientPower(currentEUt);
        }
        long powerRoom = availableEUt - currentEUt;
        int[] overclocked = calculateOverclockedTask(thread, recipe, fluidStacks, powerRoom);
        long perUnitEUt = Math.max(1, overclocked[0]);
        int duration = Math.max(1, overclocked[1]);
        int machineCap = getMaxParallelForThread(thread);
        double inputParallel = recipe.maxParallelCalculatedByInputs(
            machineCap,
            fluidStacks.toArray(new FluidStack[0]),
            items.toArray(new ItemStack[0]));
        long powerParallel = powerRoom / perUnitEUt;
        int parallel = (int) Math.max(1, Math.min(machineCap, Math.min(inputParallel, powerParallel)));
        if (parallel <= 0 || powerParallel <= 0) {
            if (currentEUt == 0) return CheckRecipeResultRegistry.insufficientPower(perUnitEUt);
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (recipe.mInputs != null) {
            for (ItemStack input : recipe.mInputs) {
                if (input != null && !depleteThreadItem(allBuses, scaleItem(input, parallel), true)) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack input : recipe.mFluidInputs) {
                if (input != null && !depleteThreadFluid(allBuses, scaleFluid(input, parallel), true)) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
            }
        }

        if (recipe.mInputs != null) {
            for (ItemStack input : recipe.mInputs) {
                if (input != null) depleteThreadItem(allBuses, scaleItem(input, parallel), false);
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack input : recipe.mFluidInputs) {
                if (input != null) depleteThreadFluid(allBuses, scaleFluid(input, parallel), false);
            }
        }

        RecipeTask task = thread.start(
            duration,
            thread.getRecipeMap(),
            scaleOutputItems(recipe.mOutputs, parallel),
            scaleOutputFluids(recipe.mFluidOutputs, parallel));
        task.setEUt(perUnitEUt * parallel);
        task.setParallel(parallel);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private void refreshInputBusCircuits() {
        inputBusCircuitNumbers.clear();
        for (MTEHatchInputBus bus : GTUtility.filterValidMTEs(mInputBusses)) {
            if (bus == null) continue;
            int circuitSlot = bus.getCircuitSlot();
            ItemStack circuitStack = bus.getBaseMetaTileEntity()
                .getStackInSlot(circuitSlot);
            int circuit = circuitStack != null && GTUtility.isAnyIntegratedCircuit(circuitStack)
                ? circuitStack.getItemDamage()
                : -1;
            inputBusCircuitNumbers.put(bus, circuit);
        }
    }

    private void consumeAstralArrayFromBuses() {
        if (astralArrayUnlocked) return;
        ItemStack starArray = CustomItemList.astralArrayFabricator.get(1);
        for (MTEHatchInputBus bus : GTUtility.filterValidMTEs(mInputBusses)) {
            if (bus == null) continue;
            IGregTechTileEntity busTile = bus.getBaseMetaTileEntity();
            int circuitSlot = getBusCircuitSlotDuringProcessing(bus);
            for (int i = busTile.getSizeInventory() - 1; i >= 0; i--) {
                if (i == circuitSlot) continue;
                ItemStack stack = busTile.getStackInSlot(i);
                if (stack != null && GTUtility.areStacksEqual(stack, starArray)) {
                    busTile.decrStackSize(i, 1);
                    astralArrayUnlocked = true;
                    ensureAstralArrayThread();
                    return;
                }
            }
        }
    }

    private ArrayList<MTEHatchInputBus> getThreadInputBuses(int circuit) {
        int controllerCircuit = getControllerCircuitNumber();
        ArrayList<MTEHatchInputBus> result = new ArrayList<>();
        for (MTEHatchInputBus bus : GTUtility.filterValidMTEs(mInputBusses)) {
            if (bus == null) continue;

            if (controllerCircuit > 0) {
                // Controller-slot circuit selects one single active pool, as shown in the tooltip.
                if (controllerCircuit == circuit) {
                    result.add(bus);
                }
                continue;
            }

            // No controller circuit: each input bus is bound to a pool by its own circuit slot, so all
            // matching threads can run independently at the same time.
            Integer cachedCircuit = inputBusCircuitNumbers.get(bus);
            if (cachedCircuit != null && cachedCircuit == circuit) {
                result.add(bus);
            }
        }
        return result;
    }

    private int getControllerCircuitNumber() {
        ItemStack controller = getStackInSlot(1);
        if (controller == null || !GTUtility.isAnyIntegratedCircuit(controller)) return -1;
        int number = controller.getItemDamage();
        return number >= 1 && number <= getMaxThreadCount() ? number : -1;
    }

    private int getBusCircuitSlotDuringProcessing(MTEHatchInputBus bus) {
        if (bus instanceof MTEHatchInputBusME) {
            // During recipe processing an ME input bus exposes its circuit at the virtual offset.
            return bus.getCircuitSlot() + MTEHatchInputBusME.SLOT_COUNT;
        }
        return bus.getCircuitSlot();
    }

    private boolean depleteThreadItem(ArrayList<MTEHatchInputBus> buses, ItemStack stack, boolean simulate) {
        if (stack == null) return false;
        for (MTEHatchInputBus bus : buses) {
            if (!hasThreadResourceInBus(bus, stack)) continue;
            if (simulate) return true;
            // Existing per-bus removal. For ME buses the override handles the recipe-processing virtual
            // slots; for normal buses the inherited method also skips the circuit slot correctly.
            return bus.removeResource(stack, stack.stackSize) != null;
        }
        return false;
    }

    private boolean hasThreadResourceInBus(MTEHatchInputBus bus, ItemStack stack) {
        IGregTechTileEntity busTile = bus.getBaseMetaTileEntity();
        int circuitSlot = getBusCircuitSlotDuringProcessing(bus);
        for (int i = busTile.getSizeInventory() - 1; i >= 0; i--) {
            if (i == circuitSlot) continue;
            ItemStack slotStack = busTile.getStackInSlot(i);
            if (slotStack != null && GTUtility.areStacksEqual(stack, slotStack)
                && slotStack.stackSize >= stack.stackSize) {
                return true;
            }
        }
        return false;
    }

    private Set<Byte> getThreadColors(ArrayList<MTEHatchInputBus> buses) {
        Set<Byte> colors = new HashSet<>();
        for (MTEHatchInputBus bus : buses) {
            byte color = bus.getColor();
            if (color != -1) colors.add(color);
        }
        return colors;
    }

    private ArrayList<MTEHatchInput> getThreadFluidHatches(ArrayList<MTEHatchInputBus> buses) {
        Set<Byte> colors = getThreadColors(buses);
        ArrayList<MTEHatchInput> result = new ArrayList<>();
        for (MTEHatchInput hatch : GTUtility.filterValidMTEs(mInputHatches)) {
            byte hatchColor = hatch.getColor();
            if (colors.isEmpty() || hatchColor == -1 || colors.contains(hatchColor)) {
                result.add(hatch);
            }
        }
        return result;
    }

    private ArrayList<FluidStack> getThreadFluidStacks(ArrayList<MTEHatchInputBus> buses) {
        Set<Byte> colors = getThreadColors(buses);
        if (colors.isEmpty()) return getStoredFluids();

        ArrayList<FluidStack> fluids = new ArrayList<>();
        for (Byte color : colors) {
            fluids.addAll(getStoredFluidsForColor(Optional.of(color)));
        }
        return fluids;
    }

    private ArrayList<MTEHatchInput> getBoardFluidHatches(WorkThread thread) {
        ArrayList<MTEHatchInputBus> buses = getThreadInputBuses(thread.getCircuitNumber());
        if (!buses.isEmpty()) {
            return getThreadFluidHatches(buses);
        }

        // If the Board thread has no matching input bus at all, let the immersion tank still see
        // fluid hatches when the controller is set to Board mode or when the machine has no input
        // buses. This makes the tank fill/debug GUI usable without requiring a dummy item bus.
        int controller = getControllerCircuitNumber();
        boolean noBusesAtAll = GTUtility.filterValidMTEs(mInputBusses)
            .isEmpty();
        if (controller == thread.getCircuitNumber() || noBusesAtAll) {
            return GTUtility.filterValidMTEs(mInputHatches);
        }
        return new ArrayList<>();
    }

    private ArrayList<FluidStack> getBoardFluidStacks(WorkThread thread) {
        ArrayList<MTEHatchInputBus> buses = getThreadInputBuses(thread.getCircuitNumber());
        if (!buses.isEmpty()) {
            return getThreadFluidStacks(buses);
        }

        int controller = getControllerCircuitNumber();
        boolean noBusesAtAll = GTUtility.filterValidMTEs(mInputBusses)
            .isEmpty();
        if (controller == thread.getCircuitNumber() || noBusesAtAll) {
            return getStoredFluids();
        }
        return new ArrayList<>();
    }

    private int drainFluidFromHatches(ArrayList<MTEHatchInput> hatches, FluidStack request, boolean simulate) {
        if (request == null || request.amount <= 0) return 0;
        int total = 0;
        int remaining = request.amount;
        for (MTEHatchInput hatch : hatches) {
            FluidStack drained = hatch
                .drain(ForgeDirection.UNKNOWN, new FluidStack(request.getFluid(), remaining), !simulate);
            if (drained == null || drained.amount <= 0) continue;
            total += drained.amount;
            remaining -= drained.amount;
            if (remaining <= 0) break;
        }
        return total;
    }

    private boolean depleteThreadFluid(ArrayList<MTEHatchInputBus> buses, FluidStack fluid, boolean simulate) {
        if (fluid == null) return false;
        ArrayList<MTEHatchInput> hatches = getThreadFluidHatches(buses);
        if (hatches.isEmpty()) return false;

        FluidStack remaining = fluid.copy();
        for (MTEHatchInput hatch : hatches) {
            FluidStack drained = hatch.drain(ForgeDirection.UNKNOWN, remaining, !simulate);
            if (drained == null || drained.amount <= 0 || drained.getFluid() != fluid.getFluid()) continue;
            if (drained.amount >= remaining.amount) return true;
            remaining.amount -= drained.amount;
        }
        return remaining.amount <= 0;
    }

    private float getThreadEuModifier(WorkThread thread, GTRecipe recipe, ArrayList<FluidStack> fluids) {
        return switch (thread.getCircuitNumber()) {
            case 4 -> getBoardEuModifier(thread, recipe);
            case 5 -> getEtchingEuModifier();
            case 9 -> getOpticalEuModifier(fluids);
            default -> 1.0F;
        };
    }

    private BoardTankState getBoardTank(WorkThread thread) {
        return thread == null ? null : boardTanks.get(thread.getName());
    }

    public int getBoardTankTypeCount() {
        return 4;
    }

    public FluidStack getBoardTankStoredFluid(int type) {
        BoardTankState tank = getBoardTank(getThread("BoardProcessor"));
        BoardTank chamber = tank == null ? null : tank.getTank(type);
        return chamber == null || chamber.storedFluid == null ? null : chamber.storedFluid.copy();
    }

    public FluidStack getBoardTankImpurityFluid(int type) {
        BoardTankState tank = getBoardTank(getThread("BoardProcessor"));
        BoardTank chamber = tank == null ? null : tank.getTank(type);
        return chamber == null || chamber.impurityFluid == null ? null : chamber.impurityFluid.copy();
    }

    public int getBoardTankFluidAmount(int type) {
        BoardTankState tank = getBoardTank(getThread("BoardProcessor"));
        BoardTank chamber = tank == null ? null : tank.getTank(type);
        return chamber == null ? 0 : chamber.fluidAmount;
    }

    public int getBoardTankCapacity() {
        return BoardTankState.CAPACITY;
    }

    public double getBoardTankImpurityPercentage(int type) {
        BoardTankState tank = getBoardTank(getThread("BoardProcessor"));
        BoardTank chamber = tank == null ? null : tank.getTank(type);
        return chamber == null ? 0 : chamber.getImpurityPercentage();
    }

    private float getBoardEuModifier(WorkThread thread, GTRecipe recipe) {
        BoardTankState tank = getBoardTank(thread);
        return tank == null ? 1.0F : tank.getEuMultiplier(recipe);
    }

    private float getThreadSpeedModifier(WorkThread thread, GTRecipe recipe, ArrayList<FluidStack> fluids) {
        return switch (thread.getCircuitNumber()) {
            case 5 -> getEtchingSpeedModifier();
            case 9 -> getOpticalSpeedModifier(fluids);
            default -> 1.0F;
        };
    }

    private float getEtchingEuModifier() {
        long amps = getMaxInputAmps();
        long divisor = Math.max(1, GTUtility.log4ceil(amps) - 3);
        return 1.0F / divisor;
    }

    private float getEtchingSpeedModifier() {
        long tier = getInputVoltageTier();
        return 1.0F / Math.max(1, tier - 9);
    }

    private float getOpticalEuModifier(ArrayList<FluidStack> fluids) {
        float modifier = 1.0F;
        boolean grade7 = false;
        boolean grade8 = false;
        for (FluidStack fluid : fluids) {
            if (fluid == null) continue;
            if (Materials.Grade7PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)) grade7 = true;
            if (Materials.Grade8PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)) grade8 = true;
        }
        if (grade7) modifier *= 0.9F;
        if (grade8) modifier *= 0.7F;
        return modifier;
    }

    private float getOpticalSpeedModifier(ArrayList<FluidStack> fluids) {
        float modifier = 1.0F;
        boolean grade5 = false;
        boolean grade6 = false;
        for (FluidStack fluid : fluids) {
            if (fluid == null) continue;
            if (Materials.Grade5PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)) grade5 = true;
            if (Materials.Grade6PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)) grade6 = true;
        }
        if (grade5) modifier *= 0.9F;
        if (grade6) modifier *= 0.7F;
        return modifier;
    }

    private OpticalTaskData createOpticalTaskData(ArrayList<FluidStack> fluids) {
        List<FluidStack> waters = getOpticalBoostingWaters(fluids);
        if (waters.size() < 2) return null;
        return new OpticalTaskData(waters.get(0), waters.get(1));
    }

    private List<FluidStack> getOpticalBoostingWaters(ArrayList<FluidStack> fluids) {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack fluid : fluids) {
            if (fluid == null || !isOpticalBoostingWater(fluid)) continue;
            boolean duplicate = false;
            for (FluidStack existing : result) {
                if (existing.isFluidEqual(fluid)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(fluid.copy());
        }
        return result;
    }

    private boolean isOpticalBoostingWater(FluidStack fluid) {
        return Materials.Grade3PurifiedWater.getFluid(1)
            .isFluidEqual(fluid)
            || Materials.Grade4PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)
            || Materials.Grade5PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)
            || Materials.Grade6PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)
            || Materials.Grade7PurifiedWater.getFluid(1)
                .isFluidEqual(fluid)
            || Materials.Grade8PurifiedWater.getFluid(1)
                .isFluidEqual(fluid);
    }

    private int[] calculateOverclockedTask(WorkThread thread, GTRecipe recipe, ArrayList<FluidStack> fluids,
        long powerRoom) {
        double duration = recipe.mDuration * getSpeedBonus() * getThreadSpeedModifier(thread, recipe, fluids);
        double eut = recipe.mEUt * getEuModifier() * getThreadEuModifier(thread, recipe, fluids);

        long energyTier = getInputVoltageTier();
        long recipeTier = GTUtility.getTier(recipe.mEUt);
        int maxOverclocks = (int) Math.max(0, energyTier - recipeTier);

        for (int i = 0; i < maxOverclocks; i++) {
            // Do not overclock below 5 seconds, and do not let a single task exceed remaining power.
            if (duration / 2.0 < 100) break;
            double nextEut = eut * 2.0;
            if (nextEut > powerRoom) break;
            duration /= 2.0;
            eut = nextEut;
        }

        return new int[] { (int) Math.ceil(eut), (int) Math.ceil(duration) };
    }

    private static ItemStack scaleItem(ItemStack stack, int parallel) {
        ItemStack copy = stack.copy();
        long amount = (long) copy.stackSize * parallel;
        copy.stackSize = (int) Math.min(Integer.MAX_VALUE, amount);
        return copy;
    }

    private static FluidStack scaleFluid(FluidStack stack, int parallel) {
        FluidStack copy = stack.copy();
        long amount = (long) copy.amount * parallel;
        copy.amount = (int) Math.min(Integer.MAX_VALUE, amount);
        return copy;
    }

    private static ItemStack[] scaleOutputItems(ItemStack[] outputs, int parallel) {
        if (outputs == null) return null;
        ItemStack[] result = new ItemStack[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            result[i] = outputs[i] == null ? null : scaleItem(outputs[i], parallel);
        }
        return result;
    }

    private static FluidStack[] scaleOutputFluids(FluidStack[] outputs, int parallel) {
        if (outputs == null) return null;
        FluidStack[] result = new FluidStack[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            result[i] = outputs[i] == null ? null : scaleFluid(outputs[i], parallel);
        }
        return result;
    }

    public int getThreadProgressTime(int index) {
        WorkThread thread = getThreadByIndex(index);
        return thread == null ? 0 : thread.getProgressTime();
    }

    public int getThreadMaxProgressTime(int index) {
        WorkThread thread = getThreadByIndex(index);
        return thread == null ? 0 : thread.getMaxProgressTime();
    }

    public int getThreadParallel(int index) {
        WorkThread thread = getThreadByIndex(index);
        return thread == null ? 0 : thread.getParallel();
    }

    public long getThreadEUt(int index) {
        WorkThread thread = getThreadByIndex(index);
        return thread == null ? 0 : thread.getEUt();
    }

    public String getThreadOutputText(int index) {
        WorkThread thread = getThreadByIndex(index);
        if (thread == null || !thread.isActive()
            || thread.getTasks()
                .isEmpty())
            return "";
        ItemStack[] outputs = thread.getTasks()
            .get(0)
            .getOutputItems();
        if (outputs == null || outputs.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (ItemStack output : outputs) {
            if (output == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("  ")
                .append(output.getDisplayName())
                .append(" x ")
                .append(output.stackSize);
        }
        return sb.toString();
    }

    public String getThreadStatusText() {
        StringBuilder sb = new StringBuilder();
        for (WorkThread thread : getThreads()) {
            if (!thread.isActive()) continue;
            sb.append(EnumChatFormatting.AQUA)
                .append(thread.getName())
                .append(EnumChatFormatting.RESET)
                .append(": ");
            if (thread.getCircuitNumber() == 4) {
                BoardTankState tank = getBoardTank(thread);
                if (tank != null) {
                    sb.append("Tanks ");
                    for (int type = 1; type <= 4; type++) {
                        BoardTank chamber = tank.getTank(type);
                        if (chamber != null && chamber.fluidAmount > 0) {
                            sb.append(type)
                                .append(':')
                                .append(chamber.fluidAmount)
                                .append('/')
                                .append(BoardTankState.CAPACITY)
                                .append(' ');
                        }
                    }
                }
            }
            for (RecipeTask task : thread.getTasks()) {
                sb.append(task.getProgressTime())
                    .append('/')
                    .append(task.getMaxProgressTime())
                    .append("t ")
                    .append(task.getParallel())
                    .append("x ")
                    .append(task.getEUt())
                    .append("EU/t  ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    protected void onTaskTick(WorkThread thread, RecipeTask task) {
        if (thread.getCircuitNumber() == 9 && task.getModuleData() instanceof OpticalTaskData data) {
            ArrayList<MTEHatchInput> hatches = getThreadFluidHatches(getThreadInputBuses(thread.getCircuitNumber()));
            for (FluidStack water : data.waters) {
                int amount = (int) (data.waterDiscount * water.amount);
                if (amount <= 0) continue;
                int drained = drainFluidFromHatches(hatches, new FluidStack(water.getFluid(), amount), false);
                if (drained < amount) {
                    stopMachine(ShutDownReasonRegistry.outOfFluid(water));
                }
            }
        }
    }

    @Override
    protected void onTaskFinished(WorkThread thread, RecipeTask task) {
        if (thread.getCircuitNumber() == 4) {
            BoardTankState tank = getBoardTank(thread);
            ItemStack[] outputs = task.getOutputItems();
            if (tank != null && outputs != null && outputs.length > 0 && outputs[0] != null) {
                int type = task.getModuleData() instanceof Integer integer ? integer : 1;
                tank.addProcessed(type, outputs[0].stackSize);
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("nsfAstralArray", astralArrayUnlocked);
        NBTTagList tankList = new NBTTagList();
        for (Map.Entry<String, BoardTankState> entry : boardTanks.entrySet()) {
            NBTTagCompound tankTag = new NBTTagCompound();
            tankTag.setString("thread", entry.getKey());
            entry.getValue()
                .writeToNBT(tankTag);
            tankList.appendTag(tankTag);
        }
        aNBT.setTag("nsfBoardTanks", tankList);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        astralArrayUnlocked = aNBT.getBoolean("nsfAstralArray");
        ensureAstralArrayThread();

        boardTanks.clear();
        if (!aNBT.hasKey("nsfBoardTanks")) return;
        NBTTagList tankList = aNBT.getTagList("nsfBoardTanks", 10);
        for (int i = 0; i < tankList.tagCount(); i++) {
            NBTTagCompound tankTag = tankList.getCompoundTagAt(i);
            BoardTankState tank = new BoardTankState();
            tank.readFromNBT(tankTag);
            boardTanks.put(tankTag.getString("thread"), tank);
        }
    }

    @Override
    public void setItemNBT(NBTTagCompound NBT) {
        if (astralArrayUnlocked) {
            NBT.setBoolean("nsfAstralArray", true);
        }
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("nsfAstralArray")) {
            tooltip.add(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("machine.nanoscale.tooltip.astral"));
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) {
            refreshInputBusCircuits();
        }
        if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == 0) {
            for (WorkThread thread : getThreads()) {
                if (thread.getCircuitNumber() != 4) continue;
                BoardTankState tank = getBoardTank(thread);
                if (tank == null) continue;
                tank.tick(this, thread);
            }
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        NBTTagList list = new NBTTagList();
        int index = 1;
        for (WorkThread thread : getThreads()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("name", thread.getName());
            entry.setInteger("index", index);
            entry.setBoolean("active", thread.isActive());
            if (thread.isActive()) {
                RecipeTask first = thread.getTasks()
                    .isEmpty() ? null
                        : thread.getTasks()
                            .get(0);
                entry.setInteger("progress", first == null ? 0 : first.getProgressTime());
                entry.setInteger("max", first == null ? 0 : first.getMaxProgressTime());
                entry.setLong("eut", thread.getEUt());
                entry.setInteger("parallel", thread.getParallel());

                if (first != null && first.getOutputItems() != null) {
                    ItemStack[] outputs = first.getOutputItems();
                    entry.setInteger("outCount", outputs.length);
                    for (int o = 0; o < outputs.length; o++) {
                        if (outputs[o] == null) continue;
                        entry.setString("outName" + o, outputs[o].getDisplayName());
                        entry.setInteger("outAmount" + o, outputs[o].stackSize);
                    }
                }
            }
            if (thread.getCircuitNumber() == 4) {
                BoardTankState tank = getBoardTank(thread);
                entry.setBoolean("board", true);
                entry.setInteger("boardTankCount", 4);
                for (int type = 1; type <= 4; type++) {
                    BoardTank chamber = tank == null ? null : tank.getTank(type);
                    entry.setInteger("tankFluid" + type, chamber == null ? 0 : chamber.fluidAmount);
                    entry.setInteger(
                        "tankImpurity" + type,
                        chamber == null ? 0 : (int) Math.round(chamber.getImpurityPercentage() * 100));
                    if (chamber != null && chamber.storedFluid != null) {
                        entry.setString(
                            "tankFluidName" + type,
                            chamber.storedFluid.getFluid()
                                .getName());
                        entry.setString(
                            "tankFluidIcon" + type,
                            TTRenderStack.create(GTUtility.getFluidDisplayStack(chamber.storedFluid, false), true));
                    }
                }
            }
            list.appendTag(entry);
            index++;
        }
        tag.setTag("threads", list);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (!tag.hasKey("threads")) return;

        NBTTagList list = tag.getTagList("threads", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int index = entry.getInteger("index");
            String name = getLocalizedThreadName(entry.getString("name"));
            boolean active = entry.getBoolean("active");

            if (!active) {
                currentTip.add(
                    EnumChatFormatting.GRAY + "#"
                        + index
                        + " "
                        + name
                        + ": "
                        + EnumChatFormatting.DARK_GRAY
                        + StatCollector.translateToLocal("GT5U.waila.machine.idle")
                        + EnumChatFormatting.RESET);
                if (entry.getBoolean("board")) {
                    addBoardTankTip(entry, currentTip);
                }
                continue;
            }

            currentTip.add(
                EnumChatFormatting.AQUA + "#"
                    + index
                    + " "
                    + name
                    + EnumChatFormatting.RESET
                    + EnumChatFormatting.GRAY
                    + " | "
                    + EnumChatFormatting.WHITE
                    + entry.getInteger("parallel")
                    + StatCollector.translateToLocal("machine.nanoscale.unit.parallel")
                    + " "
                    + EnumChatFormatting.RED
                    + entry.getLong("eut")
                    + StatCollector.translateToLocal("machine.nanoscale.unit.eut"));
            if (entry.getBoolean("board")) {
                addBoardTankTip(entry, currentTip);
            }
            currentTip.add(
                GTWaila.getMachineProgressString(true, true, entry.getInteger("max"), entry.getInteger("progress")));

            int outCount = entry.getInteger("outCount");
            for (int o = 0; o < outCount; o++) {
                if (!entry.hasKey("outName" + o)) continue;
                currentTip.add(
                    "  " + EnumChatFormatting.AQUA
                        + entry.getString("outName" + o)
                        + EnumChatFormatting.RESET
                        + " x "
                        + EnumChatFormatting.GOLD
                        + entry.getInteger("outAmount" + o));
            }
        }
    }

    private void addBoardTankTip(NBTTagCompound entry, List<String> currentTip) {
        String immersionLabel = StatCollector
            .translateToLocal("GT5U.tooltip.nac.module.boardprocessor.immersion_fluid");
        String impurityLabel = StatCollector.translateToLocal("GT5U.tooltip.nac.module.boardprocessor.impurity");
        String emptyLabel = StatCollector.translateToLocal("GT5U.tooltip.nac.module.boardprocessor.empty");

        int count = entry.hasKey("boardTankCount") ? entry.getInteger("boardTankCount") : 4;
        boolean any = false;
        for (int type = 1; type <= count; type++) {
            int amount = entry.getInteger("tankFluid" + type);
            if (amount <= 0) continue;
            any = true;
            StringBuilder line = new StringBuilder().append(EnumChatFormatting.GRAY)
                .append(immersionLabel)
                .append(' ')
                .append(type)
                .append(": ");
            if (entry.hasKey("tankFluidIcon" + type)) {
                line.append(entry.getString("tankFluidIcon" + type));
            }
            if (entry.hasKey("tankFluidName" + type)) {
                String internalName = entry.getString("tankFluidName" + type);
                net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(internalName);
                String displayName = fluid != null ? new FluidStack(fluid, 1).getLocalizedName() : internalName;
                line.append(EnumChatFormatting.AQUA)
                    .append(displayName)
                    .append(EnumChatFormatting.RESET);
                line.append(EnumChatFormatting.GRAY)
                    .append(": ")
                    .append(EnumChatFormatting.AQUA)
                    .append(amount)
                    .append(StatCollector.translateToLocal("machine.nanoscale.unit.liter"));
            }
            line.append(EnumChatFormatting.GRAY)
                .append(" | ")
                .append(impurityLabel)
                .append(": ")
                .append(EnumChatFormatting.RED)
                .append(entry.getInteger("tankImpurity" + type))
                .append(StatCollector.translateToLocal("machine.nanoscale.unit.percent"));
            currentTip.add(line.toString());
        }
        if (!any) {
            currentTip.add(
                EnumChatFormatting.GRAY + immersionLabel
                    + ": "
                    + EnumChatFormatting.DARK_GRAY
                    + emptyLabel
                    + EnumChatFormatting.RESET);
        }
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (astralArrayUnlocked) {
            ItemStack starArray = CustomItemList.astralArrayFabricator.get(1);
            addOutputPartial(starArray);
            if (starArray.stackSize <= 0) {
                astralArrayUnlocked = false;
                ensureAstralArrayThread();
                markDirty();
            }
            return true;
        }
        return false;
    }

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
        return new MTNanoScaleFoundry(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("machine.nanoscale.machinetype"))
            .addSeparator()
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.nanoscale.tooltip.0"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.nanoscale.tooltip.1"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.nanoscale.tooltip.2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("machine.nanoscale.special.header"))
            .addInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("machine.nanoscale.special.conversion"))
            .addInfo(EnumChatFormatting.DARK_GREEN + StatCollector.translateToLocal("machine.nanoscale.special.board"))
            .addInfo(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("machine.nanoscale.special.optical"))
            .addInfo(EnumChatFormatting.DARK_RED + StatCollector.translateToLocal("machine.nanoscale.special.etching"))
            .addSeparator()
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.nanoscale.mode.conversion"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "2",
                    StatCollector.translateToLocal("machine.nanoscale.mode.assemblymatrix")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "3",
                    StatCollector.translateToLocal("machine.nanoscale.mode.smd")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "4",
                    StatCollector.translateToLocal("machine.nanoscale.mode.board")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "5",
                    StatCollector.translateToLocal("machine.nanoscale.mode.etching")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "6",
                    StatCollector.translateToLocal("machine.nanoscale.mode.cutting")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "7",
                    StatCollector.translateToLocal("machine.nanoscale.mode.wiretracer")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "8",
                    StatCollector.translateToLocal("machine.nanoscale.mode.superconductor")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "9",
                    StatCollector.translateToLocal("machine.nanoscale.mode.optical")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "10",
                    StatCollector.translateToLocal("machine.nanoscale.mode.encasement")))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "machine.nanoscale.mode.line",
                    "11",
                    StatCollector.translateToLocal("machine.nanoscale.mode.biological")))
            .addSeparator()
            .addStructureInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("machine.nanoscale.tooltip.3"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("machine.nanoscale.tooltip.astral_enable"))
            .addInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("machine.nanoscale.tooltip.astral_wirecutter"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture casing = TextureFactory
            .of(Casings.NanochipMeshInterfaceCasing.getBlock(), Casings.NanochipMeshInterfaceCasing.getBlockMeta());
        if (side == facing) {
            ITexture overlay = TextureFactory
                .of(aActive ? OVERLAY_FRONT_NANOCHIP_ASSEMBLY_COMPLEX_ACTIVE : OVERLAY_FRONT_NANOCHIP_ASSEMBLY_COMPLEX);
            return new ITexture[] { casing, overlay };
        }
        return new ITexture[] { casing };
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return true;
    }

    @Override
    protected float getEuModifier() {
        // Global Nano-Scale Foundry buff: consume 50% of the original machine's EU.
        return 0.5F;
    }

    @Override
    protected float getSpeedBonus() {
        // Global Nano-Scale Foundry buff: 2x speed => 0.5 duration modifier.
        return 0.5F;
    }

    @Override
    public int getMaxParallelRecipes() {
        return Integer.MAX_VALUE;
    }

    private class OpticalTaskData {

        private final List<FluidStack> waters;
        private final float waterDiscount;

        OpticalTaskData(FluidStack first, FluidStack second) {
            this.waters = Arrays.asList(first, second);
            this.waterDiscount = calculateWaterDiscount(first, second);
        }

        private float calculateWaterDiscount(FluidStack a, FluidStack b) {
            float discount = 1.0F;
            if (Materials.Grade3PurifiedWater.getFluid(1)
                .isFluidEqual(a)
                || Materials.Grade3PurifiedWater.getFluid(1)
                    .isFluidEqual(b)) {
                discount *= 0.8F;
            }
            if (Materials.Grade4PurifiedWater.getFluid(1)
                .isFluidEqual(a)
                || Materials.Grade4PurifiedWater.getFluid(1)
                    .isFluidEqual(b)) {
                discount *= 0.6F;
            }
            return discount;
        }
    }

    private class BoardTankState {

        private static final int CAPACITY = 1_000_000;
        private static final int IMPURITY_THRESHOLD = 1000;
        private static final int IMPURITY_INCREASE = 100;

        private final Map<Integer, BoardTank> tanks = new LinkedHashMap<>();
        private int autoFlushPercentage = 100;

        BoardTankState() {
            for (int type = 1; type <= 4; type++) {
                tanks.put(type, new BoardTank(type));
            }
        }

        BoardTank getTank(int type) {
            return tanks.get(type);
        }

        boolean canProcess(GTRecipe recipe) {
            int type = recipe.getMetadataOrDefault(BoardProcessingModuleFluidKey.INSTANCE, 1);
            BoardTank tank = getTank(type);
            return tank != null && tank.canProcess();
        }

        float getEuMultiplier(GTRecipe recipe) {
            int type = recipe.getMetadataOrDefault(BoardProcessingModuleFluidKey.INSTANCE, 1);
            BoardTank tank = getTank(type);
            return tank == null ? 1.0F : tank.getEuMultiplier();
        }

        void addProcessed(int type, int amount) {
            BoardTank tank = getTank(type);
            if (tank != null) tank.addProcessed(amount);
        }

        void tick(MTNanoScaleFoundry machine, WorkThread thread) {
            for (BoardTank tank : tanks.values()) {
                if (tank.getImpurityPercentage() >= autoFlushPercentage / 100.0) {
                    tank.flush(machine);
                }
            }
            fillTanks(machine, thread);
        }

        void fillTanks(MTNanoScaleFoundry machine, WorkThread thread) {
            ArrayList<MTEHatchInput> hatches = machine.getBoardFluidHatches(thread);
            if (hatches.isEmpty()) return;

            for (FluidStack fluid : machine.getBoardFluidStacks(thread)) {
                if (fluid == null || !isLegalFluid(fluid.getFluid())) continue;
                BoardTank tank = getTank(fluidType(fluid.getFluid()));
                if (tank != null) tank.fill(hatches);
            }
        }

        private int fluidType(Fluid fluid) {
            if (fluid == Materials.IronIIIChloride.mFluid) return 1;
            if (fluid == Materials.GrowthMediumSterilized.mFluid) return 2;
            if (fluid == Materials.BioMediumSterilized.mFluid) return 3;
            if (fluid == Materials.PrismaticAcid.mFluid) return 4;
            return -1;
        }

        private boolean isLegalFluid(Fluid fluid) {
            return fluid != null
                && (fluid == Materials.IronIIIChloride.mFluid || fluid == Materials.GrowthMediumSterilized.mFluid
                    || fluid == Materials.BioMediumSterilized.mFluid
                    || fluid == Materials.PrismaticAcid.mFluid);
        }

        private FluidStack requiredFluid(int type) {
            return switch (type) {
                case 1 -> Materials.IronIIIChloride.getFluid(0);
                case 2 -> Materials.GrowthMediumSterilized.getFluid(0);
                case 3 -> Materials.BioMediumSterilized.getFluid(0);
                case 4 -> Materials.PrismaticAcid.getFluid(0);
                default -> null;
            };
        }

        void writeToNBT(NBTTagCompound tag) {
            tag.setInteger("autoFlush", autoFlushPercentage);
            NBTTagList list = new NBTTagList();
            for (BoardTank tank : tanks.values()) {
                NBTTagCompound tankTag = new NBTTagCompound();
                tankTag.setInteger("type", tank.type);
                tankTag.setInteger("fluidAmount", tank.fluidAmount);
                tankTag.setInteger("impurityAmount", tank.impurityAmount);
                tankTag.setInteger("processedItems", tank.processedItems);
                if (tank.storedFluid != null) {
                    tankTag.setTag("stored", tank.storedFluid.writeToNBT(new NBTTagCompound()));
                }
                if (tank.impurityFluid != null) {
                    tankTag.setTag("impurity", tank.impurityFluid.writeToNBT(new NBTTagCompound()));
                }
                list.appendTag(tankTag);
            }
            tag.setTag("tanks", list);
        }

        void readFromNBT(NBTTagCompound tag) {
            autoFlushPercentage = tag.getInteger("autoFlush");
            NBTTagList list = tag.getTagList("tanks", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tankTag = list.getCompoundTagAt(i);
                BoardTank tank = getTank(tankTag.getInteger("type"));
                if (tank == null) continue;
                tank.fluidAmount = tankTag.getInteger("fluidAmount");
                tank.impurityAmount = tankTag.getInteger("impurityAmount");
                tank.processedItems = tankTag.getInteger("processedItems");
                tank.storedFluid = tankTag.hasKey("stored")
                    ? FluidStack.loadFluidStackFromNBT(tankTag.getCompoundTag("stored"))
                    : null;
                tank.impurityFluid = tankTag.hasKey("impurity")
                    ? FluidStack.loadFluidStackFromNBT(tankTag.getCompoundTag("impurity"))
                    : null;
            }
        }
    }

    private class BoardTank {

        private final int type;
        private FluidStack storedFluid;
        private FluidStack impurityFluid;
        private int fluidAmount;
        private int impurityAmount;
        private int processedItems;

        BoardTank(int type) {
            this.type = type;
        }

        boolean canProcess() {
            if (storedFluid == null || fluidAmount <= 0) return false;
            if (getFillPercentage() < 0.5) return false;
            if (getImpurityPercentage() >= 1.0) return false;
            FluidStack required = requiredFluid(type);
            return required != null && storedFluid.isFluidEqual(required);
        }

        float getEuMultiplier() {
            double impurity = getImpurityPercentage();
            if (impurity <= 0.15) {
                return (float) (0.7 + impurity * 2);
            } else if (impurity >= 0.65) {
                return (float) (1 + 2 * (impurity - 0.65));
            }
            return 1.0F;
        }

        double getFillPercentage() {
            return (double) fluidAmount / BoardTankState.CAPACITY;
        }

        double getImpurityPercentage() {
            if (fluidAmount <= 0) return 0;
            return (double) impurityAmount / fluidAmount;
        }

        void addProcessed(int amount) {
            processedItems += amount;
            while (processedItems >= BoardTankState.IMPURITY_THRESHOLD && storedFluid != null) {
                processedItems -= BoardTankState.IMPURITY_THRESHOLD;
                int increase = (int) Math.min(
                    BoardTankState.IMPURITY_INCREASE * (1 / Math.pow(getFillPercentage(), 1.5)),
                    fluidAmount - impurityAmount);
                impurityAmount += increase;
                if (impurityFluid != null) {
                    impurityFluid.amount = impurityAmount;
                }
            }
        }

        void fill(ArrayList<MTEHatchInput> hatches) {
            if (storedFluid == null) {
                int drained = drain(hatches, new FluidStack(requiredFluid(type), BoardTankState.CAPACITY), false);
                if (drained > 0) {
                    storedFluid = new FluidStack(requiredFluid(type), drained);
                    fluidAmount = drained;
                    updateImpurityFluid();
                }
            } else {
                int missing = BoardTankState.CAPACITY - fluidAmount;
                if (missing <= 0) return;
                int drained = drain(hatches, new FluidStack(storedFluid.getFluid(), missing), false);
                if (drained > 0) {
                    storedFluid.amount += drained;
                    fluidAmount = storedFluid.amount;
                }
            }
        }

        void flush(MTNanoScaleFoundry machine) {
            if (storedFluid == null || impurityFluid == null) return;
            impurityAmount = fluidAmount;
            FluidStack toFlush = new FluidStack(
                impurityFluid.getFluid(),
                impurityFluid.getFluid() == Materials.PrismaticGas.mFluid ? impurityAmount / 4 : impurityAmount);
            machine.addOutputPartial(toFlush);
            storedFluid = null;
            impurityFluid = null;
            fluidAmount = 0;
            impurityAmount = 0;
            processedItems = 0;
        }

        private int drain(ArrayList<MTEHatchInput> hatches, FluidStack request, boolean simulate) {
            if (request == null || request.amount <= 0) return 0;
            int drainedTotal = 0;
            int remaining = request.amount;
            for (MTEHatchInput hatch : hatches) {
                FluidStack toDrain = new FluidStack(request.getFluid(), remaining);
                FluidStack drained = hatch.drain(ForgeDirection.UNKNOWN, toDrain, !simulate);
                if (drained == null || drained.amount <= 0) continue;
                drainedTotal += drained.amount;
                remaining -= drained.amount;
                if (remaining <= 0) break;
            }
            return drainedTotal;
        }

        private void updateImpurityFluid() {
            if (storedFluid == null) return;
            if (storedFluid.isFluidEqual(Materials.IronIIIChloride.getFluid(0))) {
                impurityFluid = GGMaterial.ferrousChloride.getFluidOrGas(0);
            } else if (storedFluid.isFluidEqual(Materials.GrowthMediumSterilized.getFluid(0))) {
                impurityFluid = Materials.GrowthMediumRaw.getFluid(0);
            } else if (storedFluid.isFluidEqual(Materials.BioMediumSterilized.getFluid(0))) {
                impurityFluid = Materials.BioMediumRaw.getFluid(0);
            } else if (storedFluid.isFluidEqual(Materials.PrismaticAcid.getFluid(0))) {
                impurityFluid = Materials.PrismaticGas.getFluid(0);
            }
            if (impurityFluid != null) impurityFluid.amount = impurityAmount;
        }

        private FluidStack requiredFluid(int type) {
            return switch (type) {
                case 1 -> Materials.IronIIIChloride.getFluid(0);
                case 2 -> Materials.GrowthMediumSterilized.getFluid(0);
                case 3 -> Materials.BioMediumSterilized.getFluid(0);
                case 4 -> Materials.PrismaticAcid.getFluid(0);
                default -> null;
            };
        }
    }
}
