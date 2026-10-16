package com.MessTech.doom.s.opl;

import java.util.logging.Level;

import com.MessTech.doom.mochadoom.Loggers;
import com.MessTech.doom.s.IMusic;
import com.MessTech.doom.s.ISoundDriver;
import com.MessTech.doom.s.IWadAware;
import com.MessTech.doom.s.MusicMixBuffer;
import com.MessTech.doom.w.IWadLoader;

/**
 * DOOM's music, synthesised here instead of by the JDK.
 * <p>
 * The songs are FM: they were written for the Yamaha YM3812 (OPL2) that shipped in AdLib and
 * Sound Blaster cards, and the WAD carries the matching {@code GENMIDI} patch table. So this module
 * emulates the chip, plays the song through it, and hands the resulting PCM to
 * {@link MusicMixBuffer} - which is the same hand-off the synthesiser-based module used, so the
 * music still leaves through the console's single audio line along with the sound effects.
 * <p>
 * The point of doing it this way is that it depends on nothing outside the JDK's ordinary MIDI data
 * classes. The previous approach asked the JDK's software synthesiser for its audio stream, which
 * lives in a package Java 9 and later do not export, so it needed a {@code --add-opens} to be mixed
 * in and quietly fell back to a device of its own without one. An FM chip emulator is just
 * arithmetic: it behaves the same on Java 8 and on Java 25, and it sounds like the game did.
 * <p>
 * Where a WAD has no {@code GENMIDI} lump there is no patch table to play, and the module stays
 * silent rather than inventing one.
 */
public final class OplMusicModule implements IMusic, IWadAware {

    /** Frames rendered per pass. 1024 frames is 4096 bytes, about 46 ms of audio. */
    private static final int CHUNK_FRAMES = 1024;

    /** Big-endian stereo 16-bit, the same shape {@code SuperDoomSoundDriver} mixes. */
    private static final int BYTES_PER_FRAME = 4;

    private final MusicMixBuffer mix = MusicMixBuffer.get();

    private OPL2 chip;
    private OplMusicPlayer player;
    private IWadLoader wadLoader;

    private Thread pump;
    private volatile boolean pumping;
    private volatile boolean available;

    private DoomSong song;
    private volatile boolean looping;

    private short[] frames;
    private byte[] pcm;

    // ------------------------------------------------------------------ IMusic

    @Override
    public void setWadLoader(final IWadLoader loader) {
        this.wadLoader = loader;
    }

    @Override
    public void InitMusic() {
        try {
            if (wadLoader == null) {
                return;
            }
            final byte[] lump = wadLoader.CacheLumpNameAsRawBytes("GENMIDI", com.MessTech.doom.data.Defines.PU_STATIC);
            final GenMidiBank bank = GenMidiBank.parse(lump);
            if (bank == null) {
                Loggers.getLogger(OplMusicModule.class.getName())
                    .warning("This WAD has no usable GENMIDI patch table; the game console will be silent");
                return;
            }

            this.chip = new OPL2(ISoundDriver.SAMPLERATE);
            this.player = new OplMusicPlayer(chip, bank);
            this.frames = new short[CHUNK_FRAMES * 2];
            this.pcm = new byte[CHUNK_FRAMES * BYTES_PER_FRAME];

            available = true;
            mix.setEnabled(true);
            startPump();
            Loggers.getLogger(OplMusicModule.class.getName())
                .info("Music through the OPL2 emulator at " + ISoundDriver.SAMPLERATE + " Hz");
        } catch (final Exception e) {
            Loggers.getLogger(OplMusicModule.class.getName())
                .log(Level.WARNING, "Music module unavailable, continuing without music", e);
            shutdown();
        }
    }

    /**
     * Renders the song into the mix buffer, and doubles as its clock.
     * <p>
     * The chip is rendered only when the buffer has room, and the buffer is emptied by the sound
     * driver one chunk per tick. That makes the room itself the playback rate, so the music runs at
     * the speed the sound card consumes it with no timer of our own to drift.
     */
    private void startPump() {
        pumping = true;
        pump = new Thread(this::pumpAudio, "MessTech-DoomOplMusic");
        pump.setDaemon(true);
        pump.start();
    }

    private void pumpAudio() {
        while (pumping) {
            try {
                if (!mix.hasRoomFor(pcm.length)) {
                    Thread.sleep(4L);
                    continue;
                }
                if (player.isFinished() && looping && song != null) {
                    player.setScore(song);
                    player.start();
                }
                java.util.Arrays.fill(frames, (short) 0);
                player.render(frames, 0, CHUNK_FRAMES);
                for (int i = 0; i < frames.length; i++) {
                    final int sample = frames[i];
                    pcm[i * 2] = (byte) ((sample >> 8) & 0xFF);
                    pcm[i * 2 + 1] = (byte) (sample & 0xFF);
                }
                mix.write(pcm, 0, pcm.length);
            } catch (final InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                break;
            } catch (final RuntimeException e) {
                if (pumping) {
                    Loggers.getLogger(OplMusicModule.class.getName())
                        .log(Level.WARNING, "The music stopped unexpectedly", e);
                    pumping = false;
                }
            }
        }
    }

    @Override
    public void ShutdownMusic() {
        shutdown();
    }

    private void shutdown() {
        available = false;
        pumping = false;
        song = null;
        mix.setEnabled(false);
        final Thread thread = pump;
        pump = null;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(500L);
            } catch (final InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
        }
        if (player != null) {
            player.stop();
            player = null;
        }
        chip = null;
    }

    @Override
    public void SetMusicVolume(final int volume) {
        // The mixer scales the music as it sums it into the sound effects, so this needs no clock
        // and no chip round trip.
        mix.setVolume(volume / 127.0F);
    }

    @Override
    public void PauseSong(final int handle) {
        if (available && player != null) {
            player.stop();
        }
    }

    @Override
    public void ResumeSong(final int handle) {
        if (available && player != null && song != null) {
            player.start();
        }
    }

    @Override
    public int RegisterSong(final byte[] data) {
        if (!available || data == null || data.length == 0) {
            return -1;
        }
        final DoomSong parsed = DoomSong.parse(data);
        if (parsed == null) {
            Loggers.getLogger(OplMusicModule.class.getName())
                .warning("Song is neither MUS nor MIDI; skipping it");
            song = null;
            return -1;
        }
        song = parsed;
        return 0;
    }

    @Override
    public void PlaySong(final int handle, final boolean loop) {
        if (!available || player == null || song == null) {
            return;
        }
        this.looping = loop;
        player.setScore(song);
        player.start();
    }

    @Override
    public void StopSong(final int handle) {
        if (available && player != null) {
            player.stop();
        }
    }

    @Override
    public void UnRegisterSong(final int handle) {
        song = null;
    }
}
