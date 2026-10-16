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
 * Embedding hook (MessTech): a handful of engine files - the savegames and the translucency
 * cache - are named without any directory, so upstream resolves them against the process'
 * working directory. Inside Minecraft that directory is the game instance itself, which would
 * scatter {@code doomsav0.dsg} and {@code tranmap.dat} next to the world folders.
 * <p>
 * The host points this at the mod's own folder instead; with the default empty base the
 * behaviour is exactly upstream's.
 */
public final class HostPaths {

    private static volatile String base = "";

    /** Directory that bare engine file names are resolved against. Trailing separator optional. */
    public static void setBaseDirectory(final String dir) {
        if (dir == null || dir.isEmpty()) {
            base = "";
        } else if (dir.endsWith("/") || dir.endsWith("\\")) {
            base = dir;
        } else {
            base = dir + "/";
        }
    }

    public static String getBaseDirectory() {
        return base;
    }

    public static String resolve(final String name) {
        return base + name;
    }

    private HostPaths() {}
}
