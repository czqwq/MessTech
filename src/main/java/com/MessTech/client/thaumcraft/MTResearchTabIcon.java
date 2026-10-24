package com.MessTech.client.thaumcraft;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import com.MessTech.common.thaumcraft.MTThaumcraftResearch;
import com.MessTech.init.MessTech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The dynamically rendered tab icon of the MessTech Thaumonomicon page: the Chaos Matter gear with its own overlay
 * animation (a ring of hue-cycling sparks and a pulsing halo), uploaded once as a {@link DynamicTexture} and redrawn
 * every other client tick.
 */
@SideOnly(Side.CLIENT)
public final class MTResearchTabIcon {

    private static final ResourceLocation GEAR = new ResourceLocation(
        "messtech",
        "textures/items/materialicons/chaos/gearGt.png");
    private static final int SIZE = 32;
    private static final int FRAME_TICKS = 2;

    private static int[] gear;
    private static int[] canvas;
    private static DynamicTexture texture;
    private static int tick;
    private static boolean loggedFailure;

    private MTResearchTabIcon() {}

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MTResearchTabIcon());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tick % FRAME_TICKS != 0) return;
        if (!ensureTexture()) return;
        drawFrame();
        // DynamicTexture keeps its own data array; copy the frame in before it is uploaded.
        System.arraycopy(canvas, 0, texture.getTextureData(), 0, canvas.length);
        texture.updateDynamicTexture();
    }

    private static boolean ensureTexture() {
        if (texture != null) return true;
        try (InputStream stream = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(GEAR)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                logFailure(null);
                return false;
            }
            gear = new int[SIZE * SIZE];
            // Nearest-neighbour upscale: the 16x16 material icon keeps its pixel look at the 32x32 tab size.
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int sourceX = x * image.getWidth() / SIZE;
                    int sourceY = y * image.getHeight() / SIZE;
                    gear[y * SIZE + x] = image.getRGB(sourceX, sourceY);
                }
            }
            canvas = new int[SIZE * SIZE];
            texture = new DynamicTexture(SIZE, SIZE);
            Minecraft.getMinecraft()
                .getTextureManager()
                .loadTexture(MTThaumcraftResearch.TAB_ICON, texture);
            return true;
        } catch (Exception e) {
            texture = null;
            logFailure(e);
            return false;
        }
    }

    private static void logFailure(Exception e) {
        if (loggedFailure) return;
        loggedFailure = true;
        if (e == null) {
            MessTech.MT_LOG.warn("Thaumonomicon tab icon {} could not be decoded", GEAR);
        } else {
            MessTech.MT_LOG.warn("Thaumonomicon tab icon {} could not be loaded", GEAR, e);
        }
    }

    private static void drawFrame() {
        System.arraycopy(gear, 0, canvas, 0, gear.length);

        // Pulsing halo: a ring that expands and fades every 25 frames.
        int haloPhase = tick % 25;
        if (haloPhase < 6) {
            int radius = 6 + haloPhase * 2;
            int alpha = 150 - haloPhase * 22;
            plotRing(radius, (alpha << 24) | 0xFFDD66);
        }

        // Ten sparks orbiting the gear, each with its own hue and phase.
        long frame = tick / FRAME_TICKS;
        for (int i = 0; i < 10; i++) {
            double angle = frame * 0.05D + i * (Math.PI * 2D / 10D);
            int x = (int) Math.round(16 + Math.cos(angle) * 14D);
            int y = (int) Math.round(16 + Math.sin(angle) * 14D);
            int hue = (int) ((frame * 4 + i * 36) % 360);
            plotGlow(x, y, 0xFF000000 | java.awt.Color.HSBtoRGB(hue / 360F, 0.75F, 1F));
        }
    }

    private static void plotRing(int radius, int argb) {
        for (int step = 0; step < 64; step++) {
            double angle = step * (Math.PI * 2D / 64D);
            int x = (int) Math.round(16 + Math.cos(angle) * radius);
            int y = (int) Math.round(16 + Math.sin(angle) * radius);
            blend(x, y, argb, 0.75F);
        }
    }

    private static void plotGlow(int x, int y, int argb) {
        blend(x, y, argb, 1.0F);
        blend(x - 1, y, argb, 0.45F);
        blend(x + 1, y, argb, 0.45F);
        blend(x, y - 1, argb, 0.45F);
        blend(x, y + 1, argb, 0.45F);
    }

    private static void blend(int x, int y, int argb, float strength) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int index = y * SIZE + x;
        int sourceAlpha = (int) (((argb >>> 24) & 0xFF) * strength);
        if (sourceAlpha <= 0) return;
        int destination = canvas[index];
        int destinationAlpha = (destination >>> 24) & 0xFF;
        int outAlpha = sourceAlpha + destinationAlpha * (255 - sourceAlpha) / 255;
        if (outAlpha <= 0) return;
        int red = blendChannel(
            (argb >> 16) & 0xFF,
            (destination >> 16) & 0xFF,
            sourceAlpha,
            destinationAlpha,
            outAlpha);
        int green = blendChannel(
            (argb >> 8) & 0xFF,
            (destination >> 8) & 0xFF,
            sourceAlpha,
            destinationAlpha,
            outAlpha);
        int blue = blendChannel(argb & 0xFF, destination & 0xFF, sourceAlpha, destinationAlpha, outAlpha);
        canvas[index] = (outAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int blendChannel(int source, int destination, int sourceAlpha, int destinationAlpha, int outAlpha) {
        int value = (source * sourceAlpha + destination * destinationAlpha * (255 - sourceAlpha) / 255) / outAlpha;
        return Math.min(255, value);
    }
}
