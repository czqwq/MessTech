package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.BlockBeacon;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.gtnhlib.util.ServerPlayerUtils;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ganymedes01.etfuturum.ModBlocks;
import gregtech.api.casing.Casings;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.tileentities.machines.RecipeCheckReason;
import vazkii.botania.common.block.ModFluffBlocks;

/**
 * 协议信标 / Protocol Beacon - an <b>unregistered scrap copy</b> of {@code MTProtocolCore} (协议核心), kept as a draft
 * for a future variant. It is intentionally not added to {@code MTItemList} / {@code MTMachineLoader}, so it has no
 * item, no machine id and never shows up in game; the current MTProtocolCore feature code is untouched by this copy.
 * <p>
 * The effect-program part is a full copy of the original: a round applies every enabled level-1 potion effect to the
 * online members of the bound player's (machine owner's) team and refreshes it while the machine runs. The round length
 * is {@link #BASE_ROUND_SECONDS} seconds, doubled per enabled effect and halved per energy hatch level above EV (lossy
 * overclocking: EU/t grows four-fold per tier while the round only halves); the running cost is 1 A at the highest
 * hatch tier, i.e. {@code GTValues.V[tier]} EU/t. The per-effect enable switch plus the player name blacklist /
 * whitelist are stored in NBT.
 * <p>
 * Unlike the original it does not override {@code getGui()}: {@code MTProtocolCoreGui} is typed against
 * {@code MTProtocolCore}, so this draft currently uses the plain multiblock GUI. Wire up a GUI (and its own lang keys)
 * when the variant is actually registered.
 * <p>
 * The structure is the same beacon-tower dump; the block legend of {@code structure_string} is on the field itself.
 */
public class MTProtocolBeacon extends MTMultiMachineBase<MTProtocolBeacon> implements ISurvivalConstructable {

    // region Effect program constants
    /** Round length in seconds with a single enabled effect on an EV energy hatch (the first overclock level). */
    public static final int BASE_ROUND_SECONDS = 500;
    /** GT voltage tier index of EV: an EV hatch is overclock level 1 and every level above halves the round. */
    private static final int EV_TIER_INDEX = 4;
    /** Extra effect ticks on top of the round so the next round's refresh never leaves a visible one tick gap. */
    private static final int EFFECT_REFRESH_OVERLAP_TICKS = 20;
    /** 1.7.10 stores potion ids in a fixed array; each index is a potion id and not every slot is used. */
    public static final int MAX_EFFECTS = Potion.potionTypes.length;

    /** Per potion id: whether the effect is applied at all (default: every effect is disabled). */
    private final boolean[] effectEnabled = new boolean[MAX_EFFECTS];
    /** Per potion id: true = whitelist mode, false = blacklist mode (the default). */
    private final boolean[] effectWhitelistMode = new boolean[MAX_EFFECTS];
    /** Per potion id: comma/space/newline separated player names excluded in blacklist mode. */
    private final String[] effectBlacklist = new String[MAX_EFFECTS];
    /** Per potion id: comma/space/newline separated player names that are the only ones included in whitelist mode. */
    private final String[] effectWhitelist = new String[MAX_EFFECTS];
    /**
     * Per potion id: 0 = not checked, 1 = not representable through the vanilla {@link PotionEffect} API, 2 = usable.
     * Lazy because the check constructs a throwaway {@link PotionEffect}; only ids that survive that trip may ever be
     * handed out - see {@link #isEffectApplyable(int)}.
     */
    private static final byte[] EFFECT_APPLYABLE_CACHE = new byte[MAX_EFFECTS];
    /** Cached bound player / team names for the GUI (server refreshed, see {@link #refreshDisplayNames()}). */
    private String displayOwnerName = "";
    private String displayTeamName = "";
    /** Duration of the round just started; the effects are applied on the first powered running tick. 0 = no packet. */
    private int pendingEffectDurationTicks = 0;
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

    private static final IStructureDefinition<MTProtocolBeacon> STRUCTURE_DEFINITION = StructureDefinition
        .<MTProtocolBeacon>builder()
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
            HatchElementBuilder.<MTProtocolBeacon>builder()
                .anyOf(HatchElement.Energy, HatchElement.InputBus, HatchElement.OutputBus)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(1)
                .buildAndChain(ofBlock(Blocks.lapis_block, 0)))
        // Y: the six beacons in the tower. Accept anything that is a beacon block plus a beacon tile entity, so the
        // vanilla beacon, Et Futurum's New Beacon (BlockNewBeacon / TileEntityNewBeacon) and other mods' beacons work
        // alike - the pattern used by 123Technology's OTEMegaNineInOne.
        .addElement('Y', new IStructureElement<MTProtocolBeacon>() {

            private final IStructureElement.BlocksToPlace cached = IStructureElement.BlocksToPlace
                .create(Blocks.beacon, 0);

            @Override
            public boolean check(MTProtocolBeacon machine, World world, int x, int y, int z) {
                // Only care that it is a beacon: both the block and the tile entity have to match.
                return world.getBlock(x, y, z) instanceof BlockBeacon
                    && world.getTileEntity(x, y, z) instanceof TileEntityBeacon;
            }

            @Override
            public boolean couldBeValid(MTProtocolBeacon machine, World world, int x, int y, int z, ItemStack trigger) {
                // check() is side effect free, so it can be reused here.
                return check(machine, world, x, y, z);
            }

            @Override
            public boolean spawnHint(MTProtocolBeacon machine, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, Blocks.beacon, 0);
                return true;
            }

            @Override
            public boolean placeBlock(MTProtocolBeacon machine, World world, int x, int y, int z, ItemStack trigger) {
                world.setBlock(x, y, z, Blocks.beacon, 0, 2);
                if (check(machine, world, x, y, z)) return true;
                return world.setBlockToAir(x, y, z);
            }

            @Override
            public IStructureElement.BlocksToPlace getBlocksToPlace(MTProtocolBeacon machine, World world, int x, int y,
                int z, ItemStack trigger, AutoPlaceEnvironment env) {
                return cached;
            }
        })
        .build();
    // endregion

    public MTProtocolBeacon(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTProtocolBeacon(String aName) {
        super(aName);
    }

    @Override
    public IStructureDefinition<MTProtocolBeacon> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors);
        if (!errors.isEmpty()) return;
        // The broadcast needs power: at least one energy hatch has to be placed in one of the two X slots.
        checkHasEnergyHatch(errors);
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
        return new MTProtocolBeacon(mName);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // Program machine: there is no recipe map, the round timer and the effect program are the machine's logic.
        return null;
    }

    // region Tooltip
    /** House style: one {@code addInfo(translate(key))} per line, colour codes live in the lang files. */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.protocolcore.machinetype"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.desc"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.protocolcore.tooltip.power.header"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.power.current"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.power.min"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.power.overclock"))
            .addSeparator()
            .addInfo(translateToLocal("machine.protocolcore.tooltip.effects.header"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.effects.apply"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.effects.round"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.effects.stack"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.effects.list"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.effects.team"))
            .addSeparator()
            .addInfo(translateToLocal("machine.protocolcore.tooltip.details"))
            .addInfo(translateToLocal("machine.protocolcore.tooltip.details.hint"))
            .addStructureInfo(translateToLocal("machine.protocolcore.structure"))
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

    // region NBT
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        NBTTagCompound effects = new NBTTagCompound();
        for (int potionId = 0; potionId < MAX_EFFECTS; potionId++) {
            if (Potion.potionTypes[potionId] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setBoolean("enabled", effectEnabled[potionId]);
            entry.setBoolean("whitelistMode", effectWhitelistMode[potionId]);
            entry.setString("blacklist", effectBlacklist[potionId] == null ? "" : effectBlacklist[potionId]);
            entry.setString("whitelist", effectWhitelist[potionId] == null ? "" : effectWhitelist[potionId]);
            effects.setTag(Integer.toString(potionId), entry);
        }
        aNBT.setTag("protocolEffects", effects);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        NBTTagCompound effects = aNBT.getCompoundTag("protocolEffects");
        for (int potionId = 0; potionId < MAX_EFFECTS; potionId++) {
            if (Potion.potionTypes[potionId] == null) continue;
            String key = Integer.toString(potionId);
            if (!effects.hasKey(key)) continue;
            NBTTagCompound entry = effects.getCompoundTag(key);
            effectEnabled[potionId] = entry.getBoolean("enabled");
            effectWhitelistMode[potionId] = entry.getBoolean("whitelistMode");
            effectBlacklist[potionId] = entry.getString("blacklist");
            effectWhitelist[potionId] = entry.getString("whitelist");
        }
    }
    // endregion

    // region Tick hooks
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        refreshDisplayNames();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // Team membership stays cached in the GUI sync values; refresh it now and then so a changed team name shows up.
        if (aBaseMetaTileEntity.isServerSide() && aTick % 200L == 0L) refreshDisplayNames();
    }
    // endregion

    // region Processing (one GT cycle = one effect round)
    /**
     * One round: apply every enabled effect to the online team for {@link #roundTicks(int, int)} ticks and drain 1 A at
     * the highest energy hatch tier for the whole round. When the round finishes GT calls this again right away, which
     * keeps the effects refreshed for as long as the machine runs.
     */
    @Override
    public CheckRecipeResult checkProcessing() {
        long hatchTier = getEnergyHatchTier();
        if (hatchTier <= 0L) return SimpleCheckRecipeResult.ofFailure("protocolcore_no_energy_hatch");
        // EV is the reference level: a weaker hatch cannot drive the protocol (and would make EV look worse).
        if (hatchTier < EV_TIER_INDEX) return SimpleCheckRecipeResult.ofFailure("protocolcore_energy_tier_too_low");
        int enabledEffects = getEnabledEffectCount();
        if (enabledEffects <= 0) return SimpleCheckRecipeResult.ofFailure("protocolcore_no_enabled_effects");
        int level = getHatchLevel();
        int roundTicks = (int) roundTicks(level, enabledEffects);

        refreshDisplayNames();
        // The effects are applied by the first successfully powered running tick, not here: otherwise switching the
        // machine on without any stored EU would still hand out one free round before the power loss shutdown.
        pendingEffectDurationTicks = roundTicks;

        mEUt = 0;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mProgresstime = 0;
        mMaxProgresstime = roundTicks;
        mOutputItems = null;
        mOutputFluids = null;
        mPollution = 0;
        // 1 A at the highest hatch tier; MTEExtendedPowerMultiBlockBase drains lEUt every running tick.
        lEUt = -getHatchVoltage();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * The extended-power base drains the 1 A here; only after that first successful drain is the round's effect packet
     * handed out, so a round without power never results in free effects. The packet lasts the round length plus a
     * {@link #EFFECT_REFRESH_OVERLAP_TICKS} buffer so the refresh at the next round never leaves a one tick HUD gap.
     */
    @Override
    public boolean onRunningTick(ItemStack aStack) {
        boolean stillRunning = super.onRunningTick(aStack);
        if (stillRunning && pendingEffectDurationTicks > 0) {
            int durationTicks = pendingEffectDurationTicks;
            pendingEffectDurationTicks = 0;
            int effectTicks = durationTicks >= Integer.MAX_VALUE - EFFECT_REFRESH_OVERLAP_TICKS ? Integer.MAX_VALUE
                : durationTicks + EFFECT_REFRESH_OVERLAP_TICKS;
            applyEffects(effectTicks);
        }
        return stillRunning;
    }
    // endregion

    // region Round maths
    /** @return 1-based overclock level of the energy hatches, EV = 1 (0 when there is no hatch). */
    public int getHatchLevel() {
        long hatchTier = getEnergyHatchTier();
        if (hatchTier <= 0L) return 0;
        // EV is level 1 and every level above halves the round (lossy overclocking). Below-EV hatches cannot run the
        // machine at all (see checkProcessing), the clamp only keeps the GUI readout sane.
        return (int) Math.max(1L, hatchTier - (EV_TIER_INDEX - 1L));
    }

    /** @return the voltage of the highest energy hatch tier, i.e. the EU/t of the 1 A the machine draws. */
    public long getHatchVoltage() {
        long hatchTier = getEnergyHatchTier();
        if (hatchTier <= 0L) return 0L;
        return GTValues.V[(int) Math.min(hatchTier, GTValues.V.length - 1L)];
    }

    /** @return the round length in ticks: 500 s, queued by every enabled effect, halved per overclock level. */
    private static long roundTicks(int level, int enabledEffects) {
        double seconds = (double) BASE_ROUND_SECONDS * Math.pow(2.0D, enabledEffects)
            / Math.pow(2.0D, Math.max(0, level - 1));
        double ticks = seconds * 20.0D;
        if (ticks >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1L, (long) ticks);
    }

    /** @return the round length the current configuration would use, in seconds (0 = nothing to do). */
    public int getPlannedRoundSeconds() {
        int level = getHatchLevel();
        int enabledEffects = getEnabledEffectCount();
        if (level <= 0 || enabledEffects <= 0) return 0;
        return (int) Math.max(1L, roundTicks(level, enabledEffects) / 20L);
    }

    /** @return the length of the round currently running, in seconds. */
    public int getRoundTotalSeconds() {
        return Math.max(0, mMaxProgresstime) / 20;
    }

    /** @return the seconds left in the currently running round. */
    public int getRoundRemainingSeconds() {
        return Math.max(0, mMaxProgresstime - mProgresstime) / 20;
    }
    // endregion

    // region Effect program (machine NBT + GUI sync)
    public boolean isEffectEnabled(int potionId) {
        return potionId >= 0 && potionId < MAX_EFFECTS && effectEnabled[potionId];
    }

    public void setEffectEnabled(int potionId, boolean enabled) {
        if (potionId < 0 || potionId >= MAX_EFFECTS || effectEnabled[potionId] == enabled) return;
        effectEnabled[potionId] = enabled;
        markMachineDirty();
        // 205's multiblock recipe checks are event-driven: without this push the machine would keep idling after the
        // last enabled effect was switched on in the GUI.
        scheduleEffectRecheck();
    }

    /** @return true = whitelist mode (only listed players), false = blacklist mode (everyone but listed players). */
    public boolean isEffectWhitelistMode(int potionId) {
        return potionId >= 0 && potionId < MAX_EFFECTS && effectWhitelistMode[potionId];
    }

    public void setEffectWhitelistMode(int potionId, boolean whitelistMode) {
        if (potionId < 0 || potionId >= MAX_EFFECTS || effectWhitelistMode[potionId] == whitelistMode) return;
        effectWhitelistMode[potionId] = whitelistMode;
        markMachineDirty();
    }

    public String getEffectBlacklist(int potionId) {
        if (potionId < 0 || potionId >= MAX_EFFECTS || effectBlacklist[potionId] == null) return "";
        return effectBlacklist[potionId];
    }

    public void setEffectBlacklist(int potionId, String playerNames) {
        if (potionId < 0 || potionId >= MAX_EFFECTS) return;
        effectBlacklist[potionId] = playerNames == null ? "" : playerNames;
        markMachineDirty();
    }

    public String getEffectWhitelist(int potionId) {
        if (potionId < 0 || potionId >= MAX_EFFECTS || effectWhitelist[potionId] == null) return "";
        return effectWhitelist[potionId];
    }

    public void setEffectWhitelist(int potionId, String playerNames) {
        if (potionId < 0 || potionId >= MAX_EFFECTS) return;
        effectWhitelist[potionId] = playerNames == null ? "" : playerNames;
        markMachineDirty();
    }

    /** @return how many effects are switched on and can actually be applied (unused/inapplicable slots never count). */
    public int getEnabledEffectCount() {
        int count = 0;
        for (int potionId = 0; potionId < MAX_EFFECTS; potionId++) {
            if (effectEnabled[potionId] && isEffectApplyable(potionId)) count++;
        }
        return count;
    }

    /**
     * @return whether a potion id can be handed to a player through the vanilla {@link PotionEffect} API without being
     *         mangled. GTNH's Hodgepodge ({@code fixPotionLimit}, on by default) rewrites the id in the
     *         {@code PotionEffect(int, int, int, boolean)} constructor to {@code id & 0xFF}; an id above 255 (for
     *         example Thaumcraft's ids starting at 1000 via EndlessIDs) then points at an unused potion slot and the
     *         player tick crashes with an NPE in {@link PotionEffect#onUpdate}. Such ids are reported as unsupported so
     *         the GUI can grey them out instead of crashing; they start working automatically if the pack ever turns
     *         that fix off.
     */
    public static boolean isEffectApplyable(int potionId) {
        if (potionId < 0 || potionId >= MAX_EFFECTS || Potion.potionTypes[potionId] == null) return false;
        byte cached = EFFECT_APPLYABLE_CACHE[potionId];
        if (cached != 0) return cached == 2;
        boolean applyable;
        try {
            applyable = new PotionEffect(potionId, 1, 0).getPotionID() == potionId;
        } catch (Throwable ignored) {
            applyable = false;
        }
        EFFECT_APPLYABLE_CACHE[potionId] = (byte) (applyable ? 2 : 1);
        return applyable;
    }

    private void markMachineDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    /** A queued round packet must not survive a shutdown: the next start will queue a fresh one in checkProcessing. */
    @Override
    public void stopMachine(@NotNull ShutDownReason reason) {
        pendingEffectDurationTicks = 0;
        super.stopMachine(reason);
    }

    /**
     * Pushes an immediate recipe check on the server so config changes in the GUI take effect without a manual kick.
     */
    private void scheduleEffectRecheck() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) scheduleRecipeCheck(RecipeCheckReason.IMMEDIATE);
    }

    /** @return the cached name of the bound player (machine owner), empty when unknown. */
    public String getOwnerNameForGui() {
        return displayOwnerName;
    }

    /** @return the cached team name of the bound player, empty when the player has no team. */
    public String getTeamNameForGui() {
        return displayTeamName;
    }
    // endregion

    // region Effect application
    /**
     * @return the player whose team receives the effects: the machine owner, resolved from the owner name if needed.
     */
    private UUID getBoundPlayer() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return null;
        UUID owner = base.getOwnerUuid();
        if (owner != null) return owner;
        String ownerName = base.getOwnerName();
        if (ownerName == null || ownerName.isEmpty()) return null;
        return ServerPlayerUtils.getPlayerUUID(ownerName);
    }

    /** @return the team of the given player, or {@code null} when the player is not in one. */
    private static Team findTeam(UUID player) {
        for (Team team : TeamManager.getTeamMap()
            .values()) {
            if (team.isMember(player)) return team;
        }
        return null;
    }

    /** Refreshes the bound player / team names shown in the GUI (server side only). */
    private void refreshDisplayNames() {
        UUID boundPlayer = getBoundPlayer();
        if (boundPlayer == null) {
            displayOwnerName = "";
            displayTeamName = "";
            return;
        }
        String ownerName = ServerPlayerUtils.getPlayerName(boundPlayer);
        displayOwnerName = ownerName == null ? "" : ownerName;
        Team team = findTeam(boundPlayer);
        displayTeamName = team == null ? "" : team.getTeamName();
    }

    /**
     * Applies every enabled effect to the online targets: all online members of the bound player's team, or only the
     * bound player when they have no team. Each effect also carries its own player name blacklist / whitelist, so
     * individual effects can be handed to a subset of the team.
     */
    private void applyEffects(int durationTicks) {
        if (durationTicks <= 0) return;
        UUID boundPlayer = getBoundPlayer();
        if (boundPlayer == null) return;
        Team team = findTeam(boundPlayer);

        List<EntityPlayerMP> targets = new ArrayList<>();
        ServerPlayerUtils.forAllOnlinePlayers(player -> {
            if (team != null ? team.isMember(player.getUniqueID()) : boundPlayer.equals(player.getUniqueID())) {
                targets.add(player);
            }
        });
        if (targets.isEmpty()) return;

        for (int potionId = 0; potionId < MAX_EFFECTS; potionId++) {
            if (!effectEnabled[potionId] || !isEffectApplyable(potionId)) continue;
            // Build the effect once and verify it survives the constructor: a mangled id would poison the player's
            // active effect map and crash the next entity tick (see #isEffectApplyable).
            PotionEffect effect = new PotionEffect(potionId, durationTicks, 0);
            if (effect.getPotionID() != potionId) continue;
            boolean whitelistMode = effectWhitelistMode[potionId];
            Set<String> listedNames = parsePlayerNames(
                whitelistMode ? effectWhitelist[potionId] : effectBlacklist[potionId]);
            for (EntityPlayerMP player : targets) {
                String playerName = player.getCommandSenderName()
                    .toLowerCase(Locale.ROOT);
                boolean listed = listedNames.contains(playerName);
                if (whitelistMode ? listed : !listed) {
                    player.addPotionEffect(effect);
                }
            }
        }
    }

    /** Parses a comma / whitespace separated player name list into lower case names. */
    private static Set<String> parsePlayerNames(String raw) {
        Set<String> names = new HashSet<>();
        if (raw == null || raw.isEmpty()) return names;
        for (String name : raw.split("[,\\s]+")) {
            if (name.isEmpty()) continue;
            names.add(name.toLowerCase(Locale.ROOT));
        }
        return names;
    }
    // endregion

    // region Info (scanner / WAILA)
    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        boolean active = getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isActive();
        return new String[] {
            translateToLocalFormatted(
                "machine.protocolcore.info.status",
                translateToLocal(active ? "machine.protocolcore.info.running" : "machine.protocolcore.info.idle")),
            translateToLocalFormatted("machine.protocolcore.info.tier", getHatchLevel(), getHatchVoltage()),
            translateToLocalFormatted(
                "machine.protocolcore.info.round",
                getPlannedRoundSeconds(),
                getRoundRemainingSeconds()),
            translateToLocalFormatted("machine.protocolcore.info.effects", getEnabledEffectCount()),
            translateToLocalFormatted(
                "machine.protocolcore.info.owner",
                displayOwnerName.isEmpty() ? translateToLocal("machine.protocolcore.gui.unknown") : displayOwnerName,
                displayTeamName.isEmpty() ? translateToLocal("machine.protocolcore.gui.team.none") : displayTeamName) };
    }
    // endregion
}
