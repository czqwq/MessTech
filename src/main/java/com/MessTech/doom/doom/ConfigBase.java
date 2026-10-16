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

package com.MessTech.doom.doom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.MessTech.doom.HostPaths;
import com.MessTech.doom.data.dstrings;
import com.MessTech.doom.m.Settings;
import com.MessTech.doom.mochadoom.Engine;
import com.MessTech.doom.utils.OSValidator;
import com.MessTech.doom.utils.ResourceIO;

/**
 * Manages loading different config files from different places
 * (the part about different places is still unfinished)
 * 
 * @author Good Sign
 */
public enum ConfigBase {

    WINDOWS("default.cfg", "USERPROFILE"),
    UNIX(".doomrc", "HOME");

    /**
     * Early detection of the system and setting this is important to define global config Files
     */
    public static final ConfigBase CURRENT = OSValidator.isMac() || OSValidator.isUnix() ? UNIX : WINDOWS;

    /**
     * Reference these in Settings.java to set which file they will go on by default
     */
    public static final Files FILE_DOOM = new Files(CURRENT.defaultConfigName, Enum::compareTo),
        FILE_MOCHADOOM = new Files("mochadoom.cfg");

    public final String defaultConfigName;
    public final String env;

    ConfigBase(final String fileName, final String env) {
        this.defaultConfigName = fileName;
        this.env = env;
    }

    public static class Files {

        private static String folder;

        public final Comparator<Settings> comparator;
        public final String fileName;
        public boolean changed = true;

        private String[] paths;

        public Files(String fileName) {
            this(fileName, Comparator.comparing(Enum::name, String::compareTo));
        }

        public Files(String fileName, Comparator<Settings> comparator) {
            this.fileName = fileName;
            this.comparator = comparator;
        }

        public Optional<ResourceIO> firstValidPathIO() {
            return Arrays.stream(getPaths())
                .map(ResourceIO::new)
                .filter(ResourceIO::exists)
                .findFirst();
        }

        public ResourceIO workDirIO() {
            return new ResourceIO(getFolder() + fileName);
        }

        /**
         * Get file / paths combinations
         * 
         * @return a one or more path to the file
         */
        private String[] getPaths() {
            if (paths != null) {
                return paths;
            }

            String getPath = null;

            try { // get it if have rights to do, otherwise ignore and use only current folder
                getPath = System.getenv(CURRENT.env);
            } catch (SecurityException ex) {}

            if (getPath == null || "".equals(getPath)) {
                return new String[] { getFolder() + fileName };
            }

            getPath += System.getProperty("file.separator");
            return paths = new String[] {
                /**
                 * Uncomment the next line and it will load default.cfg and mochadoom.cfg from user home dir
                 * I find it undesirable - it can load some unrelated file and even write it at exit
                 * - Good Sign 2017/04/19
                 */

                // getPath + folder + fileName,
                getFolder() + fileName };
        }

        /**
         * Embedding hook (MessTech): the directory the engine's own config files are read from and
         * written to. Upstream that was always the process' working directory (or {@code data/}
         * under the developer switches), which inside Minecraft would drop {@code default.cfg} and
         * {@code mochadoom.cfg} next to the world folders. The host points it at the game console's
         * own directory instead; an empty base - the default - keeps upstream's behaviour exactly.
         * <p>
         * Note that {@code -config} only ever replaced the list of files to <em>read</em>:
         * {@code Settings} binds every setting to the two static file constants below, so this
         * folder is what decides where they are written.
         */
        private static String getFolder() {
            if (folder != null) {
                return folder;
            }
            final String devFolder = Engine.getCVM()
                .bool(CommandVariable.SHDEV)
                || Engine.getCVM()
                    .bool(CommandVariable.REGDEV)
                || Engine.getCVM()
                    .bool(CommandVariable.FR1DEV)
                || Engine.getCVM()
                    .bool(CommandVariable.FRDMDEV)
                || Engine.getCVM()
                    .bool(CommandVariable.FR2DEV)
                || Engine.getCVM()
                    .bool(CommandVariable.COMDEV) ? dstrings.DEVDATA + System.getProperty("file.separator") : "";
            return folder = HostPaths.getBaseDirectory() + devFolder;
        }
    }

    /**
     * To be able to look for config in several places
     * Still unfinished
     */
    public static List<Files> getFiles() {
        final List<Files> ret = new ArrayList<>();

        /**
         * If user supplied -config argument, it will only use the values from these files instead of defaults
         */
        if (!Engine.getCVM()
            .with(
                CommandVariable.CONFIG,
                0,
                (String[] fileNames) -> Arrays.stream(fileNames)
                    .map(Files::new)
                    .forEach(ret::add))

        /**
         * If there is no such argument, load default.cfg (or .doomrc) and mochadoom.cfg
         */
        ) {
            ret.add(FILE_DOOM);
            ret.add(FILE_MOCHADOOM);
        }

        return ret;
    }
}
