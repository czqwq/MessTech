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

package com.MessTech.doom;

/**
 * Embedding hook (MessTech): Mocha Doom was written as a stand-alone application, so every one of
 * its fatal paths ends in {@code System.exit} - which inside Minecraft would take the whole game
 * down with it. Those call sites now throw this instead, and the host turns it into a message in
 * the game console's own window.
 */
public class DoomFatalError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DoomFatalError(final String message) {
        super(message);
    }

    public DoomFatalError(final String message, final Throwable cause) {
        super(message, cause);
    }
}
