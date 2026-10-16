package com.MessTech.doom.s;

import com.MessTech.doom.w.IWadLoader;

/**
 * For music modules that need lumps out of the WAD rather than just a song handed to them.
 * <p>
 * {@link IMusic#InitMusic()} takes no arguments, and most modules do not need any - but an OPL
 * module has to read the {@code GENMIDI} patch table before it can play a note, and that lives in
 * the WAD. {@code DoomMain} passes the loader to any module that asks for it, right before calling
 * {@code InitMusic()}, so this stays a capability rather than a change to the interface everyone
 * implements.
 */
public interface IWadAware {

    void setWadLoader(IWadLoader loader);
}
