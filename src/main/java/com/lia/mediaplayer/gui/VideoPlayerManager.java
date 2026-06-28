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
public final class VideoPlayerManager {
    /**
     * Hard cap on simultaneous windows; the oldest is disposed past this.
     */


    private static final List<VideoWindow> WINDOWS = new ArrayList<>();

    private VideoPlayerManager() {
    }

    /**
     * Creates a brand-new, independent window playing {@code url} and starts it.
     * Use this when the user wants a separate player rather than queueing.
     */
    static VideoWindow open(String url) {
        evictIfFull();
        VideoPlayer player = new VideoPlayer(url);
        VideoWindow window = new VideoWindow(player);
        WINDOWS.add(window);
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
    static VideoWindow enqueue(String url) {
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
    static VideoWindow frontMost() {
        return WINDOWS.stream().max(Comparator.comparingLong(VideoWindow::zOrder)).orElse(null);
    }

    public static boolean hasFrontMost() {
        return frontMost() != null;
    }

    /**
     * A stable snapshot for iterating during render / input handling.
     */
    static List<VideoWindow> windows() {
        return new ArrayList<>(WINDOWS);
    }

    static boolean isEmpty() {
        return WINDOWS.isEmpty();
    }

    /**
     * Number of windows that are currently hidden (playing but not on screen).
     */
    static int hiddenCount() {
        int n = 0;
        for (VideoWindow window : WINDOWS) {
            if (!window.isVisible()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Makes every hidden window visible again and raises them to the front.
     */
    static void revealAll() {
        for (VideoWindow window : WINDOWS) {
            if (!window.isVisible()) {
                window.setVisible(true);
                window.bringToFront();
            }
        }
    }

    /**
     * Disposes and removes a single window (and anything it had queued).
     */
    static void close(VideoWindow window) {
        if (WINDOWS.remove(window)) {
            window.disposeAll();
        }
    }

    /**
     * Disposes every window (e.g. on disconnect).
     */
    public static void disposeAll() {
        for (VideoWindow window : WINDOWS) {
            window.disposeAll();
        }
        WINDOWS.clear();
    }

    private static void evictIfFull() {
        while (WINDOWS.size() >= com.lia.mediaplayer.config.ConfigStore.MAX_VIDEO_WINDOWS.getValue()) {
            VideoWindow eldest = WINDOWS.removeFirst();
            eldest.disposeAll();
        }
    }

    // ------------------------------------------------------------------
    // Public API entry points (called by MediaPlayerAPI)
    // ------------------------------------------------------------------

    /** Public entry point for the API: enqueue a video URL. */
    public static void enqueuePublic(String url) {
        enqueue(url);
    }

    /** Public entry point for the API: open a new independent window. */
    public static void openPublic(String url) {
        open(url);
    }

    /** Toggles pause on the front-most video player (API + keybind). */
    public static void togglePauseFrontMost() {
        VideoWindow window = frontMost();
        if (window != null) {
            window.player().togglePause();
        }
    }

    /** Skips to the next queued video in the front-most window (API). */
    public static void nextFrontMost() {
        VideoWindow window = frontMost();
        if (window != null) {
            window.advance();
        }
    }

    /** Skips to the previous queued video in the front-most window (API). */
    public static void previousFrontMost() {
        // VideoWindow doesn't have previous(), but for keybind compatibility we just ignore it
        // or we could implement it if we had a history. For now, do nothing.
    }

    /** Seeks the front-most video player to a fraction (API). */
    public static void seekFrontMost(double fraction) {
        VideoWindow window = frontMost();
        if (window != null) {
            window.player().seekToFraction(fraction);
        }
    }
}

