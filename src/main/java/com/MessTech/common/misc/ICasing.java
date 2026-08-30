package com.MessTech.common.misc;

import static gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import gregtech.api.casing.ICasingGroup;
import gregtech.api.interfaces.ITexture;
import gregtech.api.structure.IStructureInstance;

public interface ICasing extends ImmutableBlockMeta {

    /**
     * Gets the casing texture id. Used to update the background of hatches. If this casing does not have a texture id,
     * this method must throw an {@link UnsupportedOperationException} or an equivalent exception.
     */
    int getTextureId();

    /**
     * Gets a tiered casing background texture, if possible. Defaults to the standard untiered background.
     */
    default <T> int getTextureId(T t, CasingElementContext<T> context) {
        return getTextureId();
    }

    /**
     * Gets an ITexture for this casing. May return a valid value when {@link #getTextureId()} does not since textures
     * do not have to be registered in the GT texture index.
     */
    default ITexture getCasingTexture() {
        return getCasingTextureForId(getTextureId());
    }

    /**
     * The context for converting an ICasing to an IStructureElement. This exists primarily to make refactoring easier
     * if we ever need to include another field here.
     */
    interface CasingElementContext<T> {

        ICasingGroup getGroup();

        /** Gets the structure instance from the generic context object (which is likely a multi). */
        IStructureInstance<T> getInstance(T t);
    }

}
