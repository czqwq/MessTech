package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofSpecificTileAdder;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.MessTech.common.block.MTBlocks;
import com.MessTech.common.gui.MTSmallEssentiaSmelteryGui;
import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.MessTech.common.thaumcraft.MTEssentiaOutputHatchMETileEntity;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import goodgenerator.blocks.tileEntity.MTEEssentiaOutputHatch;
import goodgenerator.loader.Loaders;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Mods;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Small Essentia Smeltery (小型源质冶炼厂): a compact, passively cooled essentia smeltery. Items and fluids are melted
 * into Thaumcraft essentia exactly like GoodGenerator's Large Essentia Smeltery does (one parallel unit per item or
 * bucket, Thaumcraft's object/bonus aspect tags drive the output), but without the large smeltery's vis-node power
 * requirement, pollution and flux gas.
 *
 * <p>
 * The seven Essentia Diffusion Cells on the central axis decide the machine's level: the structure needs one uniform
 * tier (0 = Novice .. 3 = Grandmaster). Each level multiplies the duration by 0.5 and the parallel by 4 (level 0 = 1x
 * duration and 1 parallel, level 3 = 0.125x duration and 64 parallel). When every Alchemical Construct in the
 * structure is the advanced alchemical sheet variant, the machine unlocks Perfect Overclock.
 */
public class MTSmallEssentiaSmeltery extends MTMultiMachineBase<MTSmallEssentiaSmeltery>
    implements ISurvivalConstructable {

    // region Structure piece offsets
    // '~' (controller): char index 1 of X, layer index 1 of Y, inner string index 0 of Z.
    private static final int HORIZONTAL_OFFSET = 1;
    private static final int VERTICAL_OFFSET = 1;
    private static final int DEPTH_OFFSET = 0;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";
    /** Magic Casing texture (GoodGenerator texture page 12, index 0). */
    private static final int CASING_INDEX = 12 << 7;

    /** GoodGenerator's Large Essentia Smeltery duration per essentia unit. */
    private static final int BASE_TICKS_PER_ESSENTIA = 32;
    /** GoodGenerator's Large Essentia Smeltery base EU/t. */
    private static final long BASE_RECIPE_EUT = 480;
    /**
     * GT batch mode fill target: one run is stretched to at most this many ticks instead of being clamped at 1 tick
     * (the same constant as {@code MTParallelHelper#MAX_BATCH_MODE_TICK_TIME}).
     */
    private static final int MAX_BATCH_MODE_TICK_TIME = 128;

    // spotless:off
    private static final String[][] structure_string = new String[][]{
        {"BBB","BCB","BCB","BCB","BCB","BCB","BCB","BCB","BBB"},
        {"B~B","DAD","DAD","DAD","DAD","DAD","DAD","DAD","BBB"},
        {"BBB","BBB","BBB","BBB","BBB","BBB","BBB","BBB","BBB"},

    };
    // spotless:on

    private static final IIconContainer FRONT_ACTIVE = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "icons/LargeEssentiaSmeltery_On");
    private static final IIconContainer FRONT_ACTIVE_GLOW = Textures.BlockIcons
        .customOptional(Mods.GregTech.resourceDomain, "icons/LargeEssentiaSmeltery_On_GLOW");
    private static final IIconContainer FRONT_INACTIVE = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "icons/LargeEssentiaSmeltery_Off");
    private static final IIconContainer FRONT_INACTIVE_GLOW = Textures.BlockIcons
        .customOptional(Mods.GregTech.resourceDomain, "icons/LargeEssentiaSmeltery_Off_GLOW");

    /** Uniform tier of the 7 Essentia Diffusion Cells (0..3, -1 = no cell); decides speed and parallel. */
    private int mCellTier = -1;
    /** Uniform tier of the alchemical constructs (0 = Alchemical Construct, 1 = advanced sheet, -1 = unset). */
    private int mAlchemicalTier = -1;

    /** Essentia produced by the running recipe, flushed into the output hatches when it finishes. */
    private AspectList mOutputAspects = new AspectList();
    private final List<MTEEssentiaOutputHatch> mEssentiaOutputHatches = new ArrayList<>();
    private final List<MTEssentiaOutputHatchMETileEntity> mEssentiaOutputHatchesME = new ArrayList<>();

    private IStructureDefinition<MTSmallEssentiaSmeltery> structureDefinition;

    public MTSmallEssentiaSmeltery(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTSmallEssentiaSmeltery(String aName) {
        super(aName);
    }

    // region Structure definition
    @Override
    public IStructureDefinition<MTSmallEssentiaSmeltery> getStructureDefinition() {
        if (structureDefinition == null) {
            structureDefinition = StructureDefinition.<MTSmallEssentiaSmeltery>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
                .addElement(
                    'A',
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
                .addElement('B', buildBElement())
                .addElement(
                    'C',
                    ofBlocksTiered(
                        (block, meta) -> block == Casings.AlchemicalConstructTiered.getBlock() ? switch (meta) {
                        case 9 -> 0;
                        case 3 -> 1;
                        default -> null;
                        } : null,
                        ImmutableList.of(
                            Pair.of(Casings.AlchemicalConstructTiered.getBlock(), 9),
                            Pair.of(Casings.AlchemicalConstructTiered.getBlock(), 3)),
                        -1,
                        (t, tier) -> t.mAlchemicalTier = tier == null ? -1 : tier,
                        t -> t.mAlchemicalTier))
                .addElement('D', ofBlock(ConfigBlocks.blockCosmeticOpaque, 2))
                .build();
        }
        return structureDefinition;
    }

    /** 'B': any base hatch or essentia output hatch, else the Magic Casing fill block. */
    @SuppressWarnings("unchecked")
    private static IStructureElement<MTSmallEssentiaSmeltery> buildBElement() {
        List<IStructureElement<MTSmallEssentiaSmeltery>> elements = new ArrayList<>(5);
        elements.add(
            buildHatchAdder(MTSmallEssentiaSmeltery.class)
                .atLeast(
                    HatchElement.Energy.or(HatchElement.ExoticEnergy),
                    HatchElement.InputBus,
                    HatchElement.InputHatch)
                .casingIndex(CASING_INDEX)
                .hint(1)
                .build());
        elements.add(
            ofSpecificTileAdder(
                MTSmallEssentiaSmeltery::addEssentiaOutputHatch,
                MTEEssentiaOutputHatch.class,
                Loaders.essentiaOutputHatch,
                0));
        // The ME variant only exists when Thaumic Energistics is installed; its class is touched here and nowhere
        // else.
        if (MTBlocks.essentiaOutputHatchMEBlock != null) {
            elements.add(
                ofSpecificTileAdder(
                    MTSmallEssentiaSmeltery::addEssentiaOutputHatchME,
                    MTEssentiaOutputHatchMETileEntity.class,
                    MTBlocks.essentiaOutputHatchMEBlock,
                    0));
        }
        elements.add(Casings.MagicCasing.asElement());
        return ofChain(elements.toArray(new IStructureElement[0]));
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
    // endregion

    @Override
    public void clearHatches() {
        super.clearHatches();
        mEssentiaOutputHatches.clear();
        mEssentiaOutputHatchesME.clear();
    }

    // region Structure check
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // The element scan writes these, so reset them first: a half-failed check must not keep stale tiers.
        mCellTier = -1;
        mAlchemicalTier = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;

        checkHasAnyEnergy(errors);
        checkHasAnyInput(errors);
        if (mEssentiaOutputHatches.isEmpty() && mEssentiaOutputHatchesME.isEmpty()) {
            errors.add(StructureErrors.of("machine.smallessentiasmeltery.need_output_hatch"));
        }
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // Not a recipemap machine: checkProcessing() melts the stored inputs itself.
        return null;
    }

    private boolean addEssentiaOutputHatch(MTEEssentiaOutputHatch hatch) {
        return hatch != null && mEssentiaOutputHatches.add(hatch);
    }

    private boolean addEssentiaOutputHatchME(MTEssentiaOutputHatchMETileEntity hatch) {
        return hatch != null && mEssentiaOutputHatchesME.add(hatch);
    }

    /** @return Essentia Diffusion Cell level, 0..3; -1 before the first structure check counts as 0. */
    private int getDiffusionLevel() {
        return Math.max(0, Math.min(3, mCellTier));
    }

    /** @return Parallel per operation: 4^level (1 / 4 / 16 / 64). */
    private int getParallel() {
        return 1 << (2 * getDiffusionLevel());
    }

    /** @return Duration multiplier: 0.5^level (level 0..3 -> 1 / 0.5 / 0.25 / 0.125). */
    private double getDurationMultiplier() {
        return 1.0D / (1 << getDiffusionLevel());
    }

    /**
     * @return Perfect Overclock is unlocked by replacing every Alchemical Construct with advanced alchemical sheets.
     */
    private boolean hasPerfectOverclock() {
        return mAlchemicalTier == 1;
    }
    // endregion

    // region Recipe processing
    /**
     * GoodGenerator's Large Essentia Smeltery conversion, one parallel unit per item (or per 1000 L of fluid): the
     * item's Thaumcraft object tags plus bonus tags become the output essentia, an item without tags yields 1 Entropy.
     * <p>
     * With GT's batch mode enabled the run is stretched towards {@link #MAX_BATCH_MODE_TICK_TIME} ticks by melting more
     * units in the same run (GT batch semantics: EU/t stays that of a single batch, the longer duration pays for the
     * extra units), so overclocking cannot leave the machine stuck on the 1-tick wall.
     */
    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;

        AspectList output = new AspectList();
        int consumed = consumeInputs(getParallel(), output);
        if (consumed <= 0) return CheckRecipeResultRegistry.NO_RECIPE;

        OverclockCalculator calculator = buildOverclockCalculator(output.visSize());
        if (isBatchModeEnabled() && calculator.getDuration() < MAX_BATCH_MODE_TICK_TIME) {
            double batchMultiplierMax = MAX_BATCH_MODE_TICK_TIME / (double) calculator.getDuration();
            int maxExtraUnits = (int) Math
                .floor(consumed * Math.min(batchMultiplierMax - 1.0D, getMaxBatchSize() - 1.0D));
            if (maxExtraUnits > 0 && consumeInputs(maxExtraUnits, output) > 0) {
                calculator = buildOverclockCalculator(output.visSize());
            }
        }

        mOutputAspects = output;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        lEUt = -Math.max(1L, calculator.getConsumption());
        mMaxProgresstime = Math.max(1, calculator.getDuration());
        mProgresstime = 0;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** @return An overclocked calculator for one run that yields {@code essentiaPoints} points of essentia. */
    private OverclockCalculator buildOverclockCalculator(int essentiaPoints) {
        int baseDuration = Math.max(1, (int) Math.ceil(essentiaPoints * (double) BASE_TICKS_PER_ESSENTIA));
        int duration = Math.max(1, (int) Math.ceil(baseDuration * getDurationMultiplier()));
        OverclockCalculator calculator = new OverclockCalculator().setRecipeEUt(BASE_RECIPE_EUT)
            .setEUt(getMaxInputEu())
            .setDuration(duration);
        if (hasPerfectOverclock()) calculator.enablePerfectOC();
        return calculator.calculate();
    }

    /**
     * Melts up to {@code maxUnits} parallel units (1 item or 1000 L of fluid each) into {@code output}. Inputs that
     * cannot be depleted (e.g. a controller circuit) are skipped.
     *
     * @return How many units were actually consumed.
     */
    private int consumeInputs(int maxUnits, AspectList output) {
        int remaining = maxUnits;
        ArrayList<ItemStack> items = getStoredInputs();
        for (int i = items.size() - 1; i >= 0 && remaining > 0; i--) {
            ItemStack stack = items.get(i);
            if (stack == null || stack.stackSize <= 0) continue;
            int take = Math.min(stack.stackSize, remaining);
            ItemStack consumed = stack.copy();
            consumed.stackSize = take;
            if (!depleteInput(consumed)) continue;
            addAspects(output, getEssentia(stack, take));
            remaining -= take;
        }

        ArrayList<FluidStack> fluids = getStoredFluids();
        for (int i = fluids.size() - 1; i >= 0 && remaining > 0; i--) {
            FluidStack fluid = fluids.get(i);
            if (fluid == null || fluid.amount < 1000) continue;
            int buckets = Math.min(fluid.amount / 1000, remaining);
            AspectList aspects = getEssentia(fluid, buckets);
            if (aspects.size() == 0) continue;
            FluidStack consumed = fluid.copy();
            consumed.amount = buckets * 1000;
            if (!depleteInput(consumed)) continue;
            addAspects(output, aspects);
            remaining -= buckets;
        }
        return maxUnits - remaining;
    }

    /** Adds {@code amount} items' worth of the item's essentia tags to {@code output}. */
    private static AspectList getEssentia(ItemStack stack, int amount) {
        AspectList result = new AspectList();
        AspectList aspects = ThaumcraftCraftingManager.getObjectTags(stack);
        aspects = ThaumcraftCraftingManager.getBonusTags(stack, aspects);
        if (aspects != null) {
            for (Aspect aspect : aspects.getAspects()) {
                // TC can return null entries for some item tags; a null aspect in the output list can stall it.
                if (aspect == null) continue;
                int perItem = aspects.getAmount(aspect);
                if (perItem <= 0) continue;
                result.add(aspect, (int) Math.min(Integer.MAX_VALUE, (long) perItem * amount));
            }
        }
        if (result.size() == 0) result.add(Aspect.ENTROPY, amount);
        return result;
    }

    /** GoodGenerator's fluid conversion: the fluid's block item is looked up in Thaumcraft's object tags. */
    private static AspectList getEssentia(FluidStack fluid, int buckets) {
        net.minecraft.block.Block fluidBlock = fluid.getFluid()
            .getBlock();
        if (fluidBlock == null) return new AspectList();
        return getEssentia(new ItemStack(fluidBlock), buckets);
    }

    private static void addAspects(AspectList target, AspectList source) {
        for (Aspect aspect : source.getAspects()) {
            if (aspect == null) continue;
            int amount = source.getAmount(aspect);
            if (amount > 0) target.add(aspect, amount);
        }
    }
    // endregion

    // region Output
    @Override
    protected void outputAfterRecipe() {
        super.outputAfterRecipe();
        fillEssentiaOutputHatches();
        mOutputAspects = new AspectList();
    }

    /**
     * Empties {@link #mOutputAspects} into every essentia output hatch in turn; whatever no hatch takes is voided,
     * exactly like the Large Essentia Smeltery's own {@code fillEssentiaOutputHatch}.
     */
    private void fillEssentiaOutputHatches() {
        if (mOutputAspects == null || mOutputAspects.size() == 0) return;
        for (MTEEssentiaOutputHatch hatch : mEssentiaOutputHatches) {
            if (hatch == null || hatch.isInvalid()) continue;
            addToHatch(hatch);
        }
        for (MTEssentiaOutputHatchMETileEntity hatch : mEssentiaOutputHatchesME) {
            if (hatch == null || hatch.isInvalid()) continue;
            addToHatch(hatch);
        }
        mOutputAspects.aspects.clear();
    }

    private void addToHatch(IEssentiaTransport hatch) {
        for (Aspect aspect : mOutputAspects.getAspects()) {
            if (aspect == null) continue;
            int stored = mOutputAspects.getAmount(aspect);
            if (stored <= 0) continue;
            int accepted = hatch.addEssentia(aspect, stored, null);
            if (accepted > 0) mOutputAspects.remove(aspect, Math.min(accepted, stored));
        }
    }
    // endregion

    // region Persistence
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        NBTTagCompound output = new NBTTagCompound();
        mOutputAspects.writeToNBT(output);
        aNBT.setTag("mOutputAspects", output);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mOutputAspects = new AspectList();
        if (aNBT.hasKey("mOutputAspects")) {
            mOutputAspects.readFromNBT(aNBT.getCompoundTag("mOutputAspects"));
        }
    }
    // endregion

    // region Rendering / registration
    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTSmallEssentiaSmeltery(mName);
    }

    /**
     * The essentia the running recipe will flush into the output hatches, as {@code tag|amount;tag|amount} rows sorted
     * by amount (see {@code MTSmallEssentiaSmelteryGui}). Empty when no recipe is running.
     */
    public String getPendingOutputAspectsText() {
        AspectList output = mOutputAspects;
        if (output == null || output.size() == 0) return "";
        Aspect[] sorted = output.getAspectsSortedAmount();
        if (sorted == null) return "";
        StringBuilder text = new StringBuilder();
        for (Aspect aspect : sorted) {
            if (aspect == null) continue;
            int amount = output.getAmount(aspect);
            if (amount <= 0) continue;
            if (text.length() > 0) text.append(';');
            text.append(aspect.getTag())
                .append('|')
                .append(amount);
        }
        return text.toString();
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTSmallEssentiaSmelteryGui(this);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.smallessentiasmeltery.machinetype"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.desc"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.cell.header"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.cell.tier"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.cell.speed"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.cell.parallel"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.cell.poc"))
            .addSeparator()
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.process.header"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.process.input"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.process.duration"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.process.output"))
            .addSeparator()
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.structure.header"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.structure.shell"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.structure.constructs"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.structure.cells"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.structure.hatches"))
            .addSeparator()
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.details"))
            .addInfo(translateToLocal("machine.smallessentiasmeltery.tooltip.details.hint"))
            .beginStructureBlock(3, 3, 9, false)
            .addController(translateToLocal("machine.smallessentiasmeltery.tooltip.controller"))
            .addCasingInfoMin(translateToLocal("machine.smallessentiasmeltery.tooltip.cell_name"), 7, true)
            .addInputBus("1+", translateToLocal("machine.smallessentiasmeltery.tooltip.hatch_info"), 1)
            .addInputHatch("1+", translateToLocal("machine.smallessentiasmeltery.tooltip.hatch_info"), 1)
            .addEnergyHatch("1+", translateToLocal("machine.smallessentiasmeltery.tooltip.hatch_info"), 1)
            .addOtherStructurePart(
                translateToLocal("machine.smallessentiasmeltery.tooltip.output_hatch"),
                translateToLocal("machine.smallessentiasmeltery.tooltip.hatch_info"),
                1);
        if (MTBlocks.essentiaOutputHatchMEBlock != null) {
            tt.addOtherStructurePart(
                translateToLocal("machine.smallessentiasmeltery.tooltip.output_hatch_me"),
                translateToLocal("machine.smallessentiasmeltery.tooltip.hatch_info"),
                1);
        }
        tt.addSubChannel(GTStructureChannels.LES_ESSENTIA_CELL)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture casing = TextureFactory.of(Casings.MagicCasing.getBlock(), Casings.MagicCasing.getBlockMeta());
        if (side == facing) {
            if (aActive) {
                return new ITexture[] { casing, TextureFactory.of(FRONT_ACTIVE), TextureFactory.builder()
                    .addIcon(FRONT_ACTIVE_GLOW)
                    .glow()
                    .build() };
            }
            return new ITexture[] { casing, TextureFactory.of(FRONT_INACTIVE), TextureFactory.builder()
                .addIcon(FRONT_INACTIVE_GLOW)
                .glow()
                .build() };
        }
        return new ITexture[] { casing };
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return hasPerfectOverclock();
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        return getParallel();
    }
    // endregion
}
