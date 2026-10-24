package com.MessTech.common.gameconsole;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.MessTech.doom.doom.DoomMain;
import com.MessTech.doom.g.Signals.ScanCode;
import com.MessTech.init.MessTech;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The game console's own key bindings. Deliberately <em>not</em> Minecraft {@code KeyBinding}s: those are global,
 * one-key-per-action and live in {@code options.txt}, and every key the console wants (E, Tab, Ctrl, Shift, 1-9) is
 * already a
 * vanilla key, so registering them would only fill the controls screen with conflict warnings. They mean something only
 * while
 * the console's window is open, so they live in its own file, next to its own DOOM.
 * <p>
 * The physical key map ({@link MTGameConsoleKeys}) never changes: it answers "which DOOM scan code is this keyboard
 * key" for
 * the whole keyboard, once. Each {@link Action} has a <em>reference</em> scan code - the one DOOM listens for - and,
 * for the
 * ten actions DOOM reads out of {@code gamekeydown}, the slot that code indexes: binding a key makes it drive the same
 * slot
 * through {@code DoomStatus#aliasKey}, the hook that also makes the arrow keys work next to WASD.
 * {@link Action#AUTOMAP} is
 * the exception (DOOM reads it as a scan code, so a rebound key also emits that code). DOOM's own OPTIONS -> KEYS page
 * is
 * therefore not a second place to change these: the console forces the reference codes on every start.
 */
@SideOnly(Side.CLIENT)
public final class MTGameConsoleBindings {

    /** How many keys one action may hold. Two is what makes "WASD *or* the arrow keys" expressible. */
    public static final int SLOTS_PER_ACTION = 2;

    /**
     * One thing the player can press a key for. {@code reference} is the scan code DOOM listens for; {@code slot} is
     * the
     * entry it looks at in {@code gamekeydown}, or -1 when DOOM reads the scan code itself (the automap key does).
     */
    public enum Action {

        FORWARD("forward", ScanCode.SC_W),
        BACK("back", ScanCode.SC_S),
        TURN_LEFT("turnLeft", ScanCode.SC_A),
        TURN_RIGHT("turnRight", ScanCode.SC_D),
        STRAFE_LEFT("strafeLeft", ScanCode.SC_COMMA),
        STRAFE_RIGHT("strafeRight", ScanCode.SC_PERIOD),
        FIRE("fire", ScanCode.SC_LCTRL),
        USE("use", ScanCode.SC_E),
        RUN("run", ScanCode.SC_LSHIFT),
        STRAFE("strafeModifier", ScanCode.SC_LALT),
        /**
         * The one action DOOM reads as a scan code rather than out of {@code gamekeydown}, hence the explicit -1: a key
         * bound to it emits {@code SC_TAB} instead of driving a slot.
         */
        AUTOMAP("automap", ScanCode.SC_TAB, -1),

        WEAPON_1("weapon1", ScanCode.SC_1),
        WEAPON_2("weapon2", ScanCode.SC_2),
        WEAPON_3("weapon3", ScanCode.SC_3),
        WEAPON_4("weapon4", ScanCode.SC_4),
        WEAPON_5("weapon5", ScanCode.SC_5),
        WEAPON_6("weapon6", ScanCode.SC_6),
        WEAPON_7("weapon7", ScanCode.SC_7),
        WEAPON_8("weapon8", ScanCode.SC_8),
        WEAPON_9("weapon9", ScanCode.SC_9),
        WEAPON_0("weapon0", ScanCode.SC_0);

        /** Stable id, and the key used in the bindings file. */
        public final String id;
        /** The scan code DOOM itself listens for. */
        public final ScanCode reference;
        /** {@code gamekeydown} index this action drives, or -1 when DOOM reads the scan code directly. */
        public final int slot;

        Action(final String id, final ScanCode reference) {
            this(id, reference, reference.ordinal());
        }

        Action(final String id, final ScanCode reference, final int slot) {
            this.id = id;
            this.reference = reference;
            this.slot = slot;
        }

        public boolean isWeapon() {
            return ordinal() >= WEAPON_1.ordinal();
        }

        /** Localisation key of the name the settings screen shows. */
        public String labelKey() {
            return "messtech.gameConsole.key." + id;
        }

        public static Action byId(final String id) {
            for (final Action action : values()) {
                if (action.id.equals(id)) {
                    return action;
                }
            }
            return null;
        }
    }

    /** The actions in the order the settings screen lists them, split into its two pages. */
    public static final List<Action> MOVEMENT_ACTIONS = Arrays.asList(
        Action.FORWARD,
        Action.BACK,
        Action.TURN_LEFT,
        Action.TURN_RIGHT,
        Action.STRAFE_LEFT,
        Action.STRAFE_RIGHT,
        Action.FIRE,
        Action.USE,
        Action.RUN,
        Action.STRAFE,
        Action.AUTOMAP);

    public static final List<Action> WEAPON_ACTIONS = Arrays.asList(
        Action.WEAPON_1,
        Action.WEAPON_2,
        Action.WEAPON_3,
        Action.WEAPON_4,
        Action.WEAPON_5,
        Action.WEAPON_6,
        Action.WEAPON_7,
        Action.WEAPON_8,
        Action.WEAPON_9,
        Action.WEAPON_0);

    private static final String HEADER = "# MessTech game console key bindings.\n"
        + "# One line per action, up to two LWJGL key codes; the name in the comment is what the\n"
        + "# console's own key screen shows. Delete this file to get the defaults back.\n";

    private final EnumMap<Action, int[]> bindings = new EnumMap<>(Action.class);

    /** Extra scan codes to emit for keys bound to {@link Action#AUTOMAP}; rebuilt by {@link #apply}. */
    private final Map<Integer, ScanCode> extraScanCodes = new java.util.HashMap<>();

    public MTGameConsoleBindings() {
        resetToDefaults();
    }

    public int get(final Action action, final int slot) {
        final int[] keys = bindings.get(action);
        return keys == null || slot < 0 || slot >= keys.length ? -1 : keys[slot];
    }

    /** Sets one slot; a negative {@code key} clears it. Refuses to leave a key used twice. */
    public void set(final Action action, final int slot, final int key) {
        if (key >= 0) {
            clearKeyEverywhere(key);
        }
        final int[] keys = bindings.computeIfAbsent(action, a -> emptySlots());
        if (slot >= 0 && slot < keys.length) {
            keys[slot] = key;
        }
    }

    private void clearKeyEverywhere(final int key) {
        for (final int[] keys : bindings.values()) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] == key) {
                    keys[i] = -1;
                }
            }
        }
    }

    public void resetToDefaults() {
        bindings.clear();
        for (final Action action : Action.values()) {
            bindings.put(action, defaultsOf(action));
        }
    }

    private static int[] emptySlots() {
        final int[] keys = new int[SLOTS_PER_ACTION];
        Arrays.fill(keys, -1);
        return keys;
    }

    private static int[] defaultsOf(final Action action) {
        final int[] keys = emptySlots();
        switch (action) {
            case FORWARD:
                keys[0] = Keyboard.KEY_W;
                keys[1] = Keyboard.KEY_UP;
                break;
            case BACK:
                keys[0] = Keyboard.KEY_S;
                keys[1] = Keyboard.KEY_DOWN;
                break;
            case TURN_LEFT:
                keys[0] = Keyboard.KEY_A;
                keys[1] = Keyboard.KEY_LEFT;
                break;
            case TURN_RIGHT:
                keys[0] = Keyboard.KEY_D;
                keys[1] = Keyboard.KEY_RIGHT;
                break;
            case STRAFE_LEFT:
                keys[0] = Keyboard.KEY_COMMA;
                break;
            case STRAFE_RIGHT:
                keys[0] = Keyboard.KEY_PERIOD;
                break;
            case FIRE:
                keys[0] = Keyboard.KEY_LCONTROL;
                break;
            case USE:
                keys[0] = Keyboard.KEY_E;
                keys[1] = Keyboard.KEY_SPACE;
                break;
            case RUN:
                keys[0] = Keyboard.KEY_LSHIFT;
                keys[1] = Keyboard.KEY_RSHIFT;
                break;
            case STRAFE:
                keys[0] = Keyboard.KEY_LMENU;
                break;
            case AUTOMAP:
                keys[0] = Keyboard.KEY_TAB;
                break;
            default:
                // weapon1 .. weapon0 sit on the digit row, in order.
                keys[0] = Keyboard.KEY_1 + (action.ordinal() - Action.WEAPON_1.ordinal());
                break;
        }
        return keys;
    }

    /** A key's display name, as LWJGL knows it ("W", "UP", "SPACE"). */
    public static String keyName(final int key) {
        if (key < 0) {
            return "";
        }
        final String name = Keyboard.getKeyName(key);
        return name == null ? "#" + key : name;
    }

    /**
     * Puts the current bindings onto a freshly built engine. Called on every start, right after the engine read its own
     * config, so DOOM's own KEYS page can never diverge from this file.
     */
    public void apply(final DoomMain<?, ?> doom) {
        // The ten slots DOOM reads out of gamekeydown get their reference scan codes back; the player's keys reach them
        // through aliases instead.
        doom.key_up = ScanCode.SC_W.ordinal();
        doom.key_down = ScanCode.SC_S.ordinal();
        doom.key_left = ScanCode.SC_A.ordinal();
        doom.key_right = ScanCode.SC_D.ordinal();
        doom.key_strafeleft = ScanCode.SC_COMMA.ordinal();
        doom.key_straferight = ScanCode.SC_PERIOD.ordinal();
        doom.key_fire = ScanCode.SC_LCTRL.ordinal();
        doom.key_use = ScanCode.SC_E.ordinal();
        doom.key_speed = ScanCode.SC_LSHIFT.ordinal();
        doom.key_strafe = ScanCode.SC_LALT.ordinal();
        doom.key_recordstop = ScanCode.SC_Q.ordinal();

        extraScanCodes.clear();
        for (final Action action : Action.values()) {
            for (int slot = 0; slot < SLOTS_PER_ACTION; slot++) {
                final int key = get(action, slot);
                if (key < 0) {
                    continue;
                }
                final ScanCode scanCode = MTGameConsoleKeys.of(key);
                if (scanCode == null) {
                    // A key the console's keyboard map does not know would never be forwarded, so binding it would
                    // silently do nothing. Say so in the log.
                    MessTech.LOG.warn(
                        "Game console: key {} ({}) is not part of the console keyboard, ignored for {}",
                        key,
                        keyName(key),
                        action.id);
                    continue;
                }
                if (action.slot >= 0) {
                    doom.aliasKey(scanCode, action.slot);
                } else {
                    extraScanCodes.put(key, action.reference);
                }
            }
        }
    }

    /** For a key DOOM reads as a scan code (the automap), the code its binding makes it emit. */
    public ScanCode extraScanCode(final int lwjglKey) {
        return extraScanCodes.get(lwjglKey);
    }

    public static MTGameConsoleBindings load(final File file) {
        final MTGameConsoleBindings bindings = new MTGameConsoleBindings();
        if (file == null || !file.isFile()) {
            return bindings;
        }
        try {
            for (final String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final int equals = trimmed.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                final Action action = Action.byId(
                    trimmed.substring(0, equals)
                        .trim());
                if (action == null) {
                    continue;
                }
                final int[] keys = emptySlots();
                // The file writes a "# W / UP" comment after the codes; drop it before parsing, or the second key would
                // parse as garbage and come back unbound.
                String value = trimmed.substring(equals + 1);
                final int comment = value.indexOf('#');
                if (comment >= 0) {
                    value = value.substring(0, comment);
                }
                final String[] parts = value.split(",");
                for (int i = 0; i < keys.length && i < parts.length; i++) {
                    try {
                        final int key = Integer.parseInt(parts[i].trim());
                        keys[i] = key > 0 ? key : -1;
                    } catch (final NumberFormatException ignored) {
                        // Leave the slot empty.
                    }
                }
                bindings.bindings.put(action, keys);
            }
        } catch (final IOException e) {
            MessTech.LOG.warn("Game console: could not read {}", file, e);
        }
        return bindings;
    }

    public void save(final File file) {
        final StringBuilder text = new StringBuilder(HEADER);
        for (final Action action : Action.values()) {
            text.append(action.id)
                .append('=')
                .append(get(action, 0))
                .append(',')
                .append(get(action, 1));
            final StringBuilder names = new StringBuilder();
            for (int slot = 0; slot < SLOTS_PER_ACTION; slot++) {
                final String name = keyName(get(action, slot));
                if (!name.isEmpty()) {
                    names.append(names.length() == 0 ? "" : " / ")
                        .append(name);
                }
            }
            text.append("  # ")
                .append(names.length() == 0 ? "(unbound)" : names)
                .append('\n');
        }
        try {
            final File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                // noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            Files.write(
                file.toPath(),
                text.toString()
                    .getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            MessTech.LOG.warn("Game console: could not write {}", file, e);
        }
    }
}
