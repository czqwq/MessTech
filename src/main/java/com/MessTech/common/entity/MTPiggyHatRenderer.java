package com.MessTech.common.entity;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.MessTech.common.items.MTItems;
import com.MessTech.common.util.MTDynamicItemHelper;
import com.MessTech.init.Config;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What a piggy in the helmet slot does to the player who wears it: it is drawn standing on their head, and a
 * Transcendent Metal one tumbles the whole model along with itself.
 * <p>
 * <b>The hat.</b> 1.7.10 has no renderer for an arbitrary item on the head: vanillas own hat code
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
 * <p>
 * <b>The tumble.</b> A Transcendent Metal pig is not a still picture - {@link MTDynamicItemHelper.Style#TUMBLE} turns
 * it about the oblique axis (0.3, 0.5, 0.2) by 3.5 degrees per client tick - and while such a pig is on the head the
 * same turn is put on the whole player model, so the wearer tumbles with it. That transform has to wrap the render,
 * which is what {@link RenderPlayerEvent.Pre} and {@link RenderPlayerEvent.Post} are for: the first one pushes it
 * and the second one pops it. The two are a pair because {@code Post} is only posted for a {@code Pre} that was not
 * cancelled; {@link #onRenderWorldLast} is the net under the one case that breaks that - another mod cancelling
 * {@code Pre} after this handler ran - so a matrix can never leak.
 * <p>
 * Note that the pig keeps its own tumble as well, so on the head the two add up. Pinning the pig to the head instead
 * (dropping its own transform while the player tumbles) is a one line change in
 * {@link MTDynamicItemHelper#renderOnHead} if the pair is wanted rigid.
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

    /**
     * How far above the feet of the model the tumble of the player turns: half of the 1.8 blocks a player is tall, so
     * the model turns about the middle of its own body - the same thing the pig does about the middle of the sprite.
     */
    private static final double TUMBLE_PIVOT_Y = 0.9D;

    /**
     * The player whose render currently carries the tumble transform, i.e. the one between a
     * {@link RenderPlayerEvent.Pre} and its {@link RenderPlayerEvent.Post}. Null while no player is tumbled.
     */
    private EntityPlayer tumbled;

    private MTPiggyHatRenderer() {}

    /** Registers the handler; called by the client proxy during preInit. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTPiggyHatRenderer());
    }

    /**
     * @return the piggy the player wears in the helmet slot, or null when they wear none or are not drawn at all.
     */
    private static ItemStack wornPiggy(EntityPlayer player) {
        if (player.isPlayerSleeping() || player.isDead || player.isInvisible()) return null;

        ItemStack helmet = player.inventory.armorItemInSlot(HELMET_ARMOR_ITEM_SLOT);
        return helmet != null && helmet.getItem() == MTItems.piggy ? helmet : null;
    }

    // region Hat

    @SubscribeEvent
    public void onRenderPlayerSpecialsPost(RenderPlayerEvent.Specials.Post event) {
        // Nothing to draw unless the hat slot holds a piggy; the effect of that very stack decides the look, so the
        // one on the head looks like the one in the inventory.
        ItemStack helmet = wornPiggy(event.entityPlayer);
        if (helmet == null) return;

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

        MTDynamicItemHelper.renderOnHead(event.entityPlayer, helmet);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    // endregion

    // region Tumble

    /**
     * Turns the model of the wearer about its own middle, by the very angle the pig on its head is turned by.
     * <p>
     * LOWEST priority for a reason: it is the last handler to run, so a {@code Pre} another handler cancels is
     * already cancelled here and no matrix is pushed for a render that will not happen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!Config.PIGGY_TUMBLES_WEARER || event.isCanceled()) return;

        EntityPlayer player = event.entityPlayer;
        ItemStack helmet = wornPiggy(player);
        if (helmet == null || !MTDynamicItemHelper.isTumbling(helmet)) return;

        // The event is posted before RenderPlayer#doRender puts the model anywhere, so the matrix is still the one
        // the camera left behind: its origin is the eye and the model is about to be placed at the interpolated
        // entity position minus the view position. Those are the numbers the renderer gets, and the pivot is that
        // point lifted to the middle of the body (TUMBLE_PIVOT_Y).
        float partialTicks = event.partialRenderTick;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - RenderManager.renderPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
            - RenderManager.renderPosY
            - player.yOffset
            + TUMBLE_PIVOT_Y;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - RenderManager.renderPosZ;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(
            MTDynamicItemHelper.tumbleAngle(),
            MTDynamicItemHelper.TUMBLE_AXIS_X,
            MTDynamicItemHelper.TUMBLE_AXIS_Y,
            MTDynamicItemHelper.TUMBLE_AXIS_Z);
        GL11.glTranslated(-x, -y, -z);
        tumbled = player;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (tumbled == null) return;
        GL11.glPopMatrix();
        tumbled = null;
    }

    /**
     * The net under the one way the pair above can break: another mod cancelling {@link RenderPlayerEvent.Pre} after
     * this handler already pushed. {@code Post} is not posted for a cancelled {@code Pre}, so the transform would
     * stay on the matrix stack for the rest of the frame and one more would leak every frame after that. At the end
     * of the world render that leaked push is still the top of the stack, which is what makes undoing it here exact.
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (tumbled == null) return;
        GL11.glPopMatrix();
        tumbled = null;
    }

    // endregion
}
