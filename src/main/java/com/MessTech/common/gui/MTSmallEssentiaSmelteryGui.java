package com.MessTech.common.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.MessTech.common.gui.base.MTMultiMachineBaseGui;
import com.MessTech.common.machine.MTSmallEssentiaSmeltery;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.aspectrecipeindex.ModItems;
import com.gtnewhorizons.aspectrecipeindex.common.items.ItemAspect;

import cpw.mods.fml.common.Loader;
import gregtech.api.util.GTUtility;
import thaumcraft.api.aspects.Aspect;

/**
 * Controller GUI of the Small Essentia Smeltery. The terminal text list gets the pending essentia output as a list of
 * aspect icon + name + amount rows, in the same place GT shows a recipe's item/fluid products: the essentia of the
 * running recipe that will be flushed into the output hatches (the machine has no item/fluid outputs of its own).
 * Nothing is shown while no recipe is running.
 */
public class MTSmallEssentiaSmelteryGui extends MTMultiMachineBaseGui<MTSmallEssentiaSmeltery> {

    /** Most rows the terminal list shows before the rest of the pending aspects is cut off. */
    private static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 12;
    private static final int ICON_SIZE = ROW_HEIGHT - 2;

    /** {@code tag|amount;tag|amount}, sorted by amount, as synced from the controller. */
    private static final String PENDING_SYNC_KEY = "pendingEssentia";

    private static final boolean ASPECT_ICONS_AVAILABLE = Loader.isModLoaded("aspectrecipeindex");

    public MTSmallEssentiaSmelteryGui(MTSmallEssentiaSmeltery multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(PENDING_SYNC_KEY, new StringSyncValue(multiblock::getPendingOutputAspectsText));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> terminal = super.createTerminalTextWidget(syncManager, parent);
        StringSyncValue pending = syncManager.findSyncHandler(PENDING_SYNC_KEY, StringSyncValue.class);
        if (pending == null) return terminal;
        for (int i = 0; i < MAX_ROWS; i++) {
            final int row = i;
            Flow line = Flow.row()
                .fullWidth()
                .height(ROW_HEIGHT)
                .child(
                    new IDrawable.DrawableWidget(new DynamicDrawable(() -> aspectIcon(pending.getValue(), row)))
                        .size(ICON_SIZE)
                        .marginRight(2))
                .child(
                    new TextWidget<>(IKey.dynamic(() -> rowText(pending.getValue(), row))).height(ROW_HEIGHT)
                        .scale(0.7f));
            line.setEnabledIf(widget -> !rowText(pending.getValue(), row).isEmpty());
            terminal.child(line);
        }
        return terminal;
    }

    /** @return The aspect of {@code row}, or {@code null} when the pending list has no such row. */
    private static Aspect rowAspect(String pending, int row) {
        String[] entries = pending.split(";");
        if (row >= entries.length) return null;
        int separator = entries[row].indexOf('|');
        if (separator <= 0) return null;
        return Aspect.getAspect(entries[row].substring(0, separator));
    }

    private static int rowAmount(String pending, int row) {
        String[] entries = pending.split(";");
        if (row >= entries.length) return 0;
        int separator = entries[row].indexOf('|');
        if (separator <= 0) return 0;
        try {
            return Integer.parseInt(entries[row].substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String rowText(String pending, int row) {
        Aspect aspect = rowAspect(pending, row);
        if (aspect == null) return "";
        return EnumChatFormatting.AQUA + aspect
            .getName() + " " + EnumChatFormatting.YELLOW + "x" + GTUtility.formatShortenedLong(rowAmount(pending, row));
    }

    /** The aspect icon as a drawable; without AspectRecipeIndex a colour swatch in the aspect's colour stands in. */
    private static IDrawable aspectIcon(String pending, int row) {
        Aspect aspect = rowAspect(pending, row);
        if (aspect == null) return IDrawable.EMPTY;
        if (!ASPECT_ICONS_AVAILABLE) {
            return new Rectangle().color(0xFF000000 | aspect.getColor());
        }
        ItemStack icon = new ItemStack(ModItems.itemAspect);
        ItemAspect.setAspect(icon, aspect);
        return new ItemDrawable(icon);
    }
}
