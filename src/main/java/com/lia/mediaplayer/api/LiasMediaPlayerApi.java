/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.image.ImageDecoder;
import com.lia.mediaplayer.api.policy.MediaInterceptor;
import com.lia.mediaplayer.api.screen.MediaScreenTab;
import com.lia.mediaplayer.api.source.MediaMetadataProvider;
import com.lia.mediaplayer.api.source.MediaResolver;
import com.lia.mediaplayer.api.window.WindowAction;

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

    // ------------------------------------------------------------------
    // The other two extension points (since API 2.3.0)
    //
    // Same shape as registerSourceProvider, and for the same reason: `api` knows about
    // no mod loader, so a static registry is the one discovery story that works on both.
    // May be called before the mod has finished initializing.
    // ------------------------------------------------------------------

    private static final List<MediaResolver> RESOLVERS = new CopyOnWriteArrayList<>();
    private static final List<MediaMetadataProvider> METADATA_PROVIDERS = new CopyOnWriteArrayList<>();

    /**
     * Registers a way of turning an addon's own links into something {@code ffmpeg} can
     * open — see {@link MediaResolver}.
     *
     * @since API 2.3.0
     */
    public static void registerResolver(MediaResolver resolver) {
        if (resolver != null) {
            RESOLVERS.add(resolver);
        }
    }

    /**
     * Registers a supplier of titles (and durations, and thumbnails) for an addon's own
     * links — see {@link MediaMetadataProvider}.
     *
     * @since API 2.3.0
     */
    public static void registerMetadataProvider(MediaMetadataProvider provider) {
        if (provider != null) {
            METADATA_PROVIDERS.add(provider);
        }
    }

    /** Every registered resolver, in registration order (unmodifiable). */
    public static List<MediaResolver> resolvers() {
        return Collections.unmodifiableList(RESOLVERS);
    }

    /** Every registered metadata provider, in registration order (unmodifiable). */
    public static List<MediaMetadataProvider> metadataProviders() {
        return Collections.unmodifiableList(METADATA_PROVIDERS);
    }

    // ------------------------------------------------------------------
    // The 3.2 and 3.4 extension points
    //
    // Same static-registry shape as everything above, and for the same reason: `api`
    // knows about no mod loader, and every one of these has to be registrable from an
    // addon's entry point, which runs before this mod has finished initializing.
    // ------------------------------------------------------------------

    private static final List<MediaInterceptor> INTERCEPTORS = new CopyOnWriteArrayList<>();
    private static final List<WindowAction> WINDOW_ACTIONS = new CopyOnWriteArrayList<>();
    private static final List<ImageDecoder> IMAGE_DECODERS = new CopyOnWriteArrayList<>();
    private static final List<MediaScreenTab> SCREEN_TABS = new CopyOnWriteArrayList<>();

    /**
     * Registers the right to veto or rewrite what gets played, and what a chat link
     * becomes — see {@link MediaInterceptor}. Interceptors are asked in registration
     * order and the first veto wins.
     *
     * @since API 3.2.0
     */
    public static void registerInterceptor(MediaInterceptor interceptor) {
        if (interceptor != null) {
            INTERCEPTORS.add(interceptor);
        }
    }

    /**
     * Removes an interceptor previously registered. Unlike the other registries this one
     * has a way out, because an interceptor is a <em>policy</em> — an addon that turns
     * its own moderation off must be able to stop being asked.
     *
     * @since API 3.2.0
     */
    public static void unregisterInterceptor(MediaInterceptor interceptor) {
        INTERCEPTORS.remove(interceptor);
    }

    /** Every registered interceptor, in registration order (unmodifiable). */
    public static List<MediaInterceptor> interceptors() {
        return Collections.unmodifiableList(INTERCEPTORS);
    }

    /**
     * Registers a button of your own in every media window's corner row — see
     * {@link WindowAction}. Registering the same {@link WindowAction#id()} twice replaces
     * the first, so a re-registration on reload does not double the button.
     *
     * @since API 3.2.0
     */
    public static void registerWindowAction(WindowAction action) {
        if (action == null) {
            return;
        }
        WINDOW_ACTIONS.removeIf(existing -> existing.id().equals(action.id()));
        WINDOW_ACTIONS.add(action);
    }

    /** Every registered window action, in registration order (unmodifiable). */
    public static List<WindowAction> windowActions() {
        return Collections.unmodifiableList(WINDOW_ACTIONS);
    }

    /**
     * Registers a decoder for a picture format the mod does not know — see
     * {@link ImageDecoder}. Decoders are asked before the built-in ones, in registration
     * order.
     *
     * @since API 3.4.0
     */
    public static void registerImageDecoder(ImageDecoder decoder) {
        if (decoder != null) {
            IMAGE_DECODERS.add(decoder);
        }
    }

    /** Every registered image decoder, in registration order (unmodifiable). */
    public static List<ImageDecoder> imageDecoders() {
        return Collections.unmodifiableList(IMAGE_DECODERS);
    }

    /**
     * Registers a way into a screen of your own from the mod's library screens — see
     * {@link MediaScreenTab}. Registering the same {@link MediaScreenTab#id()} twice
     * replaces the first.
     *
     * @since API 3.4.0
     */
    public static void registerScreenTab(MediaScreenTab tab) {
        if (tab == null) {
            return;
        }
        SCREEN_TABS.removeIf(existing -> existing.id().equals(tab.id()));
        SCREEN_TABS.add(tab);
    }

    /** Every registered screen tab, in registration order (unmodifiable). */
    public static List<MediaScreenTab> screenTabs() {
        return Collections.unmodifiableList(SCREEN_TABS);
    }
}
