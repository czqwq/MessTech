package com.MessTech.common.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The flight behind {@code /messtech GoUp}: keeps a player climbing at one fixed speed for a while.
 * <p>
 * The speed is held by re-setting {@code motionY} and moving the entity every server tick, so neither gravity nor
 * drag changes it - the climb is exactly {@link #SPEED} blocks per tick, every tick, until the duration runs out.
 * <p>
 * Two numbers here matter for the Galacticraft hook ({@code MTGalacticraftSpaceHandler}), which fires only when a
 * player crosses the rocket altitude with {@code motionY >= 256 / 20 = 12.8} blocks/tick:
 * <ul>
 * <li>{@link #SPEED} is 14.0, i.e. 280 blocks/s. That is above the hook's line even after a tick of vanilla decay
 * ({@code (14 - 0.08) * 0.98 = 13.64}), so the hook sees a qualifying speed no matter which of the two handlers runs
 * first, and its square (196) still sits well below {@code Config.MOVED_TOO_QUICKLY_THRESHOLD}'s 2000 default.</li>
 * <li>The move is done with {@link EntityPlayerMP#setPositionAndUpdate}, not by letting vanilla physics fly the
 * player: in 1.7.10 the client is authoritative for its own position, so the server-side {@code motionY} alone would
 * be overwritten by the next {@code C03PacketPlayer}. {@code setPositionAndUpdate} moves the server entity, clears
 * {@code hasMoved} and sends {@code S08PacketPlayerPosLook}, which pins the client to the same spot; the S08 carries
 * {@code y + 1.62} precisely so the client's next report lands on the server's own Y, which is what lets the vanilla
 * packet path (and with it the hook's {@code PlayerTickEvent}) keep running during the flight.</li>
 * </ul>
 * A side effect worth knowing: without the {@code MOVED_TOO_QUICKLY_THRESHOLD} change the vanilla
 * "moved too quickly!" check (100.0) would reject this flight every tick and pull the player back, so the command
 * warns when the configured ceiling is too low for {@link #SPEED}.
 */
public final class MTGoUpFlight {

    /** Climb speed in blocks per tick: 14.0 = 280 blocks/s, just past the hook's 256 blocks/s line. */
    public static final double SPEED = 14.0D;

    /**
     * Default duration in seconds: 30 s of 280 blocks/s is about 8400 blocks, which clears the hook's 1200 with room
     * to spare (y=1200 is reached after roughly 82 ticks, i.e. just over 4 seconds).
     */
    public static final double DEFAULT_SECONDS = 30.0D;

    /** Not a real flight limit - just a guard so a mistyped duration cannot throw anyone out of the world. */
    public static final double MAX_SECONDS = 3600.0D;

    private static final Map<EntityPlayerMP, Integer> flights = new HashMap<>();
    private static boolean initialized;

    private MTGoUpFlight() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        FMLCommonHandler.instance()
            .bus()
            .register(new MTGoUpFlight());
    }

    /** Starts (or restarts) a flight. {@code seconds} is clamped to {@link #MAX_SECONDS} by the caller. */
    public static void start(EntityPlayerMP player, double seconds) {
        flights.put(player, (int) Math.ceil(seconds * 20.0D));
    }

    /**
     * Runs after the world and the client's own movement packets have been handled, so the position written here is
     * the last word for the tick.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || flights.isEmpty()) return;

        final Iterator<Map.Entry<EntityPlayerMP, Integer>> iterator = flights.entrySet()
            .iterator();

        while (iterator.hasNext()) {
            final Map.Entry<EntityPlayerMP, Integer> flight = iterator.next();
            final EntityPlayerMP player = flight.getKey();
            final int remaining = flight.getValue() - 1;

            // Drop the flight when it is over, or when the player it belongs to cannot be flown any more (death, a
            // world change to a null world, logout). Keeping the map bounded matters because it holds entities.
            if (remaining <= 0 || player.isDead || player.worldObj == null || player.playerNetServerHandler == null) {
                iterator.remove();
                continue;
            }

            flight.setValue(remaining);

            // The fixed speed itself, plus the two things that must not interfere with it.
            player.motionY = SPEED;
            player.fallDistance = 0.0F;
            player.setPositionAndUpdate(player.posX, player.posY + SPEED, player.posZ);
        }
    }
}
