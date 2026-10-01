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
 * Hands a player who blasts past the altitude a rocket leaves the atmosphere at, while climbing at 256 blocks per
 * second or more, the same celestial selection screen Galacticraft's own spaceships open there. Overworld only - the
 * hook is for somebody thrown up off the surface, not for a launch that already left the planet.
 * <p>
 * Galacticraft does that from {@code EntitySpaceshipBase#onUpdate}: any spaceship above
 * {@code IExitHeight#getYCoordinateToTeleport()} (or above 1200 in a dimension that does not implement it) calls
 * {@code onReachAtmosphere()}, and {@code EntityTieredRocket#onReachAtmosphere} passes the riding player to
 * {@code WorldUtil#toCelestialSelection} with the rocket's own tier. A rocket only ever climbs at 1 block per tick
 * ({@code EntityTier1Rocket} caps its motion at {@code timeSinceLaunch / 150}), so 256 blocks per tick never happens
 * to a rocket - it is what a TNT pile does to whoever is standing on it. This hook gives that flight the screen too,
 * but pinned to tier 0, so Earth is the only destination it offers.
 */
public class MTGalacticraftSpaceHandler {

    private static final String GALACTICRAFT_MODID = "GalacticraftCore";

    /** Climb speed that opens the screen, in blocks per tick: 256 blocks per second at 20 ticks per second. */
    private static final double SPEED_THRESHOLD = 256.0D / 20.0D;

    /**
     * Altitude a spaceship has to pass in a dimension that does not implement {@link IExitHeight} - Galacticraft's own
     * fallback, so the two hooks agree on where the atmosphere ends.
     */
    private static final double DEFAULT_EXIT_HEIGHT = 1200.0D;

    /**
     * Rocket tier handed to the celestial selection. Tier 0 is Earth and nothing else: every other entry of
     * {@code WorldUtil#getPossibleDimensionsForSpaceshipTier} has to pass
     * {@code IGalacticraftWorldProvider#canSpaceshipTierPass}, and the Moon already asks for tier 1.
     */
    private static final int EARTH_ONLY_TIER = 0;

    public static void init() {
        // Galacticraft is only pulled in transitively (Galaxy Space depends on it), so never touch its classes or the
        // screen unless it is really there.
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
        // Overworld only: this is for somebody thrown up off the surface, not for a launch that already left. The
        // dimension id is Galacticraft's own field for it, so a pack that moves the Overworld off 0 still works.
        if (player.dimension != ConfigManagerCore.idDimensionOverworld) return;
        // A pile big enough to get here kills whoever stands on it - one TNT alone already deals 46 damage - and the
        // corpse keeps flying with the full impulse, because Explosion applies the knockback regardless of the damage
        // it just dealt. Handing a dead player the destination screen would only fight the death and respawn flow, so
        // the screen waits for a living one: in practice that means creative mode, where the knockback is unchanged
        // but the damage is not taken.
        if (!player.isEntityAlive()) return;

        // Only the tick that crosses the altitude from below counts, so one flight opens the screen exactly once:
        // prevPosY is the position the entity started this tick at (Entity#onEntityUpdate), and a teleport moves both
        // values together, which is why it cannot fake a crossing.
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
