package com.lia.mediaplayer.media;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.MemoryReleasable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public class MemoryMonitor {
    private static final List<MemoryReleasable> REGISTERED = new CopyOnWriteArrayList<>();
    private static final long CHECK_INTERVAL_MS = 5000;
    private static final double MODERATE_THRESHOLD = 0.85;
    private static final double CRITICAL_THRESHOLD = 0.95;

    private static long lastCheckTime = 0;

    public static void register(MemoryReleasable releasable) {
        if (!REGISTERED.contains(releasable)) {
            REGISTERED.add(releasable);
            REGISTERED.sort(Comparator.comparingInt(MemoryReleasable::getReleasePriority));
        }
    }

    public static void unregister(MemoryReleasable releasable) {
        REGISTERED.remove(releasable);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        Runtime rt = Runtime.getRuntime();
        long maxMemory = rt.maxMemory();
        long usedMemory = rt.totalMemory() - rt.freeMemory();

        if (maxMemory == 0) return;

        double usage = (double) usedMemory / maxMemory;

        if (usage >= MODERATE_THRESHOLD) {
            boolean isCritical = usage >= CRITICAL_THRESHOLD;
            triggerRelease(isCritical);
        }
    }

    private static void triggerRelease(boolean isCritical) {
        boolean freedSomething = false;

        for (MemoryReleasable r : REGISTERED) {
            if (r.releaseMemory(isCritical)) {
                freedSomething = true;
            }
        }

        if (freedSomething) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // If it's critical we use a different message or just one message
                mc.player.displayClientMessage(
                    Component.translatable(isCritical ? "liasmediaplayer.memory.freed.critical" : "liasmediaplayer.memory.freed.moderate"), 
                    false
                );
            }
        }
    }
}
