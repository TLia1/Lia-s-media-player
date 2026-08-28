package com.lia.mediaplayer;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaSourceProvider;
import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import com.lia.mediaplayer.tools.MediaBinaries;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces image links with a hoverable
 * [picture] entry that renders an image preview over the chat, and turns
 * video/YouTube and audio links into an in-game player.
 *
 * <p>The player shells out to two external command-line tools — yt-dlp (to
 * resolve YouTube links) and ffmpeg (to decode video/audio). Rather than
 * bundling them in the jar, we download the official builds into the game
 * folder on first launch; see {@link MediaBinaries}. Kicking that off from
 * startup means the binaries are usually ready before the first link is
 * clicked, instead of being fetched lazily mid-feature.</p>
 *
 * <p>This class is the mod's <em>loader-neutral</em> startup: no {@code @Mod},
 * no {@code ClientModInitializer}, no event bus. Each loader has a small bridge
 * in {@code com.lia.mediaplayer.platform} that owns its entry point and calls
 * {@link #init()} and {@link #registerExternalSources(List)} at the right moment.</p>
 */
public final class LiasMediaPlayer {
    // The value here should match an entry in the loader metadata (neoforge.mods.toml
    // and fabric.mod.json).
    public static final String MODID = "liasmediaplayer";
    public static final Logger LOGGER = LogUtils.getLogger();

    private LiasMediaPlayer() {
    }

    /**
     * Builds the composition root, publishes it as the API singleton, and starts the
     * background work that should be done before the player clicks anything.
     *
     * <p>Called once, from the loader bridge's entry point.</p>
     *
     * @return the freshly created context, already published via
     *         {@link LiasMediaPlayerApi#setInstance}
     */
    public static MediaPlayerContext init() {
        MediaPlayerContext context = new MediaPlayerContext();
        LiasMediaPlayerApi.setInstance(context);

        // Install yt-dlp and ffmpeg in the background so they are ready when needed.
        MediaBinaries.installAllAsync();

        // Load persisted volume.
        context.getVolumeManager().load();

        // And the settings. This used to happen on the first read that asked for it
        // (the video resolution, when something was played), which was late enough that
        // anything consulted earlier — the link filters, on the first chat message —
        // would have answered from the declared defaults rather than from the file.
        context.getConfigStore().ensureLoaded();

        return context;
    }

    /**
     * Runs every addon-supplied {@link MediaSourceProvider} and applies whatever sources
     * they contribute. Called during client setup, once the loader has had a chance to
     * discover them.
     *
     * @param discovered providers the loader found by its own mechanism (a Fabric
     *                   entrypoint, for instance); those registered through
     *                   {@link LiasMediaPlayerApi#registerSourceProvider} are added to
     *                   these and work on either loader
     */
    public static void registerExternalSources(List<MediaSourceProvider> discovered) {
        List<MediaSourceProvider> providers = new ArrayList<>(LiasMediaPlayerApi.sourceProviders());
        providers.addAll(discovered);

        MediaSourceRegistrationEvent registrationEvent = new MediaSourceRegistrationEvent();
        for (MediaSourceProvider provider : providers) {
            try {
                provider.registerSources(registrationEvent);
            } catch (RuntimeException e) {
                // One broken addon must not stop the others, nor the mod itself.
                LOGGER.error("Media source provider {} threw while registering",
                        provider.getClass().getName(), e);
            }
        }

        MediaPlayerContext context = MediaPlayerContext.get();
        registrationEvent.getRegistered().forEach(source -> context.getMediaSources().register(source));
        LOGGER.info("Registered {} external media source(s) via API",
                registrationEvent.getRegistered().size());
    }
}
