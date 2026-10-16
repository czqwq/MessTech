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

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.MessTech.doom.p.ActiveStates;
import com.MessTech.doom.v.graphics.Patches;

/**
 * Facility to manage Logger Levels for different classes
 * All of that should be used instead of System.err.println for debug
 *
 * The AWT-event LogEvent() helper was dropped with the awt package.
 *
 * @author Good Sign
 */
public class Loggers {

    private static final Level DEFAULT_LEVEL = Level.WARNING;

    private static final Map<Level, Logger> PARENT_LOGGERS_MAP = Stream
        .of(Level.FINE, Level.FINER, Level.FINEST, Level.INFO, Level.SEVERE, Level.WARNING)
        .collect(Collectors.toMap(l -> l, Loggers::newLoggerHandlingLevel));

    private static final Logger DEFAULT_LOGGER = PARENT_LOGGERS_MAP.get(DEFAULT_LEVEL);
    private static final HashMap<String, Logger> INDIVIDUAL_CLASS_LOGGERS = new HashMap<>();

    static {
        INDIVIDUAL_CLASS_LOGGERS.put(ActiveStates.class.getName(), PARENT_LOGGERS_MAP.get(Level.FINER));
        INDIVIDUAL_CLASS_LOGGERS.put(Patches.class.getName(), PARENT_LOGGERS_MAP.get(Level.INFO));
        // Embedding (MessTech): which music route the console ended up on belongs in the log, since
        // "no music" is otherwise indistinguishable from "no patch table in this WAD".
        INDIVIDUAL_CLASS_LOGGERS.put("com.MessTech.doom.s.opl.OplMusicModule", PARENT_LOGGERS_MAP.get(Level.INFO));
    }

    public static Logger getLogger(final String className) {
        final Logger ret = Logger.getLogger(className);
        ret.setParent(INDIVIDUAL_CLASS_LOGGERS.getOrDefault(className, DEFAULT_LOGGER));

        return ret;
    }

    private Loggers() {}

    private static Logger newLoggerHandlingLevel(final Level l) {
        final OutHandler h = new OutHandler();
        h.setLevel(l);
        final Logger ret = Logger.getAnonymousLogger();
        ret.setUseParentHandlers(false);
        ret.setLevel(l);
        ret.addHandler(h);
        return ret;
    }

    private static final class OutHandler extends ConsoleHandler {

        @Override
        @SuppressWarnings("UseOfSystemOutOrSystemErr")
        protected synchronized void setOutputStream(final OutputStream out) throws SecurityException {
            super.setOutputStream(System.out);
        }
    }
}
