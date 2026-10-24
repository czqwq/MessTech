package com.MessTech.common.render;

import java.awt.Color;
import java.util.Random;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.ItemRenderUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;
import gregtech.api.interfaces.IGT_ItemWithMaterialRenderer;
import gregtech.common.render.items.CosmicNeutroniumRenderer;
import gregtech.common.render.items.GeneratedMaterialRenderer;
import gregtech.common.render.items.GlitchEffectRenderer;
import gregtech.common.render.items.InfinityRenderer;
import gregtech.common.render.items.UniversiumRenderer;

/**
 * The Chaos Matter look: several of GT's animated material renderers playing <b>at once</b> on a <b>random</b>
 * playback, not a fixed rotation.
 * <p>
 * GT's four effect kinds only combine in fixed ways:
 * <ul>
 * <li><b>Geometry</b> - {@code TranscendentMetalRenderer} only rotates the matrix. {@link #applySpin} (axis 0.3, 0.5,
 * 0.2 at 3.5 deg/client tick + a slower second axis) is <b>disabled</b> (call in {@link #renderItem} commented out on
 * request), so the icon draws straight.</li>
 * <li><b>Colour modulation</b> - {@code GaiaSpiritRenderer} / {@code RainbowOverlayRenderer} only set {@code glColor};
 * {@link #renderRegularItem} mixes both hues, each on a per-session random period + phase ({@link #gaiaPeriod},
 * {@link #rainbowPeriod}, {@link #gaiaPhase}, {@link #rainbowPhase}): never a fixed clock.</li>
 * <li><b>Extra translucent passes</b> - {@code InfinityRenderer} halo + pulse, {@code CosmicNeutroniumRenderer} halo,
 * {@code GlitchEffectRenderer} red/cyan ghosts (public static, inventory only); pulse every frame, the rest rolled per
 * window in {@link #rollWindow}.</li>
 * <li><b>Whole-icon replacements</b> - {@code UniversiumRenderer} (whole-sprite shader),
 * {@code CosmicNeutroniumRenderer}, {@code InfinityRenderer} draw the icon themselves, so two would hide each other;
 * {@link #renderLayer} draws one over the base, additive or normal, {@link #rollWindow} picking it, its length and
 * blend, and no layer twice in a row.</li>
 * </ul>
 * The icons are grey with multi-colour speckles
 * ({@code assets/messtech/textures/items/materialicons/chaos}), tinted by the material's light grey via
 * {@link #mixedTint}, so the colour never settles.
 */
@SideOnly(Side.CLIENT)
public class MTChaosMatterRenderer extends GeneratedMaterialRenderer {

    /** The whole-icon layers. */
    private static final GeneratedMaterialRenderer[] LAYERS = { new UniversiumRenderer(),
        new CosmicNeutroniumRenderer(), new InfinityRenderer() };

    /** Window length range: 10..39 client ticks. */
    private static final int WINDOW_MIN_TICKS = 10;
    private static final int WINDOW_RANDOM_TICKS = 30;

    /** Per-window chances: additive burst, second halo, glitch burst. */
    private static final float BURST_CHANCE = 0.35F;
    private static final float SECOND_HALO_CHANCE = 0.5F;
    private static final float GLITCH_CHANCE = 0.6F;

    /** Burst length in ticks; the ghost offset re-rolls every {@value #GLITCH_JITTER_TICKS}. */
    private static final int GLITCH_LENGTH_TICKS = 8;
    private static final int GLITCH_JITTER_TICKS = 2;

    /** Tint gain: two multiplied hues would come out too dark. */
    private static final float TINT_GAIN = 1.35F;

    /** Per-session randomness for the playback rolls and the hue cycles. */
    private final Random playback = new Random();

    private final int gaiaPeriod = 140 + playback.nextInt(120);
    private final int rainbowPeriod = 70 + playback.nextInt(60);
    private final int gaiaPhase = playback.nextInt(180);
    private final int rainbowPhase = playback.nextInt(90);

    // region Current window, rolled by rollWindow()

    /** Window end tick, or -1 before the first roll. */
    private int windowEndTick = -1;
    private int windowLayerIndex;
    private int previousLayerIndex = -1;
    private boolean windowBurst;
    private boolean windowSecondHalo;

    /** Glitch burst start tick, or {@link Integer#MIN_VALUE} for none. */
    private int glitchStartTick = Integer.MIN_VALUE;

    private double offsetRed;
    private double offsetCyan;
    private int lastJitterTick = Integer.MIN_VALUE;

    // endregion

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        int tick = (int) GTMod.clientProxy()
            .getAnimationRenderTicks();
        rollWindow(tick);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        // Disabled on request: the permanent Transcendent Metal tumble; re-enable by uncommenting this call.
        // applySpin(type);

        // Halos behind the icon (GT's order).
        renderHalos(type);

        // The tinted base icon.
        super.renderItem(type, stack, data);

        // Pulse + glitch ghosts on top.
        renderSimultaneous(type, stack, tick);

        // The window's whole-icon layer above everything.
        renderLayer(type, stack, data);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    @Override
    protected void renderRegularItem(ItemRenderType type, ItemStack stack, IIcon icon, boolean shouldModulateColor) {
        if (shouldModulateColor) {
            float[] tint = mixedTint(rgbaOf(stack));
            GL11.glColor4f(tint[0], tint[1], tint[2], 1.0F);
        }
        ItemRenderUtil.renderItem(type, icon);
    }

    /** Rolls the next window: layer (never the one that just played), length, and the burst / halo / glitch flips. */
    private void rollWindow(int tick) {
        if (tick < windowEndTick) return;

        int index = playback.nextInt(LAYERS.length);
        if (LAYERS.length > 1 && index == previousLayerIndex) {
            // Skip the layer that just played: no effect twice in a row.
            index = (index + 1 + playback.nextInt(LAYERS.length - 1)) % LAYERS.length;
        }
        previousLayerIndex = index;
        windowLayerIndex = index;

        windowBurst = playback.nextFloat() < BURST_CHANCE;
        windowSecondHalo = playback.nextFloat() < SECOND_HALO_CHANCE;

        int length = WINDOW_MIN_TICKS + playback.nextInt(WINDOW_RANDOM_TICKS);
        windowEndTick = tick + length;
        glitchStartTick = playback.nextFloat() < GLITCH_CHANCE
            ? tick + playback.nextInt(Math.max(1, length - GLITCH_LENGTH_TICKS + 1))
            : Integer.MIN_VALUE;
    }

    /** GT's {@code TranscendentMetalRenderer} tumble + a slower second axis; unused, see {@link #renderItem}. */
    private static void applySpin(ItemRenderType type) {
        float ticks = GTMod.clientProxy()
            .getAnimationRenderTicks();
        boolean inventory = type == IItemRenderer.ItemRenderType.INVENTORY;

        if (RenderItem.renderInFrame) {
            GL11.glTranslatef(0.0F, 0.0F, -0.5F);
        }
        if (inventory) {
            GL11.glTranslatef(8.0F, 8.0F, 0.0F);
        } else {
            GL11.glTranslatef(0.5F, 0.5F, 0.0F);
        }

        GL11.glRotatef((ticks * 3.5F) % 360.0F, 0.3F, 0.5F, 0.2F);
        GL11.glRotatef((ticks * 1.9F) % 360.0F, -0.2F, 0.85F, 0.35F);
        GL11.glRotatef(180.0F, 0.5F, 0.0F, 0.0F);

        if (inventory) {
            GL11.glTranslatef(-8.0F, -8.0F, 0.0F);
        } else {
            GL11.glTranslatef(-0.5F, -0.5F, 0.0F);
        }
        GL11.glTranslatef(0.0F, 0.0F, 0.03125F);
    }

    /**
     * Halos: Infinity's always (as GT draws for Infinity itself), CosmicNeutronium's only in the windows whose coin
     * flip turned it on.
     */
    private void renderHalos(ItemRenderType type) {
        if (type != IItemRenderer.ItemRenderType.INVENTORY) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        InfinityRenderer.renderHalo();
        if (windowSecondHalo) {
            CosmicNeutroniumRenderer.renderHalo(type);
        }
        GL11.glPopAttrib();
    }

    /**
     * Pulse every frame (like Infinity's own items); glitch ghosts only in the rolled burst, offset refreshed every
     * {@value #GLITCH_JITTER_TICKS} ticks.
     */
    private void renderSimultaneous(ItemRenderType type, ItemStack stack, int tick) {
        if (type != IItemRenderer.ItemRenderType.INVENTORY) return;

        IIcon icon = iconOf(stack, 0);
        IIcon overlay = overlayOf(stack, 0);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        InfinityRenderer.renderPulse(icon, overlay);

        if (isGlitching(tick)) {
            jitterOffsets(tick);
            GlitchEffectRenderer.applyRedGlitchEffect(type, true, offsetRed, icon, overlay);
            GlitchEffectRenderer.applyCyanGlitchEffect(type, true, offsetCyan, icon, overlay);
        }

        GL11.glPopAttrib();
    }

    /**
     * The window's whole-icon layer over the base, so it merges with the chaos icon instead of replacing it; additive
     * windows are the "everything at once" bursts.
     */
    private void renderLayer(ItemRenderType type, ItemStack stack, Object... data) {
        GeneratedMaterialRenderer layer = LAYERS[windowLayerIndex];

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, windowBurst ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        layer.renderItem(type, stack, data);
        GL11.glPopAttrib();
    }

    /** The material's light grey × the mixed Gaia/Rainbow hues, each on its own random period and phase. */
    private float[] mixedTint(short[] rgba) {
        float ticks = GTMod.clientProxy()
            .getAnimationRenderTicks();
        Color gaia = Color.getHSBColor(((ticks + gaiaPhase) % gaiaPeriod) / (float) gaiaPeriod, 0.40F, 0.95F);
        Color rainbow = Color
            .getHSBColor(((ticks + rainbowPhase) % rainbowPeriod) / (float) rainbowPeriod, 0.30F, 0.90F);

        float hueR = (gaia.getRed() + rainbow.getRed()) / (2.0F * 255.0F);
        float hueG = (gaia.getGreen() + rainbow.getGreen()) / (2.0F * 255.0F);
        float hueB = (gaia.getBlue() + rainbow.getBlue()) / (2.0F * 255.0F);

        return new float[] { clamp01(rgba[0] / 255.0F * hueR * TINT_GAIN), clamp01(rgba[1] / 255.0F * hueG * TINT_GAIN),
            clamp01(rgba[2] / 255.0F * hueB * TINT_GAIN) };
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    private static short[] rgbaOf(ItemStack stack) {
        if (stack.getItem() instanceof IGT_ItemWithMaterialRenderer item) {
            short[] rgba = item.getRGBa(stack);
            if (rgba != null && rgba.length >= 3) return rgba;
        }
        return new short[] { 255, 255, 255, 255 };
    }

    private static IIcon iconOf(ItemStack stack, int pass) {
        if (stack.getItem() instanceof IGT_ItemWithMaterialRenderer item) {
            return item.getIcon(stack.getItemDamage(), pass);
        }
        return stack.getIconIndex();
    }

    private static IIcon overlayOf(ItemStack stack, int pass) {
        if (stack.getItem() instanceof IGT_ItemWithMaterialRenderer item) {
            return item.getOverlayIcon(stack.getItemDamage(), pass);
        }
        return null;
    }

    private boolean isGlitching(int tick) {
        return glitchStartTick != Integer.MIN_VALUE && tick >= glitchStartTick
            && tick - glitchStartTick < GLITCH_LENGTH_TICKS;
    }

    /** GT's own jitter: a new random ghost offset every few ticks. */
    private void jitterOffsets(int tick) {
        if (lastJitterTick != Integer.MIN_VALUE && tick - lastJitterTick < GLITCH_JITTER_TICKS) return;
        lastJitterTick = tick;
        offsetRed = playback.nextDouble() * 1.7D * Math.signum(playback.nextGaussian());
        offsetCyan = playback.nextDouble() * 1.7D * Math.signum(playback.nextGaussian());
    }
}
