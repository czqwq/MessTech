package com.MessTech.init;

import com.MessTech.common.explosion.MTExplosionDE;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultBoolean;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultDouble;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultFloat;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultInt;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultString;
import com.gtnewhorizon.gtnhlib.config.Config.LangKeyPattern;
import com.gtnewhorizon.gtnhlib.config.Config.Order;
import com.gtnewhorizon.gtnhlib.config.Config.RangeDouble;
import com.gtnewhorizon.gtnhlib.config.Config.RangeFloat;
import com.gtnewhorizon.gtnhlib.config.Config.RangeInt;

/**
 * The mod's configuration: GTNHLib's annotation-driven config ({@code com.gtnewhorizon.gtnhlib.config}). Each nested
 * class is one config category, and the {@link LangKeyPattern} generates the lang keys that the in-game config screen
 * shows ({@code messtech.config.<category>.<field>}, see {@code assets/messtech/lang/*.lang}). The category titles and
 * the option titles are localized through those keys in both lang files.
 * <p>
 * The config is registered in {@link CommonProxy#preInit} and the screen is opened from the Mods menu through
 * {@code @Mod(guiFactory = ...)}; {@code MTGuiClientConfig} lists this class.
 */
@com.gtnewhorizon.gtnhlib.config.Config(modid = MessTech.MODID, category = "", filename = "MessTech")
@LangKeyPattern(pattern = "%mod.config.%cat.%field")
public class Config {

    @Comment("General options of the mod.")
    @Order(0)
    public static final General general = new General();

    @Comment("Machine options of the mod.")
    @Order(1)
    public static final Machines machines = new Machines();

    @Comment("Options of the Voidstar Piercer.")
    @Order(2)
    public static final VoidstarBow voidstarbow = new VoidstarBow();

    @Comment("Options of the game console.")
    @Order(3)
    public static final GameConsole gameconsole = new GameConsole();

    public static class General {

        @Comment("Default Batch mode state of machine when placed. True is auto enable Batch mode.")
        @DefaultBoolean(true)
        public boolean DEFAULT_BATCH_MODE = true;

        @Comment({
            "Client-side look only: while a Transcendent Metal piggy is worn in the helmet slot, the player model",
            "tumbles with it." })
        @DefaultBoolean(true)
        public boolean PIGGY_TUMBLES_WEARER = true;

        @Comment({
            "Client-side: draw effect-decorated text with its shader (see docs/text-fx/). Off: the same spans still",
            "render, in the effect's first palette colour, so the markers never show up as characters." })
        @DefaultBoolean(true)
        public boolean TEXT_EFFECTS = true;

        @Comment({ "Open Galacticraft's celestial selection screen (tier 0, Earth only) for a player who crosses the",
            "altitude a rocket would leave the atmosphere at while climbing at 256 blocks/s or more." })
        @DefaultBoolean(true)
        public boolean GALACTICRAFT_FAST_ASCENT_OPENS_SPACE_GUI = true;

        @Comment({
            "Ceiling of vanilla's 'moved too quickly!' server-side speed check, in the squared per-tick distance it",
            "compares against (vanilla 100.0 = 10 blocks/tick in a single axis, 200.0 is about 14, and",
            "1.7976931348623157E308 effectively disables the check). The default 2000.0 is sized for the 50-TNT",
            "launch the Galacticraft hook needs, which peaks at 1590 squared; 100.0 or below leaves vanilla's own",
            "limit in place." })
        @DefaultDouble(2000.0D)
        @RangeDouble(min = Double.MIN_VALUE, max = Double.MAX_VALUE)
        public double MOVED_TOO_QUICKLY_THRESHOLD = 2000.0D;
    }

    public static class Machines {

        @Comment({
            "One batch calculation of the parallel helper: when enabled, a machine takes every matching material in",
            "its input buses in a single run instead of one parallel-limited batch. The EU/t becomes the normal",
            "parallel total divided by the parallel count and the duration is multiplied by that count, so the total",
            "EU spent is unchanged. The machine's parallel cap and the power-based parallel limit are ignored; void",
            "protection and the int tick limit of the duration still apply." })
        @DefaultBoolean(false)
        public boolean ENABLE_ONE_BATCH_CALCULATOR = false;

        @Comment("Options of the MTReactor.")
        public final Reactor reactor = new Reactor();

        public static class Reactor {

            @Comment({
                "Upper bound of the MTReactor explosion in Draconic Evolution power units (1 unit ~ 10 blocks of",
                "radius, Draconic Evolution's own reactor tops out at 20, this port at twice that)." })
            @DefaultFloat(40.0F)
            @RangeFloat(min = 0.0F, max = MTExplosionDE.MAX_POWER)
            public float REACTOR_EXPLOSION_DE_POWER_LIMIT = 40.0F;

            @Comment({
                "Raw reactor explosion power the MTReactor clamps to before scaling it onto the Draconic Evolution",
                "power scale (IC2's own protection/reactorExplosionPowerLimit, default 45)." })
            @DefaultFloat(45.0F)
            @RangeFloat(min = 1.0F, max = 1000.0F)
            public float REACTOR_IC2_EXPLOSION_POWER_LIMIT = 45.0F;
        }
    }

    public static class VoidstarBow {

        @Comment({
            "Whether the Voidstar Piercer's arrow blasts a tunnel through every block on its flight path when it",
            "hits something. Off: the arrow is still straight and still trails, it just stops at the wall." })
        @DefaultBoolean(true)
        public boolean VOIDSTAR_BOW_BREAKS_BLOCKS = true;

        @Comment({
            "Whether the Voidstar Piercer's arrow kills every living thing inside its tunnel, through the true kill",
            "ladder of MTKillBase (its MTBowKill branch) - so armour, shields and second life items do not save",
            "anything in there. The shooter is never caught by it. Off: the tunnel is blocks only and nothing inside",
            "it dies." })
        @DefaultBoolean(true)
        public boolean VOIDSTAR_BOW_ANNIHILATES_LIVING = true;

        @Comment({
            "Whether letting go of the Voidstar Piercer heals the archer back to full health. A shot that does not",
            "happen (a draw too weak to fire, no arrow in the inventory) heals nothing." })
        @DefaultBoolean(true)
        public boolean VOIDSTAR_BOW_HEALS_SHOOTER = true;

        @Comment({
            "Power of the explosion the bow's DE mode fires on the first block it hits: ten blocks of blast radius",
            "per unit, and about one stone block of depth downwards per unit (two upwards), plus fire and lava where",
            "the energy runs out. The default 20 is what a Draconic Evolution reactor makes when it melts down at",
            "full fuel (DE: 2 + fuelFraction * 18), i.e. a 200 block radius; the minimum 0.5 is a five block scar.",
            "The fireball around the blast grows to ten times this value in blocks, exactly like DE's own." })
        @DefaultFloat(20.0F)
        @RangeFloat(min = 0.5F, max = MTExplosionDE.MAX_POWER)
        public float VOIDSTAR_BOW_DE_POWER = 20.0F;

        @Comment({
            "Radius of that tunnel in blocks, measured from the line the arrow flew to the centre of a block: 0.5",
            "is the single column of blocks the line passes through, 1.5 a three by three hole, 2.5 a five by five",
            "one and 6.0 (the default) a thirteen by thirteen one. The same radius decides which living things the",
            "tunnel catches, so a large value is heavy on both the world and the mobs." })
        @DefaultFloat(6.0F)
        @RangeFloat(min = 0.5F, max = 16.0F)
        public float VOIDSTAR_BOW_TUNNEL_RADIUS = 6.0F;

        @Comment({
            "How long that tunnel may be in blocks. A longer flight keeps the tunnel at the end it hit and drops the",
            "part behind it, so one arrow cannot walk the whole render distance." })
        @DefaultDouble(128.0D)
        @RangeDouble(min = 1.0D, max = 2048.0D)
        public double VOIDSTAR_BOW_TUNNEL_LENGTH = 128.0D;

        @Comment({
            "Ticks of holding the bow for a full draw (20 ticks to the second), i.e. how long the arrow takes to",
            "reach full speed and how long the magic circle takes to unfold. 80 (the default) is four seconds. Note",
            "that vanilla's own first person bow pose always finishes its pull after twenty ticks (ItemRenderer line",
            "466), so a longer draw keeps charging after the arm has stopped." })
        @DefaultInt(80)
        @RangeInt(min = 5, max = 400)
        public int VOIDSTAR_BOW_DRAW_TICKS = 80;
    }

    public static class GameConsole {

        @Comment({
            "Client-side: folder the game console keeps its IWAD, engine config, savegames and the translucency cache",
            "in. A relative path is resolved against Minecraft's config folder, so the default means",
            "<game>/config/messtech/gameconsole." })
        @DefaultString("messtech/gameconsole")
        public String GAME_CONSOLE_DIR = "messtech/gameconsole";

        @Comment({
            "Client-side: an explicit IWAD to run (DOOM.WAD, DOOM2.WAD, freedoom1.wad, ...). Leave empty to use any",
            "IWAD found in GAME_CONSOLE_DIR." })
        @DefaultString("")
        public String GAME_CONSOLE_WAD = "";

        @Comment({
            "Client-side: fetch Freedoom Phase 1 (a free, BSD-licensed Doom) the first time the console is opened",
            "and no IWAD is present. Nothing commercial is ever downloaded or shipped; id's own WADs are not",
            "distributable, so this is the only automatic source." })
        @DefaultBoolean(true)
        public boolean GAME_CONSOLE_DOWNLOAD_FREEDOOM = true;

        @Comment("Client-side: where that Freedoom release zip is downloaded from.")
        @DefaultString("https://github.com/freedoom/freedoom/releases/download/v0.13.0/freedoom-0.13.0.zip")
        public String GAME_CONSOLE_FREEDOOM_URL = "https://github.com/freedoom/freedoom/releases/download/v0.13.0/freedoom-0.13.0.zip";

        @Comment({
            "Client-side: fraction of the screen's width the console's window takes, keeping DOOM's 320x200 aspect.",
            "0.5 is half the screen; the window is capped at 90% of the height so it always fits." })
        @DefaultFloat(0.5F)
        @RangeFloat(min = 0.2F, max = 1.0F)
        public float GAME_CONSOLE_SCREEN_FRACTION = 0.5F;

        @Comment({ "Client-side: threads the DOOM engine's true-colour post-processing may use. One core is enough for",
            "320x200; the engine's own default is every core in the machine, which fights Minecraft's renderer." })
        @DefaultInt(2)
        @RangeInt(min = 1, max = 16)
        public int GAME_CONSOLE_THREADS = 2;

        @Comment({ "Client-side: play DOOM's sound effects. They go through javax.sound (the engine's own mixer), not",
            "Minecraft's OpenAL, so the console has its own volume next to the game's." })
        @DefaultBoolean(true)
        public boolean GAME_CONSOLE_SOUND = true;

        @Comment({ "Client-side: play DOOM's music as well. The songs are FM music written for the OPL2 chip, so the",
            "console emulates that chip and mixes the result into the same output as the sound effects." })
        @DefaultBoolean(true)
        public boolean GAME_CONSOLE_MUSIC = true;

        @Comment({
            "Client-side: freeze the world while the console's window is open. Off by default, so that playing DOOM",
            "does not also stop every mob and machine around the player." })
        @DefaultBoolean(false)
        public boolean GAME_CONSOLE_PAUSES_WORLD = false;
    }
}
