package com.MessTech.doom.s;

//

import com.MessTech.doom.doom.CVarManager;
import com.MessTech.doom.doom.CommandVariable;

// MUSIC I/O
//

public interface IMusic {

    void InitMusic();

    void ShutdownMusic();

    // Volume.
    void SetMusicVolume(int volume);

    /** PAUSE game handling. */
    void PauseSong(int handle);

    void ResumeSong(int handle);

    /**
     * Registers a song handle to song data.
     * This should handle any conversions from MUS/MIDI/OPL/etc.
     * 
     */
    int RegisterSong(byte[] data);

    /**
     * Called by anything that wishes to start music.
     * plays a song, and when the song is done,
     * starts playing it again in an endless loop.
     * Horrible thing to do, considering.
     */

    void PlaySong(int handle, boolean looping);

    /** Stops a song over 3 seconds. */
    void StopSong(int handle);

    /** See above (register), then think backwards */
    void UnRegisterSong(int handle);

    public static IMusic chooseModule(CVarManager CVM) {
        if (CVM.bool(CommandVariable.NOMUSIC) || CVM.bool(CommandVariable.NOSOUND)) {
            return new DummyMusic();
        } else {
            /**
             * Embedding (MessTech): DOOM's songs are FM music written for the OPL2, and the WAD
             * carries the matching patch table, so the console emulates the chip
             * ({@code opl.OplMusicModule}) rather than asking the JDK for a MIDI synthesiser. The
             * synthesiser lives in a package Java 9 and later do not export, which made the music
             * need a JVM flag to be mixed into the console's output; arithmetic has no such problem.
             * {@link DavidMusicModule} and {@link FinnwMusicModule} still send MIDI to a device of
             * their own and are kept for reference.
             */
            return new com.MessTech.doom.s.opl.OplMusicModule();
        }
    }
}
