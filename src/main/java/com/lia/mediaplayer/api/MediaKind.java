/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

/**
 * The kind of media a {@link MediaSource} produces. The kind decides how a link is
 * presented in chat (label colour) and which feature picks it up afterwards (the
 * image preview/pin path, the in-game video player, or the audio player).
 *
 * <p>This is part of the <b>public API</b> — other mods depend on it to implement
 * custom {@link MediaSource}s and to query media support programmatically.</p>
 */
public enum MediaKind {
    /**
     * A still picture or animated GIF, shown as a hover preview / pinned window.
     */
    IMAGE,
    /**
     * A video, stream or YouTube link, played by the in-game video player.
     */
    VIDEO,
    /**
     * A sound-only file, played by the in-game audio player (a compact bar + queue).
     */
    AUDIO
}
