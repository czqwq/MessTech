package com.MessTech.common.nei;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.MessTech.common.items.MTItemList;
import com.MessTech.common.items.MTNACComponentItem;
import com.MessTech.init.MessTech;

import codechicken.nei.NEIClientConfig;
import cpw.mods.fml.common.registry.GameData;

/**
 * NEI collapsible item groups for MessTech, shaped after Twist Space Technology's plugin (see
 * {@code tmp/Twist-Space-Technology-Mod-main/.../nei/NEICollapsibleItems.java}): NEI has no API for this feature, it
 * only reads {@code config/NEI/collapsibleitems.cfg} in {@code CollapsibleItems.load()}. That method runs in NEI's
 * plugin
 * loader thread <em>after</em> every {@code IConfigureNEI.loadConfig()}, so this class rewrites the file from
 * {@link NEI_MessTechConfig#loadConfig()} and NEI picks the groups up in the same startup.
 * <p>
 * The groups only affect the item panel (families are shown as one collapsible slot, collapsed by default); recipe
 * pages
 * and every actual stack keep working unchanged. Group names are lang keys so they follow the client language.
 */
final class MTNEICollapsibleItems {

    /** NEI's own default file, used as the base when the player has none yet (NEI would copy it later anyway). */
    private static final String DEFAULT_RESOURCE = "/assets/nei/cfg/collapsibleitems.cfg";
    private static final String CONFIG_NAME = "collapsibleitems.cfg";

    private MTNEICollapsibleItems() {}

    /** Builds the groups and rewrites NEI's collapsible items file; never throws into the NEI plugin loader. */
    static void load() {
        final Map<String, String> groups = new LinkedHashMap<>();
        // The three module hatch families, one group each: selectors are exact gt.blockmachines metas taken from the
        // registered item list, so moving MT_ID cannot leave stale ids behind.
        put(groups, "messtech.nei.collapsible.speed", selectors(MTItemList.SPEED_MODULES));
        put(groups, "messtech.nei.collapsible.eu", selectors(MTItemList.EU_MODULES));
        put(groups, "messtech.nei.collapsible.parallel", selectors(MTItemList.PARALLEL_MODULES));
        // The NAC component item is a single item with one metadata value per CircuitComponent, so one item-wide filter
        // folds all of them.
        put(groups, "messtech.nei.collapsible.nac", itemFilter(MTNACComponentItem.INSTANCE));
        if (groups.isEmpty()) return;

        final File config = new File(NEIClientConfig.configDir, CONFIG_NAME);
        final Path path = config.toPath();
        // NEI reads and writes this file with the platform charset (FileReader/FileWriter), so match it - the lines we
        // generate are pure ASCII, but the rest of the file is the player's.
        final Charset charset = Charset.defaultCharset();
        try {
            final String original = Files.exists(path) ? new String(Files.readAllBytes(path), charset)
                : defaultContent(charset);
            final String updated = MTNEICollapsibleConfig.update(original, groups);
            if (Files.exists(path) && original.equals(updated)) return;
            writeAtomically(path, updated.getBytes(charset));
            MessTech.LOG.info("Updated MessTech NEI collapsible item groups.");
        } catch (IOException | IllegalArgumentException e) {
            MessTech.LOG.error("Failed to update MessTech NEI collapsible item groups", e);
        }
    }

    private static void put(Map<String, String> groups, String langKey, String filter) {
        if (filter != null && !filter.isEmpty()) groups.put(langKey, filter);
    }

    /** Exact {@code <registryName:meta>} selectors for a family of registered machines/hatches. */
    private static String selectors(MTItemList[] entries) {
        if (entries == null) return "";
        final Set<String> selectors = new LinkedHashSet<>();
        for (MTItemList entry : entries) {
            if (entry == null || !entry.hasBeenSet()) continue;
            final ItemStack stack = entry.getInternalStack_unsafe();
            if (stack == null || stack.getItem() == null) continue;
            final String name = GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
            if (name != null) selectors.add("<" + name + ":" + stack.getItemDamage() + ">");
        }
        return String.join(" | ", selectors);
    }

    /**
     * Regex selector matching every metadata value of one item (NEI's string identifier filter only checks the name).
     */
    private static String itemFilter(Item item) {
        if (item == null) return "";
        final String name = GameData.getItemRegistry()
            .getNameForObject(item);
        return name == null ? "" : "r/^" + Pattern.quote(name) + "$/";
    }

    private static String defaultContent(Charset charset) throws IOException {
        try (InputStream resource = NEIClientConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (resource == null) throw new IOException("Missing NEI default " + CONFIG_NAME);
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = resource.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), charset);
        }
    }

    /** Temp file in the same directory plus an atomic move, so a crash mid-write cannot truncate NEI's config. */
    private static void writeAtomically(Path path, byte[] content) throws IOException {
        final Path directory = path.getParent();
        Files.createDirectories(directory);
        final Path temporary = Files.createTempFile(directory, "collapsibleitems-messtech-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
