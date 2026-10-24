package com.MessTech.common.gui;

import java.nio.IntBuffer;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.MessTech.common.gameconsole.MTGameConsoleBindings;
import com.MessTech.common.gameconsole.MTGameConsoleBindings.Action;
import com.MessTech.common.gameconsole.MTGameConsoleHost;
import com.MessTech.init.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The game console's window: a centred panel about half the screen across running DOOM, with the console's own key
 * settings one click away.
 * <p>
 * The engine renders 320x200 into a plain ARGB array (see {@code v.renderers.HeadlessRaster}); this class uploads
 * each finished frame into a texture and draws it with nearest-neighbour filtering, so the pixels stay square.
 * <p>
 * {@code Esc} closes the window (DOOM's menu is on {@code F1}); everything else reaches the engine by polling
 * LWJGL's key state, the only way to learn about key releases inside a {@code GuiScreen}. The defaults and the
 * rebinding behind {@link MTGameConsoleBindings} are on the key screen, which is a <em>mode</em> of this window and
 * not a second {@code GuiScreen}: swapping screens would close this one, and closing this one stops the engine, so
 * the player would lose their game.
 */
@SideOnly(Side.CLIENT)
public class GuiGameConsole extends GuiScreen {

    private static final int FRAME_WIDTH = MTGameConsoleHost.WIDTH;
    private static final int FRAME_HEIGHT = MTGameConsoleHost.HEIGHT;
    private static final int PIXEL_COUNT = FRAME_WIDTH * FRAME_HEIGHT;

    /** DOOM's 320x200 shape, kept by the panel. */
    private static final double ASPECT = (double) FRAME_WIDTH / FRAME_HEIGHT;

    private static final int COLOUR_BEZEL = 0xFF1B1D22;
    private static final int COLOUR_BEZEL_EDGE = 0xFF3A3F49;
    private static final int COLOUR_SCREEN_OFF = 0xFF07080A;
    private static final int COLOUR_ROW = 0xFF14161A;
    private static final int COLOUR_KEY_BOX = 0xFF23272F;
    private static final int COLOUR_HOT = 0xFF4A90D9;
    private static final int COLOUR_TEXT = 0xFFDDDDDD;
    private static final int COLOUR_TEXT_DIM = 0xFF8A8F99;
    private static final int COLOUR_CAPTURE = 0xFFE0B040;
    private static final int COLOUR_CAPTURE_TEXT = 0xFF202020;

    private static final String HINT = "\u00A78Esc \u00A77退出    \u00A78F1 \u00A77DOOM 菜单    \u00A78WASD/方向键 \u00A77移动    \u00A78E \u00A77使用    \u00A78Ctrl \u00A77开火";

    private static final int ROW_HEIGHT = 12;
    private static final int KEY_BOX_WIDTH = 46;
    private static final int KEY_BOX_HEIGHT = 10;
    private static final int KEY_BOX_GAP = 4;

    private static final String KEY_OPEN = "messtech.gameConsole.keys.open";
    private static final String KEY_TITLE_ACTIONS = "messtech.gameConsole.keys.titleActions";
    private static final String KEY_TITLE_WEAPONS = "messtech.gameConsole.keys.titleWeapons";
    private static final String KEY_RESET = "messtech.gameConsole.keys.reset";
    private static final String KEY_NEXT = "messtech.gameConsole.keys.nextPage";
    private static final String KEY_PREV = "messtech.gameConsole.keys.prevPage";
    private static final String KEY_BACK = "messtech.gameConsole.keys.back";
    private static final String KEY_NOTE = "messtech.gameConsole.keys.note";

    private final MTGameConsoleHost host;

    private final int[] frame = new int[PIXEL_COUNT];
    private final IntBuffer upload = BufferUtils.createIntBuffer(PIXEL_COUNT);

    private int textureId = -1;
    private int uploadedRevision = -1;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    /** Frames uploaded in the last second, for the readout above the panel. */
    private int framesThisSecond;
    private long secondStarted = -1L;
    private int framesPerSecond;

    /** True while the key screen is showing instead of the game. */
    private boolean keyScreen;
    /** 0 = movement and actions, 1 = weapons. */
    private int keyPage;
    /** The binding slot waiting for a key press, or null. */
    private Action capturing;
    private int capturingSlot;

    /** Bottom bar geometry, recomputed while drawing. */
    private int barY;
    private int barResetX;
    private int barPageX;
    private int barBackX;

    public GuiGameConsole() {
        this.host = MTGameConsoleHost.start();
    }

    @Override
    public void initGui() {
        int width = Math.max(160, (int) (this.width * Config.GAME_CONSOLE_SCREEN_FRACTION));
        int height = (int) Math.round(width / ASPECT);

        final int maxHeight = (int) (this.height * 0.9D);
        if (height > maxHeight) {
            height = maxHeight;
            width = (int) Math.round(height * ASPECT);
        }

        this.panelWidth = width;
        this.panelHeight = height;
        this.panelX = (this.width - width) / 2;
        this.panelY = (this.height - height) / 2;
    }

    /**
     * The console is a toy, not a container: the world keeps running behind it, or a player would be frozen in place
     * while playing. The config can freeze it instead.
     */
    @Override
    public boolean doesGuiPauseGame() {
        return Config.GAME_CONSOLE_PAUSES_WORLD;
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) {
        if (capturing != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                // Escape always means "stop asking", never "assign Escape": it belongs to the window.
                capturing = null;
            } else if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                host.getBindings()
                    .set(capturing, capturingSlot, -1);
                capturing = null;
            } else if (keyCode != Keyboard.KEY_NONE) {
                host.getBindings()
                    .set(capturing, capturingSlot, keyCode);
                capturing = null;
            }
            return;
        }

        if (keyScreen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeKeyScreen();
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        // Everything else is swallowed: Minecraft must not act on keys DOOM is using.
    }

    @Override
    public void onGuiClosed() {
        this.capturing = null;
        if (keyScreen) {
            saveBindings();
        }
        if (this.host != null) {
            this.host.releaseKeys();
            this.host.stop();
        }
        if (this.textureId != -1) {
            GL11.glDeleteTextures(this.textureId);
            this.textureId = -1;
        }
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        if (mouseButton != 0) {
            return;
        }

        if (!keyScreen) {
            final String open = StatCollector.translateToLocal(KEY_OPEN);
            final int x = panelX + panelWidth - this.fontRendererObj.getStringWidth(open) - 2;
            if (hits(open, x, panelY - 11, mouseX, mouseY)) {
                enterKeyScreen();
            }
            return;
        }

        if (capturing != null) {
            capturing = null;
            return;
        }

        final List<Action> actions = currentActions();
        final int right = panelX + panelWidth - 8;
        int y = panelY + 20;
        for (final Action action : actions) {
            for (int slot = 0; slot < MTGameConsoleBindings.SLOTS_PER_ACTION; slot++) {
                final int boxX = keyBoxX(right, slot);
                if (mouseX >= boxX && mouseX < boxX + KEY_BOX_WIDTH && mouseY >= y && mouseY < y + KEY_BOX_HEIGHT) {
                    this.capturing = action;
                    this.capturingSlot = slot;
                    return;
                }
            }
            y += ROW_HEIGHT;
        }

        if (mouseY >= barY && mouseY < barY + 9) {
            if (hits(StatCollector.translateToLocal(KEY_RESET), barResetX, barY, mouseX, mouseY)) {
                host.getBindings()
                    .resetToDefaults();
            } else if (hits(StatCollector.translateToLocal(pageKey()), barPageX, barY, mouseX, mouseY)) {
                keyPage = keyPage == 0 ? 1 : 0;
            } else if (hits(StatCollector.translateToLocal(KEY_BACK), barBackX, barY, mouseX, mouseY)) {
                closeKeyScreen();
            }
        }
    }

    private boolean hits(final String label, final int x, final int y, final int mouseX, final int mouseY) {
        return mouseX >= x && mouseX < x + this.fontRendererObj.getStringWidth(label) && mouseY >= y && mouseY < y + 9;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.drawDefaultBackground();

        // A bezel around the screen, like the item's own little console.
        drawRect(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, COLOUR_BEZEL_EDGE);
        drawRect(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, COLOUR_BEZEL);
        drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOUR_SCREEN_OFF);

        if (keyScreen) {
            drawKeyScreen(mouseX, mouseY);
        } else {
            final MTGameConsoleHost.State state = host.getState();
            if (state == MTGameConsoleHost.State.RUNNING) {
                host.pollInput();
                drawFrame();
            } else {
                drawStatus(state);
            }
            drawKeyScreenEntry(mouseX, mouseY);
        }

        this.fontRendererObj.drawStringWithShadow(
            HINT,
            panelX + (panelWidth - this.fontRendererObj.getStringWidth(HINT)) / 2,
            panelY + panelHeight + 7,
            0xFFFFFF);
    }

    /** Uploads the newest finished frame when there is one, then draws the texture. */
    private void drawFrame() {
        if (this.textureId == -1) {
            createTexture();
            this.uploadedRevision = -1;
        }

        final int revision = host.getRevision();
        if (revision != this.uploadedRevision) {
            this.uploadedRevision = revision;
            uploadFrame();
            this.framesThisSecond++;
        }

        final long now = System.currentTimeMillis();
        if (this.secondStarted < 0L) {
            this.secondStarted = now;
        } else if (now - this.secondStarted >= 1000L) {
            this.framesPerSecond = this.framesThisSecond;
            this.framesThisSecond = 0;
            this.secondStarted = now;
        }

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);

        final int x1 = panelX;
        final int y1 = panelY;
        final int x2 = panelX + panelWidth;
        final int y2 = panelY + panelHeight;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2i(x1, y2);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2i(x2, y2);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2i(x2, y1);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2i(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        if (this.framesPerSecond > 0) {
            final String stats = FRAME_WIDTH + "x" + FRAME_HEIGHT + "  " + this.framesPerSecond + " fps";
            this.fontRendererObj.drawString(
                stats,
                panelX + panelWidth - this.fontRendererObj.getStringWidth(stats),
                panelY - 11,
                0x808080);
        }
    }

    private void createTexture() {
        this.textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        // Allocate the storage once; every frame after this is a sub-image update.
        this.upload.clear();
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            FRAME_WIDTH,
            FRAME_HEIGHT,
            0,
            GL12.GL_BGRA,
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
            this.upload);
    }

    private void uploadFrame() {
        host.copyFrame(this.frame);
        this.upload.clear();
        this.upload.put(this.frame);
        this.upload.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            0,
            0,
            FRAME_WIDTH,
            FRAME_HEIGHT,
            GL12.GL_BGRA,
            GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
            this.upload);
    }

    /** Everything that is not a running game: preparing, loading, failed, stopped. */
    private void drawStatus(final MTGameConsoleHost.State state) {
        final int centreX = panelX + panelWidth / 2;
        int y = panelY + panelHeight / 2 - 14;

        final String title;
        switch (state) {
            case FAILED:
                title = "\u00A7c游戏机启动失败";
                break;
            case STOPPED:
                title = "\u00A77游戏机已停止";
                break;
            default:
                title = "\u00A7e游戏机启动中…";
                break;
        }
        this.fontRendererObj
            .drawStringWithShadow(title, centreX - this.fontRendererObj.getStringWidth(title) / 2, y, 0xFFFFFF);
        y += this.fontRendererObj.FONT_HEIGHT + 6;

        final String status = host.getStatus();
        if (status != null && !status.isEmpty()) {
            final List<?> lines = this.fontRendererObj
                .listFormattedStringToWidth(status, Math.max(40, panelWidth - 24));
            for (final Object line : lines) {
                final String text = "\u00A77" + line;
                this.fontRendererObj
                    .drawStringWithShadow(text, centreX - this.fontRendererObj.getStringWidth(text) / 2, y, 0xFFFFFF);
                y += this.fontRendererObj.FONT_HEIGHT + 1;
            }
        }

        final int progress = host.getProgress();
        if (progress >= 0) {
            y += 6;
            final int barWidth = Math.max(40, panelWidth - 60);
            final int barX = centreX - barWidth / 2;
            drawRect(barX, y, barX + barWidth, y + 5, 0xFF000000);
            drawRect(barX, y, barX + barWidth * progress / 100, y + 5, 0xFF4A90D9);
            y += 11;
        }

        if (state == MTGameConsoleHost.State.FAILED) {
            final String help = "\u00A77把 doom.wad 或 freedoom1.wad 放进 \u00A7f" + Config.GAME_CONSOLE_DIR
                + " \u00A77后重开游戏机";
            for (final Object line : this.fontRendererObj
                .listFormattedStringToWidth(help, Math.max(40, panelWidth - 24))) {
                final String text = line.toString();
                this.fontRendererObj.drawString(
                    text,
                    centreX - this.fontRendererObj.getStringWidth(text) / 2,
                    panelY + panelHeight - 24,
                    0xFFFFFF);
            }
        }
    }

    /** The clickable "按键设置" label in the bezel's corner: the way into the key screen. */
    private void drawKeyScreenEntry(final int mouseX, final int mouseY) {
        final String label = StatCollector.translateToLocal(KEY_OPEN);
        final int x = panelX + panelWidth - this.fontRendererObj.getStringWidth(label) - 2;
        final int y = panelY - 11;
        this.fontRendererObj.drawString(label, x, y, hits(label, x, y, mouseX, mouseY) ? COLOUR_HOT : COLOUR_TEXT_DIM);
    }

    private List<Action> currentActions() {
        return keyPage == 1 ? MTGameConsoleBindings.WEAPON_ACTIONS : MTGameConsoleBindings.MOVEMENT_ACTIONS;
    }

    private String pageKey() {
        return keyPage == 0 ? KEY_NEXT : KEY_PREV;
    }

    private void drawKeyScreen(final int mouseX, final int mouseY) {
        final int left = panelX + 8;
        final int right = panelX + panelWidth - 8;

        final String title = StatCollector.translateToLocal(keyPage == 1 ? KEY_TITLE_WEAPONS : KEY_TITLE_ACTIONS);
        this.fontRendererObj.drawStringWithShadow(title, left, panelY + 6, 0xFFFFFF);

        int y = panelY + 20;
        for (final Action action : currentActions()) {
            drawRect(left, y - 1, right, y + ROW_HEIGHT - 2, COLOUR_ROW);
            this.fontRendererObj
                .drawString(StatCollector.translateToLocal(action.labelKey()), left + 4, y + 1, COLOUR_TEXT);

            for (int slot = 0; slot < MTGameConsoleBindings.SLOTS_PER_ACTION; slot++) {
                final int boxX = keyBoxX(right, slot);
                final boolean waiting = capturing == action && capturingSlot == slot;
                final boolean hot = mouseX >= boxX && mouseX < boxX + KEY_BOX_WIDTH
                    && mouseY >= y
                    && mouseY < y + KEY_BOX_HEIGHT;
                drawRect(
                    boxX,
                    y,
                    boxX + KEY_BOX_WIDTH,
                    y + KEY_BOX_HEIGHT,
                    waiting ? COLOUR_CAPTURE : hot ? COLOUR_HOT : COLOUR_KEY_BOX);

                final String text = waiting ? "…"
                    : MTGameConsoleBindings.keyName(
                        host.getBindings()
                            .get(action, slot));
                if (!text.isEmpty()) {
                    this.fontRendererObj.drawString(
                        text,
                        boxX + (KEY_BOX_WIDTH - this.fontRendererObj.getStringWidth(text)) / 2,
                        y + 1,
                        waiting ? COLOUR_CAPTURE_TEXT : COLOUR_TEXT);
                }
            }
            y += ROW_HEIGHT;
        }

        final String note = StatCollector.translateToLocal(KEY_NOTE);
        final String reset = StatCollector.translateToLocal(KEY_RESET);
        final String page = StatCollector.translateToLocal(pageKey());
        final String back = StatCollector.translateToLocal(KEY_BACK);

        this.barY = panelY + panelHeight - 20;
        this.fontRendererObj.drawString(note, left, barY - 10, COLOUR_TEXT_DIM);

        this.barResetX = left + 4;
        this.barPageX = panelX + panelWidth / 2 - 20;
        this.barBackX = right - this.fontRendererObj.getStringWidth(back) - 4;
        drawBarLabel(reset, barResetX, mouseX, mouseY);
        drawBarLabel(page, barPageX, mouseX, mouseY);
        drawBarLabel(back, barBackX, mouseX, mouseY);
    }

    private void drawBarLabel(final String label, final int x, final int mouseX, final int mouseY) {
        this.fontRendererObj
            .drawString(label, x, barY, hits(label, x, barY, mouseX, mouseY) ? COLOUR_HOT : COLOUR_TEXT);
    }

    private static int keyBoxX(final int right, final int slot) {
        return right - (MTGameConsoleBindings.SLOTS_PER_ACTION - slot) * (KEY_BOX_WIDTH + KEY_BOX_GAP);
    }

    private void enterKeyScreen() {
        this.keyScreen = true;
        this.keyPage = 0;
        this.capturing = null;
        // Nothing the player was holding should stay held in here.
        host.releaseKeys();
    }

    /**
     * Leaves the key screen, storing what changed. The running engine keeps the bindings it was started with - the
     * note on the screen says so - so this only writes the file for the next time the console opens.
     */
    private void closeKeyScreen() {
        this.capturing = null;
        this.keyScreen = false;
        saveBindings();
    }

    private void saveBindings() {
        host.getBindings()
            .save(host.getBindingsFile());
    }
}
