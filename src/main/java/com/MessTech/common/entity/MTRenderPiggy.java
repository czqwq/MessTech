package com.MessTech.common.entity;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.MessTech.common.items.MTItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the thrown piggy as the item icon it was thrown with: always facing the camera and slowly spinning around
 * the view axis. Animated effects (the ones with a {@code .mcmeta} strip) animate on their own, the atlas does that
 * for every sprite.
 */
@SideOnly(Side.CLIENT)
public class MTRenderPiggy extends Render {

    /** Spin of the sprite around the view axis, in degrees per tick. */
    private static final float SPIN_SPEED = 24.0F;

    public MTRenderPiggy() {
        this.shadowSize = 0.15F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (!(entity instanceof MTEntityPiggy piggy)) return;

        IIcon icon = MTItems.piggy.getIconFromDamage(piggy.getEffect());
        if (icon == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(0.6F, 0.6F, 0.6F);
        bindEntityTexture(piggy);

        // Billboard first (the same two rotations the vanilla thrown item renderers use), then spin the sprite
        // around the view axis, which is what makes the throw look alive.
        GL11.glRotatef(180.0F - this.renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-this.renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef((piggy.ticksExisted + partialTicks) * SPIN_SPEED, 0.0F, 0.0F, 1.0F);

        float minU = icon.getMinU(), maxU = icon.getMaxU(), minV = icon.getMinV(), maxV = icon.getMaxV();
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 1.0F, 0.0F);
        tessellator.addVertexWithUV(-0.5D, -0.25D, 0.0D, minU, maxV);
        tessellator.addVertexWithUV(0.5D, -0.25D, 0.0D, maxU, maxV);
        tessellator.addVertexWithUV(0.5D, 0.75D, 0.0D, maxU, minV);
        tessellator.addVertexWithUV(-0.5D, 0.75D, 0.0D, minU, minV);
        tessellator.draw();

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        // The icon lives in the item atlas, which is exactly what the quad is textured with.
        return TextureMap.locationItemsTexture;
    }
}
