package com.MessTech.common.gui;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.MessTech.common.gui.base.MTMultiMachineBaseGui;
import com.MessTech.common.machine.MTSmallMolecularArcaneAssembler;
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
import thaumcraft.api.aspects.Aspect;

/**
 * Controller GUI of the Small Molecular Arcane Assembler. The terminal list shows the six primal aspect buffers (icon,
 * name, stored / capacity in vis) and, while no wand or sceptre is inserted, a red reminder that the buffer is
 * inactive. Everything comes from the one state string the controller syncs.
 */
public class MTSmallMolecularArcaneAssemblerGui extends MTMultiMachineBaseGui<MTSmallMolecularArcaneAssembler> {

    private static final String STATE_SYNC_KEY = "arcaneAssemblerState";
    private static final int ROW_HEIGHT = 12;
    private static final int ICON_SIZE = ROW_HEIGHT - 2;
    private static final boolean ASPECT_ICONS_AVAILABLE = Loader.isModLoaded("aspectrecipeindex");
    /** The six primal aspects, cached: {@code Aspect#getPrimalAspects()} allocates a new list on every call. */
    private static final List<Aspect> PRIMALS = Aspect.getPrimalAspects();

    public MTSmallMolecularArcaneAssemblerGui(MTSmallMolecularArcaneAssembler multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(STATE_SYNC_KEY, new StringSyncValue(multiblock::getGuiStateText));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> terminal = super.createTerminalTextWidget(syncManager, parent);
        StringSyncValue state = syncManager.findSyncHandler(STATE_SYNC_KEY, StringSyncValue.class);
        if (state == null) return terminal;

        terminal.child(
            new TextWidget<>(IKey.lang("machine.smallmoleculararcaneassembler.gui.no_staff")).height(ROW_HEIGHT)
                .scale(0.8f)
                .setEnabledIf(widget -> !hasStaff(state.getValue())));

        for (int i = 0; i < PRIMALS.size(); i++) {
            final int index = i;
            Flow line = Flow.row()
                .fullWidth()
                .height(ROW_HEIGHT)
                .child(
                    new IDrawable.DrawableWidget(new DynamicDrawable(() -> aspectIcon(index))).size(ICON_SIZE)
                        .marginRight(2))
                .child(
                    new TextWidget<>(IKey.dynamic(() -> aspectRowText(state.getValue(), index))).height(ROW_HEIGHT)
                        .scale(0.7f));
            line.setEnabledIf(widget -> hasStaff(state.getValue()));
            terminal.child(line);
        }
        return terminal;
    }

    /** @return true when the synced state says a usable wand or sceptre sits in the controller slot. */
    private static boolean hasStaff(String state) {
        int[] values = parseState(state);
        return values != null && values[0] == 1;
    }

    /** @return "§?风 §b12§7/§b100" for one primal aspect, or an empty string when the state is unusable. */
    private static String aspectRowText(String state, int index) {
        int[] values = parseState(state);
        if (values == null || values[0] != 1) return "";

        Aspect aspect = PRIMALS.get(index);
        int stored = values[2 + index];
        int capacity = values[1];
        // The GUI shows the localised aspect name; the NEI requirement line keeps the pseudo-Latin tag name.
        return "§" + aspect.getChatcolor()
            + aspect.getLocalizedDescription()
            + " "
            + EnumChatFormatting.AQUA
            + visText(stored)
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.AQUA
            + visText(capacity);
    }

    /** @return "1;capacity;six primal amounts" parsed into ints, or null when the payload is malformed. */
    private static int[] parseState(String state) {
        if (state == null || state.isEmpty()) return null;
        String[] parts = state.split(";");
        if (parts.length < 2 + PRIMALS.size()) return null;

        int[] values = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return values;
    }

    /** @return Centivis as vis: whole numbers stay whole, the rest keeps one decimal (sceptres hold half vis). */
    private static String visText(int centivis) {
        if (centivis % 100 == 0) return Integer.toString(centivis / 100);
        return String.format(java.util.Locale.ROOT, "%.1f", centivis / 100.0F);
    }

    /** The aspect icon as a drawable; without AspectRecipeIndex a colour swatch in the aspect's colour stands in. */
    private static IDrawable aspectIcon(int index) {
        Aspect aspect = PRIMALS.get(index);
        if (!ASPECT_ICONS_AVAILABLE) {
            return new Rectangle().color(0xFF000000 | aspect.getColor());
        }
        ItemStack icon = new ItemStack(ModItems.itemAspect);
        ItemAspect.setAspect(icon, aspect);
        return new ItemDrawable(icon);
    }
}
