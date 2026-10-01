package com.MessTech.init;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.MessTech.common.explosion.MTExplosionDE;

public class Config {

    public static final String GENERAL = "General";
    public static final String REACTOR = "Reactor";
    public static final String DEBUG = "Debug";
    public static boolean DEFAULT_BATCH_MODE = true;
    /**
     * Client-side look only: while a Transcendent Metal piggy is worn in the helmet slot, the whole player model
     * tumbles with it (see {@code MTPiggyHatRenderer}). Purely visual, so the value that counts is the one in the
     * config of the client doing the rendering.
     */
    public static boolean PIGGY_TUMBLES_WEARER = true;
    /**
     * Upper bound of the MTReactor meltdown in Draconic Evolution explosion power units (see
     * {@code MTExplosionDE}) - Draconic Evolution's own reactor explodes with 2..20 and this port keeps twice the
     * original value. One power unit is roughly ten blocks of radius, so 40 vaporises everything within 400 blocks.
     * {@code MTExplosionDE.MAX_POWER} hard caps the same value, so raising it past 40 has no effect.
     */
    public static float REACTOR_EXPLOSION_DE_POWER_LIMIT = 40.0F;
    /**
     * Server-side: a player climbing past the altitude a Galacticraft rocket leaves the atmosphere at (1200, or the
     * dimension's own {@code IExitHeight}) at 256 blocks per second or more is shown Galacticraft's celestial
     * selection screen, pinned to tier 0 so Earth is the only destination on offer (see
     * {@code MTGalacticraftSpaceHandler}).
     */
    public static boolean GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI = true;
    /**
     * Server-side ceiling of vanilla's "moved too quickly!" check, in the units the check itself uses: the squared
     * per-tick distance it compares against, where the vanilla 100.0 means 10 blocks per tick in a single axis (and
     * Forge's build of the method takes the largest of the reported movement and the entity's own motion per axis).
     * Only used to <em>raise</em> the limit - 100.0 or anything below it leaves vanilla alone.
     * <p>
     * The default is sized for the launch the Galacticraft hook exists for. 50 TNT at the player's feet leaves the
     * ground at {@code 50 * 0.7975 = 39.875} blocks/tick, i.e. 1590.016 squared, and that peak on the first flying
     * tick is the largest value the check ever sees - speed only decays from there, down to the ~13.4 blocks/tick the
     * flight still has when it crosses the rocket altitude at y=1200. 2000.0 therefore clears the 1590 the launch
     * needs with roughly 26% of headroom, which is about 44.7 blocks/tick or a ~56 TNT pile. See
     * {@code MixinNetHandlerPlayServer_MovedTooQuickly}.
     */
    public static double MOVED_TOO_QUICKLY_THRESHOLD = 2000.0D;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        // region General
        DEFAULT_BATCH_MODE = configuration.getBoolean(
            "DEFAULT_BATCH_MODE",
            GENERAL,
            DEFAULT_BATCH_MODE,
            "Default Batch mode state of machine when placed. True is auto enable Batch mode.");

        PIGGY_TUMBLES_WEARER = configuration.getBoolean(
            "PIGGY_TUMBLES_WEARER",
            GENERAL,
            PIGGY_TUMBLES_WEARER,
            "Client-side look only: while a Transcendent Metal piggy is worn in the helmet slot, the player model"
                + " tumbles with it.");

        REACTOR_EXPLOSION_DE_POWER_LIMIT = configuration.getFloat(
            "REACTOR_EXPLOSION_DE_POWER_LIMIT",
            REACTOR,
            REACTOR_EXPLOSION_DE_POWER_LIMIT,
            0.0F,
            MTExplosionDE.MAX_POWER,
            "Upper bound of the MTReactor explosion in Draconic Evolution power units (1 unit ~ 10 blocks of radius,"
                + " Draconic Evolution's own reactor tops out at 20, this port at twice that).");

        GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI = configuration.getBoolean(
            "GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI",
            GENERAL,
            GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI,
            "Open Galacticraft's celestial selection screen (tier 0, Earth only) for a player who crosses the altitude"
                + " a rocket would leave the atmosphere at while climbing at 256 blocks/s or more.");

        // 1.7.10's Configuration has no getDouble overload, so this goes through get(...) and reads the Property.
        MOVED_TOO_QUICKLY_THRESHOLD = configuration
            .get(
                GENERAL,
                "MOVED_TOO_QUICKLY_THRESHOLD",
                MOVED_TOO_QUICKLY_THRESHOLD,
                "Ceiling of vanilla's 'moved too quickly!' server-side speed check, in the squared per-tick distance it"
                    + " compares against (vanilla 100.0 = 10 blocks/tick in a single axis, 200.0 is about 14, and"
                    + " 1.7976931348623157E308 effectively disables the check). The default 2000.0 is sized for the"
                    + " 50-TNT launch the Galacticraft hook needs, which peaks at 1590 squared; 100.0 or below leaves"
                    + " vanilla's own limit in place.",
                Double.MIN_VALUE,
                Double.MAX_VALUE)
            .getDouble();

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
