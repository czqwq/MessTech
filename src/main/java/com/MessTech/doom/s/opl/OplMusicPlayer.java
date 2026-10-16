package com.MessTech.doom.s.opl;

import java.util.List;

/**
 * Plays a Doom song on the OPL2 chip, the way the game's own DMX driver did: walk the events in
 * time order, turn each channel message into register writes, and let {@link OPL2} do the
 * synthesis.
 * <p>
 * The song comes from {@link DoomSong}, which flattens either lump format into timestamped events in
 * seconds - see that class for why the engine's own reader is not used.
 * <p>
 * The chip has nine channels and General MIDI has sixteen, so channels are handed out as notes
 * start and taken back when they end; when all nine are busy the oldest note gives way, which is
 * what a small sequencer has to do.
 * <p>
 * Percussion is played as ordinary notes on the percussion patches rather than in the chip's rhythm
 * mode. That is a real simplification - drums come out as short pitched hits rather than the
 * noise-based ones the chip could make - but it keeps the channel handling honest, and it is the
 * difference between music with drums and music without.
 */
public final class OplMusicPlayer {

    /** The OPL2 has nine two-operator channels. */
    private static final int CHANNELS = 9;

    /** General MIDI reserves channel 10 (index 9) for percussion. */
    private static final int PERCUSSION_CHANNEL = 9;

    /** Lowest note the block/F-Number split can reach; below this the block would go negative. */
    private static final int LOWEST_NOTE = 12;

    /**
     * F-Numbers for the twelve semitones of one octave at block 4, from the chip's own tables.
     * Every other note is the same value at a different block.
     */
    private static final int[] FNUMBER_BLOCK4 = { 0x157, 0x16B, 0x181, 0x198, 0x1B0, 0x1CA, 0x1E5, 0x202, 0x220, 0x241,
        0x263, 0x287 };

    /** One chip channel, and the note it is currently playing. */
    private static final class Voice {

        final int oplChannel;
        boolean active;
        int midiChannel = -1;
        /** The note actually programmed into the chip, after the patch's own offset. */
        int chipNote;
        /** The MIDI note that started it, which is what a note-off has to match. */
        int sourceNote;
        long startedAt;
        int volume;
        GenMidiBank.Voice patch;

        Voice(final int oplChannel) {
            this.oplChannel = oplChannel;
        }
    }

    private final OPL2 chip;
    private final GenMidiBank bank;

    private final Voice[] voices = new Voice[CHANNELS];
    private final int[] program = new int[16];
    private final int[] channelVolume = new int[16];
    private final int[] channelExpression = new int[16];

    private DoomSong score;
    private List<DoomSong.Event> events;
    private int eventCursor;
    private double position;
    private boolean playing;
    private boolean finished;
    private long noteCounter;

    public OplMusicPlayer(final OPL2 chip, final GenMidiBank bank) {
        this.chip = chip;
        this.bank = bank;
        for (int i = 0; i < CHANNELS; i++) {
            voices[i] = new Voice(i);
        }
        resetControllers();
        chip.reset();
    }

    // ------------------------------------------------------------------ transport

    /** Loads a score and rewinds to its start. A null score leaves the player with nothing to do. */
    public synchronized void setScore(final DoomSong newScore) {
        stopAll();
        this.score = newScore;
        this.events = newScore == null ? null : newScore.getEvents();
        this.eventCursor = 0;
        this.position = 0.0D;
        this.finished = false;
        this.playing = false;
        resetControllers();
    }

    private void resetControllers() {
        for (int i = 0; i < 16; i++) {
            program[i] = 0;
            channelVolume[i] = 100;
            channelExpression[i] = 127;
        }
    }

    public synchronized void start() {
        if (events != null) {
            this.playing = true;
        }
    }

    public synchronized void stop() {
        this.playing = false;
        stopAll();
    }

    public boolean isFinished() {
        return finished;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Advances the song by {@code frames} samples and renders exactly that much audio into
     * {@code out}, adding to whatever is there.
     * <p>
     * Called from the music thread with the buffer's own pacing (see {@code OplMusicModule}), so
     * "samples rendered" and "time passed" are the same thing and there is no clock to drift
     * against.
     * <p>
     * Only the event walk is locked: loading a song happens on the engine thread while this one is
     * rendering, and rewinding the score underneath it would be a race. The chip itself is only ever
     * touched here, so synthesising needs no lock at all.
     */
    public void render(final short[] out, final int offset, final int frames) {
        advance(frames);
        chip.render(out, offset, frames);
    }

    private synchronized void advance(final int frames) {
        if (playing && events != null) {
            final double target = position + frames / (double) chip.getSampleRate();
            while (eventCursor < events.size()) {
                final DoomSong.Event event = events.get(eventCursor);
                if (event.time > target) {
                    break;
                }
                apply(event);
                eventCursor++;
            }
            position = target;
            if (eventCursor >= events.size()) {
                // The song is over: let the last notes ring out but do not start anything again.
                finished = true;
                playing = false;
            }
        }
    }

    private void apply(final DoomSong.Event event) {
        switch (event.type) {
            case DoomSong.NOTE_ON:
                if (event.data2 > 0) {
                    noteOn(event.channel, event.data1, event.data2);
                } else {
                    noteOff(event.channel, event.data1);
                }
                break;
            case DoomSong.NOTE_OFF:
                noteOff(event.channel, event.data1);
                break;
            case DoomSong.PROGRAM:
                program[event.channel] = event.data1 & 0x7F;
                break;
            case DoomSong.VOLUME:
                channelVolume[event.channel] = event.data1;
                restateVolume(event.channel);
                break;
            case DoomSong.EXPRESSION:
                channelExpression[event.channel] = event.data1;
                restateVolume(event.channel);
                break;
            case DoomSong.ALL_NOTES_OFF:
                releaseChannel(event.channel);
                break;
            default:
                // Pitch bend has no cheap equivalent on this chip; the songs barely use it.
                break;
        }
    }

    // ------------------------------------------------------------------ notes

    private void noteOn(final int midiChannel, final int note, final int velocity) {
        final boolean percussion = midiChannel == PERCUSSION_CHANNEL;

        // The same note starting again replaces whatever was sounding, like a monophonic voice.
        for (final Voice voice : voices) {
            if (voice.active && voice.midiChannel == midiChannel && voice.sourceNote == note) {
                keyOff(voice);
            }
        }

        final Voice voice = allocate();
        if (voice == null) {
            return;
        }
        final GenMidiBank.Instrument instrument = percussion ? bank.percussion(note)
            : bank.melodic(program[midiChannel]);
        final GenMidiBank.Voice patch = instrument.voices[0];

        voice.active = true;
        voice.midiChannel = midiChannel;
        voice.sourceNote = note;
        voice.startedAt = noteCounter++;
        voice.patch = patch;
        voice.volume = percussion ? 127 : volumeOf(midiChannel);

        programPatch(voice, patch);
        programPitch(voice, instrument.fixedPitch ? instrument.note : note, patch);

        // Key on last: the chip should not start a note against half-written registers.
        chip.write(0xB0 + voice.oplChannel, 0x20 | keyOnBits(voice.chipNote));
    }

    private void programPatch(final Voice voice, final GenMidiBank.Voice patch) {
        final int c = voice.oplChannel;
        final int mod = modulatorSlot(c);
        final int car = carrierSlot(c);

        chip.write(0x20 + mod, patch.modulator.reg20);
        chip.write(0x40 + mod, patch.modulator.reg40());
        chip.write(0x60 + mod, patch.modulator.reg60);
        chip.write(0x80 + mod, patch.modulator.reg80);
        chip.write(0xE0 + mod, patch.modulator.waveform);

        chip.write(0x20 + car, patch.carrier.reg20);
        chip.write(0x60 + car, patch.carrier.reg60);
        chip.write(0x80 + car, patch.carrier.reg80);
        chip.write(0xE0 + car, patch.carrier.waveform);
        chip.write(0xC0 + c, patch.regC0());

        // 0x40 for the carrier goes through the volume path, since that is where volume lives.
        applyVolume(voice, patch);
    }

    /**
     * The chip's operators do not sit next to their channels. Register 0x20 and its siblings cover
     * six operator slots at a time - the modulators of channels 0-2, then their carriers, then the
     * same for 3-5 and 6-8 - so channel {@code n} uses slot {@code n + 3*(n/3)} and its carrier is
     * three slots later. That gives modulators {0,1,2,6,7,8,12,13,14} and carriers
     * {3,4,5,9,10,11,15,16,17}, which is the hardware table; writing to {@code 0x20 + n} would only
     * reach the first three channels and would hit another channel's carrier for the rest.
     */
    private static int modulatorSlot(final int channel) {
        return channel + channel / 3;
    }

    private static int carrierSlot(final int channel) {
        return modulatorSlot(channel) + 3;
    }

    private void programPitch(final Voice voice, final int sourceNote, final GenMidiBank.Voice patch) {
        final int note = Math.max(LOWEST_NOTE, Math.min(127, sourceNote + patch.baseNoteOffset));
        voice.chipNote = note;
        final int fNumber = FNUMBER_BLOCK4[note % 12];
        chip.write(0xA0 + voice.oplChannel, fNumber & 0xFF);
        // Block and the F-Number's top two bits; the key-on bit goes in with the same write later.
        chip.write(0xB0 + voice.oplChannel, keyOnBits(note) & ~0x20);
    }

    private static int keyOnBits(final int note) {
        final int fNumber = FNUMBER_BLOCK4[note % 12];
        final int block = note / 12 - 1;
        return ((block & 0x07) << 2) | ((fNumber >> 8) & 0x03);
    }

    private void noteOff(final int midiChannel, final int note) {
        for (final Voice voice : voices) {
            if (voice.active && voice.midiChannel == midiChannel && voice.sourceNote == note) {
                keyOff(voice);
            }
        }
    }

    private void keyOff(final Voice voice) {
        chip.write(0xB0 + voice.oplChannel, keyOnBits(voice.chipNote));
        voice.active = false;
    }

    private void releaseChannel(final int midiChannel) {
        for (final Voice voice : voices) {
            if (voice.active && voice.midiChannel == midiChannel) {
                keyOff(voice);
            }
        }
    }

    private void stopAll() {
        for (final Voice voice : voices) {
            if (voice.active) {
                keyOff(voice);
            }
        }
    }

    /** Re-applies carrier level after a volume change, for every note still sounding. */
    private void restateVolume(final int midiChannel) {
        for (final Voice voice : voices) {
            if (voice.active && voice.midiChannel == midiChannel && voice.patch != null) {
                voice.volume = midiChannel == PERCUSSION_CHANNEL ? 127 : volumeOf(midiChannel);
                applyVolume(voice, voice.patch);
            }
        }
    }

    private int volumeOf(final int midiChannel) {
        return channelVolume[midiChannel] * channelExpression[midiChannel] / 127;
    }

    /**
     * Channel volume becomes carrier attenuation. The chip's total level is a logarithmic
     * attenuation of roughly 0.75 dB per step, so fading it linearly is what makes a fade sound
     * even instead of dropping off a cliff at the quiet end.
     */
    private void applyVolume(final Voice voice, final GenMidiBank.Voice patch) {
        final int volume = Math.max(0, Math.min(127, voice.volume));
        final int attenuation = (63 * (127 - volume)) / 127;
        final int tl = Math.min(63, patch.carrier.tl + attenuation);
        chip.write(0x40 + carrierSlot(voice.oplChannel), (patch.carrier.ksl << 6) | tl);
    }

    /** Takes a chip channel for a new note; the oldest sounding note gives way when all are busy. */
    private Voice allocate() {
        Voice oldest = null;
        for (final Voice voice : voices) {
            if (!voice.active) {
                return voice;
            }
            if (oldest == null || voice.startedAt < oldest.startedAt) {
                oldest = voice;
            }
        }
        if (oldest != null) {
            keyOff(oldest);
        }
        return oldest;
    }
}
