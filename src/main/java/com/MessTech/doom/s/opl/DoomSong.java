package com.MessTech.doom.s.opl;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.MessTech.doom.s.MusReader;

/**
 * A DOOM song, in whichever of the two formats the WAD happens to use, flattened into one list of
 * timestamped events.
 * <p>
 * Both formats turn up in the wild. id's own WADs store music as MUS, the compact format the DMX
 * sound engine used; Freedoom ships ordinary MIDI files under the same {@code D_*} lump names. The
 * engine's {@code MusReader} only knows MUS, which is why it throws on a Freedoom lump - and in fact
 * it was reading MIDI bytes as MUS, which is where its "invalid controller value" came from.
 * <p>
 * MIDI is read through {@code MidiSystem.getSequence}, which is ordinary public API and behaves the
 * same on every Java version - it is the synthesiser that does not, and this module exists
 * precisely to avoid needing one. The parser then walks the tracks in tick order, converting ticks
 * to seconds with whatever tempo map the file carries, and flattens both formats into the same
 * event vocabulary so the player never has to care which one it was given.
 */
public final class DoomSong {

    // Event types. These are neither MUS controllers nor MIDI controllers: they are the handful of
    // things this chip can actually act on, and each parser translates its own numbering into them.
    public static final int NOTE_OFF = 0;
    public static final int NOTE_ON = 1;
    public static final int PROGRAM = 2;
    public static final int VOLUME = 3;
    public static final int EXPRESSION = 4;
    public static final int ALL_NOTES_OFF = 5;

    /** One thing that happens at one moment. */
    public static final class Event {

        /** Seconds from the start of the song. */
        public final double time;
        public final int type;
        /** MUS channel 0-15, or MIDI channel; 9 is percussion in both. */
        public final int channel;
        public final int data1;
        public final int data2;

        Event(final double time, final int type, final int channel, final int data1, final int data2) {
            this.time = time;
            this.type = type;
            this.channel = channel;
            this.data1 = data1;
            this.data2 = data2;
        }
    }

    private final List<Event> events;
    private final double lengthSeconds;
    private final String source;

    private DoomSong(final List<Event> events, final double lengthSeconds, final String source) {
        this.events = events;
        this.lengthSeconds = lengthSeconds;
        this.source = source;
    }

    public List<Event> getEvents() {
        return events;
    }

    public double getLengthSeconds() {
        return lengthSeconds;
    }

    /** "MUS" or "MIDI", for the log line. */
    public String getSource() {
        return source;
    }

    /** Reads either format, or returns null when it is neither. */
    public static DoomSong parse(final byte[] data) {
        if (isMidi(data)) {
            try {
                return fromSequence(MidiSystem.getSequence(new ByteArrayInputStream(data)), "MIDI");
            } catch (final Exception e) {
                return null;
            }
        }
        if (isMus(data)) {
            // MUS goes through the engine's own reader. It is the one piece of MUS handling in this
            // code base that has been exercised against real WADs, and the format is fiddly enough
            // (velocity presence, group delays, controller numbering) that a second hand-rolled
            // reader is a liability rather than a saving. A hand-written one was tried first and
            // disagreed with this reader on the same bytes, which is exactly the argument for not
            // having two of them. It hands back the same kind of Sequence, so the walk below is
            // shared: one conversion, so a song cannot behave differently by virtue of its format.
            try {
                return fromSequence(MusReader.getSequence(new ByteArrayInputStream(data)), "MUS");
            } catch (final Exception e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isMidi(final byte[] data) {
        return data != null && data.length > 14 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd';
    }

    /** True for something that starts like a MUS lump. */
    public static boolean isMus(final byte[] data) {
        return data != null && data.length > 16
            && data[0] == 'M'
            && data[1] == 'U'
            && data[2] == 'S'
            && data[3] == 0x1A;
    }

    // ------------------------------------------------------------------ sequence to events

    /**
     * Flattens a MIDI sequence into timestamped events. Both lump formats end up here: MIDI parsed
     * by the JDK, MUS parsed by the engine's own reader, which hands back the same kind of object.
     * Timing is converted from ticks to seconds through whatever tempo map the file carries, and for
     * MUS that comes out as one delay unit = 1/140 s, which is what the format means.
     */
    private static DoomSong fromSequence(final Sequence sequence, final String source) {
        final List<MidiEvent> all = new ArrayList<>();
        for (final Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                all.add(track.get(i));
            }
        }
        Collections.sort(all, Comparator.comparingLong(MidiEvent::getTick));

        final List<Event> events = new ArrayList<>();
        final float division = sequence.getDivisionType();
        final int resolution = sequence.getResolution();
        // With PPQ the tick length depends on the tempo map; with SMPTE it is fixed by the header.
        double microsPerTick = division == Sequence.PPQ ? 500000.0D / resolution : 1000000.0D / (division * resolution);

        long lastTick = 0;
        double seconds = 0.0D;
        for (final MidiEvent midi : all) {
            seconds += (midi.getTick() - lastTick) * microsPerTick / 1000000.0D;
            lastTick = midi.getTick();

            if (midi.getMessage() instanceof MetaMessage) {
                final MetaMessage meta = (MetaMessage) midi.getMessage();
                if (meta.getType() == 0x51 && meta.getData().length >= 3) {
                    final byte[] tempo = meta.getData();
                    final int microsPerQuarter = ((tempo[0] & 0xFF) << 16) | ((tempo[1] & 0xFF) << 8)
                        | (tempo[2] & 0xFF);
                    if (division == Sequence.PPQ) {
                        microsPerTick = microsPerQuarter / (double) resolution;
                    }
                }
                continue;
            }
            if (!(midi.getMessage() instanceof ShortMessage)) {
                continue;
            }
            final ShortMessage message = (ShortMessage) midi.getMessage();
            final int channel = message.getChannel() & 0x0F;
            switch (message.getCommand()) {
                case ShortMessage.NOTE_ON:
                    if (message.getData2() > 0) {
                        events.add(new Event(seconds, NOTE_ON, channel, message.getData1(), message.getData2()));
                    } else {
                        events.add(new Event(seconds, NOTE_OFF, channel, message.getData1(), 0));
                    }
                    break;
                case ShortMessage.NOTE_OFF:
                    events.add(new Event(seconds, NOTE_OFF, channel, message.getData1(), 0));
                    break;
                case ShortMessage.PROGRAM_CHANGE:
                    events.add(new Event(seconds, PROGRAM, channel, message.getData1() & 0x7F, 0));
                    break;
                case ShortMessage.CONTROL_CHANGE:
                    addMidiControl(events, seconds, channel, message.getData1(), message.getData2());
                    break;
                default:
                    // Pitch bend and channel pressure: the chip has no cheap equivalent.
                    break;
            }
        }
        return new DoomSong(events, seconds, source);
    }

    private static void addMidiControl(final List<Event> events, final double time, final int channel,
        final int controller, final int value) {
        switch (controller) {
            case 7:
                events.add(new Event(time, VOLUME, channel, value, 0));
                break;
            case 11:
                events.add(new Event(time, EXPRESSION, channel, value, 0));
                break;
            case 120:
            case 123:
                events.add(new Event(time, ALL_NOTES_OFF, channel, 0, 0));
                break;
            default:
                // Pan on a mono chip, sustain pedal, vibrato depth: nowhere useful to go.
                break;
        }
    }

}
