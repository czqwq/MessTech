package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.ofFrame;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.MTComprehensiveMiningPlantGui;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import bwcrossmod.galacticgreg.VoidMinerUtility.DropMap;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IChunkLoader;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.objects.GTChunkManager;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.UndergroundOil;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.WirelessNetworkManager;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * 综合采掘工厂 (Comprehensive Mining Plant), ported from Elinar's standalone verification mod and then given a real
 * structure: an 11×10×10 mining tower whose controller sits on its front face.
 * <p>
 * The machine's own {@link #onPostTick} does the work, one second at a time (the GT recipe cycle is the clock):
 * <ul>
 * <li>ores: {@link VoidMinerUtility}'s per-dimension drop map (the one the GT Void Miners roll on), 64 ores per second
 * per ore parallel;</li>
 * <li>fluids: GT's underground oil field ({@link GTMod#proxy}{@code .mUndergroundOil}), {@code vein amount / 5000} per
 * second per fluid parallel;</li>
 * <li>power: no energy hatch - the personal wireless EU network ({@link WirelessNetworkManager}) is charged in bulk, a
 * two-minute buffer being topped up once a minute;</li>
 * <li>output: no AE shortcut - every second's ores and fluids are handed to GT as {@code mOutputItems} /
 * {@code mOutputFluids}, i.e. they go out through the output buses and output hatches placed in the structure's
 * {@code F} region. At least one of each is required, so neither items nor fluids are silently voided;</li>
 * <li>chunk: the machine keeps its own working chunk loaded ({@link IChunkLoader}).</li>
 * </ul>
 * The structure (controller at {@code 5, 7, 4}, i.e. front face, 8th layer up, centred):
 *
 * <pre>
 * A = Steel Gearbox Casing            (main casing)
 * B = Steel frame                     (core columns)
 * C = Tritanium frame                 (tower skeleton)
 * D = HSLA Steel block                (core)
 * E = Steel block                     (core)
 * F = High Power Casing               (hatch region: at least one output bus and one output hatch)
 * G = Rebolted Naquadah Casing        (advanced casing ring; the structure scanner sees it as the
 *                                      BWTileEntityMetaGeneratedBlocksCasingAdvanced special tile)
 * </pre>
 *
 * The controller itself and the hatch backgrounds use the High Power Casing texture: it is a TecTech casing texture
 * (page 8, id 1024) that stays inside the {@code casingTexturePages[128][128]} range hatch backgrounds can index. The
 * Rebolted Naquadah Casing is only a structure block - its texture id (32090) cannot be used as a hatch casing index.
 * <p>
 * All visible text is language-keyed (see {@code machine.comprehensiveminingplant.*} in both lang files); the GUI and
 * WAILA only ever carry raw, locale free values (numbers, fluid registry names) and format them client side.
 */
public class MTComprehensiveMiningPlant extends MTEEnhancedMultiBlockBase<MTComprehensiveMiningPlant>
    implements IChunkLoader, ISurvivalConstructable, ICasingTextureProvider {

    /** EU/t of one ore parallel or one fluid parallel (LuV voltage, as in the source mod). */
    public static final int EU_PER_PARALLEL = 32768;
    /** Ores per second of one ore parallel. */
    public static final int ORE_PER_SECOND_PER_PARALLEL = 64;
    /** Upper bound of both parallel settings, as in the source mod. */
    public static final int MAX_PARALLEL = 1024;
    /** The number of fluid rate rows the GUI shows; further fluids are not listed. */
    public static final int FLUID_LINE_COUNT = 10;

    private static final int TICKS_PER_SECOND = 20;
    /** Ticks between two refills of the wireless EU buffer. */
    private static final int REFILL_INTERVAL = 1200;
    /** The underground-oil vein amount is divided by this to get the extraction rate. */
    private static final int OIL_DIVIDER = UndergroundOil.DIVIDER;
    private static final String TAG_OWNER = "owner";

    // region Structure
    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFFSET = 5;
    private static final int VERTICAL_OFFSET = 7;
    private static final int DEPTH_OFFSET = 4;

    // spotless:off
    /** The shape of the structure: {@code [y][z]} layers of rows, each string spanning x, as scanned in game. */
    private static final String[][] structure_string = new String[][]{
        {"           ","           ","           ","           ","           ","           ","     C     ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","     C     ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","     C     ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","     C     ","    CDC    ","     C     ","           ","           "},
        {"           ","           ","           ","           ","     C     ","     A     ","   CADAC   ","     A     ","     C     ","           "},
        {"           ","           ","           ","           ","     C     ","     B     ","   CBDBC   ","     B     ","     C     ","           "},
        {"           ","           ","           ","           ","     C     ","     A     ","   CADAC   ","     A     ","     C     ","           "},
        {"           ","           ","           ","           ","    F~F    ","   FGGGF   ","   FGDGF   ","   FGGGF   ","    FFF    ","           "},
        {"           ","           ","           ","           ","   F   F   ","     E     ","    EDE    ","     E     ","   F   F   ","           "},
        {"           ","           ","           ","           ","   F   F   ","           ","     D     ","           ","   F   F   ","           "},
    };
    // spotless:on

    private static final IStructureDefinition<MTComprehensiveMiningPlant> STRUCTURE_DEFINITION = StructureDefinition
        .<MTComprehensiveMiningPlant>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        .addElement('A', Casings.SteelGearBoxCasing.asElement())
        .addElement('B', ofFrame(Materials.Steel))
        .addElement('C', ofFrame(Materials.Tritanium))
        .addElement('D', ofBlock(GregTechAPI.sBlockMetal3, 8))
        .addElement('E', ofBlock(GregTechAPI.sBlockMetal6, 13))
        // The hatch ring: output buses and output hatches go here, High Power Casing fills the rest. Both the fallback
        // block and the hatch background use HighPowerCasing: its casing texture id (texturePage 8, i.e. 1024) is
        // inside the range MTEHatch#updateTexture can address. A BartWorks advanced casing id (32xxx) would be pushed
        // through as `texturePage = id >> 7` without masking and crash the hatch renderer with an
        // ArrayIndexOutOfBoundsException (casingTexturePages[250] of length 128).
        .addElement(
            'F',
            HatchElementBuilder.<MTComprehensiveMiningPlant>builder()
                .atLeast(HatchElement.OutputBus, HatchElement.OutputHatch)
                .casingIndex(Casings.HighPowerCasing.getTextureId())
                .hint(1)
                .buildAndChain(Casings.HighPowerCasing.asElement()))
        .addElement('G', Casings.ReboltedNaquadahCasing.asElement())
        .build();
    // endregion

    // region Working state
    /** Parallels of the current run (what the machine is actually drawing power for). */
    private int oreParallel = 1;
    private int fluidParallel = 1;
    /** Parallels the GUI set; they take effect on the next start of the machine. */
    private int configuredOre = 1;
    private int configuredFluid = 1;
    /** Stored EU, filled in bulk from the wireless network; spares the per-tick map access. */
    private long energyBuffer;
    private long workTicks;
    /** Owner of the wireless network the machine charges, saved so a reload keeps billing the same network. */
    private UUID owner;
    private transient boolean wasWorking;
    // endregion

    // region Server side runtime data
    /**
     * Ores of the current dimension, resolved when a run starts; {@code null} when the dimension cannot be void mined.
     */
    private transient DropMap dropMap;
    /** Fluids of the current dimension's underground oil field and their rates, mB/s. */
    private transient final HashMap<Fluid, Long> fluidRates = new HashMap<>();
    private boolean chunkLoaded;
    // endregion

    public MTComprehensiveMiningPlant(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTComprehensiveMiningPlant(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTComprehensiveMiningPlant(mName);
    }

    // region No maintenance
    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public final boolean shouldCheckMaintenance() {
        return false;
    }

    @Override
    public void checkMaintenance() {}

    /**
     * Must stay {@code true}. GT's {@code runMachine} guard is
     * {@code mMaxProgresstime > 0 && doRandomMaintenanceDamage()}, so the source mod's {@code return false} stopped the
     * recipe tick altogether: the one-second cycle never completed and GT never asked for the next one.
     */
    @Override
    public boolean doRandomMaintenanceDamage() {
        return true;
    }
    // endregion

    // region Structure
    @Override
    public IStructureDefinition<MTComprehensiveMiningPlant> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;

        // atLeast(...) on the hatch element is only a preview filter, so the requirement is enforced here: items need a
        // bus and fluids need an output hatch, otherwise GT would silently void that half of the production.
        if (mOutputBusses.isEmpty()) {
            errors.add(StructureErrors.of("machine.comprehensiveminingplant.structure.error.no_output_bus"));
        }
        if (mOutputHatches.isEmpty()) {
            errors.add(StructureErrors.of("machine.comprehensiveminingplant.structure.error.no_output_hatch"));
        }
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
    // endregion

    // region Texture
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return Textures.BlockIcons.createTextureWithCasing(
                this,
                side,
                aFacing,
                aActive,
                Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL,
                Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW);
        }
        return new ITexture[] { getCasingTexture() };
    }

    /**
     * @return the base texture of the controller and of every block the machine adds itself: High Power Casing, as
     *         requested - the same casing {@code F} uses, so the hatch region and the controller read as one machine.
     */
    @Override
    public ITexture getCasingTexture() {
        return Casings.HighPowerCasing.getCasingTexture();
    }
    // endregion

    // region GUI
    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTComprehensiveMiningPlantGui(this);
    }
    // endregion

    // region Parallel settings (GUI)
    /** @return the ore parallel the GUI set; it applies when the machine (re)starts. */
    public int getConfiguredOre() {
        return configuredOre;
    }

    public void setConfiguredOre(int value) {
        configuredOre = Math.max(1, Math.min(MAX_PARALLEL, value));
        markMachineDirty();
    }

    /** @return the fluid parallel the GUI set; 0 disables fluid extraction. */
    public int getConfiguredFluid() {
        return configuredFluid;
    }

    public void setConfiguredFluid(int value) {
        configuredFluid = Math.max(0, Math.min(MAX_PARALLEL, value));
        markMachineDirty();
    }
    // endregion

    // region Runtime values (server side, mirrored to the GUI and WAILA)
    public boolean isWorkEnabled() {
        return getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isAllowedToWork();
    }

    public long getPerTickEUt() {
        return (long) (oreParallel + fluidParallel) * EU_PER_PARALLEL;
    }

    public long getPerSecondEUt() {
        return getPerTickEUt() * TICKS_PER_SECOND;
    }

    /** @return the fill level of the wireless EU buffer, percent. */
    public long getEnergyPercent() {
        long cap = bufferCapacity();
        if (cap <= 0L) return 0L;
        return Math.max(0L, Math.min(100L, energyBuffer * 100L / cap));
    }

    public long getOrePerSecond() {
        return (long) ORE_PER_SECOND_PER_PARALLEL * oreParallel;
    }

    public long getWorkSeconds() {
        return workTicks / TICKS_PER_SECOND;
    }

    /** @return how many different ores this dimension's drop map holds. */
    public int getOreTypeCount() {
        if (dropMap == null || dropMap.getTotalWeight() <= 0F) return 0;
        GTUtility.ItemId[] ores = dropMap.getOres();
        return ores == null ? 0 : ores.length;
    }

    public int getFluidTypeCount() {
        return fluidRates.size();
    }

    /** @return true when the dimension has neither a void-miner drop map nor an underground oil field. */
    public boolean isDimensionEmpty() {
        boolean noOre = dropMap == null || dropMap.getTotalWeight() <= 0F;
        return noOre && fluidRates.isEmpty();
    }

    /**
     * One GUI fluid line as locale free text, {@code registryName|mB/s}; past the end it is an empty string. The GUI
     * syncs this from the server and localizes it client side.
     */
    public String getFluidRateRaw(int index) {
        if (index < 0 || index >= fluidRates.size()) return "";
        int i = 0;
        for (Map.Entry<Fluid, Long> entry : fluidRates.entrySet()) {
            if (i == index) {
                long rate = entry.getValue() == null ? 0L : entry.getValue();
                return entry.getKey()
                    .getName() + "|"
                    + rate;
            }
            i++;
        }
        return "";
    }
    // endregion

    // region Processing (one GT cycle = one second of extraction)
    /**
     * Every cycle rolls one second of ores and pumps one second of fluids into GT's output fields, so GT ejects them
     * into the output buses and output hatches of the structure. {@code mEUt} stays 0 - the machine pays the wireless
     * EU network directly, see {@link #onPostTick}.
     */
    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        // The first check can arrive before the first tick ran onWorkStart(); make sure the parallel settings and the
        // dimension data are in place, otherwise the first second would use stale values.
        if (!wasWorking) prepareWorkData();
        mEUt = 0;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = TICKS_PER_SECOND;
        mOutputItems = rollOres();
        mOutputFluids = pumpFluids();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** Rolls one second of ores from the dimension's void miner drop map, aggregated per item id. */
    private ItemStack[] rollOres() {
        if (dropMap == null || dropMap.getTotalWeight() <= 0F) return null;
        final int amount = ORE_PER_SECOND_PER_PARALLEL * oreParallel;
        final HashMap<GTUtility.ItemId, Integer> counts = new HashMap<>();
        for (int i = 0; i < amount; i++) {
            GTUtility.ItemId id = dropMap.nextOre();
            if (id != null) counts.merge(id, 1, Integer::sum);
        }
        if (counts.isEmpty()) return null;
        final ItemStack[] outputs = new ItemStack[counts.size()];
        int i = 0;
        for (Map.Entry<GTUtility.ItemId, Integer> entry : counts.entrySet()) {
            outputs[i++] = entry.getKey()
                .getItemStack(entry.getValue());
        }
        return outputs;
    }

    /** One second of the dimension's underground oil, as output fluid stacks. */
    private FluidStack[] pumpFluids() {
        if (fluidRates.isEmpty()) return null;
        final FluidStack[] outputs = new FluidStack[fluidRates.size()];
        int i = 0;
        for (Map.Entry<Fluid, Long> entry : fluidRates.entrySet()) {
            long rate = entry.getValue() == null ? 0L : entry.getValue();
            if (rate <= 0L) continue;
            outputs[i++] = new FluidStack(entry.getKey(), (int) Math.min(rate, Integer.MAX_VALUE));
        }
        if (i == 0) return null;
        return i == outputs.length ? outputs : Arrays.copyOf(outputs, i);
    }
    // endregion

    // region Tick
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        owner = aBaseMetaTileEntity.getOwnerUuid();
        if (owner == null) owner = WirelessNetworkManager.processInitialSettings(aBaseMetaTileEntity);
        if (owner != null) WirelessNetworkManager.strongCheckOrAddUser(owner);
        MTMiningPlantRegistry.register(this);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        boolean allowed = aBaseMetaTileEntity.isAllowedToWork() && mMachine;
        if (allowed && !wasWorking) {
            wasWorking = true;
            workTicks = 0L;
            onWorkStart();
        } else if (!allowed && wasWorking) {
            wasWorking = false;
            releaseChunk();
        }
        if (!allowed) return;

        workTicks++;
        long perTick = getPerTickEUt();
        if (energyBuffer < perTick) {
            aBaseMetaTileEntity.disableWorking();
            return;
        }
        energyBuffer -= perTick;
        if (aTick % REFILL_INTERVAL == 0 && !refillEnergy()) {
            aBaseMetaTileEntity.disableWorking();
        }
    }

    /**
     * Starts a run: applies the parallel settings, resolves the dimension data, fills the EU buffer, loads the chunk.
     */
    private void onWorkStart() {
        prepareWorkData();
        energyBuffer = 0L;
        if (!refillEnergy() && !chargeOneSecond()) {
            getBaseMetaTileEntity().disableWorking();
            return;
        }
        loadChunk();
    }

    /** Applies the configured parallels and re-reads the dimension's ores and fluids. */
    private void prepareWorkData() {
        oreParallel = Math.max(1, Math.min(MAX_PARALLEL, configuredOre));
        fluidParallel = Math.max(0, Math.min(MAX_PARALLEL, configuredFluid));
        computeDropMap();
        computeFluids();
    }

    /** @return true when the network paid for one second, which then sits in the buffer. */
    private boolean chargeOneSecond() {
        long oneSecond = getPerSecondEUt();
        if (owner == null || !WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -oneSecond)) return false;
        energyBuffer = oneSecond;
        return true;
    }
    // endregion

    // region Dimension data
    /** Reads the drop map of the dimension the controller is in; no map means the dimension cannot be void mined. */
    private void computeDropMap() {
        dropMap = null;
        try {
            ModDimensionDef def = DimensionDef.getDefForWorld(getBaseMetaTileEntity().getWorld());
            if (def == null || !def.canBeVoidMined()) return;
            final String dimensionName = def.getDimensionName();
            DropMap base = VoidMinerUtility.dropMapsByDimName.get(dimensionName);
            DropMap extra = VoidMinerUtility.extraDropsByDimName.get(dimensionName);
            if (base == null) {
                if (extra == null) return;
                base = new DropMap();
            }
            base.isDistributionCached(extra);
            dropMap = base;
        } catch (Throwable ignored) {
            dropMap = null;
        }
    }

    /**
     * Reads the dimension's underground oil field. The amount of a vein is a per-operation value; dividing it by
     * {@link #OIL_DIVIDER} gives the mB a second of extraction per fluid parallel, exactly what the GT oil drilling
     * rigs produce.
     */
    private void computeFluids() {
        fluidRates.clear();
        try {
            int dim = getBaseMetaTileEntity().getWorld().provider.dimensionId;
            gregtech.api.objects.GTUODimension dimension = GTMod.proxy.mUndergroundOil.GetDimension(dim);
            if (dimension == null) return;
            for (gregtech.api.objects.GTUOFluid fluid : dimension.getFluids()
                .values()) {
                if (fluid == null || fluid.getFluid() == null) continue;
                // Deterministic per fluid, as in the source mod: the same fluid always yields the same vein amount.
                int amount = fluid.getRandomAmount(
                    new Random(
                        fluid.getFluid()
                            .getName()
                            .hashCode()));
                long rate = (long) ((double) amount * fluidParallel / OIL_DIVIDER);
                if (rate > 0L) fluidRates.put(fluid.getFluid(), rate);
            }
        } catch (Throwable ignored) {
            fluidRates.clear();
        }
    }
    // endregion

    // region Wireless EU
    /** Two minutes of the current draw: the buffer covers a full refill interval. */
    private long bufferCapacity() {
        return getPerSecondEUt() * 60L * 2L;
    }

    /** Tops the buffer up to capacity from the owner's wireless network in one lump sum. */
    private boolean refillEnergy() {
        if (owner == null) return false;
        long need = bufferCapacity() - energyBuffer;
        if (need <= 0L) return true;
        boolean ok = WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -need);
        if (ok) energyBuffer += need;
        return ok;
    }
    // endregion

    // region Chunk loading
    @Override
    public ChunkCoordIntPair getActiveChunk() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return new ChunkCoordIntPair(base.getXCoord() >> 4, base.getZCoord() >> 4);
    }

    private void loadChunk() {
        if (chunkLoaded) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        chunkLoaded = GTChunkManager.requestChunkLoad((TileEntity) base, getActiveChunk());
    }

    private void releaseChunk() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        if (chunkLoaded) {
            GTChunkManager.releaseTicket((TileEntity) base);
            chunkLoaded = false;
        }
    }
    // endregion

    // region Removal / NBT
    @Override
    public void onRemoval() {
        releaseChunk();
        MTMiningPlantRegistry.unregister(this);
        super.onRemoval();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("oreParallel", oreParallel);
        aNBT.setInteger("fluidParallel", fluidParallel);
        aNBT.setInteger("configuredOre", configuredOre);
        aNBT.setInteger("configuredFluid", configuredFluid);
        aNBT.setLong("energyBuffer", energyBuffer);
        if (owner != null) aNBT.setString(TAG_OWNER, owner.toString());
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        oreParallel = aNBT.getInteger("oreParallel");
        fluidParallel = aNBT.getInteger("fluidParallel");
        configuredOre = aNBT.hasKey("configuredOre") ? aNBT.getInteger("configuredOre") : oreParallel;
        configuredFluid = aNBT.hasKey("configuredFluid") ? aNBT.getInteger("configuredFluid") : fluidParallel;
        if (configuredOre < 1) configuredOre = 1;
        if (configuredFluid < 0) configuredFluid = 0;
        energyBuffer = aNBT.getLong("energyBuffer");
        String ownerRaw = aNBT.getString(TAG_OWNER);
        if (ownerRaw != null && !ownerRaw.isEmpty()) {
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
    }
    // endregion

    // region Tooltip
    /** House style: one {@code addInfo(translate(key))} per line, colour codes live in the lang files. */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.comprehensiveminingplant.machinetype"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.desc"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.flavour"))
            .addSeparator()
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.work.header"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.work.ore"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.work.fluid"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.work.parallel"))
            .addSeparator()
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.power.header"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.power.wireless"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.power.buffer"))
            .addSeparator()
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.details"))
            .addInfo(translateToLocal("machine.comprehensiveminingplant.tooltip.details.hint"))
            .beginStructureBlock(11, 10, 10, false)
            .addController(translateToLocal("machine.comprehensiveminingplant.structure.controller"))
            .addCasing("8", Casings.SteelGearBoxCasing.getLocalizedName(), false)
            .addCasing("4", blockName(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID), false)
            .addCasing("19", blockName(GregTechAPI.sBlockFrames, Materials.Tritanium.mMetaItemSubID), false)
            .addCasing("7", blockName(GregTechAPI.sBlockMetal3, 8), false)
            .addCasing("4", blockName(GregTechAPI.sBlockMetal6, 13), false)
            .addCasing("0-19", Casings.HighPowerCasing.getLocalizedName(), false)
            .addCasing("8", Casings.ReboltedNaquadahCasing.getLocalizedName(), false)
            .addOutputBus("1+", translateToLocal("machine.comprehensiveminingplant.structure.output_bus"), 1)
            .addOutputHatch("1+", translateToLocal("machine.comprehensiveminingplant.structure.output_hatch"), 1)
            .toolTipFinisher();
        return tt;
    }

    /** Localized display name of a GT block and meta, used for the structure block lines. */
    private static String blockName(Block block, int meta) {
        return new ItemStack(block, 1, meta).getDisplayName();
    }
    // endregion

    // region Info / WAILA
    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] {
            translateToLocalFormatted(
                "machine.comprehensiveminingplant.info.status",
                translateToLocal(
                    isWorkEnabled() ? "machine.comprehensiveminingplant.status.running"
                        : "machine.comprehensiveminingplant.status.idle")),
            translateToLocalFormatted(
                "machine.comprehensiveminingplant.info.power",
                getPerTickEUt(),
                getPerSecondEUt()),
            translateToLocalFormatted("machine.comprehensiveminingplant.info.buffer", getEnergyPercent()),
            translateToLocalFormatted("machine.comprehensiveminingplant.info.runtime", getWorkSeconds()),
            translateToLocalFormatted(
                "machine.comprehensiveminingplant.info.types",
                getOreTypeCount(),
                getFluidTypeCount()) };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setBoolean("cmpRunning", isWorkEnabled());
        tag.setLong("cmpEUt", getPerTickEUt());
        tag.setLong("cmpEUtSec", getPerSecondEUt());
        tag.setLong("cmpPct", getEnergyPercent());
        tag.setLong("cmpSec", getWorkSeconds());
        tag.setInteger("cmpOreTypes", getOreTypeCount());
        tag.setInteger("cmpFluidTypes", getFluidTypeCount());
        // Fluid registry names, localized by the client - this method runs server side.
        StringBuilder names = new StringBuilder();
        int i = 0;
        for (Fluid fluid : fluidRates.keySet()) {
            if (i++ >= 3) break;
            if (names.length() > 0) names.append('|');
            names.append(fluid.getName());
        }
        tag.setString("cmpFluidNames", names.toString());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        currentTip.add(
            translateToLocalFormatted(
                "machine.comprehensiveminingplant.waila.status",
                translateToLocal(
                    tag.getBoolean("cmpRunning") ? "machine.comprehensiveminingplant.status.running"
                        : "machine.comprehensiveminingplant.status.idle")));
        currentTip.add(
            translateToLocalFormatted(
                "machine.comprehensiveminingplant.waila.power",
                tag.getLong("cmpEUt"),
                tag.getLong("cmpEUtSec")));
        currentTip
            .add(translateToLocalFormatted("machine.comprehensiveminingplant.waila.buffer", tag.getLong("cmpPct")));
        currentTip
            .add(translateToLocalFormatted("machine.comprehensiveminingplant.waila.runtime", tag.getLong("cmpSec")));
        currentTip.add(
            translateToLocalFormatted(
                "machine.comprehensiveminingplant.waila.types",
                tag.getInteger("cmpOreTypes"),
                tag.getInteger("cmpFluidTypes")));
        String rawNames = tag.getString("cmpFluidNames");
        if (rawNames != null && !rawNames.isEmpty()) {
            StringBuilder localized = new StringBuilder();
            for (String name : rawNames.split("\\|")) {
                Fluid fluid = FluidRegistry.getFluid(name);
                if (fluid == null) continue;
                if (localized.length() > 0) localized.append(", ");
                localized.append(new FluidStack(fluid, 1).getLocalizedName());
            }
            if (localized.length() > 0) {
                currentTip.add(
                    translateToLocalFormatted("machine.comprehensiveminingplant.waila.fluids", localized.toString()));
            }
        }
    }
    // endregion

    // region Helpers
    private void markMachineDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }
    // endregion
}
