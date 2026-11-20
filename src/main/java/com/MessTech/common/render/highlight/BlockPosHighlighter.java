package com.MessTech.common.render.highlight;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Outlines a single block in the world, for the wireless NAC vacuum conveyor hatches' GUI: the player clicks the
 * highlight button to see where the linked counterpart sits, then the screen closes and the outline keeps blinking for
 * a while.
 * <p>
 * Ported from Applied Energistics 2's {@code appeng.client.render.highlighter.BlockPosHighlighter} (itself taken from
 * McJtyLib), without AE2's API types: AE2 stores {@code DimensionalCoord}s of every interface of a wireless network and
 * chat-reports "found"/"wrong dimension" per block, whereas a hatch has exactly one counterpart, so this holds a small
 * list of its own immutable positions and reports a single result.
 * <p>
 * The look is AE2's: a red 1x1x1 box outline drawn through walls, blinking with {@link HighlighterManager}'s 500 ms
 * phase, gone after a distance-scaled duration - AE2's {@code 500 ms * taxicab distance}, clamped to 3..30 seconds, so
 * a
 * nearby hatch stays visible while the player walks to it and a far one stays for half a minute. The caller needs
 * nothing but a client-side position (the GUI already syncs the link), so no packet is involved.
 */
@SideOnly(Side.CLIENT)
public final class BlockPosHighlighter implements IHighlighter {

    /** The one instance the static entry point and the manager share. */
    static final BlockPosHighlighter INSTANCE = new BlockPosHighlighter();

    /** Shortest time an outline stays, in milliseconds: AE2's 3 seconds. */
    private static final int MIN_TIME_MS = 3000;

    /** Longest time an outline stays, in milliseconds: AE2's 30 seconds. */
    private static final int MAX_TIME_MS = MIN_TIME_MS * 10;

    /** Outline time per block of taxicab distance, in milliseconds: AE2's 500 ms. */
    private static final int TIME_PER_BLOCK_MS = 500;

    /** Lang keys; the text and its colours live in the lang files (house style). */
    private static final String KEY_HIGHLIGHTED = "machine.wirelessvacuum.chat.highlighted";
    private static final String KEY_OTHER_DIMENSION = "machine.wirelessvacuum.chat.highlight.other_dim";

    /** AE2's highlight colour: pure red, fully opaque. */
    private static final float RED = 1.0F, GREEN = 0.0F, BLUE = 0.0F, ALPHA = 1.0F;

    /** AE2's outline thickness. */
    private static final float LINE_WIDTH = 3.0F;

    /** The currently highlighted blocks, all in the player's dimension when they were added. */
    private final List<DimPos> highlightedBlocks = new ArrayList<>();

    /** Wall-clock expiry of the current highlight; -1 while there is none. */
    private long expireHighlightTime = -1L;

    private BlockPosHighlighter() {}

    /**
     * Highlights the block at {@code dimension}/{@code x}/{@code y}/{@code z} and reports the result in chat: the
     * outline itself when the block is in the player's current dimension, AE2's "located in dimension ..." line
     * otherwise (that block cannot be drawn from here). Must be called on the client.
     * <p>
     * The previous highlight is replaced - one click asks for one outline.
     */
    public static void highlight(EntityPlayer player, int dimension, int x, int y, int z) {
        // A GUI normally has both, but a closing screen may race a world unload; there is nothing to chat to then.
        if (player == null || player.worldObj == null) return;

        INSTANCE.clear();

        int playerDimension = player.worldObj.provider.dimensionId;
        if (dimension != playerDimension) {
            sendChat(player, KEY_OTHER_DIMENSION, dimension);
            return;
        }

        INSTANCE.highlightedBlocks.add(new DimPos(dimension, x, y, z));
        INSTANCE.expireHighlightTime = System.currentTimeMillis() + durationFor(player, x, y, z);
        sendChat(player, KEY_HIGHLIGHTED, dimension, x, y, z);
    }

    /** AE2's duration: half a second per taxicab block, clamped to 3..30 seconds. */
    private static int durationFor(EntityPlayer player, int x, int y, int z) {
        int distance = Math.abs(x - MathHelper.floor_double(player.posX))
            + Math.abs(y - MathHelper.floor_double(player.posY))
            + Math.abs(z - MathHelper.floor_double(player.posZ));
        long scaled = (long) TIME_PER_BLOCK_MS * distance;
        return (int) Math.max(MIN_TIME_MS, Math.min(MAX_TIME_MS, scaled));
    }

    /** Sends one already-localized chat line to the player who clicked the button. */
    private static void sendChat(EntityPlayer player, String key, Object... args) {
        player.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(key, args)));
    }

    @Override
    public void renderHighlightedBlocks(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;

        int dimension = minecraft.theWorld.provider.dimensionId;
        double viewX = RenderManager.renderPosX, viewY = RenderManager.renderPosY, viewZ = RenderManager.renderPosZ;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // Through walls: the outline exists to be found, so terrain must not hide it.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glLineWidth(LINE_WIDTH);
        GL11.glTranslated(-viewX, -viewY, -viewZ);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.setColorRGBA_F(RED, GREEN, BLUE, ALPHA);
        for (DimPos position : highlightedBlocks) {
            if (position.dimension != dimension) continue;
            addBoxOutline(tessellator, position.x, position.y, position.z);
        }
        tessellator.draw();

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** The twelve edges of the block's 1x1x1 box, as vertex pairs for {@link GL11#GL_LINES}. */
    private static void addBoxOutline(Tessellator tessellator, double x, double y, double z) {
        double x1 = x + 1.0D, y1 = y + 1.0D, z1 = z + 1.0D;

        // Bottom and top face.
        addLine(tessellator, x, y, z, x1, y, z);
        addLine(tessellator, x1, y, z, x1, y, z1);
        addLine(tessellator, x1, y, z1, x, y, z1);
        addLine(tessellator, x, y, z1, x, y, z);
        addLine(tessellator, x, y1, z, x1, y1, z);
        addLine(tessellator, x1, y1, z, x1, y1, z1);
        addLine(tessellator, x1, y1, z1, x, y1, z1);
        addLine(tessellator, x, y1, z1, x, y1, z);

        // The four uprights joining them.
        addLine(tessellator, x, y, z, x, y1, z);
        addLine(tessellator, x1, y, z, x1, y1, z);
        addLine(tessellator, x1, y, z1, x1, y1, z1);
        addLine(tessellator, x, y, z1, x, y1, z1);
    }

    private static void addLine(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2,
        double z2) {
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
    }

    @Override
    public boolean noWork() {
        return highlightedBlocks.isEmpty();
    }

    @Override
    public void clear() {
        highlightedBlocks.clear();
        expireHighlightTime = -1L;
    }

    @Override
    public long getExpireTime() {
        return expireHighlightTime;
    }

    /** An immutable highlighted block position: dimension id plus integer block coordinates (no AE2 API types). */
    private static final class DimPos {

        private final int dimension;
        private final int x;
        private final int y;
        private final int z;

        private DimPos(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
