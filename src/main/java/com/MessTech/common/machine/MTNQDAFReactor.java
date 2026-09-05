package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FUSION1;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FUSION1_GLOW;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.MessTech.common.machine.Base.MTGeneratorMultiBase;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import goodgenerator.api.recipe.GoodGeneratorRecipeMaps;
import goodgenerator.items.GGMaterial;
import goodgenerator.loader.Loaders;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.ErrorType;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.thermalfoundation.fluid.TFFluids;
import lombok.Getter;
import lombok.Setter;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Large NQD Annihilation Field Reactor.
 * <p>
 * A Naquadah-reactor derivative that runs on liquid nuclear / naquadah fuels, uses the Large
 * Naquadah Reactor coolant and excited-liquid mechanics, scales output with reactor coil tier,
 * gains work efficiency while running, and can freeze its idle efficiency decay with molten
 * spacetime. When no dynamo hatch is installed it automatically switches to wireless output.
 */
public class MTNQDAFReactor extends MTGeneratorMultiBase<MTNQDAFReactor>
    implements ISurvivalConstructable, ICasingTextureProvider {
    // MTNQDAFReactor full name called Large NQD Annihilation Field Reactor

    // region Structure piece offsets
    // '~' (controller) is at char index 1 (X), outer array index 1 (Y), inner string index 2 (Z).
    private static final int HORIZONTAL_OFFSET = 16;
    private static final int VERTICAL_OFFSET = 6;
    private static final int DEPTH_OFFSET = 0;

    // endregion
    public enum LevelTier {

        TIER1(1, Loaders.FRF_Coil_1, 0),
        TIER2(2, Loaders.FRF_Coil_2, 0),
        TIER3(3, Loaders.FRF_Coil_3, 0),
        TIER4(4, Loaders.FRF_Coil_4, 0),
        INVALID(-1, null, -1);

        /** Sentinel meaning "no / unmatched / unrecognised tier". */
        public static final int INVALID_TIER = -1;

        /** 1..5, or -1 for {@link #INVALID}. */
        public final int tier;
        /** A: reactor coil block of this tier. */
        public final Block coilBlock;
        public final int coilMeta;

        LevelTier(int tier, Block coilBlock, int coilMeta) {
            this.tier = tier;
            this.coilBlock = coilBlock;
            this.coilMeta = coilMeta;
        }

        public boolean isValid() {
            return tier != INVALID_TIER;
        }

        public String getTierName() {
            if (!isValid()) return "INVALID";
            return switch (tier) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                default -> String.valueOf(tier);
            };
        }

        /**
         * Is this tier greater than or equal to {@code other}? {@link #INVALID} is never "at least" a real tier.
         */
        public boolean isAtLeast(LevelTier other) {
            return isValid() && other != null && other.isValid() && tier >= other.tier;
        }

        /** Is this tier greater than or equal to the given tier number (1..5)? {@link #INVALID} is never. */
        public boolean isAtLeast(int minTier) {
            return isValid() && tier >= minTier;
        }

        public static LevelTier fromTier(int tier) {
            for (LevelTier level : values()) {
                if (level.tier == tier) return level;
            }
            return INVALID;
        }

        public static LevelTier getFromCoilBlock(Block block, int meta) {
            for (LevelTier level : values()) {
                if (level.isValid() && level.coilBlock == block && level.coilMeta == meta) return level;
            }
            return INVALID;
        }

        public static LevelTier getFromMachineBlock(Block block, int meta) {
            for (LevelTier level : values()) {
                if (level.isValid()) return level;
            }
            return INVALID;
        }

        /**
         * Tier fetcher for StructureLib's {@code StructureUtility#ofBlocksTiered} used by structure element 'A'.
         *
         * @return the reactor coil tier of the given block, or {@code null} if it is not a recognised reactor coil.
         */
        public static Integer getCoilBlockTier(Block block, int meta) {
            LevelTier level = getFromCoilBlock(block, meta);
            return level.isValid() ? level.tier : null;
        }

        public static List<Pair<Block, Integer>> getCoilList() {
            return Arrays.asList(
                Pair.of(TIER1.coilBlock, TIER1.coilMeta),
                Pair.of(TIER2.coilBlock, TIER2.coilMeta),
                Pair.of(TIER3.coilBlock, TIER3.coilMeta),
                Pair.of(TIER4.coilBlock, TIER4.coilMeta));
        }
    }

    @Getter
    @Setter
    private int CoilTier = LevelTier.INVALID_TIER;

    // region Work efficiency / reactor state
    private static final int BASE_WORK_EFFICIENCY = 100;
    private static final int MAX_WORK_EFFICIENCY = 400;
    private static final int EFFICIENCY_STEP_TICKS = 30 * 20;
    private static final long MAX_EFFICIENCY_TIME_TICKS = (long) (MAX_WORK_EFFICIENCY - BASE_WORK_EFFICIENCY)
        * EFFICIENCY_STEP_TICKS;
    private static final int EFFICIENCY_DECAY_RATE = 100; // DTPF/PlasmaForge-style idle decay.
    private static final int LIQUID_AIR_PER_SECOND = 1_000_000;
    private static final int MAX_SPACETIME_COST_PER_SECOND = 1_073_741_824;

    /** Equivalent active runtime used to derive {@link #getWorkEfficiency()}. */
    @Getter
    private long workEfficiencyTime = 0;
    @Getter
    private int spacetimeCostPerSecond = 1;
    @Getter
    private int decayFreezeTicks = 0;

    private FluidStack lockedExcitedFluid;
    private int currentFuelMultiplier = 1;
    private int currentCoolantEfficiency = 100;
    private int currentBasicOutput = 0;
    @Getter
    private int currentParallel = 1;
    private long currentOutputEUt = 0;
    // endregion

    // region Fluid helpers
    private static List<Pair<FluidStack, Integer>> excitedLiquids;
    private static List<Pair<FluidStack, Integer>> coolants;

    private static List<Pair<FluidStack, Integer>> getExcitedLiquids() {
        if (excitedLiquids == null) {
            excitedLiquids = Arrays.asList(
                Pair.of(Materials.Space.getMolten(20L), 64),
                Pair.of(GGMaterial.atomicSeparationCatalyst.getMolten(20), 16),
                Pair.of(Materials.Naquadah.getMolten(20L), 4),
                Pair.of(Materials.Uranium235.getMolten(180L), 3),
                Pair.of(Materials.Caesium.getMolten(180L), 2));
        }
        return excitedLiquids;
    }

    private static List<Pair<FluidStack, Integer>> getCoolants() {
        if (coolants == null) {
            coolants = Arrays.asList(
                Pair.of(Materials.Time.getMolten(20L), 500),
                Pair.of(new FluidStack(TFFluids.fluidCryotheum, 1_000), 275),
                Pair.of(Materials.SuperCoolant.getFluid(1_000), 150),
                Pair.of(GTModHandler.getIC2Coolant(1_000), 105));
        }
        return coolants;
    }
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                E               ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","               EEE              ","               E E              ","               EEE              ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},
        {"              FAAAF             ","              F   F             ","              F   F             ","              F   F             ","              F   F             ","             FF   FF            ","           FF       FF          ","         FF           FF        ","        F               F       ","       F                 F      ","       F                 F      ","      F                   F     ","      F                   F     ","     F                     F    "," FFFFF         EEE         FFFFF","              ECCCE             ","              ECCCE             ","              ECCCE             "," FFFFF         EEE         FFFFF","     F                     F    ","      F                   F     ","      F                   F     ","       F                 F      ","       F                 F      ","        F               F       ","         FF           FF        ","           FF       FF          ","             FF   FF            ","              F   F             ","              F   F             ","              F   F             ","              F   F             "},
        {"              AAAAA             ","             FFFFFFF            ","          FFFGGGGGGGFFF         ","        FFGGFGDDDDDGFGGFF       ","       FGGDGFGGGDGGGFGDGGF      ","      FGDDDGFFFFDFFFFGDDDGF     ","     FGDDGGFF   D   FFGGDDGF    ","    FGDDGFF     D     FFGDDGF   ","   FGDDGF       D       FGDDGF  ","   FGDGF        D        FGDGF  ","  FGDDGF        D        FGDDGF ","  FGGGF         D         FGGGF ","  FFFFF         D         FFFFF "," FGGGF         EEE         FGGGF"," FGDGF        ECCCE        FGDGF"," FGDGF       ECCCCCE       FGDGF"," FGDGF       ECCCCCE       FGDGF"," FGDGF       ECCCCCE       FGDGF"," FGDGF        ECCCE        FGDGF"," FGGGF         EEE         FGGGF","  FFFFF         D         FFFFF ","  FGGGF         D         FGGGF ","  FGDDGF        D        FGDDGF ","   FGDGF        D        FGDGF  ","   FGDDGF       D       FGDDGF  ","    FGDDGFF     D     FFGDDGF   ","     FGDDGGFF   D   FFGGDDGF    ","      FGDDDGFFFFDFFFFGDDDGF     ","       FGGDGFGGGDGGGFGDGGF      ","        FFGGFGDDDDDGFGGFF       ","          FFFGGGGGGGFFF         ","             FFFFFFF            "},
        {"              AA~AA             ","             DDDDDDDD           ","          DDDGGGGGGG DD         ","        DDGGGBBBBBBBGGGDD       ","       DGGBBBGGGBGGGBBBGGD      ","      DGBBBGG   BD  GGBBBGD     ","     DGBBGG    DBD    GGBBGD    ","    DGBBG      DBD      GBBGD   ","   DGBBG       DBD       GBBGD  ","   DGBG        DBD        GBGD  ","  DGBBG        DBD        GBBGD ","  DGBG         DBD         GBGD ","  DGBG         DBD         GBGD "," DGBG          EBE          GBGD"," DGBG         ECBCE         GBGD"," DGBG        ECCBCCE        GBGD"," FGBG        ECC CCE        GBGF"," DGBG        ECCBCCE        GBGD"," DGBG         ECBCE         GBGD"," DGBG          EBE          GBGD","  DGBG         DBD         GBGD ","  DGBG         DBD         GBGD ","  DGBBG        DBD        GBBGD ","   DGBG        DBD        GBGD  ","   DGBBG       DBD       GBBGD  ","    DGBBG      DBD      GBBGD   ","     DGBBGG    DBD    GGBBGD    ","      DGBBBGG  DBD  GGBBBGD     ","       DGGBBBGGGBGGGBBBGGD      ","        DDGGGBBBBBBBGGGDD       ","          DDDGGGGGGGDDD         ","             DDDDDDD            "},
        {"              AAAAA             ","             FFFFFFF            ","          FFFGGGGGGGFFF         ","        FFGGGDDDDDDDGGGFF       ","       FGGDDDGGGDGGGDDDGGF      ","      FGDDDGGFFFDFFFGGDDDGF     ","     FGDDGGFF   D   FFGGDDGF    ","    FGDDGFF     D     FFGDDGF   ","   FGDDGF       D       FGDDGF  ","   FGDGF        D        FGDGF  ","  FGDDGF        D        FGDDGF ","  FGDGF         D         FGDGF ","  FGDGF         D         FGDGF "," FGDGF         EEE         FGDGF"," FGDGF        ECCCE        FGDGF"," FGDGF       ECCCCCE       FGDGF"," FGDGF       ECCCCCE       FGDGF"," FGDGF       ECCCCCE       FGDGF"," FGDGF        ECCCE        FGDGF"," FGDGF         EEE         FGDGF","  FGDGF         D         FGDGF ","  FGDGF         D         FGDGF ","  FGDDGF        D        FGDDGF ","   FGDGF        D        FGDGF  ","   FGDDGF       D       FGDDGF  ","    FGDDGFF     D     FFGDDGF   ","     FGDDGGFF   D   FFGGDDGF    ","      FGDDDGGFFFDFFFGGDDDGF     ","       FGGDDDGGGDGGGDDDGGF      ","        FFGGGDDDDDDDGGGFF       ","          FFFGGGGGGGFFF         ","             FFFFFFF            "},
        {"              FAAAF             ","              F   F             ","              F   F             ","              F   F             ","              F   F             ","             FF   FF            ","           FF       FF          ","         FF           FF        ","        F               F       ","       F                 F      ","       F                 F      ","      F                   F     ","      F                   F     ","     F                     F    "," FFFFF         EEE         FFFFF","              ECCCE             ","              ECCCE             ","              ECCCE             "," FFFFF         EEE         FFFFF","     F                     F    ","      F                   F     ","      F                   F     ","       F                 F      ","       F                 F      ","        F               F       ","         FF           FF        ","           FF       FF          ","             FF   FF            ","              F   F             ","              F   F             ","              F   F             ","              F   F             "},
        {"                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","               EEE              ","               E E              ","               EEE              ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                ","                                "},

    };
    //spotless:on
    /*
     * Blocks:
     * A-> addElement('A', Casings.NaquadahFuelRefineryCasing.asElement()
     * B -> ofBlock(FRF_Coil_1, 0);
     * C-> addElement('C', Casings.FieldRestrictionCasing.asElement()
     * D-> addElement('D', Casings.FieldRestrictionGlass.asElement()
     * E-> addElement('E', Casings.NaquadahReactorCasing.asElement()
     * F-> addElement('F', Casings.RadiationProofMachineCasing.asElement()
     * G-> addElement('G', Casings.EuropiumReinforcedRadiationProofMachineCasing.asElement()
     * Offsets:
     * 16, 6, 0
     */

    private static final IStructureDefinition<MTNQDAFReactor> STRUCTURE_DEFINITION = StructureDefinition
        .<MTNQDAFReactor>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        .addElement(
            'A',
            HatchElementBuilder.<MTNQDAFReactor>builder()
                .anyOf(HatchElement.Dynamo.or(HatchElement.ExoticDynamo))
                .casingIndex(Casings.NaquadahFuelRefineryCasing.textureId)
                .hint(2)
                .buildAndChain(
                    Casings.NaquadahFuelRefineryCasing.getBlock(),
                    Casings.NaquadahFuelRefineryCasing.getBlockMeta()))
        .addElement(
            'B',
            ofBlocksTiered(
                LevelTier::getCoilBlockTier,
                LevelTier.getCoilList(),
                LevelTier.INVALID_TIER,
                MTNQDAFReactor::setCoilTier,
                MTNQDAFReactor::getCoilTier,
                Collections.singletonList("misc.structure_tooltip.coil")))
        .addElement('C', Casings.FieldRestrictionCasing.asElement())
        .addElement('D', Casings.FieldRestrictionGlass.asElement())
        // .addElement('', Casings.ContainmentCasing.asElement())
        .addElement('F', Casings.RadiationProofMachineCasing.asElement())
        .addElement('G', Casings.EuropiumReinforcedRadiationProofMachineCasing.asElement())
        .addElement(
            'E',
            HatchElementBuilder.<MTNQDAFReactor>builder()
                .anyOf(HatchElement.InputHatch, HatchElement.OutputHatch, HatchElement.InputBus, HatchElement.OutputBus)
                .casingIndex(Casings.NaquadahReactorCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(Casings.NaquadahReactorCasing.getBlock(), Casings.NaquadahReactorCasing.getBlockMeta())))
        .build();
    // endregion

    public MTNQDAFReactor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTNQDAFReactor(String aName) {
        super(aName);
    }

    @Override
    public IStructureDefinition<MTNQDAFReactor> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        setCoilTier(LevelTier.INVALID_TIER);
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;

        LevelTier coilTier = LevelTier.fromTier(CoilTier);
        if (!coilTier.isValid()) {
            errors.add(StructureErrors.of("misc.structure_error.no_coil"));
        }

        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);

        // No dynamo hatch -> wireless output mode is automatic.
        int dynamoCount = mDynamoHatches.size() + mExoticDynamoHatches.size();
        if (dynamoCount == 0) {
            setEnableWirelessFunc(true);
            setEnableWireless(true);
        } else {
            setEnableWirelessFunc(false);
            setEnableWireless(false);
            if (dynamoCount > 1) {
                errors.add(StructureErrors.hatchCount(ErrorType.TOO_MANY, HatchElement.Dynamo, dynamoCount, 1));
            }
        }
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GoodGeneratorRecipeMaps.naquadahReactorFuels;
    }

    @Override
    protected boolean filtersFluid() {
        return false;
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
        return new MTNQDAFReactor(mName);
    }

    // region Efficiency / spacetime
    public int getWorkEfficiency() {
        return Math.min(MAX_WORK_EFFICIENCY, BASE_WORK_EFFICIENCY + (int) (workEfficiencyTime / EFFICIENCY_STEP_TICKS));
    }

    private int getCoilTierForOutput() {
        LevelTier tier = LevelTier.fromTier(CoilTier);
        return tier.isValid() ? tier.tier : 1;
    }

    /**
     * Consumes one spacetime dose and starts a 30 second decay freeze. The dose cost doubles after
     * every successful dose, capped at the Black Hole Compressor style 1,073,741,824 limit.
     */
    private boolean tryConsumeSpacetime() {
        if (spacetimeCostPerSecond <= 0) {
            return false;
        }
        FluidStack cost = Materials.SpaceTime.getMolten(spacetimeCostPerSecond);
        if (!depleteInput(cost)) {
            return false;
        }
        if (spacetimeCostPerSecond <= MAX_SPACETIME_COST_PER_SECOND / 2) {
            spacetimeCostPerSecond *= 2;
        } else {
            spacetimeCostPerSecond = MAX_SPACETIME_COST_PER_SECOND;
        }
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("nqdWorkEfficiencyTime", workEfficiencyTime);
        aNBT.setInteger("nqdSpacetimeCost", spacetimeCostPerSecond);
        aNBT.setInteger("nqdCurrentBasicOutput", currentBasicOutput);
        aNBT.setInteger("nqdCurrentFuelMultiplier", currentFuelMultiplier);
        aNBT.setInteger("nqdCurrentCoolantEfficiency", currentCoolantEfficiency);
        aNBT.setInteger("nqdCurrentParallel", currentParallel);
        if (lockedExcitedFluid != null) {
            aNBT.setString(
                "nqdLockedExcitedFluid",
                lockedExcitedFluid.getFluid()
                    .getName());
            aNBT.setInteger("nqdLockedExcitedFluidAmount", lockedExcitedFluid.amount);
        } else {
            aNBT.removeTag("nqdLockedExcitedFluid");
            aNBT.removeTag("nqdLockedExcitedFluidAmount");
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("nqdWorkEfficiencyTime")) {
            workEfficiencyTime = aNBT.getLong("nqdWorkEfficiencyTime");
        }
        if (aNBT.hasKey("nqdSpacetimeCost")) {
            spacetimeCostPerSecond = Math.max(1, aNBT.getInteger("nqdSpacetimeCost"));
        }
        if (aNBT.hasKey("nqdCurrentBasicOutput")) {
            currentBasicOutput = aNBT.getInteger("nqdCurrentBasicOutput");
        }
        if (aNBT.hasKey("nqdCurrentFuelMultiplier")) {
            currentFuelMultiplier = Math.max(1, aNBT.getInteger("nqdCurrentFuelMultiplier"));
        }
        if (aNBT.hasKey("nqdCurrentCoolantEfficiency")) {
            currentCoolantEfficiency = Math.max(0, aNBT.getInteger("nqdCurrentCoolantEfficiency"));
        }
        if (aNBT.hasKey("nqdCurrentParallel")) {
            currentParallel = Math.max(1, aNBT.getInteger("nqdCurrentParallel"));
        }
        String excitedName = aNBT.getString("nqdLockedExcitedFluid");
        if (!excitedName.isEmpty() && FluidRegistry.getFluid(excitedName) != null) {
            lockedExcitedFluid = new FluidStack(
                FluidRegistry.getFluid(excitedName),
                Math.max(1, aNBT.getInteger("nqdLockedExcitedFluidAmount")));
        } else {
            lockedExcitedFluid = null;
        }
    }
    // endregion

    // region Reactor processing
    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        ArrayList<FluidStack> fluids = getStoredFluids();
        FluidStack[] fluidArray = fluids.toArray(new FluidStack[0]);

        GTRecipe recipe = GoodGeneratorRecipeMaps.naquadahReactorFuels.findRecipeQuery()
            .fluids(fluidArray)
            .find();
        if (recipe == null || recipe.mFluidInputs == null || recipe.mFluidInputs.length == 0) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        FluidStack fuelInput = recipe.mFluidInputs[0];
        if (fuelInput == null) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int excitedMultiplier = 1;
        FluidStack excitedTemplate = null;
        for (Pair<FluidStack, Integer> excited : getExcitedLiquids()) {
            if (hasFluid(fluids, excited.getKey())) {
                excitedTemplate = excited.getKey();
                excitedMultiplier = excited.getValue();
                break;
            }
        }

        long fuelPerParallel = (long) fuelInput.amount * excitedMultiplier;
        if (fuelPerParallel <= 0) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int parallel = getParallelForAvailable(getFluidAmount(fluids, fuelInput), fuelPerParallel);
        if (parallel <= 0) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        // Keep the total consumed fuel within the single-FluidStack int limit.
        long maxParallelForInt = fuelPerParallel > 0 ? Integer.MAX_VALUE / fuelPerParallel : Integer.MAX_VALUE;
        if (parallel > maxParallelForInt) {
            parallel = (int) maxParallelForInt;
        }

        currentBasicOutput = recipe.mSpecialValue;
        currentFuelMultiplier = excitedMultiplier;

        // Wired dynamos cannot accept more than a signed long, so cap parallel in wired mode.
        // In wireless mode the BigInteger path is used and this cap is intentionally skipped.
        if (!isWirelessModeActive()) {
            long unitOutput = (long) currentBasicOutput * getCoilTierForOutput() * 500 * currentFuelMultiplier / 100;
            if (unitOutput > 0) {
                long maxParallelForOutput = Long.MAX_VALUE / unitOutput;
                if (parallel > maxParallelForOutput) {
                    parallel = (int) Math.min(Integer.MAX_VALUE, maxParallelForOutput);
                }
            }
        }
        if (parallel <= 0) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        currentParallel = parallel;

        long fuelAmountLong = fuelPerParallel * currentParallel;
        if (fuelAmountLong <= 0 || fuelAmountLong > Integer.MAX_VALUE) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        FluidStack fuelCost = copyFluid(fuelInput, (int) fuelAmountLong);
        if (!depleteInput(fuelCost)) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        lockedExcitedFluid = excitedTemplate == null ? null : excitedTemplate.copy();
        currentCoolantEfficiency = 100;
        currentOutputEUt = 0;
        applyOutput(computeOutputEUt(100, currentFuelMultiplier, currentParallel));

        long burnTime = (long) recipe.mDuration * getWorkEfficiency() / BASE_WORK_EFFICIENCY;
        if (burnTime <= 0) {
            burnTime = 1;
        }
        mMaxProgresstime = (int) Math.min(Integer.MAX_VALUE, burnTime);
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mProgresstime = 0;
        mOutputItems = null;
        mOutputFluids = makeDepletedOutput(recipe, (long) excitedMultiplier * currentParallel);
        return CheckRecipeResultRegistry.GENERATING;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        IGregTechTileEntity tileEntity = getBaseMetaTileEntity();
        if (tileEntity != null && tileEntity.isServerSide()) {
            // Growth is only based on actual running time and is independent of the decay freeze.
            if (mMaxProgresstime > 0) {
                workEfficiencyTime = Math.min(MAX_EFFICIENCY_TIME_TICKS, workEfficiencyTime + 1);
            }
            if (tileEntity.getWorld() != null && tileEntity.getWorld()
                .getTotalWorldTime() % 20 == 0) {
                updateReactorSecond();
            }
        }
        return super.onRunningTick(aStack);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) {
            return;
        }

        // The freeze is a real-time 30s effect. It only suppresses decay while the machine is idle;
        // it never suppresses efficiency growth while running.
        if (decayFreezeTicks > 0) {
            decayFreezeTicks--;
        }

        if (mMaxProgresstime == 0) {
            if (decayFreezeTicks <= 0 && aTick % 20 == 0 && tryConsumeSpacetime()) {
                decayFreezeTicks = 30 * 20;
            }

            if (decayFreezeTicks <= 0) {
                workEfficiencyTime = Math.max(0, workEfficiencyTime - EFFICIENCY_DECAY_RATE);
            }
        }
    }

    private void updateReactorSecond() {
        if (currentBasicOutput <= 0) {
            return;
        }
        if (!depleteInput(Materials.LiquidAir.getFluid(LIQUID_AIR_PER_SECOND))) {
            currentOutputEUt = 0;
            lEUt = 0;
            resetWirelessGenerationPerTick();
            return;
        }

        int coolantEfficiency = 100;
        for (Pair<FluidStack, Integer> coolant : getCoolants()) {
            if (depleteInput(coolant.getKey())) {
                coolantEfficiency = coolant.getValue();
                break;
            }
        }

        int multiplier = 1;
        if (lockedExcitedFluid != null && depleteInput(lockedExcitedFluid)) {
            multiplier = currentFuelMultiplier;
        }

        currentCoolantEfficiency = coolantEfficiency;
        applyOutput(computeOutputEUt(coolantEfficiency, multiplier, currentParallel));
    }

    /**
     * Computes EU/t with BigInteger so huge parallel values cannot overflow to a negative long.
     */
    private BigInteger computeOutputEUt(int coolantEfficiency, int multiplier, int parallel) {
        return BigInteger.valueOf(currentBasicOutput)
            .multiply(BigInteger.valueOf(getCoilTierForOutput()))
            .multiply(BigInteger.valueOf(coolantEfficiency))
            .multiply(BigInteger.valueOf(multiplier))
            .multiply(BigInteger.valueOf(parallel))
            .divide(BigInteger.valueOf(100));
    }

    private void applyOutput(BigInteger outputEUt) {
        if (outputEUt == null || outputEUt.signum() <= 0) {
            outputEUt = BigInteger.ZERO;
        }
        setWirelessGenerationPerTick(outputEUt);
        if (outputEUt.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            // Wired dynamos cannot accept more than long; wireless mode sends the BigInteger value.
            currentOutputEUt = Long.MAX_VALUE;
            lEUt = 0;
        } else {
            currentOutputEUt = outputEUt.longValue();
            lEUt = currentOutputEUt;
        }
    }

    private boolean hasFluid(ArrayList<FluidStack> fluids, FluidStack template) {
        return getFluidAmount(fluids, template) >= template.amount;
    }

    private long getFluidAmount(ArrayList<FluidStack> fluids, FluidStack template) {
        if (template == null) {
            return 0;
        }
        long amount = 0;
        for (FluidStack fluid : fluids) {
            if (fluid != null && fluid.isFluidEqual(template)) {
                amount += fluid.amount;
            }
        }
        return amount;
    }

    private static FluidStack copyFluid(FluidStack fluid, int amount) {
        if (fluid == null || amount <= 0) {
            return null;
        }
        FluidStack copy = fluid.copy();
        copy.amount = amount;
        return copy;
    }

    private FluidStack[] makeDepletedOutput(GTRecipe recipe, long multiplier) {
        if (recipe.mFluidOutputs == null || recipe.mFluidOutputs.length == 0) {
            return null;
        }
        ArrayList<FluidStack> outputs = new ArrayList<>();
        for (FluidStack output : recipe.mFluidOutputs) {
            if (output == null) {
                continue;
            }
            long total = (long) output.amount * multiplier;
            if (total <= 0) {
                continue;
            }
            while (total > 0) {
                int part = (int) Math.min(Integer.MAX_VALUE, total);
                outputs.add(copyFluid(output, part));
                total -= part;
            }
        }
        return outputs.isEmpty() ? null : outputs.toArray(new FluidStack[0]);
    }
    // endregion

    // region Waila / info
    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        final IGregTechTileEntity tileEntity = getBaseMetaTileEntity();
        if (tileEntity != null) {
            tag.setInteger("nqdWorkEfficiency", getWorkEfficiency());
            tag.setInteger("nqdSpacetimeCost", spacetimeCostPerSecond);
            tag.setInteger("nqdDecayFreeze", decayFreezeTicks);
            tag.setInteger("nqdParallel", currentParallel);
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey("nqdWorkEfficiency")) {
            currentTip.add(
                StatCollector.translateToLocal("machine.nqdafreactor.efficiency") + ": "
                    + EnumChatFormatting.GOLD
                    + tag.getInteger("nqdWorkEfficiency")
                    + EnumChatFormatting.RESET
                    + "%");
            if (tag.getInteger("nqdParallel") > 1) {
                currentTip.add(
                    StatCollector.translateToLocal("machine.nqdafreactor.parallel") + ": "
                        + EnumChatFormatting.YELLOW
                        + tag.getInteger("nqdParallel")
                        + EnumChatFormatting.RESET);
            }
            if (tag.getInteger("nqdDecayFreeze") > 0) {
                currentTip.add(
                    StatCollector.translateToLocal("machine.nqdafreactor.decay_freeze") + ": "
                        + EnumChatFormatting.AQUA
                        + (tag.getInteger("nqdDecayFreeze") / 20)
                        + EnumChatFormatting.RESET
                        + "s");
            }
        }
    }
    // endregion

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("machine.nqdafreactor.name"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.desc"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.eff.ramp"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.eff.burn"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.eff.decay"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.liquid_air"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.fuel_input"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.coil"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.coolant_header"))
            .addInfo(getCoolantTextFormatted("machine.nqdafreactor.fluid.ic2coolant", "1000", 105))
            .addInfo(getCoolantTextFormatted("machine.nqdafreactor.fluid.supercoolant", "1000", 150))
            .addInfo(getCoolantTextFormatted("machine.nqdafreactor.fluid.cryotheum", "1000", 275))
            .addInfo(getCoolantTextFormatted("machine.nqdafreactor.fluid.tachyon", "20", 500))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.excited_header"))
            .addInfo(getExcitedTextFormatted("machine.nqdafreactor.fluid.caesium", "180", 2))
            .addInfo(getExcitedTextFormatted("machine.nqdafreactor.fluid.uranium235", "180", 3))
            .addInfo(getExcitedTextFormatted("machine.nqdafreactor.fluid.naquadah", "20", 4))
            .addInfo(getExcitedTextFormatted("machine.nqdafreactor.fluid.atomic", "20", 16))
            .addInfo(getExcitedTextFormatted("machine.nqdafreactor.fluid.space", "20", 64))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.spacetime"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.spacetime_growth"))
            .addInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.no_dynamo"))
            .addSupportAny()
            .beginStructureBlock(31, 32, 32, false)
            .addController(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.controller"))
            .addStructureInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.a"))
            .addStructureInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.b"))
            .addStructureInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.c"))
            .addStructureInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.d"))
            .addStructureInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.e"))
            .addStructureInfo(StatCollector.translateToLocal("machine.nqdafreactor.tooltip.structure.f"))
            .addDynamoHatch("0-1", StatCollector.translateToLocal("machine.nqdafreactor.tooltip.dynamo"), 2)
            .addInputHatch("1+", StatCollector.translateToLocal("machine.nqdafreactor.tooltip.input"), 1)
            .addOutputHatch("1+", StatCollector.translateToLocal("machine.nqdafreactor.tooltip.output"), 1)
            .toolTipFinisher();
        return tt;
    }

    private static String getCoolantTextFormatted(String fluidNameKey, String litersConsumed, int effBoost) {
        return StatCollector.translateToLocalFormatted(
            "machine.nqdafreactor.tooltip.coolant_line",
            litersConsumed,
            effBoost,
            StatCollector.translateToLocal(fluidNameKey));
    }

    private static String getExcitedTextFormatted(String fluidNameKey, String litersConsumed, int multiplier) {
        return StatCollector.translateToLocalFormatted(
            "machine.nqdafreactor.tooltip.excited_line",
            litersConsumed,
            multiplier,
            StatCollector.translateToLocal(fluidNameKey));
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture baseCasing = Casings.NaquadahFuelRefineryCasing.getCasingTexture();
        if (side == facing) {
            // Same front overlay as GT5U's "Shielded Lagrangian Annihilation Matrix" (AntimatterGenerator),
            // but with Naquadah Fuel Refinery Casing as the base texture.
            return new ITexture[] { baseCasing, TextureFactory.builder()
                .addIcon(OVERLAY_FUSION1)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FUSION1_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        // Other faces use the same Naquadah Fuel Refinery Casing base.
        return new ITexture[] { baseCasing };
    }

    @Override
    public ITexture getCasingTexture() {
        return Casings.NaquadahFuelRefineryCasing.getCasingTexture();
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

}
