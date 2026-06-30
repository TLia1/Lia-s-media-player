package com.lia.mediaplayer.input;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.gui.PlaylistScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Turns presses of the {@link ModKeybinds} bindings into actions on the active audio
 * bar. Polled once per client tick: {@link net.minecraft.client.KeyMapping#consumeClick()}
 * only returns {@code true} for a bound key that was actually pressed, so an unbound
 * binding simply never fires.
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class KeybindHandler {

    private KeybindHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (ModKeybinds.OPEN_PLAYLISTS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new PlaylistScreen());
            }
        }

        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
        if (ctx == null) return;

        while (ModKeybinds.PLAY_PAUSE.consumeClick()) {
            if (ctx.getAudioManager().hasFrontMost()) {
                ctx.getAudioManager().togglePauseFrontMost();
            } else if (ctx.getVideoManager().hasFrontMost()) {
                ctx.getVideoManager().togglePauseFrontMost();
            }
        }
        while (ModKeybinds.NEXT.consumeClick()) {
            if (ctx.getAudioManager().hasFrontMost()) {
                ctx.getAudioManager().nextFrontMost();
            } else if (ctx.getVideoManager().hasFrontMost()) {
                ctx.getVideoManager().nextFrontMost();
            }
        }
        while (ModKeybinds.PREVIOUS.consumeClick()) {
            if (ctx.getAudioManager().hasFrontMost()) {
                ctx.getAudioManager().previousFrontMost();
            } else if (ctx.getVideoManager().hasFrontMost()) {
                ctx.getVideoManager().previousFrontMost();
            }
        }
    }
}
