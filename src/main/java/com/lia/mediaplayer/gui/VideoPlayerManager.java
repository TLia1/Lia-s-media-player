package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.video.VideoPlayer;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry of the active {@link VideoWindow}s.
 *
 * <p>By default a new link does <em>not</em> spawn its own window: it is
 * {@linkplain #enqueue(String) appended} to the queue of the most-recently-focused
 * window, which plays the videos one after another in place. A brand-new,
 * independent window is only created when there is none yet, or when the caller
 * explicitly asks for one via {@link #open(String)} (e.g. shift-click).</p>
 *
 * <p>All methods are expected to run on the render/main thread (the only place
 * GUI events fire), so no synchronization is needed here — each {@link VideoPlayer}
 * handles its own background decoding internally.</p>
 */
public class VideoPlayerManager {
    /**
     * Hard cap on simultaneous windows; the oldest is disposed past this.
     */
    private final List<VideoWindow> windows = new ArrayList<>();

    public VideoPlayerManager() {
    }

    /**
     * Creates a brand-new, independent window playing {@code url} and starts it.
     * Use this when the user wants a separate player rather than queueing.
     */
    public VideoWindow open(String url) {
        evictIfFull();
        VideoPlayer player = new VideoPlayer(url);
        VideoWindow window = new VideoWindow(player);
        windows.add(window);
        player.start();
        window.setVisible(true);
        return window;
    }

    /**
     * Adds {@code url} to the play queue of the front-most window (creating a window
     * if there is none yet), reveals it and brings it to the front. This is the
     * default click behaviour: links pile up in one player instead of opening a new
     * window each time.
     */
    public VideoWindow enqueue(String url) {
        VideoWindow target = frontMost();
        if (target == null) {
            return open(url);
        }
        target.enqueue(url);
        target.setVisible(true);
        target.bringToFront();
        return target;
    }

    /**
     * The visible-or-hidden window with the highest stacking order, or {@code null}.
     */
    @Nullable
    public VideoWindow frontMost() {
        return windows.stream().max(Comparator.comparingLong(VideoWindow::zOrder)).orElse(null);
    }

    public boolean hasFrontMost() {
        return frontMost() != null;
    }

    /**
     * A stable snapshot for iterating during render / input handling.
     */
    public List<VideoWindow> getWindows() {
        return new ArrayList<>(windows);
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }

    /**
     * Number of windows that are currently hidden (playing but not on screen).
     */
    public int hiddenCount() {
        int n = 0;
        for (VideoWindow window : windows) {
            if (!window.isVisible()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Makes every hidden window visible again and raises them to the front.
     */
    public void revealAll() {
        for (VideoWindow window : windows) {
            if (!window.isVisible()) {
                window.setVisible(true);
                window.bringToFront();
            }
        }
    }

    /**
     * Disposes and removes a single window (and anything it had queued).
     */
    public void close(VideoWindow window) {
        if (windows.remove(window)) {
            window.disposeAll();
        }
    }

    /**
     * Disposes every window (e.g. on disconnect).
     */
    public void disposeAll() {
        for (VideoWindow window : windows) {
            window.disposeAll();
        }
        windows.clear();
    }

    private void evictIfFull() {
        while (windows.size() >= com.lia.mediaplayer.config.ConfigStore.MAX_VIDEO_WINDOWS.getValue()) {
            VideoWindow eldest = windows.removeFirst();
            eldest.disposeAll();
        }
    }

    // ------------------------------------------------------------------
    // Public API entry points (called by MediaPlayerContext)
    // ------------------------------------------------------------------

    public long enqueuePublic(String url) {
        return enqueue(url).getId();
    }

    public long openPublic(String url) {
        return open(url).getId();
    }

    public void togglePauseFrontMost() {
        VideoWindow window = frontMost();
        if (window != null) {
            window.player().togglePause();
        }
    }

    public void nextFrontMost() {
        VideoWindow window = frontMost();
        if (window != null) {
            window.advance();
        }
    }

    public void previousFrontMost() {
        // VideoWindow doesn't have previous(), but for keybind compatibility we just ignore it
    }

    public void seekFrontMost(double fraction) {
        VideoWindow window = frontMost();
        if (window != null) {
            window.player().seekToFraction(fraction);
        }
    }

    // ------------------------------------------------------------------
    // ID-based API methods
    // ------------------------------------------------------------------

    public VideoWindow getById(long id) {
        for (VideoWindow window : windows) {
            if (window.getId() == id) {
                return window;
            }
        }
        return null;
    }

    public boolean exists(long id) {
        return getById(id) != null;
    }

    public void togglePause(long id) {
        VideoWindow window = getById(id);
        if (window != null) {
            window.player().togglePause();
        }
    }

    public void next(long id) {
        VideoWindow window = getById(id);
        if (window != null) {
            window.advance();
        }
    }

    public void enqueueTo(long id, String url) {
        VideoWindow window = getById(id);
        if (window != null) {
            window.enqueue(url);
        }
    }

    public void setVisible(long id, boolean visible) {
        VideoWindow window = getById(id);
        if (window != null) {
            window.setVisible(visible);
        }
    }

    public void closePublic(long id) {
        VideoWindow window = getById(id);
        if (window != null) {
            close(window);
        }
    }
}

