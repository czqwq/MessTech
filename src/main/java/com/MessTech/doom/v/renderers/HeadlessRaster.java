package com.MessTech.doom.v.renderers;

/**
 * Implemented by the bit-depth renderers that can finish a frame without an AWT display
 * device: they post-process the palette, gamma and tint into a plain ARGB array and hand
 * it out instead of blitting it into a VolatileImage.
 */
public interface HeadlessRaster {

    /** Runs the post-processing and returns the finished 0xAARRGGBB frame (never null once sized). */
    int[] renderFrame();

    int getWidth();

    int getHeight();

    /**
     * Releases the post-processing worker threads. A host that tears the engine down has to
     * call this, the pool threads are not daemons and would otherwise outlive the session.
     */
    void shutdown();
}
