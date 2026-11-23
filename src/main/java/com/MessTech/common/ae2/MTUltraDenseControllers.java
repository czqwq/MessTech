package com.MessTech.common.ae2;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.block.AEBaseItemBlock;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registration of the two ultra dense ME controllers and the pathing side state the AE2 mixins share.
 *
 * <p>
 * The blocks are plain subclasses of AE2's own controller blocks, so the only tile entity work here is: nothing. AE2
 * already registered {@code TileController} / {@code TileCreativeEnergyController} and both block constructors set
 * them as their tile entity classes, which is exactly why the blocks reuse AE2's controller logic (see
 * {@code docs/ultra-ae2.md} §1.1). {@code AEBaseItemBlock} is used as the item so tooltips are delegated to the block,
 * including AE2's own tooltip of the neutron infused controller.
 *
 * <p>
 * AE2 limits every controller face to {@code CONTROLLER_FACE_CHANNELS = 32} inside
 * {@code appeng.me.pathfinding.PathingCalculation}. The patching is done by two mixins:
 * {@code MixinPathingCalculation_UltraDense} detects whether the grid being computed contains one of these blocks and
 * publishes that on the current thread through {@link #beginUltraDensePathing(boolean)}, and
 * {@code MixinControllerFace_UltraDense} raises the capacity of every {@code ControllerFace} created in that window.
 * A {@link ThreadLocal} is used because only the thread running the calculation takes part and a plain static would
 * leak the flag into unrelated grids computed at the same time.
 *
 * <p>
 * Note that the effect is grid wide: if a network mixes an ultra dense controller with stock ones, the stock
 * controller faces are raised as well. That is the documented, accepted behaviour (the mix is extremely rare, see
 * {@code docs/ultra-ae2.md} §1.4).
 */
public final class MTUltraDenseControllers {

    /** Registry name of the 超致密ME控制器. */
    public static final String ULTRA_DENSE_ME_CONTROLLER = "ultraDenseMeController";
    /** Registry name of the 超致密ME中子注入控制器. */
    public static final String ULTRA_DENSE_NEUTRON_CONTROLLER = "ultraDenseNeutronController";
    /** Channels every controller face of an ultra dense controller grid carries: the requested 2.1G (int max). */
    public static final int ULTRA_DENSE_CONTROLLER_FACE_CHANNELS = Integer.MAX_VALUE;

    /**
     * Whether the {@code PathingCalculation} constructor currently running on this thread belongs to a grid with an
     * ultra dense controller. Set by the HEAD injection and cleared by the RETURN injection.
     */
    private static final ThreadLocal<Boolean> ULTRA_DENSE_PATHING = new ThreadLocal<>();

    private MTUltraDenseControllers() {}

    /** Registers both blocks with their item blocks. Called from {@code MTUltraAeContent#registerBlocks()}. */
    public static void registerBlocks() {
        GameRegistry
            .registerBlock(MTUltraDenseControllerBlock.INSTANCE, AEBaseItemBlock.class, ULTRA_DENSE_ME_CONTROLLER);
        GameRegistry.registerBlock(
            MTUltraDenseNeutronControllerBlock.INSTANCE,
            AEBaseItemBlock.class,
            ULTRA_DENSE_NEUTRON_CONTROLLER);
    }

    /** @return a stack of the 超致密ME控制器, for recipes and messages. */
    public static ItemStack ultraDenseMeControllerStack() {
        return new ItemStack(MTUltraDenseControllerBlock.INSTANCE);
    }

    /** @return a stack of the 超致密ME中子注入控制器, for recipes and messages. */
    public static ItemStack ultraDenseNeutronControllerStack() {
        return new ItemStack(MTUltraDenseNeutronControllerBlock.INSTANCE);
    }

    /** Opens the window in which the controller faces of this thread's pathing calculation get the raised capacity. */
    public static void beginUltraDensePathing(boolean ultraDense) {
        ULTRA_DENSE_PATHING.set(ultraDense);
    }

    /** Closes the window opened by {@link #beginUltraDensePathing(boolean)}. */
    public static void endUltraDensePathing() {
        ULTRA_DENSE_PATHING.remove();
    }

    /** @return whether the controller faces created on this thread right now belong to an ultra dense grid. */
    public static boolean isUltraDensePathing() {
        return Boolean.TRUE.equals(ULTRA_DENSE_PATHING.get());
    }

    /**
     * Scans both controller machine sets of the grid for a block that implements {@link IMTUltraDenseControllerBlock}.
     *
     * <p>
     * {@code IGrid#getMachines(Class)} is keyed by the <em>exact</em> machine class, and
     * {@code TileCreativeEnergyController} extends {@code TileController}, so the neutron infused controllers are only
     * found in the second set and both have to be scanned. The block of a found tile entity is resolved through
     * {@code TileEntity#getBlockType()}, i.e. through the world position of the controller.
     *
     * <p>
     * The two AE2 tile entity classes are looked up by name instead of being referenced directly: the GTNH dev jar
     * declares the optional RotaryCraft / Mekanism energy interfaces on AE2's tile entity hierarchy, and those mods are
     * not on this mod's compile classpath, so a direct {@code TileController.class} reference fails to compile.
     *
     * @return {@code true} if the grid has at least one ultra dense controller.
     */
    public static boolean hasUltraDenseController(IGrid grid) {
        if (grid == null) return false;
        return scanControllerClass(grid, "appeng.tile.networking.TileController")
            || scanControllerClass(grid, "appeng.tile.networking.TileCreativeEnergyController");
    }

    private static boolean scanControllerClass(IGrid grid, String className) {
        final Class<? extends IGridHost> type = controllerClass(className);
        if (type == null) return false;
        for (IGridNode node : grid.getMachines(type)) {
            if (isUltraDenseController(node)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends IGridHost> controllerClass(String className) {
        try {
            return (Class<? extends IGridHost>) (Class<?>) Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static boolean isUltraDenseController(IGridNode node) {
        if (!(node.getMachine() instanceof TileEntity tile)) return false;
        // TileEntity#getBlockType() resolves the block through the world on first use, so it needs a world.
        if (tile.getWorldObj() == null) return false;
        return tile.getBlockType() instanceof IMTUltraDenseControllerBlock;
    }
}
