/*
 * Copyright (C) 2017 Good Sign
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.MessTech.doom.mochadoom;

import java.io.IOException;

import com.MessTech.doom.doom.CVarManager;
import com.MessTech.doom.doom.ConfigManager;
import com.MessTech.doom.doom.DoomMain;
import com.MessTech.doom.i.DoomSystem;
import com.MessTech.doom.i.IDoomSystem;

/**
 * Engine facade, rewritten for embedding.
 *
 * Differences from upstream Mocha Doom:
 * - no AWT window is created; the host (Minecraft) supplies the frames and the input;
 * - the process is never terminated from here, and the game loop is host-controlled;
 * - the engine is created through bootstrap() instead of being a command line application.
 *
 * The rest of the engine still reaches the configuration through the static
 * Engine.getCVM() / Engine.getConfig() accessors (52 call sites), so the singleton is kept,
 * but it is now the host's to create.
 */
public class Engine {

    /** Everything the embedding application has to provide. */
    public interface Host {

        /** D_Display finished a frame: the finished ARGB frame is available. */
        default void frameReady(final DoomMain<?, ?> doom) {}

        /** D_DoomLoop keeps looping while this returns true. */
        default boolean running() {
            return true;
        }

        /** I_Quit / I_Error implementation. */
        default IDoomSystem createDoomSystem(final DoomMain<?, ?> doom) {
            return new DoomSystem(doom);
        }

        /** Last resort: an uncaught engine failure. */
        default void fatal(final String message, final Throwable cause) {}
    }

    private static final Host FALLBACK_HOST = new Host() {};
    private static volatile Host host = FALLBACK_HOST;
    private static volatile Engine instance;

    public final CVarManager cvm;
    public final ConfigManager cm;
    private final DoomMain<?, ?> DOOM;

    private Engine(final CVarManager cvm) throws IOException {
        /**
         * ConfigManager's own constructor reads Engine.getCVM(), and DoomStatus.CM = Engine.getConfig()
         * runs inside DoomStatus' field initialisers, i.e. before DoomMain's constructor completes.
         * So `instance', `cvm' and `cm' all have to be visible by then.
         */
        instance = this;
        this.cvm = cvm;
        this.cm = new ConfigManager();
        this.DOOM = new DoomMain<>();
    }

    /** Creates the one and only engine. Never opens a window, never exits the process. */
    public static Engine bootstrap(final CVarManager cvm, final Host newHost) throws IOException {
        synchronized (Engine.class) {
            host = newHost == null ? FALLBACK_HOST : newHost;
            return instance = new Engine(cvm);
        }
    }

    public static Host host() {
        return host;
    }

    public DoomMain<?, ?> doom() {
        return DOOM;
    }

    /** The engine's main loop - upstream's DoomMain.setupLoop(), bounded by Host.running(). */
    public void run() throws IOException {
        DOOM.setupLoop();
    }

    public static Engine getEngine() {
        return instance;
    }

    public static CVarManager getCVM() {
        return instance.cvm;
    }

    public static ConfigManager getConfig() {
        return instance.cm;
    }

    /** D_Display's page-flip hook. */
    public static void updateFrame() {
        host.frameReady(instance.DOOM);
    }
}
