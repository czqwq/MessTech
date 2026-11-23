package com.MessTech.common.wireless;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

/**
 * World-scoped store of the wireless ME networks: one persistent record per network frequency (display name, owner,
 * color, access level, the position of the one core that may serve it, and the bound ME hatches) plus a derived member
 * index that guarantees a hatch position belongs to at most one network.
 *
 * <p>
 * Since v2 the networks are world-level objects of their own: removing the core only clears the record's {@code core}
 * slot (see {@link #clearCore(UUID)}), it does not delete the record, its name/color/access or its member bindings. A
 * record is only removed by {@link #deleteNetwork(UUID)}, i.e. by its owner.
 *
 * <p>
 * The data lives in the overworld {@link MapStorage} so it is shared by all dimensions; every mutation calls
 * {@link #markDirty()} so vanilla saves it with the world. Duplicate memberships (possible in hand-edited or legacy
 * data) are repaired deterministically: networks are visited in frequency UUID order and the first claim wins, which is
 * the same strategy GTLCore's {@code rebuildMemberIndex} uses.
 */
public class MTWirelessSavedData extends WorldSavedData {

    public static final String DATA_NAME = "messtech_wireless_me_networks";

    /** Current NBT schema version. v1 (before the persistent network model) wrote no {@code version} tag at all. */
    public static final int SCHEMA_VERSION = 2;

    /** Upper bound of {@link #setName(UUID, String)}/{@link #createNetwork}, in characters. */
    public static final int MAX_NAME_LENGTH = 32;

    private static final String TAG_VERSION = "version";
    private static final String TAG_NETWORKS = "networks";
    private static final String TAG_FREQUENCY = "freq";
    private static final String TAG_CORE = "core";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_NAME = "name";
    private static final String TAG_COLOR = "color";
    private static final String TAG_ACCESS = "access";
    private static final String TAG_MEMBERS = "members";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_UUID_MOST = "m";
    private static final String TAG_UUID_LEAST = "l";
    private static final String TAG_UUID_STRING = "s";

    private static final int SHORT_FREQUENCY_LENGTH = 8;

    private static final Comparator<MTWirelessMemberKey> MEMBER_ORDER = Comparator
        .comparingInt((MTWirelessMemberKey key) -> key.dim)
        .thenComparingInt(key -> key.x)
        .thenComparingInt(key -> key.y)
        .thenComparingInt(key -> key.z);

    private static final Comparator<NetworkInfo> NETWORK_ORDER = Comparator
        .comparing((NetworkInfo info) -> info.name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(info -> info.frequency);

    private final Map<UUID, Network> networks = new LinkedHashMap<>();
    private final Map<MTWirelessMemberKey, UUID> memberIndex = new HashMap<>();

    public MTWirelessSavedData(String name) {
        super(name);
    }

    /**
     * @return the shared store, or {@code null} when there is no overworld yet (very early startup / shutdown).
     */
    public static MTWirelessSavedData get(MinecraftServer server) {
        if (server == null) return null;
        World overworld = server.worldServerForDimension(0);
        if (overworld == null) return null;
        MapStorage storage = overworld.mapStorage;
        if (storage == null) return null;

        WorldSavedData loaded = storage.loadData(MTWirelessSavedData.class, DATA_NAME);
        if (loaded instanceof MTWirelessSavedData data) return data;

        MTWirelessSavedData data = new MTWirelessSavedData(DATA_NAME);
        storage.setData(DATA_NAME, data);
        return data;
    }

    /** Immutable view of one network for GUIs: the display name already falls back to the short frequency. */
    public static final class NetworkInfo {

        public final UUID frequency;
        /** Display name: the stored name, or the first eight characters of the frequency when it is empty. */
        public final String name;
        public final UUID owner;
        /** {@code 0xRRGGBB}; {@link MTWirelessColors#DEFAULT} for a legacy record without a color. */
        public final int color;
        public final MTWirelessAccess access;
        public final boolean hasCore;
        public final int memberCount;

        public NetworkInfo(UUID frequency, String name, UUID owner, int color, MTWirelessAccess access, boolean hasCore,
            int memberCount) {
            this.frequency = frequency;
            this.name = name;
            this.owner = owner;
            this.color = MTWirelessColors.sanitize(color);
            this.access = access == null ? MTWirelessAccess.PRIVATE : access;
            this.hasCore = hasCore;
            this.memberCount = memberCount;
        }

        /**
         * Legacy constructor kept for source compatibility with the v1 callers; it produces the v1 defaults
         * ({@link MTWirelessColors#DEFAULT} color, {@link MTWirelessAccess#TEAM} access).
         */
        public NetworkInfo(UUID frequency, String name, UUID owner, boolean hasCore, int memberCount) {
            this(frequency, name, owner, MTWirelessColors.DEFAULT, MTWirelessAccess.TEAM, hasCore, memberCount);
        }

        public UUID frequency() {
            return this.frequency;
        }

        public String name() {
            return this.name;
        }

        public UUID owner() {
            return this.owner;
        }

        public int color() {
            return this.color;
        }

        public MTWirelessAccess access() {
            return this.access;
        }

        public boolean hasCore() {
            return this.hasCore;
        }

        public int memberCount() {
            return this.memberCount;
        }
    }

    // region Core

    public void setCore(UUID frequency, int dim, int x, int y, int z) {
        if (frequency == null) return;
        Network network = network(frequency);
        if (network.core == null || network.core[0] != dim
            || network.core[1] != x
            || network.core[2] != y
            || network.core[3] != z) {
            network.core = new int[] { dim, x, y, z };
            markDirty();
        }
    }

    /**
     * Clears the core slot of the network without touching anything else: the record, its name/color/access level and
     * every member binding stay. Called when the core is broken or drifts to another network.
     */
    public void clearCore(UUID frequency) {
        if (frequency == null) return;
        Network network = this.networks.get(frequency);
        if (network == null || network.core == null) return;
        network.core = null;
        markDirty();
    }

    public boolean hasCore(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network != null && network.core != null;
    }

    /** @return {@code {dim, x, y, z}} of the core, or {@code null} when the network has no known core. */
    public int[] getCore(UUID frequency) {
        Network network = this.networks.get(frequency);
        if (network == null || network.core == null) return null;
        return network.core.clone();
    }

    /**
     * Reverse lookup of the core position: the network whose record claims that core block. Used to re-claim a network
     * when the core tile entity NBT lost its id (a broken tile entity tag, a world-editor paste, ...).
     *
     * @return the network frequency, or {@code null} when no record claims that position
     */
    public UUID findCoreNetwork(int dim, int x, int y, int z) {
        for (UUID frequency : getFrequencies()) {
            Network network = this.networks.get(frequency);
            if (network == null || network.core == null) continue;
            if (network.core[0] == dim && network.core[1] == x && network.core[2] == y && network.core[3] == z) {
                return frequency;
            }
        }
        return null;
    }

    public List<UUID> getFrequencies() {
        List<UUID> frequencies = new ArrayList<>(this.networks.keySet());
        frequencies.sort(Comparator.naturalOrder());
        return frequencies;
    }

    /** @return all networks sorted by display name and then frequency, i.e. stable for GUIs. */
    public List<NetworkInfo> getNetworks() {
        List<NetworkInfo> result = new ArrayList<>(this.networks.size());
        for (Network network : this.networks.values()) {
            result.add(
                new NetworkInfo(
                    network.frequency,
                    displayName(network),
                    network.owner,
                    network.color,
                    network.access,
                    network.core != null,
                    network.members.size()));
        }
        result.sort(NETWORK_ORDER);
        return result;
    }

    /** @return the networks created by {@code owner}, same stable order as {@link #getNetworks()}. */
    public List<NetworkInfo> getNetworksOwnedBy(UUID owner) {
        List<NetworkInfo> result = new ArrayList<>();
        if (owner == null) return result;
        for (NetworkInfo info : getNetworks()) {
            if (owner.equals(info.owner)) result.add(info);
        }
        return result;
    }

    /** @return how many networks {@code owner} created (the per-player creation limit is enforced by the runtime). */
    public int countOwnedNetworks(UUID owner) {
        if (owner == null) return 0;
        int count = 0;
        for (Network network : this.networks.values()) {
            if (owner.equals(network.owner)) count++;
        }
        return count;
    }

    public UUID getOwner(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null ? null : network.owner;
    }

    public void setOwner(UUID frequency, UUID owner) {
        if (frequency == null) return;
        Network network = network(frequency);
        if (!Objects.equals(network.owner, owner)) {
            network.owner = owner;
            markDirty();
        }
    }

    /** @return the display name (short frequency when unnamed), or {@code null} when the network is unknown. */
    public String getName(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null ? null : displayName(network);
    }

    /** @return the name as stored (possibly the empty string), or the empty string for an unknown network. */
    public String getRawName(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null || network.name == null ? "" : network.name;
    }

    /** Stores the name, trimmed and truncated to 32 characters; an empty name means "show the short frequency". */
    public void setName(UUID frequency, String name) {
        if (frequency == null) return;
        Network network = network(frequency);
        String sanitized = sanitizeName(name);
        if (!sanitized.equals(network.name)) {
            network.name = sanitized;
            markDirty();
        }
    }

    /** @return the color of the network, {@link MTWirelessColors#DEFAULT} when it is unknown. */
    public int getColor(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null ? MTWirelessColors.DEFAULT : network.color;
    }

    /** Stores {@code rgb} reduced to the low 24 bits ({@link MTWirelessColors#sanitize(int)}). */
    public void setColor(UUID frequency, int rgb) {
        if (frequency == null) return;
        Network network = network(frequency);
        int color = MTWirelessColors.sanitize(rgb);
        if (network.color != color) {
            network.color = color;
            markDirty();
        }
    }

    /** @return the access level of the network, {@link MTWirelessAccess#PRIVATE} when it is unknown. */
    public MTWirelessAccess getAccess(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null ? MTWirelessAccess.PRIVATE : network.access;
    }

    public void setAccess(UUID frequency, MTWirelessAccess access) {
        if (frequency == null || access == null) return;
        Network network = network(frequency);
        if (network.access != access) {
            network.access = access;
            markDirty();
        }
    }

    // endregion

    // region Members

    public long getMemberRevision(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null ? 0L : network.revision;
    }

    public int getMemberCount(UUID frequency) {
        Network network = this.networks.get(frequency);
        return network == null ? 0 : network.members.size();
    }

    /** @return the bound members of the network, sorted by dimension/position for stable GUI output. */
    public List<MTWirelessMemberKey> getMembers(UUID frequency) {
        Network network = this.networks.get(frequency);
        if (network == null || network.members.isEmpty()) return new ArrayList<>();
        List<MTWirelessMemberKey> members = new ArrayList<>(network.members);
        members.sort(MEMBER_ORDER);
        return members;
    }

    public UUID getMemberNetwork(MTWirelessMemberKey member) {
        if (member == null) return null;
        UUID indexed = this.memberIndex.get(member);
        if (indexed != null) {
            Network network = this.networks.get(indexed);
            if (network != null && network.members.contains(member)) return indexed;
            this.memberIndex.remove(member);
        }
        // Repair the index lazily instead of trusting a stale entry.
        for (Network network : this.networks.values()) {
            if (network.members.contains(member)) {
                this.memberIndex.put(member, network.frequency);
                return network.frequency;
            }
        }
        return null;
    }

    /** Adds the member to the network, removing it from any other network it was bound to before. */
    public void addMember(UUID frequency, MTWirelessMemberKey member) {
        if (frequency == null || member == null) return;

        UUID current = getMemberNetwork(member);
        boolean changed = false;
        if (current != null && !current.equals(frequency)) {
            Network previous = this.networks.get(current);
            if (previous != null && previous.members.remove(member)) {
                previous.revision++;
                changed = true;
            }
        }

        Network network = network(frequency);
        if (network.members.add(member)) {
            network.revision++;
            changed = true;
        }
        this.memberIndex.put(member, frequency);
        if (changed) markDirty();
    }

    public void removeMember(MTWirelessMemberKey member) {
        if (member == null) return;

        boolean changed = false;
        this.memberIndex.remove(member);
        for (Network network : this.networks.values()) {
            if (network.members.remove(member)) {
                network.revision++;
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    /** @return the networks the member was removed from (at most one with a well-formed index). */
    public List<UUID> removeMembersAt(int dim, int x, int y, int z) {
        MTWirelessMemberKey member = new MTWirelessMemberKey(dim, x, y, z);
        this.memberIndex.remove(member);

        List<UUID> removedFrom = new ArrayList<>();
        for (Network network : this.networks.values()) {
            if (network.members.remove(member)) {
                network.revision++;
                removedFrom.add(network.frequency);
            }
        }
        if (!removedFrom.isEmpty()) markDirty();
        return removedFrom;
    }

    // endregion

    // region Whole-network

    /**
     * Creates a persistent network record and returns its fresh frequency id. This is the low level API: the per-player
     * creation limit, the "name must not be empty" rule and the player permission are enforced by
     * {@link MTWirelessNetworkRuntime#createNetwork}. Only the name and the color are sanitized here.
     */
    public UUID createNetwork(UUID owner, String name, int color, MTWirelessAccess access) {
        UUID frequency = UUID.randomUUID();
        Network network = network(frequency);
        network.owner = owner;
        network.name = sanitizeName(name);
        network.color = MTWirelessColors.sanitize(color);
        network.access = access == null ? MTWirelessAccess.PRIVATE : access;
        markDirty();
        return frequency;
    }

    /** @return whether a record for that frequency exists (regardless of whether it currently has a core). */
    public boolean isNetwork(UUID frequency) {
        return frequency != null && this.networks.containsKey(frequency);
    }

    /**
     * Deletes the record and every member binding of the network. The runtime drops the network's AE2 connections and
     * unbinds its core separately ({@link MTWirelessNetworkRuntime#deleteNetwork}).
     *
     * @return whether the network existed
     */
    public boolean deleteNetwork(UUID frequency) {
        if (frequency == null) return false;

        Network removed = this.networks.remove(frequency);
        if (removed == null) return false;

        Iterator<Map.Entry<MTWirelessMemberKey, UUID>> iterator = this.memberIndex.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            if (frequency.equals(
                iterator.next()
                    .getValue()))
                iterator.remove();
        }
        markDirty();
        return true;
    }

    /** @return the previous owner (for disconnecting its members), or {@code null} when the network did not exist. */
    public UUID removeNetwork(UUID frequency) {
        if (frequency == null) return null;
        Network network = this.networks.get(frequency);
        if (network == null) return null;

        UUID owner = network.owner;
        deleteNetwork(frequency);
        return owner;
    }

    /** Drops every network and member. Only meant for a full reset, never for a normal server stop. */
    public void clearAll() {
        this.networks.clear();
        this.memberIndex.clear();
        markDirty();
    }

    // endregion

    // region Persistence

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.networks.clear();
        this.memberIndex.clear();

        // v1 saves wrote no version tag at all; v1 networks are migrated to the "owner + team" access they had.
        int version = tag.getInteger(TAG_VERSION);
        NBTTagList networkList = tag.getTagList(TAG_NETWORKS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < networkList.tagCount(); i++) {
            NBTTagCompound networkTag = networkList.getCompoundTagAt(i);
            UUID frequency = readUuid(networkTag, TAG_FREQUENCY);
            if (frequency == null) continue;

            Network network = network(frequency);

            NBTTagCompound coreTag = networkTag.getCompoundTag(TAG_CORE);
            if (coreTag.hasKey("d") && coreTag.hasKey("x") && coreTag.hasKey("y") && coreTag.hasKey("z")) {
                network.core = new int[] { coreTag.getInteger("d"), coreTag.getInteger("x"), coreTag.getInteger("y"),
                    coreTag.getInteger("z") };
            }

            UUID owner = readUuid(networkTag, TAG_OWNER);
            if (owner != null) network.owner = owner;
            network.name = sanitizeName(networkTag.getString(TAG_NAME));
            network.revision = networkTag.getLong(TAG_REVISION);

            network.color = networkTag.hasKey(TAG_COLOR) ? MTWirelessColors.sanitize(networkTag.getInteger(TAG_COLOR))
                : MTWirelessColors.DEFAULT;
            if (networkTag.hasKey(TAG_ACCESS)) {
                network.access = MTWirelessAccess.byId(networkTag.getByte(TAG_ACCESS));
            } else {
                // A v1 record had no access level; keep its old semantics (owner + ServerUtilities team).
                network.access = version >= SCHEMA_VERSION ? MTWirelessAccess.PRIVATE : MTWirelessAccess.TEAM;
            }

            NBTTagList memberList = networkTag.getTagList(TAG_MEMBERS, Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < memberList.tagCount(); j++) {
                MTWirelessMemberKey member = MTWirelessMemberKey.read(memberList.getCompoundTagAt(j));
                if (member != null) network.members.add(member);
            }
        }

        rebuildMemberIndex();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(TAG_VERSION, SCHEMA_VERSION);

        NBTTagList networkList = new NBTTagList();
        for (UUID frequency : getFrequencies()) {
            Network network = this.networks.get(frequency);
            if (network == null) continue;

            NBTTagCompound networkTag = new NBTTagCompound();
            writeUuid(networkTag, TAG_FREQUENCY, frequency);
            if (network.core != null) {
                NBTTagCompound coreTag = new NBTTagCompound();
                coreTag.setInteger("d", network.core[0]);
                coreTag.setInteger("x", network.core[1]);
                coreTag.setInteger("y", network.core[2]);
                coreTag.setInteger("z", network.core[3]);
                networkTag.setTag(TAG_CORE, coreTag);
            }
            if (network.owner != null) writeUuid(networkTag, TAG_OWNER, network.owner);
            networkTag.setString(TAG_NAME, network.name == null ? "" : network.name);
            networkTag.setInteger(TAG_COLOR, network.color);
            networkTag.setByte(TAG_ACCESS, (byte) network.access.id());
            networkTag.setLong(TAG_REVISION, network.revision);

            NBTTagList memberList = new NBTTagList();
            for (MTWirelessMemberKey member : network.members) {
                memberList.appendTag(member.write());
            }
            networkTag.setTag(TAG_MEMBERS, memberList);
            networkList.appendTag(networkTag);
        }
        tag.setTag(TAG_NETWORKS, networkList);
    }

    // endregion

    // region Helpers

    private Network network(UUID frequency) {
        return this.networks.computeIfAbsent(frequency, Network::new);
    }

    /**
     * Rebuilds the derived member index. Duplicates are resolved by visiting networks in frequency UUID order, so the
     * smallest UUID deterministically keeps the member.
     */
    private void rebuildMemberIndex() {
        this.memberIndex.clear();
        int conflicts = 0;
        for (UUID frequency : getFrequencies()) {
            Network network = this.networks.get(frequency);
            if (network == null) continue;
            Iterator<MTWirelessMemberKey> iterator = network.members.iterator();
            while (iterator.hasNext()) {
                MTWirelessMemberKey member = iterator.next();
                if (this.memberIndex.putIfAbsent(member, frequency) != null) {
                    iterator.remove();
                    conflicts++;
                }
            }
        }
        if (conflicts > 0) markDirty();
    }

    private static String sanitizeName(String name) {
        String sanitized = name == null ? "" : name.trim();
        if (sanitized.length() > MAX_NAME_LENGTH) sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        return sanitized;
    }

    private static String displayName(Network network) {
        String name = network.name == null ? "" : network.name.trim();
        if (!name.isEmpty()) return name;
        String frequency = network.frequency.toString();
        return frequency.length() <= SHORT_FREQUENCY_LENGTH ? frequency
            : frequency.substring(0, SHORT_FREQUENCY_LENGTH);
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        if (uuid == null) return;
        NBTTagCompound uuidTag = new NBTTagCompound();
        uuidTag.setLong(TAG_UUID_MOST, uuid.getMostSignificantBits());
        uuidTag.setLong(TAG_UUID_LEAST, uuid.getLeastSignificantBits());
        uuidTag.setString(TAG_UUID_STRING, uuid.toString());
        tag.setTag(key, uuidTag);
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        if (tag.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound uuidTag = tag.getCompoundTag(key);
            if (uuidTag.hasKey(TAG_UUID_MOST) && uuidTag.hasKey(TAG_UUID_LEAST)) {
                return new UUID(uuidTag.getLong(TAG_UUID_MOST), uuidTag.getLong(TAG_UUID_LEAST));
            }
            if (uuidTag.hasKey(TAG_UUID_STRING)) {
                try {
                    return UUID.fromString(uuidTag.getString(TAG_UUID_STRING));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }
        // Tolerate the flat "freqMost"/"freqLeast" spelling used by the core tile entity NBT.
        if (tag.hasKey(key + "Most") && tag.hasKey(key + "Least")) {
            return new UUID(tag.getLong(key + "Most"), tag.getLong(key + "Least"));
        }
        return null;
    }

    private static final class Network {

        private final UUID frequency;
        private int[] core;
        private UUID owner;
        private String name = "";
        private int color = MTWirelessColors.DEFAULT;
        private MTWirelessAccess access = MTWirelessAccess.PRIVATE;
        private long revision;
        private final Set<MTWirelessMemberKey> members = new LinkedHashSet<>();

        private Network(UUID frequency) {
            this.frequency = frequency;
        }
    }

    // endregion
}
