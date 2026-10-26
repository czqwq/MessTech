package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

import gregtech.api.enums.Dyes;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtil;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyor;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponentPacket;

/**
 * Data-stick address pairing shared by the wireless NAC vacuum conveyor hatches.
 * <p>
 * An output hatch stores the address of exactly one input hatch and pushes its pending packet over on
 * {@code VACUUM_MOVE_TICK}; the input hatch stores the output address back. Both references are validated on every move
 * tick, so a hatch can never end up feeding two partners at once - that is the strict 1:1 rule of the wired, coloured
 * pipe run it replaces. The transfer is push-only: the output is the single mover, which rules out the old
 * pull-plus-push double path that let several outputs merge into one input.
 * <p>
 * The address is copied the GT-Not-Leisure way, with a plain GT data stick: left-click the output, right-click the
 * input. Rebinding detaches the old partners first, and a GT wire cutter used while sneaking unbinds. Both hatches must
 * be painted with the same dye: the colour still decides which NAC line a hatch belongs to, so it is part of the link
 * contract instead of the link key.
 * <p>
 * Addresses carry their dimension, so links work across dimensions while both chunks are loaded. Moves never force-load
 * chunks; only an explicit binding action loads the target chunk once, so a player can bind a far-away output from an
 * input on the other side of the map.
 */
public final class WirelessVacuumConveyorUtil {

    /** NBT {@code type} tag of a data stick holding a wireless vacuum conveyor address. */
    public static final String DATA_STICK_TYPE = "messtechWirelessVacuumConveyorOutput";

    /** Lang key of the data stick display name. */
    private static final String DATA_STICK_NAME_KEY = "machine.wirelessvacuum.datastick";

    private static final String TAG_TYPE = "type";
    private static final String TAG_DIMENSION = "dim";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    /** NBT of a hatch: the address of its single counterpart. */
    private static final String HATCH_LINK = "wirelessVacuumLink";

    private WirelessVacuumConveyorUtil() {}

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

    /** Live pairing state of a hatch, for the scanner info. */
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
        /** Both ends resolved, but they are not painted in the same dye. */
        COLOR_MISMATCH,
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

    /** True when the stack is a data stick written by a wireless vacuum conveyor output hatch. */
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

    public static MTWirelessVacuumConveyorInput asInput(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity base) || base.isDead()) return null;
        return base.getMetaTileEntity() instanceof MTWirelessVacuumConveyorInput input ? input : null;
    }

    public static MTWirelessVacuumConveyorOutput asOutput(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity base) || base.isDead()) return null;
        return base.getMetaTileEntity() instanceof MTWirelessVacuumConveyorOutput output ? output : null;
    }

    // ------------------------------------------------------------------ colour / permission

    /** Dye index of a hatch, or {@code -1} when it is not painted (unpainted hatches never link). */
    public static int channelColour(MTEHatchVacuumConveyor hatch) {
        if (hatch == null) return -1;
        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
        if (base == null) return -1;
        int colour = base.getColorization();
        return Dyes.isDyeIndex(colour) ? colour : -1;
    }

    /** Both ends painted and in the same colour - the same rule a coloured pipe run uses. */
    public static boolean sameColour(MTEHatchVacuumConveyor a, MTEHatchVacuumConveyor b) {
        int colour = channelColour(a);
        return colour >= 0 && colour == channelColour(b);
    }

    /** Only the owner (or an operator) may re-point a hatch that is not theirs. */
    public static boolean canConfigure(EntityPlayer player, IGregTechTileEntity base) {
        if (player == null || base == null) return false;
        UUID owner = base.getOwnerUuid();
        if (owner == null || owner.equals(player.getUniqueID())) return true;
        return player instanceof EntityPlayerMP playerMP && playerMP.canCommandSenderUseCommand(2, "messtech");
    }

    // ------------------------------------------------------------------ link evaluation

    public static LinkState evaluateOutput(MTWirelessVacuumConveyorOutput output, Target target) {
        if (target == null) return LinkState.UNBOUND;
        LinkState failure = resolveFailure(target);
        if (failure != null) return failure;
        MTWirelessVacuumConveyorInput input = asInput(resolve(target));
        if (input == null) return LinkState.WRONG_SIDE;
        if (!sameColour(output, input)) return LinkState.COLOR_MISMATCH;
        Target selfAddress = targetOf(output.getBaseMetaTileEntity());
        if (!selfAddress.equals(input.getLinkTarget())) return LinkState.BROKEN;
        return LinkState.LINKED;
    }

    public static LinkState evaluateInput(MTWirelessVacuumConveyorInput input, Target target) {
        if (target == null) return LinkState.UNBOUND;
        LinkState failure = resolveFailure(target);
        if (failure != null) return failure;
        MTWirelessVacuumConveyorOutput output = asOutput(resolve(target));
        if (output == null) return LinkState.WRONG_SIDE;
        if (!sameColour(input, output)) return LinkState.COLOR_MISMATCH;
        Target selfAddress = targetOf(input.getBaseMetaTileEntity());
        if (!selfAddress.equals(output.getLinkTarget())) return LinkState.BROKEN;
        return LinkState.LINKED;
    }

    /** True when a one-sided stale address should be dropped instead of waiting for the counterpart (data gone). */
    public static boolean isDeadLink(LinkState state) {
        return state == LinkState.MISSING || state == LinkState.WRONG_SIDE || state == LinkState.BROKEN;
    }

    /**
     * True when the packet holds no components. GT5U's own {@code isEmpty()} only checks the map, but
     * {@code CircuitComponentPacket#consume} leaves drained per-component lists behind, so a fully consumed hatch would
     * otherwise stay "active" forever.
     */
    public static boolean hasNoComponents(CircuitComponentPacket packet) {
        if (packet == null) return true;
        for (List<?> amounts : packet.getComponents()
            .values()) {
            if (!amounts.isEmpty()) return false;
        }
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
        lines.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.wirelessvacuum.desc.0"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessvacuum.desc.1"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessvacuum.desc.2"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessvacuum.desc.3"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.wirelessvacuum.desc." + kind));
        return lines.toArray(new String[0]);
    }

    /**
     * Scanner info of a wireless hatch: one live link-state line, plus the stored address when the link is not clean.
     * The state is built server-side but decoded client-side, so numbers travel as {@code encode} arguments and the
     * keys stay untranslated.
     */
    public static void appendLinkInfo(List<String> info, MTEHatchVacuumConveyor self, Target target,
        boolean selfIsOutput) {
        IGregTechTileEntity base = self.getBaseMetaTileEntity();
        // the link addresses live in server-side NBT only; the client must not report a state it cannot know
        if (base == null || !base.isServerSide()) return;

        LinkState state = selfIsOutput ? evaluateOutput((MTWirelessVacuumConveyorOutput) self, target)
            : evaluateInput((MTWirelessVacuumConveyorInput) self, target);
        if (state == LinkState.UNBOUND) {
            info.add("machine.wirelessvacuum.info.unbound");
            return;
        }
        if (state == LinkState.LINKED) {
            info.add(
                IGregTechDeviceInformation.encode(
                    selfIsOutput ? "machine.wirelessvacuum.info.linked.output"
                        : "machine.wirelessvacuum.info.linked.input",
                    target.dimension,
                    target.x,
                    target.y,
                    target.z));
            return;
        }
        switch (state) {
            case DIM_UNLOADED:
                info.add("machine.wirelessvacuum.info.dim_unloaded");
                break;
            case CHUNK_UNLOADED:
                info.add("machine.wirelessvacuum.info.chunk_unloaded");
                break;
            case MISSING:
                info.add("machine.wirelessvacuum.info.missing");
                break;
            case WRONG_SIDE:
                info.add("machine.wirelessvacuum.info.wrong_side");
                break;
            case COLOR_MISMATCH:
                info.add("machine.wirelessvacuum.info.color_mismatch");
                break;
            default:
                info.add("machine.wirelessvacuum.info.broken");
                break;
        }
        info.add(
            IGregTechDeviceInformation
                .encode("machine.wirelessvacuum.info.target", target.dimension, target.x, target.y, target.z));
    }
}
