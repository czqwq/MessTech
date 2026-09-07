package com.MessTech.common.item;

import static gregtech.loaders.ExtraIcons.circuitComponentOverlay;
import static net.minecraftforge.client.IItemRenderer.ItemRenderType.INVENTORY;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.ItemRenderUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;

/**
 * Inventory renderer for {@link MTNACComponentItem}. Mirrors GT5U's fake CircuitComponent renderer:
 * unprocessed components show their real item plus the NAC overlay, processed components show their
 * dedicated processed texture.
 */
@SideOnly(Side.CLIENT)
public class MTNACComponentItemRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == INVENTORY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        CircuitComponent cc = CircuitComponent.tryGetFromFakeStack(item);
        if (cc == null) return;

        if (!cc.isProcessed) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

            GL11.glPushMatrix();
            GL11.glTranslatef(2.4f, 2.4f, 0);
            GL11.glScalef(0.7f, 0.7f, 0);

            ItemStack realItem = cc.realComponent.get();
            if (realItem == null) {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
                return;
            }

            if (realItem.getItem() instanceof ItemBlock) {
                RenderHelper.enableGUIStandardItemLighting();
                RenderItem.getInstance()
                    .renderItemIntoGUI(
                        Minecraft.getMinecraft().fontRenderer,
                        Minecraft.getMinecraft().renderEngine,
                        realItem,
                        0,
                        0,
                        false);
                RenderHelper.disableStandardItemLighting();
            } else {
                IItemRenderer baseRenderer = MinecraftForgeClient.getItemRenderer(realItem, INVENTORY);
                if (baseRenderer != null) {
                    baseRenderer.renderItem(type, realItem, data);
                } else if (realItem.getItem() != null) {
                    int coloration = realItem.getItem()
                        .getColorFromItemStack(realItem, 0);
                    int r = (coloration & 0xFF0000) >> 16;
                    int g = (coloration & 0xFF00) >> 8;
                    int b = (coloration & 0xFF);

                    GL11.glColor3f(r / 255F, g / 255F, b / 255F);
                    ItemRenderUtil.renderItem(
                        type,
                        realItem.getItem()
                            .getIcon(realItem, 0));
                }
            }

            GL11.glPopMatrix();

            GL11.glPushMatrix();
            GL11.glColor3f(1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            GL11.glTranslatef(0f, 0f, 2f);
            ItemRenderUtil.renderItem(type, circuitComponentOverlay);
            GL11.glDisable(GL11.GL_BLEND);

            GL11.glPopMatrix();

            RenderHelper.enableGUIStandardItemLighting();
            GL11.glPopAttrib();
        } else {
            ItemRenderUtil.renderItem(type, item.getIconIndex());
        }
    }
}
