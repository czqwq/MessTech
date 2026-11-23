package com.MessTech.common.wireless.gui;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.wireless.MTWirelessColors;
import com.MessTech.common.wireless.MTWirelessMemberKey;
import com.MessTech.common.wireless.MTWirelessNetworkRuntime;
import com.MessTech.common.wireless.MTWirelessSavedData;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

import appeng.me.helpers.IGridProxyable;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/**
 * The 「无线网络」 tab that every AE2 based GregTech hatch GUI gets: a 24x24 tab hanging on the left outside of the
 * machine panel and the network list popup it opens.
 *
 * <p>
 * This is the 1.7.10 counterpart of GTLCore's {@code WirelessAeTargetMenu}/{@code WirelessAeTargetScreen}. The tab is
 * attached from a mixin on {@code MTETieredMachineBlockBaseGui#build} - the shared base GUI of every GT single block
 * hatch GUI, which also covers this mod's own {@code MTInventoryInputBusMEGui}/{@code MTInventoryInputHatchMEGui} -
 * and directly from {@code MTEssentiaHatchMETileEntity#buildUI} for the Thaumcraft ME essentia hatch (its GUI does not
 * go through the GT base class).
 *
 * <p>
 * v2 look (see {@code docs/wireless-me-network.md} §9.4): the tab button draws the Flux 「无线充能」 icon
 * ({@link MTWirelessGuiStyle#SPRITE_WIRELESS}, 16x16 centered in the 24x24 button) and the popup is a Flux-style
 * 176x172 panel - rounded dark background, network-colored frame, network-colored name bar and row bars. Everything
 * else is plain MUI2: the network list is a {@code syncManager.syncedPanel} whose widgets read
 * {@link com.cleanroommc.modularui.value.sync.SyncHandler}s registered on the sub panel's own sync manager, and the
 * connect/disconnect clicks are {@code registerSyncedAction(Side.SERVER, ...)} handlers calling
 * {@link MTWirelessNetworkRuntime}. No custom packets, no client classes outside the {@code draw} overrides.
 *
 * <p>
 * Display semantics: the panel shows the network the hatch is bound to, then up to {@link #MAX_ROWS} accessible
 * networks (name + 「已连接」 marker, or 「等待核心」 while the network has no core yet). Left click connects, left click
 * on an already connected row disconnects, and an extra button drops the current binding. A hatch that is already wired
 * to an ME cable is locked (buttons do nothing) and the panel explains that through {@code wirelessme.tab.locked}. A
 * wireless link created by the runtime itself is not treated as "wired" - the runtime owns that connection and the
 * player must be able to disconnect it.
 */
public final class MTWirelessHatchTab {

    /** Maximum amount of networks listed in the popup, see docs/wireless-me-network.md §9.4. */
    public static final int MAX_ROWS = 8;

    /** Panel key of the always visible tab. Also used by {@code MixinModularScreen_WirelessTab}. */
    public static final String TAB_PANEL_KEY = "messtech:wireless_tab";
    /** Panel key of the network list popup. */
    public static final String NETWORK_PANEL_KEY = "messtech:wireless_networks";

    /** Synced action keys, see docs/wireless-me-network.md §9.4. */
    public static final String ACTION_CONNECT = "messtech:wireless:connect";
    public static final String ACTION_DISCONNECT = "messtech:wireless:disconnect";

    // Lang keys, all from docs/wireless-me-network.md §9.5.
    private static final String KEY_TITLE = "wirelessme.tab.title";
    private static final String KEY_CURRENT = "wirelessme.tab.current";
    private static final String KEY_CURRENT_NONE = "wirelessme.tab.current.none";
    private static final String KEY_CONNECT = "wirelessme.tab.connect";
    private static final String KEY_CONNECTED = "wirelessme.tab.connected";
    private static final String KEY_WAITING_CORE = "wirelessme.tab.waiting_core";
    private static final String KEY_LOCKED = "wirelessme.tab.locked";
    private static final String KEY_DISCONNECT = "wirelessme.tab.disconnect";
    private static final String KEY_EMPTY = "wirelessme.tab.empty";
    private static final String KEY_MORE = "wirelessme.tab.more";
    private static final String KEY_CLOSE = "wirelessme.tab.close";

    // Tab: a bare 24x24 tab hanging on the left outside of the machine panel.
    private static final int TAB_SIZE = 24;
    private static final int TAB_OFFSET_X = -26;
    private static final int TAB_OFFSET_Y = 20;
    /** The 16x16 icon is centered inside the 24x24 tab panel. */
    private static final int TAB_ICON_OFFSET = (TAB_SIZE - MTWirelessGuiStyle.TAB_SIZE) / 2;

    // Network list popup: same width as the standard machine panel so it can safely replace it on screen. The 172x172
    // Flux content area is centered horizontally (176 wide panel, 2 px on each side).
    private static final int NETWORK_PANEL_WIDTH = 176;
    private static final int NETWORK_PANEL_HEIGHT = 172;
    private static final int CONTENT_X = (NETWORK_PANEL_WIDTH - MTWirelessGuiStyle.CONTENT_SIZE) / 2;
    private static final int CONTENT_Y = (NETWORK_PANEL_HEIGHT - MTWirelessGuiStyle.CONTENT_SIZE) / 2;

    /** Content-local layout: header at y = 6, current network bar at y = 20, status line at y = 36, rows at y = 48. */
    private static final int TEXT_LEFT = 20;
    private static final int TITLE_Y = 6;
    private static final int CURRENT_BAR_Y = 20;
    private static final int STATUS_Y = 36;
    private static final int ROW_X = 13;
    private static final int ROW_Y = 48;
    private static final int ROW_WIDTH = MTWirelessGuiStyle.ROW_BAR_WIDTH;
    private static final int ROW_HEIGHT = MTWirelessGuiStyle.ROW_BAR_HEIGHT;
    private static final int BUTTON_Y = 150;
    private static final int BUTTON_HEIGHT = 14;
    private static final int DISCONNECT_WIDTH = 92;
    private static final int CLOSE_WIDTH = 48;
    private static final int BUTTON_GAP = 6;
    /** Longest network name shown inline before it is ellipsized. */
    private static final int DISPLAY_NAME_LENGTH = 16;
    /** A row without a state suffix has room for a slightly longer name than the current network bar. */
    private static final int ROW_NAME_LENGTH = 20;
    private static final int ROW_NAME_LENGTH_WITH_SUFFIX = 14;

    private MTWirelessHatchTab() {}

    // ------------------------------------------------------------------ attachment

    /**
     * Attaches the wireless tab to the GUI of a GregTech meta tile entity. Only AE2 based hatches get the tab; every
     * other GUI is left completely untouched.
     */
    public static void attach(ModularPanel panel, PanelSyncManager syncManager, MetaTileEntity machine) {
        if (panel == null || syncManager == null || machine == null) return;
        if (!(machine instanceof MTEHatch)) return;
        if (!(machine instanceof IGridProxyable)) return;
        IGregTechTileEntity baseMetaTileEntity = machine.getBaseMetaTileEntity();
        if (!(baseMetaTileEntity instanceof TileEntity tileEntity)) return;
        attach(panel, syncManager, tileEntity);
    }

    /**
     * Attaches the wireless tab to a GUI that is not a GT meta tile entity GUI (the Thaumcraft ME essentia hatch uses
     * this overload). The tile entity must host an AE2 grid node, otherwise there is nothing to connect.
     */
    public static void attach(ModularPanel panel, PanelSyncManager syncManager, TileEntity tileEntity) {
        if (panel == null || syncManager == null || tileEntity == null) return;
        if (!(tileEntity instanceof IGridProxyable)) return;
        MTWirelessMemberKey member = MTWirelessMemberKey.of(tileEntity);
        if (member == null) return;

        // The list popup is a synced panel: its widgets own every sync value and both synced actions. The tab itself
        // holds no synced data, it only opens/closes the popup.
        IPanelHandler networkPanel = syncManager.syncedPanel(
            NETWORK_PANEL_KEY,
            true,
            (subSyncManager, panelHandler) -> createNetworkPanel(subSyncManager, panelHandler, member, panel));
        IPanelHandler tabPanel = syncManager
            .syncedPanel(TAB_PANEL_KEY, true, (subSyncManager, panelHandler) -> createTabPanel(panel, networkPanel));

        // Client side safety net for the always visible tab: MixinModularScreen_WirelessTab opens it from
        // ModularScreen#onOpen, this second attempt makes the tab show up even if that mixin is unavailable. Opening an
        // already open panel is a no-op, so the two paths cannot fight each other.
        syncManager.onClientTick(new Runnable() {

            private boolean attempted;

            @Override
            public void run() {
                if (this.attempted) return;
                this.attempted = true;
                if (!tabPanel.isPanelOpen()) tabPanel.openPanel();
            }
        });
    }

    // ------------------------------------------------------------------ panels

    private static ModularPanel createTabPanel(ModularPanel mainPanel, IPanelHandler networkPanel) {
        return new ModularPanel(TAB_PANEL_KEY) {

            @Override
            public boolean isDraggable() {
                return false;
            }
        }.size(TAB_SIZE, TAB_SIZE)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .relative(mainPanel)
            .left(TAB_OFFSET_X)
            .top(TAB_OFFSET_Y)
            .child(
                new WirelessTabButton(networkPanel).size(TAB_SIZE, TAB_SIZE)
                    .tooltip(tooltip -> tooltip.addLine(IKey.lang(KEY_TITLE))));
    }

    private static ModularPanel createNetworkPanel(PanelSyncManager syncManager, IPanelHandler panelHandler,
        MTWirelessMemberKey member, ModularPanel mainPanel) {
        NetworkState state = new NetworkState(syncManager, member);

        StringSyncValue current = new StringSyncValue(() -> "", null, state::currentName, null);
        syncManager.syncValue("current", current);
        IntSyncValue currentColor = new IntSyncValue(() -> MTWirelessColors.DEFAULT, null, state::currentColor, null);
        syncManager.syncValue("current_color", currentColor);
        BooleanSyncValue wired = new BooleanSyncValue(() -> false, null, state::wired, null);
        syncManager.syncValue("wired", wired);
        IntSyncValue rowCount = new IntSyncValue(() -> 0, null, state::rowCount, null);
        syncManager.syncValue("rows", rowCount);
        IntSyncValue total = new IntSyncValue(() -> 0, null, state::total, null);
        syncManager.syncValue("total", total);

        StringSyncValue[] names = new StringSyncValue[MAX_ROWS];
        IntSyncValue[] colors = new IntSyncValue[MAX_ROWS];
        BooleanSyncValue[] connected = new BooleanSyncValue[MAX_ROWS];
        BooleanSyncValue[] hasCore = new BooleanSyncValue[MAX_ROWS];
        for (int i = 0; i < MAX_ROWS; i++) {
            final int index = i;
            names[i] = new StringSyncValue(() -> "", null, () -> state.rowName(index), null);
            colors[i] = new IntSyncValue(() -> MTWirelessColors.DEFAULT, null, () -> state.rowColor(index), null);
            connected[i] = new BooleanSyncValue(() -> false, null, () -> state.rowConnected(index), null);
            hasCore[i] = new BooleanSyncValue(() -> false, null, () -> state.rowHasCore(index), null);
            syncManager.syncValue("row_name_" + i, names[i]);
            syncManager.syncValue("row_color_" + i, colors[i]);
            syncManager.syncValue("row_connected_" + i, connected[i]);
            syncManager.syncValue("row_core_" + i, hasCore[i]);
        }

        syncManager.registerSyncedAction(ACTION_CONNECT, Side.SERVER, buffer -> connect(syncManager, member, buffer));
        syncManager
            .registerSyncedAction(ACTION_DISCONNECT, Side.SERVER, buffer -> disconnect(syncManager, member, buffer));

        ModularPanel panel = new ModularPanel(NETWORK_PANEL_KEY).size(NETWORK_PANEL_WIDTH, NETWORK_PANEL_HEIGHT)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .relative(mainPanel)
            .left(0)
            .top(0);

        // Rounded Flux background plus the frame tinted with the bound network's color (DEFAULT when unbound).
        IDrawable frame = tinted(currentColor, MTWirelessGuiStyle::frame);
        panel.child(
            new IDrawable.DrawableWidget(IDrawable.of(MTWirelessGuiStyle.background(), frame))
                .size(MTWirelessGuiStyle.CONTENT_SIZE, MTWirelessGuiStyle.CONTENT_SIZE)
                .left(CONTENT_X)
                .top(CONTENT_Y));

        panel.child(
            new TextWidget<>(IKey.lang(KEY_TITLE)).size(ROW_WIDTH, 10)
                .left(CONTENT_X + TEXT_LEFT)
                .top(CONTENT_Y + TITLE_Y)
                .color(MTWirelessGuiStyle.TEXT_COLOR_LIGHT)
                .textAlign(Alignment.CenterLeft));

        panel.child(new CurrentNetworkBar(current, currentColor));

        panel.child(
            new IDrawable.DrawableWidget(MTWirelessGuiStyle.lockIcon())
                .size(MTWirelessGuiStyle.LOCK_SIZE, MTWirelessGuiStyle.LOCK_SIZE)
                .left(CONTENT_X + ROW_X)
                .top(CONTENT_Y + STATUS_Y)
                .setEnabledIf(widget -> wired.getBoolValue()));
        panel.child(
            new TextWidget<>(IKey.dynamic(() -> statusLine(wired, rowCount, total))).size(ROW_WIDTH - 14, 10)
                .left(CONTENT_X + TEXT_LEFT + 7)
                .top(CONTENT_Y + STATUS_Y)
                .textAlign(Alignment.CenterLeft));

        for (int i = 0; i < MAX_ROWS; i++) {
            panel.child(new NetworkRow(i, names[i], colors[i], connected[i], hasCore[i], wired, rowCount, syncManager));
        }

        panel.child(
            new HatchButton(
                IKey.lang(KEY_DISCONNECT),
                () -> currentColor.getIntValue(),
                () -> !current.getStringValue()
                    .isEmpty(),
                button -> {
                    syncManager.callSyncedAction(ACTION_DISCONNECT, buffer -> buffer.writeInt(-1));
                    return true;
                }).size(DISCONNECT_WIDTH, BUTTON_HEIGHT)
                    .left(CONTENT_X + ROW_X)
                    .top(CONTENT_Y + BUTTON_Y));
        panel.child(new HatchButton(IKey.lang(KEY_CLOSE), () -> currentColor.getIntValue(), () -> true, button -> {
            panelHandler.closePanel();
            return true;
        }).size(CLOSE_WIDTH, BUTTON_HEIGHT)
            .left(CONTENT_X + ROW_X + DISCONNECT_WIDTH + BUTTON_GAP)
            .top(CONTENT_Y + BUTTON_Y));
        return panel;
    }

    // ------------------------------------------------------------------ text helpers

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

    // ------------------------------------------------------------------ display text

    private static String currentLine(StringSyncValue current) {
        String networkName = displayName(current.getStringValue(), DISPLAY_NAME_LENGTH);
        if (networkName.isEmpty()) {
            return StatCollector.translateToLocal(KEY_CURRENT_NONE);
        }
        return StatCollector.translateToLocalFormatted(KEY_CURRENT, networkName);
    }

    private static String statusLine(BooleanSyncValue wired, IntSyncValue rowCount, IntSyncValue total) {
        if (wired.getBoolValue()) return StatCollector.translateToLocal(KEY_LOCKED);
        if (rowCount.getIntValue() <= 0) return StatCollector.translateToLocal(KEY_EMPTY);
        if (total.getIntValue() > rowCount.getIntValue()) {
            return StatCollector.translateToLocalFormatted(KEY_MORE, total.getIntValue() - rowCount.getIntValue());
        }
        return StatCollector.translateToLocal(KEY_CONNECT);
    }

    private static String rowLine(StringSyncValue name, BooleanSyncValue connected, BooleanSyncValue hasCore) {
        boolean isConnected = connected.getBoolValue();
        // A network without a core can only be bound as "waiting for core"; mark it so the click is not surprising.
        boolean waitingForCore = !isConnected && !hasCore.getBoolValue();
        String networkName = displayName(
            name.getStringValue(),
            isConnected || waitingForCore ? ROW_NAME_LENGTH_WITH_SUFFIX : ROW_NAME_LENGTH);
        if (networkName.isEmpty()) return "";
        String text = networkName;
        if (isConnected) text = text + "  " + StatCollector.translateToLocal(KEY_CONNECTED);
        else if (waitingForCore) text = text + "  " + StatCollector.translateToLocal(KEY_WAITING_CORE);
        return text;
    }

    /** Network names may be 32 chars long; the popup only has one 138 px row, so long names are ellipsized. */
    private static String displayName(String networkName, int maxLength) {
        if (networkName == null) return "";
        String flat = networkName.replace('\n', ' ');
        if (flat.length() <= maxLength) return flat;
        return flat.substring(0, maxLength) + "…";
    }

    private static String rowTooltipKey(BooleanSyncValue wired, BooleanSyncValue connected) {
        if (wired.getBoolValue()) return KEY_LOCKED;
        return connected.getBoolValue() ? KEY_DISCONNECT : KEY_CONNECT;
    }

    // ------------------------------------------------------------------ server actions

    /**
     * Left click on a network row: connect to it (or retry the existing binding when the row is already the bound
     * network but not connected yet). Disconnecting a row uses {@link #ACTION_DISCONNECT}.
     */
    private static void connect(PanelSyncManager syncManager, MTWirelessMemberKey member, PacketBuffer buffer) {
        int index = buffer.readInt();
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        EntityPlayer player = syncManager.getPlayer();
        if (server == null || player == null || index < 0 || index >= MAX_ROWS) return;

        List<MTWirelessSavedData.NetworkInfo> networks = MTWirelessNetworkRuntime.getAccessibleNetworks(player);
        if (index >= networks.size()) return;
        UUID frequency = networks.get(index)
            .frequency();
        UUID bound = MTWirelessNetworkRuntime.getMemberNetwork(server, member);
        if (frequency.equals(bound)) {
            // The binding exists but the client did not see it as connected (e.g. its core was offline): retry rather
            // than dropping the binding, dropping is the disconnect action's job.
            MTWirelessNetworkRuntime.connectMemberNow(server, frequency, member);
            return;
        }
        // A hatch that is already wired to a cable cannot be bound wirelessly. A binding that is just waiting for its
        // core to come online is not "wired", the player may switch or disconnect it.
        if (bound == null && MTWirelessNetworkRuntime.isWired(server, member)) return;
        MTWirelessNetworkRuntime.bindTarget(player, member, frequency, true);
    }

    /** Disconnect the row's network ({@code index >= 0}) or the current binding ({@code index < 0}). */
    private static void disconnect(PanelSyncManager syncManager, MTWirelessMemberKey member, PacketBuffer buffer) {
        int index = buffer.readInt();
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        EntityPlayer player = syncManager.getPlayer();
        if (server == null || player == null) return;

        if (index >= 0) {
            List<MTWirelessSavedData.NetworkInfo> networks = MTWirelessNetworkRuntime.getAccessibleNetworks(player);
            if (index >= networks.size()) return;
            UUID frequency = networks.get(index)
                .frequency();
            UUID bound = MTWirelessNetworkRuntime.getMemberNetwork(server, member);
            if (!frequency.equals(bound)) return;
        }
        MTWirelessNetworkRuntime.unbindTarget(player, member);
    }

    // ------------------------------------------------------------------ server side snapshot

    /**
     * Per panel snapshot of the server side state. MUI2 re-reads every {@code SyncHandler} each tick, so the snapshot
     * is only rebuilt once per server tick no matter how many sync values ask for it.
     */
    private static final class NetworkState {

        private final PanelSyncManager syncManager;
        private final MTWirelessMemberKey member;
        private final String[] names = new String[MAX_ROWS];
        private final int[] colors = new int[MAX_ROWS];
        private final boolean[] connected = new boolean[MAX_ROWS];
        private final boolean[] hasCore = new boolean[MAX_ROWS];

        private int cachedTick = Integer.MIN_VALUE;
        private String currentName = "";
        private int currentColor = MTWirelessColors.DEFAULT;
        private boolean wired;
        private int rowCount;
        private int total;

        NetworkState(PanelSyncManager syncManager, MTWirelessMemberKey member) {
            this.syncManager = syncManager;
            this.member = member;
            Arrays.fill(this.names, "");
            Arrays.fill(this.colors, MTWirelessColors.DEFAULT);
        }

        /** Server getter of the current network name; never called on the client. */
        String currentName() {
            refresh();
            return this.currentName;
        }

        /** Server getter of the current network's color; never called on the client. */
        int currentColor() {
            refresh();
            return this.currentColor;
        }

        /** Server getter of the "hatch is wired to a cable" lock state; never called on the client. */
        boolean wired() {
            refresh();
            return this.wired;
        }

        /** Server getter of the number of listed networks; never called on the client. */
        int rowCount() {
            refresh();
            return this.rowCount;
        }

        /** Server getter of the number of accessible networks; never called on the client. */
        int total() {
            refresh();
            return this.total;
        }

        /** Server getter of a listed network's name; never called on the client. */
        String rowName(int index) {
            refresh();
            return index >= 0 && index < MAX_ROWS ? this.names[index] : "";
        }

        /** Server getter of a listed network's color; never called on the client. */
        int rowColor(int index) {
            refresh();
            return index >= 0 && index < MAX_ROWS ? this.colors[index] : MTWirelessColors.DEFAULT;
        }

        /** Server getter of a listed network's connection state; never called on the client. */
        boolean rowConnected(int index) {
            refresh();
            return index >= 0 && index < MAX_ROWS && this.connected[index];
        }

        /** Server getter of "the listed network has a core" (a network without one can only wait); server only. */
        boolean rowHasCore(int index) {
            refresh();
            return index >= 0 && index < MAX_ROWS && this.hasCore[index];
        }

        private void refresh() {
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server == null) return;
            int tick = server.getTickCounter();
            if (tick == this.cachedTick) return;
            this.cachedTick = tick;

            EntityPlayer player = this.syncManager.getPlayer();
            if (player == null) {
                reset();
                return;
            }

            List<MTWirelessSavedData.NetworkInfo> networks = MTWirelessNetworkRuntime.getAccessibleNetworks(player);
            this.total = networks.size();
            this.rowCount = Math.min(MAX_ROWS, this.total);
            Arrays.fill(this.names, "");
            Arrays.fill(this.colors, MTWirelessColors.DEFAULT);
            Arrays.fill(this.connected, false);
            Arrays.fill(this.hasCore, false);
            for (int i = 0; i < this.rowCount; i++) {
                MTWirelessSavedData.NetworkInfo network = networks.get(i);
                this.names[i] = network.name();
                this.colors[i] = network.color();
                this.connected[i] = MTWirelessNetworkRuntime
                    .isMemberConnected(server, network.frequency(), this.member);
                this.hasCore[i] = network.hasCore();
            }

            UUID boundFrequency = MTWirelessNetworkRuntime.getMemberNetwork(server, this.member);
            MTWirelessSavedData data = MTWirelessSavedData.get(server);
            if (boundFrequency == null || data == null) {
                this.currentName = "";
                this.currentColor = MTWirelessColors.DEFAULT;
            } else {
                String name = data.getName(boundFrequency);
                this.currentName = name == null ? "" : name;
                this.currentColor = data.getColor(boundFrequency);
            }
            // A wireless connection created by the runtime itself also makes the grid node report connections. Only a
            // hatch that is not currently on a wireless network can be genuinely "wired" by a cable.
            this.wired = MTWirelessNetworkRuntime.findConnectedNetwork(server, this.member) == null
                && MTWirelessNetworkRuntime.isWired(server, this.member);
        }

        private void reset() {
            this.currentName = "";
            this.currentColor = MTWirelessColors.DEFAULT;
            this.wired = false;
            this.rowCount = 0;
            this.total = 0;
            Arrays.fill(this.names, "");
            Arrays.fill(this.colors, MTWirelessColors.DEFAULT);
            Arrays.fill(this.connected, false);
            Arrays.fill(this.hasCore, false);
        }
    }

    // ------------------------------------------------------------------ widgets

    /** 24x24 tab button: the Flux wireless-charging icon, 16x16 centered; toggles the network list popup. */
    private static final class WirelessTabButton extends Widget<WirelessTabButton> implements Interactable {

        private final IPanelHandler networkPanel;

        WirelessTabButton(IPanelHandler networkPanel) {
            this.networkPanel = networkPanel;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            float shade = this.networkPanel.isPanelOpen() ? 1f : isHovering() ? 0.85f : 0.7f;
            MTWirelessGuiStyle.tabIcon(MTWirelessGuiStyle.SPRITE_WIRELESS, shade)
                .draw(
                    context,
                    TAB_ICON_OFFSET,
                    TAB_ICON_OFFSET,
                    MTWirelessGuiStyle.TAB_SIZE,
                    MTWirelessGuiStyle.TAB_SIZE,
                    getActiveWidgetTheme(widgetTheme, isHovering()));
        }

        @Override
        public @NotNull Interactable.Result onMousePressed(int mouseButton) {
            if (mouseButton != 0 && mouseButton != 1) return Interactable.Result.IGNORE;
            Interactable.playButtonClickSound();
            if (this.networkPanel.isPanelOpen()) {
                this.networkPanel.closePanel();
            } else {
                this.networkPanel.openPanel();
            }
            return Interactable.Result.SUCCESS;
        }
    }

    /** 135x12 network name bar of the bound network (hidden while the hatch is not bound to any network). */
    private static final class CurrentNetworkBar extends ParentWidget<CurrentNetworkBar> {

        CurrentNetworkBar(StringSyncValue current, IntSyncValue color) {
            size(MTWirelessGuiStyle.NETWORK_BAR_WIDTH, MTWirelessGuiStyle.NETWORK_BAR_HEIGHT);
            left(CONTENT_X + (MTWirelessGuiStyle.CONTENT_SIZE - MTWirelessGuiStyle.NETWORK_BAR_WIDTH) / 2);
            top(CONTENT_Y + CURRENT_BAR_Y);
            background(currentBar(current, color));
            disableHoverThemeBackground(true);
            child(
                new TextWidget<>(IKey.dynamic(() -> currentLine(current)))
                    .size(MTWirelessGuiStyle.NETWORK_BAR_WIDTH - 8, 10)
                    .left(4)
                    .top(1)
                    .textAlign(Alignment.CenterLeft));
        }
    }

    /** One accessible network: left click connects, left click when connected disconnects. */
    private static final class NetworkRow extends ButtonWidget<NetworkRow> {

        NetworkRow(int index, StringSyncValue name, IntSyncValue color, BooleanSyncValue connected,
            BooleanSyncValue hasCore, BooleanSyncValue wired, IntSyncValue rowCount, PanelSyncManager syncManager) {
            size(ROW_WIDTH, ROW_HEIGHT);
            left(CONTENT_X + ROW_X);
            top(CONTENT_Y + ROW_Y + index * ROW_HEIGHT);
            background(tinted(color, MTWirelessGuiStyle::rowBar));
            disableHoverThemeBackground(true);
            paddingLeft(4);
            overlay(
                IKey.dynamic(() -> rowLine(name, connected, hasCore))
                    .color(
                        () -> connected.getBoolValue() ? MTWirelessGuiStyle.TEXT_COLOR
                            : MTWirelessGuiStyle.TEXT_COLOR_DIM)
                    .alignment(Alignment.CenterLeft));
            tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(() -> rowTooltipKey(wired, connected))));
            tooltipAutoUpdate(true);
            onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                if (wired.getBoolValue()) return true;
                syncManager.callSyncedAction(
                    connected.getBoolValue() ? ACTION_DISCONNECT : ACTION_CONNECT,
                    buffer -> buffer.writeInt(index));
                return true;
            });
            setEnabledIf(row -> index < rowCount.getIntValue());
            // Hide the row until the first sync arrives, so the panel does not flash 8 empty rows for one frame.
            setEnabled(index < rowCount.getIntValue());
        }
    }

    /** Flux's SimpleButton: a 1 px outline in the network color, dimmed while {@code available} is false. */
    private static final class HatchButton extends ButtonWidget<HatchButton> {

        private final IKey label;
        private final IntSupplier color;
        private final BooleanSupplier available;

        HatchButton(IKey label, IntSupplier color, BooleanSupplier available, IGuiAction.MousePressed action) {
            this.label = label;
            this.color = color;
            this.available = available;
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                if (!this.available.getAsBoolean()) return true;
                return action.press(mouseButton);
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            boolean enabled = this.available.getAsBoolean();
            int rgb = this.color.getAsInt();
            MTWirelessGuiStyle.buttonFrame(enabled ? rgb : MTWirelessGuiStyle.TEXT_COLOR_DIM)
                .drawAtZero(context, getArea(), getActiveWidgetTheme(widgetTheme, isHovering()));
            drawText(
                this.label.getFormatted(),
                0,
                0,
                getArea().width,
                getArea().height,
                enabled ? MTWirelessGuiStyle.argb(rgb) : MTWirelessGuiStyle.TEXT_COLOR_DIM,
                Alignment.Center);
        }
    }

    // ------------------------------------------------------------------ drawable helpers

    /**
     * The bound network's name bar; returns {@code null} (nothing drawn) while there is no current network. The tinted
     * texture is only rebuilt when the synced color changes.
     */
    private static IDrawable currentBar(StringSyncValue current, IntSyncValue color) {
        return new DynamicDrawable(new Supplier<IDrawable>() {

            private int cachedColor = Integer.MIN_VALUE;
            private IDrawable cachedDrawable;

            @Override
            public IDrawable get() {
                String name = current.getStringValue();
                if (name == null || name.isEmpty()) return null;
                int rgb = color.getIntValue();
                if (this.cachedDrawable == null || rgb != this.cachedColor) {
                    this.cachedColor = rgb;
                    this.cachedDrawable = MTWirelessGuiStyle.networkBar(rgb);
                }
                return this.cachedDrawable;
            }
        });
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
}
