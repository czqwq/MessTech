package com.MessTech.common.command.kill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * A parsed entity selector, ported from 1.20.1's
 * {@code net.minecraft.commands.arguments.selector.EntitySelector} (the object {@code EntityArgument.entities()}
 * resolves to). {@link MTEntitySelectorParser} builds one from a token such as {@code @e[type=zombie,limit=3]}, and
 * {@link #findEntities(ICommandSender)} turns it into the entities the token names.
 * <p>
 * The 1.20.1 split is kept: the parser owns every raw option while it is being read, this class owns the finished
 * {@link Predicate} plus the position/box/order/limit machinery. A selector whose {@link #includesEntities} is
 * {@code false} only ever returns players ({@code @p}/{@code @a}/{@code @r}, or an option such as {@code gamemode=}
 * that can only apply to players); otherwise every loaded entity of every loaded dimension is a candidate unless
 * {@link #worldLimited} says the selector must stay in the sender's world.
 */
public class MTEntitySelector {

    public static final int INFINITE = Integer.MAX_VALUE;

    /** No particular order: whatever the entity list returns (1.20.1 {@code ORDER_ARBITRARY}). */
    public static final int ORDER_ARBITRARY = 0;
    public static final int ORDER_NEAREST = 1;
    public static final int ORDER_FURTHEST = 2;
    public static final int ORDER_RANDOM = 3;

    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final Predicate<Entity> predicate;
    private final DoubleRange distance;
    private final Function<Pos, Pos> position;
    /** Relative box from {@code dx}/{@code dy}/{@code dz} as {@code minX,minY,minZ,maxX,maxY,maxZ}, or {@code null}. */
    private final double[] box;
    private final int order;
    private final boolean currentEntity;
    private final String playerName;
    private final UUID entityUUID;

    MTEntitySelector(int maxResults, boolean includesEntities, boolean worldLimited, Predicate<Entity> predicate,
        DoubleRange distance, Function<Pos, Pos> position, double[] box, int order, boolean currentEntity,
        String playerName, UUID entityUUID) {
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
        this.worldLimited = worldLimited;
        this.predicate = predicate;
        this.distance = distance;
        this.position = position;
        this.box = box;
        this.order = order;
        this.currentEntity = currentEntity;
        this.playerName = playerName;
        this.entityUUID = entityUUID;
    }

    /**
     * The entities this selector names. An empty list is a normal result; the command turns it into the 1.20.1
     * "no entity was found" error, exactly like {@code EntityArgument#getEntities}.
     */
    public List<Entity> findEntities(ICommandSender sender) {
        final Pos source = Pos.of(sender);
        final Pos pos = this.position.apply(source);
        final Predicate<Entity> test = buildPredicate(pos);

        // A plain name / UUID selector never has options, so 1.20.1 looks the entity up before any filtering.
        if (this.playerName != null) {
            EntityPlayerMP player = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(this.playerName);
            return player == null ? Collections.emptyList() : Collections.<Entity>singletonList(player);
        }
        if (this.entityUUID != null) {
            return findByUUID();
        }
        if (!this.includesEntities) {
            return new ArrayList<Entity>(sortAndLimit(pos, findPlayers(sender, test)));
        }
        if (this.currentEntity) {
            Entity self = sender instanceof Entity ? (Entity) sender : null;
            return self != null && test.test(self) ? Collections.<Entity>singletonList(self)
                : Collections.<Entity>emptyList();
        }

        List<Entity> found = new ArrayList<>();
        if (this.worldLimited) {
            addEntities(found, sender.getEntityWorld(), test);
        } else {
            WorldServer[] worlds = DimensionManager.getWorlds();
            for (WorldServer world : worlds) {
                if (world != null) addEntities(found, world, test);
            }
        }
        return sortAndLimit(pos, found);
    }

    /** The predicate every candidate of this selector must pass, including the position-dependent options. */
    private Predicate<Entity> buildPredicate(final Pos pos) {
        Predicate<Entity> test = this.predicate;
        if (this.box != null) {
            // 1.7.10's AxisAlignedBB is mutable, so the moved box is made once per lookup rather than per entity.
            final AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(
                this.box[0] + pos.x,
                this.box[1] + pos.y,
                this.box[2] + pos.z,
                this.box[3] + pos.x,
                this.box[4] + pos.y,
                this.box[5] + pos.z);
            test = test.and(entity -> aabb.intersectsWith(entity.boundingBox));
        }
        if (this.distance != null) {
            final DoubleRange range = this.distance;
            test = test.and(entity -> range.matchesSqr(entity.getDistanceSq(pos.x, pos.y, pos.z)));
        }
        return test;
    }

    private List<EntityPlayerMP> findPlayers(ICommandSender sender, Predicate<Entity> test) {
        List<EntityPlayerMP> players = new ArrayList<>();
        World limitedTo = sender.getEntityWorld();
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (this.worldLimited && player.worldObj != limitedTo) continue;
            if (test.test(player)) players.add(player);
        }
        return players;
    }

    private void addEntities(List<Entity> out, World world, Predicate<Entity> test) {
        if (world == null || world.loadedEntityList == null) return;
        for (Entity entity : world.loadedEntityList) {
            if (entity != null && test.test(entity)) out.add(entity);
        }
    }

    /** 1.20.1 looks a UUID up in the player list first and then in every level's entity map. */
    private List<Entity> findByUUID() {
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (this.entityUUID.equals(player.getUniqueID())) {
                return Collections.<Entity>singletonList(player);
            }
        }
        WorldServer[] worlds = DimensionManager.getWorlds();
        for (WorldServer world : worlds) {
            if (world == null || world.loadedEntityList == null) continue;
            for (Entity entity : world.loadedEntityList) {
                if (entity != null && this.entityUUID.equals(entity.getUniqueID())) {
                    return Collections.<Entity>singletonList(entity);
                }
            }
        }
        return Collections.emptyList();
    }

    private <T extends Entity> List<T> sortAndLimit(final Pos pos, List<T> list) {
        if (list.size() > 1) {
            if (this.order == ORDER_NEAREST) {
                Collections.sort(
                    list,
                    (left, right) -> Double
                        .compare(left.getDistanceSq(pos.x, pos.y, pos.z), right.getDistanceSq(pos.x, pos.y, pos.z)));
            } else if (this.order == ORDER_FURTHEST) {
                Collections.sort(
                    list,
                    (left, right) -> Double
                        .compare(right.getDistanceSq(pos.x, pos.y, pos.z), left.getDistanceSq(pos.x, pos.y, pos.z)));
            } else if (this.order == ORDER_RANDOM) {
                Collections.shuffle(list);
            }
        }
        int size = Math.min(this.maxResults, list.size());
        return size >= list.size() ? list : new ArrayList<>(list.subList(0, size));
    }

    /**
     * A position in the world, the 1.7.10 stand-in for 1.20.1's {@code Vec3}. The selector's {@code x=}/{@code y=}/
     * {@code z=} options override the sender's own position axis by axis.
     */
    public static final class Pos {

        public final double x;
        public final double y;
        public final double z;

        public Pos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Pos of(ICommandSender sender) {
            if (sender instanceof Entity) {
                Entity entity = (Entity) sender;
                return new Pos(entity.posX, entity.posY, entity.posZ);
            }
            ChunkCoordinates coordinates = sender.getPlayerCoordinates();
            return new Pos(coordinates.posX, coordinates.posY, coordinates.posZ);
        }
    }

    /** 1.20.1's {@code MinMaxBounds.Doubles} in the one shape the selector uses: a squared-distance range. */
    public static final class DoubleRange {

        public final Double min;
        public final Double max;

        public DoubleRange(Double min, Double max) {
            this.min = min;
            this.max = max;
        }

        public boolean matchesSqr(double squared) {
            if (this.min != null && this.min * this.min > squared) return false;
            return this.max == null || !(this.max * this.max < squared);
        }
    }

    /** 1.20.1's {@code MinMaxBounds.Ints}: the {@code level=} option. */
    public static final class IntRange {

        public final Integer min;
        public final Integer max;

        public IntRange(Integer min, Integer max) {
            this.min = min;
            this.max = max;
        }

        public boolean matches(int value) {
            if (this.min != null && value < this.min) return false;
            return this.max == null || value <= this.max;
        }
    }

    /**
     * 1.20.1's {@code WrappedMinMaxBounds}: {@code x_rotation=}/{@code y_rotation=} wrap around at +/-180 degrees.
     * A range whose minimum is above its maximum (after wrapping) therefore means "outside the gap".
     */
    public static final class WrappedRange {

        public final Float min;
        public final Float max;

        public WrappedRange(Float min, Float max) {
            this.min = min;
            this.max = max;
        }

        public boolean matches(float value) {
            double low = MathHelper.wrapAngleTo180_float(this.min == null ? 0.0F : this.min);
            double high = MathHelper.wrapAngleTo180_float(this.max == null ? 359.0F : this.max);
            double wrapped = MathHelper.wrapAngleTo180_float(value);
            return low > high ? wrapped >= low || wrapped <= high : wrapped >= low && wrapped <= high;
        }
    }
}
