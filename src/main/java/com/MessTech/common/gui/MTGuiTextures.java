package com.MessTech.common.gui;

import com.cleanroommc.modularui.drawable.UITexture;

/**
 * MUI2 GUI textures for MessTech.
 */
public class MTGuiTextures {

    public static final String ASSET_PREFIX = "messtech";

    public static final UITexture PICTURE_MT_LOGO = UITexture.builder()
        .location(ASSET_PREFIX, "gui/picture/mt_logo")
        .fullImage()
        .name("messtech:mt_logo")
        .build();

    public static final UITexture PICTURE_MT_SPACE = UITexture.builder()
        .location(ASSET_PREFIX, "gui/picture/mt_space")
        .fullImage()
        .name("messtech:mt_space")
        .build();
}
