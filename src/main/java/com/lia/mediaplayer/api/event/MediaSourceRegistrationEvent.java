/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.event;

import com.lia.mediaplayer.api.MediaSource;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fired on the <b>mod event bus</b> during Lia's Media Player initialization to let
 * other mods register custom {@link MediaSource}s.
 *
 * <p>Example usage in an addon mod:</p>
 * <pre>{@code
 * @Mod("myaddon")
 * public class MyAddon {
 *     public MyAddon(IEventBus modBus) {
 *         modBus.addListener(this::onRegisterSources);
 *     }
 *
 *     private void onRegisterSources(MediaSourceRegistrationEvent event) {
 *         event.register(new MySoundCloudSource());
 *     }
 * }
 * }</pre>
 *
 * <p>This is part of the <b>public API</b>.</p>
 */
public class MediaSourceRegistrationEvent extends Event implements IModBusEvent {

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
