package com.MessTech.common.material;

import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IMaterialHandler;

/**
 * The Rainbow material (彩虹): nothing but the name and the three tool numbers the Rainbow Wrench (彩虹小扳手) is
 * built from.
 * <p>
 * The wrench is a {@code MetaGeneratedTool} entry ({@link com.MessTech.common.items.MTRainbowWrench}), and a GT tool
 * stack has to name a primary material in its {@code GT.ToolStats} NBT: that name is what the tooltip shows next to the
 * harvest level, and it is what GT multiplies the tool's own stats with. This material therefore exists only to be that
 * carrier, and its numbers are chosen so that they do not fight the wrench's:
 * <ul>
 * <li><b>durability = {@link Integer#MAX_VALUE}</b>: every stack made from the material starts effectively unbreakable;
 * the obtainable wrench is then pinned to exactly {@code Integer.MAX_VALUE} by
 * {@link com.MessTech.common.items.MTRainbowWrench#prepareStack}.</li>
 * <li><b>quality = 0</b>: the wrench's 325 harvest level comes from
 * {@link com.MessTech.common.items.tools.ToolRainbowWrench#getBaseQuality()} instead - GT's harvest level is
 * {@code getBaseQuality() + mToolQuality}, and {@code mToolQuality} is a byte, so 325 would not fit here.</li>
 * <li><b>speed = 1.0</b>: the wrench's 999 mining speed is {@code getSpeedMultiplier() * mToolSpeed} on the tool side,
 * so the material factor must be neutral.</li>
 * </ul>
 * No items are generated for it (no {@code add*Items()} call): it is a stat carrier, not a metal.
 * <p>
 * <b>Registration timing</b> is the same as {@link MTChaosMatter}'s: the handler goes in from the {@code @Mod}
 * constructor, because GT's {@code Materials.init()} runs in GT's own preInit (before this mod's) and only calls
 * {@link IMaterialHandler#onMaterialsInit()} from there.
 */
public final class MTRainbow {

    /**
     * Internal material name, namespaced on purpose: {@code Materials} keys its map by this name, and a generic
     * "Rainbow" could collide with another addon's material. The display name is the lang key
     * {@code Material.rainbowwrench}.
     */
    public static final String NAME = "RainbowWrench";

    /** Fallback name used where the lang file has no entry. */
    public static final String DEFAULT_LOCAL_NAME = "Rainbow";

    /**
     * The three values handed to {@link MaterialBuilder#setTool(int, int, float)}; see the class javadoc for why the
     * durability is {@link Integer#MAX_VALUE}, the quality 0 and the speed 1.0.
     */
    public static final int TOOL_DURABILITY = Integer.MAX_VALUE;
    public static final int TOOL_QUALITY = 0;
    public static final float TOOL_SPEED = 1.0F;

    /** The registered material, or null before GT's {@code Materials.init()} ran. */
    private static Materials material;

    private MTRainbow() {}

    /** Adds the material handler. Must be called from the {@code @Mod} constructor, see the class javadoc. */
    public static void register() {
        Materials.add(new RainbowHandler());
    }

    public static Materials getMaterial() {
        return material;
    }

    private static final class RainbowHandler implements IMaterialHandler {

        @Override
        public void onMaterialsInit() {
            material = new MaterialBuilder().setName(NAME)
                .setDefaultLocalName(DEFAULT_LOCAL_NAME)
                // Opaque, so nothing can read a transparent material colour; no items are drawn with it anyway.
                .setARGB(0xFFFF55FF)
                .setTool(TOOL_DURABILITY, TOOL_QUALITY, TOOL_SPEED)
                .constructMaterial();
        }
    }
}
