package com.MessTech.common.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.MessTech.common.entity.MTEntityDEBlast;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the fireball of every live {@link MTEntityDEBlast}: the visible half of a
 * {@link com.MessTech.common.explosion.MTExplosionDE}, i.e. of Draconic Evolution's reactor explosion.
 * <p>
 * <b>What it is a port of.</b> DE draws that blast with {@code Particles.ReactorExplosionParticle}
 * ({@code client/render/particle/Particles.java:788-915}): one effect that grows one block per tick, and per frame
 * renders <em>eleven</em> nested copies of the reactor's UV sphere, each of them five shells deep, in colours that go
 * from deep orange ({@code 1, 0.4, 0}) on the outside to a pale yellow ({@code 1, 0.8, 0.55}) in the middle, plus
 * two extra shapes - a flattened one ({@code glScaled(2, 0.2, 2)}, the ground disk) and a stretched one
 * ({@code glScaled(5, 10, 5)}, the leading edge). Everything there is a plain white texture with
 * {@code GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA}, i.e. a coloured sphere and no lighting.
 * <p>
 * This is the same construction without DE's model file: the sphere is built here as a lat/long mesh of quads
 * ({@link #addSphere}), which a matrix scale can flatten into the disk or stretch into the leading edge. The layering
 * is shallower than DE's (see {@link #SPHERE_LAYERS}); the five shells per layer, their scales and their colours are
 * DE's own numbers. The one thing that is not DE's is the texture coordinates: a fireball needs none, so none are
 * sent.
 * <p>
 * Like DE, the blast is drawn with the depth test on and the depth mask off (a fireball behind a wall stays behind
 * it, and the fireball does not hide itself), with culling off and with a lightmap forced to full brightness, so the
 * blast lights up the night instead of being a dark blob.
 */
@SideOnly(Side.CLIENT)
public final class MTDEBlastRenderer {

    /** The plain white texture DE binds ({@code textures/models/white.png}); 8x8 so no filtering surprises. */
    private static final ResourceLocation WHITE = new ResourceLocation("messtech", "textures/misc/white.png");

    /** A lightmap coordinate that is brighter than daylight, the value DE forces for the same effect. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Segments of the sphere mesh: 10 around, 6 up and down is smooth enough for a fireball. */
    private static final int SPHERE_SEGMENTS = 10, SPHERE_RINGS = 6;

    /**
     * DE's layering, each layer 5% smaller than the one outside it. DE draws <em>eleven</em> of them; four are used
     * here, because a layer here is a mesh of quads rather than a model and the outer layers of a fireball this
     * translucent are indistinguishable. The cost is what sets the number: four layers of five shells of 60 quads is
     * 1,200 quads per blast per frame, where DE draws 55 sphere models.
     */
    private static final int SPHERE_LAYERS = 4;
    private static final double LAYER_SCALE = 0.95D;

    /**
     * DE's five shells per layer: scale and colour from the outside in, all of them the same alpha. The colours are
     * DE's own literals ({@code glColor4f(1F, 0.4F, 0F, a)} through {@code glColor4f(1F, 0.8F, 0.55F, a)}).
     */
    private static final double[] SHELL_SCALE = { 0.80D, 0.64D, 0.45D, 0.27D, 0.13D };
    private static final float[][] SHELL_COLOUR = { { 1.00F, 0.40F, 0.00F }, { 1.00F, 0.50F, 0.10F },
        { 1.00F, 0.60F, 0.25F }, { 1.00F, 0.70F, 0.40F }, { 1.00F, 0.80F, 0.55F } };

    /** The flattened shape DE scales with {@code (2, 0.2, 2)} and the stretched one with {@code (5, 10, 5)}. */
    private static final double DISK_FLATTEN = 0.10D, DISK_WIDTH = 2.0D;
    private static final double EDGE_WIDTH = 5.0D, EDGE_HEIGHT = 10.0D;

    /** Opacity of the leading edge and of the disk/outer shell at the start of the blast, straight out of DE. */
    private static final float EDGE_ALPHA = 0.30F;
    private static final float BODY_ALPHA = 0.50F;

    /** The alpha DE applies to its shells, i.e. {@code (1 - 0.5 * size / maxSize)} becomes {@code 1 - this} halved. */
    private static final double ALPHA_FADE = 0.5D;

    private MTDEBlastRenderer() {}

    /** Registers the world render hook. Called by the client proxy. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTDEBlastRenderer());
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;

        float partial = event.partialTicks;
        double viewX = RenderManager.renderPosX, viewY = RenderManager.renderPosY, viewZ = RenderManager.renderPosZ;

        // The blasts are found the same way vanilla finds entities to render: everything loaded near the player.
        // They are rare (one per explosion), so the scan costs nothing outside the moment of a nuke.
        for (Object candidate : minecraft.theWorld.loadedEntityList) {
            if (!(candidate instanceof MTEntityDEBlast)) continue;
            MTEntityDEBlast blast = (MTEntityDEBlast) candidate;

            double centerX = blast.prevPosX + (blast.posX - blast.prevPosX) * partial;
            double centerY = blast.prevPosY + (blast.posY - blast.prevPosY) * partial;
            double centerZ = blast.prevPosZ + (blast.posZ - blast.prevPosZ) * partial;

            draw(blast, centerX - viewX, centerY - viewY, centerZ - viewZ, blast.getSize(partial));
        }
    }

    /**
     * Draws one fireball at the given camera relative position.
     *
     * @param size how far it has grown in blocks, one block per tick (DE's {@code size})
     */
    private void draw(MTEntityDEBlast blast, double x, double y, double z, double size) {
        double maxSize = Math.max(1.0D, blast.getMaxSize());
        double grown = size / maxSize;

        // DE fades its shells out as the fireball grows: the leading edge is gone at a third of the way and the
        // body at the end, which is what turns the flash into smoke.
        float edgeAlpha = (float) Math.max(0.0D, EDGE_ALPHA - grown);
        float bodyAlpha = (float) Math.max(0.0D, BODY_ALPHA - grown * ALPHA_FADE);
        if (edgeAlpha <= 0.0F && bodyAlpha <= 0.0F) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        // DE's own blend for this effect: plain alpha, not additive - the shells are meant to hide each other.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(WHITE);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setBrightness(FULL_BRIGHT);
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);

        // 1) The body: DE's nested layers, outermost first, each one five shells deep.
        for (int layer = 0; layer < SPHERE_LAYERS; layer++) {
            double layerScale = size * Math.pow(LAYER_SCALE, layer);
            for (int shell = 0; shell < SHELL_SCALE.length; shell++) {
                if (bodyAlpha <= 0.0F) break;

                float[] colour = SHELL_COLOUR[shell];
                tessellator.setColorRGBA_F(colour[0], colour[1], colour[2], bodyAlpha);
                addSphere(tessellator, x, y, z, layerScale * SHELL_SCALE[shell]);
            }
        }

        // 2) The ground disk: the same sphere squashed flat (DE: glScaled(2, 0.2, 2)), centred on the blast.
        if (bodyAlpha > 0.0F) {
            tessellator.setColorRGBA_F(1.0F, 0.4F, 0.0F, bodyAlpha);
            addSphere(tessellator, x, y, z, size * DISK_WIDTH, size * DISK_WIDTH * DISK_FLATTEN, size * DISK_WIDTH);
        }

        // 3) The leading edge: the same sphere stretched upwards (DE: glScaled(5, 10, 5)).
        if (edgeAlpha > 0.0F) {
            tessellator.setColorRGBA_F(1.0F, 0.4F, 0.0F, edgeAlpha);
            addSphere(tessellator, x, y, z, size * EDGE_WIDTH, size * EDGE_HEIGHT, size * EDGE_WIDTH);
        }

        tessellator.draw();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** Adds the sphere DE renders, centred on the given offset, of radius {@code 1} scaled by the given factors. */
    private static void addSphere(Tessellator tessellator, double x, double y, double z, double radius) {
        addSphere(tessellator, x, y, z, radius, radius, radius);
    }

    /**
     * Adds a lat/long sphere at the given offset, with one radius per axis so the same mesh serves the round body,
     * the flattened disk and the stretched leading edge. Bands and columns share their vertices, so the shells have
     * no visible seams; a fireball needs no UVs and no normals, so none are sent.
     */
    private static void addSphere(Tessellator tessellator, double x, double y, double z, double radiusX, double radiusY,
        double radiusZ) {
        for (int ring = 0; ring < SPHERE_RINGS; ring++) {
            double phi0 = Math.PI * ring / SPHERE_RINGS - Math.PI / 2.0D;
            double phi1 = Math.PI * (ring + 1) / SPHERE_RINGS - Math.PI / 2.0D;
            double y0 = Math.sin(phi0), y1 = Math.sin(phi1);
            double r0 = Math.cos(phi0), r1 = Math.cos(phi1);

            for (int segment = 0; segment < SPHERE_SEGMENTS; segment++) {
                double theta0 = 2.0D * Math.PI * segment / SPHERE_SEGMENTS;
                double theta1 = 2.0D * Math.PI * (segment + 1) / SPHERE_SEGMENTS;

                double cos0 = Math.cos(theta0), sin0 = Math.sin(theta0);
                double cos1 = Math.cos(theta1), sin1 = Math.sin(theta1);

                tessellator.addVertex(x + radiusX * r0 * cos0, y + radiusY * y0, z + radiusZ * r0 * sin0);
                tessellator.addVertex(x + radiusX * r0 * cos1, y + radiusY * y0, z + radiusZ * r0 * sin1);
                tessellator.addVertex(x + radiusX * r1 * cos1, y + radiusY * y1, z + radiusZ * r1 * sin1);
                tessellator.addVertex(x + radiusX * r1 * cos0, y + radiusY * y1, z + radiusZ * r1 * sin0);
            }
        }
    }
}
