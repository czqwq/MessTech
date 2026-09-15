package com.MessTech.common.items;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.ItemRenderUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;

/**
 * Item renderer for the fuel rods that copies GT5U's Transcendent Metal preview: the flat item quad is
 * rotated around the oblique axis (0.3, 0.5, 0.2) by 3.5 degrees per client tick before being drawn with
 * {@link ItemRenderer#renderItemIn2D}. The angle comes from {@link GTMod#clientProxy()}'s
 * {@code getAnimationRenderTicks()}, so the tumble pauses together with the game and looks identical to
 * Transcendent Metal in the inventory, NEI preview, item frames, drops and first/third person hand.
 */
@SideOnly(Side.CLIENT)
public class MTFuelRodItemRenderer implements IItemRenderer {

    private static final float ROTATION_PER_TICK = 3.5F;
    private static final float ROTATION_AXIS_X = 0.3F;
    private static final float ROTATION_AXIS_Y = 0.5F;
    private static final float ROTATION_AXIS_Z = 0.2F;

    /** Registers one shared renderer for every burnable and depleted rod in {@link MTItems}. */
    public static void registerItemRenderers() {
        MTFuelRodItemRenderer renderer = new MTFuelRodItemRenderer();
        MinecraftForgeClient.registerItemRenderer(MTItems.rodTranscendentMetal, renderer);
        MinecraftForgeClient.registerItemRenderer(MTItems.rodTranscendentMetal2, renderer);
        MinecraftForgeClient.registerItemRenderer(MTItems.rodTranscendentMetal4, renderer);
        MinecraftForgeClient.registerItemRenderer(MTItems.rodTranscendentMetalDepleted, renderer);
        MinecraftForgeClient.registerItemRenderer(MTItems.rodTranscendentMetalDepleted2, renderer);
        MinecraftForgeClient.registerItemRenderer(MTItems.rodTranscendentMetalDepleted4, renderer);
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY && helper == ItemRendererHelper.ENTITY_BOBBING
            || (helper == ItemRendererHelper.ENTITY_ROTATION && Minecraft.getMinecraft().gameSettings.fancyGraphics);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (stack == null || stack.getItem() == null) return;
        IIcon icon = stack.getItem()
            .getIcon(stack, 0);
        if (icon == null) return;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

        // Same pipeline as GT's MetaGeneratedItemRenderer: apply the ENTITY standard transform first, then
        // pretend fancy graphics is on (magic numbers taken from vanilla RenderItem) when it is off.
        ItemRenderUtil.applyStandardItemTransform(type);
        if (type == ItemRenderType.ENTITY && !Minecraft.getMinecraft().gameSettings.fancyGraphics) {
            if (RenderItem.renderInFrame) {
                GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
            }
            GL11.glTranslatef(-0.5F, -0.25F, 0.0421875F);
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        applyRotation(type);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);

        boolean flipUv = type == ItemRenderType.INVENTORY;
        if (flipUv) {
            // The inventory render space is 1 unit = 1 GUI pixel; renderItemIn2D draws a 1x1 quad.
            GL11.glScalef(16F, 16F, 32F);
        }

        ItemRenderer.renderItemIn2D(
            Tessellator.instance,
            flipUv ? icon.getMinU() : icon.getMaxU(),
            flipUv ? icon.getMinV() : icon.getMaxV(),
            flipUv ? icon.getMaxU() : icon.getMinU(),
            flipUv ? icon.getMaxV() : icon.getMinV(),
            icon.getIconWidth(),
            icon.getIconHeight(),
            0.0625F);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /** Bit-for-bit copy of Transcendent Metal's rotation: pivot on the quad centre, tumble, then flip over. */
    private static void applyRotation(ItemRenderType type) {
        if (RenderItem.renderInFrame) {
            // Float in front of the item frame to avoid z-fighting with its border.
            GL11.glTranslatef(0.0F, 0.0F, -0.5F);
        }

        if (type == ItemRenderType.INVENTORY) {
            GL11.glTranslatef(8F, 8F, 0F);
        } else {
            GL11.glTranslatef(0.5F, 0.5F, 0.0F);
        }

        GL11.glRotatef(
            (GTMod.clientProxy()
                .getAnimationRenderTicks() * ROTATION_PER_TICK) % 360F,
            ROTATION_AXIS_X,
            ROTATION_AXIS_Y,
            ROTATION_AXIS_Z);

        GL11.glRotatef(180F, 0.5F, 0.0F, 0.0F);

        if (type == ItemRenderType.INVENTORY) {
            GL11.glTranslatef(-8F, -8F, 0F);
        } else {
            GL11.glTranslatef(-0.5F, -0.5F, 0.0F);
        }

        // Move the 2D quad onto the rotation axis (half of its thickness).
        GL11.glTranslatef(0.0F, 0.0F, 0.03125F);
    }
}
