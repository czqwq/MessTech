package com.MessTech.common.wireless;

import java.util.Locale;

/**
 * Access level of one wireless ME network, i.e. who may use it (bind ME hatches to it / select it for a core).
 *
 * <p>
 * Editing and deleting a network is <b>always</b> limited to its owner; the access level only widens who may use the
 * network. It is stored per network in {@link MTWirelessSavedData} (NBT {@code access}, one byte) and edited from the
 * core GUI's network settings page.
 */
public enum MTWirelessAccess {

    /** Only the owner may use the network. */
    PRIVATE,
    /** The owner and the players in the owner's ServerUtilities team may use the network. */
    TEAM,
    /** Every player may use the network. */
    PUBLIC;

    /** Cached {@link #values()} array; the enum constants never change at runtime. */
    public static final MTWirelessAccess[] VALUES = values();

    /** @return the stable numeric id of this level, i.e. its ordinal (stored in NBT as one byte). */
    public int id() {
        return ordinal();
    }

    /** @return the level with that id; out-of-range ids fall back to {@link #PRIVATE}. */
    public static MTWirelessAccess byId(int id) {
        return id < 0 || id >= VALUES.length ? PRIVATE : VALUES[id];
    }

    /** @return the next level in the {@code PRIVATE -> TEAM -> PUBLIC -> PRIVATE} cycle. */
    public MTWirelessAccess next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** @return the translation key of the level name, e.g. {@code "wirelessme.gui.access.team"}. */
    public String langKey() {
        return "wirelessme.gui.access." + name().toLowerCase(Locale.ROOT);
    }
}
