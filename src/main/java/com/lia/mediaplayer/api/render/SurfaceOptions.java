/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.render;

/**
 * How a video {@link MediaSurface} should be decoded.
 *
 * <p>Two surfaces asking for the same URL share one decode only when their options match
 * as well, so keep them stable: building a fresh {@code SurfaceOptions} with a different
 * resolution cap for the same video means a second ffmpeg process, not a second view of
 * the first.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param maxWidth  a ceiling on the decoded width, or {@code 0} for "whatever the user's
 *                  video-quality setting says". A cap here can only make the picture
 *                  <em>smaller</em> than that setting, never larger — the quality the
 *                  user chose is a budget on their machine, not a default for an addon
 *                  to raise.
 * @param maxHeight the same for the height
 * @param loop      restart from the beginning when the track ends, instead of stopping
 * @param autoplay  start decoding immediately; {@code false} leaves the surface paused
 *                  on its first frame until something calls {@code playback().play()}
 * @since API 3.0.0
 */
public record SurfaceOptions(int maxWidth, int maxHeight, boolean loop, boolean autoplay) {

    private static final SurfaceOptions DEFAULTS = new SurfaceOptions(0, 0, false, true);

    /** The user's video quality, played once, straight away. */
    public static SurfaceOptions defaults() {
        return DEFAULTS;
    }

    /**
     * A ceiling on the decoded picture. Worth setting for anything drawn small — a
     * television the size of a block does not need a 720p decode.
     *
     * @throws IllegalArgumentException if either is negative
     */
    public SurfaceOptions withMaxSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("A size cap must not be negative");
        }
        return new SurfaceOptions(width, height, loop, autoplay);
    }

    public SurfaceOptions withLoop(boolean value) {
        return new SurfaceOptions(maxWidth, maxHeight, value, autoplay);
    }

    public SurfaceOptions withAutoplay(boolean value) {
        return new SurfaceOptions(maxWidth, maxHeight, loop, value);
    }
}
