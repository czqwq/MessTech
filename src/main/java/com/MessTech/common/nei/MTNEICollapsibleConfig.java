package com.MessTech.common.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Pure text transformation behind {@link MTNEICollapsibleItems}: replaces MessTech's marker block inside NEI's
 * {@code config/NEI/collapsibleitems.cfg} and leaves everything outside of it untouched, so the GTNH default groups,
 * other mods' blocks and anything the player wrote stay exactly as they are.
 * <p>
 * The block uses NEI's own format ({@code codechicken.nei.CollapsibleItems#parseFile}): before each filter line an
 * optional {@code ; {...}} settings line gives the group a display name (here a lang key, so the name follows the
 * client language), and the filter line itself is an {@code ItemStackFilterParser} expression. NEI strips {@code #}
 * comments before parsing, so the two marker lines are invisible to it and only serve this class to find its block
 * again on the next launch.
 */
final class MTNEICollapsibleConfig {

    static final String BEGIN = "# BEGIN MESSTECH COLLAPSIBLE ITEMS";
    static final String END = "# END MESSTECH COLLAPSIBLE ITEMS";

    private MTNEICollapsibleConfig() {}

    /**
     * @param content the current {@code collapsibleitems.cfg} text (or NEI's defaults when the file does not exist yet)
     * @param groups  language key of the group name -> filter expression, in display order
     * @return {@code content} with the MessTech block replaced or appended; the newline style and every other line are
     *         preserved
     * @throws IllegalArgumentException when only one of the two marker lines is present (a hand-edited broken block)
     */
    static String update(String content, Map<String, String> groups) {
        final String newline = content.contains("\r\n") ? "\r\n" : "\n";
        final List<String> lines = new ArrayList<>(Arrays.asList(content.split("\\r\\n|\\n|\\r", -1)));

        int begin = lines.indexOf(BEGIN);
        int end = lines.indexOf(END);
        if ((begin == -1) != (end == -1) || begin > end) {
            throw new IllegalArgumentException("Incomplete MessTech collapsible items block");
        }

        final List<String> block = buildBlock(groups);
        if (begin != -1) {
            lines.subList(begin, end + 1)
                .clear();
            lines.addAll(begin, block);
        } else {
            // Append at the end of the file, keeping the trailing newline of the split (the last element is "" when the
            // file ends with a newline) after the block.
            int insertAt = lines.size();
            if (!lines.isEmpty() && lines.get(lines.size() - 1)
                .isEmpty()) {
                insertAt--;
            }
            if (insertAt > 0 && !lines.get(insertAt - 1)
                .trim()
                .isEmpty()) {
                lines.add(insertAt, "");
                insertAt++;
            }
            lines.addAll(insertAt, block);
        }
        return String.join(newline, lines);
    }

    private static List<String> buildBlock(Map<String, String> groups) {
        final List<String> block = new ArrayList<>();
        block.add(BEGIN);
        for (Map.Entry<String, String> group : groups.entrySet()) {
            if (group.getValue() == null || group.getValue()
                .isEmpty()) {
                continue;
            }
            // The settings line belongs to the filter line below it; NEI resolves the key against the client language
            // and falls back to showing the key itself when nothing is translated.
            block.add("; {\"unlocalizedName\": \"" + group.getKey() + "\"}");
            block.add(group.getValue());
        }
        block.add(END);
        return block;
    }
}
