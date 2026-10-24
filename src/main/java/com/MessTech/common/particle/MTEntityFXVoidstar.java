package com.MessTech.common.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One particle of the Voidstar Piercer's effects: a camera facing quad of one item atlas sprite, with a lifetime,
 * a colour and a fade.
 * <p>
 * Layer {@link #getFXLayer() 2}, the item atlas - the one atlas a mod can add its own sprite to without a stitch
 * handler: the bow's {@code registerIcons} registers the spark ({@code particle/voidstarSpark}) next to its icons,
 * and {@code EffectRenderer#renderParticles} binds that atlas for layer 2. Layer 0, the vanilla particle sheet,
 * cannot take a custom sprite at all: {@code EntityFX#setParticleIcon} throws.
 * <p>
 * Full bright ({@link #getBrightnessForRender}: {@code 0xF000F0}, as vanilla's spell particles), so trail and bursts
 * glow in an unlit cave instead of turning grey.
 */
@SideOnly(Side.CLIENT)
public final class MTEntityFXVoidstar extends EntityFX {

    /** Packed lightmap full daylight; see the class comment. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final float initialScale;
    private final float initialAlpha;
    private final int lifetime;

    /**
     * Position {@code x}/{@code y}/{@code z}, velocity {@code motionX}/{@code motionY}/{@code motionZ} in blocks per
     * tick, colour {@code red}/{@code green}/{@code blue} 0..1.
     *
     * @param world    client world
     * @param icon     sprite of the item atlas
     * @param scale    1 = a 0.2 block quad
     * @param lifetime ticks lived, i.e. the fade
     * @param alpha    starting opacity, 0..1
     */
    public MTEntityFXVoidstar(World world, double x, double y, double z, double motionX, double motionY, double motionZ,
        IIcon icon, float scale, int lifetime, float red, float green, float blue, float alpha) {
        super(world, x, y, z);

        setParticleIcon(icon);
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.particleScale = scale;
        this.initialScale = scale;
        this.particleMaxAge = Math.max(1, lifetime);
        this.lifetime = this.particleMaxAge;
        this.particleGravity = 0.0F;
        this.particleRed = red;
        this.particleGreen = green;
        this.particleBlue = blue;
        this.particleAlpha = alpha;
        this.initialAlpha = alpha;
        // Drawn in mid-air: colliding with the blocks behind it would stop it dead.
        this.noClip = true;
    }

    @Override
    public int getFXLayer() {
        return 2;
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        return FULL_BRIGHT;
    }

    /**
     * Ages and fades: the colour holds for the first third of the life, then fades while the sprite shrinks slightly
     * - a spark burning down, not blinking off.
     */
    @Override
    public void onUpdate() {
        super.onUpdate();

        float life = (float) this.particleAge / (float) this.lifetime;
        if (life <= 0.33F) {
            this.particleAlpha = this.initialAlpha;
        } else {
            float fade = (life - 0.33F) / 0.67F;
            this.particleAlpha = this.initialAlpha * (1.0F - fade);
        }
        this.particleScale = this.initialScale * (1.0F - 0.35F * life);
    }
}
