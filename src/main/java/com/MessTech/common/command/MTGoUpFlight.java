package com.MessTech.common.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The flight behind {@code /messtech GoUp}: a fixed-speed climb for a set duration, held by re-setting {@code motionY}
 * and moving the entity every server tick so gravity and drag cannot change it.
 * <p>
 * The Galacticraft hook fires at {@code motionY >= 256 / 20 = 12.8} blocks/tick; {@link #SPEED} is 14.0 (280 blocks/s),
 * above that even after a tick of vanilla decay ({@code (14 - 0.08) * 0.98 = 13.64}), with a square of 196 against
 * {@code Config.MOVED_TOO_QUICKLY_THRESHOLD}'s 2000. The move is {@link EntityPlayerMP#setPositionAndUpdate}, not
 * vanilla
 * physics: 1.7.10's client is authoritative, so a server-side {@code motionY} alone is overwritten by the next
 * {@code C03PacketPlayer}, whereas this clears {@code hasMoved} and sends {@code S08PacketPlayerPosLook} (carrying
 * {@code y + 1.62}) that pins the client and keeps the packet path - and with it the hook's {@code PlayerTickEvent} -
 * running.
 * <p>
 * Vanilla's "moved too quickly!" check (100.0) would otherwise reject every tick and pull the player back, so the
 * command
 * warns when the configured ceiling is too low for {@link #SPEED}.
 */
public final class MTGoUpFlight {

    /** Climb speed in blocks per tick: 14.0 = 280 blocks/s, just past the hook's 256 blocks/s line. */
    public static final double SPEED = 14.0D;

    /**
     * Default duration in seconds: 30 s of 280 blocks/s is about 8400 blocks, clearing the hook's 1200 with room to
     * spare (y=1200 is reached after roughly 82 ticks, just over 4 seconds).
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
     * Runs after the world and the client's own movement packets have been handled, so the position written here is the
     * last word for the tick.
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

            // Drop the flight when it is over, or when its player cannot be flown any more (death, a null world,
            // logout).
            // Keeping the map bounded matters because it holds entities.
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
