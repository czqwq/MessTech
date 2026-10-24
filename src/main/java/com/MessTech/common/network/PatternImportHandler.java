package com.MessTech.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.network.base.ServerboundPacket;
import com.MessTech.common.parts.PartUltimatePatternTerminal;
import com.MessTech.init.MessTech;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;

/**
 * Client to server half of the NEI import into the Ultimate Pattern Terminal: NEI runs on the client and the grid lives
 * in
 * the part, so the overlay's cells travel as one compound - {@code in} and {@code out} map cell index to ItemStack.
 * Cells go
 * through {@link #writeCell}/{@link #readCell}, never plain {@code ItemStack#writeToNBT}/{@code loadItemStackFromNBT}:
 * see
 * {@link #COUNT_KEY}.
 */
public class PatternImportHandler extends ServerboundPacket {

    /**
     * NBT key holding a cell's real item count.
     * <p>
     * 1.7.10 writes {@code ItemStack}'s count as tag <em>byte</em> {@code Count} and reads it back as a byte, so any
     * count
     * outside -128..127 comes back different (1024 and 4096 as 0, 1025 and 4097 as 1). The Nano-Scale Foundry "24" pool
     * needs exactly those: its flattened Planck chain asks for 1024 Optical Fiber Cables and 4096 ASoC RAMs <em>in a
     * single
     * input cell</em>, and a cell collapsed to 0 is a stack AE shows without a count and encodes as {@code Cnt: 0},
     * losing
     * the material - so each cell carries its real count under this long tag, the same fix NotEnoughEnergistics
     * applies.
     */
    public static final String COUNT_KEY = "MTCount";

    private NBTTagCompound cells = new NBTTagCompound();

    public PatternImportHandler() {

    }

    public PatternImportHandler(NBTTagCompound cells) {
        this.cells = cells;
    }

    /**
     * Writes one grid cell: the stack as vanilla NBT plus its real count under {@link #COUNT_KEY}. GT's NEI fluid
     * inputs are
     * {@code ItemList.Display_Fluid} display stacks carrying the amount in their NBT, so their count stays the stack's
     * own 1.
     */
    public static NBTTagCompound writeCell(ItemStack stack) {
        final NBTTagCompound cell = stack.writeToNBT(new NBTTagCompound());
        cell.setLong(COUNT_KEY, stack.stackSize);
        return cell;
    }

    /**
     * Reads one grid cell back. A cell without {@link #COUNT_KEY} (a payload from another build) keeps whatever vanilla
     * read,
     * and a count of zero or below (the vanilla byte mangled it) settles on one - a zero-count cell cannot be encoded.
     */
    public static ItemStack readCell(NBTTagCompound cell) {
        final ItemStack stack = ItemStack.loadItemStackFromNBT(cell);
        if (stack == null) return null;
        final long count = cell.hasKey(COUNT_KEY) ? cell.getLong(COUNT_KEY) : stack.stackSize;
        stack.stackSize = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, count));
        return stack;
    }

    @Override
    protected void read(ByteBuf buf) {
        cells = ByteBufUtils.readTag(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, cells);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        final NBTTagCompound payload = cells;
        ServerThreadUtil.addScheduledTask(() -> {
            // The terminal is opened with this mod's ContainerUltimatePatternTerminal; the part check keeps the same
            // write path away from AE's own pattern terminals, whose container this is a subclass of.
            if (player.openContainer instanceof ContainerPatternTerm c
                && c.getPatternTerminal() instanceof PartUltimatePatternTerminal) {
                writeIntoGrid(c, payload);
            }
        });
    }

    /**
     * Fills the terminal's grid from the compound: two maps of cell index to stack, {@code in} for the inputs and
     * {@code out} for the expected outputs. GT5U's {@code ItemList.Display_Fluid} stacks become real fluids here.
     */
    private static void writeIntoGrid(ContainerPatternTerm container, NBTTagCompound cells) {
        // A pool page is a machine recipe, so it can only become a processing pattern. While AE's container is in
        // crafting
        // mode it mirrors the first nine cells into its crafting matrix on every later detectAndSendChanges()
        // (ContainerPatternTerm#copyToMatrix), clamping those to one and clearing non-items - an import must not lose
        // its
        // amounts to that. NotEnoughEnergistics switches the mode the same way before writing its own transfer.
        if (container.isCraftingMode()) {
            container.setCraftingMode(false);
        }

        final IAEStackInventory inputs = container.getPatternTerminal()
            .getAEInventoryByName(StorageName.CRAFTING_INPUT);
        final IAEStackInventory outputs = container.getPatternTerminal()
            .getAEInventoryByName(StorageName.CRAFTING_OUTPUT);

        for (int i = 0; i < inputs.getSizeInventory(); i++) {
            inputs.putAEStackInSlot(i, null);
        }
        for (int i = 0; i < outputs.getSizeInventory(); i++) {
            outputs.putAEStackInSlot(i, null);
        }

        final int droppedInputs = fill(inputs, cells.getCompoundTag("in"));
        final int droppedOutputs = fill(outputs, cells.getCompoundTag("out"));
        if (droppedInputs > 0 || droppedOutputs > 0) {
            // The page can be wider than this terminal: never drop a material without saying so.
            MessTech.LOG.warn(
                "NEI import dropped {} input and {} output cells: the recipe has more materials than the Ultimate "
                    + "Pattern Terminal's {} input and {} output cells.",
                droppedInputs,
                droppedOutputs,
                inputs.getSizeInventory(),
                outputs.getSizeInventory());
        }

        container.inputsSync.markDirty();
        container.outputsSync.markDirty();
    }

    /** Writes one map into an inventory; returns the number of cells the inventory is too small for. */
    private static int fill(IAEStackInventory inventory, NBTTagCompound cells) {
        int dropped = 0;
        for (var key : cells.func_150296_c()) {
            final int index = Integer.parseInt((String) key);
            if (index < 0 || index >= inventory.getSizeInventory()) {
                dropped++;
                continue;
            }
            final IAEStack<?> aes = toAEStack(readCell(cells.getCompoundTag((String) key)));
            if (aes != null) inventory.putAEStackInSlot(index, aes);
        }
        return dropped;
    }

    /** A NEI fluid display stack becomes the fluid it stands for, anything else a plain AE item stack. */
    private static IAEStack<?> toAEStack(ItemStack is) {
        if (is == null) return null;
        if (ItemList.Display_Fluid.isStackEqual(is, true, true)) {
            final FluidStack fluid = GTUtility.getFluidFromDisplayStack(is);
            return fluid == null ? null : AEFluidStack.create(fluid);
        }
        return AEItemStack.create(is);
    }
}
