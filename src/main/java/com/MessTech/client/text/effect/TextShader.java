package com.MessTech.client.text.effect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** A text fragment shader compiled on first use, with the shared vertex stage and {@code #include} support. */
public class TextShader implements AutoCloseable {

    public static final String RESOURCE_DOMAIN = "messtech";
    public static final ResourceLocation VERTEX = new ResourceLocation(RESOURCE_DOMAIN, "shaders/text/text.vert.glsl");

    private static final Pattern INCLUDE = Pattern.compile("(?m)^[\\t ]*#include[\\t ]+\"([^\"]+)\"[\\t ]*\\r?$");
    private static final int LOG_LENGTH = 8192;

    private final ResourceLocation fragment;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private int program;

    public TextShader(ResourceLocation fragment) {
        this.fragment = fragment;
    }

    /** Binds the program, compiling and linking it the first time it is needed. */
    public void use() {
        if (program == 0) compile();
        GL20.glUseProgram(program);
    }

    public int uniform(String name) {
        return uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key));
    }

    private void compile() {
        int vertex = 0;
        int pixel = 0;
        int linked = 0;
        try {
            vertex = compileStage(GL20.GL_VERTEX_SHADER, source(VERTEX, new HashSet<>()));
            pixel = compileStage(GL20.GL_FRAGMENT_SHADER, source(fragment, new HashSet<>()));
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, pixel);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked, LOG_LENGTH));
            }
            program = linked;
            linked = 0;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read text shader " + fragment, exception);
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (pixel != 0) GL20.glDeleteShader(pixel);
            if (linked != 0) GL20.glDeleteProgram(linked);
        }
    }

    private static int compileStage(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(shader, LOG_LENGTH);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    private static String read(ResourceLocation path) throws IOException {
        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        try (InputStream stream = manager.getResource(path)
            .getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
            byte[] buffer = new byte[4096];
            for (int read; (read = stream.read(buffer)) >= 0;) bytes.write(buffer, 0, read);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Expands {@code #include "name.glsl"} relative to the including file, rejecting include cycles. */
    private static String source(ResourceLocation path, Set<ResourceLocation> active) throws IOException {
        if (!active.add(path)) throw new IOException("Cyclic shader include: " + path);
        try {
            Matcher matcher = INCLUDE.matcher(read(path));
            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                String include = matcher.group(1);
                String directory = path.getResourcePath()
                    .substring(
                        0,
                        path.getResourcePath()
                            .lastIndexOf('/') + 1);
                ResourceLocation child = include.indexOf(':') >= 0 ? new ResourceLocation(include)
                    : new ResourceLocation(path.getResourceDomain(), directory + include);
                matcher.appendReplacement(result, Matcher.quoteReplacement(source(child, active)));
            }
            matcher.appendTail(result);
            return result.toString();
        } finally {
            active.remove(path);
        }
    }

    @Override
    public void close() {
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        uniforms.clear();
    }
}
