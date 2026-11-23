package com.MessTech.common.wireless.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;

import com.MessTech.common.wireless.MTWirelessAccess;
import com.MessTech.common.wireless.MTWirelessColors;
import com.MessTech.common.wireless.MTWirelessMemberKey;
import com.MessTech.common.wireless.MTWirelessNetworkCoreTile;
import com.MessTech.common.wireless.MTWirelessNetworkRuntime;
import com.MessTech.common.wireless.MTWirelessSavedData;
import com.MessTech.init.MessTech;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTUtility;

/**
 * Flux Networks 1.20 styled GUI of {@link MTWirelessNetworkCoreTile} (see {@code docs/wireless-me-network.md} §9.4): a
 * 172x194 panel with a 172x172 rounded content area below a 16 px navigation tab row and the five pages
 *
 * <ul>
 * <li>HOME: current network bar (click = network selection page), ME access state, bound/connected hatch counters, the
 * network id, the access level and the unbind button,</li>
 * <li>SELECTION: up to {@value #MAX_ROWS} usable networks, each on a row bar tinted with its own color,</li>
 * <li>MEMBERS: up to {@value #MAX_ROWS} hatches bound to the network plus their connection state,</li>
 * <li>SETTINGS: name input + save, the 14 color palette, the access level cycle button and the two click delete,</li>
 * <li>CREATE: name input (default "&lt;player&gt;'s Network"), color palette, access level and the create button.</li>
 * </ul>
 *
 * <p>
 * MUI2 builds the panel on the server and on the client (see §2), so every server owned value reaches the client
 * through {@link com.cleanroommc.modularui.value.sync.SyncHandler}s whose four argument form is
 * {@code (clientGetter, clientSetter, serverGetter, serverSetter)}: the server getters read the tile and the
 * {@code WorldSavedData} through {@link CoreState}, the client getters read the cache the sync packets fill. The rows
 * are a fixed {@value #MAX_ROWS} widgets (a row beyond the synced row count is simply disabled), so the widget tree is
 * identical on both sides.
 *
 * <p>
 * No page state is synced: switching pages is a {@link PagedWidget.Controller} call on the client only. All server
 * actions ({@code registerSyncedAction(Side.SERVER, ...)}) run through {@link ServerThreadUtil#addScheduledTask}
 * because
 * the {@code WorldSavedData} and the tile entities belong to the server thread, and they only receive a row index, a
 * color or a string which they re-validate against the current data.
 */
public final class MTWirelessCoreGui {

    private static final String PANEL_NAME = "messtech:wireless_network_core";

    /** Rows shown by the selection and members pages; the rest is summarised with {@code wirelessme.gui.more}. */
    private static final int MAX_ROWS = 8;

    private static final int CONTENT_SIZE = MTWirelessGuiStyle.CONTENT_SIZE;
    private static final int ROW_WIDTH = MTWirelessGuiStyle.ROW_BAR_WIDTH;
    private static final int ROW_HEIGHT = MTWirelessGuiStyle.ROW_BAR_HEIGHT;
    private static final int ROW_SPACING = 14;
    private static final int ROW_X = (CONTENT_SIZE - ROW_WIDTH) / 2;
    private static final int TEXT_X = 16;
    private static final int BAR_X = 20;
    private static final int BAR_Y = 8;
    private static final int SELECTION_FIRST_ROW = 26;
    private static final int MEMBERS_FIRST_ROW = 36;
    private static final int SWATCHES_PER_ROW = 7;
    private static final int SWATCH_SPACING = 12;
    private static final int SWATCHES_X = 45;

    // ------------------------------------------------------------------ language keys (docs/wireless-me-network.md
    // §9.5)

    private static final String KEY_TAB_HOME = "wirelessme.gui.tab.home";
    private static final String KEY_TAB_SELECTION = "wirelessme.gui.tab.selection";
    private static final String KEY_TAB_MEMBERS = "wirelessme.gui.tab.members";
    private static final String KEY_TAB_SETTINGS = "wirelessme.gui.tab.settings";
    private static final String KEY_TAB_CREATE = "wirelessme.gui.tab.create";
    private static final String[] TAB_KEYS = { KEY_TAB_HOME, KEY_TAB_SELECTION, KEY_TAB_MEMBERS, KEY_TAB_SETTINGS,
        KEY_TAB_CREATE };

    private static final String KEY_CURRENT_NONE = "wirelessme.gui.current.none";
    private static final String KEY_MORE = "wirelessme.gui.more";
    private static final String KEY_CLOSE = "wirelessme.gui.close";

    private static final String KEY_HOME_TITLE = "wirelessme.gui.home.title";
    private static final String KEY_HOME_UNBOUND = "wirelessme.gui.home.unbound";
    private static final String KEY_HOME_UNBOUND_HINT = "wirelessme.gui.home.unbound.hint";
    private static final String KEY_HOME_LINKED = "wirelessme.gui.home.linked";
    private static final String KEY_HOME_UNLINKED = "wirelessme.gui.home.unlinked";
    private static final String KEY_HOME_MEMBERS = "wirelessme.gui.home.members";
    private static final String KEY_HOME_CONNECTED = "wirelessme.gui.home.connected";
    private static final String KEY_HOME_NETWORK_ID = "wirelessme.gui.home.network_id";
    private static final String KEY_HOME_ACCESS = "wirelessme.gui.home.access";
    private static final String KEY_HOME_DETACH = "wirelessme.gui.home.detach";

    private static final String KEY_SELECTION_EMPTY = "wirelessme.gui.selection.empty";
    private static final String KEY_SELECTION_EMPTY_HINT = "wirelessme.gui.selection.empty_hint";
    private static final String KEY_SELECTION_CURRENT = "wirelessme.gui.selection.current";
    private static final String KEY_SELECTION_CORE_IN_USE = "wirelessme.gui.selection.core_in_use";

    private static final String KEY_MEMBERS_TITLE = "wirelessme.gui.members.title";
    private static final String KEY_MEMBERS_TOTAL = "wirelessme.gui.members.total";
    private static final String KEY_MEMBERS_ENTRY = "wirelessme.gui.members.entry";
    private static final String KEY_MEMBERS_CONNECTED = "wirelessme.gui.members.connected";
    private static final String KEY_MEMBERS_PENDING = "wirelessme.gui.members.pending";
    private static final String KEY_MEMBERS_EMPTY = "wirelessme.gui.members.empty";

    private static final String KEY_SETTINGS_TITLE = "wirelessme.gui.settings.title";
    private static final String KEY_SETTINGS_NAME = "wirelessme.gui.settings.name";
    private static final String KEY_SETTINGS_SAVE = "wirelessme.gui.settings.save";
    private static final String KEY_SETTINGS_COLOR = "wirelessme.gui.settings.color";
    private static final String KEY_SETTINGS_ACCESS = "wirelessme.gui.settings.access";
    private static final String KEY_SETTINGS_DELETE = "wirelessme.gui.settings.delete";
    private static final String KEY_SETTINGS_DELETE_CONFIRM = "wirelessme.gui.settings.delete_confirm";
    private static final String KEY_SETTINGS_READONLY = "wirelessme.gui.settings.readonly";

    private static final String KEY_CREATE_TITLE = "wirelessme.gui.create.title";
    private static final String KEY_CREATE_NAME = "wirelessme.gui.create.name";
    private static final String KEY_CREATE_DEFAULT_NAME = "wirelessme.gui.create.default_name";
    private static final String KEY_CREATE_COLOR = "wirelessme.gui.create.color";
    private static final String KEY_CREATE_ACCESS = "wirelessme.gui.create.access";
    private static final String KEY_CREATE_BUTTON = "wirelessme.gui.create.button";
    private static final String KEY_CREATE_LIMIT = "wirelessme.gui.create.limit";

    private static final String KEY_CHAT_NETWORK_CREATED = "wirelessme.chat.network_created";
    private static final String KEY_CHAT_NETWORK_DELETED = "wirelessme.chat.network_deleted";
    private static final String KEY_CHAT_NETWORK_LIMIT = "wirelessme.chat.network_limit";
    private static final String KEY_CHAT_NETWORK_SELECTED = "wirelessme.chat.network_selected";
    private static final String KEY_CHAT_NETWORK_UNSELECTED = "wirelessme.chat.network_unselected";
    private static final String KEY_CHAT_NETWORK_MISSING = "wirelessme.chat.network_missing";
    private static final String KEY_CHAT_CORE_IN_USE = "wirelessme.chat.core_in_use";
    private static final String KEY_CHAT_ONLY_OWNER = "wirelessme.chat.only_owner";
    private static final String KEY_CHAT_NAME_EMPTY = "wirelessme.chat.name_empty";
    private static final String KEY_CHAT_NAME_SAVED = "wirelessme.chat.name_saved";
    private static final String KEY_CHAT_NO_ACCESS = "wirelessme.chat.no_access";

    // ------------------------------------------------------------------ sync keys (both sides register exactly these)

    private static final String SYNC_BOUND = "bound";
    private static final String SYNC_NAME = "networkName";
    private static final String SYNC_SHORT_ID = "shortNetworkId";
    private static final String SYNC_RAW_NAME = "rawNetworkName";
    private static final String SYNC_COLOR = "networkColor";
    private static final String SYNC_ACCESS = "networkAccess";
    private static final String SYNC_LINKED = "linkedToAeNetwork";
    private static final String SYNC_MEMBERS = "memberCount";
    private static final String SYNC_CONNECTED = "connectedCount";
    private static final String SYNC_EDITABLE = "editable";
    private static final String SYNC_ACCESSIBLE_TOTAL = "accessibleTotal";
    private static final String SYNC_LIMIT = "networkLimit";
    private static final String SYNC_CAN_CREATE = "canCreateNetwork";
    private static final String SYNC_SELECTION_ROWS = "selectionRows";
    private static final String SYNC_MEMBER_ROWS = "memberRows";
    private static final String SYNC_SELECTION_NAME = "selectionName";
    private static final String SYNC_SELECTION_COLOR = "selectionColor";
    private static final String SYNC_SELECTION_ACCESS = "selectionAccess";
    private static final String SYNC_SELECTION_CURRENT = "selectionCurrent";
    private static final String SYNC_SELECTION_CORE_IN_USE = "selectionCoreInUse";
    private static final String SYNC_MEMBER_DIM = "memberDim";
    private static final String SYNC_MEMBER_X = "memberX";
    private static final String SYNC_MEMBER_Y = "memberY";
    private static final String SYNC_MEMBER_Z = "memberZ";
    private static final String SYNC_MEMBER_CONNECTED = "memberConnected";

    // ------------------------------------------------------------------ server actions

    private static final String ACTION_SELECT = "selectNetwork";
    private static final String ACTION_DETACH = "detachNetwork";
    private static final String ACTION_SET_NAME = "setNetworkName";
    private static final String ACTION_SET_COLOR = "setNetworkColor";
    private static final String ACTION_CYCLE_ACCESS = "cycleNetworkAccess";
    private static final String ACTION_DELETE = "deleteNetwork";
    private static final String ACTION_CREATE = "createNetwork";

    private MTWirelessCoreGui() {}

    /** {@code MTWirelessNetworkCoreTile#buildUI} delegates here. */
    public static ModularPanel build(MTWirelessNetworkCoreTile tile, PosGuiData data, PanelSyncManager syncManager,
        UISettings settings) {
        return new Ui(tile, data.getPlayer(), syncManager).panel;
    }

    @SideOnly(Side.CLIENT)
    public static ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        // Flux has no screen wide dimming: the rounded panel is the only thing drawn over the world.
        return new ModularScreen(MessTech.MODID, mainPanel).drawDarkBackground(false);
    }

    // ------------------------------------------------------------------ server thread helpers

    /**
     * MUI2 invokes server actions from the network thread; the {@code WorldSavedData} and the tile entities belong to
     * the server thread. GTNHLib's helper runs the task right away when we are already on it.
     */
    private static void runOnServerThread(Runnable task) {
        try {
            ServerThreadUtil.addScheduledTask(task);
        } catch (IllegalStateException e) {
            // The server is shutting down, so there is no state left to update.
        }
    }

    private static MTWirelessSavedData savedData() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? null : MTWirelessSavedData.get(server);
    }

    // ------------------------------------------------------------------ display helpers

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private static String accessName(int accessId) {
        return translate(
            MTWirelessAccess.byId(accessId)
                .langKey());
    }

    private static int visibleRows(int total) {
        return Math.min(Math.max(total, 0), MAX_ROWS);
    }

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

    /** A text line widget positioned in content-local coordinates. */
    private static TextWidget<?> text(IKey key, int x, int y, int color) {
        TextWidget<?> widget = key.asWidget();
        widget.left(x);
        widget.top(y);
        widget.color(color);
        return widget;
    }

    /** A wrapped, scaled text widget positioned in content-local coordinates. */
    private static TextWidget<?> wrappedText(IKey key, int x, int y, int width, float scale, int color) {
        TextWidget<?> widget = key.asWidget();
        widget.left(x);
        widget.top(y);
        widget.width(width);
        widget.scale(scale);
        widget.color(color);
        return widget;
    }

    /** Hides a widget until {@code visible} turns true; the widget keeps its slot in the tree on both sides. */
    private static Widget<?> visible(Widget<?> widget, BooleanSupplier visible) {
        widget.setEnabled(visible.getAsBoolean());
        widget.setEnabledIf(w -> visible.getAsBoolean());
        return widget;
    }

    // ------------------------------------------------------------------ pages host

    private static final class Pages extends PagedWidget<Pages> {

        private Pages() {
            size(CONTENT_SIZE, CONTENT_SIZE);
        }
    }

    private static final class Page extends ParentWidget<Page> {

        private Page() {
            size(CONTENT_SIZE, CONTENT_SIZE);
        }
    }

    // ------------------------------------------------------------------ widgets

    /** One navigation tab; the icon is shaded by selection/availability and a left click switches the page. */
    private static final class TabButton extends ButtonWidget<TabButton> {

        private final PagedWidget.Controller controller;
        private final int tab;
        private final BooleanSupplier available;

        private TabButton(PagedWidget.Controller controller, int tab, BooleanSupplier available) {
            this.controller = controller;
            this.tab = tab;
            this.available = available;
            size(MTWirelessGuiStyle.TAB_SIZE, MTWirelessGuiStyle.TAB_SIZE);
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                if (!this.available.getAsBoolean()) return true;
                this.controller.setPage(this.tab);
                return true;
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            boolean selected = this.controller.isInitialised() && this.controller.getActivePageIndex() == this.tab;
            float shade = !this.available.getAsBoolean() ? 0.5f : (selected || isHovering() ? 1f : 0.7f);
            MTWirelessGuiStyle.tabIcon(MTWirelessGuiStyle.tabSprite(this.tab), shade)
                .drawAtZero(context, getArea(), getActiveWidgetTheme(widgetTheme, isHovering()));
        }
    }

    /** The rounded network name bar (135x12) tinted with the network color; clicking it jumps to a page. */
    private static final class BarButton extends ButtonWidget<BarButton> {

        private final IKey label;
        private final IntSupplier color;

        private BarButton(IKey label, IntSupplier color, IGuiAction.MousePressed action) {
            this.label = label;
            this.color = color;
            size(MTWirelessGuiStyle.NETWORK_BAR_WIDTH, MTWirelessGuiStyle.NETWORK_BAR_HEIGHT);
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            onMousePressed(action);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            MTWirelessGuiStyle.networkBar(this.color.getAsInt())
                .drawAtZero(context, getArea(), getActiveWidgetTheme(widgetTheme, isHovering()));
            drawText(
                this.label.getFormatted(),
                4,
                0,
                getArea().width - 8,
                getArea().height,
                MTWirelessGuiStyle.TEXT_COLOR,
                Alignment.CenterLeft);
        }
    }

    /** A 1 px outline button in the network color, dimmed while {@code available} is false (Flux's SimpleButton). */
    private static final class FluxButton extends ButtonWidget<FluxButton> {

        private final IKey label;
        private final IntSupplier color;
        private final BooleanSupplier available;

        private FluxButton(IKey label, IntSupplier color, BooleanSupplier available, IGuiAction.MousePressed action) {
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

    /** A palette swatch (10x10) which draws Flux's white selection border while it is the active color. */
    private static final class SwatchButton extends ButtonWidget<SwatchButton> {

        private final int rgb;
        private final IntSupplier selected;

        private SwatchButton(int rgb, IntSupplier selected, IGuiAction.MousePressed action) {
            this.rgb = rgb;
            this.selected = selected;
            size(MTWirelessGuiStyle.SWATCH_SIZE, MTWirelessGuiStyle.SWATCH_SIZE);
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            background(
                new DynamicDrawable(
                    () -> this.selected.getAsInt() == this.rgb ? MTWirelessGuiStyle.paletteSwatchSelected(this.rgb)
                        : MTWirelessGuiStyle.paletteSwatch(this.rgb)));
            onMousePressed(action);
        }
    }

    /** One network of the selection page: color bar + name + current/core-in-use markers + lock icon. */
    private static final class NetworkRow extends ButtonWidget<NetworkRow> {

        private final int row;
        private final StringSyncValue name;
        private final IntSyncValue color;
        private final IntSyncValue access;
        private final BooleanSyncValue current;
        private final BooleanSyncValue coreInUse;
        private final IntSyncValue rowCount;
        private final PanelSyncManager syncManager;

        private NetworkRow(int row, StringSyncValue name, IntSyncValue color, IntSyncValue access,
            BooleanSyncValue current, BooleanSyncValue coreInUse, IntSyncValue rowCount, PanelSyncManager syncManager) {
            this.row = row;
            this.name = name;
            this.color = color;
            this.access = access;
            this.current = current;
            this.coreInUse = coreInUse;
            this.rowCount = rowCount;
            this.syncManager = syncManager;
            size(ROW_WIDTH, ROW_HEIGHT);
            disableThemeBackground(true);
            disableHoverThemeBackground(true);
            setEnabled(row < visibleRows(rowCount.getIntValue()));
            setEnabledIf(widget -> this.row < visibleRows(this.rowCount.getIntValue()));
            onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                if (this.row >= visibleRows(this.rowCount.getIntValue())) return true;
                this.syncManager.callSyncedAction(ACTION_SELECT, buffer -> buffer.writeInt(this.row));
                return true;
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            String networkName = this.name.getStringValue();
            if (networkName == null || networkName.isEmpty()) return;

            WidgetTheme theme = getActiveWidgetTheme(widgetTheme, isHovering());
            MTWirelessGuiStyle.rowBar(this.color.getIntValue())
                .drawAtZero(context, getArea(), theme);
            boolean selected = this.current.getBoolValue();
            StringBuilder text = new StringBuilder(networkName);
            if (selected) {
                text.append("  ")
                    .append(translate(KEY_SELECTION_CURRENT));
            } else if (this.coreInUse.getBoolValue()) {
                text.append("  ")
                    .append(translate(KEY_SELECTION_CORE_IN_USE));
            }
            drawText(
                text.toString(),
                6,
                0,
                getArea().width - 20,
                getArea().height,
                selected ? MTWirelessGuiStyle.TEXT_COLOR : MTWirelessGuiStyle.TEXT_COLOR_DIM,
                Alignment.CenterLeft);
            if (this.coreInUse.getBoolValue() || this.access.getIntValue() != MTWirelessAccess.PUBLIC.id()) {
                MTWirelessGuiStyle.lockIcon()
                    .draw(
                        context,
                        getArea().width - 4 - MTWirelessGuiStyle.LOCK_SIZE,
                        (getArea().height - MTWirelessGuiStyle.LOCK_SIZE) / 2,
                        MTWirelessGuiStyle.LOCK_SIZE,
                        MTWirelessGuiStyle.LOCK_SIZE,
                        theme);
            }
        }
    }

    /** One hatch bound to the network: color bar + "Dim x: x, y, z" + connected/pending state. */
    private static final class MemberRow extends Widget<MemberRow> {

        private final int row;
        private final IntSyncValue dim;
        private final IntSyncValue x;
        private final IntSyncValue y;
        private final IntSyncValue z;
        private final IntSyncValue color;
        private final BooleanSyncValue connected;
        private final IntSyncValue rowCount;

        private MemberRow(int row, IntSyncValue dim, IntSyncValue x, IntSyncValue y, IntSyncValue z, IntSyncValue color,
            BooleanSyncValue connected, IntSyncValue rowCount) {
            this.row = row;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.connected = connected;
            this.rowCount = rowCount;
            size(ROW_WIDTH, ROW_HEIGHT);
            setEnabled(row < visibleRows(rowCount.getIntValue()));
            setEnabledIf(widget -> this.row < visibleRows(this.rowCount.getIntValue()));
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            WidgetTheme theme = getActiveWidgetTheme(widgetTheme, isHovering());
            MTWirelessGuiStyle.rowBar(this.color.getIntValue())
                .drawAtZero(context, getArea(), theme);
            String entry = translate(
                KEY_MEMBERS_ENTRY,
                this.dim.getIntValue(),
                this.x.getIntValue(),
                this.y.getIntValue(),
                this.z.getIntValue());
            String state = this.connected.getBoolValue() ? translate(KEY_MEMBERS_CONNECTED)
                : translate(KEY_MEMBERS_PENDING);
            drawText(
                entry + "  " + state,
                6,
                0,
                getArea().width - 12,
                getArea().height,
                MTWirelessGuiStyle.TEXT_COLOR,
                Alignment.CenterLeft);
        }
    }

    // ------------------------------------------------------------------ server side snapshot

    /**
     * Per panel snapshot of everything the sync values expose. MUI2 re-reads every {@code SyncHandler} each tick, so
     * the
     * snapshot is rebuilt at most once per server tick; on the client the same fields are the cache the sync packets
     * write (the server getters are never called there).
     */
    private static final class CoreState {

        private final MTWirelessNetworkCoreTile core;
        private final EntityPlayer player;

        private int cachedTick = Integer.MIN_VALUE;

        private boolean bound;
        private boolean linked;
        private boolean editable;
        private boolean canCreate;
        private String name = "";
        private String shortId = "";
        private String rawName = "";
        private int color = MTWirelessColors.DEFAULT;
        private int accessId = MTWirelessAccess.PRIVATE.id();
        private int memberCount;
        private int connectedCount;
        private int accessibleTotal;
        private int limit;

        private final String[] selectionNames = new String[MAX_ROWS];
        private final int[] selectionColors = new int[MAX_ROWS];
        private final int[] selectionAccess = new int[MAX_ROWS];
        private final boolean[] selectionCurrent = new boolean[MAX_ROWS];
        private final boolean[] selectionCoreInUse = new boolean[MAX_ROWS];
        private final int[] memberDims = new int[MAX_ROWS];
        private final int[] memberXs = new int[MAX_ROWS];
        private final int[] memberYs = new int[MAX_ROWS];
        private final int[] memberZs = new int[MAX_ROWS];
        private final boolean[] memberConnected = new boolean[MAX_ROWS];

        private CoreState(MTWirelessNetworkCoreTile core, EntityPlayer player) {
            this.core = core;
            this.player = player;
            for (int row = 0; row < MAX_ROWS; row++) {
                this.selectionNames[row] = "";
                this.selectionColors[row] = MTWirelessColors.DEFAULT;
                this.selectionAccess[row] = MTWirelessAccess.PRIVATE.id();
            }
        }

        // ---------------------------------------------------------- server getters

        private boolean isBound() {
            refresh();
            return this.bound;
        }

        private String networkName() {
            refresh();
            return this.name;
        }

        private String shortNetworkId() {
            refresh();
            return this.shortId;
        }

        private String rawNetworkName() {
            refresh();
            return this.rawName;
        }

        private int networkColor() {
            refresh();
            return this.color;
        }

        private int networkAccess() {
            refresh();
            return this.accessId;
        }

        private boolean linked() {
            refresh();
            return this.linked;
        }

        private int memberCount() {
            refresh();
            return this.memberCount;
        }

        private int connectedCount() {
            refresh();
            return this.connectedCount;
        }

        private boolean editable() {
            refresh();
            return this.editable;
        }

        private int accessibleTotal() {
            refresh();
            return this.accessibleTotal;
        }

        private int limit() {
            refresh();
            return this.limit;
        }

        private boolean canCreate() {
            refresh();
            return this.canCreate;
        }

        private int selectionRows() {
            refresh();
            return Math.min(this.accessibleTotal, MAX_ROWS);
        }

        private int memberRows() {
            refresh();
            return Math.min(this.memberCount, MAX_ROWS);
        }

        private String selectionName(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.selectionNames[row] : "";
        }

        private int selectionColor(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.selectionColors[row] : MTWirelessColors.DEFAULT;
        }

        private int selectionAccess(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.selectionAccess[row] : MTWirelessAccess.PRIVATE.id();
        }

        private boolean selectionCurrent(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS && this.selectionCurrent[row];
        }

        private boolean selectionCoreInUse(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS && this.selectionCoreInUse[row];
        }

        private int memberDim(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.memberDims[row] : 0;
        }

        private int memberX(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.memberXs[row] : 0;
        }

        private int memberY(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.memberYs[row] : 0;
        }

        private int memberZ(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS ? this.memberZs[row] : 0;
        }

        private boolean memberConnected(int row) {
            refresh();
            return row >= 0 && row < MAX_ROWS && this.memberConnected[row];
        }

        // ---------------------------------------------------------- snapshot

        private void refresh() {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return;
            int tick = server.getTickCounter();
            if (tick == this.cachedTick) return;
            this.cachedTick = tick;

            UUID network = this.core.getNetworkId();
            this.bound = network != null;
            this.name = this.core.getNetworkName();
            this.shortId = this.core.getShortNetworkId();
            this.color = this.core.getNetworkColor();
            this.accessId = this.core.getNetworkAccess()
                .id();
            this.linked = this.core.isLinkedToAeNetwork();
            this.limit = MTWirelessNetworkRuntime.getNetworkLimit(this.player);
            this.canCreate = MTWirelessNetworkRuntime.canCreateNetwork(this.player);
            this.editable = network != null && MTWirelessNetworkRuntime.canEdit(this.player, network);

            MTWirelessSavedData data = savedData();

            // The network the core is bound to: name/color/access and the bound hatches. An unbound core simply has no
            // member block, but the selection list below is still filled so a network can be picked.
            if (data != null && network != null) {
                this.rawName = data.getRawName(network);

                List<MTWirelessMemberKey> members = data.getMembers(network);
                this.memberCount = members.size();
                int connected = 0;
                for (int index = 0; index < members.size(); index++) {
                    MTWirelessMemberKey member = members.get(index);
                    boolean isConnected = MTWirelessNetworkRuntime.isMemberConnected(server, network, member);
                    if (isConnected) connected++;
                    if (index < MAX_ROWS) {
                        this.memberDims[index] = member.dim;
                        this.memberXs[index] = member.x;
                        this.memberYs[index] = member.y;
                        this.memberZs[index] = member.z;
                        this.memberConnected[index] = isConnected;
                    }
                }
                for (int index = this.memberCount; index < MAX_ROWS; index++) {
                    this.memberDims[index] = 0;
                    this.memberXs[index] = 0;
                    this.memberYs[index] = 0;
                    this.memberZs[index] = 0;
                    this.memberConnected[index] = false;
                }
                this.connectedCount = connected;
            } else {
                this.rawName = "";
                this.memberCount = 0;
                this.connectedCount = 0;
                clearMemberRows();
            }

            List<MTWirelessSavedData.NetworkInfo> networks = MTWirelessNetworkRuntime
                .getAccessibleNetworks(this.player);
            this.accessibleTotal = networks.size();
            for (int index = 0; index < MAX_ROWS; index++) {
                if (index < networks.size()) {
                    MTWirelessSavedData.NetworkInfo info = networks.get(index);
                    boolean current = network != null && network.equals(info.frequency());
                    this.selectionNames[index] = info.name();
                    this.selectionColors[index] = info.color();
                    this.selectionAccess[index] = info.access()
                        .id();
                    this.selectionCurrent[index] = current;
                    this.selectionCoreInUse[index] = info.hasCore() && !current;
                } else {
                    this.selectionNames[index] = "";
                    this.selectionColors[index] = MTWirelessColors.DEFAULT;
                    this.selectionAccess[index] = MTWirelessAccess.PRIVATE.id();
                    this.selectionCurrent[index] = false;
                    this.selectionCoreInUse[index] = false;
                }
            }
        }

        private void clearMemberRows() {
            for (int index = 0; index < MAX_ROWS; index++) {
                this.memberDims[index] = 0;
                this.memberXs[index] = 0;
                this.memberYs[index] = 0;
                this.memberZs[index] = 0;
                this.memberConnected[index] = false;
            }
        }
    }

    // ------------------------------------------------------------------ panel and sync/actions wiring

    /** The widget tree plus the sync values and server actions of one open GUI. */
    private static final class Ui {

        private final MTWirelessNetworkCoreTile core;
        private final EntityPlayer player;
        private final PanelSyncManager syncManager;
        private final CoreState state;
        private final PagedWidget.Controller controller;

        private final ModularPanel panel;

        private final BooleanSyncValue boundSync;
        private final StringSyncValue nameSync;
        private final StringSyncValue shortIdSync;
        private final StringSyncValue rawNameSync;
        private final IntSyncValue colorSync;
        private final IntSyncValue accessSync;
        private final BooleanSyncValue linkedSync;
        private final IntSyncValue memberCountSync;
        private final IntSyncValue connectedCountSync;
        private final BooleanSyncValue editableSync;
        private final IntSyncValue accessibleTotalSync;
        private final IntSyncValue limitSync;
        private final BooleanSyncValue canCreateSync;
        private final IntSyncValue selectionRowCountSync;
        private final IntSyncValue memberRowCountSync;

        private final StringSyncValue[] selectionNames = new StringSyncValue[MAX_ROWS];
        private final IntSyncValue[] selectionColors = new IntSyncValue[MAX_ROWS];
        private final IntSyncValue[] selectionAccess = new IntSyncValue[MAX_ROWS];
        private final BooleanSyncValue[] selectionCurrent = new BooleanSyncValue[MAX_ROWS];
        private final BooleanSyncValue[] selectionCoreInUse = new BooleanSyncValue[MAX_ROWS];
        private final IntSyncValue[] memberDims = new IntSyncValue[MAX_ROWS];
        private final IntSyncValue[] memberXs = new IntSyncValue[MAX_ROWS];
        private final IntSyncValue[] memberYs = new IntSyncValue[MAX_ROWS];
        private final IntSyncValue[] memberZs = new IntSyncValue[MAX_ROWS];
        private final BooleanSyncValue[] memberConnected = new BooleanSyncValue[MAX_ROWS];

        /** Client side state of the create page, sent to the server with the create action. */
        private final int[] createColor = { MTWirelessColors.PALETTE[0] };
        private final int[] createAccess = { MTWirelessAccess.PRIVATE.id() };
        /** Whether the settings page's delete button already asked for the confirmation click. */
        private boolean deleteArmed;

        private Ui(MTWirelessNetworkCoreTile core, EntityPlayer player, PanelSyncManager syncManager) {
            this.core = core;
            this.player = player;
            this.syncManager = syncManager;
            this.state = new CoreState(core, player);
            this.controller = new PagedWidget.Controller();

            this.boundSync = new BooleanSyncValue(
                () -> state.bound,
                value -> state.bound = value,
                state::isBound,
                null);
            this.nameSync = new StringSyncValue(
                () -> state.name,
                value -> state.name = value,
                state::networkName,
                null);
            this.shortIdSync = new StringSyncValue(
                () -> state.shortId,
                value -> state.shortId = value,
                state::shortNetworkId,
                null);
            this.rawNameSync = new StringSyncValue(
                () -> state.rawName,
                value -> state.rawName = value,
                state::rawNetworkName,
                null);
            this.colorSync = new IntSyncValue(
                () -> state.color,
                value -> state.color = value,
                state::networkColor,
                null);
            this.accessSync = new IntSyncValue(
                () -> state.accessId,
                value -> state.accessId = value,
                state::networkAccess,
                null);
            this.linkedSync = new BooleanSyncValue(
                () -> state.linked,
                value -> state.linked = value,
                state::linked,
                null);
            this.memberCountSync = new IntSyncValue(
                () -> state.memberCount,
                value -> state.memberCount = value,
                state::memberCount,
                null);
            this.connectedCountSync = new IntSyncValue(
                () -> state.connectedCount,
                value -> state.connectedCount = value,
                state::connectedCount,
                null);
            this.editableSync = new BooleanSyncValue(
                () -> state.editable,
                value -> state.editable = value,
                state::editable,
                null);
            this.accessibleTotalSync = new IntSyncValue(
                () -> state.accessibleTotal,
                value -> state.accessibleTotal = value,
                state::accessibleTotal,
                null);
            this.limitSync = new IntSyncValue(() -> state.limit, value -> state.limit = value, state::limit, null);
            this.canCreateSync = new BooleanSyncValue(
                () -> state.canCreate,
                value -> state.canCreate = value,
                state::canCreate,
                null);
            this.selectionRowCountSync = new IntSyncValue(
                () -> Math.min(state.accessibleTotal, MAX_ROWS),
                value -> {},
                state::selectionRows,
                null);
            this.memberRowCountSync = new IntSyncValue(
                () -> Math.min(state.memberCount, MAX_ROWS),
                value -> {},
                state::memberRows,
                null);

            for (int row = 0; row < MAX_ROWS; row++) {
                final int index = row;
                this.selectionNames[row] = new StringSyncValue(
                    () -> state.selectionNames[index],
                    value -> state.selectionNames[index] = value,
                    () -> state.selectionName(index),
                    null);
                this.selectionColors[row] = new IntSyncValue(
                    () -> state.selectionColors[index],
                    value -> state.selectionColors[index] = value,
                    () -> state.selectionColor(index),
                    null);
                this.selectionAccess[row] = new IntSyncValue(
                    () -> state.selectionAccess[index],
                    value -> state.selectionAccess[index] = value,
                    () -> state.selectionAccess(index),
                    null);
                this.selectionCurrent[row] = new BooleanSyncValue(
                    () -> state.selectionCurrent[index],
                    value -> state.selectionCurrent[index] = value,
                    () -> state.selectionCurrent(index),
                    null);
                this.selectionCoreInUse[row] = new BooleanSyncValue(
                    () -> state.selectionCoreInUse[index],
                    value -> state.selectionCoreInUse[index] = value,
                    () -> state.selectionCoreInUse(index),
                    null);
                this.memberDims[row] = new IntSyncValue(
                    () -> state.memberDims[index],
                    value -> state.memberDims[index] = value,
                    () -> state.memberDim(index),
                    null);
                this.memberXs[row] = new IntSyncValue(
                    () -> state.memberXs[index],
                    value -> state.memberXs[index] = value,
                    () -> state.memberX(index),
                    null);
                this.memberYs[row] = new IntSyncValue(
                    () -> state.memberYs[index],
                    value -> state.memberYs[index] = value,
                    () -> state.memberY(index),
                    null);
                this.memberZs[row] = new IntSyncValue(
                    () -> state.memberZs[index],
                    value -> state.memberZs[index] = value,
                    () -> state.memberZ(index),
                    null);
                this.memberConnected[row] = new BooleanSyncValue(
                    () -> state.memberConnected[index],
                    value -> state.memberConnected[index] = value,
                    () -> state.memberConnected(index),
                    null);
            }

            registerSyncValues();
            registerActions();
            this.panel = createPanel();

            // A network can be deleted (or its core unbound) while a page that requires a binding is open; fall back to
            // the home page so the player does not stare at a stale page.
            syncManager.onClientTick(new Runnable() {

                @Override
                public void run() {
                    if (!controller.isInitialised() || boundSync.getBoolValue()) return;
                    int page = controller.getActivePageIndex();
                    if (page == MTWirelessGuiStyle.TAB_MEMBERS || page == MTWirelessGuiStyle.TAB_SETTINGS) {
                        controller.setPage(MTWirelessGuiStyle.TAB_HOME);
                    }
                }
            });
        }

        // ---------------------------------------------------------- sync values and actions

        private void registerSyncValues() {
            this.syncManager.syncValue(SYNC_BOUND, this.boundSync);
            this.syncManager.syncValue(SYNC_NAME, this.nameSync);
            this.syncManager.syncValue(SYNC_SHORT_ID, this.shortIdSync);
            this.syncManager.syncValue(SYNC_RAW_NAME, this.rawNameSync);
            this.syncManager.syncValue(SYNC_COLOR, this.colorSync);
            this.syncManager.syncValue(SYNC_ACCESS, this.accessSync);
            this.syncManager.syncValue(SYNC_LINKED, this.linkedSync);
            this.syncManager.syncValue(SYNC_MEMBERS, this.memberCountSync);
            this.syncManager.syncValue(SYNC_CONNECTED, this.connectedCountSync);
            this.syncManager.syncValue(SYNC_EDITABLE, this.editableSync);
            this.syncManager.syncValue(SYNC_ACCESSIBLE_TOTAL, this.accessibleTotalSync);
            this.syncManager.syncValue(SYNC_LIMIT, this.limitSync);
            this.syncManager.syncValue(SYNC_CAN_CREATE, this.canCreateSync);
            this.syncManager.syncValue(SYNC_SELECTION_ROWS, this.selectionRowCountSync);
            this.syncManager.syncValue(SYNC_MEMBER_ROWS, this.memberRowCountSync);
            for (int row = 0; row < MAX_ROWS; row++) {
                this.syncManager.syncValue(SYNC_SELECTION_NAME + "_" + row, this.selectionNames[row]);
                this.syncManager.syncValue(SYNC_SELECTION_COLOR + "_" + row, this.selectionColors[row]);
                this.syncManager.syncValue(SYNC_SELECTION_ACCESS + "_" + row, this.selectionAccess[row]);
                this.syncManager.syncValue(SYNC_SELECTION_CURRENT + "_" + row, this.selectionCurrent[row]);
                this.syncManager.syncValue(SYNC_SELECTION_CORE_IN_USE + "_" + row, this.selectionCoreInUse[row]);
                this.syncManager.syncValue(SYNC_MEMBER_DIM + "_" + row, this.memberDims[row]);
                this.syncManager.syncValue(SYNC_MEMBER_X + "_" + row, this.memberXs[row]);
                this.syncManager.syncValue(SYNC_MEMBER_Y + "_" + row, this.memberYs[row]);
                this.syncManager.syncValue(SYNC_MEMBER_Z + "_" + row, this.memberZs[row]);
                this.syncManager.syncValue(SYNC_MEMBER_CONNECTED + "_" + row, this.memberConnected[row]);
            }
        }

        private void registerActions() {
            this.syncManager.registerSyncedAction(ACTION_SELECT, Side.SERVER, buffer -> {
                int row = buffer.readInt();
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> selectNetwork(serverPlayer, row));
                }
            });
            this.syncManager.registerSyncedAction(ACTION_DETACH, Side.SERVER, buffer -> {
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> detachNetwork(serverPlayer));
                }
            });
            this.syncManager.registerSyncedAction(ACTION_SET_NAME, Side.SERVER, buffer -> {
                String name = NetworkUtils.readStringSafe(buffer);
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> setNetworkName(serverPlayer, name));
                }
            });
            this.syncManager.registerSyncedAction(ACTION_SET_COLOR, Side.SERVER, buffer -> {
                int rgb = buffer.readInt();
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> setNetworkColor(serverPlayer, rgb));
                }
            });
            this.syncManager.registerSyncedAction(ACTION_CYCLE_ACCESS, Side.SERVER, buffer -> {
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> cycleNetworkAccess(serverPlayer));
                }
            });
            this.syncManager.registerSyncedAction(ACTION_DELETE, Side.SERVER, buffer -> {
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> deleteNetwork(serverPlayer));
                }
            });
            this.syncManager.registerSyncedAction(ACTION_CREATE, Side.SERVER, buffer -> {
                String name = NetworkUtils.readStringSafe(buffer);
                int rgb = buffer.readInt();
                int access = buffer.readInt();
                if (this.player instanceof EntityPlayerMP serverPlayer) {
                    runOnServerThread(() -> createNetwork(serverPlayer, name, rgb, access));
                }
            });
        }

        // ---------------------------------------------------------- server actions

        private void selectNetwork(EntityPlayerMP serverPlayer, int row) {
            List<MTWirelessSavedData.NetworkInfo> networks = MTWirelessNetworkRuntime
                .getAccessibleNetworks(serverPlayer);
            if (row < 0 || row >= networks.size()) return;

            MTWirelessSavedData.NetworkInfo network = networks.get(row);
            switch (MTWirelessNetworkRuntime.selectNetworkForCore(serverPlayer, this.core, network.frequency())) {
                case OK -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_SELECTED, network.name());
                case NO_ACCESS -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NO_ACCESS);
                case NETWORK_NOT_FOUND -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_MISSING);
                case CORE_IN_USE -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_CORE_IN_USE);
                case INVALID -> {}
            }
        }

        private void detachNetwork(EntityPlayerMP serverPlayer) {
            switch (MTWirelessNetworkRuntime.selectNetworkForCore(serverPlayer, this.core, null)) {
                case OK -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_UNSELECTED);
                case NO_ACCESS -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NO_ACCESS);
                default -> {}
            }
        }

        private void setNetworkName(EntityPlayerMP serverPlayer, String rawName) {
            UUID network = this.core.getNetworkId();
            if (network == null) return;
            if (!MTWirelessNetworkRuntime.canEdit(serverPlayer, network)) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_ONLY_OWNER);
                return;
            }

            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty()) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NAME_EMPTY);
                return;
            }

            MTWirelessSavedData data = savedData();
            if (data == null) return;
            data.setName(network, name);
            GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NAME_SAVED, data.getName(network));
        }

        private void setNetworkColor(EntityPlayerMP serverPlayer, int rgb) {
            UUID network = this.core.getNetworkId();
            if (network == null) return;
            if (!MTWirelessNetworkRuntime.canEdit(serverPlayer, network)) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_ONLY_OWNER);
                return;
            }

            MTWirelessSavedData data = savedData();
            if (data != null) data.setColor(network, rgb);
        }

        private void cycleNetworkAccess(EntityPlayerMP serverPlayer) {
            UUID network = this.core.getNetworkId();
            if (network == null) return;
            if (!MTWirelessNetworkRuntime.canEdit(serverPlayer, network)) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_ONLY_OWNER);
                return;
            }

            MTWirelessSavedData data = savedData();
            if (data != null) data.setAccess(
                network,
                data.getAccess(network)
                    .next());
        }

        private void deleteNetwork(EntityPlayerMP serverPlayer) {
            UUID network = this.core.getNetworkId();
            if (network == null) return;
            if (!MTWirelessNetworkRuntime.canEdit(serverPlayer, network)) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_ONLY_OWNER);
                return;
            }

            MTWirelessSavedData data = savedData();
            String name = data == null ? "" : data.getName(network);
            if (MTWirelessNetworkRuntime.deleteNetwork(serverPlayer, network)) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_DELETED, name);
            }
        }

        private void createNetwork(EntityPlayerMP serverPlayer, String rawName, int rgb, int access) {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty()) {
                GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NAME_EMPTY);
                return;
            }

            UUID created = MTWirelessNetworkRuntime
                .createNetwork(serverPlayer, name, rgb, MTWirelessAccess.byId(access));
            if (created == null) {
                GTUtility.sendChatTrans(
                    serverPlayer,
                    KEY_CHAT_NETWORK_LIMIT,
                    MTWirelessNetworkRuntime.getNetworkLimit(serverPlayer));
                return;
            }

            GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_CREATED, name);
            switch (MTWirelessNetworkRuntime.selectNetworkForCore(serverPlayer, this.core, created)) {
                case OK -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_SELECTED, name);
                case NO_ACCESS -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NO_ACCESS);
                case NETWORK_NOT_FOUND -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_NETWORK_MISSING);
                case CORE_IN_USE -> GTUtility.sendChatTrans(serverPlayer, KEY_CHAT_CORE_IN_USE);
                case INVALID -> {}
            }
        }

        // ---------------------------------------------------------- panel

        private ModularPanel createPanel() {
            Pages pages = new Pages();
            pages.controller(this.controller);
            pages.addPage(homePage());
            pages.addPage(selectionPage());
            pages.addPage(membersPage());
            pages.addPage(settingsPage());
            pages.addPage(createPage());
            pages.initialPage(MTWirelessGuiStyle.TAB_HOME);
            pages.top(MTWirelessGuiStyle.CONTENT_Y);

            ModularPanel panel = new ModularPanel(PANEL_NAME)
                .size(MTWirelessGuiStyle.PANEL_WIDTH, MTWirelessGuiStyle.PANEL_HEIGHT);
            panel.disableThemeBackground(true);
            panel.disableHoverThemeBackground(true);

            // The content area is drawn by a widget at y = 18 instead of the panel background, exactly the Flux layout:
            // the tab row sits above it and the panel itself only occupies the 172x172 square plus the row.
            panel.child(
                new IDrawable.DrawableWidget(MTWirelessGuiStyle.dynamicPanel(this.colorSync::getIntValue))
                    .size(CONTENT_SIZE, CONTENT_SIZE)
                    .top(MTWirelessGuiStyle.CONTENT_Y));
            panel.child(pages);

            panel.child(tab(MTWirelessGuiStyle.TAB_HOME, MTWirelessGuiStyle.TAB_FIRST_X, () -> true));
            panel.child(
                tab(
                    MTWirelessGuiStyle.TAB_SELECTION,
                    MTWirelessGuiStyle.TAB_FIRST_X + MTWirelessGuiStyle.TAB_SPACING,
                    () -> true));
            panel.child(
                tab(
                    MTWirelessGuiStyle.TAB_MEMBERS,
                    MTWirelessGuiStyle.TAB_FIRST_X + 2 * MTWirelessGuiStyle.TAB_SPACING,
                    this.boundSync::getBoolValue));
            panel.child(
                tab(
                    MTWirelessGuiStyle.TAB_SETTINGS,
                    MTWirelessGuiStyle.TAB_FIRST_X + 3 * MTWirelessGuiStyle.TAB_SPACING,
                    this.boundSync::getBoolValue));
            panel.child(tab(MTWirelessGuiStyle.TAB_CREATE, MTWirelessGuiStyle.TAB_CREATE_X, () -> true));

            ButtonWidget<?> closeButton = ButtonWidget.panelCloseButton();
            closeButton.size(10, 10)
                .top(3)
                .right(4)
                .disableThemeBackground(true)
                .disableHoverThemeBackground(true);
            closeButton.tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(KEY_CLOSE)));
            panel.child(closeButton);
            return panel;
        }

        private TabButton tab(int tab, int x, BooleanSupplier available) {
            TabButton button = new TabButton(this.controller, tab, available);
            button.left(x);
            button.top(0);
            button.tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(TAB_KEYS[tab])));
            return button;
        }

        // ---------------------------------------------------------- pages

        private Page homePage() {
            Page page = new Page();

            BarButton bar = new BarButton(
                IKey.dynamic(
                    () -> this.boundSync.getBoolValue() ? this.nameSync.getStringValue() : translate(KEY_HOME_UNBOUND)),
                this.colorSync::getIntValue,
                mouseButton -> {
                    if (mouseButton != 0) return false;
                    this.controller.setPage(MTWirelessGuiStyle.TAB_SELECTION);
                    return true;
                });
            bar.left(BAR_X);
            bar.top(BAR_Y);
            page.child(bar);

            page.child(
                visible(
                    text(IKey.lang(KEY_HOME_TITLE), TEXT_X, 26, MTWirelessGuiStyle.TEXT_COLOR),
                    this.boundSync::getBoolValue));
            page.child(
                visible(
                    text(
                        IKey.dynamic(
                            () -> translate(this.linkedSync.getBoolValue() ? KEY_HOME_LINKED : KEY_HOME_UNLINKED)),
                        TEXT_X,
                        38,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    this.boundSync::getBoolValue));
            page.child(
                visible(
                    text(
                        IKey.dynamic(() -> translate(KEY_HOME_MEMBERS, this.memberCountSync.getIntValue())),
                        TEXT_X,
                        50,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    this.boundSync::getBoolValue));
            page.child(
                visible(
                    text(
                        IKey.dynamic(() -> translate(KEY_HOME_CONNECTED, this.connectedCountSync.getIntValue())),
                        TEXT_X,
                        62,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    this.boundSync::getBoolValue));
            page.child(
                visible(
                    text(
                        IKey.dynamic(() -> translate(KEY_HOME_NETWORK_ID, this.shortIdSync.getStringValue())),
                        TEXT_X,
                        74,
                        MTWirelessGuiStyle.TEXT_COLOR_DIM),
                    this.boundSync::getBoolValue));
            page.child(
                visible(
                    text(
                        IKey.dynamic(() -> translate(KEY_HOME_ACCESS, accessName(this.accessSync.getIntValue()))),
                        TEXT_X,
                        86,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    this.boundSync::getBoolValue));

            FluxButton detach = new FluxButton(
                IKey.lang(KEY_HOME_DETACH),
                this.colorSync::getIntValue,
                this.boundSync::getBoolValue,
                mouseButton -> {
                    this.syncManager.callSyncedAction(ACTION_DETACH);
                    return true;
                });
            detach.size(70, 16);
            detach.left(TEXT_X);
            detach.top(124);
            page.child(visible(detach, this.boundSync::getBoolValue));

            page.child(
                visible(
                    wrappedText(
                        IKey.lang(KEY_HOME_UNBOUND_HINT),
                        TEXT_X,
                        30,
                        CONTENT_SIZE - 2 * TEXT_X,
                        0.75f,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> !this.boundSync.getBoolValue()));
            return page;
        }

        private Page selectionPage() {
            Page page = new Page();

            BarButton bar = new BarButton(
                IKey.dynamic(
                    () -> this.boundSync.getBoolValue() ? this.nameSync.getStringValue() : translate(KEY_CURRENT_NONE)),
                this.colorSync::getIntValue,
                mouseButton -> true);
            bar.left(BAR_X);
            bar.top(BAR_Y);
            page.child(bar);

            for (int row = 0; row < MAX_ROWS; row++) {
                NetworkRow networkRow = new NetworkRow(
                    row,
                    this.selectionNames[row],
                    this.selectionColors[row],
                    this.selectionAccess[row],
                    this.selectionCurrent[row],
                    this.selectionCoreInUse[row],
                    this.selectionRowCountSync,
                    this.syncManager);
                networkRow.left(ROW_X);
                networkRow.top(SELECTION_FIRST_ROW + row * ROW_SPACING);
                page.child(networkRow);
            }

            page.child(
                visible(
                    text(IKey.lang(KEY_SELECTION_EMPTY), TEXT_X, 30, MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> this.accessibleTotalSync.getIntValue() == 0));
            page.child(
                visible(
                    wrappedText(
                        IKey.lang(KEY_SELECTION_EMPTY_HINT),
                        TEXT_X,
                        44,
                        CONTENT_SIZE - 2 * TEXT_X,
                        0.75f,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> this.accessibleTotalSync.getIntValue() == 0));
            page.child(
                visible(
                    text(
                        IKey.dynamic(() -> translate(KEY_MORE, this.accessibleTotalSync.getIntValue() - MAX_ROWS)),
                        ROW_X,
                        140,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> this.accessibleTotalSync.getIntValue() > MAX_ROWS));
            return page;
        }

        private Page membersPage() {
            Page page = new Page();

            page.child(text(IKey.lang(KEY_MEMBERS_TITLE), TEXT_X, 6, MTWirelessGuiStyle.TEXT_COLOR));
            page.child(
                visible(
                    text(
                        IKey.dynamic(
                            () -> translate(
                                KEY_MEMBERS_TOTAL,
                                this.memberCountSync.getIntValue(),
                                this.connectedCountSync.getIntValue())),
                        TEXT_X,
                        20,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    this.boundSync::getBoolValue));

            for (int row = 0; row < MAX_ROWS; row++) {
                MemberRow memberRow = new MemberRow(
                    row,
                    this.memberDims[row],
                    this.memberXs[row],
                    this.memberYs[row],
                    this.memberZs[row],
                    this.colorSync,
                    this.memberConnected[row],
                    this.memberRowCountSync);
                memberRow.left(ROW_X);
                memberRow.top(MEMBERS_FIRST_ROW + row * ROW_SPACING);
                page.child(memberRow);
            }

            page.child(
                visible(
                    text(IKey.lang(KEY_MEMBERS_EMPTY), TEXT_X, MEMBERS_FIRST_ROW, MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> this.memberCountSync.getIntValue() == 0));
            page.child(
                visible(
                    text(
                        IKey.dynamic(() -> translate(KEY_MORE, this.memberCountSync.getIntValue() - MAX_ROWS)),
                        ROW_X,
                        150,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> this.memberCountSync.getIntValue() > MAX_ROWS));
            return page;
        }

        private Page settingsPage() {
            Page page = new Page();

            page.child(
                text(IKey.lang(KEY_SETTINGS_TITLE), 0, 6, MTWirelessGuiStyle.TEXT_COLOR).width(CONTENT_SIZE)
                    .alignment(Alignment.Center));

            page.child(text(IKey.lang(KEY_SETTINGS_NAME), TEXT_X, 26, MTWirelessGuiStyle.TEXT_COLOR_LIGHT));
            StringValue nameValue = new StringValue("");
            TextFieldWidget nameField = new TextFieldWidget().value(nameValue)
                .setMaxLength(MTWirelessSavedData.MAX_NAME_LENGTH)
                .size(112, 14);
            nameField.left(44);
            nameField.top(23);
            page.child(nameField);
            // The sync value is updated on the server too (detectAndSendChanges runs while the GUI is being opened),
            // where the widget is not attached to a screen yet: isFocused() would throw "not in a valid state". Seed
            // the
            // field only once it is valid, and once per client tick for the case where the first sync packet arrived
            // before the widget tree was attached.
            this.rawNameSync.changeListener(() -> seedNameField(nameField));
            this.syncManager.onClientTick(() -> seedNameField(nameField));

            FluxButton save = new FluxButton(
                IKey.lang(KEY_SETTINGS_SAVE),
                this.colorSync::getIntValue,
                this.editableSync::getBoolValue,
                mouseButton -> {
                    String text = nameField.getText();
                    this.syncManager
                        .callSyncedAction(ACTION_SET_NAME, buffer -> NetworkUtils.writeStringSafe(buffer, text));
                    return true;
                });
            save.size(50, 14);
            save.left(TEXT_X);
            save.top(42);
            page.child(save);

            page.child(text(IKey.lang(KEY_SETTINGS_COLOR), TEXT_X, 62, MTWirelessGuiStyle.TEXT_COLOR_LIGHT));
            for (int index = 0; index < MTWirelessColors.PALETTE.length; index++) {
                final int rgb = MTWirelessColors.PALETTE[index];
                SwatchButton swatch = new SwatchButton(rgb, this.colorSync::getIntValue, mouseButton -> {
                    if (!this.editableSync.getBoolValue()) return true;
                    this.syncManager.callSyncedAction(ACTION_SET_COLOR, buffer -> buffer.writeInt(rgb));
                    return true;
                });
                swatch.left(swatchesX(index));
                swatch.top(swatchesY(index));
                page.child(swatch);
            }

            page.child(text(IKey.lang(KEY_SETTINGS_ACCESS), TEXT_X, 102, MTWirelessGuiStyle.TEXT_COLOR_LIGHT));
            FluxButton access = new FluxButton(
                IKey.lang(
                    () -> MTWirelessAccess.byId(this.accessSync.getIntValue())
                        .langKey()),
                this.colorSync::getIntValue,
                this.editableSync::getBoolValue,
                mouseButton -> {
                    this.syncManager.callSyncedAction(ACTION_CYCLE_ACCESS);
                    return true;
                });
            access.size(100, 14);
            access.left(TEXT_X);
            access.top(114);
            page.child(access);

            FluxButton delete = new FluxButton(
                IKey.dynamic(() -> translate(this.deleteArmed ? KEY_SETTINGS_DELETE_CONFIRM : KEY_SETTINGS_DELETE)),
                this.colorSync::getIntValue,
                this.editableSync::getBoolValue,
                mouseButton -> {
                    if (!this.deleteArmed) {
                        this.deleteArmed = true;
                        return true;
                    }
                    this.syncManager.callSyncedAction(ACTION_DELETE);
                    if (this.controller.isInitialised()) {
                        this.controller.setPage(MTWirelessGuiStyle.TAB_HOME);
                    }
                    return true;
                });
            delete.size(100, 14);
            delete.left(TEXT_X);
            delete.top(138);
            page.child(visible(delete, this.editableSync::getBoolValue));

            page.child(
                visible(
                    wrappedText(
                        IKey.lang(KEY_SETTINGS_READONLY),
                        TEXT_X,
                        138,
                        CONTENT_SIZE - 2 * TEXT_X,
                        0.75f,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> this.boundSync.getBoolValue() && !this.editableSync.getBoolValue()));
            return page;
        }

        private Page createPage() {
            Page page = new Page();

            page.child(
                text(IKey.lang(KEY_CREATE_TITLE), 0, 6, MTWirelessGuiStyle.TEXT_COLOR).width(CONTENT_SIZE)
                    .alignment(Alignment.Center));

            page.child(text(IKey.lang(KEY_CREATE_NAME), TEXT_X, 24, MTWirelessGuiStyle.TEXT_COLOR_LIGHT));
            String defaultName = translate(
                KEY_CREATE_DEFAULT_NAME,
                this.player == null ? "" : this.player.getCommandSenderName());
            StringValue nameValue = new StringValue(defaultName);
            TextFieldWidget nameField = new TextFieldWidget().value(nameValue)
                .setMaxLength(MTWirelessSavedData.MAX_NAME_LENGTH)
                .size(112, 14);
            nameField.left(44);
            nameField.top(21);
            page.child(nameField);

            page.child(text(IKey.lang(KEY_CREATE_COLOR), TEXT_X, 42, MTWirelessGuiStyle.TEXT_COLOR_LIGHT));
            for (int index = 0; index < MTWirelessColors.PALETTE.length; index++) {
                final int rgb = MTWirelessColors.PALETTE[index];
                SwatchButton swatch = new SwatchButton(rgb, () -> this.createColor[0], mouseButton -> {
                    this.createColor[0] = rgb;
                    return true;
                });
                swatch.left(swatchesX(index));
                swatch.top(createSwatchesY(index));
                page.child(swatch);
            }

            page.child(text(IKey.lang(KEY_CREATE_ACCESS), TEXT_X, 84, MTWirelessGuiStyle.TEXT_COLOR_LIGHT));
            FluxButton access = new FluxButton(
                IKey.lang(
                    () -> MTWirelessAccess.byId(this.createAccess[0])
                        .langKey()),
                () -> this.createColor[0],
                () -> true,
                mouseButton -> {
                    this.createAccess[0] = MTWirelessAccess.byId(this.createAccess[0])
                        .next()
                        .id();
                    return true;
                });
            access.size(100, 14);
            access.left(TEXT_X);
            access.top(96);
            page.child(access);

            FluxButton create = new FluxButton(
                IKey.lang(KEY_CREATE_BUTTON),
                () -> this.createColor[0],
                () -> !nameField.getText()
                    .trim()
                    .isEmpty() && this.canCreateSync.getBoolValue(),
                mouseButton -> {
                    String name = nameField.getText();
                    int rgb = this.createColor[0];
                    int accessId = this.createAccess[0];
                    this.syncManager.callSyncedAction(ACTION_CREATE, buffer -> {
                        NetworkUtils.writeStringSafe(buffer, name);
                        buffer.writeInt(rgb);
                        buffer.writeInt(accessId);
                    });
                    if (this.controller.isInitialised()) {
                        this.controller.setPage(MTWirelessGuiStyle.TAB_HOME);
                    }
                    return true;
                });
            create.size(48, 16);
            create.left((CONTENT_SIZE - 48) / 2);
            create.top(124);
            page.child(create);

            page.child(
                visible(
                    wrappedText(
                        IKey.dynamic(() -> translate(KEY_CREATE_LIMIT, this.limitSync.getIntValue())),
                        TEXT_X,
                        146,
                        CONTENT_SIZE - 2 * TEXT_X,
                        0.75f,
                        MTWirelessGuiStyle.TEXT_COLOR_LIGHT),
                    () -> !this.canCreateSync.getBoolValue()));
            return page;
        }

        /**
         * Copies the saved raw network name into the settings text field. Guarded because MUI2 also builds this panel
         * on
         * the server and updates sync values while the GUI is being opened: {@code isFocused()}/{@code getContext()}
         * throw "not in a valid state" until the widget tree is attached to a screen.
         */
        private void seedNameField(TextFieldWidget nameField) {
            if (!nameField.isValid()) return;
            if (nameField.isFocused()) return;

            String raw = this.rawNameSync.getStringValue();
            String text = raw == null ? "" : raw;
            if (!nameField.getText()
                .equals(text)) {
                nameField.setText(text);
            }
        }

        private static int swatchesX(int index) {
            return SWATCHES_X + (index % SWATCHES_PER_ROW) * SWATCH_SPACING;
        }

        private static int swatchesY(int index) {
            return 74 + (index / SWATCHES_PER_ROW) * SWATCH_SPACING;
        }

        private static int createSwatchesY(int index) {
            return 54 + (index / SWATCHES_PER_ROW) * SWATCH_SPACING;
        }
    }
}
