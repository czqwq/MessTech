package com.MessTech.doom.s;

/**
 * The hand-off point between DOOM's music and DOOM's sound effects.
 * <p>
 * Upstream Mocha Doom had these two on completely separate audio paths ({@code IMusic} talked to a
 * MIDI device, {@code ISoundDriver} opened its own data line), which on a desktop means two devices
 * and two volume controls. In the console there is exactly one output - the {@code SourceDataLine}
 * {@code SuperDoomSoundDriver} writes its mixed chunks to - so the music is rendered to PCM and
 * parked here, and the playback thread sums it into each chunk on its way out.
 * <p>
 * The buffer is deliberately dumb: a byte ring in the same format as the mixer's output (22050 Hz,
 * signed 16-bit, stereo, big-endian, matching {@code new AudioFormat(SAMPLERATE, 16, 2, true,
 * true)} in {@code InitSound}). It is fed by the music thread and drained by the playback thread,
 * so both ends synchronise.
 * <p>
 * Starving is normal (the synthesiser is not always producing) and is answered with silence rather
 * than with a stall - a hiccup in the music must never hold up the sound effects.
 */
public final class MusicMixBuffer {

    /**
     * 32 KB, about 0.37 seconds of this format. This is not just a slack buffer, it is the clock:
     * the synthesiser renders on demand with no pacing of its own (pulling as fast as possible
     * produces roughly 22x real time, almost all of it silence, because the sequencer hands over its
     * events in real time), so the renderer is only allowed to run when there is room here. Since
     * the mixer drains exactly one chunk per tick, the room appearing <em>is</em> real time.
     */
    private static final int SIZE = 1 << 15;

    private static final MusicMixBuffer INSTANCE = new MusicMixBuffer();

    private final byte[] ring = new byte[SIZE];

    private int readPosition;
    private int writePosition;
    private int available;
    private long drained;

    private volatile boolean enabled;
    private volatile float volume = 1.0F;

    public static MusicMixBuffer get() {
        return INSTANCE;
    }

    /** Called when the music module starts or stops; a disabled buffer is drained and ignored. */
    public void setEnabled(final boolean value) {
        this.enabled = value;
        if (!value) {
            clear();
        }
    }

    public synchronized void clear() {
        readPosition = 0;
        writePosition = 0;
        available = 0;
    }

    /** Music volume as 0..1. Applied while mixing, so a volume change is heard within one chunk. */
    public void setVolume(final float value) {
        this.volume = value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    public synchronized int available() {
        return available;
    }

    /**
     * Total bytes the mixer has taken out of here. Not used by anything at runtime - it is what
     * makes "is the music actually flowing, or is it just sitting in the buffer" answerable.
     */
    public synchronized long drainedBytes() {
        return drained;
    }

    /**
     * Whether there is music waiting to be mixed in. The sound driver uses this to decide that it
     * has work to do: upstream only mixed a chunk when a sound effect was playing, so music by
     * itself would have sat here forever.
     */
    public synchronized boolean hasMusicWaiting() {
        return enabled && available > 0;
    }

    /**
     * Whether {@code length} more bytes fit. The renderer waits on this, which is what keeps it
     * running at playback speed rather than at the speed of an unthrottled pull.
     */
    public synchronized boolean hasRoomFor(final int length) {
        return SIZE - available >= length;
    }

    /**
     * Queues rendered PCM. When the reader has fallen behind the oldest bytes are dropped, because
     * music that lags further and further behind the game is worse than music that skips.
     */
    public synchronized void write(final byte[] source, final int offset, final int length) {
        for (int i = 0; i < length; i++) {
            ring[writePosition] = source[offset + i];
            writePosition = writePosition + 1 == SIZE ? 0 : writePosition + 1;
            if (available == SIZE) {
                readPosition = readPosition + 1 == SIZE ? 0 : readPosition + 1;
            } else {
                available++;
            }
        }
    }

    /**
     * Sums the next {@code length} bytes of music into {@code dest}, which holds the mixed sound
     * effects in the same format. Samples saturate rather than wrap: clipping a loud moment is
     * ugly, but a sign flip is much worse.
     */
    public synchronized void mixInto(final byte[] dest, final int offset, final int length) {
        if (!enabled) {
            return;
        }
        final float scale = volume;
        if (scale <= 0.0F) {
            // Still consume, so the music stays in step with playback while muted.
            skip(length);
            return;
        }

        final int end = Math.min(offset + length, dest.length);
        for (int i = offset; i + 1 < end; i += 2) {
            if (available < 2) {
                // Starved: the rest of this chunk gets no music at all, and nothing is fabricated.
                return;
            }
            final int music = (short) (((ring[readPosition] & 0xFF) << 8) | (ring[readPosition + 1] & 0xFF));
            readPosition = readPosition + 2 >= SIZE ? readPosition + 2 - SIZE : readPosition + 2;
            available -= 2;
            drained += 2;

            if (music == 0) {
                continue;
            }
            final int effects = (short) (((dest[i] & 0xFF) << 8) | (dest[i + 1] & 0xFF));
            int sum = effects + (int) (music * scale);
            if (sum > 32767) {
                sum = 32767;
            } else if (sum < -32768) {
                sum = -32768;
            }
            dest[i] = (byte) ((sum >> 8) & 0xFF);
            dest[i + 1] = (byte) (sum & 0xFF);
        }
    }

    private void skip(final int length) {
        final int drop = Math.min(length, available);
        readPosition = (readPosition + drop) % SIZE;
        available -= drop;
        drained += drop;
    }

    private MusicMixBuffer() {}
}
