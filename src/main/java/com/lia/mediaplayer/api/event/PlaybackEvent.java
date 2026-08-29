/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.event;

import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.PlaybackState;

import org.jetbrains.annotations.Nullable;

/**
 * Describes a playback state change. Addons subscribe through {@link PlaybackEvents} to
 * implement features like video/audio synchronization across a server.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PlaybackEvents.register(event -> {
 *     if (event.getType() == PlaybackEvent.Type.STARTED) {
 *         // A new track started playing
 *         String url = event.getUrl();
 *     }
 * });
 * }</pre>
 *
 * <p>This used to extend {@code net.neoforged.bus.api.Event} and be documented on
 * {@code NeoForge.EVENT_BUS}. It is now a plain object dispatched by
 * {@link PlaybackEvents}, so the same addon code works on both loaders.</p>
 *
 * <p>Since API 2.1.0 an event carries the {@link #getHandle() handle} it came from,
 * which is what makes two windows playing two things tell-apart-able in a listener.
 * It is {@code null} only on the lifecycle events, which belong to no player.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.0.0 — no longer extends {@code net.neoforged.bus.api.Event}
 */
public class PlaybackEvent {

    /**
     * The type of playback event.
     */
    public enum Type {
        /**
         * A new track has started playing.
         */
        STARTED,
        /**
         * Playback was paused by the user.
         */
        PAUSED,
        /**
         * Playback was resumed from a pause.
         */
        RESUMED,
        /**
         * A seek was performed (position changed).
         */
        SEEKED,
        /**
         * The track has ended naturally.
         */
        ENDED,
        /**
         * Playback failed with an error.
         */
        FAILED,
        /**
         * The player was closed/disposed.
         */
        STOPPED,
        /**
         * A player's queue changed — something was added, removed, moved, or the queue
         * advanced to its next entry.
         *
         * <p>Carries the handle whose queue it was; read the new contents through
         * {@code event.getHandle().queue()}. There is deliberately no snapshot on the
         * event itself: it is posted once a tick from a version check, so a listener that
         * wants the list is asking for it at exactly the moment it is valid.</p>
         *
         * @since API 2.3.0
         */
        QUEUE_CHANGED,
        /**
         * A title (and whatever else came with it) finished resolving for a URL.
         *
         * <p>Carries no handle: a title is resolved for a <em>link</em>, often long
         * before — or long after — anything is playing it. {@link #getUrl()} is the link,
         * and {@link #getDurationMicros()} is the duration if one came with it.</p>
         *
         * @since API 2.3.0
         */
        METADATA_RESOLVED,
        /**
         * The mod has finished initializing and every addon-supplied source has been
         * registered: the moment an addon may safely call the API.
         *
         * <p>Carries no handle, no url and no state. Before this existed addons guessed
         * at it by polling {@code getInstanceOrNull()} or by relying on setup order.</p>
         *
         * @since API 2.1.0
         */
        LIFECYCLE_READY,
        /**
         * The player left the world or the server. Every window has been closed and
         * every cache emptied by the time this is posted, so any handle an addon still
         * holds is now dead.
         *
         * @since API 2.1.0
         */
        WORLD_LEFT
    }

    /**
     * The kind of player that fired this event.
     */
    public enum PlayerKind {
        VIDEO,
        AUDIO,
        /**
         * A pinned image window. It has no clock and no transport, so it only ever
         * reports {@link Type#STARTED} and {@link Type#STOPPED}.
         *
         * @since API 2.1.0
         */
        IMAGE
    }

    private final Type type;
    private final PlayerKind playerKind;
    private final String url;
    private final PlaybackState state;
    private final long positionMicros;
    private final long durationMicros;
    private final MediaHandle handle;

    /**
     * The 2.0 constructor, kept because it is public API. Events built this way carry
     * no handle.
     */
    public PlaybackEvent(Type type, PlayerKind playerKind, String url,
                         PlaybackState state, long positionMicros, long durationMicros) {
        this(type, playerKind, url, state, positionMicros, durationMicros, null);
    }

    /**
     * @param handle the player this came from, or {@code null} for a lifecycle event
     * @since API 2.1.0
     */
    public PlaybackEvent(Type type, PlayerKind playerKind, String url,
                         PlaybackState state, long positionMicros, long durationMicros,
                         @Nullable MediaHandle handle) {
        this.type = type;
        this.playerKind = playerKind;
        this.url = url;
        this.state = state;
        this.positionMicros = positionMicros;
        this.durationMicros = durationMicros;
        this.handle = handle;
    }

    /**
     * A {@link Type#LIFECYCLE_READY} or {@link Type#WORLD_LEFT} event: no player, no
     * media, nothing to read but the type.
     *
     * @since API 2.1.0
     */
    public static PlaybackEvent lifecycle(Type type) {
        return new PlaybackEvent(type, null, "", null, 0L, 0L, null);
    }

    /**
     * A {@link Type#METADATA_RESOLVED} event: a link and what was learned about it, with
     * no player behind it.
     *
     * @since API 2.3.0
     */
    public static PlaybackEvent metadata(String url, long durationMicros) {
        return new PlaybackEvent(Type.METADATA_RESOLVED, null, url, null, 0L, durationMicros, null);
    }

    /**
     * The type of playback event (started, paused, ended, etc.).
     */
    public Type getType() {
        return type;
    }

    /**
     * Whether this event comes from a video or audio player. {@code null} on the
     * lifecycle events.
     */
    @Nullable
    public PlayerKind getPlayerKind() {
        return playerKind;
    }

    /**
     * The URL of the media being played; the empty string on the lifecycle events.
     */
    public String getUrl() {
        return url;
    }

    /**
     * The current state of the player. {@code null} on the lifecycle events.
     */
    @Nullable
    public PlaybackState getState() {
        return state;
    }

    /**
     * The playback position in microseconds at the time of the event.
     */
    public long getPositionMicros() {
        return positionMicros;
    }

    /**
     * The total duration in microseconds, or 0 if unknown (e.g. live streams).
     */
    public long getDurationMicros() {
        return durationMicros;
    }

    /**
     * The player this event came from, or {@code null} for a lifecycle event and for
     * an event built through the 2.0 constructor.
     *
     * <p>The handle may already be {@linkplain MediaHandle#isAlive() dead} by the time a
     * listener reads it — {@link Type#STOPPED} is posted as the window goes away — which
     * is fine: a dead handle still answers {@link MediaHandle#id()} and
     * {@link MediaHandle#url()}, and every other call on it is a no-op.</p>
     *
     * @since API 2.1.0
     */
    @Nullable
    public MediaHandle getHandle() {
        return handle;
    }
}
