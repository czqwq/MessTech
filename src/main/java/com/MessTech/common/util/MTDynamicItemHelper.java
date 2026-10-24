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
 * Helper for "dynamic" items: one item, several looks, picked through {@link ItemStack#getItemDamage()}.
 * <p>
 * Per {@link Effect}: one icon ({@link #registerIcons}), one renderer ({@link #registerItemRenderer}); damage value =
 * effect index, so {@link #cycleEffect(ItemStack)} (shift + right click) walks the enum.
 * <p>
 * Each {@link Effect} copies a GT5U animated renderer, so the looks match the GT items:
 * <ul>
 * <li>{@link Style#TUMBLE} - {@code TranscendentMetalRenderer}: quad tumbles about the oblique axis
 * (0.3, 0.5, 0.2), 3.5 degrees per client tick.</li>
 * <li>{@link Style#HALO_PULSE} - {@code InfinityRenderer}: dark halo behind the icon plus a randomly scaling
 * translucent copy on top (Infinity, Eternity, MagMatter: strips animate, renderer pulses).</li>
 * <li>{@link Style#UNIVERSIUM} - {@code UniversiumRenderer}: icon, then gtnhlib's cosmic star shader (alpha only); a
 * second icon from {@link #registerUniversiumOverlay} restores the colour.</li>
 * <li>{@link Style#GLITCH} - {@code GlitchEffectRenderer}: red and cyan ghosts jittering around the icon, 10 ticks
 * every 100.</li>
 * </ul>
 * One effect per item by design; no stacking.
 * <p>
 * Icons are pre-rendered per material (see {@code assets/messtech/textures/items/pigs}): {@code GL11.glColor4f} would
 * flatten a 32x32 pixel-art icon's shading, so the look is baked into the PNG; animated effects need their own
 * {@code .mcmeta}, all icons of one item the same frame size.
 * <p>
 * Icons live under {@code textures/items/} (atlas-added): {@code pigs/pig} ->
 * {@code assets/messtech/textures/items/pigs/pig.png}; an {@code items/} prefix gives
 * {@code textures/items/items/...}, i.e. the purple/black missing texture.
 */
public final class MTDynamicItemHelper {

    /** Icon domain: {@code assets/messtech/textures/}. */
    public static final String TEXTURE_DOMAIN = "messtech";

    /** {@link #registerUniversiumOverlay} icon suffix: {@code pigs/pig} -> {@code pigs/pigUniversiumFace}. */
    public static final String UNIVERSIUM_OVERLAY_SUFFIX = "UniversiumFace";

    private static final Random RANDOM = new Random();

    /** Glitch timing, in 10 ms frames. */
    private static final long GLITCH_FRAME_NANOS = 10_000_000L;
    private static final int GLITCH_LOOP_FRAMES = 200;
    private static final int GLITCH_DURATION_FRAMES = 40;
    private static final int GLITCH_MOVE_FRAMES = 5;
    private static final double GLITCH_MAX_OFFSET = 1.7D;

    /** Offsets {@code GlitchEffectRenderer} keeps per instance; the shared renderer holds one. */
    private static double glitchOffsetRed;
    private static double glitchOffsetCyan;

    /**
     * Oblique axis of the {@link Style#TUMBLE} spin, as {@code GL11.glRotatef} takes it (GT5U's
     * {@code TranscendentMetalRenderer}); public for {@code MTPiggyHatRenderer}, which tumbles a worn item too.
     */
    public static final float TUMBLE_AXIS_X = 0.3F, TUMBLE_AXIS_Y = 0.5F, TUMBLE_AXIS_Z = 0.2F;

    /** Degrees per client tick, GT5U's number. */
    private static final float TUMBLE_DEGREES_PER_TICK = 3.5F;

    private MTDynamicItemHelper() {}

    /**
     * Current {@link Style#TUMBLE} angle in degrees; one clock for inventory, head and player model so they cannot
     * drift, and {@link GTMod#clientProxy()}'s so it stops with the game.
     */
    @SideOnly(Side.CLIENT)
    public static float tumbleAngle() {
        return (GTMod.clientProxy()
            .getAnimationRenderTicks() * TUMBLE_DEGREES_PER_TICK) % 360F;
    }

    /**
     * @return whether the stack wears the Transcendent Metal tumble now, GT5U's fancy-graphics switch included; a
     *         helmet-wearing player tumbles too.
     */
    @SideOnly(Side.CLIENT)
    public static boolean isTumbling(ItemStack stack) {
        return stack != null && getEffect(stack).style == Style.TUMBLE && isFancyEnabled(Style.TUMBLE);
    }

    /** How an {@link Effect} is drawn. */
    public enum Style {

        /** The icon through GT5U's plain-material pipeline. */
        PLAIN,
        TUMBLE,
        HALO_PULSE,
        UNIVERSIUM,
        GLITCH
    }

    /**
     * Looks an item cycles through, in player-visible order; the ordinal is the damage value, so {@link #NONE} must
     * stay at index 0 (fallback for unregistered/damaged stacks).
     */
    public enum Effect {

        NONE("", "plain", Style.PLAIN),
        /** GT5U Transcendent Metal, dark metal tint. */
        TRANSCENDENT_METAL("TranscendentMetal", "transcendentMetal", Style.TUMBLE),
        /** GT5U Infinity, rainbow-ish strip. */
        INFINITY("Infinity", "infinity", Style.HALO_PULSE),
        /** GT5U MagMatter, greyscale flowing pattern. */
        MAGMATTER("MagMatter", "magMatter", Style.HALO_PULSE),
        /** GT5U Eternity, animated strip. */
        ETERNITY("Eternity", "eternity", Style.HALO_PULSE),
        /** GT5U Universium, cosmic star shader. */
        UNIVERSIUM("Universium", "universium", Style.UNIVERSIUM),
        /** GT5U Six-Phased Copper, ghost copies. */
        SIX_PHASED_COPPER("SixPhasedCopper", "sixPhasedCopper", Style.GLITCH);

        /** {@code values()} clones its array each call; cached. */
        public static final Effect[] VALUES = values();

        /** Appended to the base icon path. */
        public final String iconSuffix;
        /** Suffix of the "messtech.itemEffect." lang key. */
        public final String langSuffix;
        public final Style style;

        Effect(String iconSuffix, String langSuffix, Style style) {
            this.iconSuffix = iconSuffix;
            this.langSuffix = langSuffix;
            this.style = style;
        }

        public String getDisplayName() {
            return StatCollector.translateToLocal("messtech.itemEffect." + langSuffix);
        }

        /** @return effect for that damage value, {@link #NONE} out of range. */
        public static Effect byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : NONE;
        }

        /** @return the next effect, wrapping at the end. */
        public Effect next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    /**
     * Registers one icon per {@link Effect} from a base path, e.g. {@code "pigs/pig"} -> {@code pig},
     * {@code pigTranscendentMetal}, {@code pigInfinity}, ...
     * <p>
     * Indexed by {@link Effect#ordinal()}, i.e. the damage value, so hand the array over as-is.
     *
     * @param basePath icon path without domain, per-effect suffix or {@code items/} prefix (the atlas prepends
     *                 {@code textures/items/})
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
     * Registers the icon over an item's {@link Style#UNIVERSIUM} look, beside the per-effect icons of
     * {@link #registerIcons}: {@code pigs/pig} -> {@code assets/messtech/textures/items/pigs/pigUniversiumFace.png}.
     * <p>
     * The shader keeps only the icon's alpha and paints the sky over the rest, so detail (piggy eyes, snout) ends up
     * behind the stars; this icon goes on top of the finished shader. Dark sky, so pass untinted art.
     *
     * @param item     as given to {@link #registerItemRenderer}
     * @param register the register {@link Item#registerIcons} gets
     * @param basePath as given to {@link #registerIcons}
     */
    @SideOnly(Side.CLIENT)
    public static void registerUniversiumOverlay(Item item, IIconRegister register, String basePath) {
        String iconName = basePath + UNIVERSIUM_OVERLAY_SUFFIX;
        warnIfIconFileMissing(iconName);
        EffectRenderer.UNIVERSIUM_OVERLAYS.put(item, register.registerIcon(TEXTURE_DOMAIN + ":" + iconName));
    }

    /**
     * Logs a warning when an icon PNG is missing from the resource pack.
     * <p>
     * 1.7.10 replaced the "using missing texture" error with FML's silent tracking ({@code TextureMap} line 189): a
     * bad {@code basePath} only shows as the purple/black board, which this logs.
     *
     * @param iconName path below {@code textures/items}, no extension
     */
    @SideOnly(Side.CLIENT)
    private static void warnIfIconFileMissing(String iconName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        IResourceManager resources = minecraft == null ? null : minecraft.getResourceManager();
        if (resources == null) return;

        ResourceLocation file = new ResourceLocation(TEXTURE_DOMAIN, "textures/items/" + iconName + ".png");
        try {
            // 1.7.10 has no IResourceManager#resourceExists, so the lookup is the existence check.
            resources.getResource(file);
        } catch (IOException e) {
            MessTech.MT_LOG
                .warn("Dynamic item icon {} is missing, the item will show the purple/black missing texture", file);
        }
    }

    /** @return the stack's effect, {@link Effect#NONE} for a null stack. */
    public static Effect getEffect(ItemStack stack) {
        return stack == null ? Effect.NONE : Effect.byIndex(stack.getItemDamage());
    }

    public static void setEffect(ItemStack stack, Effect effect) {
        if (stack != null) stack.setItemDamage(effect.ordinal());
    }

    /**
     * Switches the stack to the next effect, wrapping around.
     *
     * @return the effect shown afterwards
     */
    public static Effect cycleEffect(ItemStack stack) {
        Effect next = getEffect(stack).next();
        setEffect(stack, next);
        return next;
    }

    /**
     * Registers the shared effect renderer: icon from {@link Item#getIcon(ItemStack, int)}, effect from the damage
     * value; no renderer class per item.
     */
    @SideOnly(Side.CLIENT)
    public static void registerItemRenderer(Item item) {
        MinecraftForgeClient.registerItemRenderer(item, new EffectRenderer());
    }

    /**
     * Draws a stack standing on the wearer's head, in player-model space: the {@code RenderPlayerEvent.Specials} one,
     * where vanilla puts the pumpkin and skull ({@code MTPiggyHatRenderer} sets it up).
     * <p>
     * Sprite on the matrix origin (feet there, top one unit up), facing the wearer's way; the effect decides the
     * rest:
     * <ul>
     * <li>{@link Style#TUMBLE}: the item renderer's tumble about the sprite centre.</li>
     * <li>{@link Style#UNIVERSIUM}: its cosmic shader, the same three passes as in the world, overlay last.</li>
     * <li>Else: the sprite alone - GT5U draws {@link Style#HALO_PULSE}'s halo/pulse and {@link Style#GLITCH}'s ghosts
     * in inventory only; other strips animate themselves.</li>
     * </ul>
     *
     * @param wearer its position lights the cosmic shader
     */
    @SideOnly(Side.CLIENT)
    public static void renderOnHead(Entity wearer, ItemStack stack) {
        EffectRenderer.renderOnHead(wearer, stack);
    }

    /**
     * Honours GT5U's "fancy" switches: an effect a player turned off draws the plain icon, as with the
     * {@code GeneratedMaterialRenderer} fallback in GT5U.
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
     * Renderer shared by every item registered through {@link #registerItemRenderer}; the only state is the glitch
     * offsets, which GT5U also keeps per renderer.
     */
    @SideOnly(Side.CLIENT)
    private static final class EffectRenderer implements IItemRenderer {

        /**
         * Icons of {@link #registerUniversiumOverlay}, keyed by item. Client only, so not in the outer class the
         * server loads.
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

        /**
         * Draws the stack's look standing on a head; the outer {@link MTDynamicItemHelper#renderOnHead} covers the
         * caller's setup. Sprite from {@link Item#getIcon(ItemStack, int)}.
         */
        private static void renderOnHead(Entity wearer, ItemStack stack) {
            if (stack == null || stack.getItem() == null) return;
            IIcon icon = stack.getItem()
                .getIcon(stack, 0);
            if (icon == null) return;

            Style style = getEffect(stack).style;
            if (!isFancyEnabled(style)) style = Style.PLAIN;

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            GL11.glColor4f(1F, 1F, 1F, 1F);

            switch (style) {
                case TUMBLE -> {
                    GL11.glPushMatrix();
                    applyHeadTumbleTransform();
                    drawStandingSprite(icon);
                    GL11.glPopMatrix();
                }
                case UNIVERSIUM -> renderHeadUniversium(wearer, stack, icon);
                default -> drawStandingSprite(icon);
            }
        }

        /**
         * {@link #applyTumbleTransform} on a sprite standing on a head: same angles, but about the sprite centre, half
         * a unit up. Y and Z are mirrored because model space is mirrored (its Y points down), which keeps the tumble
         * turning as in inventory and hand.
         */
        private static void applyHeadTumbleTransform() {
            GL11.glTranslatef(0.0F, -0.5F, 0.0F);
            GL11.glRotatef(tumbleAngle(), TUMBLE_AXIS_X, -TUMBLE_AXIS_Y, -TUMBLE_AXIS_Z);
            GL11.glTranslatef(0.0F, 0.5F, 0.0F);
        }

        /**
         * In-world half of {@link #renderUniversium} on a head: sprite once, then the cosmic shader over the same
         * geometry with {@link GL11#GL_EQUAL} (stars on the sprite, not the head behind it), then the stack's overlay.
         */
        private static void renderHeadUniversium(Entity wearer, ItemStack stack, IIcon icon) {
            UniversiumShader shader = UniversiumShader.getInstance();
            processLightLevel(shader, wearer);

            drawStandingSprite(icon);

            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            shader.use();

            drawStandingSprite(icon);

            UniversiumShader.unbind();
            GL20.glUseProgram(program);

            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_ALPHA_TEST);

            // Same geometry as the sprite pass, so GL_EQUAL still matches its pixels.
            IIcon overlay = UNIVERSIUM_OVERLAYS.get(stack.getItem());
            if (overlay == null) return;

            GL11.glColor4f(1F, 1F, 1F, 1F);
            drawStandingSprite(overlay);
        }

        /**
         * Draws a sprite standing on the matrix origin: feet on {@code y = 0}, top at {@code y = -1}, width from the
         * icon's aspect - the trick for characters drawn standing in their picture (the pig).
         * <p>
         * Mirrored because model space is: it grows towards {@code -Y} (up is down there), faces {@code -Z}, the
         * wearer's face; left to right stays usual - mirrored X cancels the turn into the facing - so the texture is
         * not flipped.
         */
        private static void drawStandingSprite(IIcon icon) {
            double halfWidth = 0.5D * icon.getIconWidth() / icon.getIconHeight();
            float minU = icon.getMinU(), maxU = icon.getMaxU(), minV = icon.getMinV(), maxV = icon.getMaxV();

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setNormal(0.0F, 0.0F, -1.0F);
            tessellator.addVertexWithUV(-halfWidth, 0.0D, 0.0D, minU, maxV);
            tessellator.addVertexWithUV(halfWidth, 0.0D, 0.0D, maxU, maxV);
            tessellator.addVertexWithUV(halfWidth, -1.0D, 0.0D, maxU, minV);
            tessellator.addVertexWithUV(-halfWidth, -1.0D, 0.0D, minU, minV);
            tessellator.draw();
        }

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
         * Copy of GT5U's {@code TranscendentMetalRenderer}: the quad via
         * {@link net.minecraft.client.renderer.ItemRenderer#renderItemIn2D}, not the vanilla pipeline, so it can
         * tumble;
         * the angle comes from {@link GTMod#clientProxy()}, so it stops with the game.
         */
        private static void renderTumble(ItemRenderType type, IIcon icon) {
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

            // Same order as GT5U's MetaGeneratedItemRenderer: transform, then fake fancy graphics (vanilla RenderItem
            // numbers) if off.
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
                // Inventory space is 1 unit = 1 GUI pixel; renderItemIn2D draws a 1x1 quad.
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

        /** Transcendent Metal's rotation, copied: pivot on the quad centre, tumble, flip over. */
        private static void applyTumbleTransform(ItemRenderType type) {
            if (RenderItem.renderInFrame) {
                // Float in front of the item frame, else z-fighting with the border.
                GL11.glTranslatef(0.0F, 0.0F, -0.5F);
            }

            if (type == ItemRenderType.INVENTORY) {
                GL11.glTranslatef(8F, 8F, 0F);
            } else {
                GL11.glTranslatef(0.5F, 0.5F, 0.0F);
            }

            GL11.glRotatef(tumbleAngle(), TUMBLE_AXIS_X, TUMBLE_AXIS_Y, TUMBLE_AXIS_Z);

            GL11.glRotatef(180F, 0.5F, 0.0F, 0.0F);

            if (type == ItemRenderType.INVENTORY) {
                GL11.glTranslatef(-8F, -8F, 0F);
            } else {
                GL11.glTranslatef(-0.5F, -0.5F, 0.0F);
            }

            // Move the quad onto the rotation axis (half its thickness).
            GL11.glTranslatef(0.0F, 0.0F, 0.03125F);
        }

        /**
         * {@code InfinityRenderer}: in the inventory a dark halo behind the icon and a randomly scaling translucent
         * copy on top; elsewhere both are skipped, as in GT5U ({@link ItemRenderType#INVENTORY} only).
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

        /** {@code InfinityRenderer#renderHalo}: a 10 pixel black spread around the 16x16 icon quad. */
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

        /** {@code InfinityRenderer#renderPulse}: the icon at 60% alpha, scaled by a gaussian. */
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
         * Copy of GT5U's {@code UniversiumRenderer}: the icon once, then gtnhlib's cosmic star shader over the same
         * geometry. In inventory the second pass paints over the first (no depth buffer to test); outside, the depth
         * function becomes {@link GL11#GL_EQUAL}, so stars land only on the icon.
         * <p>
         * Opaque icon pixels become the shader's colour, hiding the art; {@link #registerUniversiumOverlay} items get a
         * third pass after the sky, with the icon that brings the details back.
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

                ItemRenderUtil.renderItem(type, icon);

                UniversiumShader.clear();
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);

                renderUniversiumOverlay(type, stack);
            } else {
                ItemRenderUtil.renderItem(type, icon);

                int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDepthFunc(GL11.GL_EQUAL);
                shader.use();

                ItemRenderUtil.renderItem(type, icon);

                UniversiumShader.unbind();
                GL20.glUseProgram(program);

                // Same geometry as the item pass, so GL_EQUAL still matches its pixels.
                renderUniversiumOverlay(type, stack);

                GL11.glDepthFunc(GL11.GL_LEQUAL);
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }

        /**
         * Draws the item's overlay (see {@link #registerUniversiumOverlay}) over the finished sky, so what the shader
         * painted away comes back in front of the stars; no overlay renders unchanged. Colour is left white either way
         * - the GL colour is whatever the previous pass left.
         */
        private static void renderUniversiumOverlay(ItemRenderType type, ItemStack stack) {
            IIcon overlay = UNIVERSIUM_OVERLAYS.get(stack.getItem());
            if (overlay == null) return;

            GL11.glColor4f(1F, 1F, 1F, 1F);
            ItemRenderUtil.renderItem(type, overlay);
        }

        /**
         * {@code UniversiumRenderer#processLightLevel}: the shader is lit by the block the item is in. GT5U casts
         * {@code data[1]} to the entity, true only for some render types, so the render data is searched;
         * {@link UniversiumShader#setLightLevel(float)} is the fallback.
         */
        private static void processLightLevel(UniversiumShader shader, Object... data) {
            Entity entity = null;
            for (Object argument : data) {
                // Render data also holds the renderer and the stack; only an entity can light the shader.
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
         * Copy of GT5U's {@code GlitchEffectRenderer}: for the first 40 of every 200 ten millisecond frames the icon is
         * overdrawn with cyan and red copies, offsets re-rolled every 5 frames.
         * {@link GlitchEffectRenderer#applyRedGlitchEffect} draws only in the inventory, so the effect stays there.
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
