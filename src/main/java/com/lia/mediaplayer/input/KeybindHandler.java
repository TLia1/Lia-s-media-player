package com.lia.mediaplayer.input;

import com.lia.mediaplayer.gui.PlaylistScreen;

/**
 * Turns presses of the {@link ModKeybinds} bindings into actions on the active audio
 * bar. Polled once per client tick: {@link net.minecraft.client.KeyMapping#consumeClick()}
 * only returns {@code true} for a bound key that was actually pressed, so an unbound
 * binding simply never fires.
 *
 * <p>Loader-neutral: the per-loader bridge in {@code platform} owns the tick event and
 * calls {@link #onClientTick()}.</p>
 */
public final class KeybindHandler {

    private KeybindHandler() {
    }

    public static void onClientTick() {
        while (ModKeybinds.OPEN_PLAYLISTS.consumeClick()) {
            if (com.lia.mediaplayer.gui.Screens.current() == null) {
                com.lia.mediaplayer.gui.Screens.open(new PlaylistScreen());
            }
        }

        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
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
