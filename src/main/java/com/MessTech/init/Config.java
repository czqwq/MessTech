package com.MessTech.init;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.MessTech.common.explosion.MTExplosionDE;

public class Config {

    public static final String GENERAL = "General";
    public static final String REACTOR = "Reactor";
    public static final String VOIDSTAR_BOW = "VoidstarBow";
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
     * IC2's own {@code protection/reactorExplosionPowerLimit} (its default is 45): the raw reactor explosion power the
     * MTReactor computes is clamped to this value before it is mapped onto the Draconic Evolution power scale of
     * {@link #REACTOR_EXPLOSION_DE_POWER_LIMIT}. Kept as a MessTech config option instead of reading IC2's ini, so a
     * pack can change it without an IC2 dependency - see {@code MTReactor#explodeReactor()}.
     */
    public static float REACTOR_IC2_EXPLOSION_POWER_LIMIT = 45.0F;
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
    /**
     * Server-side: whether the Voidstar Piercer's arrow destroys the blocks on its flight path when it hits
     * something (see {@code MTExplosionTunnel}). With it off the arrow is still straight, still trails and still
     * hits like a vanilla arrow, it just stops at the wall instead of blowing a tunnel through it - the switch for a
     * server that wants the weapon without the block damage.
     */
    public static boolean VOIDSTAR_BOW_BREAKS_BLOCKS = true;
    /**
     * Server-side: whether the Voidstar Piercer's arrow also kills every living thing inside its tunnel. The kill
     * goes through {@code MTBowKill}, the bow's branch of the {@code MTKillBase} true kill ladder, so it reaches a
     * target through armour, shields, entity level invulnerability and "second life" items - the same death a direct
     * hit deals. The shooter is never caught by it (the tunnel starts inside their own body, and the branch refuses
     * them as well). With it off the tunnel is blocks only, and nothing inside it dies.
     */
    public static boolean VOIDSTAR_BOW_ANNIHILATES_LIVING = true;
    /**
     * Server-side: whether letting go of the Voidstar Piercer heals the archer back to full health. It is a real
     * heal of the difference between the maximum and the current health, so the game's heal path runs (the Forge
     * heal event, healing effects) and the archer can never be pushed past their own maximum; a shot that does not
     * happen - a draw too weak to fire, no arrow in the inventory - heals nothing.
     */
    public static boolean VOIDSTAR_BOW_HEALS_SHOOTER = true;
    /**
     * Server-side: power of the explosion the Voidstar Piercer's DE mode fires (see {@code MTExplosionDE}, and
     * {@link com.MessTech.common.explosion.MTExplosionDE#MAX_POWER} for the scale). One power unit is ten blocks of
     * blast radius, and ten points of energy per column trace, spent against each block's own explosion resistance
     * (stone is 10) - so the default 20 is the same blast a Draconic Evolution reactor makes when it melts down at
     * full fuel: DE's own formula is {@code 2 + fuelFraction * 18}, i.e. 2 for an empty reactor and 20 for a full
     * one. That is a 200 block radius: about 20 stone blocks of depth downwards per the same scale and twice that
     * upwards, and a fireball that grows to 200 blocks over 12 seconds. It is a server-wide event - lower it, or set
     * it to 2 for the weakest DE reactor, if that is more than the server wants.
     */
    public static float VOIDSTAR_BOW_DE_POWER = 20.0F;
    /**
     * Server-side: radius of that tunnel in blocks, measured from the line the arrow flew to the centre of a block,
     * so 0.5 leaves the single column of blocks the line passes through, 1.5 a three by three hole (its corners sit
     * at 1.41), 2.5 a five by five one and the default 6.0 a thirteen by thirteen one. The same radius decides which
     * living things the tunnel catches.
     */
    public static float VOIDSTAR_BOW_TUNNEL_RADIUS = 6.0F;
    /**
     * Server-side: how long the tunnel may be in blocks. The far end of a longer flight keeps its tunnel and the
     * near end is dropped, so one arrow can never walk the whole render distance; see {@code MTExplosionTunnel}.
     */
    public static double VOIDSTAR_BOW_TUNNEL_LENGTH = 128.0D;
    /**
     * How long the Voidstar Piercer takes to reach a full draw, in ticks (20 ticks to the second), i.e. how long
     * {@code MTItemVoidstarBow} eases its speed up and how long {@code MTMagicCircleRenderer} takes to unfold the
     * magic circle. The default 80 is the four seconds the charge effect was designed around - the pack it was
     * copied from spreads its own unfold over the same four seconds.
     * <p>
     * The bow's pull frames and the magic circle follow this value. One pose does not: vanilla's first person bow
     * pose ramps over twenty ticks in {@code ItemRenderer}, line 466 ({@code f11 = f10 / 20.0F}) - that is a
     * hardcoded constant of the client renderer, so a draw longer than twenty ticks finishes its arm movement first
     * and keeps charging after it.
     */
    public static int VOIDSTAR_BOW_DRAW_TICKS = 80;

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

        // 1.7.10's Configuration has no getDouble overload, so this goes through get(...) and reads the Property.
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
