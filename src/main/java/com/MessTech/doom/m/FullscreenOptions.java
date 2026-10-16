package com.MessTech.doom.m;

/**
 * Headless carve-out of awt.FullscreenOptions: only the three option enums are
 * referenced by m.Settings. The AWT full-screen switching code is not ported.
 */
public interface FullscreenOptions {

    enum InterpolationMode {
        Nearest,
        Bilinear,
        Biqubic;
    }

    enum FullMode {
        Best,
        Native;
    }

    enum StretchMode {
        Centre,
        Stretch,
        Fit,
        Aspect_4_3;
    }
}
