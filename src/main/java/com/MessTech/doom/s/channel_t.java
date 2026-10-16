package com.MessTech.doom.s;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;

import com.MessTech.doom.data.sfxinfo_t;
import com.MessTech.doom.p.mobj_t;

public class channel_t {

    public channel_t() {
        sfxinfo = new sfxinfo_t();
    }

    /** Currently playing sound. If null, then it's free */
    DoomSound currentSound = null;

    sfxinfo_t sfxinfo;

    // origin of sound (usually a mobj_t).
    mobj_t origin;

    // handle of the sound being played
    int handle;

    AudioFormat format;

    public int sfxVolume;

    SourceDataLine auline = null;
}
