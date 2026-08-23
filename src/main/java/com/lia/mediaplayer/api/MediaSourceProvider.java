/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;

/**
 * An addon's contribution of custom {@link MediaSource}s.
 *
 * <p>Implement this, then make Lia's Media Player aware of it. <b>How</b> it is
 * discovered is the one thing that differs per loader; what you write inside
 * {@link #registerSources} does not.</p>
 *
 * <p>On <b>either</b> loader, call
 * {@link LiasMediaPlayerApi#registerSourceProvider(MediaSourceProvider)} from your mod's
 * entry point:</p>
 * <pre>{@code
 * // NeoForge
 * @Mod("myaddon")
 * public class MyAddon {
 *     public MyAddon(IEventBus modBus) {
 *         LiasMediaPlayerApi.registerSourceProvider(new MySources());
 *     }
 * }
 * }</pre>
 *
 * <p>On <b>Fabric</b> you may instead declare a custom entrypoint, which frees you from
 * caring whether your initializer runs before or after this mod's:</p>
 * <pre>{@code
 * // fabric.mod.json
 * "entrypoints": {
 *   "liasmediaplayer:sources": ["com.example.addon.MySources"]
 * }
 * }</pre>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.0.0
 */
@FunctionalInterface
public interface MediaSourceProvider {

    /**
     * Called once during client setup. Register your sources on {@code event}.
     *
     * <p>Sources are appended after the built-in ones, and the first source that matches
     * a URL wins, so registration order decides who claims a link.</p>
     */
    void registerSources(MediaSourceRegistrationEvent event);
}
