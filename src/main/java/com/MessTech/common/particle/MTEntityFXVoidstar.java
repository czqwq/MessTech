package com.MessTech.common.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One particle of the Voidstar Piercer's effects: a single sprite of the item atlas, drawn as a camera facing quad
 * with a lifetime, a colour and a fade.
 * <p>
 * The layer is {@link #getFXLayer() 2}, the item atlas, because that is the one atlas a mod can put its own sprite
 * into without a texture stitch handler of its own - the bow's {@code registerIcons} registers the spark sprite
 * ({@code particle/voidstarSpark}) next to its own icons, and {@code EffectRenderer#renderParticles} binds exactly
 * that atlas for layer 2. Layer 0, the vanilla particle sheet, cannot take a custom sprite at all:
 * {@code EntityFX#setParticleIcon} throws for it.
 * <p>
 * The particles are full bright ({@link #getBrightnessForRender}, the same {@code 0xF000F0} vanilla's spell
 * particles return), so the trail and the bursts glow in an unlit cave instead of turning into grey dots.
 */
@SideOnly(Side.CLIENT)
public final class MTEntityFXVoidstar extends EntityFX {

    /** Full daylight brightness as a packed lightmap value; see the class comment. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final float initialScale;
    private final float initialAlpha;
    private final int lifetime;

    /**
     * @param world    the client world
     * @param x        position of the particle
     * @param y        see {@code x}
     * @param z        see {@code x}
     * @param motionX  velocity, in blocks per tick
     * @param motionY  see {@code motionX}
     * @param motionZ  see {@code motionX}
     * @param icon     the sprite to draw, from the item atlas
     * @param scale    size multiplier: a scale of 1 draws a quad {@code 0.2} blocks wide
     * @param lifetime how many ticks the particle lives, i.e. how long it fades
     * @param red      colour of the sprite, 0..1
     * @param green    see {@code red}
     * @param blue     see {@code red}
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
        // A magic circle is drawn in the air in front of the player; a particle that collides with the blocks
        // behind it would stop dead on them.
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
     * Ages and fades the particle: the colour holds for the first third of its life and then fades out, while the
     * sprite shrinks slightly, which reads as a spark burning down rather than blinking off.
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
