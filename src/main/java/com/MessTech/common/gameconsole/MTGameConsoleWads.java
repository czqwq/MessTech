package com.MessTech.common.gameconsole;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.MessTech.init.Config;
import com.MessTech.init.MessTech;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where the game console's data lives, and how it gets there.
 * <p>
 * Doom needs an IWAD to run at all, and id's original ones are commercial - they cannot ship with
 * the mod. What this does instead, in order:
 * <ol>
 * <li>a WAD named in the config wins;</li>
 * <li>otherwise any IWAD the player dropped into the console's folder is used (the list is Mocha
 * Doom's own {@code DoomVersion} list, so {@code doom.wad}, {@code doom2.wad}, {@code tnt.wad} and
 * the Freedoom ones all count);</li>
 * <li>otherwise, if downloading is enabled, Freedoom Phase 1 is fetched from its GitHub release
 * and unpacked. Freedoom is a free, BSD-licensed Doom whose data files are redistributable, so it
 * is the sensible default - nothing commercial is ever downloaded or shipped.</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public final class MTGameConsoleWads {

    /**
     * Every base name Mocha Doom's {@code defines.DoomVersion} recognises as an IWAD, in the order
     * the engine itself probes them - so a folder holding several uses the same one the engine
     * would have picked on its own.
     */
    private static final String[] IWAD_NAMES = { "doom2f.wad", "doom2.wad", "plutonia.wad", "tnt.wad", "xbla.wad",
        "doomu.wad", "udoom.wad", "doom.wad", "doom1.wad", "freedm.wad", "freedoom1.wad", "freedoom2.wad" };

    /** File inside the Freedoom release archive that the console wants. */
    private static final String FREEDOOM_IWAD = "freedoom1.wad";

    /** Reports download and unpacking progress. {@code percent} is negative when unknown. */
    public interface Progress {

        void report(String text, int percent);
    }

    /** The console's own folder: WAD, engine config, savegames and the translucency cache. */
    public static File runtimeDirectory() {
        final String configured = Config.GAME_CONSOLE_DIR;
        final File dir = new File(configured);
        if (dir.isAbsolute()) {
            return dir;
        }
        return new File(
            Loader.instance()
                .getConfigDir(),
            configured);
    }

    public static void ensureDirectory(final File dir) {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("无法创建游戏机目录: " + dir);
        }
    }

    /**
     * Finds an IWAD, downloading Freedoom if there is none and downloads are allowed.
     *
     * @return the readable IWAD, or null when there is none and none could be fetched
     */
    public static File resolveIwad(final File dir, final Progress progress) {
        final File configured = configuredIwad(dir);
        if (configured != null) {
            return configured;
        }

        final File existing = findIwad(dir);
        if (existing != null) {
            return existing;
        }

        if (!Config.GAME_CONSOLE_DOWNLOAD_FREEDOOM) {
            return null;
        }

        return downloadFreedoom(dir, progress);
    }

    private static File configuredIwad(final File dir) {
        final String name = Config.GAME_CONSOLE_WAD;
        if (name == null || name.trim()
            .isEmpty()) {
            return null;
        }
        final File file = new File(name.trim());
        final File resolved = file.isAbsolute() ? file : new File(dir, name.trim());
        return resolved.isFile() && resolved.canRead() ? resolved : null;
    }

    public static File findIwad(final File dir) {
        for (final String name : IWAD_NAMES) {
            final File candidate = new File(dir, name);
            if (candidate.isFile() && candidate.canRead()) {
                return candidate;
            }
        }
        // Case-insensitive fallback: Windows players often have DOOM2.WAD.
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                final String lower = file.getName()
                    .toLowerCase(Locale.ROOT);
                for (final String name : IWAD_NAMES) {
                    if (lower.equals(name)) {
                        return file;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fetches the Freedoom release archive and unpacks {@value #FREEDOOM_IWAD} out of it. The
     * download goes to a {@code .part} file first, so an interrupted attempt cannot leave a
     * half-written WAD that the engine would then fail to parse.
     */
    public static File downloadFreedoom(final File dir, final Progress progress) {
        final File target = new File(dir, FREEDOOM_IWAD);
        final File partial = new File(dir, FREEDOOM_IWAD + ".part");
        final String url = Config.GAME_CONSOLE_FREEDOOM_URL;

        MessTech.LOG.info("Game console: downloading Freedoom from {}", url);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "MessTech-GameConsole");
            final int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                if (progress != null) {
                    progress.report("下载失败（HTTP " + code + "）", -1);
                }
                return null;
            }

            final long total = connection.getContentLengthLong();
            try (InputStream raw = new BufferedInputStream(connection.getInputStream(), 1 << 16);
                ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    final String entryName = entry.getName()
                        .toLowerCase(Locale.ROOT);
                    if (!entryName.endsWith("/" + FREEDOOM_IWAD) && !entryName.equals(FREEDOOM_IWAD)) {
                        continue;
                    }
                    copy(zip, partial, total, progress);
                    if (partial.renameTo(target) || (target.delete() && partial.renameTo(target))) {
                        MessTech.LOG.info("Game console: Freedoom ready at {}", target);
                        return findIwad(dir);
                    }
                    if (progress != null) {
                        progress.report("无法写入 " + target.getName(), -1);
                    }
                    return null;
                }
            }

            if (progress != null) {
                progress.report("压缩包里没有找到 " + FREEDOOM_IWAD, -1);
            }
            return null;
        } catch (final IOException e) {
            MessTech.LOG.warn("Game console: Freedoom download failed", e);
            if (progress != null) {
                progress.report("下载失败：" + e.getMessage(), -1);
            }
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (partial.exists()) {
                // noinspection ResultOfMethodCallIgnored
                partial.delete();
            }
        }
    }

    private static void copy(final InputStream in, final File target, final long total, final Progress progress)
        throws IOException {
        final byte[] buffer = new byte[1 << 16];
        long read = 0L;
        int lastPercent = -1;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target), 1 << 16)) {
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
                read += count;
                if (progress != null && total > 0L) {
                    final int percent = (int) Math.min(100L, read * 100L / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress.report("正在下载 Freedoom（首次打开需要一次）…", percent);
                    }
                }
            }
        }
    }

    private MTGameConsoleWads() {}
}
