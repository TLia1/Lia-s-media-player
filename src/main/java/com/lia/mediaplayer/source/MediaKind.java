package com.lia.mediaplayer.source;

/**
 * The kind of media a {@link MediaSource} produces. The kind decides how a link is
 * presented in chat (label colour) and which feature picks it up afterwards (the
 * image preview/pin path, or the in-game video player).
 *
 * <p>The two kinds are intentionally <em>disjoint</em> across all registered
 * sources, so a single link is only ever claimed by one feature (a {@code .gif} is
 * an image; a {@code .mp4} is a video) and the two never fight over the same URL.</p>
 */
public enum MediaKind {
    /** A still picture or animated GIF, shown as a hover preview / pinned window. */
    IMAGE,
    /** A video, stream or YouTube link, played by the in-game video player. */
    VIDEO
}
