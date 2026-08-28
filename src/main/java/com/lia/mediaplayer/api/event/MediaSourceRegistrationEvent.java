/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.event;

import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.MediaSourceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handed to every registered {@link MediaSourceProvider} during
 * Lia's Media Player initialization so it can contribute custom {@link MediaSource}s.
 *
 * <p>This used to be a NeoForge mod-bus event. It is now a plain object, because the mod
 * ships for two loaders and Fabric has no global event bus to post it on: the loader
 * decides how providers are <em>discovered</em>, and this type is what they are all
 * handed once discovered. See {@link MediaSourceProvider} for
 * how to register one on each loader.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.0.0 — no longer extends {@code net.neoforged.bus.api.Event}
 */
public class MediaSourceRegistrationEvent {

    private final List<MediaSource> registered = new ArrayList<>();

    /**
     * Registers a custom media source. Sources registered here are appended after
     * the built-in ones, so a custom source can override built-in behaviour by
     * matching URLs before any built-in source does (registration order matters:
     * first match wins).
     */
    public void register(MediaSource source) {
        if (source != null) {
            registered.add(source);
        }
    }

    /**
     * Returns the sources registered during this event (unmodifiable). Called
     * internally by the mod after the event has fired.
     */
    public List<MediaSource> getRegistered() {
        return Collections.unmodifiableList(registered);
    }
}
