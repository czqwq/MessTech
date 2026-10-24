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
 * Port of DE's {@code Particles.ReactorExplosionParticle} ({@code client/render/particle/Particles.java:788-915})
 * without the model file: the sphere is a lat/long quad mesh ({@link #addSphere}) that a matrix scale flattens into
 * the disk or stretches into the edge. Shallower layering than DE's (see {@link #SPHERE_LAYERS}); the shell scales
 * and colours are DE's own numbers. No UVs are sent - a fireball needs none.
 * <p>
 * Like DE: depth test on, depth mask off (a fireball behind a wall stays behind it and never hides itself), culling
 * off, full-bright lightmap so the blast lights up the night instead of being a dark blob.
 */
@SideOnly(Side.CLIENT)
public final class MTDEBlastRenderer {

    /** DE's plain white texture ({@code textures/models/white.png}); 8x8, so no filtering surprises. */
    private static final ResourceLocation WHITE = new ResourceLocation("messtech", "textures/misc/white.png");

    /** Brighter than daylight; DE's value. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** 10 around, 6 up/down: smooth enough for a fireball. */
    private static final int SPHERE_SEGMENTS = 10, SPHERE_RINGS = 6;

    /**
     * Each layer 5% smaller than the one outside it; DE's eleven cut to four (a quad mesh is not a model, and the
     * outer layers are indistinguishable at this alpha). 4 x 5 x 60 quads = 1,200 quads per blast per frame, vs DE's
     * 55 sphere models.
     */
    private static final int SPHERE_LAYERS = 4;
    private static final double LAYER_SCALE = 0.95D;

    /**
     * DE's five shells per layer: scale and colour outside in, same alpha. Literals are DE's
     * ({@code glColor4f(1F, 0.4F, 0F, a)} .. {@code glColor4f(1F, 0.8F, 0.55F, a)}).
     */
    private static final double[] SHELL_SCALE = { 0.80D, 0.64D, 0.45D, 0.27D, 0.13D };
    private static final float[][] SHELL_COLOUR = { { 1.00F, 0.40F, 0.00F }, { 1.00F, 0.50F, 0.10F },
        { 1.00F, 0.60F, 0.25F }, { 1.00F, 0.70F, 0.40F }, { 1.00F, 0.80F, 0.55F } };

    /** DE's disk {@code (2, 0.2, 2)} and leading edge {@code (5, 10, 5)}. */
    private static final double DISK_FLATTEN = 0.10D, DISK_WIDTH = 2.0D;
    private static final double EDGE_WIDTH = 5.0D, EDGE_HEIGHT = 10.0D;

    /** Leading edge and disk/outer shell opacity at blast start; DE's values. */
    private static final float EDGE_ALPHA = 0.30F;
    private static final float BODY_ALPHA = 0.50F;

    /** DE's shell alpha: {@code (1 - 0.5 * size / maxSize)} becomes {@code 1 - this} halved. */
    private static final double ALPHA_FADE = 0.5D;

    private MTDEBlastRenderer() {}

    /** Registers the world render hook; called by the client proxy. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTDEBlastRenderer());
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;

        float partial = event.partialTicks;
        double viewX = RenderManager.renderPosX, viewY = RenderManager.renderPosY, viewZ = RenderManager.renderPosZ;

        // Found as vanilla finds entities to render: everything loaded near the player. Rare (one per explosion), so
        // the scan is free outside a nuke.
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
     * @param size growth in blocks, one block per tick (DE's {@code size})
     */
    private void draw(MTEntityDEBlast blast, double x, double y, double z, double size) {
        double maxSize = Math.max(1.0D, blast.getMaxSize());
        double grown = size / maxSize;

        // DE fades the shells as it grows: edge gone a third of the way, body at the end - that turns the flash into
        // smoke.
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
        // DE's own blend: plain alpha, not additive - the shells hide each other.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(WHITE);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setBrightness(FULL_BRIGHT);
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);

        // 1) Body: DE's nested layers, outermost first, five shells each.
        for (int layer = 0; layer < SPHERE_LAYERS; layer++) {
            double layerScale = size * Math.pow(LAYER_SCALE, layer);
            for (int shell = 0; shell < SHELL_SCALE.length; shell++) {
                if (bodyAlpha <= 0.0F) break;

                float[] colour = SHELL_COLOUR[shell];
                tessellator.setColorRGBA_F(colour[0], colour[1], colour[2], bodyAlpha);
                addSphere(tessellator, x, y, z, layerScale * SHELL_SCALE[shell]);
            }
        }

        // 2) Ground disk: same sphere squashed flat (DE: glScaled(2, 0.2, 2)).
        if (bodyAlpha > 0.0F) {
            tessellator.setColorRGBA_F(1.0F, 0.4F, 0.0F, bodyAlpha);
            addSphere(tessellator, x, y, z, size * DISK_WIDTH, size * DISK_WIDTH * DISK_FLATTEN, size * DISK_WIDTH);
        }

        // 3) Leading edge: same sphere stretched up (DE: glScaled(5, 10, 5)).
        if (edgeAlpha > 0.0F) {
            tessellator.setColorRGBA_F(1.0F, 0.4F, 0.0F, edgeAlpha);
            addSphere(tessellator, x, y, z, size * EDGE_WIDTH, size * EDGE_HEIGHT, size * EDGE_WIDTH);
        }

        tessellator.draw();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** The same sphere, one radius for all three axes. */
    private static void addSphere(Tessellator tessellator, double x, double y, double z, double radius) {
        addSphere(tessellator, x, y, z, radius, radius, radius);
    }

    /**
     * Lat/long sphere, one radius per axis, so one mesh serves body, disk and edge. Bands and columns share vertices
     * (no seams); no UVs and no normals - a fireball needs neither.
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
