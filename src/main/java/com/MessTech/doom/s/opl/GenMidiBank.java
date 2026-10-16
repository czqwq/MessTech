package com.MessTech.doom.s.opl;

/**
 * The WAD's {@code GENMIDI} lump: the General MIDI to OPL2 patch table Doom shipped with, so the
 * songs play on the FM chip they were written for.
 * <p>
 * The layout below was worked out from the lump itself (Freedoom's copy, 11908 bytes) and is
 * confirmed by the values it produces: the bass drum patch comes out with attack 15 (an instant
 * attack, which is what a drum needs), the pianos with attack 14-15, every {@code KSL} is 0-3 and
 * every {@code TL} is within 0-63 - the only ranges the chip accepts.
 *
 * <pre>
 * lump      : "#OPL_II#" (8 bytes), then 175 instruments of 36 bytes, then 175 names of 32 bytes
 * instrument: flags:u16, finetune:u8 (stored as 0x80|fine), note:u8, voices[2]
 * voice     : 16 bytes = operator[7] + operator[7] + int16le baseNoteOffset
 * operator  : reg0x20, reg0x60, reg0x80, waveform, KSL (&lt;&lt;6), TL, unused
 * </pre>
 *
 * The instrument names sit in the second block and decode as "Acoustic Grand Piano" and so on,
 * which is what pins the 36-byte stride down.
 * <p>
 * The seventh operator byte is small, usually zero, and never used by anything here; every
 * register field that matters is accounted for by the other six, so it is ignored rather than
 * guessed at.
 */
public final class GenMidiBank {

    /** Instruments 0-127 are melodic, 128-174 are the fixed-pitch percussion set. */
    public static final int INSTRUMENT_COUNT = 175;

    private static final int INSTRUMENT_SIZE = 36;
    private static final int VOICE_SIZE = 16;
    private static final String MAGIC = "#OPL_II#";

    /** The first percussion instrument; General MIDI note 35 maps here. */
    private static final int PERCUSSION_BASE = 128;
    private static final int PERCUSSION_FIRST_NOTE = 35;

    /** One FM operator: the six register fields the lump stores for it. */
    public static final class Operator {

        /** Register 0x20: AM, VIB, sustaining, key scale rate, frequency multiple. */
        public final int reg20;
        /** Register 0x60: attack rate (high nibble) and decay rate (low nibble). */
        public final int reg60;
        /** Register 0x80: sustain level (high nibble) and release rate (low nibble). */
        public final int reg80;
        /** Register 0xE0: waveform 0-3. */
        public final int waveform;
        /** Key scale level, 0-3. Stored shifted left by six in the lump, as register 0x40 wants it. */
        public final int ksl;
        /** Total level (attenuation), 0 = loudest, 63 = quietest. */
        public final int tl;

        Operator(final int reg20, final int reg60, final int reg80, final int waveform, final int ksl, final int tl) {
            this.reg20 = reg20 & 0xFF;
            this.reg60 = reg60 & 0xFF;
            this.reg80 = reg80 & 0xFF;
            this.waveform = waveform & 0x07;
            this.ksl = ksl & 0x03;
            this.tl = tl & 0x3F;
        }

        /** Register 0x40, built from the two fields the lump splits it into. */
        public int reg40() {
            return (ksl << 6) | tl;
        }
    }

    /** One of the two voices an instrument can have. */
    public static final class Voice {

        public final Operator modulator;
        public final Operator carrier;
        /** Register 0xC0 bits 3-1. */
        public final int feedback;
        /** Register 0xC0 bit 0: 0 = FM (modulator feeds carrier), 1 = additive. */
        public final int connection;
        /** Semitones to shift the patch by, signed. */
        public final int baseNoteOffset;

        Voice(final byte[] data, final int offset) {
            this.modulator = new Operator(
                data[offset],
                data[offset + 1],
                data[offset + 2],
                data[offset + 3],
                (data[offset + 4] & 0xFF) >> 6,
                data[offset + 5]);
            this.carrier = new Operator(
                data[offset + 7],
                data[offset + 8],
                data[offset + 9],
                data[offset + 10],
                (data[offset + 11] & 0xFF) >> 6,
                data[offset + 12]);
            // Register 0xC0 as the lump stores it: feedback in bits 3-1, connection in bit 0.
            final int regC0 = data[offset + 6] & 0x0F;
            this.feedback = (regC0 >> 1) & 0x07;
            this.connection = regC0 & 0x01;
            this.baseNoteOffset = (short) ((data[offset + 14] & 0xFF) | ((data[offset + 15] & 0xFF) << 8));
        }

        /** Register 0xC0: feedback in bits 3-1, connection in bit 0. */
        public int regC0() {
            return (feedback << 1) | connection;
        }
    }

    /** One complete patch. */
    public static final class Instrument {

        /** True for the percussion set, which always plays at {@link #note}. */
        public final boolean fixedPitch;
        /** Fine tuning, in the lump's 0-15 range. */
        public final int finetune;
        /** The note a fixed-pitch instrument plays at. */
        public final int note;
        public final Voice[] voices;

        Instrument(final byte[] data, final int offset) {
            final int flags = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
            this.fixedPitch = (flags & 0x01) != 0;
            // Stored with the top bit set: 0x80 means "no fine tuning".
            this.finetune = data[offset + 2] & 0x0F;
            this.note = data[offset + 3] & 0xFF;
            this.voices = new Voice[2];
            this.voices[0] = new Voice(data, offset + 4);
            this.voices[1] = new Voice(data, offset + 4 + VOICE_SIZE);
        }
    }

    private final Instrument[] instruments = new Instrument[INSTRUMENT_COUNT];

    private GenMidiBank() {}

    /** True if this looks like a GENMIDI lump at all. */
    public static boolean isGenMidi(final byte[] lump) {
        if (lump == null || lump.length < 8 + INSTRUMENT_COUNT * INSTRUMENT_SIZE) {
            return false;
        }
        for (int i = 0; i < MAGIC.length(); i++) {
            if (lump[i] != MAGIC.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Parses a lump; returns null when it is not one, so the caller can fall back. */
    public static GenMidiBank parse(final byte[] lump) {
        if (!isGenMidi(lump)) {
            return null;
        }
        final GenMidiBank bank = new GenMidiBank();
        for (int i = 0; i < INSTRUMENT_COUNT; i++) {
            bank.instruments[i] = new Instrument(lump, 8 + i * INSTRUMENT_SIZE);
        }
        return bank;
    }

    /** A melodic program 0-127, clamped rather than thrown over. */
    public Instrument melodic(final int program) {
        return instruments[Math.max(0, Math.min(127, program))];
    }

    /**
     * The percussion patch for a General MIDI note. Doom's table covers notes 35 to 81; anything
     * outside that gets the nearest entry rather than silence.
     */
    public Instrument percussion(final int note) {
        final int index = Math.max(0, Math.min(46, note - PERCUSSION_FIRST_NOTE));
        return instruments[PERCUSSION_BASE + index];
    }
}
