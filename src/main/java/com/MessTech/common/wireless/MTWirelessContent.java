package com.MessTech.common.wireless;

import net.minecraft.item.ItemStack;

import com.MessTech.common.items.MTItemList;
import com.MessTech.common.util.MTRegistryMark;
import com.MessTech.common.wireless.gui.MTWirelessNetworkPickerGui;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registration of the wireless ME network content, the 1.7.10 counterpart of GTLCore's
 * {@code GTLWirelessAeContent}: one place that owns the block, its tile entity, the binding tool and the runtime's
 * lifecycle hooks, so the rest of the mod only calls {@link #preInit()} and {@link #registerBlocks()}.
 * <p>
 * The block is a plain {@code BlockContainer} (not a GT MetaTileEntity): its tile entity is an AE2
 * {@link appeng.tile.grid.AENetworkTile} so AE2 itself manages the grid node's lifecycle, which is what makes the
 * core's single exposed side behave exactly like a cable.
 */
public final class MTWirelessContent {

    private static boolean preInitialised;

    private MTWirelessContent() {}

    /** Items, GUI factories, packet-free runtime handlers and event listeners. Called from the proxy's preInit. */
    public static void preInit() {
        if (preInitialised) return;
        preInitialised = true;

        GameRegistry.registerItem(MTWirelessNetworkBindingTool.INSTANCE, "WirelessNetworkBindingTool");
        MTItemList.WirelessNetworkBindingTool.set(new ItemStack(MTWirelessNetworkBindingTool.INSTANCE, 1, 0));

        // The tool's dedicated Baubles accessory slot (GTLCore's Curios `wireless_connector` slot). Baubles Expanded
        // only accepts slot registration during pre-init, which is exactly where this runs.
        MTWirelessBaubles.init();

        // The picker is a SimpleGuiFactory, which registers itself in its constructor.
        MTWirelessNetworkPickerGui.init();
        // Shift + left click unbind.
        MTWirelessToolEvents.init();
        // Server tick maintenance of every wireless link.
        MTWirelessNetworkRuntime.init();
    }

    /** The block, its item and its tile entity. Called from {@code MTBlocks.registerBlocks()}. */
    public static void registerBlocks() {
        GameRegistry.registerBlock(
            MTWirelessNetworkCoreBlock.INSTANCE,
            MTWirelessNetworkCoreItemBlock.class,
            "wirelessNetworkCore");
        GameRegistry.registerTileEntity(MTWirelessNetworkCoreTile.class, MTWirelessNetworkCoreTile.TE_ID);
        MTItemList.WirelessNetworkCore.set(new ItemStack(MTWirelessNetworkCoreBlock.INSTANCE, 1, 0));

        // Source markers in GTLCore's style: both stacks are content only this mod adds.
        MTRegistryMark.markAdded(new ItemStack(MTWirelessNetworkCoreBlock.INSTANCE, 1, 0));
        MTRegistryMark.markAdded(new ItemStack(MTWirelessNetworkBindingTool.INSTANCE, 1, 0));
    }

    /** The core's item stack, for recipes and messages. */
    public static ItemStack coreStack() {
        return MTItemList.WirelessNetworkCore.get(1);
    }

    /** The binding tool's item stack. */
    public static ItemStack toolStack() {
        return MTItemList.WirelessNetworkBindingTool.get(1);
    }
}
