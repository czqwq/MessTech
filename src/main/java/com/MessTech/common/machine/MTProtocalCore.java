package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.BlockBeacon;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.MTProtocalCoreGui;
import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.gtnhlib.util.ServerPlayerUtils;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ganymedes01.etfuturum.ModBlocks;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.WirelessNetworkManager;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;
import tectech.thing.metaTileEntity.hatch.MTEHatchWirelessMulti;
import vazkii.botania.common.block.ModFluffBlocks;

/**
 * 协议核心 / Protocal Core: the beacon-tower multiblock (16 x 52 x 16) that turns the placer's GT5U wireless EU
 * network into wired power for a whole team.
 * <p>
 * While the machine is switched on and the structure is complete it scans the loaded worlds every
 * {@link #TARGET_REFRESH_INTERVAL} ticks for wired energy hatches ({@link MTEHatchEnergy}) and laser / multi-amp energy
 * hatches ({@link MTEHatchEnergyMulti}, wireless multi hatches excluded) owned by a member of the placer's GTNHLib
 * team - or only by the placer when they are not in a team. Every tick each found hatch is charged straight from the
 * placer's wireless EU network ({@link WirelessNetworkManager}) with up to its own intake voltage times rated amperes,
 * 1:1. Energy hatch buffers are tiny ({@code 512 + V * 8} for a normal energy hatch), so this has to run per tick like
 * a cable instead of in bulk.
 * <p>
 * The machine has no energy hatch requirement and no running cost of its own: it is only the wireless-to-wired
 * converter. The wireless network balance must cover the delivered EU; while it does not, the output drops to 0 and
 * comes back automatically once the network is charged again.
 * <p>
 * The structure is the original beacon-tower dump; the block legend of {@code structure_string} is on the field
 * itself. This class does not use a recipe map - the 20 tick GT round is only the delivery clock.
 */
public class MTProtocalCore extends MTMultiMachineBase<MTProtocalCore> implements ISurvivalConstructable {

    // region Wireless distribution constants
    /** Ticks between two scans of the loaded worlds for target energy hatches. */
    private static final int TARGET_REFRESH_INTERVAL = 100;
    /** Length of one GT processing round; it is only the clock that keeps the per-tick delivery running. */
    private static final int ROUND_TICKS = 20;
    /** Ticks between two refreshes of the values shown in the GUI (wireless balance / totals). */
    private static final int DISPLAY_REFRESH_INTERVAL = 20;
    // endregion

    // region Wireless distribution state
    /** Placer of the machine; also the key of the GT5U wireless EU network the machine drains. */
    private UUID ownerUUID;
    /** UUIDs whose energy hatches are charged: the owner's GTNHLib team, or only the owner when there is no team. */
    private final Set<UUID> teamMemberUUIDs = new HashSet<>();
    /** Energy hatches / laser hatches found by the last world scan (server side only). */
    private final List<MetaTileEntity> targetHatches = new ArrayList<>();
    /** Internal tick counter; the GT {@code aTick} argument is not used for the intervals. */
    private int internalTicks;
    /** EU actually handed to the target hatches during the last tick. */
    private long outputEUtPerTick;
    /** Running total of the EU handed to the target hatches since the machine was placed. */
    private BigInteger totalDeliveredEUt = BigInteger.ZERO;
    /** Cached display strings, refreshed server side every {@link #DISPLAY_REFRESH_INTERVAL} ticks. */
    private String networkBalanceText = "0";
    private String totalDeliveredText = "0";
    private String displayTeamName = "";
    private String displayOwnerName = "";
    // endregion

    // region Structure piece offsets
    // '~' (controller): char index 8 of X, outer array index 49 of Y, inner string index 4 of Z.
    private static final int HORIZONTAL_OFFSET = 8;
    private static final int VERTICAL_OFFSET = 49;
    private static final int DEPTH_OFFSET = 4;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // Block legend of the structure dump (the transposed scan below):
    // A = Solid Steel Machine Casing, B = iron block, C = cyan hardened clay, D = lime wool, E = black wool,
    // F = Et Futurum chain, G = Et Futurum dark oak trapdoor, H = Et Futurum deepslate brick wall,
    // I = Et Futurum deepslate bricks, J = Et Futurum end rod, K = Et Futurum granite wall (meta 1 = diorite wall),
    // L = Et Futurum iron trapdoor, M = Et Futurum smooth stone, N = Botania prismarine,
    // O = Botania prismarine stairs, P = quartz block, Q = light blue stained glass, R = lime stained glass,
    // S / T / U = Botania stone, 4 = polished andesite / 5 = polished basalt / 6 = polished diorite,
    // V = Botania diorite brick stairs, W = vanilla stone brick slab,
    // X = the hatch slots flanking the controller (lapis block as their fill),
    // Y = beacons (any block that carries a beacon tile entity).
    // spotless:off
    private static final String[][] structure_string = new String[][]{
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        H       ","       IHI      ","       HHH      ","       HHH      ","       IHI      ","        H       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","        F       ","       I I      ","       K K      ","       K K      ","       I I      ","        F       ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      J F J     ","      HI IH     ","      KK KK     ","      KK KK     ","      HI IH     ","      J F J     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      J F J     ","      HI IH     ","      K   K     ","      K   K     ","      HI IH     ","      J F J     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      J F J     ","      HI IH     ","      K   K     ","      K   K     ","      HI IH     ","      J F J     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      J F J     ","      HI IH     ","      K   K     ","      K   K     ","      HI IH     ","      J F J     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      J F J     ","      HI IH     ","      K   K     ","      K   K     ","      HI IH     ","      J F J     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      HI IH     ","      K   K     ","      K   K     ","      HI IH     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      K   K     ","      K   K     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      V   V     ","      V   V     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      V   V     ","      V   V     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      V   V     ","      V   V     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      V   V     ","      V   V     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      V   V     ","      V   V     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      V   V     ","      V   V     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","      K F K     ","      II II     ","      U   U     ","      U   U     ","      II II     ","      K F K     ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","     L     L    ","     LK F KL    ","     LII IIL    ","     LU   UL    ","     LU   UL    ","     LII IIL    ","     LK F KL    ","     L     L    ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    VU     UV   ","    VIK F KIV   ","     UII IIU    ","     M     M    ","     M     M    ","     UII IIU    ","    VIK F KIV   ","    VU     UV   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UU     UU   ","    UIK F KIU   ","     UII IIU    ","     VI    V    ","     VI    V    ","     UII IIU    ","    UIK F KIU   ","    UU     UU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UU     UU   ","    UIK F KIU   ","     UII IIU    ","     VI   IV    ","     VI   IV    ","     UII IIU    ","    UIK F KIU   ","    UU     UU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UU     UU   ","    UIK F KIU   ","     UII IIU    ","     VI   IV    ","     VI   IV    ","     UII IIU    ","    UIK F KIU   ","    UU     UU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UU     UU   ","    UIK F KIU   ","     UII IIU    ","     VI   IV    ","     VI   IV    ","     UII IIU    ","    UIK F KIU   ","    UU     UU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UU     UU   ","    UIK F KIU   ","     UII IIU    ","     VI   IV    ","     VI   IV    ","     UII IIU    ","    UIK F KIU   ","    UU     UU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UV     VU   ","    UIK F KIU   ","     UII IIU    ","     UI   IU    ","     UI   IU    ","     UII IIU    ","    UIK F KIU   ","    UV     VU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UV     VU   ","    UIK F KIU   ","     UII IIU    ","     UI   IU    ","     UI   IU    ","     UII IIU    ","    UIK F KIU   ","    UV     VU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    UUWWWWWUU   ","    KI  K  IK   ","     SIIIIIS    ","     SI   IS    ","     SI   IS    ","     SIIIIIS    ","    KI  K  IK   ","    UUWWWWWUU   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    SSIDDDISS   ","    SWI   IWS   ","    SW     WS   ","    SWIRRRIWS   ","    SWIRRRIWS   ","    SW     WS   ","    SW     WS   ","    SSI   ISS   ","                ","                ","                ","                ","                "},
        {"                ","                ","    UUUDDDUUU   ","   UNNN   NNNU  ","   UNNO   ONNU  ","   DNO     OND  ","   DN TYYYT  D  ","   D  TYYYT  D  ","   D         D  ","   DNO     OND  ","   UNNN   NNNU  ","    UUU   UUU   ","                ","                ","                ","                "},
        {"                ","                ","                ","       CCC      ","      AEEEA     ","    KABBBBBAK   ","    BEBBBBBEB   ","    BEBBBBBEB   ","    BEBBBBBEB   ","    KABBBBBAK   ","      AEEEA     ","      KCCCK     ","                ","                ","                ","                "},
        {"                ","                ","                ","      KCCCK     ","     AEEEEEA    ","     EBBBBBE    ","    BEBBBBBEB   ","    BEBBBBBEB   ","    BEBBBBBEB   ","     EBBBBBE    ","     AEEEEEA    ","       CCC      ","                ","                ","                ","                "},
        {"                ","                ","                ","       RRR      ","    PPAAAAAPP   ","     ABBBBBA    ","     ABBBBBA    ","     ABBBBBA    ","     ABBBBBA    ","     ABBBBBA    ","    PPAAAAAPP   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","    GNOOOOONG   ","     OBBBBBO    ","     OBBBBBO    ","     OBBBBBO    ","     OBBBBBO    ","     OBBBBBO    ","    GNOOOOONG   ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","    Q       Q   ","     KAX~XAK    ","     ABBBBBA    ","     ABBBBBA    ","     ABBBBBA    ","     ABBBBBA    ","     ABBBBBA    ","     KAAAAAK    ","    Q       Q   ","                ","                ","                ","                "},
        {"                ","                ","                ","    K       K   ","     AAAAAAA    ","     AAAAAAA    ","     AAAAAAA    ","     AAAAAAA    ","     AAAAAAA    ","     AAAAAAA    ","     AAAAAAA    ","    K       K   ","                ","                ","                ","                "},
        {"                ","                ","    NNNOOONNN   ","   NNNNNNNNNNN  ","   NNNNNNNNNNN  ","   NNNNNNNNNNN  ","   ONNNNNNNNNO  ","   ONNNNNNNNNO  ","   ONNNNNNNNNO  ","   NNNNNNNNNNN  ","   NNNNNNNNNNN  ","   NNNNNNNNNNN  ","    NNNOOONNN   ","                ","                ","                "},
    };
    // spotless:on

    private static final IStructureDefinition<MTProtocalCore> STRUCTURE_DEFINITION = StructureDefinition
        .<MTProtocalCore>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        // A: Solid Steel Machine Casing - the shell block of the tower, and the fill of every non-hatch position.
        .addElement('A', Casings.SolidSteelMachineCasing.asElement())
        // B-E: the vanilla filling blocks of the dump.
        .addElement('B', ofBlock(Blocks.iron_block, 0))
        .addElement('C', ofBlock(Blocks.stained_hardened_clay, 9))
        .addElement('D', ofBlock(Blocks.wool, 5))
        .addElement('E', ofBlock(Blocks.wool, 15))
        // F-M: Et Futurum's blocks, exactly the block+meta pairs of the dump.
        .addElement('F', ofBlock(ModBlocks.CHAIN.get(), 0))
        .addElement('G', ofBlock(ModBlocks.TRAPDOOR_DARK_OAK.get(), 0))
        .addElement('H', ofBlock(ModBlocks.DEEPSLATE_BRICK_WALL.get(), 0))
        .addElement('I', ofBlock(ModBlocks.DEEPSLATE_BRICKS.get(), 0))
        .addElement('J', ofBlock(ModBlocks.END_ROD.get(), 0))
        // STONE_WALL_2 is Et Futurum's granite/diorite/andesite wall block: meta 1 is the diorite wall.
        .addElement('K', ofBlock(ModBlocks.STONE_WALL_2.get(), 1))
        .addElement('L', ofBlock(ModBlocks.IRON_TRAPDOOR.get(), 0))
        .addElement('M', ofBlock(ModBlocks.SMOOTH_STONE.get(), 0))
        // N-V: Botania's 1.8 decorative blocks (their registry names carry no modid prefix in the dump). The prismarine
        // block lives on Botania's ModBlocks, passed fully qualified because Et Futurum's ModBlocks is imported above.
        .addElement('N', ofBlock(vazkii.botania.common.block.ModBlocks.prismarine, 0))
        .addElement('O', ofBlock(ModFluffBlocks.prismarineStairs, 0))
        .addElement('P', ofBlock(Blocks.quartz_block, 0))
        .addElement('Q', ofBlock(Blocks.stained_glass, 3))
        .addElement('R', ofBlock(Blocks.stained_glass, 5))
        .addElement('S', ofBlock(ModFluffBlocks.stone, 4))
        .addElement('T', ofBlock(ModFluffBlocks.stone, 5))
        .addElement('U', ofBlock(ModFluffBlocks.stone, 6))
        // stoneStairs[6] is the meta-10 stone variant, i.e. the diorite brick stairs of the dump.
        .addElement('V', ofBlock(ModFluffBlocks.stoneStairs[6], 0))
        .addElement('W', ofBlock(Blocks.stone_slab, 5))
        // X: the two hatch slots flanking the controller - any of these hatches at either slot, lapis block otherwise.
        .addElement(
            'X',
            HatchElementBuilder.<MTProtocalCore>builder()
                .anyOf(HatchElement.Energy, HatchElement.InputBus, HatchElement.OutputBus)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(1)
                .buildAndChain(ofBlock(Blocks.lapis_block, 0)))
        // Y: the six beacons in the tower. Accept anything that is a beacon block plus a beacon tile entity, so the
        // vanilla beacon, Et Futurum's New Beacon (BlockNewBeacon / TileEntityNewBeacon) and other mods' beacons work
        // alike - the pattern used by 123Technology's OTEMegaNineInOne.
        .addElement('Y', new IStructureElement<MTProtocalCore>() {

            private final IStructureElement.BlocksToPlace cached = IStructureElement.BlocksToPlace
                .create(Blocks.beacon, 0);

            @Override
            public boolean check(MTProtocalCore machine, World world, int x, int y, int z) {
                // Only care that it is a beacon: both the block and the tile entity have to match.
                return world.getBlock(x, y, z) instanceof BlockBeacon
                    && world.getTileEntity(x, y, z) instanceof TileEntityBeacon;
            }

            @Override
            public boolean couldBeValid(MTProtocalCore machine, World world, int x, int y, int z, ItemStack trigger) {
                // check() is side effect free, so it can be reused here.
                return check(machine, world, x, y, z);
            }

            @Override
            public boolean spawnHint(MTProtocalCore machine, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, Blocks.beacon, 0);
                return true;
            }

            @Override
            public boolean placeBlock(MTProtocalCore machine, World world, int x, int y, int z, ItemStack trigger) {
                world.setBlock(x, y, z, Blocks.beacon, 0, 2);
                if (check(machine, world, x, y, z)) return true;
                return world.setBlockToAir(x, y, z);
            }

            @Override
            public IStructureElement.BlocksToPlace getBlocksToPlace(MTProtocalCore machine, World world, int x, int y,
                int z, ItemStack trigger, AutoPlaceEnvironment env) {
                return cached;
            }
        })
        .build();
    // endregion

    public MTProtocalCore(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTProtocalCore(String aName) {
        super(aName);
    }

    @Override
    public IStructureDefinition<MTProtocalCore> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    /** Read-only status screen: the default multiblock layout plus the wireless distribution readout. */
    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTProtocalCoreGui(this);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // Structure only: the machine draws its power from the wireless network, so no energy hatch is required.
        checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors);
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
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTProtocalCore(mName);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // Program machine: no recipe map, the 20 tick GT round is only the delivery clock.
        return null;
    }

    // region Tooltip
    /** House style: one {@code addInfo(translate(key))} per line, colour codes live in the lang files. */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.protocalcore.machinetype"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.desc"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.protocalcore.tooltip.power.header"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.power.wireless"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.power.empty"))
            .addSeparator()
            .addInfo(translateToLocal("machine.protocalcore.tooltip.targets.header"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.targets.scope"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.targets.rate"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.targets.loaded"))
            .addSeparator()
            .addInfo(translateToLocal("machine.protocalcore.tooltip.activation"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.details"))
            .addInfo(translateToLocal("machine.protocalcore.tooltip.details.hint"))
            .addStructureInfo(translateToLocal("machine.protocalcore.structure"))
            .toolTipFinisher();
        return tt;
    }
    // endregion

    // region Rendering
    /** Front-face overlay of the controller. Registered in {@link #registerIcons(IIconRegister)} (client only). */
    @SideOnly(Side.CLIENT)
    private static IIconContainer PROTOCOL_FACE;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        PROTOCOL_FACE = gregtech.api.enums.Textures.BlockIcons
            .custom("messtech", "iconsets/controllerFaces/ProtocolCoreOverlay");
        super.registerIcons(register);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        // Solid steel machine casing everywhere (the tower's shell block); the protocol overlay only on the front.
        ITexture casing = TextureFactory
            .of(Casings.SolidSteelMachineCasing.getBlock(), Casings.SolidSteelMachineCasing.getBlockMeta());
        if (side == facing) {
            return new ITexture[] { casing, TextureFactory.of(PROTOCOL_FACE) };
        }
        return new ITexture[] { casing };
    }
    // endregion

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

    // region Tick hooks
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        resolveOwner();
        refreshTeamMembers();
        refreshTargets();
        refreshDisplayCache();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        internalTicks++;
        if (internalTicks % DISPLAY_REFRESH_INTERVAL == 0) refreshDisplayCache();
        if (internalTicks % TARGET_REFRESH_INTERVAL == 0) {
            refreshTeamMembers();
            refreshTargets();
            // 2.9's multiblock recipe checks are event driven: nudge the controller while it idles so newly placed
            // target hatches (or a structure that just formed) are picked up without toggling the machine.
            if (mMaxProgresstime <= 0 && mMachine && aBaseMetaTileEntity.isAllowedToWork()) {
                scheduleRecipeCheckImmediate();
            }
        }
    }
    // endregion

    // region Processing (the GT round is only the delivery clock)
    /**
     * The machine has no recipes: a round simply keeps {@link #onRunningTick} coming for {@link #ROUND_TICKS} ticks.
     * The only failure states are a missing owner and no loaded target hatch - {@link #onPostTick} pushes a fresh recipe
     * check every {@link #TARGET_REFRESH_INTERVAL} ticks while the machine idles, so both recover on their own.
     */
    @Override
    public CheckRecipeResult checkProcessing() {
        if (!getBaseMetaTileEntity().isServerSide()) return CheckRecipeResultRegistry.NO_RECIPE;
        if (ownerUUID == null) resolveOwner();
        if (ownerUUID == null) return SimpleCheckRecipeResult.ofFailure("protocalcore_no_owner");
        if (targetHatches.isEmpty()) {
            refreshTeamMembers();
            refreshTargets();
        }
        if (targetHatches.isEmpty()) return SimpleCheckRecipeResult.ofFailure("protocalcore_no_targets");

        mEUt = 0;
        lEUt = 0;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mProgresstime = 0;
        mMaxProgresstime = ROUND_TICKS;
        mOutputItems = null;
        mOutputFluids = null;
        mPollution = 0;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) deliverEnergy();
        return true;
    }
    // endregion

    // region Wireless distribution
    /** Resolves and registers the placer's UUID for the GT5U wireless network (server side). */
    private void resolveOwner() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        UUID owner = base.getOwnerUuid();
        if (owner == null) return;
        ownerUUID = owner;
        WirelessNetworkManager.strongCheckOrAddUser(owner);
    }

    /** @return the GTNHLib team of the given player, or {@code null} when the player is not in one. */
    private static Team findTeam(UUID player) {
        for (Team team : TeamManager.getTeamMap()
            .values()) {
            if (team.isMember(player)) return team;
        }
        return null;
    }

    /** Rebuilds the set of UUIDs whose energy hatches are target of the machine (server side). */
    private void refreshTeamMembers() {
        teamMemberUUIDs.clear();
        if (ownerUUID == null) resolveOwner();
        if (ownerUUID == null) {
            displayOwnerName = "";
            displayTeamName = "";
            return;
        }
        String ownerName = ServerPlayerUtils.getPlayerName(ownerUUID);
        displayOwnerName = ownerName == null ? "" : ownerName;
        Team team = findTeam(ownerUUID);
        if (team != null) {
            displayTeamName = team.getTeamName();
            teamMemberUUIDs.addAll(team.getMembers());
        } else {
            displayTeamName = "";
            teamMemberUUIDs.add(ownerUUID);
        }
    }

    /**
     * Scans every loaded world for wired energy hatches / laser hatches owned by a team member and caches them. Only
     * loaded chunks are scanned - hatches in unloaded areas naturally start/stop being supplied with their chunk.
     */
    private void refreshTargets() {
        targetHatches.clear();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || ownerUUID == null || teamMemberUUIDs.isEmpty()) return;
        for (World world : server.worldServers) {
            if (world == null) continue;
            for (Object entry : world.loadedTileEntityList) {
                if (!(entry instanceof IGregTechTileEntity base)) continue;
                if (!(base.getMetaTileEntity() instanceof MetaTileEntity meta)) continue;
                if (!isTargetHatch(meta)) continue;
                UUID hatchOwner = base.getOwnerUuid();
                if (hatchOwner == null || !teamMemberUUIDs.contains(hatchOwner)) continue;
                targetHatches.add(meta);
            }
        }
    }

    private static boolean isTargetHatch(MetaTileEntity meta) {
        // Wired energy hatches and laser / multi-amp energy hatches; the wireless multi hatch pulls from the network
        // itself, so handing it energy as well would double up.
        if (meta instanceof MTEHatchWirelessMulti) return false;
        return meta instanceof MTEHatchEnergy || meta instanceof MTEHatchEnergyMulti;
    }

    /**
     * One delivery tick: charge every cached hatch up to its own intake (voltage times rated amperes, or the free buffer
     * space if that is smaller), draining the same amount from the placer's wireless network 1:1. If the network does
     * not hold enough EU the delivery is trimmed to the available balance for this tick.
     */
    private void deliverEnergy() {
        if (ownerUUID == null || targetHatches.isEmpty()) {
            outputEUtPerTick = 0L;
            return;
        }

        long needed = 0L;
        for (int i = 0; i < targetHatches.size(); i++) {
            MetaTileEntity hatch = targetHatches.get(i);
            if (!hatch.isValid()) {
                targetHatches.remove(i--);
                continue;
            }
            long amount = potentialCharge(hatch);
            if (amount <= 0L) continue;
            needed = needed > Long.MAX_VALUE - amount ? Long.MAX_VALUE : needed + amount;
        }
        if (needed <= 0L) {
            outputEUtPerTick = 0L;
            return;
        }

        BigInteger available = WirelessNetworkManager.getUserEU(ownerUUID);
        long budget = available.bitLength() >= 63 ? Long.MAX_VALUE : available.longValue();
        long deliverable = Math.min(needed, budget);
        if (deliverable <= 0L) {
            outputEUtPerTick = 0L;
            return;
        }
        if (!WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, -deliverable)) {
            outputEUtPerTick = 0L;
            return;
        }

        long delivered = 0L;
        for (MetaTileEntity hatch : targetHatches) {
            if (delivered >= deliverable) break;
            long amount = potentialCharge(hatch);
            if (amount <= 0L) continue;
            amount = Math.min(amount, deliverable - delivered);
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base == null) continue;
            if (base.increaseStoredEnergyUnits(amount, true)) delivered += amount;
        }
        outputEUtPerTick = delivered;
        totalDeliveredEUt = totalDeliveredEUt.add(BigInteger.valueOf(delivered));
    }

    /** @return how much EU the hatch can take this tick: its free buffer space capped by its own rated intake. */
    private static long potentialCharge(MetaTileEntity hatch) {
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null) return 0L;
        long free = base.getEUCapacity() - base.getStoredEU();
        if (free <= 0L) return 0L;
        long voltage = base.getInputVoltage();
        long amperes = base.getInputAmperage();
        if (voltage <= 0L || amperes <= 0L) return 0L;
        long intake;
        try {
            intake = Math.multiplyExact(voltage, amperes);
        } catch (ArithmeticException overflow) {
            intake = Long.MAX_VALUE;
        }
        return Math.min(free, intake);
    }

    /** Refreshes the cached strings the GUI and the scanner show (server side). */
    private void refreshDisplayCache() {
        if (ownerUUID == null) {
            networkBalanceText = "0";
            totalDeliveredText = "0";
            return;
        }
        networkBalanceText = NumberFormatUtil.formatNumber(WirelessNetworkManager.getUserEU(ownerUUID));
        totalDeliveredText = NumberFormatUtil.formatNumber(totalDeliveredEUt);
    }
    // endregion

    // region GUI / scanner values
    public int getTargetHatchCount() {
        return targetHatches.size();
    }

    public long getOutputEUtPerTick() {
        return outputEUtPerTick;
    }

    public String getNetworkBalanceText() {
        return networkBalanceText;
    }

    public String getTotalDeliveredText() {
        return totalDeliveredText;
    }

    /** @return the cached GTNHLib team name of the placer, empty when the player has no team. */
    public String getTeamNameForGui() {
        return displayTeamName;
    }

    /** @return the cached name of the placer, empty when unknown. */
    public String getOwnerNameForGui() {
        return displayOwnerName;
    }
    // endregion

    // region Info (scanner / WAILA)
    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        boolean active = getBaseMetaTileEntity() != null && getBaseMetaTileEntity()
            .isActive();
        return new String[] {
            translateToLocalFormatted(
                "machine.protocalcore.info.status",
                translateToLocal(
                    active ? "machine.protocalcore.info.running" : "machine.protocalcore.info.idle")),
            translateToLocalFormatted(
                "machine.protocalcore.info.team",
                displayTeamName.isEmpty() ? translateToLocal("machine.protocalcore.gui.team.none")
                    : displayTeamName),
            translateToLocalFormatted(
                "machine.protocalcore.info.owner",
                displayOwnerName.isEmpty() ? translateToLocal("machine.protocalcore.gui.unknown")
                    : displayOwnerName),
            translateToLocalFormatted("machine.protocalcore.info.targets", getTargetHatchCount()),
            translateToLocalFormatted("machine.protocalcore.info.balance", networkBalanceText),
            translateToLocalFormatted("machine.protocalcore.info.output", outputEUtPerTick),
            translateToLocalFormatted("machine.protocalcore.info.total", totalDeliveredText) };
    }
    // endregion
}
