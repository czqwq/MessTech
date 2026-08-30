package com.MessTech.common.mixin;

/**
 * Mods that can be used as target/exclusion filters for MessTech mixins.
 */
@SuppressWarnings("unused")
public enum TargetMod {

    GTNH_INTERGALACTIC("GTNH Intergalactic", "gtnhintergalactic");

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
