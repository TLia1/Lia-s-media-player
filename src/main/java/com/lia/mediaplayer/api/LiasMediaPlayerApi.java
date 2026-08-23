/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The API's front door: the live {@link IMediaPlayerAPI} instance, and the place addons
 * register their {@link MediaSourceProvider}s. All public API surfaces live in this
 * package ({@code com.lia.mediaplayer.api}).
 *
 * <p>Other mods should <b>only depend on classes in this package</b> and never
 * import anything from {@code com.lia.mediaplayer} directly.</p>
 *
 * <p>Nothing here mentions a mod loader. The NeoForge {@code @Mod} entry that used to
 * live on this class — the one that gives the API its own line in the Mods menu — moved
 * to {@code platform.neoforge}, so that this package compiles unchanged on Fabric.</p>
 */
public class LiasMediaPlayerApi {
    /**
     * The mod ID for the API entry in the loader metadata.
     */
    public static final String API_ID = "liasmediaplayerapi";

    /**
     * Providers registered before the mod collected them, plus any registered after
     * (which are applied immediately). Copy-on-write: addons register from their mod
     * constructor or client initializer, which is not the thread that reads this.
     */
    private static final List<MediaSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    /**
     * Written once on the mod-construction thread and read from the render thread,
     * the decode threads and the IO pool, so it has to be safely published.
     */
    private static volatile IMediaPlayerAPI instance;

    /**
     * Retrieves the active Media Player API instance.
     *
     * @return the API instance
     * @throws IllegalStateException if called before the mod is fully initialized
     */
    public static IMediaPlayerAPI getInstance() {
        IMediaPlayerAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("Lia's Media Player API is not initialized yet.");
        }
        return api;
    }

    /**
     * The active API instance, or {@code null} if the mod has not finished initializing.
     *
     * <p>For code that runs off an event bus — chat, ticks, rendering — where an
     * {@link IllegalStateException} would surface as a crash in someone else's callback.
     * Prefer {@link #getInstance()} everywhere the mod is known to be up.</p>
     */
    @org.jetbrains.annotations.Nullable
    public static IMediaPlayerAPI getInstanceOrNull() {
        return instance;
    }

    /**
     * Internal method used by the main mod to inject the API implementation.
     */
    public static void setInstance(IMediaPlayerAPI api) {
        instance = api;
    }

    /**
     * Registers a provider of custom {@link MediaSource}s. Call this from your mod's
     * entry point; it works identically on NeoForge and Fabric, and may be called before
     * Lia's Media Player has finished initializing.
     *
     * <p>Fabric addons may use the {@code liasmediaplayer:sources} entrypoint instead —
     * see {@link MediaSourceProvider}.</p>
     *
     * @since API 2.0.0
     */
    public static void registerSourceProvider(MediaSourceProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    /**
     * Every provider registered so far (unmodifiable). Called by the mod during client
     * setup; addons have no reason to.
     */
    public static List<MediaSourceProvider> sourceProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }
}
