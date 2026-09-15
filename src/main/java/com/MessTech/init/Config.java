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
     * Upper bound of the MTReactor meltdown in Draconic Evolution explosion power units (see
     * {@code MTExplosionDE}) - Draconic Evolution's own reactor explodes with 2..20 and this port keeps twice the
     * original value. One power unit is roughly ten blocks of radius, so 40 vaporises everything within 400 blocks.
     * {@code MTExplosionDE.MAX_POWER} hard caps the same value, so raising it past 40 has no effect.
     */
    public static float REACTOR_EXPLOSION_DE_POWER_LIMIT = 40.0F;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        // region General
        DEFAULT_BATCH_MODE = configuration.getBoolean(
            "DEFAULT_BATCH_MODE",
            GENERAL,
            DEFAULT_BATCH_MODE,
            "Default Batch mode state of machine when placed. True is auto enable Batch mode.");

        REACTOR_EXPLOSION_DE_POWER_LIMIT = configuration.getFloat(
            "REACTOR_EXPLOSION_DE_POWER_LIMIT",
            REACTOR,
            REACTOR_EXPLOSION_DE_POWER_LIMIT,
            0.0F,
            MTExplosionDE.MAX_POWER,
            "Upper bound of the MTReactor explosion in Draconic Evolution power units (1 unit ~ 10 blocks of radius,"
                + " Draconic Evolution's own reactor tops out at 20, this port at twice that).");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
