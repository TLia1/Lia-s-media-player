package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.render.SurfacePixels;
import com.lia.mediaplayer.image.ImagePreviewCache;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A still or animated image surface.
 *
 * <p>It owns nothing. The pixels live in {@link ImagePreviewCache}, which is where every
 * other picture in the mod lives, and this asks it again on every call rather than
 * keeping the {@code Entry} it got back — the cache is bounded by a VRAM budget and may
 * have dropped it in between, and asking again is how the surface heals from that instead
 * of holding a texture id nobody uploaded any more.</p>
 *
 * <p>That is also why an image surface does not need the {@code markWanted} back-pressure
 * a video does: there is no process to slow down, only a decode that happened once.</p>
 */
final class ImageSurfaceEntry extends SurfaceEntry {

    private final String url;

    /** Whether the caller asked to be able to read the picture back — see {@code MediaSurface.pixels()}. */
    private final boolean keepPixels;

    ImageSurfaceEntry(String key, String url, boolean keepPixels) {
        super(key);
        this.url = url;
        this.keepPixels = keepPixels;
    }

    private ImagePreviewCache.Entry entry() {
        return MediaPlayerContext.get().getImagePreviewCache().getOrLoad(url, keepPixels);
    }

    @Override
    @Nullable
    ResourceLocation texture() {
        return isDisposed() ? null : entry().currentFrame();
    }

    @Override
    PlaybackState state() {
        if (isDisposed()) {
            return PlaybackState.ENDED;
        }
        return switch (entry().state) {
            case IDLE, LOADING -> PlaybackState.LOADING;
            case LOADED -> PlaybackState.PLAYING;
            case FAILED -> PlaybackState.FAILED;
        };
    }

    @Override
    Optional<SurfacePixels> pixels() {
        return isDisposed() ? Optional.empty() : Optional.ofNullable(entry().pixels());
    }

    @Override
    int sourceWidth() {
        return isDisposed() ? 0 : entry().width;
    }

    @Override
    int sourceHeight() {
        return isDisposed() ? 0 : entry().height;
    }
}
