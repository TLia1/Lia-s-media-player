/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.event;

/**
 * Receives {@link PlaybackEvent}s. Register one with
 * {@link PlaybackEvents#register(PlaybackListener)}.
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.0.0
 */
@FunctionalInterface
public interface PlaybackListener {

    /**
     * Called on the thread that caused the state change — usually the render thread, but
     * a decode thread for {@link PlaybackEvent.Type#ENDED} and
     * {@link PlaybackEvent.Type#FAILED}. Do not block in here.
     */
    void onPlayback(PlaybackEvent event);
}
