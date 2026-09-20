package com.MessTech.common.util;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;

/**
 * Draws the moving half of {@link MTTranscendentMetalText}: the line itself, turned about its own centre with the
 * exact transform GT5U gives its Transcendent Metal item - the same oblique axis (0.3, 0.5, 0.2), the same 3.5
 * degrees per client tick and the same {@code getAnimationRenderTicks()} clock, one way round without stopping or
 * jumping back - so the word turns in step with an item lying next to it.
 * <p>
 * The renderer <b>replaces</b> the text ({@link #replacesText()}): the turning copy is the only copy. A second,
 * still copy underneath it would be the thing the player actually reads while the metal turns across it.
 * <p>
 * The one addition a flat run of text needs is the back face: an item quad has two sides, so GT5U can turn it
 * through a whole circle and the sprite still reads from behind. Letters would not - past the edge-on pose they
 * come out mirrored. {@link MTTranscendentMetalText#showsBack} says when the plate faces away, and the glyphs are
 * mirrored back for that half, so they read the same way round on both sides. The switch happens exactly where the
 * plate is edge-on and has no visible area, so it is never seen: the turn itself stays the item's.
 */
@SideOnly(Side.CLIENT)
public final class MTTranscendentMetalTextRenderer implements MTTextRenderer {

    /** The glint: additive, so it brightens the glyphs it covers instead of replacing them. */
    private static final int GLINT_COLOUR = 0x5AFFFFFF;

    /** How far the glint copy is offset, in pixels, so the highlight sits on one edge of the letters. */
    private static final float GLINT_OFFSET = 1.0F;

    /** Half the height of a capital: the vertical centre of the 8 pixel tall font, where the plate turns. */
    private static final float HALF_LINE = 4.0F;

    @Override
    public boolean replacesText() {
        return true;
    }

    @Override
    public void draw(FontRenderer font, String line, int x, int y, long millis) {
        if (line == null || line.isEmpty()) return;

        float width = font.getStringWidth(line);
        if (width <= 0.0F) return;

        float pivotX = x + width / 2.0F;
        float pivotY = y + HALF_LINE;
        float angle = MTTranscendentMetalText.spinFor(
            GTMod.clientProxy()
                .getAnimationRenderTicks());

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        // The flipped half turns the quads around, so culling must not hide them.
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glTranslatef(pivotX, pivotY, 0.0F);
        // GT5U's own transform, verbatim: the item renderer passes the same three axis numbers and the same angle
        // to glRotatef, which is what makes the text turn exactly like the item.
        GL11.glRotatef(
            angle,
            MTTranscendentMetalText.ROTATION_AXIS_X,
            MTTranscendentMetalText.ROTATION_AXIS_Y,
            MTTranscendentMetalText.ROTATION_AXIS_Z);
        if (MTTranscendentMetalText.showsBack(angle)) {
            GL11.glScalef(-1.0F, 1.0F, 1.0F);
        }
        GL11.glTranslatef(-pivotX, -pivotY, 0.0F);

        GL11.glEnable(GL11.GL_BLEND);

        // The metal itself: the frame's dark grey - grey - white band, with the font's shadow for depth.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        font.drawStringWithShadow(line, x, y, -1);

        // The glint: the same glyphs a pixel further along, added, so the turned plate catches the light.
        GL11.glTranslatef(GLINT_OFFSET, GLINT_OFFSET, 0.0F);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        font.drawString(line, x, y, GLINT_COLOUR);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
