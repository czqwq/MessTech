package com.MessTech.common.util;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

import com.MessTech.init.MessTech;
import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.shaders.UniversiumShader;
import com.gtnewhorizon.gtnhlib.util.ItemRenderUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;
import gregtech.api.enums.Textures;
import gregtech.common.config.Client;
import gregtech.common.render.items.GlitchEffectRenderer;

/**
 * Helper for "dynamic" items: one item, several looks, selected through {@link ItemStack#getItemDamage()}.
 * <p>
 * An item decides how many looks it has by registering one icon per {@link Effect} ({@link #registerIcons}) and by
 * asking this class for a renderer ({@link #registerItemRenderer}). The damage value of a stack is the index of the
 * effect it currently shows, so {@link #cycleEffect(ItemStack)} (used by shift + right click) simply walks the enum.
 * <p>
 * Each {@link Effect} copies one of GT5U's animated item renderers, which is what makes the effects look and behave
 * exactly like the GT items that use them:
 * <ul>
 * <li>{@link Style#TUMBLE} - {@code TranscendentMetalRenderer}: the flat quad tumbles around the oblique axis
 * (0.3, 0.5, 0.2) by 3.5 degrees per client tick, i.e. the "Transcendent Metal" animation.</li>
 * <li>{@link Style#HALO_PULSE} - {@code InfinityRenderer}: a dark halo behind the icon plus a randomly scaling
 * translucent copy of the icon on top of it. Used by Infinity, Eternity and MagMatter in GT5U; the animated part of
 * those materials is their texture strip, the renderer itself only pulses it.</li>
 * <li>{@link Style#UNIVERSIUM} - {@code UniversiumRenderer}: the plain icon, then gtnhlib's cosmic star shader draws
 * the Universium look on top of it. The shader keeps only the icon's alpha, so anything the sprite draws loses its
 * colour to the star field; an item can hand a second icon to {@link #registerUniversiumOverlay} to get it back.</li>
 * <li>{@link Style#GLITCH} - {@code GlitchEffectRenderer}: red and cyan ghost copies jittering around the icon for
 * 10 ticks every 100 ticks.</li>
 * </ul>
 * Because the look is picked from the damage value, an item can use exactly one effect at a time. Stacking several
 * renderers on one stack is not supported on purpose.
 * <p>
 * The icons themselves are expected to be pre-rendered per material (see {@code assets/messtech/textures/items/pigs}):
 * tinting a 32x32 pixel-art icon through {@code GL11.glColor4f} would flatten the shading of the whole picture, so the
 * material look is baked into the PNG instead. Animated effects need their own {@code .mcmeta} next to the PNG, and
 * every icon of one item must use the same frame size.
 * <p>
 * Icon names are relative to {@code textures/items/}: the item atlas adds that folder itself, so a base path of
 * {@code pigs/pig} points at {@code assets/messtech/textures/items/pigs/pig.png}. Adding an {@code items/} prefix
 * makes the atlas look for {@code textures/items/items/...} and every icon turns into the purple/black missing
 * texture.
 */
public final class MTDynamicItemHelper {

    /** Texture domain of the icons: they live in {@code assets/messtech/textures/}. */
    public static final String TEXTURE_DOMAIN = "messtech";

    /**
     * Suffix of the icon {@link #registerUniversiumOverlay} registers: for a base path of {@code pigs/pig} it is
     * {@code pigs/pigUniversiumFace}.
     */
    public static final String UNIVERSIUM_OVERLAY_SUFFIX = "UniversiumFace";

    private static final Random RANDOM = new Random();

    /** Frames of the glitch animation before it repeats, and how long the glitch lasts. */
    private static final long GLITCH_FRAME_NANOS = 10_000_000L;
    private static final int GLITCH_LOOP_FRAMES = 200;
    private static final int GLITCH_DURATION_FRAMES = 40;
    private static final int GLITCH_MOVE_FRAMES = 5;
    private static final double GLITCH_MAX_OFFSET = 1.7D;

    /** Copy of the offsets {@code GlitchEffectRenderer} keeps per instance, so the shared renderer can hold one set. */
    private static double glitchOffsetRed;
    private static double glitchOffsetCyan;

    private MTDynamicItemHelper() {}

    /** How an {@link Effect} is drawn. */
    public enum Style {

        /** Nothing special: the icon is drawn with the same pipeline GT5U uses for plain materials. */
        PLAIN,
        /** GT5U's Transcendent Metal animation. */
        TUMBLE,
        /** GT5U's Infinity / Eternity / MagMatter animation. */
        HALO_PULSE,
        /** GT5U's Universium cosmic shader. */
        UNIVERSIUM,
        /** GT5U's Six-Phased Copper glitch. */
        GLITCH
    }

    /**
     * The looks an item can cycle through, in the order the player sees them. The ordinal is the damage value of the
     * stack, so {@link #NONE} has to stay at index 0: an unregistered/damaged stack then falls back to the plain icon.
     */
    public enum Effect {

        NONE("", "plain", Style.PLAIN),
        /** GT5U Transcendent Metal: dark metal tint plus the tumbling preview. */
        TRANSCENDENT_METAL("TranscendentMetal", "transcendentMetal", Style.TUMBLE),
        /** GT5U Infinity: the animated rainbow-ish strip with halo and pulse. */
        INFINITY("Infinity", "infinity", Style.HALO_PULSE),
        /** GT5U MagMatter: a greyscale flowing pattern, animated by the strip and pulsed by the renderer. */
        MAGMATTER("MagMatter", "magMatter", Style.HALO_PULSE),
        /** GT5U Eternity: animated strip with halo and pulse. */
        ETERNITY("Eternity", "eternity", Style.HALO_PULSE),
        /** GT5U Universium: the icon is overlaid with the cosmic star shader. */
        UNIVERSIUM("Universium", "universium", Style.UNIVERSIUM),
        /** GT5U Six-Phased Copper: the icon glitches into red and cyan ghosts from time to time. */
        SIX_PHASED_COPPER("SixPhasedCopper", "sixPhasedCopper", Style.GLITCH);

        /** {@code values()} clones its backing array on every call, so the array is cached here. */
        public static final Effect[] VALUES = values();

        /** Suffix appended to an item's base icon path to build this effect's icon name. */
        public final String iconSuffix;
        /** Suffix of this effect's "messtech.itemEffect." translation key. */
        public final String langSuffix;
        /** How this effect is drawn. */
        public final Style style;

        Effect(String iconSuffix, String langSuffix, Style style) {
            this.iconSuffix = iconSuffix;
            this.langSuffix = langSuffix;
            this.style = style;
        }

        /** @return the localised name of this effect, e.g. "Infinity". */
        public String getDisplayName() {
            return StatCollector.translateToLocal("messtech.itemEffect." + langSuffix);
        }

        /** @return the effect with the given damage value, or {@link #NONE} when the value is out of range. */
        public static Effect byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : NONE;
        }

        /** @return the next effect, wrapping around at the end. */
        public Effect next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    /**
     * Registers one icon per {@link Effect} using the given base path, e.g. {@code "pigs/pig"}:
     * {@code pig}, {@code pigTranscendentMetal}, {@code pigInfinity}, ...
     * <p>
     * The returned array is indexed by {@link Effect#ordinal()}, i.e. by the damage value of a stack, so it can be
     * handed to the item as-is.
     *
     * @param register the icon register of the item
     * @param basePath icon path without domain, without the per-effect suffix and without an {@code items/} prefix
     *                 (the item atlas prepends {@code textures/items/} on its own)
     * @return the icons, one per effect
     */
    @SideOnly(Side.CLIENT)
    public static IIcon[] registerIcons(IIconRegister register, String basePath) {
        IIcon[] icons = new IIcon[Effect.VALUES.length];
        for (Effect effect : Effect.VALUES) {
            String iconName = basePath + effect.iconSuffix;
            icons[effect.ordinal()] = register.registerIcon(TEXTURE_DOMAIN + ":" + iconName);
            warnIfIconFileMissing(iconName);
        }
        return icons;
    }

    /**
     * Registers the icon an item wants drawn over its {@link Style#UNIVERSIUM} look, next to the per-effect icons
     * from {@link #registerIcons}: for a base path of {@code pigs/pig} the file is
     * {@code assets/messtech/textures/items/pigs/pigUniversiumFace.png}.
     * <p>
     * The cosmic shader throws the icon's colour away - it reads the icon's alpha and paints the star field over
     * everything else - so any detail the sprite carries (the piggy's eyes and snout, for instance) ends up behind
     * the stars. The icon registered here is drawn back on top of the finished shader, so it is what the player
     * sees in front of the sky.
     * <p>
     * The sky is dark, so an overlay painted in the material's own colour would be invisible on it: hand in the
     * untinted art, not one of the {@link Effect} builds.
     *
     * @param item     the item the overlay belongs to, i.e. the one passed to {@link #registerItemRenderer}
     * @param register the item's icon register, i.e. the one {@link Item#registerIcons} is called with
     * @param basePath base icon path, exactly as passed to {@link #registerIcons}
     */
    @SideOnly(Side.CLIENT)
    public static void registerUniversiumOverlay(Item item, IIconRegister register, String basePath) {
        String iconName = basePath + UNIVERSIUM_OVERLAY_SUFFIX;
        warnIfIconFileMissing(iconName);
        EffectRenderer.UNIVERSIUM_OVERLAYS.put(item, register.registerIcon(TEXTURE_DOMAIN + ":" + iconName));
    }

    /**
     * Checks that the PNG of an icon really exists in the resource pack and logs a warning if it does not.
     * <p>
     * 1.7.10 replaced the "using missing texture" error with FML's silent missing texture tracking
     * ({@code TextureMap} line 189), so a typo in {@code basePath} only shows up in game as the purple/black checker
     * board. Checking the pack here turns that silence into a log line.
     *
     * @param iconName the registered icon name, i.e. the path below {@code textures/items} without the extension
     */
    @SideOnly(Side.CLIENT)
    private static void warnIfIconFileMissing(String iconName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        IResourceManager resources = minecraft == null ? null : minecraft.getResourceManager();
        if (resources == null) return;

        ResourceLocation file = new ResourceLocation(TEXTURE_DOMAIN, "textures/items/" + iconName + ".png");
        try {
            // 1.7.10 has no IResourceManager#resourceExists, so the lookup itself is the existence check.
            resources.getResource(file);
        } catch (IOException e) {
            MessTech.MT_LOG
                .warn("Dynamic item icon {} is missing, the item will show the purple/black missing texture", file);
        }
    }

    /** @return the effect the given stack currently shows, {@link Effect#NONE} for a null stack. */
    public static Effect getEffect(ItemStack stack) {
        return stack == null ? Effect.NONE : Effect.byIndex(stack.getItemDamage());
    }

    /** Makes the given stack show the given effect. */
    public static void setEffect(ItemStack stack, Effect effect) {
        if (stack != null) stack.setItemDamage(effect.ordinal());
    }

    /**
     * Switches the given stack to the next effect, wrapping around.
     *
     * @return the effect the stack shows afterwards
     */
    public static Effect cycleEffect(ItemStack stack) {
        Effect next = getEffect(stack).next();
        setEffect(stack, next);
        return next;
    }

    /**
     * Registers the shared effect renderer for an item: the icon is read from {@link Item#getIcon(ItemStack, int)} and
     * the effect from the damage value, so no extra renderer class is needed per item.
     */
    @SideOnly(Side.CLIENT)
    public static void registerItemRenderer(Item item) {
        MinecraftForgeClient.registerItemRenderer(item, new EffectRenderer());
    }

    /**
     * Honours GT5U's "fancy" accessibility switches: when a player turned an effect off there, the plain icon is drawn
     * instead, exactly like GT5U itself falls back to {@code GeneratedMaterialRenderer}.
     */
    @SideOnly(Side.CLIENT)
    private static boolean isFancyEnabled(Style style) {
        Client.Render render = Client.render;
        return switch (style) {
            case TUMBLE -> render.renderTransMetalFancy;
            case HALO_PULSE -> render.renderInfinityFancy;
            case UNIVERSIUM -> render.renderUniversiumFancy;
            case GLITCH -> render.renderGlitchFancy;
            default -> true;
        };
    }

    /**
     * The renderer shared by every item registered through {@link #registerItemRenderer}. All methods are static
     * because the renderer is stateless apart from the glitch offsets, which GT5U also keeps per renderer.
     */
    @SideOnly(Side.CLIENT)
    private static final class EffectRenderer implements IItemRenderer {

        /**
         * The icons of {@link #registerUniversiumOverlay}, keyed by item. Both ends of this map are client only -
         * it is filled while the item atlas is stitched and read while an item is drawn - so it lives here rather
         * than in the outer class, which the server also loads.
         */
        private static final Map<Item, IIcon> UNIVERSIUM_OVERLAYS = new IdentityHashMap<>();

        @Override
        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            return type != ItemRenderType.FIRST_PERSON_MAP;
        }

        @Override
        public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            return type == ItemRenderType.ENTITY && helper == ItemRendererHelper.ENTITY_BOBBING
                || (helper == ItemRendererHelper.ENTITY_ROTATION
                    && Minecraft.getMinecraft().gameSettings.fancyGraphics);
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
            if (stack == null || stack.getItem() == null) return;
            IIcon icon = stack.getItem()
                .getIcon(stack, 0);
            if (icon == null) return;

            Style style = getEffect(stack).style;
            if (!isFancyEnabled(style)) style = Style.PLAIN;

            switch (style) {
                case TUMBLE -> renderTumble(type, icon);
                case HALO_PULSE -> renderHaloPulse(type, icon);
                case UNIVERSIUM -> renderUniversium(type, stack, icon, data);
                case GLITCH -> renderGlitch(type, icon);
                default -> renderPlain(type, icon);
            }
        }

        /** Plain material look: the icon on its own. */
        private static void renderPlain(ItemRenderType type, IIcon icon) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);

            ItemRenderUtil.applyStandardItemTransform(type);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            ItemRenderUtil.renderItem(type, icon);

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }

        /**
         * Copy of GT5U's {@code TranscendentMetalRenderer}: the flat quad is drawn with
         * {@link net.minecraft.client.renderer.ItemRenderer#renderItemIn2D} instead of the vanilla pipeline, so it can
         * be tumbled around the oblique axis (0.3, 0.5, 0.2) by 3.5 degrees per client tick. The angle comes from
         * {@link GTMod#clientProxy()}, so the animation stops together with the game.
         */
        private static void renderTumble(ItemRenderType type, IIcon icon) {
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

            // Same order as GT5U's MetaGeneratedItemRenderer: standard transform first, then pretend fancy graphics is
            // on (magic numbers taken from vanilla RenderItem) when it is off.
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

            applyTumbleTransform(type);
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
        private static void applyTumbleTransform(ItemRenderType type) {
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
                    .getAnimationRenderTicks() * 3.5F) % 360F,
                0.3F,
                0.5F,
                0.2F);

            GL11.glRotatef(180F, 0.5F, 0.0F, 0.0F);

            if (type == ItemRenderType.INVENTORY) {
                GL11.glTranslatef(-8F, -8F, 0F);
            } else {
                GL11.glTranslatef(-0.5F, -0.5F, 0.0F);
            }

            // Move the 2D quad onto the rotation axis (half of its thickness).
            GL11.glTranslatef(0.0F, 0.0F, 0.03125F);
        }

        /**
         * Copy of GT5U's {@code InfinityRenderer}: in the inventory a dark halo is drawn behind the icon and a
         * randomly scaling translucent copy on top of it; everywhere else the effect is skipped, which is what GT5U
         * does as well (it only renders these two layers for {@link ItemRenderType#INVENTORY}).
         */
        private static void renderHaloPulse(ItemRenderType type, IIcon icon) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);

            ItemRenderUtil.applyStandardItemTransform(type);

            if (type == ItemRenderType.INVENTORY) {
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(TextureMap.locationItemsTexture);
                renderHalo();
                renderPulse(icon);
            }

            GL11.glColor4f(1F, 1F, 1F, 1F);
            ItemRenderUtil.renderItem(type, icon);

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }

        /** Copy of {@code InfinityRenderer#renderHalo}: a 10 pixel black spread around the 16x16 icon quad. */
        private static void renderHalo() {
            IIcon halo = Textures.ItemIcons.HALO.getIcon();
            if (halo == null) return;

            int spread = 10;
            int haloAlpha = 0xFF000000;

            Tessellator tessellator = Tessellator.instance;

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            GL11.glColor4f(20 / 255.0f, 20 / 255.0f, 20 / 255.0f, (float) (haloAlpha >> 24 & 255) / 255.0F);

            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(-spread, -spread, 0, halo.getMinU(), halo.getMinV());
            tessellator.addVertexWithUV(-spread, 16 + spread, 0, halo.getMinU(), halo.getMaxV());
            tessellator.addVertexWithUV(16 + spread, 16 + spread, 0, halo.getMaxU(), halo.getMaxV());
            tessellator.addVertexWithUV(16 + spread, -spread, 0, halo.getMaxU(), halo.getMinV());
            tessellator.draw();
        }

        /** Copy of {@code InfinityRenderer#renderPulse}: a copy of the icon at 60% alpha, scaled by a gaussian. */
        private static void renderPulse(IIcon... icons) {
            if (icons.length == 0) return;

            Tessellator tessellator = Tessellator.instance;
            float random = (float) RANDOM.nextGaussian();
            float scale = (random * 0.15f) + 0.95f;
            float offset = (1.0f - scale) / 2.0f;

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glTranslatef(offset * 16.0f, offset * 16.0f, 1.0f);
            GL11.glScalef(scale, scale, 1.0f);

            for (IIcon icon : icons) {
                if (icon == null) continue;
                tessellator.startDrawingQuads();
                tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, 0.6f);
                tessellator.addVertexWithUV(0 - offset, 0 - offset, 0, icon.getMinU(), icon.getMinV());
                tessellator.addVertexWithUV(0 - offset, 16 + offset, 0, icon.getMinU(), icon.getMaxV());
                tessellator.addVertexWithUV(16 + offset, 16 + offset, 0, icon.getMaxU(), icon.getMaxV());
                tessellator.addVertexWithUV(16 + offset, 0 - offset, 0, icon.getMaxU(), icon.getMinV());
                tessellator.draw();
            }

            GL11.glPopMatrix();
        }

        /**
         * Copy of GT5U's {@code UniversiumRenderer}: the icon is drawn once, then gtnhlib's cosmic star shader draws
         * the star field over exactly the same geometry. In the inventory the second pass is drawn over the first one
         * (there is no depth buffer to test against), outside of it the depth function is switched to
         * {@link GL11#GL_EQUAL} so the stars only land on the icon itself.
         * <p>
         * The shader writes its own colour wherever the icon is opaque, so the icon's own art ends up behind the
         * sky. An item registered through {@link #registerUniversiumOverlay} therefore gets a third pass, once the
         * sky is finished, with the icon that brings its details back.
         */
        private static void renderUniversium(ItemRenderType type, ItemStack stack, IIcon icon, Object... data) {
            UniversiumShader shader = UniversiumShader.getInstance();
            processLightLevel(shader, data);

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);

            ItemRenderUtil.applyStandardItemTransform(type);

            if (type == ItemRenderType.INVENTORY) {
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);

                ItemRenderUtil.renderItem(type, icon);

                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);

                shader.setRenderInInventory()
                    .use();
                GL11.glColor4f(1, 1, 1, 1);

                // Draw cosmic overlay
                ItemRenderUtil.renderItem(type, icon);

                UniversiumShader.clear();
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);

                renderUniversiumOverlay(type, stack);
            } else {
                // RENDER ITEM
                ItemRenderUtil.renderItem(type, icon);

                int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDepthFunc(GL11.GL_EQUAL);
                shader.use();

                // RENDER COSMIC OVERLAY
                ItemRenderUtil.renderItem(type, icon);

                UniversiumShader.unbind();
                GL20.glUseProgram(program);

                // The overlay is the icon's own geometry, so GL_EQUAL still refers to the pixels the item pass
                // wrote and the sky pass kept.
                renderUniversiumOverlay(type, stack);

                GL11.glDepthFunc(GL11.GL_LEQUAL);
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }

        /**
         * Draws the item's own overlay (see {@link #registerUniversiumOverlay}) over the finished cosmic sky, so
         * the details the shader painted away come back in front of the stars. Items without an overlay render
         * exactly as they did before, and the method leaves the colour state white either way - the shader has
         * been unbound by the caller, but the GL colour is whatever the pass before it left behind.
         */
        private static void renderUniversiumOverlay(ItemRenderType type, ItemStack stack) {
            IIcon overlay = UNIVERSIUM_OVERLAYS.get(stack.getItem());
            if (overlay == null) return;

            GL11.glColor4f(1F, 1F, 1F, 1F);
            ItemRenderUtil.renderItem(type, overlay);
        }

        /**
         * Copy of {@code UniversiumRenderer#processLightLevel}: the shader is lit by the block the item is in. GT5U
         * casts {@code data[1]} straight to the entity, which only holds for some render types, so the entity is
         * looked up in the render data instead and {@link UniversiumShader#setLightLevel(float)} is the fallback.
         */
        private static void processLightLevel(UniversiumShader shader, Object... data) {
            Entity entity = null;
            for (Object argument : data) {
                // The render data also carries the renderer and the stack; only an entity can light the shader.
                if (argument instanceof Entity found) {
                    entity = found;
                    break;
                }
            }

            if (entity == null) {
                shader.setLightLevel(1.0f);
                return;
            }

            shader.setLightFromLocation(
                entity.worldObj,
                MathHelper.floor_double(entity.posX),
                MathHelper.floor_double(entity.posY),
                MathHelper.floor_double(entity.posZ));
        }

        /**
         * Copy of GT5U's {@code GlitchEffectRenderer}: for the first 40 of every 200 ten millisecond frames the icon
         * is overdrawn with a cyan and a red copy, whose offsets are re-rolled every 5 frames. GT5U's
         * {@link GlitchEffectRenderer#applyRedGlitchEffect} only draws in the inventory, so the effect is naturally
         * limited to there.
         */
        private static void renderGlitch(ItemRenderType type, IIcon icon) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);

            ItemRenderUtil.applyStandardItemTransform(type);
            GL11.glColor4f(1F, 1F, 1F, 1F);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            ItemRenderUtil.renderItem(type, icon);

            if (type == ItemRenderType.INVENTORY) {
                int frame = (int) ((System.nanoTime() % (GLITCH_FRAME_NANOS * GLITCH_LOOP_FRAMES))
                    / GLITCH_FRAME_NANOS);

                if (frame <= GLITCH_DURATION_FRAMES && frame % GLITCH_MOVE_FRAMES == 0) {
                    glitchOffsetRed = RANDOM.nextDouble() * GLITCH_MAX_OFFSET * Math.signum(RANDOM.nextGaussian());
                    glitchOffsetCyan = RANDOM.nextDouble() * GLITCH_MAX_OFFSET * Math.signum(RANDOM.nextGaussian());
                }

                if (frame <= GLITCH_DURATION_FRAMES) {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GlitchEffectRenderer.applyCyanGlitchEffect(type, glitchOffsetCyan, icon);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GlitchEffectRenderer.applyRedGlitchEffect(type, glitchOffsetRed, icon);
                }
            }

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }
}
