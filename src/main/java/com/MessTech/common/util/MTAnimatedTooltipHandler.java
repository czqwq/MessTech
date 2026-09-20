package com.MessTech.common.util;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent;
import com.gtnewhorizon.gtnhlib.util.map.ItemStackMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * MessTech's own animated tooltip handler: the gtnhlib {@code AnimatedTooltipHandler} idea - a supplier per
 * {@link ItemStack}, re-evaluated once per frame - extended by the renderer half an animated line may carry.
 * <p>
 * gtnhlib's handler can only add <b>strings</b> to a tooltip, so an animation that has to touch the letters
 * themselves (rotate them, blend them) has nowhere to run. This handler keeps the same registration shape and
 * additionally remembers an {@link MTTextAnimation} per line; when a tooltip of a stack with such a line is about
 * to be drawn, it takes the drawing over through gtnhlib's {@code RenderTooltipEvent#alternativeRenderer} - which
 * NEI honours as well, so it works in the inventory and in the NEI panels alike - draws the tooltip exactly the
 * way vanilla does and then calls the {@link MTTextRenderer} of every line the animation belongs to.
 * <p>
 * The drawing follows vanilla's {@code drawHoveringText}: the same box, the same line positions. NEI specific
 * layout of the same tooltip - its paging for tooltips taller than the screen, and its {@code TIP_LINE} drawable
 * lines (recipe widgets, bookmarks) - is therefore not reproduced for a decorated stack; keep rendered animations
 * on stacks whose tooltips are plain text, which is what machine tooltips are.
 * <p>
 * Registration is common code (the machine loader decorates its items on both sides), only the drawing is client
 * side, which is why the animation is a separate object from its renderer: see {@link MTTextAnimation}.
 */
public final class MTAnimatedTooltipHandler {

    /** One registered line: the supplier that produces it and, optionally, the animation it wears. */
    private static final class Entry {

        private final Supplier<String> line;
        private final MTTextAnimation animation;

        private Entry(Supplier<String> line, MTTextAnimation animation) {
            this.line = line;
            this.animation = animation;
        }
    }

    /** The lines of every decorated stack, keyed the way gtnhlib keys its own tooltips (wildcard damage allowed). */
    private static final Map<ItemStack, List<Entry>> TOOLTIPS = new ItemStackMap<>(false);

    /** The renderer of an animation, client side only: the animations themselves are common code. */
    @SideOnly(Side.CLIENT)
    private static final Map<MTTextAnimation, MTTextRenderer> RENDERERS = new IdentityHashMap<>();

    /** Whether {@link #init()} has already run. */
    @SideOnly(Side.CLIENT)
    private static boolean initialised;

    private MTAnimatedTooltipHandler() {}

    /**
     * Registers the handler and the renderers of the built-in animations. Called by the client proxy.
     */
    @SideOnly(Side.CLIENT)
    public static void init() {
        if (initialised) return;
        initialised = true;

        registerRenderer(AuthorDynamic.TRANSCENDENT_METAL, new MTTranscendentMetalTextRenderer());
        MinecraftForge.EVENT_BUS.register(new MTAnimatedTooltipHandler());
    }

    /**
     * Adds one line to the tooltip of the given stack, re-evaluated once per frame.
     *
     * @param stack the stack to decorate; {@code OreDictionary.WILDCARD_VALUE} as its damage covers every variant
     * @param line  the line, normally a {@link Supplier} that returns a fresh frame on every call; a supplier may
     *              return several lines separated by {@code '\n'}
     */
    public static void addItemTooltip(ItemStack stack, Supplier<String> line) {
        addItemTooltip(stack, line, null);
    }

    /**
     * Adds one line that wears the given animation to the tooltip of the given stack.
     * <p>
     * The line is drawn with the font like any other, and the renderer registered for the animation (see
     * {@link #registerRenderer}) is drawn over it afterwards.
     *
     * @param stack     the stack to decorate; {@code OreDictionary.WILDCARD_VALUE} as its damage covers every variant
     * @param line      the finished line, formatting codes included; animated lines normally come from an
     *                  {@link MTTextAnimation} (see {@link AuthorDynamic#register})
     * @param animation the animation the line wears, {@code null} for a plain line
     */
    public static void addItemTooltip(ItemStack stack, Supplier<String> line, MTTextAnimation animation) {
        if (stack == null || line == null) return;

        List<Entry> entries = TOOLTIPS.computeIfAbsent(stack, key -> new ArrayList<>());
        entries.add(new Entry(line, animation));
    }

    /**
     * Adds one free standing line that wears the given animation - no author formatting, no prefix - which is what
     * an animated word on its own needs.
     * <p>
     * The line is the animated text alone: the animation's frame of {@code text.get()}, so the supplier is
     * evaluated again on every frame (hand it {@code () -> StatCollector.translateToLocal(...)} to stay live
     * across a resource pack reload).
     *
     * @param stack     the stack to decorate; {@code OreDictionary.WILDCARD_VALUE} as its damage covers every variant
     * @param text      the text to animate, formatting codes allowed; a supplier may return several lines
     *                  separated by {@code '\n'}
     * @param animation the animation the line wears, e.g. {@link AuthorDynamic#TRANSCENDENT_METAL}
     */
    public static void addAnimatedText(ItemStack stack, Supplier<String> text, MTTextAnimation animation) {
        addAnimatedText(stack, null, text, animation);
    }

    /**
     * Adds one free standing line made of a static prefix and an animated word, the way the MessTech and PigTech
     * brand lines are built: the prefix is copied into every frame verbatim, so it never changes width or colour,
     * and only the word after it wears the animation. "Add by: NuclearTech" is one of these.
     *
     * @param stack     the stack to decorate; {@code OreDictionary.WILDCARD_VALUE} as its damage covers every variant
     * @param prefix    the static text in front of the animation, normally the translated {@code messTech.addBy};
     *                  {@code null} or empty leaves it out, and {@link MTPigTechText#PREFIX_SEPARATOR} is put
     *                  between it and the word
     * @param text      the word to animate, formatting codes allowed; a supplier may return several lines
     *                  separated by {@code '\n'}
     * @param animation the animation the word wears, e.g. {@link AuthorDynamic#TRANSCENDENT_METAL}
     */
    public static void addAnimatedText(ItemStack stack, Supplier<String> prefix, Supplier<String> text,
        MTTextAnimation animation) {
        if (stack == null || text == null || animation == null) return;

        addItemTooltip(stack, () -> {
            String word = animation.frame(MTTextAnimation.visibleText(text.get()), System.currentTimeMillis());
            String head = prefix == null ? "" : prefix.get();
            return head == null || head.isEmpty() ? word : head + MTPigTechText.PREFIX_SEPARATOR + word;
        }, animation);
    }

    /**
     * Registers the renderer an animation is drawn with.
     * <p>
     * Client side only: this is where the drawing lives, while the animation itself can be registered from common
     * code. Registering the same animation twice replaces its renderer, and an animation without a renderer is
     * drawn as its plain strings.
     *
     * @param animation the animation to draw
     * @param renderer  the renderer that draws it, {@code null} removes the animation's renderer
     */
    @SideOnly(Side.CLIENT)
    public static void registerRenderer(MTTextAnimation animation, MTTextRenderer renderer) {
        if (animation == null) return;
        if (renderer == null) RENDERERS.remove(animation);
        else RENDERERS.put(animation, renderer);
    }

    /** Appends the registered lines of the hovered stack, the same way gtnhlib's handler does. */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        List<Entry> entries = TOOLTIPS.get(event.itemStack);
        if (entries == null) return;

        for (Entry entry : entries) {
            String text = entry.line.get();
            if (text == null || text.isEmpty()) continue;

            for (String line : text.split("\n", -1)) {
                event.toolTip.add(line);
            }
        }
    }

    /**
     * Takes the drawing of a tooltip over when one of its lines wears a rendered animation, so the renderer can be
     * drawn on top of the finished font. A tooltip without such a line is left completely alone.
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderTooltip(RenderTooltipEvent event) {
        if (event.itemStack == null || event.alternativeRenderer != null) return;

        List<Entry> entries = TOOLTIPS.get(event.itemStack);
        if (!hasRenderer(entries)) return;

        event.alternativeRenderer = lines -> renderTooltip(event, lines, entries);
    }

    /** @return true when at least one of the lines wears an animation that has a renderer. */
    @SideOnly(Side.CLIENT)
    private static boolean hasRenderer(List<Entry> entries) {
        if (entries == null) return false;

        for (Entry entry : entries) {
            if (entry.animation != null && RENDERERS.containsKey(entry.animation)) return true;
        }
        return false;
    }

    /**
     * Draws a tooltip the way vanilla's {@code GuiScreen#drawHoveringText} does - background, border and one line
     * of shadowed font per line - and calls the renderer of every line that wears one afterwards, so the effect is
     * an overlay on the finished font.
     */
    @SideOnly(Side.CLIENT)
    private static void renderTooltip(RenderTooltipEvent event, List<String> lines, List<Entry> entries) {
        FontRenderer font = event.font;
        int mouseX = event.x;
        int mouseY = event.y;

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        int height = 8;
        if (lines.size() > 1) {
            height += 2 + (lines.size() - 1) * 10;
        }
        if (x + width > event.gui.width) {
            x -= 28 + width;
        }
        if (y + height + 6 > event.gui.height) {
            y = event.gui.height - height - 6;
        }

        BACKGROUND.draw(x, y, width, height, event);

        long millis = System.currentTimeMillis();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            MTTextRenderer renderer = rendererFor(entries, line);

            // A renderer that turns the line itself replaces the plain font copy: a still line with a turning one
            // drawn over it shows both at once.
            if (renderer == null || !renderer.replacesText()) {
                font.drawStringWithShadow(line, x, y, -1);
            }
            if (renderer != null) {
                renderer.draw(font, line, x, y, millis);
            }

            if (i == 0) y += 2;
            y += 10;
        }
    }

    /**
     * Finds the renderer of the line: the entry whose animation has a renderer and whose visible text is the
     * visible text of the drawn line. Matching the text rather than a position keeps the overlay on its own line
     * even when another mod (NEI inserts its second display name at index 1, for instance) has moved the lines.
     */
    @SideOnly(Side.CLIENT)
    private static MTTextRenderer rendererFor(List<Entry> entries, String line) {
        if (entries == null) return null;

        String visible = MTTextAnimation.visibleText(line);
        for (Entry entry : entries) {
            if (entry.animation == null) continue;

            MTTextRenderer renderer = RENDERERS.get(entry.animation);
            if (renderer == null) continue;

            String frame = entry.line.get();
            if (frame != null && visible.equals(MTTextAnimation.visibleText(frame))) return renderer;
        }
        return null;
    }

    /**
     * The vanilla tooltip box. {@code Gui#drawGradientRect} is protected, so it is reached through a subclass of
     * {@code Gui} instead of a static helper.
     */
    @SideOnly(Side.CLIENT)
    private static final class Background extends Gui {

        private void rect(int x1, int y1, int x2, int y2, int startColour, int endColour) {
            drawGradientRect(x1, y1, x2, y2, startColour, endColour);
        }

        private void draw(int x, int y, int width, int height, RenderTooltipEvent event) {
            int backgroundStart = event.backgroundStart;
            int backgroundEnd = event.backgroundEnd;
            int borderStart = event.borderStart;
            int borderEnd = event.borderEnd;

            zLevel = 300.0F;

            rect(x - 3, y - 4, x + width + 3, y - 3, backgroundStart, backgroundStart);
            rect(x - 3, y + height + 3, x + width + 3, y + height + 4, backgroundEnd, backgroundEnd);
            rect(x - 3, y - 3, x + width + 3, y + height + 3, backgroundStart, backgroundEnd);
            rect(x - 4, y - 3, x - 3, y + height + 3, backgroundStart, backgroundEnd);
            rect(x + width + 3, y - 3, x + width + 4, y + height + 3, backgroundStart, backgroundEnd);

            rect(x - 3, y - 2, x - 2, y + height + 2, borderStart, borderEnd);
            rect(x + width + 2, y - 2, x + width + 3, y + height + 2, borderStart, borderEnd);
            rect(x - 3, y - 3, x + width + 3, y - 2, borderStart, borderStart);
            rect(x - 3, y + height + 2, x + width + 3, y + height + 3, borderEnd, borderEnd);

            zLevel = 0.0F;
        }
    }

    /** The one box every tooltip of this handler is drawn with. */
    @SideOnly(Side.CLIENT)
    private static final Background BACKGROUND = new Background();
}
