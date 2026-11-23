package com.MessTech.common.wireless.gui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;

import com.MessTech.common.wireless.MTWirelessColors;
import com.MessTech.common.wireless.MTWirelessNetworkBindingTool;
import com.MessTech.common.wireless.MTWirelessNetworkRuntime;
import com.MessTech.common.wireless.MTWirelessSavedData;
import com.MessTech.common.wireless.MTWirelessToolLocator;
import com.MessTech.init.MessTech;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Network picker of the {@link MTWirelessNetworkBindingTool}: lists the wireless ME networks the player may use and
 * saves the clicked one into the tool.
 * <p>
 * Opened by right-clicking the air with the tool ({@code MTWirelessNetworkBindingTool#onItemRightClick} calls
 * {@link #open}), and by the picker key bind ({@code MTWirelessKeyBindings}) so the tool can be used while it sits in
 * its Baubles accessory slot. Every action locates the tool through {@link MTWirelessToolLocator} rather than the main
 * hand, so the picker works for a worn tool too.
 * <p>
 * The {@link SimpleGuiFactory} is created by {@link #init()}, which has to run in {@code preInit}: the constructor
 * registers the factory, and both sides need it.
 * <p>
 * v2 look: the panel is drawn in the Flux Networks 1.20 style through {@link MTWirelessGuiStyle} - a rounded dark
 * background with a network-colored frame, one tinted row bar per network and a white / grey row label. The frame is
 * tinted with the color of the network currently saved in the tool ({@link MTWirelessColors#DEFAULT} when there is
 * none), the saved row is marked with {@code wirelessme.gui.picker.selected} ("§a已保存") and rows are clicked to save
 * that network into the tool.
 * <p>
 * MUI2 builds this panel on the server and on the client, but the network list only exists on the server. It therefore
 * reaches the client through sync values (fixed {@value #MAX_ROWS} rows of name + color, the total count, the saved row
 * and the frame color) whose server getters re-read
 * {@link MTWirelessNetworkRuntime#getAccessibleNetworks(EntityPlayer)} every tick; the client getters are placeholders
 * that the sync packets overwrite. The rows are fixed {@value #MAX_ROWS} buttons, disabled while no network uses them,
 * so the widget tree is the same on both sides.
 * <p>
 * A click only reports its row index: the server action re-reads the list and ignores an out-of-range index, which
 * keeps a list change between drawing and clicking harmless.
 */
public final class MTWirelessNetworkPickerGui implements IGuiHolder<GuiData> {

    private static final String FACTORY_NAME = "messtech_wireless_picker";
    private static final String PANEL_NAME = "messtech:wireless_picker";

    /** How many networks the panel shows; the rest is summarised with {@code wirelessme.gui.more}. */
    private static final int MAX_ROWS = 8;

    /** Flux picker panel: the 172x172 content area is centered in a slightly larger standard panel. */
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 180;
    private static final int CONTENT_X = (PANEL_WIDTH - MTWirelessGuiStyle.CONTENT_SIZE) / 2;
    private static final int CONTENT_Y = (PANEL_HEIGHT - MTWirelessGuiStyle.CONTENT_SIZE) / 2;

    private static final int ROW_WIDTH = MTWirelessGuiStyle.ROW_BAR_WIDTH;
    private static final int ROW_HEIGHT = MTWirelessGuiStyle.ROW_BAR_HEIGHT;
    private static final int ROW_X = CONTENT_X + (MTWirelessGuiStyle.CONTENT_SIZE - ROW_WIDTH) / 2;
    /** Content-local layout, matching the core GUI pages: header at y = 8, first row at y = 24, 12 px row pitch. */
    private static final int TITLE_X = CONTENT_X + 20;
    private static final int TITLE_Y = CONTENT_Y + 8;
    private static final int ROW_Y = CONTENT_Y + 24;
    private static final int HINT_X = CONTENT_X + 20;
    private static final int HINT_Y = CONTENT_Y + 128;
    private static final int BUTTON_Y = CONTENT_Y + 148;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CLEAR_WIDTH = 92;
    private static final int CLOSE_WIDTH = 48;
    private static final int BUTTON_GAP = 6;

    /** Sync keys; both sides register exactly these, in this order. */
    private static final String SYNC_TOTAL = "networkTotal";
    private static final String SYNC_SAVED_ROW = "savedRow";
    private static final String SYNC_BORDER_COLOR = "borderColor";

    /** Server actions. */
    private static final String ACTION_PICK = "pickNetwork";
    private static final String ACTION_CLEAR = "clearSavedNetwork";

    private static SimpleGuiFactory factory;

    private MTWirelessNetworkPickerGui() {}

    /** Registers the GUI factory; called once from {@code MTWirelessContent#preInit}. */
    public static void init() {
        if (factory != null) return;
        factory = new SimpleGuiFactory(FACTORY_NAME, new MTWirelessNetworkPickerGui());
    }

    /** Server: opens the picker for the binding tool the player carries (Baubles accessory slot first). */
    public static void open(EntityPlayerMP player) {
        if (factory == null || player == null) return;
        if (MTWirelessToolLocator.find(player) == null) return;
        factory.open(player);
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        // The panel is built before the ModularContainer exists, so syncManager.getPlayer() is not usable yet; the
        // build context's player is the right one on both sides (server player on the server, where the getters run).
        EntityPlayer player = data.getPlayer();
        PickerState state = new PickerState(player);

        // Server getters re-read the list every tick; client getters are placeholders (MUI2 does not update the cache
        // from the source on the client, sync packets fill it).
        IntSyncValue totalSync = new IntSyncValue(() -> 0, null, state::total, null);
        IntSyncValue savedRowSync = new IntSyncValue(() -> -1, null, state::savedRow, null);
        IntSyncValue borderColorSync = new IntSyncValue(() -> MTWirelessColors.DEFAULT, null, state::borderColor, null);

        StringSyncValue[] nameSyncs = new StringSyncValue[MAX_ROWS];
        IntSyncValue[] colorSyncs = new IntSyncValue[MAX_ROWS];
        for (int index = 0; index < MAX_ROWS; index++) {
            final int row = index;
            nameSyncs[row] = new StringSyncValue(() -> "", null, () -> state.rowName(row), null);
            colorSyncs[row] = new IntSyncValue(() -> MTWirelessColors.DEFAULT, null, () -> state.rowColor(row), null);
            syncManager.syncValue(rowNameKey(row), nameSyncs[row]);
            syncManager.syncValue(rowColorKey(row), colorSyncs[row]);
        }
        syncManager.syncValue(SYNC_TOTAL, totalSync);
        syncManager.syncValue(SYNC_SAVED_ROW, savedRowSync);
        syncManager.syncValue(SYNC_BORDER_COLOR, borderColorSync);

        syncManager.registerSyncedAction(ACTION_PICK, Side.SERVER, buffer -> {
            int row = buffer.readInt();
            if (player instanceof EntityPlayerMP serverPlayer) runOnServerThread(() -> pickNetwork(serverPlayer, row));
        });
        syncManager.registerSyncedAction(ACTION_CLEAR, Side.SERVER, buffer -> {
            if (!(player instanceof EntityPlayerMP serverPlayer)) return;
            runOnServerThread(() -> clearSavedNetwork(serverPlayer));
        });

        ModularPanel panel = new ModularPanel(PANEL_NAME).size(PANEL_WIDTH, PANEL_HEIGHT)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true);

        // Rounded Flux background plus the frame tinted with the saved network's color. The background is drawn
        // stretched to the 172x172 content area, so the panel around it stays transparent.
        IDrawable frame = tinted(borderColorSync, MTWirelessGuiStyle::frame);
        panel.child(
            new IDrawable.DrawableWidget(IDrawable.of(MTWirelessGuiStyle.background(), frame))
                .size(MTWirelessGuiStyle.CONTENT_SIZE, MTWirelessGuiStyle.CONTENT_SIZE)
                .left(CONTENT_X)
                .top(CONTENT_Y));

        panel.child(
            new TextWidget<>(IKey.lang("wirelessme.gui.picker.title")).size(ROW_WIDTH, 10)
                .left(TITLE_X)
                .top(TITLE_Y)
                .color(MTWirelessGuiStyle.TEXT_COLOR_LIGHT)
                .textAlign(Alignment.CenterLeft));

        for (int index = 0; index < MAX_ROWS; index++) {
            final int row = index;
            ButtonWidget<?> rowButton = new ButtonWidget<>().size(ROW_WIDTH, ROW_HEIGHT)
                .left(ROW_X)
                .top(ROW_Y + row * ROW_HEIGHT)
                .background(tinted(colorSyncs[row], MTWirelessGuiStyle::rowBar))
                .disableHoverThemeBackground(true)
                .paddingLeft(4)
                .overlay(
                    IKey.dynamic(() -> rowLabel(nameSyncs[row].getStringValue(), savedRowSync.getIntValue(), row))
                        .color(() -> rowTextColor(savedRowSync.getIntValue(), row))
                        .alignment(Alignment.CenterLeft))
                .setEnabledIf(button -> row < visibleRows(totalSync.getIntValue()))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    if (row >= visibleRows(totalSync.getIntValue())) return false;
                    syncManager.callSyncedAction(ACTION_PICK, buffer -> buffer.writeInt(row));
                    return true;
                });
            // Hide the row until the first sync arrives, so the panel does not flash 8 empty rows for one frame.
            rowButton.setEnabled(row < visibleRows(totalSync.getIntValue()));
            panel.child(rowButton);
        }

        panel.child(
            new TextWidget<>(IKey.dynamic(() -> hintLine(totalSync.getIntValue()))).size(ROW_WIDTH, 10)
                .left(HINT_X)
                .top(HINT_Y)
                .textAlign(Alignment.CenterLeft));

        panel.child(
            new PickerButton(IKey.lang("wirelessme.gui.picker.clear"), () -> borderColorSync.getIntValue(), button -> {
                syncManager.callSyncedAction(ACTION_CLEAR);
                return true;
            }).size(CLEAR_WIDTH, BUTTON_HEIGHT)
                .left(ROW_X)
                .top(BUTTON_Y));
        panel.child(
            new PickerButton(IKey.lang("wirelessme.gui.picker.close"), () -> borderColorSync.getIntValue(), button -> {
                panel.closeIfOpen();
                return true;
            }).size(CLOSE_WIDTH, BUTTON_HEIGHT)
                .left(ROW_X + CLEAR_WIDTH + BUTTON_GAP)
                .top(BUTTON_Y));

        return panel;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(MessTech.MODID, mainPanel);
    }

    // ------------------------------------------------------------------ server side

    /**
     * MUI2 invokes server actions from the network thread; the tool's NBT is player/world state and belongs to the
     * server thread. GTNHLib's helper runs the task right away when we are already on it.
     */
    private static void runOnServerThread(Runnable task) {
        try {
            ServerThreadUtil.addScheduledTask(task);
        } catch (IllegalStateException e) {
            // The server is shutting down, so there is no state left to update.
        }
    }

    /** Forgets the network in the tool the player carries. */
    private static void clearSavedNetwork(EntityPlayerMP player) {
        ItemStack tool = MTWirelessToolLocator.find(player);
        if (tool == null) return;
        MTWirelessNetworkBindingTool.clearNetwork(player, tool);
    }

    private static List<MTWirelessSavedData.NetworkInfo> accessibleNetworks(EntityPlayer player) {
        if (player == null) return Collections.emptyList();
        return MTWirelessNetworkRuntime.getAccessibleNetworks(player);
    }

    /** Saves the network the clicked row showed, if the list still has that row. */
    private static void pickNetwork(EntityPlayerMP player, int row) {
        ItemStack tool = MTWirelessToolLocator.find(player);
        if (tool == null) return;

        List<MTWirelessSavedData.NetworkInfo> networks = accessibleNetworks(player);
        if (row < 0 || row >= MAX_ROWS || row >= networks.size()) return;
        MTWirelessNetworkBindingTool.saveNetwork(
            player,
            tool,
            networks.get(row)
                .frequency());
    }

    // ------------------------------------------------------------------ payload helpers

    /** Draws one line of text in the local widget space (the caller's draw pass is already transformed). */
    private static void drawText(String text, int x, int y, int width, int height, int color, Alignment alignment) {
        TextRenderer renderer = TextRenderer.SHARED;
        renderer.setAlignment(alignment, width, height);
        renderer.setColor(color);
        renderer.setShadow(false);
        renderer.setScale(1f);
        renderer.setPos(x, y);
        renderer.setSimulate(false);
        renderer.draw(text);
    }

    private static String rowNameKey(int row) {
        return "row" + row + "Name";
    }

    private static String rowColorKey(int row) {
        return "row" + row + "Color";
    }

    /** Number of rows that carry a network; the panel keeps a fixed {@value #MAX_ROWS} buttons. */
    private static int visibleRows(int total) {
        return Math.min(Math.max(total, 0), MAX_ROWS);
    }

    /** Network names may be 32 chars long; the row bar only has 138 px for name and state marker. */
    private static String displayName(String name, int maxChars) {
        if (name == null) return "";
        String flat = name.replace('\n', ' ');
        if (flat.length() <= maxChars) return flat;
        return flat.substring(0, maxChars) + "…";
    }

    private static String rowLabel(String name, int savedRow, int row) {
        String display = displayName(name, row == savedRow ? 14 : 20);
        if (display.isEmpty()) return "";
        if (row != savedRow) return display;
        return display + " " + StatCollector.translateToLocal("wirelessme.gui.picker.selected");
    }

    /** Saved row: white; the other rows: grey (Flux list style). */
    private static int rowTextColor(int savedRow, int row) {
        return row == savedRow ? MTWirelessGuiStyle.TEXT_COLOR : MTWirelessGuiStyle.TEXT_COLOR_DIM;
    }

    /** Empty-list line, how many networks are hidden, or the "left click to save" hint. */
    private static String hintLine(int total) {
        if (total <= 0) return StatCollector.translateToLocal("wirelessme.gui.picker.empty");
        if (total > MAX_ROWS) return StatCollector.translateToLocalFormatted("wirelessme.gui.more", total - MAX_ROWS);
        return StatCollector.translateToLocal("wirelessme.gui.picker.hint");
    }

    /**
     * Wraps a {@code rgb -> drawable} factory in a {@link DynamicDrawable} that only rebuilds the texture when the
     * synced color actually changes, so a still panel does not allocate a tinted texture every frame.
     */
    private static IDrawable tinted(IntSyncValue color, IntFunction<IDrawable> factory) {
        return new DynamicDrawable(new Supplier<IDrawable>() {

            private int cachedColor = Integer.MIN_VALUE;
            private IDrawable cachedDrawable;

            @Override
            public IDrawable get() {
                int rgb = color.getIntValue();
                if (this.cachedDrawable == null || rgb != this.cachedColor) {
                    this.cachedColor = rgb;
                    this.cachedDrawable = factory.apply(rgb);
                }
                return this.cachedDrawable;
            }
        });
    }

    // ------------------------------------------------------------------ widgets

    /** Flux's SimpleButton: a 1 px outline in the network color with the label centered inside it. */
    private static final class PickerButton extends ButtonWidget<PickerButton> {

        private final IKey label;
        private final IntSupplier color;

        PickerButton(IKey label, IntSupplier color, IGuiAction.MousePressed action) {
            this.label = label;
            this.color = color;
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                return action.press(mouseButton);
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            int rgb = this.color.getAsInt();
            MTWirelessGuiStyle.buttonFrame(rgb)
                .drawAtZero(context, getArea(), getActiveWidgetTheme(widgetTheme, isHovering()));
            drawText(
                this.label.getFormatted(),
                0,
                0,
                getArea().width,
                getArea().height,
                MTWirelessGuiStyle.argb(rgb),
                Alignment.Center);
        }
    }

    // ------------------------------------------------------------------ server side snapshot

    /**
     * Per panel snapshot of the server side state. MUI2 re-reads every {@code SyncHandler} each tick, so the snapshot
     * is only rebuilt once per server tick no matter how many sync values ask for it.
     */
    private static final class PickerState {

        private final EntityPlayer player;
        private final String[] names = new String[MAX_ROWS];
        private final int[] colors = new int[MAX_ROWS];

        private int cachedTick = Integer.MIN_VALUE;
        private int total;
        private int savedRow = -1;
        private int borderColor = MTWirelessColors.DEFAULT;

        PickerState(EntityPlayer player) {
            this.player = player;
            Arrays.fill(this.names, "");
            Arrays.fill(this.colors, MTWirelessColors.DEFAULT);
        }

        /** Server getter of the number of accessible networks; never called on the client. */
        int total() {
            refresh();
            return this.total;
        }

        /** Server getter of the saved network's row ({@code -1} = none); never called on the client. */
        int savedRow() {
            refresh();
            return this.savedRow;
        }

        /** Server getter of the frame color (saved network color, or {@link MTWirelessColors#DEFAULT}). */
        int borderColor() {
            refresh();
            return this.borderColor;
        }

        /** Server getter of a listed network's name; never called on the client. */
        String rowName(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.names[row] : "";
        }

        /** Server getter of a listed network's color; never called on the client. */
        int rowColor(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.colors[row] : MTWirelessColors.DEFAULT;
        }

        private void refresh() {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) {
                reset();
                return;
            }
            int tick = server.getTickCounter();
            if (tick == this.cachedTick) return;
            this.cachedTick = tick;

            reset();

            List<MTWirelessSavedData.NetworkInfo> networks = accessibleNetworks(this.player);
            this.total = networks.size();
            int rows = Math.min(this.total, MAX_ROWS);
            for (int row = 0; row < rows; row++) {
                MTWirelessSavedData.NetworkInfo network = networks.get(row);
                this.names[row] = network.name() == null ? "" : network.name();
                this.colors[row] = network.color();
            }

            ItemStack tool = this.player == null ? null : MTWirelessToolLocator.find(this.player);
            UUID saved = tool == null ? null : MTWirelessNetworkBindingTool.getSavedNetwork(tool);
            if (saved == null) return;

            for (int row = 0; row < rows; row++) {
                if (saved.equals(
                    networks.get(row)
                        .frequency())) {
                    this.savedRow = row;
                    break;
                }
            }

            MTWirelessSavedData data = MTWirelessSavedData.get(server);
            if (data != null) this.borderColor = data.getColor(saved);
        }

        private void reset() {
            this.total = 0;
            this.savedRow = -1;
            this.borderColor = MTWirelessColors.DEFAULT;
            Arrays.fill(this.names, "");
            Arrays.fill(this.colors, MTWirelessColors.DEFAULT);
        }
    }
}
