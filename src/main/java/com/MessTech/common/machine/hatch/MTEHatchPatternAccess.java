package com.MessTech.common.machine.hatch;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.machine.MTSmallMolecularArcaneAssembler;
import com.MessTech.common.recipe.MTArcaneAssemblyRecipes;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.items.misc.ItemEncodedPattern;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEItemStack;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.render.TextureFactory;

/**
 * Pattern access hatch (样板访问仓) of the Small Molecular Arcane Assembler: an AE2 crafting medium that stores encoded
 * patterns for the machine's arcane recipes.
 *
 * <p>
 * The hatch is the machine's AE network node (the controller itself has no grid proxy), so AE cables connect directly
 * to it. It reads the pattern's condensed inputs/outputs, checks them against the machine's recipe pool and registers
 * the pattern with the network; the AE crafting CPU then extracts the ingredients and pushes the pattern into the
 * hatch, which hands the job to the machine. The machine pays the aspects with EU, crafts for the pool's duration and
 * injects the results (plus non-consumed inputs) back into the ME network through this hatch.
 *
 * <p>
 * The casing is Magic Casing; the front overlay is GT5U's pattern provider overlay (the Large Molecular Assembler's
 * pattern provider hatch icon), as requested.
 */
public class MTEHatchPatternAccess extends MTEHatchInputBus implements ICraftingProvider, IGridProxyable {

    /** EV tier: the machine runs at EV (2048 EU/t) and the hatch tier drives its item form and GUI grid. */
    private static final int TIER = 4;
    /**
     * Pattern slots. Must equal {@code (tier + 1)^2} (GT5U's {@code MTEHatch.getSlots}), because the inherited input
     * bus
     * GUI and slot math derive the slot count from the tier; each slot holds one encoded pattern.
     */
    public static final int SLOT_COUNT = (TIER + 1) * (TIER + 1);

    private static final IIconContainer PATTERN_PROVIDER_OVERLAY = Textures.BlockIcons.OVERLAY_HATCH_PATTERN_PROVIDER;

    @Nullable
    private AENetworkProxy gridProxy;
    @Nullable
    private MTSmallMolecularArcaneAssembler controller;
    private boolean patternsDirty = true;

    public MTEHatchPatternAccess(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TIER,
            SLOT_COUNT,
            new String[] { translateToLocal("machine.patternaccesshatch.tooltip.1"),
                translateToLocal("machine.patternaccesshatch.tooltip.2"),
                translateToLocal("machine.patternaccesshatch.tooltip.3"),
                translateToLocal("machine.patternaccesshatch.tooltip.4") });
        this.disableSort = true;
        // The bus recipe-map filter would reject pattern items (the machine's recipe map holds recipes, not patterns).
        this.disableFilter = true;
    }

    public MTEHatchPatternAccess(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, SLOT_COUNT, aDescription, aTextures);
        this.disableSort = true;
        this.disableFilter = true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPatternAccess(this.mName, this.mTier, this.mDescriptionArray, this.mTextures);
    }

    /**
     * The inherited input bus description is generic and, worse, derived from the tier's slot count; a pattern hatch
     * must explain its own function instead.
     */
    @Override
    public String[] getDescription() {
        return new String[] { translateToLocal("machine.patternaccesshatch.tooltip.1"),
            translateToLocal("machine.patternaccesshatch.tooltip.2"),
            translateToLocal("machine.patternaccesshatch.tooltip.3"),
            translateToLocal("machine.patternaccesshatch.tooltip.4"),
            translateToLocal("machine.patternaccesshatch.tooltip.5") };
    }

    /** Only encoded arcane patterns may enter through pipes/hoppers either; the base bus would accept anything. */
    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return isItemValidForSlot(aIndex, aStack);
    }

    /**
     * The base bus' screwdriver toggles the recipe-map filter, which would lock patterns out; keep the filter fixed.
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, net.minecraft.entity.player.EntityPlayer aPlayer, float aX,
        float aY, float aZ, ItemStack aTool) {}

    // region inventory

    @Override
    public int getCircuitSlot() {
        return -1;
    }

    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        if (aStack == null) return true;
        if (!(aStack.getItem() instanceof ItemEncodedPattern)) return false;

        final IGregTechTileEntity base = getBaseMetaTileEntity();
        // The client cannot decode an AE pattern reliably; only the item type is checked there, the server re-validates
        // the pattern against the machine's recipe pool.
        if (base == null || base.getWorld() == null || base.getWorld().isRemote) return true;
        return isValidArcanePattern(aStack, base.getWorld());
    }

    @Nullable
    private World getSafeWorld() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null ? null : base.getWorld();
    }

    /**
     * @return whether the stack is an encoded AE2 pattern whose inputs and outputs describe one recipe of the machine's
     *         arcane pool. Patterns that do not describe a recipe are rejected on insertion.
     */
    public static boolean isValidArcanePattern(@Nullable ItemStack stack, @Nullable World world) {
        if (world == null) return false;
        final ICraftingPatternDetails details = getPatternDetails(stack, world);
        return details != null
            && MTArcaneAssemblyRecipes.findRecipeForPattern(condensedInputs(details), condensedOutputs(details))
                != null;
    }

    @Nullable
    public static ICraftingPatternDetails getPatternDetails(@Nullable ItemStack stack, @Nullable World world) {
        if (stack == null || world == null || !(stack.getItem() instanceof ItemEncodedPattern encoded)) return null;
        // Both AE crafting (3x3) patterns and processing patterns are accepted: the arcane pool is a GT recipe map, so
        // pattern terminals encode it as a processing pattern (its recipe is not a vanilla IRecipe).
        return encoded.getPatternForItem(stack, world);
    }

    public static List<ItemStack> condensedInputs(ICraftingPatternDetails details) {
        return copyStacks(details.getCondensedInputs());
    }

    public static List<ItemStack> condensedOutputs(ICraftingPatternDetails details) {
        return copyStacks(details.getCondensedOutputs());
    }

    private static List<ItemStack> copyStacks(IAEItemStack[] stacks) {
        final List<ItemStack> result = new ArrayList<>(stacks == null ? 0 : stacks.length);
        if (stacks == null) return result;
        for (IAEItemStack stack : stacks) {
            if (stack == null) continue;
            final ItemStack itemStack = stack.getItemStack();
            if (itemStack != null) result.add(itemStack.copy());
        }
        return result;
    }
    // endregion

    // region machine binding

    public void bindController(@Nullable MTSmallMolecularArcaneAssembler machine) {
        if (this.controller == machine) return;
        this.controller = machine;
        markPatternsDirty();
    }

    @Nullable
    public MTSmallMolecularArcaneAssembler getController() {
        return controller;
    }

    /** @return whether patterns can currently be offered to the network: bound to a formed machine with a live grid. */
    private boolean isCraftingAvailable() {
        return controller != null && getProxy().isActive();
    }
    // endregion

    // region AE2 crafting provider

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        if (!isCraftingAvailable()) return;
        final World world = getSafeWorld();
        if (world == null) return;

        for (int slot = 0; slot < getSizeInventory(); slot++) {
            final ItemStack stack = getStackInSlot(slot);
            final ICraftingPatternDetails details = getPatternDetails(stack, world);
            if (details != null
                && MTArcaneAssemblyRecipes.findRecipeForPattern(condensedInputs(details), condensedOutputs(details))
                    != null) {
                craftingTracker.addCraftingOption(this, details);
            }
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        final MTSmallMolecularArcaneAssembler machine = controller;
        if (machine == null) return false;

        final List<ItemStack> provided = new ArrayList<>(table.getSizeInventory());
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            final ItemStack stack = table.getStackInSlot(slot);
            if (stack != null) provided.add(stack.copy());
        }
        return machine.pushArcanePattern(this, condensedOutputs(patternDetails), provided);
    }

    @Override
    public boolean isBusy() {
        final MTSmallMolecularArcaneAssembler machine = controller;
        return machine == null || machine.isArcanePatternBusy() || !getProxy().isActive();
    }

    /**
     * Injects the finished stacks into this hatch's ME network.
     *
     * @return the stacks the network did not accept (empty when everything went in).
     */
    public List<ItemStack> injectIntoNetwork(List<ItemStack> stacks) {
        final List<ItemStack> remaining = new ArrayList<>(stacks.size());
        if (stacks.isEmpty()) return remaining;

        final AENetworkProxy proxy = getProxy();
        if (!proxy.isActive()) {
            remaining.addAll(stacks);
            return remaining;
        }
        try {
            final IMEMonitor<IAEItemStack> inventory = proxy.getStorage()
                .getItemInventory();
            final MachineSource source = new MachineSource((IActionHost) getBaseMetaTileEntity());
            for (ItemStack stack : stacks) {
                if (stack == null) continue;
                final IAEItemStack rest = inventory.injectItems(AEItemStack.create(stack), Actionable.MODULATE, source);
                if (rest == null || rest.getStackSize() <= 0) continue;
                final ItemStack left = rest.getItemStack();
                if (left != null) remaining.add(left);
            }
        } catch (GridAccessException e) {
            remaining.clear();
            remaining.addAll(stacks);
        }
        return remaining;
    }
    // endregion

    // region AE2 grid proxy

    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            gridProxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            updateValidGridProxySides();
        }
        return gridProxy;
    }

    private void updateValidGridProxySides() {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || gridProxy == null) return;
        gridProxy.setValidSides(EnumSet.of(base.getFrontFacing()));
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
        updateValidGridProxySides();
    }

    @Override
    public void onFacingChange() {
        updateValidGridProxySides();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection forgeDirection) {
        return isOutputFacing(forgeDirection) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public DimensionalCoord getLocation() {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        return new DimensionalCoord(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public void gridChanged() {}

    @Override
    public void securityBreak() {}

    @MENetworkEventSubscribe
    public void powerChangeME(MENetworkPowerStatusChange change) {
        markPatternsDirty();
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(isActive());
    }

    @MENetworkEventSubscribe
    public void bootChangeME(MENetworkBootingStatusChange change) {
        markPatternsDirty();
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(isActive());
    }

    @MENetworkEventSubscribe
    public void channelChangeME(MENetworkChannelsChanged change) {
        markPatternsDirty();
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(isActive());
    }

    public boolean isActive() {
        return getProxy().isActive();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        getProxy().writeToNBT(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("proxy")) getProxy().readFromNBT(aNBT);
        markPatternsDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTimer) {
        if (aBaseMetaTileEntity.isServerSide() && aBaseMetaTileEntity.hasInventoryBeenModified()) {
            markPatternsDirty();
        }
        super.onPostTick(aBaseMetaTileEntity, aTimer);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        aBaseMetaTileEntity.setActive(isActive());
        // Retry the pattern change until the grid accepts it: a network that was offline/unpowered at the first attempt
        // would otherwise never learn about the patterns.
        if (patternsDirty && aTimer % 20 == 0 && postPatternChange()) {
            patternsDirty = false;
        }
    }

    private void markPatternsDirty() {
        patternsDirty = true;
    }

    /** @return whether the pattern change event was posted to a live grid. */
    private boolean postPatternChange() {
        final AENetworkProxy proxy = getProxy();
        if (!proxy.isReady()) return false;
        try {
            if (proxy.getGrid() == null) return false;
            proxy.getGrid()
                .postEvent(new MENetworkCraftingPatternChange(this, proxy.getNode()));
            return true;
        } catch (GridAccessException ignored) {
            return false;
        }
    }
    // endregion

    // region textures

    @Override
    public ITexture getCasingTexture() {
        return TextureFactory.of(Casings.MagicCasing.getBlock(), Casings.MagicCasing.getBlockMeta());
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(PATTERN_PROVIDER_OVERLAY) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(PATTERN_PROVIDER_OVERLAY) };
    }
    // endregion
}
