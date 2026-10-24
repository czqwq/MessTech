package com.MessTech.common.gameconsole;

import org.lwjgl.input.Keyboard;

import com.MessTech.doom.g.Signals.ScanCode;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * LWJGL key code to Mocha Doom {@link ScanCode} mapping for the game console. Written out by hand, not computed: Mocha
 * Doom's
 * scan codes are DOS scan codes, and LWJGL's {@code KEY_*} constants agree with them across the main keyboard block but
 * not on
 * the arrow and editing keys (LWJGL {@code KEY_UP} is 200, Doom's {@code SC_UP} is 103) - a silent off-by-one there
 * would have
 * the player firing when they meant to walk.
 */
@SideOnly(Side.CLIENT)
public final class MTGameConsoleKeys {

    private static final ScanCode[] BY_LWJGL = new ScanCode[Keyboard.KEYBOARD_SIZE];

    private static void bind(final int lwjgl, final ScanCode scanCode) {
        if (lwjgl > 0 && lwjgl < BY_LWJGL.length) {
            BY_LWJGL[lwjgl] = scanCode;
        }
    }

    /** True while the key is physically held, straight from LWJGL's own state. */
    public static boolean isDown(final int lwjgl) {
        return lwjgl > 0 && lwjgl < BY_LWJGL.length && Keyboard.isKeyDown(lwjgl);
    }

    /** The Doom scan code for a raw key code, or null when the key is not part of the console's keyboard. */
    public static ScanCode of(final int lwjgl) {
        if (lwjgl <= 0 || lwjgl >= BY_LWJGL.length) {
            return null;
        }
        return BY_LWJGL[lwjgl];
    }

    private MTGameConsoleKeys() {}

    static {
        // Row of digits and the punctuation around them.
        bind(Keyboard.KEY_1, ScanCode.SC_1);
        bind(Keyboard.KEY_2, ScanCode.SC_2);
        bind(Keyboard.KEY_3, ScanCode.SC_3);
        bind(Keyboard.KEY_4, ScanCode.SC_4);
        bind(Keyboard.KEY_5, ScanCode.SC_5);
        bind(Keyboard.KEY_6, ScanCode.SC_6);
        bind(Keyboard.KEY_7, ScanCode.SC_7);
        bind(Keyboard.KEY_8, ScanCode.SC_8);
        bind(Keyboard.KEY_9, ScanCode.SC_9);
        bind(Keyboard.KEY_0, ScanCode.SC_0);
        bind(Keyboard.KEY_MINUS, ScanCode.SC_MINUS);
        bind(Keyboard.KEY_EQUALS, ScanCode.SC_EQUALS);
        bind(Keyboard.KEY_BACK, ScanCode.SC_BACKSPACE);
        bind(Keyboard.KEY_TAB, ScanCode.SC_TAB);

        // Letters.
        bind(Keyboard.KEY_Q, ScanCode.SC_Q);
        bind(Keyboard.KEY_W, ScanCode.SC_W);
        bind(Keyboard.KEY_E, ScanCode.SC_E);
        bind(Keyboard.KEY_R, ScanCode.SC_R);
        bind(Keyboard.KEY_T, ScanCode.SC_T);
        bind(Keyboard.KEY_Y, ScanCode.SC_Y);
        bind(Keyboard.KEY_U, ScanCode.SC_U);
        bind(Keyboard.KEY_I, ScanCode.SC_I);
        bind(Keyboard.KEY_O, ScanCode.SC_O);
        bind(Keyboard.KEY_P, ScanCode.SC_P);
        bind(Keyboard.KEY_LBRACKET, ScanCode.SC_LBRACE);
        bind(Keyboard.KEY_RBRACKET, ScanCode.SC_RBRACE);
        bind(Keyboard.KEY_RETURN, ScanCode.SC_ENTER);
        bind(Keyboard.KEY_LCONTROL, ScanCode.SC_LCTRL);
        bind(Keyboard.KEY_A, ScanCode.SC_A);
        bind(Keyboard.KEY_S, ScanCode.SC_S);
        bind(Keyboard.KEY_D, ScanCode.SC_D);
        bind(Keyboard.KEY_F, ScanCode.SC_F);
        bind(Keyboard.KEY_G, ScanCode.SC_G);
        bind(Keyboard.KEY_H, ScanCode.SC_H);
        bind(Keyboard.KEY_J, ScanCode.SC_J);
        bind(Keyboard.KEY_K, ScanCode.SC_K);
        bind(Keyboard.KEY_L, ScanCode.SC_L);
        bind(Keyboard.KEY_SEMICOLON, ScanCode.SC_SEMICOLON);
        bind(Keyboard.KEY_APOSTROPHE, ScanCode.SC_QUOTE);
        bind(Keyboard.KEY_GRAVE, ScanCode.SC_TILDE);
        bind(Keyboard.KEY_LSHIFT, ScanCode.SC_LSHIFT);
        bind(Keyboard.KEY_BACKSLASH, ScanCode.SC_BACKSLASH);
        bind(Keyboard.KEY_Z, ScanCode.SC_Z);
        bind(Keyboard.KEY_X, ScanCode.SC_X);
        bind(Keyboard.KEY_C, ScanCode.SC_C);
        bind(Keyboard.KEY_V, ScanCode.SC_V);
        bind(Keyboard.KEY_B, ScanCode.SC_B);
        bind(Keyboard.KEY_N, ScanCode.SC_N);
        bind(Keyboard.KEY_M, ScanCode.SC_M);
        bind(Keyboard.KEY_COMMA, ScanCode.SC_COMMA);
        bind(Keyboard.KEY_PERIOD, ScanCode.SC_PERIOD);
        bind(Keyboard.KEY_SLASH, ScanCode.SC_SLASH);
        bind(Keyboard.KEY_RSHIFT, ScanCode.SC_RSHIFT);

        // Modifiers and the space bar.
        bind(Keyboard.KEY_LMENU, ScanCode.SC_LALT);
        bind(Keyboard.KEY_RMENU, ScanCode.SC_RALT);
        bind(Keyboard.KEY_SPACE, ScanCode.SC_SPACE);
        bind(Keyboard.KEY_CAPITAL, ScanCode.SC_CAPSLK);
        bind(Keyboard.KEY_RCONTROL, ScanCode.SC_RCTRL);
        bind(Keyboard.KEY_LMETA, ScanCode.SC_LWIN);
        bind(Keyboard.KEY_RMETA, ScanCode.SC_RWIN);
        bind(Keyboard.KEY_APPS, ScanCode.SC_MENU);

        // Function keys. F1 goes to Doom as its own Escape: the console reserves the real Escape for closing its
        // window,
        // and Doom's menu - options, save, load, quit - would otherwise be unreachable. That costs the help screen,
        // which shares the key.
        bind(Keyboard.KEY_F1, ScanCode.SC_ESCAPE);
        bind(Keyboard.KEY_F2, ScanCode.SC_F2);
        bind(Keyboard.KEY_F3, ScanCode.SC_F3);
        bind(Keyboard.KEY_F4, ScanCode.SC_F4);
        bind(Keyboard.KEY_F5, ScanCode.SC_F5);
        bind(Keyboard.KEY_F6, ScanCode.SC_F6);
        bind(Keyboard.KEY_F7, ScanCode.SC_F7);
        bind(Keyboard.KEY_F8, ScanCode.SC_F8);
        bind(Keyboard.KEY_F9, ScanCode.SC_F9);
        bind(Keyboard.KEY_F10, ScanCode.SC_F10);
        bind(Keyboard.KEY_F11, ScanCode.SC_F11);
        bind(Keyboard.KEY_F12, ScanCode.SC_F12);

        // The key pad. Doom's default turn keys live here, so the console keeps them reachable.
        bind(Keyboard.KEY_NUMLOCK, ScanCode.SC_NUMLK);
        bind(Keyboard.KEY_SCROLL, ScanCode.SC_SCROLLLK);
        bind(Keyboard.KEY_NUMPAD7, ScanCode.SC_NUMKEY7);
        bind(Keyboard.KEY_NUMPAD8, ScanCode.SC_NUMKEY8);
        bind(Keyboard.KEY_NUMPAD9, ScanCode.SC_NUMKEY9);
        bind(Keyboard.KEY_SUBTRACT, ScanCode.SC_NPMINUS);
        bind(Keyboard.KEY_NUMPAD4, ScanCode.SC_NUMKEY4);
        bind(Keyboard.KEY_NUMPAD5, ScanCode.SC_NUMKEY5);
        bind(Keyboard.KEY_NUMPAD6, ScanCode.SC_NUMKEY6);
        bind(Keyboard.KEY_ADD, ScanCode.SC_NPPLUS);
        bind(Keyboard.KEY_NUMPAD1, ScanCode.SC_NUMKEY1);
        bind(Keyboard.KEY_NUMPAD2, ScanCode.SC_NUMKEY2);
        bind(Keyboard.KEY_NUMPAD3, ScanCode.SC_NUMKEY3);
        bind(Keyboard.KEY_NUMPAD0, ScanCode.SC_NUMKEY0);
        bind(Keyboard.KEY_DECIMAL, ScanCode.SC_NPDOT);
        bind(Keyboard.KEY_NUMPADENTER, ScanCode.SC_NPENTER);
        bind(Keyboard.KEY_DIVIDE, ScanCode.SC_NPSLASH);
        bind(Keyboard.KEY_MULTIPLY, ScanCode.SC_NPMULTIPLY);

        // Arrow and editing block - these are the ones that differ from Doom's own numbering.
        bind(Keyboard.KEY_PRIOR, ScanCode.SC_PGUP);
        bind(Keyboard.KEY_NEXT, ScanCode.SC_PGDOWN);
        bind(Keyboard.KEY_HOME, ScanCode.SC_HOME);
        bind(Keyboard.KEY_END, ScanCode.SC_END);
        bind(Keyboard.KEY_INSERT, ScanCode.SC_INSERT);
        bind(Keyboard.KEY_DELETE, ScanCode.SC_DELETE);
        bind(Keyboard.KEY_UP, ScanCode.SC_UP);
        bind(Keyboard.KEY_DOWN, ScanCode.SC_DOWN);
        bind(Keyboard.KEY_LEFT, ScanCode.SC_LEFT);
        bind(Keyboard.KEY_RIGHT, ScanCode.SC_RIGHT);
        bind(Keyboard.KEY_PAUSE, ScanCode.SC_PAUSE);
        bind(Keyboard.KEY_SYSRQ, ScanCode.SC_PRTSCRN);

        // Escape is deliberately absent: the console reserves it for closing its window, and Doom's menu hangs off F1
        // (see above).
    }
}
