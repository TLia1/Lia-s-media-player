package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.audio.AudioPlayer;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Registry of the active {@link AudioWindow} bars — the audio counterpart of
 * {@link VideoPlayerManager}.
 *
 * <p>By default a new audio link is {@linkplain #enqueue(String) appended} to the queue
 * of the most-recently-focused bar (so links pile into one player); a separate bar is
 * created only when there is none yet, or on demand via {@link #open(String)}
 * (shift-click). {@link #playAll} starts a whole playlist in a fresh bar.</p>
 *
 * <p>All methods run on the render/main thread. The transport helpers
 * ({@link #togglePauseFrontMost()}, {@link #nextFrontMost()}, {@link #previousFrontMost()})
 * back the configurable keybinds and act on the front-most bar.</p>
 */
public final class AudioPlayerManager {
    /** Hard cap on simultaneous bars; the oldest is disposed past this. */


    private static final List<AudioWindow> WINDOWS = new ArrayList<>();

    private AudioPlayerManager() {
    }

    /** Creates a brand-new, independent bar playing {@code url} and starts it. */
    static AudioWindow open(String url) {
        evictIfFull();
        AudioPlayer player = new AudioPlayer(url);
        AudioWindow window = new AudioWindow(player);
        WINDOWS.add(window);
        player.start();
        window.setVisible(true);
        return window;
    }

    /**
     * Adds {@code url} to the queue of the front-most bar (creating one if none exists),
     * reveals it and brings it to the front.
     */
    static AudioWindow enqueue(String url) {
        AudioWindow target = frontMost();
        if (target == null) {
            return open(url);
        }
        target.enqueue(url);
        target.setVisible(true);
        target.bringToFront();
        return target;
    }

    /**
     * Plays a whole list of URLs in a fresh bar: the first track starts immediately and
     * the rest queue behind it. When {@code shuffle} is set the order is randomised once,
     * up front. Returns {@code null} for an empty list.
     */
    @Nullable
    public static AudioWindow playAll(List<String> urls, boolean shuffle) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String> order = new ArrayList<>(urls);
        if (shuffle) {
            Collections.shuffle(order);
        }
        AudioWindow window = open(order.getFirst());
        if (order.size() > 1) {
            window.enqueueAll(order.subList(1, order.size()));
        }
        window.bringToFront();
        return window;
    }

    @Nullable
    static AudioWindow frontMost() {
        return WINDOWS.stream().max(Comparator.comparingLong(AudioWindow::zOrder)).orElse(null);
    }

    public static boolean hasFrontMost() {
        return frontMost() != null;
    }

    static List<AudioWindow> windows() {
        return new ArrayList<>(WINDOWS);
    }

    static boolean isEmpty() {
        return WINDOWS.isEmpty();
    }

    static int hiddenCount() {
        int n = 0;
        for (AudioWindow window : WINDOWS) {
            if (!window.isVisible()) {
                n++;
            }
        }
        return n;
    }

    static void revealAll() {
        for (AudioWindow window : WINDOWS) {
            if (!window.isVisible()) {
                window.setVisible(true);
                window.bringToFront();
            }
        }
    }

    static void close(AudioWindow window) {
        if (WINDOWS.remove(window)) {
            window.disposeAll();
        }
    }

    /** Disposes every bar (e.g. on disconnect). */
    public static void disposeAll() {
        for (AudioWindow window : WINDOWS) {
            window.disposeAll();
        }
        WINDOWS.clear();
    }

    // ------------------------------------------------------------------
    // Transport helpers for the keybinds (act on the front-most bar)
    // ------------------------------------------------------------------

    /** Whether at least one audio bar exists (so a keybind has something to act on). */
    public static boolean hasAny() {
        return !WINDOWS.isEmpty();
    }

    public static void togglePauseFrontMost() {
        AudioWindow window = frontMost();
        if (window != null) {
            window.player().togglePause();
        }
    }

    public static void nextFrontMost() {
        AudioWindow window = frontMost();
        if (window != null) {
            window.advance();
        }
    }

    public static void previousFrontMost() {
        AudioWindow window = frontMost();
        if (window != null) {
            window.previous();
        }
    }

    private static void evictIfFull() {
        while (WINDOWS.size() >= com.lia.mediaplayer.config.ConfigStore.MAX_AUDIO_WINDOWS.getValue()) {
            AudioWindow eldest = WINDOWS.removeFirst();
            eldest.disposeAll();
        }
    }

    // ------------------------------------------------------------------
    // Public API entry points (called by MediaPlayerAPI)
    // ------------------------------------------------------------------

    /** Public entry point for the API: enqueue an audio URL. Returns the window ID. */
    public static long enqueuePublic(String url) {
        return enqueue(url).getId();
    }

    /** Public entry point for the API: play a list of URLs. Returns the window ID, or -1 if empty. */
    public static long playAllPublic(List<String> urls, boolean shuffle) {
        AudioWindow window = playAll(urls, shuffle);
        return window != null ? window.getId() : -1;
    }

    /** Seeks the front-most audio player to a fraction (API). */
    public static void seekFrontMost(double fraction) {
        AudioWindow window = frontMost();
        if (window != null) {
            window.player().seekToFraction(fraction);
        }
    }

    // ------------------------------------------------------------------
    // ID-based API methods
    // ------------------------------------------------------------------

    static AudioWindow getById(long id) {
        for (AudioWindow window : WINDOWS) {
            if (window.getId() == id) {
                return window;
            }
        }
        return null;
    }

    public static boolean exists(long id) {
        return getById(id) != null;
    }

    public static void togglePause(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.player().togglePause();
        }
    }

    public static void next(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.advance();
        }
    }

    public static void previous(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.previous();
        }
    }

    public static void enqueueTo(long id, String url) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.enqueue(url);
        }
    }

    public static void setVisible(long id, boolean visible) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.setVisible(visible);
        }
    }

    public static void closePublic(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            close(window);
        }
    }
}
