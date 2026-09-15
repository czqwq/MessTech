package com.MessTech.common.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.MessTech.common.items.MTItems;
import com.MessTech.common.util.MTDynamicItemHelper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the piggy a player wears in the helmet slot standing on their head.
 * <p>
 * 1.7.10 has no renderer for an arbitrary item on the head: vanillas own hat code
 * ({@code RenderPlayer#renderEquippedItems}) only handles {@code ItemBlock} blocks (the pumpkin) and skulls, so a
 * plain item like the piggy is accepted by the slot but never drawn. This handler fills that gap from the outside, in
 * exactly the space vanilla draws those two in.
 * <p>
 * The hook is {@link RenderPlayerEvent.Specials.Post}, which is posted at the end of
 * {@code RenderPlayer#renderEquippedItems}, i.e. while the model of the player is still on the matrix stack and the
 * head item of vanilla is drawn. From there the head part of the model puts the sprite in place:
 * {@code ModelBiped#bipedHead} carries the whole pose of the head - its angles are the head's own yaw and pitch - so
 * calling {@code ModelRenderer#postRender} on it, the same call vanilla makes before it draws the pumpkin or the
 * skull, attaches the pig to the head instead of the world. Yaw, pitch, the crouch, riding, the death pose: all of it
 * comes along without a single number worked out by hand, and the pig ends up on the top of the head, the spot the
 * pumpkin and the skull are built around.
 * <p>
 * What is drawn there is {@link MTDynamicItemHelper#renderOnHead}: the look of the worn stack, in its own effect and
 * with its own animation, so the pig on the head is the pig in the inventory.
 */
@SideOnly(Side.CLIENT)
public final class MTPiggyHatRenderer {

    /**
     * The helmet slot as {@code InventoryPlayer#armorItemInSlot} numbers it (0 boots ... 3 helmet), i.e. the same
     * slot vanilla's own hat code reads for the pumpkin. Not to be confused with
     * {@link com.MessTech.common.items.MTItemPiggy#HELMET_ARMOR_SLOT}, which uses the numbering of
     * {@code Item#isValidArmor}.
     */
    private static final int HELMET_ARMOR_ITEM_SLOT = 3;

    /** Height of the drawn pig, in blocks (the head of the model is half a block wide). */
    private static final float SCALE = 0.5F;

    /**
     * Height of the head of the model, in blocks: {@code ModelBiped} grows the box of its head 8 px up from the
     * rotation point that {@code postRender} puts the origin on, so the origin is the neck and the pig - which stands
     * on top of the head - has to be moved up by exactly this. In the model space that is {@code -Y}, and the
     * {@code 0.0625} of {@code postRender} means a pixel is a sixteenth of a unit.
     */
    private static final float HEAD_HEIGHT = 8.0F * 0.0625F;

    /**
     * The scale {@code RenderPlayer#preRenderCallback} puts on the whole player model, i.e. how many blocks one unit
     * of the space this handler draws in is worth. Undone here so that {@link #SCALE} and the sprite drawing stay
     * blocks: the {@code 0.9375} of the player renderer is the only thing that makes its model space differ from the
     * usual "one unit is one block".
     */
    private static final float PLAYER_MODEL_SCALE = 0.9375F;

    private MTPiggyHatRenderer() {}

    /** Registers the handler; called by the client proxy during preInit. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTPiggyHatRenderer());
    }

    @SubscribeEvent
    public void onRenderPlayerSpecialsPost(RenderPlayerEvent.Specials.Post event) {
        EntityPlayer player = event.entityPlayer;
        if (player.isPlayerSleeping() || player.isDead || player.isInvisible()) return;

        // Nothing to draw unless the hat slot holds a piggy; the effect of that very stack decides the look, so the
        // one on the head looks like the one in the inventory.
        ItemStack helmet = player.inventory.armorItemInSlot(HELMET_ARMOR_ITEM_SLOT);
        if (helmet == null || helmet.getItem() != MTItems.piggy) return;

        GL11.glPushMatrix();
        // Culling and blending are whatever the player renderer left behind, so save and restore all of it.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        // The head of the model: its angles are the head's own yaw and pitch, so the pig turns and tilts with the
        // head. Vanilla draws the pumpkin and the skull from exactly this point on.
        event.renderer.modelBipedMain.bipedHead.postRender(0.0625F);
        // ... and up onto the top of the head: the origin of the head part is its neck (see HEAD_HEIGHT).
        GL11.glTranslatef(0.0F, -HEAD_HEIGHT, 0.0F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // The sprite is a single quad: without this it would be invisible from behind, i.e. for the player looking at
        // their own pig in the third person camera.
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glScalef(SCALE / PLAYER_MODEL_SCALE, SCALE / PLAYER_MODEL_SCALE, SCALE / PLAYER_MODEL_SCALE);

        MTDynamicItemHelper.renderOnHead(player, helmet);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
