package com.MessTech.common.process;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 1:1 port of Draconic Evolution's
 * {@code com.brandon3055.draconicevolution.common.utils.handlers.ProcessHandler} (see
 * {@code tmp/Draconic-Evolution-master}). Processes are similar to tile entities except that they are not bound to
 * anything and they are not persistent (they are dropped when the world closes).
 */
public class MTProcessHandler {

    private static final List<IMTProcess> processes = new ArrayList<>();
    private static final List<IMTProcess> newProcesses = new ArrayList<>();

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MTProcessHandler());
        MinecraftForge.EVENT_BUS.register(new MTProcessHandler());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {

            Iterator<IMTProcess> i = processes.iterator();

            while (i.hasNext()) {
                IMTProcess process = i.next();
                if (process.isDead()) i.remove();
                else process.updateProcess();
            }

            if (!newProcesses.isEmpty()) {
                processes.addAll(newProcesses);
                newProcesses.clear();
            }
        }
    }

    @SubscribeEvent
    public void onWorldClose(WorldEvent.Unload event) {
        processes.clear();
        newProcesses.clear();
    }

    public static void addProcess(IMTProcess process) {
        newProcesses.add(process);
    }
}
