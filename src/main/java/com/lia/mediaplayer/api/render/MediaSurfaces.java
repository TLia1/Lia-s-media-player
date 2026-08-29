/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.render;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

/**
 * Where a {@link MediaSurface} comes from.
 *
 * <p>Every method here answers with a surface rather than throwing or returning
 * {@code null}: a URL the mod cannot play, a mod that has not started yet, and a request
 * past the configured cap all come back as a surface in {@code FAILED} whose
 * {@link MediaSurface#texture()} is {@code null}. Drawing code should not have to branch
 * on which of those happened, and a caller in a block-entity renderer has nowhere sensible
 * to catch an exception.</p>
 *
 * <h2>The caps</h2>
 *
 * <p>Two settings bound this, and they are the reason the API can be handed to an addon
 * that loops over block entities: one on how many surfaces may exist at all, and one on
 * how many of them may be <em>decoding video</em> at once — every one of those is an
 * ffmpeg process. Past either, a request is refused (and logged), not queued.</p>
 *
 * <p><b>Render thread only.</b></p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.0.0
 */
public final class MediaSurfaces {

    private MediaSurfaces() {
    }

    /**
     * A still image or an animated GIF. The animation advances on its own; there is
     * nothing to play and {@link MediaSurface#playback()} is empty.
     */
    public static MediaSurface image(String url) {
        return image(url, false);
    }

    /**
     * The same, optionally keeping the decoded pixels readable through
     * {@link MediaSurface#pixels()}.
     *
     * <p>{@code keepPixels} costs one frame's worth of heap for as long as the picture is
     * cached, and it is part of the sharing key: two callers share one decode only when
     * they both asked for it (or neither did). Ask for it when you mean to sample the
     * picture — pulling an accent colour out of album art is the case this exists for —
     * and not otherwise.</p>
     *
     * @since API 3.4.0
     */
    public static MediaSurface image(String url, boolean keepPixels) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? DeadSurface.INSTANCE : api.createImageSurface(url, keepPixels);
    }

    /** A video decoded off-screen, with {@link SurfaceOptions#defaults()}. */
    public static MediaSurface video(String url) {
        return video(url, SurfaceOptions.defaults());
    }

    /**
     * A video decoded off-screen.
     *
     * <p>Its sound plays through the mod's single volume, positioned nowhere. Remember
     * {@link MediaSurface#markWanted()} every frame it is visible, or the picture stops
     * (deliberately) after a moment.</p>
     */
    public static MediaSurface video(String url, SurfaceOptions options) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null
                ? DeadSurface.INSTANCE
                : api.createVideoSurface(url, options == null ? SurfaceOptions.defaults() : options);
    }

    /**
     * A single frame — a poster. Decoded once and then still, so it costs one ffmpeg
     * launch rather than a running process, which is what makes it the right thing for a
     * grid of many.
     *
     * @param atSeconds where to grab it, or {@code <= 0} to let the mod choose (which
     *                  also lets a YouTube link answer with its published poster image
     *                  instead of launching anything at all)
     */
    public static MediaSurface thumbnail(String url, double atSeconds) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? DeadSurface.INSTANCE : api.createThumbnailSurface(url, atSeconds);
    }
}
