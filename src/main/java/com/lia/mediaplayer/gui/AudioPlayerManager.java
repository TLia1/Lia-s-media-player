package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.audio.AudioPlayer;
import com.lia.mediaplayer.config.ConfigStore;

/**
 * Registry of the active {@link AudioWindow} bars — the audio counterpart of
 * {@link VideoPlayerManager}.
 *
 * <p>Everything a player-window registry does lives in {@link PlayerWindowManager}. On
 * top of that, this one has "previous": the audio bar is the only window with a
 * previous-track control, so the manager that drives the keybind and the public API is
 * the only one that answers it.</p>
 */
public class AudioPlayerManager extends PlayerWindowManager<AudioWindow> {

    public AudioPlayerManager() {
    }

    @Override
    protected AudioWindow create(String url) {
        return new AudioWindow(new AudioPlayer(url));
    }

    @Override
    protected int maxWindows() {
        return ConfigStore.MAX_AUDIO_WINDOWS.getValue();
    }

    /**
     * Goes back a track on the front-most bar — what the PREVIOUS keybind does.
     */
    public void previousFrontMost() {
        AudioWindow window = frontMost();
        // The key-binding path, so the sync lock applies — see PlayerWindowManager.
        if (window != null && !window.isLocked()) {
            window.previous();
        }
    }

    public void previous(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.previous();
        }
    }
}
