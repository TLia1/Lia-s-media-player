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
public class AudioPlayerManager {
    /** Hard cap on simultaneous bars; the oldest is disposed past this. */
    private final List<AudioWindow> windows = new ArrayList<>();

    public AudioPlayerManager() {
    }

    /** Creates a brand-new, independent bar playing {@code url} and starts it. */
    public AudioWindow open(String url) {
        evictIfFull();
        AudioPlayer player = new AudioPlayer(url);
        AudioWindow window = new AudioWindow(player);
        windows.add(window);
        player.start();
        window.setVisible(true);
        return window;
    }

    /**
     * Adds {@code url} to the queue of the front-most bar (creating one if none exists),
     * reveals it and brings it to the front.
     */
    public AudioWindow enqueue(String url) {
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
    public AudioWindow playAll(List<String> urls, boolean shuffle) {
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
    public AudioWindow frontMost() {
        return windows.stream().max(Comparator.comparingLong(AudioWindow::zOrder)).orElse(null);
    }

    public boolean hasFrontMost() {
        return frontMost() != null;
    }

    public List<AudioWindow> getWindows() {
        return new ArrayList<>(windows);
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }

    public int hiddenCount() {
        int n = 0;
        for (AudioWindow window : windows) {
            if (!window.isVisible()) {
                n++;
            }
        }
        return n;
    }

    public void revealAll() {
        for (AudioWindow window : windows) {
            if (!window.isVisible()) {
                window.setVisible(true);
                window.bringToFront();
            }
        }
    }

    public void close(AudioWindow window) {
        if (windows.remove(window)) {
            window.disposeAll();
        }
    }

    /** Disposes every bar (e.g. on disconnect). */
    public void disposeAll() {
        for (AudioWindow window : windows) {
            window.disposeAll();
        }
        windows.clear();
    }

    // ------------------------------------------------------------------
    // Transport helpers for the keybinds (act on the front-most bar)
    // ------------------------------------------------------------------

    /** Whether at least one audio bar exists (so a keybind has something to act on). */
    public boolean hasAny() {
        return !windows.isEmpty();
    }

    public void togglePauseFrontMost() {
        AudioWindow window = frontMost();
        if (window != null) {
            window.player().togglePause();
        }
    }

    public void nextFrontMost() {
        AudioWindow window = frontMost();
        if (window != null) {
            window.advance();
        }
    }

    public void previousFrontMost() {
        AudioWindow window = frontMost();
        if (window != null) {
            window.previous();
        }
    }

    private void evictIfFull() {
        while (windows.size() >= com.lia.mediaplayer.config.ConfigStore.MAX_AUDIO_WINDOWS.getValue()) {
            AudioWindow eldest = windows.removeFirst();
            eldest.disposeAll();
        }
    }

    // ------------------------------------------------------------------
    // Public API entry points (called by MediaPlayerContext)
    // ------------------------------------------------------------------

    public long enqueuePublic(String url) {
        return enqueue(url).getId();
    }

    public long playAllPublic(List<String> urls, boolean shuffle) {
        AudioWindow window = playAll(urls, shuffle);
        return window != null ? window.getId() : -1;
    }

    public long playNewWindowPublic(String url){
        return open(url).getId();
    }

    public void seekFrontMost(double fraction) {
        AudioWindow window = frontMost();
        if (window != null) {
            window.player().seekToFraction(fraction);
        }
    }

    // ------------------------------------------------------------------
    // ID-based API methods
    // ------------------------------------------------------------------

    public AudioWindow getById(long id) {
        for (AudioWindow window : windows) {
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
        AudioWindow window = getById(id);
        if (window != null) {
            window.player().togglePause();
        }
    }

    public void next(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.advance();
        }
    }

    public void previous(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.previous();
        }
    }

    public void enqueueTo(long id, String url) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.enqueue(url);
        }
    }

    public void setVisible(long id, boolean visible) {
        AudioWindow window = getById(id);
        if (window != null) {
            window.setVisible(visible);
        }
    }

    public void closePublic(long id) {
        AudioWindow window = getById(id);
        if (window != null) {
            close(window);
        }
    }
}
