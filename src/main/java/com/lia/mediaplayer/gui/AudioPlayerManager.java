package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.MemoryReleasable;
import com.lia.mediaplayer.media.MemoryMonitor;
import com.lia.mediaplayer.audio.AudioPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
    private static final int MAX_WINDOWS = 4;

    private static final List<AudioWindow> WINDOWS = new ArrayList<>();

    static {
        MemoryMonitor.register(new MemoryReleasable() {
            @Override
            public int getReleasePriority() {
                return 50;
            }

            @Override
            public boolean releaseMemory(boolean isCritical) {
                List<AudioWindow> toClose = new ArrayList<>();
                AudioWindow front = frontMost();

                for (AudioWindow window : WINDOWS) {
                    if (window == front) continue; // Never close the front-most
                    
                    if (!window.isVisible() || window.player().isPaused() || window.player().state() == AudioPlayer.State.ENDED) {
                        toClose.add(window);
                    }
                }

                if (!toClose.isEmpty()) {
                    Minecraft.getInstance().execute(() -> {
                        for (AudioWindow window : toClose) {
                            close(window);
                        }
                    });
                    return true;
                }
                return false;
            }
        });
    }

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
        AudioWindow window = open(order.get(0));
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
        while (WINDOWS.size() >= MAX_WINDOWS) {
            AudioWindow eldest = WINDOWS.removeFirst();
            eldest.disposeAll();
        }
    }

    // ------------------------------------------------------------------
    // Public API entry points (called by MediaPlayerAPI)
    // ------------------------------------------------------------------

    /** Public entry point for the API: enqueue an audio URL. */
    public static void enqueuePublic(String url) {
        enqueue(url);
    }

    /** Seeks the front-most audio player to a fraction (API). */
    public static void seekFrontMost(double fraction) {
        AudioWindow window = frontMost();
        if (window != null) {
            window.player().seekToFraction(fraction);
        }
    }
}
