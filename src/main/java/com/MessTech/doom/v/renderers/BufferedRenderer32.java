/**
 * Copyright (C) 2017 Good Sign
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.MessTech.doom.v.renderers;

import static com.MessTech.doom.v.tables.ColorTint.GREY_TINTS;
import static com.MessTech.doom.v.tables.ColorTint.NORMAL_TINTS;
import static java.awt.Transparency.*;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import com.MessTech.doom.mochadoom.Loggers;
import com.MessTech.doom.v.tables.BlurryTable;
import com.MessTech.doom.v.tables.ColorTint;

/**
 * Merged with ParallelTruecolorRenderer as it fixed both bugs of parallel and single-core versions.
 *
 * Embedding change (MessTech): upstream blitted the finished frame into a VolatileImage so AWT
 * could put it on screen. That needs a live display device, and the embedding host wants the raw
 * pixels anyway, so this renderer now stops after the palette/gamma/tint post-processing and
 * hands the ARGB array out through {@link HeadlessRaster}. The BufferedImage is still created
 * because it *is* the array: `raster` is its backing DataBufferInt.
 *
 * - Good Sign 2017/04/12
 */
class BufferedRenderer32 extends SoftwareParallelVideoRenderer<byte[], int[]> implements HeadlessRaster {

    protected final int[] raster;

    protected final int transparency;
    protected final BlurryTable blurryTable;

    /**
     * This implementation will "tie" a BufferedImage to the underlying byte raster.
     *
     * NOTE: this relies on the ability to "tap" into a BufferedImage's backing array, in order to have fast writes
     * without setPixel/getPixel. If that is not possible, then we'll need to use a special renderer.
     */
    BufferedRenderer32(RendererFactory.WithWadLoader<byte[], int[]> rf) {
        super(rf, int[].class);

        transparency = rf.getBppMode().transparency;

        /**
         * A plain (unaccelerated) BufferedImage: it needs no display device, and its raster is
         * exactly the buffer the post-processing writes into.
         */
        currentscreen = new BufferedImage(width, height, transparency == TRANSLUCENT ? TYPE_INT_ARGB : TYPE_INT_RGB);
        currentscreen.setAccelerationPriority(1.0f);

        // extract raster from the created image
        raster = ((DataBufferInt) ((BufferedImage) currentscreen).getRaster()
            .getDataBuffer()).getData();

        blurryTable = new BlurryTable(liteColorMaps);

        /**
         * Create postprocess worker threads
         * 320 is dividable by 16, so any scale of it would
         */
        final int len = raster.length, chunk = len / PARALLELISM;
        for (int i = 0; i < PARALLELISM; i++) {
            paletteThreads[i] = new IntPaletteThread(i * chunk, (i + 1) * chunk);
        }
    }

    /**
     * Finishes the current frame (palette, gamma, tints) and returns it. Called by the host
     * once D_Display is done; the returned array is the live buffer, so the host must copy or
     * swap it before the next frame.
     */
    @Override
    public int[] renderFrame() {
        doWriteScreen();
        return raster;
    }

    @Override
    public void shutdown() {
        final Executor pool = executor;
        if (pool instanceof ExecutorService) {
            ((ExecutorService) pool).shutdownNow();
        }
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    /** No AWT screen here: finishing the frame and returning it is the whole job. */
    @Override
    public Image getScreenImage() {
        doWriteScreen();
        return currentscreen;
    }

    @Override
    void doWriteScreen() {
        for (int i = 0; i < PARALLELISM; i++) {
            executor.execute(paletteThreads[i]);
        }
        try {
            updateBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            Loggers.getLogger(BufferedRenderer32.class.getName())
                .log(Level.SEVERE, e, null);
        }
    }

    /**
     * Returns pure color without tinting and gamma
     */
    @Override
    public int getBaseColor(byte color) {
        return palette[color & 0xFF];
    }

    @Override
    public BlurryTable getBlurryTable() {
        return blurryTable;
    }

    /**
     * Looks monstrous. Works swiss.
     * - Good Sign 2017/04/12
     */
    private class IntPaletteThread implements Runnable {

        private final int[] FG;
        private final int start;
        private final int stop;

        IntPaletteThread(int start, int stop) {
            this.start = start;
            this.stop = stop;
            this.FG = screens.get(DoomScreen.FG);
        }

        /**
         * BFG-9000. Definitely not the pesky pistol in the Indexed renderer
         */
        @Override
        public void run() {
            final ColorTint t = (GRAYPAL_SET ? GREY_TINTS : NORMAL_TINTS).get(usepalette);
            final byte[] LUT_R = t.LUT_r8[usegamma];
            final byte[] LUT_G = t.LUT_g8[usegamma];
            final byte[] LUT_B = t.LUT_b8[usegamma];
            for (int i = start; i < stop;) {
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
                raster[i] = (FG[i] & 0xFF000000) + ((LUT_R[(FG[i] >> 16) & 0xFF] & 0xFF) << 16)
                    + ((LUT_G[(FG[i] >> 8) & 0xFF] & 0xFF) << 8)
                    + (LUT_B[FG[i++] & 0xFF] & 0xFF);
            }
            try {
                updateBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                Loggers.getLogger(BufferedRenderer32.class.getName())
                    .log(Level.WARNING, e, null);
            }
        }
    }
}
