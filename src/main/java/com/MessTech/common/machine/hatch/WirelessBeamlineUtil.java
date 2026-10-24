package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.enums.Dyes;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchBeamlineConnector;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;

/**
 * Pairing registry of the wireless beamline (particle) hatches.
 * <p>
 * GT pushes a {@link BeamLinePacket} from an output hatch through a line of {@code MTEBeamlinePipe}s into an input
 * hatch;
 * these hatches replace that line with a registry lookup keyed by dye colour, so same-colour hatches pair up anywhere,
 * dimensions included.
 * <p>
 * 1:1 is enforced: a colour carries a beam only with <b>exactly one</b> output and <b>exactly one</b> input; anything
 * else
 * (no counterpart, several outputs, several inputs) drops it.
 */
public final class WirelessBeamlineUtil {

    /** Every live wireless beamline hatch; needed because isolated hatches share no pipe edge. */
    private static final Set<MTEHatchBeamlineConnector> REGISTRY = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Packets being handed over by {@link #moveBeam(MTEHatchOutputBeamline)} right now: the input accepts only these,
     * so a
     * wired output hatch in front of it cannot bypass the colour channel.
     */
    private static final Set<BeamLinePacket> WIRELESS_DELIVERIES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Channel of a hatch that is not painted. GT5U uses {@code -1} for "no colour". */
    public static final int INVALID_CHANNEL = -1;

    /** Lang key prefix of the colour names GT5U already ships for its spray cans. */
    private static final String COLOUR_LANG_KEY = "GT5U.infinite_spray_can.color.";

    private WirelessBeamlineUtil() {}

    public static void register(MTEHatchBeamlineConnector hatch) {
        if (hatch != null) REGISTRY.add(hatch);
    }

    public static void unregister(MTEHatchBeamlineConnector hatch) {
        if (hatch != null) REGISTRY.remove(hatch);
    }

    /**
     * Pairing key of a hatch: its dye colour, or {@link #INVALID_CHANNEL} while unpainted. Unpainted hatches never
     * link,
     * so none joins a channel by accident.
     */
    public static int getChannel(MTEHatchBeamlineConnector hatch) {
        if (hatch == null) return INVALID_CHANNEL;
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null) return INVALID_CHANNEL;
        int colour = base.getColorization();
        // Dyes are 0..15; anything else (GT5U's -1 for "no colour", or a corrupted byte) is not a channel
        return Dyes.isDyeIndex(colour) ? colour : INVALID_CHANNEL;
    }

    private static boolean isValid(MTEHatchBeamlineConnector hatch) {
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        return base != null && !base.isDead() && base.getMetaTileEntity() == hatch;
    }

    /** Drops hatches whose tile entity is gone, so the registry cannot grow forever. */
    private static void prune() {
        for (MTEHatchBeamlineConnector hatch : REGISTRY) {
            if (!isValid(hatch)) REGISTRY.remove(hatch);
        }
    }

    private static List<MTEHatchBeamlineConnector> find(int channel, Class<?> type) {
        List<MTEHatchBeamlineConnector> result = new ArrayList<>();
        if (channel == INVALID_CHANNEL) return result;
        for (MTEHatchBeamlineConnector hatch : REGISTRY) {
            if (!type.isInstance(hatch)) continue;
            if (!isValid(hatch)) continue;
            if (getChannel(hatch) == channel) result.add(hatch);
        }
        return result;
    }

    public static List<MTEHatchInputBeamline> findInputs(int channel) {
        List<MTEHatchInputBeamline> result = new ArrayList<>();
        for (MTEHatchBeamlineConnector hatch : find(channel, MTEHatchInputBeamline.class)) {
            result.add((MTEHatchInputBeamline) hatch);
        }
        return result;
    }

    /** Output hatches (plain and filtered) on that colour channel. */
    public static int countOutputs(int channel) {
        return find(channel, MTEHatchOutputBeamline.class).size();
    }

    public static int countInputs(int channel) {
        return find(channel, MTEHatchInputBeamline.class).size();
    }

    /**
     * Hands the output's beam to its counterpart, then clears the hatch; called from the hatch's {@code moveAround}
     * tick,
     * i.e. at the wired pipe's cadence - one offer per move tick, never queued. Dropped unless the channel is a clean
     * 1:1
     * pair, like the wired hatch when no pipe reaches an input (LHC/beam splitter publish only once per recipe cycle,
     * so
     * nothing is cached: the beam belongs to the tick it was published in).
     */
    public static void moveBeam(MTEHatchOutputBeamline output) {
        BeamLinePacket packet = output.dataPacket;
        output.dataPacket = null;
        if (packet == null) return;

        int channel = getChannel(output);
        if (channel == INVALID_CHANNEL) return;
        // strict 1:1: exactly one input to receive it, and this output must be the only one on the channel
        if (countOutputs(channel) != 1) return;
        List<MTEHatchInputBeamline> inputs = findInputs(channel);
        if (inputs.size() != 1) return;

        WIRELESS_DELIVERIES.add(packet);
        try {
            inputs.get(0)
                .setContents(packet);
        } finally {
            WIRELESS_DELIVERIES.remove(packet);
        }
    }

    /** True while {@link #moveBeam(MTEHatchOutputBeamline)} hands that exact packet to an input hatch. */
    public static boolean isWirelessDelivery(BeamLinePacket packet) {
        return packet != null && WIRELESS_DELIVERIES.contains(packet);
    }

    public enum LinkState {
        /** Not painted, so it is not part of any channel. */
        UNPAINTED,
        /** Painted, but the colour has no counterpart at all. */
        NO_COUNTERPART,
        /** The colour has more than one output or more than one input. */
        CONFLICT,
        /** Exactly one output and one input share the colour. */
        LINKED
    }

    /** Live pairing state of a hatch, for the scanner info. */
    public static LinkState getLinkState(MTEHatchBeamlineConnector hatch) {
        prune();
        int channel = getChannel(hatch);
        if (channel == INVALID_CHANNEL) return LinkState.UNPAINTED;
        int outputs = countOutputs(channel);
        int inputs = countInputs(channel);
        if (outputs > 1 || inputs > 1) return LinkState.CONFLICT;
        if (outputs == 0 || inputs == 0) return LinkState.NO_COUNTERPART;
        return LinkState.LINKED;
    }

    private static String stateKey(LinkState state) {
        switch (state) {
            case UNPAINTED:
                return "unpainted";
            case CONFLICT:
                return "conflict";
            case LINKED:
                return "linked";
            default:
                return "no_counterpart";
        }
    }

    /**
     * Scanner info is built server-side but decoded by the client, so the colour travels as
     * {@code [colour code, colour name key]}, not a translated string: else a non-English client on a dedicated server
     * would read the English name.
     */
    private static Object[] channelArgs(int channel) {
        if (!Dyes.isDyeIndex(channel)) {
            return new Object[] { EnumChatFormatting.GRAY.toString(),
                IGregTechDeviceInformation.translatable("machine.wirelessbeamline.channel.none") };
        }
        Dyes dye = Dyes.VALUES[channel];
        String key = COLOUR_LANG_KEY + dye.mName;
        // older/newer GT5U builds may not carry the spray can colour names: fall back to the enum's name
        if (StatCollector.translateToLocal(key)
            .equals(key)) {
            return new Object[] { dye.formatting.toString(), dye.mName };
        }
        return new Object[] { dye.formatting.toString(), IGregTechDeviceInformation.translatable(key) };
    }

    /** Description of a wireless hatch: the parent's lines plus the wireless channel rules. */
    public static String[] decorateDescription(String[] base, String kind) {
        List<String> lines = new ArrayList<>();
        if (base != null) {
            for (String line : base) {
                if (line != null) lines.add(line);
            }
        }
        lines.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.wirelessbeamline.desc.0"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessbeamline.desc.1"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessbeamline.desc." + kind));
        return lines.toArray(new String[0]);
    }

    /** Scanner info of a wireless hatch: the parent's beam report plus the live channel state. */
    public static String[] appendLinkInfo(String[] base, MTEHatchBeamlineConnector self) {
        List<String> lines = new ArrayList<>();
        if (base != null) {
            for (String line : base) {
                if (line != null) lines.add(line);
            }
        }
        int channel = getChannel(self);
        Object[] channelArgs = channelArgs(channel);
        lines.add(
            IGregTechDeviceInformation.encode("machine.wirelessbeamline.info.channel", channelArgs[0], channelArgs[1]));
        // the registry only exists on the server, so the client must not report a "no counterpart" it cannot know
        IGregTechTileEntity baseTile = self.getBaseMetaTileEntity();
        if (baseTile == null || !baseTile.isServerSide()) return lines.toArray(new String[0]);

        // the parent already reports up to six lines and IGregTechDeviceInformation documents eight: exactly one more
        // line fits; getLinkState covers "no counterpart" and "too many same-colour hatches" itself
        LinkState state = getLinkState(self);
        // a bare translation key is valid here: IGregTechDeviceInformation's readers translate it client-side
        lines.add("machine.wirelessbeamline.state." + stateKey(state));
        return lines.toArray(new String[0]);
    }
}
