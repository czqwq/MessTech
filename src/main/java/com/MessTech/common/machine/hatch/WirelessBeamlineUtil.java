package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtil;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchBeamlineConnector;
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;

/**
 * Data-stick address pairing shared by the wireless beamline (particle) hatches, the same contract as
 * {@link WirelessVacuumConveyorUtil}.
 * <p>
 * GT pushes a {@link BeamLinePacket} from an output hatch through a line of beamline pipes into an input hatch; these
 * hatches replace that line with an explicit player-made pair. An output hatch stores the address of exactly one input
 * hatch and pushes its pending packet over on its move tick; the input hatch stores the output address back. Both
 * references are validated on every move tick, so a hatch can never end up feeding two partners at once - that is the
 * strict 1:1 rule of the wired pipe run it replaces. The transfer is push-only: the output is the single mover.
 * <p>
 * The address is copied the GT-Not-Leisure way, with a plain GT data stick: left-click the output, right-click the
 * input. Rebinding detaches the old partners first, and a GT wire cutter used while sneaking unbinds. Unlike the NAC
 * vacuum conveyor there is no colour rule: the dye does not take part in the link contract at all.
 * <p>
 * Addresses carry their dimension, so links work across dimensions while both chunks are loaded. Moves never force-load
 * chunks; only an explicit binding action loads the target chunk once, so a player can bind a far-away output from an
 * input on the other side of the map.
 */
public final class WirelessBeamlineUtil {

    /** NBT {@code type} tag of a data stick holding a wireless beamline output address. */
    public static final String DATA_STICK_TYPE = "messtechWirelessBeamlineOutput";

    /** Lang key of the data stick display name. */
    private static final String DATA_STICK_NAME_KEY = "machine.wirelessbeamline.datastick";

    private static final String TAG_TYPE = "type";
    private static final String TAG_DIMENSION = "dim";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    /** NBT of a hatch: the address of its single counterpart. */
    private static final String HATCH_LINK = "wirelessBeamlineLink";

    /**
     * Packets being handed over by {@link #moveBeam(MTEHatchOutputBeamline)} right now: the input accepts only these,
     * so a wired output hatch in front of it cannot bypass the link.
     */
    private static final Set<BeamLinePacket> WIRELESS_DELIVERIES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** NBT keys of the WAILA payload written by {@link #writeWailaData} and decoded by {@link #appendWailaBody}. */
    private static final String WAILA_STATE = "wirelessBeamlineState";
    private static final String WAILA_DIM = "wirelessBeamlineDim";
    private static final String WAILA_X = "wirelessBeamlineX";
    private static final String WAILA_Y = "wirelessBeamlineY";
    private static final String WAILA_Z = "wirelessBeamlineZ";

    private WirelessBeamlineUtil() {}

    /**
     * Implemented by every wireless beamline hatch: the address of its single counterpart, {@code null} when unpaired.
     */
    public interface LinkHolder {

        Target getLinkTarget();

        void setLinkTarget(Target target);
    }

    /** Address of a hatch: dimension id plus block coordinates. */
    public static final class Target {

        public final int dimension;
        public final int x;
        public final int y;
        public final int z;

        public Target(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Target other)) return false;
            return dimension == other.dimension && x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, x, y, z);
        }
    }

    /** Live pairing state of a hatch, for the scanner info and WAILA. */
    public enum LinkState {
        /** No counterpart address stored. */
        UNBOUND,
        /** Address resolved and both sides point at each other. */
        LINKED,
        /** The counterpart's dimension is not loaded. */
        DIM_UNLOADED,
        /** The counterpart's chunk is not loaded. */
        CHUNK_UNLOADED,
        /** The chunk is loaded, but there is no TileEntity at the address any more. */
        MISSING,
        /** A hatch of the other kind sits at the address (input vs output). */
        WRONG_SIDE,
        /** Both ends resolved, but the counterpart points somewhere else. */
        BROKEN
    }

    // ------------------------------------------------------------------ addresses

    /** Address of the given tile entity. */
    public static Target targetOf(IGregTechTileEntity base) {
        return new Target(base.getWorld().provider.dimensionId, base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    public static Target readTarget(NBTTagCompound hatchNbt) {
        if (hatchNbt == null || !hatchNbt.hasKey(HATCH_LINK)) return null;
        NBTTagCompound link = hatchNbt.getCompoundTag(HATCH_LINK);
        // a present-but-empty tag (corrupted or foreign NBT) is not an address: reading zeros would fake a dim-0 link
        if (!link.hasKey(TAG_DIMENSION) || !link.hasKey(TAG_X) || !link.hasKey(TAG_Y) || !link.hasKey(TAG_Z)) {
            return null;
        }
        return new Target(
            link.getInteger(TAG_DIMENSION),
            link.getInteger(TAG_X),
            link.getInteger(TAG_Y),
            link.getInteger(TAG_Z));
    }

    public static void writeTarget(NBTTagCompound hatchNbt, Target target) {
        if (target == null) {
            hatchNbt.removeTag(HATCH_LINK);
            return;
        }
        NBTTagCompound link = new NBTTagCompound();
        link.setInteger(TAG_DIMENSION, target.dimension);
        link.setInteger(TAG_X, target.x);
        link.setInteger(TAG_Y, target.y);
        link.setInteger(TAG_Z, target.z);
        hatchNbt.setTag(HATCH_LINK, link);
    }

    // ------------------------------------------------------------------ data stick

    /** True when the stack is a data stick written by a wireless beamline output hatch. */
    public static boolean isLinkDataStick(ItemStack stack) {
        return ItemList.Tool_DataStick.isStackEqual(stack, false, true) && stack.stackTagCompound != null
            && DATA_STICK_TYPE.equals(stack.stackTagCompound.getString(TAG_TYPE));
    }

    public static Target readDataStick(ItemStack stack) {
        if (!isLinkDataStick(stack)) return null;
        NBTTagCompound tag = stack.stackTagCompound;
        return new Target(
            tag.getInteger(TAG_DIMENSION),
            tag.getInteger(TAG_X),
            tag.getInteger(TAG_Y),
            tag.getInteger(TAG_Z));
    }

    public static void writeDataStick(ItemStack stack, Target target) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_TYPE, DATA_STICK_TYPE);
        tag.setInteger(TAG_DIMENSION, target.dimension);
        tag.setInteger(TAG_X, target.x);
        tag.setInteger(TAG_Y, target.y);
        tag.setInteger(TAG_Z, target.z);
        stack.stackTagCompound = tag;
        stack.setStackDisplayName(StatCollector.translateToLocal(DATA_STICK_NAME_KEY));
    }

    // ------------------------------------------------------------------ lookup

    /** The loaded world of that dimension, or {@code null} while the dimension is not loaded. */
    public static World worldOf(int dimension) {
        return DimensionManager.getWorld(dimension);
    }

    /** Resolves the hatch at the address without ever force-loading a chunk. */
    public static TileEntity resolve(Target target) {
        if (target == null) return null;
        World world = worldOf(target.dimension);
        if (world == null) return null;
        return GTUtil.getTileEntity(world, target.x, target.y, target.z, false);
    }

    /**
     * Resolves the hatch for an explicit player binding: the target chunk is loaded once if needed, so a player can
     * pair hatches that are far away from each other.
     */
    public static TileEntity resolveForBinding(Target target) {
        if (target == null) return null;
        World world = worldOf(target.dimension);
        if (world == null) return null;
        return GTUtil.getTileEntity(world, target.x, target.y, target.z, true);
    }

    /** Why the address does not resolve to a live tile entity, or {@code null} when a tile entity is there. */
    public static LinkState resolveFailure(Target target) {
        if (target == null) return LinkState.UNBOUND;
        World world = worldOf(target.dimension);
        if (world == null) return LinkState.DIM_UNLOADED;
        if (GTUtil.getTileEntity(world, target.x, target.y, target.z, false) != null) return null;
        return world.blockExists(target.x, target.y, target.z) ? LinkState.MISSING : LinkState.CHUNK_UNLOADED;
    }

    public static MTWirelessBeamlineInput asInput(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity base) || base.isDead()) return null;
        return base.getMetaTileEntity() instanceof MTWirelessBeamlineInput input ? input : null;
    }

    /** Either wireless output hatch (plain or filtered), or {@code null} when the tile is something else. */
    public static MTEHatchOutputBeamline asOutput(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity base) || base.isDead()) return null;
        return base.getMetaTileEntity() instanceof MTWirelessBeamlineOutput
            || base.getMetaTileEntity() instanceof MTWirelessBeamlineAdvancedOutput
                ? (MTEHatchOutputBeamline) base.getMetaTileEntity()
                : null;
    }

    // ------------------------------------------------------------------ permission

    /** Only the owner (or an operator) may re-point a hatch that is not theirs. */
    public static boolean canConfigure(EntityPlayer player, IGregTechTileEntity base) {
        if (player == null || base == null) return false;
        UUID owner = base.getOwnerUuid();
        if (owner == null || owner.equals(player.getUniqueID())) return true;
        return player instanceof EntityPlayerMP playerMP && playerMP.canCommandSenderUseCommand(2, "messtech");
    }

    // ------------------------------------------------------------------ link evaluation

    public static LinkState evaluateOutput(MTEHatchBeamlineConnector output, Target target) {
        if (target == null) return LinkState.UNBOUND;
        LinkState failure = resolveFailure(target);
        if (failure != null) return failure;
        MTWirelessBeamlineInput input = asInput(resolve(target));
        if (input == null) return LinkState.WRONG_SIDE;
        Target selfAddress = targetOf(output.getBaseMetaTileEntity());
        if (!selfAddress.equals(input.getLinkTarget())) return LinkState.BROKEN;
        return LinkState.LINKED;
    }

    public static LinkState evaluateInput(MTWirelessBeamlineInput input, Target target) {
        if (target == null) return LinkState.UNBOUND;
        LinkState failure = resolveFailure(target);
        if (failure != null) return failure;
        MTEHatchOutputBeamline output = asOutput(resolve(target));
        if (output == null) return LinkState.WRONG_SIDE;
        Target selfAddress = targetOf(input.getBaseMetaTileEntity());
        if (!selfAddress.equals(linkTargetOf(output))) return LinkState.BROKEN;
        return LinkState.LINKED;
    }

    /** True when a one-sided stale address should be dropped instead of waiting for the counterpart (data gone). */
    public static boolean isDeadLink(LinkState state) {
        return state == LinkState.MISSING || state == LinkState.WRONG_SIDE || state == LinkState.BROKEN;
    }

    // ------------------------------------------------------------------ transfer

    /**
     * Hands the output's beam to its paired input, then clears the hatch; called from the hatch's {@code moveAround}
     * tick, i.e. at the wired pipe's cadence - one offer per move tick, never queued. Dropped unless the pair is clean
     * and both sides still point at each other; a merely unloaded counterpart reports itself in WAILA and the packet
     * belongs to the tick it was published in, exactly like the wired hatch with no pipe reaching an input.
     */
    public static void moveBeam(MTEHatchOutputBeamline output) {
        BeamLinePacket packet = output.dataPacket;
        output.dataPacket = null;
        if (packet == null) return;

        Target target = linkTargetOf(output);
        if (target == null) return;
        if (evaluateOutput(output, target) != LinkState.LINKED) return;
        MTWirelessBeamlineInput input = asInput(resolve(target));
        if (input == null) return;

        WIRELESS_DELIVERIES.add(packet);
        try {
            input.setContents(packet);
        } finally {
            WIRELESS_DELIVERIES.remove(packet);
        }
    }

    /** True while {@link #moveBeam(MTEHatchOutputBeamline)} hands that exact packet to an input hatch. */
    public static boolean isWirelessDelivery(BeamLinePacket packet) {
        return packet != null && WIRELESS_DELIVERIES.contains(packet);
    }

    // ------------------------------------------------------------------ live state / WAILA

    /**
     * Live pairing state of a wireless hatch, dispatching on its direction. Returns {@link LinkState#UNBOUND} on the
     * client: the stored address only lives in server-side NBT and resolving the counterpart needs the loaded worlds,
     * so the client must not try to compute the state itself.
     */
    public static LinkState linkState(MTEHatchBeamlineConnector hatch) {
        IGregTechTileEntity base = hatch == null ? null : hatch.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return LinkState.UNBOUND;
        Target target = linkTargetOf(hatch);
        if (hatch instanceof MTEHatchOutputBeamline) return evaluateOutput(hatch, target);
        if (hatch instanceof MTWirelessBeamlineInput input) return evaluateInput(input, target);
        return LinkState.UNBOUND;
    }

    /** Address of the single counterpart of a wireless hatch, or {@code null} when it is not paired yet. */
    public static Target linkTargetOf(MTEHatchBeamlineConnector hatch) {
        return hatch instanceof LinkHolder holder ? holder.getLinkTarget() : null;
    }

    private static void setLinkTarget(MTEHatchBeamlineConnector hatch, Target target) {
        if (hatch instanceof LinkHolder holder) holder.setLinkTarget(target);
    }

    /** Decodes a {@link LinkState} ordinal (WAILA payload), defaulting to unbound. */
    public static LinkState decodeState(int ordinal) {
        LinkState[] states = LinkState.values();
        return ordinal >= 0 && ordinal < states.length ? states[ordinal] : LinkState.UNBOUND;
    }

    /**
     * Colour of a link state wherever it is shown: green for a clean link, red for no link, yellow for a broken one.
     */
    public static EnumChatFormatting stateColor(LinkState state) {
        if (state == LinkState.LINKED) return EnumChatFormatting.GREEN;
        if (state == LinkState.UNBOUND) return EnumChatFormatting.RED;
        return EnumChatFormatting.YELLOW;
    }

    /**
     * WAILA server-provider side: writes the live link state and, when a counterpart is stored, its address into the
     * WAILA NBT tag. Resolving the peer needs the loaded worlds, so the state is computed here, server-side; the client
     * only decodes and translates it in {@link #appendWailaBody}.
     */
    public static void writeWailaData(NBTTagCompound tag, MTEHatchBeamlineConnector hatch) {
        if (tag == null) return;
        tag.setInteger(WAILA_STATE, linkState(hatch).ordinal());
        Target target = linkTargetOf(hatch);
        if (target == null) return;
        tag.setInteger(WAILA_DIM, target.dimension);
        tag.setInteger(WAILA_X, target.x);
        tag.setInteger(WAILA_Y, target.y);
        tag.setInteger(WAILA_Z, target.z);
    }

    /**
     * WAILA client side: appends the AE2 wireless-connector style link status line for a wireless hatch. Does nothing
     * when the tag was not written by {@link #writeWailaData}.
     */
    public static void appendWailaBody(List<String> currentTip, NBTTagCompound tag) {
        if (currentTip == null || tag == null || !tag.hasKey(WAILA_STATE)) return;
        LinkState state = decodeState(tag.getInteger(WAILA_STATE));
        String line;
        switch (state) {
            case LINKED:
                line = StatCollector.translateToLocalFormatted(
                    "machine.wirelessbeamline.waila.linked",
                    tag.getInteger(WAILA_DIM),
                    tag.getInteger(WAILA_X),
                    tag.getInteger(WAILA_Y),
                    tag.getInteger(WAILA_Z));
                break;
            case UNBOUND:
                line = StatCollector.translateToLocal("machine.wirelessbeamline.waila.unbound");
                break;
            case DIM_UNLOADED:
            case CHUNK_UNLOADED:
                line = StatCollector.translateToLocal("machine.wirelessbeamline.waila.unloaded");
                break;
            default:
                line = StatCollector.translateToLocal("machine.wirelessbeamline.waila.invalid");
                break;
        }
        currentTip.add(stateColor(state) + line);
    }

    // ------------------------------------------------------------------ player actions

    /** Left-click on a wireless output with a data stick: store that hatch's address on the stick. */
    public static void saveAddressToStick(MTEHatchBeamlineConnector hatch, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        ItemStack stick = player.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(stick, false, true)) return;

        writeDataStick(stick, targetOf(base));
        sendChat(player, "machine.wirelessbeamline.chat.saved");
    }

    /**
     * Right-click on the input with a data stick that holds a wireless output address: link both hatches to each
     * other, detaching their previous partners first. Returns false when the held stack is not such a stick, so the
     * hatch can fall back to its normal right-click behaviour.
     */
    public static boolean tryLinkInput(MTWirelessBeamlineInput input, IGregTechTileEntity base, EntityPlayer player) {
        ItemStack stick = player.inventory.getCurrentItem();
        if (!isLinkDataStick(stick)) return false;

        Target stickAddress = readDataStick(stick);
        // binding is an explicit player action: loading the target chunk once is fine and lets a far-away output be
        // paired from this input, unlike the move tick which never loads chunks
        TileEntity tile = resolveForBinding(stickAddress);
        if (tile == null) {
            sendChat(player, "machine.wirelessbeamline.chat.fail.target");
            return true;
        }
        MTEHatchOutputBeamline output = asOutput(tile);
        if (output == null) {
            sendChat(player, "machine.wirelessbeamline.chat.fail.role");
            return true;
        }

        IGregTechTileEntity outputBase = output.getBaseMetaTileEntity();
        if (outputBase == null || !canConfigure(player, outputBase)) {
            sendChat(player, "machine.wirelessbeamline.chat.fail.permission");
            return true;
        }

        Target selfAddress = targetOf(base);
        Target outputAddress = targetOf(outputBase);
        if (outputAddress.equals(input.getLinkTarget()) && selfAddress.equals(linkTargetOf(output))) {
            sendChat(player, "machine.wirelessbeamline.chat.already");
            return true;
        }

        detachOldOutput(player, input, selfAddress);
        detachOldInput(player, selfAddress, outputAddress, linkTargetOf(output));

        setLinkTarget(output, selfAddress);
        input.setLinkTarget(outputAddress);
        sendChat(player, "machine.wirelessbeamline.chat.linked");
        return true;
    }

    /** Releases this input's previous output. */
    private static void detachOldOutput(EntityPlayer player, MTWirelessBeamlineInput input, Target selfAddress) {
        MTEHatchOutputBeamline oldOutput = asOutput(resolve(input.getLinkTarget()));
        if (oldOutput != null && canConfigure(player, oldOutput.getBaseMetaTileEntity())
            && selfAddress.equals(linkTargetOf(oldOutput))) {
            setLinkTarget(oldOutput, null);
        }
    }

    /**
     * Releases the target output's previous input; an unloaded or foreign hatch keeps a stale address and self-heals.
     */
    private static void detachOldInput(EntityPlayer player, Target selfAddress, Target outputAddress,
        Target targetInput) {
        if (targetInput == null || targetInput.equals(selfAddress)) return;
        MTWirelessBeamlineInput oldInput = asInput(resolve(targetInput));
        if (oldInput != null && canConfigure(player, oldInput.getBaseMetaTileEntity())
            && outputAddress.equals(oldInput.getLinkTarget())) {
            oldInput.setLinkTarget(null);
        }
    }

    /** Sneak + wire cutter right-click on either hatch: drop the link, clearing the counterpart when allowed. */
    public static boolean unbind(MTEHatchBeamlineConnector self, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return false;
        IGregTechTileEntity base = self.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || !player.isSneaking()) return false;
        Target target = linkTargetOf(self);
        if (target == null) return false;

        if (self instanceof MTEHatchOutputBeamline) {
            MTWirelessBeamlineInput input = asInput(resolve(target));
            if (input != null && canConfigure(player, input.getBaseMetaTileEntity())
                && targetOf(base).equals(input.getLinkTarget())) {
                input.setLinkTarget(null);
            }
        } else {
            MTEHatchOutputBeamline output = asOutput(resolve(target));
            if (output != null && canConfigure(player, output.getBaseMetaTileEntity())
                && targetOf(base).equals(linkTargetOf(output))) {
                setLinkTarget(output, null);
            }
        }
        setLinkTarget(self, null);
        sendChat(player, "machine.wirelessbeamline.chat.unlinked");
        return true;
    }

    // ------------------------------------------------------------------ text output

    public static void sendChat(EntityPlayer player, String langKey, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(langKey, args));
    }

    /** Description of a wireless hatch: the parent's lines plus the data-stick pairing rules. */
    public static String[] decorateDescription(String[] base, String kind) {
        List<String> lines = new ArrayList<>();
        if (base != null) {
            for (String line : base) {
                if (line != null) lines.add(line);
            }
        }
        lines.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.wirelessbeamline.desc.0"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessbeamline.desc.1"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessbeamline.desc.2"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessbeamline.desc.3"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessbeamline.desc." + kind));
        return lines.toArray(new String[0]);
    }

    /**
     * Scanner info of a wireless hatch: one live link-state line, plus the stored address when the link is not clean.
     * The state is built server-side but decoded client-side, so numbers travel as {@code encode} arguments and the
     * keys stay untranslated. The parent already reports up to six lines and {@link IGregTechDeviceInformation}
     * documents eight, so the two lines added here still fit.
     */
    public static String[] appendLinkInfo(String[] base, MTEHatchBeamlineConnector self) {
        List<String> lines = new ArrayList<>();
        if (base != null) {
            for (String line : base) {
                if (line != null) lines.add(line);
            }
        }
        IGregTechTileEntity baseTile = self.getBaseMetaTileEntity();
        // the link addresses live in server-side NBT only; the client must not report a state it cannot know
        if (baseTile == null || !baseTile.isServerSide()) return lines.toArray(new String[0]);

        boolean selfIsOutput = self instanceof MTEHatchOutputBeamline;
        Target target = linkTargetOf(self);
        LinkState state = selfIsOutput ? evaluateOutput(self, target)
            : evaluateInput((MTWirelessBeamlineInput) self, target);
        if (state == LinkState.UNBOUND) {
            lines.add("machine.wirelessbeamline.info.unbound");
            return lines.toArray(new String[0]);
        }
        if (state == LinkState.LINKED) {
            lines.add(
                IGregTechDeviceInformation.encode(
                    selfIsOutput ? "machine.wirelessbeamline.info.linked.output"
                        : "machine.wirelessbeamline.info.linked.input",
                    target.dimension,
                    target.x,
                    target.y,
                    target.z));
            return lines.toArray(new String[0]);
        }
        switch (state) {
            case DIM_UNLOADED:
                lines.add("machine.wirelessbeamline.info.dim_unloaded");
                break;
            case CHUNK_UNLOADED:
                lines.add("machine.wirelessbeamline.info.chunk_unloaded");
                break;
            case MISSING:
                lines.add("machine.wirelessbeamline.info.missing");
                break;
            case WRONG_SIDE:
                lines.add("machine.wirelessbeamline.info.wrong_side");
                break;
            default:
                lines.add("machine.wirelessbeamline.info.broken");
                break;
        }
        lines.add(
            IGregTechDeviceInformation
                .encode("machine.wirelessbeamline.info.target", target.dimension, target.x, target.y, target.z));
        return lines.toArray(new String[0]);
    }
}
