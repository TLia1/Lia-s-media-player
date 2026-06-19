/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

/**
 * The state of a media player (video or audio).
 *
 * <p>This is part of the <b>public API</b>.</p>
 */
public enum PlaybackState {
    /** The player is resolving the URL or buffering the first frames. */
    LOADING,
    /** Media is actively playing. */
    PLAYING,
    /** Playback is temporarily paused by the user. */
    PAUSED,
    /** The track has reached its natural end. */
    ENDED,
    /** Playback failed (network error, unsupported format, etc.). */
    FAILED
}
