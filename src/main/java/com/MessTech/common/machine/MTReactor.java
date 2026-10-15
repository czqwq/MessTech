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
import net.minecraft.entity.player.EntityPlayer;
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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.explosion.MTExplosionDE;
import com.MessTech.common.gui.MTReactorGui;
import com.MessTech.common.machine.Base.MTGeneratorMultiBase;
import com.MessTech.common.machine.hatch.MTReactorAccessHatch;
import com.MessTech.common.machine.hatch.MTReactorCoolantInputHatch;
import com.MessTech.common.machine.hatch.MTReactorHeatHatch;
import com.MessTech.common.machine.hatch.MTReactorHotCoolantOutputHatch;
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
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import ic2.core.IC2DamageSource;
import ic2.core.init.MainConfig;
import ic2.core.util.ConfigUtil;
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
 * single {@link MTReactorHeatHatch} of the structure and not by the reactor components - except for the heat a reactor
 * plating adds, which is accumulated on top (see {@link MTReactorHeatSystem#addMaxHeat(int)}).</li>
 * <li>The heat cycle runs in {@link #onPostTick}, not in {@code checkProcessing}: it therefore keeps running while the
 * machine is switched off, which is what lets the heat controls bring a hot reactor back down. Only the fuel and energy
 * passes are gated by the power switch.</li>
 * <li>At 85% heat the core can ignite its surroundings, at 70% it irradiates entities, and at 100% a 30 second meltdown
 * countdown starts, after which the page explodes. The explosion power is computed from the reactor components'
 * {@code influenceExplosion}. See {@link MTReactorHeatSystem} for the state and the countdown.</li>
 * <li>The generated energy ({@code output * 5 EU/t * balance/energy/generator/nuclear}, the IC2 conversion, see
 * {@link #getEuPerOutput()}) is emitted through dynamo hatches.</li>
 * </ul>
 */
public class MTReactor extends MTGeneratorMultiBase<MTReactor> implements ISurvivalConstructable {

    /**
     * IC2 reactor tick rate: components are processed once every 20 ticks. Kept as a separate constant because the heat
     * system owns its own copy for the state it manages.
     */
    public static final int CYCLE_TICKS = MTReactorHeatSystem.CYCLE_TICKS;
    /**
     * IC2's own EU/t conversion of the accumulated reactor output, before the pack's balance factor:
     * {@code TileEntityNuclearReactorElectric#getOfferedEnergy()} returns
     * {@code output * 5.0F * balance/energy/generator/nuclear}.
     */
    private static final float IC2_EU_PER_OUTPUT = 5.0F;
    /**
     * The extra factor of 2 on top of IC2's conversion. This is a deliberate MessTech balance decision (it doubles the
     * output of the ported IC2 numbers); it is not read from any config, so it is named here instead of being an
     * unexplained literal - see {@code docs/GT5U-NOTES.md} section 15.
     */
    private static final int MESSTECH_OUTPUT_MULTIPLIER = 2;
    /** Simulated reactor cycles used by the stability prediction, i.e. how far ahead the GUI can look. */
    private static final int STABILITY_PREDICTION_CYCLES = 20;
    /** Consecutive ticks of "the dynamos took less than the rated output" before the machine reports it. */
    private static final int OUTPUT_SHORTFALL_REPORT_TICKS = 20;

    // Why not hardcoded?
    public static float getEuPerOutput() {
        if (MainConfig.get() == null) return IC2_EU_PER_OUTPUT * 2 * 5;
        return IC2_EU_PER_OUTPUT * ConfigUtil.getFloat(MainConfig.get(), "balance/energy/generator/nuclear")
            * MESSTECH_OUTPUT_MULTIPLIER;
    }

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

    /**
     * Custom structure element for the coolant input hatch of the fluid mode. Declared here, above
     * {@link #STRUCTURE_DEFINITION}, because that static initializer reads all four {@code REACTOR_*} elements: a
     * static field may not be referenced before its own declaration.
     */
    public static final IHatchElement<MTReactor> REACTOR_COOLANT_INPUT = new IHatchElement<MTReactor>() {

        @Override
        public List<Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTReactorCoolantInputHatch.class);
        }

        @Override
        public IGTHatchAdder<? super MTReactor> adder() {
            return MTReactor::addReactorCoolantInputToMachineList;
        }

        @Override
        public String name() {
            return "mt_reactor_coolant_input";
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal("machine.mtreactor.coolantinput.name");
        }

        @Override
        public String getDescriptionLangKey() {
            return "machine.mtreactor.coolantinput.name";
        }

        @Override
        public long count(MTReactor t) {
            return t.mReactorCoolantInputs.size();
        }
    };

    /** Custom structure element for the hot coolant output hatch of the fluid mode. */
    public static final IHatchElement<MTReactor> REACTOR_COOLANT_OUTPUT = new IHatchElement<MTReactor>() {

        @Override
        public List<Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTReactorHotCoolantOutputHatch.class);
        }

        @Override
        public IGTHatchAdder<? super MTReactor> adder() {
            return MTReactor::addReactorCoolantOutputToMachineList;
        }

        @Override
        public String name() {
            return "mt_reactor_coolant_output";
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal("machine.mtreactor.coolantoutput.name");
        }

        @Override
        public String getDescriptionLangKey() {
            return "machine.mtreactor.coolantoutput.name";
        }

        @Override
        public long count(MTReactor t) {
            return t.mReactorCoolantOutputs.size();
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
                .atLeast(HatchElement.InputHatch, REACTOR_ACCESS_HATCH, REACTOR_COOLANT_INPUT)
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
                .atLeast(REACTOR_HEAT_HATCH, HatchElement.OutputBus, HatchElement.InputBus, REACTOR_COOLANT_OUTPUT)
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
    /** Coolant input hatches of the fluid (wet) mode. Only used while that mode is enabled. */
    public final List<MTReactorCoolantInputHatch> mReactorCoolantInputs = new ArrayList<>();
    /** Hot coolant output hatches of the fluid (wet) mode. */
    public final List<MTReactorCoolantOutputProxy> mReactorCoolantOutputs = new ArrayList<>();
    /** Per-page heat, meltdown countdown and the passive/fluid heat exchange. See {@link MTReactorHeatSystem}. */
    private final MTReactorHeatSystem mHeat = new MTReactorHeatSystem(this);
    /** EU/t that is currently emitted through dynamo hatches (IC2 keeps the output constant between cycles). */
    private long mReactorEUPerTick = 0;
    /** EU/t the dynamo hatches actually swallowed during the last tick; lower than the rated value means loss. */
    private long mReactorActualEUPerTick = 0;
    /** Rated output the shortfall detector compares against, and how long the shortfall already persists. */
    private long mOutputShortfallRated = 0;
    private int mOutputShortfallTicks = 0;
    /** Energy stored in the dynamo hatches at the start of the previous tick, sampled to measure the accepted EU. */
    private long mLastDynamoEnergy = -1;
    /** Ticks until the next IC2 reactor material cycle. */
    private int mReactorCycleTicks = 0;
    /** True once the machine could run at the start of the last tick (everything except the power switch). */
    private boolean mLastCouldRun = false;
    /** True while a page is at or over its ceiling; the machine stops producing until the countdown resolves. */
    private boolean mReactorMeltingDown = false;
    /** Result of the multi-cycle heat prediction shown in the GUI/Waila: cycles left before the meltdown, or -1. */
    private int mCyclesToMeltdown = -1;
    /** Cheap fingerprint of all reactor items, used to re-run the live simulation only when something changed. */
    private int mReactorInventorySignature = 0;
    /** Enables automatic input into / output from every locked access hatch slot. Enabled by default. */
    private boolean mAutoMatchTarget = true;
    /** Locked slots and their components that the next heat pass is predicted to destroy. */
    private final List<DoomedSlot> mNextCycleDestroyed = new ArrayList<>();
    /** True when any locked slot would be destroyed by the next heat cycle. */
    private boolean mNextCycleDoomed = false;
    /** Access hatches sorted by coordinates, refreshed once per material cycle. */
    private final List<MTReactorAccessHatch> mSortedHatches = new ArrayList<>();
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
     * Runs the heat cycle, the meltdown countdown and the EU bookkeeping, none of which the base class's
     * {@code runMachine} would ever reach while the machine is switched off.
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            // The machine had the whole previous tick to push its generation into the dynamo hatches. Sampling the
            // accepted amount here is what turns "the rated output" into "the output that really made it out".
            settleActualOutputEU();
            refreshHatches();
            // The hatch lists were just rebuilt from the live structure, so this is the safe place to re-derive the
            // ceiling: doing it before the refresh would rescale onto the previous hatch's value and leave one cycle
            // in which the stored heat is compared against the old ceiling.
            //
            // Guarded by mMachine for the same reason the pruning above is. While the shape is broken the heat hatch
            // list is empty, so getReactorHeatCapacity() falls back to the EV default and this call would rescale the
            // store onto a ceiling the machine does not actually have - and, worse, clear the meltdown countdown, since
            // a ceiling change deliberately grants a fresh 30 s grace window. That made "wrench one casing" a way to
            // defuse a running meltdown. With the guard, a ceiling change is only ever applied by a formed machine.
            if (mMachine) {
                mHeat.onCapacityChanged(getReactorHeatCapacity());
            }
            boolean couldRun = couldRun();
            mLastCouldRun = couldRun;
            mReactorMeltingDown = mHeat.isCountdownActive();
            if (!couldRun) {
                // No fuel path at all: the machine must not advertise EU or output it.
                mReactorEUPerTick = 0;
            }
            // Heat bleeding is per game tick, so it also works while the machine is switched off or paused. The
            // ingredients only advance during a real material cycle, which is the part the power switch controls.
            if (mHeat.tick()) markDirty();
            // A page whose grace window ran out detonates here, before any material work of this tick.
            if (explodeExpiredCountdowns(aTick)) {
                super.onPostTick(aBaseMetaTileEntity, aTick);
                snapshotDynamoEnergy();
                return;
            }
            if (mReactorCycleTicks <= 0) {
                if (runMaterialCycle(aTick)) {
                    // A page detonated; the structure is being removed, so do not report anything else this tick.
                    super.onPostTick(aBaseMetaTileEntity, aTick);
                    snapshotDynamoEnergy();
                    return;
                }
            } else {
                mReactorCycleTicks--;
            }
            ensureLiveSimulation();
            if (mAutoMatchTarget && !mReactorAccessHatches.isEmpty() && processAutoMatchTarget()) {
                // Inventory changed; refresh the prediction and let the controller re-check immediately.
                mReactorInventorySignature = 0;
                ensureLiveSimulation();
                scheduleRecipeCheckImmediate();
            }
            // While paused for a locked-component problem, poll for a manual/automatic fix.
            if (aBaseMetaTileEntity.wasShutdown()
                && isReactorSoftStopReason(aBaseMetaTileEntity.getLastShutDownReason())) {
                scheduleRecipeCheckImmediate();
            }
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // Baseline for the next tick's "did the dynamos really take it" measurement: sampled after the base class has
        // had its chance to push this tick's generation out.
        snapshotDynamoEnergy();
    }

    private static boolean isReactorSoftStopReason(ShutDownReason reason) {
        if (reason == null) return false;
        String key = reason.getKey();
        return "reactor_component_missing".equals(key) || "reactor_component_low".equals(key);
    }

    /** True when the structure, the components and the power switch all allow the reactor to run. */
    private boolean couldRun() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isAllowedToWork()) return false;
        if (hasMissingLockedComponents() || hasLowOrDoomedLockedComponents()) return false;
        if (mHeat.isFluidMode() && (hasNoCoolantInput() || mReactorCoolantOutputs.isEmpty())) {
            return false;
        }
        return true;
    }

    private boolean hasNoCoolantInput() {
        for (MTReactorCoolantInputHatch hatch : mReactorCoolantInputs) {
            if (hatch != null) return false;
        }
        return true;
    }

    /**
     * Rebuilds the sorted access-hatch list and prunes the heat store.
     * <p>
     * Pruning only happens while the structure is formed. GT calls {@code clearHatches()} at the start of every
     * structure re-check and {@code checkMachine} bails out early when the shape does not match, so on a broken
     * structure the hatch lists are legitimately empty - pruning from that state would delete the reactor's entire
     * heat store and cancel a running meltdown countdown one tick after any single casing was removed, which is
     * exactly what a player does when swapping the heat control hatch. Waiting for a formed structure still satisfies
     * the reason pruning exists at all: a hatch that really is gone is forgotten on the first successful re-check, so
     * its heat can never be inherited by a different hatch at the same coordinates.
     */
    private void refreshHatches() {
        List<MTReactorAccessHatch> sorted = new ArrayList<>(mReactorAccessHatches.size());
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch == null) continue;
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base == null || base.isDead()) continue;
            sorted.add(hatch);
        }
        sorted.sort(
            Comparator.comparingInt(
                (MTReactorAccessHatch hatch) -> hatch.getBaseMetaTileEntity()
                    .getXCoord())
                .thenComparingInt(
                    hatch -> hatch.getBaseMetaTileEntity()
                        .getYCoord())
                .thenComparingInt(
                    hatch -> hatch.getBaseMetaTileEntity()
                        .getZCoord()));
        mSortedHatches.clear();
        mSortedHatches.addAll(sorted);
        if (mMachine) {
            mHeat.retainOnly(sorted);
        }
        int totalPages = getTotalPageCount();
        if (totalPages > MTReactorHeatSystem.MAX_PAGES) {
            // The structure cannot offer this many pages, so reaching it means a layout this class does not model.
            // Falling back on a silent truncation used to skip the heat effects of the tail pages entirely.
            softStopReactor(ExtraShutdownReason.TOO_MANY_PAGES);
        }
    }

    /**
     * Samples how much of the rated output the dynamo hatches really swallowed. A generator does not buffer, so
     * anything the dynamos refuse is lost; comparing the rated value against the actually accepted one turns that
     * silent loss into something the player can see.
     */
    private void settleActualOutputEU() {
        long energy = getStoredDynamoEnergy();
        if (mLastDynamoEnergy >= 0) {
            long delta = energy - mLastDynamoEnergy;
            if (mReactorEUPerTick > 0 && delta < (mReactorEUPerTick * 9) / 10) {
                mOutputShortfallTicks++;
                mOutputShortfallRated = mReactorEUPerTick;
            } else {
                mOutputShortfallTicks = 0;
                mOutputShortfallRated = 0;
            }
            mReactorActualEUPerTick = Math.max(0, delta);
        }
        // The snapshot is taken after the base class has had its chance to inject this tick's generation.
        mLastDynamoEnergy = -1;
    }

    /**
     * Energy currently buffered in every dynamo hatch. Used to measure how much of the rated output actually made it
     * out of the machine: the exotic dynamo list holds plain {@link MTEHatch} entries, so it is walked directly.
     */
    private long getStoredDynamoEnergy() {
        long total = 0;
        for (MTEHatchDynamo dynamo : filterValidMTEs(mDynamoHatches)) {
            total += dynamo.getBaseMetaTileEntity()
                .getStoredEU();
        }
        for (MTEHatch dynamo : mExoticDynamoHatches) {
            if (dynamo == null) continue;
            IGregTechTileEntity base = dynamo.getBaseMetaTileEntity();
            if (base == null) continue;
            total += base.getStoredEU();
        }
        return total;
    }

    /** True while the dynamo hatches have been refusing a meaningful part of the rated output. */
    public boolean hasOutputShortfall() {
        return mOutputShortfallTicks >= OUTPUT_SHORTFALL_REPORT_TICKS && mOutputShortfallRated > 0;
    }

    /** Refreshes the dynamo energy baseline the next tick's shortfall measurement compares against. */
    private void snapshotDynamoEnergy() {
        mLastDynamoEnergy = getStoredDynamoEnergy();
    }

    private void ensureLiveSimulation() {
        if (mReactorAccessHatches.isEmpty()) return;
        if (computeInventorySignature() != mReactorInventorySignature) {
            predictFuture();
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
        checkAccessHatchTier(errors);
        // The fluid (wet) mode needs both of its hatches; a missing one is a soft shutdown (see checkProcessing), not
        // an
        // invalid structure, so the machine can keep its shape while the player builds them.
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

    /**
     * A heat control hatch caps the tier of the access hatches it drives. The structure may hold at most as many pages
     * as the heat ceiling is meant to back, so an UIV access hatch cannot be paired with an EV heat hatch. Validation
     * lives here rather than in the hatches: a hatch only carries data, the controller decides what is legal.
     */
    private void checkAccessHatchTier(List<StructureError> errors) {
        if (mReactorHeatHatches.isEmpty() || mReactorAccessHatches.isEmpty()) return;
        int maxTier = getHeatControlTier();
        for (MTReactorAccessHatch hatch : mReactorAccessHatches) {
            if (hatch == null) continue;
            if (hatch.mTier > maxTier) {
                errors.add(StructureErrors.of("machine.mtreactor.structure.error.access_hatch_tier"));
                return;
            }
        }
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        mReactorAccessHatches.clear();
        mReactorHeatHatches.clear();
        mReactorCoolantInputs.clear();
        mReactorCoolantOutputs.clear();
        // The stored heat is deliberately NOT dropped here. GT calls clearHatches() at the start of every structure
        // re-check (and a re-check runs after any block update, plus from setFluidMode / setAutoMatchTarget), so
        // dropping it here deleted the reactor's whole heat state - and a running meltdown countdown - several times a
        // minute. Pruning is already handled exactly and continuously by refreshHatches() -> mHeat.retainOnly(live),
        // which only forgets hatches that are genuinely gone.
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

    /** Structure adder for the coolant input hatch of the fluid mode. */
    public boolean addReactorCoolantInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MTReactorCoolantInputHatch hatch)) return false;
        hatch.updateTexture(aBaseCasingIndex);
        if (!mReactorCoolantInputs.contains(hatch)) {
            mReactorCoolantInputs.add(hatch);
        }
        return true;
    }

    /** Structure adder for the hot coolant output hatch of the fluid mode. */
    public boolean addReactorCoolantOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MTReactorHotCoolantOutputHatch hatch)) return false;
        for (MTReactorCoolantOutputProxy existing : mReactorCoolantOutputs) {
            if (existing.hatch == hatch) return true;
        }
        mReactorCoolantOutputs.add(new MTReactorCoolantOutputProxy(hatch, aBaseCasingIndex));
        return true;
    }

    /**
     * Heat ceiling of the reactor, taken from the installed heat control hatch. While the structure is incomplete
     * (no hatch at all) the EV value is used, so the GUI and the stability simulation always have a meaningful
     * number to show. Should several hatches ever be present, the lowest ceiling wins to stay on the safe side.
     */
    public int getReactorHeatCapacity() {
        boolean found = false;
        int capacity = Integer.MAX_VALUE;
        for (MTReactorHeatHatch hatch : mReactorHeatHatches) {
            if (hatch == null) continue;
            found = true;
            capacity = Math.min(capacity, hatch.getHeatCapacity());
        }
        if (!found) return MTReactorHeatHatch.DEFAULT_HEAT_CAPACITY;
        return Math.min(capacity, MTReactorHeatSystem.MAX_HEAT_CAPACITY);
    }

    /**
     * Tier of the installed heat control hatch, or the lowest supported tier while none is installed. Drives the
     * passive cooling rate and the access hatch tier limit.
     */
    public int getHeatControlTier() {
        int tier = MTReactorHeatHatch.MIN_TIER;
        for (MTReactorHeatHatch hatch : mReactorHeatHatches) {
            if (hatch == null) continue;
            tier = Math.max(tier, hatch.getHeatControlTier());
        }
        return tier;
    }

    public MTReactorHeatSystem getHeatSystem() {
        return mHeat;
    }

    // region Locked slot auto input / output

    public boolean hasMissingLockedComponents() {
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            if (hatch != null && hatch.hasMissingLockedComponents()) return true;
        }
        return false;
    }

    /** True when a locked component is at/below its output threshold, or would be destroyed next cycle. */
    public boolean hasLowOrDoomedLockedComponents() {
        if (mNextCycleDoomed) return true;
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
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
     * Moves finished, or about-to-be-destroyed, components out to the output buses and refills empty locked slots from
     * the input buses.
     * <p>
     * A locked slot holding a <b>full durability</b> component is never pre-emptively output: a fresh component that is
     * predicted to die next cycle means the layout itself is wrong, and swapping it cannot help - in that case the
     * machine stays soft-stopped instead of consuming input items.
     *
     * @return true when a locked slot changed
     */
    private boolean processAutoMatchTarget() {
        boolean changed = false;
        for (MTReactorAccessHatch hatch : mSortedHatches) {
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base == null) continue;
            for (int page = 0; page < hatch.getPageCount(); page++) {
                for (int localSlot = 0; localSlot < MTReactorAccessHatch.SLOTS_PER_PAGE; localSlot++) {
                    int slot = page * MTReactorAccessHatch.SLOTS_PER_PAGE + localSlot;
                    if (!hatch.isSlotLocked(slot)) continue;
                    ItemStack memory = hatch.getSlotMemory(slot);
                    if (memory == null) continue;
                    int threshold = hatch.getSlotThreshold(slot);
                    ItemStack current = hatch.getSlotStack(slot);
                    // Pull a doomed component before the fatal cycle, but only when it already lost durability.
                    boolean doomed = current != null && isDoomed(hatch, page, localSlot)
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

    private boolean isDoomed(MTReactorAccessHatch hatch, int page, int localSlot) {
        if (mNextCycleDestroyed.isEmpty()) return false;
        ChunkCoordinates key = key(hatch);
        for (DoomedSlot doomed : mNextCycleDestroyed) {
            if (doomed.page == page && doomed.localSlot == localSlot && doomed.owner.equals(key)) return true;
        }
        return false;
    }

    /**
     * A locked slot is emptied when the item is no longer the remembered item type (a depleted fuel rod for example) or
     * when its remaining durability is <b>less than or equal to</b> the configured percentage.
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
        // The heat cycle lives in onPostTick; this method only reports the currently generating state and keeps the
        // soft-stop reasons of the locked-component handling. It must not run any heat math: it is skipped entirely
        // while the machine is switched off, and heat has to keep working then.
        if (hasMissingLockedComponents()) {
            softStopReactor(ExtraShutdownReason.MISS_REACTOR_COMPONENT);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        if (hasLowOrDoomedLockedComponents()) {
            softStopReactor(ExtraShutdownReason.REACTOR_COMPONENT_LOW);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        if (mHeat.isFluidMode() && (hasNoCoolantInput() || mReactorCoolantOutputs.isEmpty())) {
            softStopReactor(ExtraShutdownReason.MISS_COOLANT_HATCH);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
        clearReactorSoftStop();

        if (mReactorMeltingDown || mReactorEUPerTick <= 0) {
            // A meltdown in progress or an idle core: no generation, but the machine keeps its shape so the countdown
            // can still be cancelled and the residual heat can still bleed off.
            mMaxProgresstime = 0;
            lEUt = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }
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

    /** Sorted, live view of the access hatches, refreshed once per tick by {@link #refreshHatches()}. */
    private List<MTReactorAccessHatch> getSortedReactorHatches() {
        return mSortedHatches;
    }

    private static ChunkCoordinates key(MTReactorAccessHatch hatch) {
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null) return null;
        return new ChunkCoordinates(base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    /**
     * Runs one full IC2 material cycle: every page is visited once, on the tick the cycle comes around.
     * <p>
     * The pages are processed in one pass rather than spread over the 20 ticks of the cycle, which is exactly what the
     * ported version did and what keeps the page cursor trivial. The cycle itself is therefore a short burst of work
     * per
     * page count, once every {@value #CYCLE_TICKS} ticks - the same shape IC2's own single-grid reactor has. What the
     * burst does <b>not</b> do is hold the meltdown countdown hostage: the countdown runs on an absolute tick deadline
     * (see {@link MTReactorHeatSystem#armCountdown}), so its 30 seconds are 30 real seconds no matter how many pages
     * the
     * structure has.
     *
     * @return true when a page detonated
     */
    private boolean runMaterialCycle(long now) {
        mHeat.refreshCountdownClock(now);
        boolean melting = mHeat.isCountdownActive();
        boolean allowOutput = mLastCouldRun;
        for (MTReactorAccessHatch hatch : mSortedHatches) {
            if (hatch == null) continue;
            for (int page = 0; page < hatch.getPageCount(); page++) {
                if (hatch.isPageEmpty(page)) {
                    mHeat.clearCountdown(hatch, page);
                    continue;
                }
                tickPage(hatch, page, melting, allowOutput);
            }
        }
        finishCycle(now);
        return false;
    }

    /**
     * One page's material tick.
     *
     * @param melting     the core is already dying, so no more energy is produced
     * @param allowOutput the machine may run, i.e. the power switch is on and the components are complete. When it is
     *                    false the heat pass still runs (heat has to keep moving whether or not the machine is
     *                    switched on) but the energy pass is skipped, so a stopped reactor neither produces EU nor
     *                    charges its dynamos - IC2's {@code produceEnergy()} semantics.
     */
    private void tickPage(MTReactorAccessHatch hatch, int page, boolean melting, boolean allowOutput) {
        // A removed plating has to stop contributing immediately, so this page's bonus is re-collected from scratch.
        mHeat.resetPageBonus(hatch, page);
        mReactorContext.bind(hatch, page);
        processPage(melting, allowOutput);
        // The heat effect modifier belongs to the page, so the world effects have to take the hem of the hottest page,
        // not whatever page happened to be bound last.
        if (mHeat.getPageHeat(hatch, page) >= mHeat.getHottestHeat()) {
            mHottestHem = mReactorContext.hem;
        }
        int overflow = releaseCoolantHeat();
        if (overflow > 0) {
            // The fluid loop could not absorb the component heat; IC2 pushes half of it back onto the core.
            mHeat.addPageHeat(hatch, page, overflow);
        }
    }

    /** The IC2 component passes of one page: heat first, then energy unless the core is not producing. */
    private void processPage(boolean melting, boolean allowOutput) {
        if (melting || !allowOutput) {
            processChambers(true);
            mReactorContext.pageOutput = 0.0F;
            return;
        }
        processChambers(false);
        mPendingOutput += mReactorContext.pageOutput;
    }

    /**
     * Publishes the cycle's accumulated output, refreshes the prediction, and runs the world heat effects once for the
     * whole reactor.
     */
    private void finishCycle(long now) {
        mReactorEUPerTick = mLastCouldRun ? (long) (mPendingOutput * getEuPerOutput()) : 0L;
        mPendingOutput = 0.0F;
        mReactorCycleTicks = CYCLE_TICKS;
        predictFuture();
        armCountdowns(now);
        // IC2's world effects (ignite / irradiate / evaporate water / ignite wood) are a property of the whole core,
        // not of one page, so they run once per cycle on the hottest page's ratio.
        int limit = Math.max(1, mHeat.getEffectiveMaxHeat());
        if (mHeat.getHottestHeat() > 0) {
            applyHeatEffects((float) mHeat.getHottestHeat() / (float) limit, mHottestHem <= 0.0F ? 1.0F : mHottestHem);
        }
        markDirty();
    }

    /**
     * Starts the grace window of every page that is at or over its own ceiling. A page that is already counting down
     * keeps its original deadline, so keeping the heat pinned at the ceiling cannot postpone the meltdown forever.
     */
    private void armCountdowns(long now) {
        for (MTReactorAccessHatch hatch : mSortedHatches) {
            if (hatch == null) continue;
            for (int page = 0; page < hatch.getPageCount(); page++) {
                if (hatch.isPageEmpty(page)) {
                    mHeat.clearCountdown(hatch, page);
                    continue;
                }
                if (mHeat.getPageHeat(hatch, page) >= mHeat.getPageMaxHeat(hatch, page)) {
                    mHeat.armCountdown(hatch, page, now);
                } else {
                    mHeat.clearCountdown(hatch, page);
                }
            }
        }
    }

    /**
     * Detonates the pages whose grace window ran out.
     * <p>
     * The detonation does <b>not</b> depend on resolving the page's hatch from the live structure. It used to: an
     * expired entry was consumed by {@link MTReactorHeatSystem#takeExpiredCountdowns(long)} and then dropped on the
     * floor when {@code findHatch} came back empty, which is exactly the state a broken structure leaves behind - so
     * pulling one casing or the heat control hatch and keeping it off across the deadline defused the meltdown, and
     * the 30 s guarantee became repeatable every 30 s. While the shape is broken no cycle runs, so nothing could have
     * re-armed it either. The blast is anchored on the controller, and the component influence loop simply finds no
     * parts to add, so the explosion is the base power - a small reactor that detonated while disassembled still
     * explodes, which is the behaviour the countdown promises.
     *
     * @return true when a page exploded
     */
    private boolean explodeExpiredCountdowns(long now) {
        List<MTReactorHeatSystem.ArmedPage> expired = mHeat.takeExpiredCountdowns(now);
        if (expired.isEmpty()) return false;
        // One blast is enough: the first expired page detonates the whole machine, exactly as the in-cycle path did.
        MTReactorHeatSystem.ArmedPage first = expired.get(0);
        explodeReactor(findHatch(first.owner), first.page, mHottestHem <= 0.0F ? 1.0F : mHottestHem);
        return true;
    }

    /** The access hatch at these coordinates, or null while the structure is broken. */
    private MTReactorAccessHatch findHatch(ChunkCoordinates key) {
        for (MTReactorAccessHatch hatch : mSortedHatches) {
            ChunkCoordinates own = key(hatch);
            if (own != null && own.equals(key)) return hatch;
        }
        return null;
    }

    /**
     * Exactly IC2's {@code processChambers()}: heat pass first, then the energy pass.
     *
     * @param heatPassOnly true to only run the heat pass, used by the stability prediction
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

    // endregion

    // region Heat prediction

    /**
     * Multi-cycle heat prediction. The single-cycle version could only answer "is the next cycle fatal", so a page that
     * crossed the ceiling after two or three cycles was reported as stable until the frame it detonated. This runs the
     * heat pass repeatedly on copies, so the GUI/WAILA can show how many cycles are left, and it also collects the
     * locked slots the next cycle would destroy.
     */
    private void predictFuture() {
        mNextCycleDestroyed.clear();
        mNextCycleDoomed = false;
        mCyclesToMeltdown = -1;
        boolean stable = true;

        for (MTReactorAccessHatch hatch : mSortedHatches) {
            if (hatch == null) continue;
            for (int page = 0; page < hatch.getPageCount(); page++) {
                // A page without items can neither add nor redistribute heat, so it cannot start exploding.
                if (hatch.isPageEmpty(page)) continue;
                ItemStack[] snapshot = snapshotPage(hatch, page);
                int heat = mHeat.getPageHeat(hatch, page);
                int platingBonus = mHeat.collectPlatingBonus(snapshot);
                int limit = mHeat.getMaxHeatWithBonus(platingBonus);

                mReactorContext.bindSimulation(snapshot, heat, platingBonus);
                processChambers(true);
                int nextHeat = mReactorContext.getSimulatedHeat();

                // Detect locked components the next heat pass would destroy entirely (for example a coolant cell that
                // cannot absorb the next heat pulse). Converted items (depleted fuel rods) are fine.
                int baseSlot = page * MTReactorAccessHatch.SLOTS_PER_PAGE;
                for (int localSlot = 0; localSlot < MTReactorAccessHatch.SLOTS_PER_PAGE; localSlot++) {
                    int slot = baseSlot + localSlot;
                    if (!hatch.isSlotLocked(slot)) continue;
                    ItemStack original = hatch.getSlotStack(slot);
                    if (original != null && snapshot[localSlot] == null) {
                        mNextCycleDestroyed.add(new DoomedSlot(key(hatch), page, localSlot));
                        mNextCycleDoomed = true;
                    }
                }

                // Extrapolate the same heat pass until the page stops heating up or crosses the ceiling. The single
                // cycle version could only answer "is the next cycle fatal", so a page that crossed its ceiling on the
                // second or third cycle was displayed as stable until the frame it detonated.
                int projected = nextHeat;
                int cycle = 1;
                if (projected < limit) {
                    for (cycle = 2; cycle <= STABILITY_PREDICTION_CYCLES; cycle++) {
                        mReactorContext.bindSimulation(snapshot, projected, platingBonus);
                        processChambers(true);
                        int stepped = mReactorContext.getSimulatedHeat();
                        if (stepped <= projected) {
                            // Heating stopped: the page found its own equilibrium below the ceiling.
                            cycle = -1;
                            break;
                        }
                        projected = stepped;
                        if (projected >= limit) break;
                    }
                }
                if (projected >= limit && cycle > 0) {
                    stable = false;
                    if (mCyclesToMeltdown < 0 || cycle < mCyclesToMeltdown) {
                        mCyclesToMeltdown = Math.min(cycle, STABILITY_PREDICTION_CYCLES);
                    }
                }
            }
        }
        mReactorStable = stable;
        mReactorInventorySignature = computeInventorySignature();
    }

    /**
     * Cheap fingerprint of every item in every page (identity, damage, stack size and NBT). Used to detect item
     * changes without deep-copying the inventory for the simulation. The NBT hash is only folded in every few ticks,
     * because it is the expensive part and fuel rods change their NBT on every cycle anyway.
     */
    private int computeInventorySignature() {
        int signature = 1;
        for (MTReactorAccessHatch hatch : mSortedHatches) {
            // Lock state / thresholds: a lock change must also refresh the destruction prediction.
            for (byte state : hatch.getSlotStateArray()) {
                signature = 31 * signature + state;
            }
            for (int page = 0; page < hatch.getPageCount(); page++) {
                for (int y = 0; y < MTReactorAccessHatch.PAGE_HEIGHT; y++) {
                    for (int x = 0; x < MTReactorAccessHatch.PAGE_WIDTH; x++) {
                        ItemStack stack = hatch.getReactorItem(page, x, y);
                        if (stack == null) continue;
                        signature = 31 * signature + System.identityHashCode(stack.getItem());
                        signature = 31 * signature + stack.getItemDamage();
                        signature = 31 * signature + stack.stackSize;
                    }
                }
            }
        }
        return signature;
    }

    /** Copies one 9x6 page so the prediction cannot damage or deplete the real components. */
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

    // endregion

    // region Heat effects

    /**
     * IC2's heat effect logic, scaled to the hottest page's own heat cap and heat effect modifier.
     * <p>
     * The <b>world</b> effects (igniting air, irradiating entities, evaporating water, igniting wood) run once per
     * cycle using the hottest page's power, not once per page. IC2 has a single grid, so a multi-page reactor running
     * the per-page version irradiated the same entity and ignited the same air block once per over-heating page.
     * Whether a page actually melts down is still decided per page, in {@link #armCountdowns} and
     * {@link #explodeExpiredCountdowns}.
     *
     * @param power heat ratio of the hottest page, already clamped to at least 0
     */
    private void applyHeatEffects(float power, float hem) {
        if (power < 0.4F) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (world == null) return;
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
     * <p>
     * The heat of every page is reset here, on purpose: the structure is being removed by the blast, but the controller
     * tile entity (and with it this heat store) survives the {@code explodeMultiblock()} call for the rest of the tick,
     * so a hot store would otherwise be left behind on a dead machine. The energy the cycle accumulated before the
     * detonation is settled first, so the pages that melted down do not also cost the player their output.
     */
    private void explodeReactor(MTReactorAccessHatch explodedHatch, int explodedPage, float hem) {
        float boomPower = 10.0F;
        float boomMod = 1.0F;

        for (MTReactorAccessHatch hatch : mSortedHatches) {
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

        mHeat.clear();
        mReactorEUPerTick = 0;
        // Settle what the pages produced before the detonation; the machine is about to be removed either way.
        if (mPendingOutput > 0) {
            addEnergyOutput((long) (mPendingOutput * getEuPerOutput()));
            mPendingOutput = 0.0F;
        }
        mReactorCycleTicks = CYCLE_TICKS;
        mReactorStable = false;
        boomPower *= hem * boomMod;

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
            // IC2 clamps its own reactors to protection/reactorExplosionPowerLimit (45); the MTReactor uses the
            // MessTech config option Config.REACTOR_IC2_EXPLOSION_POWER_LIMIT for that number and maps the result onto
            // the Draconic Evolution power scale used by MTExplosionDE, whose maximum is twice DE's own full reactor
            // (2 x 20 = 40) - see MTExplosionDE and Config.
            float ic2Limit = Math.max(1.0F, Config.REACTOR_IC2_EXPLOSION_POWER_LIMIT);
            float dePower = Math.min(boomPower, ic2Limit) / ic2Limit * Config.REACTOR_EXPLOSION_DE_POWER_LIMIT;
            MTProcessHandler.addProcess(new MTExplosionDE(world, centerX, centerY, centerZ, dePower));
        }
    }
    // endregion

    // region Fluid (wet) mode

    /**
     * IC2's fluid loop for one page: the heat the components emitted this tick is absorbed by coolant, and whatever the
     * coolant did not take goes back onto the core as {@code overflow / 2}.
     * <p>
     * Every step is paired so the loop can neither duplicate nor delete fluid: the coolant is drained first, then the
     * same amount of hot coolant is filled, and if the output hatch cannot take it the drained coolant is put back.
     *
     * @return the heat the core has to take back, 0 when the coolant absorbed everything
     */
    private int releaseCoolantHeat() {
        int pending = mHeat.getPendingEmitHeat();
        // The flow buffer belongs to a single page tick, so it is cleared here whatever the outcome - but only after
        // the exchange below has been resolved, never before it.
        if (pending <= 0) {
            mHeat.resetCycleEmitHeat();
            return 0;
        }
        if (!mHeat.isFluidMode()) {
            // Dry mode: the buffered heat goes back to the core, like an IC2 reactor without a fluid loop.
            mHeat.resetCycleEmitHeat();
            return pending / 2;
        }
        // GT's own accessor for the IC2 fluid, so this stays in step with however the pack exposes it. The hatch's own
        // accessor is used for the cold coolant because it is null-guarded and matches the hatch's filter exactly;
        // GTModHandler.getIC2Coolant would fall back to water when IC2 is absent, and this loop must never drain water
        // as if it were coolant.
        Fluid coolant = MTReactorCoolantInputHatch.getCoolantFluid();
        FluidStack hotStack = GTModHandler.getHotCoolant(1);
        Fluid hotCoolant = hotStack == null ? null : hotStack.getFluid();
        if (coolant == null || hotCoolant == null) {
            mHeat.resetCycleEmitHeat();
            return pending / 2;
        }
        long needed = MTReactorHeatSystem.coolantForHeat(pending);
        if (needed > Integer.MAX_VALUE) needed = Integer.MAX_VALUE;

        long available = 0;
        for (MTReactorCoolantInputHatch hatch : mReactorCoolantInputs) {
            if (hatch == null) continue;
            available += hatch.getCoolantAmount(coolant);
        }
        long free = 0;
        for (MTReactorCoolantOutputProxy hatch : mReactorCoolantOutputs) {
            free += hatch.getFreeSpace();
        }
        long taken = Math.min(needed, Math.min(available, free));
        if (taken <= 0) {
            // No coolant to take or nowhere for the hot coolant to go: nothing is drained at all.
            mHeat.resetCycleEmitHeat();
            return pending / 2;
        }

        long drained = 0;
        for (MTReactorCoolantInputHatch hatch : mReactorCoolantInputs) {
            if (hatch == null || drained >= taken) continue;
            drained += hatch.drainCoolant(coolant, (int) Math.min(taken - drained, Integer.MAX_VALUE));
        }
        if (drained <= 0) {
            mHeat.resetCycleEmitHeat();
            return pending / 2;
        }
        long filled = 0;
        for (MTReactorCoolantOutputProxy hatch : mReactorCoolantOutputs) {
            if (filled >= drained) break;
            filled += hatch.fill(hotCoolant, (int) Math.min(drained - filled, Integer.MAX_VALUE));
        }
        if (filled < drained) {
            // The hot coolant had nowhere to go: put the drained coolant back so nothing is destroyed. The refund is
            // best effort - an already full input hatch cannot take everything back, and that leftover is the coolant
            // that paid for the heat actually exchanged.
            refundCoolant(coolant, drained - filled);
        }
        if (filled <= 0) {
            mHeat.resetCycleEmitHeat();
            return pending / 2;
        }
        // mB of coolant exchanged: with COOLANT_PER_BUFFER_UNIT == 1 that is also the buffered-heat unit count. If that
        // constant ever moves off 1, this call has to pass filled / COOLANT_PER_BUFFER_UNIT instead.
        MTReactorHeatSystem.CoolantExchange exchange = mHeat.coolByCoolant(filled);
        mHeat.resetCycleEmitHeat();
        return exchange.overflow;
    }

    private void refundCoolant(Fluid coolant, long amount) {
        long left = amount;
        for (MTReactorCoolantInputHatch hatch : mReactorCoolantInputs) {
            if (hatch == null || left <= 0) continue;
            left -= hatch.refundCoolant(coolant, (int) Math.min(left, Integer.MAX_VALUE));
        }
    }

    /** Fluid (wet) mode state, shown in the GUI. */
    public boolean isFluidMode() {
        return mHeat.isFluidMode();
    }

    /** Toggles the fluid mode; called by the controller's screwdriver handling and by the GUI. */
    public void setFluidMode(boolean value) {
        if (mHeat.isFluidMode() == value) return;
        mHeat.setFluidMode(value);
        // Switching the cooling model invalidates a running countdown: give the player the full grace period back.
        mHeat.resetCountdown();
        markDirty();
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) checkStructure(true, base);
    }

    /**
     * Screwdriver on the controller toggles the fluid (wet) mode. As with every manual interaction, it also cancels a
     * running meltdown countdown - the machine is a machine, not a trap that keeps ticking while the player works on
     * it.
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        setFluidMode(!mHeat.isFluidMode());
        GTUtility.sendChatTrans(
            aPlayer,
            mHeat.isFluidMode() ? "machine.mtreactor.fluidmode.on" : "machine.mtreactor.fluidmode.off");
    }

    // endregion

    // region NBT / info

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        mHeat.saveNBTData(aNBT);
        aNBT.setBoolean("mtReactorStable", mReactorStable);
        aNBT.setBoolean("mtReactorAutoMatch", mAutoMatchTarget);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // The machine's tick counter is not persisted and restarts around here, so the store is told which tick the
        // restored countdowns have to be anchored onto before it reads them.
        IGregTechTileEntity base = getBaseMetaTileEntity();
        mHeat.setLoadClock(base == null ? 0L : base.getTimer());
        mHeat.loadNBTData(aNBT);
        // "mtReactorPageHeat" (the old global-page-order int[]) and "mtReactorPageMaxHeat" from older saves are
        // ignored: pages are anchored on hatch coordinates now and the ceiling always comes from the control hatch.
        if (aNBT.hasKey("mtReactorStable")) {
            mReactorStable = aNBT.getBoolean("mtReactorStable");
        }
        // Default on for existing/new machines unless explicitly disabled.
        mAutoMatchTarget = !aNBT.hasKey("mtReactorAutoMatch") || aNBT.getBoolean("mtReactorAutoMatch");
        // The hatch may not be resolved yet at this point; the first onPostTick re-reads the capacity and rescales.
        mReactorInventorySignature = 0;
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
            StatCollector.translateToLocalFormatted("machine.mtreactor.waila.output", mReactorEUPerTick),
            StatCollector.translateToLocalFormatted("machine.mtreactor.waila.output_actual", mReactorActualEUPerTick),
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.fluidmode",
                StatCollector.translateToLocal(
                    mHeat.isFluidMode() ? "machine.mtreactor.waila.fluidmode.on"
                        : "machine.mtreactor.waila.fluidmode.off")),
            StatCollector
                .translateToLocalFormatted("machine.mtreactor.waila.cooling", mHeat.getPassiveCoolingPerSecond()) };
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
        return mHeat.getHottestHeat();
    }

    /**
     * Heat ceiling shown in the GUI/Waila/scanner. It is read live from the heat control hatch instead of being
     * cached, so installing, upgrading or losing the hatch is visible immediately instead of only after the next
     * reactor cycle (an empty or switched-off reactor never runs one).
     */
    public int getHottestPageMaxHeat() {
        return mHeat.getHottestMaxHeat();
    }

    public int getHottestPageHeatPercent() {
        return mHeat.getHottestHeatPercent();
    }

    public boolean isReactorStable() {
        return mReactorStable;
    }

    /** Cycles the prediction says the hottest page can run before the meltdown; -1 when it is stable. */
    public int getCyclesToMeltdown() {
        return mCyclesToMeltdown;
    }

    /** True while the meltdown countdown of a page is running. */
    public boolean isMeltdownCountdownActive() {
        return mHeat.isCountdownActive();
    }

    /** Seconds left before the page detonates, rounded up; 0 while nothing counts down. */
    public int getMeltdownCountdownSeconds() {
        return (mHeat.getCountdownTicks() + 19) / 20;
    }

    public float getMeltdownCountdownProgress() {
        return mHeat.getCountdownProgress();
    }

    /** Rated EU/t of the last completed cycle. */
    public long getRatedEUPerTick() {
        return mReactorEUPerTick;
    }

    /** EU/t the dynamo hatches actually accepted over the last tick. */
    public long getActualEUPerTick() {
        return mReactorActualEUPerTick;
    }

    public long getPassiveCoolingPerSecond() {
        return mHeat.getPassiveCoolingPerSecond();
    }

    /**
     * Component heat the fluid loop has buffered but not carried away yet, in the units one mB of coolant absorbs.
     * Shown by the GUI so a fluid-mode reactor whose coolant ran dry is visible before the heat comes back onto the
     * core.
     */
    public int getPendingCoolantHeat() {
        return mHeat.getPendingEmitHeat();
    }

    /** Heat percentage of every page, for the GUI heat map; ordered hatch by hatch, page by page. */
    public int[] getPageHeatPercents() {
        int max = getHottestPageMaxHeat();
        int[] result = new int[getTotalPageCount()];
        int index = 0;
        for (MTReactorAccessHatch hatch : getSortedReactorHatches()) {
            for (int page = 0; page < hatch.getPageCount() && index < result.length; page++, index++) {
                result[index] = (int) Math.round(mHeat.getPageHeat(hatch, page) * 100.0D / max);
            }
        }
        return result;
    }

    private int getTotalPageCount() {
        int pages = 0;
        for (MTReactorAccessHatch hatch : mSortedHatches) {
            if (hatch != null) pages += hatch.getPageCount();
        }
        return Math.min(pages, MTReactorHeatSystem.MAX_PAGES);
    }
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
        tag.setInteger("mtReactorCyclesToMeltdown", mCyclesToMeltdown);
        tag.setInteger("mtReactorCountdownSeconds", getMeltdownCountdownSeconds());
        tag.setLong("mtReactorOutput", mReactorEUPerTick);
        tag.setLong("mtReactorOutputActual", mReactorActualEUPerTick);
        tag.setBoolean("mtReactorFluidMode", mHeat.isFluidMode());
        tag.setInteger("mtReactorPendingHeat", getPendingCoolantHeat());
        tag.setBoolean("mtReactorOutputShortfall", hasOutputShortfall());
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
        int countdown = tag.getInteger("mtReactorCountdownSeconds");
        if (countdown > 0) {
            currentTip.add(
                EnumChatFormatting.DARK_RED
                    + StatCollector.translateToLocalFormatted("machine.mtreactor.waila.meltdown", countdown)
                    + EnumChatFormatting.RESET);
        } else if (!stable) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocalFormatted(
                    "machine.mtreactor.waila.cycles",
                    tag.getInteger("mtReactorCyclesToMeltdown")) + EnumChatFormatting.RESET);
        }
        currentTip.add(
            StatCollector.translateToLocalFormatted("machine.mtreactor.waila.output", tag.getLong("mtReactorOutput")));
        currentTip.add(
            StatCollector.translateToLocalFormatted(
                "machine.mtreactor.waila.output_actual",
                tag.getLong("mtReactorOutputActual")));
        if (tag.getBoolean("mtReactorOutputShortfall")) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("machine.mtreactor.waila.output_shortfall")
                    + EnumChatFormatting.RESET);
        }
        if (tag.getBoolean("mtReactorFluidMode")) {
            currentTip.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.mtreactor.waila.fluidmode.on")
                    + EnumChatFormatting.RESET);
            // The same line the GUI shows, so a wet reactor whose coolant ran dry is visible from outside the machine.
            currentTip.add(
                StatCollector.translateToLocalFormatted(
                    "machine.mtreactor.gui.pending_heat",
                    tag.getInteger("mtReactorPendingHeat")));
        }

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
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.desc"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.flavour"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.structure.header"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.structure.access"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.structure.pages"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.structure.heat"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.structure.tier"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.core.header"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.core.cycle"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.core.heat"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.core.meltdown"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.core.cooling"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.power.header"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.power.output"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.details"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.details.fluidmode"))
            .addInfo(StatCollector.translateToLocal("machine.mtreactor.tooltip.details.hint"))
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
            // No muffler hatch line: this machine produces no pollution, and the structure never accepted one. The
            // old line promised a hatch the player could not usefully place.
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
        private int maxHeat = MTReactorHeatHatch.DEFAULT_HEAT_CAPACITY;
        private float hem = 1.0F;
        private float pageOutput = 0.0F;
        /** Simulation mode: everything is redirected to the snapshot below, never to the real reactor. */
        private boolean simulating;
        private ItemStack[] simulatedItems;
        private int simulatedHeat;
        private int simulatedBonus;

        void bind(MTReactorAccessHatch hatch, int page) {
            this.hatch = hatch;
            this.page = page;
            this.maxHeat = mHeat.getPageMaxHeat(hatch, page);
            this.hem = 1.0F;
            this.pageOutput = 0.0F;
            this.simulating = false;
            this.simulatedItems = null;
            this.simulatedHeat = 0;
            this.simulatedBonus = 0;
        }

        /**
         * Binds a page snapshot for the prediction. The plating bonus is supplied up front (the prediction reads it
         * straight out of the snapshot) so the ceiling is already correct while the heat pass runs, instead of only
         * after the platings had their say.
         */
        void bindSimulation(ItemStack[] items, int heat, int platingBonus) {
            this.hatch = null;
            this.page = -1;
            this.simulatedItems = items;
            this.simulatedHeat = heat;
            this.simulatedBonus = Math.max(0, platingBonus);
            this.maxHeat = mHeat.getMaxHeatWithBonus(simulatedBonus);
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
            if (simulating) return simulatedHeat;
            if (hatch == null) return 0;
            return mHeat.getPageHeat(hatch, page);
        }

        @Override
        public void setHeat(int heat) {
            if (simulating) {
                simulatedHeat = heat;
            } else if (hatch != null) {
                mHeat.setPageHeat(hatch, page, heat);
            }
        }

        @Override
        public int addHeat(int amount) {
            if (simulating) {
                simulatedHeat += amount;
                return simulatedHeat;
            }
            if (hatch == null) return 0;
            return mHeat.addPageHeat(hatch, page, amount);
        }

        @Override
        public int getMaxHeat() {
            return maxHeat;
        }

        @Override
        public void setMaxHeat(int newMaxHeat) {
            // Reactor plating is the one component that may raise the ceiling (IC2 semantics). The heat control hatch
            // stays the main term and the sum is clamped, so plating can never turn the risk model off. The bonus is
            // per page: a 9x6 page is an independent reactor grid, so plating on one page must not lift another's.
            if (simulating) {
                simulatedBonus = Math.max(simulatedBonus, newMaxHeat - mHeat.getCapacity());
                maxHeat = mHeat.getMaxHeatWithBonus(simulatedBonus);
            } else if (hatch != null) {
                mHeat.setPageMaxHeat(hatch, page, newMaxHeat - mHeat.getCapacity());
                maxHeat = mHeat.getPageMaxHeat(hatch, page);
            }
        }

        @Override
        public void addEmitHeat(int heat) {
            // Component heat that goes into the coolant loop instead of the core.
            mHeat.addEmitHeat(heat);
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
            return pageOutput * getEuPerOutput();
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
            if (!simulating && hatch != null) {
                explodeReactor(hatch, page, hem);
            }
        }

        @Override
        public int getTickRate() {
            return CYCLE_TICKS;
        }

        @Override
        public boolean produceEnergy() {
            // The prediction always assumes the reactor is running, so switching the machine off does not make the
            // stability check meaningless: it shows whether the current layout would explode once enabled.
            if (simulating) return true;
            return mLastCouldRun;
        }

        @Override
        public void setRedstoneSignal(boolean redstone) {
            // The GT controller's power switch is the redstone equivalent.
        }

        @Override
        public boolean isFluidCooled() {
            return mHeat.isFluidMode();
        }
    }
    // endregion

    // region Template support

    /**
     * Thin wrapper over the hot coolant output hatch. The reactor only ever needs its free space and its fill path, so
     * keeping the two together avoids repeating the capacity arithmetic in every place that touches the loop.
     */
    private static final class MTReactorCoolantOutputProxy {

        private final MTReactorHotCoolantOutputHatch hatch;

        MTReactorCoolantOutputProxy(MTReactorHotCoolantOutputHatch hatch, int aBaseCasingIndex) {
            this.hatch = hatch;
            hatch.updateTexture(aBaseCasingIndex);
        }

        long getFreeSpace() {
            return Math.max(0L, (long) hatch.getCapacity() - hatch.getFluidAmount());
        }

        long fill(Fluid fluid, int amount) {
            if (fluid == null || amount <= 0) return 0;
            return hatch.fill(new FluidStack(fluid, amount), true);
        }
    }

    /** One locked slot the next heat pass is predicted to destroy. */
    private static final class DoomedSlot {

        final ChunkCoordinates owner;
        final int page;
        final int localSlot;

        DoomedSlot(ChunkCoordinates owner, int page, int localSlot) {
            this.owner = owner;
            this.page = page;
            this.localSlot = localSlot;
        }
    }

    /** Output the current IC2 cycle has accumulated so far, published by {@link #finishCycle()}. */
    private float mPendingOutput = 0.0F;
    /** Heat effect modifier of the hottest page, used by the aggregated world heat effects. */
    private float mHottestHem = 1.0F;
    /**
     * Result of the multi-cycle heat prediction shown in the GUI/Waila; true when no page is heading for a meltdown.
     */
    private boolean mReactorStable = true;
    // endregion
}
