package com.MessTech.client.text.effect;

import java.util.List;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.MessTech.client.text.TextEffect;
import com.MessTech.client.text.TextMaskCache.Mask;
import com.MessTech.client.text.TextRenderContext;

/**
 * A fragment shader plus its default palette, registered under one effect id.
 * <p>
 * The shared uniform block comes from {@code common.glsl}: the mask texture and its extents in text space, the bounding
 * box, the shared clock and the palette. The shadow pass stays a plain dark copy of the mask, so effect files only
 * describe the lit text.
 */
public class ShaderTextEffect implements TextEffect {

    private static final String[] PALETTE_UNIFORMS = { "palette[0]", "palette[1]", "palette[2]", "palette[3]",
        "palette[4]", "palette[5]", "palette[6]", "palette[7]" };
    private static final float SHADOW_ALPHA = 0.62F;

    private final TextShader shader;
    private final int[] colors;
    private final float paddingScale;

    public ShaderTextEffect(ResourceLocation fragment, int... colors) {
        this(fragment, 1, colors);
    }

    public ShaderTextEffect(ResourceLocation fragment, float paddingScale, int... colors) {
        if (colors.length == 0 || colors.length > PALETTE_UNIFORMS.length)
            throw new IllegalArgumentException("A palette needs one to eight colors");
        if (!Float.isFinite(paddingScale) || paddingScale < 0)
            throw new IllegalArgumentException("Padding scale must be finite and nonnegative");
        this.shader = new TextShader(fragment);
        this.colors = colors.clone();
        this.paddingScale = paddingScale;
    }

    @Override
    public float padding(float height) {
        return height * paddingScale;
    }

    @Override
    public int fallbackColor() {
        return colors[0];
    }

    @Override
    public void render(TextRenderContext context) {
        if (context.shadow()) drawShadow(context);
        else drawLit(context);
    }

    private void drawLit(TextRenderContext context) {
        Mask mask = context.mask();
        shader.use();
        List<Integer> overrides = context.style()
            .colors();
        int count = overrides.isEmpty() ? colors.length : overrides.size();
        GL20.glUniform1i(shader.uniform("textMask"), 0);
        GL20.glUniform1i(shader.uniform("paletteCount"), count);
        GL20.glUniform1i(shader.uniform("paletteOverridden"), overrides.isEmpty() ? 0 : 1);
        GL20.glUniform2f(shader.uniform("textOrigin"), context.x(), context.y());
        for (int i = 0; i < PALETTE_UNIFORMS.length; i++) {
            int color = overrides.isEmpty() ? colors[Math.min(i, colors.length - 1)]
                : overrides.get(Math.min(i, overrides.size() - 1));
            GL20.glUniform3f(
                shader.uniform(PALETTE_UNIFORMS[i]),
                (color >> 16 & 255) / 255F,
                (color >> 8 & 255) / 255F,
                (color & 255) / 255F);
        }
        GL20.glUniform2f(shader.uniform("maskSize"), mask.textureWidth(), mask.textureHeight());
        GL20.glUniform1f(shader.uniform("maskResolution"), mask.resolution());
        GL20.glUniform2f(shader.uniform("textSize"), Math.max(1, mask.width()), Math.max(1, mask.height()));
        GL20.glUniform1f(shader.uniform("padding"), mask.padding());
        GL20.glUniform1f(
            shader.uniform("time"),
            (float) (context.seconds() * context.style()
                .speed() % 3600));
        GL20.glUniform1f(shader.uniform("opacity"), (context.color() >>> 24) / 255F);
        GL20.glUniform1i(shader.uniform("shadowPass"), 0);
        bindMask(mask.target().framebufferTexture);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        quad(mask, context);
    }

    private void drawShadow(TextRenderContext context) {
        Mask mask = context.mask();
        GL20.glUseProgram(0);
        bindMask(mask.target().framebufferTexture);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper
            .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // The mask's RGB channels carry glyph metadata, so a black color multiplies them away and only coverage is
        // left.
        // The shadow follows the text color's alpha, so fading text fades its shadow too.
        GL11.glColor4f(0F, 0F, 0F, SHADOW_ALPHA * ((context.color() >>> 24) / 255F));
        try {
            quad(mask, context);
        } finally {
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    private static void bindMask(int texture) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void quad(Mask mask, TextRenderContext context) {
        float left = context.x() - mask.padding();
        float top = context.y() - mask.padding();
        float right = left + mask.textureWidth();
        float bottom = top + mask.textureHeight();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0F, 1F);
        GL11.glVertex3f(left, top, 0F);
        GL11.glTexCoord2f(0F, 0F);
        GL11.glVertex3f(left, bottom, 0F);
        GL11.glTexCoord2f(1F, 0F);
        GL11.glVertex3f(right, bottom, 0F);
        GL11.glTexCoord2f(1F, 1F);
        GL11.glVertex3f(right, top, 0F);
        GL11.glEnd();
    }

    @Override
    public void close() {
        shader.close();
    }
}
