package com.MessTech.init;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.MessTech.common.explosion.MTExplosionDE;

public class Config {

    public static final String GENERAL = "General";
    public static final String REACTOR = "Reactor";
    public static final String VOIDSTAR_BOW = "VoidstarBow";
    public static final String GAME_CONSOLE = "GameConsole";
    public static final String DEBUG = "Debug";
    public static boolean DEFAULT_BATCH_MODE = true;
    /** Client-side look only, purely visual: see {@code MTPiggyHatRenderer}. */
    public static boolean PIGGY_TUMBLES_WEARER = true;
    /**
     * Client-side: the text effect shaders (see {@code TextEffectRenderer} and {@code docs/text-fx/}). Off renders
     * every
     * effect-decorated span as plain text in its first palette colour, so the markers never show up as characters.
     */
    public static boolean TEXT_EFFECTS = true;
    /**
     * Upper bound of the MTReactor meltdown in DE power units (see {@code MTExplosionDE}): 40 = 400 blocks of radius,
     * and
     * {@code MTExplosionDE.MAX_POWER} caps it, so raising it past 40 does nothing.
     */
    public static float REACTOR_EXPLOSION_DE_POWER_LIMIT = 40.0F;
    /**
     * IC2's {@code protection/reactorExplosionPowerLimit} (default 45), clamped before the
     * {@link #REACTOR_EXPLOSION_DE_POWER_LIMIT} scale applies. A config option instead of IC2's ini, so a pack needs no
     * IC2 dependency - see {@code MTReactor#explodeReactor()}.
     */
    public static float REACTOR_IC2_EXPLOSION_POWER_LIMIT = 45.0F;
    /**
     * Server-side, see {@code MTGalacticraftSpaceHandler}: 1200, or the dimension's own {@code IExitHeight}, at 256
     * blocks/s or more.
     */
    public static boolean GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI = true;
    /**
     * Server-side ceiling of vanilla's "moved too quickly!" check, in squared per-tick distance (Forge's build takes
     * the
     * largest of the reported movement and the entity's own motion per axis). Only ever <em>raises</em> the limit.
     * <p>
     * Sized for the Galacticraft hook's launch: 50 TNT at the player's feet leaves the ground at
     * {@code 50 * 0.7975 = 39.875} blocks/tick = 1590.016 squared, the peak the check ever sees (speed only decays
     * after
     * that, to ~13.4 blocks/tick at y=1200); 2000.0 clears it with ~26% headroom, ~44.7 blocks/tick or a ~56 TNT pile -
     * see
     * {@code MixinNetHandlerPlayServer_MovedTooQuickly}.
     */
    public static double MOVED_TOO_QUICKLY_THRESHOLD = 2000.0D;
    /**
     * Server-side, see {@code MTExplosionTunnel}. Off: the arrow still flies straight and still trails, it just stops
     * at the wall.
     */
    public static boolean VOIDSTAR_BOW_BREAKS_BLOCKS = true;
    /**
     * Server-side. The kill is {@code MTBowKill}, the {@code MTKillBase} ladder's bow branch: the death a direct hit
     * deals,
     * through armour, shields, entity level invulnerability and "second life" items. The shooter is never caught - the
     * tunnel
     * starts inside their own body and the branch refuses them too.
     */
    public static boolean VOIDSTAR_BOW_ANNIHILATES_LIVING = true;
    /**
     * Server-side. A real heal of the maximum-minus-current difference, so the game's heal path runs (Forge heal event,
     * healing effects) and the archer never ends above their own maximum; a shot that does not happen heals nothing.
     */
    public static boolean VOIDSTAR_BOW_HEALS_SHOOTER = true;
    /**
     * Server-side, for the bow's DE mode (see {@code MTExplosionDE} and
     * {@link com.MessTech.common.explosion.MTExplosionDE#MAX_POWER}). One unit = ten blocks of blast radius and ten
     * energy
     * points per column trace, spent against each block's explosion resistance (stone 10), so 20 is the full-fuel DE
     * reactor blast ({@code 2 + fuelFraction * 18}): a 200 block radius, ~20 stone blocks of depth downwards and twice
     * that upwards, with a fireball growing to 200 blocks over 12 s. Server-wide event.
     */
    public static float VOIDSTAR_BOW_DE_POWER = 20.0F;
    /**
     * Server-side: radius of that tunnel in blocks, so 1.5 leaves a three by three hole (its corners sit at 1.41); the
     * same
     * radius decides which living things it catches.
     */
    public static float VOIDSTAR_BOW_TUNNEL_RADIUS = 6.0F;
    /**
     * Server-side: max tunnel length in blocks. A longer flight keeps the far end and drops the near one, so one arrow
     * cannot walk the render distance - see {@code MTExplosionTunnel}.
     */
    public static double VOIDSTAR_BOW_TUNNEL_LENGTH = 128.0D;
    /**
     * Ticks to a full draw (20/s): how long {@code MTItemVoidstarBow} eases its speed up and
     * {@code MTMagicCircleRenderer}
     * takes to unfold; 80 = 4 s. Vanilla's first person bow pose ignores it - it ramps over twenty ticks in
     * {@code ItemRenderer}, line 466 ({@code f11 = f10 / 20.0F}) - so the arm stops before the charge does.
     */
    public static int VOIDSTAR_BOW_DRAW_TICKS = 80;

    /**
     * Client-side: the console's folder (游戏机), a relative path resolved against Minecraft's {@code config} folder.
     * Holds
     * the IWAD, the engine's {@code mochadoom.cfg} and savegames, and the Freedoom download.
     */
    public static String GAME_CONSOLE_DIR = "messtech/gameconsole";

    /**
     * Client-side: an explicit IWAD to run ({@code DOOM.WAD}, {@code freedoom2.wad}, ...); empty means any IWAD found
     * in
     * {@link #GAME_CONSOLE_DIR}.
     */
    public static String GAME_CONSOLE_WAD = "";

    /**
     * Client-side: fill a missing IWAD with Freedoom Phase 1 - free (BSD-licensed) and the only Doom WAD that may
     * legally be
     * downloaded, so nothing commercial is ever fetched or shipped.
     */
    public static boolean GAME_CONSOLE_DOWNLOAD_FREEDOOM = true;

    /** Where that download comes from. It is a zip; the console unpacks {@code freedoom1.wad} from it. */
    public static String GAME_CONSOLE_FREEDOOM_URL = "https://github.com/freedoom/freedoom/releases/download/v0.13.0/freedoom-0.13.0.zip";

    /**
     * Client-side: the window's width as a fraction of the screen's, keeping DOOM's 320x200 shape; also capped at 90%
     * of the
     * screen height so it always fits.
     */
    public static float GAME_CONSOLE_SCREEN_FRACTION = 0.5F;

    /**
     * Client-side: threads the engine's true-colour post-processing may use. 320x200 needs one; the engine default is
     * every
     * core, competing with Minecraft's renderer.
     */
    public static int GAME_CONSOLE_THREADS = 2;

    /**
     * Client-side: DOOM's sound effects, through {@code javax.sound} (Mocha Doom's own mixer), not Minecraft's OpenAL -
     * the
     * engine's volume controls them; the console runs fine without it.
     */
    public static boolean GAME_CONSOLE_SOUND = true;

    /**
     * Client-side: DOOM's music, an emulated OPL2 chip (the WAD's own patch table) mixed into the sound effects' output
     * -
     * nothing to install, no extra audio device, no JVM flags.
     */
    public static boolean GAME_CONSOLE_MUSIC = true;

    /**
     * Client-side: freeze the world while the window is open; off by default so playing DOOM does not stop every mob
     * and machine.
     */
    public static boolean GAME_CONSOLE_PAUSES_WORLD = false;

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

        TEXT_EFFECTS = configuration.getBoolean(
            "TEXT_EFFECTS",
            GENERAL,
            TEXT_EFFECTS,
            "Client-side: draw effect-decorated text with its shader (see docs/text-fx/). Off: the same spans still"
                + " render, in the effect's first palette colour, so the markers never show up as characters.");

        REACTOR_EXPLOSION_DE_POWER_LIMIT = configuration.getFloat(
            "REACTOR_EXPLOSION_DE_POWER_LIMIT",
            REACTOR,
            REACTOR_EXPLOSION_DE_POWER_LIMIT,
            0.0F,
            MTExplosionDE.MAX_POWER,
            "Upper bound of the MTReactor explosion in Draconic Evolution power units (1 unit ~ 10 blocks of radius,"
                + " Draconic Evolution's own reactor tops out at 20, this port at twice that).");

        REACTOR_IC2_EXPLOSION_POWER_LIMIT = configuration.getFloat(
            "REACTOR_IC2_EXPLOSION_POWER_LIMIT",
            REACTOR,
            REACTOR_IC2_EXPLOSION_POWER_LIMIT,
            1.0F,
            1000.0F,
            "Raw reactor explosion power the MTReactor clamps to before scaling it onto the Draconic Evolution"
                + " power scale (IC2's own protection/reactorExplosionPowerLimit, default 45).");

        VOIDSTAR_BOW_BREAKS_BLOCKS = configuration.getBoolean(
            "VOIDSTAR_BOW_BREAKS_BLOCKS",
            VOIDSTAR_BOW,
            VOIDSTAR_BOW_BREAKS_BLOCKS,
            "Whether the Voidstar Piercer's arrow blasts a tunnel through every block on its flight path when it"
                + " hits something. Off: the arrow is still straight and still trails, it just stops at the wall.");

        VOIDSTAR_BOW_ANNIHILATES_LIVING = configuration.getBoolean(
            "VOIDSTAR_BOW_ANNIHILATES_LIVING",
            VOIDSTAR_BOW,
            VOIDSTAR_BOW_ANNIHILATES_LIVING,
            "Whether the Voidstar Piercer's arrow kills every living thing inside its tunnel, through the true kill"
                + " ladder of MTKillBase (its MTBowKill branch) - so armour, shields and second life items do not"
                + " save anything in there. The shooter is never caught by it. Off: the tunnel is blocks only and"
                + " nothing inside it dies.");

        VOIDSTAR_BOW_HEALS_SHOOTER = configuration.getBoolean(
            "VOIDSTAR_BOW_HEALS_SHOOTER",
            VOIDSTAR_BOW,
            VOIDSTAR_BOW_HEALS_SHOOTER,
            "Whether letting go of the Voidstar Piercer heals the archer back to full health. A shot that does not"
                + " happen (a draw too weak to fire, no arrow in the inventory) heals nothing.");

        VOIDSTAR_BOW_DE_POWER = configuration.getFloat(
            "VOIDSTAR_BOW_DE_POWER",
            VOIDSTAR_BOW,
            VOIDSTAR_BOW_DE_POWER,
            0.5F,
            MTExplosionDE.MAX_POWER,
            "Power of the explosion the bow's DE mode fires on the first block it hits: ten blocks of blast radius per"
                + " unit, and about one stone block of depth downwards per unit (two upwards), plus fire and lava where"
                + " the energy runs out. The default 20 is what a Draconic Evolution reactor makes when it melts down at"
                + " full fuel (DE: 2 + fuelFraction * 18), i.e. a 200 block radius; the minimum 0.5 is a five block scar."
                + " The fireball around the blast grows to ten times this value in blocks, exactly like DE's own.");

        VOIDSTAR_BOW_TUNNEL_RADIUS = configuration.getFloat(
            "VOIDSTAR_BOW_TUNNEL_RADIUS",
            VOIDSTAR_BOW,
            VOIDSTAR_BOW_TUNNEL_RADIUS,
            0.5F,
            16.0F,
            "Radius of that tunnel in blocks, measured from the line the arrow flew to the centre of a block: 0.5 is"
                + " the single column of blocks the line passes through, 1.5 a three by three hole, 2.5 a five by five"
                + " one and 6.0 (the default) a thirteen by thirteen one. The same radius decides which living things"
                + " the tunnel catches, so a large value is heavy on both the world and the mobs.");

        // 1.7.10's Configuration has no getDouble overload: go through get(...) and read the Property.
        VOIDSTAR_BOW_TUNNEL_LENGTH = configuration
            .get(
                VOIDSTAR_BOW,
                "VOIDSTAR_BOW_TUNNEL_LENGTH",
                VOIDSTAR_BOW_TUNNEL_LENGTH,
                "How long that tunnel may be in blocks. A longer flight keeps the tunnel at the end it hit and drops"
                    + " the part behind it, so one arrow cannot walk the whole render distance.",
                1.0D,
                2048.0D)
            .getDouble();

        VOIDSTAR_BOW_DRAW_TICKS = configuration.getInt(
            "VOIDSTAR_BOW_DRAW_TICKS",
            VOIDSTAR_BOW,
            VOIDSTAR_BOW_DRAW_TICKS,
            5,
            400,
            "Ticks of holding the bow for a full draw (20 ticks to the second), i.e. how long the arrow takes to reach"
                + " full speed and how long the magic circle takes to unfold. 80 (the default) is four seconds. Note"
                + " that vanilla's own first person bow pose always finishes its pull after twenty ticks (ItemRenderer"
                + " line 466), so a longer draw keeps charging after the arm has stopped.");

        GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI = configuration.getBoolean(
            "GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI",
            GENERAL,
            GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI,
            "Open Galacticraft's celestial selection screen (tier 0, Earth only) for a player who crosses the altitude"
                + " a rocket would leave the atmosphere at while climbing at 256 blocks/s or more.");

        // region Game console
        GAME_CONSOLE_DIR = configuration.getString(
            "GAME_CONSOLE_DIR",
            GAME_CONSOLE,
            GAME_CONSOLE_DIR,
            "Client-side: folder the game console keeps its IWAD, engine config, savegames and the translucency cache"
                + " in. A relative path is resolved against Minecraft's config folder, so the default means"
                + " <game>/config/messtech/gameconsole.");

        GAME_CONSOLE_WAD = configuration.getString(
            "GAME_CONSOLE_WAD",
            GAME_CONSOLE,
            GAME_CONSOLE_WAD,
            "Client-side: an explicit IWAD to run (DOOM.WAD, DOOM2.WAD, freedoom1.wad, ...). Leave empty to use any"
                + " IWAD found in GAME_CONSOLE_DIR.");

        GAME_CONSOLE_DOWNLOAD_FREEDOOM = configuration.getBoolean(
            "GAME_CONSOLE_DOWNLOAD_FREEDOOM",
            GAME_CONSOLE,
            GAME_CONSOLE_DOWNLOAD_FREEDOOM,
            "Client-side: fetch Freedoom Phase 1 (a free, BSD-licensed Doom) the first time the console is opened and"
                + " no IWAD is present. Nothing commercial is ever downloaded or shipped; id's own WADs are not"
                + " distributable, so this is the only automatic source.");

        GAME_CONSOLE_FREEDOOM_URL = configuration.getString(
            "GAME_CONSOLE_FREEDOOM_URL",
            GAME_CONSOLE,
            GAME_CONSOLE_FREEDOOM_URL,
            "Client-side: where that Freedoom release zip is downloaded from.");

        GAME_CONSOLE_SCREEN_FRACTION = configuration.getFloat(
            "GAME_CONSOLE_SCREEN_FRACTION",
            GAME_CONSOLE,
            GAME_CONSOLE_SCREEN_FRACTION,
            0.2F,
            1.0F,
            "Client-side: fraction of the screen's width the console's window takes, keeping DOOM's 320x200 aspect."
                + " 0.5 is half the screen; the window is capped at 90% of the height so it always fits.");

        GAME_CONSOLE_THREADS = configuration.getInt(
            "GAME_CONSOLE_THREADS",
            GAME_CONSOLE,
            GAME_CONSOLE_THREADS,
            1,
            16,
            "Client-side: threads the DOOM engine's true-colour post-processing may use. One core is enough for"
                + " 320x200; the engine's own default is every core in the machine, which fights Minecraft's renderer.");

        GAME_CONSOLE_PAUSES_WORLD = configuration.getBoolean(
            "GAME_CONSOLE_PAUSES_WORLD",
            GAME_CONSOLE,
            GAME_CONSOLE_PAUSES_WORLD,
            "Client-side: freeze the world while the console's window is open. Off by default, so that playing DOOM"
                + " does not also stop every mob and machine around the player.");

        GAME_CONSOLE_SOUND = configuration.getBoolean(
            "GAME_CONSOLE_SOUND",
            GAME_CONSOLE,
            GAME_CONSOLE_SOUND,
            "Client-side: play DOOM's sound effects. They go through javax.sound (the engine's own mixer), not"
                + " Minecraft's OpenAL, so the console has its own volume next to the game's.");

        GAME_CONSOLE_MUSIC = configuration.getBoolean(
            "GAME_CONSOLE_MUSIC",
            GAME_CONSOLE,
            GAME_CONSOLE_MUSIC,
            "Client-side: play DOOM's music as well. The songs are FM music written for the OPL2 chip, so the"
                + " console emulates that chip and mixes the result into the same output as the sound effects.");

        // endregion

        // 1.7.10's Configuration has no getDouble overload: go through get(...) and read the Property.
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
