package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofSpecificTileAdder;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.MessTech.common.block.MTBlocks;
import com.MessTech.common.machine.Base.MTGeneratorMultiBase;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.MessTech.common.thaumcraft.MTEssentiaEnergyData;
import com.MessTech.common.thaumcraft.MTEssentiaHatchMETileEntity;
import com.MessTech.common.thaumcraft.MTEssentiaHatchTileEntity;
import com.MessTech.common.thaumcraft.MTEssentiaUpgradeItem;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import bartworks.system.material.WerkstoffLoader;
import goodgenerator.items.GGMaterial;
import goodgenerator.loader.Loaders;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.config.ConfigBlocks;

/**
 * Large Essentia Generator: burns essentia for EU/t. The essentia fuel table decides the base value and the auxiliary
 * fluid of every aspect; the diffusion cells decide the stable point multiplier and the highest output hatch tier
 * (Grandmaster cells remove the tier limit), and the upgrade items unlock the non-NORMAL aspect categories on the
 * controller's right-click.
 *
 * <p>
 * Output is wired: one dynamo hatch, multi-amp or laser included, so the per-tick EU can exceed the MAX tier voltage;
 * wireless output is switched off for this machine. Nothing here is a recipe-map machine - {@link #checkProcessing()}
 * converts stored essentia directly, keeping the leftover EU in {@link #mLeftEnergy} for the next tick.
 */
public class MTLargeEssentiaGenerator extends MTGeneratorMultiBase<MTLargeEssentiaGenerator>
    implements ISurvivalConstructable, ICasingTextureProvider, RecipeMapWorkable {

    // region Structure
    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFFSET = 4;
    private static final int VERTICAL_OFFSET = 0;
    private static final int DEPTH_OFFSET = 4;
    /** Magic Casing texture (GoodGenerator texture page 12, index 0). */
    private static final int CASING_INDEX = 12 << 7;

    private IStructureDefinition<MTLargeEssentiaGenerator> structureDefinition;

    // spotless:off
    private static final String[][] STRUCTURE_STRING = {
        { "A       A", "         ", "         ", "         ", "    ~    ", "         ", "         ", "         ",
            "A       A" },
        { "T   C   T", "   CEC   ", "  CEEEC  ", " CEEEEEC ", "CEEEEEEEC", " CEEEEEC ", "  CEEEC  ", "   CEC   ",
            "T   C   T" },
        { "T  TXT  T", "  TCXCT  ", " TCCXCCT ", "TCCCXCCCT", "XXXXXXXXX", "TCCCXCCCT", " TCCXCCT ", "  TCXCT  ",
            "T  TXT  T" } };
    // endregion

    //spotless:on

    /**
     * Uniform tier of the 25 diffusion cells (0..3, -1 = no cell): decides the stable points (1/2/5/10) and the highest
     * hatch tier (EV/IV/LuV/no cap). The cell tier is published on {@link GTStructureChannels#LES_ESSENTIA_CELL}, so a
     * blueprint can select it with the {@code essentia_cell} sub-channel.
     */
    private int mCellTier = -1;
    /** Stable points of the diffusion cells; the EU per essentia unit is {@code base * stable / 25}. */
    protected int mStableValue = 0;
    /** Highest dynamo/input hatch tier the cells accept; {@link Integer#MAX_VALUE} with Grandmaster cells. */
    protected int mTierLimit = -1;
    /** EU left over from the previous tick, carried over instead of being voided. */
    protected long mLeftEnergy;
    /** Bit mask of installed upgrade categories, bit 0 (NORMAL) is on from the start. */
    private int mUpgrade = 1;

    protected final List<MTEssentiaHatchTileEntity> mEssentiaHatch = new ArrayList<>();
    private final XSTR random = new XSTR();

    public MTLargeEssentiaGenerator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setEnableWirelessFunc(false);
    }

    public MTLargeEssentiaGenerator(String aName) {
        super(aName);
        setEnableWirelessFunc(false);
    }

    // region Structure definition
    @Override
    public IStructureDefinition<MTLargeEssentiaGenerator> getStructureDefinition() {
        if (structureDefinition == null) {
            structureDefinition = StructureDefinition.<MTLargeEssentiaGenerator>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(STRUCTURE_STRING))
                .addElement('A', ofBlock(ConfigBlocks.blockCosmeticOpaque, 1))
                .addElement('T', ofBlock(ConfigBlocks.blockCosmeticSolid, 7))
                .addElement('C', Casings.MagicCasing.asElement())
                .addElement(
                    'E',
                    GTStructureChannels.LES_ESSENTIA_CELL.use(
                        ofBlocksTiered(
                            (block, meta) -> block == Loaders.essentiaCell && meta >= 0 && meta <= 3 ? meta : null,
                            ImmutableList.of(
                                Pair.of(Loaders.essentiaCell, 0),
                                Pair.of(Loaders.essentiaCell, 1),
                                Pair.of(Loaders.essentiaCell, 2),
                                Pair.of(Loaders.essentiaCell, 3)),
                            -1,
                            (t, tier) -> t.mCellTier = tier == null ? -1 : tier,
                            t -> t.mCellTier)))
                .addElement('X', buildXElement())
                .build();
        }
        return structureDefinition;
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<MTLargeEssentiaGenerator> buildXElement() {
        List<IStructureElement<MTLargeEssentiaGenerator>> elements = new ArrayList<>(4);
        elements.add(
            buildHatchAdder(MTLargeEssentiaGenerator.class)
                .atLeast(
                    HatchElement.InputHatch,
                    HatchElement.Dynamo.or(HatchElement.ExoticDynamo)
                        .or(HatchElement.LaserSource))
                .casingIndex(CASING_INDEX)
                .hint(1)
                .build());
        elements.add(Casings.MagicCasing.asElement());
        elements.add(
            ofSpecificTileAdder(
                MTLargeEssentiaGenerator::addEssentiaHatch,
                MTEssentiaHatchTileEntity.class,
                Loaders.magicCasing,
                0));
        // The ME variant only exists when Thaumic Energistics is installed; its class is touched here and nowhere else.
        if (MTBlocks.essentiaHatchMEBlock != null) {
            elements.add(
                ofSpecificTileAdder(
                    MTLargeEssentiaGenerator::addEssentiaHatch,
                    MTEssentiaHatchMETileEntity.class,
                    MTBlocks.essentiaHatchMEBlock,
                    0));
        }
        return ofChain(elements.toArray(new IStructureElement[0]));
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
    public void clearHatches() {
        super.clearHatches();
        mEssentiaHatch.clear();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // The cell scan writes these, so reset them before it: a half-failed check must not keep the previous values.
        mCellTier = -1;
        mStableValue = 0;
        mTierLimit = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;

        // The cells are one uniform tier: derive the stable points and the hatch tier cap from it.
        mStableValue = switch (mCellTier) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            default -> 0;
        };
        mTierLimit = switch (mCellTier) {
            case 0 -> 4;
            case 1 -> 5;
            case 2 -> 6;
            case 3 -> Integer.MAX_VALUE;
            default -> -1;
        };

        int dynamoCount = mDynamoHatches.size() + mExoticDynamoHatches.size();
        if (dynamoCount != 1) {
            errors.add(StructureErrors.of("machine.leg.structure.error.dynamo_count"));
        }
        // The structure hatch element only filters the preview; the counts it wants are checked here.
        if (mInputHatches.size() != 1) {
            errors.add(StructureErrors.of("machine.leg.structure.error.input_hatch"));
        }
        if (mEssentiaHatch.isEmpty()) {
            errors.add(StructureErrors.of("machine.leg.structure.error.no_essentia_hatch"));
        }
        if (mStableValue <= 0) {
            errors.add(StructureErrors.of("machine.leg.structure.error.no_cell"));
        }
        for (MTEHatchInput hatch : mInputHatches) {
            if (hatch.mTier > mTierLimit) {
                errors.add(StructureErrors.of("machine.leg.structure.error.hatch_tier"));
            }
        }
        for (MTEHatchDynamo hatch : mDynamoHatches) {
            if (hatch.mTier > mTierLimit) {
                errors.add(StructureErrors.of("machine.leg.structure.error.hatch_tier"));
            }
        }
        for (MTEHatch hatch : mExoticDynamoHatches) {
            if (hatch.mTier > mTierLimit) {
                errors.add(StructureErrors.of("machine.leg.structure.error.hatch_tier"));
            }
        }

        updateEssentiaHatchState();
        // Wireless output is a feature of the base class only; this machine never uses it.
        setEnableWirelessFunc(false);
    }

    public boolean addEssentiaHatch(MTEssentiaHatchTileEntity hatch) {
        return mEssentiaHatch.add(hatch);
    }

    private void updateEssentiaHatchState() {
        for (MTEssentiaHatchTileEntity hatch : mEssentiaHatch) {
            hatch.mState = mUpgrade;
        }
    }
    // endregion

    // region Upgrades
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.inventory.getCurrentItem();
            if (held != null && held.getItem() instanceof MTEssentiaUpgradeItem) {
                int upgrade = held.getItemDamage();
                if (upgrade > 0 && (mUpgrade & (1 << upgrade)) == 0) {
                    held.stackSize--;
                    mUpgrade |= 1 << upgrade;
                    GTUtility.sendChatTrans(aPlayer, "machine.leg.chat.upgrade", held.getDisplayName());
                }
                updateEssentiaHatchState();
                return true;
            }
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }
    // endregion

    // region Generation
    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        mEfficiency = 10000;
        mMaxProgresstime = 1;
        convertEssentia(getVoltageLimit(), getAmpLimit());
        return CheckRecipeResultRegistry.GENERATING;
    }

    /**
     * The generator has no recipe map of its own; this fake pool only shows the NEI pages (upgrade items with their
     * auxiliary fluids and the burnable aspects' base EU values). Returning it here is what makes GT register the LEG
     * as the NEI catalyst of those pages.
     */
    @Override
    public Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Collections.singletonList(MTRecipeMaps.legFakeRecipes);
    }

    /** @return Total output voltage of the single dynamo hatch (multi-amp and laser included). */
    public long getVoltageLimit() {
        long voltage = 0;
        for (MTEHatch hatch : mExoticDynamoHatches) {
            voltage = saturatedAdd(voltage, hatch.maxEUOutput());
        }
        for (MTEHatchDynamo hatch : mDynamoHatches) {
            voltage = saturatedAdd(voltage, hatch.maxEUOutput());
        }
        return voltage;
    }

    /** @return Total amperage the single dynamo hatch can output. */
    public long getAmpLimit() {
        long amps = 0;
        for (MTEHatch hatch : mExoticDynamoHatches) {
            amps = saturatedAdd(amps, hatch.maxAmperesOut());
        }
        for (MTEHatchDynamo hatch : mDynamoHatches) {
            amps = saturatedAdd(amps, hatch.maxAmperesOut());
        }
        return amps;
    }

    /** @return EU one unit of the aspect makes, after the stable point multiplier and its auxiliary fluid. */
    public long getPerAspectEnergy(Aspect aspect) {
        if (!isValidEssentia(aspect)) return 0;
        return switch (MTEssentiaEnergyData.getAspectTypeIndex(aspect)) {
            case 0 -> MTEssentiaEnergyData.getAspectFuelValue(aspect);
            case 1 -> airEssentia(aspect);
            case 2 -> thermalEssentia(aspect);
            case 3 -> unstableEssentia(aspect);
            case 4 -> victusEssentia(aspect);
            case 5 -> taintedEssentia(aspect);
            case 6 -> mechanicEssentia(aspect);
            case 7 -> spiritEssentia(aspect);
            case 8 -> radiationEssentia(aspect);
            case 9 -> electricEssentia(aspect);
            default -> 0;
        };
    }

    private long airEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 8;
        double multiplier = 0;
        if (deplete(Materials.LiquidAir.getFluid(ceo))) multiplier = 1.5D;
        else if (deplete(Materials.Air.getGas(ceo))) multiplier = 1.0D;
        return (long) (base * multiplier);
    }

    private long thermalEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 2;
        double multiplier = 0;
        if (deplete(Materials.SuperCoolant.getFluid(ceo))) multiplier = 9.0D;
        else if (deplete(FluidRegistry.getFluidStack("cryotheum", ceo))) multiplier = 5.0D;
        else if (deplete(FluidRegistry.getFluidStack("ic2coolant", ceo))) multiplier = 1.5D;
        else if (deplete(FluidRegistry.getFluidStack("ice", ceo))) multiplier = 1.2D;
        else if (deplete(FluidRegistry.getFluidStack("ic2distilledwater", ceo))) multiplier = 1.0D;
        else if (deplete(Materials.Water.getFluid(ceo))) multiplier = 0.5D;
        return (long) (base * multiplier);
    }

    private long unstableEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 4;
        double multiplier = 0;
        if (deplete(WerkstoffLoader.Xenon.getFluidOrGas(ceo))) multiplier = 4.0D;
        else if (deplete(WerkstoffLoader.Krypton.getFluidOrGas(ceo))) multiplier = 3.0D;
        else if (deplete(Materials.Argon.getFluid(ceo))) multiplier = 2.5D;
        else if (deplete(WerkstoffLoader.Neon.getFluidOrGas(ceo))) multiplier = 2.2D;
        else if (deplete(Materials.Helium.getFluid(ceo))) multiplier = 2.0D;
        else if (deplete(Materials.Nitrogen.getFluid(ceo))) multiplier = 1.0D;
        return (long) (base * multiplier);
    }

    private long victusEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 18;
        double multiplier = 1.0D;
        if (deplete(FluidRegistry.getFluidStack("xpjuice", ceo))) multiplier = 2.0D;
        else if (deplete(FluidRegistry.getFluidStack("lifeessence", ceo))) multiplier = 6.0D;
        return (long) (base * multiplier);
    }

    private long taintedEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 3;
        double multiplier = 1.0D;
        int fluxChance = 2000;
        if (deplete(FluidRegistry.getFluidStack("fluidpure", ceo))) {
            multiplier = 60.0D;
            fluxChance = 0;
        } else if (deplete(FluidRegistry.getFluidStack("fluiddeath", ceo))) {
            multiplier = Math.pow(25000D / base, 4);
            fluxChance = 4000;
        }
        if (fluxChance > 0 && random.nextInt(10000) < fluxChance && getBaseMetaTileEntity() != null) {
            World world = getBaseMetaTileEntity().getWorld();
            if (world != null) {
                int x = getBaseMetaTileEntity().getXCoord() + random.nextInt(5) - 2;
                int y = getBaseMetaTileEntity().getYCoord();
                int z = getBaseMetaTileEntity().getZCoord() + random.nextInt(5) - 2;
                if (world.isAirBlock(x, y, z)) {
                    world.setBlock(x, y, z, ConfigBlocks.blockFluxGas, random.nextInt(8), 3);
                }
            }
        }
        return (long) (base * multiplier);
    }

    private long mechanicEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 20;
        double multiplier = 0;
        if (deplete(Materials.Lubricant.getFluid(ceo))) multiplier = 1.0D;
        return (long) (base * multiplier);
    }

    private long spiritEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 2;
        double multiplier = 1.0D;
        if (deplete(FluidRegistry.getFluidStack("witchery:fluidspirit", ceo))) {
            multiplier = 10D * (1 + mStableValue / 100D);
        } else if (deplete(FluidRegistry.getFluidStack("witchery:hollowtears", ceo))) {
            multiplier = 15D * (1 + 100D / mStableValue);
        }
        return (long) (base * multiplier);
    }

    private long radiationEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        int ceo = (int) MTEssentiaEnergyData.getAspectCeo(aspect) * 6;
        double multiplier = 1.0D;
        if (deplete(Materials.Caesium.getMolten(ceo))) multiplier = 2.0D;
        else if (deplete(Materials.Uranium235.getMolten(ceo))) multiplier = 3.0D;
        else if (deplete(Materials.Naquadah.getMolten(ceo))) multiplier = 4.0D;
        else if (deplete(GGMaterial.atomicSeparationCatalyst.getMolten(ceo))) multiplier = 16.0D;
        return (long) (base * multiplier);
    }

    private long electricEssentia(Aspect aspect) {
        long base = MTEssentiaEnergyData.getAspectFuelValue(aspect);
        double multiplier = Math.pow(3.0D, GTUtility.getTier(getVoltageLimit()));
        return (long) (base * multiplier);
    }

    /** {@code depleteInput} does not take a null stack (and the optional fluids can be absent). */
    private boolean deplete(FluidStack stack) {
        return stack != null && depleteInput(stack);
    }

    /**
     * Burns stored essentia into {@link #lEUt}, keeping the EU that does not fit into the hatch capacity for the next
     * tick. Voltage is the hatch voltage, amperage fills up to the hatch amperage.
     */
    public void convertEssentia(long voltageLimit, long ampLimit) {
        long eu = mLeftEnergy;
        long capacity = saturatedMultiply(voltageLimit, ampLimit);

        for (MTEssentiaHatchTileEntity hatch : mEssentiaHatch) {
            AspectList aspects = hatch.getAspects();
            for (Aspect aspect : aspects.getAspects()) {
                if (!isValidEssentia(aspect)) continue;
                while (eu <= capacity && aspects.getAmount(aspect) > 0) {
                    long added = saturatedMultiply(getPerAspectEnergy(aspect), mStableValue) / 25;
                    if (added <= 0) break;
                    eu = saturatedAdd(eu, added);
                    aspects.reduce(aspect, 1);
                }
            }
        }

        long euVoltage;
        long euAmp;
        if (eu <= voltageLimit) {
            euVoltage = eu;
            euAmp = 1;
            mLeftEnergy = 0;
        } else {
            euVoltage = voltageLimit;
            euAmp = 1;
            while (euAmp + 1 <= ampLimit && saturatedMultiply(euVoltage, euAmp + 1) <= eu) {
                euAmp++;
            }
            mLeftEnergy = eu - saturatedMultiply(euVoltage, euAmp);
        }
        lEUt = saturatedMultiply(euVoltage, euAmp);
    }

    public boolean isValidEssentia(Aspect aspect) {
        int type = MTEssentiaEnergyData.getAspectTypeIndex(aspect);
        return type != MTEssentiaEnergyData.TYPE_INVALID && (mUpgrade & (1 << type)) != 0;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    private static long saturatedMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        long result = a * b;
        return result / b != a ? Long.MAX_VALUE : result;
    }
    // endregion

    // region Persistence
    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setInteger("mStableValue", mStableValue);
        tag.setLong("mLeftEnergy", mLeftEnergy);
        tag.setInteger("mUpgrade", mUpgrade);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        mStableValue = tag.getInteger("mStableValue");
        mLeftEnergy = tag.getLong("mLeftEnergy");
        mUpgrade = tag.getInteger("mUpgrade");
        if (mUpgrade == 0) mUpgrade = 1;
        setEnableWirelessFunc(false);
    }
    // endregion

    // region Rendering / registration
    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTLargeEssentiaGenerator(mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
        if (side != facing) return new ITexture[] { casing };
        if (aActive) {
            return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.MACHINE_CASING_DRAGONEGG),
                TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.MACHINE_CASING_DRAGONEGG_GLOW)
                    .glow()
                    .build() };
        }
        return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.MACHINE_CASING_DRAGONEGG) };
    }

    @Override
    public ITexture getCasingTexture() {
        return Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
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
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.leg.machinetype"))
            .addInfo(translateToLocal("machine.leg.tooltip.desc"))
            .addInfo(translateToLocal("machine.leg.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.leg.tooltip.fuel.header"))
            .addInfo(translateToLocal("machine.leg.tooltip.fuel.cells"))
            .addInfo(translateToLocal("machine.leg.tooltip.fuel.upgrade"))
            .addInfo(translateToLocal("machine.leg.tooltip.fuel.fluids"))
            .addSeparator()
            .addInfo(translateToLocal("machine.leg.tooltip.output.header"))
            .addInfo(translateToLocal("machine.leg.tooltip.output.hatch"))
            .addInfo(translateToLocal("machine.leg.tooltip.output.overmax"))
            .addInfo(translateToLocal("machine.leg.tooltip.output.wireless"))
            .addSeparator()
            .addInfo(translateToLocal("machine.leg.tooltip.structure.header"))
            .addInfo(translateToLocal("machine.leg.tooltip.structure.cells"))
            .addInfo(translateToLocal("machine.leg.tooltip.structure.cells.limit"))
            .addInfo(translateToLocal("machine.leg.tooltip.structure.blocks"))
            .addInfo(translateToLocal("machine.leg.tooltip.structure.hatches"))
            .addSeparator()
            .addInfo(translateToLocal("machine.leg.tooltip.details"))
            .addInfo(translateToLocal("machine.leg.tooltip.details.hint"))
            .beginStructureBlock(9, 3, 9, true)
            .addController(translateToLocal("machine.leg.tooltip.controller"))
            .addCasingInfoMin(translateToLocal("machine.leg.tooltip.cells_name"), 25, true)
            .addDynamoHatch("1", translateToLocal("machine.leg.tooltip.hatch_info"), 1)
            .addInputHatch("1", translateToLocal("machine.leg.tooltip.hatch_info"), 1)
            .addOtherStructurePart(
                translateToLocal("machine.leg.tooltip.essentia_hatch"),
                translateToLocal("machine.leg.tooltip.hatch_info"),
                1);
        if (MTBlocks.essentiaHatchMEBlock != null) {
            tt.addOtherStructurePart(
                translateToLocal("machine.leg.tooltip.essentia_hatch_me"),
                translateToLocal("machine.leg.tooltip.hatch_info"),
                1);
        }
        tt.addSubChannel(GTStructureChannels.LES_ESSENTIA_CELL)
            .toolTipFinisher();
        return tt;
    }
    // endregion
}
