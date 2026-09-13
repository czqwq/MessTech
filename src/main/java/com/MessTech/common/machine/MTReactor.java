package com.MessTech.common.machine;

import static com.MessTech.common.util.Utils.filterValidMTEs;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.explosion.MTExplosionDE;
import com.MessTech.common.gui.MTReactorGui;
import com.MessTech.common.machine.Base.MTGeneratorMultiBase;
import com.MessTech.common.machine.hatch.MTReactorAccessHatch;
import com.MessTech.common.machine.hatch.MTReactorHeatHatch;
import com.MessTech.common.process.MTProcessHandler;
import com.MessTech.common.util.ExtraShutdownReason;
import com.MessTech.init.Config;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import ic2.core.IC2DamageSource;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * MessTech nuclear reactor.
 * <p>
 * This machine ports IC2's nuclear reactor mechanics:
 * <ul>
 * <li>The reactor components live in a {@link MTReactorAccessHatch}. Every 9x6 page of the hatch is processed as an
 * independent reactor grid: components can never interact across page borders.</li>
 * <li>Every 20 ticks both component passes run exactly like IC2 does (heat pass first, then the energy pass), heat
 * accumulates per page and each page has its own heat effect modifier. The heat ceiling of every page is set by the
 * single {@link MTReactorHeatHatch} of the structure and not by the reactor components.</li>
 * <li>At 85% heat the core can ignite its surroundings, at 70% it irradiates entities, and at 100% the page
 * explodes. The explosion power is computed from the reactor components' {@code influenceExplosion}.</li>
 * <li>The generated energy ({@code output * 5} EU/t, the IC2 conversion) is emitted through dynamo hatches.</li>
 * </ul>
 */
public class MTReactor extends MTGeneratorMultiBase<MTReactor> implements ISurvivalConstructable {

    /** IC2 reactor tick rate: components are processed once every 20 ticks. */
    public static final int CYCLE_TICKS = 20;
    /** IC2 EU/t conversion of the accumulated reactor output. */
    public static final float EU_PER_OUTPUT = 5.0F;
    /**
     * What IC2 clamps a reactor explosion to ({@code protection/reactorExplosionPowerLimit} in IC2's general.ini).
     * The MTReactor does not read IC2's config: it computes the identical IC2 number itself and uses it to scale the
     * component influence onto the {@link MTExplosionDE} power range, see {@link #explodeReactor()}.
     */
    private static final float IC2_EXPLOSION_POWER_LIMIT = 45.0F;
    /** Safety cap for pages processed per cycle (a 3x3x3 shell cannot hold that many hatches anyway). */
    private static final int MAX_PAGES = 256;

    // region Controller face
    /**
     * Face art of the controller, taken from GoodGenerator's neutron activator ({@code MTENeutronActivator}): the four
     * icons that machine draws on its own face, idle and running, each with the glow layer that is drawn on top of it.
     * They live in the gregtech assets, so they are looked up by their path there; the two glow icons are optional,
     * exactly as in the machine they come from. See {@link #getTexture}.
     */
    private static final IIconContainer FACE_NEUTRON_ACTIVATOR_OFF = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "icons/NeutronActivator_Off");
    private static final IIconContainer FACE_NEUTRON_ACTIVATOR_OFF_GLOW = Textures.BlockIcons
        .customOptional(Mods.GregTech.resourceDomain, "icons/NeutronActivator_Off_GLOW");
    private static final IIconContainer FACE_NEUTRON_ACTIVATOR_ON = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "icons/NeutronActivator_On");
    private static final IIconContainer FACE_NEUTRON_ACTIVATOR_ON_GLOW = Textures.BlockIcons
        .customOptional(Mods.GregTech.resourceDomain, "icons/NeutronActivator_On_GLOW");
    // endregion

    // region Hatch element
    /** Custom structure element letting the template-style structure accept reactor access hatches. */
    public static final IHatchElement<MTReactor> REACTOR_ACCESS_HATCH = new IHatchElement<MTReactor>() {

        @Override
        public List<Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTReactorAccessHatch.class);
        }

        @Override
        public IGTHatchAdder<? super MTReactor> adder() {
            return MTReactor::addReactorAccessHatchToMachineList;
        }

        @Override
        public String name() {
            return "mt_reactor_access_hatch";
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal("machine.mtreactor.accesshatch.name");
        }

        @Override
        public String getDescriptionLangKey() {
            return "machine.mtreactor.accesshatch.name";
        }

        @Override
        public long count(MTReactor t) {
            return t.getReactorAccessHatchCount();
        }
    };

    /** Custom structure element letting the structure accept exactly one reactor heat control hatch. */
    public static final IHatchElement<MTReactor> REACTOR_HEAT_HATCH = new IHatchElement<MTReactor>() {

        @Override
        public List<Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTReactorHeatHatch.class);
        }

        @Override
        public IGTHatchAdder<? super MTReactor> adder() {
            return MTReactor::addReactorHeatHatchToMachineList;
        }

        @Override
        public String name() {
            return "mt_reactor_heat_hatch";
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal("machine.mtreactor.heathatch.name");
        }

        @Override
        public String getDescriptionLangKey() {
            return "machine.mtreactor.heathatch.name";
        }

        @Override
        public long count(MTReactor t) {
            return t.getReactorHeatHatchCount();
        }
    };
    // endregion

    // region Structure
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    private static final int HORIZONTAL_OFFSET = 3;
    private static final int VERTICAL_OFFSET = 4;
    private static final int DEPTH_OFFSET = 1;

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"                                 ","            HH     HH            ","          HHHHH   HHHHH          ","         HHHHHH   HHHHHH         ","         HIIIIHH HHIIIIH         ","         HHHHHH   HHHHHH         ","          HHHHH   HHHHH          ","            HH     HH            ","                                 "},
        {"            HH     HH            ","GGGGGGG   HH  H   H  HH          ","GCCCCCG  H E   H H E E HH        ","GCCCCCG H      HHH      HH       ","GCCCCCG H       H       HH       ","GCCCCCG H  E   HHH      HH       ","GCCCCCG  H E   H H E E HH        ","GGGGGGG   HH  H   H  HH          ","            HH     HH            "},
        {"          HHHHH   HHHHH          ","GKKKKKG  H E   H H E E HH        ","C     C H  E    H  E E   H       ","C I I CG     E   E E      HAAAHHI","C  D  CG                  HAAAHHI","C I I CG   E   E     E    HAAAHHI","C     C H  E    H  E E   H       ","GCCCCCG  H E   H H E E HH        ","          HHHHH   HHHHH          "},
        {"         HHHHHH   HHHHHH         ","GKKKKKG H      HHH      HH       ","C     CG   E   E     E    HAAAHHI","C DDD GF   E       E E          J","C DDD GF     E E E     E  BB BBBJ","C DDD GF   E       E E          J","C     CG     E   E E      HAAAHHI","GCCCCCG H      HHH      HH       ","         HHHHHH   HHHHHH         "},
        {"         HIIIIHH HHIIIIH         ","GKK~KKG H       H       HH       ","C     CG                  HAAAHHI","C DDD GF     E E E     E  BB BBBJ","D D D EEEEEEEEEEEEEEEEEEEEEEEEEEJ","C DDD GF     E E E     E  BB BBBJ","C     CG                  HAAAHHI","GCCCCCG H       H       HH       ","         HIIIIHH HHIIIIH         "},
        {"         HHHHHH   HHHHHH         ","GKKKKKG H      HHH      HH       ","C     CG     E   E E      HAAAHHI","C DDD GF   E       E E          J","D DDD GF     E E E     E  BB BBBJ","C DDD GF   E       E E          J","C     CG   E   E     E    HAAAHHI","GCCCCCG H      HHH      HH       ","         HHHHHH   HHHHHH         "},
        {"          HHHHH   HHHHH          ","GKKKKKG  H E   H H E E HH        ","C     C H  E    H  E E   H       ","C I I CG   E   E     E    HAAAHHI","C  D  CG                  HAAAHHI","C I I CG     E   E E      HAAAHHI","C     C H  E    H  E E   H       ","GCCCCCG  H E   H H E E HH        ","          HHHHH   HHHHH          "},
        {"            HH     HH            ","GGGGGGG   HH  H   H  HH          ","GCCCCCG  H E   H H E E HH        ","GCCCCCG H      HHH      HH       ","GCCCCCG H       H       HH       ","GCCCCCG H      HHH      HH       ","GCCCCCG  H E   H H E E HH        ","GGGGGGG   HH  H   H  HH          ","            HH     HH            "},
        {"                                 ","            HH     HH            ","          HHHHH   HHHHH          ","         HHHHHH   HHHHHH         ","         HIIIIHH HHIIIIH         ","         HHHHHH   HHHHHH         ","          HHHHH   HHHHH          ","            HH     HH            ","                                 "},

    };
    // spotless:on

    private static final IStructureDefinition<MTReactor> STRUCTURE_DEFINITION = StructureDefinition.<MTReactor>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        .addElement('A', Casings.BorosilicateGlassAny.asElement())
        .addElement('B', Casings.MVSolenoidSuperconductorCoil.asElement())
        .addElement(
            'C',
            HatchElementBuilder.<MTReactor>builder()
                .atLeast(HatchElement.InputHatch, REACTOR_ACCESS_HATCH)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(1)
                .buildAndChain(
                    ofBlock(
                        Casings.SolidSteelMachineCasing.getBlock(),
                        Casings.SolidSteelMachineCasing.getBlockMeta())))
        .addElement('D', Casings.SolidSteelMachineCasing.asElement())
        .addElement('E', Casings.SteelPipeCasing.asElement())
        .addElement('F', Casings.YellowStripesBlockA.asElement())
        .addElement('G', Casings.RadioactiveHazardSignBlock.asElement())
        .addElement('H', Casings.TurbineCasing.asElement())
        .addElement('I', ofFrame(Materials.Steel))
        .addElement(
            'J',
            HatchElementBuilder.<MTReactor>builder()
                .atLeast(HatchElement.Dynamo.or(HatchElement.ExoticDynamo))
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(2)
                .buildAndChain(
                    ofBlock(
                        Casings.SolidSteelMachineCasing.getBlock(),
                        Casings.SolidSteelMachineCasing.getBlockMeta())))
        .addElement(
            'K',
            HatchElementBuilder.<MTReactor>builder()
                .atLeast(REACTOR_HEAT_HATCH, HatchElement.OutputBus, HatchElement.InputBus)
                .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                .hint(3)
                .buildAndChain(
                    ofBlock(
                        Casings.SolidSteelMachineCasing.getBlock(),
                        Casings.SolidSteelMachineCasing.getBlockMeta())))
        .build();
    // endregion

    // region Reactor state
    public final List<MTReactorAccessHatch> mReactorAccessHatches = new ArrayList<>();
    /** The single heat control hatch of this reactor (the structure rejects zero or more than one). */
    public final List<MTReactorHeatHatch> mReactorHeatHatches = new ArrayList<>();
    /** Persistent heat of every 9x6 page, indexed by the global page order over all access hatches. */
    private final int[] mPageHeat = new int[MAX_PAGES];
    /** EU/t that is currently emitted through dynamo hatches (IC2 keeps the output constant between cycles). */
    private long mReactorEUPerTick = 0;
    /** Ticks until the next IC2 reactor cycle. */
    private int mReactorCycleTicks = 0;
    /** Result of the next-cycle heat simulation shown in the GUI/Waila. */
    private boolean mReactorStable = true;
    /** Cheap fingerprint of all reactor items, used to re-run the live simulation only when something changed. */
    private int mReactorInventorySignature = 0;
    /** Enables automatic input into / output from every locked access hatch slot. Enabled by default. */
    private boolean mAutoMatchTarget = true;
    /** Per locked slot: the next simulated heat cycle destroys that component. */
    private final boolean[] mNextCycleDestroyed = new boolean[MAX_PAGES * MTReactorAccessHatch.SLOTS_PER_PAGE];
    /** True when any locked slot would be destroyed by the next heat cycle. */
    private boolean mNextCycleDoomed = false;
    /** Adapter exposing the currently processed page to IC2 reactor components. */
    private final ReactorContext mReactorContext = new ReactorContext();
    // endregion

    public MTReactor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTReactor(String aName) {
        super(aName);
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTReactorGui(this);
    }

    /**
     * The stability simulation is a design-time check: it must also run while the machine is switched off, so the
     * player can see whether the current layout would explode once the reactor starts working. It uses the item
     * fingerprint to avoid a full simulation every tick when nothing changed.
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) return;

        if (mAutoMatchTarget && !mReactorAccessHatches.isEmpty()) {
            // Make sure the destruction prediction is fresh before using it for preemptive outputs.
            ensureLiveSimulation();
            if (processAutoMatchTarget()) {
                // Inventory changed; refresh the prediction and let the controller re-check immediately.
                mReactorStable = simulateNextCycleStability();
                scheduleRecipeCheckImmediate();
            }
        }
        // While paused for a locked-component problem, poll for a manual/automatic fix.
        if (aBaseMetaTileEntity.wasShutdown() && isReactorSoftStopReason(aBaseMetaTileEntity.getLastShutDownReason())) {
            scheduleRecipeCheckImmediate();
        }
        if (mMachine) {
            ensureLiveSimulation();
        }
    }

    private static boolean isReactorSoftStopReason(ShutDownReason reason) {
        if (reason == null) return false;
        String key = reason.getKey();
        return "reactor_component_missing".equals(key) || "reactor_component_low".equals(key);
    }

    private void ensureLiveSimulation() {
        if (computeInventorySignature() != mReactorInventorySignature) {
            mReactorStable = simulateNextCycleStability();
        }
    }

    public boolean isAutoMatchTarget() {
        return mAutoMatchTarget;
    }

    public void setAutoMatchTarget(boolean value) {
        if (this.mAutoMatchTarget == value) return;
        this.mAutoMatchTarget = value;
        markDirty();
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            // Auto mode requires input/output buses, so re-evaluate the structure immediately.
            checkStructure(true, base);
        }
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTReactor(mName);
    }

    @Override
    public IStructureDefinition<MTReactor> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // Energy always leaves through dynamo hatches; the wireless output mode of the generator base is not used.
        setEnableWirelessFunc(false);
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        checkHasAnyDynamo(errors);
        if (mReactorAccessHatches.isEmpty()) {
            errors.add(StructureErrors.of("machine.mtreactor.structure.error.missing_access_hatch"));
        }
        // Exactly one heat control hatch sets the reactor heat ceiling. The structure must reject duplicates instead
        // of silently picking one of them, otherwise the player could not tell which ceiling is in effect.
        if (mReactorHeatHatches.isEmpty()) {
            errors.add(StructureErrors.of("machine.mtreactor.structure.error.missing_heat_hatch"));
        } else if (mReactorHeatHatches.size() > 1) {
            errors.add(StructureErrors.of("machine.mtreactor.structure.error.too_many_heat_hatch"));
        }
        // The auto input/output mode needs both buses to move components around. A missing locked component is a
        // soft shutdown (see checkProcessing), not an invalid structure.
        if (mAutoMatchTarget) {
            if (mInputBusses.isEmpty()) {
                errors.add(StructureErrors.of("machine.mtreactor.structure.error.missing_input_bus"));
            }
            if (mOutputBusses.isEmpty()) {
                errors.add(StructureErrors.of("machine.mtreactor.structure.error.missing_output_bus"));
            }
        }
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        mReactorAccessHatches.clear();
        mReactorHeatHatches.clear();
    }

    /**
     * Structure adder for {@link #REACTOR_ACCESS_HATCH}. Like every regular GT hatch, the hatch switches to the
     * multiblock casing texture (and therefore its color) as soon as the multiblock recognises it.
     */
    public boolean addReactorAccessHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MTReactorAccessHatch hatch)) return false;
        hatch.updateTexture(aBaseCasingIndex);
        if (!mReactorAccessHatches.contains(hatch)) {
            mReactorAccessHatches.add(hatch);
        }
        return true;
    }

    public long getReactorAccessHatchCount() {
        return mReactorAccessHatches.size();
    }

    /** Structure adder for {@link #REACTOR_HEAT_HATCH}. */
    public boolean addReactorHeatHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MTReactorHeatHatch hatch)) return false;
        hatch.updateTexture(aBaseCasingIndex);
        if (!mReactorHeatHatches.contains(hatch)) {
            mReactorHeatHatches.add(hatch);
        }
        return true;
    }

    public long getReactorHeatHatchCount() {
        return mReactorHeatHatches.size();
    }

    /**
     * Heat ceiling of the reactor, taken from the installed heat control hatch. While the structure is incomplete
     * (no hatch at all) the EV value is used, so the GUI and the stability simulation always have a meaningful
     * number to show. Should several hatches ever be present, the lowest ceiling wins to stay on the safe side.
     * <p>
     * Note: the UIV hatch legitimately uses {@link Integer#MAX_VALUE} as its ceiling, so the "no hatch" case must be
     * tracked with a flag instead of comparing the result against that value.
     */
    public int getReactorHeatCapacity() {
        boolean found = false;
        int capacity = Integer.MAX_VALUE;
        for (MTReactorHeatHatch hatch : mReactorHeatHatches) {
            if (hatch == null) continue;
            found = true;
            capacity = Math.min(capacity, hatch.getHeatCapacity());
        }
        return found ? capacity : MTReactorHeatHatch.DEFAULT_HEAT_CAPACITY;
    }

    // region Locked slot auto input / output

    public boolean hasMissingLockedComponents() {
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch != null && hatch.hasMissingLockedComponents()) return true;
        }
        return false;
    }

    /** True when a locked component is at/below its output threshold, or would be destroyed next cycle. */
    public boolean hasLowOrDoomedLockedComponents() {
        if (mNextCycleDoomed) return true;
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch == null) continue;
            for (int slot = 0; slot < hatch.getSlotCount(); slot++) {
                if (!hatch.isSlotLocked(slot)) continue;
                ItemStack stack = hatch.getSlotStack(slot);
                if (stack == null) continue;
                if (MTReactorAccessHatch.getDurabilityFraction(stack) <= hatch.getSlotThreshold(slot)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Moves finished (or about-to-be-destroyed) components out to the output buses and refills empty locked slots
     * from the input buses.
     *
     * @return true when a locked slot changed
     */
    private boolean processAutoMatchTarget() {
        boolean changed = false;
        int globalPage = 0;
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            for (int page = 0; page < hatch.getPageCount() && globalPage < MAX_PAGES; page++, globalPage++) {
                int baseSlot = page * MTReactorAccessHatch.SLOTS_PER_PAGE;
                for (int localSlot = 0; localSlot < MTReactorAccessHatch.SLOTS_PER_PAGE; localSlot++) {
                    int slot = baseSlot + localSlot;
                    if (!hatch.isSlotLocked(slot)) continue;
                    ItemStack memory = hatch.getSlotMemory(slot);
                    if (memory == null) continue;
                    int threshold = hatch.getSlotThreshold(slot);
                    int globalSlot = globalPage * MTReactorAccessHatch.SLOTS_PER_PAGE + localSlot;
                    ItemStack current = hatch.getSlotStack(slot);
                    // Pull a doomed component before the fatal cycle, but only when it already lost durability.
                    // A fresh component dying next cycle means the layout itself is wrong; swapping cannot help,
                    // so in that case the machine just stays soft-stopped instead of consuming input items.
                    boolean doomed = current != null && globalSlot >= 0
                        && globalSlot < mNextCycleDestroyed.length
                        && mNextCycleDestroyed[globalSlot]
                        && MTReactorAccessHatch.getDurabilityFraction(current) < 100.0D;

                    if (current != null && (doomed || shouldAutoOutput(current, memory, threshold))) {
                        if (addOutputAtomic(current.copy())) {
                            hatch.setSlotStack(slot, null);
                            current = null;
                            changed = true;
                        }
                    }
                    if (current == null && tryAutoInput(hatch, slot, memory, threshold)) {
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * A locked slot is emptied when the item is no longer the remembered item type (a depleted fuel rod for
     * example) or when its remaining durability is <b>less than or equal to</b> the configured percentage.
     */
    private static boolean shouldAutoOutput(ItemStack current, ItemStack memory, int threshold) {
        if (current == null || memory == null) return false;
        if (!MTReactorAccessHatch.isSameItemType(current, memory)) return true;
        if (!(current.getItem() instanceof IReactorComponent)) return true;
        return MTReactorAccessHatch.getDurabilityFraction(current) <= threshold;
    }

    private boolean tryAutoInput(MTReactorAccessHatch hatch, int slot, ItemStack memory, int threshold) {
        for (MTEHatchInputBus bus : filterValidMTEs(mInputBusses)) {
            int circuitSlot = bus.getCircuitSlot();
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                if (i == circuitSlot) continue;
                ItemStack candidate = bus.getStackInSlot(i);
                if (!canAutoInput(hatch, slot, candidate, memory, threshold)) continue;
                ItemStack extracted = bus.decrStackSize(i, 1);
                if (extracted != null) {
                    hatch.setSlotStack(slot, extracted);
                    return true;
                }
            }
        }
        return false;
    }

    /** The input item must match the remembered type, be allowed in the hatch and be above the threshold. */
    private static boolean canAutoInput(MTReactorAccessHatch hatch, int slot, ItemStack candidate, ItemStack memory,
        int threshold) {
        if (candidate == null || memory == null) return false;
        if (!MTReactorAccessHatch.isSameItemType(candidate, memory)) return false;
        if (!(candidate.getItem() instanceof IReactorComponent)) return false;
        if (!hatch.isItemValidForSlot(slot, candidate)) return false;
        return MTReactorAccessHatch.getDurabilityFraction(candidate) > threshold;
    }
    // endregion

    // region Processing

    @Override
    public boolean isEnablePerfectOverclock() {
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

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        ensureLiveSimulation();
        // Locked components are a soft shutdown: the structure stays formed, the reactor just pauses until the
        // auto input/output (or the player) fills the slot again. This also happens BEFORE a heat cycle that
        // would destroy a locked component, so it cannot be lost mid-cycle.
        if (hasMissingLockedComponents()) {
            softStopReactor(ExtraShutdownReason.MISS_REACTOR_COMPONENT);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        if (hasLowOrDoomedLockedComponents()) {
            softStopReactor(ExtraShutdownReason.REACTOR_COMPONENT_LOW);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        clearReactorSoftStop();

        if (mReactorCycleTicks <= 0) {
            mReactorCycleTicks = CYCLE_TICKS;
            if (runReactorCycle()) {
                // The core exploded and the structure was destroyed by the nuclear blast.
                lEUt = 0;
                mMaxProgresstime = 0;
                return CheckRecipeResultRegistry.NO_FUEL_FOUND;
            }
        }
        mReactorCycleTicks--;

        // Keep ticking even without output so residual heat keeps cooling/exploding like an IC2 reactor does.
        return startGenerating(mReactorEUPerTick, 1, null, null);
    }

    /** Pauses the reactor and shows a shutdown reason without disabling the machine. */
    private void softStopReactor(ShutDownReason reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.setShutDownReason(reason);
            base.setShutdownStatus(true);
        }
        lEUt = 0;
        mMaxProgresstime = 0;
    }

    private void clearReactorSoftStop() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.wasShutdown()) {
            base.setShutdownStatus(false);
            base.setShutDownReason(ShutDownReasonRegistry.NONE);
        }
    }

    private List<MTReactorAccessHatch> getSortedReactorHatches() {
        List<MTReactorAccessHatch> hatches = new ArrayList<>(mReactorAccessHatches.size());
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch == null) continue;
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base == null || base.isDead()) continue;
            hatches.add(hatch);
        }
        hatches.sort(
            Comparator.comparingInt(
                (MTReactorAccessHatch hatch) -> hatch.getBaseMetaTileEntity()
                    .getXCoord())
                .thenComparingInt(
                    hatch -> hatch.getBaseMetaTileEntity()
                        .getYCoord())
                .thenComparingInt(
                    hatch -> hatch.getBaseMetaTileEntity()
                        .getZCoord()));
        return hatches;
    }

    /**
     * Runs one IC2 reactor tick for every page of every access hatch.
     *
     * @return true if a page exploded
     */
    private boolean runReactorCycle() {
        float totalOutput = 0.0F;
        int globalPage = 0;

        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            for (int page = 0; page < hatch.getPageCount(); page++) {
                if (globalPage >= MAX_PAGES) break;
                mReactorContext.bind(hatch, page, globalPage);
                processChambers(false);
                totalOutput += mReactorContext.pageOutput;
                if (calculateHeatEffects(globalPage)) {
                    return true;
                }
                globalPage++;
            }
        }

        mReactorEUPerTick = (long) (totalOutput * EU_PER_OUTPUT);
        mReactorStable = simulateNextCycleStability();
        markDirty();
        return false;
    }

    /**
     * Exactly IC2's {@code processChambers()}: heat pass first, then the energy pass.
     *
     * @param heatPassOnly true to only run the heat pass, used by the stability simulation
     */
    private void processChambers(boolean heatPassOnly) {
        int passes = heatPassOnly ? 1 : 2;
        for (int pass = 0; pass < passes; pass++) {
            boolean heatRun = pass == 0;
            for (int y = 0; y < MTReactorAccessHatch.PAGE_HEIGHT; y++) {
                for (int x = 0; x < MTReactorAccessHatch.PAGE_WIDTH; x++) {
                    ItemStack stack = mReactorContext.getItemAt(x, y);
                    if (stack != null && stack.getItem() instanceof IReactorComponent component) {
                        component.processChamber(mReactorContext, stack, x, y, heatRun);
                    }
                }
            }
        }
    }

    /**
     * Simulates the heat pass of the next IC2 cycle on a copy of every page and reports whether the reactor
     * would stay below 100% heat. Nothing from the simulation is written back to the real inventory or heat.
     *
     * @return true if every page would survive the next cycle
     */
    private boolean simulateNextCycleStability() {
        boolean stable = true;
        Arrays.fill(mNextCycleDestroyed, false);
        mNextCycleDoomed = false;
        int globalPage = 0;
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            for (int page = 0; page < hatch.getPageCount() && globalPage < MAX_PAGES; page++, globalPage++) {
                // A page without items can neither add nor redistribute heat, so it cannot start exploding.
                if (hatch.isPageEmpty(page)) continue;
                ItemStack[] snapshot = snapshotPage(hatch, page);
                mReactorContext.bindSimulation(snapshot, mPageHeat[globalPage]);
                processChambers(true);

                // Detect locked components that the next heat pass would destroy entirely (for example a coolant
                // cell that cannot absorb the next heat pulse). Converted items (depleted fuel rods) are fine.
                int baseSlot = page * MTReactorAccessHatch.SLOTS_PER_PAGE;
                for (int localSlot = 0; localSlot < MTReactorAccessHatch.SLOTS_PER_PAGE; localSlot++) {
                    int slot = baseSlot + localSlot;
                    if (!hatch.isSlotLocked(slot)) continue;
                    ItemStack original = hatch.getSlotStack(slot);
                    if (original != null && snapshot[localSlot] == null) {
                        mNextCycleDestroyed[globalPage * MTReactorAccessHatch.SLOTS_PER_PAGE + localSlot] = true;
                        mNextCycleDoomed = true;
                    }
                }

                if (stable) {
                    int predictedHeat = mReactorContext.getSimulatedHeat();
                    int predictedMaxHeat = mReactorContext.maxHeat;
                    if (predictedHeat >= 4000 && predictedMaxHeat > 0 && predictedHeat >= predictedMaxHeat) {
                        stable = false;
                    }
                }
            }
        }
        mReactorInventorySignature = computeInventorySignature();
        return stable;
    }

    /**
     * Cheap fingerprint of every item in every page (identity, damage, stack size and NBT). Used to detect item
     * changes without deep-copying the inventory for the simulation.
     */
    private int computeInventorySignature() {
        int signature = 1;
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            // Lock state / thresholds: a lock change must also refresh the destruction prediction.
            for (byte state : hatch.getSlotStateArray()) {
                signature = 31 * signature + state;
            }
            for (int page = 0; page < hatch.getPageCount(); page++) {
                for (int y = 0; y < MTReactorAccessHatch.PAGE_HEIGHT; y++) {
                    for (int x = 0; x < MTReactorAccessHatch.PAGE_WIDTH; x++) {
                        ItemStack stack = hatch.getReactorItem(page, x, y);
                        if (stack == null) continue;
                        signature = 31 * signature + System.identityHashCode(stack);
                        signature = 31 * signature + stack.getItemDamage();
                        signature = 31 * signature + stack.stackSize;
                        NBTTagCompound tag = stack.getTagCompound();
                        signature = 31 * signature + (tag == null ? 0 : tag.hashCode());
                    }
                }
            }
        }
        return signature;
    }

    /** Copies one 9x6 page so the simulation cannot damage or deplete the real components. */
    private static ItemStack[] snapshotPage(MTReactorAccessHatch hatch, int page) {
        ItemStack[] snapshot = new ItemStack[MTReactorAccessHatch.SLOTS_PER_PAGE];
        for (int y = 0; y < MTReactorAccessHatch.PAGE_HEIGHT; y++) {
            for (int x = 0; x < MTReactorAccessHatch.PAGE_WIDTH; x++) {
                ItemStack stack = hatch.getReactorItem(page, x, y);
                snapshot[y * MTReactorAccessHatch.PAGE_WIDTH + x] = stack == null ? null : stack.copy();
            }
        }
        return snapshot;
    }

    /**
     * IC2's heat effect logic, scaled to the currently processed page's own heat cap and heat effect modifier.
     *
     * @return true if the page exploded
     */
    private boolean calculateHeatEffects(int page) {
        int heat = mPageHeat[page];
        int maxHeat = mReactorContext.maxHeat;
        float hem = mReactorContext.hem;
        if (heat < 4000 || maxHeat <= 0) return false;

        float power = (float) heat / (float) maxHeat;
        if (power >= 1.0F) {
            explodeReactor();
            return true;
        }

        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (world == null) return false;
        Random random = world.rand;

        if (power >= 0.85F && random.nextFloat() <= 0.2F * hem) {
            int[] coord = getRandCoord(2);
            if (coord != null) {
                // Only ever ignite air. IC2's original code overwrites every non-air block with fire or flowing
                // lava (GT casings use their own material, so they became fire, stone/ground became lava), which
                // eats the multiblock's own casings and hatches and dissolves the machine. Igniting air cannot
                // break the structure: every structure position requires a specific non-air block, and the
                // unchecked ' ' positions do not care about the block.
                if (world.getBlock(coord[0], coord[1], coord[2])
                    .isAir(world, coord[0], coord[1], coord[2])) {
                    world.setBlock(coord[0], coord[1], coord[2], Blocks.fire, 0, 7);
                }
            }
        }

        if (power >= 0.7F) {
            List<EntityLivingBase> entities = world.getEntitiesWithinAABB(
                EntityLivingBase.class,
                AxisAlignedBB.getBoundingBox(
                    base.getXCoord() - 3,
                    base.getYCoord() - 3,
                    base.getZCoord() - 3,
                    base.getXCoord() + 4,
                    base.getYCoord() + 4,
                    base.getZCoord() + 4));
            for (Entity entity : entities) {
                entity.attackEntityFrom(IC2DamageSource.radiation, (int) (random.nextInt(4) * hem));
            }
        }

        if (power >= 0.5F && random.nextFloat() <= hem) {
            int[] coord = getRandCoord(2);
            if (coord != null && world.getBlock(coord[0], coord[1], coord[2])
                .getMaterial() == Material.water) {
                world.setBlockToAir(coord[0], coord[1], coord[2]);
            }
        }

        if (power >= 0.4F && random.nextFloat() <= hem) {
            int[] coord = getRandCoord(2);
            if (coord != null && world.getTileEntity(coord[0], coord[1], coord[2]) == null) {
                Material material = world.getBlock(coord[0], coord[1], coord[2])
                    .getMaterial();
                if (material == Material.wood || material == Material.leaves || material == Material.cloth) {
                    world.setBlock(coord[0], coord[1], coord[2], Blocks.fire, 0, 7);
                }
            }
        }

        return false;
    }

    private int[] getRandCoord(int radius) {
        if (radius <= 0) return null;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        World world = base.getWorld();
        int[] coord = new int[3];
        coord[0] = base.getXCoord() + world.rand.nextInt(2 * radius + 1) - radius;
        coord[1] = base.getYCoord() + world.rand.nextInt(2 * radius + 1) - radius;
        coord[2] = base.getZCoord() + world.rand.nextInt(2 * radius + 1) - radius;
        if (coord[0] == base.getXCoord() && coord[1] == base.getYCoord() && coord[2] == base.getZCoord()) {
            return null;
        }
        return coord;
    }

    /**
     * IC2's {@code explode()}: the explosion power is derived from the reactor components' influence, all
     * components are destroyed, and the multiblock itself is removed. The IC2 style ray explosion is replaced by
     * the Draconic Evolution style {@link MTExplosionDE} - see that class for why.
     */
    private void explodeReactor() {
        float boomPower = 10.0F;
        float boomMod = 1.0F;

        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            for (int page = 0; page < hatch.getPageCount(); page++) {
                for (int y = 0; y < MTReactorAccessHatch.PAGE_HEIGHT; y++) {
                    for (int x = 0; x < MTReactorAccessHatch.PAGE_WIDTH; x++) {
                        ItemStack stack = hatch.getReactorItem(page, x, y);
                        if (stack != null && stack.getItem() instanceof IReactorComponent component) {
                            float influence = component.influenceExplosion(mReactorContext, stack);
                            if (influence > 0.0F && influence < 1.0F) {
                                boomMod *= influence;
                            } else {
                                boomPower += influence;
                            }
                        }
                    }
                }
            }
            hatch.clearAllPages();
        }

        Arrays.fill(mPageHeat, 0);
        mReactorEUPerTick = 0;
        mReactorCycleTicks = CYCLE_TICKS;
        mReactorStable = false;
        boomPower *= mReactorContext.hem * boomMod;

        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        int centerX = base == null ? 0 : base.getXCoord();
        int centerY = base == null ? 0 : base.getYCoord();
        int centerZ = base == null ? 0 : base.getZCoord();

        // Standard GT destruction first, so the controller and all regular hatches are removed cleanly.
        explodeMultiblock();
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch == null) continue;
            IGregTechTileEntity hatchBase = hatch.getBaseMetaTileEntity();
            if (hatchBase != null && !hatchBase.isDead()) {
                hatchBase.doExplosion(GTValues.V[8]);
            }
        }
        if (world != null && !world.isRemote) {
            // IC2 clamps its own reactors to protection/reactorExplosionPowerLimit (45); the MTReactor computes that
            // exact number itself and maps it onto the Draconic Evolution power scale used by MTExplosionDE, whose
            // maximum is twice DE's own full reactor (2 x 20 = 40) - see MTExplosionDE and Config.
            float dePower = Math.min(boomPower, IC2_EXPLOSION_POWER_LIMIT) / IC2_EXPLOSION_POWER_LIMIT
                * Config.REACTOR_EXPLOSION_DE_POWER_LIMIT;
            MTProcessHandler.addProcess(new MTExplosionDE(world, centerX, centerY, centerZ, dePower));
        }
    }
    // endregion

    // region NBT / info

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setIntArray("mtReactorPageHeat", mPageHeat);
        aNBT.setBoolean("mtReactorStable", mReactorStable);
        aNBT.setBoolean("mtReactorAutoMatch", mAutoMatchTarget);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        int[] savedHeat = aNBT.getIntArray("mtReactorPageHeat");
        Arrays.fill(mPageHeat, 0);
        if (savedHeat != null) {
            System.arraycopy(savedHeat, 0, mPageHeat, 0, Math.min(savedHeat.length, mPageHeat.length));
        }
        // "mtReactorPageMaxHeat" from older saves is ignored: the ceiling always comes from the heat control hatch.
        if (aNBT.hasKey("mtReactorStable")) {
            mReactorStable = aNBT.getBoolean("mtReactorStable");
        }
        // Default on for existing/new machines unless explicitly disabled.
        mAutoMatchTarget = !aNBT.hasKey("mtReactorAutoMatch") || aNBT.getBoolean("mtReactorAutoMatch");
    }

    @Override
    public String[] getInfoData() {
        String[] base = super.getInfoData();
        String[] extra = new String[] {
            StatCollector
                .translateToLocalFormatted("machine.mtreactor.waila.access_hatches", getReactorAccessHatchCountInt()),
            StatCollector.translateToLocalFormatted("machine.mtreactor.waila.components", getReactorComponentCount()),
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.heat",
                getHottestPageHeat(),
                getHottestPageMaxHeat()),
            StatCollector
                .translateToLocalFormatted("machine.mtreactor.waila.heat_percent", getHottestPageHeatPercent()),
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.stable",
                StatCollector.translateToLocal(
                    mReactorStable ? "machine.mtreactor.waila.stable.yes" : "machine.mtreactor.waila.stable.no")),
            StatCollector.translateToLocalFormatted("machine.mtreactor.waila.output", mReactorEUPerTick) };
        String[] result = Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }

    // region GUI / Waila getters

    public int getReactorAccessHatchCountInt() {
        return mReactorAccessHatches.size();
    }

    /** Number of occupied reactor slots across every page. */
    public int getReactorComponentCount() {
        int count = 0;
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            for (int page = 0; page < hatch.getPageCount(); page++) {
                for (int y = 0; y < MTReactorAccessHatch.PAGE_HEIGHT; y++) {
                    for (int x = 0; x < MTReactorAccessHatch.PAGE_WIDTH; x++) {
                        if (hatch.getReactorItem(page, x, y) != null) count++;
                    }
                }
            }
        }
        return count;
    }

    public int getHottestPageHeat() {
        int index = getHottestPageIndex();
        return index < 0 ? 0 : mPageHeat[index];
    }

    /**
     * Heat ceiling shown in the GUI/Waila/scanner. It is read live from the heat control hatch instead of being
     * cached, so installing, upgrading or losing the hatch is visible immediately instead of only after the next
     * reactor cycle (an empty or switched-off reactor never runs one).
     */
    public int getHottestPageMaxHeat() {
        return Math.max(1, getReactorHeatCapacity());
    }

    public int getHottestPageHeatPercent() {
        return (int) Math.round(getHottestPageHeat() * 100.0D / getHottestPageMaxHeat());
    }

    public boolean isReactorStable() {
        return mReactorStable;
    }

    private int getHottestPageIndex() {
        int pages = getTotalPageCount();
        if (pages <= 0) return -1;
        int hottest = 0;
        for (int i = 1; i < pages; i++) {
            if (mPageHeat[i] > mPageHeat[hottest]) hottest = i;
        }
        return hottest;
    }

    private int getTotalPageCount() {
        int pages = 0;
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch != null) pages += hatch.getPageCount();
        }
        return Math.min(pages, MAX_PAGES);
    }
    // endregion
    // endregion

    // region Waila

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setBoolean("incompleteStructure", (getErrorDisplayID() & 64) != 0);
        tag.setInteger("mtReactorHatches", getReactorAccessHatchCountInt());
        tag.setInteger("mtReactorComponents", getReactorComponentCount());
        tag.setInteger("mtReactorHeat", getHottestPageHeat());
        tag.setInteger("mtReactorMaxHeat", getHottestPageMaxHeat());
        tag.setInteger("mtReactorHeatPercent", getHottestPageHeatPercent());
        tag.setBoolean("mtReactorStable", mReactorStable);
        tag.setLong("mtReactorOutput", mReactorEUPerTick);
        tag.setBoolean("mtReactorMissingLocked", hasMissingLockedComponents());
        tag.setBoolean("mtReactorLowLocked", hasLowOrDoomedLockedComponents());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("incompleteStructure")) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("machine.mtreactor.waila.incomplete")
                    + EnumChatFormatting.RESET);
            return;
        }

        currentTip.add(
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.access_hatches",
                tag.getInteger("mtReactorHatches")));
        currentTip.add(
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.components",
                tag.getInteger("mtReactorComponents")));
        currentTip.add(
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.heat",
                tag.getInteger("mtReactorHeat"),
                tag.getInteger("mtReactorMaxHeat")));
        currentTip.add(
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.heat_percent",
                tag.getInteger("mtReactorHeatPercent")));
        boolean stable = tag.getBoolean("mtReactorStable");
        currentTip.add(
            (stable ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + StatCollector.translateToLocalFormatted(
                    "machine.mtreactor.waila.stable",
                    StatCollector.translateToLocal(
                        stable ? "machine.mtreactor.waila.stable.yes" : "machine.mtreactor.waila.stable.no"))
                + EnumChatFormatting.RESET);
        currentTip.add(
            StatCollector.translateToLocalFormatted("machine.mtreactor.waila.output", tag.getLong("mtReactorOutput")));

        if (tag.getBoolean("mtReactorMissingLocked")) {
            currentTip.add(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocal("machine.mtreactor.structure.error.missing_locked_component")
                    + EnumChatFormatting.RESET);
        } else if (tag.getBoolean("mtReactorLowLocked")) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("machine.mtreactor.gui.component_low")
                    + EnumChatFormatting.RESET);
        }
    }
    // endregion

    // region Tooltip / texture

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("machine.mtreactor.machinetype"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.0"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.1"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.mtreactor.tooltip.2"))
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal("machine.mtreactor.tooltip.3"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("machine.mtreactor.tooltip.4"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.5"))
            .beginStructureBlock(33, 8, 9, true)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_2nd_layer"))
            .addCasing("0-25", StatCollector.translateToLocal("gt.blockcasings2.9.name"), false)
            .addDynamoHatch(StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("machine.mtreactor.accesshatch.name"),
                StatCollector.translateToLocal("gt.mbtt.structure.any_casing"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("machine.mtreactor.heathatch.name"),
                StatCollector.translateToLocal("gt.mbtt.structure.any_casing"))
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addMufflerHatch(StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side != facing) {
            return new ITexture[] { TextureFactory
                .of(Casings.AssemblerMachineCasing.getBlock(), Casings.AssemblerMachineCasing.getBlockMeta()) };
        }

        // The face of the controller is the face art of GoodGenerator's neutron activator - idle or running, each with
        // its glow layer drawn on top - over the casing that was already under that face. Only the overlay is taken
        // from the other machine: the base layer is unchanged, the neutron activator would bring a casing texture of
        // its own along (it draws its face over its own casing id).
        IIconContainer face = aActive ? FACE_NEUTRON_ACTIVATOR_ON : FACE_NEUTRON_ACTIVATOR_OFF;
        IIconContainer glow = aActive ? FACE_NEUTRON_ACTIVATOR_ON_GLOW : FACE_NEUTRON_ACTIVATOR_OFF_GLOW;
        return new ITexture[] {
            TextureFactory.of(Casings.AssemblyLineCasing.getBlock(), Casings.AssemblyLineCasing.getBlockMeta()),
            TextureFactory.builder()
                .addIcon(face)
                .extFacing()
                .build(),
            TextureFactory.builder()
                .addIcon(glow)
                .extFacing()
                .glow()
                .build() };
    }
    // endregion

    // region IC2 reactor adapter

    /**
     * Per-page {@link IReactor} implementation. Only the currently bound page is visible to reactor components,
     * which is what keeps every page an independent 9x6 reactor grid.
     */
    private final class ReactorContext implements IReactor {

        private MTReactorAccessHatch hatch;
        private int page;
        private int heatIndex;
        private int maxHeat = MTReactorHeatHatch.DEFAULT_HEAT_CAPACITY;
        private float hem = 1.0F;
        private float pageOutput = 0.0F;
        /** Simulation mode: everything is redirected to the snapshot below, never to the real reactor. */
        private boolean simulating;
        private ItemStack[] simulatedItems;
        private int simulatedHeat;

        void bind(MTReactorAccessHatch hatch, int page, int heatIndex) {
            this.hatch = hatch;
            this.page = page;
            this.heatIndex = heatIndex;
            this.maxHeat = getReactorHeatCapacity();
            this.hem = 1.0F;
            this.pageOutput = 0.0F;
            this.simulating = false;
            this.simulatedItems = null;
            this.simulatedHeat = 0;
        }

        void bindSimulation(ItemStack[] items, int heat) {
            this.hatch = null;
            this.page = -1;
            this.heatIndex = -1;
            this.simulatedItems = items;
            this.simulatedHeat = heat;
            this.maxHeat = getReactorHeatCapacity();
            this.hem = 1.0F;
            this.pageOutput = 0.0F;
            this.simulating = true;
        }

        int getSimulatedHeat() {
            return simulatedHeat;
        }

        @Override
        public ChunkCoordinates getPosition() {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            return new ChunkCoordinates(base.getXCoord(), base.getYCoord(), base.getZCoord());
        }

        @Override
        public World getWorld() {
            return MTReactor.this.getBaseMetaTileEntity()
                .getWorld();
        }

        @Override
        public int getHeat() {
            return simulating ? simulatedHeat : mPageHeat[heatIndex];
        }

        @Override
        public void setHeat(int heat) {
            if (simulating) {
                simulatedHeat = heat;
            } else {
                mPageHeat[heatIndex] = heat;
            }
        }

        @Override
        public int addHeat(int amount) {
            if (simulating) {
                simulatedHeat += amount;
                return simulatedHeat;
            }
            mPageHeat[heatIndex] += amount;
            return mPageHeat[heatIndex];
        }

        @Override
        public int getMaxHeat() {
            return maxHeat;
        }

        @Override
        public void setMaxHeat(int newMaxHeat) {
            // The heat ceiling is fixed by the heat control hatch, so component contributions (reactor plating and
            // friends) are ignored on purpose instead of stacking on top of the hatch value.
        }

        @Override
        public void addEmitHeat(int heat) {
            // Fluid cooling is not supported by this reactor.
        }

        @Override
        public float getHeatEffectModifier() {
            return hem;
        }

        @Override
        public void setHeatEffectModifier(float newHem) {
            this.hem = newHem;
        }

        @Override
        public float getReactorEnergyOutput() {
            return pageOutput;
        }

        @Override
        public double getReactorEUEnergyOutput() {
            return pageOutput * EU_PER_OUTPUT;
        }

        @Override
        public float addOutput(float energy) {
            return pageOutput += energy;
        }

        @Override
        public ItemStack getItemAt(int x, int y) {
            if (x < 0 || x >= MTReactorAccessHatch.PAGE_WIDTH || y < 0 || y >= MTReactorAccessHatch.PAGE_HEIGHT) {
                return null;
            }
            if (simulating) {
                return simulatedItems[y * MTReactorAccessHatch.PAGE_WIDTH + x];
            }
            return hatch.getReactorItem(page, x, y);
        }

        @Override
        public void setItemAt(int x, int y, ItemStack item) {
            if (x < 0 || x >= MTReactorAccessHatch.PAGE_WIDTH || y < 0 || y >= MTReactorAccessHatch.PAGE_HEIGHT) {
                return;
            }
            if (simulating) {
                simulatedItems[y * MTReactorAccessHatch.PAGE_WIDTH + x] = item;
                return;
            }
            hatch.setReactorItem(page, x, y, item);
        }

        @Override
        public void explode() {
            if (!simulating) {
                explodeReactor();
            }
        }

        @Override
        public int getTickRate() {
            return CYCLE_TICKS;
        }

        @Override
        public boolean produceEnergy() {
            // The simulation always assumes the reactor is running, so switching the machine off does not make the
            // stability check meaningless: it shows whether the current layout would explode once enabled.
            if (simulating) return true;
            IGregTechTileEntity base = MTReactor.this.getBaseMetaTileEntity();
            return base != null && base.isAllowedToWork();
        }

        @Override
        public void setRedstoneSignal(boolean redstone) {
            // The GT controller's power switch is the redstone equivalent.
        }

        @Override
        public boolean isFluidCooled() {
            return false;
        }
    }
    // endregion
}
