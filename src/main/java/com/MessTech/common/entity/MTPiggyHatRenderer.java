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
 * A piggy in the helmet slot is drawn on the head; a Transcendent Metal one also tumbles the whole model.
 * <p>
 * <b>The hat.</b> 1.7.10 cannot draw an arbitrary item on the head: {@code RenderPlayer#renderEquippedItems}
 * handles only {@code ItemBlock} blocks (the pumpkin) and skulls, so the slot takes a plain item but draws nothing.
 * <p>
 * Hook: {@link RenderPlayerEvent.Specials.Post}, at the end of {@code RenderPlayer#renderEquippedItems}, the model
 * still on the matrix stack. {@code ModelBiped#bipedHead} carries the head pose, so {@code ModelRenderer#postRender}
 * on it (as vanilla does before the pumpkin) puts the pig on the head, not in the world: yaw, pitch, crouch, riding,
 * death pose come free. {@link MTDynamicItemHelper#renderOnHead} draws the worn stack's own look, effect, animation.
 * <p>
 * <b>The tumble.</b> {@link MTDynamicItemHelper.Style#TUMBLE} turns it about the oblique axis (0.3, 0.5, 0.2) by 3.5
 * degrees per client tick, and a worn pig puts that turn on the whole model too. It must wrap the render:
 * {@link RenderPlayerEvent.Pre} pushes, {@link RenderPlayerEvent.Post} pops ({@link #onRenderWorldLast} nets the
 * cancelled-{@code Pre} leak). Both tumbles add up on the head.
 */
@SideOnly(Side.CLIENT)
public final class MTPiggyHatRenderer {

    /**
     * Helmet slot, {@code InventoryPlayer#armorItemInSlot} numbering (0 boots ... 3 helmet), vanilla's hat code
     * reads; not {@link com.MessTech.common.items.MTItemPiggy#HELMET_ARMOR_SLOT} ({@code Item#isValidArmor} numbering).
     */
    private static final int HELMET_ARMOR_ITEM_SLOT = 3;

    /** Height of the drawn pig, in blocks (head width = 0.5). */
    private static final float SCALE = 0.5F;

    /**
     * Height of the model head, in blocks: {@code ModelBiped} grows its head box 8 px up from the {@code postRender}
     * rotation point (the neck), so the pig on top moves up by this, along {@code -Y} (a pixel is 0.0625 = 1/16).
     */
    private static final float HEAD_HEIGHT = 8.0F * 0.0625F;

    /**
     * The {@code 0.9375} {@code RenderPlayer#preRenderCallback} scales the player model by: how many blocks one unit
     * here is worth. Undone so {@link #SCALE} and the sprite stay in blocks - otherwise the space is the usual
     * "one unit is one block".
     */
    private static final float PLAYER_MODEL_SCALE = 0.9375F;

    /**
     * Tumble pivot above the feet: half of the 1.8 blocks a player is tall - the middle of the body, as the pig turns
     * about its sprite's middle.
     */
    private static final double TUMBLE_PIVOT_Y = 0.9D;

    /**
     * The player whose render carries the tumble transform, between a {@link RenderPlayerEvent.Pre} and its Post;
     * null while none is tumbled.
     */
    private EntityPlayer tumbled;

    private MTPiggyHatRenderer() {}

    /** Registers the handler; called by the client proxy in preInit. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTPiggyHatRenderer());
    }

    /** @return the worn piggy in the helmet slot, or null. */
    private static ItemStack wornPiggy(EntityPlayer player) {
        if (player.isPlayerSleeping() || player.isDead || player.isInvisible()) return null;

        ItemStack helmet = player.inventory.armorItemInSlot(HELMET_ARMOR_ITEM_SLOT);
        return helmet != null && helmet.getItem() == MTItems.piggy ? helmet : null;
    }

    // region Hat

    @SubscribeEvent
    public void onRenderPlayerSpecialsPost(RenderPlayerEvent.Specials.Post event) {
        ItemStack helmet = wornPiggy(event.entityPlayer);
        if (helmet == null) return;

        GL11.glPushMatrix();
        // Culling/blending are whatever the player renderer left: restore them.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        // Head angles = its yaw and pitch, so the pig turns and tilts with it; vanilla draws pumpkin/skull here.
        event.renderer.modelBipedMain.bipedHead.postRender(0.0625F);
        GL11.glTranslatef(0.0F, -HEAD_HEIGHT, 0.0F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Single quad: otherwise invisible from behind, i.e. in the third-person camera.
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
     * Turns the wearer's model about its middle, by the angle its piggy is turned by. LOWEST priority so it runs
     * last: a {@code Pre} another handler cancels is skipped, so nothing is pushed for a render that will not happen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!Config.general.PIGGY_TUMBLES_WEARER || event.isCanceled()) return;

        EntityPlayer player = event.entityPlayer;
        ItemStack helmet = wornPiggy(player);
        if (helmet == null || !MTDynamicItemHelper.isTumbling(helmet)) return;

        // Posted before RenderPlayer#doRender places the model: matrix still the camera's (origin at the eye), model
        // landing at the interpolated entity position minus the view position. Pivot: that point lifted to the body's
        // middle (TUMBLE_PIVOT_Y).
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
     * Net for the one way the pair above breaks: another mod cancelling {@link RenderPlayerEvent.Pre} after this
     * handler pushed - {@code Post} is not posted for a cancelled {@code Pre}, so one push a frame would leak; at
     * world render end it is still the top of the stack, so popping here is exact.
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (tumbled == null) return;
        GL11.glPopMatrix();
        tumbled = null;
    }

    // endregion
}
