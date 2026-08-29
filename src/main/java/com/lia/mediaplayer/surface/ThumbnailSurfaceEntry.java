package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.video.VideoThumbnailCache;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A single frame grabbed at a timestamp — a poster.
 *
 * <p>Backed by {@link VideoThumbnailCache}, the same store the queue panel draws its rows
 * from, so a grid of posters for videos the player has already queued costs nothing
 * extra. One ffmpeg launch and then a still picture, which is what makes this rather than
 * {@link VideoSurfaceEntry} the right thing for a wall of many.</p>
 */
final class ThumbnailSurfaceEntry extends SurfaceEntry {

    private final String url;
    private final double atSeconds;

    ThumbnailSurfaceEntry(String key, String url, double atSeconds) {
        super(key);
        this.url = url;
        this.atSeconds = atSeconds;
    }

    private VideoThumbnailCache.Thumb thumb() {
        VideoThumbnailCache cache = MediaPlayerContext.get().getThumbnailCache();
        return atSeconds > 0 ? cache.getOrLoadAt(url, atSeconds) : cache.getOrLoad(url);
    }

    @Override
    @Nullable
    ResourceLocation texture() {
        if (isDisposed()) {
            return null;
        }
        VideoThumbnailCache.Thumb thumb = thumb();
        return thumb.isLoaded() ? thumb.texture : null;
    }

    @Override
    PlaybackState state() {
        if (isDisposed()) {
            return PlaybackState.ENDED;
        }
        return switch (thumb().state) {
            case IDLE, LOADING -> PlaybackState.LOADING;
            case LOADED -> PlaybackState.PLAYING;
            case FAILED -> PlaybackState.FAILED;
        };
    }

    @Override
    int sourceWidth() {
        return isDisposed() ? 0 : thumb().width;
    }

    @Override
    int sourceHeight() {
        return isDisposed() ? 0 : thumb().height;
    }
}
