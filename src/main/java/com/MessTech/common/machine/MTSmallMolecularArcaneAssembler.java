package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofTileAdder;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.MTSmallMolecularArcaneAssemblerGui;
import com.MessTech.common.machine.Base.MTMultiMachineBase;
import com.MessTech.common.machine.Base.MTProcessingLogic;
import com.MessTech.common.machine.hatch.MTEHatchPatternAccess;
import com.MessTech.common.recipe.MTArcaneAssemblyRecipes;
import com.MessTech.common.recipe.MTArcaneAssemblyRecipes.ArcaneDefinition;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.items.wands.ItemWandCasting;
import thaumcraft.common.tiles.TileOwned;

/**
 * Small Molecular Arcane Assembler (小型分子奥术装配室): the Thaumic Energistics arcane assembler rebuilt as a GT
 * multiblock. Its recipe pool ({@link MTArcaneAssemblyRecipes}) is a plain GT recipe map used for the NEI pages only:
 * actual crafting is driven by AE2 encoded patterns put into the pattern access hatch, exactly like a molecular
 * assembler. The hatch is the AE network node, the crafting CPU pushes a pattern (with its inputs already extracted)
 * into it, the machine validates the pattern against the pool and crafts the arcane recipe.
 *
 * <p>
 * Aspects are paid with EU: each primal aspect point is bought for {@link MTArcaneAssemblyRecipes#EU_PER_ASPECT} EU and
 * buffered in the machine. The buffer only exists while a wand or sceptre ({@link ItemWandCasting}, but not a staff)
 * sits in the controller slot: its per-aspect vis capacity is the buffer's capacity. Aspects are topped up one point
 * per aspect per tick, clamped by the EU the energy hatches actually hold. Recipes whose research the machine owner has
 * not unlocked are refused. There is no extra power failure mechanic - a pattern job simply pauses while the machine
 * has no power, like any other GT multiblock.
 */
public class MTSmallMolecularArcaneAssembler extends MTMultiMachineBase<MTSmallMolecularArcaneAssembler>
    implements ISurvivalConstructable, RecipeMapWorkable {

    // region structure
    private static final String STRUCTURE_PIECE_MAIN = "main";
    /** The controller: char 2 of X, layer 2 of Y, row 0 of Z. */
    private static final int HORIZONTAL_OFFSET = 2;
    private static final int VERTICAL_OFFSET = 2;
    private static final int DEPTH_OFFSET = 0;
    /** GoodGenerator's Magic Casing texture (page 12, index 0), shared with the Small Essentia Smeltery. */
    private static final int CASING_INDEX = 12 << 7;
    /** The frame holds the hatches; every one of its 44 blocks may be replaced by one. */
    private static final int FRAME_BLOCKS = 44;
    /** Warded glass blocks: the 54 'B' positions minus the one the controller occupies. */
    private static final int WARDED_GLASS_BLOCKS = 53;

    // spotless:off
    /**
     * 5x5x5 hollow cube: 'A' is the Magic Casing frame (every block may be replaced by a hatch), 'B' is the warded
     * glass shell and the controller '~' sits in the middle of the front face.
     */
    private static final String[][] STRUCTURE = new String[][]{
        {"AAAAA", "ABBBA", "ABBBA", "ABBBA", "AAAAA"},
        {"ABBBA", "B   B", "B   B", "B   B", "ABBBA"},
        {"AB~BA", "B   B", "B   B", "B   B", "ABBBA"},
        {"ABBBA", "B   B", "B   B", "B   B", "ABBBA"},
        {"AAAAA", "ABBBA", "ABBBA", "ABBBA", "AAAAA"},
    };
    // spotless:on
    // endregion

    // region pattern checks
    private static final CheckRecipeResult NO_STAFF = SimpleCheckRecipeResult.ofFailure("arcaneroom_no_staff");
    private static final CheckRecipeResult RESEARCH_LOCKED = SimpleCheckRecipeResult
        .ofFailure("arcaneroom_research_locked");
    private static final CheckRecipeResult NOT_ENOUGH_ASPECTS = SimpleCheckRecipeResult
        .ofFailure("arcaneroom_not_enough_aspects");
    private static final CheckRecipeResult CAPACITY_TOO_LOW = SimpleCheckRecipeResult
        .ofFailure("arcaneroom_capacity_too_low");
    private static final CheckRecipeResult UNKNOWN_PATTERN = SimpleCheckRecipeResult
        .ofFailure("arcaneroom_unknown_pattern");
    // endregion

    // region pattern job NBT keys
    private static final String NBT_PATTERN_RECIPE = "patternRecipe";
    private static final String NBT_PATTERN_INPUTS = "patternInputs";
    private static final String NBT_PATTERN_OUTPUTS = "patternOutputs";
    private static final String NBT_PENDING_OUTPUTS = "pendingPatternOutputs";
    // endregion

    /** Buffered primal aspects, in Thaumcraft's centivis (100 CV = 1 vis). */
    private final AspectList mStoredAspects = new AspectList();

    /** The six primal aspects, cached: {@code Aspect#getPrimalAspects()} allocates a new list on every call. */
    private static final List<Aspect> PRIMALS = Aspect.getPrimalAspects();

    /** The pattern access hatches bound by the last successful structure check. */
    private final List<MTEHatchPatternAccess> mPatternHatches = new ArrayList<>(1);

    /** Recipe of the pattern job currently being crafted, or null while idle. */
    @Nullable
    private ArcaneDefinition mPatternRecipe;
    /**
     * Stacks the AE CPU handed over for the current job (kept until it finishes, or returned if the structure breaks).
     */
    private final List<ItemStack> mPatternInputs = new ArrayList<>();
    /** What the finished job has to hand back: the recipe results plus its non-consumed inputs. */
    private final List<ItemStack> mPatternOutputs = new ArrayList<>();
    /** Finished stacks waiting for the AE network to accept them; retried every tick. */
    private final List<ItemStack> mPendingAEOutputs = new ArrayList<>();
    /** Last rejection reason of a pushed pattern, shown in the GUI while the machine idles. */
    @Nullable
    private CheckRecipeResult mPatternFailure;

    private IStructureDefinition<MTSmallMolecularArcaneAssembler> structureDefinition;

    @SideOnly(Side.CLIENT)
    private static IIconContainer frontInactive;
    @SideOnly(Side.CLIENT)
    private static IIconContainer frontActive;

    public MTSmallMolecularArcaneAssembler(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTSmallMolecularArcaneAssembler(String aName) {
        super(aName);
    }

    // region structure

    @Override
    public IStructureDefinition<MTSmallMolecularArcaneAssembler> getStructureDefinition() {
        if (structureDefinition == null) {
            structureDefinition = StructureDefinition.<MTSmallMolecularArcaneAssembler>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(STRUCTURE))
                .addElement('A', buildAElement())
                .addElement('B', buildBElement())
                .build();
        }
        return structureDefinition;
    }

    /** 'A': the Magic Casing frame, any block may be replaced by a hatch the machine accepts. */
    @SuppressWarnings("unchecked")
    private IStructureElement<MTSmallMolecularArcaneAssembler> buildAElement() {
        List<IStructureElement<MTSmallMolecularArcaneAssembler>> elements = new ArrayList<>(2);
        elements.add(
            buildHatchAdder(MTSmallMolecularArcaneAssembler.class)
                .atLeast(HatchElement.Energy.or(HatchElement.ExoticEnergy), HatchElement.InputBus)
                .casingIndex(CASING_INDEX)
                .hint(1)
                .build());
        elements.add(Casings.MagicCasing.asElement());
        return ofChain(elements.toArray(new IStructureElement[0]));
    }

    /**
     * 'B': Thaumcraft's warded glass. The machine claims every unowned warded glass in its shell for its placer, the
     * same way Thaumcraft's own warded structures and TST do, so other players cannot take the shell apart.
     */
    private IStructureElement<MTSmallMolecularArcaneAssembler> buildBElement() {
        return ofChain(
            ofTileAdder(MTSmallMolecularArcaneAssembler::addOwnedWardedGlass, ConfigBlocks.blockCosmeticOpaque, 2),
            ofBlock(ConfigBlocks.blockCosmeticOpaque, 2));
    }

    /** Warded glass adder: claims an unowned warded glass for the machine's owner and accepts it. */
    private boolean addOwnedWardedGlass(TileEntity tileEntity) {
        // ofTileAdder accepts any tile entity, so the block itself has to be checked: arcane doors and warded wooden
        // devices are TileOwned too and must not form the shell.
        if (tileEntity == null || tileEntity.getBlockType() != ConfigBlocks.blockCosmeticOpaque) return false;
        if (tileEntity.getBlockMetadata() != 2) return false;
        if (!(tileEntity instanceof TileOwned owned)) return false;

        if (owned.owner == null || owned.owner.isEmpty()) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            String owner = base == null ? null : base.getOwnerName();
            if (owner != null && !owner.isEmpty() && !"Player".equals(owner)) {
                owned.owner = owner;
                tileEntity.markDirty();
            }
            // GT's "Player" fallback means the machine was not placed by a real player (dev/automation placement). No
            // owner can be claimed then; the warded glass stays ownerless and therefore unbreakable, exactly like a
            // warded structure no player warded. Normal player placement always has a real owner.
        }
        return true;
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;

        checkHasAnyEnergy(errors);
        checkHasPatternAccessHatch(errors);
        if (errors.isEmpty()) bindPatternHatches();
    }

    /** The machine has no other input path: without a pattern access hatch it cannot do anything. */
    private void checkHasPatternAccessHatch(List<StructureError> errors) {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof MTEHatchPatternAccess) return;
        }
        errors.add(StructureErrors.of("machine.smallmoleculararcaneassembler.error.need_pattern_hatch"));
    }

    /** Binds every pattern access hatch of the fresh structure to this controller. */
    private void bindPatternHatches() {
        mPatternHatches.clear();
        for (MTEHatchInputBus bus : mInputBusses) {
            if (!(bus instanceof MTEHatchPatternAccess hatch)) continue;
            hatch.bindController(this);
            mPatternHatches.add(hatch);
        }
    }

    @Override
    public void clearHatches() {
        for (MTEHatchPatternAccess hatch : mPatternHatches) hatch.bindController(null);
        mPatternHatches.clear();
        super.clearHatches();
    }
    // endregion

    // region staff slot

    /**
     * @return The controller slot item if it can power the buffer: a wand or sceptre. The arcane workbench accepts
     *         exactly the same items, refusing staffs (手杖) and staff sceptres (手权杖), so the same predicate is used
     *         here.
     */
    @Nullable
    public ItemStack getStaffStack() {
        ItemStack stack = getControllerSlot();
        return stack != null && isUsableStaff(stack) ? stack : null;
    }

    public static boolean isUsableStaff(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemWandCasting wand)) return false;
        return !wand.isStaff(stack);
    }

    /** @return Per-aspect buffer capacity in centivis, 0 while no usable wand or sceptre is inserted. */
    public int getStaffCapacity() {
        ItemStack staff = getStaffStack();
        if (staff == null) return 0;
        return ((ItemWandCasting) staff.getItem()).getMaxVis(staff);
    }

    /**
     * Only a wand or sceptre may be put into the controller slot; it is the machine's buffer key, not a recipe input.
     */
    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        if (aIndex == getControllerSlotIndex()) return aStack == null || isUsableStaff(aStack);
        return super.isItemValidForSlot(aIndex, aStack);
    }

    @Override
    public int getStackSizeLimit(int aSlot, ItemStack aStack) {
        if (aSlot == getControllerSlotIndex()) return 1;
        return super.getStackSizeLimit(aSlot, aStack);
    }
    // endregion

    // region aspect buffer

    /**
     * Clamps the buffer to the capacity of the staff currently inserted: swapping a large sceptre for a small wand
     * must not keep amounts the smaller staff could never hold.
     */
    private void clampStoredAspects() {
        int capacity = getStaffCapacity();
        if (capacity <= 0) return;

        boolean changed = false;
        for (Aspect aspect : PRIMALS) {
            if (mStoredAspects.getAmount(aspect) <= capacity) continue;
            mStoredAspects.remove(aspect);
            mStoredAspects.add(aspect, capacity);
            changed = true;
        }
        if (changed) markDirty();
    }

    /**
     * Tops the buffer up from the energy hatches: one point (1 vis) of each missing primal aspect per tick, each point
     * costing {@link MTArcaneAssemblyRecipes#EU_PER_ASPECT} EU. The running pattern job's EU/t is kept in reserve, so
     * refilling cannot starve it of power.
     */
    private void refillStoredAspects(long aTick) {
        int capacity = getStaffCapacity();
        if (capacity <= 0) return;

        long reserve = mMaxProgresstime > 0 ? Math.max(1L, getActualEnergyUsage()) : 0L;
        long availableEu = getStoredHatchEnergy();
        boolean changed = false;

        for (Aspect aspect : PRIMALS) {
            if (availableEu - reserve < MTArcaneAssemblyRecipes.EU_PER_ASPECT) break;

            int stored = mStoredAspects.getAmount(aspect);
            if (capacity - stored < MTArcaneAssemblyRecipes.CENTIVIS_PER_VIS) continue;

            if (!drainEnergyExactly(MTArcaneAssemblyRecipes.EU_PER_ASPECT)) break;

            mStoredAspects.add(aspect, MTArcaneAssemblyRecipes.CENTIVIS_PER_VIS);
            availableEu -= MTArcaneAssemblyRecipes.EU_PER_ASPECT;
            changed = true;
        }

        if (changed) markDirty();
    }

    /** @return EU currently buffered in the machine's energy hatches. */
    private long getStoredHatchEnergy() {
        long stored = 0L;
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) {
            IGregTechTileEntity hatchTile = hatch.getBaseMetaTileEntity();
            if (hatchTile != null) stored = GTUtility.addSafe(stored, hatchTile.getStoredEU());
        }
        return stored;
    }

    /**
     * Drains exactly {@code amount} EU, spread over the energy hatches, or nothing at all. {@code drainEnergyInput}
     * would take whatever it finds in the first hatches even when the total is short, which would burn EU for no
     * aspect.
     */
    private boolean drainEnergyExactly(long amount) {
        if (getStoredHatchEnergy() < amount) return false;

        long remaining = amount;
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) {
            IGregTechTileEntity hatchTile = hatch.getBaseMetaTileEntity();
            if (hatchTile == null) continue;

            long take = Math.min(hatchTile.getStoredEU(), remaining);
            if (take <= 0) continue;
            hatchTile.decreaseStoredEnergyUnits(take, false);
            remaining -= take;
            if (remaining <= 0) return true;
        }
        return false;
    }

    private boolean hasStoredAspects(AspectList cost) {
        for (Aspect aspect : cost.getAspects()) {
            if (aspect == null) continue;
            if (mStoredAspects.getAmount(aspect) < cost.getAmount(aspect) * MTArcaneAssemblyRecipes.CENTIVIS_PER_VIS) {
                return false;
            }
        }
        return true;
    }

    /** @return true when {@code cost} fits into the buffer capacity of the inserted staff at all. */
    private boolean fitsStaffCapacity(AspectList cost) {
        int capacity = getStaffCapacity();
        for (Aspect aspect : cost.getAspects()) {
            if (aspect == null) continue;
            if (cost.getAmount(aspect) * MTArcaneAssemblyRecipes.CENTIVIS_PER_VIS > capacity) return false;
        }
        return true;
    }

    private void consumeStoredAspects(AspectList cost) {
        for (Aspect aspect : cost.getAspects()) {
            if (aspect == null) continue;
            mStoredAspects.remove(aspect, cost.getAmount(aspect) * MTArcaneAssemblyRecipes.CENTIVIS_PER_VIS);
        }
        markDirty();
    }

    /** @return Visitor's completed research check, keyed by the machine owner (its placer). */
    private boolean isOwnerResearchComplete(List<String> researchKeys) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return false;

        String owner = base.getOwnerName();
        // GT falls back to "Player" when a machine was placed without a real owner; there is nobody to gate recipes
        // for in that case (creative building, automation placement).
        if (owner == null || owner.isEmpty() || "Player".equals(owner)) return true;

        for (String key : researchKeys) {
            if (key == null || key.isEmpty()) continue;
            if (!ThaumcraftApiHelper.isResearchComplete(owner, key)) return false;
        }
        return true;
    }

    /**
     * @return GUI sync payload: 1/0 for a usable staff, the buffer capacity in centivis, then the six primal amounts.
     */
    public String getGuiStateText() {
        StringBuilder text = new StringBuilder(64);
        ItemStack staff = getStaffStack();
        text.append(staff == null ? 0 : 1)
            .append(';')
            .append(staff == null ? 0 : getStaffCapacity());
        for (Aspect aspect : PRIMALS) {
            text.append(';')
                .append(mStoredAspects.getAmount(aspect));
        }
        return text.toString();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        NBTTagCompound stored = new NBTTagCompound();
        mStoredAspects.writeToNBT(stored);
        aNBT.setTag("storedAspects", stored);

        aNBT.setInteger(
            NBT_PATTERN_RECIPE,
            mPatternRecipe == null ? -1 : MTArcaneAssemblyRecipes.indexOf(mPatternRecipe));
        writeStacks(aNBT, NBT_PATTERN_INPUTS, mPatternInputs);
        writeStacks(aNBT, NBT_PATTERN_OUTPUTS, mPatternOutputs);
        writeStacks(aNBT, NBT_PENDING_OUTPUTS, mPendingAEOutputs);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mStoredAspects.aspects.clear();
        if (aNBT.hasKey("storedAspects")) {
            mStoredAspects.readFromNBT(aNBT.getCompoundTag("storedAspects"));
        }

        mPatternRecipe = MTArcaneAssemblyRecipes.definitionAt(aNBT.getInteger(NBT_PATTERN_RECIPE));
        mPatternInputs.clear();
        readStacks(aNBT, NBT_PATTERN_INPUTS, mPatternInputs);
        mPatternOutputs.clear();
        readStacks(aNBT, NBT_PATTERN_OUTPUTS, mPatternOutputs);
        mPendingAEOutputs.clear();
        readStacks(aNBT, NBT_PENDING_OUTPUTS, mPendingAEOutputs);
        if (mPatternRecipe == null && !mPatternInputs.isEmpty()) {
            // The recipe pool changed between sessions: hand the CPU's items back instead of voiding them.
            mPendingAEOutputs.addAll(mPatternInputs);
            mPatternInputs.clear();
            mPatternOutputs.clear();
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        // A job whose progress the base machine stopped (power loss, structure check, world load before the first
        // structure validation) is restarted once the structure is valid again; its data is persisted, never voided.
        if (mPatternRecipe != null && mMachine && mMaxProgresstime <= 0) {
            resumePatternJob();
        }
        injectPendingOutputs();

        if (!mMachine || !aBaseMetaTileEntity.isAllowedToWork()) return;
        clampStoredAspects();
        refillStoredAspects(aTick);
    }
    // endregion

    // region pattern crafting

    @Override
    public RecipeMap<?> getRecipeMap() {
        return MTArcaneAssemblyRecipes.arcaneAssemblyRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 1.0F;
    }

    /**
     * Crafting is pattern-driven only: the recipe pool backs the NEI pages and the pattern validation, it is never
     * matched automatically. Rejections of pushed patterns are shown as the machine status while it idles.
     */
    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new MTProcessingLogic() {

            @NotNull
            @Override
            public CheckRecipeResult process() {
                return mPatternFailure != null ? mPatternFailure : CheckRecipeResultRegistry.NO_RECIPE;
            }
        };
    }

    /** @return true while a pattern job is running or its results are still on the way into the AE network. */
    public boolean isArcanePatternBusy() {
        return mPatternRecipe != null || !mPendingAEOutputs.isEmpty();
    }

    /**
     * Accepts one crafting job from a pattern access hatch: the AE crafting CPU already extracted the pattern's inputs
     * from the network and hands them over. The pattern is validated against the recipe pool; aspects are paid when the
     * job starts.
     *
     * @param hatch          the hatch the job came from (must be one of this machine's pattern hatches)
     * @param patternOutputs the pattern's outputs, as described by the AE pattern
     * @param provided       the stacks the AE CPU handed over (the pattern's inputs)
     * @return whether the job was accepted; a rejected job is retried by the AE CPU later.
     */
    public boolean pushArcanePattern(MTEHatchPatternAccess hatch, List<ItemStack> patternOutputs,
        List<ItemStack> provided) {
        if (!mMachine || !mPatternHatches.contains(hatch)) return false;
        if (isArcanePatternBusy()) return false;

        ArcaneDefinition recipe = MTArcaneAssemblyRecipes.findRecipeForPattern(provided, patternOutputs);
        if (recipe == null) {
            mPatternFailure = UNKNOWN_PATTERN;
            return false;
        }
        if (getStaffStack() == null) {
            mPatternFailure = NO_STAFF;
            return false;
        }
        if (!isOwnerResearchComplete(recipe.getResearch())) {
            mPatternFailure = RESEARCH_LOCKED;
            return false;
        }
        AspectList cost = recipe.getAspects();
        if (cost != null) {
            if (!fitsStaffCapacity(cost)) {
                mPatternFailure = CAPACITY_TOO_LOW;
                return false;
            }
            if (!hasStoredAspects(cost)) {
                mPatternFailure = NOT_ENOUGH_ASPECTS;
                return false;
            }
        }

        if (cost != null) consumeStoredAspects(cost);
        mPatternFailure = null;

        mPatternRecipe = recipe;
        mPatternInputs.clear();
        addCopies(mPatternInputs, provided);
        mPatternOutputs.clear();
        addCopies(mPatternOutputs, recipe.getOutputs());
        final List<ItemStack> leftovers = MTArcaneAssemblyRecipes.unconsumedInputs(recipe, provided);
        if (inputBusesContainAll(leftovers)) {
            // The input buses already hold the machine's own copies of the non-consumed inputs (catalysts): keep those
            // and hand the AE-provided copies straight back to the network instead of carrying them through the job.
            mPendingAEOutputs.addAll(leftovers);
        } else {
            // No local copy to prefer: the AE copy is the catalyst for this job and is returned when it finishes.
            mPatternOutputs.addAll(leftovers);
        }

        // Fixed recipe duration/EU/t, no overclocking: the AE CPU pushes one pattern at a time. Both EU/t fields are
        // set: MTEExtendedPowerMultiBlockBase drains lEUt, the int mEUt is what the GUI/NBT and the base class read.
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mProgresstime = 0;
        mMaxProgresstime = MTArcaneAssemblyRecipes.CRAFT_TICKS;
        lEUt = -MTArcaneAssemblyRecipes.CRAFT_EU_T;
        mEUt = (int) -MTArcaneAssemblyRecipes.CRAFT_EU_T;
        markDirty();
        return true;
    }

    /** A pattern job whose progress the base machine stopped (power loss) is restarted as long as it can be powered. */
    private void resumePatternJob() {
        if (getStoredHatchEnergy() < MTArcaneAssemblyRecipes.CRAFT_EU_T) return;
        // A power-loss stop resets mEfficiency to 0, which would make getActualEnergyUsage() pay 10x EU/t.
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mProgresstime = 0;
        mMaxProgresstime = MTArcaneAssemblyRecipes.CRAFT_TICKS;
        lEUt = -MTArcaneAssemblyRecipes.CRAFT_EU_T;
        mEUt = (int) -MTArcaneAssemblyRecipes.CRAFT_EU_T;
        markDirty();
    }

    /** Called by the base machine when the progress bar finishes: hand the results to the AE network. */
    @Override
    protected void outputAfterRecipe() {
        super.outputAfterRecipe();
        if (mPatternRecipe == null) return;

        mPendingAEOutputs.addAll(mPatternOutputs);
        mPatternOutputs.clear();
        mPatternInputs.clear();
        mPatternRecipe = null;
        mProgresstime = 0;
        lEUt = 0;
        mEUt = 0;
        markDirty();
        injectPendingOutputs();
    }

    /**
     * @return whether every pending stack reached the AE network. Stacks the network does not accept stay queued for
     *         the next tick; if the structure breaks they are kept in the controller NBT until it is repaired.
     */
    private boolean injectPendingOutputs() {
        if (mPendingAEOutputs.isEmpty()) return true;

        List<ItemStack> remaining = new ArrayList<>(mPendingAEOutputs);
        for (MTEHatchPatternAccess hatch : mPatternHatches) {
            remaining = hatch.injectIntoNetwork(remaining);
            if (remaining.isEmpty()) break;
        }
        mPendingAEOutputs.clear();
        mPendingAEOutputs.addAll(remaining);
        markDirty();
        return remaining.isEmpty();
    }

    /**
     * @return whether the machine's own input buses hold every stack of {@code stacks} (the non-consumed pattern
     *         inputs). Pattern hatches are skipped: their contents are AE patterns, not recipe items.
     */
    private boolean inputBusesContainAll(List<ItemStack> stacks) {
        if (stacks.isEmpty()) return true;

        final List<ItemStack> available = new ArrayList<>();
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof MTEHatchPatternAccess) continue;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                final ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null) available.add(GTUtility.copy(stack));
            }
        }

        for (ItemStack wanted : stacks) {
            if (wanted == null) continue;
            boolean found = false;
            for (int index = 0; index < available.size(); index++) {
                final ItemStack candidate = available.get(index);
                if (candidate == null || !GTUtility.areStacksEqual(candidate, wanted)) continue;
                if (candidate.stackSize < wanted.stackSize) continue;
                final int left = candidate.stackSize - wanted.stackSize;
                if (left <= 0) {
                    available.remove(index);
                } else {
                    candidate.stackSize = left;
                }
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    private static void addCopies(List<ItemStack> target, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            final ItemStack copy = GTUtility.copy(stack);
            if (copy != null) target.add(copy);
        }
    }

    private static void writeStacks(NBTTagCompound aNBT, String key, List<ItemStack> stacks) {
        final NBTTagList list = new NBTTagList();
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            list.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        aNBT.setTag(key, list);
    }

    private static void readStacks(NBTTagCompound aNBT, String key, List<ItemStack> target) {
        if (!aNBT.hasKey(key)) return;
        final NBTTagList list = aNBT.getTagList(key, 10);
        for (int index = 0; index < list.tagCount(); index++) {
            final ItemStack stack = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(index));
            if (stack != null) target.add(stack);
        }
    }
    // endregion

    // region rendering

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        frontInactive = Textures.BlockIcons.custom("messtech", "iconsets/controllerFaces/industrialMultiMachine");
        frontActive = Textures.BlockIcons.custom("messtech", "iconsets/controllerFaces/industrialMultiMachineActive");
        super.registerIcons(register);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture casing = TextureFactory.of(Casings.MagicCasing.getBlock(), Casings.MagicCasing.getBlockMeta());
        if (side == facing) {
            return new ITexture[] { casing, TextureFactory.of(aActive ? frontActive : frontInactive) };
        }
        return new ITexture[] { casing };
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTSmallMolecularArcaneAssembler(mName);
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTSmallMolecularArcaneAssemblerGui(this);
    }
    // endregion

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(translateToLocal("machine.smallmoleculararcaneassembler.machinetype"))
            .addInfo(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.desc"))
            .addInfo(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.flavour"))
            .addInfo(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.refill"))
            .addInfo(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.staff"))
            .addInfo(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.research"))
            .addInfo(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.pattern"))
            .beginStructureBlock(5, 5, 5, true)
            .addController(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.controller"))
            .addCasing(
                "0-" + (FRAME_BLOCKS - 2),
                translateToLocal("machine.smallmoleculararcaneassembler.tooltip.casing_name"),
                false)
            .addCasing(
                Integer.toString(WARDED_GLASS_BLOCKS),
                translateToLocal("machine.smallmoleculararcaneassembler.tooltip.warded_glass_name"),
                false)
            .addOtherStructurePart(
                translateToLocal("machine.smallmoleculararcaneassembler.tooltip.pattern_hatch_name"),
                translateToLocal("machine.smallmoleculararcaneassembler.tooltip.hatch_info"),
                1)
            .addInputBus(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.hatch_info"), 1)
            .addEnergyHatch(translateToLocal("machine.smallmoleculararcaneassembler.tooltip.hatch_info"), 1)
            .toolTipFinisher();
        return tt;
    }
}
