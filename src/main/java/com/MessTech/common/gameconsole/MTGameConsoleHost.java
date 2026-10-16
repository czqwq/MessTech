package com.MessTech.common.gameconsole;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;

import com.MessTech.doom.HostPaths;
import com.MessTech.doom.doom.CVarManager;
import com.MessTech.doom.doom.DoomMain;
import com.MessTech.doom.g.Signals.ScanCode;
import com.MessTech.doom.i.IDoomSystem;
import com.MessTech.doom.mochadoom.Engine;
import com.MessTech.doom.v.renderers.HeadlessRaster;
import com.MessTech.init.Config;
import com.MessTech.init.MessTech;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One running copy of the Mocha Doom engine, embedded in the game console item.
 * <p>
 * The engine is a stand-alone program at heart: it owns a thread, it runs its own 35 Hz loop and it
 * renders into its own 320x200 buffer. This class is the seam - it is the {@link Engine.Host} the
 * patched engine asks for frames, for a stop decision and for an {@code IDoomSystem}, and it is
 * what the GUI talks to.
 * <p>
 * Threading contract, and it matters:
 * <ul>
 * <li>Everything the engine touches stays on the engine thread. The GUI only hands it input through
 * {@link #postKey} / {@link DoomMain#PostEvent}, which is the same queue the AWT window upstream
 * used to post into from the event dispatch thread.</li>
 * <li>Frames travel the other way by double buffering: the engine finishes into one array, the
 * arrays swap under {@link #frameLock}, and the GUI copies the finished one out. The engine never
 * waits for the GUI and the GUI never sees a half-drawn frame.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class MTGameConsoleHost implements Engine.Host {

    /** Native Doom resolution - the engine renders 1:1 and OpenGL does the upscaling. */
    public static final int WIDTH = 320;
    public static final int HEIGHT = 200;
    private static final int PIXELS = WIDTH * HEIGHT;

    public enum State {
        /** WAD lookup or download. */
        PREPARING,
        /** The engine is loading the IWAD and initialising. */
        LOADING,
        /** Frames are being produced. */
        RUNNING,
        /** Something went wrong; {@link #status} says what. */
        FAILED,
        /** Stopped, either by the player or because the engine returned. */
        STOPPED
    }

    private final File runtimeDir;
    private final int threads;

    private final Object frameLock = new Object();
    private int[] front = new int[PIXELS];
    private int[] back = new int[PIXELS];
    private int revision;

    private final boolean[] keyState = new boolean[256];

    /** The player's key map, loaded before the engine starts. */
    private volatile MTGameConsoleBindings bindings;

    private volatile State state = State.PREPARING;
    private volatile String status = "";
    private volatile int progress = -1;
    private volatile boolean running = true;
    private volatile DoomMain<?, ?> doom;
    private volatile Thread thread;

    private MTGameConsoleHost(final File runtimeDir, final int threads) {
        this.runtimeDir = runtimeDir;
        this.threads = threads;
        this.bindings = MTGameConsoleBindings.load(new File(runtimeDir, "keys.cfg"));
    }

    /** Prepares the folder, then boots the engine on its own daemon thread. */
    public static MTGameConsoleHost start() {
        final File dir = MTGameConsoleWads.runtimeDirectory();
        final MTGameConsoleHost host = new MTGameConsoleHost(dir, Config.GAME_CONSOLE_THREADS);
        final Thread t = new Thread(host::run, "MessTech-GameConsole");
        t.setDaemon(true);
        host.thread = t;
        t.start();
        return host;
    }

    // ------------------------------------------------------------------ engine thread

    private void run() {
        try {
            MTGameConsoleWads.ensureDirectory(runtimeDir);

            setStatus(State.PREPARING, "正在查找 WAD 数据…", -1);
            final File wad = MTGameConsoleWads
                .resolveIwad(runtimeDir, (text, percent) -> setStatus(State.PREPARING, text, percent));
            if (wad == null) {
                fail("找不到可用的 WAD 数据，也无法自动下载 Freedoom。");
                return;
            }

            if (!running) {
                state = State.STOPPED;
                return;
            }

            setStatus(State.LOADING, "正在初始化 DOOM 引擎…", -1);
            HostPaths.setBaseDirectory(runtimeDir.getAbsolutePath());
            writeSeedConfig(runtimeDir);

            final CVarManager cvm = new CVarManager(commandLine(wad));
            final Engine engine = Engine.bootstrap(cvm, this);
            final DoomMain<?, ?> main = engine.doom();
            // The console's own bindings, on top of whatever DOOM's config said. This is the only
            // place keys are decided - DOOM's own OPTIONS -> KEYS page is not a second one, which is
            // why nothing is written back into mochadoom.cfg here any more.
            bindings.apply(main);
            this.doom = main;

            setStatus(State.RUNNING, "", -1);
            applyMinecraftVolume(main);
            engine.run();

            if (state != State.FAILED) {
                setStatus(State.STOPPED, "", -1);
            }
        } catch (final Throwable t) {
            MessTech.LOG.error("Game console engine failed", t);
            fail(
                t.getClass()
                    .getSimpleName() + ": "
                    + t.getMessage());
        } finally {
            running = false;
            releaseAudio();
            releaseRenderer();
        }
    }

    /**
     * Keeps the console quiet when the player has turned Minecraft down: DOOM mixes its own audio
     * and has a volume of its own, so without this the two would disagree.
     * <p>
     * Applied <em>after</em> the bindings were applied, and only to the live {@code IDoomSound},
     * so the volume DOOM's own menu writes into {@code mochadoom.cfg} is left alone.
     */
    private void applyMinecraftVolume(final DoomMain<?, ?> main) {
        final float master;
        try {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.gameSettings == null) {
                return;
            }
            master = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
        } catch (final Throwable ignored) {
            // Not a client, or the settings are not up yet: leave the engine's own volume.
            return;
        }
        final int volume = Math.max(0, Math.min(127, Math.round(master * 127.0F)));
        main.doomSound.SetSfxVolume(volume);
        main.doomSound.SetMusicVolume(volume);
    }

    /**
     * Stops the engine's audio. Best effort on purpose: a dummy driver answers with a no-op, and a
     * failure here must never keep the console's window from closing.
     */
    private void releaseAudio() {
        final DoomMain<?, ?> main = doom;
        if (main == null) {
            return;
        }
        try {
            main.music.ShutdownMusic();
        } catch (final RuntimeException e) {
            MessTech.LOG.warn("Game console: could not stop the music", e);
        }
        try {
            main.soundDriver.ShutdownSound();
        } catch (final RuntimeException e) {
            MessTech.LOG.warn("Game console: could not stop the sound driver", e);
        }
    }

    /**
     * Writes the engine config the console wants to start from, once, before the engine reads it.
     * <p>
     * The one setting that matters here is the true-colour post-processing thread count: Mocha
     * Doom defaults it to {@code availableProcessors()}, and a 320x200 frame does not need eight
     * threads competing with Minecraft's own renderer on the same cores. The engine rewrites the
     * whole file as soon as the player changes anything in DOOM's menu, so this only ever seeds
     * the first run.
     */
    private void writeSeedConfig(final File dir) {
        final File cfg = new File(dir, "mochadoom.cfg");
        if (cfg.isFile()) {
            return;
        }
        final String seed = "# Written by MessTech's game console. DOOM overwrites this file as soon as\n"
            + "# anything is changed in its own OPTIONS menu.\n"
            + "parallelism_realcolor_tint\t\t"
            + Math.max(1, threads)
            + "\n";
        try {
            Files.write(cfg.toPath(), seed.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            MessTech.LOG.warn("Game console: could not seed {}", cfg, e);
        }
    }

    private List<String> commandLine(final File wad) {
        final List<String> args = new ArrayList<>();
        args.add("-iwad");
        args.add(wad.getAbsolutePath());
        // No -config here on purpose: it only replaces the list of files the engine *reads*, while
        // where the settings are written is decided by ConfigBase's folder, which HostPaths points
        // at the console's own directory. Passing an absolute path there would even be wrong, as
        // that folder is prepended to the name.
        // Vanilla resolution: the cheapest frame the engine can make, and the crispest once
        // OpenGL scales it up with nearest-neighbour filtering.
        args.add("-multiply");
        args.add("1");
        args.add("-truecolor");
        // Mocha Doom's audio is javax.sound, not Minecraft's OpenAL: the engine mixes its own
        // 22050 Hz stream and writes it to a second data line, so it runs alongside the game
        // without touching the OpenAL context. Anything that fails to open (no device, no MIDI
        // synth) falls back to the engine's own dummy drivers rather than failing the console.
        if (!Config.GAME_CONSOLE_SOUND) {
            args.add("-nosfx");
        }
        if (!Config.GAME_CONSOLE_MUSIC) {
            args.add("-nomusic");
        }
        if (!Config.GAME_CONSOLE_SOUND && !Config.GAME_CONSOLE_MUSIC) {
            args.add("-nosound");
        }
        return args;
    }

    /**
     * Aims the console at another key map. Called by the settings screen; the running engine keeps
     * the bindings it was started with, so this only takes effect the next time the console is
     * opened - which is also what the settings screen tells the player.
     */
    public void setBindings(final MTGameConsoleBindings newBindings) {
        if (newBindings != null) {
            this.bindings = newBindings;
        }
    }

    public MTGameConsoleBindings getBindings() {
        return bindings;
    }

    /** Where the bindings are stored, so the settings screen can save them in one place. */
    public File getBindingsFile() {
        return new File(runtimeDir, "keys.cfg");
    }

    private void releaseRenderer() {
        final DoomMain<?, ?> main = doom;
        if (main == null) {
            return;
        }
        final Object graphics = main.graphicSystem;
        if (graphics instanceof HeadlessRaster) {
            ((HeadlessRaster) graphics).shutdown();
        }
    }

    private void setStatus(final State newState, final String text, final int percent) {
        this.state = newState;
        this.status = text;
        this.progress = percent;
    }

    private void fail(final String message) {
        this.state = State.FAILED;
        this.status = message == null ? "未知错误" : message;
        this.progress = -1;
    }

    // ------------------------------------------------------------------ Engine.Host

    @Override
    public void frameReady(final DoomMain<?, ?> main) {
        final Object graphics = main.graphicSystem;
        if (!(graphics instanceof HeadlessRaster)) {
            return;
        }
        final int[] frame = ((HeadlessRaster) graphics).renderFrame();
        synchronized (frameLock) {
            final int count = Math.min(frame.length, back.length);
            System.arraycopy(frame, 0, back, 0, count);
            final int[] swap = front;
            front = back;
            back = swap;
            revision++;
        }
    }

    @Override
    public boolean running() {
        return running;
    }

    @Override
    public IDoomSystem createDoomSystem(final DoomMain<?, ?> main) {
        return new MTGameConsoleSystem(main, this);
    }

    @Override
    public void fatal(final String message, final Throwable cause) {
        MessTech.LOG.error("Game console fatal: {}", message, cause);
        fail(message);
        running = false;
    }

    // ------------------------------------------------------------------ GUI side

    public State getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    /** True once a frame has been produced and the GUI has not uploaded it yet. */
    public boolean hasNewFrame(final int since) {
        synchronized (frameLock) {
            return revision != since;
        }
    }

    public int getRevision() {
        synchronized (frameLock) {
            return revision;
        }
    }

    /** Copies the newest finished frame into {@code dest} with an opaque alpha. */
    public void copyFrame(final int[] dest) {
        synchronized (frameLock) {
            final int count = Math.min(front.length, dest.length);
            for (int i = 0; i < count; i++) {
                dest[i] = front[i] | 0xFF000000;
            }
        }
    }

    /**
     * Samples the keyboard and posts the edges to the engine.
     * <p>
     * Polling rather than listening is deliberate: a {@code GuiScreen} is only ever told about key
     * presses, never about releases, and Doom cannot stop walking without the release. LWJGL's own
     * key state is always current, and reading it here also means Minecraft's own bindings (E for
     * the inventory, / for chat) cannot swallow a key before Doom sees it.
     */
    public void pollInput() {
        final DoomMain<?, ?> main = doom;
        if (main == null || state != State.RUNNING) {
            return;
        }
        final MTGameConsoleBindings bound = bindings;
        for (int code = 0; code < keyState.length; code++) {
            final ScanCode sc = MTGameConsoleKeys.of(code);
            if (sc == null) {
                continue;
            }
            final boolean down = MTGameConsoleKeys.isDown(code);
            if (down == keyState[code]) {
                continue;
            }
            keyState[code] = down;
            // The physical key goes through as itself first, so anything DOOM reads by scan code
            // (its menus, for one) still sees the key the player actually pressed.
            main.PostEvent(down ? sc.doomEventDown : sc.doomEventUp);

            // Then the one action that is not a gamekeydown slot - the automap - whose rebound key
            // has to emit the scan code DOOM is listening for.
            final ScanCode extra = bound == null ? null : bound.extraScanCode(code);
            if (extra != null && extra != sc) {
                main.PostEvent(down ? extra.doomEventDown : extra.doomEventUp);
            }
        }
    }

    /** Releases every key the engine currently thinks is held (used when the window closes). */
    public void releaseKeys() {
        final DoomMain<?, ?> main = doom;
        if (main == null) {
            return;
        }
        final MTGameConsoleBindings bound = bindings;
        for (int code = 0; code < keyState.length; code++) {
            if (!keyState[code]) {
                continue;
            }
            keyState[code] = false;
            final ScanCode sc = MTGameConsoleKeys.of(code);
            if (sc != null) {
                main.PostEvent(sc.doomEventUp);
                final ScanCode extra = bound == null ? null : bound.extraScanCode(code);
                if (extra != null && extra != sc) {
                    main.PostEvent(extra.doomEventUp);
                }
            }
        }
    }

    /**
     * Asks the engine loop to finish its current frame and stop. Called from the engine's own
     * thread (DOOM's QUIT GAME) and from the GUI, so it must not wait for anything.
     */
    public void requestStop() {
        running = false;
    }

    /** Stops the engine and waits for its thread. Safe to call more than once. */
    public void stop() {
        running = false;
        final Thread t = thread;
        if (t == null || t == Thread.currentThread()) {
            return;
        }
        t.interrupt();
        try {
            t.join(3000L);
        } catch (final InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        keyStateFill(false);
    }

    private void keyStateFill(final boolean value) {
        for (int i = 0; i < keyState.length; i++) {
            keyState[i] = value;
        }
    }

    public int getThreads() {
        return threads;
    }
}
