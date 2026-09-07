package com.MessTech.common.item;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.ItemStackGuiData;
import com.cleanroommc.modularui.factory.ItemStackGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMEInventoryHandler;
import appeng.core.features.AEFeature;
import appeng.items.AEBaseInfiniteCell;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;
import gregtech.api.modularui2.GTModularScreen;

/**
 * ME Creative Ore Disk.
 * <p>
 * An AE storage cell that acts like a creative cell for every item registered under an ore dictionary
 * name beginning with {@code ore}. Shift-right-click opens a small MUI2 GUI to set the quantity that
 * each matching ore should appear with in the ME network.
 */
public class MECreativeOreDisk extends AEBaseInfiniteCell implements IGuiHolder<ItemStackGuiData> {

    public final ItemStackGuiFactory factory = new ItemStackGuiFactory("messtech:creative_ore_disk", this);

    public MECreativeOreDisk() {
        setUnlocalizedName("messtech.meCreativeOreDisk");
        setTextureName("appliedenergistics2:ItemCreativeStorageCell");
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxStackSize(1);
        setFeature(EnumSet.of(AEFeature.StorageCells, AEFeature.Creative));
    }

    public static int getQuantity(ItemStack stack) {
        if (stack != null && stack.getTagCompound() != null
            && stack.getTagCompound()
                .hasKey("quantity")) {
            return Math.max(
                1,
                stack.getTagCompound()
                    .getInteger("quantity"));
        }
        return 64;
    }

    public static void setQuantity(ItemStack stack, int quantity) {
        if (stack == null) return;
        stack.setTagInfo("quantity", new net.minecraft.nbt.NBTTagInt(Math.max(1, quantity)));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking()) {
            GuiManager.open(factory, new ItemStackGuiData(player, stack), (EntityPlayerMP) player);
        }
        return stack;
    }

    @Override
    public IMEInventoryHandler getCellInventory(ItemStack stack) {
        return new MECreativeOreDiskInventory(stack);
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return 63;
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {}

    @Override
    public ModularPanel buildUI(ItemStackGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        ItemStack stack = guiData.getItemStack();
        EntityPlayer player = guiData.getPlayer();
        IntSyncValue quantitySync = new IntSyncValue(() -> getQuantity(stack), val -> {
            // Make sure we mutate the actual stack in the player's inventory, not a GUI-side copy.
            ItemStack held = player.inventory.getCurrentItem();
            if (held != null && held.getItem() == MECreativeOreDisk.this) {
                setQuantity(held, val);
            } else {
                setQuantity(stack, val);
            }
            player.inventory.markDirty();
        }).allowC2S();
        syncManager.syncValue("quantity", quantitySync);

        ModularPanel panel = ModularPanel.defaultPanel("me_creative_ore_disk", 180, 80);
        panel.child(
            Flow.column()
                .coverChildren()
                .pos(10, 10)
                .child(new TextWidget("ME Creative Ore Disk").textAlign(Alignment.CenterLeft))
                .child(
                    Flow.row()
                        .coverChildren()
                        .childPadding(4)
                        .child(
                            new TextFieldWidget().setTextAlignment(Alignment.CenterRight)
                                .size(70, 14)
                                .setMaxLength(10)
                                .value(quantitySync)
                                .formatAsInteger(true)
                                .numbersInt(1, Integer.MAX_VALUE)
                                .defaultNumber(64))
                        .child(
                            new ButtonWidget<>().child(new TextWidget("Apply"))
                                .onMousePressed(mouseButton -> {
                                    if (mouseButton == 0) {
                                        panel.closeIfOpen();
                                        return true;
                                    }
                                    return false;
                                }))));
        return panel;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public @NotNull ModularScreen createScreen(ItemStackGuiData data, ModularPanel mainPanel) {
        return new GTModularScreen(mainPanel, GTGuiThemes.STANDARD);
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines,
        boolean displayMoreInfo) {
        lines.add("ME Creative Ore Disk");
        lines.add("OreDict: ore*");
        lines.add("Quantity per ore: " + getQuantity(stack));
    }

}
