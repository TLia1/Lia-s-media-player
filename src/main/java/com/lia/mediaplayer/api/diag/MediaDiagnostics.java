/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.diag;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

/**
 * What the mod is holding right now — see {@link MediaPlayerStats}.
 *
 * <p>A facade over {@link IMediaPlayerAPI#stats()}, in the same shape as
 * {@code MediaTools} and {@code MediaAudio}, so an addon reads one name rather than
 * fetching the instance itself.</p>
 *
 * <p><b>Render thread only</b> — the counters are read off the live window lists and
 * caches.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.4.0
 */
public final class MediaDiagnostics {

    /** What is answered before the mod has initialized: all zeroes, nothing ready. */
    private static final MediaPlayerStats EMPTY =
            new MediaPlayerStats(0, 0, 0, 0, 0, 0, 0L, 0, 0, 0, false);

    private MediaDiagnostics() {
    }

    /** A snapshot of the counters, or an all-zero one if the mod is not up yet. */
    public static MediaPlayerStats stats() {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? EMPTY : api.stats();
    }
}
