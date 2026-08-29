/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.audio.AudioControls;
import com.lia.mediaplayer.api.event.PlaybackListener;
import com.lia.mediaplayer.api.window.MediaWindowHandle;

import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * A live media instance — today a window, later also a headless player or an off-screen
 * surface. The thing a {@code long} id could never be.
 *
 * <p>The {@code long}-taking methods on {@link IMediaPlayerAPI} are write-only: an addon
 * can act on an id but cannot read a state, a position, a duration or a title back out of
 * it, and is never told when the window behind it goes away — which happens on its own,
 * because both players evict their oldest window once the configured cap is reached. A
 * handle answers all of that, and {@link #isAlive()} answers the last one.</p>
 *
 * <p><b>Threading.</b> {@link #id()}, {@link #url()}, {@link #kind()} and
 * {@link #isAlive()} are safe from any thread. Everything else — every transport call,
 * every position read, {@link #title()} — is <b>render thread only</b>, like the rest of
 * the playback API.</p>
 *
 * <p><b>A dead handle is inert, not fatal.</b> Once {@link #isAlive()} is {@code false}
 * every method below is a no-op returning a neutral value, so an addon holding a handle
 * across a disconnect does not have to guard every call.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.1.0
 */
public interface MediaHandle {

    /**
     * The same id {@link IMediaPlayerAPI#playVideo(String)} and its siblings return, so
     * the two halves of the API address the same thing.
     */
    long id();

    /** The media playing right now — which changes as a queue advances. */
    String url();

    MediaKind kind();

    PlaybackState state();

    /**
     * Whether this handle still points at something. {@code false} once the window was
     * closed, evicted past the window cap, or dropped on disconnect; it never becomes
     * {@code true} again.
     */
    boolean isAlive();

    // ------------------------------------------------------------------
    // Transport — render thread
    // ------------------------------------------------------------------

    /** Resumes, if paused. Does nothing to a player that is already running. */
    void play();

    /** Pauses, if playing. */
    void pause();

    void togglePause();

    /**
     * Stops playback and leaves the handle alive with {@link PlaybackState#ENDED}. What
     * a "stop" button means, as opposed to {@link #close()}.
     */
    void stop();

    /** Disposes the window and its player; {@link #isAlive()} is {@code false} after this. */
    void close();

    long positionMicros();

    /** Total length, or {@code <= 0} for a live stream or one not yet probed. */
    long durationMicros();

    /** {@link #positionMicros()} over {@link #durationMicros()}, or {@code 0} when unknown. */
    double progress();

    void seekTo(long micros);

    /** Seeks to a point given as {@code 0..1} of the duration. */
    void seekToFraction(double fraction);

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    /**
     * The readable title once it has been resolved, else the URL. Never blocks: a title
     * still being fetched comes back as the URL and is correct on a later call.
     */
    Component title();

    /** Whether the window is on screen (a hidden player keeps playing). */
    boolean isVisible();

    void setVisible(boolean visible);

    /** Raises this window above the rest of the stack. */
    void bringToFront();

    /**
     * The window this is playing in — where it sits, how big it is, and what the user is
     * allowed to do to it.
     *
     * <p>Empty for a handle with no window of its own. Every handle has one today; a
     * headless audio player and an off-screen surface will not.</p>
     *
     * @since API 2.2.0
     */
    Optional<MediaWindowHandle> window();

    /**
     * What this player is going to play next — see {@link MediaQueue}.
     *
     * <p>Empty for a handle with no queue behind it: a pinned image is one picture, not
     * a playlist.</p>
     *
     * @since API 2.3.0
     */
    Optional<MediaQueue> queue();

    /**
     * This sound's own share of the mix — its gain, its channel and where in the world
     * it is coming from. See {@link AudioControls}.
     *
     * <p>Empty for a handle with no sound of its own: a pinned image. Present for every
     * video and audio player, windowed or not — a video's soundtrack is placed and
     * scaled by exactly the same controls a headless track's is.</p>
     *
     * @since API 3.1.0
     */
    Optional<AudioControls> audio();

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /**
     * Subscribes to this handle's events only — the common case, without the global
     * listener plus id comparison it used to need. Listeners are dropped when the handle
     * dies, so this one cannot leak across a world reload the way a
     * {@code PlaybackEvents.register} does.
     */
    void addListener(PlaybackListener listener);

    void removeListener(PlaybackListener listener);
}
