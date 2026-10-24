package com.MessTech.common.mixin;

/**
 * Mods that can be used as target/exclusion filters for MessTech mixins.
 */
@SuppressWarnings("unused")
public enum TargetMod {

    ANGELICA("Angelica", "angelica"),
    GTNH_INTERGALACTIC("GTNH Intergalactic", "gtnhintergalactic"),
    NOT_ENOUGH_ITEMS("Not Enough Items", "NotEnoughItems"),
    SPICE_OF_LIFE("The Spice of Life - Carrot Edition", "SpiceOfLife");

    private final String modName;
    private final String modId;

    TargetMod(String modName, String modId) {
        this.modName = modName;
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }

    public String getModName() {
        return modName;
    }
}
