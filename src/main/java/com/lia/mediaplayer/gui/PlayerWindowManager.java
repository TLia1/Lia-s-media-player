package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.MediaRequest;
import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.history.HistoryStore;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The registry of one kind of player window — the shared half of
 * {@link VideoPlayerManager} and {@link AudioPlayerManager}.
 *
 * <p>By default a new link does <em>not</em> spawn its own window: it is
 * {@linkplain #enqueue(String) appended} to the queue of the most-recently-focused
 * window, which plays the items one after another in place. A brand-new, independent
 * window is only created when there is none yet, or when the caller explicitly asks for
 * one via {@link #open(String)} (shift-click).</p>
 *
 * <p>The two managers were the same class twice over — the same list, the same
 * {@code frontMost()} by z-order, the same enqueue/playAll/reveal/evict, and the same
 * dozen ID-addressed methods behind the public API. Only three things actually differed,
 * and those are the abstract methods below: which window to build, and what the cap on
 * simultaneous windows is called in the config.</p>
 *
 * <p>All methods are expected to run on the render/main thread (the only place GUI events
 * fire), so no synchronization is needed here — each player handles its own background
 * decoding internally.</p>
 *
 * @param <W> the window type this manager owns
 */
public abstract class PlayerWindowManager<W extends QueuedMediaWindow<?>> {

    private final List<W> windows = new ArrayList<>();

    protected PlayerWindowManager() {
    }

    // ------------------------------------------------------------------
    // Per-kind seams
    // ------------------------------------------------------------------

    /**
     * Builds a window (and its player) for {@code url}, without starting it —
     * {@link #open} starts it once the window has joined the stack.
     */
    protected abstract W create(String url);

    /**
     * Hard cap on simultaneous windows of this kind; the oldest is disposed past this.
     * Read on every open rather than cached, because it is a config option the user can
     * change mid-session.
     */
    protected abstract int maxWindows();

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    /**
     * Creates a brand-new, independent window playing {@code url} and starts it.
     * Use this when the user wants a separate player rather than queueing.
     */
    public W open(String url) {
        return open(url, null);
    }

    /**
     * {@link #open(String)}, with a {@code MediaRequest}'s window options applied before
     * the window has been laid out once — which is the only moment they can be applied
     * without the placement being seen changing.
     */
    private W open(String url, @Nullable MediaRequest request) {
        evictIfFull();
        W window = create(url);
        if (request != null) {
            window.applyRequest(request);
            window.requestStart(request.startMicros(), request.isAutoplay());
        }
        HistoryStore.record(url, window.mediaKind());
        windows.add(window);
        window.startPlayback();
        window.setVisible(true);
        return window;
    }

    /**
     * Plays a whole {@link MediaRequest} and hands back a handle on what is playing it.
     *
     * <p>The window options a request carries — placement, sizing, chrome, geometry
     * persistence — describe a <em>window</em>, so they only apply when this opens one.
     * A request that queues into the front-most player (the default, and what a chat
     * click does) leaves that window exactly as the user arranged it; ask for
     * {@code newWindow(true)} when the geometry is the point.</p>
     */
    public MediaHandle play(MediaRequest request) {
        List<String> urls = request.urls();
        W target = request.isNewWindow() ? null : frontMost();
        if (target != null) {
            for (String url : urls) {
                target.enqueue(url);
            }
            target.setVisible(true);
            target.bringToFront();
            return target.handle();
        }
        // Shuffled before the first one is opened, not only after — the same thing
        // playAll does. Turning the flag on afterwards would reorder what is *queued*
        // and leave the first track as whatever happened to be written first, so
        // "shuffle this playlist" would play the same opening track every time.
        List<String> order = urls;
        if (request.isShuffle() && urls.size() > 1) {
            order = new ArrayList<>(urls);
            Collections.shuffle(order);
        }
        W window = open(order.getFirst(), request);
        if (order.size() > 1) {
            window.enqueueAll(order.subList(1, order.size()));
        }
        window.setShuffle(request.isShuffle());
        window.setRepeat(request.repeat());
        window.bringToFront();
        return window.handle();
    }

    /**
     * Adds {@code url} to the play queue of the front-most window (creating a window if
     * there is none yet), reveals it and brings it to the front. This is the default
     * click behaviour: links pile up in one player instead of opening a new window each
     * time.
     */
    public W enqueue(String url) {
        W target = frontMost();
        if (target == null) {
            return open(url);
        }
        target.enqueue(url);
        target.setVisible(true);
        target.bringToFront();
        return target;
    }

    /**
     * Plays a whole list of URLs in a fresh window, without looping.
     */
    @Nullable
    public W playAll(List<String> urls, boolean shuffle) {
        return playAll(urls, shuffle, RepeatMode.OFF);
    }

    /**
     * Plays a whole list of URLs in a fresh window: the first item starts immediately and
     * the rest queue behind it (this is how an expanded YouTube playlist is played).
     * Returns {@code null} for an empty list.
     *
     * <p>{@code shuffle} randomises the order and <em>stays on</em> for the window, so a
     * looping playlist is reshuffled at the start of every round instead of repeating the
     * first round's order. {@code repeat} is the window's initial loop mode.</p>
     */
    @Nullable
    public W playAll(List<String> urls, boolean shuffle, RepeatMode repeat) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String> order = new ArrayList<>(urls);
        if (shuffle) {
            Collections.shuffle(order);
        }
        W window = open(order.getFirst());
        if (order.size() > 1) {
            window.enqueueAll(order.subList(1, order.size()));
        }
        window.setShuffle(shuffle);
        window.setRepeat(repeat);
        window.bringToFront();
        return window;
    }

    // ------------------------------------------------------------------
    // The stack
    // ------------------------------------------------------------------

    /**
     * The visible-or-hidden window with the highest stacking order, or {@code null}.
     */
    @Nullable
    public W frontMost() {
        return windows.stream().max(Comparator.comparingLong(MediaWindow::zOrder)).orElse(null);
    }

    public boolean hasFrontMost() {
        return frontMost() != null;
    }

    /**
     * A stable snapshot for iterating during render / input handling.
     */
    public List<W> getWindows() {
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
        for (W window : windows) {
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
        for (W window : windows) {
            if (!window.isVisible()) {
                window.setVisible(true);
                window.bringToFront();
            }
        }
    }

    /**
     * Disposes and removes a single window (and anything it had queued).
     */
    public void close(W window) {
        if (windows.remove(window)) {
            window.disposeAll();
        }
    }

    /**
     * Disposes every window (e.g. on disconnect).
     */
    public void disposeAll() {
        for (W window : windows) {
            window.disposeAll();
        }
        windows.clear();
    }

    private void evictIfFull() {
        while (windows.size() >= maxWindows()) {
            W eldest = windows.removeFirst();
            eldest.disposeAll();
        }
    }

    // ------------------------------------------------------------------
    // Transport helpers (act on the front-most window)
    // ------------------------------------------------------------------

    public void togglePauseFrontMost() {
        W window = unlockedFrontMost();
        if (window != null) {
            window.player().togglePause();
        }
    }

    public void nextFrontMost() {
        W window = unlockedFrontMost();
        if (window != null) {
            window.advance();
        }
    }

    public void seekFrontMost(double fraction) {
        W window = unlockedFrontMost();
        if (window != null) {
            window.player().seekToFraction(fraction);
        }
    }

    /**
     * The front-most window, unless it is held off the user's own transport by
     * {@code api.sync.SyncControl.setLocked}.
     *
     * <p>These three are the <em>key binding</em> path — what a global shortcut does with
     * no screen open — so they are the user's hands and the lock applies. The ID-addressed
     * methods further down are the API's, and deliberately are not gated: an addon that
     * locked a window still has to be able to drive it.</p>
     */
    @Nullable
    private W unlockedFrontMost() {
        W window = frontMost();
        return window != null && window.isLocked() ? null : window;
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

    public long playAllPublic(List<String> urls, boolean shuffle) {
        W window = playAll(urls, shuffle);
        return window != null ? window.getId() : -1;
    }

    /**
     * {@link #playAll(List, boolean)} for the API's handle-returning entry points.
     * Separate from {@link #playAllPublic} because the window type is package-private:
     * outside {@code gui} nothing can name it, so nothing outside can turn one into a
     * handle either.
     */
    @Nullable
    public MediaHandle playAllHandle(List<String> urls, boolean shuffle) {
        W window = playAll(urls, shuffle);
        return window == null ? null : window.handle();
    }

    // ------------------------------------------------------------------
    // ID-based API methods
    // ------------------------------------------------------------------

    @Nullable
    public W getById(long id) {
        for (W window : windows) {
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
        W window = getById(id);
        if (window != null) {
            window.player().togglePause();
        }
    }

    public void next(long id) {
        W window = getById(id);
        if (window != null) {
            window.advance();
        }
    }

    public void enqueueTo(long id, String url) {
        W window = getById(id);
        if (window != null) {
            window.enqueue(url);
        }
    }

    public void setVisible(long id, boolean visible) {
        W window = getById(id);
        if (window != null) {
            window.setVisible(visible);
        }
    }

    public void closePublic(long id) {
        W window = getById(id);
        if (window != null) {
            close(window);
        }
    }
}
