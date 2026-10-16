package com.MessTech.doom.st;

import com.MessTech.doom.doom.DoomMain;

public abstract class AbstractStatusBar implements IDoomStatusBar {

    protected final DoomMain<?, ?> DOOM;

    public AbstractStatusBar(DoomMain<?, ?> DOOM) {
        this.DOOM = DOOM;
    }
}
