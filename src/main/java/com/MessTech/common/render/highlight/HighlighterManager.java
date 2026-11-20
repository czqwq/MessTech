package com.MessTech.common.render.highlight;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Drives every registered {@link IHighlighter} from the world render pass.
 * <p>
 * Ported from Applied Energistics 2's {@code appeng.client.render.highlighter.HighlighterManager}: one
 * {@code RenderWorldLastEvent} handler that blinks the outline (half a second on, half a second off, taken from the
 * wall clock rather than the tick counter) and expires a highlighter whose time has run out. Because the drawing is
 * driven here and not by a GUI, an outline survives the screen close that follows the click.
 * <p>
 * {@link #init()} is called once from the client proxy's {@code preInit}.
 */
@SideOnly(Side.CLIENT)
public final class HighlighterManager {

    /** The blink: this long on, this long off, by wall-clock phase like AE2's 500 ms. */
    private static final long BLINK_PERIOD_MS = 500L;

    /** The highlighters this manager drives, registered once when the class loads. */
    private static final Set<IHighlighter> HIGHLIGHTERS = new HashSet<>();

    static {
        registerHighlighter(BlockPosHighlighter.INSTANCE);
    }

    private HighlighterManager() {}

    /** Registers the world render hook. Called by the client proxy. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new HighlighterManager());
    }

    /** Adds one highlighter to the set the manager drives. */
    static void registerHighlighter(IHighlighter highlighter) {
        HIGHLIGHTERS.add(highlighter);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // A world render only happens with a world loaded; still guard, the event is also fired while leaving one.
        if (Minecraft.getMinecraft().theWorld == null) return;

        long now = System.currentTimeMillis();
        // The empty half of the blink phase: drawing nothing for 500 ms is what makes the outline blink.
        if (((now / BLINK_PERIOD_MS) & 1L) == 0L) return;

        for (IHighlighter highlighter : HIGHLIGHTERS) {
            if (highlighter.noWork()) continue;
            if (now > highlighter.getExpireTime()) {
                highlighter.clear();
                continue;
            }
            highlighter.renderHighlightedBlocks(event);
        }
    }
}
