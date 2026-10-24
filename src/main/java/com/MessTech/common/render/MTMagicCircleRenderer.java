package com.MessTech.common.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.MessTech.common.items.MTItems;
import com.MessTech.init.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The magic circle of the Voidstar Piercer, drawn while the bow is charged: the Yes Steve Model pack
 * {@code wine_fox / 05_magical} bow charge ({@code animations/arm.animation.json -> use_mainhand:bow};
 * {@code models/main.json} bones {@code ysmGlowdamofazhen}, {@code ysmGlowmofazhen6..9}; {@code textures/magic.png}
 * sprites at {@code 191,191} and {@code 161,50}) rebuilt as world space quads, not model bones:
 * <ul>
 * <li><b>Two sprites:</b> big (segment ring + inscribed square with corner marks + inner circle), small (compass
 * flower), from {@code tmp/chaosbow/gen_voidstar_ysm_circles.py} as {@code misc/magicRing.png},
 * {@code misc/magicFlower.png}; the pack's pixels are another author's art, so only the motifs are shared.</li>
 * <li><b>Two planes per circle:</b> the pack's small circle bones carry a second plane of 60% turned 45 degrees - the
 * turned copy is what reads as a magic circle, not a decal ({@link #INNER_RATIO}, {@link #INNER_ANGLE}).</li>
 * <li><b>One big circle, four around it:</b> the pack's four are children of the big bone, nine units off its pivot
 * (~1.33 big radii), so its spin carries them <b>around</b> it; corner positions turn by its angle.</li>
 * <li><b>Pack spins</b>, degrees per second: big slowly ({@link #BIG_SPIN_PER_SECOND}: 0 -> 90 over five seconds),
 * small ten times faster ({@link #SMALL_SPIN_PER_SECOND}), corners the other way.</li>
 * <li><b>Pack unfold:</b> the small circles one after another, each <b>bursts</b> ({@link #burstScale}) along the
 * weapon axis ({@code 0,0,38} -> {@code 0,0,-24} = {@link #AXIAL_FLIGHT}); the big one joins at the end of the
 * charge.</li>
 * <li><b>Plain alpha blending</b> (the pack's {@code entityTranslucent}), no back-face culling so both sides show;
 * one deviation: full bright ({@code Tessellator#setBrightness(0xF000F0)}) where the pack uses world light, because a
 * summoned circle should glow in the dark, not turn grey.</li>
 * </ul>
 * The quads hang in the plane perpendicular to the look direction; the phase is the draw itself
 * ({@code getMaxItemUseDuration() - getItemInUseCount()}), so it cannot drift out of step with the bow. Only the
 * drawing player's client draws: 1.7.10 does not sync item use to other clients.
 */
@SideOnly(Side.CLIENT)
public final class MTMagicCircleRenderer {

    /** The two sprites: plain textures, not atlas sprites, so soft edges cannot bleed into a neighbour. */
    private static final ResourceLocation RING_TEXTURE = new ResourceLocation(
        "messtech",
        "textures/misc/magicRing.png");
    private static final ResourceLocation FLOWER_TEXTURE = new ResourceLocation(
        "messtech",
        "textures/misc/magicFlower.png");

    /** Full daylight brightness: the circle is light, not a lit object. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Ticks of drawing at which the draw counts as full: the bow's own draw length
     * ({@link Config#VOIDSTAR_BOW_DRAW_TICKS}, four seconds by default), so the whole unfold lands inside a charge.
     */
    private static int fullDrawTicks() {
        return Math.max(1, Config.VOIDSTAR_BOW_DRAW_TICKS);
    }

    /** Distance in front of the eye. */
    private static final double DISTANCE = 1.90D;

    /** How far the centre sits below the eye. */
    private static final double EYE_OFFSET = -0.25D;

    /** Big circle radius at draw start and when fully formed. */
    private static final double BIG_RADIUS_FROM = 0.30D;
    private static final double BIG_RADIUS_TO = 0.95D;

    /** The pack's inner copy: 16.2 of 27 units, turned 45 degrees. */
    private static final double INNER_RATIO = 0.60D;
    private static final double INNER_ANGLE = Math.PI / 4.0D;

    /** Corner distance and size, in big radii (pack: 9 of 6.75, 9.45 of 13.5). */
    private static final double CORNER_DISTANCE = 1.33D;
    private static final double CORNER_RADIUS = 0.70D;

    /** Spin, in degrees per second, from the pack. */
    private static final double BIG_SPIN_PER_SECOND = 18.0D;
    private static final double SMALL_SPIN_PER_SECOND = 180.0D;

    /** Opacity at draw start and at a full draw. */
    private static final float ALPHA_FROM = 0.20F;
    private static final float ALPHA_TO = 0.95F;

    /** Sprite tint: the pack's own violet. */
    private static final float RED = 0.92F, GREEN = 0.84F, BLUE = 1.0F;

    /** The four corners unfold one after another over this stretch. */
    private static final double CORNER_FIRST = 0.10D;
    private static final double CORNER_STEP = 0.07D;
    private static final double CORNER_GROWTH = 0.35D;

    /** Oversize a corner bursts at, and the share of the growth the collapse takes. */
    private static final double BURST_SCALE = 2.8D;
    private static final double BURST_COLLAPSE = 0.45D;

    /** Flight along the look axis while a circle unfolds, in blocks. */
    private static final double AXIAL_FLIGHT = 1.60D;

    /** The big circle joins this late and takes this much to grow. */
    private static final double BIG_FIRST = 0.62D;
    private static final double BIG_GROWTH = 0.30D;

    private MTMagicCircleRenderer() {}

    /** Registers the world render hook. Called by the client proxy. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTMagicCircleRenderer());
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (player == null || player.worldObj == null) return;

        // Item in use and its draw: the numbers the item itself works with.
        ItemStack using = player.getItemInUse();
        if (using == null || using.getItem() != MTItems.voidstarBow) return;

        int drawn = using.getMaxItemUseDuration() - player.getItemInUseCount();
        if (drawn <= 0) return;

        float charge = MathHelper.clamp_float((float) drawn / fullDrawTicks(), 0.0F, 1.0F);
        float partial = event.partialTicks;

        Vec3 look = player.getLook(partial);
        double forwardX = look.xCoord, forwardY = look.yCoord, forwardZ = look.zCoord;
        double forwardLength = Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
        if (forwardLength < 1.0E-4D) return;
        forwardX /= forwardLength;
        forwardY /= forwardLength;
        forwardZ /= forwardLength;

        // The circle's plane: "right" is the look turned 90 degrees around the world's up axis, "up" perpendicular
        // to both. Straight up or down leaves no right vector (the two would be parallel), so x is used there.
        double rightX = -forwardZ, rightZ = forwardX;
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLength < 1.0E-3D) {
            rightX = 1.0D;
            rightZ = 0.0D;
            rightLength = 1.0D;
        }
        rightX /= rightLength;
        rightZ /= rightLength;
        double upX = -rightZ * forwardY;
        double upY = rightZ * forwardX - rightX * forwardZ;
        double upZ = rightX * forwardY;

        double eyeX = player.prevPosX + (player.posX - player.prevPosX) * partial;
        double eyeY = player.prevPosY + (player.posY - player.prevPosY) * partial + player.getEyeHeight() + EYE_OFFSET;
        double eyeZ = player.prevPosZ + (player.posZ - player.prevPosZ) * partial;

        double centerX = eyeX + forwardX * DISTANCE;
        double centerY = eyeY + forwardY * DISTANCE;
        double centerZ = eyeZ + forwardZ * DISTANCE;

        double bigRadius = BIG_RADIUS_FROM + (BIG_RADIUS_TO - BIG_RADIUS_FROM) * smoothStep(charge / BIG_GROWTH);
        float alpha = ALPHA_FROM + (ALPHA_TO - ALPHA_FROM) * Math.min(1.0F, charge / 0.15F);
        double seconds = ((double) player.ticksExisted + partial) / 20.0D;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // The world pass may leave the alpha test on (entities draw with it); its threshold would eat the soft edges
        // of the line art.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        // entityTranslucent, i.e. plain alpha blending.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        Tessellator tessellator = Tessellator.instance;

        // the four corners, in the flower sprite
        minecraft.getTextureManager()
            .bindTexture(FLOWER_TEXTURE);
        tessellator.startDrawingQuads();
        tessellator.setBrightness(FULL_BRIGHT);

        // The corners orbit the big circle; its spin carries them around it.
        double orbit = Math.toRadians(seconds * BIG_SPIN_PER_SECOND);
        for (int corner = 0; corner < 4; corner++) {
            double unfolded = smoothStep((charge - (CORNER_FIRST + corner * CORNER_STEP)) / CORNER_GROWTH);
            if (unfolded <= 0.0D) continue;

            double burst = burstScale(charge, CORNER_FIRST + corner * CORNER_STEP);
            double radius = bigRadius * CORNER_RADIUS * burst;
            double distance = bigRadius * CORNER_DISTANCE * unfolded;

            double angle = orbit + Math.PI / 4.0D + corner * Math.PI / 2.0D;
            double cos = Math.cos(angle), sin = Math.sin(angle);

            // Along the look axis while it unfolds: out of the bow, and back in.
            double axial = (1.0D - unfolded) * AXIAL_FLIGHT;

            // `right` is horizontal by construction, so it never adds to a corner's Y.
            double circleX = centerX + (rightX * cos + upX * sin) * distance - forwardX * axial;
            double circleY = centerY + upY * sin * distance - forwardY * axial;
            double circleZ = centerZ + (rightZ * cos + upZ * sin) * distance - forwardZ * axial;

            // The small ones turn ten times as fast, alternately the other way round.
            double spin = Math.toRadians(seconds * SMALL_SPIN_PER_SECOND) * (corner % 2 == 0 ? 1.0D : -1.0D);
            float cornerAlpha = (float) (alpha * unfolded);

            addCircle(
                tessellator,
                circleX,
                circleY,
                circleZ,
                rightX,
                0.0D,
                rightZ,
                upX,
                upY,
                upZ,
                radius,
                spin,
                cornerAlpha);
            addCircle(
                tessellator,
                circleX,
                circleY,
                circleZ,
                rightX,
                0.0D,
                rightZ,
                upX,
                upY,
                upZ,
                radius * INNER_RATIO,
                spin + INNER_ANGLE,
                cornerAlpha);
        }

        tessellator.draw();

        // the big circle, in the ring sprite
        double bigUnfold = smoothStep((charge - BIG_FIRST) / BIG_GROWTH);
        if (bigUnfold > 0.0D) {
            minecraft.getTextureManager()
                .bindTexture(RING_TEXTURE);
            tessellator.startDrawingQuads();
            tessellator.setBrightness(FULL_BRIGHT);

            // It arrives flying forward out of the bow, as its position keys do (0,0,-42 to 0,0,13).
            double axial = (1.0D - bigUnfold) * AXIAL_FLIGHT;
            double circleX = centerX - forwardX * axial;
            double circleY = centerY - forwardY * axial;
            double circleZ = centerZ - forwardZ * axial;
            double spin = Math.toRadians(seconds * BIG_SPIN_PER_SECOND);
            float bigAlpha = (float) (alpha * bigUnfold);

            addCircle(
                tessellator,
                circleX,
                circleY,
                circleZ,
                rightX,
                0.0D,
                rightZ,
                upX,
                upY,
                upZ,
                bigRadius * bigUnfold,
                spin,
                bigAlpha);
            addCircle(
                tessellator,
                circleX,
                circleY,
                circleZ,
                rightX,
                0.0D,
                rightZ,
                upX,
                upY,
                upZ,
                bigRadius * bigUnfold * INNER_RATIO,
                spin + INNER_ANGLE,
                bigAlpha);

            tessellator.draw();
        }

        GL11.glDepthMask(true);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** Adds one circle to the batch: a quad of the bound sprite, centred on the point, turned by {@code angle}. */
    private static void addCircle(Tessellator tessellator, double centerX, double centerY, double centerZ,
        double rightX, double rightY, double rightZ, double upX, double upY, double upZ, double radius, double angle,
        float alpha) {
        if (radius <= 0.0D || alpha <= 0.0F) return;

        // Turning the axes it spans keeps the sprite in the plane, not the plane itself.
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double spanX = rightX * cos + upX * sin;
        double spanY = rightY * cos + upY * sin;
        double spanZ = rightZ * cos + upZ * sin;
        double crossX = -rightX * sin + upX * cos;
        double crossY = -rightY * sin + upY * cos;
        double crossZ = -rightZ * sin + upZ * cos;

        float offsetX = (float) (centerX - RenderManager.renderPosX);
        float offsetY = (float) (centerY - RenderManager.renderPosY);
        float offsetZ = (float) (centerZ - RenderManager.renderPosZ);

        float x = (float) (spanX * radius), y = (float) (spanY * radius), z = (float) (spanZ * radius);
        float a = (float) (crossX * radius), b = (float) (crossY * radius), c = (float) (crossZ * radius);

        tessellator.setColorRGBA_F(RED, GREEN, BLUE, alpha);
        tessellator.addVertexWithUV(offsetX - x - a, offsetY - y - b, offsetZ - z - c, 0.0D, 0.0D);
        tessellator.addVertexWithUV(offsetX - x + a, offsetY - y + b, offsetZ - z + c, 0.0D, 1.0D);
        tessellator.addVertexWithUV(offsetX + x + a, offsetY + y + b, offsetZ + z + c, 1.0D, 1.0D);
        tessellator.addVertexWithUV(offsetX + x - a, offsetY + y - b, offsetZ + z - c, 1.0D, 0.0D);
    }

    /**
     * The burst of one small circle, from the pack's scale keys (3 and 4, then 0, then the resting value): far too
     * large, collapses to nothing, grows back to exactly its resting size.
     *
     * @param charge draw, 0..1
     * @param start  draw at which this circle's unfold begins
     * @return factor the resting radius is multiplied with
     */
    private static double burstScale(double charge, double start) {
        double phase = (charge - start) / CORNER_GROWTH;
        if (phase <= 0.0D) return 0.0D;
        if (phase < BURST_COLLAPSE) return BURST_SCALE * (1.0D - phase / BURST_COLLAPSE);

        return smoothStep((phase - BURST_COLLAPSE) / (1.0D - BURST_COLLAPSE));
    }

    /** Smooth 0..1 ramp for a value that may leave that range, so circles ease in and out. */
    private static double smoothStep(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}
