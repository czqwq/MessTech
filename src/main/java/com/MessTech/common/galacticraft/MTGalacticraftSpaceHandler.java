package com.MessTech.common.galacticraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.MessTech.init.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import micdoodle8.mods.galacticraft.api.world.IExitHeight;
import micdoodle8.mods.galacticraft.core.client.gui.screen.GuiCelestialSelection;
import micdoodle8.mods.galacticraft.core.entities.player.GCPlayerStats;
import micdoodle8.mods.galacticraft.core.util.ConfigManagerCore;
import micdoodle8.mods.galacticraft.core.util.WorldUtil;

/**
 * Gives a player climbing past the altitude a rocket leaves the atmosphere at, at 256 blocks per second or more, the
 * celestial selection screen Galacticraft's own spaceships open there, pinned to tier 0 so Earth is the only
 * destination.
 * Overworld only: for somebody thrown up off the surface, not a launch that already left.
 * <p>
 * Galacticraft does it in {@code EntitySpaceshipBase#onUpdate}: a spaceship above
 * {@code IExitHeight#getYCoordinateToTeleport()} (1200 where the dimension has no such provider) calls
 * {@code onReachAtmosphere()}, which passes the rider to {@code WorldUtil#toCelestialSelection} with the rocket's tier.
 * A
 * rocket itself climbs at 1 block per tick at most ({@code EntityTier1Rocket} caps its motion at
 * {@code timeSinceLaunch /
 * 150}), so 256 blocks/s is what a TNT pile does.
 */
public class MTGalacticraftSpaceHandler {

    private static final String GALACTICRAFT_MODID = "GalacticraftCore";

    /** Climb speed that opens the screen, in blocks per tick: 256 blocks per second at 20 ticks per second. */
    private static final double SPEED_THRESHOLD = 256.0D / 20.0D;

    /**
     * Altitude a spaceship has to pass where the dimension does not implement {@link IExitHeight} - Galacticraft's own
     * fallback, so the two hooks agree on where the atmosphere ends.
     */
    private static final double DEFAULT_EXIT_HEIGHT = 1200.0D;

    /** Tier handed to the celestial selection: tier 0 is Earth and nothing else. The Moon already asks for tier 1. */
    private static final int EARTH_ONLY_TIER = 0;

    public static void init() {
        // Galacticraft comes in transitively (Galaxy Space depends on it), so never touch its classes unless loaded.
        if (!Loader.isModLoaded(GALACTICRAFT_MODID)) return;
        FMLCommonHandler.instance()
            .bus()
            .register(new MTGalacticraftSpaceHandler());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Config.GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        // Overworld only, by Galacticraft's own field, so a pack that moves the Overworld off 0 still works.
        if (player.dimension != ConfigManagerCore.idDimensionOverworld) return;
        // A pile big enough to get here kills whoever stands on it (one TNT already deals 46 damage) and the corpse
        // keeps
        // flying with the full impulse - Explosion applies knockback regardless of damage dealt. So wait for a living
        // player: in practice creative mode, where the knockback is unchanged and the damage is not taken.
        if (!player.isEntityAlive()) return;

        // Only the tick that crosses the altitude from below counts, so one flight opens the screen once: prevPosY is
        // where the entity started the tick (Entity#onEntityUpdate) and a teleport moves both values together.
        final double exitHeight = exitHeight(player.worldObj);
        if (player.prevPosY > exitHeight || player.posY <= exitHeight) return;
        if (player.motionY < SPEED_THRESHOLD) return;

        WorldUtil.toCelestialSelection(
            player,
            GCPlayerStats.get(player),
            EARTH_ONLY_TIER,
            GuiCelestialSelection.MapMode.TRAVEL);
    }

    /** The altitude Galacticraft's own spaceships leave the atmosphere at, in this dimension. */
    private static double exitHeight(World world) {
        return world.provider instanceof IExitHeight exitHeight ? exitHeight.getYCoordinateToTeleport()
            : DEFAULT_EXIT_HEIGHT;
    }
}
