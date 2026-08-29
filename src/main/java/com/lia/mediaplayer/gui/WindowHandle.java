package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaQueue;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.audio.AudioControls;
import com.lia.mediaplayer.api.event.PlaybackEvent;
import com.lia.mediaplayer.api.event.PlaybackListener;
import com.lia.mediaplayer.api.window.MediaWindowHandle;
import com.lia.mediaplayer.api.window.Placement;
import com.lia.mediaplayer.api.window.Sizing;
import com.lia.mediaplayer.api.window.WindowChromeOptions;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link MediaWindow} as the public API sees it — the implementation of
 * {@link MediaHandle}.
 *
 * <p>Deliberately a thin adapter and nothing more. Every window already holds an id, a
 * player and a queue; what an addon was missing was an <em>object</em> to ask them of,
 * because a {@code long} id can only be written to. So this owns no state of its own
 * beyond the per-handle listener list and the last URL it saw, and it never hands the
 * window itself out: {@link MediaWindow} and its subclasses stay package-private, which
 * is what lets the window internals keep moving.</p>
 *
 * <p>Created by {@link MediaWindow#handle()}, one per window, so a listener added twice
 * from two places lands on the same object.</p>
 *
 * <p>It is its own {@link MediaWindowHandle} as well. The two interfaces are separate
 * because a handle will not always have a window — a headless player and an off-screen
 * surface will not — but while every handle is a window there is nothing for a second
 * object to hold, and the three members they share ({@code isVisible}, {@code setVisible},
 * {@code bringToFront}) have one meaning, not two.</p>
 *
 * <p><b>Death.</b> A window goes away for three reasons — closed, evicted past the
 * window cap, or dropped on disconnect — and only the first is something the addon did.
 * After any of them {@link #isAlive()} is {@code false} and every call here is a no-op
 * returning a neutral value, which is what lets an addon hold a handle across a world
 * change without guarding each call.</p>
 */
public final class WindowHandle implements MediaHandle, MediaWindowHandle {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MediaWindow window;
    private final long id;
    private final MediaKind kind;

    /**
     * Copy-on-write for the same reason the global dispatcher is: listeners are added
     * from an addon's setup and dispatched from the render and decode threads.
     */
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * The last URL this window was seen playing. Kept so a dead handle can still say
     * what it was — a {@code STOPPED} listener that cannot read the url it is being told
     * about would be of very little use.
     */
    private volatile String lastUrl;

    WindowHandle(MediaWindow window) {
        this.window = window;
        this.id = window.getId();
        this.kind = window.mediaKind();
        this.lastUrl = window.mediaUrl();
    }

    // ------------------------------------------------------------------
    // Identity (thread-safe)
    // ------------------------------------------------------------------

    @Override
    public long id() {
        return id;
    }

    @Override
    public String url() {
        if (window.isAlive()) {
            lastUrl = window.mediaUrl();
        }
        return lastUrl;
    }

    @Override
    public MediaKind kind() {
        return kind;
    }

    @Override
    public boolean isAlive() {
        return window.isAlive();
    }

    @Override
    public PlaybackState state() {
        return window.isAlive() ? window.playbackState() : PlaybackState.ENDED;
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @Override
    public void play() {
        if (window.isAlive()) {
            window.play();
        }
    }

    @Override
    public void pause() {
        if (window.isAlive()) {
            window.pause();
        }
    }

    @Override
    public void togglePause() {
        if (window.isAlive()) {
            window.togglePlayPause();
        }
    }

    @Override
    public void stop() {
        if (window.isAlive()) {
            window.pause();
            window.seekTo(0);
        }
    }

    @Override
    public void close() {
        if (window.isAlive()) {
            window.closeWithFade();
        }
    }

    @Override
    public long positionMicros() {
        return window.isAlive() ? Math.max(0, window.positionMicros()) : 0L;
    }

    @Override
    public long durationMicros() {
        return window.isAlive() ? window.durationMicros() : -1L;
    }

    @Override
    public double progress() {
        long duration = durationMicros();
        return duration <= 0 ? 0.0 : Math.min(1.0, (double) positionMicros() / duration);
    }

    @Override
    public void seekTo(long micros) {
        if (window.isAlive()) {
            window.seekTo(micros);
        }
    }

    @Override
    public void seekToFraction(double fraction) {
        if (window.isAlive()) {
            window.seekToFraction(fraction);
        }
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public Component title() {
        // literal, not translatable: what comes back is either a title the site gave us
        // or the raw URL, and both are already the text to draw.
        return Component.literal(MediaPlayerContext.get().getTitleCache().getOrLoad(url()));
    }

    @Override
    public boolean isVisible() {
        return window.isAlive() && window.isVisible();
    }

    @Override
    public void setVisible(boolean visible) {
        if (window.isAlive()) {
            window.setVisible(visible);
        }
    }

    @Override
    public void bringToFront() {
        if (window.isAlive()) {
            window.bringToFront();
        }
    }

    // ------------------------------------------------------------------
    // The window (MediaWindowHandle)
    // ------------------------------------------------------------------

    @Override
    public Optional<MediaWindowHandle> window() {
        return Optional.of(this);
    }

    @Override
    public Optional<MediaQueue> queue() {
        return Optional.ofNullable(window.queueHandle());
    }

    @Override
    public Optional<AudioControls> audio() {
        // The isAlive guard is not just hygiene: audioControls() builds the window's gain
        // on first use, and a dead window should not be made to allocate one.
        return window.isAlive() ? Optional.ofNullable(window.audioControls()) : Optional.empty();
    }

    @Override
    public int x() {
        return window.boxX;
    }

    @Override
    public int y() {
        return window.boxY;
    }

    @Override
    public int width() {
        return window.boxW;
    }

    @Override
    public int height() {
        return window.boxH;
    }

    @Override
    public void setPlacement(Placement placement) {
        if (window.isAlive()) {
            window.placement().setRequestedPlacement(placement);
        }
    }

    @Override
    public void setSizing(Sizing sizing) {
        if (window.isAlive()) {
            window.placement().setRequestedSizing(sizing);
        }
    }

    @Override
    public boolean isTheater() {
        return window.isAlive() && window.isTheater();
    }

    @Override
    public void setTheater(boolean theater) {
        if (window.isAlive() && window.isTheater() != theater) {
            window.toggleTheater();
        }
    }

    @Override
    public WindowChromeOptions chrome() {
        return window.chrome();
    }

    @Override
    public void setChrome(WindowChromeOptions chrome) {
        if (window.isAlive()) {
            window.setChrome(chrome);
        }
    }

    @Override
    public void setInteractive(boolean interactive) {
        setChrome(chrome().withInteractive(interactive));
    }

    @Override
    public boolean persistsGeometry() {
        return window.persistsGeometry();
    }

    @Override
    public void setPersistGeometry(boolean persist) {
        if (window.isAlive()) {
            window.setPersistGeometry(persist);
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @Override
    public void addListener(PlaybackListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    /**
     * Delivers {@code event} to this handle's own listeners. The window posts to the
     * global {@code PlaybackEvents} as well; this is the handle-scoped half, which is
     * what saves the common case an id comparison in every callback.
     *
     * <p>Swallow-and-log, exactly like the global dispatcher: a listener that throws
     * must not take down the player that posted the event.</p>
     */
    void dispatch(PlaybackEvent event) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlayback(event);
            } catch (RuntimeException e) {
                LOGGER.error("A handle listener threw on {}", event.getType(), e);
            }
        }
    }

    /** Drops every listener; called once the window is gone and nothing more will fire. */
    void clearListeners() {
        listeners.clear();
    }

    @Override
    public String toString() {
        return "MediaHandle[" + id + " " + kind + " " + lastUrl + (isAlive() ? "]" : " dead]");
    }
}
