package com.MessTech.common.machine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Save-local list of the Comprehensive Mining Plants that are currently loaded, the data source of
 * {@code /messtech infiore}: "which dimension, which coordinates" for every machine of this type.
 * <p>
 * Only raw numbers are stored - the command sends a {@code ChatComponentTranslation}, so the line is formatted in the
 * player's language and never on the server. The map is static because the command has no other way to reach the
 * machines, which also means it has to be cleared when the server stops: one client JVM can visit several worlds, and
 * entries of the previous world must not leak into the next one.
 */
public final class MTMiningPlantRegistry {

    /** One registered machine: dimension id plus block coordinates. */
    public static final class Entry {

        private final int dimension;
        private final int x;
        private final int y;
        private final int z;

        private Entry(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getDimension() {
            return dimension;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        private String key() {
            return dimension + ":" + x + ":" + y + ":" + z;
        }
    }

    private static final Map<String, Entry> PLANTS = new LinkedHashMap<>();

    private MTMiningPlantRegistry() {}

    /** Registers (or refreshes) a machine, called from its first tick. */
    public static void register(MTComprehensiveMiningPlant machine) {
        Entry entry = entryOf(machine);
        if (entry != null) PLANTS.put(entry.key(), entry);
    }

    /** Drops a machine again, called from its removal. */
    public static void unregister(MTComprehensiveMiningPlant machine) {
        Entry entry = entryOf(machine);
        if (entry != null) PLANTS.remove(entry.key());
    }

    /** @return the registered machines, in registration order. */
    public static List<Entry> snapshot() {
        return new ArrayList<>(PLANTS.values());
    }

    /** Called on server stop: entries belong to one world and must not survive into the next one. */
    public static void clear() {
        PLANTS.clear();
    }

    private static Entry entryOf(MTComprehensiveMiningPlant machine) {
        if (machine == null) return null;
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null) return null;
        World world = base.getWorld();
        if (world == null) return null;
        return new Entry(world.provider.dimensionId, base.getXCoord(), base.getYCoord(), base.getZCoord());
    }
}
