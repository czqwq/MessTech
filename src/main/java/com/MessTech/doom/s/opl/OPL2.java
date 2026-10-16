package com.MessTech.doom.s.opl;

/**
 * A self-contained emulator of the <b>YM3812 (OPL2)</b>, the FM synthesis chip of the AdLib and
 * Sound Blaster cards - the hardware DOOM's {@code GENMIDI} instrument bank was written for.
 * <p>
 * The only dependency is {@code java.lang}: no {@code javax.sound}, no reflection, no file I/O and
 * no printing. {@link #write} and {@link #render} are deterministic and allocate nothing, so the
 * class is safe to drive from an audio thread. Only the constructor allocates, when it sizes its
 * wave and gain tables for the requested sample rate.
 * <p>
 * <b>Architecture.</b> Nine channels, each with a modulator and a carrier operator. The operator
 * slot mapping is the one {@code OplMusicPlayer} writes: the modulator of channel {@code n} is slot
 * {@code n + n/3} and its carrier is three slots later, and the per-operator register groups
 * ({@code 0x20}, {@code 0x40}, {@code 0x60}, {@code 0x80}, {@code 0xE0}) are addressed as
 * {@code base + slot}. The packing puts three modulators and their carriers into every six offsets,
 * so slots 4, 5, 8 and 9 end up shared between two neighbouring channels; an operator shared that
 * way is evaluated once per output sample and used in both roles. Slots 14-17 belong to no channel.
 * <p>
 * <b>Register groups.</b>
 * <ul>
 * <li>{@code 0x01} - test / WSE; bit 5 is the waveform select enable, and while it is clear every
 * operator is forced back to waveform 0, as on a chip left in its power-on state.</li>
 * <li>{@code 0x20 + slot} - bit 7 AM (tremolo) enable, bit 6 VIB (vibrato) enable, bit 5 EG type
 * ({@code 1} sustaining, {@code 0} percussive), bit 4 KSR, bits 3-0 MULT ({@code 0} means x0.5).</li>
 * <li>{@code 0x40 + slot} - bits 7-6 KSL, bits 5-0 TL. TL is an attenuation: {@code 0} is loudest
 * and one step is 0.75 dB.</li>
 * <li>{@code 0x60 + slot} - bits 7-4 AR, bits 3-0 DR.</li>
 * <li>{@code 0x80 + slot} - bits 7-4 SL (3 dB per step, {@code 15} holding near silence), bits 3-0
 * RR.</li>
 * <li>{@code 0xE0 + slot} - bits 1-0 waveform: 0 full sine, 1 half sine, 2 absolute sine,
 * 3 pulse (quarter) sine.</li>
 * <li>{@code 0xA0 + n} - the low eight bits of the F-Number.</li>
 * <li>{@code 0xB0 + n} - <b>bit 5 KEY-ON, bits 4-2 BLOCK (0-7), bits 1-0 F-Number bits 9-8</b>; bits
 * 7-6 are unused. This is the AdLib/DOSBox layout and it is the one {@code OplMusicPlayer} writes,
 * {@code 0x20 | (block << 2) | fnumHigh}, so a key-on arrives as bit 5 and a key-off as the same
 * byte with bit 5 clear.</li>
 * <li>{@code 0xC0 + n} - feedback depth in bits 3-1, connection in bit 0 ({@code 0} FM, {@code 1}
 * additive).</li>
 * <li>{@code 0xBD} - AM depth in bit 7 (1.1 or 4.8 dB), VIB depth in bit 6 (7 or 14 cents), rhythm
 * mode in bit 5, the five rhythm enables in bits 4-0.</li>
 * </ul>
 * <p>
 * <b>Pitch.</b> Phase increments use the real chip relation {@code f = fnum * 49716 / 2^(20 - block)}
 * for the 3.579545 MHz master clock, evaluated at the chip's 49716 Hz DAC rate and scaled by the
 * output rate, so block 4 F-Number {@code 0x157} lands on middle C and block 4 {@code 0x241} on
 * A440. MULT scales the increment and the phase accumulator is a 32-bit wrap-around; a full-scale
 * modulator - the chip's 13-bit operator word added ten bits above the wave index - swings the
 * carrier by {@code 4096 * 1024 / 2^20 = 4} cycles, which is the modulation index used here.
 * <p>
 * <b>Level.</b> One channel at TL 0 peaks near a quarter of full scale, so a single voice is clearly
 * audible without eating the whole range. The mix of all nine channels runs through a {@code tanh}
 * soft saturation, which is within 2% of linear at a single voice and compresses a dense mix to just
 * under full scale instead of wrapping it.
 * <p>
 * <b>Approximations</b> - pitch, level, envelope shape and register semantics are in the right place,
 * but this is not silicon-accurate, and these are the known gaps:
 * <ul>
 * <li>Envelopes advance once per output sample in the dB domain. Attack, decay and release follow the
 * published OPL anchors - attack 6.5 s at AR 0 down to about 16 microseconds, decay and release 4 s
 * down to about 0.1 ms - at the chip's quarter-step rate granularity, whereas the chip's counter
 * accelerates progressively; the endpoints match, the curvature does not.</li>
 * <li>Attenuation is kept as a continuous dB value rather than the chip's 12-bit counter with its
 * 0.1875 dB steps, so the quantisation and the stepped release of real hardware are smoothed away.</li>
 * <li>KSL is applied as a flat attenuation offset of 1.5, 3 or 6 dB per octave of key scale level
 * rather than the chip's exact per-key table.</li>
 * <li>The LFOs are plain 3.7 Hz and 6.1 Hz sines with the two published depth steps. The chip's odd
 * LFO waveform and its per-operator LFO delay are ignored.</li>
 * <li>Rhythm mode is a work-alike layer, not the chip's dedicated percussion operators: its five
 * pieces are additive voices built from a noise shift register plus their own pitch and envelopes,
 * and they borrow no melodic channel at all, so enabling rhythm mode can never silence anything the
 * caller programmed. The chip instead rewires operators of channels 6-8 for the drums.</li>
 * <li>The chip is mono, so left and right always receive identical samples.</li>
 * </ul>
 * Nothing a caller writes to a register can throw or index out of bounds: unknown registers are
 * ignored, operator offsets at or beyond slot 17 are dropped and every table index is masked.
 * <p>
 * Not thread safe: use one instance per audio thread.
 */
public final class OPL2 {

    // =============================================================================================
    // Chip-wide constants
    // =============================================================================================

    /** Sample rate of the YM3812's internal DAC, 3579545 Hz / 72. */
    public static final double CHIP_RATE = 49716.0;

    private static final int CHANNELS = 9;

    private static final int SLOTS = CHANNELS * 2;

    /** Modulator slot of channel n: the chip's operators do not sit next to their channels. */
    private static int modulatorSlot(int channel) {
        return channel + channel / 3;
    }

    /** Carrier slot of channel n: three slots past its modulator, as the caller's offsets assume. */
    private static int carrierSlot(int channel) {
        return modulatorSlot(channel) + 3;
    }

    /** Channel whose modulator is this slot, or -1 for the slots no channel uses. */
    private static final int[] MODULATOR_CHANNEL = new int[SLOTS];

    /** Channel whose carrier is this slot, or -1 for the slots no channel uses. */
    private static final int[] CARRIER_CHANNEL = new int[SLOTS];

    static {
        for (int slot = 0; slot < SLOTS; slot++) {
            MODULATOR_CHANNEL[slot] = -1;
            CARRIER_CHANNEL[slot] = -1;
        }
        for (int channel = 0; channel < CHANNELS; channel++) {
            MODULATOR_CHANNEL[modulatorSlot(channel)] = channel;
            CARRIER_CHANNEL[carrierSlot(channel)] = channel;
        }
    }

    /** Register 0xB0+n: bit 5 keys the channel on. */
    private static final int KEY_ON_BIT = 0x20;

    /** Register 0xB0+n: bits 4-2 carry the block. */
    private static final int BLOCK_SHIFT = 2;

    private static final int BLOCK_MASK = 0x07;

    /** Register 0xB0+n: bits 1-0 carry the top of the F-Number; 0xA0+n carries the rest. */
    private static final int FNUM_HIGH_MASK = 0x03;

    private static final int FNUM_LOW_MASK = 0xFF;

    /** MULT field to frequency multiple, with the chip's special x0.5 at index 0. */
    private static final double[] MULTIPLE = { 0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 12, 12, 15, 15 };

    /** KSL field to dB of attenuation per octave of key scale level. */
    private static final double[] KSL_DB_PER_OCTAVE = { 0.0, 3.0, 1.5, 6.0 };

    /** Feedback depth of each 0xC0 field value, as cycles of self phase modulation at full output. */
    private static final double[] FEEDBACK_CYCLES = { 0.0, 1.0 / 32.0, 2.0 / 32.0, 4.0 / 32.0, 8.0 / 32.0, 16.0 / 32.0,
        32.0 / 32.0, 64.0 / 32.0 };

    // =============================================================================================
    // Envelope constants
    // =============================================================================================

    /** Full attenuation range of an envelope, in dB. */
    private static final double MAX_ATTENUATION_DB = 96.0;

    /** Attenuation of one total-level step, in dB. */
    private static final double TL_DB = 0.75;

    /** Attenuation of one sustain-level step, in dB. */
    private static final double SL_DB = 3.0;

    /** Sustain level 15 is the chip's special near-full attenuation hold. */
    private static final double SL_MAX_DB = 93.0;

    /** Longest attack, in seconds, at the slowest attack rate. */
    private static final double MAX_ATTACK_SECONDS = 6.5;

    /** Factor by which the attack shortens per quarter rate step, matching the published anchors. */
    private static final double ATTACK_RATIO_PER_QUARTER = Math.pow(2.6, 0.25);

    /** Slowest decay and release slope, in dB per second: the 96 dB range in four seconds. */
    private static final double SLOWEST_SLOPE_DB_PER_SEC = 24.0;

    /** Factor by which a decay or release slope doubles, per quarter rate step. */
    private static final double SLOPE_RATIO_PER_QUARTER = Math.pow(2.0, 0.25);

    /**
     * The chip's rate adder works in quarter rate steps: the register field contributes
     * {@code rate * 4} and the key code up to fifteen more, so every slope from the slowest to the
     * fastest is one of sixty-four values, each 2^(1/4) times the one below it.
     */
    private static final int RATE_STEPS = 64;

    /** Attack time in seconds for each quarter-step rate. */
    private static final double[] ATTACK_SECONDS = new double[RATE_STEPS];

    /** Decay and release slope in dB per second for each quarter-step rate. */
    private static final double[] SLOPE_DB_PER_SEC = new double[RATE_STEPS];

    static {
        for (int rate = 0; rate < RATE_STEPS; rate++) {
            ATTACK_SECONDS[rate] = MAX_ATTACK_SECONDS / Math.pow(ATTACK_RATIO_PER_QUARTER, rate);
            SLOPE_DB_PER_SEC[rate] = SLOWEST_SLOPE_DB_PER_SEC * Math.pow(SLOPE_RATIO_PER_QUARTER, rate);
        }
    }

    /** Stage: silent and idle. */
    private static final int STAGE_IDLE = 0;

    /** Stage: the attack is climbing from full attenuation towards no attenuation. */
    private static final int STAGE_ATTACK = 1;

    /** Stage: the decay is falling towards the sustain level. */
    private static final int STAGE_DECAY = 2;

    /** Stage: the decay has reached the sustain level and holds there until key-off. */
    private static final int STAGE_SUSTAIN = 3;

    /** Stage: the release is falling away from wherever the envelope was at key-off. */
    private static final int STAGE_RELEASE = 4;

    // =============================================================================================
    // Synthesis constants
    // =============================================================================================

    /** Table entries per operator period; a 12-bit phase index covers exactly one cycle. */
    private static final int WAVE_SIZE = 4096;

    /** Peak of the wave tables, the chip's own 13-bit operator full scale. */
    private static final double OPERATOR_FULL_SCALE = 4096.0;

    /** 32-bit phase units in one output cycle. */
    private static final double PHASE_PER_CYCLE = 4294967296.0;

    /** Phase offset, in 32-bit phase units, of one operator unit at one cycle of modulation. */
    private static final double PHASE_PER_UNIT = PHASE_PER_CYCLE / OPERATOR_FULL_SCALE;

    /** Modulation applied by a full-scale modulator, in cycles of carrier phase. */
    private static final double MODULATION_CYCLES = 4.0;

    /** Full scale of the 15-bit envelope gain. */
    private static final double GAIN_FULL_SCALE = 32768.0;

    /** Output gain of one channel: a single full-volume carrier peaks near a quarter of full scale. */
    private static final double CHANNEL_GAIN = 2.0;

    /** Full scale of the 16-bit output. */
    private static final double OUTPUT_FULL_SCALE = 32767.0;

    /** Attenuation of one gain table entry, in dB; the chip's own envelope step. */
    private static final double GAIN_STEP_DB = 0.1875;

    /** Gain table entries per dB, the reciprocal of {@link #GAIN_STEP_DB}. */
    private static final double GAIN_STEPS_PER_DB = 1.0 / GAIN_STEP_DB;

    private static final int WAVE_SINE = 0;

    private static final int WAVE_HALF_SINE = 1;

    private static final int WAVE_ABS_SINE = 2;

    private static final int WAVE_PULSE_SINE = 3;

    /** One quarter of a sine period, the size of the rising quarter of the wave tables. */
    private static final int WAVE_QUARTER = WAVE_SIZE / 4;

    // =============================================================================================
    // LFO constants
    // =============================================================================================

    private static final double TWO_PI = 2.0 * Math.PI;

    /** Tremolo rate in Hz, the chip's AM LFO. */
    private static final double TREMOLO_HZ = 3.7;

    /** Vibrato rate in Hz, the chip's VIB LFO. */
    private static final double VIBRATO_HZ = 6.1;

    private static final double AM_DEPTH_DB = 1.1;

    private static final double AM_DEPTH_DEEP_DB = 4.8;

    private static final double VIBRATO_CENTS = 7.0;

    private static final double VIBRATO_CENTS_DEEP = 14.0;

    // =============================================================================================
    // Rhythm constants
    // =============================================================================================

    // Rhythmic voices, in the chip's order: bass drum, snare, tom, cymbal, hi-hat.
    private static final int PERCUSSION_PIECES = 5;

    private static final int PERCUSSION_ALL = 0x1F;

    /** Peak amplitude of each rhythm voice, as a fraction of one full-scale operator. */
    private static final double[] PERCUSSION_LEVEL = { 0.85, 0.5, 0.6, 0.3, 0.25 };

    /** Decay time constant in seconds of each rhythm voice. */
    private static final double[] PERCUSSION_DECAY_SECONDS = { 0.22, 0.09, 0.12, 0.5, 0.035 };

    /** Steady frequency in Hz of each rhythm voice; the noise pieces ignore it. */
    private static final double[] PERCUSSION_HZ = { 62.0, 190.0, 150.0, 0.0, 0.0 };

    /** Extra starting frequency in Hz, swept away to give a drum its thump. */
    private static final double[] PERCUSSION_SWEEP_HZ = { 120.0, 0.0, 90.0, 0.0, 0.0 };

    /** Weight of the noise component of each voice; the rest is its pitched body. */
    private static final double[] PERCUSSION_NOISE_MIX = { 0.15, 1.0, 0.1, 1.0, 1.0 };

    /** Time constant of the pitch sweep, in seconds. */
    private static final double PERCUSSION_SWEEP_SECONDS = 0.04;

    /** Below this envelope gain a rhythm voice has finished and stops being rendered. */
    private static final double PERCUSSION_SILENCE = 1.0E-4;

    // =============================================================================================
    // Chip state
    // =============================================================================================

    private final int sampleRate;

    private boolean waveSelectEnable;

    private boolean rhythmMode;

    /** Rhythm enable bits of the last 0xBD write, so only a 0 to 1 edge retriggers a voice. */
    private int rhythmMask;

    private double tremoloDepthDb;

    private double vibratoRatio;

    private double phaseTremolo;

    private double phaseVibrato;

    private double tremoloNowDb;

    private int tremoloOperators;

    private int vibratoOperators;

    /** The noise shift register shared by the rhythm voices; a 16-bit maximal LFSR. */
    private int noiseRegister = 0x4001;

    // =============================================================================================
    // Operator state, in flat arrays indexed by slot 0..17
    // =============================================================================================

    private final int[] opWaveform = new int[SLOTS];

    private final int[] opWaveformSelect = new int[SLOTS];

    private final double[] opMultiple = new double[SLOTS];

    private final boolean[] opPercussive = new boolean[SLOTS];

    private final boolean[] opTremoloEnable = new boolean[SLOTS];

    private final boolean[] opVibratoEnable = new boolean[SLOTS];

    private final boolean[] opKeyScaleRate = new boolean[SLOTS];

    /** Total level converted to dB, 0 for the loudest setting. */
    private final double[] opTlDb = new double[SLOTS];

    private final int[] opKsl = new int[SLOTS];

    /** Total level plus key scale attenuation: everything the envelope adds to, in dB. */
    private final double[] opBaseDb = new double[SLOTS];

    private final int[] opAr = new int[SLOTS];

    private final int[] opDr = new int[SLOTS];

    private final int[] opSl = new int[SLOTS];

    private final int[] opRr = new int[SLOTS];

    /** Envelope stages: see the {@code STAGE_*} constants. */
    private final int[] opStage = new int[SLOTS];

    private final double[] opEnvelopeDb = new double[SLOTS];

    /** Attenuation added per output sample during the attack, the decay and the release, in dB. */
    private final double[] opAttackStep = new double[SLOTS];

    private final double[] opDecayStep = new double[SLOTS];

    private final double[] opReleaseStep = new double[SLOTS];

    /** Current envelope gain in 0..32767, recomputed from the attenuation every sample. */
    private final int[] opGain = new int[SLOTS];

    /** Phase in 32-bit units: the top twelve bits address the wave table. */
    private final int[] opPhase = new int[SLOTS];

    /** Phase step before vibrato, in 32-bit units per output sample. */
    private final double[] opBaseInc = new double[SLOTS];

    /** Phase step actually used, vibrato included. */
    private final int[] opPhaseInc = new int[SLOTS];

    /** Output of each slot for the current sample, one evaluation per slot per sample. */
    private final double[] opSample = new double[SLOTS];

    /** Channel whose note sets the key scaling and the envelope rates of each slot. */
    private final int[] opChannel = new int[SLOTS];

    // =============================================================================================
    // Channel state
    // =============================================================================================

    private final int[] fnumber = new int[CHANNELS];

    private final int[] block = new int[CHANNELS];

    private final boolean[] keyActive = new boolean[CHANNELS];

    private final int[] feedback = new int[CHANNELS];

    private final double[] feedbackCycles = new double[CHANNELS];

    private final boolean[] additive = new boolean[CHANNELS];

    /** Previous modulator output of each channel, which is what the feedback loop feeds back. */
    private final double[] feedbackSample = new double[CHANNELS];

    /** Set from a channel's key-on until its release has died away, so an idle channel stays silent. */
    private final boolean[] channelPlaying = new boolean[CHANNELS];

    // =============================================================================================
    // Rhythm layer state
    // =============================================================================================

    private final boolean[] percussionActive = new boolean[PERCUSSION_PIECES];

    /** Envelope gain of each voice, 1.0 at its trigger. */
    private final double[] percussionGain = new double[PERCUSSION_PIECES];

    /** Per-sample multiplier of the decay, precomputed for the output rate. */
    private final double[] percussionDecay = new double[PERCUSSION_PIECES];

    /** Phase in wave table units of the pitched voices. */
    private final double[] percussionPhase = new double[PERCUSSION_PIECES];

    /** Steady phase step in wave table units per sample. */
    private final double[] percussionStep = new double[PERCUSSION_PIECES];

    /** Extra step on top of the steady pitch, swept away to give the drums their thump. */
    private final double[] percussionSweep = new double[PERCUSSION_PIECES];

    private final double percussionSweepFall;

    // =============================================================================================
    // Lookup tables
    // =============================================================================================

    /** The four OPL2 operator waveforms, one period each at 12-bit resolution. */
    private final int[][] waveTable = new int[4][WAVE_SIZE];

    /** Linear gain in 0..32767 for an attenuation of {@code index * 0.1875} dB. */
    private final int[] gainTable = new int[(int) (MAX_ATTENUATION_DB * GAIN_STEPS_PER_DB) + 2];

    // =============================================================================================
    // Construction
    // =============================================================================================

    /**
     * Builds an emulator rendering {@code sampleRate} stereo frames per second.
     *
     * @param sampleRate the output sample rate in Hz, for example 22050; must be positive
     * @throws IllegalArgumentException if {@code sampleRate} is not positive
     */
    public OPL2(int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive, got " + sampleRate);
        }
        this.sampleRate = sampleRate;
        buildWaveTables();
        buildGainTable();
        for (int piece = 0; piece < PERCUSSION_PIECES; piece++) {
            // The voices are one-pole decays, so their shape depends only on the output rate.
            percussionDecay[piece] = Math.exp(-1.0 / (PERCUSSION_DECAY_SECONDS[piece] * sampleRate));
            percussionStep[piece] = PERCUSSION_HZ[piece] / sampleRate * WAVE_SIZE;
        }
        percussionSweepFall = Math.exp(-1.0 / (PERCUSSION_SWEEP_SECONDS * sampleRate));
        reset();
    }

    /** Precomputes the four operator waveforms. */
    private void buildWaveTables() {
        for (int i = 0; i < WAVE_SIZE; i++) {
            int sine = (int) Math.round(Math.sin(i * TWO_PI / WAVE_SIZE) * OPERATOR_FULL_SCALE);
            waveTable[WAVE_SINE][i] = sine;
            // Half sine mutes the negative half, leaving the rest of the period flat.
            waveTable[WAVE_HALF_SINE][i] = sine > 0 ? sine : 0;
            // Absolute sine rectifies the waveform, doubling the apparent frequency.
            waveTable[WAVE_ABS_SINE][i] = sine < 0 ? -sine : sine;
            // Pulse sine keeps the rising quarter and repeats it once per half period.
            boolean rising = i < WAVE_QUARTER || (i >= 2 * WAVE_QUARTER && i < 3 * WAVE_QUARTER);
            waveTable[WAVE_PULSE_SINE][i] = rising ? sine : 0;
        }
    }

    /** Precomputes the attenuation to gain table at 0.1875 dB per entry, the chip's envelope step. */
    private void buildGainTable() {
        for (int i = 0; i < gainTable.length; i++) {
            int gain = (int) Math.round(Math.pow(10.0, -(i * GAIN_STEP_DB) / 20.0) * GAIN_FULL_SCALE);
            gainTable[i] = gain > 32767 ? 32767 : gain;
        }
    }

    // =============================================================================================
    // Public API
    // =============================================================================================

    /**
     * Silences the chip. Every envelope is forced to full attenuation and its stage cleared, phases
     * and LFOs are zeroed and any rhythm voice still sounding is stopped.
     * <p>
     * Register contents are deliberately <em>not</em> cleared, so a patch programmed before the reset
     * stays programmed and the next key-on plays the same instrument.
     */
    public void reset() {
        for (int slot = 0; slot < SLOTS; slot++) {
            opStage[slot] = STAGE_IDLE;
            opEnvelopeDb[slot] = MAX_ATTENUATION_DB;
            opGain[slot] = 0;
            opPhase[slot] = 0;
            opSample[slot] = 0.0;
            opBaseInc[slot] = 0.0;
            opPhaseInc[slot] = 0;
            opChannel[slot] = 0;
            opTremoloEnable[slot] = false;
            opVibratoEnable[slot] = false;
            opWaveform[slot] = WAVE_SINE;
            opWaveformSelect[slot] = WAVE_SINE;
        }
        for (int channel = 0; channel < CHANNELS; channel++) {
            fnumber[channel] = 0;
            block[channel] = 0;
            keyActive[channel] = false;
            feedback[channel] = 0;
            feedbackCycles[channel] = 0.0;
            additive[channel] = false;
            feedbackSample[channel] = 0.0;
            channelPlaying[channel] = false;
        }
        for (int piece = 0; piece < PERCUSSION_PIECES; piece++) {
            percussionActive[piece] = false;
            percussionGain[piece] = 0.0;
            percussionPhase[piece] = 0.0;
            percussionSweep[piece] = 0.0;
        }
        waveSelectEnable = false;
        rhythmMode = false;
        rhythmMask = 0;
        noiseRegister = 0x4001;
        phaseTremolo = 0.0;
        phaseVibrato = 0.0;
        tremoloNowDb = 0.0;
        // Power-on LFO settings: shallow tremolo and a seven cent vibrato, both off per operator.
        tremoloDepthDb = AM_DEPTH_DB;
        vibratoRatio = Math.pow(2.0, -VIBRATO_CENTS / 1200.0);
        tremoloOperators = 0;
        vibratoOperators = 0;
    }

    /**
     * Performs one chip register write.
     * <p>
     * As on hardware, a byte with bit 7 set is an address that latches the register, so a caller can
     * pair address and data writes in any order. The timer block ({@code 0x02}-{@code 0x1F}), the
     * reset register {@code 0x00} and the status port {@code 0x08} are accepted and ignored, as is
     * any register this chip does not have.
     * <p>
     * No write can throw, whatever the byte: operator register groups past slot 17 are dropped and
     * every field, including the rhythm bits of {@code 0xBD}, is masked before it is used.
     *
     * @param register register address; only the low 8 bits are used
     * @param value    data byte; only the low 8 bits are used
     */
    public void write(int register, int value) {
        register &= 0xFF;
        value &= 0xFF;
        if (register == 0x00 || (register >= 0x02 && register <= 0x1F)) {
            return;
        }
        if (register == 0x01) {
            writeWaveSelectEnable(value);
        } else if (register >= 0x20 && register <= 0x35) {
            writeAmVibEgtKsrMult(register - 0x20, value);
        } else if (register >= 0x40 && register <= 0x55) {
            writeKslTl(register - 0x40, value);
        } else if (register >= 0x60 && register <= 0x75) {
            writeArDr(register - 0x60, value);
        } else if (register >= 0x80 && register <= 0x95) {
            writeSlRr(register - 0x80, value);
        } else if (register >= 0xA0 && register <= 0xA8) {
            writeFnumberLow(register - 0xA0, value);
        } else if (register >= 0xB0 && register <= 0xB8) {
            writeFnumberHigh(register - 0xB0, value);
        } else if (register >= 0xC0 && register <= 0xC8) {
            writeFeedbackConnection(register - 0xC0, value);
        } else if (register >= 0xE0 && register <= 0xF5) {
            writeWaveform(register - 0xE0, value);
        } else if (register == 0xBD) {
            writeRhythm(value);
        }
    }

    /**
     * Renders {@code frames} stereo frames, <b>adding</b> them into {@code out} so that the caller can
     * mix the chip with other audio sources. Each frame writes two consecutive shorts; the YM3812 is a
     * mono chip, so both receive the same sample. The routine never allocates, never prints and is
     * fully deterministic, and its output is soft-saturated, so it always stays inside the 16-bit
     * range even when all nine channels are loud at once.
     * <p>
     * A null buffer, a negative offset or a request longer than the buffer is clipped rather than
     * rejected, so a bookkeeping mistake in an audio thread cannot turn into an exception.
     *
     * @param out    destination buffer with room for {@code 2 * (offset + frames)} shorts
     * @param offset index of the first stereo frame to update
     * @param frames number of stereo frames to render
     */
    public void render(short[] out, int offset, int frames) {
        if (out == null || frames <= 0 || offset < 0 || offset >= out.length) {
            return;
        }
        int room = (out.length - offset) / 2;
        if (frames > room) {
            frames = room;
        }
        int index = offset;
        for (int frame = 0; frame < frames; frame++) {
            stepLowFrequencyOscillators();
            advanceEnvelopes();
            advanceNoise();
            // Every slot is evaluated once per sample, in slot order. A carrier's slot number is
            // always three past its modulator's, so the modulator's sample for this frame is ready by
            // the time its carrier asks for it, and a slot shared by two channels is still only
            // advanced once.
            for (int slot = 0; slot < SLOTS; slot++) {
                int asModulator = MODULATOR_CHANNEL[slot];
                int asCarrier = CARRIER_CHANNEL[slot];
                int modulation = 0;
                if (asModulator >= 0) {
                    modulation += phaseOffset(feedbackSample[asModulator], feedbackCycles[asModulator]);
                }
                if (asCarrier >= 0) {
                    modulation += phaseOffset(opSample[modulatorSlot(asCarrier)], MODULATION_CYCLES);
                }
                double value = operatorSample(slot, modulation);
                opSample[slot] = value;
                if (asModulator >= 0) {
                    feedbackSample[asModulator] = value;
                }
            }
            double mixed = 0.0;
            for (int channel = 0; channel < CHANNELS; channel++) {
                if (!channelPlaying[channel]) {
                    continue;
                }
                mixed += opSample[carrierSlot(channel)];
                if (additive[channel]) {
                    mixed += opSample[modulatorSlot(channel)];
                }
            }
            if (rhythmMode) {
                mixed += renderPercussion();
            }
            int sample = softClip(mixed * CHANNEL_GAIN);
            out[index++] += (short) sample;
            out[index++] += (short) sample;
        }
    }

    /**
     * Returns the output sample rate this emulator was built for.
     *
     * @return the sample rate in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    // =============================================================================================
    // Register group handlers
    // =============================================================================================

    /** 0x01: the waveform select enable. Clearing it returns every operator to the full sine. */
    private void writeWaveSelectEnable(int value) {
        waveSelectEnable = (value & 0x20) != 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            opWaveform[slot] = waveSelectEnable ? opWaveformSelect[slot] : WAVE_SINE;
        }
    }

    /** 0x20 + slot: AM and VIB enables, envelope type, key scale rate and frequency multiple. */
    private void writeAmVibEgtKsrMult(int slot, int value) {
        if (slot >= SLOTS) {
            return;
        }
        opTremoloEnable[slot] = (value & 0x80) != 0;
        opVibratoEnable[slot] = (value & 0x40) != 0;
        // Bit 5 selects the envelope type; clear means percussive, so the attack runs into the
        // release instead of holding at the sustain level.
        opPercussive[slot] = (value & 0x20) == 0;
        opKeyScaleRate[slot] = (value & 0x10) != 0;
        opMultiple[slot] = MULTIPLE[value & 0x0F];
        countLfoOperators();
        updatePhaseIncrements(opChannel[slot]);
        refreshEnvelopeRates(slot);
    }

    /** 0x40 + slot: key scale level in bits 7-6 and total level in bits 5-0. */
    private void writeKslTl(int slot, int value) {
        if (slot >= SLOTS) {
            return;
        }
        opKsl[slot] = (value >> 6) & 0x03;
        opTlDb[slot] = (value & 0x3F) * TL_DB;
        refreshStaticAttenuation(slot);
    }

    /** 0x60 + slot: attack rate in bits 7-4 and decay rate in bits 3-0. */
    private void writeArDr(int slot, int value) {
        if (slot >= SLOTS) {
            return;
        }
        opAr[slot] = (value >> 4) & 0x0F;
        opDr[slot] = value & 0x0F;
        refreshEnvelopeRates(slot);
    }

    /** 0x80 + slot: sustain level in bits 7-4 and release rate in bits 3-0. */
    private void writeSlRr(int slot, int value) {
        if (slot >= SLOTS) {
            return;
        }
        opSl[slot] = (value >> 4) & 0x0F;
        opRr[slot] = value & 0x0F;
        refreshEnvelopeRates(slot);
    }

    /** 0xE0 + slot: waveform select, which is inert while the WSE bit of register 0x01 is clear. */
    private void writeWaveform(int slot, int value) {
        if (slot >= SLOTS) {
            return;
        }
        opWaveformSelect[slot] = value & 0x03;
        opWaveform[slot] = waveSelectEnable ? opWaveformSelect[slot] : WAVE_SINE;
    }

    /** 0xA0 + n: the low eight bits of the F-Number. */
    private void writeFnumberLow(int channel, int value) {
        int low = value & FNUM_LOW_MASK;
        if (low == (fnumber[channel] & FNUM_LOW_MASK)) {
            return;
        }
        fnumber[channel] = (fnumber[channel] & 0xF00) | low;
        refreshNote(channel);
    }

    /**
     * 0xB0 + n: <b>bit 5 the key-on</b>, bits 4-2 the block and bits 1-0 the F-Number's top bits.
     * The key is level sensitive, as on the chip: writing a set key-on bit over a sounding channel
     * does not restart it, and the write that clears the bit is what starts the release.
     */
    private void writeFnumberHigh(int channel, int value) {
        int newBlock = (value >> BLOCK_SHIFT) & BLOCK_MASK;
        int newHigh = value & FNUM_HIGH_MASK;
        boolean noteChanged = newBlock != block[channel] || newHigh != ((fnumber[channel] >> 8) & FNUM_HIGH_MASK);
        block[channel] = newBlock;
        fnumber[channel] = (fnumber[channel] & FNUM_LOW_MASK) | (newHigh << 8);
        if (noteChanged) {
            refreshNote(channel);
        }
        boolean on = (value & KEY_ON_BIT) != 0;
        if (on && !keyActive[channel]) {
            keyOn(channel);
        } else if (!on && keyActive[channel]) {
            keyOff(channel);
        }
    }

    /** 0xC0 + n: feedback depth in bits 3-1 and connection in bit 0 (0 FM, 1 additive). */
    private void writeFeedbackConnection(int channel, int value) {
        feedback[channel] = (value >> 1) & 0x07;
        feedbackCycles[channel] = FEEDBACK_CYCLES[feedback[channel]];
        additive[channel] = (value & 1) != 0;
    }

    /** 0xBD: the two LFO depths and rhythm mode with its five percussion enables. */
    private void writeRhythm(int value) {
        tremoloDepthDb = (value & 0x80) != 0 ? AM_DEPTH_DEEP_DB : AM_DEPTH_DB;
        double cents = (value & 0x40) != 0 ? VIBRATO_CENTS_DEEP : VIBRATO_CENTS;
        vibratoRatio = Math.pow(2.0, -cents / 1200.0);
        boolean mode = (value & 0x20) != 0;
        int mask = value & PERCUSSION_ALL;
        rhythmMode = mode;
        if (!mode) {
            // Leaving rhythm mode stops the five voices, like the chip's own rhythm reset.
            for (int piece = 0; piece < PERCUSSION_PIECES; piece++) {
                percussionActive[piece] = false;
                percussionGain[piece] = 0.0;
            }
            rhythmMask = 0;
            return;
        }
        // A voice starts on the rising edge of its bit, so a driver retriggers a drum by clearing and
        // setting that bit again. The voices are additive: they never take a melodic channel over.
        triggerPercussion(mask & ~rhythmMask);
        rhythmMask = mask;
    }

    // =============================================================================================
    // Key handling
    // =============================================================================================

    /**
     * Key-on: both operators restart their attack from full attenuation and their phases are reset,
     * which is what makes an FM note start with the same transient every time.
     */
    private void keyOn(int channel) {
        keyActive[channel] = true;
        channelPlaying[channel] = true;
        feedbackSample[channel] = 0.0;
        int modulator = modulatorSlot(channel);
        int carrier = carrierSlot(channel);
        // Both operators belong to this channel's note from now on, so their key scaling and their
        // envelope rates follow its block and F-Number.
        retuneSlot(modulator, channel);
        retuneSlot(carrier, channel);
        opStage[modulator] = STAGE_ATTACK;
        opEnvelopeDb[modulator] = MAX_ATTENUATION_DB;
        opGain[modulator] = 0;
        opPhase[modulator] = 0;
        opStage[carrier] = STAGE_ATTACK;
        opEnvelopeDb[carrier] = MAX_ATTENUATION_DB;
        opGain[carrier] = 0;
        opPhase[carrier] = 0;
    }

    /**
     * Key-off: both operators enter the release stage from wherever their envelope had got to. An
     * operator that is already idle stays idle, so a key-off on a silent channel is inert.
     */
    private void keyOff(int channel) {
        keyActive[channel] = false;
        releaseEnvelope(modulatorSlot(channel));
        releaseEnvelope(carrierSlot(channel));
    }

    private void releaseEnvelope(int slot) {
        if (opStage[slot] != STAGE_IDLE) {
            opStage[slot] = STAGE_RELEASE;
        }
    }

    // =============================================================================================
    // Note and envelope bookkeeping
    // =============================================================================================

    /** Recomputes everything that follows the block and the F-Number of a channel. */
    private void refreshNote(int channel) {
        updatePhaseIncrements(channel);
        retuneSlot(modulatorSlot(channel), channel);
        retuneSlot(carrierSlot(channel), channel);
    }

    /** Re-points a slot at the channel that is now playing it and recomputes its key scaling. */
    private void retuneSlot(int slot, int channel) {
        opChannel[slot] = channel;
        refreshStaticAttenuation(slot);
        refreshEnvelopeRates(slot);
    }

    /** The key code of a channel, 0..15: the block, with the F-Number's top bit below it. */
    private int keyCode(int channel) {
        return (block[channel] << 1) | ((fnumber[channel] >> 9) & 1);
    }

    /** Total level plus key scale attenuation of one operator, in dB. */
    private void refreshStaticAttenuation(int slot) {
        int key = keyCode(opChannel[slot]);
        // KSL attenuates high notes by 1.5, 3 or 6 dB per octave; two key codes are one octave.
        opBaseDb[slot] = opTlDb[slot] + KSL_DB_PER_OCTAVE[opKsl[slot]] * (key / 2.0);
    }

    /**
     * Recomputes the per-sample envelope steps of one operator. The chip's rate adder works in
     * quarter rate steps: the register field supplies {@code rate * 4} and the key code supplies the
     * rest, the key code itself when KSR is set and a quarter of it when it is clear.
     */
    private void refreshEnvelopeRates(int slot) {
        int key = keyCode(opChannel[slot]);
        int offset = opKeyScaleRate[slot] ? key : key >> 2;
        opAttackStep[slot] = MAX_ATTENUATION_DB / ATTACK_SECONDS[rateStep(opAr[slot], offset)] / sampleRate;
        opDecayStep[slot] = SLOPE_DB_PER_SEC[rateStep(opDr[slot], offset)] / sampleRate;
        opReleaseStep[slot] = SLOPE_DB_PER_SEC[rateStep(opRr[slot], offset)] / sampleRate;
    }

    /** Clamps a register rate plus a key code offset onto the 64 quarter-step rate ladder. */
    private static int rateStep(int rate, int offset) {
        int step = (rate << 2) + offset;
        if (step < 0) {
            return 0;
        }
        return step > RATE_STEPS - 1 ? RATE_STEPS - 1 : step;
    }

    /** The attenuation the decay stage holds at, in dB. */
    private static double sustainLevelDb(int sustainLevel) {
        return sustainLevel == 15 ? SL_MAX_DB : sustainLevel * SL_DB;
    }

    /**
     * Advances every operator envelope by one output sample and refreshes its gain. Envelopes run in
     * the dB domain: the attack subtracts from the attenuation until it reaches silence, the decay
     * adds until it reaches the sustain level and holds there, and the release adds until the
     * operator is fully attenuated and goes idle.
     */
    private void advanceEnvelopes() {
        for (int slot = 0; slot < SLOTS; slot++) {
            double attenuation = opEnvelopeDb[slot];
            switch (opStage[slot]) {
                case STAGE_ATTACK:
                    attenuation -= opAttackStep[slot];
                    if (attenuation <= 0.0) {
                        attenuation = 0.0;
                        // A percussive envelope leaves the attack straight into the release.
                        opStage[slot] = opPercussive[slot] ? STAGE_RELEASE : STAGE_DECAY;
                    }
                    break;
                case STAGE_DECAY:
                    double sustain = sustainLevelDb(opSl[slot]);
                    attenuation += opDecayStep[slot];
                    if (attenuation >= sustain) {
                        attenuation = sustain;
                        opStage[slot] = STAGE_SUSTAIN;
                    }
                    break;
                case STAGE_RELEASE:
                    attenuation += opReleaseStep[slot];
                    if (attenuation >= MAX_ATTENUATION_DB) {
                        attenuation = MAX_ATTENUATION_DB;
                        opStage[slot] = STAGE_IDLE;
                    }
                    break;
                default:
                    // Idle holds full attenuation and sustain holds its level.
                    break;
            }
            opEnvelopeDb[slot] = attenuation;
            double db = opBaseDb[slot] + attenuation;
            if (opTremoloEnable[slot]) {
                db += tremoloNowDb;
            }
            opGain[slot] = gainForDb(db);
        }
        // A channel stops being mixed once its key is up and both of its operators have gone quiet.
        for (int channel = 0; channel < CHANNELS; channel++) {
            if (channelPlaying[channel] && !keyActive[channel]
                && opStage[modulatorSlot(channel)] == STAGE_IDLE
                && opStage[carrierSlot(channel)] == STAGE_IDLE) {
                channelPlaying[channel] = false;
            }
        }
    }

    /** Converts an attenuation in dB to the 0..32767 linear gain, clamped at both ends. */
    private int gainForDb(double db) {
        if (!(db > 0.0)) {
            return 32767;
        }
        if (db >= MAX_ATTENUATION_DB) {
            return 0;
        }
        int index = (int) (db * GAIN_STEPS_PER_DB);
        return index >= gainTable.length ? 0 : gainTable[index];
    }

    // =============================================================================================
    // Synthesis
    // =============================================================================================

    /**
     * Evaluates one operator at the current sample and advances its phase.
     *
     * @param slot       operator index
     * @param modulation phase offset in 32-bit phase units contributed by its modulator
     * @return the operator's raw output in operator units, already scaled by its envelope
     */
    private double operatorSample(int slot, int modulation) {
        // Only the top twelve bits address the table, the addition wraps modulo 2^32 and the mask is
        // applied after the shift, so the index is valid whatever the modulation is.
        int index = (opPhase[slot] + modulation) >>> 20 & (WAVE_SIZE - 1);
        double value = waveTable[opWaveform[slot]][index];
        opPhase[slot] = opPhase[slot] + opPhaseInc[slot];
        return value * opGain[slot] / GAIN_FULL_SCALE;
    }

    /** Phase offset, in 32-bit units, of an operator output at some number of modulation cycles. */
    private static int phaseOffset(double operatorOutput, double cycles) {
        return (int) (long) (operatorOutput * cycles * PHASE_PER_UNIT);
    }

    /** Maps a mix in operator units onto 16-bit range with a soft saturation instead of a wrap. */
    private static int softClip(double sample) {
        int value = (int) Math.round(Math.tanh(sample / OUTPUT_FULL_SCALE) * OUTPUT_FULL_SCALE);
        if (value > 32767) {
            return 32767;
        }
        return value < -32767 ? -32767 : value;
    }

    /** Recomputes a channel's phase increments from F-Number, block and each operator's multiple. */
    private void updatePhaseIncrements(int channel) {
        int modulator = modulatorSlot(channel);
        int carrier = carrierSlot(channel);
        // The chip's own pitch relation, at the output rate: f = fnum * 49716 / 2^(20 - block) Hz,
        // and one full cycle of phase is 2^32 of the accumulator.
        double hz = fnumber[channel] * CHIP_RATE / Math.scalb(1.0, 20 - block[channel]);
        double base = hz / sampleRate * PHASE_PER_CYCLE;
        opBaseInc[modulator] = base * opMultiple[modulator];
        opBaseInc[carrier] = base * opMultiple[carrier];
        opPhaseInc[modulator] = toPhaseStep(opBaseInc[modulator]);
        opPhaseInc[carrier] = toPhaseStep(opBaseInc[carrier]);
    }

    /**
     * Narrows a phase step to the accumulator's width. Frequencies above the output's Nyquist limit
     * fold back exactly as an aliasing note does; the value is reduced before the cast so that the
     * conversion never saturates and a step is never negative.
     */
    private static int toPhaseStep(double step) {
        if (!(step > 0.0)) {
            return 0;
        }
        double reduced = step % PHASE_PER_CYCLE;
        return (int) (long) reduced;
    }

    /** Recomputes the shared LFO phase and, with it, the vibrato-affected phase increments. */
    private void stepLowFrequencyOscillators() {
        phaseTremolo += TREMOLO_HZ / sampleRate;
        if (phaseTremolo >= 1.0) {
            phaseTremolo -= 1.0;
        }
        if (tremoloOperators > 0) {
            tremoloNowDb = tremoloDepthDb * (0.5 + 0.5 * Math.sin(phaseTremolo * TWO_PI));
        } else {
            tremoloNowDb = 0.0;
        }
        phaseVibrato += VIBRATO_HZ / sampleRate;
        if (phaseVibrato >= 1.0) {
            phaseVibrato -= 1.0;
        }
        if (vibratoOperators > 0) {
            // One swing of the LFO, taken as a ratio so it can scale any operator's increment.
            double factor = 1.0 - (1.0 - vibratoRatio) * (0.5 + 0.5 * Math.sin(phaseVibrato * TWO_PI));
            for (int slot = 0; slot < SLOTS; slot++) {
                if (opVibratoEnable[slot]) {
                    opPhaseInc[slot] = toPhaseStep(opBaseInc[slot] * factor);
                }
            }
        }
    }

    /** Counts the operators that follow the LFOs, so the render loop can skip them when none do. */
    private void countLfoOperators() {
        int tremolo = 0;
        int vibrato = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (opTremoloEnable[slot]) {
                tremolo++;
            }
            if (opVibratoEnable[slot]) {
                vibrato++;
            }
        }
        tremoloOperators = tremolo;
        vibratoOperators = vibrato;
    }

    /** Advances the shared noise shift register, clocked once per output sample. */
    private void advanceNoise() {
        int bit = (noiseRegister ^ (noiseRegister >> 2) ^ (noiseRegister >> 3) ^ (noiseRegister >> 5)) & 1;
        noiseRegister = (noiseRegister >> 1) | (bit << 15);
        if (noiseRegister == 0) {
            // An LFSR that reached all zeros would stay there; the sequencer never lets it.
            noiseRegister = 0x4001;
        }
    }

    // =============================================================================================
    // Rhythm layer
    // =============================================================================================

    /** Starts every percussion voice whose enable bit has just gone from clear to set. */
    private void triggerPercussion(int risingEdges) {
        for (int piece = 0; piece < PERCUSSION_PIECES; piece++) {
            if ((risingEdges & (1 << piece)) == 0) {
                continue;
            }
            percussionActive[piece] = true;
            percussionGain[piece] = 1.0;
            percussionPhase[piece] = 0.0;
            percussionSweep[piece] = PERCUSSION_SWEEP_HZ[piece] / sampleRate * WAVE_SIZE;
        }
    }

    /** Sums the rhythm voices that are currently sounding, in operator units. */
    private double renderPercussion() {
        double sum = 0.0;
        for (int piece = 0; piece < PERCUSSION_PIECES; piece++) {
            if (percussionActive[piece]) {
                sum += renderPercussionVoice(piece);
            }
        }
        return sum;
    }

    /** Renders one rhythm voice: the noise pieces read the LFSR, the others a pitch-swept sine. */
    private double renderPercussionVoice(int piece) {
        double noiseMix = PERCUSSION_NOISE_MIX[piece];
        double value = 0.0;
        if (noiseMix > 0.0) {
            // One bit of the shared shift register per voice, so the five pieces are decorrelated.
            value += noiseMix * (((noiseRegister >> piece) & 1) != 0 ? 1.0 : -1.0);
        }
        if (noiseMix < 1.0) {
            // Falling pitch is what gives the bass drum and the tom their thump. The sweep decays
            // towards zero, so the phase always advances and always stays in range.
            double phase = percussionPhase[piece] + percussionStep[piece] + percussionSweep[piece];
            phase %= WAVE_SIZE;
            if (!(phase >= 0.0)) {
                phase = 0.0;
            }
            percussionPhase[piece] = phase;
            double body = waveTable[WAVE_SINE][((int) phase) & (WAVE_SIZE - 1)] / OPERATOR_FULL_SCALE;
            value += (1.0 - noiseMix) * body;
            percussionSweep[piece] *= percussionSweepFall;
            if (percussionSweep[piece] < 1.0E-6) {
                percussionSweep[piece] = 0.0;
            }
        }
        double gain = percussionGain[piece] * PERCUSSION_LEVEL[piece];
        percussionGain[piece] *= percussionDecay[piece];
        if (percussionGain[piece] < PERCUSSION_SILENCE) {
            percussionGain[piece] = 0.0;
            percussionActive[piece] = false;
        }
        return value * gain * OPERATOR_FULL_SCALE;
    }

    // =============================================================================================
    // Small helpers
    // =============================================================================================
}
