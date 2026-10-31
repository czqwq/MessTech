package com.MessTech.common.machine.hatch;

import java.util.Arrays;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.MessTech.common.machine.MTReactorHeatSystem;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.items.ItemDepletedCell;
import ic2.api.reactor.IReactorComponent;
import ic2.core.Ic2Items;
import ic2.core.item.ItemGradualInt;
import ic2.core.item.reactor.ItemReactorHeatStorage;
import ic2.core.item.reactor.ItemReactorUranium;

/**
 * Tiered reactor access hatch: {@code MTReactor}'s IC2 component inventory in independent 9x6 pages (one IC2 reactor
 * grid
 * each), 1 page at EV up to 8 at UIV.
 * <p>
 * Base texture = own-tier casing + decal {@code assets/messtech/textures/blocks/hatch/Hatch_Reactor_Access.png} on the
 * front face, so {@code updateTexture} is never called (casing updates must not overwrite the tier base).
 */
public class MTReactorAccessHatch extends MTEHatch implements IDataCopyable, MTReactorHeatSystem.PageOwner {

    /** Data stick "type" carrying this hatch's copyable configuration. One identifier for every tier. */
    public static final String DATA_STICK_DATA_TYPE = "MTReactorAccessHatch";
    private static final Logger LOG = LogManager.getLogger("MessTech");

    /** Payload layout version: a stick written by an older layout is refused instead of half applied. */
    public static final int DATA_STICK_VERSION = 2;

    public static final int PAGE_WIDTH = 9;
    public static final int PAGE_HEIGHT = 6;
    public static final int SLOTS_PER_PAGE = PAGE_WIDTH * PAGE_HEIGHT;

    private static final String OVERLAY_DOMAIN = "messtech";
    /** Icon path relative to {@code textures/blocks}, matching {@code Textures.BlockIcons.custom}. */
    private static final String OVERLAY_PATH = "hatch/Hatch_Reactor_Access";

    /** Highest tier supported by this hatch (UIV). */
    public static final int MAX_TIER = 11;
    /** Lowest tier supported by this hatch (EV). */
    public static final int MIN_TIER = 4;

    private static IIconContainer sReactorOverlay;

    private final int pageCount;

    /** Per-slot lock state: -1 = unlocked, 0..100 = locked auto-output threshold in percent. */
    private final byte[] mSlotState;
    /** Remembered item type of every locked slot (matching ignores NBT on purpose). */
    private final ItemStack[] mSlotMemory;
    /** Cached {@link #mtReactorKey()}; see there for why it is shared. */
    private ChunkCoordinates mReactorKey;

    public MTReactorAccessHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            getPageCountForTier(aTier) * SLOTS_PER_PAGE,
            new String[] { StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.0") });
        this.pageCount = getPageCountForTier(aTier);
        this.mSlotState = new byte[mInventory.length];
        Arrays.fill(this.mSlotState, (byte) -1);
        this.mSlotMemory = new ItemStack[mInventory.length];
    }

    public MTReactorAccessHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, getPageCountForTier(aTier) * SLOTS_PER_PAGE, aDescription, aTextures);
        this.pageCount = getPageCountForTier(aTier);
        this.mSlotState = new byte[mInventory.length];
        Arrays.fill(this.mSlotState, (byte) -1);
        this.mSlotMemory = new ItemStack[mInventory.length];
    }

    /** Pages per tier: EV = 1, IV = 2, ... UIV = 8. */
    public static int getPageCountForTier(int aTier) {
        return Math.max(1, Math.min(aTier - MIN_TIER + 1, MAX_TIER - MIN_TIER + 1));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTReactorAccessHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTReactorAccessHatchGui(this).build(data, syncManager, uiSettings);
    }

    /** MTEHatch defaults to MUI1; this hatch needs the custom MUI2 page GUI. */
    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();
        if (ItemList.Tool_DataStick.isStackEqual(heldItem, false, true)) {
            // Only the server applies (and answers for) the configuration; the client has nothing to do here.
            if (!(aPlayer instanceof EntityPlayerMP)) return true;
            if (!pasteCopiedData(aPlayer, heldItem.stackTagCompound)) {
                GTUtility.sendChatTrans(aPlayer, "machine.mtreactor.accesshatch.copy.invalid");
                return true;
            }
            GTUtility.sendChatTrans(aPlayer, "machine.mtreactor.accesshatch.copy.loaded");
            return true;
        }
        openGui(aPlayer);
        return true;
    }

    /**
     * Left click with a data stick saves the lock configuration, the GT way ({@code MTEHatchOutputBus#onLeftclick}); no
     * block damage, as {@code BlockMachines} extends GT's {@code MaterialMachines} ({@code setRequiresTool()}) with a
     * wrench harvest tool.
     */
    @Override
    public void onLeftclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!(aPlayer instanceof EntityPlayerMP)) return;
        ItemStack dataStick = aPlayer.getHeldItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return;
        dataStick.stackTagCompound = getCopiedData(aPlayer);
        dataStick.setStackDisplayName(StatCollector.translateToLocal("machine.mtreactor.accesshatch.name") + " Config");
        GTUtility.sendChatTrans(aPlayer, "machine.mtreactor.accesshatch.copy.saved");
    }

    /**
     * Horizontal facings only: the base tile entity defaults to {@link ForgeDirection#DOWN} and {@code setFrontFacing}
     * refuses an invalid facing (the decal used to point down); this also keeps the decal off top/bottom, in view from
     * the side.
     */
    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing.offsetY == 0;
    }

    /**
     * Placement can still pick no horizontal facing (looking straight down, auto-placement offering a vertical side),
     * leaving {@code DOWN}: force a horizontal fallback on the first tick.
     */
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()
            && aBaseMetaTileEntity.getFrontFacing().offsetY != 0) {
            aBaseMetaTileEntity.setFrontFacing(ForgeDirection.NORTH);
        }
    }

    @Override
    public String[] getDescription() {
        return new String[] { EnumChatFormatting.AQUA + StatCollector
            .translateToLocalFormatted("machine.mtreactor.accesshatch.desc.0", pageCount, pageCount * SLOTS_PER_PAGE),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.2"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.3"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.4"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.5"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.6") };
    }

    // region Inventory layout

    public int getPageCount() {
        return pageCount;
    }

    // region Heat system page ownership (MTReactorHeatSystem.PageOwner)

    /**
     * Stable identity for the reactor heat store: block coordinates, not the hatch's index in a coordinate-sorted hatch
     * list, so adding, removing or moving another access hatch cannot renumber this hatch's pages and hand its heat
     * elsewhere. The hatch stores no heat ({@code MTReactor} owns it via {@code MTReactorHeatSystem}); before placement
     * this is {@code null} = "not a page owner".
     * <p>
     * The object is cached and shared: {@code MTReactorHeatSystem} looks a page up through it on every access, and
     * those
     * accesses sit inside the reactor's component loops (a MOX rod asks for the core heat once per pulse). Callers only
     * read it - it is a map key, so treat it as immutable. Re-validated against the tile entity's coordinates.
     */
    @Override
    public ChunkCoordinates mtReactorKey() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return null;
        int x = base.getXCoord();
        int y = base.getYCoord();
        int z = base.getZCoord();
        ChunkCoordinates cached = mReactorKey;
        if (cached != null && cached.posX == x && cached.posY == y && cached.posZ == z) {
            return cached;
        }
        mReactorKey = new ChunkCoordinates(x, y, z);
        return mReactorKey;
    }

    /** 9x6 pages, one IC2 reactor grid each. */
    @Override
    public int mtReactorPageCount() {
        return pageCount;
    }

    // endregion

    /** Reads a slot of a 9x6 page; coordinates are clamped to that one page, so pages never see each other. */
    public ItemStack getReactorItem(int page, int x, int y) {
        if (!isInPage(page, x, y)) return null;
        return mInventory[toIndex(page, x, y)];
    }

    public void setReactorItem(int page, int x, int y, ItemStack stack) {
        if (!isInPage(page, x, y)) return;
        int index = toIndex(page, x, y);
        mInventory[index] = stack;
        onContentsChanged(index);
    }

    public boolean isPageEmpty(int page) {
        if (page < 0 || page >= pageCount) return true;
        int start = page * SLOTS_PER_PAGE;
        for (int i = start; i < start + SLOTS_PER_PAGE; i++) {
            if (mInventory[i] != null) return false;
        }
        return true;
    }

    public void clearAllPages() {
        Arrays.fill(mInventory, null);
        markDirty();
    }

    private boolean isInPage(int page, int x, int y) {
        return page >= 0 && page < pageCount && x >= 0 && x < PAGE_WIDTH && y >= 0 && y < PAGE_HEIGHT;
    }

    private static int toIndex(int page, int x, int y) {
        return page * SLOTS_PER_PAGE + y * PAGE_WIDTH + x;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** Also cap the legacy IInventory path used by GT automation, not just the MUI2 item handler. */
    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    /**
     * Every reactor slot is a real inventory slot: {@code MTEBasicTank}'s slot 2 fluid display slot would hide the 3rd.
     */
    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex >= 0 && aIndex < mInventory.length;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        if (!isUsefulReactorItem(itemStack)) return false;
        // A locked slot only accepts items matching its remembered type (durability/heat ignored).
        if (isSlotLocked(index)) {
            ItemStack memory = getSlotMemory(index);
            if (memory != null && !isSameItemType(itemStack, memory)) return false;
        }
        return true;
    }

    /**
     * IC2 style: per-slot validated insertion, extraction from every slot, so logistics mods (GT pipes, AE,
     * OpenComputers) reach single reactor slots.
     */
    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return isValidSlot(aIndex) && isItemValidForSlot(aIndex, aStack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return isValidSlot(aIndex);
    }

    /**
     * Direct IInventory writes (e.g. OpenComputers' {@code inventory.setItem}) must not bypass slot validation and
     * locks; internal writes use {@link #setSlotStack(int, ItemStack)}.
     */
    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aStack != null && !isItemValidForSlot(aIndex, aStack)) return;
        super.setInventorySlotContents(aIndex, aStack);
    }

    /** Readable name for logistics/OpenComputers instead of the raw localization key. */
    @Override
    public String getInventoryName() {
        return getLocalName();
    }

    /**
     * Mirrors IC2's {@code isUsefulItem}: reactor components plus the inert items IC2 also keeps (depleted fuel cells,
     * tritium cells).
     */
    public static boolean isUsefulReactorItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (stack.getItem() instanceof IReactorComponent) return true;
        Item item = stack.getItem();
        return matchesIc2Item(item, Ic2Items.TritiumCell) || matchesIc2Item(item, Ic2Items.reactorDepletedUraniumSimple)
            || matchesIc2Item(item, Ic2Items.reactorDepletedUraniumDual)
            || matchesIc2Item(item, Ic2Items.reactorDepletedUraniumQuad)
            || matchesIc2Item(item, Ic2Items.reactorDepletedMOXSimple)
            || matchesIc2Item(item, Ic2Items.reactorDepletedMOXDual)
            || matchesIc2Item(item, Ic2Items.reactorDepletedMOXQuad);
    }

    private static boolean matchesIc2Item(Item item, ItemStack template) {
        return template != null && template.getItem() == item;
    }

    // endregion

    // region Slot lock / memory

    public int getSlotCount() {
        return mInventory.length;
    }

    public ItemStack getSlotStack(int index) {
        if (index < 0 || index >= mInventory.length) return null;
        return mInventory[index];
    }

    public void setSlotStack(int index, ItemStack stack) {
        if (index < 0 || index >= mInventory.length) return;
        mInventory[index] = stack;
        onContentsChanged(index);
    }

    public boolean isSlotLocked(int index) {
        return index >= 0 && index < mSlotState.length && mSlotState[index] >= 0;
    }

    /** Locked auto-output percentage, or -1 when the slot is not locked. */
    public int getSlotThreshold(int index) {
        if (!isSlotLocked(index)) return -1;
        return mSlotState[index];
    }

    public ItemStack getSlotMemory(int index) {
        if (index < 0 || index >= mSlotMemory.length) return null;
        return mSlotMemory[index];
    }

    /** The raw array used by the MUI2 sync handler (which copies it). */
    public ItemStack[] getSlotMemoryArray() {
        return mSlotMemory;
    }

    /** The raw array used by the MUI2 sync handler (which copies it). */
    public byte[] getSlotStateArray() {
        return mSlotState;
    }

    /**
     * Locks the clicked slot and every slot of this hatch with the same item type (all pages, durability/heat and NBT
     * ignored); an existing locked threshold for that type is kept, not reset to the item default.
     */
    public void lockSlotGroup(int index) {
        if (index < 0 || index >= mInventory.length) return;
        ItemStack reference = mInventory[index];
        if (reference == null) return;
        byte threshold = (byte) getDefaultOutputPercent(reference);
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] >= 0 && isSameItemType(mSlotMemory[i], reference)) {
                threshold = mSlotState[i];
                break;
            }
        }
        for (int i = 0; i < mInventory.length; i++) {
            if (isSameItemType(mInventory[i], reference)) {
                mSlotState[i] = threshold;
                mSlotMemory[i] = mInventory[i].copy();
            }
        }
        markDirty();
    }

    /** Unlocks only the clicked slot, so a single misclick cannot remove a whole item group. */
    public void unlockSlot(int index) {
        if (index < 0 || index >= mSlotState.length) return;
        mSlotState[index] = -1;
        mSlotMemory[index] = null;
        markDirty();
    }

    /**
     * Global unlock (Alt + Shift + right click): removes the lock from every locked slot holding the clicked slot's
     * item
     * type, all pages included.
     */
    public void unlockSlotGroup(int index) {
        if (index < 0 || index >= mInventory.length) return;
        ItemStack reference = mSlotMemory[index] != null ? mSlotMemory[index] : mInventory[index];
        if (reference == null) {
            unlockSlot(index);
            return;
        }
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] >= 0 && isSameItemType(mSlotMemory[i], reference)) {
                mSlotState[i] = -1;
                mSlotMemory[i] = null;
            }
        }
        markDirty();
    }

    /**
     * The auto-output percentage belongs to the item type inside one hatch: setting it on any locked slot updates every
     * locked slot with that type (all pages).
     */
    public void setSlotThreshold(int index, int percent) {
        if (!isSlotLocked(index)) return;
        byte clamped = (byte) Math.max(0, Math.min(100, percent));
        ItemStack reference = mSlotMemory[index];
        if (reference == null) {
            mSlotState[index] = clamped;
            markDirty();
            return;
        }
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] >= 0 && isSameItemType(mSlotMemory[i], reference)) {
                mSlotState[i] = clamped;
            }
        }
        markDirty();
    }

    /** True when a locked slot does not hold its remembered item type. */
    public boolean hasMissingLockedComponents() {
        for (int i = 0; i < mInventory.length; i++) {
            if (mSlotState[i] < 0) continue;
            if (!isSameItemType(mInventory[i], mSlotMemory[i])) return true;
        }
        return false;
    }

    /**
     * Item type comparison for slot memory: what a locked slot accepts and what the group operations
     * ({@link #lockSlotGroup}, {@link #unlockSlotGroup}, {@link #setSlotThreshold},
     * {@link #hasMissingLockedComponents}) treat as one type.
     * <p>
     * Same {@link Item} plus: damageable ({@code getMaxDamage() > 0}) -> damage is the wear bar, ignored (half-burnt
     * rod
     * == fresh rod); else damage is a metadata variant and must match.
     * <p>
     * Damage is ignored when damageable because the state lives in NBT ({@code advDmg} for GT's
     * {@code ItemRadioactiveCell}/{@code ItemRadioactiveCellIC} and IC2's {@code ItemGradualInt}; {@code heat} for
     * {@code ItemCoolantCell}/{@code ItemCoolantCellIC}) and only paints vanilla's 0..99 bar.
     * <p>
     * Item identity works because each grade is its own Item: IC2 {@code ic2.core.init.BlocksItems}
     * ({@code ItemReactorUranium} per uranium/MOX size, {@code ItemReactorHeatStorage} per cell size), GT
     * {@code LoaderGTBlockFluid} ({@code ItemRadioactiveCellIC}/{@code ItemCoolantCellIC} per grade), MessTech
     * {@code MTFuelRod}.
     * <p>
     * Not {@code ItemStack#getUnlocalizedName()}: stack-aware ({@code ItemStack} 432 calls
     * {@code item.getUnlocalizedName(stack)}; {@code GTGenericItem} appends {@code "." + getDamage(stack)} for
     * subtypes),
     * so it embeds the fuel state, and {@code Item#getUnlocalizedName()} adds nothing. Not
     * {@code ItemStack#isItemEqual}
     * ({@code ItemStack} 427): compares damage as well, i.e. wear-sensitive.
     */
    public static boolean isSameItemType(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getMaxDamage() > 0 || b.getMaxDamage() > 0) return true;
        return a.getItemDamage() == b.getItemDamage();
    }

    /**
     * True for rods that still burn fuel: IC2/GT burnable rods (including MessTech's transcendent ones) but not
     * depleted
     * cells; the slot sub-panel shows the fuel durability line only for these.
     */
    public static boolean isFuelRod(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (stack.getItem() instanceof ItemReactorUranium) return true;
        // GT's ItemRadioactiveCellIC stores its real state in NBT (advDmg) and paints the vanilla 0..99 bar.
        return stack.getItem() instanceof ItemRadioactiveCell && !(stack.getItem() instanceof ItemDepletedCell);
    }

    public static boolean isCoolingCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemReactorHeatStorage;
    }

    /** Remaining durability in percent (rounded) for display; IC2 fuel rods and coolant cells use custom damage. */
    public static int getDurabilityPercent(ItemStack stack) {
        return (int) Math.round(getDurabilityFraction(stack));
    }

    /**
     * Exact remaining durability in percent (fractional) for the auto-output/input threshold: a rod with a tiny bit
     * left
     * must not round down to 0% and output early.
     */
    public static double getDurabilityFraction(ItemStack stack) {
        if (stack == null) return 0.0D;
        if (stack.getItem() instanceof ItemGradualInt gradual) {
            int max = gradual.getMaxCustomDamage(stack);
            if (max <= 0) return 100.0D;
            int remaining = max - gradual.getCustomDamage(stack);
            if (remaining <= 0) return 0.0D;
            return remaining * 100.0D / max;
        }
        if (stack.getMaxDamage() > 0) {
            int remaining = stack.getMaxDamage() - stack.getItemDamage();
            if (remaining <= 0) return 0.0D;
            return remaining * 100.0D / stack.getMaxDamage();
        }
        return 100.0D;
    }

    /** Current IC2 custom damage (heat for coolant cells, used damage for fuel rods). */
    public static int getCurrentHeat(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemGradualInt gradual)) return 0;
        return gradual.getCustomDamage(stack);
    }

    public static int getMaxHeat(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemGradualInt gradual)) return 0;
        return gradual.getMaxCustomDamage(stack);
    }

    /** Fuel rods default to 0%, coolant cells to 5%, everything else to 0%. */
    public static int getDefaultOutputPercent(ItemStack stack) {
        return isCoolingCell(stack) ? 5 : 0;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setByteArray("mtReactorSlotState", mSlotState);
        NBTTagList memoryList = new NBTTagList();
        for (int i = 0; i < mSlotMemory.length; i++) {
            if (mSlotMemory[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("slot", i);
            NBTTagCompound itemTag = new NBTTagCompound();
            mSlotMemory[i].writeToNBT(itemTag);
            entry.setTag("item", itemTag);
            memoryList.appendTag(entry);
        }
        aNBT.setTag("mtReactorSlotMemory", memoryList);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        Arrays.fill(mSlotState, (byte) -1);
        byte[] state = aNBT.getByteArray("mtReactorSlotState");
        if (state != null) {
            System.arraycopy(state, 0, mSlotState, 0, Math.min(state.length, mSlotState.length));
        }
        Arrays.fill(mSlotMemory, null);
        NBTTagList memoryList = aNBT.getTagList("mtReactorSlotMemory", 10);
        for (int i = 0; i < memoryList.tagCount(); i++) {
            NBTTagCompound entry = memoryList.getCompoundTagAt(i);
            int slot = entry.getInteger("slot");
            if (slot < 0 || slot >= mSlotMemory.length) continue;
            ItemStack item = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            if (item != null) {
                mSlotMemory[slot] = item;
            }
        }
    }

    // endregion

    // region Data stick copy / paste (IDataCopyable)

    @Override
    public String getCopiedDataIdentifier(EntityPlayer player) {
        return DATA_STICK_DATA_TYPE;
    }

    /**
     * Data stick payload: page count plus, per slot, locked component type and auto-output threshold. Components are
     * absent on purpose - a paste only applies settings to what the target holds.
     */
    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", DATA_STICK_DATA_TYPE);
        tag.setInteger("ver", DATA_STICK_VERSION);
        tag.setInteger("page", pageCount);

        // Sparse archive of the locked component types: locked slots only.
        NBTTagList lockList = new NBTTagList();
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] < 0 || mSlotMemory[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("slot", i);
            entry.setInteger("threshold", mSlotState[i]);
            entry.setTag("item", mSlotMemory[i].writeToNBT(new NBTTagCompound()));
            lockList.appendTag(entry);
        }
        tag.setTag("locks", lockList);
        return tag;
    }

    /**
     * Replays a copied lock configuration: percentage and locked type come from the stick. The inventory is only
     * <b>read</b> (to find the slot a lock's component already sits in), never written.
     * <p>
     * Pages map one to one; with more source pages than here, only those here are replayed and the chat reports the
     * dropped ones. An unlocked slot stays unlocked, an empty locked slot is reported (the lock acts once the component
     * arrives), and the chat reports locks replayed and pages covered.
     * <p>
     * <b>The component type decides the slot, not the slot number</b> (same component, other layout): first a slot
     * already
     * holding its type, then its original number when free, last another free slot of the same page; a lock with no
     * room is
     * dropped and reported, never written over another lock.
     */
    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound nbt) {
        if (nbt == null || !DATA_STICK_DATA_TYPE.equals(nbt.getString("type"))) return false;

        int sourcePages = nbt.getInteger("page");
        if (nbt.getInteger("ver") != DATA_STICK_VERSION || sourcePages < 1) return false;
        int sourceSlots = sourcePages * SLOTS_PER_PAGE;

        ItemStack[] lockedType = new ItemStack[sourceSlots];
        int[] lockedThreshold = new int[sourceSlots];
        NBTTagList lockList = nbt.getTagList("locks", 10);
        int entriesRead = 0;
        for (int i = 0; i < lockList.tagCount(); i++) {
            NBTTagCompound entry = lockList.getCompoundTagAt(i);
            int slot = entry.getInteger("slot");
            if (slot < 0 || slot >= sourceSlots) continue;
            ItemStack type = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            if (type == null) continue;
            lockedType[slot] = type;
            lockedThreshold[slot] = entry.getInteger("threshold");
            entriesRead++;
        }

        int appliedPages = Math.min(pageCount, sourcePages);
        int appliedSlots = appliedPages * SLOTS_PER_PAGE;
        // Locks the stick carries for the pages this hatch has, counted before the remap; the difference to what landed
        // is the number that found no room (not countable afterwards, a lock may land on another slot).
        int entriesValid = 0;
        for (int i = 0; i < appliedSlots; i++) {
            if (lockedType[i] != null) entriesValid++;
        }
        int[] remapped = remapLocks(lockedType, appliedSlots);

        int applied = 0;
        int emptyLocked = 0;

        // Lock settings come straight from the stick; only the slot they land on is decided by the remap above. The
        // inventory is never written: which component sits in a slot (if any) is the player's business.
        for (int i = 0; i < mSlotState.length; i++) {
            int source = i < remapped.length ? remapped[i] : -1;
            if (source < 0) {
                mSlotState[i] = -1;
                mSlotMemory[i] = null;
                continue;
            }
            mSlotState[i] = (byte) Math.max(0, Math.min(100, lockedThreshold[source]));
            mSlotMemory[i] = lockedType[source].copy();
            applied++;
            if (mInventory[i] == null) emptyLocked++;
        }

        markDirty();
        if (sourcePages > appliedPages) {
            player.addChatComponentMessage(
                new ChatComponentTranslation(
                    "machine.mtreactor.accesshatch.copy.pages_dropped",
                    sourcePages - appliedPages,
                    appliedPages));
        }
        if (applied == 0) {
            LOG.warn(
                "[MTReactorAccessHatch] stick carried {} lock entr(ies), {} readable, {} usable page(s)",
                lockList.tagCount(),
                entriesRead,
                appliedPages);
            player.addChatComponentMessage(new ChatComponentTranslation("machine.mtreactor.accesshatch.copy.no_locks"));
            return true;
        }
        int dropped = entriesValid - applied;
        if (dropped > 0) {
            player.addChatComponentMessage(
                new ChatComponentTranslation("machine.mtreactor.accesshatch.copy.remap_dropped", dropped));
        }
        if (emptyLocked > 0) {
            player.addChatComponentMessage(
                new ChatComponentTranslation("machine.mtreactor.accesshatch.copy.empty_slots", emptyLocked));
        }
        // How many locks were actually applied (else a partial paste looks like a full one): locks written, pages
        // covered.
        player.addChatComponentMessage(
            new ChatComponentTranslation("machine.mtreactor.accesshatch.copy.remapped", applied, appliedPages));
        return true;
    }

    /**
     * Which of this hatch's slots each lock lands on: component type first, original slot number as fallback.
     * <p>
     * Two booleans for two questions: {@code targetTaken} = "a lock already occupies this <b>slot</b>" (by slot),
     * {@code placed} = "this <b>lock</b> already has a home" (by source slot) - using the source index on the slot
     * array
     * would place a lock twice.
     *
     * @param lockedType   component type per source slot; null marks an unlocked one
     * @param appliedSlots how many of this hatch's slots the stick's pages cover
     * @return indexed by <b>target</b> slot: the <b>source</b> slot its lock comes from, or -1 for "no lock here"
     */
    private int[] remapLocks(ItemStack[] lockedType, int appliedSlots) {
        int[] remapped = new int[appliedSlots];
        Arrays.fill(remapped, -1);
        // A lock already occupies some target slot.
        boolean[] targetTaken = new boolean[appliedSlots];
        // A lock already has a home, so it must not be placed again.
        boolean[] placed = new boolean[appliedSlots];

        // Pass 1: a slot already holding the component the stick locked is where that lock belongs, whatever its number
        // on the source hatch - this makes a paste follow the parts instead of the grid.
        for (int source = 0; source < appliedSlots; source++) {
            ItemStack wanted = lockedType[source];
            if (wanted == null) continue;
            for (int target = 0; target < appliedSlots; target++) {
                if (targetTaken[target]) continue;
                if (!isSameItemType(mInventory[target], wanted)) continue;
                remapped[target] = source;
                targetTaken[target] = true;
                placed[source] = true;
                break;
            }
        }

        // Pass 2: no slot held the part, so keep the original slot number when it is free. An occupied slot must not be
        // overwritten with a different lock: the occupant was put there by the player.
        for (int source = 0; source < appliedSlots; source++) {
            if (lockedType[source] == null || placed[source]) continue;
            if (targetTaken[source]) continue;
            if (mInventory[source] != null && isSlotLocked(source)) continue;
            remapped[source] = source;
            targetTaken[source] = true;
            placed[source] = true;
        }

        // Pass 3: spill onto a free slot of the same page, so the lock still exists instead of being lost.
        for (int source = 0; source < appliedSlots; source++) {
            if (lockedType[source] == null || placed[source]) continue;
            int pageStart = (source / SLOTS_PER_PAGE) * SLOTS_PER_PAGE;
            int pageEnd = Math.min(pageStart + SLOTS_PER_PAGE, appliedSlots);
            for (int target = pageStart; target < pageEnd; target++) {
                if (targetTaken[target]) continue;
                if (mInventory[target] != null && isSlotLocked(target)) continue;
                remapped[target] = source;
                targetTaken[target] = true;
                placed[source] = true;
                break;
            }
        }
        return remapped;
    }

    // endregion

    // region Textures

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        sReactorOverlay = Textures.BlockIcons.custom(OVERLAY_DOMAIN, OVERLAY_PATH);
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return withOverlay(aBaseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return withOverlay(aBaseTexture);
    }

    private ITexture[] withOverlay(ITexture aBaseTexture) {
        if (sReactorOverlay == null) {
            return new ITexture[] { aBaseTexture };
        }
        return new ITexture[] { aBaseTexture, TextureFactory.of(sReactorOverlay) };
    }

    // endregion
}
