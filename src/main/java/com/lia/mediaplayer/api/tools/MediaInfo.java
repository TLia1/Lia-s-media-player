/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.tools;

/**
 * What {@link MediaTools#probe(String)} found out about a URL.
 *
 * <p>A deliberately small, closed shape: the fields something needs to decide whether it
 * can show a link and how big to make the box for it. It is not a window onto ffprobe's
 * output, and it will not grow into one.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param durationMicros total length, or {@code <= 0} for a live stream
 * @since API 2.1.0
 */
public record MediaInfo(int width, int height, double fps, long durationMicros,
                        boolean hasVideo, boolean hasAudio) {

    /** Width over height, or {@code 0} when there is no picture. */
    public float aspectRatio() {
        return hasVideo && height > 0 ? (float) width / height : 0f;
    }
}
