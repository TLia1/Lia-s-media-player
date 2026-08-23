/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.event;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The dispatcher for {@link PlaybackEvent}.
 *
 * <p>This replaces the NeoForge event bus the API used to document. A bus is exactly
 * what Fabric does not have — there, an extension point is its own {@code Event} object —
 * so the API owns the (tiny) dispatcher itself and both loaders share it, and an addon
 * writes its listener once. Listeners that used to be added to {@code NeoForge.EVENT_BUS}
 * must move here; that is the breaking half of the API 2.0.0 bump.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.0.0
 */
public final class PlaybackEvents {

    // Its own logger rather than LiasMediaPlayer.LOGGER: `api` is the bottom of the
    // package graph and the one package addons compile against, so it must not reach
    // up into the mod.
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Copy-on-write because listeners are added on the mod-construction thread and
     * dispatched from the render and decode threads.
     */
    private static final List<PlaybackListener> LISTENERS = new CopyOnWriteArrayList<>();

    private PlaybackEvents() {
    }

    /** Subscribes {@code listener} to every playback state change. */
    public static void register(PlaybackListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** Unsubscribes a listener previously passed to {@link #register}. */
    public static void unregister(PlaybackListener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Dispatches {@code event} to every listener. A listener that throws is not allowed
     * to take down whichever player posted the event, so its failure is swallowed after
     * being logged.
     */
    public static void post(PlaybackEvent event) {
        for (PlaybackListener listener : LISTENERS) {
            try {
                listener.onPlayback(event);
            } catch (RuntimeException e) {
                LOGGER.error("A playback listener threw on {}", event.getType(), e);
            }
        }
    }
}
