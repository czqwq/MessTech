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
 * MessTech's own animated tooltip handler: a supplier per {@link ItemStack}, re-evaluated once per frame, plus the
 * renderer half an animated line may carry.
 * <p>
 * gtnhlib's {@code AnimatedTooltipHandler} adds only <b>strings</b>, leaving an animation that must touch the letters
 * themselves (rotate, blend) nowhere to run. This handler remembers an {@link MTTextAnimation} per line and, for a
 * stack that has one, takes the drawing over through gtnhlib's {@code RenderTooltipEvent#alternativeRenderer} (NEI
 * honours it too), draws the tooltip like vanilla, then calls the {@link MTTextRenderer} of every line the animation
 * belongs to.
 * <p>
 * Positions follow vanilla's {@code drawHoveringText}, so NEI's own layout (paging for tall tooltips, {@code TIP_LINE}
 * drawables) is <b>not</b> reproduced for a decorated stack: keep rendered animations on plain-text tooltips, as
 * machine tooltips are.
 * <p>
 * Registration is common code, drawing is client side, hence animation and renderer are separate objects
 * ({@link MTTextAnimation}).
 */
public final class MTAnimatedTooltipHandler {

    /**
     * One registered line: the supplier that produces it and, optionally, the animation it wears. A name entry
     * ({@link #addItemNameAnimation}) adds no line of its own - its supplier only says what the item's name line looks
     * like, so the animation's renderer attaches to that line and the tooltip keeps the lines it had.
     */
    private static final class Entry {

        private final Supplier<String> line;
        private final MTTextAnimation animation;
        /** Whether {@link #line} is a line of its own, i.e. whether it is appended to the tooltip. */
        private final boolean drawn;

        private Entry(Supplier<String> line, MTTextAnimation animation, boolean drawn) {
            this.line = line;
            this.animation = animation;
            this.drawn = drawn;
        }
    }

    /** The lines of every decorated stack, keyed the way gtnhlib keys its own tooltips (wildcard damage allowed). */
    private static final Map<ItemStack, List<Entry>> TOOLTIPS = new ItemStackMap<>(false);

    /** The renderer of an animation, client side only: the animations themselves are common code. */
    @SideOnly(Side.CLIENT)
    private static Map<MTTextAnimation, MTTextRenderer> renderers;

    /**
     * The renderer map, built on first use rather than in the static initializer: FML's {@code SideTransformer}
     * deletes a {@code @SideOnly(Side.CLIENT)} field on a dedicated server but never the {@code <clinit>} that assigns
     * it, so a client-only field with an initializer kills the server with {@code NoSuchFieldError: RENDERERS}.
     * {@link #background()} is lazy for the same reason: the nested class it creates would be loaded by
     * {@code <clinit>} too.
     */
    @SideOnly(Side.CLIENT)
    private static Map<MTTextAnimation, MTTextRenderer> renderers() {
        if (renderers == null) renderers = new IdentityHashMap<>();
        return renderers;
    }

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
        registerRenderer(AuthorDynamic.MODULE_PROJECT, new MTModuleProjectTextRenderer());
        registerRenderer(MTStarfieldText.INSTANCE, new MTStarfieldTextRenderer());
        MinecraftForge.EVENT_BUS.register(new MTAnimatedTooltipHandler());
    }

    /**
     * Adds one line to the tooltip of the given stack, re-evaluated once per frame.
     *
     * @param stack the stack to decorate; {@code OreDictionary.WILDCARD_VALUE} as its damage covers every variant
     * @param line  normally a {@link Supplier} returning a fresh frame per call; may return several lines separated
     *              by {@code '\n'}
     */
    public static void addItemTooltip(ItemStack stack, Supplier<String> line) {
        addItemTooltip(stack, line, null);
    }

    /**
     * Adds one line that wears the given animation. It is drawn with the font like any other, then the renderer
     * registered for the animation (see {@link #registerRenderer}) is drawn over it.
     *
     * @param line      the finished line, formatting codes included; animated lines normally come from an
     *                  {@link MTTextAnimation} (see {@link AuthorDynamic#register})
     * @param animation the animation the line wears, {@code null} for a plain line
     */
    public static void addItemTooltip(ItemStack stack, Supplier<String> line, MTTextAnimation animation) {
        if (stack == null || line == null) return;

        List<Entry> entries = TOOLTIPS.computeIfAbsent(stack, key -> new ArrayList<>());
        entries.add(new Entry(line, animation, true));
    }

    /**
     * Puts an animation on the item's <b>own name line</b>, without adding a line of its own.
     * <p>
     * The first tooltip line is the display name, drawn by vanilla before any handler sees the tooltip, so the
     * animation is attached by text: a renderer's line is found by its <b>visible text</b> (see {@link #rendererFor}),
     * so a supplier answering with the item's name is enough. The entry is not appended - the difference to
     * {@link #addAnimatedText}, since adding the name again would show the item twice. The supplier runs every frame,
     * so return the name as the item displays it (the lang key, or {@code ItemStack#getDisplayName} for one renameable
     * in an anvil).
     *
     * @param stack     the item whose name wears the animation; {@code OreDictionary.WILDCARD_VALUE} covers every
     *                  variant
     * @param name      the name the tooltip shows; formatting codes are allowed and ignored when matching
     * @param animation the animation the name wears, e.g. {@link MTStarfieldText#INSTANCE}
     */
    public static void addItemNameAnimation(ItemStack stack, Supplier<String> name, MTTextAnimation animation) {
        if (stack == null || name == null || animation == null) return;

        List<Entry> entries = TOOLTIPS.computeIfAbsent(stack, key -> new ArrayList<>());
        entries.add(new Entry(name, animation, false));
    }

    /**
     * Adds one free standing line that wears the given animation - no author formatting, no prefix. The line is the
     * animation's frame of {@code text.get()}, re-evaluated every frame (hand it
     * {@code () -> StatCollector.translateToLocal(...)} to stay live across a resource pack reload).
     *
     * @param text      the text to animate, formatting codes allowed; a supplier may return several lines separated
     *                  by {@code '\n'}
     * @param animation the animation the line wears, e.g. {@link AuthorDynamic#TRANSCENDENT_METAL}
     */
    public static void addAnimatedText(ItemStack stack, Supplier<String> text, MTTextAnimation animation) {
        addAnimatedText(stack, null, text, animation);
    }

    /**
     * Adds one free standing line of a static prefix and an animated word, the shape of the MessTech and PigTech
     * brand lines: the prefix is copied into every frame verbatim, so it never changes width or colour, and only the
     * word wears the animation ("Add by: NuclearTech" is one of these).
     *
     * @param prefix    the static text in front, normally the translated {@code messTech.addBy}; {@code null} or empty
     *                  leaves it out, {@link MTPigTechText#PREFIX_SEPARATOR} goes between it and the word
     * @param text      the word to animate, formatting codes allowed; a supplier may return several lines separated
     *                  by {@code '\n'}
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
     * Registers the renderer an animation is drawn with; client side only, while the animation itself can be
     * registered from common code. Registering again replaces the renderer; without one, an animation is drawn as its
     * plain strings.
     *
     * @param animation the animation to draw
     * @param renderer  the renderer that draws it, {@code null} removes the animation's renderer
     */
    @SideOnly(Side.CLIENT)
    public static void registerRenderer(MTTextAnimation animation, MTTextRenderer renderer) {
        if (animation == null) return;
        if (renderer == null) renderers().remove(animation);
        else renderers().put(animation, renderer);
    }

    /** Appends the registered lines of the hovered stack, the same way gtnhlib's handler does. */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        List<Entry> entries = TOOLTIPS.get(event.itemStack);
        if (entries == null) return;

        for (Entry entry : entries) {
            if (!entry.drawn) continue; // a name animation decorates a line the tooltip already has

            String text = entry.line.get();
            if (text == null || text.isEmpty()) continue;

            for (String line : text.split("\n", -1)) {
                event.toolTip.add(line);
            }
        }
    }

    /**
     * Takes the drawing of a tooltip over when one of its lines wears a rendered animation, so the renderer can be
     * drawn on top of the finished font; a tooltip without such a line is left alone.
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
            if (entry.animation != null && renderers().containsKey(entry.animation)) return true;
        }
        return false;
    }

    /**
     * Draws a tooltip the way vanilla's {@code GuiScreen#drawHoveringText} does - background, border, one line of
     * shadowed font per line - then calls the renderer of every line that wears one, so the effect is an overlay on
     * the finished font.
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

        background().draw(x, y, width, height, event);

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
     * Finds the renderer of the line: the entry whose animation has a renderer and whose visible text equals that of
     * the drawn line. Matching text rather than position keeps the overlay on its own line even when another mod moved
     * the lines (NEI inserts its second display name at index 1, for instance).
     */
    @SideOnly(Side.CLIENT)
    private static MTTextRenderer rendererFor(List<Entry> entries, String line) {
        if (entries == null) return null;

        String visible = MTTextAnimation.visibleText(line);
        for (Entry entry : entries) {
            if (entry.animation == null) continue;

            MTTextRenderer renderer = renderers().get(entry.animation);
            if (renderer == null) continue;

            String frame = entry.line.get();
            if (frame != null && visible.equals(MTTextAnimation.visibleText(frame))) return renderer;
        }
        return null;
    }

    /**
     * The vanilla tooltip box. {@code Gui#drawGradientRect} is protected, so it is reached through a subclass of
     * {@code Gui} rather than a static helper.
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

    /** The one box every tooltip of this handler is drawn with, {@code null} until {@link #background()}. */
    @SideOnly(Side.CLIENT)
    private static Background background;

    /** @return the shared tooltip box, built on first use - see {@link #renderers()} for why it is not a constant. */
    @SideOnly(Side.CLIENT)
    private static Background background() {
        if (background == null) background = new Background();
        return background;
    }
}
