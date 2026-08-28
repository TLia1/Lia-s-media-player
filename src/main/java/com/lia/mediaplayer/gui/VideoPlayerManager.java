package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.video.VideoPlayer;

/**
 * Registry of the active {@link VideoWindow}s.
 *
 * <p>Everything a player-window registry does lives in {@link PlayerWindowManager}; what
 * makes this one the video one is the window it builds and the cap it reads.</p>
 */
public class VideoPlayerManager extends PlayerWindowManager<VideoWindow> {

    public VideoPlayerManager() {
    }

    @Override
    protected VideoWindow create(String url) {
        return new VideoWindow(new VideoPlayer(url));
    }

    @Override
    protected int maxWindows() {
        return ConfigStore.MAX_VIDEO_WINDOWS.getValue();
    }
}
