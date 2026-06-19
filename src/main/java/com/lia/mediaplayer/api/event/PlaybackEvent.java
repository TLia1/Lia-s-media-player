/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.event;

import com.lia.mediaplayer.api.PlaybackState;
import net.neoforged.bus.api.Event;

/**
 * Fired on the <b>NeoForge event bus</b> ({@code NeoForge.EVENT_BUS}) when
 * a playback state change occurs. Addons can listen to these events to implement
 * features like video/audio synchronization across a server.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * NeoForge.EVENT_BUS.addListener((PlaybackEvent event) -> {
 *     if (event.getType() == PlaybackEvent.Type.STARTED) {
 *         // A new track started playing
 *         String url = event.getUrl();
 *     }
 * });
 * }</pre>
 *
 * <p>This is part of the <b>public API</b>.</p>
 */
public class PlaybackEvent extends Event {

    /** The type of playback event. */
    public enum Type {
        /** A new track has started playing. */
        STARTED,
        /** Playback was paused by the user. */
        PAUSED,
        /** Playback was resumed from a pause. */
        RESUMED,
        /** A seek was performed (position changed). */
        SEEKED,
        /** The track has ended naturally. */
        ENDED,
        /** Playback failed with an error. */
        FAILED,
        /** The player was closed/disposed. */
        STOPPED
    }

    /** The kind of player that fired this event. */
    public enum PlayerKind {
        VIDEO,
        AUDIO
    }

    private final Type type;
    private final PlayerKind playerKind;
    private final String url;
    private final PlaybackState state;
    private final long positionMicros;
    private final long durationMicros;

    public PlaybackEvent(Type type, PlayerKind playerKind, String url,
                         PlaybackState state, long positionMicros, long durationMicros) {
        this.type = type;
        this.playerKind = playerKind;
        this.url = url;
        this.state = state;
        this.positionMicros = positionMicros;
        this.durationMicros = durationMicros;
    }

    /** The type of playback event (started, paused, ended, etc.). */
    public Type getType() {
        return type;
    }

    /** Whether this event comes from a video or audio player. */
    public PlayerKind getPlayerKind() {
        return playerKind;
    }

    /** The URL of the media being played. */
    public String getUrl() {
        return url;
    }

    /** The current state of the player. */
    public PlaybackState getState() {
        return state;
    }

    /** The playback position in microseconds at the time of the event. */
    public long getPositionMicros() {
        return positionMicros;
    }

    /** The total duration in microseconds, or 0 if unknown (e.g. live streams). */
    public long getDurationMicros() {
        return durationMicros;
    }
}
