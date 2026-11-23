package com.MessTech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.MessTech.common.network.MTNetwork;
import com.MessTech.common.network.OpenWirelessPickerPacket;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 无线ME网络选择界面的键位：1.7.10 换掉了 GTLCore 的 Curios 槽以后，穿在饰品栏里的绑定工具没有「右键空气」可用，
 * 这条键位就是 GTLCore {@code WirelessBindingPickerKeyMapping} 的对应实现（同样默认不绑定按键）。
 * <p>
 * 只负责把 {@link OpenWirelessPickerPacket} 发给服务端：GUI 由 MUI2 在服务端真正打开（工厂在建的时候已经注册），
 * 客户端这里只消费按键、不做任何状态判断。
 */
public final class MTWirelessKeyBindings {

    /** 打开网络选择界面；默认不绑定按键，玩家自己在「控制」里设。 */
    public static final KeyBinding OPEN_PICKER = new KeyBinding(
        "key.messtech.wirelessNetworkPicker",
        Keyboard.KEY_NONE,
        "key.categories.messtech");

    private static boolean initialised;

    private MTWirelessKeyBindings() {}

    /** 客户端 preInit 调用一次：注册键位与客户端 tick 监听。 */
    public static void init() {
        if (initialised) return;
        initialised = true;

        ClientRegistry.registerKeyBinding(OPEN_PICKER);
        FMLCommonHandler.instance()
            .bus()
            .register(new TickHandler());
    }

    /**
     * The listener is a separate class and has to be <b>public</b>: FML's {@code ASMEventHandler} generates the proxy
     * in its own class loader, which cannot access a package-private / private nested class (an
     * {@code IllegalAccessError} on every client tick).
     */
    public static final class TickHandler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            // isPressed() 会消费一次按键，所以用 while 把同一 tick 的重复队列排空。
            while (OPEN_PICKER.isPressed()) {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft.thePlayer == null || minecraft.currentScreen != null) continue;
                MTNetwork.NETWORK.sendToServer(new OpenWirelessPickerPacket());
            }
        }
    }
}
