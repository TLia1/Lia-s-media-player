/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.diag;

/**
 * A snapshot of what the mod is currently holding — the numbers that make a support
 * question answerable without asking anyone for their {@code latest.log}.
 *
 * <p>An F3-style overlay, a pack maintainer's debug screen, or an addon deciding whether
 * it can afford another surface all want the same handful of counters, and every one of
 * them is already tracked somewhere inside the mod. Read it through
 * {@link MediaDiagnostics#stats()}.</p>
 *
 * <p>A value, not a view: it does not update itself, and reading it starts nothing.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param videoWindows       open video players
 * @param audioWindows       open audio bars
 * @param pinnedImages       pinned image windows
 * @param headlessSounds     sounds playing with no window at all (see {@code MediaAudio})
 * @param activeSurfaces     live off-screen surfaces, shared decodes counted once
 * @param decodingSurfaces   how many of those are running a video decode — the expensive
 *                           ones, and the ones the tighter cap counts
 * @param imageCacheBytes    the image preview cache's estimate of its own VRAM use
 * @param imageCacheEntries  pictures in that cache
 * @param thumbnailEntries   entries in the video thumbnail cache
 * @param titleCacheEntries  resolved titles held
 * @param binariesReady      whether ffmpeg, ffprobe and yt-dlp are all present
 * @since API 3.4.0
 */
public record MediaPlayerStats(int videoWindows, int audioWindows, int pinnedImages,
                               int headlessSounds, int activeSurfaces, int decodingSurfaces,
                               long imageCacheBytes, int imageCacheEntries,
                               int thumbnailEntries, int titleCacheEntries,
                               boolean binariesReady) {

    /** Every window the mod has on screen, of any kind. */
    public int totalWindows() {
        return videoWindows + audioWindows + pinnedImages;
    }
}
