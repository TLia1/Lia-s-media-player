package com.lia.mediaplayer.input;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.gui.ConfigScreen;
import com.lia.mediaplayer.gui.Keys;
import com.lia.mediaplayer.gui.MediaControlScreen;
import com.lia.mediaplayer.gui.MediaWindowOverlay;
import com.lia.mediaplayer.gui.PlaylistScreen;
import com.lia.mediaplayer.gui.Screens;
import net.minecraft.client.Minecraft;

/**
 * Turns presses of the {@link ModKeybinds} bindings into actions on the players.
 * Polled once per client tick: {@link net.minecraft.client.KeyMapping#consumeClick()}
 * only returns {@code true} for a bound key that was actually pressed, so an unbound
 * binding simply never fires.
 *
 * <p>These are the <em>global</em> shortcuts — they work in the world, with no screen
 * open, which is what separates them from {@code gui.WindowShortcuts}: those act on the
 * window under the cursor while a screen that hosts the stack is open, these act on the
 * mod from wherever the player happens to be.</p>
 *
 * <p>Loader-neutral: the per-loader bridge in {@code platform} owns the tick event and
 * calls {@link #onClientTick()}.</p>
 */
public final class KeybindHandler {

    /** How much one press of the volume keys moves the shared level. */
    private static final float VOLUME_STEP = 0.05f;

    private KeybindHandler() {
    }

    public static void onClientTick() {
        // Screens first: these are the bindings that work with nothing playing, so they
        // must not be behind the context check below.
        while (ModKeybinds.OPEN_PLAYLISTS.consumeClick()) {
            if (Screens.current() == null) {
                Screens.open(new PlaylistScreen());
            }
        }
        while (ModKeybinds.OPEN_CONFIG.consumeClick()) {
            if (Screens.current() == null) {
                Screens.open(new ConfigScreen(null));
            }
        }
        // Also with nothing playing: the screen says so, which is a better answer than a
        // key that appears not to work.
        while (ModKeybinds.OPEN_CONTROLS.consumeClick()) {
            if (Screens.current() == null) {
                Screens.open(new MediaControlScreen());
            }
        }

        MediaPlayerContext ctx = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
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

        // Volume goes through media.Volume, the one level both engines read, so there is
        // no front-most player to find: it applies to everything that is playing.
        while (ModKeybinds.VOLUME_UP.consumeClick()) {
            ctx.getVolumeManager().change(VOLUME_STEP);
        }
        while (ModKeybinds.VOLUME_DOWN.consumeClick()) {
            ctx.getVolumeManager().change(-VOLUME_STEP);
        }
        while (ModKeybinds.MUTE.consumeClick()) {
            ctx.getVolumeManager().toggleMute();
        }

        while (ModKeybinds.TOGGLE_WINDOWS.consumeClick()) {
            MediaWindowOverlay.toggleAllVisible();
        }
        while (ModKeybinds.CLOSE_ALL.consumeClick()) {
            MediaWindowOverlay.closeAll();
        }
        while (ModKeybinds.PLAY_CLIPBOARD.consumeClick()) {
            playClipboard();
        }
    }

    /**
     * Plays the link on the clipboard, exactly as clicking that link in chat would.
     *
     * <p>The modifiers mean the same thing here as they do there — alt for sound only,
     * shift for a window of its own — because the routing is literally the same method
     * ({@link MediaWindowOverlay#play}). A clipboard holding something that is not a
     * media link is simply ignored; there is nothing useful to say about it that the
     * absence of a player does not already say.</p>
     */
    private static void playClipboard() {
        String clipboard;
        try {
            clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (RuntimeException e) {
            return; // no clipboard access on this platform, or nothing on it
        }
        if (clipboard == null) {
            return;
        }
        String url = clipboard.strip();
        if (url.isEmpty()) {
            return;
        }
        MediaWindowOverlay.play(url, Keys.altDown(), Keys.shiftDown());
    }
}
